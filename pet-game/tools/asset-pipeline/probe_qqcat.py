"""QQ 宠物风格新猫（qqcat-prod.glb）几何量测 + 预览。
为绑骨提供实测：身体各层剖面、尾巴路径、眼区估计。
运行： blender --background --python probe_qqcat.py -- <in.glb> <outdir>
"""
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
GLB, OUT = argv[0], argv[1]
os.makedirs(OUT, exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB)
meshes = [o for o in bpy.context.scene.objects if o.type == 'MESH']
print('[qq] 网格对象:', [(o.name, len(o.data.vertices)) for o in meshes])
mesh = meshes[0]
bpy.context.view_layer.objects.active = mesh
mesh.select_set(True)
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

co = np.array([v.co[:] for v in mesh.data.vertices])
print('[qq] 顶点 %d  dims=%s' % (len(co), [round(v, 4) for v in mesh.dimensions]))
print('[qq] bbox x %.3f..%.3f  y %.3f..%.3f  z %.3f..%.3f'
      % (co[:, 0].min(), co[:, 0].max(), co[:, 1].min(), co[:, 1].max(),
         co[:, 2].min(), co[:, 2].max()))

# 各层剖面（z 从低到高）：x/y 范围 → 判断身体、头、尾巴的分层
for z in (0.02, 0.06, 0.10, 0.14, 0.18, 0.22, 0.26, 0.30, 0.36, 0.42, 0.50, 0.60):
    band = co[(co[:, 2] > z - 0.02) & (co[:, 2] < z + 0.02)]
    if len(band) < 8:
        continue
    print('[qq] z=%.2f n=%4d  x %+.3f..%+.3f  y %+.3f..%+.3f'
          % (z, len(band), band[:, 0].min(), band[:, 0].max(),
             band[:, 1].min(), band[:, 1].max()))

# 尾巴：预览图显示尾巴在身侧（+X 侧后方）卷起 —— 取 +X 且偏后的孤立簇
tail = co[(co[:, 0] > 0.18) & (co[:, 1] > 0.10)]
if len(tail) > 20:
    print('[qq] 尾部候选簇 %d 顶点  x %.3f..%.3f  y %.3f..%.3f  z %.3f..%.3f'
          % (len(tail), tail[:, 0].min(), tail[:, 0].max(),
             tail[:, 1].min(), tail[:, 1].max(), tail[:, 2].min(), tail[:, 2].max()))

# 连通分量：尾巴是否独立岛
parent = list(range(len(co)))

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
for i in range(len(co)):
    buckets.setdefault(find(i), []).append(i)
comps = sorted(buckets.values(), key=len, reverse=True)
print('[qq] 连通分量 %d 个，前 5 尺寸 %s' % (len(comps), [len(c) for c in comps[:5]]))
for ci, comp in enumerate(comps[:3]):
    p = co[comp]
    print('[qq] 岛%d  bbox x %.3f..%.3f y %.3f..%.3f z %.3f..%.3f'
          % (ci, p[:, 0].min(), p[:, 0].max(), p[:, 1].min(), p[:, 1].max(),
             p[:, 2].min(), p[:, 2].max()))

# 渲染：front / side / back（浅灰底）
scene = bpy.context.scene
scene.render.engine = 'BLENDER_WORKBENCH'
scene.display.shading.light = 'STUDIO'
scene.display.shading.color_type = 'TEXTURE'
scene.display.shading.show_shadows = True
scene.render.resolution_x = 900
scene.render.resolution_y = 900
world = bpy.data.worlds.get('World') or bpy.data.worlds.new('World')
scene.world = world
world.color = (0.92, 0.92, 0.92)
cam_data = bpy.data.cameras.new('Cam')
cam_data.lens = 60
cam = bpy.data.objects.new('Cam', cam_data)
bpy.context.collection.objects.link(cam)
scene.camera = cam
dims = mesh.dimensions
mid = Vector((0, 0, dims.z * 0.45))
for tag, loc in (('front', Vector((0.3, -3.0, dims.z * 0.55))),
                 ('side', Vector((3.0, 0.2, dims.z * 0.55))),
                 ('back', Vector((0.2, 3.0, dims.z * 0.55)))):
    cam.location = loc
    d = mid - loc
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    scene.render.filepath = os.path.join(OUT, 'qq_%s.png' % tag)
    bpy.ops.render.render(write_still=True)
print('[qq] 渲染完成 -> %s' % OUT)
