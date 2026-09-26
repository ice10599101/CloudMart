"""新猫 v2：虹膜 UV 定位 + 耳尖定位。

为什么改用"先定位贴图上的虹膜 UV、再反查顶点"：
  v1 用"顶点落在虹膜像素上"来聚类，结果那 281 个顶点的包围盒是 0.36×0.72×0.21 ——
  一片弥散区域，说明暖米色毛发与金色铃铛都被算进"琥珀色"了，聚类质心根本不是眼球。
  正确做法：先在**贴图空间**把两个虹膜找出来（收紧阈值 + 按 x 分成左右两簇取质心），
  再把 uv 落在该质心附近的顶点收出来 —— 这样得到的是真正的眼球表面顶点。

另：耳朵改为取**最顶端突出部**（z > 0.78），而不是 z>0.64 的整个头顶（那会把头顶一起算进去）。

用法：
    blender --background --python measure_v2c.py -- <model.glb>
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


uv = per_vertex_uv()
img = None
if mesh.data.materials and mesh.data.materials[0].use_nodes:
    for n in mesh.data.materials[0].node_tree.nodes:
        if n.type == 'TEX_IMAGE' and n.image:
            img = n.image
            break

if img is None:
    print('找不到反照率贴图')
else:
    W, Hi = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(Hi, W, 4)
    r, g, b = px[:, :, 0], px[:, :, 1], px[:, :, 2]
    mx = np.maximum(np.maximum(r, g), b)
    mn = np.minimum(np.minimum(r, g), b)
    # 收紧：#C98A3E -> r≈0.79 g≈0.54 b≈0.24
    mask = (r > 0.62) & (r - b > 0.42) & (r - g > 0.18) & ((mx - mn) > 0.36)
    print('== 虹膜（收紧阈值）像素数 %d / %d  %.3f%%' % (int(mask.sum()), W * Hi, mask.sum() / (W * Hi) * 100))
    ys, xs = np.nonzero(mask)
    if len(xs) > 10:
        # 按 x 围绕整体均值分左右两簇 -> 两个虹膜中心（贴图坐标，行序自下而上）
        mid = float(xs.mean())
        left = xs < mid
        right = xs >= mid
        for tag, sel in (('左簇', left), ('右簇', right)):
            if sel.sum() > 5:
                print('   %s n=%d  uv像素中心=(%.1f, %.1f)  uv=(%.4f, %.4f)' % (
                    tag, int(sel.sum()), xs[sel].mean(), ys[sel].mean(), xs[sel].mean() / W,
                    ys[sel].mean() / Hi))
        for tag, sel in (('左簇', left), ('右簇', right)):
            if sel.sum() <= 5:
                continue
            cu, cv = xs[sel].mean() / W, ys[sel].mean() / Hi
            duv = np.sqrt((uv[:, 0] - cu) ** 2 + (uv[:, 1] - cv) ** 2)
            near = co[duv < 0.020]
            print('   -> %s 对应顶点 n=%d  centroid=%s' % (tag, len(near), np.round(near.mean(axis=0), 4).tolist() if len(near) else '-'))
            if len(near) >= 20:
                print('      bbox %s' % (np.round(near.max(axis=0) - near.min(axis=0), 3).tolist()))

print('\n== 耳尖（z > 0.78 的顶端突出部）==')
top = co[co[:, 2] > 0.78]
print('   n=%d  x=%.3f..%.3f  y=%.3f..%.3f'
      % (len(top), float(top[:, 0].min()), float(top[:, 0].max()),
         float(top[:, 1].min()), float(top[:, 1].max()))
      if len(top) else '   n=0')
if len(top) > 30:
    xs = top[:, 0]
    mid = float(xs.mean())
    for tag, sel in (('+X 耳', xs >= mid), ('-X 耳', xs < mid)):
        g2 = top[sel]
        if len(g2) > 8:
            print('   %s n=%d centroid=%s bbox=%s' % (tag, len(g2), np.round(g2.mean(axis=0), 3).tolist(),
                                                      np.round(g2.max(axis=0) - g2.min(axis=0), 3).tolist()))
