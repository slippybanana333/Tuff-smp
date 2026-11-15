#!/usr/bin/env python3
"""
armor_variants.py <moss|runes|copper|bricks|pauldrons|none> - TEMPORARY mock-ups of possible armor upgrades (owner: "give me a few cool things i could add
to make it better then screenshots of what they look like and i choose"). Applies one variant on top of the finished polished-tuff layer sheets
(run AFTER `paint_textures.py armor`; 'none' restores nothing - to go back to the real armor run make_polished_tuff_armor.py + paint_textures.py armor).
Only the variant the owner picks gets built into the real pipeline.
"""
import os
import random
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tuffpal
from paint_textures import Box, HEAD, BODY, ARM, LEG, TEX_EQUIP

SHEETS = {                      # piece -> (folder, [(region box, name)])
    "shrine_helmet": ("humanoid", [(HEAD(1.0), "head")]),
    "shrine_chestplate": ("humanoid", [(BODY(1.0), "body"), (ARM(1.0), "arm")]),
    "shrine_leggings": ("humanoid_leggings", [(BODY(0.5), "body"), (LEG(0.4), "leg")]),
    "shrine_boots": ("humanoid", [(LEG(0.9), "leg")]),
}


def load(piece):
    folder, _ = SHEETS[piece]
    return np.array(Image.open(os.path.join(TEX_EQUIP, folder, piece + ".png")).convert("RGBA"))


def save(piece, arr):
    folder, _ = SHEETS[piece]
    Image.fromarray(arr, "RGBA").save(os.path.join(TEX_EQUIP, folder, piece + ".png"))


def put(arr, x, y, col):
    if 0 <= y < arr.shape[0] and 0 <= x < arr.shape[1] and arr[y, x, 3]:
        arr[y, x, :3] = col


def moss(rng):
    G = [(46, 70, 32), (66, 100, 44), (92, 132, 58), (122, 160, 76)]
    for piece, (_, boxes) in SHEETS.items():
        arr = load(piece)
        for box, name in boxes:
            for face, (ru, rv, rw, rh) in box.rects().items():
                for j in range(rh):
                    for i in range(rw):
                        depth = j / max(1, rh - 1)
                        top = face == "top"
                        p = 0.8 if top else max(0.0, 0.55 - depth * 0.9)       # moss grows on top faces and just below upper edges
                        p *= 0.7 + 0.5 * tuffpal.fbm3(i * 0.7 + ru, j * 0.7 + rv, 3.0, 5, 2)
                        if name in ("leg",) and piece == "shrine_boots":
                            p = 0.5 if face in ("top", "front", "back", "left", "right") and j > rh - 3 else p * 0.3
                        if rng.random() < p:
                            put(arr, ru + i, rv + j, G[min(3, int(rng.random() * 3 + (1 if top else 0)))])
        save(piece, arr)


