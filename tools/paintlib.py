"""
Pixel-art paint engine + material library.

A material is a function  f(ctx) -> (r, g, b, a)  evaluated once per texel.  ctx tells it where that texel really is in 3D
(so cracks, gradients and swirls continue smoothly across the faces of a model) and how the face is lit.
Colours always come from small discrete ramps -> crisp, vanilla-style pixel art (no smooth gradients).
"""
import math

from common import (Ramp, clamp01, fbm3, hash01, hex_rgb, lerp, value_noise3, voronoi_edge)


class Ctx:
    __slots__ = ("lx", "ly", "lz", "wx", "wy", "wz", "n", "fu", "fv", "i", "j", "w", "h", "el", "tx", "ty", "tz", "seed", "shade", "state", "ny")

    def __init__(self):
        self.state = None


FACE_SHIFT = {"up": 1, "down": -1}


def R(*hexes):
    return Ramp(*hexes)


def rgba(rgb, a=255):
    return (rgb[0], rgb[1], rgb[2], a)


def dither(c, thresh, salt=0):
    """True for about `thresh` (0..1) of the texels, deterministically (checker-ish noise).
    Tiny faces (under 8 texels) never get holes, otherwise a 1-texel sparkle could vanish completely."""
    if c.w * c.h < 8:
        return False
    return hash01(c.i + c.wx * 7.3, c.j + c.wy * 5.1, c.seed + salt, int(c.wz * 3.1)) < thresh


# ---------------------------------------------------------------------------------------------------------
#  small pattern helpers
# ---------------------------------------------------------------------------------------------------------

def stripes(c, period, axis="y", skew=0.0, width=0.5, offset=0.0):
    """1 where inside a diagonal stripe."""
    if axis == "y":
        v = c.wy + skew * (c.wx + c.wz)
    elif axis == "x":
        v = c.wx + skew * (c.wy + c.wz)
    else:
        v = c.wz + skew * (c.wx + c.wy)
    return ((v + offset) / period) % 1.0 < width


NOISE_SCALE = 0.55   # lower = bigger, calmer clusters of colour (vanilla-like); higher = noisier


def noise(c, sx=1.0, sy=1.0, sz=1.0, k=0, octaves=2):
    return fbm3(c.wx * sx * NOISE_SCALE, c.wy * sy * NOISE_SCALE, c.wz * sz * NOISE_SCALE, c.seed + k, octaves)


def cracks(c, scale, width, k=0):
    return voronoi_edge(c.wx * scale, c.wy * scale, c.wz * scale, c.seed + k) < width


# ---------------------------------------------------------------------------------------------------------
#  METAL / STONE
# ---------------------------------------------------------------------------------------------------------
NETHERITE = R("#16121c", "#221d2b", "#302a3d", "#40384f", "#544a66")
OBS = R("#0d0715", "#150b24", "#1f1136", "#2d1a4a")
CRACK = R("#c02400", "#ff4a00", "#ff8a10", "#ffc23a")
LAVA = R("#7a1400", "#c22d00", "#ff6a00", "#ffa81c", "#fff0a0")
EDGE = R("#a01400", "#ff3d00", "#ff8a1a", "#ffd25a")
FLAME = R("#b01400", "#ff4a00", "#ff9a10", "#ffe45a", "#fffbd0")
EMBER_METAL = R("#2a1810", "#4a2a14", "#7a4a1e", "#b86a2a", "#ff9a3a")
LEATHER_D = R("#1e120d", "#2b1912", "#3b241a", "#4e3023")
CURSED = R("#0e1215", "#161c21", "#212a31", "#2e3a43", "#41505b")
TEAL_EDGE = R("#08564f", "#0f8f82", "#27d8c0", "#8ff8e8", "#e2fffa")
GOLD = R("#5e400c", "#8e6414", "#c2972a", "#ecc04c", "#fff0a0")
BARN = R("#7e8a86", "#a4b0ac", "#cdd8d2", "#f0f6f0")
BLACKWRAP = R("#07070a", "#0f0f15", "#181820", "#23232d")
FOAM = R("#8fded2", "#c4f4ec", "#effffb", "#ffffff")
SCULK = R("#081317", "#0d1f26", "#132f39", "#1a424f", "#245a6a")
VEIN = R("#0a5f66", "#0f8f8e", "#1fc8bc", "#78f4e6")
VEIN_METAL = R("#0b1417", "#122025", "#1a3036", "#264650")
SENSOR = R("#093842", "#0d505e", "#14707f", "#1f9aab")
SENSOR_TIP = R("#26b8b4", "#58e8dc", "#a8fff4")


def netherite(c, ramp=NETHERITE):
    v = noise(c, 0.55, 0.55, 0.55, 3) * 0.75 + (0.22 if hash01(c.i, c.j, c.seed) < 0.1 else 0.0)
    if int(c.wy) % 7 == 0 and c.n not in ("up", "down"):
        v -= 0.2  # plate seams
    return rgba(ramp.at(v, c.shade))


def ember_metal(c):
    v = noise(c, 0.8, 0.8, 0.8, 5)
    return rgba(EMBER_METAL.at(v + (0.3 if hash01(c.i, c.j, c.seed) < 0.07 else 0), c.shade))


def lava(c):
    wob = noise(c, 0.45, 0.45, 0.45, 9)
    v = fbm3(c.wx * 0.5 + wob * 1.5, c.wy * 0.5 - wob, c.wz * 0.5, c.seed + 3, 3)
    v = clamp01(v * 1.25 - 0.05)
    if hash01(c.i, c.j, c.seed + 5) < 0.06:
        v = 1.0
    return rgba(LAVA.at(v))


def obsidian_crack(c):
    v = noise(c, 0.7, 0.7, 0.7, 2)
    base = OBS.at(v, c.shade if c.shade < 0 else 0)
    d = voronoi_edge(c.wx * 0.34, c.wy * 0.34, c.wz * 0.34, c.seed + 1)
    if d < 0.07:
        t = clamp01(0.35 + noise(c, 1.2, 1.2, 1.2, 4) * 0.9)
        return rgba(CRACK.at(t))
    if d < 0.12:
        return rgba(OBS.at(0.9))  # faint violet halo around the glow
    return rgba(base)


def edge_glow(c):
    t = clamp01(0.25 + noise(c, 0.9, 0.9, 0.9, 6) * 0.9 + (c.wy % 6) / 40.0)
    if hash01(c.i, c.j, c.seed + 8) < 0.1:
        t = 1.0
    return rgba(EDGE.at(t))


def flame(c):
    h = clamp01(c.ty)
    n = noise(c, 1.2, 1.2, 1.2, 7)
    if c.w * c.h >= 8 and h > 0.55 + 0.35 * n and (c.i + c.j) % 2 == 0 and h > 0.6:
        return (0, 0, 0, 0)
    v = clamp01(1.0 - h * 0.85 + n * 0.25)
    return rgba(FLAME.at(v))


