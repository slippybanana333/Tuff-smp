#!/usr/bin/env python3
"""
paint_textures.py  -  paints EVERY texture in the mod with pixel-art style (no smooth gradients), straight from the model JSON files.

    python3 tools/paint_textures.py              # everything
    python3 tools/paint_textures.py greataxe     # only textures whose name contains 'greataxe'

What it paints
  * item + shrine model textures  (assets/shrinecraft/textures/item|block)  - one atlas per model, 1 texel per model unit,
    each face painted according to the material of its element (tools/model_materials.json, falls back by element-name prefix)
  * worn armor layers             (textures/entity/equipment/humanoid[_leggings]/<name>.png, vanilla 64x32 armor layout)
  * the Strange Cape wing layer   (textures/entity/equipment/wings/strange_cape.png, vanilla elytra layout)
  * extra worn geometry textures  (textures/entity/armor_extra/*.png)
  * five GUI backgrounds          (textures/gui/backgrounds/{ocean,lava,sculk,arctic,end}.png)
"""
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa
import paintlib as P
import tuffpal
from paintlib import Ctx, match_material, MATERIALS

ONLY = sys.argv[1] if len(sys.argv) > 1 else None


def want(name):
    return ONLY is None or ONLY in name


# ====================================================================================================================
#  1. item / block model textures
# ====================================================================================================================

def mat3(el):
    m = element_rotation_matrix(el)
    return m


def paint_model_texture(img, covered, model, meta, painted):
    state = meta.get("state")
    table = meta["elements"]
    default_mat = meta.get("default_mat", "netherite")
    W, H = model["texture_size"]
    ctx = Ctx()
    ctx.state = state
    for el in model["elements"]:
        matname = match_material(el["name"], table, default_mat)
        fn = P.material_for(matname)
        seed = str_seed(el["name"])
        frm, to = el["from"], el["to"]
        rot = element_rotation_matrix(el)
        r3 = rot[:3, :3]
        for face, fdata in el["faces"].items():
            x0, y0, x1, y1 = face_rect_texels_model(model, fdata)
            xi0, yi0 = int(round(x0)), int(round(y0))
            w, h = int(round(x1 - x0)), int(round(y1 - y0))
            key = (el["name"], face, xi0, yi0, w, h)
            if key in painted:
                continue
            painted.add(key)
            nrm = r3 @ np.array(FACE_NORMAL[face], dtype=float)
            ny = nrm[1]
            base_shift = 1 if ny > 0.55 else (-1 if ny < -0.55 else 0)
            if base_shift == 0:
                # side faces: darken the ones turned away from the key light (east/west a little)
                base_shift = -1 if abs(nrm[0]) > abs(nrm[2]) + 0.2 and nrm[0] > 0 else 0
            ctx.n, ctx.w, ctx.h, ctx.el, ctx.seed = face, w, h, el["name"], seed
            dx = max(1e-6, abs(to[0] - frm[0]))
            dy = max(1e-6, abs(to[1] - frm[1]))
            dz = max(1e-6, abs(to[2] - frm[2]))
            for j in range(h):
                for i in range(w):
                    fu, fv = (i + 0.5) / w, (j + 0.5) / h
                    p = face_point(face, frm, to, fu, fv)
                    q = xf(rot, p)
                    ctx.lx, ctx.ly, ctx.lz = p
                    ctx.wx, ctx.wy, ctx.wz = float(q[0]), float(q[1]), float(q[2])
                    ctx.fu, ctx.fv, ctx.i, ctx.j = fu, fv, i, j
                    ctx.tx, ctx.ty, ctx.tz = (p[0] - frm[0]) / dx, (p[1] - frm[1]) / dy, (p[2] - frm[2]) / dz
                    shade = base_shift
                    if w >= 3 and h >= 3:
                        if j == 0 or i == 0:
                            shade += 1
                        elif j == h - 1 or i == w - 1:
                            shade -= 1
                    ctx.shade = max(-2, min(2, shade))
                    col = fn(ctx)
                    px, py = xi0 + i, yi0 + j
                    if 0 <= px < W and 0 <= py < H:
                        img[py, px] = col
                        covered[py, px] = True


def face_rect_texels_model(model, fdata):
    tw, th = model["texture_size"]
    u1, v1, u2, v2 = fdata["uv"]
    return u1 / 16 * tw, v1 / 16 * th, u2 / 16 * tw, v2 / 16 * th


def dilate_gutters(img, covered):
    """Copy edge colours 1px outward into the empty gutter so mip-mapping never bleeds transparent/black into faces."""
    H, W = covered.shape
    out = img.copy()
    for y in range(H):
        for x in range(W):
            if covered[y, x]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < H and covered[ny, nx] and img[ny, nx][3] > 0:
                    out[y, x] = img[ny, nx]
                    break
    return out


