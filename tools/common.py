"""
Shared helpers for every tool in /tools.

Facts in here were checked against the real 1.21.11 decompiled client:
  * block-model face UV orientation  -> net.minecraft.client.render.model.CubeFace / BakedQuadFactory
  * display transform application    -> net.minecraft.client.render.model.json.Transformation.apply
  * held item chain (third person)   -> HeldItemFeatureRenderer.renderItem
  * held item chain (first person)   -> HeldItemRenderer.applyEquipOffset
  * entity box UV layout             -> net.minecraft.client.model.ModelPart.Cuboid
"""
import json
import math
import os
import sys

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "src", "main", "resources")
CLIENT_RES = os.path.join(ROOT, "src", "client", "resources")
ASSETS = os.path.join(RES, "assets", "shrinecraft")
MODELS_DIR = os.path.join(ASSETS, "models")
ITEM_MODELS = os.path.join(MODELS_DIR, "item")
BLOCK_MODELS = os.path.join(MODELS_DIR, "block")
ITEM_DEFS = os.path.join(ASSETS, "items")
TEX_ITEM = os.path.join(ASSETS, "textures", "item")
TEX_BLOCK = os.path.join(ASSETS, "textures", "block")
TEX_EQUIP = os.path.join(ASSETS, "textures", "entity", "equipment")
TEX_EXTRA = os.path.join(ASSETS, "textures", "entity", "armor_extra")
TEX_GUI = os.path.join(ASSETS, "textures", "gui", "backgrounds")
EQUIP_DIR = os.path.join(ASSETS, "equipment")
EXTRAS_DIR = os.path.join(ASSETS, "armor_extras")
VIEWER_DIR = os.path.join(ROOT, "viewer")
BLOCKBENCH_DIR = os.path.join(ROOT, "blockbench")
REF_DIR = os.path.join(ROOT, "tools", "reference")  # vanilla textures the viewer needs (steve skin etc.)

FACES = ["north", "east", "south", "west", "up", "down"]

# The 14 items + their model names (ids are shrinecraft:<name>)
ITEM_NAMES = [
    "molten_greataxe", "cutlass_of_doom", "sculk_mace", "frost_longbow", "cloud_lance",
    "shrine_helmet", "shrine_chestplate", "shrine_leggings", "shrine_boots",
    "harbinger_of_death", "anaklusmos", "sculk_cannon", "strange_cape", "silvers_spawn_orb",
]
ARMOR_NAMES = ["shrine_helmet", "shrine_chestplate", "shrine_leggings", "shrine_boots"]


def ensure_dir(path):
    os.makedirs(path, exist_ok=True)
    return path


def load_json(path):
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def save_json(path, data, indent=2):
    ensure_dir(os.path.dirname(path))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=indent, ensure_ascii=False)
        f.write("\n")


def model_path(name):
    """Item model json path. 'shrine' lives in models/block."""
    if name.startswith("block/"):
        return os.path.join(BLOCK_MODELS, name[len("block/"):] + ".json")
    return os.path.join(ITEM_MODELS, name + ".json")


# ------------------------------------------------------------------------------------------------------
#  Block-model faces: pixel (i, j) of a face  ->  3D point on that face (model units)
#  UV orientation (from CubeFace corner order + ModelElementFace.UV):
#      up    : u -> +x , v -> +z
#      down  : u -> +x , v -> -z
#      north : u -> -x , v -> -y     (north = -z face)
#      south : u -> +x , v -> -y
#      west  : u -> +z , v -> -y
#      east  : u -> -z , v -> -y
# ------------------------------------------------------------------------------------------------------

def face_point(face, frm, to, fu, fv):
    """fu, fv in [0,1] across the face in its UV orientation. Returns (x, y, z) in unrotated element space."""
    x1, y1, z1 = frm
    x2, y2, z2 = to
    if face == "up":
        return (x1 + fu * (x2 - x1), y2, z1 + fv * (z2 - z1))
    if face == "down":
        return (x1 + fu * (x2 - x1), y1, z2 - fv * (z2 - z1))
    if face == "north":
        return (x2 - fu * (x2 - x1), y2 - fv * (y2 - y1), z1)
    if face == "south":
        return (x1 + fu * (x2 - x1), y2 - fv * (y2 - y1), z2)
    if face == "west":
        return (x1, y2 - fv * (y2 - y1), z1 + fu * (z2 - z1))
    if face == "east":
        return (x2, y2 - fv * (y2 - y1), z2 - fu * (z2 - z1))
    raise ValueError(face)


FACE_NORMAL = {
    "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
}


def face_texel_size(face, frm, to, density=1.0):
    """How many texels a face gets at the given texel-per-unit density (>= 1 texel)."""
    dx, dy, dz = abs(to[0] - frm[0]), abs(to[1] - frm[1]), abs(to[2] - frm[2])
    if face in ("north", "south"):
        w, h = dx, dy
    elif face in ("east", "west"):
        w, h = dz, dy
    else:
        w, h = dx, dz
    return max(1, int(math.ceil(w * density - 1e-6))), max(1, int(math.ceil(h * density - 1e-6)))


