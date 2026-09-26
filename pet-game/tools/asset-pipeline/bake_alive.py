#!/usr/bin/env python3
"""
bake_alive.py — 把"生动感"重绘烘进宠物 GLB 的 body albedo（一次性、可复现）。

根因修复（对照上一版白猫事故）：
  上一版 splice_glb.py 在 PNG 的 IEND *之后* 用零字节补满槽位，而引擎按
  bufferView.byteLength 读满字节再交 PNG 解码器 —— 尾部零被当成坏数据，
  整张贴图退化成白板 → 整只猫白。
  本版改为在 PNG *内部* 插入一个私有 ancillary 区块（prVw，IEND 之前）凑到
  恰好槽位长度；合法 PNG，解码器读到 IEND 即止、忽略它，白猫不再发生。

实现要点：
  - 纯 stdlib 读/写 PNG（8bit, colortype 2/6, 自适应每行 filter），不经过
    Blender 的色彩管理，保证 sRGB 字节逐字节保真。
  - numpy 只做绘图数学；用 Blender 自带的 numpy 即可（python 解释器直接跑）。
  - 重绘在归一化 UV 坐标下进行，与目标分辨率无关，避免降采样糊掉眼睛。

输入 : _probe/deployed/img_1_1024x1024.png（从 .bak 抽出的干净 body albedo，RGB）
输出 : 重绘后烘回 assets/resources/models/cat/pet-cat-rigged.glb（先备份为 .bak2）
"""
import sys, struct, zlib, os, shutil
import numpy as np

CLEAN  = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/deployed/img_1_1024x1024.png'
DEPLOY = 'D:/Ide/IdeaProjects/CloudMart/pet-game/assets/resources/models/cat/pet-cat-rigged.glb'
OFF    = 742520          # body albedo 槽位在 GLB 内的绝对文件偏移
LENGTH = 1155761        # 槽位长度（bufferView.byteLength）
KNOWN_EYES = [(0.101, 0.250), (0.588, 0.250)]   # 干净 albedo 中眼睛的 UV

# ----------------------------------------------------------------------------
# 纯 stdlib PNG 编解码（8bit, colortype 2/6, 非交错）
# ----------------------------------------------------------------------------
def _paeth(a, b, c):
    p = a + b - c
    pa = abs(p - a); pb = abs(p - b); pc = abs(p - c)
    if pa <= pb and pa <= pc: return a
    if pb <= pc: return b
    return c

def _paeth_vec(a, b, c):
    p = a + b - c
    pa = np.abs(p - a); pb = np.abs(p - b); pc = np.abs(p - c)
    return np.where((pa <= pb) & (pa <= pc), a, np.where(pb <= pc, b, c)).astype(np.int32)

def png_decode(data):
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a PNG'
    pos = 8; width = height = bitdepth = colortype = None; idat = b''
    while pos < len(data):
        length = struct.unpack('>I', data[pos:pos+4])[0]
        ctype = data[pos+4:pos+8]
        cdata = data[pos+8:pos+8+length]
        if ctype == b'IHDR':
            width, height, bitdepth, colortype, comp, filt, inter = struct.unpack('>IIBBBBB', cdata[:13])
        elif ctype == b'IDAT':
            idat += cdata
        elif ctype == b'IEND':
            break
        pos += 12 + length
    assert bitdepth == 8, f'unsupported bitdepth {bitdepth}'
    assert colortype in (2, 6), f'unsupported colortype {colortype}'
    raw = zlib.decompress(idat)
    ch = 3 if colortype == 2 else 4
    stride = width * ch
    out = bytearray(); prev = bytearray(stride); p = 0
    for _ in range(height):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if f == 1:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                line[i] = (line[i] + a) & 0xff
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xff
        elif f == 3:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xff
        elif f == 4:
            for i in range(stride):
                a = line[i-ch] if i >= ch else 0
                c = prev[i-ch] if i >= ch else 0
                line[i] = (line[i] + _paeth(a, prev[i], c)) & 0xff
        out += line; prev = line
    arr = np.frombuffer(out, dtype=np.uint8).reshape(height, width, ch).astype(np.float32) / 255.0
    return width, height, colortype, arr

