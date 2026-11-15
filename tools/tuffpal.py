"""
tuffpal.py - the POLISHED TUFF look shared by the worn armor layers (paint_textures.py) and the item icons (make_polished_tuff_armor.py).

Owner: "make it look like netherite with the shape and layers but the actual texturing should look like polished tuff".
The shape / panels / rivets stay the vanilla netherite textures; every pixel's COLOUR comes from the real vanilla polished_tuff.png
(tools/reference/polished_tuff.png, 8 blue-grey-green tones, tiled over the sheet), shifted lighter / darker by how light or dark the netherite
pixel was, so the netherite panel shading survives but the surface reads as polished tuff.
"""
import os

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
_PT = Image.open(os.path.join(HERE, "reference", "polished_tuff.png")).convert("RGB")
PAL = sorted({_PT.getpixel((x, y)) for x in range(16) for y in range(16)}, key=lambda c: 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2])


def polished_tuff(x, y, lum):
    """Colour for layer / icon pixel (x, y) whose netherite luminance was `lum` (about 12..112)."""
    tile = _PT.getpixel((x % 16, y % 16))
    ti = PAL.index(tile)
    t = max(0.0, min(1.0, (lum - 12.0) / 100.0))
    shift = int(round((t - 0.45) * 5.0))
    return PAL[max(0, min(len(PAL) - 1, ti + shift))]


# ---------------------------------------------------------------------------------------------------------------------------------------
#  VARIATION + UNIQUENESS (owner: "add variation and uniqueness" -> across the armor as a whole; colour variation, weathering and chips,
#  mineral flecks; SUBTLE). Applied to a finished RGBA array (worn layer sheet or icon) by weather(); each piece uses its own seed so
#  the four pieces never repeat the same pattern, yet they share the same stone.
# ---------------------------------------------------------------------------------------------------------------------------------------
import sys
sys.path.insert(0, HERE)
from common import fbm3, hash01  # noqa: E402


def weather(arr, seed, scale=1.0):
    """arr: numpy uint8 (h, w, 4), modified in place and returned. `scale` > 1 for tiny icons (bigger features)."""
    h, w = arr.shape[0], arr.shape[1]
    opaque = arr[..., 3] > 0

    def clear(x, y):
        return not (0 <= x < w and 0 <= y < h) or not opaque[y, x]
    chips = []
    for y in range(h):
        for x in range(w):
            if not opaque[y, x]:
                continue
            r, g, b = [int(v) for v in arr[y, x, :3]]
            # 1) colour variation: big slow drift between cool blue-grey, green-grey and warm brown-grey tuff, plus a finer shimmer
            warm = (fbm3(x * 0.11 / scale + seed, y * 0.11 / scale, seed * 0.37, seed, 2) - 0.5) * 2.0
            green = (fbm3(x * 0.2 / scale, y * 0.2 / scale + seed, seed * 0.71, seed + 5, 2) - 0.5) * 2.0
            fine = (hash01(x, y, seed + 1) - 0.5) * 2.0
            r += int(warm * 6 - green * 2 + fine * 1.5)
            g += int(green * 5 - warm * 1 + fine * 1.5)
            b += int(-warm * 6 - green * 2 + fine * 1.5)
            # 2) weathering: worn lighter edges, a few dark dents, and chipped corners / edge pixels
            nclear = sum(clear(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            hv = hash01(x, y, seed + 2)
            if nclear >= 2 and hv < 0.2:
                chips.append((x, y))                                   # a missing corner / edge pixel
                continue
            if nclear >= 1:
                if hv < 0.42:
                    r, g, b = r + 12, g + 12, b + 11                     # worn, lighter edge
                elif hv > 0.88:
                    r, g, b = r - 12, g - 12, b - 12                     # grimy edge
            if hash01(x, y, seed + 3) < 0.022:
                r, g, b = r - 16, g - 15, b - 15                         # tiny dent
            # 3) mineral flecks: pale crystal specks, rare rust-brown specks, dark mica specks
            fv = hash01(x, y, seed + 4)
            if fv < 0.028:
                r, g, b = r + 26, g + 26, b + 24
            elif fv < 0.04:
                r, g, b = r + 24, g + 8, b - 14
            elif fv < 0.055:
                r, g, b = r - 22, g - 22, b - 20
            arr[y, x, 0], arr[y, x, 1], arr[y, x, 2] = max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b))
    for (x, y) in chips:
        arr[y, x, 3] = 0
    return arr
