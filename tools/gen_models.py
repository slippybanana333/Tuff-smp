#!/usr/bin/env python3
"""
gen_models.py  -  ONE-TIME SCAFFOLD GENERATOR for the model JSON files, item definitions, block states and equipment assets.

  python3 tools/gen_models.py            -> creates everything, but REFUSES to overwrite model files that already exist
  python3 tools/gen_models.py --force    -> overwrites (throws away hand edits to the model JSON!)

After the first run the JSON files in src/main/resources are the single source of truth.  To change a model, edit the JSON
(or tell Claude "make the blade of the cutlass longer"), then run:   python3 tools/rebuild_all.py
"""
import argparse
import math
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa
import modelspec
import uvlayout


# --------------------------------------------------------------------------------------------------
#  display transforms
# --------------------------------------------------------------------------------------------------

def unit(v):
    v = np.array(v, dtype=float)
    return v / np.linalg.norm(v)


def frame_from(y_dir, z_dir):
    """Right-handed rotation whose columns are where the model's X, Y, Z axes should point."""
    y = unit(y_dir)
    z = np.array(z_dir, dtype=float)
    z = unit(z - np.dot(z, y) * y)
    x = np.cross(y, z)
    return np.column_stack([x, y, z])


def d2(deg):
    return math.radians(deg)


def _held_dirs(pitch_up, yaw_out, roll, model_forward):
    """Third-person direction pair built from 'which way the weapon points'.  pitch_up = degrees the muzzle/spout is raised, yaw_out = degrees it
    swings away from straight ahead, roll = degrees the weapon is canted around its pointing axis (positive = top leans toward the player's
    right, away from the body).  model_forward = 'z' (crossbow: model +Z points ahead) or 'x' (lamp: model +X, the spout, points ahead).
    Found with the clearance search (arm / torso / head / legs overlap) - see MOD_KNOWLEDGE_BASE.md."""
    P, Y, R = math.radians(pitch_up), math.radians(yaw_out), math.radians(roll)
    f = np.array([math.cos(P) * math.sin(Y), math.sin(P), math.cos(P) * math.cos(Y)])
    up = np.array([0.0, 1.0, 0.0])
    u = up - np.dot(up, f) * f
    u /= np.linalg.norm(u)
    u = u * math.cos(R) + np.cross(f, u) * math.sin(R)
    if model_forward == "z":
        return tuple(u), tuple(f)
    return tuple(u), tuple(np.cross(f, u))


def _vanilla_crossbow_dirs():
    """Carry the cannon exactly like the VANILLA crossbow.  The vanilla crossbow sprite is the crossbow seen from above (its length runs along
    the sprite's upper-left/lower-right diagonal, its 'up' is the sprite normal) and vanilla holds it with rotation [-90,0,-60] (third person)
    and [-90,0,-55] (first person).  We map our model (+Z = muzzle, +Y = up, +X = side) onto that sprite frame and reuse vanilla's rotation, so the
    hand closes around the handle and the weapon hangs/points the same way as the vanilla crossbow.  Returns (third-person world dirs, first-person camera dirs)."""
    s2 = math.sqrt(2.0)
    rmap = np.column_stack([np.array([-1, -1, 0]) / s2, np.array([0, 0, 1.0]), np.array([-1, 1, 0]) / s2])
    a = np.diag([1.0, -1.0, -1.0]) @ third_person_chain()[:3, :3]
    rw = a @ (rot_xyz(-90, 0, -60)[:3, :3] @ rmap)
    rf = rot_xyz(-90, 0, -55)[:3, :3] @ rmap
    return (tuple(rw[:, 1]), tuple(rw[:, 2])), (tuple(rf[:, 1]), tuple(rf[:, 2]))


_XBOW_THIRD, _XBOW_FIRST = _vanilla_crossbow_dirs()

