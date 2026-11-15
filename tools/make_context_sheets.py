#!/usr/bin/env python3
"""
make_context_sheets.py - builds one screenshot sheet per item (inventory, first/third person, item frame, ground, worn...) from the REAL game
screenshots taken by the gametest (build/run/clientGameTest/screenshots).

    TUFF_ONLY="gui zoom,ground close,hand ,frame closeups,worn closeup,orb states" DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 \
      GALLIUM_DRIVER=llvmpipe MESA_GL_VERSION_OVERRIDE=4.5 ./gradlew runClientGameTest
    python3 tools/make_context_sheets.py [screenshot_dir]       # -> screenshots/<item>.png and screenshots/_overview_inventory.png
"""
import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
D = (sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "run", "clientGameTest", "screenshots")) + "/"
OUT = os.path.join(ROOT, "screenshots")
os.makedirs(OUT, exist_ok=True)
FONT = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
PW, PH = 480, 360


def get(name):
    f = sorted(glob.glob(D + "*_" + name + ".png"))
    return Image.open(f[-1]).convert("RGB") if f else None


def panel(img, box, label):
    p = img.crop(box).resize((PW, PH), Image.NEAREST)
    d = ImageDraw.Draw(p)
    d.rectangle((0, 0, PW, 30), fill=(0, 0, 0))
    d.text((8, 3), label, font=FONT, fill=(255, 255, 255))
    return p


def base(item):
    return [panel(get("z_gui_" + item), (187, 60, 667, 420), "Inventory (big)"),
            panel(get("a_1p_" + item), (374, 120, 854, 480), "In hand - first person"),
            panel(get("a_3p_" + item), (307, 200, 547, 380), "In hand - third person"),
            panel(get("g_frame_" + item), (277, 127, 577, 352), "In an item frame"),
            panel(get("y_ground_" + item), (327, 170, 527, 320), "On the ground")]


WORN = {"shrine_helmet": (362, 152, 482, 242), "shrine_chestplate": (340, 205, 520, 340),
        "shrine_leggings": (370, 315, 490, 405), "shrine_boots": (380, 345, 490, 427)}
ITEMS = ["molten_greataxe", "cutlass_of_doom", "sculk_mace", "frost_longbow", "cloud_lance", "shrine_helmet", "shrine_chestplate",
         "shrine_leggings", "shrine_boots", "harbinger_of_death", "anaklusmos", "sculk_cannon", "strange_cape", "silvers_spawn_orb"]

thumbs = []
for it in ITEMS:
    ps = base(it)
    thumbs.append(ps[0])
    if it in WORN:
        ps.append(panel(get("w_outfit_front"), WORN[it], "Worn - front (close)"))
        ps.append(panel(get("w_outfit_back"), WORN[it], "Worn - back (close)"))
        ps.append(panel(get("w_outfit_front"), (300, 145, 560, 340), "Worn - whole outfit"))
    if it == "strange_cape":
        ps.append(panel(get("w_cape_front"), (330, 160, 530, 310), "Worn - front"))
        ps.append(panel(get("w_cape_back"), (330, 165, 530, 315), "Worn - back"))
    if it == "silvers_spawn_orb":
        for st, lab in (("stored", "Orb - Silver inside (in hand)"), ("released", "Orb - Silver released (in hand)"), ("cooldown", "Orb - recharging (in hand)")):
            ps.append(panel(get("o_orb_" + st), (374, 120, 854, 480), lab))
    cols = 3
    rows = (len(ps) + cols - 1) // cols
    sheet = Image.new("RGB", (PW * cols, PH * rows), (20, 20, 24))
    for i, p in enumerate(ps):
        sheet.paste(p, ((i % cols) * PW, (i // cols) * PH))
    sheet.save(os.path.join(OUT, it + ".png"))

TW, TH = 320, 240
ov = Image.new("RGB", (TW * 5, TH * 3), (20, 20, 24))
for i, t in enumerate(thumbs):
    ov.paste(t.resize((TW, TH)), ((i % 5) * TW, (i // 5) * TH))
ov.save(os.path.join(OUT, "_overview_inventory.png"))
print("sheets written to", OUT)
