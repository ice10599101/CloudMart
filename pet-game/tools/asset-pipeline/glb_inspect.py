# -*- coding: utf-8 -*-
"""
Inspect a .glb and pull its embedded textures out to disk.

Used to tell "the texture really is washed out" apart from "the preview
render was just brightly lit" — the service preview is not a faithful
read of the material, whereas the embedded baseColor image is.

Usage: python glb_inspect.py <model.glb> [outdir]
"""
import json
import os
import struct
import sys

JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942


def read_glb(path):
    raw = open(path, "rb").read()
    magic, version, total = struct.unpack("<4sII", raw[:12])
    if magic != b"glTF":
        raise ValueError("not a GLB")
    offset = 12
    gltf, binary = None, None
    while offset < total:
        clen, ctype = struct.unpack("<II", raw[offset:offset + 8])
        data = raw[offset + 8:offset + 8 + clen]
        if ctype == JSON_CHUNK:
            gltf = json.loads(data.decode("utf-8"))
        elif ctype == BIN_CHUNK:
            binary = data
        offset += 8 + clen
    return gltf, binary


def main():
    src = sys.argv[1]
    outdir = sys.argv[2] if len(sys.argv) > 2 else "model/textures"
    os.makedirs(outdir, exist_ok=True)

    gltf, binary = read_glb(src)
    print("generator     : %s" % gltf.get("asset", {}).get("generator"))
    print("meshes        : %d" % len(gltf.get("meshes", [])))
    print("materials     : %d" % len(gltf.get("materials", [])))
    print("images        : %d" % len(gltf.get("images", [])))
    print("animations    : %d" % len(gltf.get("animations", [])))
    print("skins (rigs)  : %d" % len(gltf.get("skins", [])))
    print("nodes         : %d" % len(gltf.get("nodes", [])))

    for m in gltf.get("materials", []):
        pbr = m.get("pbrMetallicRoughness", {})
        print("material %-14s baseColorFactor=%s metallic=%s roughness=%s"
              % (m.get("name"), pbr.get("baseColorFactor"),
                 pbr.get("metallicFactor"), pbr.get("roughnessFactor")))

    total_tris = 0
    for mesh in gltf.get("meshes", []):
        for prim in mesh.get("primitives", []):
            idx = prim.get("indices")
            if idx is not None:
                total_tris += gltf["accessors"][idx]["count"] // 3
    print("triangles     : %d" % total_tris)

    textures = gltf.get("textures", [])
    materials = gltf.get("materials", [])
    slot_names = {}
    for m in materials:
        pbr = m.get("pbrMetallicRoughness", {})
        for slot in ("baseColorTexture", "metallicRoughnessTexture"):
            if slot in pbr:
                slot_names[textures[pbr[slot]["index"]]["source"]] = slot
        for slot in ("normalTexture", "occlusionTexture", "emissiveTexture"):
            if slot in m:
                slot_names[textures[m[slot]["index"]]["source"]] = slot

    saved = []
    for i, img in enumerate(gltf.get("images", [])):
        bv = gltf["bufferViews"][img["bufferView"]]
        start = bv.get("byteOffset", 0)
        blob = binary[start:start + bv["byteLength"]]
        mime = img.get("mimeType", "image/png")
        ext = "png" if "png" in mime else "jpg"
        slot = slot_names.get(i, img.get("name", "image%d" % i))
        dest = os.path.join(outdir, "%s.%s" % (slot, ext))
        open(dest, "wb").write(blob)
        saved.append((slot, dest, len(blob)))
        print("texture %-22s -> %s (%.0f KB)" % (slot, dest, len(blob) / 1024.0))

    print(json.dumps({"textures": [s[1] for s in saved]}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
