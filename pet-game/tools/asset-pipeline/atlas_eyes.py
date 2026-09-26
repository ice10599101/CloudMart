# -*- coding: utf-8 -*-
"""
Locate the eye regions inside a generated baseColor atlas.

The atlases produced by image-to-3D are photogrammetry-style UV layouts, so the
eye areas have to be found before they can be re-graded. Amber iris pixels are
the most reliable anchor: they are strongly saturated, warm, and only ever
belong to the iris. From those clusters we can derive the surrounding sclera.

Usage:
    python atlas_eyes.py analyze  <baseColor.png> <outdir>
"""
import collections
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_prep import crop, read_png, write_png  # noqa: E402

CELL = 128


def is_amber(r, g, b):
    """Warm, saturated, bright-ish pixel — iris material."""
    mx, mn = max(r, g, b), min(r, g, b)
    return mx - mn > 55 and r > 110 and r >= g >= b and (r - b) > 65


def main():
    src, outdir = sys.argv[2], sys.argv[3]
    w, h, nch, px = read_png(src)
    print("atlas: %dx%d ch=%d" % (w, h, nch))

    cells = collections.Counter()
    total = 0
    for y in range(h):
        base = y * w * nch
        for x in range(w):
            i = base + x * nch
            if is_amber(px[i], px[i + 1], px[i + 2]):
                cells[(x // CELL, y // CELL)] += 1
                total += 1

    print("amber pixels: %d (%.3f%% of atlas)" % (total, 100.0 * total / (w * h)))
    print("dense cells (>=60 px):")
    for (cx, cy), n in cells.most_common(12):
        if n < 60:
            break
        print("  cell(%d,%d)  px=%d   box x=%d..%d y=%d..%d"
              % (cx, cy, n, cx * CELL, (cx + 1) * CELL, cy * CELL, (cy + 1) * CELL))

    os.makedirs(outdir, exist_ok=True)
    for idx, ((cx, cy), n) in enumerate(cells.most_common(4)):
        if n < 60:
            break
        x0, y0 = max(0, cx * CELL - 128), max(0, cy * CELL - 128)
        x1, y1 = min(w, (cx + 1) * CELL + 128), min(h, (cy + 1) * CELL + 128)
        cw, ch, cpx = crop(px, w, h, nch, x0, y0, x1, y1)
        dest = os.path.join(outdir, "eye_candidate_%d.png" % idx)
        write_png(dest, cw, ch, nch, cpx)
        print("  wrote %s  (%dx%d from %d,%d)" % (dest, cw, ch, x0, y0))


if __name__ == "__main__":
    main()