def leather_dark(c):
    wrap = stripes(c, 3.0, "y", 0.8, 0.45)
    v = 0.45 + (0.18 if wrap else -0.05) + noise(c, 1.0, 1.0, 1.0, 1) * 0.25
    if hash01(c.i, c.j, c.seed + 2) < 0.05:
        return rgba(hex_rgb("#d8581a"))  # orange stitching glint
    return rgba(LEATHER_D.at(v, c.shade))


def cursed_steel(c):
    # blade: long vertical streaks, darker core line, a few teal specks
    streak = value_noise3(c.wx * 1.4, c.wy * 0.18, c.wz * 1.4, c.seed)
    v = 0.35 + streak * 0.5 + (c.shade * 0.08)
    if hash01(c.i, c.j, c.seed + 4) < 0.04:
        return rgba(hex_rgb("#1d8a80"))
    return rgba(CURSED.at(v, c.shade))


def teal_edge(c):
    t = clamp01(0.3 + (c.ty * 0.5) + noise(c, 1.2, 1.2, 1.2, 2) * 0.5)
    return rgba(TEAL_EDGE.at(t))


def gold_barnacle(c):
    v = noise(c, 0.9, 0.9, 0.9, 3) * 0.8
    if noise(c, 1.6, 1.6, 1.6, 11) > 0.72:
        return rgba(BARN.at(0.5 + hash01(c.i, c.j, c.seed) * 0.5))      # barnacle crust
    if noise(c, 1.3, 1.3, 1.3, 17) > 0.78:
        return rgba(hex_rgb("#3a6a52"))                                  # verdigris
    return rgba(GOLD.at(v, c.shade))


def barnacle(c):
    v = noise(c, 1.0, 1.0, 1.0, 9)
    if hash01(c.i * 3, c.j * 5, c.seed) < 0.18:
        return rgba(hex_rgb("#3e4a46"))  # pore
    return rgba(BARN.at(v, c.shade))


def black_wrap(c):
    wrap = stripes(c, 2.4, "y", 0.7, 0.5)
    v = 0.4 + (0.3 if wrap else 0.0) + noise(c, 1, 1, 1, 5) * 0.2
    if wrap and hash01(c.i, c.j, c.seed) < 0.12:
        return rgba(hex_rgb("#0f7a72"))
    return rgba(BLACKWRAP.at(v, c.shade))


def foam(c):
    if dither(c, 0.45):
        return (0, 0, 0, 0)
    return rgba(FOAM.at(hash01(c.i, c.j, c.seed + 9) + c.shade * 0.1))


def sculk_stone(c):
    v = noise(c, 0.6, 0.6, 0.6, 1) * 0.8
    d = voronoi_edge(c.wx * 0.3, c.wy * 0.3, c.wz * 0.3, c.seed + 2)
    if d < 0.05:
        t = clamp01(0.2 + noise(c, 1.1, 1.1, 1.1, 8) * 0.9)
        return rgba(VEIN.at(t))
    if hash01(c.i, c.j, c.seed + 3) < 0.012:
        return rgba(VEIN.at(0.2))
    return rgba(SCULK.at(v, c.shade))


def vein_glow(c):
    t = clamp01(0.35 + noise(c, 1.3, 1.3, 1.3, 6) * 0.6 + c.ty * 0.2)
    return rgba(VEIN.at(t))


def vein_metal(c):
    v = noise(c, 0.8, 0.8, 0.8, 4) * 0.8
    if cracks(c, 0.55, 0.06, 5):
        return rgba(VEIN.at(0.5))
    return rgba(VEIN_METAL.at(v, c.shade))


def sensor_cluster(c):
    n = noise(c, 1.4, 1.4, 1.4, 12)
    if c.ty > 0.7 and n > 0.4:
        return rgba(SENSOR_TIP.at(clamp01((c.ty - 0.6) * 2 + n * 0.3)))
    v = 0.35 + n * 0.7
    return rgba(SENSOR.at(v, c.shade))


# ---------------------------------------------------------------------------------------------------------
#  ICE / SKY / SEA
# ---------------------------------------------------------------------------------------------------------
ICE = R("#6fa8cc", "#9cd0e8", "#c2e8f8", "#e4f8ff", "#ffffff")
SKY = R("#9ac8e4", "#bce0f2", "#d8f1fb", "#f2fbff")
WGOLD = R("#a68c52", "#d6bf7a", "#f3e4a4", "#fff9e0")
BRONZE = R("#47300f", "#734a1c", "#a46e2a", "#cf9440", "#f0c068")
PATINA = R("#175a4e", "#238a76", "#43c4a6", "#98ecd2")
SEAWRAP = R("#124e5e", "#1c7084", "#2894a8", "#4cc0cc")


def ice_crystal(c):
    # faceted: each voronoi cell is a flat shade, cell edges are bright
    cell = voronoi_edge(c.wx * 0.55, c.wy * 0.3, c.wz * 0.55, c.seed)
    base = noise(c, 0.35, 0.35, 0.35, 21)
    v = 0.35 + base * 0.55
    if cell < 0.08:
        v = 1.0
    return rgba(ICE.at(v, c.shade))


def ice_tip(c):
    t = clamp01(0.55 + c.ty * 0.4 + noise(c, 1.2, 1.2, 1.2, 2) * 0.3)
    return rgba(ICE.at(t, 1))


def white_wrap(c):
    wrap = stripes(c, 2.2, "y", 0.9, 0.55)
    base = R("#b6c2cc", "#d2dce3", "#eaf0f4", "#ffffff")
    return rgba(base.at(0.45 + (0.35 if wrap else 0) + noise(c, 1, 1, 1, 3) * 0.15, c.shade))


def silver(c):
    base = R("#7f8b95", "#aab6c0", "#d2dbe2", "#ffffff")
    return rgba(base.at(0.4 + noise(c, 1.2, 1.2, 1.2, 4) * 0.6, c.shade))


def frost_mist(c):
    if dither(c, 0.55):
        return (0, 0, 0, 0)
    return rgba(R("#cdeefa", "#eafaff", "#ffffff").at(hash01(c.i, c.j, c.seed + 1)))


def bowstring(c):
    return rgba(R("#bde2f2", "#e8f8ff", "#ffffff").at(0.5 + c.shade * 0.2))


def cannon_string(c):
    # the Sculk Cannon's string: dark blue (owner request)
    return rgba(R("#0b1a4a", "#14307a", "#1d46a8").at(0.45 + c.shade * 0.2))


def arrow_wood(c):
    return rgba(R("#5e4024", "#7e5a34", "#a07848").at(0.5 + noise(c, 2, 2, 2, 1) * 0.5, c.shade))


def arrow_head(c):
    return rgba(R("#8d99a2", "#bcc6cd", "#e8eef2", "#ffffff").at(0.5, c.shade))


def arrow_fletch(c):
    return rgba(R("#b7e4f6", "#e6f8ff", "#ffffff").at(hash01(c.i, c.j, c.seed) + c.shade * 0.1))