def _chunk(typ, data):
    return struct.pack('>I', len(data)) + typ + data + struct.pack('>I', zlib.crc32(typ + data) & 0xffffffff)

def _encode_raw(img):
    """img: uint8 (h,w,ch). 逐行选最优 filter，返回待压缩字节。"""
    h, w, ch = img.shape
    cur = img.reshape(h, w*ch).astype(np.int32)
    prev = np.zeros(w*ch, dtype=np.int32)
    raw = bytearray()
    for y in range(h):
        c = cur[y]
        a = np.zeros_like(c); a[ch:] = c[:-ch]
        b = prev
        cc = np.zeros_like(prev); cc[ch:] = prev[:-ch]
        f0 = c
        f1 = (c - a) & 0xff
        f2 = (c - b) & 0xff
        avg = ((a + b) >> 1) & 0xff
        f3 = (c - avg) & 0xff
        pc = _paeth_vec(a, b, cc)
        f4 = (c - pc) & 0xff
        s = [int(np.abs(x).sum()) for x in (f0, f1, f2, f3, f4)]
        k = int(np.argmin(s))
        ff = (f0, f1, f2, f3, f4)[k].astype(np.uint8)
        raw.append(k); raw += ff.tobytes()
        prev = c
    return bytes(raw)

def png_encode(arr, colortype=2, pad_to=None):
    """arr: float (h,w,ch) 0..1. 若 pad_to 给定，在 IEND 前插入 prVw 补零使其总长 = pad_to。"""
    assert arr.dtype == np.float32 or arr.dtype == np.float64
    h, w, ch = arr.shape
    assert ch in (3, 4) and colortype in (2, 6) and ch == (3 if colortype == 2 else 4)
    u8 = np.clip(arr, 0, 1) * 255.0 + 0.5
    u8 = u8.astype(np.uint8)
    comp = zlib.compress(_encode_raw(u8), 9)
    png = b'\x89PNG\r\n\x1a\n' + _chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, colortype, 0, 0, 0)) \
                            + _chunk(b'IDAT', comp)
    if pad_to is not None:
        pad_len = pad_to - len(png) - 12   # IEND 自身 12 字节
        assert pad_len >= 0, f'image too big by {-pad_len} bytes'
        if pad_len > 0:
            png += _chunk(b'prVw', b'\x00' * pad_len)
    png += _chunk(b'IEND', b'')
    return png

# ----------------------------------------------------------------------------
# 绘图（归一化 UV 坐标，与目标分辨率无关）
# ----------------------------------------------------------------------------
def rgb(r, g, b):
    return np.array([r/255.0, g/255.0, b/255.0], dtype=np.float32)

def _circle_uv(img, ux, uy, r_out, r_in, color, alpha=1.0):
    h, w = img.shape[:2]; M = min(w, h)
    cx, cy = ux * w, uy * h
    ro, ri = r_out * M, r_in * M
    if cx < -ro or cy < -ro or cx > w + ro or cy > h + ro:
        return
    yy, xx = np.ogrid[:h, :w]
    d = np.sqrt((xx - cx)**2 + (yy - cy)**2)
    mask = np.clip((ro - d) / max(ro - ri, 1e-3), 0, 1) * alpha
    for c in range(3):
        img[:, :, c] = img[:, :, c] * (1 - mask) + color[c] * mask

