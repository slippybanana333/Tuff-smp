#!/usr/bin/env python3
"""
apply_display.py - applies the position changes saved from the Display Editor (the "Copy changes" text) to the mod.

    python3 tools/apply_display.py changes.json        # a file you saved / uploaded
    python3 tools/apply_display.py - < changes.json    # or paste into stdin

What it does:
  1. merges the changes into tools/display_overrides.json  (the permanent record - gen_models.py / retune_display.py re-apply it, so a later
     rebuild never throws your hand-tuned positions away),
  2. writes them into the model files (assets/shrinecraft/models/item/<name>.json 'display' block; 'fixed' is also copied to 'on_shelf'),
  3. you then run  python3 tools/rebuild_all.py --jar  to rebuild the mod + texture pack.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa

OVR = os.path.join(ROOT, "tools", "display_overrides.json")


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "-"
    txt = sys.stdin.read() if src == "-" else open(src, encoding="utf-8").read()
    data = json.loads(txt)
    if data.get("format") not in ("tuffsmp-display-v1", "swagsmp-display-v1"):       # the old name is still accepted (saved before the Tuff rename)
        sys.exit("This is not a Display Editor export (format tuffsmp-display-v1).")
    ovr = load_json(OVR) if os.path.exists(OVR) else {}
    n = 0
    for name, keys in (data.get("models") or {}).items():
        path = model_path(name)
        if not os.path.exists(path):
            print("skip (no such model):", name)
            continue
        model = load_json(path)
        disp = model.setdefault("display", {})
        for key, blk in keys.items():
            clean = {"rotation": [round(float(v), 2) for v in blk["rotation"]], "translation": [round(float(v), 2) for v in blk["translation"]],
                     "scale": [round(float(v), 3) for v in blk["scale"]]}
            disp[key] = clean
            ovr.setdefault(name, {})[key] = clean
            if key == "fixed":
                disp["on_shelf"] = dict(clean)
                ovr[name]["on_shelf"] = dict(clean)
            n += 1
        save_json(path, model)
        print("updated", name, "->", ", ".join(keys))
    # worn pieces (cape / armor): written to assets/shrinecraft/worn_display.json (read by the game and by the editor)
    worn_path = os.path.join(ASSETS, "worn_display.json")
    for piece, blk in (data.get("worn") or {}).items():
        wd = load_json(worn_path)
        if piece not in wd["pieces"]:
            print("skip (no such worn piece):", piece)
            continue
        for k in ("translation", "rotation", "scale"):
            wd["pieces"][piece][k] = [round(float(v), 3) for v in blk[k]]
        # per-part edits (horns, crown, shoulders ...): replace the whole set; identity parts are simply left out
        groups = {}
        for gname, gb in (blk.get("groups") or {}).items():
            t = [round(float(v), 3) for v in gb.get("translation", [0, 0, 0])]
            s = [round(float(v), 3) for v in gb.get("scale", [1, 1, 1])]
            if any(t) or s != [1.0, 1.0, 1.0]:
                groups[gname] = {"translation": t, "scale": s}
        if groups:
            wd["pieces"][piece]["groups"] = groups
        else:
            wd["pieces"][piece].pop("groups", None)
        save_json(worn_path, wd)
        print("updated worn placement of", piece)
        n += 1
    save_json(OVR, ovr)
    print("%d position setting(s) applied. Now run:  python3 tools/rebuild_all.py --jar" % n)


if __name__ == "__main__":
    main()