def swirl(c, thickness=0.20):
    """wind-swirl etchings: wavy diagonal lines winding around a vertical shaft."""
    ph = c.wy * 0.42 + math.sin((c.wx + c.wz) * 1.7) * 0.55 + (c.wx - 8) * 0.12
    return (ph % 1.0) < thickness


def sky_shaft(c):
    v = 0.45 + noise(c, 0.9, 0.2, 0.9, 3) * 0.45
    if swirl(c):
        return rgba(R("#58b8ea", "#8ad2f6", "#c6ecfd").at(0.4 + noise(c, 1, 1, 1, 2) * 0.6))
    if (c.i * 7 + c.j * 3 + c.seed) % 19 == 0:
        return rgba(hex_rgb("#ffffff"))
    return rgba(SKY.at(v, c.shade))


def white_gold(c):
    v = 0.4 + noise(c, 1.2, 1.2, 1.2, 8) * 0.6
    return rgba(WGOLD.at(v, c.shade))


def leather_white(c):
    wrap = stripes(c, 2.6, "y", 0.8, 0.5)
    base = R("#c4baa4", "#ddd4c0", "#f1eadb", "#ffffff")
    if wrap and hash01(c.i, c.j, c.seed) < 0.18:
        return rgba(hex_rgb("#e6c968"))
    return rgba(base.at(0.4 + (0.3 if wrap else 0) + noise(c, 1, 1, 1, 2) * 0.2, c.shade))


def feather(c):
    barb = (c.wy * 1.6 + abs(c.wx - 8) * 0.9) % 1.0
    v = 0.5 + (0.3 if barb < 0.5 else 0.0) + noise(c, 1, 1, 1, 4) * 0.2
    if c.ty < 0.18 and dither(c, 0.5):
        return (0, 0, 0, 0)
    return rgba(R("#a4cfe8", "#cde9f8", "#eef9ff", "#ffffff").at(v, c.shade))


def sky_glow(c):
    if dither(c, 0.35):
        return (0, 0, 0, 0)
    return rgba(R("#a8e0ff", "#d8f4ff", "#ffffff").at(hash01(c.i, c.j, c.seed + 2)))


def sea_bronze(c):
    v = noise(c, 0.8, 0.8, 0.8, 5) * 0.85
    if noise(c, 1.5, 0.5, 1.5, 9) > 0.76:
        return rgba(PATINA.at(0.3 + noise(c, 2, 2, 2, 3) * 0.7))
    return rgba(BRONZE.at(v, c.shade))


def teal_patina(c):
    return rgba(PATINA.at(0.2 + noise(c, 1.2, 1.2, 1.2, 4) * 0.8, c.shade))


def bronze_bright(c):
    return rgba(R("#b87a30", "#e0a64a", "#ffd47e", "#fff2c4").at(0.35 + noise(c, 1.2, 1.2, 1.2, 4) * 0.65, c.shade))


def wave_wrap(c):
    wave = ((c.wy * 0.9 + math.sin(c.wx * 1.8 + c.wz * 1.8) * 0.8) % 1.0) < 0.5
    return rgba(SEAWRAP.at(0.35 + (0.4 if wave else 0) + noise(c, 1, 1, 1, 2) * 0.2, c.shade))


def sea_gem(c):
    return rgba(R("#0a5a8a", "#1a8ad0", "#5acbff", "#cff4ff").at(0.3 + noise(c, 1.4, 1.4, 1.4, 6) * 0.8))


# ---------------------------------------------------------------------------------------------------------
#  CLOTH / LEATHER / DENIM / SHOES
# ---------------------------------------------------------------------------------------------------------
KNIT = R("#0c0c11", "#14141b", "#1d1d27", "#292935")
KNITC = R("#15151d", "#1f1f2a", "#2b2b39", "#3a3a4b")
EMBER = R("#9a2e00", "#ff6a10", "#ffb040")


def knit(c):
    # rib knit: columns of V-shaped stitches
    col = int(c.wx * 1.0 + c.wz * 1.0)
    row = int(c.wy * 1.0)
    v = 0.4 + (0.25 if (col + row) % 2 == 0 else 0.0) + (0.12 if col % 3 == 0 else 0.0)
    if hash01(c.i, c.j, c.seed + 1) < 0.045:
        return rgba(EMBER.at(hash01(c.j, c.i, c.seed)))
    return rgba(KNIT.at(v, c.shade))


def knit_cuff(c):
    col = int(c.wx + c.wz)
    v = 0.45 + (0.3 if col % 2 == 0 else 0.0) + noise(c, 1, 1, 1, 3) * 0.15
    if c.ty < 0.12 and hash01(c.i, c.j, c.seed) < 0.25:
        return rgba(EMBER.at(0.9))
    return rgba(KNITC.at(v, c.shade))


def ember_thread(c):
    return rgba(EMBER.at(0.4 + hash01(c.i, c.j, c.seed) * 0.6))


LEATHER = R("#2b1a10", "#3b2414", "#4f321d", "#674427", "#80573a")


def leather(c):
    crease = noise(c, 0.25, 1.3, 0.25, 7)
    v = 0.5 + noise(c, 0.6, 0.6, 0.6, 2) * 0.22 + (0.22 if 0.46 < crease < 0.5 else 0) - (0.2 if 0.52 < crease < 0.55 else 0)
    if hash01(c.i, c.j, c.seed + 6) < 0.012:
        v += 0.25  # gloss speck
    return rgba(LEATHER.at(v, c.shade))


def zipper(c):
    teeth = (c.i + c.j) % 2 == 0
    return rgba(R("#6e6e78", "#a0a0ac", "#d0d0da", "#ffffff").at(0.65 if teeth else 0.25, c.shade))


def aura_glow(c):
    t = noise(c, 1.2, 1.2, 1.2, 4)
    if dither(c, 0.25):
        return (0, 0, 0, 0)
    return rgba(R("#6a2a9a", "#9a4ac8", "#e07a4a", "#ffb060").at(t))


DENIM = R("#1f3560", "#2b4a7e", "#3a6094", "#4f7eb0", "#78a6d0")


def denim(c, ramp=DENIM):
    # twill weave + pale-blue shimmering water ripples
    weave = (int(c.wx + c.wy + c.wz * 0.0 + 0.5) % 2 == 0)
    v = 0.42 + (0.1 if weave else 0.0) + noise(c, 0.5, 0.5, 0.5, 2) * 0.22
    ripple = math.sin(c.wy * 0.9 + (c.wx + c.wz) * 0.8 + noise(c, 0.4, 0.4, 0.4, 5) * 3.0)
    if ripple > 0.94:
        return rgba(hex_rgb("#a8dcf4"))
    return rgba(ramp.at(v, c.shade))


def denim_dark(c):
    return denim(c, R("#162848", "#1f3560", "#2b4a7e", "#3a6094"))


def denim_belt(c):
    return rgba(R("#3a2412", "#53341a", "#6e4824", "#8e6232").at(0.4 + noise(c, 1.5, 1.5, 1.5, 1) * 0.5, c.shade))