THIRD_DIRS = {
    # kind: (model +Y points to (world), model +Z (the detailed front) points to (world)).  world: +z forward, +y up, -x = player's right
    "sword":    ((0, math.sin(d2(8)), math.cos(d2(8))), (1, 0, 0)),     # +1: the Cutlass blade curls UP in the hand (was -1 = blade tip curled downward / upside down)
    "axe":      ((0, math.sin(d2(8)), math.cos(d2(8))), (-1, 0, 0)),
    "pick":     ((0, math.sin(d2(8)), math.cos(d2(8))), (-1, 0, 0)),
    "mace":     ((0, math.sin(d2(8)), math.cos(d2(8))), (-1, 0, 0)),
    "spear":    ((0, math.sin(d2(25)), math.cos(d2(25))), (-1, 0, 0)),
    "trident":  ((0, math.sin(d2(35)), math.cos(d2(35))), (-1, 0, 0)),
    "bow":      ((0, math.cos(d2(48)), math.sin(d2(48))), (0, 0, -1)),   # tilted forward so the limbs clear the forearm
    "crossbow": _held_dirs(-50, 10, 0, "z"),   # NOT charged: hangs down at the side like an uncharged crossbow (muzzle 50 deg down)
    "crossbow_ready": _held_dirs(0, 0, 0, "z"),      # level and straight out from the hand (owner request); no rear stock, so the forearm does not go through the body
    "orb":      _held_dirs(-8, 0, 0, "x"),     # the lamp hangs by its ring handle, spout ahead and dipping a little
    "armor":    ((0, math.cos(d2(30)), math.sin(d2(30))), (-1, 0, 0)),
    "cape":     ((0, math.cos(d2(30)), math.sin(d2(30))), (-1, 0, 0)),
}

# (business-end dir, flat-front dir) in camera space for first person.  camera: x right, y up, z toward the viewer.
# Vanilla handheld items point straight up with their flat side facing the screen centre, so the vanilla sword and axe run off the right
# edge of the screen.  Ours show their detailed FRONT to the player and lean ~25 degrees toward the screen centre so the whole item
# (and its business end) stays in view.  The fit rule in first_person_display() shrinks an item further if it still would not fit.
FIRST_DIRS = {
    "sword":    ((-0.45, 0.88, 0.15), (-0.20, 0.0, 0.98)),
    "axe":      ((-0.45, 0.88, 0.15), (-0.20, 0.0, 0.98)),
    "pick":     ((-0.45, 0.88, 0.15), (-0.20, 0.0, 0.98)),
    "mace":     ((-0.45, 0.88, 0.15), (-0.20, 0.0, 0.98)),
    "spear":    ((-0.40, 0.90, 0.15), (-0.20, 0.0, 0.98)),
    "trident":  ((-0.40, 0.90, 0.15), (-0.20, 0.0, 0.98)),
    "bow":      ((-0.30, 0.94, 0.15), (-0.94, -0.30, 0.0)),   # the bow is a flat shape in the model Y/Z plane, so its +X side faces the camera
    "crossbow": ((0.0, 0.85, -0.5), (-0.10, -0.55, -0.82)),   # NOT charged: hangs down in front of the hand
    "crossbow_ready": ((0.0, 1.0, 0.0), (-0.12, 0.03, -1.0)),   # charged / being drawn: level and straight ahead of the hand
    "orb":      ((0, 1, 0), (-0.4, 0, 1)),
    "armor":    ((-0.20, 0.97, 0.10), (-0.25, 0.0, 0.97)),
    "cape":     ((-0.20, 0.97, 0.10), (-0.25, 0.0, 0.97)),
}


