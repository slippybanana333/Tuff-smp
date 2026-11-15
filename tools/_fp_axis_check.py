#!/usr/bin/env python3
"""TEMP: first-person head-axis check for the Cloud Lance.
Camera space (common.py first_person_matrix): +x right on screen, +y up, +z TOWARD the player.
We want the head axis (+Y of the model) to point AWAY (z < 0) and up (y > 0).
"""
import sys, os
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
from common import rot_xyz, display_matrix

def head_axis(rot):
    m = display_matrix((0, 0, 0), rot, (1, 1, 1))
    d = m[:3, :3] @ np.array([0.0, 1.0, 0.0])
    return d / np.linalg.norm(d)

def front_axis(rot):
    m = display_matrix((0, 0, 0), rot, (1, 1, 1))
    d = m[:3, :3] @ np.array([0.0, 0.0, 1.0])
    return d / np.linalg.norm(d)

cases = [
    ("current      [18, 75.68, -74.3]", (18, 75.68, -74.3)),
    ("old round1   [41.84, 75.68, -74.3]", (41.84, 75.68, -74.3)),
    ("mirror       [-18, -75.68, 74.3]", (-18, -75.68, 74.3)),
    ("negate Z     [18, 75.68, 74.3]", (18, 75.68, 74.3)),
    ("X + 180      [198, 75.68, -74.3]", (198, 75.68, -74.3)),
    ("X negated    [-18, 75.68, -74.3]", (-18, 75.68, -74.3)),
    ("mirror+negX  [-18, -75.68, -74.3]", (-18, -75.68, -74.3)),
]
for name, rot in cases:
    h = head_axis(rot)
    f = front_axis(rot)
    print("%-36s head -> (%+0.2f, %+0.2f, %+0.2f)   front -> (%+0.2f, %+0.2f, %+0.2f)" % (name, h[0], h[1], h[2], f[0], f[1], f[2]))
print("camera: +z = toward the player, -z = away (forward), +x = screen right")
