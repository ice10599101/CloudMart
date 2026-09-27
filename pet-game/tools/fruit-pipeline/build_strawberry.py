"""草莓宠物打样 v2：建模 → 贴图 → 轻绑定 → 动画 → GLB → Cycles 预览（Blender 5.2 headless）。

v2 修正（v1 实证问题）：
    * 生成式图像 pixels 写入后 pack 会丢数据 → 统一走「落盘 PNG → reload → pack」
    * 颜色必须以**线性值**写入像素缓冲（pixels 接口按场景线性色处理），
      否则保存 PNG 时被再编码一次，全部发白发灰
    * 轮廓插值从余弦分段改为 Catmull-Rom（C1 连续），消除环带状折痕
    * 围巾下移贴住"脖颈"（按轮廓半径反推主半径），不再悬浮成光环
    * 萼片加宽缩短、压住头顶收拢段；嘴/舌按轮廓半径外推，不再吞进体内

运行：
    blender --background --python build_strawberry.py -- <glb输出路径> [预览目录]
"""
import math
import os
import struct
import sys
import zlib

import bmesh
import bpy
import numpy as np
from mathutils import Euler, Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
OUT_GLB = argv[0]
OUT_PREVIEW = argv[1] if len(argv) > 1 else os.path.join(os.path.dirname(OUT_GLB), '_preview')
OUT_WORK = os.path.join(os.path.dirname(OUT_PREVIEW), '_work')
os.makedirs(os.path.dirname(OUT_GLB), exist_ok=True)
os.makedirs(OUT_PREVIEW, exist_ok=True)
os.makedirs(OUT_WORK, exist_ok=True)
OUT_BLEND = os.path.join(OUT_WORK, 'strawberry.blend')

FPS = 24
IDLE_FRAMES = 96      # 4.00s
HAPPY_FRAMES = 40     # 1.67s
BLINK_FRAMES = 6      # 0.25s
BODY_H = 0.82

# ---------------------------------------------------------------- 场景复位
for obj in list(bpy.context.scene.objects):
    bpy.data.objects.remove(obj, do_unlink=True)
for act in list(bpy.data.actions):
    bpy.data.actions.remove(act)
for block in (bpy.data.meshes, bpy.data.materials, bpy.data.images, bpy.data.armatures):
    for item in list(block):
        if item.users == 0:
            block.remove(item)
bpy.context.scene.render.fps = FPS

# ---------------------------------------------------------------- 颜色与轮廓
def hex_srgb(h):
    """#RRGGBB → sRGB 域浮点 (0..1)。贴图统一在 sRGB 域计算，落盘字节即所见即所得。"""
    return ((h >> 16 & 0xFF) / 255.0, (h >> 8 & 0xFF) / 255.0, (h & 0xFF) / 255.0)


def write_png(path, arr):
    """纯 Python 写 8bit RGB PNG（不依赖 Blender 的生成式像素链路 —— v2 实证
    `images.new + foreach_set + save` 对大图会落盘全黑）。arr: uint8 (H, W, 3)。"""
    height, width = arr.shape[:2]
    raw = b''.join(b'\x00' + arr[y].tobytes() for y in range(height))

    def chunk(tag, data):
        return (struct.pack('>I', len(data)) + tag + data
                + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF))

    with open(path, 'wb') as fh:
        fh.write(b'\x89PNG\r\n\x1a\n')
        fh.write(chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)))
        fh.write(chunk(b'IDAT', zlib.compress(raw, 9)))
        fh.write(chunk(b'IEND', b''))


def load_texture(path):
    """从磁盘 PNG 装载贴图（文件真实存在 → glTF 导出器直接嵌入，pack 仅兜底）。"""
    img = bpy.data.images.load(path)
    img.pack()
    return img


# 水滴轮廓：t ∈ [0,1]（0=底 1=顶）→ 半径。Catmull-Rom 插值保证 C1 连续（v1 的余弦分段有折痕）。
PROFILE = [(0.00, 0.020), (0.045, 0.170), (0.12, 0.345), (0.26, 0.425),
           (0.42, 0.415), (0.62, 0.345), (0.80, 0.240), (0.92, 0.130), (1.00, 0.005)]
_TS = [p[0] for p in PROFILE]
_RS = [p[1] for p in PROFILE]


