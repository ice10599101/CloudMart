#!/usr/bin/env python3
"""
alive_redraw_png.py — redraw the body albedo (img_1) to make the cat livelier.
Input : extracted albedo PNG (1024x1024)
Output: redrawn PNG (same size, sRGB) for splicing back into the GLB.

Layout learned from numeric probes:
  eyes at uv (0.101,0.250) & (0.588,0.250)  (dark pupils)
  face normally oriented (pink inner-ear region at v~0.50-0.55 = top of head)
  => nose/muzzle belong BELOW the eyes (~v 0.12-0.16); no nose painted today.
"""
import sys, math
import numpy as np
import bpy

IN = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/deployed/img_1_1024x1024.png'
OUT = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/redraw.png'
DEBUG = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/redraw_debug.png'

KNOWN_EYES = [(0.101, 0.250), (0.588, 0.250)]

def rgb(r, g, b):
    return np.array([r/255.0, g/255.0, b/255.0], dtype=np.float32)

def draw_soft_circle(img, cx, cy, r_out, r_in, color, alpha=1.0):
    h, w = img.shape[:2]
    if cx < -r_out or cy < -r_out or cx > w + r_out or cy > h + r_out:
        return
    yy, xx = np.ogrid[:h, :w]
    d = np.sqrt((xx - cx)**2 + (yy - cy)**2)
    mask = np.clip((r_out - d) / max(r_out - r_in, 1e-3), 0, 1) * alpha
    for c in range(3):
        img[:, :, c] = img[:, :, c] * (1 - mask) + color[c] * mask

def detect_eyes(arr):
    h, w = arr.shape[:2]
    lum = 0.299*arr[:,:,0] + 0.587*arr[:,:,1] + 0.114*arr[:,:,2]
    thr = np.percentile(lum, 2.0)
    mask = lum < thr
    visited = np.zeros_like(mask)
    comps = []
    for sy in range(h):
        for sx in range(w):
            if not mask[sy, sx] or visited[sy, sx]:
                continue
            stack = [(sy, sx)]; visited[sy, sx] = True; pts = []
            while stack:
                y, x = stack.pop(); pts.append((y, x))
                for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    ny, nx = y+dy, x+dx
                    if 0<=ny<h and 0<=nx<w and mask[ny,nx] and not visited[ny,nx]:
                        visited[ny,nx]=True; stack.append((ny,nx))
            if len(pts) < 20:
                continue
            ys, xs = zip(*pts)
            comps.append({'n': len(pts), 'cx': np.mean(xs), 'cy': np.mean(ys)})
    # keep only face-region components (eyes sit around v 0.15-0.35, x 0.05-0.65)
    face = [c for c in comps if (0.12*h < c['cy'] < 0.38*h) and (0.04*w < c['cx'] < 0.66*w)]
    face.sort(key=lambda c: c['n'], reverse=True)
    best = None
    for i in range(min(len(face),6)):
        for j in range(i+1, min(len(face),6)):
            a, b = face[i], face[j]
            dx = abs(a['cx']-b['cx']); dy = abs(a['cy']-b['cy'])
            if dy > h*0.12 or dx < w*0.10:
                continue
            score = (a['n']+b['n']) * (1 - dy/(h*0.2)) * (dx/w)
            if best is None or score > best[0]:
                best = (score, a, b)
    if best is None:
        return [(x*w, y*h) for (x, y) in KNOWN_EYES]
    a, b = best[1], best[2]
    L = min(a, b, key=lambda c: c['cx']); R = max(a, b, key=lambda c: c['cx'])
    print(f'[redraw] detected eyes L=({L["cx"]/w:.3f},{L["cy"]/h:.3f}) R=({R["cx"]/w:.3f},{R["cy"]/h:.3f})')
    return [(L['cx'], L['cy']), (R['cx'], R['cy'])]

