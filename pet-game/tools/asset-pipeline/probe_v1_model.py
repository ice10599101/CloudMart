"""原始猫（奶盖 pet-cat-runtime.glb）几何量测 + 预览渲染。
目的：为"只改尾巴"提供实测坐标 —— 旧绕身尾的范围、屁股后表面、身体轴。
运行： blender --background --python probe_v1_model.py -- <in.glb> <outdir>
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
mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

co = np.array([v.co[:] for v in mesh.data.vertices])
print('[v1] 顶点 %d  dims=%s' % (len(co), [round(v, 4) for v in mesh.dimensions]))
print('[v1] bbox x %.3f..%.3f  y %.3f..%.3f  z %.3f..%.3f'
      % (co[:, 0].min(), co[:, 0].max(), co[:, 1].min(), co[:, 1].max(),
         co[:, 2].min(), co[:, 2].max()))

# 逐层剖面：躯干（|x|<0.15）的后缘 y，以及整体 x 范围 —— 找屁股与旧尾
for z in (0.05, 0.10, 0.15, 0.20, 0.25, 0.30, 0.40, 0.50):
    band = co[(co[:, 2] > z - 0.025) & (co[:, 2] < z + 0.025)]
    if len(band) < 10:
        continue
    torso = band[np.abs(band[:, 0]) < 0.15]
    print('[v1] z=%.2f  n=%4d  y 后缘=%+.3f  躯干后缘=%s  x 范围 %+.3f..%+.3f'
          % (z, len(band), band[:, 1].max(),
             ('%+.3f' % torso[:, 1].max()) if len(torso) else '   n/a',
             band[:, 0].min(), band[:, 0].max()))

# 旧绕身尾：低处 + 大 |x|
for tag, m in (('-X 侧', (co[:, 0] < -0.22) & (co[:, 2] < 0.30)),
               ('+X 侧', (co[:, 0] > 0.22) & (co[:, 2] < 0.30))):
    p = co[m]
    print('[v1] %s 低处顶点 %d  包围盒 x %.3f..%.3f y %.3f..%.3f z %.3f..%.3f'
          % (tag, len(p), p[:, 0].min(), p[:, 0].max(),
             p[:, 1].min() if len(p) else 0, p[:, 1].max() if len(p) else 0,
             p[:, 2].min() if len(p) else 0, p[:, 2].max() if len(p) else 0))

# 屁股后表面锚点候选（躯干低处后缘）
low = co[(co[:, 2] > 0.05) & (co[:, 2] < 0.20) & (np.abs(co[:, 0]) < 0.15)]
if len(low):
    k = int(np.argmax(low[:, 1]))
    print('[v1] 屁股后表面锚点（躯干低处最后顶点）: %s' % np.round(low[k], 4).tolist())

# 前爪区域分析：z<0.18、y<-0.10 的顶点，按 z 层打印 y 范围 —— 区分"爪"与"绕到前面的尾环"
print('[v1] ---- 前区剖面（z<0.18, y<-0.10）----')
front = co[(co[:, 2] < 0.18) & (co[:, 1] < -0.10)]
for z in (0.02, 0.05, 0.08, 0.11, 0.14):
    band = front[(front[:, 2] > z - 0.015) & (front[:, 2] < z + 0.015)]
    if len(band) < 5:
        continue
    # 按 y 分两簇：y > -0.26 大概率是爪，y < -0.30 大概率是尾环
    paw = band[band[:, 1] > -0.27]
    ring = band[band[:, 1] <= -0.27]
    print('[v1] z=%.2f n=%3d | 爪簇 n=%3d y %.3f..%.3f x %+.3f..%+.3f | 尾环簇 n=%3d y %.3f..%.3f x %+.3f..%+.3f'
          % (z, len(band),
             len(paw), paw[:, 1].min() if len(paw) else 0, paw[:, 1].max() if len(paw) else 0,
             paw[:, 0].min() if len(paw) else 0, paw[:, 0].max() if len(paw) else 0,
             len(ring), ring[:, 1].min() if len(ring) else 0, ring[:, 1].max() if len(ring) else 0,
             ring[:, 0].min() if len(ring) else 0, ring[:, 0].max() if len(ring) else 0))

# 预览渲染：正面 + 侧面
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
for tag, loc in (('front', Vector((0.35, -3.0, dims.z * 0.55))),
                 ('side', Vector((3.0, 0.2, dims.z * 0.55))),
                 ('back', Vector((0.2, 3.0, dims.z * 0.55)))):
    cam.location = loc
    d = mid - loc
    cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()
    scene.render.filepath = os.path.join(OUT, 'v1_%s.png' % tag)
    bpy.ops.render.render(write_still=True)
    print('[v1] 渲染 %s -> %s' % (tag, scene.render.filepath))
print('[v1] done')
