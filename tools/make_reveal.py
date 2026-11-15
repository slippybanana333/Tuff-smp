#!/usr/bin/env python3
"""
make_reveal.py - makes the "recipe reveal" clip for one legendary: a glowing crafting table, the ingredients drop onto the grid, flash, the real
3D item appears over the table and its name drops in as a pixel title (remake of the owner's reference clip).

    python3 tools/make_reveal.py sculk_mace                       # -> build/reveal/sculk_mace_reveal.mp4 (1280x720, 60 fps, ~6.4 s)
    python3 tools/make_reveal.py sculk_mace --preview 0.9,2.5,4.5 # only render those moments as PNGs (fast way to check a change)
    python3 tools/make_reveal.py sculk_mace --size 1920x1080 --fps 30

What it uses (nothing is invented):
  * the item's REAL model + texture from src/main/resources/assets/shrinecraft (models/item/<id>.json, textures/item/<id>.png),
  * the item's REAL ingredients from shrine_recipes.json (one of each kind; counts of 2-3 show 2-3), textures from the Minecraft jar,
  * the Minecraft font (ascii.png in the jar) for the title, the crafting table / floor textures from the jar.
Vanilla textures are read from the game jar at run time and cached in tools/reveal/.cache (git-ignored, never committed).
Per-item look (colours, how it lies on the table) is in ITEM_LOOK below; the timeline of the clip is TIMELINE.
Needs: node + playwright (global), ffmpeg.
"""
import argparse
import base64
import glob
import io
import json
import os
import subprocess
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REVEAL = os.path.join(ROOT, "tools", "reveal")
CACHE = os.path.join(REVEAL, ".cache")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "shrinecraft")
JARS = glob.glob("/root/.gradle/caches/fabric-loom/*/minecraft-client.jar")

# ------------------------------------------------------------------ the clip (seconds). Matches the reference: wide shot -> push in, items drop, hold,
# flash + item appears, title drops, hold, fade.
TIMELINE = {
    "itemsStart": 0.65, "stagger": 0.085, "fall": 0.6,
    "reveal": 3.0, "converge": 0.32, "legendIn": 0.6,
    "title": 3.55, "letterStagger": 0.07, "letterTime": 0.32,
    "fadeIn": 0.45, "fadeOut": 0.5,
    "camDist": [[0, 6.0], [1.6, 3.85], [3.0, 3.05], [6.4, 2.65]],
    "camPitch": [[0, 21], [1.6, 38], [3.0, 46], [6.4, 52]],
    "camYaw": [[0, -40], [3, -32], [6.4, -26]],
    "camTargetY": [[0, 0.6], [3, 0.8], [6.4, 0.7]],
}
DURATION = 6.4

# per-legendary look: title gradient (left, middle, right), glow / spark colours, how the item lies on the table
ITEM_LOOK = {
    "default": dict(titleColors=[[140, 255, 240], [90, 200, 235], [150, 140, 255]], accent=[0.4, 0.95, 1.0], accentLight=0x4FF0FF,
                    legendaryGlow=["rgba(120,255,240,0.9)", "rgba(40,160,200,0.35)"], legendaryEmissive=0x1F6A78, flashColor="170,255,250",
                    legendaryLength=1.3, legendaryYaw=-32, legendaryHover=0.16),
}

# ------------------------------------------------------------------ helpers
def jar():
    if not JARS:
        sys.exit("Minecraft 1.21.11 client jar not found (run ./gradlew build once so Loom downloads it)")
    return zipfile.ZipFile(JARS[0])


def png_data_url(im):
    buf = io.BytesIO()
    im.convert("RGBA").save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def jar_texture(z, path):
    """Texture from the game jar (cached), or None."""
    os.makedirs(CACHE, exist_ok=True)
    cached = os.path.join(CACHE, path.replace("/", "__") + ".png")
    if not os.path.exists(cached):
        try:
            data = z.read(f"assets/minecraft/textures/{path}.png")
        except KeyError:
            return None
        open(cached, "wb").write(data)
    return Image.open(cached)


def first_frame(im):
    """Animated textures are tall strips: use the first square frame."""
    return im.crop((0, 0, im.width, im.width)) if im.height > im.width else im


# block-like ingredients: size of the little cube on the table (blocks) and its height as a share of that
BLOCK_SHAPE = {"sculk_sensor": 0.62, "sculk_shrieker": 0.62}
BLOCK_SIZE = {"heavy_core": 0.2}


def resolve_ingredient(z, images, item_id):
    """-> ('sprite', key) or ('block', {top, side, bottom}, size, height) for a recipe ingredient id like minecraft:echo_shard."""
    ns, name = item_id.split(":")
    if ns == "shrinecraft":
        icon = os.path.join(ROOT, "tools", "reference", "icons", name + ".png")
        if not os.path.exists(icon):
            icon = os.path.join(ASSETS, "textures", "item", name + ".png")
        images["ing_" + name] = png_data_url(Image.open(icon))
        return ("sprite", "ing_" + name)
    sprite = jar_texture(z, f"item/{name}")
    if sprite is not None:
        images["ing_" + name] = png_data_url(first_frame(sprite))
        return ("sprite", "ing_" + name)
    faces = {}
    for face in ("top", "side", "bottom"):
        t = jar_texture(z, f"block/{name}_{face}") or jar_texture(z, f"block/{name}")
        if t is None:
            sys.exit(f"no texture found for ingredient {item_id}")
        images[f"ing_{name}_{face}"] = png_data_url(first_frame(t))
        faces[face] = f"ing_{name}_{face}"
    return ("block", faces, BLOCK_SIZE.get(name, 0.22), BLOCK_SHAPE.get(name, 1.0))