def paint_items_and_shrine():
    meta = load_json(os.path.join(ROOT, "tools", "model_materials.json"))
    by_texture = {}
    for name, m in meta.items():
        if m.get("sprite"):          # the icon png IS the texture (sprite_extrude.py) - never repaint it
            continue
        by_texture.setdefault((m["texture"], m["block"]), []).append(name)
    for (tex, is_block), names in sorted(by_texture.items()):
        if not want(tex):
            continue
        # all models sharing this texture share one layout; paint all of them into the one image
        first = load_json(model_path(("block/" if is_block else "") + names[0]))
        W, H = first["texture_size"]
        img = np.zeros((H, W, 4), dtype=np.uint8)
        covered = np.zeros((H, W), dtype=bool)
        painted = set()
        for nm in sorted(names):
            model = load_json(model_path(("block/" if is_block else "") + nm))
            paint_model_texture(img, covered, model, meta[nm], painted)
        img = dilate_gutters(img, covered)
        out = os.path.join(TEX_BLOCK if is_block else TEX_ITEM, tex + ".png")
        ensure_dir(os.path.dirname(out))
        Image.fromarray(img, "RGBA").save(out)
        print("painted", os.path.relpath(out, ROOT), (W, H))


# ====================================================================================================================
#  2. worn armor (vanilla armor entity boxes)
# ====================================================================================================================

class Box:
    """A vanilla entity-model cuboid (y points DOWN, the player faces -z). uv=(u,v), size=(sx,sy,sz), pos=(x,y,z) min corner."""

    def __init__(self, uv, pos, size, extra=0.0):
        self.u, self.v = uv
        self.pos, self.size, self.extra = pos, size, extra

    def rects(self):
        u, v = self.u, self.v
        sx, sy, sz = self.size
        return {
            "top":    (u + sz, v, sx, sz),               # model Direction.DOWN (smallest y = top of the body part)
            "bottom": (u + sz + sx, v, sx, sz),          # model Direction.UP
            "right":  (u, v + sz, sz, sy),               # model WEST  (x min  = the wearer's right side)
            "front":  (u + sz, v + sz, sx, sy),          # model NORTH (z min  = front)
            "left":   (u + sz + sx, v + sz, sz, sy),     # model EAST
            "back":   (u + 2 * sz + sx, v + sz, sx, sy), # model SOUTH
        }

    def point(self, face, fu, fv):
        x, y, z = self.pos
        sx, sy, sz = self.size
        e = self.extra
        x0, x1, y0, y1, z0, z1 = x - e, x + sx + e, y - e, y + sy + e, z - e, z + sz + e
        if face == "top":
            return (x0 + fu * (x1 - x0), y0, z1 - fv * (z1 - z0))
        if face == "bottom":
            return (x0 + fu * (x1 - x0), y1, z1 - fv * (z1 - z0))
        if face == "right":
            return (x0, y0 + fv * (y1 - y0), z1 - fu * (z1 - z0))
        if face == "front":
            return (x0 + fu * (x1 - x0), y0 + fv * (y1 - y0), z0)
        if face == "left":
            return (x1, y0 + fv * (y1 - y0), z0 + fu * (z1 - z0))
        if face == "back":
            return (x1 - fu * (x1 - x0), y0 + fv * (y1 - y0), z1)
        raise ValueError(face)


FACE_TO_BLOCK = {"top": "up", "bottom": "down", "front": "north", "back": "south", "right": "west", "left": "east"}


def paint_box(img, box, chooser, seed=1):
    """chooser(ctx, face_name, row, col, rows, cols) -> rgba or None (leave transparent)."""
    ctx = Ctx()
    for face, (ru, rv, rw, rh) in box.rects().items():
        ctx.n = FACE_TO_BLOCK[face]
        ctx.w, ctx.h, ctx.seed, ctx.el = rw, rh, seed, face
        for j in range(rh):
            for i in range(rw):
                fu, fv = (i + 0.5) / rw, (j + 0.5) / rh
                x, y, z = box.point(face, fu, fv)
                ctx.lx, ctx.ly, ctx.lz = x, y, z
                ctx.wx, ctx.wy, ctx.wz = x, -y, z           # wy = UP
                ctx.fu, ctx.fv, ctx.i, ctx.j = fu, fv, i, j
                ctx.tx, ctx.ty, ctx.tz = fu, 1 - fv, 0.5
                shade = 1 if face == "top" else (-1 if face == "bottom" else (-1 if face in ("left",) else 0))
                if rw >= 3 and rh >= 3 and face not in ("top", "bottom"):
                    if j == 0 or i == 0:
                        shade += 0
                    elif j == rh - 1:
                        shade -= 1
                ctx.shade = max(-2, min(2, shade))
                col = chooser(ctx, face, j, i, rh, rw)
                if col is not None and col[3] > 0:
                    img[rv + j, ru + i] = col


