"""渲染奶灰猫的动画预览（Blender headless）。

产出：
    <outdir>/idle.mp4    —— 两个完整 Idle 循环（呼吸 / 头部漂移 / 耳朵抽动）
    <outdir>/hero.png    —— 单帧定妆图（用于画布）
    <outdir>/poses.png   —— 关键帧拼图（静止 / 转头 / 前倾 / 蹭头）

为什么用 Cycles 而不是 EEVEE：EEVEE 在 `--background` 下需要 GL 上下文，
headless 不一定拿得到；Cycles 纯 CPU 一定可用。为了控制时间，分辨率与采样都压低，
并且只渲染必要的帧数。

运行：
    blender --background --python render_preview.py -- <in.blend> <outdir>
"""
import json
import math
import os
import sys

import bpy
from mathutils import Euler, Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
IN_BLEND, OUTDIR = argv[0], argv[1]
os.makedirs(OUTDIR, exist_ok=True)

bpy.ops.wm.open_mainfile(filepath=IN_BLEND)
scene = bpy.context.scene
arm = bpy.data.objects['CatRig']
mesh = [o for o in scene.objects if o.type == 'MESH'][0]

scene.render.fps = 24
scene.render.engine = 'CYCLES'
scene.cycles.device = 'CPU'
scene.cycles.samples = 24
scene.cycles.use_denoising = True
scene.render.film_transparent = False
scene.view_settings.view_transform = 'Filmic' if 'Filmic' in [
    v.name for v in scene.view_settings.bl_rna.properties['view_transform'].enum_items] else 'Standard'

# ------------------------------------------------------------------ 场景搭建
for o in list(scene.objects):
    if o.type in ('LIGHT', 'CAMERA'):
        bpy.data.objects.remove(o, do_unlink=True)

def first_node(tree, kind):
    """按类型取节点：Principled BSDF 的**节点名会随界面语言变化**（中文界面下不叫这个），
    所以绝不能按名字取，只能按 node.type。"""
    for n in tree.nodes:
        if n.type == kind:
            return n
    return None


# 地面（暖燕麦色，与设计稿同源）
bpy.ops.mesh.primitive_plane_add(size=8, location=(0, 0, 0))
floor = bpy.context.active_object
fm = bpy.data.materials.new('Floor')
fm.use_nodes = True
fbsdf = first_node(fm.node_tree, 'BSDF_PRINCIPLED')
fbsdf.inputs['Base Color'].default_value = (0.62, 0.52, 0.38, 1)
fbsdf.inputs['Roughness'].default_value = 0.9
floor.data.materials.append(fm)

world = bpy.data.worlds.new('W')
world.use_nodes = True
wbg = first_node(world.node_tree, 'BACKGROUND')
wbg.inputs[0].default_value = (0.42, 0.34, 0.26, 1)
wbg.inputs[1].default_value = 0.55
scene.world = world


def add_light(name, kind, loc, energy, size, color=(1, 1, 1)):
    ld = bpy.data.lights.new(name, kind)
    ld.energy = energy
    ld.color = color
    if kind == 'AREA':
        ld.size = size
    ob = bpy.data.objects.new(name, ld)
    bpy.context.collection.objects.link(ob)
    ob.location = Vector(loc)
    d = Vector((0, -0.02, 0.45)) - ob.location
    ob.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    return ob


# 三点式：暖主光来自左前上（对应房间里那扇窗），冷补光在右后，暖背光勾轮廓
add_light('Key', 'AREA', (-1.5, -1.9, 2.1), 260, 2.2, (1.0, 0.88, 0.72))
add_light('Fill', 'AREA', (2.1, -1.4, 0.9), 60, 2.6, (0.80, 0.86, 1.0))
add_light('Rim', 'AREA', (0.4, 1.9, 1.5), 140, 1.6, (1.0, 0.92, 0.80))

cam_data = bpy.data.cameras.new('Cam')
cam_data.lens = 72
cam = bpy.data.objects.new('Cam', cam_data)
bpy.context.collection.objects.link(cam)
cam.location = Vector((0.44, -1.72, 0.58))
d = Vector((0.0, -0.02, 0.42)) - cam.location
cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
scene.camera = cam


# ------------------------------------------------------------------ 动作控制
def set_action(name):
    act = bpy.data.actions.get(name)
    if arm.animation_data is None:
        arm.animation_data_create()
    arm.animation_data.action = act
    # 槽位动作（Blender 5）需要把 action 绑到正确的 slot，否则姿态不生效
    try:
        slots = list(getattr(act, 'slots', []))
        if slots:
            arm.animation_data.action_slot = slots[0]
    except Exception as exc:
        print('[preview] slot 绑定跳过:', exc)
    return act


def render_to(path, w, h, samples):
    scene.render.resolution_x = w
    scene.render.resolution_y = h
    scene.cycles.samples = samples
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print('[preview] still ->', path)


# ------------------------------------------------------------------ 1) 静止定妆
set_action('Idle')
scene.frame_set(0)
render_to(os.path.join(OUTDIR, 'hero.png'), 900, 900, 48)

# ------------------------------------------------------------------ 2) Idle 帧序列
# 注意：本机这版 Blender **没有编入 FFMPEG**（image_settings.file_format 的枚举里没有 FFMPEG），
# 出不了 MP4。改为逐帧 PNG，再用 png_grid.py 拼成序列图。
def render_sequence(action_name, frames, tag, size):
    set_action(action_name)
    scene.render.image_settings.file_format = 'PNG'
    scene.render.resolution_x = size
    scene.render.resolution_y = size
    scene.cycles.samples = 24
    out = []
    for f in frames:
        scene.frame_set(f)
        p = os.path.join(OUTDIR, '%s_%03d.png' % (tag, f))
        scene.render.filepath = p
        bpy.ops.render.render(write_still=True)
        out.append(p)
    print('[preview] %s 帧序列完成：%d 帧' % (tag, len(out)))
    return out


idle_frames = render_sequence('Idle', [0, 12, 24, 36, 48, 60, 72, 84], 'idle', 320)
happy_frames = render_sequence('Happy', [0, 7, 14, 21, 28, 35], 'happy', 320)
blink_frames = render_sequence('Blink', [0, 1, 2, 3, 5, 6], 'blink', 320)
print('[preview] idle 帧:', json.dumps(idle_frames))
print('[preview] happy 帧:', json.dumps(happy_frames))
print('[preview] blink 帧:', json.dumps(blink_frames))

# ------------------------------------------------------------------ 3) 关键姿势
set_action('Happy')
scene.render.image_settings.file_format = 'PNG'
for tag, frame in (('happy_06', 6), ('happy_13', 13), ('happy_23', 23)):
    scene.frame_set(frame)
    render_to(os.path.join(OUTDIR, tag + '.png'), 520, 520, 28)
print('[preview] done')