def profile_radius(t: float) -> float:
    t = min(max(t, _TS[0]), _TS[-1])
    lo, hi = 0, len(_TS) - 1
    while hi - lo > 1:
        mid = (lo + hi) // 2
        if _TS[mid] <= t:
            lo = mid
        else:
            hi = mid
    u = (t - _TS[lo]) / (_TS[hi] - _TS[lo])
    p1, p2 = _RS[lo], _RS[hi]
    p0 = _RS[max(lo - 1, 0)]
    p3 = _RS[min(hi + 1, len(_RS) - 1)]
    return 0.5 * ((2 * p1) + (-p0 + p2) * u
                  + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u
                  + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u)


def smooth(ob):
    for poly in ob.data.polygons:
        poly.use_smooth = True


# ---------------------------------------------------------------- 贴图（纯 Python 生成 PNG → load）
def solid_texture(name, color, w=64):
    arr = np.zeros((w, w, 3), dtype=np.uint8)
    arr[..., 0] = int(color >> 16 & 0xFF)
    arr[..., 1] = int(color >> 8 & 0xFF)
    arr[..., 2] = int(color & 0xFF)
    path = os.path.join(OUT_WORK, name + '.png')
    write_png(path, arr)
    return load_texture(path)


def make_material(name, img, rough=0.5):
    """所有部件一律走贴图：Cocos 侧 pet-toon 材质替换按 mainTexture 取色，
    纯颜色因子部件会在换 shader 时丢成白色（mapCtrl=1 采样贴图）。"""
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image = img
    mat.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = rough
    return mat


# ---------------------------------------------------------------- 体表贴图（底色渐变 + 种籽，sRGB 域直出）
def make_seed_texture():
    W = H = 1024
    yy, xx = np.mgrid[0:H, 0:W]
    u = (xx + 0.5) / W
    v = (yy + 0.5) / H                     # v=0 底部
    # UV 来自原生球面：环参数 φ = π·v → 体表参数 t = (cos(π·v)+1)/2
    t = (np.cos(np.pi * v) + 1.0) / 2.0
    top = np.array(hex_srgb(0xF0566F))     # 肩部亮草莓红
    bot = np.array(hex_srgb(0xE23E5C))     # 底部深草莓红
    base = bot + (top - bot) * t[..., None]
    base += (np.clip((t - 0.70) / 0.20, 0, 1) * 0.04)[..., None]       # 肩部提亮
    base *= (1.0 - np.clip((0.10 - t) / 0.10, 0, 1) * 0.12)[..., None] # 接地压暗

    seed_col = np.array(hex_srgb(0xF9E9BC))  # 奶油种籽
    golden = math.pi * (3 - math.sqrt(5))
    rng = np.random.default_rng(7)
    alpha_total = np.zeros((H, W), dtype=np.float32)
    for i in range(72):
        tt = min(max(0.10 + 0.78 * (i / 72) + float(rng.normal(0, 0.012)), 0.08), 0.90)
        uu = (i * golden / (2 * math.pi)) % 1.0 + float(rng.normal(0, 0.004))
        vv = math.acos(max(-1.0, min(1.0, 2 * tt - 1))) / math.pi
        # 环半径越小，同样物理宽度的籽在 u 向占的 UV 越大 → 按周长补偿并封顶
        du = min(0.018 / (2 * math.pi * max(profile_radius(tt), 0.05)), 0.05)
        dv = min(0.018 / BODY_H * 2 / (math.pi * max(math.sin(math.pi * vv), 0.30)), 0.05)
        dx = u - uu
        dx = dx - np.round(dx)             # u 向环绕
        dy = v - vv
        m = (dx / max(du, 1e-6)) ** 2 + (dy / max(dv, 1e-6)) ** 2
        a = np.clip(1.0 - m, 0, 1) * 0.95
        alpha_total = np.minimum(alpha_total + a, 1.0)
    px = base * (1 - alpha_total[..., None]) + seed_col * alpha_total[..., None]

    path = os.path.join(OUT_WORK, 'berry_base.png')
    write_png(path, (np.clip(px, 0.0, 1.0) * 255).astype(np.uint8))
    return load_texture(path)


