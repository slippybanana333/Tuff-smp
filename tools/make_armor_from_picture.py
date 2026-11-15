#!/usr/bin/env python3
"""make_armor_from_picture.py - builds the Shrine armor DIRECTLY FROM THE OWNER'S PICTURE (tools/reference/armor_picture.png), so that from the
front it is the same picture: the picture is cut into a grid of 1x1 cells (the picture's own pixel size), every gold cell becomes a small
3D block of that cell's colour, standing at the same spot on the player (horns + crown on the head, shoulders on the arms, chest on the body,
skirt hanging from the body). The flat worn textures are left empty - everything you see is these blocks.

    python3 tools/make_armor_from_picture.py          (then python3 tools/rebuild_all.py)
Writes: assets/shrinecraft/armor_extras/tuff_extras.json and tools/reference/icons/shrine_*.png (icons cut from the same picture).
Calibration (measured on the picture): PX_PER_UNIT picture pixels = 1 player-model pixel; model y = Y0 + picture_y / PX_PER_UNIT; picture x = X0 is x = 0."""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PIC = os.path.join(ROOT, "tools", "reference", "armor_picture.png")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "shrinecraft", "armor_extras", "tuff_extras.json")
ICONS = os.path.join(ROOT, "tools", "reference", "icons")

PX_PER_UNIT = 5.4
FINE = 2                      # cells per model pixel (2 = every block is half a Minecraft pixel; needs "fine_scale" in the json)
OVERHANG = 1.0              # everything sticking out sideways beyond the body (|x| > BODY_HALF) is pulled in to this fraction (owner: "a third tighter")
BODY_HALF = 4.0
MAX_RUN = 10                  # longest run of identical cells merged into one block
X0 = 85.5
Y0 = -17.5
COLORS = 48
TEX = 512
# the dark face opening and the dark gaps under the shoulders are NOT gold but belong to the armor (picture-pixel rectangles x0, y0, x1, y1)
DARK_OK = [(66, 60, 108, 97), (47, 126, 63, 153), (108, 126, 124, 153)]


def is_gold(r, g, b):
    return r > 120 and g > 60 and (r - b > 45 or (r > 190 and g > 190))      # gold, orange-gold shadows and the near-white highlights


def in_dark(px, py):
    return any(x0 <= px <= x1 and y0 <= py <= y1 for x0, y0, x1, y1 in DARK_OK)


def squeeze_to_picture(x):
    """Model x (after tightening) -> the x in the picture it is cut from: the part beyond the body is stretched back out."""
    if abs(x) <= BODY_HALF:
        return x
    return (BODY_HALF + (abs(x) - BODY_HALF) / OVERHANG) * (1 if x > 0 else -1)