def lay_out(ingredient_list):
    """Ingredient kinds -> grid cells. The first ingredient (the base item) goes in the middle; counts of 2-3 show that many copies."""
    order = [4, 0, 1, 2, 3, 5, 6, 7, 8]
    placed = []
    for n, ing in enumerate(ingredient_list):
        copies = ing["count"] if ing["count"] <= 3 else 1
        for _ in range(copies if n else 1):
            placed.append(ing["item"])
    placed = placed[:9]
    # base item in the centre, the rest fill the ring (essence-style duplicates end up at the bottom)
    return list(zip(order, placed))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("item", help="item id, e.g. sculk_mace")
    ap.add_argument("--size", default="1280x720")
    ap.add_argument("--fps", type=int, default=60)
    ap.add_argument("--out", default=None)
    ap.add_argument("--preview", default=None, help="comma list of seconds: render only these frames as PNGs")
    ap.add_argument("--keep-frames", action="store_true")
    ap.add_argument("--debug-cam", default=None, help="dist,pitch,yaw - fixed camera for inspecting details with --preview")
    a = ap.parse_args()
    w, h = [int(v) for v in a.size.lower().split("x")]
    item = a.item

    z = jar()
    images = {}
    # table, floor, font
    for key, path in (("table_top", "block/crafting_table_top"), ("table_front", "block/crafting_table_front"), ("table_side", "block/crafting_table_side"),
                      ("floor", "block/polished_blackstone_bricks"), ("font", "font/ascii")):
        images[key] = png_data_url(jar_texture(z, path))
    # the item
    model = json.load(open(os.path.join(ASSETS, "models", "item", item + ".json"), encoding="utf-8"))
    if "elements" not in model:
        sys.exit(f"{item} has no 3D elements (flat item) - nothing to show")
    images["model"] = png_data_url(Image.open(os.path.join(ASSETS, "textures", "item", item + ".png")))
    lang = json.load(open(os.path.join(ASSETS, "lang", "en_us.json"), encoding="utf-8"))
    title = lang.get("item.shrinecraft." + item, item.replace("_", " ").title())
    # the recipe
    recipes = json.load(open(os.path.join(ROOT, "shrine_recipes.json"), encoding="utf-8"))["recipes"]
    recipe = next((r for r in recipes if r["id"] == item), None)
    if recipe is None:
        sys.exit(f"{item} is not in shrine_recipes.json")
    ingredients = []
    for cell, item_id in lay_out(recipe["ingredients"]):
        spec = resolve_ingredient(z, images, item_id)
        d = {"cell": cell, "kind": spec[0]}
        if spec[0] == "sprite":
            d["img"] = spec[1]
        else:
            d["faces"], d["size"], d["height"] = spec[1], spec[2], spec[3]
        ingredients.append(d)

    look = dict(ITEM_LOOK["default"]); look.update(ITEM_LOOK.get(item, {}))
    config = dict(width=w, height=h, fps=a.fps, duration=DURATION, fov=38, title=title, timeline=TIMELINE,
                  titleScreenWidth=0.56, titleScreenY=0.8, titleDepth=0.9, bloom=0.34, vignette=0.62, floorRepeat=60, **look)
    if a.debug_cam:
        config["debugCam"] = [float(v) for v in a.debug_cam.split(",")]
    data = {"config": config, "images": images, "model": {"json": model}, "ingredients": ingredients}
    with open(os.path.join(REVEAL, "reveal_data.js"), "w", encoding="utf-8") as fh:
        fh.write("window.REVEAL = " + json.dumps(data) + ";\n")

    out_dir = os.path.join(ROOT, "build", "reveal")
    frames = os.path.join(out_dir, "frames_" + item)
    os.makedirs(frames, exist_ok=True)
    for f in glob.glob(os.path.join(frames, "*.png")):
        os.remove(f)
    cmd = ["node", os.path.join(REVEAL, "render_reveal.js"), frames, str(a.fps), str(DURATION)]
    if a.preview:
        cmd.append(a.preview)
    env = dict(os.environ, NODE_PATH=subprocess.check_output(["npm", "root", "-g"], text=True).strip() + ":/opt/node-tools/node_modules")
    subprocess.check_call(cmd, env=env)
    if a.preview:
        print("preview frames in", frames)
        return
    mp4 = a.out or os.path.join(out_dir, f"{item}_reveal.mp4")
    subprocess.check_call(["ffmpeg", "-y", "-v", "error", "-framerate", str(a.fps), "-i", os.path.join(frames, "f_%05d.png"),
                           "-c:v", "libx264", "-preset", "slow", "-crf", "15", "-pix_fmt", "yuv420p", "-movflags", "+faststart", mp4])
    if not a.keep_frames:
        for f in glob.glob(os.path.join(frames, "*.png")):
            os.remove(f)
    print("wrote", mp4)


if __name__ == "__main__":
    main()
