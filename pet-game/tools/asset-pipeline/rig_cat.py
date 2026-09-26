"""奶灰猫绑骨 + 自研权重 + 姿势渲染验证（Blender headless）。

骨骼坐标来自 blender_probe.py 的实测值（颈线 z=0.388 / 耳根 z≈0.79 / 眼位等），不是按比例猜。

为什么不用 Blender 的自动权重：
    `ARMATURE_AUTO`（骨热扩散）在这种**生成式融合网格**上直接失败 ——
    实测报 `Bone Heat Weighting: failed to find solution for one or more bones`，
    14 个骨骼组全部拿到 0 个顶点。骨热要求网格基本流形且无内部面，
    而图生 3D 的产物是自相交的融合体；又不能 remesh（会把烘焙好的 UV 与三张贴图一起毁掉）。
    因此这里自己算权重：
      1) 顶点到骨骼线段的最短距离；
      2) 每根骨骼给一个**作用半径**，超出半径不参与 —— 这是抑制"串味"的唯一手段
         （尾巴贴身穿行，不给半径约束的话尾骨会抓起整个身体）；
      3) 半径内按 (1 - d/r)^3 衰减，取最强的 4 根并归一化（对齐 glTF 的 4 权重上限）；
      4) 沿网格邻接做几轮拉普拉斯平滑，消除硬边。

运行：
    blender --background --python rig_cat.py -- <in.glb> <out.blend> <check_dir>
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
    # 耳朵：由 measure_v2c.py 取 z>0.78 的**顶端突出部**分簇（不是 z>0.64 的整个头顶 ——
    #   那样会把头顶一起算进去，得到的是半个脑袋而不是耳片）。
    #   +X 耳 centroid(0.084,-0.198,0.830)   -X 耳 centroid(-0.21,-0.121,0.816)
    ('EarL1', 'Head',  (0.070, -0.190, 0.745), (0.086, -0.199, 0.830), False, True),
    ('EarL2', 'EarL1', (0.086, -0.199, 0.830), (0.098, -0.205, 0.870), True, True),
    ('EarR1', 'Head',  (-0.198, -0.118, 0.745), (-0.210, -0.128, 0.830), False, True),
    ('EarR2', 'EarR1', (-0.210, -0.128, 0.830), (-0.220, -0.135, 0.870), True, True),
    # 尾巴：**新模型自带离体尾巴**（+X 侧、从地面升到 z≈0.54，全程与身体有间隙），
    #   所以直接沿实测路径摆 4 节即可，不需要旧的"删尾+重建"那套。
    # 尾巴路径由 measure_v2b.py 复核：x 0.31~0.39、y≈0.40（身后）、z 0.05→0.53
    ('Tail1', 'Hips',  (0.280, 0.400, 0.050), (0.330, 0.405, 0.130), False, True),
    ('Tail2', 'Tail1', (0.330, 0.405, 0.130), (0.360, 0.400, 0.260), True, True),
    ('Tail3', 'Tail2', (0.360, 0.400, 0.260), (0.365, 0.400, 0.390), True, True),
    ('Tail4', 'Tail3', (0.365, 0.400, 0.390), (0.375, 0.410, 0.530), True, True),
]
RADIUS = {
    'Hips': 0.30, 'Spine': 0.28, 'Chest': 0.24, 'Neck': 0.20, 'Head': 0.30,
    'EarL1': 0.100, 'EarL2': 0.080, 'EarR1': 0.100, 'EarR2': 0.080,
    # 尾巴：新模型的尾巴是独立的粗管、与身体有间隙，0.09 既够裹住整条尾、又够不到躯干
    'Tail1': 0.090, 'Tail2': 0.085, 'Tail3': 0.080, 'Tail4': 0.075,
    # 眼睑：0 = 从主体权重中排除（它们靠独立物体的整块权重绑定）
    'EyelidL': 0.0, 'EyelidR': 0.0,
}

# 解剖高度门控 —— 这是**唯一**能把"相邻但不同部位"分开的手段。
# 围巾就贴在颈线下方、耳根紧挨头顶，纯距离场必然把两者连成一片
# （实测：Head 半径 0.32 时抓走 8901/12243 个顶点，转头会把围巾和胸口一起拽裂）。
# 用 probe 实测的颈线 z=0.388 / 耳根 z≈0.79 做硬约束：
#   ('above', lo, hi) -> 影响在 [lo,hi] 上从 0 渐入到 1（用于头、耳）
#   ('below', lo, hi) -> 影响在 [lo,hi] 上从 1 渐出到 0（用于贴地的部件）
# 渐入带宽刻意留得宽（0.07~0.10），窄了会在交界处留下硬边、一动就撕裂。
# 新模型的颈线 z≈0.36 / 耳根 z≈0.63（由 measure_v2.py 的竖直剖面实测）
GATE = {
    'Head': ('above', 0.330, 0.420),
    'Neck': ('above', 0.270, 0.330),
    'EarL1': ('above', 0.700, 0.760), 'EarL2': ('above', 0.780, 0.830),
    'EarR1': ('above', 0.700, 0.760), 'EarR2': ('above', 0.780, 0.830),    # 尾巴整体低于 z=0.55，给个"上方渐出"兜底，避免尾骨抓到臀上方的身体
    'Tail1': ('below', 0.500, 0.600),
    'Tail2': ('below', 0.520, 0.620),
    'Tail3': ('below', 0.560, 0.660),
    'Tail4': ('below', 0.600, 0.700),
}

# 显式空间锁：这些区域一定有唯一正确的骨骼，不要交给距离场去猜。
# (区域谓词, 骨骼名, 锁死权重)
#   胡须/口鼻：贴在脸上但伸得很远，离头骨轴 0.4+，距离场会把它判给脖子/胸。
# 新模型的脸朝 -Y（眼睛在 y≈0.03、胡须更靠前，位于 y < -0.24、头部高度 z 0.36~0.55
LOCKS = [
    (lambda p: p.z > 0.36 and p.y < -0.24, 'Head', 1.0),
]

MAX_INFLUENCE = 4
SMOOTH_ITERS = 8
# 空间邻近图：网格被切成近千个碎片（实测 970 个连通分量、最大岛仅 359 顶点），
# 靠"共享边"做平滑根本跨不过碎片缝 —— 相邻碎片各自平滑、各自收敛到不同权重，
# 一动就互相拉开，这就是之前看到的"撕裂"（不是过渡不平滑，是碎片分家）。
# 所以邻接必须按**空间距离**建，让权重能跨缝连续。
NEIGH_CELL = 0.022
NEIGH_RADIUS = 0.032
NEIGH_MAX = 12


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
# 用户拍板：不要那条绕身铺地的尾巴，改做「贴在屁股后面、能摇晃摆动」的常规猫尾。
#
# 这是这个资产上唯一可行的做法：旧尾巴缠在身上（外缘 x≈-0.37，人体最大半宽 0.32），
# 既没法分离（一推就撕成薄板）也没法绑（尾骨会抓走 1463 个体顶点）。
# 所以：**旧尾巴整块删掉，在屁股后面重新长一条** —— 新尾巴与身体有真实间隙，
# 完全在我控制之下，权重可以直接按路径参数解析计算，不依赖距离场。
NEW_TAIL_PATH = [
    (0.00, 0.32, 0.15),    # 根部埋进身体（保证接缝连续）
    (0.06, 0.46, 0.24),    # 探出身体（身体在 z=0.24 处后缘约 y=0.46）
    (0.14, 0.52, 0.36),
    (0.20, 0.50, 0.50),    # 向 +X 侧弯 —— 游戏机位在 +X 侧，这样尾巴才看得见
    (0.22, 0.42, 0.62),
    (0.20, 0.32, 0.70),    # 尾尖向上向前卷起
]
NEW_TAIL_RADII = [0.058, 0.052, 0.044, 0.036, 0.027, 0.018]


def fit_wrapped_tail_line(co):
    """拟合旧「绕身」尾巴的中心线（复用已验证的取法：干净掩码 + 极角分桶 + 贪心最近邻串链）。"""
    mask = (co[:, 2] < 0.20) & (co[:, 0] < -0.26)
    pts = co[mask]
    if len(pts) < 30:
        return None, None
    cx, cy = 0.016, 0.05
    ang = np.arctan2(pts[:, 1] - cy, pts[:, 0] - cx)
    edges = np.linspace(float(ang.min()), float(ang.max()), 23)
    raw = []
    for k in range(22):
        sel = (ang >= edges[k]) & (ang <= edges[k + 1])
        if sel.sum() >= 10:
            raw.append(pts[sel].mean(axis=0))
    if len(raw) < 4:
        return None, None
    raw = np.array(raw)
    # 桶序**不能**按极角排：尾弧正好跨过 atan2 的 ±π 断点，按角度排会把点序弄反
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


def rebuild_tail(mesh):
    """删掉旧绕身尾巴（按连通岛整块删，避免逐点删出锯齿），在屁股后生成一条新尾巴。
    返回 (新尾巴路径, 新尾巴物体)，失败返回 (None, None)。"""
    import bmesh
    me = mesh.data
    co = np.array([v.co[:] for v in me.vertices])
    uv = per_vertex_uv(mesh)

    line, cand = fit_wrapped_tail_line(co)
    if line is None:
        print('[tail] 旧尾巴中心线拟合失败，跳过重建')
        return None, None
    d = np.array([point_polyline_dist(p, line) for p in cand])
    # 管径只取**贴着中心线**那批点的中位数：候选掩码里混着尾巴旁边的体表顶点，
    # 直接取 90 分位会把管径估到 0.093，再乘系数就宽到把腿附近的体块一起删掉（实测出破洞）。
    core = d[d < 0.08]
    tube = float(np.median(core)) if len(core) >= 20 else float(np.percentile(d, 60))
    print('[tail] 旧尾巴中心线 %d 点，管径（贴线中位）%.4f' % (len(line), tube))

    # 逐顶点按管道删除。
    # 按岛删试过两档都不行：紧一档（最大距离<0.105）只删掉 279 顶点，旧尾巴基本还在；
    # 松一档（<0.145）删到 480 顶点，反而把尾巴切成一堆撕裂的破片 ——
    # 说明旧尾巴的几何与体表是**交织**的，按岛整块处理切不断它。
    comps = components(mesh)
    d_all = np.array([point_polyline_dist(p, line) for p in co])
    # 删除半径取 0.075（破坏最小的档位）。
    # 试过 0.096 / 0.126：删得更多，但尾巴与身体**连接处会留下破口**（露出身体内侧的米色），
    # 那不是"没删干净的尾巴"，是切开连接处造成的洞 —— 加大半径只会扩大它。
    # 根因是生成式模型的尾巴与体表是**交织**的，没有可切的分界。
    # 彻底解决只能重新生成一版「尾巴与身体分离」的模型（会得到另一只猫，需用户确认）。
    del_idx = np.where((d_all < 0.075) & (co[:, 2] < 0.22))[0].tolist()
    print('[tail] 旧尾巴：逐顶点删除 %d 个顶点（共 %d 顶点 / %d 岛）'
          % (len(del_idx), len(co), len(comps)))

    # 新尾巴的贴图坐标：从**旧尾巴顶点**就近继承 —— 这样条纹花色能延续下来，
    # 不用去猜哪块 uv 是尾巴。
    src_pts = co[del_idx] if del_idx else cand
    src_uv = uv[del_idx] if del_idx else uv[np.where((co[:, 2] < 0.20) & (co[:, 0] < -0.26))[0]]

    if del_idx:
        bm = bmesh.new()
        bm.from_mesh(me)
        bm.verts.ensure_lookup_table()
        bmesh.ops.delete(bm, geom=[bm.verts[i] for i in del_idx], context='VERTS')
        bm.to_mesh(me)
        bm.free()
        me.update()

    path = np.array(NEW_TAIL_PATH)
    radii = np.array(NEW_TAIL_RADII)
    verts, faces, s_list = tube_mesh(path, radii, segs=14, cap_base=True)
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
    print('[tail] 新尾巴生成：%d 顶点 / %d 面，路径 %s'
          % (len(verts), len(faces), np.round(path[0], 3).tolist()))
    return path, tobj


def tube_mesh(path, radii, segs=14, cap_base=True):
    """沿路径扫出锥形管。返回顶点、面、以及每个顶点的路径参数 s∈[0,1]。"""
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
    """新尾巴的权重**按路径参数解析计算**，不用距离场：
    尾巴是独立的锥管、每节骨骼对应一段路径，所以直接沿弧长做三角核混合即可，
    又平滑又不可能串味（这正是"重新长一条"换来的好处）。"""
    n_joints = len(path) - 1
    seg = np.linalg.norm(np.diff(path, axis=0), axis=1)
    acc = np.concatenate([[0.0], np.cumsum(seg)])
    total = max(acc[-1], 1e-9)
    tme = tobj.data
    groups = {}
    for j in range(n_joints):
        groups[j + 1] = tobj.vertex_groups.new(name='Tail%d' % (j + 1))
    # 每根骨骼的"关节中心"在路径上的归一化位置
    centers = [(acc[j] + acc[j + 1]) / 2.0 / total for j in range(n_joints)]
    for vi, v in enumerate(tme.vertices):
        p = np.array(v.co[:])
        # 求该顶点在路径上的弧长参数
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


def components(mesh):
    """连通分量（并查集）。按岛整块删除/绑定都靠它。"""
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


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


TAIL_PUSH = 0.075
TAIL_TUBE = 0.045


def resample_polyline(line, count):
    """按弧长把折线重采样成 count 个点。"""
    seg = np.linalg.norm(np.diff(line, axis=0), axis=1)
    acc = np.concatenate([[0.0], np.cumsum(seg)])
    total = acc[-1]
    if total < 1e-6:
        return np.repeat(line[:1], count, axis=0)
    want = np.linspace(0.0, total, count)
    out = []
    for w in want:
        k = int(np.clip(np.searchsorted(acc, w) - 1, 0, len(seg) - 1))
        t = 0.0 if seg[k] < 1e-9 else (w - acc[k]) / seg[k]
        out.append(line[k] + (line[k + 1] - line[k]) * t)
    return np.array(out)


def point_polyline_dist(p, line):
    best = 1e9
    for k in range(len(line) - 1):
        a, b = line[k], line[k + 1]
        ab = b - a
        denom = float(ab @ ab)
        t = 0.0 if denom < 1e-12 else float(np.clip((p - a) @ ab / denom, 0.0, 1.0))
        best = min(best, float(np.linalg.norm(p - (a + ab * t))))
    return best


def separate_tail(mesh):
    """把尾巴几何整体外推，造出与身体之间的**真实间隙**，然后才可能绑尾骨。

    为什么必须先做这一步：尾巴是贴着人身铺在地板上的（外缘 x≈-0.37，而人体最大半宽
    就 0.32，几何上几乎相交），任何基于距离的权重都会把身体一起拽走。
    重新生成模型会得到**另一只猫**，所以选择在既有网格上外推。

    做法：先用"低空 + 明显偏 -X"这个干净条件取候选点（身体右半是干净的参照，
    尾巴只长在 -X 一侧），按极角分桶取质心**拟合出尾巴中心线**，
    再把中心线附近的顶点沿"背离躯干轴"的方向外推。
    外推量沿中心线向尾尖渐减到 0，避免在尾尖处与未移动的部分之间撕开。
    返回拟合出的中心线（供骨骼落位使用）。
    """
    co = np.array([v.co[:] for v in mesh.data.vertices])
    mask = (co[:, 2] < 0.20) & (co[:, 0] < -0.26)
    pts = co[mask]
    if len(pts) < 30:
        print('[geom] 尾巴候选点不足（%d），跳过外推' % len(pts))
        return None

    # 按极角分桶取质心 → 得到尾巴的若干中心点。
    # ⚠️ 桶的顺序**不能**用角度排序：尾巴的弧线正好跨过 atan2 的 ±π 断点，
    # 按角度排会把点的顺序弄反，骨骼链于是来回折（第一版就是这样）。
    # 改用贪心最近邻串链：从最靠后（+Y 最大）的点出发，每次接最近的点。
    cx, cy = 0.016, 0.05            # 躯干轴心（由竖直剖面的质心实测）
    ang = np.arctan2(pts[:, 1] - cy, pts[:, 0] - cx)
    edges = np.linspace(float(ang.min()), float(ang.max()), 23)
    raw = []
    for k in range(22):
        sel = (ang >= edges[k]) & (ang <= edges[k + 1])
        if sel.sum() >= 10:
            raw.append(pts[sel].mean(axis=0))
    if len(raw) < 4:
        print('[geom] 尾巴中心线拟合失败（桶数 %d）' % len(raw))
        return None
    raw = np.array(raw)
    order = [int(np.argmax(raw[:, 1]))]          # 尾根在后（+Y 最大）
    rest = [i for i in range(len(raw)) if i != order[0]]
    while rest:
        d = [float(np.linalg.norm(raw[i] - raw[order[-1]])) for i in rest]
        nxt = rest[int(np.argmin(d))]
        order.append(nxt)
        rest.remove(nxt)
    line = raw[order]
    # 尾根往身体方向补一小段（要短：补太长会把骨骼端点送进身体里，尾骨就会抓身体）
    head_dir = line[0] - line[1]
    head_dir = head_dir / max(float(np.linalg.norm(head_dir)), 1e-9)
    line = np.vstack([line[0] + head_dir * 0.05, line])
    full = np.vstack([line, [0.02, -0.27, 0.05]])
    print('[geom] 尾巴中心线拟合：%d 点，起 %s 止 %s' % (
        len(full),
        np.round(full[0], 3).tolist(), np.round(full[-1], 3).tolist()))

    moved = 0
    # 沿中心线的弧长比例：靠身体那一段满量外推，接近尾尖渐减到 0
    seg = np.linalg.norm(np.diff(full, axis=0), axis=1)
    acc = np.concatenate([[0.0], np.cumsum(seg)])
    total = acc[-1]
    for i, p in enumerate(co):
        if p[2] > 0.19 or p[0] > -0.20:
            continue
        d = point_polyline_dist(p, full)
        if d >= TAIL_TUBE:
            continue
        # 找最近线段，取其弧长位置决定外推权重
        best_k, best_d, best_t = 0, 1e9, 0.0
        for k in range(len(full) - 1):
            a, b = full[k], full[k + 1]
            ab = b - a
            denom = float(ab @ ab)
            t = 0.0 if denom < 1e-12 else float(np.clip((p - a) @ ab / denom, 0.0, 1.0))
            dd = float(np.linalg.norm(p - (a + ab * t)))
            if dd < best_d:
                best_k, best_d, best_t = k, dd, t
        s = (acc[best_k] + seg[best_k] * best_t) / max(total, 1e-9)
        w = 1.0 if s < 0.7 else max(0.0, (1.0 - s) / 0.3)
        radial = np.array([p[0] - cx, p[1] - cy, 0.0])
        n = float(np.linalg.norm(radial))
        if n < 1e-6 or w <= 0.0:
            continue
        co[i] = p + radial / n * (TAIL_PUSH * w)
        moved += 1
    for i, v in enumerate(mesh.data.vertices):
        v.co = Vector(co[i])
    print('[geom] 尾巴外推：移动 %d 个顶点（外推 %.3f，管径 %.3f）' % (moved, TAIL_PUSH, TAIL_TUBE))
    return full


def fit_sphere(pts):
    """代数最小二乘拟合球：(x²+y²+z²) = 2c·p + (r²-|c|²)"""
    A = np.hstack([2.0 * pts, np.ones((len(pts), 1))])
    b = (pts ** 2).sum(axis=1)
    sol, *_ = np.linalg.lstsq(A, b, rcond=None)
    c = sol[:3]
    r = float(np.sqrt(max(sol[3] + float(c @ c), 1e-9)))
    return c, r


def sphere_cap(center, radius, half_angle_deg, rings=8, segs=20):
    """生成以 +Z 为极轴、张角 half_angle 的球冠（顶点 + 三角/四边形面）。"""
    half = math.radians(half_angle_deg)
    verts = [tuple(center + np.array([0.0, 0.0, radius]))]     # 极点
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
    """给两只眼球各加一块球冠眼皮（贴合眼球曲率、略外扩），刚性绑到对应骨头上。

    为什么要做几何而不是用形态键/贴图：眼睛是**烘焙进反照率贴图的**，网格上没有独立眼皮，
    而"眨眼"是判断一个宠物是否活着最强的信号之一。
    眼睑用与眼球同心的球冠（半径 ×1.05），绕眼球中心旋转即可像真眼皮一样压下/抬起。
    贴图坐标取**眼球正上方那个顶点**的 uv —— 这样眼睑自动是周围的毛色，不会比身上浅一块。
    """
    co = np.array([v.co[:] for v in mesh.data.vertices])
    uv = per_vertex_uv(mesh)
    mat = mesh.data.materials[0] if mesh.data.materials else None
    out = []
    for name, eye in EYES:
        d = np.linalg.norm(co - eye, axis=1)
        sel = co[d < 0.085]
        if len(sel) < 30:
            print('[geom] %s 眼球附近顶点不足（%d），跳过' % (name, len(sel)))
            continue
        center, radius = fit_sphere(sel)
        print('[geom] %s 眼球拟合：中心 %s 半径 %.4f（用 %d 点）' % (
            name, np.round(center, 4).tolist(), radius, len(sel)))

        verts, faces = sphere_cap(center, radius * 1.05, 58.0, 8, 20)
        me = bpy.data.meshes.new(name + 'Mesh')
        me.from_pydata(verts, [], faces)
        me.update()
        # 取样点：眼球正上方（保证落在毛发上而不是虹膜上）
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
        print('[geom] %s 眼睑生成：%d 顶点 / %d 面，贴图取样 uv=(%.3f, %.3f)' % (
            name, len(verts), len(faces), u, v))
    return out


def bind_eyelids(eyelids, arm):
    """眼睑整块刚性绑到各自骨骼（新增独立物体，不动主体权重）。"""
    for name, ob, _center in eyelids:
        grp = ob.vertex_groups.new(name=name)
        grp.add([v.index for v in ob.data.vertices], 1.0, 'REPLACE')
        ob.parent = arm
        ob.matrix_parent_inverse = arm.matrix_world.inverted()
        mod = ob.modifiers.new('Armature', 'ARMATURE')
        mod.object = arm


def import_model():
    bpy.ops.import_scene.gltf(filepath=GLB)
    mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
    bpy.context.view_layer.objects.active = mesh
    mesh.select_set(True)
    # glTF 的 +Z 向上转换写在节点四元数里，必须落到网格上，否则骨骼用世界坐标会对不上
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return mesh


def build_armature(tail_line=None):
    arm_data = bpy.data.armatures.new('CatArmature')
    arm = bpy.data.objects.new('CatRig', arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    eb = arm_data.edit_bones
    for name, parent, head, tail, connect, deform in BONES:
        # 尾骨不按表里的初值建：它们由 separate_tail() 拟合出的中心线决定
        if name.startswith('Tail') and tail_line is not None:
            continue
        b = eb.new(name)
        b.head, b.tail = Vector(head), Vector(tail)
        b.use_deform = deform
        if parent:
            b.parent = eb[parent]
            b.use_connect = connect
    # 尾巴：把新路径等分成 N 段（段数 = 路径点数 - 1）
    if tail_line is not None:
        pts = np.array(tail_line)
        for k in range(len(pts) - 1):
            b = eb.new('Tail%d' % (k + 1))
            b.head, b.tail = Vector(pts[k]), Vector(pts[k + 1])
            b.use_deform = True
            b.parent = eb['Hips'] if k == 0 else eb['Tail%d' % k]
            b.use_connect = k > 0
        print('[rig] 尾骨按新路径落位：%d 节' % (len(pts) - 1))
    bpy.ops.object.mode_set(mode='OBJECT')
    return arm


def components(mesh):
    """连通分量。附件（围巾/铃铛/胡须）若是独立岛，就可以整岛刚性绑定，
    从根上避免"同一块曲面跨两根骨骼 → 撕裂"。"""
    nv = len(mesh.data.vertices)
    parent = list(range(nv))

    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    for e in mesh.data.edges:
        union(e.vertices[0], e.vertices[1])
    buckets = {}
    for i in range(nv):
        buckets.setdefault(find(i), []).append(i)
    return sorted(buckets.values(), key=len, reverse=True)




def custom_weights(mesh, arm):
    """逐点权重：点到骨段距离场 × 半径截断 × 解剖门控 → 空间邻接平滑 → 限 4 影响。
    全程不做整岛绑定：网格是碎片化的，任何"整块不同权重"都会让碎片分家。"""
    # 眼睑不参与身体的距离权重 —— 它是独立物体、整块刚性绑定。
    # 必须显式排除：否则"半径全不覆盖时兜底给最近骨骼"会把眼周的脸面顶点分给眼皮，
    # 眨眼就会把脸一起拖走（实测 EyelidR 白拿 202 个身体顶点）。
    # ⚠️ 注意**不能**把尾巴一起排除 —— 新模型的尾巴是身体网格的一部分，尾骨必须参与。
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

    # 空间邻接（跨碎片缝）
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
    linked = sum(1 for a in adj if a)
    print('[rig] 空间邻接：%d/%d 顶点有邻居' % (linked, nv))

    locked_idx = {ci for ci in range(nv) for pred, _, _ in LOCKS if pred(co[ci])}
    print('[rig] 空间锁种子 %d 顶点' % len(locked_idx))

    # 区域生长：胡须是细长条，一定会跨出任何固定的框 —— 只有一半被锁住的胡须
    # 会被硬生生拉长（实测就是这样）。所以从种子出发，沿**空间邻接**把整根胡须
    # 连同口鼻一起长进去。因为邻接按空间距离建，长到围巾那里会因为"邻居大多没锁"而自然停下。
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
# 旧绕身尾巴的"外推分离"方案**不启用**：实测会把网格撕成薄板（`_check/07_tail.png`）。
# 现在的做法是删掉旧尾巴、在屁股后重新长一条（`rebuild_tail()`），见其说明。
tail_path, tail_obj = rebuild_tail(mesh)
arm = build_armature(tail_path)
if tail_path is not None and tail_obj is not None:
    bind_tail(tail_path, tail_obj, arm)
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
# 身体前倾 + 头低下：检验颈胸过渡带（最容易撕裂的一处）
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

# 眨眼：眼皮球冠绕眼球中心压下。三个角度各出一张，确认压下量与旋转轴对不对
for tag, deg in (('05_blink_half', 34), ('06_blink_full', 62)):
    clear_pose(arm)
    pose_bone(arm, 'EyelidL', rot_deg=(0, deg, 0))
    pose_bone(arm, 'EyelidR', rot_deg=(0, deg, 0))
    shoot(os.path.join(CHECK, tag + '.png'), tag)

# 尾巴摆动：各节依次转，检查新尾巴是否干净摆动、且完全不拖动身体。
# 用**侧视机位**：尾巴在屁股后面，正面机位下会被身体整个挡住，看不出动没动。
if tail_path is not None:
    clear_pose(arm)
    for i in range(len(tail_path) - 1):
        pose_bone(arm, 'Tail%d' % (i + 1), rot_deg=(0, 0, 22 + i * 4))
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
print('[rig] saved', OUT_BLEND)
