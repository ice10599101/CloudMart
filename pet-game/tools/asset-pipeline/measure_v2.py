"""测量新猫（v2）的解剖结构，为骨骼落位提供依据。

旧猫的量法不能沿用：这是一次**重新生成**的几何，拓扑与尺寸都不同。

输出：
  - 包围盒 / 竖直剖面（找颈线、耳根）
  - 双眼中心（用反照率贴图里的琥珀金虹膜反查顶点）
  - 尾巴：分别看 +X / -X 两侧"伸出最远"的顶点，判断尾巴长在哪边、路径如何

用法：
    blender --background --python measure_v2.py -- <model.glb>
"""
import json
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
print('verts=%d  bbox_min=%s  bbox_max=%s  size=%s'
      % (len(co), np.round(co.min(axis=0), 3).tolist(),
         np.round(co.max(axis=0), 3).tolist(),
         np.round(co.max(axis=0) - co.min(axis=0), 3).tolist()))

# ---------- 竖直剖面（沿 Z，向上）----------
z0, z1 = float(co[:, 2].min()), float(co[:, 2].max())
H = z1 - z0
print('\n== 竖直剖面（frac, z, 宽度x, 厚度y, 顶点数）==')
prof = []
N = 40
for i in range(N):
    lo, hi = z0 + H * i / N, z0 + H * (i + 1) / N
    m = (co[:, 2] >= lo) & (co[:, 2] < hi)
    if m.sum() == 0:
        prof.append((round((i + 0.5) / N, 3), round((lo + hi) / 2, 3), 0.0, 0.0, 0))
        continue
    p = co[m]
    prof.append((round((i + 0.5) / N, 3), round((lo + hi) / 2, 3),
                 round(float(p[:, 0].max() - p[:, 0].min()), 3),
                 round(float(p[:, 1].max() - p[:, 1].min()), 3), int(m.sum())))
for row in prof:
    print('   %.3f  z=%.3f  xw=%.3f  yw=%.3f  n=%d %s' % (row[0], row[1], row[2], row[3], row[4],
                                                           '#' * int(row[2] * 22)))

# ---------- 双眼：反照率贴图里的琥珀金虹膜反查 ----------
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
if img is not None:
    W, Hi = img.size
    px = np.array(img.pixels[:], dtype=np.float32).reshape(Hi, W, 4)

    def amber(buf):
        r, g, b = buf[:, :, 0], buf[:, :, 1], buf[:, :, 2]
        mx = np.maximum(np.maximum(r, g), b)
        mn = np.minimum(np.minimum(r, g), b)
        return (r > 0.55) & (r - b > 0.28) & (r - g > 0.12) & ((mx - mn) > 0.22)

    mask = amber(px)
    print('\n== 眼睛 == 虹膜像素数 %d' % int(mask.sum()))
    u = np.clip((uv[:, 0] % 1.0) * (W - 1), 0, W - 1).astype(int)
    v = np.clip((uv[:, 1] % 1.0) * (Hi - 1), 0, Hi - 1).astype(int)
    on = mask[v, u]
    pts = co[on]
    print('   命中顶点数 %d' % len(pts))
    if len(pts) >= 4:
        xs = pts[:, 0]
        cxp = pts[xs >= xs.mean()]
        cxn = pts[xs < xs.mean()]
        print('   右簇(+X) 中心 %s  n=%d' % (np.round(cxp.mean(axis=0), 4).tolist(), len(cxp)))
        print('   左簇(-X) 中心 %s  n=%d' % (np.round(cxn.mean(axis=0), 4).tolist(), len(cxn)))

# ---------- 尾巴：看两侧伸得最远的部分 ----------
print('\n== 尾巴探测 ==')
for sign, name in ((1, '+X'), (-1, '-X')):
    sel = co[sign * co[:, 0] > 0]
    if len(sel) < 30:
        print('   %s: 顶点太少(%d)' % (name, len(sel)))
        continue
    thr = float(np.percentile(sign * sel[:, 0], 97))
    tip = sel[sign * sel[:, 0] >= thr]
    print('   %s: 最远 3%% 顶点 n=%d  x=%.3f..%.3f  y=%.3f..%.3f  z=%.3f..%.3f  质心 %s'
          % (name, len(tip),
             float(tip[:, 0].min()), float(tip[:, 0].max()),
             float(tip[:, 1].min()), float(tip[:, 1].max()),
             float(tip[:, 2].min()), float(tip[:, 2].max()),
             np.round(tip.mean(axis=0), 3).tolist()))

# 尾巴路径：把"明显偏外侧"的顶点按 z 分成若干层取质心
for sign, name in ((1, '+X'), (-1, '-X')):
    outer = co[sign * co[:, 0] > 0.22]
    if len(outer) < 60:
        continue
    print('\n   -- %s 外侧顶点按高度分层的质心（尾巴路径）--' % name)
    zs = outer[:, 2]
    edges = np.linspace(float(zs.min()), float(zs.max()), 11)
    for k in range(10):
        m = (zs >= edges[k]) & (zs <= edges[k + 1])
        if m.sum() >= 8:
            c = outer[m].mean(axis=0)
            print('      z=%.3f  n=%4d  centroid=%s' % (float(c[2]), int(m.sum()), np.round(c, 3).tolist()))
