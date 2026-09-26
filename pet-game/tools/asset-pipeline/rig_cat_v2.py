"""新猫 v2 绑骨（干净版，只服务 v2 这只重新生成的猫）。

v2 的尾巴是**生成时就与身体分离**的（+X 侧、从地面升到 z≈0.54，全程有间隙），
所以直接沿实测路径摆 4 节尾骨、用距离场绑即可。

本轮三个修复（探针 probe_tear.py 实证）：
  1. 耳骨权重过大（EarR1 抓 2763 顶点，头部一转头脸撕裂、后脑翘碎片）
     → 半径 0.10/0.08 收到 0.085/0.07，z 门限从 (0.60,0.67) 抬到 (0.66,0.70)，
       脸颊/眉毛（z<0.66）永远拿不到耳朵权重。
  2. 眼皮缺失（眼球定位反复失败被跳过）且旧采样取虹膜 uv（闭眼=琥珀盖琥珀=隐形）
     → 改用**琥珀色像素反查**：虹膜是全模型唯一的强琥珀色区域（铃铛用 z 过滤），
       贴图采样聚类出两只眼；眼皮碗采样**眉毛上方皮毛**的 uv（闭眼是毛色），
       建模时预旋 -100° 藏进头骨内，rest 完全隐形，Blink 时骨转 +190° 扫下来盖住眼球。
  3. 尾根开口（生成的尾巴是根端无盖的管，深色内壁外露）且与臀部有缝
     → 边界环扇形补盖 + 尾根区域顶点朝附近身体质心拉埋进臀部。

骨骼坐标全部来自 measure_v2.py / measure_v2b.py / measure_v2c.py 的实测，不是按比例猜。

用法：
    blender --background --python rig_cat_v2.py -- <in.glb> <out.blend> <check_dir>
产出：<check_dir>/eyelid_meta.json（animate_cat.py 的眨眼参数契约）
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
# 实测依据：
#   身高 0.876  颈线 z≈0.36（剖面宽度极小 0.614）  耳片 z 0.66~0.876（厚度骤降到 0.15）
#   尾巴 +X 侧：x 0.31~0.39、y≈0.40（身后）、z 0.05→0.53，全程离体
BONES = [
    ('Root',  None,    (0.00, 0.05, 0.000), (0.00, 0.05, 0.100), False, False),
    ('Hips',  'Root',  (0.02, 0.10, 0.120), (0.02, 0.09, 0.220), False, True),
    ('Spine', 'Hips',  (0.02, 0.09, 0.220), (0.02, 0.08, 0.290), True, True),
    ('Chest', 'Spine', (0.02, 0.08, 0.290), (0.02, 0.06, 0.335), True, True),
    ('Neck',  'Chest', (0.02, 0.06, 0.335), (0.02, 0.02, 0.400), True, True),
    ('Head',  'Neck',  (0.02, 0.02, 0.400), (0.02, -0.02, 0.560), True, True),
    # 耳朵：measure_v2b 实测（+X 耳根(0.096,-0.187,0.659) 耳尖(0.129,-0.247,0.783)；
    #                      -X 耳根(-0.198,-0.161,0.661) 耳尖(-0.248,-0.174,0.788)）
    # 门限 0.66 起：耳片几何从 z≈0.66 才开始，以下全是头骨——绝不能让耳朵骨碰到脸
    ('EarL1', 'Head',  (0.096, -0.187, 0.659), (0.112, -0.217, 0.721), False, True),
    ('EarL2', 'EarL1', (0.112, -0.217, 0.721), (0.129, -0.247, 0.783), True, True),
    ('EarR1', 'Head',  (-0.198, -0.161, 0.661), (-0.223, -0.168, 0.725), False, True),
    ('EarR2', 'EarR1', (-0.223, -0.168, 0.725), (-0.248, -0.174, 0.788), True, True),
    # 尾巴：measure_v2 复核的路径（+X 侧，根(0.28,0.30,0.04) -> 尖(0.35,0.28,0.49)，S 形）
    ('Tail1', 'Hips',  (0.275, 0.300, 0.045), (0.325, 0.345, 0.140), False, True),
    ('Tail2', 'Tail1', (0.325, 0.345, 0.140), (0.372, 0.373, 0.240), True, True),
    ('Tail3', 'Tail2', (0.372, 0.373, 0.240), (0.345, 0.325, 0.345), True, True),
    ('Tail4', 'Tail3', (0.345, 0.325, 0.345), (0.348, 0.285, 0.490), True, True),
]
# 眼皮骨在眼球定位之后动态加入：head=眼球中心，tail=center+(0.05,0,0)。
# 两根都指向世界 +X：骨局部 Y = 世界 X，眨眼绕骨局部 Y 转 = 绕眼球水平轴扫，
# 左右眼用同一个符号，不用分别取反。

RADIUS = {
    'Hips': 0.30, 'Spine': 0.28, 'Chest': 0.24, 'Neck': 0.20, 'Head': 0.30,
    'EarL1': 0.085, 'EarL2': 0.07, 'EarR1': 0.085, 'EarR2': 0.07,
    'Tail1': 0.09, 'Tail2': 0.085, 'Tail3': 0.08, 'Tail4': 0.075,
    'EyelidL': 0.0, 'EyelidR': 0.0,     # 半径 0 = 不参与身体距离场
}

GATE = {
    'Head': ('above', 0.300, 0.390),
    'Neck': ('above', 0.250, 0.310),
    'EarL1': ('above', 0.660, 0.700), 'EarL2': ('above', 0.700, 0.760),
    'EarR1': ('above', 0.660, 0.700), 'EarR2': ('above', 0.700, 0.760),
    'Tail1': ('below', 0.450, 0.550),
    'Tail2': ('below', 0.470, 0.570),
    'Tail3': ('below', 0.500, 0.600),
    'Tail4': ('below', 0.540, 0.640),
}

# 脸前缘保险锁：v2 没有胡须几何，这个锁只为兜住离头骨轴过远的脸颊前缘碎片。
# 盒子必须避开围巾（围巾前顶 z≈0.43）——上一版 z 从 0.36 起把围巾前襟锁到了
# Head 上，头一动围巾就被扯开（已实测踩到）。
LOCKS = [
    (lambda p: 0.44 < p.z < 0.60 and p.y < -0.32, 'Head', 1.0),
]

MAX_INFLUENCE = 4
SMOOTH_ITERS = 8
NEIGH_CELL = 0.022
NEIGH_RADIUS = 0.032
NEIGH_MAX = 12

# 眨眼参数（视觉核对后定稿；写进 eyelid_meta.json 供 animate_cat.py 使用）
EYELID_REST_DEG = -100.0     # 建模时预旋：皮碗藏进头骨内（rest 完全隐形）
# 闭合角度 160 而不是 190：190 会让皮碗把整只眼球完全盖住，静帧读作"眼球消失"
# （两个毛色圆球 = 骷髅眼窝，已实测被评审打回）；160 时皮碗盖住上 85%，下缘留出
# 一线眼球，读作"闭眼"而不是"没眼睛"。动效里一样是完整的眨眼。
EYELID_CLOSED_DEG = 160.0
EYE_RADIUS = 0.055           # 眼球半径归一值（probe_eye_pick 实测拟合 0.040~0.061，取稳态值）


def smoothstep(lo, hi, x):
    if hi <= lo:
        return 0.0 if x < lo else 1.0
    t = max(0.0, min(1.0, (x - lo) / (hi - lo)))
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
    denom = float(ab @ ab)
    t = 0.0 if denom < 1e-12 else max(0.0, min(1.0, float((p - a) @ ab) / denom))
    return float(np.linalg.norm(p - (a + ab * t)))


def per_vertex_uv(mesh):
    uv = np.zeros((len(mesh.data.vertices), 2), dtype=np.float64)
    layer = mesh.data.uv_layers.active.data
    for poly in mesh.data.polygons:
        for li in poly.loop_indices:
            vi = mesh.data.loops[li].vertex_index
            uv[vi] = layer[li].uv[:]
    return uv


def fit_sphere(pts):
    """代数最小二乘拟合球：(x²+y²+z²) = 2c·p + (r²-|c|²)"""
    A = np.hstack([2.0 * pts, np.ones((len(pts), 1))])
    b = (pts ** 2).sum(axis=1)
    sol, *_ = np.linalg.lstsq(A, b, rcond=None)
    c = sol[:3]
    r = float(np.sqrt(max(sol[3] + float(c @ c), 1e-9)))
    return c, r


def sphere_cap(center, radius, half_angle_deg, rings=8, segs=20):
    half = math.radians(half_angle_deg)
    verts = [tuple(center + np.array([0.0, 0.0, radius]))]
    faces, ring_start = [], []
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


def reset():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def import_model():
    bpy.ops.import_scene.gltf(filepath=GLB)
    mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
    bpy.context.view_layer.objects.active = mesh
    mesh.select_set(True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    return mesh


# ------------------------------------------------------------------ 尾根修复
TAIL_ROOT = np.array([0.28, 0.30, 0.045])   # measure_v2 实测尾根
TAIL_PULL = 0.055                           # 尾根朝身体最多拉多少


def fix_tail_root(mesh):
    """生成的尾巴根端是开口管（深色内壁外露），且与臀部有缝。
    1) 尾根区域的边界环扇形补盖（继承环上 uv）；
    2) 尾根区域顶点朝附近身体顶点质心拉，埋进臀部。
    返回是否完成了补盖。"""
    import bmesh
    me = mesh.data
    # -- 边界环：只被 1 个面使用的边，且落在尾根区域盒内
    edge_faces = {}
    for poly in me.polygons:
        for ek in poly.edge_keys:
            edge_faces[ek] = edge_faces.get(ek, 0) + 1
    co = np.array([v.co[:] for v in me.vertices])
    rim = set()
    for (vi, vj), n in edge_faces.items():
        if n != 1:
            continue
        for v in (vi, vj):
            p = co[v]
            if p[2] < 0.14 and p[0] > 0.22 and p[1] > 0.20:
                rim.add(v)
    rim = sorted(rim)
    print('[tail] 尾根区域边界顶点 %d' % len(rim))
    filled = False
    if len(rim) >= 6:
        # 贪心最近邻串链成环
        order = [rim[0]]
        rest = rim[1:]
        while rest:
            dd = [float(np.linalg.norm(co[i] - co[order[-1]])) for i in rest]
            nxt = rest[int(np.argmin(dd))]
            order.append(nxt)
            rest.remove(nxt)
        gaps = [float(np.linalg.norm(co[order[(k + 1) % len(order)]] - co[order[k]]))
                for k in range(len(order))]
        # 碎片化网格里可能混进别的碎片边界：环上有大跳口就不补，只靠埋根遮
        if max(gaps) < 0.06:
            bm = bmesh.new()
            bm.from_mesh(me)
            bm.verts.ensure_lookup_table()
            vs = [bm.verts[i] for i in order]
            cent = bm.verts.new(co[order].mean(axis=0))
            uvlay = bm.loops.layers.uv.active
            mean_uv = np.mean([[l[uvlay].uv.x, l[uvlay].uv.y]
                               for v in vs for l in v.link_loops], axis=0) \
                if uvlay else None
            for k in range(len(vs)):
                f = bm.faces.new((cent, vs[k], vs[(k + 1) % len(vs)]))
                f.smooth = True
                if uvlay:
                    for loop in f.loops:
                        if loop.vert == cent:
                            loop[uvlay].uv = (float(mean_uv[0]), float(mean_uv[1]))
                        else:
                            src = [l for l in loop.vert.link_loops]
                            if src:
                                loop[uvlay].uv = (src[0][uvlay].uv.x, src[0][uvlay].uv.y)
            bm.normal_update()
            bm.to_mesh(me)
            bm.free()
            me.update()
            filled = True
            print('[tail] 尾根开口补盖：%d 边界顶点成环（最大缺口 %.3f）' % (len(order), max(gaps)))
        else:
            print('[tail] 边界环最大缺口 %.3f > 0.06，疑似混入其他碎片，跳过补盖' % max(gaps))
    # -- 埋根：尾根附近顶点朝身体质心拉。开口区（z<0.10 的管底）全量拉，
    #    否则侧面视角仍会从臀部缝里看到没盖的管口（上一版拉得太轻，已实测踩到）
    co2 = np.array([v.co[:] for v in me.vertices])
    dd = np.linalg.norm(co2 - TAIL_ROOT, axis=1)
    body = co2[(co2[:, 0] < 0.24) & (dd < 0.20)]
    if len(body) >= 20:
        anchor = body.mean(axis=0)
        moved = 0
        for i, p in enumerate(co2):
            opening = p[2] < 0.10 and p[0] > 0.22 and p[1] > 0.20
            if dd[i] >= 0.13 and not opening:
                continue
            if opening:
                t = 1.0
            else:
                t = (1.0 - dd[i] / 0.13) ** 2
            co2[i] = p + (anchor - p) * t
            moved += 1
        for i, v in enumerate(me.vertices):
            v.co = Vector(co2[i])
        print('[tail] 尾根埋入：拉 %d 顶点 -> 身体质心 %s' % (moved, np.round(anchor, 3).tolist()))
    else:
        print('[tail] 附近身体顶点不足（%d），跳过埋根' % len(body))
    return filled


# ------------------------------------------------------------------ 眼球定位
def iris_amber_linear(rgb):
    """虹膜判定（线性空间）。阈值来自 probe_iris.py 对本模型贴图的实测直方图：
    虹膜核心 g/r≈0.55~0.65（线性 RGB 约 0.41~0.55 / 0.24~0.37 / 0.17~0.30），
    比肉眼感知灰得多；奶油毛 g/r≈0.87、鼻子/腮红粉棕 g/r 相近但 z 低且 b 高。
    几何校验（z 门限 + 拟合球半径区间 + 双眼对称）在 detect_eyes 里做。"""
    r, g, b = rgb[:, 0], rgb[:, 1], rgb[:, 2]
    order = (r >= g) & (g >= b)
    warm = (r - b) > 0.12
    ratio = g / np.maximum(r, 1e-6)
    return order & warm & (ratio > 0.50) & (ratio < 0.72)


def tex_sampler(mesh):
    """返回 uv->线性RGB 采样函数（无贴图时返回 None）。"""
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
    px = px.reshape(-1, 4)[:, :3]            # Blender float = 线性空间

    def sample(uv):
        xi = np.clip((uv[:, 0] * W).astype(int), 0, W - 1)
        yi = np.clip((uv[:, 1] * H).astype(int), 0, H - 1)
        return px[yi * W + xi]

    return sample


def detect_eyes(mesh):
    """眼球定位，两级策略：
    1) eye_pick.json（probe_eye_pick.py 产出，射线命中已人工核对标记落点）—— 优先；
    2) 虹膜色聚类（自动，失败则放弃，绝不猜坐标——旧锚点建出过悬浮球）。
    返回 [(center, radius, n), ...] 按 x 从大到小。"""
    pick_path = os.path.join(CHECK, 'eye_pick.json')
    if os.path.isfile(pick_path):
        with open(pick_path, encoding='utf-8') as fh:
            pick = json.load(fh)
        got = pick.get('eyes', [])
        if len(got) == 2:
            # 半径用每只眼自己的边界拟合值（多射线边界环拟合，probe_eye_pick.py）；
            # 统一归一半径会让大眼盖不住、小眼凸成球（已实测踩到）
            eyes = []
            for e in sorted(got, key=lambda e: -e['center'][0]):
                r_fit = float(e.get('radius') or EYE_RADIUS)
                eyes.append((np.array(e['center']), r_fit, e.get('n', 0)))
            print('[eyes] 使用 eye_pick.json 的边界拟合结果（每眼独立半径）')
            for i, (center, radius, n) in enumerate(eyes):
                print('[eyes] 眼球%d 中心 %s 半径 %.4f' % (i, np.round(center, 4).tolist(), radius))
            return eyes
        print('[eyes] eye_pick.json 眼数 != 2，忽略')
    mat = mesh.data.materials[0] if mesh.data.materials else None
    img = None
    if mat and mat.use_nodes:
        for n in mat.node_tree.nodes:
            if n.type == 'TEX_IMAGE' and n.image:
                img = n.image
                break
    co = np.array([v.co[:] for v in mesh.data.vertices])
    eyes = []
    if img is not None:
        W, H = img.size
        px = np.empty(W * H * 4, dtype=np.float32)
        img.pixels.foreach_get(px)
        px = px.reshape(-1, 4)[:, :3]            # Blender float = 线性空间
        uv = per_vertex_uv(mesh)
        xi = np.clip((uv[:, 0] * W).astype(int), 0, W - 1)
        yi = np.clip((uv[:, 1] * H).astype(int), 0, H - 1)
        rgb = px[yi * W + xi]
        amber = iris_amber_linear(rgb) & (co[:, 2] > 0.44)
        pts = co[amber]
        print('[eyes] 虹膜色顶点 %d' % len(pts))
        if len(pts) >= 30:
            cell = {}
            for i, p in enumerate(pts):
                cell.setdefault((int(p[0] / 0.03), int(p[1] / 0.03), int(p[2] / 0.03)), []).append(i)
            seen, clusters = set(), []
            for start in range(len(pts)):
                if start in seen:
                    continue
                stack, comp = [start], []
                seen.add(start)
                while stack:
                    ci = stack.pop()
                    comp.append(ci)
                    bx, by, bz = int(pts[ci][0] / 0.03), int(pts[ci][1] / 0.03), int(pts[ci][2] / 0.03)
                    for dx in (-1, 0, 1):
                        for dy in (-1, 0, 1):
                            for dz in (-1, 0, 1):
                                for j in cell.get((bx + dx, by + dy, bz + dz), ()):
                                    if j not in seen:
                                        seen.add(j)
                                        stack.append(j)
                if len(comp) >= 15:
                    clusters.append(pts[comp])
            clusters.sort(key=len, reverse=True)
            print('[eyes] 聚类 %d 个，尺寸 %s' % (len(clusters), [len(c) for c in clusters[:6]]))
            for c in clusters:
                centroid = c.mean(axis=0)
                if centroid[2] < 0.44:
                    continue                       # 鼻头(z≈0.41)/腮红(z≈0.42)高度带
                near = co[np.linalg.norm(co - centroid, axis=1) < 0.05]
                if len(near) < 15:
                    continue
                center, radius = fit_sphere(near)
                if not 0.04 <= radius <= 0.08:
                    print('[eyes] 簇 @%s 拟合半径 %.4f 出眼球范围，弃'
                          % (np.round(centroid, 3).tolist(), radius))
                    continue
                eyes.append((center, radius, len(near)))
            if len(eyes) >= 2:
                eyes.sort(key=len, reverse=True)
                eyes = eyes[:2]
            if len(eyes) == 2 and float(np.linalg.norm(eyes[0][0] - eyes[1][0])) < 0.07:
                print('[eyes] 两簇中心几乎重合（同一只眼被拆分），弃用聚类结果')
                eyes = []
            elif len(eyes) == 2 and abs(float(eyes[0][0][2]) - float(eyes[1][0][2])) > 0.08:
                print('[eyes] 双眼 z 相差过大（%.3f），不像一对眼，弃用'
                      % abs(float(eyes[0][0][2]) - float(eyes[1][0][2])))
                eyes = []
        else:
            print('[eyes] 虹膜色顶点过少，聚类跳过')
    else:
        print('[eyes] 未找到基础色贴图')
    if len(eyes) != 2:
        # 明确放弃，绝不拿不可靠的坐标建眼皮 —— 上一版用旧锚点建出了悬浮球（已实测踩到）
        print('[eyes] 聚类未定位到双眼（%d），眼皮跳过' % len(eyes))
        return []
    eyes.sort(key=lambda e: -float(e[0][0]))
    for i, (center, radius, n) in enumerate(eyes):
        print('[eyes] 眼球%d 中心 %s 半径 %.4f（%d 点）' % (
            i, np.round(center, 4).tolist(), radius, n))
    if len(eyes) != 2:
        print('[eyes] 眼球定位失败，眼皮跳过')
        return []
    return eyes


def build_eyelids(mesh, eyes):
    """皮碗采样**眉毛上方皮毛**的 uv（闭眼是毛色，不是虹膜色）；
    建模时预旋 EYELID_REST_DEG 藏进头骨 —— rest 完全隐形。
    cap 半径 1.10×眼球：闭合时略凸于眼球表面（1.04× 会整只缩进眼球里，
    眨眼看不见——已实测踩到）。
    皮毛 uv 不是取"最近顶点"——最近的可能落在眼睛深色描边上，闭眼成了棕球
    （已实测踩到）；改为在眉带里挑**贴图色是灰毛**的顶点。"""
    co = np.array([v.co[:] for v in mesh.data.vertices])
    uv = per_vertex_uv(mesh)
    sample = tex_sampler(mesh)
    mat = mesh.data.materials[0] if mesh.data.materials else None
    th = math.radians(EYELID_REST_DEG)
    rx = np.array([[1.0, 0.0, 0.0],
                   [0.0, math.cos(th), -math.sin(th)],
                   [0.0, math.sin(th), math.cos(th)]])
    out = []
    for name, (center, radius, _n) in zip(('EyelidL', 'EyelidR'), eyes):
        verts, faces = sphere_cap(center, radius * 1.07, 50.0, 8, 20)
        vv = (np.array(verts) - center) @ rx.T + center
        me = bpy.data.meshes.new(name + 'Mesh')
        me.from_pydata([tuple(v) for v in vv], [], faces)
        me.update()
        # 眉带：眼中心上方 z +0.025~+0.09、水平 ±0.10 的顶点里挑灰毛
        probe = center + np.array([0.0, 0.0, 0.055])
        band = (co[:, 2] > center[2] + 0.025) & (co[:, 2] < center[2] + 0.09) \
            & (np.abs(co[:, 0] - center[0]) < 0.10) & (co[:, 1] < center[1] + 0.05) \
            & (np.linalg.norm(co - center, axis=1) < 0.14)
        idx = np.where(band)[0]
        fur = None
        if len(idx) and sample is not None:
            rgb = sample(uv[idx])
            grey = (np.abs(rgb[:, 0] - rgb[:, 1]) < 0.09) \
                & (np.abs(rgb[:, 1] - rgb[:, 2]) < 0.09) \
                & (rgb[:, 0] > 0.15) & (rgb[:, 0] < 0.75)
            cand = idx[grey]
            if len(cand):
                dd = np.linalg.norm(co[cand] - probe, axis=1)
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
        print('[eyes] %s 眼皮生成：中心 %s r=%.4f 皮毛uv(%.3f,%.3f)' % (
            name, np.round(center, 4).tolist(), radius, uv[fur][0], uv[fur][1]))
    return out


def bind_eyelids(eyelids, arm):
    for name, ob, _center in eyelids:
        grp = ob.vertex_groups.new(name=name)
        grp.add([v.index for v in ob.data.vertices], 1.0, 'REPLACE')
        ob.parent = arm
        ob.matrix_parent_inverse = arm.matrix_world.inverted()
        mod = ob.modifiers.new('Armature', 'ARMATURE')
        mod.object = arm


def build_armature(eyelid_centers):
    arm_data = bpy.data.armatures.new('CatArmature')
    arm = bpy.data.objects.new('CatRig', arm_data)
    bpy.context.collection.objects.link(arm)
    bpy.context.view_layer.objects.active = arm
    arm.select_set(True)
    bpy.ops.object.mode_set(mode='EDIT')
    eb = arm_data.edit_bones
    table = list(BONES) + [
        (name, 'Head', (c[0], c[1], c[2]), (c[0] + 0.05, c[1], c[2]), False, True)
        for name, _ob, c in eyelid_centers
    ]
    for name, parent, head, tail, connect, deform in table:
        b = eb.new(name)
        b.head, b.tail = Vector(head), Vector(tail)
        b.use_deform = deform
        if parent:
            b.parent = eb[parent]
            b.use_connect = connect
    bpy.ops.object.mode_set(mode='OBJECT')
    return arm


def custom_weights(mesh, arm):
    deform = [b for b in arm.data.bones if b.use_deform]
    names = [b.name for b in deform]
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
                forced = (names.index(bone), value)
                break
        if forced:
            locked += 1
            W.append({forced[0]: forced[1]})
            continue
        row = {}
        for bi in range(len(names)):
            dd = seg_dist(p, segs[bi][0], segs[bi][1])
            rr = radii[bi]
            if dd >= rr:
                continue
            g = gate_of(names[bi], p.z)
            if g <= 1e-4:
                continue
            row[bi] = ((1.0 - dd / rr) ** 3) * g
        if not row:
            # 兜底只给**身体骨骼**：眼睑骨半径 0 不参与，耳朵骨不能兜底抢脸
            core = [bi for bi in range(len(names)) if not names[bi].startswith(('Eyelid', 'Ear'))]
            best = min(core, key=lambda bi: seg_dist(p, segs[bi][0], segs[bi][1]))
            row = {best: 1.0}
        W.append(row)
    print('[rig] 空间锁 %d 顶点' % locked)

    # 空间邻接：网格是碎片化的（v2 有近千个连通岛），平滑必须能跨碎片缝
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
        adj[i] = found
    print('[rig] 空间邻接：%d/%d 顶点有邻居' % (sum(1 for a in adj if a), nv))

    for _ in range(SMOOTH_ITERS):
        new = list(W)
        for ci in range(nv):
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
    for row in W:
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
        for vi, w in buckets[n]:
            groups[n].add([vi], w, 'REPLACE')
    mesh.parent = arm
    mesh.matrix_parent_inverse = arm.matrix_world.inverted()
    mod = mesh.modifiers.new('Armature', 'ARMATURE')
    mod.object = arm


def report(mesh):
    rows = []
    for g in mesh.vertex_groups:
        cnt = sum(1 for v in mesh.data.vertices
                  for ge in v.groups if ge.group == g.index)
        rows.append({'bone': g.name, 'verts': cnt})
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
    cam_data = bpy.data.cameras.new('Cam')
    cam_data.lens = 62
    cam = bpy.data.objects.new('Cam', cam_data)
    bpy.context.collection.objects.link(cam)
    cam.location = Vector((0.55, -2.00, 0.60))
    d = Vector((0.0, -0.02, 0.40)) - cam.location
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    scene.camera = cam


def shoot(path, tag):
    bpy.context.scene.render.filepath = path
    bpy.ops.render.render(write_still=True)
    print('[rig] rendered %s -> %s' % (tag, path))


def pose_bone(arm, name, rot_deg=(0, 0, 0), scale=None):
    pb = arm.pose.bones[name]
    pb.rotation_mode = 'XYZ'
    pb.rotation_euler = Euler([math.radians(a) for a in rot_deg], 'XYZ')
    if scale:
        pb.scale = Vector(scale)
    bpy.context.view_layer.update()


def clear_pose(arm):
    for pb in arm.pose.bones:
        pb.rotation_mode = 'XYZ'
        pb.rotation_euler = Euler((0, 0, 0), 'XYZ')
        pb.scale = Vector((1, 1, 1))
    bpy.context.view_layer.update()


# ------------------------------------------------------------------ 主流程
reset()
mesh = import_model()
print('[rig] mesh=%s verts=%d dims=%s' % (mesh.name, len(mesh.data.vertices),
                                          [round(v, 4) for v in mesh.dimensions]))
fix_tail_root(mesh)
eyes = detect_eyes(mesh)
eyelids = build_eyelids(mesh, eyes) if eyes else []
arm = build_armature(eyelids)
if eyelids:
    bind_eyelids(eyelids, arm)
names, weights = custom_weights(mesh, arm)
apply_weights(mesh, arm, names, weights)

rows = report(mesh)
print('[rig] deform bones=%d' % len(names))
for r in rows:
    flag = '' if r['verts'] > 0 else '   <== 没拿到顶点'
    print('   %-8s verts=%5d%s' % (r['bone'], r['verts'], flag))
empty = [r['bone'] for r in rows if r['verts'] == 0]
print('[rig] 空骨骼组:', empty if empty else '无')

setup_render()
clear_pose(arm)
shoot(os.path.join(CHECK, '01_rest.png'), 'rest')

clear_pose(arm)
# 动画峰值姿势 = Happy 的最大幅度（验收标准：这个姿势必须干净）
pose_bone(arm, 'Neck', rot_deg=(0, -8, 0))
pose_bone(arm, 'Head', rot_deg=(-9, -14, 0))
pose_bone(arm, 'EarL1', rot_deg=(0, 0, 22))
pose_bone(arm, 'EarR1', rot_deg=(0, 0, -18))
shoot(os.path.join(CHECK, '02_anim_peak.png'), 'head+ears (anim peak)')

clear_pose(arm)
# 极限姿势 = 超出动画的余量测试（有轻微形变可接受，不许撕裂/翘碎片）
pose_bone(arm, 'Neck', rot_deg=(-6, 10, 0))
pose_bone(arm, 'Head', rot_deg=(-12, 20, 0))
pose_bone(arm, 'EarL1', rot_deg=(0, 0, -36))
pose_bone(arm, 'EarR1', rot_deg=(0, 0, 36))
shoot(os.path.join(CHECK, '02b_stress.png'), 'head+ears (stress)')

clear_pose(arm)
pose_bone(arm, 'Spine', rot_deg=(-14, 0, 0))
pose_bone(arm, 'Chest', rot_deg=(-10, 0, 0))
pose_bone(arm, 'Neck', rot_deg=(-14, 0, 0))
pose_bone(arm, 'Head', rot_deg=(10, 0, 0))
shoot(os.path.join(CHECK, '03_lean.png'), 'lean (stress)')

clear_pose(arm)
pose_bone(arm, 'Chest', scale=(1.07, 1.07, 1.02))
pose_bone(arm, 'Spine', scale=(1.05, 1.05, 1.01))
pose_bone(arm, 'Hips', scale=(1.03, 1.03, 1.0))
shoot(os.path.join(CHECK, '04_breath.png'), 'breath')

clear_pose(arm)
for i in range(4):
    pose_bone(arm, 'Tail%d' % (i + 1), rot_deg=(0, 0, 20 + i * 4))
cam = bpy.context.scene.camera
cam.location = Vector((2.3, 0.4, 1.0))
look = Vector((0.0, 0.30, 0.40)) - cam.location
cam.rotation_euler = look.to_track_quat('-Z', 'Y').to_euler()
shoot(os.path.join(CHECK, '05_tail_side.png'), 'tail (side)')

clear_pose(arm)
cam = bpy.context.scene.camera
cam.location = Vector((0.42, -1.15, 0.60))
look = Vector((0.0, -0.12, 0.48)) - cam.location
cam.rotation_euler = look.to_track_quat('-Z', 'Y').to_euler()
shoot(os.path.join(CHECK, '06_head_closeup.png'), 'head closeup')

if eyelids:
    clear_pose(arm)
    for name, _ob, _c in eyelids:
        pose_bone(arm, name, rot_deg=(0, EYELID_CLOSED_DEG, 0))
    shoot(os.path.join(CHECK, '07_blink_closed.png'), 'blink closed')
    clear_pose(arm)

bpy.ops.wm.save_as_mainfile(filepath=OUT_BLEND)
with open(os.path.join(CHECK, 'weights.json'), 'w', encoding='utf-8') as fh:
    json.dump(rows, fh, ensure_ascii=False, indent=2)
with open(os.path.join(CHECK, 'eyelid_meta.json'), 'w', encoding='utf-8') as fh:
    json.dump({
        'present': bool(eyelids),
        'axis': 'Y',                       # 眼皮骨局部 Y = 世界 X（水平扫轴）
        'rest_deg': EYELID_REST_DEG,       # 已烘进几何，动画里恒为 0
        'closed_deg': EYELID_CLOSED_DEG,
        'eyes': [{'bone': n, 'center': [round(float(x), 4) for x in c],
                  'radius': round(float(eyes[i][1]), 4)}
                 for i, (n, _ob, c) in enumerate(eyelids)],
    }, fh, ensure_ascii=False, indent=2)
print('[rig] saved', OUT_BLEND)
