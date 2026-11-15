#!/usr/bin/env python3
"""
sprite_extrude.py - turns the owner's 32x32 item icons (tools/reference/icons/*.png) into full 3D item models.

    python3 tools/sprite_extrude.py            # rebuild every sprite-based model + its texture
    python3 tools/sprite_extrude.py cloud_lance frost_longbow

HOW IT WORKS (plain English)
  * The icon itself IS the model's texture (assets/shrinecraft/textures/item/<name>.png), so the 3D item has exactly the colours,
    patterns and details of the picture.
  * Every opaque pixel of the icon becomes a little column of 3D. How thick the column is depends on how far the pixel is from the
    icon's outline (a rounded "pillow" profile), so edges are thin and the middle of a blade / chestplate / orb is thick and round.
  * Neighbouring pixels of the same thickness are merged into boxes, so a model is a few hundred boxes instead of thousands.
  * FRONT = the icon exactly.  BACK = its own designed view (the icon re-lit darker, see back_view()).  RIM / SIDES (top, bottom, left,
    right of every step of the form) are NOT stretched edge pixels: each gets its own small texture strip made from the icon's materials
    (dark outline where it meets the front/back, the real inner colours/patterns toward the middle of the thickness), so every side of the
    item has a design that continues around the form.  All of it is packed into one texture atlas next to the icon.
  * Display transforms are the VANILLA ones (handheld tools, bow, crossbow, generated) - so every item is held exactly like the
    vanilla item it is based on (sword, axe, pickaxe, spear, bow, crossbow).  Armor pieces and the orb use the vanilla "generated" set.
  * Draw / charge states of the bow and cannon are the same icon with a drawn string + arrow painted on (see draw_bow / draw_bolt).
  * The orb has three icons (stored / released / cooldown).

To change a look: edit the icon png (tools/reference/icons) and run this script, then tools/rebuild_all.py.
To change thickness: edit CONFIG below (Dref = how many pixels in the rounded edge reaches full thickness, hmax = half thickness in model
units at the thickest point, size = how many model units the icon spans; 16 = one block).
HAND-BUILT instead (not made here): Sculk Mace, Molten Greataxe, Cutlass of Doom, Frost Longbow, Cloud Lance, Harbinger of Death (see modelspec.py).
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa

ICON_DIR = os.path.join(REF_DIR, "icons")
Q = 0.25            # thickness is rounded to this many model units (fewer distinct levels = fewer boxes)

# ---- vanilla display transforms, copied from the 1.21.11 jar (assets/minecraft/models/item/handheld.json, bow.json, crossbow.json, generated.json) ----
V_HANDHELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
V_BOW = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 40], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
V_CROSSBOW = {
    "thirdperson_righthand": {"rotation": [-90, 0, -60], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-90, 0, 30], "translation": [2, 0.1, -3], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [-90, 0, -55], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [-90, 0, 35], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
V_GENERATED = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
}
HAND = {"handheld": V_HANDHELD, "bow": V_BOW, "crossbow": V_CROSSBOW, "generated": V_GENERATED}

# ---- one entry per model.  kind = what it is (used by the checks); hand = which vanilla hand pose; size = model units the icon spans;
#      Dref/h0/hmax = rounded thickness profile (see top of file) ----
# Thickness numbers are HALF thickness in model units (total = 2x).  Vanilla items are 1 unit thick in total; ours are a little deeper but
# stay slim and smooth.  relief = how much the icon's own light/dark areas push the surface in/out (gives the sculpted, non-flat look).
TOOL = dict(Dref=2.2, h0=0.22, hmax=0.62, size=16.5, relief=0.16, q=0.125)
WEAR = dict(Dref=7.0, h0=0.4, hmax=1.5, size=16, relief=0.18, q=0.125)
CONFIG = {
    # (the four shrine_* armor ITEMS are flat 2D sprites now - owner request - so they are NOT extruded here any more; see tools/model_materials.json "flat")
    "strange_cape":            dict(WEAR, icon="strange_cape", kind="cape", hand="generated", Dref=6.0, hmax=0.8),
    "magic_essence":           dict(WEAR, icon="magic_essence", kind="orb", hand="generated", Dref=5.0, hmax=1.6, size=18),
}


# ====================================================================================================================
#  variant icons: draw the string + arrow / the loaded bolt onto a copy of the icon
# ====================================================================================================================
def put(img, c, r, color):
    if 0 <= c < 32 and 0 <= r < 32:
        img[r, c] = color


def line(img, p0, p1, color):
    (c0, r0), (c1, r1) = [(int(round(p[0])), int(round(p[1]))) for p in (p0, p1)]
    n = max(abs(c1 - c0), abs(r1 - r0), 1)
    for i in range(n + 1):
        put(img, int(round(c0 + (c1 - c0) * i / n)), int(round(r0 + (r1 - r0) * i / n)), color)


def draw_bow(img, pull):
    """The bow icon runs bottom-left (tip A) to top-right (tip B), arc on the upper-left, string on the lower-right (same as the vanilla bow).
    Remove the straight string and draw it pulled back through the arrow's nock instead."""
    a = img[..., 3] > 0
    rr, cc = np.nonzero(a)
    k = (rr - cc)
    A = np.array([cc[k.argmax()], rr[k.argmax()]], float)    # bottom-left extreme
    B = np.array([cc[k.argmin()], rr[k.argmin()]], float)    # top-right extreme
    d = (B - A) / np.linalg.norm(B - A)
    # erase the old string: light pixels lying on the chord A-B
    for r, c in zip(rr, cc):
        p = np.array([c, r], float)
        t = np.dot(p - A, d)
        v = p - A
        dist = abs(d[0] * v[1] - d[1] * v[0])
        lum = img[r, c, :3].astype(int).sum() / 3
        if dist <= 1.3 and 4 < t < np.linalg.norm(B - A) - 4 and lum > 170:
            img[r, c, 3] = 0
    M = (A + B) / 2
    perp = np.array([d[1] * -1, d[0]])                      # rotate d by 90 deg
    if perp[0] < 0:
        perp = -perp                                         # pointing to the lower-right (toward the archer)
    N = M + perp * [1.5, 4.0, 7.0][pull]
    a2, b2 = A + d * 2.5, B - d * 2.5
    col = np.array([225, 240, 255, 255], np.uint8)
    line(img, a2, N, col)
    line(img, N, b2, col)
    fwd = -perp                                              # arrow flies toward the upper-left
    tip = N + fwd * 17
    line(img, N, tip, np.array([232, 238, 246, 255], np.uint8))
    for s in (-1, 0, 1):
        o = np.array([-fwd[1], fwd[0]]) * s * 1.0
        put(img, int(round((N + fwd * 1)[0] + o[0])), int(round((N + fwd * 1)[1] + o[1])), np.array([255, 255, 255, 255], np.uint8))
    line(img, tip - fwd * 3, tip, np.array([74, 168, 255, 255], np.uint8))
    put(img, int(round(tip[0])), int(round(tip[1])), np.array([20, 60, 140, 255], np.uint8))


