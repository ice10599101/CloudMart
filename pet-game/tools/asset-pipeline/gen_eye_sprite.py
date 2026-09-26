#!/usr/bin/env python3
"""生成卡通大眼睛 sprite PNG（透明底），用于 Cocos billboard eye。"""
import numpy as np
import bpy


def rgb(r, g, b):
    return np.array([r/255.0, g/255.0, b/255.0], dtype=np.float32)


def draw_circle(img, cx, cy, r, color, alpha=1.0):
    h, w = img.shape[:2]
    yy, xx = np.ogrid[:h, :w]
    d = np.sqrt((xx - cx)**2 + (yy - cy)**2)
    mask = np.clip(r - d, 0, 1)
    mask = mask * alpha
    img[:, :, :3] = img[:, :, :3] * (1 - mask[:, :, None]) + color * mask[:, :, None]
    img[:, :, 3] = np.maximum(img[:, :, 3], mask)


def draw_soft_circle(img, cx, cy, r_outer, r_inner, color, alpha=1.0):
    h, w = img.shape[:2]
    yy, xx = np.ogrid[:h, :w]
    d = np.sqrt((xx - cx)**2 + (yy - cy)**2)
    mask = np.clip((r_outer - d) / max(r_outer - r_inner, 1e-3), 0, 1)
    mask = mask * alpha
    img[:, :, :3] = img[:, :, :3] * (1 - mask[:, :, None]) + color * mask[:, :, None]
    img[:, :, 3] = np.maximum(img[:, :, 3], mask)


def main():
    out = 'D:/Ide/IdeaProjects/CloudMart/pet-game/assets/resources/textures/eye_sprite.png'
    size = 512
    arr = np.zeros((size, size, 4), dtype=np.float32)
    cx, cy = size / 2.0, size / 2.0
    r = size * 0.42

    # 白眼底
    draw_soft_circle(arr, cx, cy, r * 1.05, r * 0.85, rgb(255, 248, 240), 0.95)
    # 虹膜
    draw_soft_circle(arr, cx, cy, r * 0.78, r * 0.40, rgb(230, 150, 45), 0.98)
    # 瞳孔
    draw_soft_circle(arr, cx, cy, r * 0.42, r * 0.22, rgb(35, 24, 18), 0.99)
    # 上眼睑阴影（细弧）
    lid_y = cy - r * 0.72
    for y in range(size):
        for x in range(size):
            dy = (y - lid_y) / (r * 0.22)
            dx = (x - cx) / (r * 0.82)
            if abs(dx) <= 1 and -0.3 <= dy <= 1.0:
                fall = max(0, 1 - dx*dx - dy*dy)
                col = rgb(140, 105, 90)
                arr[y, x, :3] = arr[y, x, :3] * (1 - fall*0.30) + col * (fall*0.30)
                arr[y, x, 3] = max(arr[y, x, 3], fall * 0.30)
    # 高光
    draw_soft_circle(arr, cx - r*0.32, cy - r*0.26, r*0.22, r*0.09, rgb(255, 255, 255), 0.98)
    draw_soft_circle(arr, cx + r*0.20, cy + r*0.18, r*0.10, r*0.04, rgb(255, 255, 255), 0.88)

    img = bpy.data.images.new('EyeSprite', width=size, height=size, alpha=True)
    img.pixels.foreach_set(arr.ravel())
    img.filepath_raw = out
    img.file_format = 'PNG'
    img.save()
    bpy.data.images.remove(img)
    print(f'saved {out}')


if __name__ == '__main__':
    main()