# ---------------------------------------------------------------- 建模
def add_sphere_part(name, loc, scale, mat):
    """球体部件：缩放直接烘进顶点（蒙皮网格不挂物体级缩放，避免 glTF 蒙皮不可控）。"""
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=16, radius=1.0, location=(0, 0, 0))
    ob = bpy.context.active_object
    ob.name = name
    for v in ob.data.vertices:
        v.co = Vector((v.co.x * scale[0], v.co.y * scale[1], v.co.z * scale[2])) + Vector(loc)
    ob.data.materials.append(mat)
    smooth(ob)
    return ob


# 眼睛中心：t≈0.66 → z=0.54，正面 = Blender -Y（导出后 = glTF +Z，正对镜头）
EYE_L = Vector((-0.140, -0.285, 0.540))
EYE_R = Vector((0.140, -0.285, 0.540))

bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=40, radius=1.0, location=(0, 0, 0))
berry = bpy.context.active_object
berry.name = 'Berry'
for v in berry.data.vertices:
    x, y, z = v.co
    t = (z + 1.0) / 2.0
    horiz = math.hypot(x, y)
    d = Vector((x, y, 0)) / horiz if horiz > 1e-5 else Vector((0, 0, 0))
    r = profile_radius(t)
    v.co = (d.x * r, d.y * r, t * BODY_H)
berry.data.materials.append(make_material('berry', img=make_seed_texture(), rough=0.42))
smooth(berry)

eye_mat = make_material('eye', solid_texture('eye', 0x1A1110), rough=0.12)
hl_mat = make_material('highlight', solid_texture('highlight', 0xFFFFFF), rough=0.08)
mouth_mat = make_material('mouth', solid_texture('mouth', 0x6E2833), rough=0.5)
blush_mat = make_material('blush', solid_texture('blush', 0xEE5F8D), rough=0.6)
sepal_mat = make_material('sepal', solid_texture('sepal', 0x5CA24E), rough=0.55)
stem_mat = make_material('stem', solid_texture('stem', 0x6BB05C), rough=0.55)

eye_l = add_sphere_part('EyeL', tuple(EYE_L), (0.080, 0.060, 0.095), eye_mat)
eye_r = add_sphere_part('EyeR', tuple(EYE_R), (0.080, 0.060, 0.095), eye_mat)
hl_l = add_sphere_part('HLL', tuple(EYE_L + Vector((-0.024, -0.058, 0.038))), (0.026, 0.017, 0.026), hl_mat)
hl_r = add_sphere_part('HLR', tuple(EYE_R + Vector((-0.024, -0.058, 0.038))), (0.026, 0.017, 0.026), hl_mat)
# 开口笑：球体削平上沿 → 半穹顶朝外下（QQ 式张嘴笑）。v4 的细管笑弧会被曲面吞掉中段，
# 半穹顶是实心面片，没有"细管凸出量"的脆弱性
def build_smile():
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=16, radius=1.0, location=(0, 0, 0))
    ob = bpy.context.active_object
    ob.name = 'Mouth'
    me = ob.data
    bm = bmesh.new()
    bm.from_mesh(me)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z > 0.002], context='VERTS')
    bm.to_mesh(me)
    bm.free()
    mouth_r = profile_radius(0.518)        # z=0.425 处的体表半径
    for v in me.vertices:
        v.co = Vector((v.co.x * 0.058, v.co.y * 0.034, v.co.z * 0.042)) \
            + Vector((0, -(mouth_r - 0.010), 0.425))
    me.materials.append(mouth_mat)
    smooth(ob)
    return ob


mouth = build_smile()
# 腮红：18° 收进正脸区，加大一号保证 toon 提亮下仍可读
for side, x in (('L', -1), ('R', 1)):
    ang = math.radians(18)
    nrm = Vector((math.sin(ang) * x, -math.cos(ang), 0))
    pos = nrm * (profile_radius(0.55) - 0.006) + Vector((0, 0, 0.47))
    blush = add_sphere_part(f'Blush{side}', tuple(pos), (0.058, 0.014, 0.040), blush_mat)
    blush.rotation_euler = nrm.to_track_quat('Y', 'Z').to_euler()


