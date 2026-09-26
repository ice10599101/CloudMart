"""把多张 PNG 拼成网格图（纯 stdlib，零依赖）。

用途：本机这版 Blender 没编 FFMPEG，出不了 MP4，所以动画预览改成
"逐帧渲染 → 拼成序列图"，让人一眼看出动作变化。

用法：
    python png_grid.py <out.png> <cols> <pad> <in1.png> <in2.png> ...
"""
import struct
import sys
import zlib


def read_png(path):
    raw = open(path, 'rb').read()
    assert raw[:8] == b'\x89PNG\r\n\x1a\n', 'not a png: ' + path
    pos, idat = 8, bytearray()
    width = height = depth = ctype = None
    while pos < len(raw):
        (length,) = struct.unpack('>I', raw[pos:pos + 4])
        tag = raw[pos + 4:pos + 8]
        data = raw[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b'IHDR':
            width, height, depth, ctype = struct.unpack('>IIBB', data[:10])
        elif tag == b'IDAT':
            idat += data
        elif tag == b'IEND':
            break
    assert depth == 8 and ctype in (2, 6), 'unsupported %s/%s' % (depth, ctype)
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
        raw.append(1)          # Sub 滤波：照片类纹理比无滤波小很多
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


out_path = sys.argv[1]
cols = int(sys.argv[2])
pad = int(sys.argv[3])
inputs = sys.argv[4:]

imgs = [read_png(p) for p in inputs]
cw = max(i[0] for i in imgs)
ch = max(i[1] for i in imgs)
rows = (len(imgs) + cols - 1) // cols
W = cols * cw + (cols + 1) * pad
H = rows * ch + (rows + 1) * pad
BG = (0xEF, 0xEC, 0xE6)

canvas = bytearray()
for _ in range(W * H):
    canvas += bytes(BG)

for idx, (w, h, bpp, px) in enumerate(imgs):
    r, c = divmod(idx, cols)
    ox = pad + c * (cw + pad)
    oy = pad + r * (ch + pad)
    for y in range(h):
        src = y * w * bpp
        dst = ((oy + y) * W + ox) * 3
        for x in range(w):
            s = src + x * bpp
            canvas[dst + x * 3] = px[s]
            canvas[dst + x * 3 + 1] = px[s + 1]
            canvas[dst + x * 3 + 2] = px[s + 2]

write_png(out_path, W, H, 3, canvas)
print('grid %dx%d (%d imgs, %d cols) -> %s' % (W, H, len(imgs), cols, out_path))
