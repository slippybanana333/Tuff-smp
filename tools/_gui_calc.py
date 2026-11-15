#!/usr/bin/env python3
"""TEMP: compute an upright (like the ground view) gui display for the cutlass."""
import json, sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
import gen_models
from common import load_json

m = load_json(os.path.join(gen_models.ROOT, "src/main/resources/assets/shrinecraft/models/item/cutlass_of_doom.json"))
rot = (0, 0, 0)
t, s = gen_models.fit_transform(m, rot, 15.0)
print("cutlass gui rot", rot, "t", [round(v, 2) for v in t], "s", round(s, 3))
