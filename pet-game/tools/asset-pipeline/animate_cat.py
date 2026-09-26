"""给绑好骨的奶灰猫 K 动画并导出 GLB（Blender headless）。

v2 动作集（全部骨骼能力都经过 rig_cat_v2.py 检查渲染实证）：
    Idle   循环 4.0s @24fps —— 呼吸（胸/脊/胯缩放）+ 头部缓慢漂移 + 耳朵偶尔抽动 + 尾巴摆动
    Happy  单次 1.7s       —— 被摸时的回应：低头侧蹭 + 耳朵后压 + 身体轻微挤压回弹 + 尾巴翘摆
    Blink  单次 0.25s      —— 毛色眼皮从眉上扫下来盖住眼球（眼皮 rest 已藏进头骨）

幅度红线（probe_tear.py 实证：头+耳同转超过 ~15°/25° 会拉伤脸颊碎片）：
    Head ≤ 14°、Neck ≤ 8°、Chest 旋转 ≤ 5°、Chest 缩放 ≤ 1.035、耳朵 ≤ 22°。
    调大之前必须先回 rig 检查渲染确认不撕裂。

眨眼契约：rig 阶段产出 eyelid_meta.json（axis/closed_deg）。没有眼皮时**不创建
Blink 动作**——导出空剪辑在 Cocos 里就是一条永远播不出内容的废轨。

导出参数要点：
    export_animation_mode='ACTIONS' —— 每个 Action 导成一条独立动画（Cocos 里按名播放）
    export_optimize_animation_size=False —— 否则关键帧会被抽稀，耳朵抽动这类短促动作会丢
    export_yup=True —— 交回 glTF 的 Y 向上约定（Blender 内部是 Z 向上）

运行：
    blender --background --python animate_cat.py -- <in.blend> <out.glb> [eyelid_meta.json]
"""
import json
import math
import os
import sys

import bpy
from mathutils import Euler, Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
IN_BLEND, OUT_GLB = argv[0], argv[1]
META_PATH = argv[2] if len(argv) > 2 else None
os.makedirs(os.path.dirname(OUT_GLB), exist_ok=True)

FPS = 24
IDLE_FRAMES = 96      # 4.0 s
HAPPY_FRAMES = 40     # 1.67 s
# 尾巴骨骼数量**从骨架里数出来**，不要写死 —— 路径点数与骨骼节数必须一致，
# 写死了就会 KeyError: Tail5（已实测踩到）。
TAIL_BONES = None   # 在打开 blend 之后填充

bpy.ops.wm.open_mainfile(filepath=IN_BLEND)
arm = bpy.data.objects['CatRig']
bpy.context.view_layer.objects.active = arm
bpy.context.scene.render.fps = FPS
TAIL_BONES = sum(1 for b in arm.data.bones if b.name.startswith('Tail'))
print('[anim] 尾骨 %d 节' % TAIL_BONES)

EYELID_META = None
if META_PATH and os.path.isfile(META_PATH):
    with open(META_PATH, encoding='utf-8') as fh:
        EYELID_META = json.load(fh)
    print('[anim] 眨眼契约: present=%s axis=%s closed=%s' % (
        EYELID_META.get('present'), EYELID_META.get('axis'), EYELID_META.get('closed_deg')))
if EYELID_META is None:
    print('[anim] WARNING: 未提供 eyelid_meta.json，Blink 将不导出')

HAS_EYELIDS = bool(EYELID_META and EYELID_META.get('present')
                   and 'EyelidL' in arm.pose.bones and 'EyelidR' in arm.pose.bones)
if not HAS_EYELIDS:
    print('[anim] WARNING: 眼皮缺失，Blink 动作跳过')

# 幂等：清掉上一次运行留下的 action 与 NLA 轨。
# 不清的话重跑会产出 `Blink.001 / Happy.001 / Idle.001` 这样的重复剪辑
# （导出后 Cocos 里就有六条动画，其中三条是空的）。已实测踩到。
if arm.animation_data:
    arm.animation_data.action = None
    for track in list(arm.animation_data.nla_tracks):
        arm.animation_data.nla_tracks.remove(track)
for act in list(bpy.data.actions):
    bpy.data.actions.remove(act)
print('[anim] 清理完成，剩余 action:', len(bpy.data.actions))


def clear_pose():
    for pb in arm.pose.bones:
        pb.rotation_mode = 'XYZ'
        pb.rotation_euler = Euler((0, 0, 0), 'XYZ')
        pb.location = Vector((0, 0, 0))
        pb.scale = Vector((1, 1, 1))


