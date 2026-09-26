#!/usr/bin/env python3
"""Load each extracted PNG, run eye detection, print diagnostics to stdout."""
import sys, math
import numpy as np
import bpy

ROOT = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/deployed'

def detect_eyes_on_texture(arr):
    h, w = arr.shape[:2]
    lum = 0.299*arr[:,:,0] + 0.587*arr[:,:,1] + 0.114*arr[:,:,2]
    threshold = np.percentile(lum, 2.0)
    mask = lum < threshold
    visited = np.zeros_like(mask)
    comps = []
    for sy in range(h):
        for sx in range(w):
            if not mask[sy, sx] or visited[sy, sx]:
                continue
            stack = [(sy, sx)]
            visited[sy, sx] = True
            pts = []
            while stack:
                y, x = stack.pop()
                pts.append((y, x))
                for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    ny, nx = y+dy, x+dx
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not visited[ny, nx]:
                        visited[ny, nx] = True
                        stack.append((ny, nx))
            if len(pts) < 20:
                continue
            ys, xs = zip(*pts)
            cy, cx = np.mean(ys), np.mean(xs)
            r = math.sqrt(len(pts) / math.pi) / min(w, h)
            comps.append({'n': len(pts), 'cx': cx, 'cy': cy, 'r': r})
    return comps, lum, threshold

def main():
    for k in range(3):
        path = f'{ROOT}/img_{k}_1024x1024.png'
        try:
            img = bpy.data.images.load(path)
        except Exception as e:
            print(f'[analyze] img_{k} load failed: {e}')
            continue
        w, h = img.size
        px = np.empty(w*h*4, dtype=np.float32)
        img.pixels.foreach_get(px)
        arr = px.reshape((h, w, 4))[:, :, :3].copy()
        bpy.data.images.remove(img)
        lum = 0.299*arr[:,:,0] + 0.587*arr[:,:,1] + 0.114*arr[:,:,2]
        print(f'\n===== img_{k} ({w}x{h}) =====')
        print(f'  lum mean={lum.mean():.3f} std={lum.std():.3f} '
              f'p2={np.percentile(lum,2):.3f} p50={np.percentile(lum,50):.3f} p98={np.percentile(lum,98):.3f}')
        # hue hint: where is redness (scarf / nose)?
        red_dom = (arr[:,:,0] - arr[:,:,2]) > 0.12
        print(f'  reddish pixels frac={red_dom.mean():.3f}')
        comps, _, thr = detect_eyes_on_texture(arr)
        print(f'  dark(<p2={thr:.3f}) components: {len(comps)}')
        for c in sorted(comps, key=lambda x: x['n'], reverse=True)[:8]:
            # report uv (x right, y up)
            print(f"    n={c['n']:.0f} uv=({c['cx']/w:.3f},{c['cy']/h:.3f}) r={c['r']:.4f}")

if __name__ == '__main__':
    main()