def build_calyx():
    """五瓣萼片合成一块网格：加宽缩短（v1 太细太长像插草），压住头顶收拢段。"""
    verts = []
    faces = []
    for k in range(5):
        yaw = k * (2 * math.pi / 5)
        base = Vector((0.050 * math.cos(yaw), 0.050 * math.sin(yaw), 0.762))
        # 局部：+Z 指向叶尖、X 为叶宽；先绕 X 外倾 17°（更直立，俯视不漏叶背），再绕 Z 均布
        rot = Euler((math.radians(-17), 0, yaw - math.pi / 2), 'XYZ').to_matrix()
        local = [(-0.045, 0, 0.004), (0.045, 0, 0.004),
                 (-0.085, 0, 0.100), (0.085, 0, 0.100), (0, 0, 0.200)]
        i0 = len(verts)
        verts.extend(tuple(base + rot @ Vector(p)) for p in local)
        # 绕向必须让法线朝外：v1 的 (0,1,3) 顺序法线朝内，pet-toon 按背面打光，
        # 整个萼片渲染成暗红（Cycles 双面渲染看不出来，引擎侧现形）
        faces.extend([(i0 + 1, i0, i0 + 3), (i0 + 3, i0, i0 + 2), (i0 + 3, i0 + 2, i0 + 4)])
    me = bpy.data.meshes.new('Calyx')
    me.from_pydata(verts, [], faces)
    me.validate()
    calyx = bpy.data.objects.new('Calyx', me)
    bpy.context.collection.objects.link(calyx)
    sol = calyx.modifiers.new('Solidify', 'SOLIDIFY')
    sol.thickness = 0.016
    me.materials.append(sepal_mat)
    smooth(calyx)

    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=0.026, depth=0.14,
                                        location=(0.008, 0.004, 0.865))
    stem = bpy.context.active_object
    stem.name = 'Stem'
    stem.rotation_euler = Euler((math.radians(6), 0, math.radians(14)), 'XYZ')
    stem.data.materials.append(stem_mat)
    smooth(stem)
    return calyx, stem


calyx, stem = build_calyx()
# 冠心补穹：五片外倾留下的中央缺口会露出叶背（引擎里成暗色碎面），加一片绿穹封顶
calyx_cap = add_sphere_part('CalyxCap', (0, 0, 0.78), (0.150, 0.150, 0.100), sepal_mat)

# ---------------------------------------------------------------- 绑定
arm_data = bpy.data.armatures.new('FruitRig')
arm = bpy.data.objects.new('FruitRig', arm_data)
bpy.context.collection.objects.link(arm)
bpy.context.view_layer.objects.active = arm
arm.select_set(True)
bpy.ops.object.mode_set(mode='EDIT')
eb = arm_data.edit_bones
body_bone = eb.new('Body')
body_bone.head = (0, 0, 0.02)
body_bone.tail = (0, 0, 0.85)          # 头在地面：缩放挤压以地面为锚
calyx_bone = eb.new('Calyx')
calyx_bone.head = (0, 0, 0.74)
calyx_bone.tail = (0, 0, 0.98)
calyx_bone.parent = body_bone
for side, x in (('L', -1), ('R', 1)):
    b = eb.new(f'Eye{side}')
    b.head = (0.140 * x, -0.285, 0.540)   # 骨头中心 = 眼球中心：scale.y 即向心闭合
    b.tail = (0.140 * x, -0.285, 0.565)
    b.parent = body_bone
bpy.ops.object.mode_set(mode='OBJECT')

mesh_objects = [berry, eye_l, eye_r, hl_l, hl_r, mouth,
                bpy.data.objects['BlushL'], bpy.data.objects['BlushR'], calyx, stem, calyx_cap]
GROUP_OF = {'Berry': 'Body', 'EyeL': 'EyeL', 'EyeR': 'EyeR', 'HLL': 'EyeL', 'HLR': 'EyeR',
            'Mouth': 'Body', 'BlushL': 'Body', 'BlushR': 'Body',
            'Calyx': 'Calyx', 'Stem': 'Calyx', 'CalyxCap': 'Calyx'}
for ob in mesh_objects:
    vg = ob.vertex_groups.new(name=GROUP_OF[ob.name])
    vg.add(list(range(len(ob.data.vertices))), 1.0, 'REPLACE')
    ob.parent = arm
    mod = ob.modifiers.new('Armature', 'ARMATURE')
    mod.object = arm

