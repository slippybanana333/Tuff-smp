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
    "itemsStart": 0.75, "stagger": 0.1, "fall": 0.7,
    "reveal": 3.15, "converge": 0.4, "legendIn": 0.75,
    "title": 3.85, "letterStagger": 0.07, "letterTime": 0.32,
    "fadeIn": 0.4, "fadeOut": 0.5,
    # wide + low opening, then the camera rises and pushes in close (the Shrine + the item above it fill the frame)
    "camDist": [[0, 6.6], [1.5, 4.8], [3.15, 3.95], [6.4, 3.65]],
    "camPitch": [[0, 10], [1.5, 17], [3.15, 20], [6.4, 21]],
    "camYaw": [[0, -42], [3.15, -30], [6.4, -22]],
    "camTargetY": [[0, 0.8], [1.5, 1.2], [3.15, 1.62], [6.4, 1.6]],
}
DURATION = 6.4

# per-legendary look: title gradient (left, middle, right), glow / spark colours, how the item lies on the table
ITEM_LOOK = {
    "default": dict(titleColors=[[120, 255, 232], [70, 195, 255], [175, 135, 255]], accent=[0.4, 0.95, 1.0], accentLight=0x4FF0FF,
                    legendaryGlow=["rgba(120,255,240,0.9)", "rgba(40,160,200,0.35)"], legendaryEmissive=0x061A20, flashColor="150,255,248",
                    legendaryLength=1.4, legendaryHover=2.02, legendarySpin=0.45),
}
# everything tinted for the item (the reference was orange for its crafting table; the owner asked for sculk teal here)
THEME = {
    "teal": dict(ambient=0x1A4A54, fog=0x031318, key=0x6AF0F2, keyI=1.3, under=0x40F0E8, underI=1.9, floorColor=0x7AB4BC, floorEmissive=0x0E3036,
                 glow1=["rgba(170,255,246,1)", "rgba(30,175,195,0.55)"], glow2=["rgba(80,225,235,0.9)", "rgba(8,100,130,0.25)"],
                 ember=[0.35, 0.95, 1.0], crystalLight=0xFFC83C, bloom=0.3, vignette=0.6),
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


def _key(tex):
    return "blk_" + tex.replace("minecraft:", "").replace("/", "__")


def vanilla_block_model(z, images, name):
    """The REAL vanilla block model (parent chain merged, texture variables resolved, every face's texture replaced by an image key) -> json
    with just 'elements'. Faces keep their own uv (the heavy core only uses an 8x8 part of its texture - stretching the whole texture was wrong)."""
    chain, cur = [], f"block/{name}"
    while cur:
        cur = cur.replace("minecraft:", "")
        j = json.loads(z.read(f"assets/minecraft/models/{cur}.json"))
        chain.append(j)
        cur = j.get("parent")
    textures, elements = {}, None
    for j in reversed(chain):                       # parents first, so the child overrides
        textures.update(j.get("textures", {}))
        if j.get("elements"):
            elements = j["elements"]

    def resolve(v):
        while v.startswith("#") or (v in textures and "/" not in v):
            v = textures[v[1:] if v.startswith("#") else v]
        return v.replace("minecraft:", "")

    elements = json.loads(json.dumps(elements))
    for el in elements:
        for face in el["faces"].values():
            tex = resolve(face["texture"])
            img = jar_texture(z, tex)
            if img is None:
                sys.exit(f"vanilla texture missing: {tex}")
            images[_key(tex)] = png_data_url(first_frame(img))
            face["texture"] = _key(tex)
    return {"elements": elements}


def resolve_ingredient(z, images, item_id):
    """-> ('sprite', key) for flat items, or ('model', json) for block items (exact in-game shape), for a recipe ingredient id."""
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
    return ("model", vanilla_block_model(z, images, name))


def lay_out(ingredient_list):
    """One of each ingredient kind (owner request), in recipe order; the clip arranges them in a ring."""
    return [ing["item"] for ing in ingredient_list][:9]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("item", help="item id, e.g. sculk_mace")
    ap.add_argument("--size", default="1280x720")
    ap.add_argument("--fps", type=int, default=60)
    ap.add_argument("--out", default=None)
    ap.add_argument("--preview", default=None, help="comma list of seconds: render only these frames as PNGs")
    ap.add_argument("--keep-frames", action="store_true")
    ap.add_argument("--bloom", type=float, default=None, help="override the glow/bloom strength (0 = none) - handy for checking colours")
    ap.add_argument("--shrine", default="configured", choices=["configured", "unconfigured", "claimed"], help="which look of the Shrine block")
    ap.add_argument("--theme", default="teal", choices=sorted(THEME), help="colour theme of the scene")
    ap.add_argument("--debug-cam", default=None, help="dist,pitch,yaw - fixed camera for inspecting details with --preview")
    a = ap.parse_args()
    w, h = [int(v) for v in a.size.lower().split("x")]
    item = a.item

    z = jar()
    images = {}
    # floor + font (vanilla), and the table = the mod's own Shrine block (configured look: gold crystal, cyan runes)
    for key, path in (("floor", "block/polished_blackstone_bricks"), ("font", "font/ascii")):
        images[key] = png_data_url(jar_texture(z, path))
    shrine = json.load(open(os.path.join(ASSETS, "models", "block", f"shrine_{a.shrine}.json"), encoding="utf-8"))
    images["shrine"] = png_data_url(Image.open(os.path.join(ASSETS, "textures", "block", f"shrine_{a.shrine}.png")))
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
    for item_id in lay_out(recipe["ingredients"]):
        spec = resolve_ingredient(z, images, item_id)
        d = {"kind": spec[0], "id": item_id}
        if spec[0] == "sprite":
            d["img"] = spec[1]
        else:
            d["model"] = spec[1]
        ingredients.append(d)

    look = dict(ITEM_LOOK["default"]); look.update(ITEM_LOOK.get(item, {}))
    theme = THEME[a.theme]
    config = dict(width=w, height=h, fps=a.fps, duration=DURATION, fov=38, title=title, timeline=TIMELINE, theme=theme,
                  titleScreenWidth=0.72, titleScreenY=0.8, titleDepth=1.2, bloom=theme["bloom"], vignette=theme["vignette"], floorRepeat=60,
                  ringRadius=0.74, ringY=1.72, ringSpeed=0.42, itemSize=0.44, **look)
    if a.bloom is not None:
        config["bloom"] = a.bloom
    if a.debug_cam:
        config["debugCam"] = [float(v) for v in a.debug_cam.split(",")]
    data = {"config": config, "images": images, "model": {"json": model}, "shrine": shrine, "ingredients": ingredients}
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
