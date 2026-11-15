#!/usr/bin/env python3
"""
make_essence_icon.py - draws the Magic Essence icon (tools/reference/icons/magic_essence.png): a little blue spirit-flame, a round glowing body
with flickering tufts and a few drifting embers (the owner's reference picture, WITHOUT the face). Edit the ASCII map / palette below and re-run:

    python3 tools/make_essence_icon.py && python3 tools/sprite_extrude.py magic_essence && python3 tools/rebuild_all.py --jar
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "reference", "icons", "magic_essence.png")
PALETTE = {
    "O": (6, 54, 168),       # darkest outline blue
    "D": (10, 96, 226),      # deep blue
    "M": (22, 148, 255),     # mid blue
    "C": (36, 200, 255),     # cyan
    "L": (110, 232, 255),    # light cyan core
    "W": (225, 252, 255),    # white-hot highlight
}
SPRITE = [
    "..........MM........",
    "...........MD.......",
    "....................",
    "........MMM...M.....",
    ".......DMMM..MM.....",
    "....DD.DMC..DMM.....",
    "...DMMDMCCMDMM......",
    "..DMMMMCCCMMMMD.M...",
    "..DMM.MCLCCMMMMMD...",
    "..DMM.DCLLCCCMMMD...",
    "..DM...DCLLLCCMMMD..",
    "...D..DCLLLLLCCMMD..",
    ".....DMCLLLLLLCCMD..",
    "....DMCLLLLLLLLCMD..",
    "...DMCLLLWLLLLLLCMD.",
    "...DMCLLWLLLLLLLCMD.",
    "...DMCLLLLLLLLLLCMD.",
    "...DMCCLLLLLLLLCCMD.",
    "....DMCCLLLLLLCCMMD.",
    "....DDMCCLLLLCCMMD..",
    ".....DDMCCCCCCMMDD..",
    "......DDMMMMMMDD....",
    ".......DDDDDDDD.....",
]


def build():
    img = np.zeros((32, 32, 4), np.uint8)
    h = len(SPRITE)
    w = max(len(r) for r in SPRITE)
    ox, oy = (32 - w) // 2, (32 - h) // 2
    for y, row in enumerate(SPRITE):
        for x, ch in enumerate(row):
            if ch in PALETTE:
                img[oy + y, ox + x] = (*PALETTE[ch], 255)
    return img


if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    Image.fromarray(build(), "RGBA").save(OUT)
    print("wrote", OUT)
