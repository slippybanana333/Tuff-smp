#!/usr/bin/env python3
"""
build_viewer.py  -  builds viewer/index.html: ONE self-contained file (three.js, all models, all textures inlined).
Double-click it to open in any browser; no internet and no server needed.  It reads the REAL model json + png files from
src/main/resources, so it always matches the mod.  Rebuild with:   python3 tools/build_viewer.py
"""
import base64
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa


def data_url(path):
    with open(path, "rb") as f:
        return "data:image/png;base64," + base64.b64encode(f.read()).decode()


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def main():
    meta = load_json(os.path.join(ROOT, "tools", "model_materials.json"))
    lang = load_json(os.path.join(ASSETS, "lang", "en_us.json"))
    models, textures = {}, {}
    for name, m in meta.items():
        is_block = m["block"]
        js = load_json(model_path(("block/" if is_block else "") + name))
        if m.get("flat"):
            # flat 2D item (vanilla item/generated): the editor draws elements, so show it as a thin 16x16 slab carrying the icon on both faces
            face = {"uv": [0, 0, 16, 16], "texture": "#0"}
            js = {"texture_size": [16, 16], "textures": {"0": js["textures"]["layer0"], "particle": js["textures"]["layer0"]},
                  "elements": [{"name": "sprite", "from": [0, 0, 7.5], "to": [16, 16, 8.5], "faces": {"north": dict(face), "south": dict(face)}}],
                  "display": {}}
        tex_key = m["texture"]
        models[name] = {"json": js, "texture": tex_key, "block": is_block}
        if tex_key not in textures:
            p = os.path.join(TEX_BLOCK if is_block else TEX_ITEM, tex_key + ".png")
            textures[tex_key] = data_url(p)
    variant_labels = {
        "frost_longbow": ["Idle", "Draw 0", "Draw 1", "Draw 2"],
        "sculk_cannon": ["Idle", "Pulling 0", "Pulling 1", "Pulling 2", "Charged"],
        "silvers_spawn_orb": ["Stored (Silver inside)", "Released (Silver out)", "Cooldown (recharging)"],
        "cloud_lance": ["Idle", "Charging (third person)"],
    }
    # cloud_lance_use is a CHILD model (parent = the lance, only its own third-person display): give the editor the full model with the child's
    # display merged in, so its charging pose can be edited and written back (apply_display.py writes into cloud_lance_use.json).
    import copy
    base_m = models["cloud_lance"]
    use_js = copy.deepcopy(base_m["json"])
    use_js["display"] = {**use_js.get("display", {}), **load_json(model_path("cloud_lance_use")).get("display", {})}
    models["cloud_lance_use"] = {"json": use_js, "texture": base_m["texture"], "block": False}
    meta["cloud_lance_use"] = dict(meta["cloud_lance"])
    items = []
    for n in ITEM_NAMES:
        variants = [k for k in meta if k == n or k.startswith(n + "_pulling_") or k == n + "_charged" or k in (n + "_released", n + "_cooldown")]
        variants.sort(key=lambda k: (k != n, k))
        if n == "silvers_spawn_orb":
            variants = ["silvers_spawn_orb", "silvers_spawn_orb_released", "silvers_spawn_orb_cooldown"]
            labels_fix = True
        if n == "cloud_lance":
            variants = ["cloud_lance", "cloud_lance_use"]
        if n == "sculk_cannon":
            variants = ["sculk_cannon", "sculk_cannon_pulling_0", "sculk_cannon_pulling_1", "sculk_cannon_pulling_2", "sculk_cannon_charged"]
        labels = variant_labels.get(n, ["Model"])
        items.append({"id": n, "label": lang.get("item.shrinecraft." + n, n), "kind": meta[n]["kind"],
                      "models": [{"name": v, "label": labels[i] if i < len(labels) else v} for i, v in enumerate(variants)]})
    items.append({"id": "shrine", "label": "Shrine (block)", "kind": "block",
                  "models": [{"name": "shrine_" + s, "label": s.capitalize()} for s in ("unconfigured", "configured", "claimed")]})

    # vanilla reference items (tools/make_vanilla_refs.py): the real vanilla sprite + real vanilla hand positions, for side-by-side comparison
    vdir = os.path.join(REF_DIR, "vanilla_models")
    for f in sorted(os.listdir(vdir)) if os.path.isdir(vdir) else []:
        vn = f[:-5]
        models["vanilla_" + vn] = {"json": load_json(os.path.join(vdir, f)), "texture": "vanilla_" + vn, "block": False, "vanilla": True}
        textures["vanilla_" + vn] = data_url(os.path.join(REF_DIR, "vanilla_items", vn + ".png"))

    armor = {}
    for n in ARMOR_NAMES:
        armor[n] = {"humanoid": data_url(os.path.join(TEX_EQUIP, "humanoid", n + ".png")),
                    "leggings": data_url(os.path.join(TEX_EQUIP, "humanoid_leggings", n + ".png"))}
    extras = load_json(os.path.join(EXTRAS_DIR, "tuff_extras.json"))
    extras["texture_url"] = data_url(os.path.join(TEX_EXTRA, "tuff_extras.png"))
    if os.path.exists(os.path.join(TEX_EXTRA, "tuff_extras_glow.png")):
        extras["glow_texture_url"] = data_url(os.path.join(TEX_EXTRA, "tuff_extras_glow.png"))
    data = {
        "items": items, "models": models, "textures": textures, "meta": meta,
        "skin": data_url(os.path.join(REF_DIR, "steve.png")),
        "armor": armor, "wings": data_url(os.path.join(TEX_EQUIP, "wings", "strange_cape.png")),
        "extras": extras,
        "worn": load_json(os.path.join(ASSETS, "worn_display.json")),
    }

    three = read(os.path.join(ROOT, "tools", "vendor", "three.min.js"))
    orbit = read(os.path.join(ROOT, "tools", "vendor", "OrbitControls.js"))
    app = read(os.path.join(ROOT, "tools", "viewer_app.js"))
    tpl = read(os.path.join(ROOT, "tools", "viewer_template.html"))
    html = (tpl.replace("/*__THREE__*/", three).replace("/*__ORBIT__*/", orbit)
               .replace("/*__DATA__*/", "window.VIEWER_DATA = " + json.dumps(data, separators=(",", ":")) + ";")
               .replace("/*__APP__*/", app))
    out = os.path.join(VIEWER_DIR, "index.html")
    ensure_dir(VIEWER_DIR)
    with open(out, "w", encoding="utf-8") as f:
        f.write(html)
    # the same page as an Artifact body (no doctype/head: the host adds them)
    import re
    style = re.search(r"<style>.*?</style>", html, re.S).group(0)
    body = re.search(r"<body>(.*)</body>", html, re.S).group(1)
    art = "<title>Tuff Display Editor</title>\n" + style + "\n" + body
    with open(os.path.join(VIEWER_DIR, "display_editor_artifact.html"), "w", encoding="utf-8") as f:
        f.write(art)
    print("viewer written:", os.path.relpath(out, ROOT), "(%.1f MB)" % (len(html) / 1e6))


if __name__ == "__main__":
    main()
