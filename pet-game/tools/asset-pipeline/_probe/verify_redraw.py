#!/usr/bin/env python3
import numpy as np, bpy
P = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/spliced/img_1_768x768.png'
def load(p):
    im = bpy.data.images.load(p); w,h=im.size
    px=np.empty(w*h*4,dtype=np.float32); im.pixels.foreach_get(px)
    a=px.reshape((h,w,4))[:,:,:3].copy(); bpy.data.images.remove(im); return a
def samp(a, ux, uy, r=6):
    h,w=a.shape[:2]; x=int(ux*w); y=int(uy*h)
    return a[max(0,y-r):y+r, max(0,x-r):x+r].reshape(-1,3).mean(axis=0)
a = load(P)
pts = {
 'EYE1 pupil (0.101,0.25)': (0.101,0.25),
 'EYE1 highlight (-0.34R,-0.30R)': (0.101-0.34*69/768, 0.25-0.30*69/768),
 'EYE1 upper iris (0, -0.5R)': (0.101, 0.25-0.5*69/768),
 'EYE2 pupil (0.588,0.25)': (0.588,0.25),
 'NOSE (0.345,0.135)': (0.345,0.135),
 'FOREHEAD (0.34,0.45)': (0.34,0.45),
 'BLUSH1 (0.101,0.327)': (0.101, 0.327),
 'LOWER-FACE (0.345,0.10)': (0.345,0.10),
}
for k,(ux,uy) in pts.items():
    c = samp(a,ux,uy)
    print(f'  {k:34s} rgb=({c[0]:.3f},{c[1]:.3f},{c[2]:.3f})')
