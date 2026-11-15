#!/usr/bin/env python3
"""
make_vanilla_refs.py - builds VANILLA reference models for the display editor (tools/reference/vanilla_models/*.json + tools/reference/vanilla_items/*.png).
Each one is the real vanilla item sprite extruded 1 unit thick (like vanilla's own 'generated' item models) with the REAL vanilla display
transforms read from the Minecraft 1.21.11 jar (parent chain: handheld / handheld_mace / bow / crossbow / spear_in_hand / generated).
The viewer shows them next to ours so the owner can line up the position / rotation with what vanilla does.

    python3 tools/make_vanilla_refs.py
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

_JARS = glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/*/minecraft-client.jar")) \
    + glob.glob("/root/.gradle/caches/fabric-loom/*/minecraft-client.jar")
if not _JARS:
    sys.exit("no Minecraft client jar in the fabric-loom gradle cache - run a gradle build first")
JAR = sorted(_JARS)[-1]
OUT_M = os.path.join(REF_DIR, "vanilla_models")
OUT_T = os.path.join(REF_DIR, "vanilla_items")
# viewer name -> (model json in the jar, texture in the jar)
REFS = {
    "netherite_sword": ("netherite_sword", "netherite_sword"),
    "netherite_axe": ("netherite_axe", "netherite_axe"),
    "netherite_pickaxe": ("netherite_pickaxe", "netherite_pickaxe"),
    "netherite_spear": ("netherite_spear_in_hand", "netherite_spear_in_hand"),
    "mace": ("mace", "mace"),
    "bow": ("bow", "bow"),
    "crossbow": ("crossbow", "crossbow_standby"),
    "trident": ("trident", "trident"),
}
GENERATED = {          # builtin/generated.json (no gui entry = identity)
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
}
KEYS = ("gui", "ground", "fixed", "head", "thirdperson_righthand", "firstperson_righthand")


def jar_json(z, name):
    try:
        return json.loads(z.read("assets/minecraft/models/item/%s.json" % name))
    except KeyError:
        return None


def display_of(z, name):
    """Merge the display blocks up the parent chain (child wins)."""
    chain = []
    n = name
    while n:
        j = jar_json(z, n)
        if j is None:
            break
        chain.append(j)
        p = j.get("parent", "")
        n = p.split("/")[-1] if p and not p.endswith("generated") else None
    disp = {k: dict(v) for k, v in GENERATED.items()}
    for j in reversed(chain):
        for k, v in (j.get("display") or {}).items():
            disp[k] = v
    return {k: disp[k] for k in KEYS if k in disp}


def sprite_model(png, display):
    im = Image.open(png).convert("RGBA")
    a = np.array(im)
    H, W = a.shape[:2]
    u = 16.0 / W
    els = []
    for y in range(H):
        for x in range(W):
            if a[y, x, 3] == 0:
                continue
            x0, x1 = x * u, (x + 1) * u
            y1, y0 = (H - y) * u, (H - y - 1) * u
            px = lambda c, r: [round(c * 16.0 / W, 4), round(r * 16.0 / H, 4)]
            faces = {"south": {"uv": [x * u, y * u, (x + 1) * u, (y + 1) * u], "texture": "#0"},
                     "north": {"uv": [(x + 1) * u, y * u, x * u, (y + 1) * u], "texture": "#0"}}
            nb = lambda dx, dy: 0 <= x + dx < W and 0 <= y + dy < H and a[y + dy, x + dx, 3] > 0
            if not nb(0, -1):
                faces["up"] = {"uv": [x * u, y * u, (x + 1) * u, (y + 0.5) * u], "texture": "#0"}
            if not nb(0, 1):
                faces["down"] = {"uv": [x * u, (y + 0.5) * u, (x + 1) * u, (y + 1) * u], "texture": "#0"}
            if not nb(-1, 0):
                faces["west"] = {"uv": [x * u, y * u, (x + 0.5) * u, (y + 1) * u], "texture": "#0"}
            if not nb(1, 0):
                faces["east"] = {"uv": [(x + 0.5) * u, y * u, (x + 1) * u, (y + 1) * u], "texture": "#0"}
            els.append({"name": "px", "from": [round(x0, 4), round(y0, 4), 7.5], "to": [round(x1, 4), round(y1, 4), 8.5], "faces": faces})
    return {"texture_size": [16, 16], "textures": {"0": "vanilla:item"}, "elements": els, "display": display}


def main():
    os.makedirs(OUT_M, exist_ok=True)
    os.makedirs(OUT_T, exist_ok=True)
    with zipfile.ZipFile(JAR) as z:
        for name, (model, tex) in REFS.items():
            png = os.path.join(OUT_T, name + ".png")
            with open(png, "wb") as f:
                f.write(z.read("assets/minecraft/textures/item/%s.png" % tex))
            disp = display_of(z, model)
            if name == "trident":      # vanilla draws the held trident with a special renderer; use the plain handheld pose for reference
                disp.update(display_of(z, "netherite_sword"))
            m = sprite_model(png, disp)
            with open(os.path.join(OUT_M, name + ".json"), "w") as f:
                json.dump(m, f, separators=(",", ":"))
            print("%-18s %3d boxes  first person %s" % (name, len(m["elements"]), disp["firstperson_righthand"]["rotation"]))


if __name__ == "__main__":
    main()
