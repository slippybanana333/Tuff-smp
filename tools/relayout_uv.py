#!/usr/bin/env python3
"""
relayout_uv.py - recomputes every face's UV rectangle (and texture_size) from the CURRENT element sizes in the model json files.
Run this after changing an element's from/to (e.g. "make the blade longer"), then repaint:   python3 tools/rebuild_all.py
UVs are automatic (1 texel per model unit); hand-edited UVs are overwritten.  Geometry, names and display transforms are untouched.
"""
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa
import uvlayout


def main():
    meta = load_json(os.path.join(ROOT, "tools", "model_materials.json"))
    groups = {}
    for name, m in meta.items():
        if m.get("sprite"):          # made by sprite_extrude.py from the owner's icon: UVs are exact pixels, never re-laid out
            continue
        groups.setdefault((m["group"], m["block"]), []).append(name)
    for (group, is_block), names in groups.items():
        models = []
        for n in names:
            p = model_path(("block/" if is_block else "") + n)
            models.append((p, load_json(p)))
        size = uvlayout.layout_group([m for _, m in models])
        for p, m in models:
            save_json(p, m)
        print("relayout", group, size)


if __name__ == "__main__":
    main()
