#!/usr/bin/env python3
"""TEMP: grip-pinned translation for the new Cloud Lance first-person rotation."""
import json, sys, os
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
from common import rot_xyz

m = json.load(open("src/main/resources/assets/shrinecraft/models/item/cloud_lance.json"))
g_el = [e for e in m["elements"] if e["name"].startswith("grip")][0]
c = [(a + b) / 2.0 for a, b in zip(g_el["from"], g_el["to"])]
g = np.array([c[0] - 8.0, c[1] - 8.0, c[2] - 8.0, 1.0])

def t_for(rot_new, rot_old, t_old, s):
    M_old = rot_xyz(*rot_old)[:3, :3]
    M_new = rot_xyz(*rot_new)[:3, :3]
    t = np.array(t_old, dtype=float) + (M_old - M_new) @ (s * g[:3])
    return [round(float(v), 3) for v in t]

print("cloud_lance firstperson rot [-18, -75.68, -74.3]  t", t_for((-18, -75.68, -74.3), (18, 75.68, -74.3), (-0.4, 3.0, 1.4), 0.67))