def _first_from_third(kind, extra_pitch=0.0, extra_yaw=0.0):
    """First-person directions that MATCH the third-person hold (owner request): take the third-person (world) directions and express them in
    camera space (world +z forward -> camera -z, world -x = player's right -> camera +x), then pitch them by extra_pitch degrees
    (positive = muzzle / tip up).  So the item points away from you exactly the way it points away from the body in third person."""
    ydir, zdir = THIRD_DIRS[kind]
    def to_cam(v):
        return np.array([-v[0], v[1], -v[2]], dtype=float)
    yc, zc = to_cam(ydir), to_cam(zdir)
    if extra_yaw:       # swing the tip toward the middle of the screen (positive = toward the left / centre)
        t = math.radians(extra_yaw)
        c, sn = math.cos(t), math.sin(t)
        def yaw(v):
            return np.array([v[0] * c - v[2] * sn, v[1], v[0] * sn + v[2] * c])
        yc, zc = yaw(yc), yaw(zc)
    if extra_pitch:
        t = math.radians(extra_pitch)
        c, sn = math.cos(t), math.sin(t)
        def pitch(v):
            return np.array([v[0], v[1] * c - v[2] * sn, v[1] * sn + v[2] * c])
        yc, zc = pitch(yc), pitch(zc)
    return tuple(yc), tuple(zc)


# Owner request: the first-person pose follows the third-person pose.  extra pitch: the Cloud Lance is tipped further DOWN (like the vanilla spear).
# owner request: these are rotated 15 more degrees TOWARD THE PLAYER (the tip comes back toward you / more upright) in first person
FIRST_TOWARD_YOU = {"pick": 45.0, "spear": 15.0, "mace": 15.0, "sword": 15.0, "axe": 45.0}      # axe + pick: 30 more (owner request)
# extra hand offset per kind (model px: x right, y up, z toward camera).  The Cutlass is pulled down / toward you so the lower handle is off-screen.
FIRST_KIND_SHIFT = {"sword": np.array([0.0, -4.5, 3.0])}
FIRST_EXTRA = {"spear": (-30.0, 29.0)}          # (pitch, yaw); everything else uses FIRST_EXTRA_DEFAULT
FIRST_EXTRA_DEFAULT = (12.0, 29.0)              # a little up and toward the centre, so the whole item is seen instead of being pointed straight away
# Where the hand sits in first person, relative to vanilla's palm (model px): up and a bit toward the centre so the item is not hidden behind the hotbar.
FIRST_NO_CLOSER = ("spear",)      # very long items are not brought closer: they would be shrunk to nothing by the fit rule
FIRST_HAND_SHIFT = np.array([-1.6, 5.0, 2.6])        # z > 0 = toward the camera (a little closer to you)
for _k in list(FIRST_DIRS):
    if _k in THIRD_DIRS:
        _pp, _yy = FIRST_EXTRA.get(_k, FIRST_EXTRA_DEFAULT)
        FIRST_DIRS[_k] = _first_from_third(_k, _pp + FIRST_TOWARD_YOU.get(_k, 0.0), _yy)
FIRST_DIRS["crossbow_ready"] = _first_from_third("crossbow_ready", 0.0, 23.0)
# The bow keeps its own first-person pose: held like the third-person bow its broad side would be edge-on to the camera, so it is turned to show its arc.
def _vanilla_bow_first():
    """First-person bow carried EXACTLY like the vanilla bow.  The vanilla bow sprite runs tip-to-tip along the sprite's lower-left -> upper-right
    diagonal with the string / archer side toward the lower-right, and vanilla holds it with rotation [0,-90,25].  Our model: +Y tip to tip,
    +Z toward the archer (string side), +X = the sprite's normal.  Reusing vanilla's rotation gives the same view of the bow as the vanilla bow."""
    s2 = math.sqrt(2.0)
    rmap = np.column_stack([np.array([0, 0, -1.0]), np.array([1, 1, 0]) / s2, np.array([1, -1, 0]) / s2])
    rf = rot_xyz(0, -90, 25)[:3, :3] @ rmap
    return tuple(rf[:, 1]), tuple(rf[:, 2])


FIRST_DIRS["bow"] = _vanilla_bow_first()

# First-person fit rule (camera = vanilla: vertical FOV 70, 16:9).  Anything may run off the screen edge a little, but at least this
# share of the item's sample points must be visible, nothing may be closer to the camera than FIRST_MIN_DIST blocks, and the centre
# of the item's top 15 % (its business end) must land inside the allowed screen region (normalised device coordinates -1..1).
FIRST_MIN_VISIBLE = 0.62
FIRST_MIN_DIST = 0.25
FIRST_HEAD_MAX_X = 0.90
FIRST_HEAD_MAX_Y = 0.85
FIRST_MIN_SCALE = 0.30

