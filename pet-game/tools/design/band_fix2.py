"""抹掉家园底图里烘死的旧版 HUD（底部按钮/导航药丸）。

做法：把 y=SEAM 这一行做横向大半径模糊得到「地板主色剖面」，
再用它向下填充并渐暗，接缝处与原行做线性过渡，避免出现硬边。
纯 stdlib：PNG 解码 -> 反滤波 -> 填充 -> Sub 滤波重编码。
"""
import struct
import sys
import zlib

SRC = sys.argv[1]
DST = sys.argv[2]
SEAM = int(sys.argv[3])     # 从该行起被替换
BLUR = int(sys.argv[4])     # 横向模糊半径


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
    assert depth == 8 and ctype in (2, 6), f'unsupported depth/ctype: {depth}/{ctype}'
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


width, height, bpp, px = read_png(SRC)
stride = width * bpp
print(f'src {width}x{height} bpp={bpp}')

# 接缝行的原始像素（保持与上方内容的连续性）
seam_row = bytes(px[SEAM * stride:(SEAM + 1) * stride])

# 用接缝以上若干行的平均值做横向大半径模糊，得到"地板主色剖面"
acc = [0] * stride
rows = 0
for y in range(max(0, SEAM - 24), SEAM):
    line = px[y * stride:(y + 1) * stride]
    for i in range(stride):
        acc[i] += line[i]
    rows += 1
avg = [acc[i] // rows for i in range(stride)]

profile = bytearray(stride)
for i in range(stride):
    ch = i % bpp
    lo = max(ch, i - BLUR * bpp)
    hi = min(stride - 1, i + BLUR * bpp)
    step = bpp
    total = 0
    n = 0
    for j in range(lo, hi + 1, step):
        total += avg[j - (j % bpp) + ch]
        n += 1
    profile[i] = total // n

FADE = 90
for y in range(SEAM, height):
    t = (y - SEAM) / max(1, (height - 1 - SEAM))
    smear = min(1.0, (y - SEAM) / FADE)
    darken = 1.0 - 0.22 * t
    row = bytearray(stride)
    for i in range(stride):
        base = int(profile[i] * darken)
        seam = seam_row[i]
        row[i] = int(seam + (base - seam) * smear) & 0xFF
    px[y * stride:(y + 1) * stride] = row

print(f'filled rows {SEAM}..{height - 1}, blur r={BLUR}px, fade={FADE}rows')
write_png(DST, width, height, bpp, px)
print('wrote', DST)
