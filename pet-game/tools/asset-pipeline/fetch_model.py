# -*- coding: utf-8 -*-
"""Download every artifact of a finished 3D job into the local model/ folder."""
import json
import os
import sys
import time
import urllib.request

SRC = sys.argv[1] if len(sys.argv) > 1 else "model/raw_result.json"
PREFIX = sys.argv[2] if len(sys.argv) > 2 else "pet_penguin"
OUT = "model"
TS = time.strftime("%Y%m%d_%H%M%S")

EXT_NAME = {
    "GLB": ("%s_%s.glb" % (PREFIX, TS), "glb"),
    "FBX": ("%s_%s.fbx" % (PREFIX, TS), "fbx"),
    "OBJ": ("%s_obj_%s.zip" % (PREFIX, TS), "obj"),
    "STL": ("%s_%s.stl" % (PREFIX, TS), "stl"),
    "USDZ": ("%s_%s.usdz" % (PREFIX, TS), "usdz"),
}


def fetch(url, dest):
    req = urllib.request.Request(url, headers={"User-Agent": "curl/8"})
    with urllib.request.urlopen(req, timeout=300) as r, open(dest, "wb") as fh:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            fh.write(chunk)
    return os.path.getsize(dest)


def main():
    data = json.load(open(SRC, encoding="utf-8"))
    files = data["raw"].get("ResultFile3Ds", [])
    os.makedirs(OUT, exist_ok=True)

    manifest = {"job_id": data.get("job_id"), "files": []}
    seen_preview = False

    for item in files:
        kind = item.get("Type", "")
        url = item.get("Url")
        if not url:
            continue
        name, _ = EXT_NAME.get(kind, ("pet_penguin_%s.%s" % (TS, kind.lower()), kind.lower()))
        dest = os.path.join(OUT, name)
        size = fetch(url, dest)
        print("downloaded %-6s -> %s (%.2f MB)" % (kind, dest, size / 1048576.0))
        manifest["files"].append({"type": kind, "path": dest, "bytes": size})

        prev = item.get("PreviewImageUrl")
        if prev and not seen_preview:
            pname = "%s_%s_preview.png" % (PREFIX, TS)
            pdest = os.path.join(OUT, pname)
            psize = fetch(prev, pdest)
            print("downloaded PREVIEW -> %s (%.2f MB)" % (pdest, psize / 1048576.0))
            manifest["files"].append({"type": "PREVIEW", "path": pdest, "bytes": psize})
            seen_preview = True

    with open(os.path.join(OUT, "%s_manifest.json" % PREFIX), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    sys.exit(main())
