# -*- coding: utf-8 -*-
"""
Give a generated pet asset a non-metallic, soft-touch surface.

Image-to-3D output routinely marks the eyeball as a polished metal mirror:
metallic ~0.92 and roughness ~0.035 on the eye region, while the fur sits at a
normal ~0.63 roughness. On top of that the exported material can carry a
KHR_materials_specular extension with specularColorFactor above 1, which boosts
the specular response beyond what any real surface does. Together they read as
"metallic" / "greasy".

Fixes applied, all data-level so they survive any downstream import:
  1. metallic channel of the metallicRoughness atlas -> 0
  2. roughness channel floored, so nothing stays a mirror
  3. material metallicFactor -> 0 (glTF defaults it to 1.0 when absent, which is
     what let the metallic channel through in the first place)
  4. KHR_materials_specular dropped

Usage:
    python material_fix.py <in.glb> <out.glb>
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from glb_repack import (find_image_index, read_glb, replace_image,  # noqa: E402
                        slice_views, write_glb)
from png_prep import read_png, write_png  # noqa: E402

ROUGH_FLOOR = 80          # ~0.31 — keeps a wet-looking gloss, never a mirror
WORKDIR = "model/cat_textures"


def main():
    src, dst = sys.argv[1], sys.argv[2]

    gltf, binary = read_glb(src)
    blobs = slice_views(gltf, binary)

    # ---- 1 & 2: rewrite the metallic / roughness channels -------------------
    img_index = find_image_index(gltf, "metallicRoughnessTexture")
    bv_index = gltf["images"][img_index]["bufferView"]
    tmp = os.path.join(WORKDIR, "_mr_original.png")
    os.makedirs(WORKDIR, exist_ok=True)
    open(tmp, "wb").write(blobs[bv_index])

    w, h, nch, px = read_png(tmp)
    print("metallicRoughness atlas: %dx%d ch=%d" % (w, h, nch))
    px = bytearray(px)

    raised = 0
    for i in range(0, len(px), nch):
        g = px[i + 1]
        if g < ROUGH_FLOOR:
            px[i + 1] = ROUGH_FLOOR
            raised += 1
        px[i + 2] = 0                      # metallic: none, anywhere

    fixed = os.path.join(WORKDIR, "metallicRoughness_fixed.png")
    write_png(fixed, w, h, nch, bytes(px))
    print("roughness raised on %d px (%.2f%%), metallic zeroed on all px"
          % (raised, 100.0 * raised / (w * h)))

    img, bv, old = replace_image(gltf, blobs, "metallicRoughnessTexture",
                                 open(fixed, "rb").read(), ".png")
    print("texture %d -> bufferView %d: %d -> %d bytes" % (img, bv, old, len(blobs[bv])))

    # ---- 3 & 4: clean the material -----------------------------------------
    for m in gltf["materials"]:
        pbr = m.setdefault("pbrMetallicRoughness", {})
        before = pbr.get("metallicFactor", "absent (=1.0 per spec)")
        pbr["metallicFactor"] = 0.0
        pbr["roughnessFactor"] = 1.0
        ext = m.get("extensions")
        if ext and "KHR_materials_specular" in ext:
            dropped = ext.pop("KHR_materials_specular")
            print("dropped KHR_materials_specular %s" % json.dumps(dropped))
            if not ext:
                m.pop("extensions")
        print("metallicFactor: %s -> 0.0" % (before,))

    # extensionsUsed must not advertise an extension nothing references —
    # leaving it in produces validator warnings in the engine importer.
    used = gltf.get("extensionsUsed")
    if used and "KHR_materials_specular" in used:
        used.remove("KHR_materials_specular")
        print("extensionsUsed cleaned -> %s" % used)
        if not used:
            gltf.pop("extensionsUsed")
            print("extensionsUsed removed (now empty)")

    size = write_glb(gltf, blobs, dst)
    print("wrote %s (%.2f MB)" % (dst, size / 1048576.0))

    os.remove(tmp)
    print(json.dumps(gltf["materials"], ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