def denim_frayed(c):
    if c.w * c.h >= 8 and c.ty < 0.22 and hash01(c.i, c.j, c.seed + 3) < 0.45:
        return (0, 0, 0, 0)
    base = denim(c)
    if c.ty < 0.32:
        return rgba(R("#6f98c0", "#9bc2e2", "#c8e2f4").at(hash01(c.i, c.j, c.seed)))
    return base


def rivet(c):
    return rgba(R("#8a4a1a", "#c06a28", "#e6903e", "#ffc070").at(0.4 + (c.shade + 2) * 0.15, 0))


def boot_white(c):
    stitch = (c.i + c.j) % 4 == 0 and c.i > 0
    v = 0.55 + noise(c, 1, 1, 1, 2) * 0.25 + (0.0 if not stitch else -0.2)
    return rgba(R("#b4bac6", "#d2d8e2", "#eceff5", "#ffffff").at(v, c.shade))


def boot_trim(c):
    return rgba(R("#101a2c", "#18283f", "#223a5a", "#32507a").at(0.4 + noise(c, 1.4, 1.4, 1.4, 3) * 0.5, c.shade))


def sole(c):
    tread = (c.i % 2 == 0) and c.n == "down"
    return rgba(R("#cfd3dc", "#eceff4", "#ffffff").at(0.35 if tread else 0.75, c.shade))


def air_window(c):
    return rgba(R("#3aa8f0", "#7ad4ff", "#c8eeff", "#ffffff").at(0.3 + noise(c, 1.5, 1.5, 1.5, 5) * 0.8))


def tongue(c):
    return rgba(R("#d8560c", "#ff7a1a", "#ffa84a", "#ffd07a").at(0.4 + noise(c, 1.5, 1.5, 1.5, 2) * 0.5, c.shade))


def bolt(c):
    return rgba(R("#e6b800", "#ffd21a", "#ffe85a", "#fff6a8").at(0.5 + noise(c, 1.5, 1.5, 1.5, 2) * 0.5))


def lace(c):
    return rgba(R("#dde2ea", "#f4f6fa", "#ffffff").at(0.5 + c.shade * 0.15))


# ---------------------------------------------------------------------------------------------------------
#  DARK PICK / CAPE / ORB / SHRINE
# ---------------------------------------------------------------------------------------------------------
DARKIRON = R("#120f18", "#1b1624", "#271f34", "#372c48", "#4a3c60")
RUNE = R("#4a0a30", "#8a1450", "#c42068", "#ff4a8a")
BONE = R("#a89878", "#cfc0a0", "#e8dcc0", "#fff4de")


def dark_iron(c):
    v = noise(c, 0.7, 0.7, 0.7, 3) * 0.8
    if cracks(c, 0.4, 0.06, 4):
        return rgba(RUNE.at(0.25 + noise(c, 1.5, 1.5, 1.5, 5) * 0.7))
    return rgba(DARKIRON.at(v, c.shade))


def rune_glow(c):
    t = clamp01(0.3 + noise(c, 1.3, 1.3, 1.3, 7) * 0.8)
    return rgba(RUNE.at(t))


def bone_wrap(c):
    wrap = stripes(c, 2.4, "y", 0.8, 0.5)
    return rgba(BONE.at(0.3 + (0.35 if wrap else 0) + noise(c, 1, 1, 1, 2) * 0.2, c.shade))


def bone_plate(c):
    v = 0.35 + noise(c, 0.8, 0.8, 0.8, 6) * 0.65
    if cracks(c, 0.5, 0.05, 2):
        return rgba(hex_rgb("#5a4a38"))
    return rgba(BONE.at(v, c.shade))


def sculk_wood(c):
    grain = value_noise3(c.wx * 0.3, c.wy * 0.3, c.wz * 1.6, c.seed)
    v = 0.25 + grain * 0.6
    if cracks(c, 0.45, 0.045, 8):
        return rgba(VEIN.at(0.35))
    return rgba(R("#0a1113", "#111b1e", "#19272b", "#243439").at(v, c.shade))


CLOTH = R("#150d28", "#1f1338", "#2c1b4e", "#3d2870", "#55399a")


def cape_cloth(c):
    fold = value_noise3(c.wx * 1.1, c.wy * 0.15, c.wz * 1.1, c.seed)
    v = 0.3 + fold * 0.6
    # odd little constellation marks
    if hash01(int(c.wx * 2), int(c.wy * 2), c.seed + 7) < 0.012:
        return rgba(hex_rgb("#fff0b0"))
    if abs(math.sin(c.wx * 0.9 + c.wy * 0.35)) < 0.05 and c.wy > 2:
        return rgba(hex_rgb("#8a58d8"))
    return rgba(CLOTH.at(v, c.shade))


def cape_cloth_dark(c):
    return rgba(R("#0e0820", "#160d2c", "#201440", "#2c1c58").at(0.3 + noise(c, 1, 1, 1, 2) * 0.6, c.shade))


def cape_clasp(c):
    return rgba(R("#6a4a10", "#a8801e", "#e0b840", "#fff0a0").at(0.4 + noise(c, 1.4, 1.4, 1.4, 4) * 0.6, c.shade))


def strange_eye(c):
    # a glowing sideways eye on the south/north face
    fx, fy = c.fu - 0.5, c.fv - 0.5
    inside = (fx / 0.5) ** 2 + (fy / 0.34) ** 2
    if inside < 1.0:
        pupil = (fx / 0.14) ** 2 + (fy / 0.34) ** 2 < 1.0
        return rgba(hex_rgb("#12001e")) if pupil else rgba(R("#ff3aa0", "#ff80c8", "#fff0fa").at(0.5 + (1 - inside) * 0.6))
    return rgba(R("#3a0e5a", "#5a1c88").at(0.5))


def star_speck(c):
    return rgba(R("#ffe9a0", "#ffffff").at(hash01(c.i, c.j, c.seed)))


ORB = R("#4a9ccc", "#74bde6", "#9cd6f4", "#c8ecff", "#effaff")
HORSE_BITMAP = [
    "..........XXX...",
    ".........XXXXX..",
    "........XXXXXXX.",
    "........XXXXXXXX",
    "........XXXXXXXX",
    ".......XXXXXXXXX",
    "......XXXXXXXXX.",
    ".....XXXXXXXXXX.",
    "....XXXXXXXXXX..",
    "...XXXXXXXXXXX..",
    "..XXXXXXXXXX....",
    ".XXXXXXXXXXX....",
    ".XXXXXXXXXX.....",
    "XXXXXXXXX.......",
    "XXXXXXXX........",
    "XXXXXX..........",
]


def orb_ice(c):
    wob = noise(c, 0.35, 0.35, 0.35, 4)
    v = fbm3(c.wx * 0.4 + wob, c.wy * 0.4, c.wz * 0.4 - wob, c.seed + 2, 3)
    if cracks(c, 0.45, 0.05, 6):
        v = 1.0
    return rgba(ORB.at(v * 0.9 + 0.1, c.shade))


