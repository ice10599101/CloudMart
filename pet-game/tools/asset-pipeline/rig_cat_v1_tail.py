"""原始猫（奶盖 pet-cat-runtime.glb）绑骨 + 删旧绕身尾 + 屁股后长可摆动新尾 + 眨眼。

这是用户拍板的最终路线：**回到原始那只猫，只改尾巴** —— 不再用图生 3D 重做整只。
骨骼坐标全部来自实测（blender_probe.py / probe_v1_model.py）：
  身高 0.9503 · 颈线 z=0.388 · 耳根 z≈0.79 · 脸朝 -Y
  双眼 (-0.088,-0.243,0.610) / (+0.140,-0.242,0.582)
  旧绕身尾在 -X 侧低处（x -0.373..-0.220，z 0..0.276，1029 顶点），与体表交织、删不干净
  → 删掉（管径 0.075 内逐点删）+ 屁股后重新长一条锥形管（UV 从旧尾顶点继承，花色延续）。

眨眼（吸取 v2 的教训）：
  - 皮碗贴眼球（1.05×拟合半径），rest 时盖在眼球上缘（同色不可见）；
  - 闭合角 **160°**（190° 会把整只眼球盖死，静帧读作骷髅眼窝 —— 已实测被打回）；
  - 两只眼皮必须**同时成功**才启用（只有一只比没有更糟）；
  - 契约：写出 eyelid_meta.json（present/axis/closed_deg），animate_cat.py 读取；
    没有眼皮就不导出 Blink 剪辑（空剪辑在 Cocos 是废轨）。

运行：
    blender --background --python rig_cat_v1_tail.py -- <in.glb> <out.blend> <check_dir>
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

# ------------------------------------------------------------------ 骨骼定义
# (名字, 父级, head, tail, 连接, 是否形变骨)
BONES = [
    ('Root',  None,    (0.00, 0.05, 0.00),  (0.00, 0.05, 0.100), False, False),
    ('Hips',  'Root',  (0.02, 0.10, 0.120), (0.02, 0.09, 0.240), False, True),
    ('Spine', 'Hips',  (0.02, 0.09, 0.240), (0.02, 0.08, 0.320), True, True),
    ('Chest', 'Spine', (0.02, 0.08, 0.320), (0.02, 0.06, 0.362), True, True),
    ('Neck',  'Chest', (0.02, 0.06, 0.362), (0.02, 0.02, 0.420), True, True),
    ('Head',  'Neck',  (0.02, 0.02, 0.420), (0.01, -0.02, 0.620), True, True),
    # 耳朵：z>0.78 的顶端突出部分簇实测（不是整个头顶）
    ('EarL1', 'Head',  (0.070, -0.190, 0.745), (0.086, -0.199, 0.830), False, True),
    ('EarL2', 'EarL1', (0.086, -0.199, 0.830), (0.098, -0.205, 0.870), True, True),
    ('EarR1', 'Head',  (-0.198, -0.118, 0.745), (-0.210, -0.128, 0.830), False, True),
    ('EarR2', 'EarR1', (-0.210, -0.128, 0.830), (-0.220, -0.135, 0.870), True, True),
    # 尾骨不按表建：由 rebuild_tail() 的新路径决定（见 build_armature）
]
RADIUS = {
    'Hips': 0.30, 'Spine': 0.28, 'Chest': 0.24, 'Neck': 0.20, 'Head': 0.30,
    'EarL1': 0.100, 'EarL2': 0.080, 'EarR1': 0.100, 'EarR2': 0.080,
    # 尾骨半径收到 0.06 系：新尾与身体背面只隔 0.02~0.08，半径大了距离场会把
    # 臀部顶点抓给尾骨 —— 摆尾时整个后躯跟着撕成黑刺（已实测踩到）。
    # 尾巴本体是独立物体、由 bind_tail 解析绑定，不依赖这里的半径。
    'Tail1': 0.060, 'Tail2': 0.055, 'Tail3': 0.050, 'Tail4': 0.045, 'Tail5': 0.045,
    'EyelidL': 0.0, 'EyelidR': 0.0,    # 半径 0 = 不参与身体距离场
}

# 解剖高度门控：唯一能把"相邻但不同部位"分开的手段（颈线 z=0.388 / 耳根 z≈0.79 实测）
GATE = {
    'Head': ('above', 0.330, 0.420),
    'Neck': ('above', 0.270, 0.330),
    'EarL1': ('above', 0.700, 0.760), 'EarL2': ('above', 0.780, 0.830),
    'EarR1': ('above', 0.700, 0.760), 'EarR2': ('above', 0.780, 0.830),
    # 新尾巴从 z≈0.15 升到 0.70：上方渐出兜底，避免尾骨抓到臀上方的身体
    'Tail1': ('below', 0.500, 0.600),
    'Tail2': ('below', 0.520, 0.620),
    'Tail3': ('below', 0.560, 0.660),
    'Tail4': ('below', 0.600, 0.700),
}

# 空间锁：胡须/口鼻（脸朝 -Y，胡须 y < -0.24、z 0.36~0.55，离头骨轴很远）
LOCKS = [
    (lambda p: p.z > 0.36 and p.y < -0.24, 'Head', 1.0),
]

MAX_INFLUENCE = 4
SMOOTH_ITERS = 8
NEIGH_CELL = 0.022
NEIGH_RADIUS = 0.032
NEIGH_MAX = 12

# 眼位（v1 实测）；眨眼参数吸取 v2 教训
EYES = [('EyelidL', np.array([0.140, -0.242, 0.582])),
        ('EyelidR', np.array([-0.088, -0.243, 0.610]))]
EYELID_REST_DEG = -100.0     # 建模时预旋：皮碗藏进头骨，rest 完全隐形
EYELID_CLOSED_DEG = 160.0    # 骨旋转量：扫到前上方盖住眼球上 85%（190° 会盖死成骷髅眼窝）


def smoothstep(lo, hi, x):
    if hi <= lo:
        return 0.0 if x < lo else 1.0
    t = (x - lo) / (hi - lo)
    t = max(0.0, min(1.0, t))
    return t * t * (3.0 - 2.0 * t)


def gate_of(name, z):
    g = GATE.get(name)
    if not g:
        return 1.0
    kind, lo, hi = g
    s = smoothstep(lo, hi, z)
    return s if kind == 'above' else (1.0 - s)


def seg_dist(p, a, b):
    ab = b - a
    denom = ab.dot(ab)
    t = 0.0 if denom < 1e-12 else max(0.0, min(1.0, (p - a).dot(ab) / denom))
    return (p - (a + ab * t)).length


# ------------------------------------------------------------------ 重建尾巴
# 旧尾巴缠在身上（外缘 x≈-0.37，人体最大半宽 0.32），分不开也绑不了。
# 做法：旧尾巴逐点删（管径 0.075，破坏最小的档位），屁股后重新长一条与身体
# 有真实间隙的锥形管，权重按路径参数解析计算，绝无串味。
# 路径向 +X 侧弯：游戏机位在 +X 侧，这样尾巴才看得见。
NEW_TAIL_PATH = [
    (0.00, 0.32, 0.15),    # 根部埋进身体（保证接缝连续）
    (0.06, 0.50, 0.24),    # 探出身体（中段向外推，与身体背面留出真实间隙）
    (0.15, 0.56, 0.36),
    (0.21, 0.54, 0.50),
    (0.23, 0.45, 0.62),
    (0.20, 0.34, 0.70),    # 尾尖向上向前卷起
]
NEW_TAIL_RADII = [0.058, 0.052, 0.044, 0.036, 0.027, 0.018]


def fit_wrapped_tail_line(co):
    """拟合旧「绕身」尾巴的中心线（干净掩码 + 极角分桶 + 贪心最近邻串链）。
    桶数必须密（61）：桶太疏时折线的弦会切过尾弧的内侧，弧段顶点离折线
    超过走廊半径 → 收缩漏掉 → 留下悬空残片（已实测踩到）。"""
    mask = (co[:, 2] < 0.20) & (co[:, 0] < -0.26)
    pts = co[mask]
    if len(pts) < 30:
        return None, None
    cx, cy = 0.016, 0.05
    ang = np.arctan2(pts[:, 1] - cy, pts[:, 0] - cx)
    edges = np.linspace(float(ang.min()), float(ang.max()), 61)
    raw = []
    for k in range(60):
        sel = (ang >= edges[k]) & (ang <= edges[k + 1])
        if sel.sum() >= 6:
            raw.append(pts[sel].mean(axis=0))
    if len(raw) < 4:
        return None, None
    raw = np.array(raw)
    # 桶序不能按极角排：尾弧跨过 atan2 的 ±π 断点，按角度排会把点序弄反
    order = [int(np.argmax(raw[:, 1]))]
    rest = [i for i in range(len(raw)) if i != order[0]]
    while rest:
        dd = [float(np.linalg.norm(raw[i] - raw[order[-1]])) for i in rest]
        nxt = rest[int(np.argmin(dd))]
        order.append(nxt)
        rest.remove(nxt)
    line = raw[order]
    hd = line[0] - line[1]
    hd = hd / max(float(np.linalg.norm(hd)), 1e-9)
    line = np.vstack([line[0] + hd * 0.05, line])
    return np.vstack([line, [0.02, -0.27, 0.05]]), pts


def smooth_path(path, radii, iters=1):
    """Chaikin 切角平滑：把控制点折线磨圆，新尾巴不再是一节节的棱角管。"""
    for _ in range(iters):
        new_p, new_r = [path[0]], [radii[0]]
        for i in range(len(path) - 1):
            p, q = path[i], path[i + 1]
            r, s = radii[i], radii[i + 1]
            new_p += [p * 0.75 + q * 0.25, p * 0.25 + q * 0.75]
            new_r += [r * 0.75 + s * 0.25, r * 0.25 + s * 0.75]
        new_p.append(path[-1])
        new_r.append(radii[-1])
        path, radii = np.array(new_p), np.array(new_r)
    return path, radii


TAIL_SHRINK = 0.15    # 收缩后保留的半径比例：尾管塌到轴心 15%，肯定在躯干内部


def rebuild_tail(mesh):
    """旧尾巴**不删**（这只网格的尾巴与体表交织，逐点删会在 z>0.22 的尾弧和
    连接处撕出破口/留下悬空残段 —— 两轮实测都失败），改为**沿径向收缩进躯干**：
    尾巴顶点朝躯干轴收到 15%，整条尾塌进身体内部、被外表面完全遮住，
    连接性零破坏、零破口。然后在屁股后面长一条全新的、可摆动的锥形尾
    （UV 从旧尾顶点原位继承，虎斑环纹延续）。"""
    me = mesh.data
    co = np.array([v.co[:] for v in me.vertices])
    uv = per_vertex_uv(mesh)

    line, cand = fit_wrapped_tail_line(co)
    if line is None:
        print('[tail] 旧尾巴中心线拟合失败，跳过重建')
        return None, None

    cx, cy = 0.016, 0.05
    d_all = np.array([point_polyline_dist(p, line) for p in co])
    corridor = np.where((d_all < 0.075) & (co[:, 2] < 0.30) & (co[:, 0] < -0.20))[0]
    print('[tail] 旧尾巴走廊顶点 %d（收缩前原位，供新尾继承 uv）' % len(corridor))
    src_pts = co[corridor] if len(corridor) >= 10 else cand
    src_uv = uv[corridor] if len(corridor) >= 10 else \
        uv[np.where((co[:, 2] < 0.20) & (co[:, 0] < -0.26))[0]]

    moved = 0
    for i in corridor:
        p = co[i]
        radial = np.array([p[0] - cx, p[1] - cy, 0.0])
        n = float(np.linalg.norm(radial))
        if n < 1e-6:
            continue
        co[i] = p - radial / n * (n * (1.0 - TAIL_SHRINK))
        moved += 1
    # 走廊外缘的渐变收缩：尾管外表面（d 0.075~0.14）按衰减系数部分收向躯干轴，
    # 消掉"走廊没盖住"的残片；衰减到 0 保证远处体表纹丝不动
    for i, p in enumerate(co):
        if p[2] > 0.30 or p[0] > -0.20:
            continue
        dd = d_all[i]
        if not 0.075 <= dd < 0.14:
            continue
        radial = np.array([p[0] - cx, p[1] - cy, 0.0])
        n = float(np.linalg.norm(radial))
        if n < 1e-6:
            continue
        factor = (1.0 - TAIL_SHRINK) * (1.0 - (dd - 0.075) / 0.065)
        co[i] = p - radial / n * (n * factor)
        moved += 1
    for i, v in enumerate(me.vertices):
        v.co = Vector(co[i])
    print('[tail] 旧尾巴隐藏：收缩 %d 顶点（含走廊外缘渐变），残片塌进躯干'
          % moved)
    # 调试：收缩后仍留在尾区（走廊外缘）的顶点 —— 它们就是可见残片
    co2 = np.array([v.co[:] for v in me.vertices])
    d2 = np.array([point_polyline_dist(p, line) for p in co2])
    left = np.where((d2 < 0.12) & (co2[:, 2] < 0.30) & (co2[:, 0] < -0.20))[0]
    moved_set = set(corridor.tolist())
    stray = [i for i in left if i not in moved_set]
    print('[tail] 走廊外残顶点 %d' % len(stray))
    if stray:
        sp = co2[stray]
        print('[tail] 残片包围盒 x %.3f..%.3f y %.3f..%.3f z %.3f..%.3f  质心 %s'
              % (sp[:, 0].min(), sp[:, 0].max(), sp[:, 1].min(), sp[:, 1].max(),
                 sp[:, 2].min(), sp[:, 2].max(), np.round(sp.mean(axis=0), 3).tolist()))

    path, radii = smooth_path(np.array(NEW_TAIL_PATH), np.array(NEW_TAIL_RADII), iters=1)
    verts, faces, s_list = tube_mesh(path, radii, segs=18, cap_base=True)
    tme = bpy.data.meshes.new('TailMesh')
    tme.from_pydata([tuple(v) for v in verts], [], faces)
    tme.update()
    tuv = tme.uv_layers.new(name='UVMap')
    uv_of_vert = {}
    for li, loop in enumerate(tme.loops):
        vi = loop.vertex_index
        if vi not in uv_of_vert:
            p = verts[vi]
            j = int(np.argmin(np.linalg.norm(src_pts - p, axis=1)))
            uv_of_vert[vi] = src_uv[j]
        tuv.data[li].uv = tuple(uv_of_vert[vi])
    for poly in tme.polygons:
        poly.use_smooth = True
    tobj = bpy.data.objects.new('Tail', tme)
    bpy.context.collection.objects.link(tobj)
    if me.materials:
        tobj.data.materials.append(me.materials[0])
    print('[tail] 新尾巴生成：%d 顶点 / %d 面，根 %s'
          % (len(verts), len(faces), np.round(path[0], 3).tolist()))
    return path, tobj


def tube_mesh(path, radii, segs=14, cap_base=True):
    """沿路径扫出锥形管。返回顶点、面、每个顶点的路径参数 s∈[0,1]。"""
    n = len(path)
    frames = []
    for i in range(n):
        if i == 0:
            tan = path[1] - path[0]
        elif i == n - 1:
            tan = path[-1] - path[-2]
        else:
            tan = path[i + 1] - path[i - 1]
        tan = tan / max(float(np.linalg.norm(tan)), 1e-9)
        ref = np.array([0.0, 0.0, 1.0])
        if abs(float(tan @ ref)) > 0.9:
            ref = np.array([1.0, 0.0, 0.0])
        nx = np.cross(tan, ref)
        nx = nx / max(float(np.linalg.norm(nx)), 1e-9)
        ny = np.cross(tan, nx)
        frames.append((nx, ny))
    verts, ss = [], []
    for i in range(n):
        nx, ny = frames[i]
        s = i / (n - 1)
        for k in range(segs):
            a = 2.0 * math.pi * k / segs
            verts.append(path[i] + radii[i] * (math.cos(a) * nx + math.sin(a) * ny))
            ss.append(s)
    faces = []
    for i in range(n - 1):
        for k in range(segs):
            k2 = (k + 1) % segs
            faces.append((i * segs + k, i * segs + k2, (i + 1) * segs + k2, (i + 1) * segs + k))
    if cap_base:
        c = len(verts)
        verts.append(path[0])
        ss.append(0.0)
        for k in range(segs):
            faces.append((c, (k + 1) % segs, k))
    c = len(verts)
    verts.append(path[-1])
    ss.append(1.0)
    base = (n - 1) * segs
    for k in range(segs):
        faces.append((c, base + k, base + (k + 1) % segs))
    return np.array(verts), faces, np.array(ss)


def bind_tail(path, tobj, arm):
    """新尾巴的权重按路径参数解析计算（独立锥管，不可能串味）。"""
    n_joints = len(path) - 1
    seg = np.linalg.norm(np.diff(path, axis=0), axis=1)
    acc = np.concatenate([[0.0], np.cumsum(seg)])
    total = max(acc[-1], 1e-9)
    tme = tobj.data
    groups = {}
    for j in range(n_joints):
        groups[j + 1] = tobj.vertex_groups.new(name='Tail%d' % (j + 1))
    centers = [(acc[j] + acc[j + 1]) / 2.0 / total for j in range(n_joints)]
    for vi, v in enumerate(tme.vertices):
        p = np.array(v.co[:])
        best = (1e9, 0.0)
        for k in range(len(path) - 1):
            a, b = path[k], path[k + 1]
            ab = b - a
            den = float(ab @ ab)
            t = 0.0 if den < 1e-12 else float(np.clip((p - a) @ ab / den, 0.0, 1.0))
            dd = float(np.linalg.norm(p - (a + ab * t)))
            if dd < best[0]:
                best = (dd, (acc[k] + seg[k] * t) / total)
        s = best[1]
        w = {}
        for j in range(n_joints):
            dist = abs(s - centers[j]) * n_joints
            val = max(0.0, 1.0 - dist / 1.4)
            if val > 1e-4:
                w[j + 1] = val
        if not w:
            w = {min(range(1, n_joints + 1), key=lambda j: abs(s - centers[j - 1])): 1.0}
        ssum = sum(w.values())
        for j, val in w.items():
            groups[j].add([vi], val / ssum, 'REPLACE')
    tobj.parent = arm
    tobj.matrix_parent_inverse = arm.matrix_world.inverted()
    mod = tobj.modifiers.new('Armature', 'ARMATURE')
    mod.object = arm
    print('[tail] 新尾巴绑定：%d 节，%d 顶点' % (n_joints, len(tme.vertices)))


def point_polyline_dist(p, line):
    best = 1e9
    for k in range(len(line) - 1):
        a, b = line[k], line[k + 1]
        ab = b - a
        denom = float(ab @ ab)
        t = 0.0 if denom < 1e-12 else float(np.clip((p - a) @ ab / denom, 0.0, 1.0))
        best = min(best, float(np.linalg.norm(p - (a + ab * t))))
    return best


# ------------------------------------------------------------------ 眼皮
def fit_sphere(pts):
    """代数最小二乘拟合球：(x²+y²+z²) = 2c·p + (r²-|c|²)"""
    A = np.hstack([2.0 * pts, np.ones((len(pts), 1))])
    b = (pts ** 2).sum(axis=1)
    sol, *_ = np.linalg.lstsq(A, b, rcond=None)
    c = sol[:3]
    r = float(np.sqrt(max(sol[3] + float(c @ c), 1e-9)))
    return c, r


def sphere_cap(center, radius, half_angle_deg, rings=8, segs=20):
    """以 +Z 为极轴、张角 half_angle 的球冠。"""
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


def per_vertex_uv(mesh):
    uv = np.zeros((len(mesh.data.vertices), 2), dtype=np.float64)
    layer = mesh.data.uv_layers.active.data
    for poly in mesh.data.polygons:
        for li in poly.loop_indices:
            vi = mesh.data.loops[li].vertex_index
            uv[vi] = layer[li].uv[:]
    return uv


def build_eyelids(mesh):
    """两只眼各一块球冠眼皮（1.05×拟合半径，贴眼球曲率），取样点在眼球正上方
    （落在毛发上，眼皮自动是毛色）。两只必须同时成功，否则全部放弃 ——
    只有一只眼皮比没有更糟（v2 已实测）。"""
    co = np.array([v.co[:] for v in mesh.data.vertices])
    uv = per_vertex_uv(mesh)
    mat = mesh.data.materials[0] if mesh.data.materials else None
    fitted = []
    for name, eye in EYES:
        d = np.linalg.norm(co - eye, axis=1)
        sel = co[d < 0.085]
        if len(sel) < 30:
            print('[eyes] %s 眼球附近顶点不足（%d）' % (name, len(sel)))
            continue
        center, radius = fit_sphere(sel)
        if not 0.03 <= radius <= 0.09:
            print('[eyes] %s 拟合半径 %.4f 出眼球范围，弃' % (name, radius))
            continue
        fitted.append((name, center, radius, len(sel)))
        print('[eyes] %s 眼球拟合：中心 %s 半径 %.4f（%d 点）'
              % (name, np.round(center, 4).tolist(), radius, len(sel)))
    if len(fitted) != 2:
        print('[eyes] 双眼未同时定位（%d/2），眼皮整体放弃' % len(fitted))
        return []
    out = []
    th = math.radians(EYELID_REST_DEG)
    rx = np.array([[1.0, 0.0, 0.0],
                   [0.0, math.cos(th), -math.sin(th)],
                   [0.0, math.sin(th), math.cos(th)]])
    for name, center, radius, _n in fitted:
        # cap 半角 50°（58° 太大，rest 藏进头骨后仍会从颅顶探出）；半径 1.05× 眼球
        verts, faces = sphere_cap(center, radius * 1.05, 50.0, 8, 20)
        # 预旋 EYELID_REST_DEG 藏进头骨：rest 完全隐形，眨眼时骨转 +160° 扫下来
        vv = (np.array(verts) - center) @ rx.T + center
        me = bpy.data.meshes.new(name + 'Mesh')
        me.from_pydata([tuple(v) for v in vv], [], faces)
        me.update()
        probe = center + np.array([0.0, -0.01, radius * 0.95])
        j = int(np.argmin(np.linalg.norm(co - probe, axis=1)))
        u, v = float(uv[j][0]), float(uv[j][1])
        layer = me.uv_layers.new(name='UVMap')
        for loop in me.loops:
            layer.data[loop.index].uv = (u, v)
        for poly in me.polygons:
            poly.use_smooth = True
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        if mat:
            ob.data.materials.append(mat)
        out.append((name, ob, center))
        print('[eyes] %s 眼皮生成：%d 顶点 / %d 面，毛色 uv=(%.3f,%.3f)'
              % (name, len(verts), len(faces), u, v))
    return out


def bind_eyelids(eyelids, arm):
    for name, ob, _center in eyelids:
        grp = ob.vertex_groups.new(name=name)
        grp.add([v.index for v in ob.data.vertices], 1.0, 'REPLACE')
        ob.parent = arm
        ob.matrix_parent_inverse = arm.matrix_world.inverted()
        mod = ob.modifiers.new('Armature', 'ARMATURE')
        mod.object = arm


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_model():
    bpy.ops.import_scene.gltf(filepath=GLB)
    mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
    bpy.context.view_layer.objects.active = mesh
    mesh.select_set(True)
    # glTF 的 +Z 向上转换写在节点四元数里，必须落到网格上，否则骨骼对不上世界坐标
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return mesh


def build_armature(tail_path, eyelid_centers):
    arm_data = bpy.data.armatures.new('CatArmature')
    arm = bpy.data.objects.new('CatRig', arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    eb = arm_data.edit_bones
    for name, parent, head, tail, connect, deform in BONES:
        # 尾骨由新路径决定，不按表建
        if name.startswith('Tail'):
            continue
        b = eb.new(name)
        b.head, b.tail = Vector(head), Vector(tail)
        b.use_deform = deform
        if parent:
            b.parent = eb[parent]
            b.use_connect = connect
    if tail_path is not None:
        pts = np.array(tail_path)
        for k in range(len(pts) - 1):
            b = eb.new('Tail%d' % (k + 1))
            b.head, b.tail = Vector(pts[k]), Vector(pts[k + 1])
            b.use_deform = True
            b.parent = eb['Hips'] if k == 0 else eb['Tail%d' % k]
            b.use_connect = k > 0
        print('[rig] 尾骨按新路径落位：%d 节' % (len(pts) - 1))
    # 眼皮骨：head=眼球中心、沿 +X（两根同向 → 眨眼绕骨局部 Y = 绕世界水平轴，
    # 左右眼同符号，契约见 eyelid_meta.json）
    for name, center in eyelid_centers:
        b = eb.new(name)
        b.head, b.tail = Vector(center), Vector(center) + Vector((0.05, 0, 0))
        b.use_deform = True
        b.parent = eb['Head']
        b.use_connect = False
    bpy.ops.object.mode_set(mode='OBJECT')
    return arm


def components(mesh):
    """连通分量（并查集）。"""
    nv = len(mesh.data.vertices)
    parent = list(range(nv))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    for e in mesh.data.edges:
        ra, rb = find(e.vertices[0]), find(e.vertices[1])
        if ra != rb:
            parent[rb] = ra
    buckets = {}
    for i in range(nv):
        buckets.setdefault(find(i), []).append(i)
    return sorted(buckets.values(), key=len, reverse=True)


def custom_weights(mesh, arm):
    """逐点权重：点到骨段距离场 × 半径截断 × 解剖门控 → 空间邻接平滑 → 限 4 影响。"""
    body_exclude = ('Eyelid',)
    deform = [b for b in arm.data.bones
              if b.use_deform and not b.name.startswith(body_exclude)]
    names = [b.name for b in deform]
    idx = {n: i for i, n in enumerate(names)}
    segs = [(b.head_local.copy(), b.tail_local.copy()) for b in deform]
    radii = [RADIUS.get(n, 0.25) for n in names]
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
    print('[rig] 空间锁命中 %d 顶点（胡须/口鼻）' % locked)

    # 空间邻接：网格碎片化（近千个连通岛），平滑必须按空间距离跨碎片缝
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

    # 区域生长：胡须是细长条会跨出固定框，从锁种子沿空间邻接长进去
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


# ------------------------------------------------------------------ 渲染验证
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
    world.color = (0.92, 0.92, 0.92)     # 浅灰底：验证图不能是黑底恐怖片
    cam_data = bpy.data.cameras.new('Cam')
    cam_data.lens = 62
    cam = bpy.data.objects.new('Cam', cam_data)
    bpy.context.collection.objects.link(cam)
    cam.location = Vector((0.52, -2.00, 0.62))
    d = Vector((0.0, -0.02, 0.44)) - cam.location
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
tail_path, tail_obj = rebuild_tail(mesh)
eyelids = build_eyelids(mesh)
arm = build_armature(tail_path, [(name, c) for name, _ob, c in eyelids])
if tail_path is not None and tail_obj is not None:
    bind_tail(tail_path, tail_obj, arm)
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
pose_bone(arm, 'Neck', rot_deg=(-6, 10, 0))
pose_bone(arm, 'Head', rot_deg=(-12, 20, 0))
pose_bone(arm, 'EarL1', rot_deg=(0, 0, -38))
pose_bone(arm, 'EarR1', rot_deg=(0, 0, 38))
shoot(os.path.join(CHECK, '02_head_ears.png'), 'head+ears')

clear_pose(arm)
pose_bone(arm, 'Spine', rot_deg=(-14, 0, 0))
pose_bone(arm, 'Chest', rot_deg=(-10, 0, 0))
pose_bone(arm, 'Neck', rot_deg=(-14, 0, 0))
pose_bone(arm, 'Head', rot_deg=(10, 0, 0))
shoot(os.path.join(CHECK, '03_lean.png'), 'lean')

clear_pose(arm)
pose_bone(arm, 'Chest', scale=(1.07, 1.07, 1.02))
pose_bone(arm, 'Spine', scale=(1.05, 1.05, 1.01))
pose_bone(arm, 'Hips', scale=(1.03, 1.03, 1.0))
shoot(os.path.join(CHECK, '04_breath.png'), 'breath')

# 眨眼：半闭 / 全闭（160°，不盖死整只眼球）
if eyelids:
    for tag, deg in (('05_blink_half', 80), ('06_blink_full', EYELID_CLOSED_DEG)):
        clear_pose(arm)
        pose_bone(arm, 'EyelidL', rot_deg=(0, deg, 0))
        pose_bone(arm, 'EyelidR', rot_deg=(0, deg, 0))
        shoot(os.path.join(CHECK, tag + '.png'), tag)

# 尾巴摆动（侧视机位：尾巴在屁股后面，正面机位看不见）
if tail_path is not None:
    clear_pose(arm)
    n_tb = len(tail_path) - 1
    # ⚠️ 链式骨骼的旋转会**沿链累加**：每节转 22°×11 节 = 尾尖 350°，整条尾拧穿身体
    # （已实测踩到）。检查姿势按"尾尖总弯 ~70°"分配：每节 ~4-7°。
    for i in range(n_tb):
        pose_bone(arm, 'Tail%d' % (i + 1), rot_deg=(0, 0, (4.0 + i * 0.6)))
    cam = bpy.context.scene.camera
    cam.location = Vector((2.3, 0.4, 1.0))
    look = Vector((0.0, 0.30, 0.40)) - cam.location
    cam.rotation_euler = look.to_track_quat('-Z', 'Y').to_euler()
    shoot(os.path.join(CHECK, '07_tail_side.png'), 'tail (side)')
    clear_pose(arm)
    shoot(os.path.join(CHECK, '08_tail_rest_side.png'), 'tail rest (side)')

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
