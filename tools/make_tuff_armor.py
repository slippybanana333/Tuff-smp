#!/usr/bin/env python3
"""
make_tuff_armor.py - the "powerful glowing tuff" knight armor (owner: sleek knight plate, purple glow, glow shines in the dark).

    python3 tools/make_tuff_armor.py        # writes armor_extras/tuff_extras.json + the four item icons, then run rebuild_all.py

Replaces the old picture-built golden armor completely (make_armor_from_picture.py is no longer used).
Every plate is a normal box in half-pixel units (fine_scale 0.5, so "2" = one Minecraft model pixel), relative to the pivot of the
body part it sits on (head 0,0,0 | body 0,0,0 | right_arm -5,2,0 | left_arm 5,2,0 | legs -1.9/+1.9,12,0 - in model pixels), Y DOWN, front = -Z.
The VANILLA armor layer underneath (inflated 1 px) is plain tuff; every plate below sits at least 1 unit outside that layer.
Materials: tuff / tuff_dark / tuff_light (veins glow), glow_purple, glow_core (always glowing) - see paintlib.py. paint_textures.py paints
tuff_extras.png (normal) and tuff_extras_glow.png (only the glowing texels, drawn full-bright in game).
Every block belongs to a `group` (an editable part in the Display Editor): groups list below with the pivot used for moving / scaling it.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "shrinecraft", "armor_extras", "tuff_extras.json")
ICONS = os.path.join(ROOT, "tools", "reference", "icons")
TEX = 512

# part pivots for the editor (model pixels, relative to the body part the group sits on)
GROUP_PIVOT = {
    "glow": (0, -7, 0), "crown": (0, -9, 0), "cracks": (0, 0, 0), "neck": (0, 0, 0),
    "core": (0, 5, 0), "back": (0, 5, 0),
    "shoulders": (0, 0, 0), "arms": (0, 6, 0),
    "belt": (0, 11, 0), "knees": (0, 8, 0),
    "trim": (0, 8, 0),
}

pieces = {"shrine_helmet": [], "shrine_chestplate": [], "shrine_leggings": [], "shrine_boots": []}
_shelf = {"x": 0, "y": 0, "h": 0}
_count = [0]


def _uv(w, h, d):
    nw, nh = 2 * (d + w), d + h
    if _shelf["x"] + nw > TEX:
        _shelf["x"], _shelf["y"], _shelf["h"] = 0, _shelf["y"] + _shelf["h"], 0
    if _shelf["y"] + nh > TEX:
        raise SystemExit("texture full")
    uv = [_shelf["x"], _shelf["y"]]
    _shelf["x"] += nw
    _shelf["h"] = max(_shelf["h"], nh)
    return uv


def box(piece, part, group, a, b, mat):
    """Box from corner a=(x0,y0,z0) to b=(x1,y1,z1) in half-pixel units. Sizes must be whole numbers (texture net); corners may be .5."""
    size = [int(round(b[i] - a[i])) for i in range(3)]
    assert all(s >= 1 for s in size) and all(abs((b[i] - a[i]) - size[i]) < 1e-6 for i in range(3)), (piece, group, a, b)
    pieces[piece].append({"name": "t%d" % _count[0], "part": part, "origin": list(a), "size": size, "uv": _uv(*size),
                          "inflate": 0.0, "material": mat, "group": group})
    _count[0] += 1


def shell(piece, part, a, b, key):
    """A glow SHELL: a box exactly the size of the vanilla armor layer of that part (a..b), drawn only in the glow pass. paint_textures paints
    glowing cracks on it, masked by the vanilla layer's own transparency, so the cracks sit ON the armor, never outside it."""
    box(piece, part, "cracks", a, b, "shell")
    pieces[piece][-1]["inflate"] = 0.25          # a hair bigger than the layer so the glow never z-fights with it
    pieces[piece][-1]["shell"] = key


def mirror(a, b):
    return (-b[0], a[1], a[2]), (-a[0], b[1], b[2])