# ------------------------------------------------------------------------------------------------------
#  Rotation / transform math (numpy, 4x4 homogeneous, column vectors)
# ------------------------------------------------------------------------------------------------------

def rx(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]], dtype=float)


def ry(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]], dtype=float)


def rz(deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]], dtype=float)


def tr(x, y, z):
    m = np.eye(4)
    m[:3, 3] = (x, y, z)
    return m


def sc(x, y=None, z=None):
    if y is None:
        y = z = x
    return np.diag([x, y, z, 1.0])


def rot_xyz(rxd, ryd, rzd):
    """Minecraft/JOML Quaternionf.rotationXYZ(x, y, z): matrix = Rx * Ry * Rz (z is applied to the vector first)."""
    return rx(rxd) @ ry(ryd) @ rz(rzd)


def euler_xyz_from_matrix(m):
    """Inverse of rot_xyz: returns (x, y, z) degrees such that rot_xyz(x, y, z) == m (upper 3x3)."""
    r = m[:3, :3]
    b = math.asin(max(-1.0, min(1.0, r[0][2])))
    if abs(r[0][2]) < 0.99999:
        a = math.atan2(-r[1][2], r[2][2])
        c = math.atan2(-r[0][1], r[0][0])
    else:  # gimbal lock
        a = math.atan2(r[2][1], r[1][1])
        c = 0.0
    return tuple(math.degrees(v) for v in (a, b, c))


def display_matrix(t, rot, scale, left_handed=False):
    """Net effect of Transformation.apply on model coordinates in 'pixels' (1 unit = 1/16 block), INCLUDING the final translate(-0.5)."""
    tx, ty, tz = t
    rxd, ryd, rzd = rot
    if left_handed:
        tx, ryd, rzd = -tx, -ryd, -rzd
    return tr(tx, ty, tz) @ rot_xyz(rxd, ryd, rzd) @ sc(*scale) @ tr(-8, -8, -8)


# ---- held-item chains (pixel units; see module docstring for sources) ----

ARM_PITCH_ITEM_DEG = -18.0  # BipedEntityModel ArmPose.ITEM: arm.pitch = pitch*0.5 - PI/10


def third_person_chain(arm_pitch_deg=ARM_PITCH_ITEM_DEG):
    """hand-frame -> entity model space (y down).  right arm pivot is (-5, 2, 0)."""
    return tr(-5, 2, 0) @ rx(arm_pitch_deg) @ rx(-90) @ ry(180) @ tr(1, 2, -10)


def entity_to_world(scale=0.9375):
    """Entity model space (y down, facing -z) -> world pixels (y up, player at yaw 0 faces +z)."""
    return sc(scale, scale, scale) @ np.diag([1, -1, -1, 1.0]) @ tr(0, 0, 0) @ _feet_fix()


def _feet_fix():
    # LivingEntityRenderer: translate(0, -1.501 blocks) happens in model space BEFORE the (-1,-1,1) flip
    return tr(0, -24.016, 0)


def third_person_world_matrix(t, rot, scale, arm_pitch_deg=ARM_PITCH_ITEM_DEG):
    """model pixel coords -> world pixel coords, for the right hand, third person, yaw 0."""
    e2w = np.diag([0.9375, -0.9375, -0.9375, 1.0]) @ tr(0, -24.016, 0)
    return e2w @ third_person_chain(arm_pitch_deg) @ display_matrix(t, rot, scale)


def first_person_matrix(t, rot, scale):
    """model pixel coords -> camera space in PIXELS (x right, y up, z toward viewer; camera looks along -z)."""
    return tr(0.56 * 16, -0.52 * 16, -0.72 * 16) @ display_matrix(t, rot, scale)


def xf(m, p):
    v = m @ np.array([p[0], p[1], p[2], 1.0])
    return v[:3]


# Where a vanilla handheld sword's grip ends up (the reference "palm" point), so our grips can be aligned to it.
VANILLA_SWORD_GRIP = (2.5, 2.5, 8.0)  # pixel (2,13) of the 16x16 sprite, in model coords


def vanilla_palm_third():
    m = display_matrix((0, 4.0, 0.5), (0, -90, 55), (0.85, 0.85, 0.85))
    return xf(m, VANILLA_SWORD_GRIP)  # in hand-frame (after third_person_chain)


def vanilla_palm_first():
    m = display_matrix((1.13, 3.2, 1.13), (0, -90, 25), (0.68, 0.68, 0.68))
    return xf(m, VANILLA_SWORD_GRIP)  # in 'camera-offset' frame (after the 0.56,-0.52,-0.72 translate)


# ------------------------------------------------------------------------------------------------------
#  Model json helpers
# ------------------------------------------------------------------------------------------------------

