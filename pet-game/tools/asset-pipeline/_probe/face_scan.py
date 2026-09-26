#!/usr/bin/env python3
"""Scan the face column/region of img_1 to learn vertical layout (eyes vs muzzle)."""
import numpy as np
import bpy

ROOT = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/deployed'

def load(path):
    img = bpy.data.images.load(path)
    w, h = img.size
    px = np.empty(w*h*4, dtype=np.float32)
    img.pixels.foreach_get(px)
    arr = px.reshape((h, w, 4))[:, :, :3].copy()
    bpy.data.images.remove(img)
    return arr

def main():
    arr = load(f'{ROOT}/img_1_1024x1024.png')
    h, w = arr.shape[:2]
    # vertical strip at face center x=0.345
    xc = int(0.345*w)
    print('=== vertical strip x=0.345 (v from 0.05 -> 0.55) ===')
    for v in np.arange(0.05, 0.56, 0.05):
        y = int(v*h)
        patch = arr[max(0,y-8):y+8, xc-8:xc+8].reshape(-1,3)
        m = patch.mean(axis=0)
        print(f'  v={v:.2f} rgb=({m[0]:.3f},{m[1]:.3f},{m[2]:.3f})')
    # look for pinkish (nose) and dark (mouth) pixels in lower face region
    print('=== lower-face scan (v 0.02..0.24, x 0.20..0.50) ===')
    lo = int(0.02*h); hi = int(0.24*h); xl = int(0.20*w); xr = int(0.50*w)
    reg = arr[lo:hi, xl:xr]
    rg = reg[:,:,0]-reg[:,:,1]; rb = reg[:,:,0]-reg[:,:,2]
    pink = (rb>0.18) & (rg<0.05) & (reg[:,:,0]>0.4)
    dark = (reg.mean(axis=2) < 0.30)
    ys, xs = np.where(pink)
    print(f'  pinkish(nose?) pixels frac={pink.mean():.4f}  count={pink.sum()}')
    if pink.sum()>0:
        print(f'    pink centroid uv=({xs.mean()/reg.shape[1]+0.20:.3f},{ys.mean()/reg.shape[0]+0.02:.3f})')
    print(f'  dark(mouth?) pixels frac={dark.mean():.4f} count={dark.sum()}')
    # upper-face above eyes: is there brow/forehead vs top of head
    print('=== above-eyes scan (v 0.26..0.50, x 0.05..0.65) ===')
    lo2=int(0.26*h); hi2=int(0.50*h); xl2=int(0.05*w); xr2=int(0.65*w)
    reg2 = arr[lo2:hi2, xl2:xr2]
    print(f'  mean rgb=({reg2[:,:,0].mean():.3f},{reg2[:,:,1].mean():.3f},{reg2[:,:,2].mean():.3f})')

if __name__ == '__main__':
    main()
