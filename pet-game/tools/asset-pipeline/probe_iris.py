"""虹膜颜色探针：打印脸部顶点的 g/r 直方图与代表性 RGB，用来定虹膜阈值。
运行： blender --background --python probe_iris.py -- <in.glb>
"""
import sys

import bpy
import numpy as np

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
GLB = argv[0]

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB)
mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

mat = mesh.data.materials[0]
img = None
for n in mat.node_tree.nodes:
    if n.type == 'TEX_IMAGE' and n.image:
        img = n.image
        break
print('[probe] image:', img.name if img else None, img.size[:] if img else None,
      img.colorspace_settings.name if img else None)
W, H = img.size
px = np.empty(W * H * 4, dtype=np.float32)
img.pixels.foreach_get(px)
px = px.reshape(-1, 4)[:, :3]

co = np.array([v.co[:] for v in mesh.data.vertices])
uv = np.zeros((len(co), 2))
layer = mesh.data.uv_layers.active.data
for poly in mesh.data.polygons:
    for li in poly.loop_indices:
        vi = mesh.data.loops[li].vertex_index
        uv[vi] = layer[li].uv[:]
xi = np.clip((uv[:, 0] * W).astype(int), 0, W - 1)
yi = np.clip((uv[:, 1] * H).astype(int), 0, H - 1)
rgb = px[yi * W + xi]
r, g, b = rgb[:, 0], rgb[:, 1], rgb[:, 2]
ratio = g / np.maximum(r, 1e-6)

# 脸前区（z>0.40，y<-0.02）的 g/r 直方图
face = (co[:, 2] > 0.40) & (co[:, 1] < -0.02) & (r > b)
print('[probe] 脸前区顶点 %d' % int(face.sum()))
bins = np.arange(0.20, 0.95, 0.05)
hist, edges = np.histogram(ratio[face], bins=bins)
for k in range(len(hist)):
    if hist[k] > 0:
        sel = face & (ratio >= edges[k]) & (ratio < edges[k + 1])
        mean = rgb[sel].mean(axis=0)
        print('[probe] g/r %.2f-%.2f: %4d  meanRGB(%.3f,%.3f,%.3f)  meanXYZ(%+.3f,%+.3f,%.3f)'
              % (edges[k], edges[k + 1], hist[k], mean[0], mean[1], mean[2],
                 co[sel][:, 0].mean(), co[sel][:, 1].mean(), co[sel][:, 2].mean()))

# 最橙的一批顶点落在哪
hot = face & (ratio < 0.55) & ((r - b) > 0.15)
print('[probe] r-b>0.15 且 g/r<0.55 的顶点 %d' % int(hot.sum()))
if hot.sum() > 5:
    pts = co[hot]
    print('[probe] 这些顶点的包围盒 x %+.3f..%+.3f  y %+.3f..%+.3f  z %.3f..%.3f'
          % (pts[:, 0].min(), pts[:, 0].max(), pts[:, 1].min(), pts[:, 1].max(),
             pts[:, 2].min(), pts[:, 2].max()))
    # 按 x 正负分两组看中心
    for tag, m in (('+X', pts[:, 0] > 0), ('-X', pts[:, 0] <= 0)):
        if m.sum() > 3:
            c = pts[m].mean(axis=0)
            print('[probe]   %s 组 %d 点 质心(%+.4f,%+.4f,%.4f)'
                  % (tag, int(m.sum()), c[0], c[1], c[2]))
