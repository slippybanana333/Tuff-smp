#!/usr/bin/env python3
"""
zones_from_drawing.py - turns the owner's hand-drawn zone borders (red lines drawn over the zone map) into a ZONE MAP PICTURE the mod reads
(config/shrinecraft/zones_mask.png, see client/gui/ZoneDetector.java "map").

    python3 tools/zones_from_drawing.py ORIGINAL_MAP.png DRAWING.png --out zones_mask.png [--blocks-per-pixel 6.5]

Owner: "follow the black lines that my lines are referencing". So the red lines only say WHICH area belongs to which zone; the border is then
snapped to the middle of the real black gap between the two zones' content on the original map:
  1. the drawing is a zoomed / cropped copy of the original map, so it is registered to the original first (similarity transform, constants below,
     measured from the island circles that appear in both pictures),
  2. the red lines are closed and the areas between them are flood-filled: sculk (top wedge), ocean, arctic, lava, end,
  3. in a band around every border between two zones, each pixel goes to the zone whose real content (land, base colour) is NEAREST - so a border
     sits in the middle of the black line between the zones, wherever the red line was a little off,
  4. everything outside the owner's outer red contour is "none" (vanilla look).
The mask is saved at 1/2 resolution (RGBA: theme colour per pixel, alpha 0 = none). Always also writes a preview (mask over the original).
Mod side: zones.json  "map": {"image": "zones_mask.png", "center_pixel": [x, y], "blocks_per_pixel": S}  (centre_pixel is in mask pixels; the mod maps
world x,z -> pixel with the existing "center" = the world position of that pixel).
"""
import argparse
import collections
import os

import numpy as np
from PIL import Image

# registration of the drawing onto the original map (x' = S*x + TX, y' = S*y + TY), fitted on three island circles seen in both pictures
S, TX, TY = 0.784, -114.7, -10.9
# the arrow (world centre) in ORIGINAL map pixels
ARROW = (795, 540)
# seed point of every zone in ORIGINAL pixels (inside the zone, clear of red lines)
SEEDS = {"sculk": (795, 290), "ocean": (1031, 454), "arctic": (979, 733), "lava": (613, 735), "end": (539, 427)}
THEME_RGB = {"sculk": (30, 140, 140), "ocean": (46, 125, 181), "arctic": (207, 227, 242), "lava": (224, 112, 26), "end": (201, 187, 122)}
ORDER = ["sculk", "ocean", "arctic", "lava", "end"]


def dilate(m, r):
    out = m.copy()
    for _ in range(r):
        n = out.copy()
        n[1:, :] |= out[:-1, :]
        n[:-1, :] |= out[1:, :]
        n[:, 1:] |= out[:, :-1]
        n[:, :-1] |= out[:, 1:]
        out = n
    return out


