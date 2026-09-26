# -*- coding: utf-8 -*-
"""
Rasterise a mesh's UV shells to find the exact atlas footprint of one island.

Colour thresholds are not a reliable way to delimit an eye in a generated
texture: the sclera sits at luminance ~200 while the eye socket sits at ~165, so
any threshold either leaks onto the socket or leaves holes in the sclera. The UV
layout, by contrast, is exact — islands are separated by padding, so rasterising
the UV triangles and taking connected components gives each island's true
footprint.

Usage:
    python uv_island.py <model.glb> <u> <v> <half> [out.png]
        u, v   point that lies inside the wanted island (atlas texels, origin
               top-left, v measured downward) — normally the iris centroid
        half   half-size of the working window in texels
"""
import json
import os
import struct
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glb_repack import read_glb  # noqa: E402
from png_prep import write_png  # noqa: E402

COMPONENTS = {5120: ("b", 1), 5121: ("B", 1), 5122: ("h", 2),
              5123: ("H", 2), 5125: ("I", 4), 5126: ("f", 4)}
NCOMP = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}


def read_accessor(gltf, binary, index):
    acc = gltf["accessors"][index]
    fmt, size = COMPONENTS[acc["componentType"]]
    n = NCOMP[acc["type"]]
    bv = gltf["bufferViews"][acc["bufferView"]]
    base = bv.get("byteOffset", 0) + acc.get("byteOffset", 0)
    stride = bv.get("byteStride") or (size * n)
    out = []
    for i in range(acc["count"]):
        off = base + i * stride
        out.append(struct.unpack_from("<%d%s" % (n, fmt), binary, off))
    return out


def main():
    path = sys.argv[1]
    pu, pv, half = float(sys.argv[2]), float(sys.argv[3]), int(sys.argv[4])
    out_png = sys.argv[5] if len(sys.argv) > 5 else None

    gltf, binary = read_glb(path)

    prim = gltf["meshes"][0]["primitives"][0]
    print("attributes: %s" % sorted(prim["attributes"].keys()))
    uv = read_accessor(gltf, binary, prim["attributes"]["TEXCOORD_0"])
    idx = [i[0] for i in read_accessor(gltf, binary, prim["indices"])]
    tris = len(idx) // 3
    print("vertices %d, triangles %d" % (len(uv), tris))

    # texture size from the baseColor image
    tex_w = tex_h = 4096

    def to_px(t):
        # glTF UV origin is bottom-left; atlas rows are written top-down
        return t[0] * tex_w, (1.0 - t[1]) * tex_h

    x0, y0 = int(pu - half), int(pv - half)
    x1, y1 = int(pu + half), int(pv + half)
    mw, mh = x1 - x0, y1 - y0

    mask = bytearray(mw * mh)
    drawn = 0
    for t in range(tris):
        a, b, c = idx[t * 3], idx[t * 3 + 1], idx[t * 3 + 2]
        pa, pb, pc = to_px(uv[a]), to_px(uv[b]), to_px(uv[c])
        tminx = min(pa[0], pb[0], pc[0])
        tmaxx = max(pa[0], pb[0], pc[0])
        tminy = min(pa[1], pb[1], pc[1])
        tmaxy = max(pa[1], pb[1], pc[1])
        if tmaxx < x0 or tminx > x1 or tmaxy < y0 or tminy > y1:
            continue
        # a triangle spanning a huge area is not part of a single island
        if (tmaxx - tminx) > 600 or (tmaxy - tminy) > 600:
            continue
        ax, ay = pa
        bx, by = pb
        cx, cy = pc
        area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if abs(area) < 1e-9:
            continue
        sx0 = max(int(min(ax, bx, cx)), x0)
        sx1 = min(int(max(ax, bx, cx)) + 1, x1)
        sy0 = max(int(min(ay, by, cy)), y0)
        sy1 = min(int(max(ay, by, cy)) + 1, y1)
        if sx1 <= sx0 or sy1 <= sy0:
            continue
        drawn += 1
        inv = 1.0 / area
        for py in range(sy0, sy1):
            fy = py + 0.5
            row = (py - y0) * mw
            for px in range(sx0, sx1):
                fx = px + 0.5
                w0 = ((bx - ax) * (fy - ay) - (by - ay) * (fx - ax)) * inv
                w1 = ((fx - ax) * (cy - ay) - (fy - ay) * (cx - ax)) * inv
                if w0 >= -0.002 and w1 >= -0.002 and (w0 + w1) <= 1.002:
                    mask[row + (px - x0)] = 1
    print("triangles rasterised: %d" % drawn)

    # connected component containing the seed point
    su, sv = int(pu) - x0, int(pv) - y0
    if not (0 <= su < mw and 0 <= sv < mh) or not mask[sv * mw + su]:
        print("seed (%d,%d) is not covered by any triangle" % (int(pu), int(pv)),
              file=sys.stderr)
        return 1

    comp = bytearray(mw * mh)
    q = deque([(su, sv)])
    comp[sv * mw + su] = 1
    size = 0
    while q:
        x, y = q.popleft()
        size += 1
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < mw and 0 <= ny < mh:
                k = ny * mw + nx
                if mask[k] and not comp[k]:
                    comp[k] = 1
                    q.append((nx, ny))

    xs = [i % mw for i, v in enumerate(comp) if v]
    ys = [i // mw for i, v in enumerate(comp) if v]
    print("island: %d texels (window %d), bbox x=%d..%d y=%d..%d"
          % (size, mw * mh, min(xs) + x0, max(xs) + x0, min(ys) + y0, max(ys) + y0))

    if out_png:
        nch = 3
        img = bytearray(mw * mh * nch)
        for i in range(mw * mh):
            v = 255 if comp[i] else (70 if mask[i] else 25)
            img[i * nch] = img[i * nch + 1] = img[i * nch + 2] = v
        write_png(out_png, mw, mh, nch, bytes(img))
        print("wrote %s (white = the island, grey = other islands)" % out_png)

    return 0


if __name__ == "__main__":
    sys.exit(main())
