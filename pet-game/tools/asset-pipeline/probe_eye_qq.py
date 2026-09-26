"""QQ 猫眼球拾取（视觉射线法，与 probe_eye_pick.py 同一套路，参数为 qqcat 定制）。
两步用法：
  1. SHOW_MARKERS=0 → eye_grid.png（40px 网格），目视读两只眼瞳孔中心像素
  2. 把读数填进 PIXELS，SHOW_MARKERS=1 → ray_cast 边界拟合 + 标记验证图 + eye_pick.json
运行： blender --background --python probe_eye_qq.py -- <in.glb> <check_dir>
"""
import json
import math
import os
import sys

import bpy
import numpy as np
from mathutils import Vector

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else []
GLB, CHECK = argv[0], argv[1]
os.makedirs(CHECK, exist_ok=True)
SHOW_MARKERS = os.environ.get('SHOW_MARKERS', '0') == '1'

# 目视读数（像素）—— 从 eye_grid.png 读（标记验证图修正过一轮的话以验证图为准）
PIXELS = [('+X', 408, 383), ('-X', 318, 385)]

# 正面特写相机（覆盖双眼）
CAM_LOC = Vector((0.10, -1.90, 0.60))
CAM_LOOK = Vector((0.00, -0.20, 0.52))
LENS = 50.0
RES = 760

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB)
mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
bpy.context.view_layer.objects.active = mesh
mesh.select_set(True)
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

fwd = (CAM_LOOK - CAM_LOC)
fwd.normalize()
right = fwd.cross(Vector((0.0, 0.0, 1.0))).normalized()
up = right.cross(fwd).normalized()
tan_half = 18.0 / LENS


def ray_dir(px, py):
    ndc_x = (px - RES / 2.0) / (RES / 2.0)
    ndc_y = (RES / 2.0 - py) / (RES / 2.0)
    d = fwd + right * (ndc_x * tan_half) + up * (ndc_y * tan_half)
    d.normalize()
    return d


def ray_cast(px, py):
    dg = bpy.context.evaluated_depsgraph_get()
    ok, loc, _n, idx, ob, _mw = bpy.context.scene.ray_cast(dg, CAM_LOC, ray_dir(px, py))
    if not ok:
        raise RuntimeError('ray_cast 未命中（像素 %d,%d）' % (px, py))
    return np.array(loc), idx, ob


def boundary_fit(px, py):
    """瞳孔种子向 24 方向步进，贴图颜色分类眼/毛，边界环拟合眼球。"""
    dg = bpy.context.evaluated_depsgraph_get()
    hit0, idx0, ob0 = ray_cast(px, py)
    img = None
    for m in ob0.data.materials:
        if m and m.use_nodes:
            for node in m.node_tree.nodes:
                if node.type == 'TEX_IMAGE' and node.image:
                    img = node.image
                    break
    if img is None:
        raise RuntimeError('命中网格没有贴图')
    W, H = img.size
    px_buf = np.empty(W * H * 4, dtype=np.float32)
    img.pixels.foreach_get(px_buf)
    px_buf = px_buf.reshape(-1, 4)[:, :3]
    uvlay = ob0.data.uv_layers.active.data

    def hit_rgb(loc, poly_i):
        poly = ob0.data.polygons[poly_i]
        us = [uvlay[li].uv for li in poly.loop_indices]
        u = sum(v[0] for v in us) / len(us)
        v = sum(v[1] for v in us) / len(us)
        return px_buf[min(int(v * H), H - 1) * W + min(int(u * W), W - 1)]

    def is_fur(rgb):
        r, g, b = float(rgb[0]), float(rgb[1]), float(rgb[2])
        if r < 0.30 and g < 0.30:
            return False                       # 深色瞳孔/眼缘
        if r > 0.55 and 0.35 < g < r and b < 0.35:
            return False                       # 琥珀虹膜
        if r > 0.75 and g > 0.75:
            return False                       # 大高光
        return True                            # 其余 = 奶油毛

    rim = [hit0]
    for k in range(24):
        a = 2.0 * math.pi * k / 24.0
        for step in range(4, 44, 2):
            ox = int(round(px + step * math.cos(a)))
            oy = int(round(py + step * math.sin(a)))
            try:
                loc, idx, ob = ray_cast(ox, oy)
            except RuntimeError:
                break
            if is_fur(hit_rgb(loc, idx)):
                rim.append(np.array(loc))
                break
    pts = np.array(rim)
    A = np.hstack([2.0 * pts, np.ones((len(pts), 1))])
    bb = (pts ** 2).sum(axis=1)
    sol, *_ = np.linalg.lstsq(A, bb, rcond=None)
    c = sol[:3]
    r = float(np.sqrt(max(sol[3] + float(c @ c), 1e-9)))
    return hit0, c, r, len(pts)