def horse_motif(c):
    # frosty background with a deep-blue horse head silhouette painted on the face
    bx = int(c.fu * 16)
    by = int(c.fv * 16)
    bx = min(15, max(0, bx))
    by = min(15, max(0, by))
    if c.n == "north":
        bx = 15 - bx
    inside = HORSE_BITMAP[by][bx] == "X"
    # mane notch + eye
    if inside and by == 4 and bx == 12:
        return rgba(hex_rgb("#f4fbff"))
    if inside:
        return rgba(R("#123e6e", "#1a5a96", "#2a7cc0").at(0.4 + noise(c, 1.2, 1.2, 1.2, 2) * 0.6 + (c.shade * 0.15)))
    return rgba(R("#8ac8ee", "#b0dcf6", "#d6f0ff").at(0.3 + noise(c, 0.8, 0.8, 0.8, 7) * 0.7))


ORB_DARK = R("#10151d", "#1b2331", "#2a3648", "#3f4f66", "#5c6f8a")
ORB_PALE = R("#9aa6b4", "#b6c0cc", "#d0d8e0", "#e8edf2")


def orb_dark(c):
    # Silver is out: blackened steel shell with faint blue cracks
    v = fbm3(c.wx * 0.4, c.wy * 0.4, c.wz * 0.4, c.seed + 5, 3)
    if cracks(c, 0.4, 0.05, 8):
        return rgba(R("#2a6aa0", "#4a9ad8").at(0.5 + v * 0.5))
    return rgba(ORB_DARK.at(v * 0.8 + 0.1, c.shade))


def orb_socket(c):
    # the empty socket where the horse used to be: dark dish with a thin glowing blue ring
    d = max(abs(c.fu - 0.5), abs(c.fv - 0.5))
    if 0.36 < d < 0.44:
        return rgba(R("#2a7cc0", "#6ac0f0", "#bfe6ff").at(0.4 + noise(c, 1.2, 1.2, 1.2, 4) * 0.6))
    return rgba(ORB_DARK.at(0.1 + 0.25 * noise(c, 0.8, 0.8, 0.8, 9) + (0.1 if d < 0.2 else 0), c.shade))


def orb_pale(c):
    # recharging: drained, chalky shell
    v = fbm3(c.wx * 0.35, c.wy * 0.35, c.wz * 0.35, c.seed + 6, 3)
    if cracks(c, 0.45, 0.05, 11):
        return rgba(hex_rgb("#6c7a8a"))
    return rgba(ORB_PALE.at(v * 0.8 + 0.1, c.shade))


def horse_ghost(c):
    # same horse silhouette as horse_motif but faded: pale blue-grey on chalk
    bx = min(15, max(0, int(c.fu * 16)))
    by = min(15, max(0, int(c.fv * 16)))
    if c.n == "north":
        bx = 15 - bx
    if HORSE_BITMAP[by][bx] == "X":
        return rgba(R("#7f93aa", "#93a8c0", "#a8bcd2").at(0.4 + noise(c, 1.2, 1.2, 1.2, 2) * 0.6))
    return rgba(ORB_PALE.at(0.3 + noise(c, 0.8, 0.8, 0.8, 7) * 0.5))


def ice_rim(c):
    return rgba(R("#8cc8f0", "#bfe6ff", "#e8f8ff", "#ffffff").at(0.4 + noise(c, 1, 1, 1, 3) * 0.6, c.shade))


def ice_sparkle(c):
    if dither(c, 0.25):
        return (0, 0, 0, 0)
    return rgba(R("#d8f4ff", "#ffffff").at(hash01(c.i, c.j, c.seed)))


# ---- Shrine: the three states differ only in their glow colours ----
SHRINE_STONE = R("#1c1c23", "#272731", "#343440", "#45455a", "#5a5a72")
SHRINE_STATE = {
    "unconfigured": {"glow": R("#4a1c8a", "#7a3ad0", "#b070ff", "#e8c8ff"), "crystal": R("#3a1470", "#6a2ec0", "#a060ff", "#e0b8ff")},
    "configured":   {"glow": R("#a06a00", "#e0a820", "#ffd84a", "#fff4b0"), "crystal": R("#0a6a7a", "#18b0c0", "#6af0f0", "#e0ffff")},
    "claimed":      {"glow": R("#22262e", "#2e343e", "#3c4450", "#4e5866"), "crystal": R("#1c2028", "#2a303a", "#3a424e", "#4c5664")},
}