def element_rotation_matrix(el):
    """4x4 matrix applied to the element's own coordinates (model pixels) for its 'rotation', else identity."""
    rot = el.get("rotation")
    if not rot:
        return np.eye(4)
    ox, oy, oz = rot["origin"]
    ang = rot.get("angle", 0.0)
    axis = rot.get("axis")
    if axis is None:  # euler form
        m = rz(rot.get("z", 0)) @ ry(rot.get("y", 0)) @ rx(rot.get("x", 0))
    else:
        m = {"x": rx, "y": ry, "z": rz}[axis](ang)
    return tr(ox, oy, oz) @ m @ tr(-ox, -oy, -oz)


def element_corners(el):
    """The 8 corners of an element in model space (after its rotation)."""
    (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
    m = element_rotation_matrix(el)
    pts = []
    for x in (x1, x2):
        for y in (y1, y2):
            for z in (z1, z2):
                pts.append(xf(m, (x, y, z)))
    return pts


def model_bounds(model):
    pts = []
    for el in model.get("elements", []):
        pts.extend(element_corners(el))
    arr = np.array(pts)
    return arr.min(axis=0), arr.max(axis=0)


def find_element(model, name):
    for el in model.get("elements", []):
        if el.get("name") == name:
            return el
    return None


def element_center(el):
    m = element_rotation_matrix(el)
    (x1, y1, z1), (x2, y2, z2) = el["from"], el["to"]
    return xf(m, ((x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2))


def grip_point(model):
    """Center of the element named 'grip' (or the model's bounding-box centre if none)."""
    el = find_element(model, "grip")
    if el is not None:
        return element_center(el)
    lo, hi = model_bounds(model)
    return (lo + hi) / 2


# ------------------------------------------------------------------------------------------------------
#  Deterministic noise for the painter
# ------------------------------------------------------------------------------------------------------

def hash_u32(*vals):
    h = 2166136261
    for v in vals:
        h ^= int(v) & 0xFFFFFFFF
        h = (h * 16777619) & 0xFFFFFFFF
        h ^= h >> 13
        h = (h * 0x5bd1e995) & 0xFFFFFFFF
        h ^= h >> 15
    return h


def hash01(*vals):
    return (hash_u32(*vals) & 0xFFFFFF) / float(0x1000000)


def str_seed(s):
    h = 5381
    for ch in s:
        h = ((h << 5) + h + ord(ch)) & 0xFFFFFFFF
    return h


def value_noise3(x, y, z, seed=0):
    """Smooth-ish 3D value noise in [0,1]; lattice spacing = 1."""
    xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
    xf_, yf_, zf_ = x - xi, y - yi, z - zi

    def s(t):
        return t * t * (3 - 2 * t)

    u, v, w = s(xf_), s(yf_), s(zf_)
    acc = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                wgt = (u if dx else 1 - u) * (v if dy else 1 - v) * (w if dz else 1 - w)
                acc += wgt * hash01(xi + dx, yi + dy, zi + dz, seed)
    return acc


def fbm3(x, y, z, seed=0, octaves=3):
    amp, freq, tot, norm = 1.0, 1.0, 0.0, 0.0
    for o in range(octaves):
        tot += amp * value_noise3(x * freq, y * freq, z * freq, seed + o * 101)
        norm += amp
        amp *= 0.5
        freq *= 2.0
    return tot / norm


def voronoi_edge(x, y, z, seed=0):
    """Distance-to-cell-edge for 3D voronoi (F2-F1). Small values = crack lines. Cell size 1."""
    xi, yi, zi = math.floor(x), math.floor(y), math.floor(z)
    d1 = d2 = 9.0
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dz in (-1, 0, 1):
                cx, cy, cz = xi + dx, yi + dy, zi + dz
                px = cx + hash01(cx, cy, cz, seed)
                py = cy + hash01(cx, cy, cz, seed + 7)
                pz = cz + hash01(cx, cy, cz, seed + 13)
                d = math.sqrt((px - x) ** 2 + (py - y) ** 2 + (pz - z) ** 2)
                if d < d1:
                    d2, d1 = d1, d
                elif d < d2:
                    d2 = d
    return d2 - d1


def hex_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def clamp01(v):
    return 0.0 if v < 0 else (1.0 if v > 1 else v)


def lerp(a, b, t):
    return a + (b - a) * t


def lerp_col(c1, c2, t):
    return tuple(int(round(lerp(c1[i], c2[i], t))) for i in range(3))


class Ramp:
    """A colour ramp (dark -> light) that quantises a 0..1 value into a few discrete pixel-art shades."""

    def __init__(self, *hexes):
        self.cols = [hex_rgb(h) for h in hexes]

    def at(self, t, shift=0):
        n = len(self.cols)
        i = int(clamp01(t) * (n - 1e-4))
        i = max(0, min(n - 1, i + shift))
        return self.cols[i]

    def smooth(self, t):
        n = len(self.cols)
        t = clamp01(t) * (n - 1)
        i = min(n - 2, int(t))
        return lerp_col(self.cols[i], self.cols[i + 1], t - i)


def log(*a):
    print(*a, file=sys.stderr)
