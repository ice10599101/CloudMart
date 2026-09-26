"""撕裂隔离探针：在已绑好的 blend 上分组摆姿势渲染，定位 02_head_ears 的撕裂来源。

A  仅头颈（不转耳朵、隐藏眼皮）  -> 撕裂=权重边界问题
B  仅头颈（显示眼皮）            -> 与 A 对比，多出来的尖刺=眼皮戳穿
C  仅耳朵（隐藏眼皮）            -> 耳骨是否误抓脸部碎片
D  rest 头部特写                 -> 量第二只眼位置 / 检查眼皮静置状态
E  rest 尾根侧视                 -> 尾根深色口是否为模型本身瑕疵

运行： blender --background --python probe_tear.py -- <blend> <out_dir>
"""
import math
import os
import sys

import bpy
from mathutils import Euler, Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
BLEND, OUT = argv[0], argv[1]
os.makedirs(OUT, exist_ok=True)

bpy.ops.wm.open_mainfile(filepath=BLEND)
arm = bpy.data.objects['CatRig']
EYELIDS = [ob for ob in bpy.data.objects if ob.type == 'MESH' and ob.name.startswith('Eyelid')]
print('[probe] eyelid objects:', [ob.name for ob in EYELIDS])

scene = bpy.context.scene
scene.render.engine = 'BLENDER_WORKBENCH'
scene.display.shading.light = 'STUDIO'
scene.display.shading.color_type = 'TEXTURE'
scene.display.shading.show_shadows = True
scene.display.shading.show_cavity = True
scene.render.resolution_x = 760
scene.render.resolution_y = 760
cam_data = bpy.data.cameras.new('Cam')
cam_data.lens = 62
cam = bpy.data.objects.new('Cam', cam_data)
bpy.context.collection.objects.link(cam)
scene.camera = cam


def shoot(path, loc, look, lens=62):
    cam.location = Vector(loc)
    cam_data.lens = lens
    d = Vector(look) - Vector(loc)
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print('[probe] rendered', path)


def pose(name, rot):
    if name not in arm.pose.bones:
        return
    pb = arm.pose.bones[name]
    pb.rotation_mode = 'XYZ'
    pb.rotation_euler = Euler([math.radians(a) for a in rot], 'XYZ')
    bpy.context.view_layer.update()


def clear():
    for pb in arm.pose.bones:
        pb.rotation_mode = 'XYZ'
        pb.rotation_euler = Euler((0, 0, 0), 'XYZ')
    bpy.context.view_layer.update()


def set_eyelids(visible):
    for ob in EYELIDS:
        ob.hide_render = not visible
        ob.hide_viewport = not visible


FRONT = ((0.55, -2.00, 0.60), (0.0, -0.02, 0.40))

# A: 仅头颈，眼皮隐藏
set_eyelids(False)
clear()
pose('Neck', (-6, 10, 0))
pose('Head', (-12, 20, 0))
shoot(os.path.join(OUT, 'A_head_only_noeyelids.png'), *FRONT)

# B: 仅头颈，眼皮显示
set_eyelids(True)
shoot(os.path.join(OUT, 'B_head_only_eyelids.png'), *FRONT)

# C: 仅耳朵，眼皮隐藏
set_eyelids(False)
clear()
pose('EarL1', (0, 0, -36))
pose('EarR1', (0, 0, 36))
shoot(os.path.join(OUT, 'C_ears_only.png'), *FRONT)

# D: rest 头部特写（眼皮显示，检查静置状态）
clear()
set_eyelids(True)
shoot(os.path.join(OUT, 'D_head_closeup.png'), (0.42, -1.15, 0.60), (0.0, -0.12, 0.48), lens=85)

# E: rest 尾根侧视（模型本身瑕疵排查）
shoot(os.path.join(OUT, 'E_tail_root_side.png'), (2.3, 0.4, 1.0), (0.0, 0.30, 0.40))
print('[probe] done')
