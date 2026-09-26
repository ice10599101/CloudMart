"""按给定矩形裁切 PNG（纯 stdlib），用于给移动端切竖版构图。"""
import struct
import sys
import zlib


def read_png(path):
    raw = open(path, 'rb').read()
    assert raw[:8] == b'\x89PNG\r\n\x1a\n', 'not a png'
    pos = 8
    idat = bytearray()
    width = height = depth = ctype = None
    while pos < len(raw):
        (length,) = struct.unpack('>I', raw[pos:pos + 4])
        ctag = raw[pos + 4:pos + 8]
        data = raw[pos + 8:pos + 8 + length]
        pos += 12 + length
        if ctag == b'IHDR':
            width, height, depth, ctype = struct.unpack('>IIBB', data[:10])
        elif ctag == b'IDAT':
            idat += data
        elif ctag == b'IEND':
            break
    assert depth == 8 and ctype in (2, 6), f'unsupported {depth}/{ctype}'
    bpp = 3 if ctype == 2 else 4
    stride = width * bpp
    data = zlib.decompress(bytes(idat))
    out = bytearray(height * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        ft = data[p]
        p += 1
        line = bytearray(data[p:p + stride])
        p += stride
        if ft == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif ft == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ft == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif ft == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return width, height, bpp, out


def write_png(path, width, height, bpp, pixels):
    stride = width * bpp
    raw = bytearray()
    for y in range(height):
        line = pixels[y * stride:(y + 1) * stride]
        filtered = bytearray(stride)
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            filtered[i] = (line[i] - a) & 0xFF
        raw.append(1)
        raw += filtered
    ctype = 2 if bpp == 3 else 6

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    out = b'\x89PNG\r\n\x1a\n'
    out += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, ctype, 0, 0, 0))
    out += chunk(b'IDAT', zlib.compress(bytes(raw), 9))
    out += chunk(b'IEND', b'')
    open(path, 'wb').write(out)


SRC, DST = sys.argv[1], sys.argv[2]
x0, y0, x1, y1 = (int(v) for v in sys.argv[3:7])
src_w, src_h, bpp, px = read_png(SRC)
x0 = max(0, min(x0, src_w - 1))
x1 = max(x0 + 1, min(x1, src_w))
y0 = max(0, min(y0, src_h - 1))
y1 = max(y0 + 1, min(y1, src_h))
w, h = x1 - x0, y1 - y0
sstride = src_w * bpp
out = bytearray(w * h * bpp)
for y in range(h):
    s = (y0 + y) * sstride + x0 * bpp
    out[y * w * bpp:(y + 1) * w * bpp] = px[s:s + w * bpp]
write_png(DST, w, h, bpp, out)
print(f'cropped {src_w}x{src_h} -> {w}x{h} (x {x0}..{x1}, y {y0}..{y1}), aspect {w / h:.4f}')
