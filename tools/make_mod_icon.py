#!/usr/bin/env python3
"""Makes the mod icon and the resource-pack icon from ONE photo, cropped to a square centred on the head.

  python3 tools/make_mod_icon.py <photo.png|jpg> [centre_x centre_y side]   (pixels of the original photo)

Defaults are for the owner's 1920x1080 photo: head centre about (530, 430), crop side 800.
Writes:  src/main/resources/assets/shrinecraft/icon.png  (512x512, the MOD icon, also shown in the mod list / inside the jar)
         tools/reference/pack_icon.png                   (128x128, the RESOURCE PACK icon; build_resourcepack.py zips it as pack.png)
and adds  "icon": "assets/shrinecraft/icon.png"  to fabric.mod.json.
Nothing is rebuilt here - run rebuild_all.py (add --jar for the mod) when you want the new icon packaged.
"""
import json, os, sys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    img = Image.open(sys.argv[1]).convert("RGB")
    w, h = img.size
    cx = float(sys.argv[2]) if len(sys.argv) > 2 else w * 530 / 1920
    cy = float(sys.argv[3]) if len(sys.argv) > 3 else h * 430 / 1080
    side = float(sys.argv[4]) if len(sys.argv) > 4 else min(w, h) * 800 / 1080
    side = min(side, w, h)
    # keep the square inside the photo while staying as centred on the head as possible
    left = max(0, min(w - side, cx - side / 2))
    top = max(0, min(h - side, cy - side / 2))
    sq = img.crop((round(left), round(top), round(left + side), round(top + side)))
    mod_icon = os.path.join(ROOT, "src", "main", "resources", "assets", "shrinecraft", "icon.png")
    pack_icon = os.path.join(ROOT, "tools", "reference", "pack_icon.png")
    sq.resize((512, 512), Image.LANCZOS).save(mod_icon)
    sq.resize((128, 128), Image.LANCZOS).save(pack_icon)
    fmj = os.path.join(ROOT, "src", "main", "resources", "fabric.mod.json")
    data = json.load(open(fmj, encoding="utf-8"))
    data["icon"] = "assets/shrinecraft/icon.png"
    json.dump(data, open(fmj, "w", encoding="utf-8"), indent=2)
    print("mod icon:", mod_icon, "\npack icon:", pack_icon)


if __name__ == "__main__":
    main()
