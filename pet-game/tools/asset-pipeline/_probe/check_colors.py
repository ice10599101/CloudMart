#!/usr/bin/env python3
"""Sample mean RGB and eye-center RGB of each extracted PNG to classify albedo vs normal."""
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
    # eye centers from previous detection on img_1
    eyes = [(0.101, 0.250), (0.588, 0.250)]
    for k in range(3):
        path = f'{ROOT}/img_{k}_1024x1024.png'
        arr = load(path)
        h, w = arr.shape[:2]
        mean = arr.reshape(-1, 3).mean(axis=0)
        print(f'\n===== img_{k} mean RGB = ({mean[0]:.3f},{mean[1]:.3f},{mean[2]:.3f}) '
              f'R-B={mean[0]-mean[2]:.3f} =====')
        # global hue distribution
        rg = (arr[:,:,0]-arr[:,:,1]); rb = (arr[:,:,0]-arr[:,:,2]); gb = (arr[:,:,1]-arr[:,:,2])
        print(f'  R>G frac={ (rg>0.1).mean():.3f}  R>B frac={ (rb>0.1).mean():.3f}  G>B frac={ (gb>0.1).mean():.3f}')
        if k == 1:
            for (ux, uy) in eyes:
                x = int(ux*w); y = int(uy*h)
                patch = arr[max(0,y-6):y+6, max(0,x-6):x+6].reshape(-1,3)
                pmean = patch.mean(axis=0)
                pmin = patch.min(axis=0); pmax = patch.max(axis=0)
                print(f'  EYE uv=({ux},{uy}) mean=({pmean[0]:.3f},{pmean[1]:.3f},{pmean[2]:.3f}) '
                      f'min=({pmin[0]:.3f},{pmin[1]:.3f},{pmin[2]:.3f}) max=({pmax[0]:.3f},{pmax[1]:.3f},{pmax[2]:.3f})')
            # forehead sample (above eyes)
            fx, fy = int(0.34*w), int(0.45*h)
            fp = arr[fy-6:fy+6, fx-6:fx+6].reshape(-1,3).mean(axis=0)
            print(f'  FOREHEAD uv=(0.34,0.45) mean=({fp[0]:.3f},{fp[1]:.3f},{fp[2]:.3f})')

if __name__ == '__main__':
    main()