# ---------------------------------------------------------------- 动画（继承猫流水线的成熟框架）
if arm.animation_data:
    arm.animation_data.action = None
    for track in list(arm.animation_data.nla_tracks):
        arm.animation_data.nla_tracks.remove(track)


def clear_pose():
    for pb in arm.pose.bones:
        pb.rotation_mode = 'XYZ'
        pb.rotation_euler = Euler((0, 0, 0), 'XYZ')
        pb.location = Vector((0, 0, 0))
        pb.scale = Vector((1, 1, 1))


def key(bone, frame, rot=None, scale=None, loc=None):
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
    """Blender 5 槽位 Action：fcurves 走 layer/strip/channelbag（4.4+ 兼容取法）。"""
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
    except Exception as exc:
        print('[fruit] 设置帧范围失败（忽略）:', exc)


def build_idle():
    clear_pose()
    action = bpy.data.actions.new('Idle')
    arm.animation_data_create()
    arm.animation_data.action = action
    step = 3
    for f in range(0, IDLE_FRAMES + 1, step):
        t = f / IDLE_FRAMES
        br = math.sin(2 * math.pi * t * 2)          # 每循环呼吸两次（~2s 一次）
        dr = math.sin(2 * math.pi * t)
        dr2 = math.sin(2 * math.pi * t + 0.9)
        key('Body', f, scale=(1 - 0.016 * br, 1 + 0.028 * br, 1 - 0.016 * br),
            rot=(0.8 * dr2, 0, 0.6 * dr))
        key('Calyx', f, rot=(2.8 * dr, 0, 3.2 * dr2))
    smooth_curves(action)
    set_range(action, 0, IDLE_FRAMES)
    return action


def build_happy():
    clear_pose()
    action = bpy.data.actions.new('Happy')
    arm.animation_data.action = action
    # 水果的招牌：下蹲 → 起跳 → 滞空笑眼 → 落地挤压 → 回弹（squash & stretch 全程以地面为锚）
    KEYS = [
        (0,  {'Body': ((0, 0, 0), (1, 1, 1), (0, 0, 0)), 'Calyx': ((0, 0, 0), None, None),
              'EyeL': (None, (1, 1, 1), None), 'EyeR': (None, (1, 1, 1), None)}),
        (4,  {'Body': ((0, 0, 0), (1.09, 0.87, 1.09), (0, 0, 0)), 'Calyx': ((-6, 0, 0), None, None),
              'EyeL': (None, (1, 0.85, 1), None), 'EyeR': (None, (1, 0.85, 1), None)}),
        (10, {'Body': ((0, 0, 0), (0.93, 1.14, 0.93), (0, 0.16, 0)), 'Calyx': ((10, 0, 0), None, None),
              'EyeL': (None, (1, 0.30, 1), None), 'EyeR': (None, (1, 0.30, 1), None)}),
        (16, {'Body': ((0, 0, 0), (0.95, 1.10, 0.95), (0, 0.24, 0)), 'Calyx': ((4, 0, 0), None, None),
              'EyeL': (None, (1, 0.22, 1), None), 'EyeR': (None, (1, 0.22, 1), None)}),
        (22, {'Body': ((0, 0, 0), (1.10, 0.86, 1.10), (0, 0, 0)), 'Calyx': ((-9, 0, 0), None, None),
              'EyeL': (None, (1, 0.80, 1), None), 'EyeR': (None, (1, 0.80, 1), None)}),
        (27, {'Body': ((0, 0, 0), (0.97, 1.06, 0.97), (0, 0, 0)), 'Calyx': ((3, 0, 0), None, None),
              'EyeL': (None, (1, 1, 1), None), 'EyeR': (None, (1, 1, 1), None)}),
        (34, {'Body': ((0, 0, 0), (1.01, 0.99, 1.01), (0, 0, 0)), 'Calyx': ((-2, 0, 0), None, None),
              'EyeL': (None, (1, 1, 1), None), 'EyeR': (None, (1, 1, 1), None)}),
        (HAPPY_FRAMES, {'Body': ((0, 0, 0), (1, 1, 1), (0, 0, 0)), 'Calyx': ((0, 0, 0), None, None),
                        'EyeL': (None, (1, 1, 1), None), 'EyeR': (None, (1, 1, 1), None)}),
    ]
    for frame, poses in KEYS:
        for bone, (rot, scale, loc) in poses.items():
            key(bone, frame, rot=rot, scale=scale, loc=loc)
    smooth_curves(action)
    set_range(action, 0, HAPPY_FRAMES)
    return action


