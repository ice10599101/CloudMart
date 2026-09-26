"""QQ 宠物风格新猫（qqcat-prod.glb）绑骨 + 原生尾绑定 + 眨眼 + 检查渲染。

与 rig_cat_v1_tail.py 的差异：
  - 这只猫的尾巴是**图生 3D 原生建模**（+X 侧地面卷尾，x 0.18..0.36 / y 0.19..0.48 /
    z≤0.148），不需要删尾/重建 —— 尾骨沿实测路径摆放，距离场 + 门控直接绑；
  - 眼球来自 probe_eye_qq.py 的视觉射线拾取（eye_pick.json 契约，r≈0.041 双眼等大）；
  - 眼皮：cap 1.05R / 半角 50° / rest 预旋 -100° 藏进头骨 / 闭眼 +160°（v2 全部教训）；
    毛色 uv 探针用"低饱和亮色"判据（这只猫是奶油毛，v2 的灰毛判据会误杀）；
  - 耳骨**从几何自动量测**（z>0.62 的突出部按 ±x 分簇取质心），换模型不用重测。

运行：
    blender --background --python rig_qqcat.py -- <in.glb> <out.blend> <check_dir>
"""
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Euler, Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
GLB, OUT_BLEND, CHECK = argv[0], argv[1], argv[2]
os.makedirs(CHECK, exist_ok=True)
os.makedirs(os.path.dirname(OUT_BLEND), exist_ok=True)

# ------------------------------------------------------------------ 常量（实测）
# 身高 0.8028，脸朝 -Y；眼 r≈0.041（probe_eye_qq 边界拟合）
EYE_RADIUS = 0.041
EYELID_REST_DEG = -100.0
EYELID_CLOSED_DEG = 145.0
EYELID_HALF_ANGLE = 38.0    # 月牙形上眼睑：半角 50° 的碗在纯色贴图上读作凸球（已实测），38° 是"眼睑线"
# 尾巴路径（probe_qqcat 实测：+X 侧地面卷尾）
TAIL_PATH = [
    (0.16, 0.22, 0.09),
    (0.28, 0.33, 0.07),
    (0.33, 0.44, 0.08),
    (0.30, 0.47, 0.12),
    (0.24, 0.44, 0.14),
]
TAIL_RADII = [0.050, 0.046, 0.042, 0.036, 0.028]

MAX_INFLUENCE = 4
SMOOTH_ITERS = 8
NEIGH_CELL = 0.022
NEIGH_RADIUS = 0.032
NEIGH_MAX = 12


def smoothstep(lo, hi, x):
    if hi <= lo:
        return 0.0 if x < lo else 1.0
    t = (x - lo) / (hi - lo)
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def seg_dist(p, a, b):
    ab = b - a
    denom = ab.dot(ab)
    t = 0.0 if denom < 1e-12 else max(0.0, min(1.0, (p - a).dot(ab) / denom))
    return (p - (a + ab * t)).length


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_model():
    bpy.ops.import_scene.gltf(filepath=GLB)
    mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
    bpy.context.view_layer.objects.active = mesh
    mesh.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return mesh


def per_vertex_uv(mesh):
    uv = np.zeros((len(mesh.data.vertices), 2), dtype=np.float64)
    layer = mesh.data.uv_layers.active.data
    for poly in mesh.data.polygons:
        for li in poly.loop_indices:
            vi = mesh.data.loops[li].vertex_index
            uv[vi] = layer[li].uv[:]
    return uv


def fit_sphere(pts):
    A = np.hstack([2.0 * pts, np.ones((len(pts), 1))])
    b = (pts ** 2).sum(axis=1)
    sol, *_ = np.linalg.lstsq(A, b, rcond=None)
    c = sol[:3]
    r = float(np.sqrt(max(sol[3] + float(c @ c), 1e-9)))
    return c, r


def sphere_cap(center, radius, half_angle_deg, rings=8, segs=20):
    half = math.radians(half_angle_deg)
    verts = [tuple(center + np.array([0.0, 0.0, radius]))]
    faces = []
    ring_start = []
    for r in range(1, rings + 1):
        th = half * r / rings
        ring_start.append(len(verts))
        for s in range(segs):
            ph = 2.0 * math.pi * s / segs
            verts.append(tuple(center + radius * np.array([
                math.sin(th) * math.cos(ph), math.sin(th) * math.sin(ph), math.cos(th)])))
    for s in range(segs):
        faces.append((0, ring_start[0] + s, ring_start[0] + (s + 1) % segs))
    for r in range(rings - 1):
        a0, b0 = ring_start[r], ring_start[r + 1]
        for s in range(segs):
            s2 = (s + 1) % segs
            faces.append((a0 + s, b0 + s, b0 + s2, a0 + s2))
    return verts, faces


