#!/usr/bin/env python3
"""Tolerant GLB structure scan: map mesh -> material -> baseColor texture -> bufferView offset.
JSON may contain non-UTF8 bytes; we decode with errors='replace' so structure survives."""
import sys, struct, json

def main():
    src = sys.argv[1]
    with open(src, 'rb') as f:
        data = f.read()
    magic, ver, length = struct.unpack('<III', data[:12])
    print(f'magic={magic:#x} ver={ver} length={length} filelen={len(data)}')
    off = 12
    json_chunk = bin_chunk = None
    while off < len(data):
        clen, ctype = struct.unpack('<II', data[off:off+8])
        cdata = data[off+8:off+8+clen]
        if ctype == 0x4E4F534A:  # JSON
            json_chunk = cdata
        elif ctype == 0x004E4942:  # BIN
            bin_chunk = cdata
        off += 8 + clen
        if clen % 4: off += 4 - (clen % 4)
    txt = json_chunk.decode('utf-8', errors='replace')
    g = json.loads(txt)
    print(f'has images={ "images" in g } meshes={len(g.get("meshes",[]))} materials={len(g.get("materials",[]))}')

    # bufferViews
    bvs = g.get('bufferViews', [])
    # images -> bufferView
    img_bv = {}
    for i, im in enumerate(g.get('images', [])):
        bv = im.get('bufferView')
        img_bv[i] = (bv, im.get('name'))
    print('images:', img_bv)

    # textures -> source(image)
    tex_src = {}
    for i, t in enumerate(g.get('textures', [])):
        tex_src[i] = t.get('source')

    # materials -> baseColorTexture.index (texture)
    mat_base = {}
    for i, m in enumerate(g.get('materials', [])):
        pbr = m.get('pbrMetallicRoughness', {})
        bt = pbr.get('baseColorTexture')
        mat_base[i] = (m.get('name'), bt.get('index') if bt else None)

    # meshes -> primitives -> material
    for mi, mesh in enumerate(g.get('meshes', [])):
        name = mesh.get('name')
        for pi, prim in enumerate(mesh.get('primitives', [])):
            mat = prim.get('material')
            # resolve material -> texture -> image -> bufferView
            mname, tex = mat_base.get(mat, (None, None))
            img = tex_src.get(tex) if tex is not None else None
            bv, iname = img_bv.get(img, (None, None))
            print(f'  mesh[{mi}] name={name!r} prim={pi} -> material[{mat}]={mname!r} '
                  f'-> texture={tex} -> image={img} name={iname!r} bufferView={bv}')

if __name__ == '__main__':
    main()
