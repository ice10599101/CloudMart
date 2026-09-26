# -*- coding: utf-8 -*-
"""
Repair degenerate (zero-length) TANGENT vectors in a .glb.

glTF requires TANGENT vectors to be unit length. Tangent generation on a mesh
with mirrored UVs or degenerate triangles can emit a handful of zero vectors,
which the validator flags as ACCESSOR_VECTOR3_NON_UNIT and which can turn into
NaN inside a shader. Replacing those few vectors with a valid fallback is safer
than dropping the attribute entirely and relying on runtime tangent generation.

Usage: python fix_tangents.py <in.glb> <out.glb>
"""
import os
import struct
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glb_repack import read_glb, slice_views, write_glb  # noqa: E402

EPS = 1e-5


def main():
    src, dst = sys.argv[1], sys.argv[2]
    gltf, binary = read_glb(src)
    blobs = slice_views(gltf, binary)

    fixed_total = 0
    for mesh in gltf["meshes"]:
        for prim in mesh["primitives"]:
            if "TANGENT" not in prim["attributes"]:
                continue
            acc_index = prim["attributes"]["TANGENT"]
            acc = gltf["accessors"][acc_index]
            if acc["componentType"] != 5126 or acc["type"] != "VEC4":
                print("skip: TANGENT is not float VEC4", file=sys.stderr)
                continue
            bv_index = acc["bufferView"]
            bv = gltf["bufferViews"][bv_index]
            item = 16  # 4 x float32
            # the vertex buffer is often interleaved; acc.byteOffset is then the
            # offset of TANGENT inside each vertex, and the stride comes from
            # the bufferView
            stride = bv.get("byteStride") or item

            blob = bytearray(blobs[bv_index])
            base = acc.get("byteOffset", 0)
            count = acc["count"]
            fixed = 0
            for i in range(count):
                off = base + i * stride
                x, y, z, w = struct.unpack_from("<4f", blob, off)
                if x * x + y * y + z * z < EPS * EPS:
                    # a valid fallback: unit +X, keep the handedness sign
                    struct.pack_into("<4f", blob, off, 1.0, 0.0, 0.0,
                                     w if w else 1.0)
                    fixed += 1
            print("TANGENT accessor %d: %d/%d degenerate -> repaired"
                  % (acc_index, fixed, count))
            fixed_total += fixed
            blobs[bv_index] = bytes(blob)

    size = write_glb(gltf, blobs, dst)
    print("repaired %d tangent(s); wrote %s (%.2f MB)" % (fixed_total, dst, size / 1048576.0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
