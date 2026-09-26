"""针对新猫 v2 补测：耳朵顶部薄结构、眼睛（限定头部高度内的琥珀色）。

上一版坐标是估的，结果 +X 的眼睛和耳朵都落错（EarL1 只拿到 2 个顶点、EarL2 拿到 0 个）。
所以这里全部改成**从几何/贴图实测**：
  · 耳朵：竖直剖面在 z>0.66 处厚度骤降到 0.15 = 耳片，按 x 正负分簇取质心
  · 眼睛：先用高度限定到头部（z 0.35~0.56），再找反照率里的琥珀金虹膜
    （不限定的话会被金色铃铛干扰 —— v1 出现过 99021 个"虹膜像素"就是这么来的）

用法：
    blender --background --python measure_v2b.py -- <model.glb>
"""
import sys

import bpy
import numpy as np

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
GLB = argv[0]

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB)
mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
bpy.context.view_layer.objects.active = mesh
mesh.select_set(True)
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

co = np.array([v.co[:] for v in mesh.data.vertices])


def per_vertex_uv():
    uv = np.zeros((len(mesh.data.vertices), 2), dtype=np.float64)
    layer = mesh.data.uv_layers.active.data
    for poly in mesh.data.polygons:
        for li in poly.loop_indices:
            vi = mesh.data.loops[li].vertex_index
            uv[vi] = layer[li].uv[:]
    return uv


# ---------- 耳朵 ----------
print('== 耳朵（z > 0.64 的薄结构）==')
top = co[co[:, 2] > 0.64]
print('   顶部顶点 n=%d  x=%.3f..%.3f  y=%.3f..%.3f'
      % (len(top), float(top[:, 0].min()), float(top[:, 0].max()),
         float(top[:, 1].min()), float(top[:, 1].max())))
for sign, name in ((1, 'EarL(+X)'), (-1, 'EarR(-X)')):
    sel = top[sign * top[:, 0] > 0]
    if len(sel) < 20:
        print('   %s: 不足(%d)' % (name, len(sel)))
        continue
    print('   %s n=%d  bbox=%s -> %s  centroid=%s'
          % (name, len(sel),
             np.round(sel.min(axis=0), 3).tolist(), np.round(sel.max(axis=0), 3).tolist(),
             np.round(sel.mean(axis=0), 3).tolist()))
    # 耳根（最低的那 25%）与耳尖（最高的 25%）
    zs = sel[:, 2]
    lo = sel[zs <= np.percentile(zs, 25)]
    hi = sel[zs >= np.percentile(zs, 75)]
    print('      耳根质心 %s   耳尖质心 %s' % (np.round(lo.mean(axis=0), 3).tolist(),
                                              np.round(hi.mean(axis=0), 3).tolist()))

# ---------- 眼睛 ----------
uv = per_vertex_uv()
img = None
if mesh.data.materials and mesh.data.materials[0].use_nodes:
    for n in mesh.data.materials[0].node_tree.nodes:
        if n.type == 'TEX_IMAGE' and n.image:
            img = n.image
            break
if img is None:
    print('\n== 眼睛 == 找不到反照率贴图')
else:
    W, Hi = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(Hi, W, 4)

    def amber(buf):
        r, g, b = buf[:, :, 0], buf[:, :, 1], buf[:, :, 2]
        mx = np.maximum(np.maximum(r, g), b)
        mn = np.minimum(np.minimum(r, g), b)
        return (r > 0.55) & (r - b > 0.28) & (r - g > 0.12) & ((mx - mn) > 0.22)

    mask = amber(px)
    u = np.clip((uv[:, 0] % 1.0) * (W - 1), 0, W - 1).astype(int)
    v = np.clip((uv[:, 1] % 1.0) * (Hi - 1), 0, Hi - 1).astype(int)
    on = mask[v, u]
    # 限定到头部高度，排除胸口铃铛
    head_band = (co[:, 2] > 0.35) & (co[:, 2] < 0.56)
    sel = co[on & head_band]
    print('\n== 眼睛（虹膜 ∩ 头部高度 z 0.35~0.56）==')
    print('   命中顶点 n=%d' % len(sel))
    if len(sel) >= 6:
        xs = sel[:, 0]
        mid = float(xs.mean())
        for sign, name in ((1, '+X 眼'), (-1, '-X 眼')):
            g2 = sel[sign * xs > mid] if sign > 0 else sel[sign * xs >= -mid]
            g2 = sel[(xs >= mid) if sign > 0 else (xs < mid)]
            if len(g2):
                print('   %s n=%d  centroid=%s  bbox=%s' % (name, len(g2),
                                                            np.round(g2.mean(axis=0), 4).tolist(),
                                                            np.round(g2.max(axis=0) - g2.min(axis=0), 3).tolist()))

# ---------- 尾巴复核 ----------
print('\n== 尾巴复核（+X 侧、离体）==')
outer = co[co[:, 0] > 0.26]
if len(outer) > 50:
    zs = outer[:, 2]
    edges = np.linspace(float(zs.min()), float(zs.max()), 9)
    for k in range(8):
        m = (zs >= edges[k]) & (zs <= edges[k + 1])
        if m.sum() >= 8:
            c = outer[m].mean(axis=0)
            print('   z=%.3f n=%4d centroid=%s' % (float(c[2]), int(m.sum()), np.round(c, 3).tolist()))
