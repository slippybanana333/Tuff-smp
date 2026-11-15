#!/usr/bin/env python3
"""
zones_from_map.py - finds the ISLANDS on the owner's zone-map picture (by distance: land blobs that are close together merge into one island,
blobs separated by enough water stay separate) and writes them as the "islands" list of config/shrinecraft/zones.json
(read by client/gui/ZoneDetector.java: nearest island within "island_margin" decides the GUI theme, everything else falls back to the pie slice).

    python3 tools/zones_from_map.py MAP.png --arrow 795,540 --pixel-radius 445 --blocks-radius 3000 --center-blocks 0,0
    python3 tools/zones_from_map.py MAP.png                      # no scale yet: prints the islands in PIXELS and writes the check picture only

  --arrow X,Y          pixel of the circle's centre (the red arrow / spawn)                       (default 795,540 = the owner's picture)
  --pixel-radius R     radius of the whole circle in pixels                                       (default 445)
  --blocks-radius B    how many blocks that radius is in the game  (gives blocks per pixel = B / R)
  --center-blocks X,Z  game x,z of the arrow                                                      (default 0,0)
  --start-angle A      compass degrees where the first slice (sculk) begins                       (default 324 = measured from the picture)
  --merge D            land blobs closer than D pixels count as ONE island                        (default 12)
  --hub-pixels N       ignore blobs whose centre is within N pixels of the arrow (map clutter next to the hub)   (default 45)
  --min-area N         ignore blobs smaller than N pixels (specks, ice flowers ...)               (default 60)
  --out FILE           write the zones.json islands into FILE (merged into the existing file; created if missing)
Always writes MAP_islands.png next to the picture: every detected island circled, so you can check it by eye.

How land is told from water (per slice, from the colours in the owner's picture; the sculk slice is underground, so nothing is detected there):
  end   = pale olive stone on black      ocean = green on dark blue-teal      arctic = pale grey-white on dark navy      lava = dark red-brown on orange
"""
import argparse
import json
import math
import os

import numpy as np
from PIL import Image, ImageDraw

THEMES = ["sculk", "ocean", "arctic", "lava", "end"]      # clockwise from start_angle, 72 degrees each


def land_mask(rgb, theme):
    r, g, b = rgb[..., 0].astype(int), rgb[..., 1].astype(int), rgb[..., 2].astype(int)
    s = r + g + b
    if theme == "end":
        return s > 150
    if theme == "arctic":
        return s > 200
    if theme == "ocean":
        return (g > b + 6) & (g > r + 6) & (s > 40)
    if theme == "lava":
        return (s > 25) & (s < 200) & (r >= b)
    return np.zeros(r.shape, dtype=bool)


def components(mask):
    """4-connected components of a boolean array -> label array + count (plain flood fill, no scipy)."""
    h, w = mask.shape
    lab = np.zeros((h, w), dtype=np.int32)
    n = 0
    for y in range(h):
        for x in range(w):
            if mask[y, x] and lab[y, x] == 0:
                n += 1
                stack = [(y, x)]
                lab[y, x] = n
                while stack:
                    cy, cx = stack.pop()
                    for ny, nx in ((cy + 1, cx), (cy - 1, cx), (cy, cx + 1), (cy, cx - 1)):
                        if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and lab[ny, nx] == 0:
                            lab[ny, nx] = n
                            stack.append((ny, nx))
    return lab, n