def shrine_stone(c, ramp=SHRINE_STONE, light=False):
    brick = ((int(c.wy / 3.0) * 5 + int(c.wx + c.wz * 0.0) // 5) % 2 == 0)
    seam = (int(c.wy) % 3 == 0 and c.n not in ("up", "down"))
    v = 0.45 + noise(c, 0.55, 0.55, 0.55, 2) * 0.35 + (0.08 if brick else 0.0) - (0.18 if seam else 0.0) + (0.12 if light else 0)
    if cracks(c, 0.3, 0.035, 3):
        v -= 0.25
    return rgba(ramp.at(v, c.shade))


def shrine_stone_light(c):
    return shrine_stone(c, SHRINE_STONE, True)


def shrine_rune(c):
    st = SHRINE_STATE[c.state or "unconfigured"]["glow"]
    fx, fy = c.fu, c.fv
    # simple glowing glyph: outer ring + inner cross
    d = max(abs(fx - 0.5), abs(fy - 0.5))
    on = (0.34 < d < 0.46) or (abs(fx - 0.5) < 0.07 and abs(fy - 0.5) < 0.28) or (abs(fy - 0.5) < 0.07 and abs(fx - 0.5) < 0.28)
    if on:
        return rgba(st.at(0.55 + noise(c, 1.3, 1.3, 1.3, 4) * 0.45))
    return rgba(SHRINE_STONE.at(0.15 + noise(c, 1, 1, 1, 6) * 0.3))


def shrine_glow(c):
    st = SHRINE_STATE[c.state or "unconfigured"]["glow"]
    return rgba(st.at(0.2 + noise(c, 0.8, 0.8, 0.8, 5) * 0.8 + (0.1 if cracks(c, 0.6, 0.08, 3) else 0)))


def shrine_crystal(c):
    st = SHRINE_STATE[c.state or "unconfigured"]["crystal"]
    cell = voronoi_edge(c.wx * 0.8, c.wy * 0.5, c.wz * 0.8, c.seed)
    v = 0.3 + noise(c, 0.5, 0.5, 0.5, 4) * 0.55 + (0.35 if cell < 0.1 else 0)
    return rgba(st.at(v, c.shade if c.state != "claimed" else 0))


MATERIALS = {
    "netherite": netherite, "ember_metal": ember_metal, "lava": lava, "obsidian_crack": obsidian_crack, "edge_glow": edge_glow,
    "flame": flame, "leather_dark": leather_dark, "cursed_steel": cursed_steel, "teal_edge": teal_edge,
    "gold_barnacle": gold_barnacle, "barnacle": barnacle, "black_wrap": black_wrap, "foam": foam,
    "sculk_stone": sculk_stone, "vein_glow": vein_glow, "vein_metal": vein_metal, "sensor_cluster": sensor_cluster,
    "ice_crystal": ice_crystal, "ice_tip": ice_tip, "white_wrap": white_wrap, "silver": silver, "frost_mist": frost_mist,
    "bowstring": bowstring, "cannon_string": cannon_string, "arrow_wood": arrow_wood, "arrow_head": arrow_head, "arrow_fletch": arrow_fletch,
    "sky_shaft": sky_shaft, "white_gold": white_gold, "leather_white": leather_white, "feather": feather, "sky_glow": sky_glow,
    "sea_bronze": sea_bronze, "teal_patina": teal_patina, "bronze_bright": bronze_bright, "wave_wrap": wave_wrap, "sea_gem": sea_gem,
    "knit": knit, "knit_cuff": knit_cuff, "ember_thread": ember_thread, "leather": leather, "zipper": zipper, "aura_glow": aura_glow,
    "denim": denim, "denim_dark": denim_dark, "denim_belt": denim_belt, "denim_frayed": denim_frayed, "rivet": rivet,
    "boot_white": boot_white, "boot_trim": boot_trim, "sole": sole, "air_window": air_window, "tongue": tongue,
    "bolt": bolt, "lace": lace,
    "dark_iron": dark_iron, "rune_glow": rune_glow, "bone_wrap": bone_wrap, "bone_plate": bone_plate, "sculk_wood": sculk_wood,
    "cape_cloth": cape_cloth, "cape_cloth_dark": cape_cloth_dark, "cape_clasp": cape_clasp, "strange_eye": strange_eye, "star_speck": star_speck,
    "orb_ice": orb_ice, "orb_dark": orb_dark, "orb_socket": orb_socket, "orb_pale": orb_pale, "horse_ghost": horse_ghost, "horse_motif": horse_motif, "ice_rim": ice_rim, "ice_sparkle": ice_sparkle,
    "shrine_stone": shrine_stone, "shrine_stone_light": shrine_stone_light, "shrine_rune": shrine_rune, "shrine_glow": shrine_glow,
    "shrine_crystal": shrine_crystal,
}


def material_for(name, fallback="netherite"):
    if name.startswith("flat:"):                       # "flat:#rrggbb" = one solid colour (used by the picture-built armor)
        col = tuple(int(name[6 + i:8 + i], 16) for i in (0, 2, 4))
        return lambda c: rgba(col)
    return MATERIALS.get(name) or MATERIALS[fallback]


def match_material(el_name, table, default_mat):
    """Material for an element: exact name, else the longest name prefix in the table, else the model default."""
    if el_name in table:
        return table[el_name]
    best = None
    for k in table:
        base = k.rstrip("_0123456789")
        if base and el_name.startswith(base) and (best is None or len(base) > len(best[0])):
            best = (base, table[k])
    return best[1] if best else default_mat


# ---------------------------------------------------------------------------------------------------------
#  WORN ARMOR / CAPE palettes taken straight from the owner's icons (tools/reference/icons/*.png)
#  A "pool material" picks pixels of one colour family from the icon, so the worn item has the icon's real colours and speckle.
# ---------------------------------------------------------------------------------------------------------
def _icon_pixels(name):
    import colorsys
    import os
    from PIL import Image
    path = os.path.join(os.path.dirname(__file__), "reference", "icons", name + ".png")
    img = Image.open(path).convert("RGBA")
    out = []
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            if a:
                h, sat, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                out.append((x, y, (r, g, b), h * 360, sat, v))
    return out


def _pool_material(pixels, pred, fallback_pred=None, salt=0):
    chosen = [p[2] for p in pixels if pred(p)]
    if not chosen and fallback_pred:
        chosen = [p[2] for p in pixels if fallback_pred(p)]
    if not chosen:
        chosen = [p[2] for p in pixels]

    def mat(c):
        k = int(hash01(c.i, c.j, c.seed + salt) * len(chosen)) % len(chosen)
        f = 1.0 + 0.07 * c.shade
        r, g, b = chosen[k]
        return rgba((min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f))))
    return mat


def _icon_materials():
    m = {}
    hue = lambda p, a, b: (a <= p[3] <= b) if a <= b else (p[3] >= a or p[3] <= b)
    # helmet: grey knit body, dark rune band with beige runes
    px = _icon_pixels("shrine_helmet")
    m["knit"] = _pool_material(px, lambda p: p[1] < 19 and p[4] < 0.3 and 0.2 < p[5] < 0.8, salt=1)
    m["knit_cuff"] = _pool_material(px, lambda p: p[1] >= 19, salt=2)
    m["ember_thread"] = _pool_material(px, lambda p: p[1] >= 19 and p[5] > 0.65, salt=3)
    # chestplate: brown leather, purple + orange accents
    px = _icon_pixels("shrine_chestplate")
    m["leather"] = _pool_material(px, lambda p: hue(p, 12, 42) and 0.3 < p[4] < 0.85 and 0.25 < p[5] < 0.7, salt=4)
    m["leather_dark"] = _pool_material(px, lambda p: p[5] < 0.3, salt=5)
    m["zipper"] = _pool_material(px, lambda p: hue(p, 15, 45) and p[5] > 0.55 and p[4] < 0.7, salt=6)
    m["aura_glow"] = _pool_material(px, lambda p: (hue(p, 255, 320) and p[4] > 0.3) or (hue(p, 25, 55) and p[4] > 0.7 and p[5] > 0.8), salt=7)
    # leggings: blue denim
    px = _icon_pixels("shrine_leggings")
    m["denim"] = _pool_material(px, lambda p: hue(p, 200, 235) and p[4] > 0.4 and 0.4 < p[5] < 0.85, salt=8)
    m["denim_dark"] = _pool_material(px, lambda p: p[5] < 0.4, salt=9)
    m["denim_belt"] = _pool_material(px, lambda p: p[1] <= 5 and p[5] > 0.2, salt=10)
    m["denim_frayed"] = _pool_material(px, lambda p: p[5] > 0.8, salt=11)
    m["rivet"] = _pool_material(px, lambda p: p[5] > 0.9, salt=12)
    # boots: black and white with a pale-blue accent
    px = _icon_pixels("shrine_boots")
    m["boot_white"] = _pool_material(px, lambda p: p[4] < 0.15 and p[5] > 0.8, salt=13)
    m["boot_trim"] = _pool_material(px, lambda p: p[5] < 0.25, salt=14)
    m["sole"] = _pool_material(px, lambda p: p[4] < 0.2 and 0.5 < p[5] < 0.95 and p[1] >= 20, salt=15)
    m["air_window"] = _pool_material(px, lambda p: hue(p, 170, 240) and p[4] > 0.08 and p[5] > 0.7, salt=16)
    m["bolt"] = m["air_window"]
    m["lace"] = m["boot_white"]
    m["tongue"] = _pool_material(px, lambda p: 0.15 < p[5] < 0.45, salt=17)
    # cape: red cloth with gold flecks
    px = _icon_pixels("strange_cape")
    m["cape_cloth"] = _pool_material(px, lambda p: (hue(p, 345, 360) or hue(p, 0, 12)) and p[4] > 0.6 and 0.4 < p[5] < 0.9, salt=18)
    m["cape_cloth_dark"] = _pool_material(px, lambda p: p[5] < 0.45, salt=19)
    m["cape_clasp"] = _pool_material(px, lambda p: hue(p, 38, 58) and p[4] > 0.7 and p[5] > 0.8, salt=20)
    m["strange_eye"] = m["cape_clasp"]
    m["star_speck"] = m["cape_clasp"]
    return m