def runes(rng):
    DARK, LIGHT = (44, 48, 44), (140, 142, 132)
    def groove(arr, x, y):            # engraved line: dark pixel with a pale lip below-right
        put(arr, x, y, DARK)
        if 0 <= y + 1 < arr.shape[0] and 0 <= x + 1 < arr.shape[1] and arr[y + 1, x + 1, 3] and tuple(arr[y + 1, x + 1, :3]) != DARK:
            pass
    for piece, (_, boxes) in SHEETS.items():
        arr = load(piece)
        for box, name in boxes:
            r = box.rects()
            if piece == "shrine_chestplate" and name == "body":
                ru, rv, rw, rh = r["front"]                                    # a carved sigil on the chest: diamond + cross
                glyph = ["...XX...", "..X..X..", ".X.XX.X.", "X..XX..X", ".X.XX.X.", "..X..X..", "...XX...", "...XX...", "..XXXX..", "...XX...", "...XX..."]
                for j, row in enumerate(glyph):
                    for i, c in enumerate(row):
                        if c == "X":
                            groove(arr, ru + i, rv + 1 + j) if rv + 1 + j < rv + rh else None
                ru, rv, rw, rh = r["back"]
                for j in (3, 8):                                              # two grooves across the back
                    for i in range(1, rw - 1):
                        groove(arr, ru + i, rv + j)
            elif piece == "shrine_helmet":
                ru, rv, rw, rh = r["front"]
                for i in range(0, rw):
                    groove(arr, ru + i, rv + 1)                              # a groove along the forehead band
                for face in ("right", "left", "back"):
                    ru, rv, rw, rh = r[face]
                    for i in range(1, rw - 1):
                        groove(arr, ru + i, rv + 2)
                    for j in (4, 5):
                        groove(arr, ru + rw // 2, rv + j)
            elif name == "arm":
                for face in ("front", "back", "right", "left"):
                    ru, rv, rw, rh = r[face]
                    for j in range(2, rh - 2, 2):
                        groove(arr, ru + rw // 2, rv + j)                    # a dotted line down each arm
            elif name == "leg":
                for face in ("front", "back"):
                    ru, rv, rw, rh = r[face]
                    for j in range(1, rh - 1):
                        if j % 4 != 0:
                            groove(arr, ru + rw // 2, rv + j)               # a vertical groove with gaps
        save(piece, arr)


def copper(rng):
    C = [(122, 62, 40), (176, 98, 62), (214, 132, 84)]
    T = [(52, 128, 112), (74, 160, 138), (108, 196, 168)]                      # weathered (oxidised) copper
    for piece, (_, boxes) in SHEETS.items():
        arr = load(piece)
        h, w = arr.shape[:2]
        edge = np.zeros((h, w), dtype=bool)
        for box, name in boxes:
            for face, (ru, rv, rw, rh) in box.rects().items():
                for j in range(rh):
                    for i in range(rw):
                        if j == 0 or j == rh - 1 or i == 0 or i == rw - 1:
                            if face in ("front", "back", "left", "right"):
                                edge[rv + j, ru + i] = True
        for y in range(h):
            for x in range(w):
                if edge[y, x] and arr[y, x, 3] and (rng.random() < 0.85):
                    n = tuffpal.fbm3(x * 0.3, y * 0.3, 9.0, 3, 2)
                    ramp = T if n > 0.55 else C                               # about a third of the trim is oxidised teal
                    arr[y, x, :3] = ramp[min(2, int(rng.random() * 2.4))]
        save(piece, arr)


def bricks(rng):
    B = Image.open(os.path.join(tuffpal.HERE, "reference", "tuff_bricks.png")).convert("RGB")
    pal = sorted({B.getpixel((x, y)) for x in range(16) for y in range(16)}, key=lambda c: 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])
    for piece in ("shrine_chestplate", "shrine_leggings"):
        arr = load(piece)
        h, w = arr.shape[:2]
        for y in range(h):
            for x in range(w):
                if arr[y, x, 3]:
                    lum = 0.3 * arr[y, x, 0] + 0.59 * arr[y, x, 1] + 0.11 * arr[y, x, 2]
                    ti = pal.index(B.getpixel((x % 16, y % 16)))
                    shift = int(round((lum - 98.0) / 14.0))              # keep the existing shading of the panels
                    arr[y, x, :3] = pal[max(0, min(len(pal) - 1, ti + shift))]
        save(piece, arr)


def pauldrons(rng):
    """3D variant: stone pauldrons (4 overlapping lames per shoulder) + gorget, helmet crest fin and two swept horns. Uses the extras system."""
    import json
    import make_tuff_armor as M
    H, C = "shrine_helmet", "shrine_chestplate"
    M.pair(C, "right_arm", "left_arm", "pauldron", (-11, -9, -7), (3, -5, 7), "ptuff_light")
    M.pair(C, "right_arm", "left_arm", "pauldron", (-12, -5, -8), (3, -1, 8), "ptuff")
    M.pair(C, "right_arm", "left_arm", "pauldron", (-11, -1, -7), (2, 3, 7), "ptuff_dark")
    M.pair(C, "right_arm", "left_arm", "pauldron", (-9, 3, -6), (2, 6, 6), "ptuff")
    M.box(C, "body", "pauldron", (-8, -5, -7), (8, -1, -6), "ptuff_dark")
    M.box(C, "body", "pauldron", (-8, -4, 6), (8, 0, 7), "ptuff_dark")
    M.box(H, "head", "crest", (-1, -24, -10), (1, -18, 9), "ptuff_light")
    M.box(H, "head", "crest", (-1, -22, 9), (1, -14, 12), "ptuff")
    for sx in (1, -1):
        for a, b, mat in (((10, -14, -2), (13, -12, 2), "ptuff"), ((12, -17, -2), (15, -14, 2), "ptuff_light"), ((14, -21, -1), (16, -17, 1), "ptuff_light")):
            if sx < 0:
                a, b = M.mirror(a, b)
            M.box(H, "head", "crest", a, b, mat)
    data = {"fine_scale": 0.5, "groups": {"pauldron": {"pivot": [0, 0, 0]}, "crest": {"pivot": [0, -9, 0]}},
            "texture": "shrinecraft:textures/entity/armor_extra/tuff_extras.png", "texture_size": [M.TEX, M.TEX], "pieces": M.pieces}
    with open(M.OUT, "w") as f:
        json.dump(data, f)
    import subprocess
    subprocess.run([sys.executable, os.path.join(os.path.dirname(os.path.abspath(__file__)), "paint_textures.py"), "extra"], capture_output=True)


def main():
    v = sys.argv[1] if len(sys.argv) > 1 else "none"
    rng = random.Random(7)
    {"moss": moss, "runes": runes, "copper": copper, "bricks": bricks, "pauldrons": pauldrons}.get(v, lambda r: None)(rng)
    print("variant applied:", v)


if __name__ == "__main__":
    main()
