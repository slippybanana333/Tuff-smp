#!/usr/bin/env python3
"""armor_concepts.py - quick front-view pixel mock-ups of armor DIRECTIONS for the owner to choose from (not game assets).
Output: screenshots/armor_concepts.png.  Each concept = palette + ornaments painted on a 16x32 player 'paper doll'."""
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H, OX, OY = 26, 42, 5, 8
SKIN, SKIN_D, EYE = (198, 134, 96), (160, 104, 72), (60, 40, 110)

CONCEPTS = [
    dict(name="1 TIDECALLER", sub="ocean - prismarine plate, coral fins, pearl", base=(38, 120, 128), dark=(16, 62, 78), light=(104, 196, 190),
         accent=(232, 122, 86), glow=(160, 255, 240), orn="fins"),
    dict(name="2 MAGMA WARDEN", sub="lava - obsidian plate, molten cracks, horns", base=(44, 30, 36), dark=(18, 12, 16), light=(86, 62, 70),
         accent=(120, 40, 20), glow=(255, 150, 30), orn="horns"),
    dict(name="3 FROSTBORN", sub="arctic - ice crystal crown, fur trim", base=(150, 196, 232), dark=(70, 110, 170), light=(232, 246, 255),
         accent=(244, 244, 250), glow=(190, 240, 255), orn="crown"),
    dict(name="4 SCULK REAVER", sub="sculk - warden horns, living veins", base=(14, 44, 50), dark=(4, 18, 22), light=(30, 84, 90),
         accent=(8, 28, 34), glow=(40, 220, 210), orn="warden"),
    dict(name="5 VOIDWALKER", sub="end - dragon purple, gold, crystal heart", base=(58, 36, 86), dark=(24, 14, 40), light=(110, 76, 150),
         accent=(224, 190, 96), glow=(230, 130, 255), orn="dragon"),
    dict(name="6 ELEMENTAL MYTHIC", sub="one biome per piece: frost helm, magma chest, tide legs, void boots", base=None, dark=None, light=None,
         accent=(224, 190, 96), glow=None, orn="mixed"),
]


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def draw_concept(c):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = im.load()
    rnd = random.Random(c["name"])

    def P(x, y, col):
        if 0 <= x < W and 0 <= y < H:
            px[x, y] = tuple(col) + (255,)

    def pal(part):
        if c["orn"] != "mixed":
            return c
        return {"helmet": CONCEPTS[2], "chest": CONCEPTS[1], "legs": CONCEPTS[0], "boots": CONCEPTS[4]}[part]

    def plate(x0, y0, x1, y1, part, veins=2):
        p = pal(part)
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                col = p["base"]
                if x == x0 or y == y0:
                    col = p["light"]
                if x == x1 or y == y1:
                    col = p["dark"]
                P(x, y, col)
        for _ in range(veins):
            x, y = rnd.randint(x0, x1), rnd.randint(y0, y1)
            for _ in range(rnd.randint(2, 4)):
                P(x, y, p["glow"])
                x += rnd.choice((-1, 0, 1))
                y += 1
                if not (x0 <= x <= x1 and y0 <= y <= y1):
                    break

    # --- body (skin) ---
    for y in range(OY, OY + 8):
        for x in range(OX + 4, OX + 12):
            P(x, y, SKIN)
    for y in range(OY + 8, OY + 20):
        for x in list(range(OX, OX + 4)) + list(range(OX + 12, OX + 16)):
            P(x, y, SKIN)
    # --- boots, leggings ---
    plate(OX + 4, OY + 20, OX + 7, OY + 27, "legs")
    plate(OX + 8, OY + 20, OX + 11, OY + 27, "legs")
    for x in range(OX + 4, OX + 12):
        P(x, OY + 20, pal("legs")["accent"] if c["orn"] != "mixed" else c["accent"])      # belt
    plate(OX + 4, OY + 28, OX + 7, OY + 31, "boots", veins=1)
    plate(OX + 8, OY + 28, OX + 11, OY + 31, "boots", veins=1)
    for x in (OX + 4, OX + 7, OX + 8, OX + 11):
        P(x, OY + 28, pal("boots")["light"])
    # --- chestplate + arms ---
    plate(OX + 4, OY + 8, OX + 11, OY + 19, "chest", veins=3)
    plate(OX, OY + 8, OX + 3, OY + 16, "chest", veins=1)
    plate(OX + 12, OY + 8, OX + 15, OY + 16, "chest", veins=1)
    pc = pal("chest")
    for x in range(OX - 1, OX + 4):                         # pauldrons stick out
        P(x, OY + 7, pc["light"]); P(x, OY + 8, pc["base"])
    for x in range(OX + 12, OX + 17):
        P(x, OY + 7, pc["light"]); P(x, OY + 8, pc["base"])
    # chest emblem: a glowing gem with an accent frame
    for (dx, dy) in ((0, 0), (1, 0), (0, 1), (1, 1)):
        P(OX + 7 + dx, OY + 11 + dy, pc["glow"])
    for (dx, dy) in ((-1, 0), (2, 0), (-1, 1), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)):
        P(OX + 7 + dx, OY + 11 + dy, c["accent"] if c["orn"] == "mixed" else pc["accent"])
    for y in range(OY + 15, OY + 20):                       # tasset lines
        P(OX + 4, y, pc["dark"]); P(OX + 11, y, pc["dark"])
    # --- helmet ---
    ph = pal("helmet")
    plate(OX + 4, OY, OX + 11, OY + 7, "helmet", veins=1)
    for y in range(OY + 3, OY + 6):                         # face opening
        for x in range(OX + 5, OX + 11):
            P(x, y, SKIN if y != OY + 5 else SKIN_D)
    P(OX + 6, OY + 4, EYE); P(OX + 9, OY + 4, EYE)
    for x in range(OX + 5, OX + 11):
        P(x, OY + 2, ph["light"])
    # --- ornaments ---
    orn = c["orn"]
    if orn in ("fins",):
        for i in range(4):                                   # side fins on the helmet + arm fins
            P(OX + 3 - (i // 2), OY + 1 + i, c["accent"]); P(OX + 12 + (i // 2), OY + 1 + i, c["accent"])
        for i in range(3):
            P(OX - 1, OY + 11 + i, c["accent"]); P(OX + 16, OY + 11 + i, c["accent"])
        for x in range(OX + 6, OX + 10):
            P(x, OY - 1, c["light"])
        P(OX + 7, OY - 2, c["glow"]); P(OX + 8, OY - 2, c["glow"])
    if orn in ("horns",):
        for i in range(4):
            P(OX + 3 - min(i, 2), OY - i, c["light"] if i < 3 else c["glow"]); P(OX + 12 + min(i, 2), OY - i, c["light"] if i < 3 else c["glow"])
        for x in (OX - 2, OX + 17):
            P(x, OY + 6, c["glow"]); P(x, OY + 7, c["light"])
    if orn in ("crown", "mixed"):
        p = CONCEPTS[2]
        for i, hgt in enumerate((2, 3, 4, 5, 4, 3, 2)):
            x = OX + 4 + i + (1 if i > 2 else 0)
            for k in range(hgt):
                P(x, OY - 1 - k, p["light"] if k < hgt - 1 else p["glow"])
        if orn == "crown":
            for x in range(OX + 3, OX + 13):                # fur collar
                P(x, OY + 8, c["accent"])
            for x in range(OX + 4, OX + 12):
                P(x, OY + 28, c["accent"])
    if orn in ("warden",):
        for i in range(5):                                   # tall curved warden horns
            P(OX + 3 - (1 if i > 1 else 0), OY + 1 - i, c["light"] if i < 4 else c["glow"])
            P(OX + 12 + (1 if i > 1 else 0), OY + 1 - i, c["light"] if i < 4 else c["glow"])
        for y in range(OY + 9, OY + 12):                     # ribcage "souls" opening
            P(OX + 6, y, c["glow"]); P(OX + 9, y, c["glow"])
    if orn in ("dragon",):
        for i in range(4):
            P(OX + 4 + i // 2 - 1, OY - 1 - i, c["dark"] if i < 3 else c["accent"]); P(OX + 11 - i // 2 + 1, OY - 1 - i, c["dark"] if i < 3 else c["accent"])
        for x in range(OX + 4, OX + 12):
            P(x, OY, c["accent"])
        for y in range(OY + 9, OY + 19):                     # small cape wings behind the arms
            P(OX - 1 - (y - OY - 9) // 4, y, c["dark"]); P(OX + 16 + (y - OY - 9) // 4, y, c["dark"])
        for x in (OX + 4, OX + 7, OX + 8, OX + 11):
            P(x, OY + 31, c["accent"])
    if orn == "mixed":
        for i in range(3):
            P(OX - 1 - (i // 2), OY + 6 - i, CONCEPTS[1]["glow"] if i == 2 else CONCEPTS[1]["light"])
            P(OX + 16 + (i // 2), OY + 6 - i, CONCEPTS[1]["glow"] if i == 2 else CONCEPTS[1]["light"])
        for i in range(3):
            P(OX + 3, OY + 22 + i, CONCEPTS[0]["accent"]); P(OX + 12, OY + 22 + i, CONCEPTS[0]["accent"])
    return im


def main():
    S = 9
    cols = 3
    cw, ch = W * S + 40, H * S + 54
    sheet = Image.new("RGB", (cols * cw, 2 * ch), (24, 26, 34))
    d = ImageDraw.Draw(sheet)
    for i, c in enumerate(CONCEPTS):
        x0, y0 = (i % cols) * cw, (i // cols) * ch
        d.rectangle((x0 + 6, y0 + 6, x0 + cw - 6, y0 + ch - 6), fill=(36, 40, 52))
        im = draw_concept(c).resize((W * S, H * S), Image.NEAREST)
        sheet.paste(im, (x0 + 20, y0 + 8), im)
        d.text((x0 + 14, y0 + ch - 42), c["name"], fill=(255, 226, 140))
        d.text((x0 + 14, y0 + ch - 26), c["sub"], fill=(220, 224, 232))
    out = os.path.join(ROOT, "screenshots", "armor_concepts.png")
    sheet.save(out)
    print(out, sheet.size)


if __name__ == "__main__":
    main()
