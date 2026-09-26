#!/usr/bin/env python3
import sys, struct, json
def main():
    src = sys.argv[1]
    with open(src,'rb') as f: data=f.read()
    off=12
    while off<len(data):
        clen,ctype=struct.unpack('<II',data[off:off+8])
        cdata=data[off+8:off+8+clen]
        if ctype==0x4E4F534A:
            g=json.loads(cdata.decode('utf-8',errors='replace'))
        off+=8+clen
        if clen%4: off+=4-(clen%4)
    for i in (6,7,8):
        bv=g['bufferViews'][i]
        img=g['images'][i] if i<len(g['images']) else None
        print(f'bv[{i}] byteOffset={bv.get("byteOffset")} byteLength={bv.get("byteLength")} img_name={img.get("name") if img else None}')
    # bin chunk start
    print('binChunkByteLength=', g['buffers'][0].get('byteLength'))
if __name__=='__main__':
    main()
