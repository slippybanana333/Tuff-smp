#!/usr/bin/env python3
"""
make_polished_tuff_armor.py - the armor is now vanilla NETHERITE ARMOR (shape, panels, layers) TEXTURED AS POLISHED TUFF (owner request).

    python3 tools/make_polished_tuff_armor.py      # writes an EMPTY armor_extras/tuff_extras.json + the four item icons, then run rebuild_all.py

* Worn layers: paint_textures.py (_tuff_layer) recolours the real netherite layer textures with the real polished_tuff.png (tuffpal.py).
* Item icons: the vanilla netherite helmet / chestplate / leggings / boots icons (tools/reference/vanilla_items) recoloured the same way and
  doubled to 32x32 for sprite_extrude.py (which makes the 3D item models).
* No extra 3D geometry, no glow, no cracks any more: tuff_extras.json has no pieces (the renderer then draws nothing extra). The old
  glow-pass code (ArmorExtrasFeatureRenderer glow texture, paintlib GLOW_FNS / shell, make_tuff_armor.py) is kept only as an unused tool.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import tuffpal

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "shrinecraft", "armor_extras", "tuff_extras.json")
ICONS = os.path.join(ROOT, "tools", "reference", "icons")
SRC = os.path.join(ROOT, "tools", "reference", "vanilla_items")


def icon(piece, vanilla, seed):
    import numpy as np
    src = Image.open(os.path.join(SRC, vanilla + ".png")).convert("RGBA")
    arr = np.zeros((src.height, src.width, 4), dtype=np.uint8)
    for y in range(src.height):
        for x in range(src.width):
            r, g, b, a = src.getpixel((x, y))
            if a:
                arr[y, x] = tuffpal.polished_tuff(x, y, 0.3 * r + 0.59 * g + 0.11 * b) + (255,)
    tuffpal.weather(arr, seed, scale=2.0)            # same variation as the worn layers (same seed per piece)
    out = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(arr.shape[0]):
        for x in range(arr.shape[1]):
            if arr[y, x, 3]:
                col = tuple(int(v) for v in arr[y, x])
                for dx in (0, 1):
                    for dy in (0, 1):
                        out.putpixel((2 * x + dx, 2 * y + dy), col)
    out.save(os.path.join(ICONS, piece + ".png"))


def main():
    data = {
        "_help": "No extra armor geometry any more (the armor is vanilla netherite shape textured as polished tuff). Kept so the renderer finds an empty definition.",
        "fine_scale": 0.5, "groups": {}, "texture": "shrinecraft:textures/entity/armor_extra/tuff_extras.png",
        "texture_size": [512, 512],
        "pieces": {"shrine_helmet": [], "shrine_chestplate": [], "shrine_leggings": [], "shrine_boots": []},
    }
    with open(OUT, "w") as f:
        json.dump(data, f)
    for piece, vanilla, seed in (("shrine_helmet", "netherite_helmet", 11), ("shrine_chestplate", "netherite_chestplate", 23),
                                 ("shrine_leggings", "netherite_leggings", 37), ("shrine_boots", "netherite_boots", 51)):
        icon(piece, vanilla, seed)
    print("polished tuff netherite armor: icons + empty extras written")


if __name__ == "__main__":
    main()
