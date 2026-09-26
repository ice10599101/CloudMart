# -*- coding: utf-8 -*-
"""
Reduce the visible sclera of a generated pet eye by extending the iris outward.

Image-to-3D tends to leave far more sclera than the concept art had, which reads
as a wide-eyed stare. The iris should fill most of the eye.

Three fills were tried and two failed, which is why this one is deliberately
dumb:

  * propagating colours along a BFS wavefront from the iris -> radial streaks,
    because the wavefront's parent chains are irregular;
  * continuing each angle's own boundary colour outward -> radial spokes,
    because the striations are high-contrast and get stretched;
  * measuring a per-angle iris boundary radius -> jagged edges, because that
    radius is itself noisy.

This version uses a purely radial vignette around the iris centroid, filled with
a single colour derived from the iris mean. Radial symmetry cannot produce streaks,
spokes or jagged edges.

No boundary detection is needed because the three things that must not change
exempt themselves by colour:
  * the iris is strongly saturated            -> skipped by SAT_MAX
  * the pupil is very dark                    -> skipped by DARK_MAX
  * the catchlight is near-white and neutral   -> skipped by SPEC_LUM / SPEC_SAT
The UV island's own padding is saturated too, so it also stops the fill from
creeping onto neighbouring islands.

Usage:
    python eye_iris_extend.py <baseColor.png> <out.png> [preview_dir]
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from eye_regrade import SEARCH, is_amber  # noqa: E402
from png_prep import crop, read_png, write_png  # noqa: E402

REACH_INNER = 34     # full strength out to boundary_radius + this
REACH_OUTER = 84     # faded to nothing by boundary_radius + this
DARKEN = 0.62        # how much the sclera band is darkened right next to the iris
                     # (fades to 0 with distance, so the iris gains a soft margin)

# The sclera is a BRIGHT band (~200-230); the eye socket and the fur around the
# eye sit at ~115-170. Selecting by luminance alone is enough, and unlike a
# colour replacement it cannot leak: see the module docstring.
LUM_MIN = 172
SPEC_LUM = 238       # catchlight
SPEC_SAT = 18
SAT_MAX = 46         # iris, and the island padding


def main():
    src, dst = sys.argv[1], sys.argv[2]
    preview_dir = sys.argv[3] if len(sys.argv) > 3 else None

    w, h, nch, px = read_png(src)
    px = bytearray(px)

    sx0, sy0, sx1, sy1 = SEARCH
    sx1, sy1 = min(sx1, w), min(sy1, h)

    # ---- iris centroid, mean colour, mean radius ---------------------------
    pts = []
    sumx = sumy = sumr = sumg = sumb = 0
    for y in range(sy0, sy1):
        base = y * w * nch
        for x in range(sx0, sx1):
            i = base + x * nch
            if is_amber(px[i], px[i + 1], px[i + 2]):
                pts.append((x, y))
                sumx += x
                sumy += y
                sumr += px[i]
                sumg += px[i + 1]
                sumb += px[i + 2]
    if not pts:
        print("no iris found", file=sys.stderr)
        return 1
    n = float(len(pts))
    cx, cy = sumx / n, sumy / n
    rmean = sum(math.hypot(x - cx, y - cy) for x, y in pts) / n

    inner = rmean + REACH_INNER
    outer = rmean + REACH_OUTER
    print("iris %d px, centre=(%.0f,%.0f), mean radius %.0f, mean #%02X%02X%02X"
          % (len(pts), cx, cy, rmean, sumr // int(n), sumg // int(n), sumb // int(n)),
          file=sys.stderr)
    print("sclera band dimmed %.0f%% at full strength; vignette %.0f..%.0f"
          % (DARKEN * 100, inner, outer), file=sys.stderr)

    # ---- radial vignette ---------------------------------------------------
    x0 = max(0, int(cx - outer) - 2)
    y0 = max(0, int(cy - outer) - 2)
    x1 = min(w, int(cx + outer) + 3)
    y1 = min(h, int(cy + outer) + 3)

    changed = 0
    for y in range(y0, y1):
        base = y * w * nch
        for x in range(x0, x1):
            r = math.hypot(x - cx, y - cy)
            if r > outer:
                continue
            t = 1.0 if r <= inner else (outer - r) / (outer - inner)
            if t <= 0.01:
                continue

            i = base + x * nch
            r0, g0, b0 = px[i], px[i + 1], px[i + 2]
            sat = max(r0, g0, b0) - min(r0, g0, b0)
            lum = (r0 + g0 + b0) / 3.0
            if sat > SAT_MAX or lum < LUM_MIN or (lum > SPEC_LUM and sat < SPEC_SAT):
                continue

            k = 1.0 - DARKEN * t
            px[i] = int(r0 * k + 0.5)
            px[i + 1] = int(g0 * k + 0.5)
            px[i + 2] = int(b0 * k + 0.5)
            changed += 1

    print("sclera shaded toward iris: %d px" % changed, file=sys.stderr)
    write_png(dst, w, h, nch, bytes(px))

    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
        px0, py0 = max(0, int(cx) - 200), max(0, int(cy) - 200)
        px1, py1 = min(w, int(cx) + 200), min(h, int(cy) + 200)
        cw, ch, cpx = crop(bytes(px), w, h, nch, px0, py0, px1, py1)
        dest = os.path.join(preview_dir, "eye_after_extend.png")
        write_png(dest, cw, ch, nch, cpx)
        print("preview   : %s" % dest, file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