def draw_bolt(img, stage):
    """Sculk Cannon: stage 0 = nothing, 1/2 = bolt sliding in, 3 = fully loaded (the icon's body runs bottom-left to top-right)."""
    if stage == 0:
        return
    length = {1: 10, 2: 17, 3: 21}[stage]
    start = np.array([8.0, 23.0])
    d = np.array([1.0, -1.0]) / math.sqrt(2)
    end = start + d * length
    line(img, start, end, np.array([214, 250, 246, 255], np.uint8))
    line(img, start + np.array([0, 1]), end + np.array([0, 1]), np.array([60, 200, 205, 255], np.uint8))
    line(img, end - d * 3, end + d, np.array([64, 240, 255, 255], np.uint8))
    for s in (-1, 1):
        o = np.array([d[1], d[0]]) * s
        put(img, int(round((start + d * 1)[0] + o[0])), int(round((start + d * 1)[1] + o[1])), np.array([255, 255, 255, 255], np.uint8))


# ====================================================================================================================
#  extrusion
# ====================================================================================================================
def distance_to_empty(opaque):
    """Euclidean distance (in pixels) of each opaque pixel to the nearest transparent pixel or the border (edge pixels = 1)."""
    H, W = opaque.shape
    empties = np.argwhere(~opaque)
    out = np.zeros((H, W), float)
    pad = np.array([[-1, y] for y in range(-1, H + 1)] + [[W, y] for y in range(-1, H + 1)] +
                   [[x, -1] for x in range(-1, W + 1)] + [[x, H] for x in range(-1, W + 1)])
    pts = np.concatenate([empties[:, ::-1], pad]) if len(empties) else pad          # (x, y)
    for r in range(H):
        for c in range(W):
            if opaque[r, c]:
                out[r, c] = np.sqrt(((pts - np.array([c, r])) ** 2).sum(axis=1).min())
    return out