def flood(free, seed):
    h, w = free.shape
    lab = np.zeros((h, w), bool)
    sx, sy = seed
    if not free[sy, sx]:
        raise SystemExit("seed %s is on a red line - move it" % (seed,))
    q = collections.deque([(sy, sx)])
    lab[sy, sx] = True
    while q:
        y, x = q.popleft()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and free[ny, nx] and not lab[ny, nx]:
                lab[ny, nx] = True
                q.append((ny, nx))
    return lab


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("original")
    ap.add_argument("drawing")
    ap.add_argument("--out", default="zones_mask.png")
    ap.add_argument("--band", type=int, default=16, help="half-width (in half-resolution pixels) of the band around a border that is re-decided by nearest content")
    a = ap.parse_args()
    orig = np.array(Image.open(a.original).convert("RGB")).astype(int)
    draw = np.array(Image.open(a.drawing).convert("RGB")).astype(int)
    H, W = orig.shape[:2]
    # work at half resolution
    f = 2
    h, w = H // f, W // f
    # 1. red lines of the drawing -> original -> half resolution
    red = (draw[..., 0] > 170) & (draw[..., 1] < 70) & (draw[..., 2] < 70)
    # drop stray red specks (a dot off the end of a line would drag the closing chord away from the lines): keep only big connected red parts
    seen = np.zeros(red.shape, bool)
    keep = np.zeros(red.shape, bool)
    for y0_, x0_ in zip(*np.nonzero(red)):
        if seen[y0_, x0_]:
            continue
        comp, st = [], [(y0_, x0_)]
        seen[y0_, x0_] = True
        while st:
            cy_, cx_ = st.pop()
            comp.append((cy_, cx_))
            for dy_ in (-1, 0, 1):
                for dx_ in (-1, 0, 1):
                    ny_, nx_ = cy_ + dy_, cx_ + dx_
                    if 0 <= ny_ < red.shape[0] and 0 <= nx_ < red.shape[1] and red[ny_, nx_] and not seen[ny_, nx_]:
                        seen[ny_, nx_] = True
                        st.append((ny_, nx_))
        if len(comp) >= 25:
            for cy_, cx_ in comp:
                keep[cy_, cx_] = True
    red = keep
    ys, xs = np.nonzero(red)
    ox = ((xs - TX) / S / f).round().astype(int)
    oy = ((ys - TY) / S / f).round().astype(int)
    lines = np.zeros((h, w), bool)
    ok = (ox >= 0) & (ox < w) & (oy >= 0) & (oy < h)
    lines[oy[ok], ox[ok]] = True
    # the outer red contour is often open where the drawing runs off the edge of the screenshot: close it with the convex hull edges of the
    # red points that lie in the bottom part (a straight chord between the two loose ends); the rest of the contour is left as drawn
    pts = sorted(set(zip(ox[ok].tolist(), oy[ok].tolist())))

    def cross(o, p, q):
        return (p[0] - o[0]) * (q[1] - o[1]) - (p[1] - o[1]) * (q[0] - o[0])
    lower, upper = [], []
    for p_ in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p_) <= 0:
            lower.pop()
        lower.append(p_)
    for p_ in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p_) <= 0:
            upper.pop()
        upper.append(p_)
    hull = lower[:-1] + upper[:-1]
    ymax = max(p_[1] for p_ in hull)
    for (x_a, y_a), (x_b, y_b) in zip(hull, hull[1:] + hull[:1]):
        if y_a > ymax - 40 and y_b > ymax - 40:           # only the hull edges along the bottom
            n = int(max(abs(x_b - x_a), abs(y_b - y_a))) + 1
            for t in range(n + 1):
                x_ = int(round(x_a + (x_b - x_a) * t / n))
                y_ = int(round(y_a + (y_b - y_a) * t / n))
                if 0 <= x_ < w and 0 <= y_ < h:
                    lines[y_, x_] = True
    base_lines = lines
    # close the small gaps of a hand drawing: thicken the lines just enough that the OUTSIDE (flooded from the picture corner) cannot reach any zone seed
    for thick in range(3, 14):
        lines = dilate(base_lines, thick)
        outside = flood(~lines, (2, 2))
        if not any(outside[SEEDS[z][1] // f, SEEDS[z][0] // f] for z in ORDER):
            break
    if os.environ.get("DEBUG_DIR"):
        print("line thickness used (half-res px):", thick)
    free = ~lines
    if os.environ.get("DEBUG_DIR"):
        Image.fromarray((lines * 255).astype("uint8")).save(os.path.join(os.environ["DEBUG_DIR"], "debug_lines.png"))
    # 2. flood fill the five areas
    lab = np.zeros((h, w), np.int8)
    for i, z in enumerate(ORDER, 1):
        sx, sy = SEEDS[z][0] // f, SEEDS[z][1] // f
        region = flood(free, (sx, sy))
        lab[region & (lab == 0)] = i
    if os.environ.get("DEBUG_DIR"):
        print("after flood:", {z: int((lab == i).sum()) for i, z in enumerate(ORDER, 1)})
    # the red line pixels themselves belong to the nearest area (grow each label into the lines)
    grow = lines.copy()
    q = collections.deque()
    ys, xs = np.nonzero(lab > 0)
    # only pixels touching a line start the growth
    edge = dilate(lines, 1) & (lab > 0)
    for y, x in zip(*np.nonzero(edge)):
        q.append((y, x))
    while q:
        y, x = q.popleft()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and grow[ny, nx] and lab[ny, nx] == 0:
                lab[ny, nx] = lab[y, x]
                q.append((ny, nx))
    # the region outside the owner's outer contour stays 0 only if it was not reached: the lines of the outer contour were grown above, so
    # shrink them back by removing label pixels that were line pixels of the outer contour's outer half (they are at most 3 px wide: harmless)
    # 3. snap the borders to the real black gaps: content masks
    small = orig[: h * f, : w * f].reshape(h, f, w, f, 3).mean(axis=(1, 3))
    s = small.sum(axis=2)
    sculk_grey = (np.abs(small[..., 0] - 36) < 9) & (np.abs(small[..., 1] - 38) < 9) & (np.abs(small[..., 2] - 39) < 9)
    content = np.zeros((h, w), bool)
    for i, z in enumerate(ORDER, 1):
        m = (lab == i) & ((sculk_grey) if z == "sculk" else ((s > 60) & ~sculk_grey))
        content |= m
    # band = pixels whose neighbourhood contains 2+ different zones
    r = a.band
    big = np.where(lab > 0, lab, 0).astype(np.int16)
    mx = big.copy()
    mn = np.where(big > 0, big, 99).astype(np.int16)
    for dy in range(-r, r + 1, 4):
        for dx in range(-r, r + 1, 4):
            sh = np.roll(np.roll(big, dy, 0), dx, 1)
            mx = np.maximum(mx, sh)
            mn = np.minimum(mn, np.where(sh > 0, sh, 99))
    band = (lab > 0) & (mx != mn) & (mn != 99)
    # nearest-content propagation inside the band (multi-source BFS from content pixels that touch the band)
    new = lab.copy()
    done = np.zeros((h, w), bool)
    q = collections.deque()
    for y, x in zip(*np.nonzero(content & dilate(band, 1))):
        done[y, x] = True
        q.append((y, x, lab[y, x]))
    while q:
        y, x, l = q.popleft()
        for ny, nx in ((y + 1, x), (y - 1, x), (y, x + 1), (y, x - 1)):
            if 0 <= ny < h and 0 <= nx < w and band[ny, nx] and not done[ny, nx]:
                done[ny, nx] = True
                new[ny, nx] = l
                q.append((ny, nx, l))
    lab = new
    if os.environ.get("DEBUG_DIR"):
        print("after snapping:", {z: int((lab == i).sum()) for i, z in enumerate(ORDER, 1)}, "band px", int(band.sum()), "content px", int(content.sum()))
    # 4. write the mask (cropped to the owner's outer contour) + preview
    ys, xs = np.nonzero(lab > 0)
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    crop = lab[y0:y1, x0:x1]
    rgba = np.zeros((y1 - y0, x1 - x0, 4), np.uint8)
    for i, z in enumerate(ORDER, 1):
        rgba[crop == i] = THEME_RGB[z] + (255,)
    Image.fromarray(rgba, "RGBA").save(a.out)
    cx, cy = ARROW[0] // f - x0, ARROW[1] // f - y0
    prev = np.array(Image.fromarray(orig.astype("uint8")).resize((w, h)))[y0:y1, x0:x1].astype(float)
    over = prev * 0.55 + rgba[..., :3].astype(float) * 0.45 * (rgba[..., 3:4] > 0)
    over[(rgba[..., 3] == 0)] = prev[(rgba[..., 3] == 0)] * 0.5
    Image.fromarray(np.clip(over, 0, 255).astype("uint8")).resize(((x1 - x0) * 2, (y1 - y0) * 2)).save(os.path.splitext(a.out)[0] + "_preview.png")
    area = {z: int((crop == i).sum()) for i, z in enumerate(ORDER, 1)}
    print("mask", a.out, (x1 - x0), "x", (y1 - y0), "pixels (each = 2 original map pixels); centre pixel", (cx, cy))
    print("area per zone (mask pixels):", area)
    print('zones.json "map":', {"image": os.path.basename(a.out), "center_pixel": [int(cx), int(cy)], "blocks_per_pixel": "<set from the map scale x2>"})


if __name__ == "__main__":
    main()