def new_armor_img():
    return np.zeros((32, 64, 4), dtype=np.uint8)


def save_png(img, path):
    ensure_dir(os.path.dirname(path))
    Image.fromarray(img, "RGBA").save(path)
    print("painted", os.path.relpath(path, ROOT), img.shape[1::-1])


def M(name):
    return P.material_for(name)


HEAD = lambda extra=1.0, uv=(0, 0): Box(uv, (-4, -8, -4), (8, 8, 8), extra)
BODY = lambda extra=1.0: Box((16, 16), (-4, 0, -2), (8, 12, 4), extra)
ARM = lambda extra=1.0: Box((40, 16), (-3, -2, -2), (4, 12, 4), extra)      # right arm (the left arm reuses it, mirrored)
LEG = lambda extra=0.9: Box((0, 16), (-2, 0, -2), (4, 12, 4), extra)        # right leg (left mirrored)


# --------------------------------------------------------------------------------------------------------------------
#  GOLDEN KNIGHT ARMOR - hand-placed pixel art (no noise): every face of every armor box is an ASCII map, one character per texel.
#  Palette = hue-shifted ramps (shadows go red-brown, highlights go pale yellow) + a dark steel under-layer for contrast, like
#  Minecraft Dungeons armor. To change the look, edit the maps / KN_PAL below and run rebuild_all.py.
#    gold   a b c d e f  (deep shadow -> specular)      steel  s t u v      outline 0
#    cloth  r R q        chainmail m n                   amber glow o O y   . = see-through
# --------------------------------------------------------------------------------------------------------------------
KN_PAL = {
    "0": "#160d10", "a": "#5a2b08", "b": "#94530a", "c": "#cc8410", "d": "#f0b322", "e": "#ffdc5c", "f": "#fff6c4",
    "s": "#1f1d27", "t": "#302e3b", "u": "#474555", "v": "#6b6980",
    "r": "#560e14", "R": "#86181c", "q": "#b52a24",
    "m": "#7c4808", "n": "#a4640c",                                     # lame shadow bands
    "o": "#ff6f08", "O": "#ffab28", "y": "#ffe88c",
    "i": "#b9780c", "j": "#e1a31a", "k": "#f7c93a", "l": "#ffe888",     # plate gold (shadow -> light): the owner's all-gold samurai reference
    "p": "#8a5208", "P": "#b9780c", "w": "#e1a31a",                     # cloth bands (also gold)
}


def art(maps, row0=0):
    """chooser for paint_box from ASCII face maps. 'left' defaults to the mirror image of 'right'; row0 = first box row the map covers."""
    def choose(ctx, face, row, col, rows, cols):
        m = maps.get(face)
        mirror = False
        if m is None and face == "left" and "right" in maps:
            m, mirror = maps["right"], True
        if m is None:
            return None
        r = row - row0
        if r < 0 or r >= len(m):
            return None
        line = m[r]
        c = cols - 1 - col if mirror else col
        ch = line[c] if c < len(line) else "."
        if ch == ".":
            return None
        return P.rgba(hex_rgb(KN_PAL[ch]))
    return choose