def smooth_depth(h, opaque):
    """One pass of 3x3 averaging over the opaque pixels only: turns hard steps into gentle slopes (smooth surface, fewer harsh ledges)."""
    out = h.copy()
    H, W = h.shape
    for r in range(H):
        for c in range(W):
            if not opaque[r, c]:
                continue
            tot = n = 0.0
            for dr in (-1, 0, 1):
                for dc in (-1, 0, 1):
                    rr, cc = r + dr, c + dc
                    if 0 <= rr < H and 0 <= cc < W and opaque[rr, cc]:
                        w = 2.0 if (dr == 0 and dc == 0) else 1.0
                        tot += h[rr, cc] * w
                        n += w
            out[r, c] = tot / n
    return out


def half_depth_map(opaque, cfg, img):
    q = cfg.get("q", Q)
    dist = distance_to_empty(opaque)
    t = np.clip((dist - 0.5) / cfg["Dref"], 0, 1)
    prof = np.sqrt(np.clip(1 - (1 - t) ** 2, 0, 1))
    h = cfg["h0"] + (cfg["hmax"] - cfg["h0"]) * prof
    h[~opaque] = 0
    h = smooth_depth(h, opaque)
    # sculpted relief: light pixels bulge out a little, dark pixels sink in, so the design has real surface depth (not a flat printed face)
    rel = cfg.get("relief", 0.0)
    if rel:
        l = (0.299 * img[..., 0] + 0.587 * img[..., 1] + 0.114 * img[..., 2]) / 255.0
        edge = np.clip(dist / 2.0, 0, 1)                      # keep the outline thin so the silhouette stays clean
        h = h + rel * (l - 0.5) * 2 * edge
    h = np.round(h / q) * q
    h[~opaque] = 0
    return np.maximum(h, np.where(opaque, q, 0))


def decompose(h):
    """Greedy rectangles of equal thickness. Returns (c0, r0, c1, r1, half) with exclusive c1/r1."""
    H, W = h.shape
    used = np.zeros((H, W), bool)
    rects = []
    for r in range(H):
        for c in range(W):
            if h[r, c] <= 0 or used[r, c]:
                continue
            lv = h[r, c]
            c1 = c
            while c1 + 1 < W and h[r, c1 + 1] == lv and not used[r, c1 + 1]:
                c1 += 1
            r1 = r
            while r1 + 1 < H and all(h[r1 + 1, x] == lv and not used[r1 + 1, x] for x in range(c, c1 + 1)):
                r1 += 1
            used[r:r1 + 1, c:c1 + 1] = True
            rects.append((c, r, c1 + 1, r1 + 1, float(lv)))
    return rects