# Weapons are held by their grip (the grip sits where a vanilla sword's grip sits).  Short items you do not "grip" in a visible way
# (armor, cape, orb, bow, crossbow) are instead centred in the lower-right of the view, otherwise they hang off the bottom of the screen.
FIRST_GRIP_PINNED = ("sword", "axe", "pick", "mace", "spear", "trident")
FIRST_CENTER_TARGET = (0.45, -0.20, -0.80)    # blocks, camera space (x right, y up, z toward the viewer)
FIRST_CENTER_MIN_VISIBLE = 0.90

# Third-person scale: vanilla tools are about 19 units long in the hand; ours stay a little bigger than vanilla (they are "legendary") but not 1.4x.
THIRD_SCALE = {"crossbow_ready": 1.0, "sword": 0.8, "axe": 0.68, "pick": 0.75, "mace": 0.72, "spear": 0.78, "trident": 0.8, "bow": 1.0, "crossbow": 1.0,
               "orb": 1.0, "armor": 0.85, "cape": 0.8}
FIRST_SCALE = {"crossbow_ready": 0.6, "sword": 0.8, "axe": 0.75, "pick": 0.75, "mace": 0.75, "spear": 0.6, "trident": 0.65, "bow": 0.8, "crossbow": 0.6,
               "orb": 0.55, "armor": 0.7, "cape": 0.65}

GUI_ROT = {
    "crossbow_ready": (28, -42, 0),
    "sword": (18, -32, -42), "axe": (18, -32, -38), "pick": (18, -32, -38), "mace": (18, -32, -38),
    "spear": (14, -32, -42), "trident": (14, -32, -40), "bow": (0, 90, 45), "crossbow": (28, -42, 0),
    "orb": (20, -28, 0), "armor": (20, -30, 0), "cape": (14, -24, 0), "block": (30, 225, 0),
}


# The bow is a flat shape in the model's Y/Z plane (thin in X), so in frames / on the ground / on the head it is turned a quarter turn
# to show its broad side instead of its edge.  (Sign chosen by looking at it in the viewer.)
BOW_FLAT_YAW = 90


def third_person_display(kind, model):
    ydir, zdir = THIRD_DIRS[kind]
    rw = frame_from(ydir, zdir)
    chain = third_person_chain()[:3, :3]
    a = np.diag([1.0, -1.0, -1.0]) @ chain
    r_item = a.T @ rw          # a is a rotation, so inverse = transpose
    m4 = np.eye(4)
    m4[:3, :3] = r_item
    rot = euler_xyz_from_matrix(m4)
    s = THIRD_SCALE[kind]
    g = grip_point(model)
    r_full = rot_xyz(*rot)
    t = vanilla_palm_third() - (r_full @ sc(s) @ np.array([g[0] - 8, g[1] - 8, g[2] - 8, 1.0]))[:3]
    return {"rotation": [round(v, 2) for v in rot], "translation": [round(float(v), 2) for v in t], "scale": [s, s, s]}


def first_person_score(m, pts):
    """(visible share, closest-approach check, head-centre inside the allowed region) under first-person matrix m (model px -> camera px)."""
    tan_v = math.tan(math.radians(35.0))
    aspect = 16.0 / 9.0
    cam = np.array([xf(m, p) / 16.0 for p in pts])
    z_ok = cam[:, 2] < -0.12
    safe_z = np.minimum(cam[:, 2], -0.12)
    x = cam[:, 0] / (-safe_z) / (tan_v * aspect)
    y = cam[:, 1] / (-safe_z) / tan_v
    visible = float((z_ok & (np.abs(x) <= 1.0) & (np.abs(y) <= 1.0)).mean())
    near_ok = cam[:, 2].max() <= -FIRST_MIN_DIST
    top = pts[:, 1] >= pts[:, 1].min() + 0.85 * (pts[:, 1].max() - pts[:, 1].min())
    head_ok = x[top].mean() <= FIRST_HEAD_MAX_X and y[top].mean() <= FIRST_HEAD_MAX_Y
    return visible, near_ok, head_ok


