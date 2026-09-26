# -*- coding: utf-8 -*-
"""
Pure-stdlib PNG loader / cropper / writer.

Used to strip generator watermarks and normalise the subject framing of a
concept sheet before feeding it into image-to-3D.
Only 8-bit, non-interlaced PNGs are supported (color types 0/2/3/4/6).
"""
import struct
import sys
import zlib

CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}


def read_png(path):
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG file")

    pos, idat, ihdr, plte = 8, b"", None, None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        ctype = data[pos + 4:pos + 8]
        chunk = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctype == b"IHDR":
            ihdr = struct.unpack(">IIBBBBB", chunk)
        elif ctype == b"IDAT":
            idat += chunk
        elif ctype == b"PLTE":
            plte = chunk
        elif ctype == b"IEND":
            break

    w, h, bitdepth, ctype, _, _, interlace = ihdr
    if bitdepth != 8:
        raise ValueError("only 8-bit PNG supported")
    if interlace != 0:
        raise ValueError("interlaced PNG not supported")

    nch = CHANNELS[ctype]
    raw = zlib.decompress(idat)
    stride = w * nch
    out = bytearray(stride * h)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        ftype = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride

        if ftype == 1:      # Sub
            for i in range(nch, stride):
                line[i] = (line[i] + line[i - nch]) & 0xFF
        elif ftype == 2:    # Up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:    # Average
            for i in range(stride):
                a = line[i - nch] if i >= nch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:    # Paeth
            for i in range(stride):
                a = line[i - nch] if i >= nch else 0
                b = prev[i]
                c = prev[i - nch] if i >= nch else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                if pa <= pb and pa <= pc:
                    pr = a
                elif pb <= pc:
                    pr = b
                else:
                    pr = c
                line[i] = (line[i] + pr) & 0xFF

        out[y * stride:(y + 1) * stride] = line
        prev = line

    if ctype == 3:  # expand palette to RGB
        rgb = bytearray(w * h * 3)
        for i in range(w * h):
            idx = out[i] * 3
            rgb[i * 3:i * 3 + 3] = plte[idx:idx + 3]
        return w, h, 3, bytes(rgb)

    return w, h, nch, bytes(out)


def write_png(path, w, h, nch, px):
    ctype = {1: 0, 3: 2, 4: 6}[nch]

    def chunk(tag, payload):
        return (struct.pack(">I", len(payload)) + tag + payload
                + struct.pack(">I", zlib.crc32(tag + payload) & 0xFFFFFFFF))

    # Sub filtering (type 1) instead of no filtering: on photographic texture
    # data this shrinks the file by roughly a third, which matters because the
    # atlas goes straight back into a .glb.
    stride = w * nch
    raw = bytearray()
    for y in range(h):
        raw.append(1)
        row = px[y * stride:(y + 1) * stride]
        filtered = bytearray(row)
        for i in range(stride - 1, nch - 1, -1):
            filtered[i] = (row[i] - row[i - nch]) & 0xFF
        raw += filtered

    blob = b"\x89PNG\r\n\x1a\n"
    blob += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, ctype, 0, 0, 0))
    blob += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    blob += chunk(b"IEND", b"")
    open(path, "wb").write(blob)


def crop(px, w, h, nch, x0, y0, x1, y1):
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(w, x1), min(h, y1)
    cw, chh = x1 - x0, y1 - y0
    out = bytearray(cw * chh * nch)
    for y in range(chh):
        src = ((y + y0) * w + x0) * nch
        dst = y * cw * nch
        out[dst:dst + cw * nch] = px[src:src + cw * nch]
    return cw, chh, bytes(out)


def cut_watermark_and_frame(px, w, h, nch, wm_box=None, margin=36, tol=26):
    """Return crop box that drops the watermark corner and centres the subject."""
    def pixel(x, y):
        i = (y * w + x) * nch
        return px[i], px[i + 1], px[i + 2]

    # background estimate: median-ish of the four corners
    corners = [pixel(4, 4), pixel(w - 5, 4), pixel(4, h - 5), pixel(w - 5, h - 5)]
    bg = tuple(sorted(c[k] for c in corners)[1] for k in range(3))

    minx, miny, maxx, maxy = w, h, -1, -1
    for y in range(h):
        row = y * w * nch
        for x in range(w):
            if wm_box and x >= wm_box[0] and y >= wm_box[1]:
                continue
            i = row + x * nch
            if (abs(px[i] - bg[0]) > tol
                    or abs(px[i + 1] - bg[1]) > tol
                    or abs(px[i + 2] - bg[2]) > tol):
                if x < minx:
                    minx = x
                if x > maxx:
                    maxx = x
                if y < miny:
                    miny = y
                if y > maxy:
                    maxy = y
    if maxx < 0:
        raise ValueError("no subject detected")

    return (max(0, minx - margin), max(0, miny - margin),
            min(w, maxx + margin + 1), min(h, maxy + margin + 1))


def paint_out(px, w, h, nch, box):
    """Erase a rectangle by extending the background down from just above it.

    Column-by-column copy of a single clean row keeps any vertical gradient of
    the backdrop intact, so no hard-edged patch is produced (a flat fill would
    leave a visible block that the image-to-3D step may read as geometry).
    """
    x0, y0, x1, y1 = box
    y0, y1 = max(0, y0), min(h, y1)
    x0, x1 = max(0, x0), min(w, x1)
    src_y = max(0, y0 - 6)

    for y in range(y0, y1):
        base = y * w * nch
        for x in range(x0, x1):
            s = (src_y * w + x) * nch
            i = base + x * nch
            px[i:i + nch] = px[s:s + nch]


if __name__ == "__main__":
    src, dst = sys.argv[1], sys.argv[2]
    w, h, nch, px = read_png(src)
    print("src %dx%d ch=%d" % (w, h, nch), file=sys.stderr)

    px = bytearray(px)
    paint_out(px, w, h, nch, (860, 925, w, h))

    box = cut_watermark_and_frame(px, w, h, nch, wm_box=(860, 925))
    print("subject crop box: %s" % (box,), file=sys.stderr)

    cw, chh, cpx = crop(bytes(px), w, h, nch, *box)
    # pad to a square canvas so the subject sits centred for the 3D pipeline
    side = max(cw, chh)
    pad_x, pad_y = (side - cw) // 2, (side - chh) // 2
    canvas = bytearray(side * side * nch)
    bgv = px[0:nch]
    for i in range(side * side):
        canvas[i * nch:(i + 1) * nch] = bgv
    for y in range(chh):
        s = y * cw * nch
        d = ((y + pad_y) * side + pad_x) * nch
        canvas[d:d + cw * nch] = cpx[s:s + cw * nch]

    write_png(dst, side, side, nch, bytes(canvas))
    print("wrote %s %dx%d" % (dst, side, side), file=sys.stderr)