STRIP_L = 6          # texels across the depth of a side strip


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def jitter(r, c, k):
    v = (r * 73856093) ^ (c * 19349663) ^ (k * 83492791)
    return ((v & 0xFFFF) / 65535.0 - 0.5)


def back_view(img):
    """The back of the item: same materials, re-lit darker with a little extra grain so it is clearly its own surface."""
    out = img.copy()
    rgb = out[..., :3].astype(float) * 0.86
    for r in range(32):
        for c in range(32):
            if out[r, c, 3]:
                rgb[r, c] *= 1.0 + 0.06 * jitter(r, c, 7)
    out[..., :3] = np.clip(rgb, 0, 255).astype(np.uint8)
    return out


def side_strip(img, opaque, side, rect):
    """Texture strip for one rim face: columns/rows follow the face, STRIP_L texels across its depth.
    Dark where it meets the front/back (continues the outline), the item's own inner colours toward the middle."""
    c0, r0, c1, r1 = rect[:4]
    if side == "up":
        rim, inward = [(r0, c) for c in range(c0, c1)], (1, 0)
    elif side == "down":
        rim, inward = [(r1 - 1, c) for c in range(c0, c1)], (-1, 0)
    elif side == "west":
        rim, inward = [(r, c0) for r in range(r0, r1)], (0, 1)
    else:
        rim, inward = [(r, c1 - 1) for r in range(r0, r1)], (0, -1)
    out = np.zeros((STRIP_L, len(rim), 3), float)
    for i, (r, c) in enumerate(rim):
        rimcol = img[r, c, :3].astype(float)
        samples = []
        rr, cc = r, c
        for _ in range(5):
            rr += inward[0]
            cc += inward[1]
            if 0 <= rr < 32 and 0 <= cc < 32 and opaque[rr, cc]:
                samples.append(img[rr, cc, :3].astype(float))
            else:
                break
        bright = [x for x in samples if lum(x) >= 45] or samples
        interior = np.mean(bright[:3], axis=0) if bright else rimcol
        outline = rimcol * 0.55 if lum(rimcol) > 60 else rimcol
        for k in range(STRIP_L):
            t = -1 + 2 * k / (STRIP_L - 1)
            w = (1 - abs(t)) ** 1.2
            col = outline * (1 - w) + interior * w
            out[k, i] = col * (1.0 + 0.10 * jitter(r, c, k))
    out = np.clip(out, 0, 255).astype(np.uint8)
    if side in ("west", "east"):
        out = np.transpose(out, (1, 0, 2))        # u runs along the depth, v along the face
    return np.dstack([out, np.full(out.shape[:2], 255, np.uint8)])


