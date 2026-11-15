#!/usr/bin/env python3
"""
build_themed_screens.py - themed copies of the OTHER vanilla container screens (crafting table, furnace, dispenser, hopper, shulker box, anvil,
brewing stand, enchanting table, loom, stonecutter, ... see SCREENS) for every GUI theme, with much simpler graphics than the owner's
inventory/chest artwork: the vanilla layout is copied, the panel gets the theme's flat colours + a thin accent line, and every slot is replaced
by the owner's own slot tile (tools/reference/gui/<theme>/slot.png, stretched with a 9-slice for the big 26x26 result slots).

Output: src/main/resources/assets/shrinecraft/textures/gui/themed/<theme>/<screen>.png   (same size/layout as the vanilla texture, so the game
just swaps the texture - see DrawContextMixin).   Vanilla originals live in tools/reference/vanilla_gui/.

To restyle: edit PALETTES below (colours are plain RGB) and run  python3 tools/build_themed_screens.py   (paint_textures.py / rebuild_all.py run it too).

UPGRADE (ornate frames): the flat panel is now dressed with the owner's own border artwork from tools/reference/gui/<theme>/ - the painted
top band with the corner ornaments, the painted bottom band, edge_1 / edge_2 down the sides - cut from chest.png / the edge pieces, so
every screen has the same frame as the inventory and chest, and the panel fill is a soft gradient in the theme colours (see ornate_pieces / dress_panel).
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa

VANILLA = os.path.join(REF_DIR, "vanilla_gui")
OUT = os.path.join(ASSETS, "textures", "gui", "themed")
# extra screens that do not live in textures/gui/container/ (the file name here = the file in vanilla_gui/ and the themed output)
EXTRA_SCREENS = ["advancements_window", "creative_tab_items", "creative_tab_inventory", "creative_tab_item_search"]
SCREENS = ["anvil", "beacon", "blast_furnace", "brewing_stand", "cartography_table", "crafter", "crafting_table", "dispenser", "enchanting_table",
           "furnace", "grindstone", "hopper", "horse", "loom", "nautilus", "shulker_box", "smithing", "smoker", "stonecutter", "villager"]

# panel = main surface, dark = outer outline, accent = thin highlight line, shadow = inner shading, slot = empty-slot colour (used for non-slot grey areas)
PALETTES = {
    "end":    dict(panel=(44, 40, 46), dark=(12, 10, 14), accent=(202, 175, 104), shadow=(26, 23, 30), slot=(38, 37, 40)),
    "lava":   dict(panel=(36, 19, 15), dark=(10, 4, 3), accent=(212, 128, 40), shadow=(20, 10, 8), slot=(23, 23, 23)),
    "arctic": dict(panel=(182, 207, 236), dark=(44, 80, 125), accent=(244, 250, 255), shadow=(112, 142, 192), slot=(61, 83, 125)),
    "sculk":  dict(panel=(11, 34, 38), dark=(2, 8, 10), accent=(40, 178, 176), shadow=(6, 20, 22), slot=(21, 24, 26)),
    "ocean":  dict(panel=(15, 62, 90), dark=(3, 20, 34), accent=(140, 162, 168), shadow=(8, 38, 58), slot=(6, 34, 54)),
}


def nine_slice(tile, w, h, corner=3):
    """Stretch an 18x18 slot tile to w x h keeping its corners/frame crisp."""
    t = np.array(tile)
    th, tw = t.shape[:2]
    out = np.zeros((h, w, 4), np.uint8)
    xs = [0, corner, tw - corner, tw]
    ys = [0, corner, th - corner, th]
    ox = [0, corner, w - corner, w]
    oy = [0, corner, h - corner, h]
    for j in range(3):
        for i in range(3):
            src = t[ys[j]:ys[j + 1], xs[i]:xs[i + 1]]
            dw, dh = ox[i + 1] - ox[i], oy[j + 1] - oy[j]
            if dw <= 0 or dh <= 0:
                continue
            sy = (np.arange(dh) * src.shape[0] // dh).clip(0, src.shape[0] - 1)
            sx = (np.arange(dw) * src.shape[1] // dw).clip(0, src.shape[1] - 1)
            out[oy[j]:oy[j + 1], ox[i]:ox[i + 1]] = src[sy][:, sx]
    return out


def find_slots(img):
    """Rectangles (x, y, w, h) of exact vanilla slot-grey (139,139,139) that fill their bounding box and are at least 16x16."""
    H, W = img.shape[:2]
    mask = (img[..., 0] == 139) & (img[..., 1] == 139) & (img[..., 2] == 139) & (img[..., 3] == 255)
    seen = np.zeros_like(mask)
    rects = []
    for y in range(H):
        for x in range(W):
            if mask[y, x] and not seen[y, x]:
                x1 = x
                while x1 + 1 < W and mask[y, x1 + 1]:
                    x1 += 1
                y1 = y
                while y1 + 1 < H and mask[y1 + 1, x:x1 + 1].all():
                    y1 += 1
                seen[y:y1 + 1, x:x1 + 1] = True
                w, h = x1 - x + 1, y1 - y + 1
                # a real slot is a full rectangle: nothing grey right next to it that would make the bounding box ragged
                if w >= 16 and h >= 16 and w <= 30 and h <= 30:
                    rects.append((x, y, w, h))
    return rects


TOP_BAND = 9      # rows of the owner's painted top band (with the corner ornaments)
BOTTOM_BAND = 9   # rows of the owner's painted bottom band
SIDE = 5          # width of edge_1 / edge_2


def ornate_pieces(theme):
    """The owner's frame pieces for a theme: top band, bottom band (both 176 wide, corners included), left + right edge (5 x 32, tileable)."""
    d = os.path.join(ROOT, "tools", "reference", "gui", theme)
    chest = np.array(Image.open(os.path.join(d, "chest.png")).convert("RGBA"))
    ys = np.where(chest[..., 3].max(axis=1) > 0)[0]
    chest_h = int(ys.max()) + 1
    top = chest[0:TOP_BAND, 0:176]
    bottom = chest[chest_h - BOTTOM_BAND:chest_h, 0:176]
    left = np.array(Image.open(os.path.join(d, "edge_1.png")).convert("RGBA"))
    right = np.array(Image.open(os.path.join(d, "edge_2.png")).convert("RGBA"))
    return top, bottom, left, right


