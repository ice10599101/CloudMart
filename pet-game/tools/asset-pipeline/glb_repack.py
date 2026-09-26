# -*- coding: utf-8 -*-
"""
Read / rebuild .glb containers.

Splitting the container handling out of the individual edit tools means both
"swap an image" and "patch the material" share one proven writer. Swapping bytes
inside a GLB is not a local operation: bufferView offsets and the BIN chunk
length all shift, so the whole buffer has to be re-laid out. Every bufferView is
re-aligned to 4 bytes, which satisfies the glTF accessor alignment rule for every
componentType in use.

Usage:
    python glb_repack.py <in.glb> <out.glb> <slot> <new_image.png>
    slot: baseColorTexture | normalTexture | metallicRoughnessTexture | emissiveTexture | occlusionTexture
"""
import json
import os
import struct
import sys

JSON_CHUNK = 0x4E4F534A
BIN_CHUNK = 0x004E4942

PBR_SLOTS = ("baseColorTexture", "metallicRoughnessTexture")
TEX_SLOTS = ("normalTexture", "occlusionTexture", "emissiveTexture")

MIME = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg"}


def pad4(n):
    return (4 - (n % 4)) % 4


def read_glb(path):
    raw = open(path, "rb").read()
    magic, version, total = struct.unpack("<4sII", raw[:12])
    if magic != b"glTF":
        raise ValueError("not a GLB")
    offset, gltf, binary = 12, None, None
    while offset < total:
        clen, ctype = struct.unpack("<II", raw[offset:offset + 8])
        data = raw[offset + 8:offset + 8 + clen]
        if ctype == JSON_CHUNK:
            gltf = json.loads(data.decode("utf-8"))
        elif ctype == BIN_CHUNK:
            binary = data
        offset += 8 + clen
    return gltf, binary


def slice_views(gltf, binary):
    """One byte blob per bufferView, in declaration order."""
    out = []
    for bv in gltf["bufferViews"]:
        start = bv.get("byteOffset", 0)
        out.append(binary[start:start + bv["byteLength"]])
    return out


def write_glb(gltf, blobs, dst):
    """Re-lay the buffer from `blobs` and write a spec-conformant GLB."""
    rebuilt = bytearray()
    for i, bv in enumerate(gltf["bufferViews"]):
        rebuilt += b"\x00" * pad4(len(rebuilt))
        bv["byteOffset"] = len(rebuilt)
        bv["byteLength"] = len(blobs[i])
        rebuilt += blobs[i]

    gltf["buffers"][0]["byteLength"] = len(rebuilt)

    json_bytes = json.dumps(gltf, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    json_bytes += b" " * pad4(len(json_bytes))
    bin_bytes = bytes(rebuilt) + b"\x00" * pad4(len(rebuilt))

    total = 12 + 8 + len(json_bytes) + 8 + len(bin_bytes)
    out = bytearray()
    out += struct.pack("<4sII", b"glTF", 2, total)
    out += struct.pack("<II", len(json_bytes), JSON_CHUNK) + json_bytes
    out += struct.pack("<II", len(bin_bytes), BIN_CHUNK) + bin_bytes

    open(dst, "wb").write(bytes(out))
    return len(out)


def find_image_index(gltf, slot):
    textures = gltf["textures"]
    for m in gltf["materials"]:
        pbr = m.get("pbrMetallicRoughness", {})
        if slot in PBR_SLOTS and slot in pbr:
            return textures[pbr[slot]["index"]]["source"]
        if slot in TEX_SLOTS and slot in m:
            return textures[m[slot]["index"]]["source"]
    raise KeyError("slot %r not found in any material" % slot)


def replace_image(gltf, blobs, slot, blob, ext):
    img_index = find_image_index(gltf, slot)
    bv_index = gltf["images"][img_index]["bufferView"]
    old = len(blobs[bv_index])
    blobs[bv_index] = blob
    gltf["images"][img_index]["mimeType"] = MIME[ext]
    return img_index, bv_index, old


def main():
    src, dst, slot, new_image = sys.argv[1:5]

    gltf, binary = read_glb(src)
    blobs = slice_views(gltf, binary)

    blob = open(new_image, "rb").read()
    ext = os.path.splitext(new_image)[1].lower()
    if ext not in MIME:
        raise ValueError("unsupported image extension %r" % ext)

    img_index, bv_index, old_len = replace_image(gltf, blobs, slot, blob, ext)
    print("slot %r -> image %d -> bufferView %d" % (slot, img_index, bv_index))
    print("replaced %d -> %d bytes (%.2f MB)" % (old_len, len(blob), len(blob) / 1048576.0))

    size = write_glb(gltf, blobs, dst)
    print("wrote %s (%.2f MB)" % (dst, size / 1048576.0))


if __name__ == "__main__":
    main()
