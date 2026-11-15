#!/usr/bin/env python3
"""
make_objective.py - makes a "New Objective" notification overlay for one legendary (remake of the owner's reference clip: a glassy card in the top-left
with a glowing border, a big star + runes behind the item icon, a glitchy typed title, a thin underline, two lines of text, a scrambled rune row and
five little stars; it wipes in from the left edge, holds, and wipes out). The icon is the item's REAL 3D model, rendered from its model json.

    python3 tools/make_objective.py sculk_mace                  # -> build/objective/sculk_mace_objective.webm / .mov (transparent) + _preview.mp4
    python3 tools/make_objective.py sculk_mace --preview 0.2,1.0,2.5,4.6     # only those moments as PNGs (fast check)
    python3 tools/make_objective.py sculk_mace --line1 "Craft the Sculk Mace at the Shrine" --line2 "with a heavy core and sculk blocks"

Outputs (1920x1080, 60 fps, ~5.6 s) - pick with --formats mov,webm,preview (default: mov):
  * <item>_objective.webm  VP9 WITH ALPHA (plays in Chrome/Edge/Firefox; most editors take it)
  * <item>_objective.mov   ProRes 4444 WITH ALPHA (Premiere / DaVinci / Final Cut / After Effects)
  * <item>_objective_greenscreen.mp4  the clip on pure chroma green (#00FF00) - H.264 has no alpha, key the green out in your editor
  * <item>_objective_preview.mp4  the same clip on a dark checkerboard, like the reference recording (easy to watch anywhere)
Text lines have at most ~34 characters. Vanilla font textures are read from the game jar at run time (not stored in the repo).
Needs: node + playwright (global), ffmpeg with libvpx-vp9 and prores_ks.
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
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "shrinecraft")
JARS = glob.glob("/root/.gradle/caches/fabric-loom/*/minecraft-client.jar")

DURATION = 5.6
TIMELINE = {"inStart": 0.3, "inTime": 0.85, "outStart": 4.45, "outTime": 0.75}

# what the card says, per item (the title is always "New Objective"); default = generic text from the item name
OBJECTIVE_TEXT = {
    "sculk_mace": ("Obtain the Sculk Mace from the Shrine", "bring heavy core, echo shards, sculk"),
}
THEMES = {
    "teal": dict(bg0="#020f13", bg1="#06303a", bg2="#0a5a66", streak="40,225,215", glow="60,235,225", rune="#7ff9ec", star="#6ff7ea",
                 titleTop="#d4fff8", titleMid="#5bf0df", titleBot="#1fb4e6", titleDark="#03222a"),
}
ITEM_THEME = {}


def data_url(im):
    buf = io.BytesIO()
    im.convert("RGBA").save(buf, "PNG")
    return "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("item")
    ap.add_argument("--fps", type=int, default=60)
    ap.add_argument("--line1", default=None)
    ap.add_argument("--line2", default=None)
    ap.add_argument("--title", default="New Objective")
    ap.add_argument("--theme", default=None, choices=sorted(THEMES))
    ap.add_argument("--formats", default="mov", help="comma list of: mov (ProRes 4444 alpha), webm (VP9 alpha), green (mp4 on chroma green), preview (mp4 on a checkerboard)")
    ap.add_argument("--preview", default=None, help="comma list of seconds: render only these frames as PNGs (on a checkerboard)")
    ap.add_argument("--out-dir", default=os.path.join(ROOT, "build", "objective"))
    a = ap.parse_args()
    item = a.item
    if not JARS:
        sys.exit("Minecraft 1.21.11 client jar not found (run ./gradlew build once so Loom downloads it)")
    z = zipfile.ZipFile(JARS[0])
    images = {"font": data_url(Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/ascii.png")))),
              "sga": data_url(Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/ascii_sga.png")))),
              "model": data_url(Image.open(os.path.join(ASSETS, "textures", "item", item + ".png")))}
    model = json.load(open(os.path.join(ASSETS, "models", "item", item + ".json"), encoding="utf-8"))
    if "elements" not in model:
        sys.exit(f"{item} has no 3D elements (flat item) - nothing to render as the icon")
    lang = json.load(open(os.path.join(ASSETS, "lang", "en_us.json"), encoding="utf-8"))
    name = lang.get("item.shrinecraft." + item, item.replace("_", " ").title())
    l1, l2 = OBJECTIVE_TEXT.get(item, (f"Craft the {name} at the Shrine", "bring every ingredient to the Shrine"))
    l1, l2 = a.line1 or l1, a.line2 or l2
    for ln in (l1, l2):
        if len(ln) > 36:
            print(f"warning: line is {len(ln)} characters, it may run off the card: {ln!r}")
    theme = THEMES[a.theme or ITEM_THEME.get(item, "teal")]
    config = dict(width=1920, height=1080, fps=a.fps, duration=DURATION, title=a.title, lines=[l1, l2], runeText=name.lower() + " " + "shrine heavy core",
                  timeline=TIMELINE, theme=theme, iconTilt=[-14, 26, 44], star=False, checker=False)
    os.makedirs(a.out_dir, exist_ok=True)
    node_path = subprocess.check_output(["npm", "root", "-g"], text=True).strip() + ":/opt/node-tools/node_modules"
    env = dict(os.environ, NODE_PATH=node_path)

    def render(checker, frames_dir, extra=None):
        config["checker"] = checker
        with open(os.path.join(REVEAL, "objective_data.js"), "w", encoding="utf-8") as fh:
            fh.write("window.OBJ = " + json.dumps({"config": config, "images": images, "model": {"json": model}}) + ";\n")
        os.makedirs(frames_dir, exist_ok=True)
        for f in glob.glob(os.path.join(frames_dir, "*.png")):
            os.remove(f)
        cmd = ["node", os.path.join(REVEAL, "render_overlay.js"), "objective.html", frames_dir, str(a.fps), str(DURATION)]
        if extra:
            cmd.append(extra)
        subprocess.check_call(cmd, env=env)

    if a.preview:
        d = os.path.join(a.out_dir, "frames_preview_" + item)
        render(True, d, a.preview)
        print("preview frames in", d)
        return
    fmts = set(f.strip() for f in a.formats.split(","))
    d_alpha = os.path.join(a.out_dir, "frames_alpha_" + item)
    d_chk = os.path.join(a.out_dir, "frames_checker_" + item)
    base = os.path.join(a.out_dir, f"{item}_objective")
    common = ["ffmpeg", "-y", "-v", "error", "-framerate", str(a.fps), "-i"]
    written = []
    if fmts & {"mov", "webm", "green"}:
        render(False, d_alpha)
        if "webm" in fmts:
            subprocess.check_call(common + [os.path.join(d_alpha, "f_%05d.png"), "-c:v", "libvpx-vp9", "-pix_fmt", "yuva420p", "-b:v", "0", "-crf", "22", "-auto-alt-ref", "0", base + ".webm"]); written.append(base + ".webm")
        if "mov" in fmts:
            subprocess.check_call(common + [os.path.join(d_alpha, "f_%05d.png"), "-c:v", "prores_ks", "-profile:v", "4444", "-pix_fmt", "yuva444p10le", "-vendor", "apl0", base + ".mov"]); written.append(base + ".mov")
    if "green" in fmts:
        # H.264 has no alpha: the transparent frames are laid over pure chroma green (#00FF00) for keying out in an editor
        subprocess.check_call(["ffmpeg", "-y", "-v", "error", "-f", "lavfi", "-i", f"color=c=0x00FF00:s=1920x1080:r={a.fps}", "-framerate", str(a.fps), "-i", os.path.join(d_alpha, "f_%05d.png"),
                               "-filter_complex", "[0:v][1:v]overlay=shortest=1:format=auto", "-c:v", "libx264", "-preset", "slow", "-crf", "12", "-pix_fmt", "yuv420p", "-movflags", "+faststart", base + "_greenscreen.mp4"])
        written.append(base + "_greenscreen.mp4")
    if "preview" in fmts:
        render(True, d_chk)
        subprocess.check_call(common + [os.path.join(d_chk, "f_%05d.png"), "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p", "-movflags", "+faststart", base + "_preview.mp4"]); written.append(base + "_preview.mp4")
    for d in (d_alpha, d_chk):
        for f in glob.glob(os.path.join(d, "*.png")):
            os.remove(f)
    print("wrote", *written)


if __name__ == "__main__":
    main()
