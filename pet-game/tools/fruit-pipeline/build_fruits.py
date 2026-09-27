"""水果流水线：橘子 / 西瓜 / 蓝莓 / 火龙果 —— 批量构建（Blender 5.2 headless）。

草莓（build_strawberry.py）是打样板，定下家族规范：
    闭合旋转曲面身体 + 贴图花纹、3D 大眼（双眼高光点）、ω 贴面猫嘴、腮红、
    软萌帽子/鳍叶（零尖角）、4 骨轻绑定（Body/Calyx/EyeL/EyeR，Body 埋地 = 挤压回弹以地面为锚）、
    Idle 4s 呼吸 / Happy 1.67s 跳跃 / Blink 0.25s —— 剪辑名与 PetGameRoot 契约一致。

本脚本按同一套框架一次产出剩余四只：
    blender --background --python build_fruits.py -- <assets_fruit_dir> [预览目录]
产出：
    <assets_fruit_dir>/<name>/<name>.glb   （orange / watermelon / blueberry / dragonfruit）
    <preview>/<name>/hero.png              （每只一张 Cycles 定妆图）
    <work>/<name>.blend                    （可继续迭代的工程）

草莓轮实证过的坑全部继承（详见 build_strawberry.py 注释）：
    贴图走纯 Python PNG + images.load；pixels 是线性色接口；嘴线/腮红逐点贴面生成；
    蒙皮网格不挂物体级缩放；导出前骨架与全部 MESH 一起选中；ACTIONS + NLA 推轨。
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
ASSET_DIR = argv[0]
OUT_PREVIEW = argv[1] if len(argv) > 1 else os.path.join(os.path.dirname(ASSET_DIR), '_preview')
OUT_WORK = os.path.join(os.path.dirname(OUT_PREVIEW), '_work')
os.makedirs(ASSET_DIR, exist_ok=True)
os.makedirs(OUT_PREVIEW, exist_ok=True)
os.makedirs(OUT_WORK, exist_ok=True)

FPS = 24
IDLE_FRAMES = 96      # 4.00s
HAPPY_FRAMES = 40     # 1.67s
BLINK_FRAMES = 6      # 0.25s

# ---------------------------------------------------------------- 基础工具（草莓板同款）
def hex_srgb(h):
    return ((h >> 16 & 0xFF) / 255.0, (h >> 8 & 0xFF) / 255.0, (h & 0xFF) / 255.0)


def write_png(path, arr):
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
    img = bpy.data.images.load(path)
    img.pack()
    return img


def solid_texture(name, color, w=64):
    arr = np.zeros((w, w, 3), dtype=np.uint8)
    arr[..., 0] = int(color >> 16 & 0xFF)
    arr[..., 1] = int(color >> 8 & 0xFF)
    arr[..., 2] = int(color & 0xFF)
    path = os.path.join(OUT_WORK, name + '.png')
    write_png(path, arr)
    return load_texture(path)


def make_material(name, img, rough=0.5):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = next(n for n in mat.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
    tex = mat.node_tree.nodes.new('ShaderNodeTexImage')
    tex.image = img
    mat.node_tree.links.new(tex.outputs['Color'], bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value = rough
    return mat


def smooth(ob):
    for poly in ob.data.polygons:
        poly.use_smooth = True


def make_profile(points):
    """旋转曲面轮廓：t ∈ [0,1]（0=底 1=顶）→ 半径，Catmull-Rom 保 C1 连续。"""
    ts = [p[0] for p in points]
    rs = [p[1] for p in points]

    def radius(t):
        t = min(max(t, ts[0]), ts[-1])
        lo, hi = 0, len(ts) - 1
        while hi - lo > 1:
            mid = (lo + hi) // 2
            if ts[mid] <= t:
                lo = mid
            else:
                hi = mid
        u = (t - ts[lo]) / (ts[hi] - ts[lo])
        p1, p2 = rs[lo], rs[hi]
        p0 = rs[max(lo - 1, 0)]
        p3 = rs[min(hi + 1, len(rs) - 1)]
        return 0.5 * ((2 * p1) + (-p0 + p2) * u
                      + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u * u
                      + (-p0 + 3 * p1 - 3 * p2 + p3) * u * u * u)
    return radius


def add_sphere_part(name, loc, scale, mat):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24, ring_count=16, radius=1.0, location=(0, 0, 0))
    ob = bpy.context.active_object
    ob.name = name
    for v in ob.data.vertices:
        v.co = Vector((v.co.x * scale[0], v.co.y * scale[1], v.co.z * scale[2])) + Vector(loc)
    ob.data.materials.append(mat)
    smooth(ob)
    return ob


def build_body(name, profile, body_h, mat, segments=64, ring_count=40):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=ring_count, radius=1.0, location=(0, 0, 0))
    ob = bpy.context.active_object
    ob.name = name
    for v in ob.data.vertices:
        x, y, z = v.co
        t = (z + 1.0) / 2.0
        horiz = math.hypot(x, y)
        d = Vector((x, y, 0)) / horiz if horiz > 1e-5 else Vector((0, 0, 0))
        r = profile(t)
        v.co = (d.x * r, d.y * r, t * body_h)
    ob.data.materials.append(mat)
    smooth(ob)
    return ob


def build_leaf(name, pos, direction, length, width, thick, mat):
    """豆叶/鳍：椭球体烘缩放（+Z 为长轴），物体级旋转指向 direction 后平移到 pos。
    蒙皮网格允许物体级旋转（草莓腮红已实证），但不允许物体级缩放。"""
    ob = add_sphere_part(name, (0, 0, 0), (width, thick, length), mat)
    d = Vector(direction).normalized()
    ob.rotation_euler = d.to_track_quat('Z', 'Y').to_euler()
    ob.location = Vector(pos)
    return ob


def surface_point(profile, body_h, alpha_deg, z, proud=0.008):
    a = math.radians(alpha_deg)
    r = profile(min(max(z / body_h, 0.0), 1.0)) + proud
    return Vector((math.sin(a) * r, -math.cos(a) * r, z))


def build_eyes(profile, body_h, eye_z, spread, eye_mat, hl_mat):
    """大眼 + 双眼同侧高光点（家族统一五官；小果子同规格眼睛 = QQ 式大头萌）。"""
    t = eye_z / body_h
    r = profile(t)
    y = -math.sqrt(max(r * r - spread * spread, 1e-4)) + 0.017
    parts = []
    centers = []
    for side, x in (('L', -1), ('R', 1)):
        c = Vector((spread * x, y, eye_z))
        centers.append(c)
        parts.append(add_sphere_part(f'Eye{side}', tuple(c), (0.080, 0.060, 0.095), eye_mat))
        parts.append(add_sphere_part(f'HL{side}', tuple(c + Vector((-0.024, -0.058, 0.038))),
                                     (0.026, 0.017, 0.026), hl_mat))
    return parts, centers


def build_smile(profile, body_h, base_z, alpha_deg=11, dip=0.016, tube=0.010):
    """ω 猫嘴：路径逐点贴面生成（每点按自身高度取轮廓半径 + 外凸量），倒角成圆管。"""
    def sp(alpha, z):
        return surface_point(profile, body_h, alpha, z)

    pts = []
    n = 8
    for i in range(n + 1):
        k = i / n
        pts.append(sp(-alpha_deg + alpha_deg * k, base_z - math.sin(k * math.pi) * dip))
    for i in range(1, n + 1):
        k = i / n
        pts.append(sp(alpha_deg * k, base_z - math.sin(k * math.pi) * dip))
    cu = bpy.data.curves.new('MouthPath', 'CURVE')
    cu.dimensions = '3D'
    spl = cu.splines.new('POLY')
    spl.points.add(len(pts) - 1)
    for i, p in enumerate(pts):
        spl.points[i].co = (*p, 1.0)
    cu.bevel_depth = tube
    cu.bevel_resolution = 4
    cu.use_fill_caps = True
    cu.materials.append(bpy.data.materials['mouth'])
    ob = bpy.data.objects.new('Mouth', cu)
    bpy.context.collection.objects.link(ob)
    bpy.ops.object.select_all(action='DESELECT')
    ob.select_set(True)
    bpy.context.view_layer.objects.active = ob
    bpy.ops.object.convert(target='MESH')
    smooth(ob)
    return ob


def build_blush_pair(profile, body_h, z, angle_deg, mat, scale=(0.062, 0.014, 0.042)):
    parts = []
    for side, x in (('L', -1), ('R', 1)):
        a = math.radians(angle_deg)
        nrm = Vector((math.sin(a) * x, -math.cos(a), 0))
        # 半径按腮红自己的高度取（草莓 v5 教训：取错高度会悬空隐形）
        pos = nrm * (profile(z / body_h) - 0.004) + Vector((0, 0, z))
        blush = add_sphere_part(f'Blush{side}', tuple(pos), scale, mat)
        blush.rotation_euler = nrm.to_track_quat('Y', 'Z').to_euler()
        parts.append(blush)
    return parts


# ---------------------------------------------------------------- 各果贴图
def grad_grid():
    W = H = 1024
    yy, xx = np.mgrid[0:H, 0:W]
    u = (xx + 0.5) / W
    v = (yy + 0.5) / H
    t = (np.cos(np.pi * v) + 1.0) / 2.0
    return u, v, t


def finish_texture(name, px):
    path = os.path.join(OUT_WORK, name + '.png')
    write_png(path, (np.clip(px, 0.0, 1.0) * 255).astype(np.uint8))
    return load_texture(path)


def make_orange_texture():
    """橙皮：上亮下深的橘色渐变 + 细颗粒噪点（果皮质感）。"""
    u, v, t = grad_grid()
    top = np.array(hex_srgb(0xFFA53E))
    bot = np.array(hex_srgb(0xF08A1F))
    base = bot + (top - bot) * t[..., None]
    base += (np.clip((t - 0.70) / 0.20, 0, 1) * 0.035)[..., None]
    rng = np.random.default_rng(11)
    base *= (1.0 + rng.normal(0, 0.016, t.shape))[..., None]
    return finish_texture('orange_base', base)


def make_watermelon_texture():
    """瓜皮：浅绿底 + 7 条波浪墨绿条纹（u 向环绕，条纹顺极向走）。"""
    u, v, t = grad_grid()
    top = np.array(hex_srgb(0x9AD86A))
    bot = np.array(hex_srgb(0x71C24A))
    base = bot + (top - bot) * t[..., None]
    dark = np.array(hex_srgb(0x3E7A26))
    uu = u + 0.05 * np.sin(2 * math.pi * (v * 2.5)) + 0.018 * np.sin(2 * math.pi * (u * 5 + v * 7))
    s = np.abs(((uu * 7.0) % 1.0) - 0.5) * 2.0
    x = np.clip((s - 0.45) / 0.30, 0.0, 1.0)
    mask = 1.0 - (x * x * (3 - 2 * x))
    px = base * (1 - mask[..., None] * 0.88) + dark * (mask[..., None] * 0.88)
    return finish_texture('watermelon_base', px)


def make_blueberry_texture():
    """蓝莓：果粉蓝渐变（顶部泛霜）+ 极轻噪点。"""
    u, v, t = grad_grid()
    top = np.array(hex_srgb(0x7A9CE4))
    bot = np.array(hex_srgb(0x5A7CC4))
    base = bot + (top - bot) * t[..., None]
    base += (np.clip((t - 0.60) / 0.30, 0, 1) * 0.03)[..., None]
    rng = np.random.default_rng(5)
    base *= (1.0 + rng.normal(0, 0.010, t.shape))[..., None]
    return finish_texture('blueberry_base', base)


def make_dragonfruit_texture():
    """火龙果：品红渐变 + 微噪点（鳞片走几何，不上贴图）。"""
    u, v, t = grad_grid()
    top = np.array(hex_srgb(0xF25A90))
    bot = np.array(hex_srgb(0xE93B72))
    base = bot + (top - bot) * t[..., None]
    rng = np.random.default_rng(3)
    base *= (1.0 + rng.normal(0, 0.012, t.shape))[..., None]
    return finish_texture('dragonfruit_base', base)


# ---------------------------------------------------------------- 四只果的定义
def build_orange():
    """橘子 · 滚动吃货：矮胖球身 + 顶上双叶嫩芽。"""
    prof = make_profile([(0.00, 0.020), (0.08, 0.240), (0.20, 0.345), (0.50, 0.400),
                         (0.80, 0.345), (0.92, 0.240), (1.00, 0.020)])
    h = 0.76
    parts = [build_body('Orange', prof, h, make_material('orange', img=make_orange_texture(), rough=0.45))]
    eye_parts, eye_centers = build_eyes(prof, h, 0.460, 0.150, bpy.data.materials['eye'],
                                        bpy.data.materials['highlight'])
    parts += eye_parts
    parts.append(build_smile(prof, h, 0.400, alpha_deg=10))
    parts += build_blush_pair(prof, h, 0.435, 18, bpy.data.materials['blush'])
    hat = [add_sphere_part('Stem', (0, 0, h + 0.048), (0.020, 0.020, 0.046),
                           bpy.data.materials['stem'])]
    hat.append(build_leaf('LeafL', (-0.040, -0.006, h + 0.085), (-0.62, -0.20, 0.76),
                          0.088, 0.032, 0.054, bpy.data.materials['scallop']))
    hat.append(build_leaf('LeafR', (0.040, -0.006, h + 0.085), (0.62, -0.20, 0.76),
                          0.088, 0.032, 0.054, bpy.data.materials['scallop']))
    return {'parts': parts, 'hat': hat, 'eye_centers': eye_centers, 'body_h': h,
            'hat_top': h + 0.16, 'max_r': 0.40}


def build_watermelon():
    """西瓜 · 躺平大师：宽扁大圆肚（比谁都宽）+ 短粗瓜蒂和小叶。"""
    prof = make_profile([(0.00, 0.030), (0.12, 0.360), (0.30, 0.460), (0.50, 0.480),
                         (0.70, 0.460), (0.88, 0.340), (1.00, 0.040)])
    h = 0.62
    parts = [build_body('Watermelon', prof, h,
                        make_material('watermelon', img=make_watermelon_texture(), rough=0.42))]
    eye_parts, eye_centers = build_eyes(prof, h, 0.372, 0.160, bpy.data.materials['eye'],
                                        bpy.data.materials['highlight'])
    parts += eye_parts
    parts.append(build_smile(prof, h, 0.340, alpha_deg=12))
    parts += build_blush_pair(prof, h, 0.385, 20, bpy.data.materials['blush'],
                              scale=(0.070, 0.015, 0.046))
    hat = []
    bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=0.026, depth=0.090,
                                        location=(0.006, 0.003, h + 0.035))
    stem = bpy.context.active_object
    stem.name = 'Stem'
    stem.rotation_euler = Euler((math.radians(8), 0, math.radians(12)), 'XYZ')
    stem.data.materials.append(bpy.data.materials['stem'])
    smooth(stem)
    hat.append(stem)
    hat.append(build_leaf('Leaf', (0.045, 0.000, h + 0.105), (0.55, -0.25, 0.80),
                          0.100, 0.034, 0.058, bpy.data.materials['scallop']))
    return {'parts': parts, 'hat': hat, 'eye_centers': eye_centers, 'body_h': h,
            'hat_top': h + 0.18, 'max_r': 0.48}


def build_blueberry():
    """蓝莓 · 安静小不点：小圆身 + 迷你星形果蒂帽（圆角化）。"""
    prof = make_profile([(0.00, 0.020), (0.10, 0.235), (0.30, 0.315), (0.50, 0.335),
                         (0.70, 0.315), (0.90, 0.215), (1.00, 0.020)])
    h = 0.62
    parts = [build_body('Blueberry', prof, h,
                        make_material('blueberry', img=make_blueberry_texture(), rough=0.40))]
    eye_parts, eye_centers = build_eyes(prof, h, 0.372, 0.115, bpy.data.materials['eye'],
                                        bpy.data.materials['highlight'])
    parts += eye_parts
    parts.append(build_smile(prof, h, 0.310, alpha_deg=9, dip=0.013, tube=0.009))
    parts += build_blush_pair(prof, h, 0.400, 16, bpy.data.materials['blush'],
                              scale=(0.052, 0.012, 0.036))
    hat = [add_sphere_part('HatCap', (0, 0, h - 0.005), (0.052, 0.052, 0.036),
                           bpy.data.materials['cap'])]
    for k in range(5):
        a = k * (2 * math.pi / 5) + math.pi / 5
        hat.append(add_sphere_part(f'HatBump{k}',
                                   (0.042 * math.cos(a), 0.042 * math.sin(a), h - 0.002),
                                   (0.024, 0.024, 0.020), bpy.data.materials['scallop']))
    hat.append(add_sphere_part('HatStemNub', (0.002, 0.001, h + 0.038), (0.015, 0.015, 0.015),
                               bpy.data.materials['stem']))
    return {'parts': parts, 'hat': hat, 'eye_centers': eye_centers, 'body_h': h,
            'hat_top': h + 0.06, 'max_r': 0.335}


def build_dragonfruit():
    """火龙果 · 中二戏精：鹅蛋身 + 绿色饱满鳍叶（身侧一圈 + 头顶三片王冠）。"""
    prof = make_profile([(0.00, 0.030), (0.10, 0.240), (0.28, 0.330), (0.50, 0.355),
                         (0.72, 0.325), (0.90, 0.215), (1.00, 0.025)])
    h = 0.80
    parts = [build_body('Dragonfruit', prof, h,
                        make_material('dragonfruit', img=make_dragonfruit_texture(), rough=0.42))]
    eye_parts, eye_centers = build_eyes(prof, h, 0.496, 0.130, bpy.data.materials['eye'],
                                        bpy.data.materials['highlight'])
    parts += eye_parts
    parts.append(build_smile(prof, h, 0.425, alpha_deg=10))
    parts += build_blush_pair(prof, h, 0.450, 18, bpy.data.materials['blush'])
    body_fins = []
    # 鳞环避开正面脸区（±50° 内不留鳍，否则读成耳朵）；只在侧后方披鳞
    for k, a_deg in enumerate((50, 130, 180, 230, 310)):
        a = math.radians(a_deg)
        z = 0.520
        r = prof(z / h) - 0.008
        pos = (math.sin(a) * r, -math.cos(a) * r, z)
        direction = (math.sin(a) * 0.55, -math.cos(a) * 0.55, 0.80)
        body_fins.append(build_leaf(f'Fin{k}', pos, direction, 0.130, 0.034, 0.062,
                                    bpy.data.materials['stem']))
    top_fins = []
    for k, a_deg in enumerate((-40, 80, 200)):
        a = math.radians(a_deg)
        z = 0.760
        r = prof(z / h) - 0.006
        pos = (math.sin(a) * r, -math.cos(a) * r, z)
        direction = (math.sin(a) * 0.40, -math.cos(a) * 0.40, 1.0)
        top_fins.append(build_leaf(f'CrownFin{k}', pos, direction, 0.140, 0.036, 0.066,
                                   bpy.data.materials['scallop']))
    return {'parts': parts, 'hat': top_fins, 'body_fins': body_fins,
            'eye_centers': eye_centers, 'body_h': h, 'hat_top': 0.94, 'max_r': 0.355}


FRUIT_BUILDERS = {
    'orange': build_orange,
    'watermelon': build_watermelon,
    'blueberry': build_blueberry,
    'dragonfruit': build_dragonfruit,
}


# ---------------------------------------------------------------- 场景复位 / 绑定 / 动画 / 导出 / 预览
def reset_scene():
    for obj in list(bpy.context.scene.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for act in list(bpy.data.actions):
        bpy.data.actions.remove(act)
    for block in (bpy.data.meshes, bpy.data.materials, bpy.data.images,
                  bpy.data.armatures, bpy.data.curves):
        for item in list(block):
            if item.users == 0:
                block.remove(item)
    bpy.context.scene.render.fps = FPS


def build_rig(body_h, eye_centers, hat_top):
    arm_data = bpy.data.armatures.new('FruitRig')
    arm = bpy.data.objects.new('FruitRig', arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    eb = arm_data.edit_bones
    body_bone = eb.new('Body')
    body_bone.head = (0, 0, 0.02)
    body_bone.tail = (0, 0, body_h * 1.04)
    calyx = eb.new('Calyx')
    calyx.head = (0, 0, body_h * 0.88)
    calyx.tail = (0, 0, hat_top)
    calyx.parent = body_bone
    for side, c in zip(('L', 'R'), eye_centers):
        b = eb.new(f'Eye{side}')
        b.head = tuple(c)
        b.tail = (c.x, c.y, c.z + 0.025)
        b.parent = body_bone
    bpy.ops.object.mode_set(mode='OBJECT')
    return arm


def clear_pose(arm):
    for pb in arm.pose.bones:
        pb.rotation_mode = 'XYZ'
        pb.rotation_euler = Euler((0, 0, 0), 'XYZ')
        pb.location = Vector((0, 0, 0))
        pb.scale = Vector((1, 1, 1))


def key(arm, bone, frame, rot=None, scale=None, loc=None):
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


def build_actions(arm):
    if arm.animation_data:
        arm.animation_data.action = None
        for track in list(arm.animation_data.nla_tracks):
            arm.animation_data.nla_tracks.remove(track)

    clear_pose(arm)
    idle = bpy.data.actions.new('Idle')
    arm.animation_data_create()
    arm.animation_data.action = idle
    for f in range(0, IDLE_FRAMES + 1, 3):
        t = f / IDLE_FRAMES
        br = math.sin(2 * math.pi * t * 2)
        dr = math.sin(2 * math.pi * t)
        dr2 = math.sin(2 * math.pi * t + 0.9)
        key(arm, 'Body', f, scale=(1 - 0.016 * br, 1 + 0.028 * br, 1 - 0.016 * br),
            rot=(0.8 * dr2, 0, 0.6 * dr))
        key(arm, 'Calyx', f, rot=(2.8 * dr, 0, 3.2 * dr2))
    smooth_curves(idle)
    set_range(idle, 0, IDLE_FRAMES)

    clear_pose(arm)
    happy = bpy.data.actions.new('Happy')
    arm.animation_data.action = happy
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
            key(arm, bone, frame, rot=rot, scale=scale, loc=loc)
    smooth_curves(happy)
    set_range(happy, 0, HAPPY_FRAMES)

    clear_pose(arm)
    blink = bpy.data.actions.new('Blink')
    arm.animation_data.action = blink
    for f, sy in ((0, 1.0), (2, 0.05), (3, 0.05), (6, 1.0)):
        key(arm, 'EyeL', f, scale=(1, sy, 1))
        key(arm, 'EyeR', f, scale=(1, sy, 1))
    smooth_curves(blink)
    set_range(blink, 0, BLINK_FRAMES)

    arm.animation_data.action = idle
    for act in (idle, happy, blink):
        track = arm.animation_data.nla_tracks.new()
        track.name = act.name
        strip = track.strips.new(act.name, int(act.frame_start), act)
        strip.name = act.name
        track.mute = True
    clear_pose(arm)
    return idle


def export_glb(arm, out_glb):
    os.makedirs(os.path.dirname(out_glb), exist_ok=True)
    bpy.ops.object.select_all(action='DESELECT')
    arm.select_set(True)
    for ob in bpy.context.scene.objects:
        if ob.type == 'MESH':
            ob.select_set(True)
    bpy.context.view_layer.objects.active = arm
    bpy.ops.export_scene.gltf(
        filepath=out_glb,
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
    print('[fruit] exported', out_glb, os.path.getsize(out_glb) / 1048576.0, 'MB')


def first_node(tree, kind):
    for n in tree.nodes:
        if n.type == kind:
            return n
    return None


def render_hero(name, body_h, max_r, outdir):
    scene = bpy.context.scene
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

    def add_light(nm, loc, energy, size, color=(1, 1, 1)):
        ld = bpy.data.lights.new(nm, 'AREA')
        ld.energy = energy
        ld.color = color
        ld.size = size
        ob = bpy.data.objects.new(nm, ld)
        bpy.context.collection.objects.link(ob)
        ob.location = Vector(loc)
        d = Vector((0, 0, body_h * 0.52)) - ob.location
        ob.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()

    add_light('Key', (-1.4, -1.8, body_h * 2.4), 240, 2.2, (1.0, 0.88, 0.72))
    add_light('Fill', (2.0, -1.2, body_h * 1.0), 70, 2.6, (0.80, 0.86, 1.0))
    add_light('Rim', (0.3, 1.8, body_h * 1.8), 130, 1.6, (1.0, 0.92, 0.80))
    cam_data = bpy.data.cameras.new('Cam')
    cam_data.lens = 55
    cam = bpy.data.objects.new('Cam', cam_data)
    bpy.context.collection.objects.link(cam)
    cam.location = Vector((0.55, -(1.35 + max_r * 1.35), body_h * 0.78))
    d = Vector((0.0, 0.0, body_h * 0.52)) - cam.location
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam
    scene.render.engine = 'CYCLES'
    scene.cycles.device = 'CPU'
    scene.cycles.use_denoising = True
    scene.view_settings.view_transform = 'Standard'

    act = bpy.data.actions.get('Idle')
    arm = bpy.data.objects['FruitRig']
    arm.animation_data.action = act
    try:
        slots = list(getattr(act, 'slots', []))
        if slots:
            arm.animation_data.action_slot = slots[0]
    except Exception as exc:
        print('[fruit] slot 绑定跳过:', exc)
    scene.frame_set(0)
    out = os.path.join(outdir, name, 'hero.png')
    os.makedirs(os.path.dirname(out), exist_ok=True)
    scene.render.resolution_x = 800
    scene.render.resolution_y = 800
    scene.cycles.samples = 40
    scene.render.filepath = out
    bpy.ops.render.render(write_still=True)
    print('[fruit] preview ->', out)


GROUP_OF_BASE = {'EyeL': 'EyeL', 'EyeR': 'EyeR', 'HLL': 'EyeL', 'HLR': 'EyeR',
                 'Mouth': 'Body', 'BlushL': 'Body', 'BlushR': 'Body'}

for NAME, builder in FRUIT_BUILDERS.items():
    reset_scene()
    bpy.context.scene.render.fps = FPS

    # 家族共用部件材质（每只果重建一次，随场景复位清理）；贴图一律捕获返回值传参，
    # 不做 bpy.data.images 字典查找（load 后的数据块名带扩展名，键名不可靠）
    eye_img = solid_texture('eye', 0x1A1110)
    hl_img = solid_texture('highlight', 0xFFFFFF)
    mouth_img = solid_texture('mouth', 0x6E2833)
    blush_img = solid_texture('blush', 0xEE5F8D)
    cap_img = solid_texture('cap', 0x6FB14E)
    scallop_img = solid_texture('scallop', 0x82C25C)
    stem_img = solid_texture('stem', 0x5CA24E)
    eye_mat = make_material('eye', eye_img, rough=0.12)
    hl_mat = make_material('highlight', hl_img, rough=0.08)
    mouth_mat = make_material('mouth', mouth_img, rough=0.5)
    blush_mat = make_material('blush', blush_img, rough=0.6)
    cap_mat = make_material('cap', cap_img, rough=0.55)
    scallop_mat = make_material('scallop', scallop_img, rough=0.55)
    stem_mat = make_material('stem', stem_img, rough=0.55)

    fruit = builder()
    parts = fruit['parts'] + fruit['hat'] + fruit.get('body_fins', [])

    arm = build_rig(fruit['body_h'], fruit['eye_centers'], fruit['hat_top'])
    group_map = dict(GROUP_OF_BASE)
    group_map[fruit['parts'][0].name] = 'Body'      # 果身（Orange/Watermelon/...）
    for ob in fruit['hat']:
        group_map[ob.name] = 'Calyx'
    for ob in fruit.get('body_fins', []):
        group_map[ob.name] = 'Body'
    for ob in parts:
        vg = ob.vertex_groups.new(name=group_map[ob.name])
        vg.add(list(range(len(ob.data.vertices))), 1.0, 'REPLACE')
        ob.parent = arm
        mod = ob.modifiers.new('Armature', 'ARMATURE')
        mod.object = arm

    build_actions(arm)
    blend_path = os.path.join(OUT_WORK, NAME + '.blend')
    bpy.ops.wm.save_as_mainfile(filepath=blend_path)

    out_glb = os.path.join(ASSET_DIR, NAME, NAME + '.glb')
    export_glb(arm, out_glb)
    render_hero(NAME, fruit['body_h'], fruit['max_r'], OUT_PREVIEW)

print('[fruit] ALL DONE')
