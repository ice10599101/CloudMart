# -*- coding: utf-8 -*-
"""
Re-grade the sclera of a generated pet's eyes inside its baseColor atlas.

Why not regenerate: the atlas is a photogrammetry-style UV layout in which the
eye occupies a single UV island (both eyes are mirrored onto it). Re-running the
generator produces the same pure-white sclera, so the fix has to happen in the
texture.

Targeting works in two stages:
  1. The iris is found by a tight amber test — the only strongly saturated warm
     area in the atlas. A loose test also catches warm fur around the socket and
     inflates the working area, which is why the amber threshold is strict here.
  2. That mask is then grown outward by REACH pixels. The sclera is immediately
     adjacent to the iris, so a short outward growth covers it while leaving the
     rest of the fur alone. A bounding circle was tried first and proved too
     blunt — it tinted the eyelid fur.

The specular catchlight is protected: the brighter a pixel is, the weaker the
re-tint, so the eye keeps a neutral highlight and does not collapse into a
single warm mass.

Usage:
    python eye_regrade.py <baseColor.png> <out.png> [preview_dir]
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from png_prep import crop, read_png, write_png  # noqa: E402

# Warm cream-yellow. Deliberately low saturation — a saturated yellow reads as
# jaundice on a pale grey animal, which is the opposite of "alive".
TARGET = (241, 226, 170)
TARGET_LUM = sum(TARGET) // 3

SAT_MAX = 40      # above this it is iris / scarf / bell — leave alone
LUM_FLOOR = 118   # below this it is pupil or fur shadow — leave alone
LUM_FULL = 185    # at or above this the sclera is fully re-tinted

# The catchlight must stay neutral.
KEEP_FROM = 200
KEEP_FULL = 235

SEARCH = (2300, 3600, 2900, 4096)   # window guaranteed to contain the eye island
REACH_INNER = 24                    # full tint strength out to here
REACH_OUTER = 54                    # tint fades to nothing by here (kills the edge)
BLEND = 1.0


def is_amber(r, g, b):
    """Strict: warm, strongly saturated, bright enough — iris only, not fur."""
    mx, mn = max(r, g, b), min(r, g, b)
    return mx - mn > 68 and r > 118 and r >= g >= b and (r - b) > 78


def find_iris(px, w, x0, y0, x1, y1, nch):
    mask = bytearray(w * (y1 - y0))
    mw = x1 - x0
    minx, miny, maxx, maxy = w, y1, -1, -1
    count = 0
    for y in range(y0, y1):
        base = y * w * nch
        row = (y - y0) * mw
        for x in range(x0, x1):
            i = base + x * nch
            if is_amber(px[i], px[i + 1], px[i + 2]):
                mask[row + (x - x0)] = 1
                count += 1
                if x < minx:
                    minx = x
                if x > maxx:
                    maxx = x
                if y < miny:
                    miny = y
                if y > maxy:
                    maxy = y
    return mask, mw, (minx, miny, maxx, maxy), count


def grow(mask, mw, mh, steps):
    """Iterative 4-neighbour dilation.

    Returns (zone, dist) where dist records the step at which each pixel was
    first reached — the tint strength is faded by that distance so the treated
    area does not end in a visible hard ring around the iris.
    """
    dist = bytearray(mask)
    cur = bytearray(mask)
    for step in range(1, steps + 1):
        nxt = bytearray(cur)
        for y in range(mh):
            row = y * mw
            up = row - mw if y > 0 else None
            dn = row + mw if y < mh - 1 else None
            for x in range(mw):
                if cur[row + x]:
                    continue
                if (x > 0 and cur[row + x - 1]) or (x < mw - 1 and cur[row + x + 1]) \
                   or (up is not None and cur[up + x]) or (dn is not None and cur[dn + x]):
                    nxt[row + x] = 1
                    dist[row + x] = step
        cur = nxt
    return cur, dist


def main():
    src, dst = sys.argv[1], sys.argv[2]
    preview_dir = sys.argv[3] if len(sys.argv) > 3 else None

    w, h, nch, px = read_png(src)
    px = bytearray(px)

    sx0, sy0, sx1, sy1 = SEARCH
    sx1, sy1 = min(sx1, w), min(sy1, h)
    mask, mw, bbox, amber_count = find_iris(px, w, sx0, sy0, sx1, sy1, nch)
    minx, miny, maxx, maxy = bbox
    if maxx < 0:
        print("no iris found in search window", file=sys.stderr)
        return 1
    print("iris mask: %d px, bbox x=%d..%d y=%d..%d"
          % (amber_count, minx, maxx, miny, maxy), file=sys.stderr)

    mh = sy1 - sy0
    zone, dist = grow(mask, mw, mh, REACH_OUTER)
    print("eye zone out to %dpx: %d px" % (REACH_OUTER, sum(zone)), file=sys.stderr)

    dir_r = TARGET[0] / float(TARGET_LUM)
    dir_g = TARGET[1] / float(TARGET_LUM)
    dir_b = TARGET[2] / float(TARGET_LUM)

    changed = 0
    for y in range(sy0, sy1):
        base = y * w * nch
        row = (y - sy0) * mw
        for x in range(sx0, sx1):
            if not zone[row + (x - sx0)] or mask[row + (x - sx0)]:
                continue
            d = dist[row + (x - sx0)]
            if d >= REACH_OUTER:
                continue
            fade = 1.0 if d <= REACH_INNER else \
                (REACH_OUTER - d) / float(REACH_OUTER - REACH_INNER)
            i = base + x * nch
            r, g, b = px[i], px[i + 1], px[i + 2]
            if max(r, g, b) - min(r, g, b) > SAT_MAX:
                continue
            lum = (r + g + b) / 3.0
            if lum < LUM_FLOOR:
                continue

            t = (lum - LUM_FLOOR) / float(LUM_FULL - LUM_FLOOR)
            t = t if t < 1.0 else 1.0
            if lum > KEEP_FROM:
                t *= max(0.0, (KEEP_FULL - lum) / float(KEEP_FULL - KEEP_FROM))
            t *= fade * BLEND
            if t <= 0.01:
                continue

            le = lum if lum < TARGET_LUM else TARGET_LUM
            tr = min(255.0, le * dir_r)
            tg = min(255.0, le * dir_g)
            tb = min(255.0, le * dir_b)

            px[i] = int(r + (tr - r) * t + 0.5)
            px[i + 1] = int(g + (tg - g) * t + 0.5)
            px[i + 2] = int(b + (tb - b) * t + 0.5)
            changed += 1

    print("re-tinted pixels: %d" % changed, file=sys.stderr)
    write_png(dst, w, h, nch, bytes(px))

    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        cx, cy = (minx + maxx) // 2, (miny + maxy) // 2
        x0, y0 = max(0, cx - 200), max(0, cy - 200)
        x1, y1 = min(w, cx + 200), min(h, cy + 200)
        cw, ch, cpx = crop(bytes(px), w, h, nch, x0, y0, x1, y1)
        dest = os.path.join(preview_dir, "eye_after.png")
        write_png(dest, cw, ch, nch, cpx)
        print("preview   : %s" % dest, file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
