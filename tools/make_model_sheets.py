#!/usr/bin/env python3
"""
make_model_sheets.py - one sheet per item (armor excluded until it is redesigned): 4 full free-standing views (front / 3-4 / side / back), inventory,
item frame, on the ground, held in first person and third person.  Special sheets: Sculk Cannon (idle hangs down vs charged level),
Silver's Spawn Orb (stored / released / recharging).

Needs these gametest steps (run each with TUFF_MODEL=<ids> TUFF_ONLY=...; copy the screenshots into one folder):
    "model views"   "ground close"   "hand ,gui zoom"   "frame closeups,orb states,cannon states"
    python3 tools/make_model_sheets.py <screenshot_dir>     -> screenshots/models/<item>.png  and  _all_front_views.png
"""
import glob
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
D = (sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "build", "run", "clientGameTest", "screenshots")) + "/"
OUT = os.path.join(ROOT, "screenshots", "models")
os.makedirs(OUT, exist_ok=True)
for f in glob.glob(OUT + "/*.png"):
    os.remove(f)
FONT = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
PW, PH = 480, 360
ITEMS = ["molten_greataxe", "cutlass_of_doom", "sculk_mace", "frost_longbow", "cloud_lance", "harbinger_of_death", "anaklusmos", "sculk_cannon",
         "strange_cape", "silvers_spawn_orb", "magic_essence"]
FULL = (127, 0, 727, 450)


def get(name):
    f = sorted(glob.glob(D + "*_" + name + ".png"))
    return Image.open(f[-1]).convert("RGB") if f else None


def panel(img, box, label):
    if img is None:
        img = Image.new("RGB", (854, 480), (40, 40, 40))
    p = img.crop(box).resize((PW, PH), Image.NEAREST if box[2] - box[0] < 300 else Image.LANCZOS)
    d = ImageDraw.Draw(p)
    d.rectangle((0, 0, PW, 30), fill=(0, 0, 0))
    d.text((8, 3), label, font=FONT, fill=(255, 255, 255))
    return p


def sheet(panels, name, cols=3):
    rows = (len(panels) + cols - 1) // cols
    s = Image.new("RGB", (PW * cols, PH * rows), (20, 20, 24))
    for i, p in enumerate(panels):
        s.paste(p, ((i % cols) * PW, (i // cols) * PH))
    s.save(os.path.join(OUT, name + ".png"))


fronts = []
for it in ITEMS:
    FULL = (0, 0, 854, 480) if it == "sculk_cannon" else (127, 0, 727, 450)      # the cannon is wide: use the whole frame
    ps = [panel(get(f"mv_{it}_{v}"), FULL, lab) for v, lab in (("front", "Full view - front"), ("threequarter", "Full view - 3/4"),
                                                                ("side", "Full view - side"), ("back", "Full view - back"))]
    fronts.append(ps[0])
    ps.append(panel(get("z_gui_" + it), (187, 60, 667, 420), "Inventory (big)"))
    ps.append(panel(get("g_frame_" + it), (277, 127, 577, 352), "In an item frame"))
    ps.append(panel(get("y_ground_" + it), (327, 170, 527, 320), "On the ground"))
    if it == "sculk_cannon":
        ps.append(panel(get("cs_3p_idle"), (307, 200, 547, 380), "Held 3rd person - NOT charged"))
        ps.append(panel(get("cs_3p_charged"), (307, 200, 547, 380), "Held 3rd person - CHARGED"))
        ps.append(panel(get("cs_1p_idle"), (0, 0, 854, 480), "First person - NOT charged"))
        ps.append(panel(get("cs_1p_charged"), (0, 0, 854, 480), "First person - CHARGED"))
    else:
        ps.append(panel(get("a_3p_" + it), (307, 200, 547, 380), "Held - third person"))
        ps.append(panel(get("a_1p_" + it), (374, 120, 854, 480), "Held - first person"))
    if it == "silvers_spawn_orb":
        for st, lab in (("released", "Released (Silver out) - full view"), ("cooldown", "Recharging - full view")):
            ps.append(panel(get(f"mv_silvers_spawn_orb_{st}_front"), FULL, lab))
        for st, lab in (("stored", "Stored - held"), ("released", "Released - held"), ("cooldown", "Recharging - held")):
            ps.append(panel(get("o_orb_" + st), (374, 120, 854, 480), lab))
    sheet(ps, it)
TW, TH = 320, 240
ov = Image.new("RGB", (TW * 4, TH * 3), (20, 20, 24))
for i, p in enumerate(fronts):
    ov.paste(p.resize((TW, TH)), ((i % 4) * TW, (i // 4) * TH))
ov.save(os.path.join(OUT, "_all_front_views.png"))
print("sheets in", OUT)