def first_person_display(kind, model):
    ydir, zdir = FIRST_DIRS[kind]
    rw = frame_from(ydir, zdir)
    m4 = np.eye(4)
    m4[:3, :3] = rw
    rot = euler_xyz_from_matrix(m4)
    r_full = rot_xyz(*rot)
    grip_pinned = kind in FIRST_GRIP_PINNED
    if grip_pinned:
        pin = grip_point(model)
    else:
        lo, hi = model_bounds(model)
        pin = (np.array(lo) + np.array(hi)) / 2
    equip = np.array([0.56 * 16, -0.52 * 16, -0.72 * 16])
    pts = []
    for el in model["elements"]:     # corners + centre of every element = cheap stand-in for the item's visible area
        c = element_corners(el)
        pts.extend(c)
        pts.append(np.mean(c, axis=0))
    pts = np.array(pts)
    need = FIRST_MIN_VISIBLE if grip_pinned else FIRST_CENTER_MIN_VISIBLE
    if kind in FIRST_KIND_SHIFT:
        need = 0.45          # the handle is meant to be off-screen
    best = None
    s = FIRST_SCALE[kind]
    while True:
        off = (r_full @ sc(s) @ np.array([pin[0] - 8, pin[1] - 8, pin[2] - 8, 1.0]))[:3]
        if grip_pinned:
            shift = FIRST_HAND_SHIFT.copy()
            if kind in FIRST_NO_CLOSER:
                shift[2] = 0.0
            shift = shift + FIRST_KIND_SHIFT.get(kind, 0.0)
            t = vanilla_palm_first() + shift - off
        else:
            t = np.array(FIRST_CENTER_TARGET) * 16.0 - equip - off
        vis, near_ok, head_ok = first_person_score(first_person_matrix(t, rot, (s, s, s)), pts)
        if vis >= need and near_ok and (head_ok or not grip_pinned):
            break
        if best is None or vis > best[0]:
            best = (vis, s, t)
        if s <= FIRST_MIN_SCALE + 1e-9:
            _, s, t = best          # nothing fitted: use the scale that shows the most of the item
            break
        s = round(s - 0.02, 3)
    return {"rotation": [round(v, 2) for v in rot], "translation": [round(float(v), 2) for v in t], "scale": [round(s, 3)] * 3}


def corners_of(model):
    pts = []
    for el in model["elements"]:
        pts.extend(element_corners(el))
    return np.array(pts)


def fit_transform(model, rot, target, center_z=True):
    """Scale + translation so the model's projected bounding box is `target` units wide/tall and centred."""
    pts = corners_of(model)
    r = rot_xyz(*rot)[:3, :3]
    q = (pts - 8.0) @ r.T
    lo, hi = q.min(axis=0), q.max(axis=0)
    ext = max(hi[0] - lo[0], hi[1] - lo[1])
    s = min(4.0, target / ext)
    c = (lo + hi) / 2 * s
    t = [-c[0], -c[1], 0.0]
    return t, s


def gui_display(kind, model):
    rot = GUI_ROT[kind]
    t, s = fit_transform(model, rot, 15.0 if kind != "block" else 16.0)
    if kind == "block":
        return {"rotation": list(rot), "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]}
    return {"rotation": list(rot), "translation": [round(v, 2) for v in t], "scale": [round(s, 3)] * 3}


def ground_display(kind, model):
    rot = (0, BOW_FLAT_YAW - 180, 0) if kind == "bow" else (0, 0, 0)
    if kind == "block":
        return {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]}
    t, s = fit_transform(model, rot, 9.0)
    return {"rotation": [0, 0, 0], "translation": [round(t[0], 2), 3 + round(t[1], 2), 0], "scale": [round(s, 3)] * 3}


