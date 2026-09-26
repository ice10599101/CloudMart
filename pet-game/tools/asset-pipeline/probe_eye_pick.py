"""眼球视觉拾取：从头部特写相机对两只眼的虹膜中心像素打射线，
吸附网格最近顶点后局部球面拟合出眼球中心，写 eye_pick.json 供 rig 使用，
并渲染标记球验证图（人眼核对标记是否落在虹膜中心）。

两步用法（同一相机参数）：
  1. SHOW_MARKERS=0 跑一次 -> eye_pick_grid.png（40px 网格标尺），目视读两只眼虹膜中心像素
  2. 把读数填进 PIXELS，SHOW_MARKERS=1 再跑 -> eye_pick_verify.png 核对标记位置
运行： blender --background --python probe_eye_pick.py -- <in.glb> <check_dir>
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
EYE_R = 0.055   # 与 rig_cat_v2.py 的 EYE_RADIUS 一致

# 目视读数（像素）—— 从 eye_pick_grid.png 的网格上读（标记验证图修正过两轮）
PIXELS = [('+X', 300, 262), ('-X', 140, 265)]

# 宽视野相机：两只眼都要在画面里
CAM_LOC = Vector((0.42, -1.45, 0.62))
CAM_LOOK = Vector((0.0, -0.10, 0.46))
LENS = 60.0
RES = 760

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=GLB)
mesh = [o for o in bpy.context.scene.objects if o.type == 'MESH'][0]
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

# 相机坐标系
fwd = (CAM_LOOK - CAM_LOC)
fwd.normalize()
right = fwd.cross(Vector((0.0, 0.0, 1.0))).normalized()
up = right.cross(fwd).normalized()
tan_half = 18.0 / LENS          # 默认 sensor_width=36mm


def ray_dir(px, py):
    ndc_x = (px - RES / 2.0) / (RES / 2.0)
    ndc_y = (RES / 2.0 - py) / (RES / 2.0)
    d = fwd + right * (ndc_x * tan_half) + up * (ndc_y * tan_half)
    d.normalize()
    return d


def ray_pick(px, py):
    """多射线边界拟合：以瞳孔像素为种子，向 24 个方向步进出射射线，
    采样命中面片的贴图颜色，找到"眼/毛"边界环，用边界点+中心点拟合眼球。
    单射线+固定半径的旧方案对两只眼大小不一的模型必然一只盖不住、一只凸成球
    （已实测踩到：归一半径 0.055，真实两眼 0.040/0.061 量级）。"""
    dg = bpy.context.evaluated_depsgraph_get()
    ok, hit_v, _n, idx, ob, _mw = bpy.context.scene.ray_cast(dg, CAM_LOC, ray_dir(px, py))
    if not ok:
        raise RuntimeError('ray_cast 未命中（像素 %d,%d）' % (px, py))
    center = np.array(hit_v)

    # 命中面片的贴图颜色（取面 loop uv 均值采样）
    img = None
    for n_ in ob.data.materials:
        if n_ and n_.use_nodes:
            for node in n_.node_tree.nodes:
                if node.type == 'TEX_IMAGE' and node.image:
                    img = node.image
                    break
    if img is None:
        raise RuntimeError('命中网格没有贴图，无法分类眼/毛')
    W, H = img.size
    px_buf = np.empty(W * H * 4, dtype=np.float32)
    img.pixels.foreach_get(px_buf)
    px_buf = px_buf.reshape(-1, 4)[:, :3]
    uvlay = ob.data.uv_layers.active.data

    def hit_rgb(loc, poly_i):
        poly = ob.data.polygons[poly_i]
        us = [uvlay[li].uv for li in poly.loop_indices]
        u = sum(v[0] for v in us) / len(us)
        v = sum(v[1] for v in us) / len(us)
        return px_buf[min(int(v * H), H - 1) * W + min(int(u * W), W - 1)]

    def is_fur(rgb):
        r, g, b = rgb
        if r >= g >= b and (r - b) > 0.12 and 0.5 < g / max(r, 1e-6) < 0.72:
            return False                      # 虹膜（琥珀）
        if r < 0.12 and g < 0.12 and b < 0.12:
            return False                      # 瞳孔（深色）
        if r > 0.75 and g > 0.75 and b > 0.75:
            return False                      # 高光（白）
        return True                           # 其余 = 毛

    rim = [center]
    for k in range(24):
        a = 2.0 * math.pi * k / 24.0
        for step in range(4, 40, 2):
            ox = int(round(px + step * math.cos(a)))
            oy = int(round(py + step * math.sin(a)))
            d = ray_dir(ox, oy)
            ok, loc, _n2, idx2, ob2, _mw2 = bpy.context.scene.ray_cast(dg, CAM_LOC, d)
            if not ok:
                break
            if is_fur(hit_rgb(loc, idx2)):
                rim.append(np.array(loc))     # 该方向上第一个"毛"命中 = 眼眶缘
                break
    pts = np.array(rim)
    A = np.hstack([2.0 * pts, np.ones((len(pts), 1))])
    bb = (pts ** 2).sum(axis=1)
    sol, *_ = np.linalg.lstsq(A, bb, rcond=None)
    c = sol[:3]
    r = float(np.sqrt(max(sol[3] + float(c @ c), 1e-9)))
    n = len(pts)
    return center, c, r, n


scene = bpy.context.scene
scene.render.engine = 'BLENDER_WORKBENCH'
scene.display.shading.light = 'STUDIO'
scene.display.shading.color_type = 'TEXTURE'
scene.display.shading.show_shadows = True
scene.display.shading.show_cavity = True
scene.render.resolution_x = RES
scene.render.resolution_y = RES
cam_data = bpy.data.cameras.new('Cam')
cam_data.lens = LENS
cam = bpy.data.objects.new('Cam', cam_data)
bpy.context.collection.objects.link(cam)
scene.camera = cam
cam.location = CAM_LOC
d = CAM_LOOK - CAM_LOC
cam.rotation_euler = d.to_track_quat('-Z', 'Y').to_euler()

if not SHOW_MARKERS:
    # 第 1 步：渲染 + 40px 网格标尺，供目视读像素
    raw = os.path.join(CHECK, 'eye_pick_grid_raw.png')
    scene.render.filepath = raw
    bpy.ops.render.render(write_still=True)
    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    from png_prep import read_png, write_png
    w, h, nch, px = read_png(raw)
    px = bytearray(px)
    step = 40
    for y in range(h):
        for x in range(w):
            if x % step == 0 or y % step == 0:
                i = (y * w + x) * nch
                px[i] = int(px[i] * 0.25)
                if nch > 1:
                    px[i + 1] = int(px[i + 1] * 0.25)
                if nch > 2:
                    px[i + 2] = int(px[i + 2] * 0.25)
    grid = os.path.join(CHECK, 'eye_pick_grid.png')
    write_png(grid, w, h, nch, px)
    print('[pick] 网格标尺图 ->', grid, '（每格 40px，原点左上）')
else:
    # 第 2 步：按 PIXELS 拾取 + 标记验证
    # 边界拟合对画面边缘的小眼可能失稳（射线走偏收集到脸颊边界点，r 会爆到 0.09+）。
    # 两只眼球生理上等大：拟合半径落在合理域 [0.035,0.075] 的才采信；
    # 失败的那只沿用成功眼的半径 + 单射线定心（已实测踩到小眼 r 拟到 0.096）。
    raw = []
    for tag, px_, py_ in PIXELS:
        hit, center, radius, n = ray_pick(px_, py_)
        print('[pick] %s 眼 像素(%d,%d) -> 表面点 %s -> 球心 %s r=%.4f（%d 点）'
              % (tag, px_, py_, np.round(hit, 4).tolist(), np.round(center, 4).tolist(), radius, n))
        raw.append({'tag': tag, 'pixel': [px_, py_], 'hit': hit,
                    'center': center, 'radius': radius, 'n': n})
    good = [e for e in raw if 0.035 <= e['radius'] <= 0.075]
    if good:
        r_ref = good[0]['radius']
    else:
        r_ref = 0.05
    eyes = []
    for e in raw:
        if 0.035 <= e['radius'] <= 0.075:
            eyes.append({'tag': e['tag'], 'pixel': e['pixel'],
                         'hit': [round(float(x), 4) for x in e['hit']],
                         'center': [round(float(x), 4) for x in e['center']],
                         'radius': round(e['radius'], 4), 'n': e['n']})
        else:
            print('[pick] %s 眼边界拟合失稳（r=%.3f），改用 %.3f + 单射线定心'
                  % (e['tag'], e['radius'], r_ref))
            d = ray_dir(*e['pixel'])
            center = e['hit'] + np.array(d) * r_ref
            eyes.append({'tag': e['tag'], 'pixel': e['pixel'],
                         'hit': [round(float(x), 4) for x in e['hit']],
                         'center': [round(float(x), 4) for x in center],
                         'radius': round(r_ref, 4), 'n': e['n']})
    with open(os.path.join(CHECK, 'eye_pick.json'), 'w', encoding='utf-8') as fh:
        json.dump({'eyes': eyes}, fh, ensure_ascii=False, indent=2)

    def marker(name, at, color):
        me = bpy.data.meshes.new(name)
        r0 = 0.014
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

    # 绿球 = 射线原始落点（诊断），橙球 = 拟合球心（rig 实际使用的值）
    for k, e in enumerate(eyes):
        marker('Hit%d' % k, e['hit'], (0.2, 1.0, 0.3, 1.0))
        marker('Fit%d' % k, e['center'], (1.0, 0.3, 0.1, 1.0))
    scene.render.filepath = os.path.join(CHECK, 'eye_pick_verify.png')
    bpy.ops.render.render(write_still=True)
    print('[pick] 验证图 ->', scene.render.filepath)
