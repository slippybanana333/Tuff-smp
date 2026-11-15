#!/usr/bin/env python3
"""
set_cannon_ready.py - sets the hand pose of the Sculk Cannon's CHARGED / DRAWING states (pulling_0, pulling_1, pulling_2, charged all share it).
Writes the display block into the four model files AND into tools/display_overrides.json (so retune_display.py / gen_models.py keep it).

    python3 tools/set_cannon_ready.py --third "-90,0,-180 | 0,-3.506,9.118 | 1.0" --first "-180,0,-180 | -3.249,7.72,2.599 | 0.6"

Each argument is "rotation | translation | scale" for that view. Leave one out to keep it as it is.
"""
import argparse
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODELS = os.path.join(ROOT, "src/main/resources/assets/shrinecraft/models/item")
NAMES = ["sculk_cannon_pulling_0", "sculk_cannon_pulling_1", "sculk_cannon_pulling_2", "sculk_cannon_charged"]


def parse(txt):
    r, t, s = [p.strip() for p in txt.split("|")]
    f = lambda v: [float(x) for x in v.split(",")]
    sc = float(s)
    return {"rotation": f(r), "translation": f(t), "scale": [sc, sc, sc]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--third")
    ap.add_argument("--first")
    a = ap.parse_args()
    new = {}
    if a.third:
        new["thirdperson_righthand"] = parse(a.third)
    if a.first:
        new["firstperson_righthand"] = parse(a.first)
    ov_path = os.path.join(ROOT, "tools/display_overrides.json")
    ov = json.load(open(ov_path, encoding="utf-8"))
    for n in NAMES:
        p = os.path.join(MODELS, n + ".json")
        d = json.load(open(p, encoding="utf-8"))
        d.setdefault("display", {}).update(new)
        json.dump(d, open(p, "w", encoding="utf-8"), indent=2)
        ov.setdefault(n, {}).update(new)
    json.dump(ov, open(ov_path, "w", encoding="utf-8"), indent=2)
    print("set", list(new), "on", len(NAMES), "models")


if __name__ == "__main__":
    main()