def fixed_display(kind, model):
    if kind == "block":
        return {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]}
    tilt = {"sword": 45, "axe": 45, "pick": 45, "mace": 45, "spear": 45, "trident": 45}.get(kind, 0)
    rot = (0, BOW_FLAT_YAW if kind == "bow" else 180, tilt)
    t, s = fit_transform(model, rot, 15.0)
    return {"rotation": list(rot), "translation": [round(v, 2) for v in t], "scale": [round(s, 3)] * 3}


def head_display(kind, model):
    if kind == "block":
        return {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    rot = (0, BOW_FLAT_YAW if kind == "bow" else 180, 0)
    t, s = fit_transform(model, rot, 9.0)
    return {"rotation": list(rot), "translation": [round(t[0], 2), 13 + round(t[1], 2), 7], "scale": [round(s, 3)] * 3}


def block_display(model):
    """The standard vanilla block display transforms (same numbers as minecraft:block/block)."""
    return {
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
        "on_shelf": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    }


def variant_base(name):
    """frost_longbow_pulling_1 -> frost_longbow, sculk_cannon_charged -> sculk_cannon (None for a normal item).
    Draw / charge states must share the base item's display transforms, otherwise the item would jump in size or position mid-draw."""
    if name.startswith("sculk_cannon_"):
        return None        # the cannon's draw / charged states get their own (level, raised) display - see _is_ready()
    for marker in ("_pulling_", "_charged", "_released", "_cooldown"):
        if marker in name:
            return name.split(marker)[0]
    return None


def _is_ready(name):
    """The Sculk Cannon while being drawn or loaded is held LEVEL (raised, aiming); idle it hangs down."""
    return bool(name) and name.startswith("sculk_cannon_")


def apply_overrides(name, display):
    """Positions saved from the Display Editor (tools/display_overrides.json, written by tools/apply_display.py) always win over the computed ones."""
    path = os.path.join(ROOT, "tools", "display_overrides.json")
    if name and os.path.exists(path):
        for key, blk in load_json(path).get(name, {}).items():
            display[key] = dict(blk)
    return display


def build_display(kind, model, name=None):
    if kind == "block":
        return block_display(model)
    if kind == "crossbow" and _is_ready(name):
        kind = "crossbow_ready"
    d = {
        "thirdperson_righthand": third_person_display(kind, model),
        "firstperson_righthand": first_person_display(kind, model),
        "gui": gui_display(kind, model),
        "ground": ground_display(kind, model),
        "fixed": fixed_display(kind, model),
        "head": head_display(kind, model),
    }
    d["on_shelf"] = dict(d["fixed"])
    return apply_overrides(name, d)


# --------------------------------------------------------------------------------------------------
#  model json
# --------------------------------------------------------------------------------------------------

def spec_to_model(spec):
    is_block = spec.kind == "block"
    tex_ns = "shrinecraft:block/" if is_block else "shrinecraft:item/"
    elements = []
    k = spec.scale

    def sp(p):
        return [round(8 + (p[0] - 8) * k, 3), round(p[1] * k, 3), round(8 + (p[2] - 8) * k, 3)]

    # Minecraft refuses element coordinates outside -16..32. Long items (the lance) are shifted down in Y so they fit.
    all_y = [sp(e["from"])[1] for e in spec.els] + [sp(e["to"])[1] for e in spec.els]
    shift_y = 0.0
    if max(all_y) > 31.0:
        shift_y = -(max(all_y) - 31.0)
    elif min(all_y) < -15.0:
        shift_y = -15.0 - min(all_y)
    if shift_y:
        print("  %s: shifted by %.2f in Y to fit the -16..32 limit" % (spec.name, shift_y))
    _sp = sp

    def sp(p):
        q = _sp(p)
        return [q[0], round(q[1] + shift_y, 3), q[2]]

    for e in spec.els:
        el = {"name": e["name"], "from": sp(e["from"]), "to": sp(e["to"])}
        if "rotation" in e:
            r = dict(e["rotation"])
            r["origin"] = sp(r["origin"])
            el["rotation"] = r
        faces = {}
        for f in FACES:
            if f in e.get("skip", ()):
                continue
            faces[f] = {"uv": [0, 0, 1, 1], "texture": "#0"}
        el["faces"] = faces
        elements.append(el)
    model = {
        "credit": "Tuff SMP - elements are NAMED so you can ask for changes by name. UVs are auto-laid-out (tools/uvlayout.py); textures are painted by tools/paint_textures.py.",
        "texture_size": [16, 16],
        "textures": {"0": tex_ns + spec.texture, "particle": tex_ns + spec.texture},
        "gui_light": "side",
        "elements": elements,
    }
    if is_block:
        model["ambientocclusion"] = False
    return model


def ordered(model):
    keys = ["credit", "texture_size", "textures", "ambientocclusion", "gui_light", "elements", "display"]
    return {k: model[k] for k in keys if k in model}


def item_definition(name):
    simple = {"model": {"type": "minecraft:model", "model": f"shrinecraft:item/{name}"}}
    if name == "frost_longbow":
        return {"model": {
            "type": "minecraft:condition", "property": "minecraft:using_item",
            "on_false": {"type": "minecraft:model", "model": "shrinecraft:item/frost_longbow"},
            "on_true": {
                "type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                "entries": [
                    {"threshold": 0.65, "model": {"type": "minecraft:model", "model": "shrinecraft:item/frost_longbow_pulling_1"}},
                    {"threshold": 0.9, "model": {"type": "minecraft:model", "model": "shrinecraft:item/frost_longbow_pulling_2"}},
                ],
                "fallback": {"type": "minecraft:model", "model": "shrinecraft:item/frost_longbow_pulling_0"},
            },
        }}
    if name == "sculk_cannon":
        return {"model": {
            "type": "minecraft:select", "property": "minecraft:charge_type",
            "cases": [
                {"when": "arrow", "model": {"type": "minecraft:model", "model": "shrinecraft:item/sculk_cannon_charged"}},
                {"when": "rocket", "model": {"type": "minecraft:model", "model": "shrinecraft:item/sculk_cannon_charged"}},
            ],
            "fallback": {
                "type": "minecraft:condition", "property": "minecraft:using_item",
                "on_false": {"type": "minecraft:model", "model": "shrinecraft:item/sculk_cannon"},
                "on_true": {
                    "type": "minecraft:range_dispatch", "property": "minecraft:crossbow/pull",
                    "entries": [
                        {"threshold": 0.58, "model": {"type": "minecraft:model", "model": "shrinecraft:item/sculk_cannon_pulling_1"}},
                        {"threshold": 1.0, "model": {"type": "minecraft:model", "model": "shrinecraft:item/sculk_cannon_pulling_2"}},
                    ],
                    "fallback": {"type": "minecraft:model", "model": "shrinecraft:item/sculk_cannon_pulling_0"},
                },
            },
        }}
    if name == "silvers_spawn_orb":      # model follows the orb's state; SilversSpawnOrbItem.setData writes the custom_model_data string
        def m(n):
            return {"type": "minecraft:model", "model": f"shrinecraft:item/{n}"}
        return {"model": {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": "released", "model": m("silvers_spawn_orb_released")},
                      {"when": "cooldown", "model": m("silvers_spawn_orb_cooldown")}],
            "fallback": m("silvers_spawn_orb"),
        }}
    return simple