def draw_eye(img, cx, cy, R):
    # faint sclera
    draw_soft_circle(img, cx, cy, R*1.05, R*0.86, rgb(250,244,236), 0.22)
    # amber iris
    draw_soft_circle(img, cx, cy, R*0.80, R*0.40, rgb(230,150,45), 0.98)
    # radial iris shading (brighter center)
    draw_soft_circle(img, cx, cy, R*0.40, R*0.10, rgb(245,180,90), 0.35)
    # dark pupil
    draw_soft_circle(img, cx, cy, R*0.44, R*0.18, rgb(35,24,18), 0.99)
    # upper eyelid line (thin dark arc over top of eye)
    h, w = img.shape[:2]
    lid_y = cy - R*0.78
    for y in range(max(0,int(lid_y-R*0.10)), min(h,int(lid_y+R*0.10))):
        for x in range(max(0,int(cx-R*0.86)), min(w,int(cx+R*0.86))):
            dy = (y-lid_y)/(R*0.20+1e-3); dx = (x-cx)/(R*0.86+1e-3)
            if abs(dx)<=1 and -0.2<=dy<=0.8:
                fall = max(0, 1-dx*dx-dy*dy)
                img[y,x] = img[y,x]*(1-fall*0.30) + rgb(110,80,70)*(fall*0.30)
    # wet highlights
    draw_soft_circle(img, cx-R*0.34, cy-R*0.30, R*0.24, R*0.10, rgb(255,255,255), 0.95)
    draw_soft_circle(img, cx+R*0.20, cy+R*0.18, R*0.10, R*0.04, rgb(255,255,255), 0.85)

def draw_face(img, eyes, R):
    h, w = img.shape[:2]
    # blush under each eye
    for (ex, ey) in eyes:
        draw_soft_circle(img, ex, ey+R*0.85, R*0.78, R*0.40, rgb(255,175,180), 0.15)
    # nose (pink) below eye midline
    nx = (eyes[0][0]+eyes[1][0])/2.0
    ny = min(eyes[0][1], eyes[1][1]) - R*1.25
    draw_soft_circle(img, nx, ny, R*0.52, R*0.22, rgb(255,150,165), 0.85)
    draw_soft_circle(img, nx-R*0.12, ny-R*0.14, R*0.16, R*0.06, rgb(255,235,235), 0.85)
    # smile mouth: two small downward-curving arcs below nose
    my = ny - R*0.55
    for sgn in (-1, 1):
        mx = nx + sgn*R*0.42
        for t in np.linspace(-0.5, 0.5, 24):
            px = mx + t*R*0.42
            py = my + (t*t)*R*0.30
            draw_soft_circle(img, px, py, R*0.10, R*0.03, rgb(120,90,80), 0.55)

def resize_area(src, T):
    """Area-average downscale of an (H,W,3) float image to (T,T)."""
    H, W = src.shape[:2]
    out = np.zeros((T, T, 3), dtype=np.float32)
    for ty in range(T):
        y0 = int(ty * H / T); y1 = max(y0 + 1, int((ty + 1) * H / T))
        for tx in range(T):
            x0 = int(tx * W / T); x1 = max(x0 + 1, int((tx + 1) * W / T))
            out[ty, tx] = src[y0:y1, x0:x1].mean(axis=(0, 1))
    return out

def main():
    img = bpy.data.images.load(IN)
    w, h = img.size
    px = np.empty(w*h*4, dtype=np.float32)
    img.pixels.foreach_get(px)
    arr = px.reshape((h, w, 4))[:, :, :3].copy()
    bpy.data.images.remove(img)

    # downscale to 768 so the re-encoded PNG fits the GLB bufferView slot
    if w > 768:
        arr = resize_area(arr, 768)
        w = h = 768

    eyes_px = detect_eyes(arr)
    # clamp eyes to safe radius (left eye at x=0.101 limits R)
    Ruv = 0.090
    R_px = Ruv * min(w, h)
    # ensure left eye fits
    left_x_uv = min(eyes_px[0][0], eyes_px[1][0]) / w
    Ruv = min(Ruv, (left_x_uv - 0.005) / 1.06)
    R_px = Ruv * min(w, h)
    print(f'[redraw] Ruv={Ruv:.4f} R_px={R_px:.1f}')

    for (ex, ey) in eyes_px:
        draw_eye(arr, ex, ey, R_px)
    draw_face(arr, eyes_px, R_px)

    # save redraw
    out = np.empty((h, w, 4), dtype=np.float32)
    out[:, :, :3] = np.clip(arr[:, :, :3], 0, 1)
    out[:, :, 3] = 1.0
    oi = bpy.data.images.new('Redraw', width=w, height=h, alpha=True)
    oi.pixels.foreach_set(out.ravel())
    oi.filepath_raw = OUT
    oi.file_format = 'PNG'
    oi.save()
    oi.filepath_raw = DEBUG
    oi.save()
    bpy.data.images.remove(oi)
    import os
    print(f'[redraw] saved {OUT} size={os.path.getsize(OUT)}')

if __name__ == '__main__':
    main()