def pair(piece, part_right, part_left, group, a, b, mat):
    """The same plate on the right part and (mirrored) on the left part."""
    box(piece, part_right, group, a, b, mat)
    ma, mb = mirror(a, b)
    box(piece, part_left, group, ma, mb, mat)


def build():
    H, C, L, B = "shrine_helmet", "shrine_chestplate", "shrine_leggings", "shrine_boots"
    # VANILLA-LIKE: the silhouette, panels and rivets are the vanilla netherite layer textures recoloured to tuff (paint_textures.py).
    # GLOW = (1) cracks: a glow "shell" per armor part, the exact size of the vanilla layer, with uneven cracks painted on it (group `cracks`,
    # nothing sticks out); (2) a few crystals set into the armor: low crown, forehead gem, chest crystal, shoulder gems, buckle / knee / toe gems.
    # Vanilla layer sizes (units): head +-10 x/z, y -18..2 | body x +-10, y -2..26, z +-6 | arm x -8..4 (right) / -4..8 (left), y -6..22, z +-6 |
    # leggings body +-9 x, -1..25 y, +-5 z; leggings legs +-5 | boots legs +-6, y -2..26.
    shell(H, "head", (-10, -18, -10), (10, 2, 10), "head")
    shell(C, "body", (-10, -2, -6), (10, 26, 6), "body")
    shell(C, "right_arm", (-8, -6, -6), (4, 22, 6), "right_arm")
    shell(C, "left_arm", (-4, -6, -6), (8, 22, 6), "left_arm")
    shell(L, "body", (-9, -1, -5), (9, 25, 5), "body_leggings")
    shell(L, "right_leg", (-5, -1, -5), (5, 25, 5), "legs_leggings")
    shell(L, "left_leg", (-5, -1, -5), (5, 25, 5), "legs_leggings")
    shell(B, "right_leg", (-6, -2, -6), (6, 26, 6), "legs")
    shell(B, "left_leg", (-6, -2, -6), (6, 26, 6), "legs")
    # ---------------------------------------------------------------------------------------- HELMET (open face: see paint_textures._tuff_layer)
    box(H, "head", "glow", (-2, -16, -11), (2, -13, -10), "glow_purple")              # forehead gem set into the band
    box(H, "head", "glow", (-1, -15, -12), (1, -14, -11), "glow_core")
    # low crystal crown on the rim of the top (about 2 pixels tall, centre spikes a bit taller)
    def spike(cx, cz, h):
        box(H, "head", "crown", (cx - 1, -18 - h, cz - 1), (cx + 1, -18, cz + 1), "glow_purple")
        box(H, "head", "crown", (cx - 0.5, -19 - h, cz - 0.5), (cx + 0.5, -18 - h, cz + 0.5), "glow_core")
    for x, h in ((-8, 2), (-4, 2), (0, 3), (4, 2), (8, 2)):
        spike(x, -9, h)
        spike(x, 9, h)
    for z, h in ((-4, 2), (0, 3), (4, 2)):
        spike(-9, z, h)
        spike(9, z, h)
    # back of the helmet: a neck guard with a ridge down the middle and two small crystals (owner: "almost no back action")
    box(H, "head", "neck", (-8, -3, 10), (8, 3, 11), "tuff_dark_plain")
    box(H, "head", "neck", (-1, -16, 10), (1, -3, 11), "tuff_light_plain")
    box(H, "head", "neck", (-4, -1, 11), (-2, 2, 12), "glow_purple")
    box(H, "head", "neck", (2, -1, 11), (4, 2, 12), "glow_purple")
    # ---------------------------------------------------------------------------------------- CHESTPLATE
    box(C, "body", "core", (-3, 3, -7), (3, 9, -6), "glow_purple")                   # chest crystal
    box(C, "body", "core", (-1, 5, -8), (1, 7, -7), "glow_core")
    # BACK: a thin back plate, two shoulder-blade panels, a ridge down the spine with three small crystals pointing back
    box(C, "body", "back", (-9, 2, 6), (9, 20, 7), "tuff_plain")
    box(C, "body", "back", (-8, 3, 7), (-2, 10, 8), "tuff_light_plain")
    box(C, "body", "back", (2, 3, 7), (8, 10, 8), "tuff_light_plain")
    box(C, "body", "back", (-1, 2, 7), (1, 19, 8), "tuff_dark_plain")
    for yy, hh in ((4, 3), (9, 4), (14, 3)):
        box(C, "body", "back", (-1, yy, 8), (1, yy + hh, 10), "glow_purple")
        box(C, "body", "back", (-0.5, yy + 1, 10), (0.5, yy + hh - 1, 11), "glow_core")
    pair(C, "right_arm", "left_arm", "shoulders", (-10, -8, -7), (2, -6, 7), "tuff_light")   # one slim shoulder cap per arm
    pair(C, "right_arm", "left_arm", "shoulders", (-7, -10, -2), (-3, -8, 2), "glow_purple")  # shoulder gem
    # ---------------------------------------------------------------------------------------- LEGGINGS
    box(L, "body", "belt", (-2, 21, -6), (2, 24, -5), "glow_purple")                 # belt buckle gem
    box(L, "body", "belt", (-1, 22, -7), (1, 23, -6), "glow_core")
    box(L, "body", "belt", (-8, 20, 5), (8, 26, 6), "tuff_plain")                    # rear tasset plate hanging at the back of the waist
    box(L, "body", "belt", (-1.5, 22, 6), (1.5, 25, 7), "glow_purple")
    box(L, "body", "belt", (-0.5, 23, 7), (0.5, 24, 8), "glow_core")
    pair(L, "right_leg", "left_leg", "knees", (-2, 15, -6), (2, 18, -5), "glow_purple")      # knee gems
    # ---------------------------------------------------------------------------------------- BOOTS
    pair(B, "right_leg", "left_leg", "trim", (-1, 22, -7), (1, 24, -6), "glow_core")
    pair(B, "right_leg", "left_leg", "trim", (-4, 17, 6), (4, 24, 7), "tuff_dark_plain")      # heel plate          # toe gem