def build_model(name, cfg, img):
    opaque = img[..., 3] > 0
    h = half_depth_map(opaque, cfg, img)
    rects = decompose(h)
    P = cfg["size"] / 32.0
    X = lambda c: round(8 + (c - 16) * P, 4)
    Y = lambda r: round(8 + (16 - r) * P, 4)
    H, W = h.shape

    def hidden(rect, side):
        c0, r0, c1, r1, lv = rect
        cells = {"up": [(r0 - 1, c) for c in range(c0, c1)], "down": [(r1, c) for c in range(c0, c1)],
                 "west": [(r, c0 - 1) for r in range(r0, r1)], "east": [(r, c1) for r in range(r0, r1)]}[side]
        return all(0 <= r < H and 0 <= c < W and h[r, c] >= lv for r, c in cells)

    # ---- pass 1: collect the side strips, pack them below the icon + back view in one atlas ----
    strips = []                                   # (element index, side, array)
    visible = []
    for i, rect in enumerate(rects):
        vis = [sd for sd in ("up", "down", "west", "east") if not hidden(rect, sd)]
        visible.append(vis)
        for sd in vis:
            strips.append((i, sd, side_strip(img, opaque, sd, rect)))
    AW = 256
    placed = {}
    x = y = rowh = 0
    y = 32
    for i, sd, arr in sorted(strips, key=lambda t: -t[2].shape[0]):
        hh, ww = arr.shape[:2]
        if x + ww > AW:
            x, y, rowh = 0, y + rowh, 0
        placed[(i, sd)] = (x, y)
        x += ww
        rowh = max(rowh, hh)
    AH = y + rowh
    AH = max(32, int(math.ceil(AH / 8.0) * 8))
    atlas = np.zeros((AH, AW, 4), np.uint8)
    atlas[0:32, 0:32] = img
    atlas[0:32, 32:64] = back_view(img)
    for i, sd, arr in strips:
        px, py = placed[(i, sd)]
        atlas[py:py + arr.shape[0], px:px + arr.shape[1]] = arr
    su, sv = 16.0 / AW, 16.0 / AH

    # ---- pass 2: the boxes ----
    els = []
    for i, rect in enumerate(rects):
        c0, r0, c1, r1, lv = rect
        faces = {
            "south": {"uv": [c0 * su, r0 * sv, c1 * su, r1 * sv], "texture": "#0"},
            "north": {"uv": [(32 + c1) * su, r0 * sv, (32 + c0) * su, r1 * sv], "texture": "#0"},
        }
        for sd in visible[i]:
            px, py = placed[(i, sd)]
            arr = next(a for (j, s2, a) in strips if j == i and s2 == sd)
            hh, ww = arr.shape[:2]
            faces[sd] = {"uv": [px * su, py * sv, (px + ww) * su, (py + hh) * sv], "texture": "#0"}
        els.append({"name": "px_r%02d_c%02d" % (r0, c0), "from": [X(c0), Y(r1), round(8 - lv, 3)], "to": [X(c1), Y(r0), round(8 + lv, 3)], "faces": faces})
    model = {
        "credit": "Tuff SMP - generated from the owner's icon by tools/sprite_extrude.py (front = the icon, back + sides designed from it). Edit tools/reference/icons/%s.png and re-run it." % cfg["icon"],
        "texture_size": [AW, AH],
        "textures": {"0": "shrinecraft:item/" + name, "particle": "shrinecraft:item/" + name},
        "gui_light": "front",      # front lighting keeps the icon colours true in the inventory
        "elements": els,
        "display": display_for(cfg),
    }
    return model, atlas


def display_for(cfg):
    fit = round(16.0 / cfg["size"], 4)
    d = {k: dict(v) for k, v in HAND[cfg["hand"]].items()}
    d["gui"] = {"rotation": [14, -26, 0], "translation": [0, 0, 0], "scale": [round(fit * 0.96, 3)] * 3}
    d["ground"] = {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}
    d["fixed"] = {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [fit] * 3}
    d["on_shelf"] = {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [fit] * 3}
    d["head"] = {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [fit] * 3}
    return d


def main(names):
    meta = load_json(os.path.join(ROOT, "tools", "model_materials.json"))
    total = 0
    for name, cfg in CONFIG.items():
        if names and name not in names:
            continue
        img = np.array(Image.open(os.path.join(ICON_DIR, cfg["icon"] + ".png")).convert("RGBA"))
        assert img.shape[:2] == (32, 32), name
        if "variant" in cfg:
            kind, n = cfg["variant"]
            (draw_bow if kind == "bow" else draw_bolt)(img, n)
        model, atlas = build_model(name, cfg, img)
        save_json(model_path(name), model)
        ensure_dir(TEX_ITEM)
        Image.fromarray(atlas, "RGBA").save(os.path.join(TEX_ITEM, name + ".png"))
        meta[name] = {"texture": name, "group": name, "kind": cfg["kind"], "block": False, "default_mat": "sprite", "state": None,
                      "elements": {}, "sprite": True}
        print("%-28s %4d boxes" % (name, len(model["elements"])))
        total += len(model["elements"])
    save_json(os.path.join(ROOT, "tools", "model_materials.json"), meta)
    print("total boxes:", total)


if __name__ == "__main__":
    main(sys.argv[1:])