def _flat(w, h, bands=()):
    """Bright gold 'brick' plating like the reference: rows of light bricks with darker joints and glints. 'bands' rows are darker."""
    rows = []
    for y in range(h):
        line = ""
        for x in range(w):
            if y in bands:
                ch = "j"
            elif y % 3 == 2:
                ch = "j"                                   # horizontal joint
            else:
                off = (y // 3) * 2
                ch = "l" if ((x + off) % 4) < 2 else "k"    # staggered light / bright bricks
            line += ch
        rows.append(line)
    return rows


HELM = {
    "top":   _flat(8, 8),
    "front": ["jlklklkj", "kllkllkl", "jkjjjjkj", "0ssssss0", "0ssssss0", "0ssssss0", "jkjjjjkj", "klkllklk"],
    "right": _flat(8, 8, bands=(2,)),
    "back":  _flat(8, 8, bands=(2,)),
}
CUIRASS = {
    "top":   _flat(8, 4),
    "front": _flat(8, 12, bands=(0, 9)),
    "back":  _flat(8, 12, bands=(0, 9)),
    "right": _flat(4, 12, bands=(0, 9)),
    "bottom": _flat(8, 4),
}
ARMPLATE = {
    "top":   _flat(4, 4),
    "right": _flat(4, 12, bands=(3, 9)),
    "front": _flat(4, 12, bands=(3, 9)),
    "left":  _flat(4, 12, bands=(3, 9)),
    "back":  _flat(4, 12, bands=(3, 9)),
    "bottom": _flat(4, 4),
}
HIPS = {   # rows 6..9 of the body box
    "front": _flat(8, 4, bands=(0,)),
    "back":  _flat(8, 4, bands=(0,)),
    "right": _flat(4, 4, bands=(0,)),
}
LEGPLATE = {
    "front": _flat(4, 12, bands=(4, 8)),
    "right": _flat(4, 12, bands=(4, 8)),
    "left":  _flat(4, 12, bands=(4, 8)),
    "back":  _flat(4, 12, bands=(4, 8)),
    "bottom": _flat(4, 4),
}
SABATON = {   # rows 6..11 of the leg box
    "front": _flat(4, 6, bands=(0,)),
    "right": _flat(4, 6, bands=(0,)),
    "left":  _flat(4, 6, bands=(0,)),
    "back":  _flat(4, 6, bands=(0,)),
    "bottom": _flat(4, 4),
}


def _under(ctx, face, row, col, rows, cols):
    """The vanilla armor layer is plain tuff (no veins: they would be too coarse at this resolution); the plates, crystals and glow are the extras (armor_extras/tuff_extras.json)."""
    return M("tuff_plain")(ctx)


def _under_dark(ctx, face, row, col, rows, cols):
    return M("tuff_dark_plain")(ctx)


# POLISHED-TUFF NETHERITE ARMOR (owner: "ditch this whole idea, make it look like netherite with the shape and layers but the actual texturing should look
# like polished tuff"): the worn layers are the real netherite layer textures
# (tools/reference/netherite_layer_1.png = helmet/chest/boots, _2 = leggings) recoloured into the tuff palette, so the panel pattern, rivets and
# silhouette are exactly vanilla netherite; only the colours are tuff. Gaps stay transparent like vanilla.
TUFF_LAYER_PAL = ["#2a2b26", "#3b3c35", "#4b4c44", "#5c5d54", "#6e6f65", "#83847a", "#9c9d91"]
NETH_REGIONS = {      # (x0, y0, x1, y1) of each piece in the 64x32 vanilla layer sheet
    "head": (0, 0, 32, 16), "body": (16, 16, 40, 32), "arms": (40, 16, 56, 32), "legs": (0, 16, 16, 32),
}


def _tuff_layer(src_name, regions, open_face=False, seed=11):
    src = np.array(Image.open(os.path.join(ROOT, "tools", "reference", src_name)).convert("RGBA"))
    out = np.zeros_like(src)
    for r in regions:
        x0, y0, x1, y1 = NETH_REGIONS[r]
        for y in range(y0, y1):
            for x in range(x0, x1):
                px = src[y, x]
                if px[3] == 0:
                    continue
                lum = 0.3 * px[0] + 0.59 * px[1] + 0.11 * px[2]
                out[y, x] = tuffpal.polished_tuff(x, y, lum) + (255,)
    tuffpal.weather(out, seed)
    if open_face:
        # OPEN FACE: the front face of the head (uv 8,8 size 8x8) keeps the forehead band + cheek columns; the window between is transparent
        for y in range(8 + 3, 16):
            for x in range(8 + 1, 16 - 1):
                out[y, x] = (0, 0, 0, 0)
    return out


def paint_helmet(path):
    save_png(_tuff_layer("netherite_layer_1.png", ["head"], open_face=True, seed=11), path)


def paint_chestplate(path):
    save_png(_tuff_layer("netherite_layer_1.png", ["body", "arms"], seed=23), path)


def paint_leggings(path):
    save_png(_tuff_layer("netherite_layer_2.png", ["body", "legs"], seed=37), path)


def paint_boots(path):
    save_png(_tuff_layer("netherite_layer_1.png", ["legs"], seed=51), path)


def paint_cape(path):
    """Elytra wing layout. Wing box uv(22,0) size 10x20x2. The back face (south) is what you see from behind."""
    img = new_armor_img()
    cloth, dark, clasp, eye, star = M("cape_cloth"), M("cape_cloth_dark"), M("cape_clasp"), M("strange_eye"), M("star_speck")
    wing = Box((22, 0), (0, 0, 0), (10, 20, 2), 1.0)

    def f(ctx, face, row, col, rows, cols):
        if face == "front":                    # the lining against the wearer's back
            return dark(ctx)
        if face in ("top", "bottom"):
            return dark(ctx) if face == "top" else None
        if face in ("right", "left"):
            return cloth(ctx)
        # back: the visible cape. Ragged, asymmetric hem and a few cut-out slits for the "odd" look.
        hem = {0: 20, 1: 18, 2: 19, 3: 16, 4: 19, 5: 17, 6: 20, 7: 15, 8: 18, 9: 20}[col]
        if row >= hem:
            return None
        if col == 4 and row in range(8, 13):
            return None                        # a strange slit
        if (col, row) in ((7, 14), (2, 12), (8, 9), (1, 15)):
            return None                        # little tears in the cloth
        if row == hem - 1 or col in (0, cols - 1) or row == 0:
            return M("cape_clasp")(ctx)        # thin gold trim along the edges and the ragged hem
        if row in (2, 3) and col in (cols - 3, cols - 2):
            return clasp(ctx)                  # half of a clasp (the two wings meet in the middle)
        if row == 4 and col in (cols - 3, cols - 2) and False:
            return eye(ctx)
        if hash01(col, row, ctx.seed + 3) < 0.03:
            return star(ctx)
        # odd diagonal glyph lines
        if (col * 2 + row) % 11 == 0 and row > 5:
            return M("cape_clasp")(ctx)
        return cloth(ctx)

    paint_box(img, wing, f, 51)
    save_png(img, path)


# the vanilla armor boxes a glow "shell" copies (uv in the 64x32 layer sheet + size in pixels) and which layer png holds them
SHELL_SRC = {
    "head": ("humanoid", "shrine_helmet", ((0, 0), (8, 8, 8)), False),
    "body": ("humanoid", "shrine_chestplate", ((16, 16), (8, 12, 4)), False),
    "right_arm": ("humanoid", "shrine_chestplate", ((40, 16), (4, 12, 4)), False),
    "left_arm": ("humanoid", "shrine_chestplate", ((40, 16), (4, 12, 4)), True),
    "legs": ("humanoid", "shrine_boots", ((0, 16), (4, 12, 4)), False),
    "body_leggings": ("humanoid_leggings", "shrine_leggings", ((16, 16), (8, 12, 4)), False),
    "legs_leggings": ("humanoid_leggings", "shrine_leggings", ((0, 16), (4, 12, 4)), False),
}
_layer_cache = {}


# Where the lightning cracks go: (face, (x0, x1), (y0, y1)) = the face of that armor part and the zone (fractions of the face) the crack STARTS in.
# 10 cracks in total, one or more on EVERY part (both arms, both legs, both boots), each in its own zone so they are spread out and never sit
# side by side; two cracks on the same face also keep >= 3 armor pixels apart (_zigzag avoid). Owner: "the cracks are too close together, spread
# out so most places have a crack, without adding more than 50% more cracks" (there were about 7).
CRACK_PLAN = {
    "head": [("right", (0.2, 0.8), (0.0, 0.2)), ("back", (0.2, 0.8), (0.2, 0.5))],
    "body": [("front", (0.05, 0.35), (0.0, 0.25)), ("front", (0.6, 0.95), (0.5, 0.7)),
             ("back", (0.05, 0.35), (0.0, 0.25)), ("back", (0.6, 0.95), (0.45, 0.7))],
    "right_arm": [("front", (0.2, 0.8), (0.1, 0.4)), ("back", (0.2, 0.8), (0.45, 0.75))],
    "left_arm": [("front", (0.2, 0.8), (0.45, 0.75)), ("back", (0.2, 0.8), (0.1, 0.4))],
    "body_leggings": [],
    "legs_leggings": [("front", (0.2, 0.8), (0.0, 0.5)), ("back", (0.2, 0.8), (0.25, 0.55))],
    "legs": [("front", (0.2, 0.8), (0.6, 0.85)), ("back", (0.2, 0.8), (0.62, 0.85))],
}


def _zigzag(rng, w, h, zone, avoid=()):
    """An IRREGULAR lightning crack in whole vanilla-armor pixels (owner: "the branch goes symmetrical"): runs of 1-4 diagonal steps, random
    straight / sideways kinks, the drift flips only sometimes, plus (40%) one short uneven side fork. It starts inside `zone` and tries again
    (up to 30 times) until it keeps at least 3 pixels away from the pixels in `avoid`. Returns pixels."""
    (zx0, zx1), (zy0, zy1) = zone
    best = []
    for _ in range(30):
        x = min(w - 1, max(0, int(w * rng.uniform(zx0, zx1))))
        y = min(h - 1, max(0, int(h * rng.uniform(zy0, zy1))))
        drift = rng.choice([-1, 1])
        pix = []
        length = rng.randrange(max(4, h // 2), max(6, h - 1))
        while len(pix) < length and y < h:
            for _ in range(rng.choice([1, 2, 2, 3, 4])):
                if 0 <= x < w and 0 <= y < h:
                    pix.append((x, y))
                x += drift
                y += 1
            r = rng.random()
            if r < 0.35:
                y += 1                       # straight drop
            elif r < 0.6:
                x += drift                   # sideways jog
            if rng.random() < 0.6:
                drift = -drift               # the drift flips only sometimes -> uneven, not a regular zigzag
        if pix and rng.random() < 0.4:       # one short fork from the middle of the crack, going the other way and shorter
            fx, fy = pix[len(pix) // 2]
            fd = -drift if rng.random() < 0.5 else drift
            for k in range(rng.randrange(2, 4)):
                fx += fd
                fy += 1 if k % 2 == 0 or rng.random() < 0.5 else 0
                if 0 <= fx < w and 0 <= fy < h:
                    pix.append((fx, fy))
        pix = [(px, py) for (px, py) in pix if 0 <= px < w and 0 <= py < h]
        best = pix
        if all(abs(px - ax) >= 3 or abs(py - ay) >= 3 for (px, py) in pix for (ax, ay) in avoid):
            break
    return best


def _shell_masked(key, name):
    """Cracks are drawn in the vanilla armor's OWN pixel grid (so they are as chunky as the armor), only on opaque layer pixels (never over a
    gap or the open face), so they sit ON the armor. Each crack pixel is a bold lilac pixel (2x2 units of the half-pixel shell texture)."""
    import random
    folder, piece, ((u, v), (sx, sy, sz)), mirrored = SHELL_SRC[key]
    path = os.path.join(TEX_EQUIP, folder, piece + ".png")
    if path not in _layer_cache:
        _layer_cache[path] = np.array(Image.open(path).convert("RGBA"))
    layer = _layer_cache[path]
    rects = Box((u, v), (0, 0, 0), (sx, sy, sz)).rects()
    rng = random.Random(str_seed(name) ^ 0x5bd1)
    cracks = {}
    drawn = {}
    for face, zx, zy in CRACK_PLAN.get(key, []):
        ru, rv, rw, rh = rects[face]
        pix = _zigzag(rng, rw, rh, (zx, zy), drawn.get(face, ()))
        drawn[face] = list(drawn.get(face, ())) + pix
        for (i, j) in pix:
            if layer[rv + j, ru + i][3]:
                cracks[(face, i, j)] = 3 if rng.random() < 0.8 else 4

    def fn(ctx):
        face = ctx.el
        if mirrored and face in ("right", "left"):
            face = "left" if face == "right" else "right"
        ru, rv, rw, rh = rects[face]
        fu = 1.0 - ctx.fu if mirrored else ctx.fu
        lv = cracks.get((face, min(rw - 1, int(fu * rw)), min(rh - 1, int(ctx.fv * rh))))
        return P.rgba(P.PURPLE.cols[lv]) if lv is not None else None
    return fn


def paint_extra_texture(path):
    """64x64 texture for the extra worn geometry. The boxes come from assets/shrinecraft/armor_extras/tuff_extras.json,
    so the texture always lines up with what the game (and the viewer) draw."""
    data = load_json(os.path.join(EXTRAS_DIR, "tuff_extras.json"))
    W, H = data["texture_size"]
    img = np.zeros((H, W, 4), dtype=np.uint8)
    glow_img = np.zeros((H, W, 4), dtype=np.uint8)
    done = set()
    for piece_name, pieces in data["pieces"].items():
        for pc in pieces:
            key = (tuple(pc["uv"]), tuple(pc["size"]))
            if key in done:
                continue
            done.add(key)
            fn = M(pc.get("material", "netherite"))
            box = Box(tuple(pc["uv"]), tuple(pc["origin"]), tuple(pc["size"]), pc.get("inflate", 0.0))

            def choose(ctx, face, row, col, rows, cols, fn=fn, pc=pc):
                if face == "bottom" and pc["material"] != "denim":
                    return fn(ctx)
                if pc["material"] == "denim" and face in ("top", "bottom"):
                    return None                       # open ends: the cuff is a tube around the leg
                if pc["material"] == "denim" and row >= rows - 2:
                    return M("denim_frayed")(ctx)     # frayed pooled cuff
                if pc["material"] == "tongue" and row == 0:
                    return M("tongue")(ctx)
                return fn(ctx)

            paint_box(img, box, choose, str_seed(pc["name"]))
            gfn = P.GLOW_FNS.get(pc.get("material", ""))
            if pc.get("material") == "shell":
                gfn = _shell_masked(pc["shell"], pc["name"])
            if gfn:
                paint_box(glow_img, box, lambda ctx, face, row, col, rows, cols, gfn=gfn: gfn(ctx), str_seed(pc["name"]))
    save_png(img, path)
    save_png(glow_img, path.replace(".png", "_glow.png"))      # only the glowing texels: drawn full-bright by the game (RenderLayers.eyes)


# ====================================================================================================================
#  3. GUI backgrounds (vanilla inventory panel, recoloured + ornate frame)
# ====================================================================================================================

def theme_defs():
    return {
        "ocean": dict(fill=R3("#0e4c66", "#13607e", "#1a7898"), light="#7fe8f0", dark="#04202c", slot="#0a2f44", slotdark="#031620",
                      void="#02101c", accent="#d8c070", motif="wave"),
        "lava": dict(fill=R3("#2a0f0c", "#3a1610", "#4e2014"), light="#ff9a3a", dark="#120403", slot="#1a0806", slotdark="#0a0201",
                     void="#140404", accent="#ffd060", motif="flame"),
        "sculk": dict(fill=R3("#06161b", "#0a232a", "#0f3340"), light="#3df0dc", dark="#020a0c", slot="#04151a", slotdark="#010608",
                      void="#030c0e", accent="#9dfbf0", motif="tendril"),
        "arctic": dict(fill=R3("#a9d3ea", "#c4e4f4", "#e2f4fc"), light="#ffffff", dark="#3c6c88", slot="#86b6d0", slotdark="#486f88",
                       void="#0c1c28", accent="#ffffff", motif="snow"),
        "end": dict(fill=R3("#0b0716", "#130c26", "#1c1238"), light="#c890ff", dark="#030108", slot="#0a0618", slotdark="#020006",
                    void="#04000a", accent="#e8b8ff", motif="star"),
    }


def R3(*hexes):
    return [hex_rgb(h) for h in hexes]


def copy_owner_gui():
    """tools/reference/gui/<theme>/{inventory,chest}.png (256x256 sheets in the vanilla layout) -> textures/gui/backgrounds/<theme>[_chest].png,
    and the loose parts (slot, player inventory strip, edges, bottom strip) -> textures/gui/parts/<theme>/."""
    base = os.path.join(ROOT, "tools", "reference", "gui")
    if not os.path.isdir(base):
        return
    for theme in sorted(os.listdir(base)):
        d = os.path.join(base, theme)
        ensure_dir(TEX_GUI)
        for src, dst in (("inventory.png", theme + ".png"), ("chest.png", theme + "_chest.png")):
            if os.path.exists(os.path.join(d, src)):
                Image.open(os.path.join(d, src)).convert("RGBA").save(os.path.join(TEX_GUI, dst))
        parts = os.path.join(os.path.dirname(TEX_GUI), "parts", theme)
        ensure_dir(parts)
        for f in os.listdir(d):
            if f not in ("inventory.png", "chest.png"):
                Image.open(os.path.join(d, f)).convert("RGBA").save(os.path.join(parts, f))
        print("owner GUI art copied:", theme)


def paint_themed_screens():
    import build_themed_screens
    build_themed_screens.main()
    import build_themed_widgets          # buttons, tooltips, hotbar, creative tabs, advancements, menu backgrounds ...
    build_themed_widgets.main()


def paint_gui_backgrounds():
    """The five themes now come from the OWNER'S artwork (tools/reference/gui/<theme>/*.png, copied into assets by copy_owner_gui()).
    The old procedural painter below only runs for a theme that has no owner artwork."""
    copy_owner_gui()
    paint_themed_screens()
    ref = os.path.join(ROOT, "tools", "reference", "inventory.png")
    if not os.path.exists(ref):
        print("missing tools/reference/inventory.png (copy of vanilla textures/gui/container/inventory.png) - skipping GUI backgrounds")
        return
    base = np.array(Image.open(ref).convert("RGBA"))
    for name, th in theme_defs().items():
        if not want(name) and not want("gui"):
            continue
        if os.path.exists(os.path.join(ROOT, "tools", "reference", "gui", name, "inventory.png")):
            continue                                   # owner artwork wins
        out = np.zeros((256, 256, 4), dtype=np.uint8)
        PW, PH = 176, 166
        for y in range(PH):
            for x in range(PW):
                r, g, b, a = base[y, x]
                if a == 0:
                    continue
                col = classify_gui(th, (int(r), int(g), int(b)), x, y)
                out[y, x] = (*col, 255)
        ornament(out, th, name)
        path = os.path.join(TEX_GUI, name + ".png")
        ensure_dir(TEX_GUI)
        Image.fromarray(out, "RGBA").save(path)
        print("painted", os.path.relpath(path, ROOT))


def classify_gui(th, rgb, x, y):
    fill = th["fill"]
    if rgb == (198, 198, 198):                  # panel
        n = value_noise3(x * 0.35, y * 0.35, 0, 7) * 0.7 + hash01(x, y, 3) * 0.3
        band = ((x // 2 + y // 2) % 2) * 0.12
        t = clamp01(n * 0.8 + band)
        return fill[min(2, int(t * 2.99))]
    if rgb == (255, 255, 255):
        return hex_rgb(th["light"])
    if rgb == (85, 85, 85):
        return hex_rgb(th["dark"])
    if rgb == (139, 139, 139):
        s = hex_rgb(th["slot"])
        return tuple(max(0, min(255, int(v + (hash01(x, y, 9) - 0.5) * 8))) for v in s)
    if rgb == (55, 55, 55):
        return hex_rgb(th["slotdark"])
    if rgb == (0, 0, 0):
        inside_preview = 26 <= x <= 76 and 8 <= y <= 78
        return hex_rgb(th["void"]) if inside_preview else hex_rgb(th["dark"])
    return rgb


def ornament(out, th, name):
    """Ornate frame: corner flourishes + a patterned band inside the panel edge, drawn only in the empty 6px margin."""
    acc = hex_rgb(th["accent"])
    light = hex_rgb(th["light"])
    dark = hex_rgb(th["dark"])
    PW, PH = 176, 166
    motif = th["motif"]

    def put(x, y, col):
        if 0 <= x < PW and 0 <= y < PH and out[y, x][3] > 0:
            out[y, x] = (*col, 255)

    # 1px bright rule + patterned band along all four sides (x/y 2..5 are free of slots)
    for x in range(2, PW - 2):
        for off in (2, PH - 3):
            put(x, off, light if (x % 2 == 0) else acc)
    for y in range(2, PH - 2):
        for off in (2, PW - 3):
            put(off, y, light if (y % 2 == 0) else acc)
    for x in range(4, PW - 4):
        for side in (0, 1):
            y = 4 if side == 0 else PH - 5
            ph = x % 8
            put(x, y, motif_color(motif, ph, side, acc, light, dark))
            put(x, y + (1 if side == 0 else -1), dark if ph in (0, 4) else None or dark)
    for y in range(4, PH - 4):
        for side in (0, 1):
            x = 4 if side == 0 else PW - 5
            ph = y % 8
            put(x, y, motif_color(motif, ph, side, acc, light, dark))
    # corner flourishes (5x5 diamonds + dots)
    corners = [(3, 3), (PW - 4, 3), (3, PH - 4), (PW - 4, PH - 4)]
    for cx, cy in corners:
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            put(cx + dx, cy + dy, acc)
        for dx, dy in ((2, 2), (-2, 2), (2, -2), (-2, -2), (2, 0), (-2, 0), (0, 2), (0, -2)):
            put(cx + dx, cy + dy, light)
    # title-bar gems on the top edge
    for gx in (PW // 2 - 14, PW // 2, PW // 2 + 14):
        for dy in range(0, 3):
            put(gx, 2 + dy, acc)
        put(gx - 1, 3, light)
        put(gx + 1, 3, light)


def motif_color(motif, ph, side, acc, light, dark):
    if motif == "wave":
        return [light, light, acc, light, dark, dark, dark, dark][ph] if True else acc
    if motif == "flame":
        return [dark, acc, light, acc, dark, dark, acc, dark][ph]
    if motif == "tendril":
        return [dark, light, light, dark, dark, acc, dark, dark][ph]
    if motif == "snow":
        return [acc, dark, acc, dark, light, dark, acc, dark][ph]
    return [dark, dark, acc, dark, light, dark, dark, acc][ph]  # end: scattered stars


# ====================================================================================================================

def main():
    paint_items_and_shrine()
    if want("helmet") or want("armor"):
        paint_helmet(os.path.join(TEX_EQUIP, "humanoid", "shrine_helmet.png"))
        paint_blank(os.path.join(TEX_EQUIP, "humanoid_leggings", "shrine_helmet.png"))
    if want("chestplate") or want("armor"):
        paint_chestplate(os.path.join(TEX_EQUIP, "humanoid", "shrine_chestplate.png"))
        paint_blank(os.path.join(TEX_EQUIP, "humanoid_leggings", "shrine_chestplate.png"))
    if want("leggings") or want("armor"):
        paint_leggings(os.path.join(TEX_EQUIP, "humanoid_leggings", "shrine_leggings.png"))
        paint_blank(os.path.join(TEX_EQUIP, "humanoid", "shrine_leggings.png"))
    if want("boots") or want("armor"):
        paint_boots(os.path.join(TEX_EQUIP, "humanoid", "shrine_boots.png"))
        paint_blank(os.path.join(TEX_EQUIP, "humanoid_leggings", "shrine_boots.png"))
    if want("cape"):
        paint_cape(os.path.join(TEX_EQUIP, "wings", "strange_cape.png"))
    if want("extra") or ONLY is None:
        paint_extra_texture(os.path.join(TEX_EXTRA, "tuff_extras.png"))
    paint_gui_backgrounds()


def paint_blank(path):
    save_png(new_armor_img(), path)


if __name__ == "__main__":
    main()