# ------------------------------------------------------------------------------------------------------------------------------------------
#  item icons (32x32 pixel art, same tuff + purple glow look; then sprite_extrude.py turns them into the 3D item models)
# ------------------------------------------------------------------------------------------------------------------------------------------
TUFF_PAL = ["#34352f", "#46473f", "#53544b", "#62635a", "#72736a", "#8a8b80", "#a9aa9e"]
PURP = ["#3a0f6a", "#6a22c0", "#9a4dff", "#c58cff", "#efd8ff"]


def _hex(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def _icon(shapes, glows, name, cracks=()):
    """shapes: filled rectangles (tuff); glows: purple rectangles (gems); cracks: (x, y, steps) random-walk crack starts, drawn only on the tuff."""
    import random
    rnd = random.Random(name)
    mask = [[False] * 32 for _ in range(32)]
    for (x0, y0, x1, y1) in shapes:
        for y in range(y0, y1):
            for x in range(x0, x1):
                mask[y][x] = True
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            if not mask[y][x]:
                continue
            up = y == 0 or not mask[y - 1][x]
            left = x == 0 or not mask[y][x - 1]
            down = y == 31 or not mask[y + 1][x]
            right = x == 31 or not mask[y][x + 1]
            v = 3 + rnd.choice([-1, 0, 0, 0, 1]) + (1 if (x + y) % 9 == 0 else 0)
            if up or left:
                v += 1
            if down or right:
                v -= 2
            if down or right:
                v = min(v, 1)
            img.putpixel((x, y), _hex(TUFF_PAL[max(0, min(6, v))]) + (255,))
    for (cx, cy, steps) in cracks:                      # uneven glowing cracks: random walks that fade out, with a branch now and then
        x, y = cx, cy
        for k in range(steps):
            if 0 <= x < 32 and 0 <= y < 32 and mask[y][x]:
                img.putpixel((x, y), _hex(PURP[max(1, 3 - k * 3 // max(1, steps))]) + (255,))
            dx, dy = rnd.choice([(0, 1), (1, 1), (-1, 1), (1, 0), (-1, 0), (0, 1)])
            x, y = x + dx, y + dy
            if rnd.random() < 0.12 and 0 <= x < 32 and 0 <= y < 32 and mask[y][x]:
                img.putpixel((x + rnd.choice([-1, 1]), y), _hex(PURP[1]) + (255,)) if 0 <= x + 1 < 32 and mask[y][min(31, x + 1)] else None
    for (x0, y0, x1, y1, lv) in glows:
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
                img.putpixel((x, y), _hex(PURP[max(0, min(4, lv - (1 if edge and (x1 - x0 > 2 and y1 - y0 > 2) else 0)))]) + (255,))
    img.save(os.path.join(ICONS, name + ".png"))


def icons():
    # helmet (open face): band + low crystal crown + cheek guards, face empty, a few cracks
    _icon([(8, 10, 24, 14), (7, 14, 10, 24), (22, 14, 25, 24), (7, 12, 25, 14)],
          [(8, 7, 10, 10, 3), (12, 6, 14, 10, 3), (15, 5, 17, 10, 4), (18, 6, 20, 10, 3), (22, 7, 24, 10, 3), (14, 12, 18, 14, 4)], "shrine_helmet",
          cracks=[(8, 14, 6), (22, 15, 7), (24, 12, 3)])
    # chestplate: vanilla shape + glowing core, uneven cracks
    _icon([(2, 6, 11, 14), (21, 6, 30, 14), (3, 14, 9, 20), (23, 14, 29, 20), (9, 5, 23, 26), (11, 4, 21, 5)],
          [(13, 11, 19, 17, 3), (15, 13, 17, 15, 4), (4, 7, 8, 9, 2), (24, 7, 28, 9, 2)], "shrine_chestplate",
          cracks=[(12, 17, 9), (20, 17, 7), (15, 20, 5), (5, 10, 5), (26, 9, 6), (10, 6, 4)])
    # leggings: belt + two legs
    _icon([(8, 5, 24, 11), (8, 11, 15, 27), (17, 11, 24, 27)], [(14, 6, 18, 10, 3), (10, 17, 13, 20, 3), (19, 17, 22, 20, 3)], "shrine_leggings",
          cracks=[(9, 11, 9), (22, 12, 8), (14, 19, 5)])
    # boots: two boots
    _icon([(6, 9, 13, 22), (5, 22, 14, 26), (19, 9, 26, 22), (18, 22, 27, 26)], [(5, 23, 7, 25, 4), (25, 23, 27, 25, 4)], "shrine_boots",
          cracks=[(8, 10, 7), (22, 12, 6), (11, 22, 3)])


def main():
    build()
    data = {
        "_help": "GENERATED by tools/make_tuff_armor.py (do not edit by hand). Boxes in half-pixel units (fine_scale 0.5) relative to the pivot of 'part' "
                 "(head 0,0,0 | body 0,0,0 | right_arm -5,2,0 | left_arm 5,2,0 | legs -1.9/1.9,12,0 in model pixels), Y down. 'group' = editable part.",
        "fine_scale": 0.5,
        "groups": {g: {"pivot": list(p)} for g, p in GROUP_PIVOT.items()},
        "texture": "shrinecraft:textures/entity/armor_extra/tuff_extras.png",
        "glow_texture": "shrinecraft:textures/entity/armor_extra/tuff_extras_glow.png",
        "texture_size": [TEX, TEX],
        "pieces": pieces,
    }
    with open(OUT, "w") as f:
        json.dump(data, f)
    icons()
    print("tuff armor:", _count[0], "boxes", {k: len(v) for k, v in pieces.items()}, "atlas rows used up to y =", _shelf["y"] + _shelf["h"])


if __name__ == "__main__":
    main()