def detect_eyes_uv(arr):
    h, w = arr.shape[:2]
    lum = 0.299*arr[:,:,0] + 0.587*arr[:,:,1] + 0.114*arr[:,:,2]
    thr = np.percentile(lum, 2.0)
    mask = lum < thr
    visited = np.zeros_like(mask); comps = []
    for sy in range(h):
        for sx in range(w):
            if not mask[sy, sx] or visited[sy, sx]:
                continue
            stack = [(sy, sx)]; visited[sy, sx] = True; pts = []
            while stack:
                y, x = stack.pop(); pts.append((y, x))
                for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    ny, nx = y+dy, x+dx
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not visited[ny, nx]:
                        visited[ny, nx] = True; stack.append((ny, nx))
            if len(pts) < 20: continue
            ys, xs = zip(*pts); comps.append({'n': len(pts), 'cx': np.mean(xs), 'cy': np.mean(ys)})
    face = [c for c in comps if (0.12*h < c['cy'] < 0.38*h) and (0.04*w < c['cx'] < 0.66*w)]
    face.sort(key=lambda c: c['n'], reverse=True); best = None
    for i in range(min(len(face), 6)):
        for j in range(i+1, min(len(face), 6)):
            a, b = face[i], face[j]; dx = abs(a['cx']-b['cx']); dy = abs(a['cy']-b['cy'])
            if dy > h*0.12 or dx < w*0.10: continue
            score = (a['n']+b['n']) * (1 - dy/(h*0.2)) * (dx/w)
            if best is None or score > best[0]: best = (score, a, b)
    if best is None:
        return [(x, y) for x, y in KNOWN_EYES]
    a, b = best[1], best[2]
    L = min(a, b, key=lambda c: c['cx']); R = max(a, b, key=lambda c: c['cx'])
    print(f'[bake] eyes L=({L["cx"]/w:.3f},{L["cy"]/h:.3f}) R=({R["cx"]/w:.3f},{R["cy"]/h:.3f})')
    return [(L['cx']/w, L['cy']/h), (R['cx']/w, R['cy']/h)]

def draw_eye_uv(img, ux, uy, Ruv):
    _circle_uv(img, ux, uy, Ruv*1.05, Ruv*0.86, rgb(250,244,236), 0.22)
    _circle_uv(img, ux, uy, Ruv*0.80, Ruv*0.40, rgb(230,150,45), 0.98)
    _circle_uv(img, ux, uy, Ruv*0.40, Ruv*0.10, rgb(245,180,90), 0.35)
    _circle_uv(img, ux, uy, Ruv*0.44, Ruv*0.18, rgb(35,24,18), 0.99)
    h, w = img.shape[:2]; M = min(w, h)
    cx, cy = ux*w, uy*h; ro = Ruv*M
    lid_y = cy - ro*0.78
    for y in range(max(0, int(lid_y-ro*0.10)), min(h, int(lid_y+ro*0.10))):
        for x in range(max(0, int(cx-ro*0.86)), min(w, int(cx+ro*0.86))):
            dy = (y - lid_y) / (ro*0.20 + 1e-3); dx = (x - cx) / (ro*0.86 + 1e-3)
            if abs(dx) <= 1 and -0.2 <= dy <= 0.8:
                fall = max(0, 1 - dx*dx - dy*dy)
                img[y, x] = img[y, x]*(1 - fall*0.30) + rgb(110,80,70)*(fall*0.30)
    _circle_uv(img, ux - Ruv*0.34, uy - Ruv*0.30, Ruv*0.24, Ruv*0.10, rgb(255,255,255), 0.95)
    _circle_uv(img, ux + Ruv*0.20, uy + Ruv*0.18, Ruv*0.10, Ruv*0.04, rgb(255,255,255), 0.85)

def draw_face_uv(img, eyes, Ruv):
    for (ex, ey) in eyes:
        _circle_uv(img, ex, ey + Ruv*0.85, Ruv*0.78, Ruv*0.40, rgb(255,175,180), 0.15)
    nx = (eyes[0][0] + eyes[1][0]) / 2.0; ny = min(eyes[0][1], eyes[1][1]) - Ruv*1.25
    _circle_uv(img, nx, ny, Ruv*0.52, Ruv*0.22, rgb(255,150,165), 0.85)
    _circle_uv(img, nx - Ruv*0.12, ny - Ruv*0.14, Ruv*0.16, Ruv*0.06, rgb(255,235,235), 0.85)
    my = ny - Ruv*0.55
    for sgn in (-1, 1):
        mx = nx + sgn*Ruv*0.42
        for t in np.linspace(-0.5, 0.5, 24):
            px = mx + t*Ruv*0.42; py = my + (t*t)*Ruv*0.30
            _circle_uv(img, px, py, Ruv*0.10, Ruv*0.03, rgb(120,90,80), 0.55)