def key(bone, frame, rot=None, scale=None, loc=None):
    # 骨骼可能不存在（比如本轮没做的眼皮）—— 静默跳过，不能让缺一条剪辑就崩掉整场
    if bone not in arm.pose.bones:
        return
    pb = arm.pose.bones[bone]
    pb.rotation_mode = 'XYZ'
    if rot is not None:
        pb.rotation_euler = Euler([math.radians(a) for a in rot], 'XYZ')
        pb.keyframe_insert('rotation_euler', frame=frame)
    if scale is not None:
        pb.scale = Vector(scale)
        pb.keyframe_insert('scale', frame=frame)
    if loc is not None:
        pb.location = Vector(loc)
        pb.keyframe_insert('location', frame=frame)


def action_fcurves(action):
    """Blender 5 起 Action 换成了 layer/strip/channelbag 的"槽位动作"结构，
    `action.fcurves` 已不存在（4.4 起废弃）。这里做向前兼容的取法。"""
    if hasattr(action, 'fcurves'):
        return list(action.fcurves)
    out = []
    for layer in getattr(action, 'layers', []):
        for strip in getattr(layer, 'strips', []):
            for bag in getattr(strip, 'channelbags', []):
                out.extend(bag.fcurves)
    return out


def smooth_curves(action):
    for fc in action_fcurves(action):
        for kp in fc.keyframe_points:
            kp.interpolation = 'BEZIER'
            kp.handle_left_type = 'AUTO_CLAMPED'
            kp.handle_right_type = 'AUTO_CLAMPED'


def set_range(action, start, end):
    try:
        action.use_frame_range = True
        action.frame_start, action.frame_end = start, end
    except Exception as exc:      # 版本差异时不影响导出
        print('[anim] 设置帧范围失败（忽略）:', exc)


def key_tail(frame, tip_deg):
    """尾巴姿势：按「尾尖总弯角」分配到各节（链式旋转会沿链累加，
    每节独立给大角度会让尾尖甩出几百度、整条尾拧穿身体 —— 已实测踩到）。
    w 沿尾尖方向渐增：根部动得少、尾尖动得多，看起来像鞭梢。"""
    n = max(TAIL_BONES, 2)
    for k in range(TAIL_BONES):
        w = 0.5 + 0.5 * (k / (n - 1))
        key('Tail%d' % (k + 1), frame, rot=(0, 0, tip_deg / n * w))


def pulse(t, center, width):
    """高斯脉冲：用来做耳朵短促抽动（不是正弦，正弦抽动看起来像机器）。"""
    d = (t - center) / width
    return math.exp(-d * d)


# ------------------------------------------------------------------ Idle
def build_idle():
    clear_pose()
    action = bpy.data.actions.new('Idle')
    arm.animation_data_create()
    arm.animation_data.action = action

    step = 3
    frames = list(range(0, IDLE_FRAMES + 1, step))
    for f in frames:
        t = f / IDLE_FRAMES                      # [0,1]
        # 呼吸每循环两次（约 2s 一次），符合猫咪安静时的呼吸频率
        br = math.sin(2 * math.pi * t * 2)
        # 头部漂移每循环一次，四个轴的相位略有差，避免看起来是机械同步
        dr = math.sin(2 * math.pi * t)
        dr2 = math.sin(2 * math.pi * t + 0.9)
        ear = pulse(t, 0.34, 0.045) + pulse(t, 0.71, 0.038)

        key('Chest', f, scale=(1 + 0.030 * br, 1 + 0.030 * br, 1 + 0.012 * br))
        key('Spine', f, scale=(1 + 0.018 * br, 1 + 0.018 * br, 1 + 0.008 * br))
        key('Hips', f, scale=(1 + 0.009 * br, 1 + 0.009 * br, 1.0))
        key('Neck', f, rot=(0.7 * dr, 1.6 * dr2, 0.0))
        key('Head', f, rot=(1.3 * dr, 3.4 * dr2, 1.1 * math.sin(2 * math.pi * t * 2 + 1.4)))
        key('EarL1', f, rot=(0, 0, -7.0 * ear))
        key('EarR1', f, rot=(0, 0, 5.0 * ear))
        key('EarL2', f, rot=(0, 0, -3.0 * ear))
        # 尾巴：每循环一次完整摆动，尾尖总弯 ±16°（链式累加，见 key_tail 注释）
        sway = math.sin(2 * math.pi * t + 0.4)
        key_tail(f, 16.0 * sway)

    smooth_curves(action)
    set_range(action, 0, IDLE_FRAMES)
    return action


