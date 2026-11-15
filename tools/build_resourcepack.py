#!/usr/bin/env python3
"""
build_resourcepack.py - zips the mod's look (models, item definitions, textures, worn-armor layers, GUI backgrounds, language file)
into a standalone Minecraft resource pack:   build/TuffSMP-ResourcePack.zip

The mod jar already contains the same files, so the mod works on its own; the pack is the editable/overridable copy for the owner
(drop it in .minecraft/resourcepacks, or the server's resource pack, to change or replace textures without rebuilding the mod).
"""
import json
import os
import sys
import zipfile

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa

OUT = os.path.join(ROOT, "build", "TuffSMP-ResourcePack.zip")
PACK_FORMAT = 75        # Minecraft 1.21.11 (resource_major in version.json)


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    # pack.png: the owner's photo icon (tools/make_mod_icon.py) if it exists, else the old orb icon
    owner_icon = os.path.join(REF_DIR, "pack_icon.png")
    if os.path.exists(owner_icon):
        icon = Image.open(owner_icon).convert("RGBA").resize((128, 128), Image.LANCZOS)
    else:
        icon = Image.open(os.path.join(REF_DIR, "icons", "silvers_spawn_orb.png")).convert("RGBA").resize((128, 128), Image.NEAREST)
    mcmeta = {"pack": {"description": "Tuff SMP - textures, models and GUI", "pack_format": PACK_FORMAT,
                       "supported_formats": [PACK_FORMAT, PACK_FORMAT]}}
    count = 0
    with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps(mcmeta, indent=2))
        tmp = os.path.join(ROOT, "build", "_pack.png")
        icon.save(tmp)
        z.write(tmp, "pack.png")
        os.remove(tmp)
        for dirpath, _, files in os.walk(ASSETS):
            for f in sorted(files):
                full = os.path.join(dirpath, f)
                z.write(full, os.path.join("assets", "shrinecraft", os.path.relpath(full, ASSETS)))
                count += 1
    print("resource pack:", os.path.relpath(OUT, ROOT), "-", count, "files,", round(os.path.getsize(OUT) / 1024), "KB")


if __name__ == "__main__":
    main()