MATERIALS.update(_icon_materials())


# ---------------------------------------------------------------------------------------------------------
#  MYTHIC ARMOR (chunky dark plate + glowing trim, in the spirit of Minecraft Dungeons' heavy sets).
#  One plate metal for the whole set; each piece glows in its own element colour:
#  helmet = magma (fire resistance), chestplate = void violet, leggings = tide teal (dolphin's grace), boots = frost (speed / no fall damage).
# ---------------------------------------------------------------------------------------------------------
TITAN = R("#2c313b", "#3d4450", "#525a69", "#6b7485", "#8791a3")   # knight steel
TITAN_LIGHT = R("#6a7383", "#8791a2", "#a6b0c0", "#c6ceda", "#e8edf4")   # polished edges
GLOW_MAGMA = R("#b23a00", "#ff6a10", "#ffa630", "#ffe08a")
GLOW_VOID = R("#5a1fa8", "#9a4dff", "#c98cff", "#f0dcff")
GLOW_TIDE = R("#0a7f78", "#18c9b6", "#5ff2de", "#d4fff8")
GLOW_FROST = R("#3f8fd0", "#7cc8f4", "#bfeaff", "#ffffff")


def titan_plate(c):
    v = noise(c, 0.5, 0.5, 0.5, 3) * 0.55 + 0.12
    return rgba(TITAN.at(v, c.shade))


def titan_light(c):
    v = noise(c, 0.5, 0.5, 0.5, 5) * 0.5 + 0.25
    return rgba(TITAN_LIGHT.at(v, c.shade))


def _glow(ramp):
    def fn(c):
        return rgba(ramp.at(clamp01(0.45 + noise(c, 1.2, 1.2, 1.2, 9) * 0.5)))
    return fn


MATERIALS.update({"titan_plate": titan_plate, "titan_light": titan_light, "glow_magma": _glow(GLOW_MAGMA), "glow_void": _glow(GLOW_VOID),
                  "glow_tide": _glow(GLOW_TIDE), "glow_frost": _glow(GLOW_FROST)})


# ---------------------------------------------------------------------------------------------------------
#  GOLDEN KNIGHT ARMOR (owner's reference: ivory-gold plate, bright gold trim, glowing amber gems and cracks, chainmail under the plates)
# ---------------------------------------------------------------------------------------------------------
KN_PLATE = R("#b39a66", "#d2bc8a", "#e9d8ac", "#f8ecca", "#fffbea")
KN_GOLD = R("#9a5c06", "#c9840e", "#eeae1c", "#ffd040", "#ffee90")
KN_AMBER = R("#ff6a00", "#ff8a08", "#ffa41c", "#ffbe3c")
KN_CHAIN = R("#231f1a", "#332d26", "#453e34", "#5a5246")


def kn_plate(c):
    v = noise(c, 0.45, 0.45, 0.45, 3) * 0.4 + 0.4
    return rgba(KN_PLATE.at(v, c.shade))


def kn_dark(c):
    return rgba((38, 26, 14))


def kn_gold(c):
    v = noise(c, 0.6, 0.6, 0.6, 5) * 0.45 + 0.4
    return rgba(KN_GOLD.at(v, c.shade))


def kn_amber(c):
    return rgba(KN_AMBER.at(clamp01(0.5 + noise(c, 1.3, 1.3, 1.3, 9) * 0.45)))


def kn_chain(c):
    # little interlocked rings: a checker of dark and lighter links
    v = 0.25 + (0.45 if (c.i + c.j) % 2 == 0 else 0.0) + noise(c, 1.0, 1.0, 1.0, 2) * 0.15
    return rgba(KN_CHAIN.at(v))


MATERIALS.update({"kn_plate": kn_plate, "kn_gold": kn_gold, "kn_amber": kn_amber, "kn_chain": kn_chain, "kn_dark": kn_dark})


def _pix(hexes):
    """Pixel-art material for small 3D parts: the top face is the lightest tone, sides step darker towards the bottom, the lowest
    row and the back edge get the shadow tone. No noise, so it matches the hand-placed armor texture."""
    cols = [tuple(int(h[i:i + 2], 16) for i in (1, 3, 5)) for h in hexes]     # dark -> light

    def fn(c):
        n = len(cols)
        if c.n == "up":
            k = n - 1 if (0 < c.i < c.w - 1 or c.w <= 2) else n - 2
        elif c.n == "down":
            k = 0
        else:
            k = n - 2 - int((c.j / max(1, c.h)) * (n - 2) + 0.35)
            if c.j == c.h - 1 and c.h > 1:
                k = 0
            if c.i == 0 and c.w > 2:
                k = max(0, k - 1)
        return rgba(cols[max(0, min(n - 1, k))])
    return fn


MATERIALS.update({
    "kn_gold_px": _pix(["#8f4f08", "#c97d10", "#f0a81c", "#ffc92e", "#ffe770"]),
    "kn_steel_px": _pix(["#1f1d27", "#302e3b", "#474555", "#6b6980"]),
    "kn_cloth_px": _pix(["#7c4808", "#a4640c", "#cd8a14", "#f0b222"]),     # cream cloth (the reference has no red)
    "kn_ivory_px": _pix(["#b8700c", "#e29a18", "#fcbe28", "#ffd848", "#fff08a"]),
    "kn_chain_px": _pix(["#5e3406", "#7c4808", "#a4640c", "#c8800e"]),
    "kn_amber_px": _pix(["#ff6f08", "#ffab28", "#ffe88c"]),
})


# ---------------------------------------------------------------------------------------------------------
#  TUFF ARMOR (owner: "powerful glowing tuff", purple glow). Greyish-green mottled stone like the vanilla tuff block, chiselled edges,
#  purple veins. Every tuff material has a GLOW twin (GLOW_FNS) that returns only the vein texels, painted into a second atlas which the
#  game draws full-bright (RenderLayers.eyes) on top.
# ---------------------------------------------------------------------------------------------------------
TUFF = R("#46473f", "#53544b", "#62635a", "#72736a", "#83847a")
TUFF_DARK = R("#34352f", "#3f4039", "#4b4c44", "#585950", "#66675d")
TUFF_LIGHT = R("#6f7066", "#7f8077", "#909187", "#a2a398", "#b6b7ab")
PURPLE = R("#3a0f6a", "#6a22c0", "#9a4dff", "#c58cff", "#efd8ff")
PURPLE_DEEP = R("#1d0a3a", "#2e1060", "#43168a")     # the unlit vein colour painted into the normal texture (glow pass adds the bright one)


