#!/usr/bin/env python3
"""Splice the redrawn albedo PNG into the deployed GLB's bufferView 7 slot (in place)."""
import sys, shutil, struct

DEPLOY = 'D:/Ide/IdeaProjects/CloudMart/pet-game/assets/resources/models/cat/pet-cat-rigged.glb'
REDRAW = 'D:/Ide/IdeaProjects/CloudMart/pet-game/tools/asset-pipeline/_probe/redraw.png'
OFF = 742520
LENGTH = 1155761

def main():
    # backup
    shutil.copy2(DEPLOY, DEPLOY + '.bak')
    print(f'[splice] backed up to {DEPLOY}.bak')

    with open(DEPLOY, 'rb') as f:
        glb = bytearray(f.read())
    with open(REDRAW, 'rb') as f:
        png = f.read()
    print(f'[splice] glb={len(glb)} png={len(png)} slot={LENGTH}')
    if len(png) > LENGTH:
        raise SystemExit(f'redraw too big: {len(png)} > {LENGTH}')
    if glb[OFF:OFF+8] != b'\x89PNG\r\n\x1a\n':
        raise SystemExit('slot does not start with PNG signature; offset wrong!')
    padded = png + b'\x00' * (LENGTH - len(png))
    glb[OFF:OFF+LENGTH] = padded
    with open(DEPLOY, 'wb') as f:
        f.write(glb)
    print(f'[splice] wrote spliced glb ({len(glb)} bytes)')

    # verify: signature of new image region
    with open(DEPLOY, 'rb') as f:
        chk = f.read()
    assert chk[:12] == glb[:12], 'header changed!'
    assert chk[OFF:OFF+8] == b'\x89PNG\r\n\x1a\n', 'PNG sig missing after splice'
    # IHDR width/height
    w, h = struct.unpack('>II', chk[OFF+16:OFF+24])
    print(f'[splice] embedded image IHDR = {w}x{h}')

if __name__ == '__main__':
    main()