def build_blink():
    clear_pose()
    action = bpy.data.actions.new('Blink')
    arm.animation_data.action = action
    for f, sy in ((0, 1.0), (2, 0.05), (3, 0.05), (6, 1.0)):
        key('EyeL', f, scale=(1, sy, 1))
        key('EyeR', f, scale=(1, sy, 1))
    smooth_curves(action)
    set_range(action, 0, BLINK_FRAMES)
    return action


idle = build_idle()
happy = build_happy()
blink = build_blink()

# 推进 NLA：ACTIONS 模式对"未使用 Action"处理不稳定，进轨导出才稳（猫流水线实证）
arm.animation_data.action = idle
for act in (idle, happy, blink):
    track = arm.animation_data.nla_tracks.new()
    track.name = act.name
    strip = track.strips.new(act.name, int(act.frame_start), act)
    strip.name = act.name
    track.mute = True

clear_pose()
bpy.ops.wm.save_as_mainfile(filepath=OUT_BLEND)
print('[fruit] blend saved ->', OUT_BLEND)

bpy.ops.object.select_all(action='DESELECT')
arm.select_set(True)
# 必须把全部 MESH 一起选中：独立物件（眼睛/萼片/围巾）缺选 = GLB 缺件（猫流水线实证）
for ob in bpy.context.scene.objects:
    if ob.type == 'MESH':
        ob.select_set(True)
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
print('[fruit] actions:', [a.name for a in bpy.data.actions])
print('[fruit] exported', OUT_GLB, os.path.getsize(OUT_GLB) / 1048576.0, 'MB')

# ---------------------------------------------------------------- 预览渲染（Cycles CPU；EEVEE headless 无 GL）
scene = bpy.context.scene


def first_node(tree, kind):
    for n in tree.nodes:
        if n.type == kind:
            return n
    return None


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


def add_light(name, loc, energy, size, color=(1, 1, 1)):
    ld = bpy.data.lights.new(name, 'AREA')
    ld.energy = energy
    ld.color = color
    ld.size = size
    ob = bpy.data.objects.new(name, ld)
    bpy.context.collection.objects.link(ob)
    ob.location = Vector(loc)
    d = Vector((0, 0, 0.45)) - ob.location
    ob.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    return ob


add_light('Key', (-1.4, -1.8, 2.0), 240, 2.2, (1.0, 0.88, 0.72))
add_light('Fill', (2.0, -1.2, 0.8), 70, 2.6, (0.80, 0.86, 1.0))
add_light('Rim', (0.3, 1.8, 1.5), 130, 1.6, (1.0, 0.92, 0.80))

cam_data = bpy.data.cameras.new('Cam')
cam_data.lens = 60
cam = bpy.data.objects.new('Cam', cam_data)
bpy.context.collection.objects.link(cam)
cam.location = Vector((0.55, -1.85, 0.62))
d = Vector((0.0, 0.0, 0.45)) - cam.location
cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
scene.camera = cam

scene.render.engine = 'CYCLES'
scene.cycles.device = 'CPU'
scene.cycles.use_denoising = True
scene.view_settings.view_transform = 'Standard'


def set_action(name):
    act = bpy.data.actions.get(name)
    arm.animation_data.action = act
    # 槽位动作（Blender 5）：action 必须绑到 slot，否则姿态不生效
    try:
        slots = list(getattr(act, 'slots', []))
        if slots:
            arm.animation_data.action_slot = slots[0]
    except Exception as exc:
        print('[fruit] slot 绑定跳过:', exc)


def render_still(action_name, frame, path, samples=40):
    set_action(action_name)
    scene.frame_set(frame)
    scene.render.resolution_x = 800
    scene.render.resolution_y = 800
    scene.cycles.samples = samples
    scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print('[fruit] preview ->', path)


render_still('Idle', 0, os.path.join(OUT_PREVIEW, 'hero.png'))
render_still('Happy', 14, os.path.join(OUT_PREVIEW, 'happy_peak.png'))
render_still('Blink', 2, os.path.join(OUT_PREVIEW, 'blink_closed.png'))
print('[fruit] DONE')
