#!/usr/bin/env python3
"""Raw GLB -> embedded PNG extractor (stdlib only).

Blender 5.2 rejects these rigged GLBs with 'Bad glTF: json error: utf-8',
so we skip the JSON and scan the BIN chunk for PNG signatures directly.
"""
import sys, struct

SIG = b'\x89PNG\r\n\x1a\n'

def find_pngs(data: bytes):
    out = []
    i = 0
    while True:
        idx = data.find(SIG, i)
        if idx < 0:
            break
        # walk chunks
        pos = idx + len(SIG)
        end = None
        try:
            while pos < len(data):
                if pos + 8 > len(data):
                    break
                length = struct.unpack('>I', data[pos:pos+4])[0]
                ctype = data[pos+4:pos+8]
                pos += 8 + length + 4  # data + CRC
                if ctype == b'IEND':
                    end = pos
                    break
        except Exception:
            end = None
        if end is None:
            # not a clean PNG from here; skip
            i = idx + 1
            continue
        out.append((idx, data[idx:end]))
        i = end
    return out

def main():
    src = sys.argv[1]
    outdir = sys.argv[2]
    import os
    os.makedirs(outdir, exist_ok=True)
    with open(src, 'rb') as f:
        data = f.read()
    print(f'[extract] glb size={len(data)}')
    pngs = find_pngs(data)
    print(f'[extract] found {len(pngs)} PNG blobs')
    for k, (off, blob) in enumerate(pngs):
        # parse IHDR for dimensions
        w = h = 0
        if len(blob) > 33:
            w, h = struct.unpack('>II', blob[16:24])
        path = f'{outdir}/img_{k}_{w}x{h}.png'
        with open(path, 'wb') as o:
            o.write(blob)
        print(f'[extract] img_{k}: offset={off} bytes={len(blob)} {w}x{h} -> {path}')

if __name__ == '__main__':
    main()