def regenerate_only(specs, names):
    """Rebuild just some models from tools/modelspec.py (their hand edits are discarded; other models are untouched)."""
    chosen = [s for s in specs if s.name in names]
    if not chosen:
        print("no such model in modelspec.py:", names)
        return 1
    models = {s.name: spec_to_model(s) for s in chosen}
    for g in sorted({s.group for s in chosen}):
        print("atlas", g, uvlayout.layout_group([models[s.name] for s in chosen if s.group == g]))
    for s in chosen:
        models[s.name]["display"] = build_display(s.kind, models[s.name], s.name)
    for s in chosen:                                  # draw/charge variants copy the base item's transforms
        base = variant_base(s.name)
        if base and base in models:
            models[s.name]["display"] = apply_overrides(s.name, dict(models[base]["display"]))
    for s in chosen:
        save_json(model_path(("block/" if s.kind == "block" else "") + s.name), ordered(models[s.name]))
    mpath = os.path.join(ROOT, "tools", "model_materials.json")
    meta = json.load(open(mpath))
    for s in chosen:
        meta[s.name] = {"texture": s.texture, "group": s.group, "kind": s.kind, "block": s.kind == "block", "default_mat": s.default_mat,
                        "state": getattr(s, "state", None), "elements": {e["name"]: e["mat"] for e in s.els}}
    save_json(mpath, meta)
    print("regenerated:", ", ".join(models))
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--force", action="store_true", help="overwrite existing model json files")
    ap.add_argument("--only", nargs="+", metavar="NAME", help="regenerate ONLY these models (geometry + display + materials), keep everything else")
    args = ap.parse_args()

    specs = modelspec.all_specs()
    if args.only:
        return regenerate_only(specs, args.only)
    existing = [s for s in specs if os.path.exists(model_path(("block/" if s.kind == "block" else "") + s.name))]
    if existing and not args.force:
        print("Model files already exist (%d). Not overwriting. Use --force to regenerate (this discards hand edits)." % len(existing))
        return 1

    models = {}
    for s in specs:
        models[s.name] = spec_to_model(s)

    # texture atlases per group
    groups = {}
    for s in specs:
        groups.setdefault(s.group, []).append(s)
    for g, members in groups.items():
        size = uvlayout.layout_group([models[m.name] for m in members])
        print("atlas", g, size)

    # display transforms (need final element lists)
    for s in specs:
        m = models[s.name]
        kind = s.kind
        m["display"] = build_display(kind, m, s.name)
    for s in specs:                                   # draw/charge variants copy the base item's transforms
        base = variant_base(s.name)
        if base and base in models:
            models[s.name]["display"] = apply_overrides(s.name, dict(models[base]["display"]))

    meta = {}
    for s in specs:
        m = models[s.name]
        is_block = s.kind == "block"
        save_json(model_path(("block/" if is_block else "") + s.name), ordered(m))
        meta[s.name] = {
            "texture": s.texture, "group": s.group, "kind": s.kind, "block": is_block, "default_mat": s.default_mat,
            "state": getattr(s, "state", None), "elements": {e["name"]: e["mat"] for e in s.els},
        }
    save_json(os.path.join(ROOT, "tools", "model_materials.json"), meta)

    # item definitions
    for n in ITEM_NAMES:
        save_json(os.path.join(ITEM_DEFS, n + ".json"), item_definition(n))
    save_json(os.path.join(ITEM_DEFS, "shrine.json"), {"model": {"type": "minecraft:model", "model": "shrinecraft:block/shrine_unconfigured"}})

    # block state
    save_json(os.path.join(ASSETS, "blockstates", "shrine.json"), {"variants": {
        "state=unconfigured": {"model": "shrinecraft:block/shrine_unconfigured"},
        "state=configured": {"model": "shrinecraft:block/shrine_configured"},
        "state=claimed": {"model": "shrinecraft:block/shrine_claimed"},
    }})

    # equipment assets (worn armor + the cape's wing layer)
    for n in ARMOR_NAMES:
        save_json(os.path.join(EQUIP_DIR, n + ".json"), {"layers": {
            "humanoid": [{"texture": "shrinecraft:" + n}],
            "humanoid_leggings": [{"texture": "shrinecraft:" + n}],
        }})
    save_json(os.path.join(EQUIP_DIR, "strange_cape.json"), {"layers": {"wings": [{"texture": "shrinecraft:strange_cape", "use_player_texture": False}]}})
    print("generated %d models" % len(specs))
    return 0


if __name__ == "__main__":
    sys.exit(main())
