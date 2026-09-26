"""QQ 猫贴图暖杏色校色：贴图比官方参考白（生成概念漂白），在贴图层面
做饱和度提升 + 暖色偏移，一次校正"惨白"。
运行： blender --background --python tint_qqcat.py -- <in.glb> <out.glb>
"""
import os
import sys

import bpy
import numpy as np

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
SRC, DST = argv[0], argv[1]

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=SRC)

SAT = 1.45        # 饱和度增益
WARM = (0.96, 0.85, 0.74)   # 暖杏色乘法偏移（对齐官方猫 #E8D3B6）

for img in bpy.data.images:
    w, h = img.size
    if w == 0 or h == 0 or w * h * 4 == 0:
        continue
    # ⚠️ 只校 baseColor：法线/金属粗糙度贴图是**方向与参数数据**，
    # 当颜色调会破坏着色（已实测：法线暖移后高光错乱）
    low = img.name.lower()
    if 'normal' in low or 'metallic' in low or 'roughness' in low:
        print('[tint] 跳过非颜色贴图 %s' % img.name)
        continue
    px = np.empty(w * h * 4, dtype=np.float32)
    try:
        img.pixels.foreach_get(px)
    except Exception as exc:
        print('[tint] 跳过 %s（像素读取失败: %s）' % (img.name, exc))
        continue
    if px.size != w * h * 4:
        print('[tint] 跳过 %s（像素长度 %d != %d）' % (img.name, px.size, w * h * 4))
        continue
    rgb = px.reshape(-1, 4)[:, :3]
    lum = rgb @ np.array([0.299, 0.587, 0.114], dtype=np.float32)
    out = lum[:, None] + (rgb - lum[:, None]) * SAT
    out *= np.array(WARM, dtype=np.float32)
    rgb[:, :3] = np.clip(out, 0.0, 1.0)
    img.pixels.foreach_set(px)
    img.pack()
    print('[tint] %s %dx%d 已校色（饱和 %.2f / 暖移 %s）'
          % (img.name, w, h, SAT, WARM))

bpy.ops.export_scene.gltf(filepath=DST, export_format='GLB')
print('[tint] saved', DST)
