#!/usr/bin/env python3
"""TEMP: compute the display translations that keep the GRIP pinned at the same hand point when the
rotation changes. p_world = t + R*S*(g-8) (Minecraft applies translate after rotate/scale), so a new
rotation R1 with the old pose (R0, t0) pinned correctly needs t1 = t0 + (R0 - R1) @ (S*(g-8)).
For the Sculk Cannon the KNOWN-GOOD pinned pose is the charged one (verified by the cannon-aim shots),
so the uncharged base pose derives from it.
"""
import json, sys, os
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
from common import rot_xyz

ITEMS = "src/main/resources/assets/shrinecraft/models/item"

def grip_centre(item):
    m = json.load(open(os.path.join(ITEMS, item + ".json")))
    g = [e for e in m["elements"] if e["name"].startswith("grip")][0]
    c = [(a + b) / 2.0 for a, b in zip(g["from"], g["to"])]
    return np.array([c[0] - 8.0, c[1] - 8.0, c[2] - 8.0, 1.0])

def t1_for(item, rot0, t0, s, rot1):
    g = grip_centre(item)
    M0 = rot_xyz(*rot0)[:3, :3]
    M1 = rot_xyz(*rot1)[:3, :3]
    t0v = np.array(t0, dtype=float)
    t1 = t0v + (M0 - M1) @ (s * g[:3])
    return [round(float(v), 3) for v in t1]

lance = t1_for("cloud_lance", (7, 90, 0), (0, -0.7, 1.58), 0.96, (5, 270, -40))
anak = t1_for("anaklusmos", (9, 90, 0), (0, -3.03, 1.49), 1.15, (0, -90, 55))
cannon = t1_for("sculk_cannon", (-90, 0, -180), (0, -3.506, 9.118), 1.0, (-90, 0, -60))
print("cloud_lance  rot", (5, 270, -40), " t", lance)
print("anaklusmos   rot", (0, -90, 55), " t", anak)
print("sculk_cannon rot", (-90, 0, -60), " t", cannon)