def dilate(mask, r):
    out = mask.copy()
    for _ in range(r):
        m = out.copy()
        m[1:, :] |= out[:-1, :]
        m[:-1, :] |= out[1:, :]
        m[:, 1:] |= out[:, :-1]
        m[:, :-1] |= out[:, 1:]
        out = m
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("--arrow", default="795,540")
    ap.add_argument("--pixel-radius", type=float, default=445)
    ap.add_argument("--blocks-radius", type=float, default=0)
    ap.add_argument("--center-blocks", default="0,0")
    ap.add_argument("--start-angle", type=float, default=324)
    ap.add_argument("--merge", type=int, default=12)
    ap.add_argument("--min-area", type=int, default=60)
    ap.add_argument("--hub-pixels", type=float, default=45)
    ap.add_argument("--out", default="")
    a = ap.parse_args()
    ax, ay = [float(v) for v in a.arrow.split(",")]
    cbx, cbz = [float(v) for v in a.center_blocks.split(",")]
    img = Image.open(a.image).convert("RGB")
    rgb = np.array(img)
    h, w = rgb.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w]
    dx, dy = xx - ax, yy - ay
    dist = np.hypot(dx, dy)
    ang = (np.degrees(np.arctan2(dx, -dy)) + 360) % 360          # compass: 0 = up/north, clockwise
    zone = (((ang - a.start_angle) % 360) // 72).astype(int)
    inside = dist < a.pixel_radius
    # work at 1/3 resolution (fast, and tiny gaps do not matter for islands)
    f = 3
    found = []
    for zi, theme in enumerate(THEMES):
        m = land_mask(rgb, theme) & inside & (zone == zi) & (dist > 12)
        small = m[: h // f * f, : w // f * f].reshape(h // f, f, w // f, f).mean(axis=(1, 3)) > 0.4
        merged = dilate(small, max(1, a.merge // f // 2))
        lab, n = components(merged)
        for k in range(1, n + 1):
            blob = (lab == k) & small                              # the real land inside the merged blob
            area = int(blob.sum()) * f * f
            if area < a.min_area:
                continue
            ys, xs = np.nonzero(blob)
            cx, cy = xs.mean() * f + f / 2, ys.mean() * f + f / 2
            if math.hypot(cx - ax, cy - ay) < a.hub_pixels:
                continue
            rad = max(math.sqrt(area / math.pi) * 1.15, float(np.hypot(xs - xs.mean(), ys - ys.mean()).max()) * f * 0.7)
            found.append({"theme": theme, "px": cx, "py": cy, "pr": rad, "area": area})
    found.sort(key=lambda i: (THEMES.index(i["theme"]), -i["area"]))
    scale = (a.blocks_radius / a.pixel_radius) if a.blocks_radius > 0 else 0
    islands = []
    for n, i in enumerate(found, 1):
        wx = cbx + (i["px"] - ax) * scale
        wz = cbz + (i["py"] - ay) * scale
        islands.append({"name": f"{i['theme']}-{n}", "theme": i["theme"], "x": round(wx), "z": round(wz), "radius": max(20, round(i["pr"] * scale))})
    # check picture
    dr = ImageDraw.Draw(img)
    cols = {"ocean": (80, 200, 255), "arctic": (255, 255, 255), "lava": (255, 160, 40), "end": (255, 240, 120), "sculk": (60, 220, 200)}
    for n, i in enumerate(found, 1):
        c = cols[i["theme"]]
        dr.ellipse([i["px"] - i["pr"], i["py"] - i["pr"], i["px"] + i["pr"], i["py"] + i["pr"]], outline=c, width=3)
        dr.text((i["px"] - 6, i["py"] - 5), str(n), fill=c)
    dr.ellipse([ax - a.pixel_radius, ay - a.pixel_radius, ax + a.pixel_radius, ay + a.pixel_radius], outline=(120, 120, 120), width=1)
    out_png = os.path.splitext(a.image)[0] + "_islands.png"
    img.save(out_png)
    per = {t: sum(1 for i in found if i["theme"] == t) for t in THEMES}
    print("islands found:", len(found), per)
    if scale:
        print("scale: %.3f blocks per pixel (circle radius %.0f blocks)" % (scale, a.blocks_radius))
        for i in islands:
            print("  %-10s %-7s x %6d  z %6d  radius %5d" % (i["name"], i["theme"], i["x"], i["z"], i["radius"]))
    else:
        print("no --blocks-radius given: showing pixel positions only")
        for n, i in enumerate(found, 1):
            print("  %2d %-7s at pixel %4d,%4d  radius %3d px  area %6d px" % (n, i["theme"], i["px"], i["py"], i["pr"], i["area"]))
    print("check picture:", out_png)
    if a.out and scale:
        cfg = {}
        if os.path.exists(a.out):
            with open(a.out, encoding="utf-8") as fh:
                cfg = json.load(fh)
        cfg["islands"] = islands
        cfg.setdefault("island_margin", 40)
        with open(a.out, "w", encoding="utf-8") as fh:
            json.dump(cfg, fh, indent=2)
        print("wrote", len(islands), "islands into", a.out)


if __name__ == "__main__":
    main()