def resize_area(src, T):
    H, W = src.shape[:2]; out = np.zeros((T, T, 3), dtype=np.float32)
    for ty in range(T):
        y0 = int(ty*H/T); y1 = max(y0+1, int((ty+1)*H/T))
        for tx in range(T):
            x0 = int(tx*W/T); x1 = max(x0+1, int((tx+1)*W/T))
            out[ty, tx] = src[y0:y1, x0:x1].mean(axis=(0, 1))
    return out

# ----------------------------------------------------------------------------
def main():
    w0, h0, ct0, arr = png_decode(open(CLEAN, 'rb').read())
    arr = np.flipud(arr)   # 转成 glTF 空间：row0 = v=0(底部)，后续绘制直接用 glTF uv
    print(f'[bake] source {w0}x{h0} colortype={ct0}')
    # KNOWN_EYES 是前期按 3D 网格 UV 反查得到的 glTF uv（v 向上）。
    # 本解码器 arr[0] 是 PNG 顶（= glTF v=1），所以绘制在 glTF 空间进行、最后 flipud 写回 PNG 朝向。
    eyes = [(x, y) for x, y in KNOWN_EYES]
    print(f'[bake] eyes(forced, glTF uv) L={eyes[0]} R={eyes[1]}')
    Ruv = 0.090
    left_x = min(eyes[0][0], eyes[1][0])
    Ruv = min(Ruv, (left_x - 0.005) / 1.06)
    print(f'[bake] Ruv={Ruv:.4f}')

    # 自适应分辨率：从 1024 往下，直到 PNG（不含内部填充）能塞进 LENGTH-12
    T = 1024; final_png = None; chosen_base = None
    while True:
        base = resize_area(arr, T) if T < w0 else arr.copy()
        for (ex, ey) in eyes: draw_eye_uv(base, ex, ey, Ruv)
        draw_face_uv(base, eyes, Ruv)
        png = png_encode(np.flipud(base), colortype=2)   # 写回 PNG 朝向（row0=顶）
        if len(png) <= LENGTH - 12 or T <= 512:
            print(f'[bake] final res={T} png={len(png)} (slot {LENGTH})')
            final_png = png; chosen_base = np.flipud(base); break
        T -= 64

    dbg = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/redraw_debug.png'
    open(dbg, 'wb').write(final_png)
    padded = png_encode(chosen_base, colortype=2, pad_to=LENGTH)
    print(f'[bake] padded png = {len(padded)} (target {LENGTH})')

    shutil.copy2(DEPLOY, DEPLOY + '.bak2')
    glb = bytearray(open(DEPLOY, 'rb').read())
    assert glb[OFF:OFF+8] == b'\x89PNG\r\n\x1a\n', 'slot head not a PNG'
    glb[OFF:OFF+LENGTH] = padded
    open(DEPLOY, 'wb').write(glb)
    print(f'[bake] spliced -> {DEPLOY} ({len(glb)} bytes)')

    # 自检：把刚写进去的 PNG 抽出来重新解码，确认不是白板
    ew, eh, ect, earr = png_decode(bytes(glb[OFF:OFF+LENGTH]))
    mean = earr.reshape(-1, 3).mean(axis=0)
    print(f'[bake] re-decoded embedded {ew}x{eh} ct={ect} mean RGB={mean.round(3)}')
    lum = 0.299*earr[:,:,0] + 0.587*earr[:,:,1] + 0.114*earr[:,:,2]
    print(f'[bake] luminance std={lum.std():.3f} (white板 std≈0, 正常奶油色有纹理)')

if __name__ == '__main__':
    main()
