"""
Recompute ONLY the `display` transforms (hand / gui / ground / frame / head) of the existing item models.
Use this after changing FIRST_DIRS, FIRST_SCALE, THIRD_*, GUI_ROT ... in gen_models.py.  Unlike `gen_models.py --force` it does NOT touch
the element geometry, UVs or textures, so hand edits to the shapes are kept.

    python3 tools/retune_display.py          # all items
    python3 tools/retune_display.py cloud_lance molten_greataxe
"""
import os
import sys

import gen_models
import modelspec
from common import ROOT, load_json, model_path, save_json


def main(names):
    done = {}
    specs = modelspec.all_specs()
    for s in sorted(specs, key=lambda sp: gen_models.variant_base(sp.name) is not None):   # base items first, then their variants
        if names and s.name not in names:
            continue
        if load_json(os.path.join(ROOT, "tools", "model_materials.json")).get(s.name, {}).get("sprite"):
            continue                       # sprite models use the vanilla display sets (sprite_extrude.py)
        path = model_path(("block/" if s.kind == "block" else "") + s.name)
        model = load_json(path)
        base = gen_models.variant_base(s.name)
        if base and base in done:
            model["display"] = gen_models.apply_overrides(s.name, dict(done[base]))          # same transforms as the base item (+ the owner's saved edits)
        else:
            model["display"] = gen_models.build_display(s.kind, model, s.name)
        done[s.name] = model["display"]
        save_json(path, gen_models.ordered(model))
        d = model["display"].get("firstperson_righthand", {})
        print("%-30s first person: scale %s rot %s" % (s.name, d.get("scale", ["-"])[0], d.get("rotation")))


if __name__ == "__main__":
    main(set(sys.argv[1:]))