scene = bpy.context.scene
scene.render.engine = 'BLENDER_WORKBENCH'
scene.display.shading.light = 'STUDIO'
scene.display.shading.color_type = 'TEXTURE'
scene.display.shading.show_shadows = True
scene.render.resolution_x = RES
scene.render.resolution_y = RES
world = bpy.data.worlds.get('World') or bpy.data.worlds.new('World')
scene.world = world
world.color = (0.92, 0.92, 0.92)
cam_data = bpy.data.cameras.new('Cam')
cam_data.lens = LENS
cam = bpy.data.objects.new('Cam', cam_data)
bpy.context.collection.objects.link(cam)
scene.camera = cam
cam.location = CAM_LOC
d = CAM_LOOK - CAM_LOC
cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()

if not SHOW_MARKERS:
    raw = os.path.join(CHECK, 'eye_grid_raw.png')
    scene.render.filepath = raw
    bpy.ops.render.render(write_still=True)
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from png_prep import read_png, write_png
    w, h, nch, px = read_png(raw)
    px = bytearray(px)
    for y in range(h):
        for x in range(w):
            if x % 40 == 0 or y % 40 == 0:
                i = (y * w + x) * nch
                px[i] = int(px[i] * 0.25)
                if nch > 1:
                    px[i + 1] = int(px[i + 1] * 0.25)
                if nch > 2:
                    px[i + 2] = int(px[i + 2] * 0.25)
    grid = os.path.join(CHECK, 'eye_grid.png')
    write_png(grid, w, h, nch, px)
    print('[pick] 网格标尺图 ->', grid)
else:
    eyes = []
    for tag, px_, py_ in PIXELS:
        hit, center, radius, n = boundary_fit(px_, py_)
        print('[pick] %s 眼 像素(%d,%d) -> 表面点 %s -> 球心 %s r=%.4f（%d 点）'
              % (tag, px_, py_, np.round(hit, 4).tolist(), np.round(center, 4).tolist(), radius, n))
        eyes.append({'tag': tag, 'pixel': [px_, py_],
                     'hit': [round(float(x), 4) for x in hit],
                     'center': [round(float(x), 4) for x in center],
                     'radius': round(radius, 4), 'n': n})
    good = [e for e in eyes if 0.035 <= e['radius'] <= 0.09]
    if good:
        r_ref = good[0]['radius']
    else:
        r_ref = 0.055
    fixed = []
    for e in eyes:
        if not 0.035 <= e['radius'] <= 0.09:
            print('[pick] %s 眼边界拟合失稳（r=%.3f），改用 %.3f + 单射线定心' % (e['tag'], e['radius'], r_ref))
            dd = ray_dir(*e['pixel'])
            center = e['hit'] + np.array(dd) * r_ref
            e = dict(e, center=[round(float(x), 4) for x in center], radius=round(r_ref, 4))
        fixed.append(e)
    with open(os.path.join(CHECK, 'eye_pick.json'), 'w', encoding='utf-8') as fh:
        json.dump({'eyes': fixed}, fh, ensure_ascii=False, indent=2)

    def marker(name, at, color):
        me = bpy.data.meshes.new(name)
        r0 = 0.012
        rings, segs = 6, 12
        verts, faces = [], []
        c = Vector(at)
        verts.append(tuple(c + Vector((0, 0, r0))))
        for ri in range(1, rings + 1):
            th = math.pi * ri / rings
            for si in range(segs):
                ph = 2 * math.pi * si / segs
                verts.append(tuple(c + r0 * Vector((math.sin(th) * math.cos(ph),
                                                    math.sin(th) * math.sin(ph),
                                                    math.cos(th)))))
        for si in range(segs):
            faces.append((0, 1 + si, 1 + (si + 1) % segs))
        for ri in range(rings - 1):
            a0, b0 = 1 + ri * segs, 1 + (ri + 1) * segs
            for si in range(segs):
                s2 = (si + 1) % segs
                faces.append((a0 + si, b0 + si, b0 + s2, a0 + s2))
        me.from_pydata(verts, [], faces)
        me.update()
        ob = bpy.data.objects.new(name, me)
        bpy.context.collection.objects.link(ob)
        m = bpy.data.materials.new(name + 'Mat')
        m.diffuse_color = color
        ob.data.materials.append(m)

    for k, e in enumerate(fixed):
        marker('Hit%d' % k, e['hit'], (0.2, 1.0, 0.3, 1.0))
        marker('Fit%d' % k, e['center'], (1.0, 0.3, 0.1, 1.0))
    scene.render.filepath = os.path.join(CHECK, 'eye_pick_verify.png')
    bpy.ops.render.render(write_still=True)
    print('[pick] 验证图 ->', scene.render.filepath)
