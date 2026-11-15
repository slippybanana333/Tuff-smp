#!/usr/bin/env python3
"""
build_themed_widgets.py - themed copies of the vanilla GUI SPRITES and the plain GUI textures for every GUI theme, so the WHOLE interface
(buttons, text fields, sliders, tooltips, hotbar, advancements, recipe book, creative-menu tabs and scroller, toasts, menu backgrounds ...)
wears the theme, not just the container panels.

How: every vanilla grey (the UI is nearly all greys) is mapped through the theme's colour gradient (dark outline -> shadow -> panel -> accent),
colourful pixels (flames, emeralds, enchant icons, XP) are left alone. Sizes and the .mcmeta 9-slice info are copied, so the game can swap a sprite
for its themed twin without any layout change (see the DrawContext mixin).

Output:  assets/shrinecraft/textures/gui/sprites/themed/<theme>/<vanilla sprite path>.png (+ .mcmeta)
         assets/shrinecraft/textures/gui/themed/<theme>/misc/<name>.png        (plain textures: menu backgrounds, separators, advancement tiles)
         assets/shrinecraft/themed_sprites.json                                  (list the game reads at start-up)
Vanilla originals are cached in tools/reference/vanilla_sprites/ (extracted once from the Minecraft client jar).
"""
import glob
import json
import os
import sys
import zipfile

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa
from build_themed_screens import PALETTES, gmap  # noqa

CACHE = os.path.join(REF_DIR, "vanilla_sprites")
SPRITE_OUT = os.path.join(ASSETS, "textures", "gui", "sprites", "themed")
MISC_OUT = os.path.join(ASSETS, "textures", "gui", "themed")

# which vanilla sprite folders get themed (prefix match on the path inside textures/gui/sprites/)
SPRITE_PREFIXES = ["widget/", "tooltip/", "hud/hotbar", "container/", "advancements/", "recipe_book/", "gamemode_switcher/", "toast/",
                   "statistics/", "social_interactions/", "player_list/", "popup/", "dialog/", "world_list/", "transferable_list/"]
# sprites that are meant to be coloured by the theme even though they are saturated (tooltip frame is purple)
FORCE_MAP = ["tooltip/"]
# plain textures (textures/gui/<name>.png) that get themed copies
MISC = ["menu_background", "header_separator", "footer_separator", "inworld_menu_background", "inworld_header_separator", "inworld_footer_separator",
        "menu_list_background", "inworld_menu_list_background", "tab_header_background",
        "advancements/backgrounds/adventure", "advancements/backgrounds/stone", "advancements/backgrounds/nether", "advancements/backgrounds/end",
        "advancements/backgrounds/husbandry"]


# Buttons / sliders / text fields carry WHITE text, so on a light theme they need a darker body than the panels do.
WIDGET_PALETTES = {
    "arctic": dict(panel=(78, 110, 168), dark=(20, 40, 82), accent=(236, 246, 255), shadow=(44, 74, 126), slot=(61, 83, 125)),
}
WIDGET_PREFIXES = ["widget/", "tooltip/", "toast/", "popup/", "dialog/"]