def widen(band, w):
    """Make a 176-wide band any width: keep the two ornate ends, repeat the plain middle."""
    if w == band.shape[1]:
        return band
    end = 44
    mid = band[:, end:band.shape[1] - end]
    left, right = band[:, :end], band[:, band.shape[1] - end:]
    mw = w - 2 * end
    reps = np.concatenate([mid] * (mw // mid.shape[1] + 1), axis=1)[:, :mw]
    return np.concatenate([left, reps, right], axis=1)


def panel_rect(src):
    """Size of the vanilla main panel (starts at 0,0): follow the opaque pixels along the top row and the left column."""
    a = src[..., 3] > 0
    w = 0
    while w < a.shape[1] and a[4, w]:
        w += 1
    h = 0
    while h < a.shape[0] and a[h, 4]:
        h += 1
    return w, h


def backdrop_tile(theme):
    """A clean (no slots) piece of the owner's painted scene from inventory.png: the "glass window" area, 40 x 70 pixels."""
    inv = np.array(Image.open(os.path.join(ROOT, "tools", "reference", "gui", theme, "inventory.png")).convert("RGBA"))
    return inv[10:80, 30:70].copy()


def scene_pixel(tile, x, y):
    """The painted scene tiled sideways, every second copy mirrored so there is no visible seam."""
    tw = tile.shape[1]
    k = x % (2 * tw)
    if k >= tw:
        k = 2 * tw - 1 - k
    return tile[min(max(y, 0), tile.shape[0] - 1), k]


def dress_panel(out, a, pal, theme, scene=True):
    """Fill the panel grey with the owner's painted scene (top part) + a deep gradient (inventory part) and lay the owner's frame round the edge."""
    W, H = panel_rect(a)
    rgb = a[..., :3].astype(int)
    fill_mask = (rgb[..., 0] == 198) & (rgb[..., 1] == 198) & (rgb[..., 2] == 198) & (a[..., 3] > 0)
    base = np.array(pal["panel"], float)
    rng = np.random.default_rng(7)
    tile = backdrop_tile(theme)
    scene_bottom = (H - 88) if scene else TOP_BAND   # (no painted scene on full-grid screens: creative menu, advancements)
    # above the player-inventory block (vanilla puts it 82-84 px above the bottom)
    for y in range(H):
        t = y / max(1, H - 1)
        col = base * (1.0 - 0.30 * t)
        row = fill_mask[y, :W]
        if not row.any():
            continue
        if TOP_BAND <= y < scene_bottom:
            for x in np.nonzero(row)[0]:
                px = scene_pixel(tile, x - SIDE, y - TOP_BAND)
                # fade the scene into the deep colour over its last rows so it melts into the inventory part
                fade = min(1.0, max(0.0, (scene_bottom - y) / 8.0))
                out[y, x, :3] = (px[:3] * fade + col * (1 - fade)).astype(np.uint8)
        else:
            noise = rng.integers(-3, 4, size=(W, 1))
            px = np.clip(col + noise, 0, 255).astype(np.uint8)
            out[y, :W][row, :3] = px[row]
    top, bottom, left, right = ornate_pieces(theme)
    top, bottom = widen(top, W), widen(bottom, W)
    out[0:TOP_BAND, 0:W] = top
    out[H - BOTTOM_BAND:H, 0:W] = bottom
    for y in range(TOP_BAND, H - BOTTOM_BAND):
        out[y, 0:SIDE] = left[y % left.shape[0]]
        out[y, W - SIDE:W] = right[y % right.shape[0]]
    # the owner's divider strip between the painted top and the inventory block
    strip = np.array(Image.open(os.path.join(ROOT, "tools", "reference", "gui", theme, "strip.png")).convert("RGBA"))
    strip = widen(strip, W)
    sy = scene_bottom - 1
    if scene and sy > TOP_BAND + 20:
        for yy in range(strip.shape[0]):
            for xx in range(W):
                if strip[yy, xx, 3] > 0 and (a[sy + yy, xx, 3] > 0):
                    out[sy + yy, xx] = strip[yy, xx]
    return out


def gmap(lum, pal):
    """Colour for a grey of brightness lum (0..255): dark -> outline, then shadow, then panel, then accent (bright)."""
    stops = [(0, pal["dark"]), (70, pal["shadow"]), (170, pal["panel"]), (255, pal["accent"])]
    for (l0, c0), (l1, c1) in zip(stops, stops[1:]):
        if lum <= l1:
            t = (lum - l0) / max(1, l1 - l0)
            return tuple(int(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
    return stops[-1][1]


def theme_one(theme, name):
    src = Image.open(os.path.join(VANILLA, name + ".png")).convert("RGBA")
    a = np.array(src)
    pal = PALETTES[theme]
    out = a.copy()
    rgb = a[..., :3].astype(int)
    opaque = a[..., 3] > 0

    def put(mask, col):
        out[mask & opaque, :3] = col

    def eq(c):
        return (rgb[..., 0] == c[0]) & (rgb[..., 1] == c[1]) & (rgb[..., 2] == c[2])

    mid = tuple(int((p + d) / 2) for p, d in zip(pal["panel"], pal["dark"]))
    put(eq((0, 0, 0)), pal["dark"])
    put(eq((255, 255, 255)), pal["accent"])
    spread = rgb.max(axis=2) - rgb.min(axis=2)
    put((rgb.min(axis=2) >= 240) & (spread <= 3), pal["accent"])      # near-white frame lines (crafter grid etc.)
    put(eq((198, 198, 198)), pal["panel"])
    put(eq((85, 85, 85)), pal["shadow"])
    put(eq((55, 55, 55)), pal["dark"])
    put(eq((255, 0, 0)), pal["dark"])        # the anvil's name field (drawn over by the game's own text-field sprite)
    put(eq((139, 139, 139)), pal["slot"])
    put(eq((139, 139, 139)), pal["slot"])
    # also the 2 near-greys vanilla uses for panel edges
    put(eq((192, 192, 192)), mid)
    # any OTHER pure grey that the exact mappings above did not touch (the advancement viewport, the enchanting list, ...) follows the same gradient
    handled = eq((0, 0, 0)) | eq((255, 255, 255)) | eq((198, 198, 198)) | eq((85, 85, 85)) | eq((55, 55, 55)) | eq((139, 139, 139)) | eq((192, 192, 192))
    grey = (rgb.max(axis=2) - rgb.min(axis=2) <= 2) & opaque & ~handled
    ys, xs = np.nonzero(grey)
    for y_, x_ in zip(ys, xs):
        out[y_, x_, :3] = gmap(int(rgb[y_, x_, 0]), pal)
    if name == "enchanting_table":
        # the three enchantment buttons sit on a tan list in the vanilla sheet: recolour that block with the same gradient
        for y_ in range(14, 72):
            for x_ in range(60, 168):
                if opaque[y_, x_]:
                    r_, g_, b_ = rgb[y_, x_]
                    out[y_, x_, :3] = gmap(int(0.3 * r_ + 0.59 * g_ + 0.11 * b_) , pal)
    tile = Image.open(os.path.join(ROOT, "tools", "reference", "gui", theme, "slot.png")).convert("RGBA")
    n = 0
    for (x, y, w, h) in find_slots(a):
        art = nine_slice(tile, w + 2, h + 2) if (w + 2, h + 2) != (18, 18) else np.array(tile)
        x0, y0 = x - 1, y - 1
        out[y0:y0 + art.shape[0], x0:x0 + art.shape[1]] = art
        n += 1
    # dress the panel AFTER the slots so the frame is never covered; slots are re-applied on top where they touch the edge area
    slots_layer = out.copy()
    out = dress_panel(out, a, pal, theme, scene=not (name.startswith("creative_") or name.startswith("advancements")))
    for (x, y, w, h) in find_slots(a):
        art = nine_slice(tile, w + 2, h + 2) if (w + 2, h + 2) != (18, 18) else np.array(tile)
        out[y - 1:y - 1 + art.shape[0], x - 1:x - 1 + art.shape[1]] = art
    return out, n


def main(only=None):
    total = 0
    for theme in PALETTES:
        if not os.path.exists(os.path.join(ROOT, "tools", "reference", "gui", theme, "slot.png")):
            continue
        d = os.path.join(OUT, theme)
        os.makedirs(d, exist_ok=True)
        for name in SCREENS + EXTRA_SCREENS:
            if only and name not in only:
                continue
            img, slots = theme_one(theme, name)
            Image.fromarray(img, "RGBA").save(os.path.join(d, name + ".png"))
            total += 1
    print("themed screen textures:", total, "(", len(PALETTES), "themes x", len(SCREENS), "screens )")


if __name__ == "__main__":
    main(sys.argv[1:])
