"""奶灰猫几何 + 面部定位分析（Blender headless）。

在绑骨之前把三件事变成事实：
  1) **竖直剖面**：沿世界 Z（glTF 导入后 Z 向上）逐层量水平包围宽度。
     正坐的猫：头宽 → 脖子窄 → 躯干宽，所以宽度曲线的极小值就是脖子，
     头/躯干的分界可以从几何反推，而不是按高度百分比瞎放骨骼。
  2) **面部朝向**：头部各切面的水平质心偏移方向 = 脸朝哪边（鼻子把质心推向前）。
  3) **眼睛中心**：反照率贴图里唯一的高饱和暖色区是琥珀金虹膜。
     把 UV 落在虹膜像素上的顶点收集起来按 X 正负聚类，两类质心就是两只眼球的中心 ——
     这是「用贴图反查几何」而不是按比例猜，后面加眼皮几何、做视线跟随都要用它。

运行：
    blender --background --python blender_probe.py -- <model.glb> [out.json]
"""
import json
import sys

import bpy
import numpy as np
from mathutils import Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
GLB = argv[0]
OUT = argv[1] if len(argv) > 1 else None

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB)

meshes = [o for o in bpy.context.scene.objects if o.type == 'MESH']
obj = meshes[0]

# 先把旋转/缩放落到网格上：glTF 导入会把 +Z 向上的转换写成节点的四元数旋转，
# 不 apply 的话局部坐标与世界坐标不一致，后面所有测量与骨骼落位都会错。
bpy.context.view_layer.objects.active = obj
obj.select_set(True)
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

rep = {'blender': bpy.app.version_string, 'mesh': obj.name,
       'verts': len(obj.data.vertices), 'faces': len(obj.data.polygons),
       'uv_layers': [l.name for l in obj.data.uv_layers],
       'materials': [s.material.name if s.material else None for s in obj.material_slots]}

co = np.array([v.co[:] for v in obj.data.vertices], dtype=np.float64)
rep['bbox_min'] = [round(float(v), 4) for v in co.min(axis=0)]
rep['bbox_max'] = [round(float(v), 4) for v in co.max(axis=0)]
rep['bbox_size'] = [round(float(v), 4) for v in (co.max(axis=0) - co.min(axis=0))]

z0, z1 = co[:, 2].min(), co[:, 2].max()
H = z1 - z0
rep['height'] = round(float(H), 4)

# ---- 1. 竖直剖面（沿 Z，向上为正）----
N = 60
prof = []
for i in range(N):
    lo, hi = z0 + H * i / N, z0 + H * (i + 1) / N
    m = (co[:, 2] >= lo) & (co[:, 2] < hi)
    if m.sum() == 0:
        prof.append({'i': i, 'frac': round((i + 0.5) / N, 4), 'n': 0,
                     'xw': 0.0, 'yw': 0.0, 'xc': 0.0, 'yc': 0.0})
        continue
    p = co[m]
    prof.append({
        'i': i, 'frac': round((i + 0.5) / N, 4), 'n': int(m.sum()),
        'xw': round(float(p[:, 0].max() - p[:, 0].min()), 4),
        'yw': round(float(p[:, 1].max() - p[:, 1].min()), 4),
        'xc': round(float((p[:, 0].max() + p[:, 0].min()) / 2), 4),
        'yc': round(float((p[:, 1].max() + p[:, 1].min()) / 2), 4),
    })
rep['profile'] = prof

# 脖子：宽度曲线的局部极小。
# 注意搜索区间必须覆盖到 0.30 以下 —— 这是一只 Q 版大头身比的猫，
# 头占了整高的 42%，真正的颈线在 frac≈0.41；下界若从 0.5 起搜，
# 会把头内部因耳根起伏造成的假极小当成脖子（第一版即栽在这里）。
cands = [p for p in prof if 0.28 < p['frac'] < 0.92 and p['n'] > 0]
neck = None
for k in range(1, len(cands) - 1):
    a, b, c = cands[k - 1], cands[k], cands[k + 1]
    if b['xw'] <= a['xw'] and b['xw'] <= c['xw']:
        if neck is None or b['xw'] < neck['xw']:
            neck = b
rep['neck'] = neck
rep['widest'] = max((p for p in prof if p['n'] > 0), key=lambda p: p['xw'])

# 耳朵：越往上**厚度(yw)**骤降的那一段（耳朵是薄片），用深度而不是宽度来判定
ear_base = None
for p in prof:
    if p['n'] > 0 and p['frac'] > 0.75 and p['yw'] < 0.36:
        ear_base = p
        break
rep['ear_base'] = ear_base

# ---- 尾巴走向：低空俯视占位图 ----
# 尾巴在融合网格里绕身，包围盒/宽度曲线都看不出它的路径（它就贴在人体的最大宽度处）。
# 这里按高度带打 ASCII 俯视图：行=y（前后，-Y 是脸），列=x（左右），
# 尾巴会在低空层显出一条**脱离躯干主团**的窄条 —— 照着它放骨骼链。
def occupancy(band_lo, band_hi, cols=58, rows=26):
    m = (co[:, 2] >= band_lo) & (co[:, 2] < band_hi)
    p = co[m]
    if len(p) == 0:
        return ['(empty)']
    x0, x1 = co[:, 0].min(), co[:, 0].max()
    y0_, y1_ = co[:, 1].min(), co[:, 1].max()
    grid = [[' '] * cols for _ in range(rows)]
    for v in p:
        cx = int((v[0] - x0) / (x1 - x0 + 1e-9) * (cols - 1))
        cy = int((v[1] - y0_) / (y1_ - y0_ + 1e-9) * (rows - 1))
        grid[cy][cx] = '#'
    # 标注脸的方向：-Y 在表的底部
    out = ['   x %+.3f .. %+.3f   y %+.3f(脸,-Y) .. %+.3f(尾根,+Y)' % (x0, x1, y0_, y1_)]
    for r in range(rows - 1, -1, -1):
        out.append('   |' + ''.join(grid[r]) + '|')
    return out