def _tuff_value(c, k):
    big = fbm3(c.wx * 0.16, c.wy * 0.16, c.wz * 0.16, c.seed + k, 2)              # large mottled blotches (tuff is blotchy)
    fine = fbm3(c.wx * 0.55, c.wy * 0.55, c.wz * 0.55, c.seed + k + 7, 2)
    v = big * 0.6 + fine * 0.4
    if hash01(c.i + c.wx * 3.1, c.j + c.wy * 2.3, c.seed + k, int(c.wz * 2.7)) < 0.07:
        v += 0.3 if hash01(c.i, c.j, c.seed + k + 1) < 0.5 else -0.3                # pale flecks and dark pits
    return v


def _tuff_edge(c):
    """-1 on the outer ring of a face (chiselled dark rim), +1 on the next ring on top/left edges (light bevel), else 0."""
    if c.w >= 3 and c.h >= 3:
        if c.i == 0 or c.j == c.h - 1:
            return -1
        if c.j == 0 or c.i == c.w - 1:
            return 1 if c.n in ("up",) else 0
    return 0


def _vein_mask(c, scale=0.085, width=0.04, k=0):
    # per-box random offset + a little warp: on a flat face one coordinate is constant, and if it sat on a cell wall the whole face became a vein
    ox, oy, oz = (hash01(c.seed, 11 + k) * 40.0, hash01(c.seed, 12 + k) * 40.0, hash01(c.seed, 13 + k) * 40.0)
    wob = fbm3(c.wx * 0.3, c.wy * 0.3, c.wz * 0.3, c.seed + 77, 2) * 3.0
    return voronoi_edge((c.wx + ox + wob) * scale, (c.wy + oy - wob) * scale, (c.wz + oz + wob * 0.5) * scale, c.seed + 41 + k) < width


def _tuff_with(ramp, vein, vs=0.085, vw=0.04):
    def fn(c):
        e = _tuff_edge(c)
        if vein and _vein_mask(c, vs, vw):
            return rgba(PURPLE_DEEP.at(0.5 + 0.4 * fbm3(c.wx, c.wy, c.wz, c.seed + 3, 1)))
        v = _tuff_value(c, 2)
        return rgba(ramp.at(v, c.shade + (e if e else 0)))
    return fn


def _glow_vein(vein_scale=0.085, width=0.04):
    def fn(c):
        if _vein_mask(c, vein_scale, width):
            t = 0.45 + 0.55 * fbm3(c.wx * 0.8, c.wy * 0.8, c.wz * 0.8, c.seed + 9, 2)
            return rgba(PURPLE.at(t))
        return None
    return fn


def glow_purple(c):
    """Glowing crystal / light strip: mottled bright purple with a pale core."""
    t = 0.55 + 0.45 * fbm3(c.wx * 0.9, c.wy * 0.9, c.wz * 0.9, c.seed + 5, 2)
    if hash01(c.i, c.j, c.seed + 6) < 0.12:
        t = 1.0
    return rgba(PURPLE.at(t))


def glow_core(c):
    """The hottest part of a crystal: almost white lilac."""
    t = 0.8 + 0.2 * hash01(c.i, c.j, c.seed + 8)
    return rgba(PURPLE.at(t))


GLOW_FNS = {"tuff": _glow_vein(), "tuff_dark": _glow_vein(0.1, 0.045), "tuff_light": _glow_vein(0.07, 0.03),
            "glow_purple": glow_purple, "glow_core": glow_core}
MATERIALS.update({"tuff": _tuff_with(TUFF, True), "tuff_dark": _tuff_with(TUFF_DARK, True, 0.1, 0.045), "tuff_light": _tuff_with(TUFF_LIGHT, True, 0.07, 0.03),
                  "tuff_plain": _tuff_with(TUFF, False), "tuff_dark_plain": _tuff_with(TUFF_DARK, False),
                  "glow_purple": glow_purple, "glow_core": glow_core})


def shell_crack(c):
    """GLOWING CRACKS on the armor's own surface (owner: "they are cracks, not outlines ... not uniform"). Cracks only appear in some zones of the
    armor (low-frequency noise), are wider/brighter in the middle of a crack and fade at the ends, branch in the heavily cracked zones and
    have gaps. Returns None where the armor is not cracked."""
    zone = fbm3(c.wx * 0.045 + 11.0, c.wy * 0.045, c.wz * 0.045 - 7.0, c.seed + 101, 2)
    if zone < 0.43:
        return None
    heat = clamp01((zone - 0.43) / 0.22)
    wob = fbm3(c.wx * 0.25, c.wy * 0.25, c.wz * 0.25, c.seed + 7, 2) * 2.5
    d1 = voronoi_edge((c.wx + wob) * 0.07, (c.wy - wob) * 0.07, (c.wz + wob * 0.5) * 0.07, c.seed + 11)
    width = 0.045 + 0.07 * fbm3(c.wx * 0.1, c.wy * 0.1, c.wz * 0.1, c.seed + 13, 2)       # varying thickness
    core = d1 < width
    if not core and heat > 0.4:                                                           # fine side branches in the worst zones
        d2 = voronoi_edge((c.wx - wob) * 0.19, (c.wy + wob) * 0.19, c.wz * 0.19, c.seed + 29)
        if d2 < 0.05 * heat:
            if hash01(c.i, c.j, c.seed + 19) < 0.25:
                return None
            return rgba(PURPLE.at(0.2 + 0.3 * heat))
    if not core:
        return None
    if hash01(c.i, c.j, c.seed + 17) < 0.1:
        return None                                                                       # gaps, so it reads as cracks, not lines
    t = 1.0 - d1 / width
    return rgba(PURPLE.at(0.2 + 0.8 * t * (0.55 + 0.45 * heat)))


GLOW_FNS["shell"] = shell_crack
MATERIALS["shell"] = lambda c: (0, 0, 0, 0)          # the normal-texture pass draws nothing for a shell; only the glow pass does

MATERIALS["tuff_light_plain"] = _tuff_with(TUFF_LIGHT, False)


# polished-tuff materials for 3D armor parts (same 8 tones as the worn layers, tuffpal.PAL)
def _ptuff(lo, hi):
    def fn(c):
        import tuffpal
        pal = tuffpal.PAL
        v = fbm3(c.wx * 0.5, c.wy * 0.5, c.wz * 0.5, c.seed + 2, 2) * 0.7 + hash01(c.i, c.j, c.seed) * 0.3
        idx = int(lo + v * (hi - lo + 0.99)) + c.shade
        return rgba(pal[max(0, min(len(pal) - 1, idx))])
    return fn


MATERIALS.update({"ptuff": _ptuff(2, 5), "ptuff_dark": _ptuff(0, 3), "ptuff_light": _ptuff(4, 7)})
