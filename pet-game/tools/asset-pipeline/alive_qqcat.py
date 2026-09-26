#!/usr/bin/env python3
"""
alive_qqcat.py — 在 qqcat 的 baseColor 贴图上重绘五官，提升"生动感"
输入: 已校色的 qqcat glb（如 qqcat-prod-tinted.glb）
输出: 五官增强后的 qqcat-prod-alive.glb
"""
import sys, json, math
import numpy as np
import bpy


def find_basecolor_image(obj):
    """返回 mesh 上第一个 principled BSDF 的 baseColor 贴图"""
    mat = obj.data.materials[0] if obj.data.materials else None
    if not mat or not mat.use_nodes:
        return None
    tree = mat.node_tree
    for node in tree.nodes:
        if node.type == 'BSDF_PRINCIPLED':
            socket = node.inputs.get('Base Color')
            if socket and socket.links:
                tex = socket.links[0].from_node
                if tex.type == 'TEX_IMAGE' and tex.image:
                    return tex.image
    return None


def save_array_as_image(arr, name, path):
    """用 Blender 保存 numpy 图像（arr: HxWx3, 0-1）"""
    h, w = arr.shape[:2]
    # Blender image pixels 按 [R,G,B,A] 行优先，从底行开始
    px = np.empty((h, w, 4), dtype=np.float32)
    px[:, :, :3] = np.clip(arr, 0, 1)
    px[:, :, 3] = 1.0
    img = bpy.data.images.new(name, width=w, height=h, alpha=True)
    img.pixels.foreach_set(px.ravel())
    img.filepath_raw = path
    img.file_format = 'PNG'
    img.save()
    bpy.data.images.remove(img)


def rgb(r, g, b):
    return np.array([r/255.0, g/255.0, b/255.0], dtype=np.float32)


def draw_soft_circle(img, cx, cy, r_outer, r_inner, color, alpha=1.0):
    h, w = img.shape[:2]
    if cx < -r_outer or cy < -r_outer or cx > w + r_outer or cy > h + r_outer:
        return
    yy, xx = np.ogrid[:h, :w]
    d = np.sqrt((xx - cx)**2 + (yy - cy)**2)
    mask = np.clip((r_outer - d) / max(r_outer - r_inner, 1e-3), 0, 1)
    mask = mask * alpha
    for c in range(3):
        img[:, :, c] = img[:, :, c] * (1 - mask) + color[c] * mask


def draw_eye_texture(img, uv_center, uv_radius):
    h, w = img.shape[:2]
    cx, cy = uv_center[0] * w, uv_center[1] * h
    r_px = uv_radius * min(w, h)
    if r_px < 2:
        return

    iris = rgb(230, 150, 45)
    pupil = rgb(38, 26, 20)
    highlight = rgb(255, 255, 255)
    lid = rgb(110, 80, 70)

    # 白眼底（淡淡的，避免"死白圈"）
    draw_soft_circle(img, cx, cy, r_px * 1.08, r_px * 0.90, rgb(253, 245, 235), 0.25)
    # 虹膜
    draw_soft_circle(img, cx, cy, r_px * 0.78, r_px * 0.36, iris, 0.98)
    # 瞳孔
    draw_soft_circle(img, cx, cy, r_px * 0.42, r_px * 0.18, pupil, 0.99)
    # 上眼睑弧线：只在眼球最上方边缘淡淡加深（细弧线）
    eyelid_y = cy - r_px * 0.80
    for y in range(max(0, int(eyelid_y - r_px*0.10)), min(h, int(eyelid_y + r_px*0.10))):
        for x in range(max(0, int(cx - r_px*0.85)), min(w, int(cx + r_px*0.85))):
            dy = (y - eyelid_y) / (r_px * 0.20 + 1e-3)
            dx = (x - cx) / (r_px * 0.85 + 1e-3)
            if abs(dx) <= 1 and -0.2 <= dy <= 0.8:
                fall = max(0, 1 - dx*dx - dy*dy)
                img[y, x] = img[y, x] * (1 - fall*0.25) + lid * (fall*0.25)
    # 高光：更偏上、更亮，营造湿润感
    draw_soft_circle(img, cx - r_px*0.34, cy - r_px*0.28, r_px*0.24, r_px*0.10, highlight, 0.98)
    draw_soft_circle(img, cx + r_px*0.22, cy + r_px*0.16, r_px*0.10, r_px*0.04, highlight, 0.88)