for lo, hi in ((0.02, 0.09), (0.09, 0.16), (0.16, 0.24)):
    print('---- Z %.2f..%.2f 俯视 ----' % (lo, hi))
    for line in occupancy(lo, hi):
        print(line)
    print()

# ---- 2. 眼睛中心：反照率贴图的琥珀金虹膜 -> 反查顶点 ----
eye = {'ok': False}
img = None
for slot in obj.material_slots:
    mat = slot.material
    if not mat or not mat.use_nodes:
        continue
    for node in mat.node_tree.nodes:
        if node.type == 'TEX_IMAGE' and node.image:
            # baseColor 是那张 JPEG（按 glTF 导入后通常是唯一带 sRGB 的贴图）
            if node.image.size[0] >= 512 and (
                    node.image.colorspace_settings.name == 'sRGB' or img is None):
                img = node.image
                break
    if img:
        break

if img is not None:
    W, Himg = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(Himg, W, 4)
    # 用 Non-Color 读到的就是 sRGB/255；若是线性的，下面的阈值会一个都命中不了，
    # 因此这里同时准备一份线性->sRGB 的近似换算结果做兜底。
    def amber_mask(buf):
        # 紧阈值：奶灰猫的虹膜是 #C98A3E，而**浅金铃铛 #E7C87F 与藕粉围巾**都在同一色相族里，
        # 阈值一松就会把胸口也筛成"虹膜"（第一版 eye 质心落在 z≈0.15~0.22 正是铃铛）。
        r, g, b = buf[:, :, 0], buf[:, :, 1], buf[:, :, 2]
        mx = np.maximum(np.maximum(r, g), b)
        mn = np.minimum(np.minimum(r, g), b)
        return (r > 0.55) & (r - b > 0.28) & (r - g > 0.12) & ((mx - mn) > 0.22)

    mask = amber_mask(px)
    if mask.sum() < 50:
        lin = np.where(px[:, :, :3] <= 0.0031308, px[:, :, :3] * 12.92,
                       1.055 * np.power(np.clip(px[:, :, :3], 1e-6, None), 1 / 2.4) - 0.055)
        px2 = px.copy()
        px2[:, :, :3] = lin
        mask = amber_mask(px2)
        rep['eye_mask_space'] = 'linear->srgb'
    else:
        rep['eye_mask_space'] = 'as-read'

    rep['eye_mask_px'] = int(mask.sum())
    rep['image_size'] = [W, Himg]

    if mask.sum() >= 20:
        # 顶点 UV（取该顶点任一角所在的 uv）
        uvs = np.zeros((len(obj.data.vertices), 2), dtype=np.float64)
        uv_layer = obj.data.uv_layers.active.data
        for poly in obj.data.polygons:
            for li in poly.loop_indices:
                vi = obj.data.loops[li].vertex_index
                uvs[vi] = uv_layer[li].uv[:]
        u = np.clip((uvs[:, 0] % 1.0) * (W - 1), 0, W - 1).astype(int)
        # Blender 图像行序自下而上，UV 的 v 也是自下而上 -> 直接对应
        v = np.clip((uvs[:, 1] % 1.0) * (Himg - 1), 0, Himg - 1).astype(int)
        on_iris = mask[v, u]
        # 再叠一层几何约束：虹膜只可能在头部（颈线以上），且左右分离。
        # 铃铛、围巾在颈线以下，这一步把它们彻底排除。
        head_floor = (z0 + H * (neck['i'] + 0.5) / N) if neck else (z0 + H * 0.45)
        on_iris &= co[:, 2] > head_floor
        iris_pts = co[on_iris]
        rep['iris_vertex_count'] = int(on_iris.sum())
        if on_iris.sum() >= 6:
            left = iris_pts[iris_pts[:, 0] < 0]
            right = iris_pts[iris_pts[:, 0] >= 0]
            entry = {'ok': True, 'left_n': int(len(left)), 'right_n': int(len(right))}
            for name, grp in (('left', left), ('right', right)):
                if len(grp):
                    c = grp.mean(axis=0)
                    entry[name] = [round(float(x), 4) for x in c]
                    entry[name + '_r'] = round(float(np.linalg.norm(grp - c, axis=1).mean()), 4)
            rep['eye'] = entry
            if 'left' in entry and 'right' in entry:
                rep['eye']['span_x'] = round(abs(entry['left'][0] - entry['right'][0]), 4)

print('== FACE PROBE ==')
print(json.dumps(rep, ensure_ascii=False, indent=2))
if OUT:
    with open(OUT, 'w', encoding='utf-8') as fh:
        json.dump(rep, fh, ensure_ascii=False, indent=2)
    print('wrote', OUT)
