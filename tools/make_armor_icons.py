#!/usr/bin/env python3
"""make_armor_icons.py - the 32x32 inventory icons of the four Shrine armor pieces, HAND-DRAWN so each one is instantly recognisable
(helmet = dome with a visor slit + crest, chestplate = shoulders + gem, leggings = belt + two legs, boots = two pointed boots) in the same
ivory / gold / amber knight colours as the worn armor. Each icon is a 16x16 ASCII map drawn at 2x with a lit top-left texel and a
dark outline. The WORN armor textures are not touched by this script.
Then:  python3 tools/make_armor_icons.py && python3 tools/sprite_extrude.py shrine_helmet shrine_chestplate shrine_leggings shrine_boots
Letters:  o outline | b deep gold | g gold | G bright gold | e highlight gold | i ivory shade | I ivory | w ivory light | s dark steel | t steel
          a amber | A bright amber | c cream cloth | . empty"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PAL = {
    "o": (52, 28, 8), "b": (148, 83, 10), "g": (204, 132, 16), "G": (240, 179, 34), "e": (255, 226, 110),
    "i": (185, 120, 12), "I": (240, 182, 30), "w": (255, 232, 130), "s": (38, 36, 48), "t": (84, 82, 100),
    "a": (255, 111, 8), "A": (255, 190, 60), "c": (190, 172, 132),
}
ART = {
    "shrine_helmet": [
        ".......ee.......",
        "......eGGe......",
        ".....oGggGo.....",
        "....oIIwwIIo....",
        "...oIwwIIIwIo...",
        "..oGGGGGGGGGGo..",
        "..oIIsssssssIIo.",
        "..oIsAAsssAAsIo.",
        "..oIIsssIsssIIo.",
        "..oiIIIIGIIIIio.",
        "..oiIIsIGIsIIio.",
        "..oiiIIsGsIIiio.",
        "...obiiIGIiibo..",
        "....obbiGibbo...",
        ".....ooooooo....",
        "................",
    ],
    "shrine_chestplate": [
        "................",
        ".oGGo......oGGo.",
        "oGeGGoo..ooGGeGo",
        "oGgGGIIooIIGGgGo",
        "obgGIIwIIwIIGgbo",
        ".obGIIIGGGIIIGbo",
        "..oiIIGAAAGIIio.",
        "..oiIIGAaAGIIio.",
        "..oiIIIGGGIIIio.",
        "..oIIIwIIwIIIIo.",
        "..oGGGGGGGGGGGo.",
        "..oIwIIGGIIwIio.",
        "..oiIIIGGIIIiio.",
        "...oiiIGGIiiio..",
        "....oobbbbboo...",
        "................",
    ],
    "shrine_leggings": [
        "..oGGGGGGGGGGo..",
        "..oGeGGaaGGeGo..",
        "..ogGGGGGGGGgo..",
        "..oIIIIGGIIIIo..",
        "..oIwIIGGIIwIo..",
        "..oIIIIoooIIIIo.",
        "..oIIIo...oIIIo.",
        "..oiIIo...oIIio.",
        "..oGGGo...oGGGo.",
        "..oGAGo...oGAGo.",
        "..oGGGo...oGGGo.",
        "..oIIIo...oIIIo.",
        "..oIwIo...oIwIo.",
        "..oiIIo...oIIio.",
        "..obbbo...obbbo.",
        "..ooooo...ooooo.",
    ],
    "shrine_boots": [
        "................",
        ".ooooo...ooooo..",
        ".oGGGo...oGGGo..",
        ".oeGGo...oeGGo..",
        ".oIIIo...oIIIo..",
        ".oIwIo...oIwIo..",
        ".oIIIo...oIIIo..",
        ".oiIIo...oiIIo..",
        ".oIIIooo.oIIIooo",
        ".oIwIIIo.oIwIIIo",
        "oGGGGGGooGGGGGGo",
        "oGeAGGbooGeAGGbo",
        "ooobbbboooobbbbo",
        "................",
        "................",
        "................",
    ],
}


def main():
    for name, rows in ART.items():
        im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        px = im.load()
        for y, row in enumerate(rows):
            for x, ch in enumerate(row[:16]):
                if ch == ".":
                    continue
                col = PAL[ch]
                for dy in (0, 1):
                    for dx in (0, 1):
                        c = col
                        if ch not in "oaA":
                            if dx == 0 and dy == 0:
                                c = tuple(min(255, v + 14) for v in col)
                            elif dx == 1 and dy == 1:
                                c = tuple(max(0, v - 14) for v in col)
                        px[x * 2 + dx, y * 2 + dy] = c + (255,)
        out = os.path.join(ROOT, "tools", "reference", "icons", name + ".png")
        im.save(out)
        print("icon", os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