# ------------------------------------------------------------------ Happy
def build_happy():
    clear_pose()
    action = bpy.data.actions.new('Happy')
    arm.animation_data.action = action

    # 低头侧蹭：头先向右下压、耳朵后压，再回中；身体有一记轻微的"挤压—回弹"。
    # 幅度全部在 probe_tear.py 实证的安全区内（头≤14° 颈≤8° 胸转≤5° 耳≤22°）
    KEYS = [
        (0,  {'Chest': ((0, 0, 0), (1.00, 1.00, 1.00)),
              'Neck': ((0, 0, 0), None), 'Head': ((0, 0, 0), None),
              'EarL1': ((0, 0, 0), None), 'EarR1': ((0, 0, 0), None)}),
        (7,  {'Chest': ((3, 0, 0), (1.02, 1.02, 0.985)),
              'Neck': ((5, -6, 0), None), 'Head': ((-7, -10, 0), None),
              'EarL1': ((0, 0, 16), None), 'EarR1': ((0, 0, -14), None)}),
        (14, {'Chest': ((5, 0, 0), (1.035, 1.035, 0.975)),
              'Neck': ((6, -8, 0), None), 'Head': ((-9, -14, 0), None),
              'EarL1': ((0, 0, 22), None), 'EarR1': ((0, 0, -18), None)}),
        (24, {'Chest': ((2, 0, 0), (1.01, 1.01, 1.00)),
              'Neck': ((3, 4, 0), None), 'Head': ((-4, 7, 0), None),
              'EarL1': ((0, 0, -4), None), 'EarR1': ((0, 0, 5), None)}),
        (32, {'Chest': ((0, 0, 0), (1.00, 1.00, 1.00)),
              'Neck': ((0, 0, 0), None), 'Head': ((1, 2, 0), None),
              'EarL1': ((0, 0, 2), None), 'EarR1': ((0, 0, -2), None)}),
        (HAPPY_FRAMES, {'Chest': ((0, 0, 0), (1.00, 1.00, 1.00)),
                        'Neck': ((0, 0, 0), None), 'Head': ((0, 0, 0), None),
                        'EarL1': ((0, 0, 0), None), 'EarR1': ((0, 0, 0), None)}),
    ]
    for frame, poses in KEYS:
        for bone, (rot, scale) in poses.items():
            key(bone, frame, rot=rot, scale=scale)
    # 被摸时的尾巴：翘起 + 两下轻拂（猫开心时就是这样）。尾尖总弯峰值 ~60°
    for frame, tip in ((0, 0.0), (7, 48.0), (13, 60.0), (18, 44.0),
                       (24, 56.0), (32, 16.0), (HAPPY_FRAMES, 0.0)):
        key_tail(frame, tip)

    smooth_curves(action)
    set_range(action, 0, HAPPY_FRAMES)
    return action


# ------------------------------------------------------------------ Blink
def build_blink():
    """眨眼：约 0.25s。闭合 2 帧 / 保持 1 帧 / 张开 3 帧 —— 猫的眨眼就是这么快，
    拉长了会像慢动作。眼皮骨局部 Y = 世界 X（水平扫轴），旋转量来自 rig 契约。"""
    closed = float(EYELID_META['closed_deg'])
    clear_pose()
    action = bpy.data.actions.new('Blink')
    arm.animation_data.action = action
    for f, deg in ((0, 0), (2, closed), (3, closed), (6, 0)):
        key('EyelidL', f, rot=(0, deg, 0))
        key('EyelidR', f, rot=(0, deg, 0))
    smooth_curves(action)
    set_range(action, 0, 6)
    return action


idle = build_idle()
happy = build_happy()
blink = build_blink() if HAS_EYELIDS else None

# 推进 NLA：导出器在 ACTIONS 模式下会为每个 Action 生成一条独立动画，
# 但把它们放进 NLA 轨更稳（不同 Blender 版本的 ACTIONS 模式对"未被使用的 Action"处理不一致）
arm.animation_data.action = idle
for act in (idle, happy, blink):
    if act is None:
        continue
    track = arm.animation_data.nla_tracks.new()
    track.name = act.name
    strip = track.strips.new(act.name, int(act.frame_start), act)
    strip.name = act.name
    track.mute = True

clear_pose()
bpy.ops.wm.save_as_mainfile(filepath=IN_BLEND)

bpy.ops.object.select_all(action='DESELECT')
arm.select_set(True)
# ⚠️ 必须把所有 MESH 都选上：眼睑是**独立物体**，只选骨架+主体的话它们不会被导出
# （骨骼与动画都在、网格却缺了，导出的 GLB 上眼睛会永远睁着 —— 已实测踩到）。
for obj in bpy.context.scene.objects:
    if obj.type == 'MESH':
        obj.select_set(True)
bpy.context.view_layer.objects.active = arm

bpy.ops.export_scene.gltf(
    filepath=OUT_GLB,
    export_format='GLB',
    use_selection=True,
    export_apply=False,
    export_yup=True,
    export_skins=True,
    export_morph=False,
    export_animations=True,
    export_animation_mode='ACTIONS',
    export_bake_animation=False,
    export_optimize_animation_size=False,
    export_force_sampling=True,
    export_frame_range=False,
    export_nla_strips=True,
    export_image_format='AUTO',
    export_texture_dir='',
)
print('[anim] actions:', [a.name for a in bpy.data.actions])
print('[anim] exported', OUT_GLB, os.path.getsize(OUT_GLB) / 1048576.0, 'MB')
