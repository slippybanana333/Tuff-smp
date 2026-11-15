#!/usr/bin/env python3
"""TEMP check: where does each display rotation point the model's +Y (head) axis in WORLD space?
World convention (common.py): the player at yaw 0 faces +z, +y up. Third-person 'forward' = +z.
"""
import numpy as np
import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
from common import third_person_world_matrix, rot_xyz

def head_dir(rot):
    m = third_person_world_matrix((0, 0, 0), rot, (1, 1, 1))
    d = m[:3, :3] @ np.array([0.0, 1.0, 0.0])   # direction of the model's +Y axis in world space
    return d / np.linalg.norm(d)

def axes(rot):
    m = third_person_world_matrix((0, 0, 0), rot, (1, 1, 1))
    y = m[:3, :3] @ np.array([0.0, 1.0, 0.0]); y /= np.linalg.norm(y)
    z = m[:3, :3] @ np.array([0.0, 0.0, 1.0]); z /= np.linalg.norm(z)   # model's FRONT (detailed face)
    x = m[:3, :3] @ np.array([1.0, 0.0, 0.0]); x /= np.linalg.norm(x)
    return x, y, z

cases = [
    ("our lance       (7, 90, 0)",        (7, 90, 0)),
    ("vanilla spear   (5, 270, -40)",     (5, 270, -40)),
    ("our anaklusmos  (9, 90, 0)",        (9, 90, 0)),
    ("vanilla trident (0, -90, 55)",      (0, -90, 55)),
    ("our cutlass     (-10, -90, 0)",     (-10, -90, 0)),
    ("vanilla sword   (0, -90, 55)",      (0, -90, 55)),
    ("our cannon base (177.57, -6.41, 172.31)", (177.57, -6.41, 172.31)),
    ("vanilla crossbow (-90, 0, -60)",    (-90, 0, -60)),
    ("our cannon charged (-90, 0, -180)", (-90, 0, -180)),
]
for name, rot in cases:
    x, y, z = axes(rot)
    print("%-38s head(+Y) -> (%+0.2f,%+0.2f,%+0.2f)  front(+Z) -> (%+0.2f,%+0.2f,%+0.2f)  [world: +z fwd, +y up, -x = player's right]" % (name, y[0], y[1], y[2], z[0], z[1], z[2]))
