#!/usr/bin/env python3
import numpy as np, bpy
PATHS = [
 'D:/Ide/IdeaProjects/CloudMart/pet-game/shots/v15/room.png',
 'D:/Ide/IdeaProjects/CloudMart/pet-game/shots/v15/front.png',
]
for path in PATHS:
    im = bpy.data.images.load(path); w,h=im.size
    px=np.empty(w*h*4,dtype=np.float32); im.pixels.foreach_get(px)
    a=px.reshape((h,w,4))[:,:,:3]
    bpy.data.images.remove(im)
    lum = a[:,:,0]*0.299+a[:,:,1]*0.587+a[:,:,2]*0.114
    print(f'{path.split("/")[-1]}: {w}x{h} meanRGB=({a[:,:,0].mean():.3f},{a[:,:,1].mean():.3f},{a[:,:,2].mean():.3f}) lum_mean={lum.mean():.3f} lum_std={lum.std():.3f}')
