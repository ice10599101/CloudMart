"""导出回归验证：把 rigged GLB 重新导入，逐条挂动画渲染代表帧。
证明 ACTIONS 导出的动画轨真的驱动网格（尾巴/头/眼皮都在动）。

运行： blender --background --python verify_anim.py -- <rigged.glb> <outdir>
"""
import os
import sys

import bpy
from mathutils import Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
GLB, OUT = argv[0], argv[1]
os.makedirs(OUT, exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB)

arm = next(o for o in bpy.context.scene.objects if o.type == 'ARMATURE')
print('[verify] 动画剪辑:', sorted(a.name for a in bpy.data.actions))
print('[verify] 骨架动画槽:', [s.name for s in getattr(arm.animation_data, 'actions_slots', [])
                            if hasattr(arm.animation_data, 'actions_slots')] or '见 action 列表')

scene = bpy.context.scene
scene.render.engine = 'BLENDER_WORKBENCH'
scene.display.shading.light = 'STUDIO'
scene.display.shading.color_type = 'TEXTURE'
scene.display.shading.show_shadows = True
scene.display.shading.show_cavity = True
# 背景必须是浅灰：默认工厂 World 是深色，回放验证图会整体发黑，
# 闭眼帧看起来像骷髅（已实测被评审打回）。与 rig 检查渲染保持一致的浅灰底。
world = bpy.data.worlds.get('World') or bpy.data.worlds.new('World')
bpy.context.scene.world = world
world.color = (0.92, 0.92, 0.92)
scene.render.resolution_x = 760
scene.render.resolution_y = 760
cam_data = bpy.data.cameras.new('Cam')
cam_data.lens = 62
cam = bpy.data.objects.new('Cam', cam_data)
bpy.context.collection.objects.link(cam)
scene.camera = cam
cam.location = Vector((0.55, -2.00, 0.60))
d = Vector((0.0, -0.02, 0.40)) - cam.location
cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()

SHOTS = [
    ('Idle', 24, 'idle_mid'),      # 呼吸+头漂移+尾巴摆动中段
    ('Idle', 60, 'idle_late'),     # 尾巴反相
    ('Happy', 14, 'happy_peak'),   # 低头侧蹭峰值
    ('Blink', 3, 'blink_closed'),  # 闭眼保持帧
    (None, 0, 'rest'),             # 无动画 rest
]

if not hasattr(arm, 'animation_data') or arm.animation_data is None:
    arm.animation_data_create()

for act_name, frame, tag in SHOTS:
    if act_name is None:
        arm.animation_data.action = None
    else:
        act = bpy.data.actions.get(act_name)
        if act is None:
            print('[verify] 缺剪辑 %s，跳过' % act_name)
            continue
        arm.animation_data.action = act
    bpy.context.scene.frame_set(frame)
    scene.render.filepath = os.path.join(OUT, 'anim_%s.png' % tag)
    bpy.ops.render.render(write_still=True)
    print('[verify] %s @%d -> anim_%s.png' % (act_name or 'rest', frame, tag))
print('[verify] done')