def draw_nose_mouth_blush(img, nose_uv, face_scale_px):
    h, w = img.shape[:2]
    nx, ny = nose_uv[0] * w, nose_uv[1] * h
    s = face_scale_px

    draw_soft_circle(img, nx, ny, s * 0.18, s * 0.08, rgb(255, 145, 160), 0.85)
    draw_soft_circle(img, nx - s*0.03, ny - s*0.04, s * 0.05, s * 0.02, rgb(255, 235, 235), 0.90)

    for dx in (-0.13, 0.13):
        mouth_cx = nx + dx * s
        mouth_cy = ny + s * 0.22
        draw_soft_circle(img, mouth_cx, mouth_cy, s * 0.06, s * 0.02, rgb(120, 90, 80), 0.65)

    for dx in (-1.0, 1.0):
        bx = nx + dx * s * 0.70
        by = ny + s * 0.02
        draw_soft_circle(img, bx, by, s * 0.28, s * 0.15, rgb(255, 175, 180), 0.20)


def detect_eyes_on_texture(arr):
    """颜色搜索两只眼睛，返回 [(uv, radius_uv)]"""
    h, w = arr.shape[:2]
    lum = 0.299*arr[:,:,0] + 0.587*arr[:,:,1] + 0.114*arr[:,:,2]
    threshold = np.percentile(lum, 2.0)
    mask = lum < threshold
    visited = np.zeros_like(mask)
    comps = []

    for sy in range(h):
        for sx in range(w):
            if not mask[sy, sx] or visited[sy, sx]:
                continue
            stack = [(sy, sx)]
            visited[sy, sx] = True
            pts = []
            while stack:
                y, x = stack.pop()
                pts.append((y, x))
                for dy, dx in ((1,0),(-1,0),(0,1),(0,-1)):
                    ny, nx = y+dy, x+dx
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not visited[ny, nx]:
                        visited[ny, nx] = True
                        stack.append((ny, nx))
            if len(pts) < 20:
                continue
            ys, xs = zip(*pts)
            cy, cx = np.mean(ys), np.mean(xs)
            # 等效圆半径
            r = math.sqrt(len(pts) / math.pi) / min(w, h)
            comps.append({'n': len(pts), 'cx': cx, 'cy': cy, 'r': r})

    print(f'[alive] dark components: {len(comps)}')
    for c in sorted(comps, key=lambda x: x['n'], reverse=True)[:8]:
        print(f"  n={c['n']:.0f} cx={c['cx']:.1f} cy={c['cy']:.1f} r={c['r']:.4f}")

    if len(comps) < 2:
        return []

    top = sorted(comps, key=lambda x: x['n'], reverse=True)[:6]
    best = None
    best_score = -1
    for i in range(len(top)):
        for j in range(i+1, len(top)):
            a, b = top[i], top[j]
            dx = abs(a['cx'] - b['cx'])
            dy = abs(a['cy'] - b['cy'])
            if dy > 120 or dx < 80:
                continue
            score = (a['n'] + b['n']) * (1 - dy/200.0) * (dx / w)
            if score > best_score:
                best_score = score
                best = (a, b)

    if best is None:
        best = (top[0], top[1])

    a, b = best
    left = min(a, b, key=lambda c: c['cx'])
    right = max(a, b, key=lambda c: c['cx'])
    res = [((left['cx']/w, left['cy']/h), left['r'] * 1.2),
           ((right['cx']/w, right['cy']/h), right['r'] * 1.2)]
    print(f"[alive] eyes uv L=({res[0][0][0]:.3f},{res[0][0][1]:.3f}) R=({res[1][0][0]:.3f},{res[1][0][1]:.3f}) r={res[0][1]:.4f}")
    return res