def tex_sampler(mesh):
    """uv -> 线性RGB 采样函数（无贴图时 None）。"""
    mat = mesh.data.materials[0] if mesh.data.materials else None
    img = None
    if mat and mat.use_nodes:
        for n in mat.node_tree.nodes:
            if n.type == 'TEX_IMAGE' and n.image:
                img = n.image
                break
    if img is None:
        return None
    W, H = img.size
    px = np.empty(W * H * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    px = px.reshape(-1, 4)[:, :3]

    def sample(uv):
        xi = np.clip((uv[:, 0] * W).astype(int), 0, W - 1)
        yi = np.clip((uv[:, 1] * H).astype(int), 0, H - 1)
        return px[yi * W + xi]

    return sample


def detect_eyes(mesh):
    """眼球定位：只认 eye_pick.json（probe_eye_qq.py 视觉射线拾取，人工核对过标记）。
    没有该文件就明确放弃 —— 不猜坐标（悬浮球教训）。"""
    pick_path = os.path.join(CHECK, 'eye_pick.json')
    if not os.path.isfile(pick_path):
        print('[eyes] 无 eye_pick.json，眼皮跳过')
        return []
    with open(pick_path, encoding='utf-8') as fh:
        pick = json.load(fh)
    got = pick.get('eyes', [])
    if len(got) != 2:
        print('[eyes] eye_pick.json 眼数 != 2，眼皮跳过')
        return []
    eyes = []
    for e in sorted(got, key=lambda e: -e['center'][0]):
        eyes.append((np.array(e['center']), float(e.get('radius') or EYE_RADIUS)))
        print('[eyes] %s 中心 %s 半径 %.4f' % (e['tag'], np.round(e['center'], 4).tolist(),
                                              float(e.get('radius') or EYE_RADIUS)))
    return eyes


def measure_ears(co):
    """耳骨自动量测：z>0.62 的突出部按 ±x 分簇取质心。
    返回 [(名字, head, tail)]，失败返回空（耳朵跟随 Head 兜底）。"""
    top = co[co[:, 2] > 0.62]
    if len(top) < 40:
        print('[ears] z>0.62 顶点不足（%d），耳骨跳过' % len(top))
        return []
    out = []
    for tag, m in (('+X', top[:, 0] > 0.02), ('-X', top[:, 0] <= -0.02)):
        p = top[m]
        if len(p) < 15:
            continue
        c = p.mean(axis=0)
        tip_z = float(p[:, 2].max())
        name = 'EarL1' if tag == '+X' else 'EarR1'
        # 骨骼：head 在耳根（头顶高度），tail 在簇质心偏上
        base_z = 0.60
        head = np.array([c[0] * 0.7, c[1] * 0.7, base_z])
        tail = np.array([c[0], c[1], max(c[2], tip_z - 0.04)])
        out.append((name, head, tail))
        print('[ears] %s 质心 %s（%d 点）head %s tail %s'
              % (name, np.round(c, 3).tolist(), len(p),
                 np.round(head, 3).tolist(), np.round(tail, 3).tolist()))
    return out


# ------------------------------------------------------------------ 骨架
def build_armature(ear_bones, eyelid_centers):
    arm_data = bpy.data.armatures.new('CatArmature')
    arm = bpy.data.objects.new('CatRig', arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    eb = arm_data.edit_bones

    def add(name, parent, head, tail, connect, deform):
        b = eb.new(name)
        b.head, b.tail = Vector(head), Vector(tail)
        b.use_deform = deform
        if parent:
            b.parent = eb[parent]
            b.use_connect = connect
        return b

    add('Root', None, (0.00, 0.05, 0.00), (0.00, 0.05, 0.08), False, False)
    add('Hips', 'Root', (0.02, 0.12, 0.10), (0.02, 0.11, 0.20), False, True)
    add('Spine', 'Hips', (0.02, 0.11, 0.20), (0.02, 0.09, 0.28), True, True)
    add('Chest', 'Spine', (0.02, 0.09, 0.28), (0.02, 0.04, 0.34), True, True)
    add('Neck', 'Chest', (0.02, 0.04, 0.34), (0.02, 0.00, 0.38), True, True)
    add('Head', 'Neck', (0.02, 0.00, 0.38), (0.01, -0.04, 0.55), True, True)
    for name, head, tail in ear_bones:
        add(name, 'Head', head, tail, False, True)
        add(name + '2', name, tail, (tail[0] + 0.01, tail[1] - 0.01, tail[2] + 0.05), True, True)
    # 尾巴：沿实测路径 5 节
    pts = np.array(TAIL_PATH)
    for k in range(len(pts) - 1):
        add('Tail%d' % (k + 1), 'Hips' if k == 0 else 'Tail%d' % k,
            pts[k], pts[k + 1], k > 0, True)
    # 眼皮骨：head=眼球中心、沿 +X（两根同向 → 眨眼绕骨局部 Y = 绕世界水平轴）
    for name, center in eyelid_centers:
        add(name, 'Head', center, (center[0] + 0.05, center[1], center[2]), False, True)
    bpy.ops.object.mode_set(mode='OBJECT')
    return arm


RADIUS = {
    'Hips': 0.28, 'Spine': 0.26, 'Chest': 0.22, 'Neck': 0.18, 'Head': 0.28,
    'EarL1': 0.090, 'EarL2': 0.070, 'EarR1': 0.090, 'EarR2': 0.070,
    # 尾巴贴地、离后腿 ≥0.12：0.07 裹住尾管又不抓腿
    'Tail1': 0.070, 'Tail2': 0.065, 'Tail3': 0.060, 'Tail4': 0.055, 'Tail5': 0.050,
    'EyelidL': 0.0, 'EyelidR': 0.0,
}

# 解剖门控（实测分层：臀 z<0.22，胸 0.22-0.34，头 0.40+，耳 0.62+，尾 z<0.15）
GATE = {
    'Head': ('above', 0.340, 0.410),
    'Neck': ('above', 0.290, 0.350),
    'EarL1': ('above', 0.600, 0.660), 'EarL2': ('above', 0.680, 0.740),
    'EarR1': ('above', 0.600, 0.660), 'EarR2': ('above', 0.680, 0.740),
    'Tail1': ('below', 0.220, 0.300),
    'Tail2': ('below', 0.220, 0.300),
    'Tail3': ('below', 0.220, 0.300),
    'Tail4': ('below', 0.220, 0.300),
    'Tail5': ('below', 0.220, 0.300),
}

# 空间锁：脸前缘（眼/口鼻在 y<-0.25，z 0.42-0.62）
LOCKS = [
    (lambda p: 0.42 < p.z < 0.62 and p.y < -0.30, 'Head', 1.0),
]


def gate_of(name, z):
    g = GATE.get(name)
    if not g:
        return 1.0
    kind, lo, hi = g
    s = smoothstep(lo, hi, z)
    return s if kind == 'above' else (1.0 - s)


def build_eyelids(mesh, eyes):
    """眼皮：cap 1.05R / 半角 50° / rest 预旋 -100° 藏进头骨。
    毛色 uv 探针：眉带内挑**低饱和亮色**（奶油毛）顶点 —— v2 的灰毛判据
    （|g-b|<0.09）会把奶油毛误杀，这里改成饱和度上限判据。"""
    if not eyes:
        return []
    co = np.array([v.co[:] for v in mesh.data.vertices])
    uv = per_vertex_uv(mesh)
    sample = tex_sampler(mesh)
    mat = mesh.data.materials[0] if mesh.data.materials else None
    th = math.radians(EYELID_REST_DEG)
    rx = np.array([[1.0, 0.0, 0.0],
                   [0.0, math.cos(th), -math.sin(th)],
                   [0.0, math.sin(th), math.cos(th)]])
    out = []
    for name, (center, radius) in zip(('EyelidL', 'EyelidR'), eyes):
        verts, faces = sphere_cap(center, radius * 1.05, EYELID_HALF_ANGLE, 8, 20)
        vv = (np.array(verts) - center) @ rx.T + center
        me = bpy.data.meshes.new(name + 'Mesh')
        me.from_pydata([tuple(v) for v in vv], [], faces)
        me.update()
        probe = center + np.array([0.0, 0.0, 0.045])
        band = (co[:, 2] > center[2] + 0.02) & (co[:, 2] < center[2] + 0.07) \
            & (np.abs(co[:, 0] - center[0]) < 0.08) & (co[:, 1] < center[1] + 0.04) \
            & (np.linalg.norm(co - center, axis=1) < 0.11)
        idx = np.where(band)[0]
        fur = None
        if len(idx) and sample is not None:
            rgb = sample(uv[idx])
            mx = rgb.max(axis=1)
            mn = rgb.min(axis=1)
            cream = ((mx - mn) < 0.20) & (rgb[:, 0] > 0.35) & (rgb[:, 0] < 0.92)
            cand = idx[cream]
            if len(cand):
                # 取"颜色最接近 band 中位色"的顶点 —— 只挑 cream 会偶发采到
                # 高光/亮斑区，眼皮比周围毛亮一圈、闭眼读作凸球（已实测踩到）
                med = np.median(rgb[cream], axis=0)
                dd = np.linalg.norm(rgb[cream] - med, axis=1)
                fur = int(cand[int(np.argmin(dd))])
        if fur is None and len(idx):
            dd = np.linalg.norm(co[idx] - probe, axis=1)
            fur = int(idx[int(np.argmin(dd))])
        if fur is None:
            fur = int(np.argmin(np.linalg.norm(co - probe, axis=1)))
        layer = me.uv_layers.new(name='UVMap')
        for loop in me.loops:
            layer.data[loop.index].uv = (float(uv[fur][0]), float(uv[fur][1]))
        for poly in me.polygons:
            poly.use_smooth = True
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        if mat:
            ob.data.materials.append(mat)
        out.append((name, ob, center))
        print('[eyes] %s 眼皮生成：中心 %s r=%.4f 毛色uv(%.3f,%.3f)'
              % (name, np.round(center, 4).tolist(), radius, uv[fur][0], uv[fur][1]))
    return out


def bind_eyelids(eyelids, arm):
    for name, ob, _center in eyelids:
        grp = ob.vertex_groups.new(name=name)
        grp.add([v.index for v in ob.data.vertices], 1.0, 'REPLACE')
        ob.parent = arm
        ob.matrix_parent_inverse = arm.matrix_world.inverted()
        mod = ob.modifiers.new('Armature', 'ARMATURE')
        mod.object = arm


def custom_weights(mesh, arm):
    body_exclude = ('Eyelid',)
    deform = [b for b in arm.data.bones
              if b.use_deform and not b.name.startswith(body_exclude)]
    names = [b.name for b in deform]
    idx = {n: i for i, n in enumerate(names)}
    segs = [(b.head_local.copy(), b.tail_local.copy()) for b in deform]
    radii = [RADIUS.get(n, 0.22) for n in names]
    nv = len(mesh.data.vertices)
    co = [v.co.copy() for v in mesh.data.vertices]

    W = []
    locked = 0
    for ci in range(nv):
        p = co[ci]
        forced = None
        for pred, bone, value in LOCKS:
            if pred(p):
                forced = (idx[bone], value)
                break
        if forced:
            locked += 1
            W.append({forced[0]: forced[1]})
            continue
        row = {}
        for bi in range(len(names)):
            d = seg_dist(p, segs[bi][0], segs[bi][1])
            r = radii[bi]
            if d >= r:
                continue
            g = gate_of(names[bi], p.z)
            if g <= 1e-4:
                continue
            row[bi] = ((1.0 - d / r) ** 3) * g
        if not row:
            best = min(range(len(names)), key=lambda bi: seg_dist(p, segs[bi][0], segs[bi][1]))
            row = {best: 1.0}
        W.append(row)
    print('[rig] 空间锁命中 %d 顶点' % locked)

    grid = {}
    for i, p in enumerate(co):
        grid.setdefault((int(p.x / NEIGH_CELL), int(p.y / NEIGH_CELL), int(p.z / NEIGH_CELL)),
                        []).append(i)
    adj = [[] for _ in range(nv)]
    r2 = NEIGH_RADIUS * NEIGH_RADIUS
    for i, p in enumerate(co):
        kx, ky, kz = int(p.x / NEIGH_CELL), int(p.y / NEIGH_CELL), int(p.z / NEIGH_CELL)
        found = []
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    for j in grid.get((kx + dx, ky + dy, kz + dz), ()):
                        if j != i and (co[j] - p).length_squared < r2:
                            found.append(j)
                            if len(found) >= NEIGH_MAX:
                                break
                    if len(found) >= NEIGH_MAX:
                        break
                if len(found) >= NEIGH_MAX:
                    break
            if len(found) >= NEIGH_MAX:
                break
        adj[i] = found
    print('[rig] 空间邻接：%d/%d 顶点有邻居' % (sum(1 for a in adj if a), nv))

    locked_idx = {ci for ci in range(nv) for pred, _, _ in LOCKS if pred(co[ci])}
    for _ in range(6):
        grew = set()
        for ci in range(nv):
            if ci in locked_idx:
                continue
            nb = adj[ci]
            if len(nb) >= 2 and sum(1 for j in nb if j in locked_idx) * 2 >= len(nb):
                grew.add(ci)
        if not grew:
            break
        locked_idx |= grew
    lock_bone, lock_value = idx[LOCKS[0][1]], LOCKS[0][2]
    for ci in locked_idx:
        W[ci] = {lock_bone: lock_value}
    print('[rig] 区域生长后锁 %d 顶点' % len(locked_idx))

    for _ in range(SMOOTH_ITERS):
        new = list(W)
        for ci in range(nv):
            if ci in locked_idx:
                continue
            acc = dict(W[ci])
            for k in acc:
                acc[k] *= 0.4
            nb = adj[ci]
            if nb:
                share = 0.6 / len(nb)
                for j in nb:
                    for k, w in W[j].items():
                        acc[k] = acc.get(k, 0.0) + w * share
            new[ci] = acc
        W = new

    out = []
    for ci, row in enumerate(W):
        if ci in locked_idx:
            out.append(row)
            continue
        items = sorted(row.items(), key=lambda kv: -kv[1])[:MAX_INFLUENCE]
        s = sum(w for _, w in items) or 1.0
        out.append({bi: w / s for bi, w in items})
    return names, out


def apply_weights(mesh, arm, names, weights):
    for g in list(mesh.vertex_groups):
        mesh.vertex_groups.remove(g)
    groups = {n: mesh.vertex_groups.new(name=n) for n in names}
    buckets = {n: [] for n in names}
    for vi, row in enumerate(weights):
        for bi, w in row.items():
            buckets[names[bi]].append((vi, w))
    for n in names:
        g = groups[n]
        for vi, w in buckets[n]:
            g.add([vi], w, 'REPLACE')
    mesh.parent = arm
    mesh.matrix_parent_inverse = arm.matrix_world.inverted()
    mod = mesh.modifiers.new('Armature', 'ARMATURE')
    mod.object = arm


def report(mesh):
    rows = []
    for g in mesh.vertex_groups:
        cnt = 0
        sm = 0.0
        for v in mesh.data.vertices:
            for ge in v.groups:
                if ge.group == g.index:
                    cnt += 1
                    sm += ge.weight
        rows.append({'bone': g.name, 'verts': cnt, 'wsum': round(sm, 2)})
    return sorted(rows, key=lambda r: -r['verts'])


def setup_render():
    scene = bpy.context.scene
    scene.render.engine = 'BLENDER_WORKBENCH'
    scene.display.shading.light = 'STUDIO'
    scene.display.shading.color_type = 'TEXTURE'
    scene.display.shading.show_shadows = True
    scene.display.shading.show_cavity = True
    scene.render.resolution_x = 760
    scene.render.resolution_y = 760
    world = bpy.data.worlds.get('World') or bpy.data.worlds.new('World')
    scene.world = world
    world.color = (0.92, 0.92, 0.92)
    cam_data = bpy.data.cameras.new('Cam')
    cam_data.lens = 62
    cam = bpy.data.objects.new('Cam', cam_data)
    bpy.context.collection.objects.link(cam)
    cam.location = Vector((0.45, -1.85, 0.58))
    d = Vector((0.0, -0.10, 0.40)) - cam.location
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam


def shoot(path, tag):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print('[rig] rendered %s -> %s' % (tag, path))


def pose_bone(arm, name, rot_deg=(0, 0, 0), loc=(0, 0, 0), scale=None):
    pb = arm.pose.bones[name]
    pb.rotation_mode = 'XYZ'
    pb.rotation_euler = Euler([math.radians(a) for a in rot_deg], 'XYZ')
    pb.location = Vector(loc)
    if scale:
        pb.scale = Vector(scale)
    bpy.context.view_layer.update()


def clear_pose(arm):
    for pb in arm.pose.bones:
        pb.rotation_mode = 'XYZ'
        pb.rotation_euler = Euler((0, 0, 0), 'XYZ')
        pb.location = Vector((0, 0, 0))
        pb.scale = Vector((1, 1, 1))
    bpy.context.view_layer.update()


# ------------------------------------------------------------------ 主流程
reset()
mesh = import_model()
print('[rig] mesh=%s verts=%d dims=%s' % (mesh.name, len(mesh.data.vertices),
                                          [round(v, 4) for v in mesh.dimensions]))
co = np.array([v.co[:] for v in mesh.data.vertices])
ear_bones = measure_ears(co)
eyes = detect_eyes(mesh)
eyelids = build_eyelids(mesh, eyes)
arm = build_armature(ear_bones, [(name, c) for name, _ob, c in eyelids])
if eyelids:
    bind_eyelids(eyelids, arm)
names, weights = custom_weights(mesh, arm)
apply_weights(mesh, arm, names, weights)

rows = report(mesh)
print('[rig] deform bones=%d  权重分布：' % len(names))
for r in rows:
    flag = '' if r['verts'] > 0 else '   <== 没拿到顶点'
    print('   %-8s verts=%5d wsum=%7.2f%s' % (r['bone'], r['verts'], r['wsum'], flag))
empty = [r['bone'] for r in rows if r['verts'] == 0]
print('[rig] 空骨骼组:', empty if empty else '无')

setup_render()
clear_pose(arm)
shoot(os.path.join(CHECK, '01_rest.png'), 'rest')

clear_pose(arm)
# 动画峰值姿势（Happy 幅度）
pose_bone(arm, 'Neck', rot_deg=(0, -8, 0))
pose_bone(arm, 'Head', rot_deg=(-9, -14, 0))
pose_bone(arm, 'EarL1', rot_deg=(0, 0, 22))
pose_bone(arm, 'EarR1', rot_deg=(0, 0, -18))
shoot(os.path.join(CHECK, '02_anim_peak.png'), 'head+ears (anim peak)')

clear_pose(arm)
pose_bone(arm, 'Spine', rot_deg=(-12, 0, 0))
pose_bone(arm, 'Chest', rot_deg=(-8, 0, 0))
pose_bone(arm, 'Neck', rot_deg=(-12, 0, 0))
pose_bone(arm, 'Head', rot_deg=(8, 0, 0))
shoot(os.path.join(CHECK, '03_lean.png'), 'lean')

clear_pose(arm)
pose_bone(arm, 'Chest', scale=(1.07, 1.07, 1.02))
pose_bone(arm, 'Spine', scale=(1.05, 1.05, 1.01))
pose_bone(arm, 'Hips', scale=(1.03, 1.03, 1.0))
shoot(os.path.join(CHECK, '04_breath.png'), 'breath')

if eyelids:
    for tag, deg in (('05_blink_half', 80), ('06_blink_full', EYELID_CLOSED_DEG)):
        clear_pose(arm)
        pose_bone(arm, 'EyelidL', rot_deg=(0, deg, 0))
        pose_bone(arm, 'EyelidR', rot_deg=(0, deg, 0))
        shoot(os.path.join(CHECK, tag + '.png'), tag)

# 尾巴摆动（链式累加警戒：总弯 ~70°，每节 4-7°）
clear_pose(arm)
n_tb = len(TAIL_PATH) - 1
for i in range(n_tb):
    pose_bone(arm, 'Tail%d' % (i + 1), rot_deg=(0, 0, 4.0 + i * 0.6))
cam = bpy.context.scene.camera
cam.location = Vector((2.4, 0.3, 0.55))
look = Vector((0.1, 0.25, 0.15)) - cam.location
cam.rotation_euler = look.to_track_quat('-Z', 'Y').to_euler()
shoot(os.path.join(CHECK, '07_tail_side.png'), 'tail (side)')
clear_pose(arm)
cam.location = Vector((0.45, -1.85, 0.58))
d = Vector((0.0, -0.10, 0.40)) - cam.location
cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
shoot(os.path.join(CHECK, '08_tail_rest.png'), 'tail rest')

clear_pose(arm)
bpy.ops.wm.save_as_mainfile(filepath=OUT_BLEND)
with open(os.path.join(CHECK, 'weights.json'), 'w', encoding='utf-8') as fh:
    json.dump(rows, fh, ensure_ascii=False, indent=2)
with open(os.path.join(CHECK, 'eyelid_meta.json'), 'w', encoding='utf-8') as fh:
    json.dump({
        'present': bool(eyelids),
        'axis': 'Y',
        'closed_deg': EYELID_CLOSED_DEG,
        'eyes': [{'bone': n, 'center': [round(float(x), 4) for x in c]}
                 for n, _ob, c in eyelids],
    }, fh, ensure_ascii=False, indent=2)
print('[rig] saved', OUT_BLEND)