def find_jar():
    jars = sorted(glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/*/minecraft-client.jar")))
    return jars[-1] if jars else None


def extract():
    """Cache the vanilla sprites + plain textures we theme (only needed the first time)."""
    marker = os.path.join(CACHE, "_done")
    if os.path.exists(marker):
        return
    jar = find_jar()
    if not jar:
        sys.exit("no Minecraft client jar found to extract the vanilla sprites from")
    z = zipfile.ZipFile(jar)
    n = 0
    for name in z.namelist():
        base = "assets/minecraft/textures/gui/"
        if not name.startswith(base):
            continue
        rel = name[len(base):]
        if rel.startswith("sprites/"):
            sp = rel[len("sprites/"):]
            if not any(sp.startswith(p) for p in SPRITE_PREFIXES) or not (sp.endswith(".png") or sp.endswith(".png.mcmeta")):
                continue
            dst = os.path.join(CACHE, "sprites", sp)
        elif rel.endswith(".png") and rel[:-4] in MISC:
            dst = os.path.join(CACHE, "misc", rel)
        else:
            continue
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, "wb") as f:
            f.write(z.read(name))
        n += 1
    open(marker, "w").write("ok")
    print("extracted", n, "vanilla GUI files")


def recolor(img, pal, force):
    a = np.array(img.convert("RGBA"))
    rgb = a[..., :3].astype(int)
    sat = rgb.max(axis=2) - rgb.min(axis=2)
    lum = (0.3 * rgb[..., 0] + 0.59 * rgb[..., 1] + 0.11 * rgb[..., 2]).astype(int)
    mask = (a[..., 3] > 0) & ((sat <= 28) | force)
    ys, xs = np.nonzero(mask)
    out = a.copy()
    cache = {}
    for y, x in zip(ys, xs):
        v = int(lum[y, x])
        if v not in cache:
            cache[v] = gmap(v, pal)
        out[y, x, :3] = cache[v]
    return Image.fromarray(out, "RGBA")


def art_button(theme, pal, w, h, state):
    """A button in the style of the owner's slot tiles: the slot's own metal frame stretched round a deep gradient body.
    state: 'normal', 'highlighted' (hover: brighter frame + lit body + accent underline) or 'disabled' (dim)."""
    from build_themed_screens import nine_slice
    tile = Image.open(os.path.join(ROOT, "tools", "reference", "gui", theme, "slot.png")).convert("RGBA")
    a = nine_slice(tile, w, h, corner=3).astype(float)
    frame = np.zeros((h, w), bool)
    frame[:2, :] = frame[-2:, :] = True
    frame[:, :2] = frame[:, -2:] = True
    body = ~frame
    panel, dark, accent = np.array(pal["panel"], float), np.array(pal["dark"], float), np.array(pal["accent"], float)
    for y in range(h):
        t = y / max(1, h - 1)
        col = panel * (1.05 - 0.45 * t) * 0.9 + dark * 0.1          # lit at the top, deep at the bottom
        if state == "highlighted":
            col = col * 1.35 + accent * 0.10
        if state == "disabled":
            col = col * 0.55
        a[y, body[y], :3] = np.clip(col, 0, 255)
    a[2, 2:-2, :3] = np.clip(a[2, 2:-2, :3] * 1.25 + 12, 0, 255)      # thin sheen under the top frame
    if state == "highlighted":
        a[frame, :3] = np.clip(a[frame, :3] * 1.45 + 20, 0, 255)
        a[h - 3, 4:-4, :3] = accent                                   # accent underline
    if state == "disabled":
        a[frame, :3] = a[frame, :3] * 0.6
    # tiny corner studs in the accent colour so it reads as crafted, not flat
    for (yy, xx) in ((3, 3), (3, w - 4), (h - 4, 3), (h - 4, w - 4)):
        a[yy, xx, :3] = accent * (0.5 if state == "disabled" else 0.9)
    a[..., 3] = 255
    return Image.fromarray(a.astype(np.uint8), "RGBA")


# sprites that are DRAWN from the owner's art instead of recoloured: path -> (width, height, state)
ART_SPRITES = {
    "widget/button.png": (200, 20, "normal"),
    "widget/button_highlighted.png": (200, 20, "highlighted"),
    "widget/button_disabled.png": (200, 20, "disabled"),
    "widget/slider.png": (200, 20, "disabled"),
    "widget/slider_highlighted.png": (200, 20, "disabled"),
    "widget/slider_handle.png": (8, 20, "normal"),
    "widget/slider_handle_highlighted.png": (8, 20, "highlighted"),
}


def panel_sheet(theme):
    """One 256x256 sheet with the pieces the game needs to draw a framed panel of ANY size (used behind the pause-menu buttons):
    top band (0,0 176x9), bottom band (0,9 176x9), left edge (0,18 5x32), right edge (5,18 5x32), painted scene + its mirror (16,18 80x70)."""
    from build_themed_screens import ornate_pieces, backdrop_tile
    top, bottom, left, right = ornate_pieces(theme)
    sheet = np.zeros((256, 256, 4), np.uint8)
    sheet[0:9, 0:176] = top
    sheet[9:18, 0:176] = bottom
    sheet[18:50, 0:5] = left
    sheet[18:50, 5:10] = right
    tile = backdrop_tile(theme)
    sheet[18:88, 16:56] = tile
    sheet[18:88, 56:96] = tile[:, ::-1]
    sheet[18:88, 16:96, 3] = 255
    return Image.fromarray(sheet, "RGBA")


def main():
    extract()
    listed = []
    for theme, pal in PALETTES.items():
        n = 0
        for src in glob.glob(os.path.join(CACHE, "sprites", "**", "*.png"), recursive=True):
            rel = os.path.relpath(src, os.path.join(CACHE, "sprites")).replace(os.sep, "/")
            force = any(rel.startswith(f) for f in FORCE_MAP)
            dst = os.path.join(SPRITE_OUT, theme, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            use = WIDGET_PALETTES.get(theme, pal) if any(rel.startswith(w) for w in WIDGET_PREFIXES) else pal
            if rel in ART_SPRITES:
                bw, bh, state = ART_SPRITES[rel]
                art_button(theme, use, bw, bh, state).save(dst)
            else:
                recolor(Image.open(src), use, force).save(dst)
            if os.path.exists(src + ".mcmeta"):
                with open(src + ".mcmeta", "rb") as f, open(dst + ".mcmeta", "wb") as g:
                    g.write(f.read())
            if theme == "ocean":
                listed.append(rel[:-4])
            n += 1
        for src in glob.glob(os.path.join(CACHE, "misc", "**", "*.png"), recursive=True):
            rel = os.path.relpath(src, os.path.join(CACHE, "misc")).replace(os.sep, "/")
            dst = os.path.join(MISC_OUT, theme, "misc", rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            recolor(Image.open(src), pal, True).save(dst)
            n += 1
        panel_sheet(theme).save(os.path.join(MISC_OUT, theme, "panel.png"))
        print(theme, n, "files")
    with open(os.path.join(ASSETS, "themed_sprites.json"), "w") as f:
        json.dump(sorted(listed), f, indent=0)
    print("sprites listed:", len(listed))


if __name__ == "__main__":
    main()