def main():
    if '--' in sys.argv:
        argv = sys.argv[sys.argv.index('--') + 1:]
    else:
        argv = sys.argv[1:]
    preview = '--preview' in argv
    argv = [a for a in argv if a != '--preview']
    if len(argv) < 3:
        print('usage: alive_qqcat.py input.glb output.glb eye_pick.json [--preview]')
        return
    src_path, out_path, eye_json = argv[0], argv[1], argv[2]

    bpy.ops.object.select_all(action='SELECT')
    bpy.ops.object.delete(use_global=False)

    bpy.ops.import_scene.gltf(filepath=src_path)
    mesh_obj = None
    for ob in bpy.context.scene.objects:
        if ob.type == 'MESH':
            mesh_obj = ob
            break
    if mesh_obj is None:
        print('[alive] no mesh found')
        return

    img = find_basecolor_image(mesh_obj)
    if img is None:
        print('[alive] no baseColor image found')
        return
    print(f'[alive] baseColor image: {img.name} {img.size[0]}x{img.size[1]}')

    w, h = img.size
    px = np.empty(w * h * 4, dtype=np.float32)
    img.pixels.foreach_get(px)
    arr = px.reshape((h, w, 4))[:, :, :3].copy()

    eye_uvs = detect_eyes_on_texture(arr)

    if len(eye_uvs) == 2:
        d_uv = math.sqrt((eye_uvs[0][0][0]-eye_uvs[1][0][0])**2 +
                         (eye_uvs[0][0][1]-eye_uvs[1][0][1])**2)
        eye_r_uv = d_uv * 0.15   # 眼睛半径 ≈ 眼间距的 15%（原型大圆眼）
        margin = 0.02
        centers = []
        for uv, _r in eye_uvs:
            cx = min(max(uv[0], eye_r_uv + margin), 1.0 - eye_r_uv - margin)
            cy = min(max(uv[1], eye_r_uv + margin), 1.0 - eye_r_uv - margin)
            centers.append((cx, cy))
        # 对齐两眼 y，避免 3D 上一高一低
        avg_y = sum(c[1] for c in centers) / len(centers)
        centers = [(min(max(c[0], eye_r_uv + margin), 1.0 - eye_r_uv - margin),
                    min(max(avg_y, eye_r_uv + margin), 1.0 - eye_r_uv - margin)) for c in centers]
        for c in centers:
            draw_eye_texture(arr, c, eye_r_uv)
        # 用实际调整后中心重新算中点/脸尺度
        d_uv = math.sqrt((centers[0][0]-centers[1][0])**2 +
                         (centers[0][1]-centers[1][1])**2)
        face_scale_px = d_uv * min(w, h)
        mid = ((centers[0][0] + centers[1][0]) / 2.0,
               (centers[0][1] + centers[1][1]) / 2.0 + d_uv * 0.55)
        print(f'[alive] centers L=({centers[0][0]:.3f},{centers[0][1]:.3f}) R=({centers[1][0]:.3f},{centers[1][1]:.3f})')
        print(f'[alive] nose uv={mid[0]:.3f},{mid[1]:.3f}')
        draw_nose_mouth_blush(arr, mid, face_scale_px)

    dbg_path = out_path.replace('.glb', '_basecolor.png')
    save_array_as_image(arr, 'AliveDebug', dbg_path)
    print(f'[alive] debug baseColor saved {dbg_path}')

    if preview:
        print('[alive] preview mode done')
        return

    out_px = np.empty((h, w, 4), dtype=np.float32)
    out_px[:, :, :3] = np.clip(arr, 0, 1)
    out_px[:, :, 3] = 1.0
    img.pixels.foreach_set(out_px.ravel())
    img.update()
    img.pack()

    bpy.ops.export_scene.gltf(filepath=out_path.replace('.glb', ''),
                              export_format='GLB',
                              export_yup=True,
                              export_materials='EXPORT',
                              export_image_format='AUTO')
    print(f'[alive] saved {out_path}')


if __name__ == '__main__':
    main()