def build_cells(img):
    W, H = img.size
    px = img.load()
    cells = {}
    step = PX_PER_UNIT / FINE
    for iy in range(-17 * FINE, 26 * FINE):
        for ix in range(-17 * FINE, 17 * FINE):
            x0 = X0 + squeeze_to_picture(ix / FINE) * PX_PER_UNIT
            x1 = X0 + squeeze_to_picture((ix + 1) / FINE) * PX_PER_UNIT
            y0, y1 = (iy / FINE - Y0) * PX_PER_UNIT, ((iy + 1) / FINE - Y0) * PX_PER_UNIT
            tot = n = 0
            r = g = b = 0
            for yy in range(max(0, int(y0)), min(H, int(y1 + 0.999))):
                for xx in range(max(0, int(x0)), min(W, int(x1 + 0.999))):
                    c = px[xx, yy][:3]
                    tot += 1
                    if is_gold(*c) or in_dark(xx, yy):
                        n += 1
                        r += c[0]; g += c[1]; b += c[2]
            if tot and n / tot >= 0.5:
                cells[(ix, iy)] = (r // n, g // n, b // n)
    return cells


def boost(c, sat=1.18, val=1.04):
    """The game's light washes colours out a little; a touch more saturation puts them back where the picture has them."""
    import colorsys
    h, sv, v = colorsys.rgb_to_hsv(*(x / 255.0 for x in c))
    return tuple(min(255, int(round(x * 255))) for x in colorsys.hsv_to_rgb(h, min(1.0, sv * sat), min(1.0, v * val)))


def quantize(cells):
    cells = {k: boost(v) for k, v in cells.items()}
    keys = list(cells)
    pal = Image.new("RGB", (len(keys), 1))
    for i, k in enumerate(keys):
        pal.putpixel((i, 0), cells[k])
    q = pal.quantize(colors=COLORS, method=Image.Quantize.MEDIANCUT).convert("RGB")
    return {k: q.getpixel((i, 0)) for i, k in enumerate(keys)}


# editable parts (the editor lists these per piece): group name -> pivot (model pixels, relative to the anchor part) the scale grows from
GROUP_PIVOT = {
    "horns": (0, -8, 0), "crown": (0, -8, 0), "helmet": (0, -4, 0),
    "chest": (0, 6, 0), "shoulders": (0, 0, 0), "arms": (0, 0, 0),
    "skirt": (0, 12, 0), "hem": (0, 21, 0),
}


def classify(ix, iy):
    """(piece item id, anchor part, depth in model pixels, z of the front face, group). Cell centre in model pixels = ((ix + 0.5) / FINE, (iy + 0.5) / FINE)."""
    x, y = (ix + 0.5) / FINE, (iy + 0.5) / FINE
    if y >= 12:
        if y >= 21:
            return "shrine_boots", "body", 7, -3.6, "hem"
        return "shrine_leggings", "body", 7, -3.6, "skirt"
    if y < -4.2 or (y < 0 and abs(x) <= 4.5):
        if abs(x) > 5.2:
            return "shrine_helmet", "head", 3, -1.5, "horns"
        if y < -8.2:
            return "shrine_helmet", "head", 2, -4.8, "crown"
        return "shrine_helmet", "head", 11, -5.4, "helmet"
    if abs(x) <= 4.5:
        return "shrine_chestplate", "body", 7, -3.4, "chest"
    part = "right_arm" if x < 0 else "left_arm"
    if y < 3:
        return "shrine_chestplate", part, 9, -4.6, "shoulders"
    return "shrine_chestplate", part, 7, -3.4, "arms"


PIVOT = {"head": (0, 0), "body": (0, 0), "right_arm": (-5, 2), "left_arm": (5, 2)}


class Packer:
    """One flat-colour patch per colour, big enough for the largest block (every texel is the same colour, so every block of that colour
    can point at the same corner of it)."""
    PW, PH = 2 * (MAX_RUN + 2 * 11) + 2, 11 * FINE + 3

    def __init__(self):
        self.x = self.y = 0
        self.seen = {}

    def place(self, col):
        if col in self.seen:
            return self.seen[col]
        if self.x + self.PW > TEX:
            self.x, self.y = 0, self.y + self.PH
        if self.y + self.PH > TEX:
            raise SystemExit("texture full")
        uv = [self.x, self.y]
        self.x += self.PW
        self.seen[col] = uv
        return uv


def main():
    img = Image.open(PIC).convert("RGB")
    cells = quantize(build_cells(img))
    pack = Packer()
    pieces = {"shrine_helmet": [], "shrine_chestplate": [], "shrine_leggings": [], "shrine_boots": []}
    by_piece = {k: {} for k in pieces}
    # group the cells into runs of identical colour / part / depth along x, row by row
    rows = {}
    for (ix, iy), col in cells.items():
        piece, part, d, z0, grp = classify(ix, iy)
        rows.setdefault((iy, piece, part, d, z0, grp, col), []).append(ix)
        by_piece[piece][(ix, iy)] = col
    count = 0
    for (iy, piece, part, d, z0, grp, col), xs in sorted(rows.items()):
        xs.sort()
        runs, start = [], xs[0]
        for a, b in zip(xs, xs[1:] + [None]):
            if b is None or b != a + 1 or (a - start + 1) >= MAX_RUN:
                runs.append((start, a))
                start = b
        hexcol = "#%02x%02x%02x" % col
        px0, py0 = PIVOT[part]
        for (a, b) in runs:
            w = b - a + 1
            pieces[piece].append({"name": f"b{count}", "part": part, "origin": [a - px0 * FINE, iy - py0 * FINE, z0 * FINE], "size": [w, 1, d * FINE],
                                  "uv": pack.place(hexcol), "inflate": 0.0, "material": "flat:" + hexcol, "group": grp})
            count += 1
    data = {
        "_help": "GENERATED by tools/make_armor_from_picture.py from tools/reference/armor_picture.png (do not edit by hand). Blocks of the picture's colours, "
                 "in units of 1/fine_scale model pixels relative to the pivot of 'part' (head 0,0,0 | body 0,0,0 | right_arm -5,2,0 | left_arm 5,2,0), Y down.",
        "fine_scale": 1.0 / FINE,
        "groups": {g: {"pivot": list(p)} for g, p in GROUP_PIVOT.items()},
        "texture": "shrinecraft:textures/entity/armor_extra/tuff_extras.png",
        "texture_size": [TEX, TEX],
        "pieces": pieces,
    }
    with open(OUT, "w") as f:
        json.dump(data, f)
    print("picture armor:", count, "blocks from", len(cells), "cells", {k: len(v) for k, v in pieces.items()})
    # icons: the piece's own cells, nearest-neighbour to 32x32
    for piece, cs in by_piece.items():
        if not cs:
            continue
        xs = [k[0] for k in cs]; ys = [k[1] for k in cs]
        x0, x1, y0, y1 = min(xs), max(xs) + 1, min(ys), max(ys) + 1
        s = 30.0 / max(x1 - x0, y1 - y0)
        icon = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        for py in range(32):
            for pxx in range(32):
                cx = int((pxx - (32 - (x1 - x0) * s) / 2) / s) + x0
                cy = int((py - (32 - (y1 - y0) * s) / 2) / s) + y0
                if (cx, cy) in cs:
                    icon.putpixel((pxx, py), cs[(cx, cy)] + (255,))
        icon.save(os.path.join(ICONS, piece + ".png"))


if __name__ == "__main__":
    main()
