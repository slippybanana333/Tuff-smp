#!/usr/bin/env python3
"""
check_models.py - verifies every model against the real game's rules AND the player's proportions. FAILS LOUDLY (exit code 1).

    python3 tools/check_models.py

The player model is 32 units tall (head 8, torso 12, legs 12; arm 12 long; 16 units = 1 block).  Held items are measured in that
same "entity unit" space after the real third-person hand chain + the model's display transform, so
    "this sword is 22 units long"  really means "about 1.8 arms long in the player's hand".

What is checked
  1. vanilla loader rules: element range -16..32, valid faces/axes, uv range, display limits (translation +-80, scale <= 4), texture exists + size
  2. every face points at painted texture (no model/texture mismatch), no two different faces share the same texels
  3. size per item kind in the hand (too big / too small), and where the grip sits relative to the palm (third + first person)
  4. inventory (gui), ground and item-frame views fit their boxes
  5. supporting files: item definitions, block state, equipment assets + armor textures, lang entries, tag files, GUI backgrounds
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from common import *  # noqa

FAILS, WARNS, OKS = [], [], []


def fail(msg):
    FAILS.append(msg)


def warn(msg):
    WARNS.append(msg)


def ok(msg):
    OKS.append(msg)


# allowed overall length (longest side) of the item in the hand, in player-model units (arm = 12, player = 32)
HAND_SIZE = {
    "sword": (12, 24), "axe": (14, 24), "mace": (14, 24), "pick": (14, 24), "spear": (24, 36), "trident": (18, 30),
    "bow": (18, 30), "crossbow": (10, 22), "orb": (6, 16), "armor": (8, 22), "cape": (10, 26),
}
GRIP_TOL_THIRD = 1.5     # grip centre may be at most this far from the palm point (units)
GRIP_TOL_FIRST = 2.0
FIRST_GRIP_KINDS = ("sword", "axe", "pick", "mace", "spear", "trident")   # held by the grip in first person (others are centred in view)
FIRST_MIN_SHOWN_GRIP = 0.55
FIRST_MIN_SHOWN_CENTER = 0.85
VALID_DISPLAY = {"thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand", "head", "gui", "ground", "fixed", "on_shelf"}


def bounds_after(model, matrix):
    pts = []
    for el in model["elements"]:
        for p in element_corners(el):
            pts.append(xf(matrix, p))
    a = np.array(pts)
    return a.min(axis=0), a.max(axis=0)


def check_vanilla_rules(name, model, meta):
    names = set()
    if not model.get("elements"):
        fail(f"{name}: no elements")
        return
    for el in model["elements"]:
        n = el.get("name")
        if not n:
            fail(f"{name}: an element has no name")
            continue
        if n in names:
            fail(f"{name}: duplicate element name '{n}'")
        names.add(n)
        for key in ("from", "to"):
            for v in el[key]:
                if v < -16 or v > 32:
                    fail(f"{name}/{n}: '{key}' coordinate {v} outside the allowed -16..32 range (game refuses to load it)")
        size = [el["to"][i] - el["from"][i] for i in range(3)]
        if any(s < 0 for s in size):
            warn(f"{name}/{n}: negative size {size} (from > to)")
        if sum(1 for s in size if abs(s) < 1e-6) > 1:
            warn(f"{name}/{n}: degenerate element (two flat sides)")
        r = el.get("rotation")
        if r:
            if "origin" not in r or len(r["origin"]) != 3:
                fail(f"{name}/{n}: rotation without origin")
            if r.get("axis") not in ("x", "y", "z") and not any(k in r for k in ("x", "y", "z")):
                fail(f"{name}/{n}: rotation axis must be x, y or z")
        for f, fd in el["faces"].items():
            if f not in FACES:
                fail(f"{name}/{n}: unknown face '{f}'")
            uv = fd.get("uv")
            if not uv or len(uv) != 4 or any(u < -1e-6 or u > 16 + 1e-6 for u in uv):
                fail(f"{name}/{n}/{f}: bad uv {uv} (must be 4 numbers in 0..16)")
            if fd.get("texture") != "#0":
                fail(f"{name}/{n}/{f}: texture must be '#0'")
    for k, d in (model.get("display") or {}).items():
        if k not in VALID_DISPLAY:
            fail(f"{name}: unknown display context '{k}'")
        if any(abs(v) > 80 for v in d.get("translation", [0, 0, 0])):
            fail(f"{name}: display '{k}' translation beyond +-80 (the game clamps it)")
        if any(abs(v) > 4 for v in d.get("scale", [1, 1, 1])):
            fail(f"{name}: display '{k}' scale above 4 (the game clamps it)")
    for need in ("gui", "ground", "fixed", "head", "thirdperson_righthand", "firstperson_righthand"):
        if need not in (model.get("display") or {}):
            fail(f"{name}: display transform '{need}' is missing (item would render wrong in that context)")
    if model.get("gui_light") not in ("side", "front"):
        warn(f"{name}: gui_light should be 'side' for 3D models")


def check_texture_match(name, model, tex_path):
    if not os.path.exists(tex_path):
        fail(f"{name}: texture file missing: {os.path.relpath(tex_path, ROOT)}")
        return
    img = np.array(Image.open(tex_path).convert("RGBA"))
    H, W = img.shape[:2]
    if [W, H] != list(model["texture_size"]):
        fail(f"{name}: texture is {W}x{H} but the model says texture_size {model['texture_size']}  -> model and texture are out of sync, run tools/rebuild_all.py")
        return
    seen = {}
    empty = 0
    for el in model["elements"]:
        for f, fd in el["faces"].items():
            u1, v1, u2, v2 = fd["uv"]
            x0, y0, x1, y1 = int(round(u1 / 16 * W)), int(round(v1 / 16 * H)), int(round(u2 / 16 * W)), int(round(v2 / 16 * H))
            if x1 <= x0 or y1 <= y0:
                fail(f"{name}/{el['name']}/{f}: empty uv rectangle")
                continue
            region = img[y0:y1, x0:x1]
            if region.size and region[..., 3].max() == 0:
                empty += 1
                fail(f"{name}/{el['name']}/{f}: its texture area is completely transparent (face would be invisible)")
            key = (x0, y0, x1, y1)
            owner = seen.get(key)
            same_shape = owner and owner[0] == el["name"] and owner[1] == f
            if owner and not same_shape:
                fail(f"{name}/{el['name']}/{f}: shares its texels with {owner[0]}/{owner[1]} (painting one would change the other)")
            seen[key] = (el["name"], f)
    if not empty:
        ok(f"{name}: all {len(seen)} faces point at painted texture")


def check_sprite_texture(name, model, tex_path):
    """Sprite models share texels on purpose (front/back/sides read the same icon); just make sure the size matches and no face points at empty pixels."""
    if not os.path.exists(tex_path):
        fail(f"{name}: texture file missing: {os.path.relpath(tex_path, ROOT)}")
        return
    img = np.array(Image.open(tex_path).convert("RGBA"))
    H, W = img.shape[:2]
    if [W, H] != list(model["texture_size"]):
        fail(f"{name}: texture is {W}x{H} but the model says texture_size {model['texture_size']}")
        return
    bad = 0
    for el in model["elements"]:
        for f, fd in el["faces"].items():
            u1, v1, u2, v2 = fd["uv"]
            x0, x1 = sorted((int(math.floor(u1 / 16 * W + 1e-6)), int(math.ceil(u2 / 16 * W - 1e-6))))
            y0, y1 = sorted((int(math.floor(v1 / 16 * H + 1e-6)), int(math.ceil(v2 / 16 * H - 1e-6))))
            if img[y0:y1, x0:x1, 3].max() == 0:
                bad += 1
                fail(f"{name}/{el['name']}/{f}: points at transparent pixels")
    if not bad:
        ok(f"{name}: all faces point at opaque icon pixels")


def hand_checks(name, model, kind):
    g = grip_point(model)
    lo_hi = {}
    # ---- third person (entity units: arm length 12, player 32) ----
    disp = model["display"]["thirdperson_righthand"]
    chain = third_person_chain() @ display_matrix(disp["translation"], disp["rotation"], disp["scale"])
    lo, hi = bounds_after(model, chain)
    ext = hi - lo
    longest = float(ext.max())
    lim = HAND_SIZE.get(kind)
    if lim:
        if longest > lim[1]:
            fail(f"{name}: OVERSIZED in the hand - longest side {longest:.1f} units, allowed {lim[0]}-{lim[1]} for a {kind} (player is 32, arm is 12)")
        elif longest < lim[0]:
            fail(f"{name}: too SMALL in the hand - longest side {longest:.1f} units, allowed {lim[0]}-{lim[1]} for a {kind}")
        else:
            ok(f"{name}: hand size {longest:.1f} units (allowed {lim[0]}-{lim[1]}; arm=12, player=32)")
    gp = xf(display_matrix(disp["translation"], disp["rotation"], disp["scale"]), g)   # grip in the hand frame
    palm = vanilla_palm_third()
    d = float(np.linalg.norm(gp - palm))
    center_hand = xf(display_matrix(disp["translation"], disp["rotation"], disp["scale"]), (np.array(model_bounds(model)[0]) + np.array(model_bounds(model)[1])) / 2)
    if d > GRIP_TOL_THIRD:
        fail(f"{name}: GRIP NOT IN THE HAND (third person) - grip is {d:.1f} units from the palm point (max {GRIP_TOL_THIRD})")
    else:
        ok(f"{name}: grip sits {d:.2f} units from the palm (third person)")
    reach = float(np.linalg.norm(center_hand - palm))
    if reach > 0.75 * longest + 4:
        fail(f"{name}: item floats away from the hand - its centre is {reach:.1f} units from the palm (longest side {longest:.1f})")
    # the grip must really be inside the model's bounding box
    glo, ghi = model_bounds(model)
    if not (np.all(g >= glo - 1e-6) and np.all(g <= ghi + 1e-6)):
        fail(f"{name}: grip point is outside the model")
    # ---- first person ----
    dispf = model["display"]["firstperson_righthand"]
    mf = display_matrix(dispf["translation"], dispf["rotation"], dispf["scale"])
    if kind in FIRST_GRIP_KINDS:
        # weapons are held by the grip: it must sit where a vanilla sword's grip sits
        gf = xf(mf, g)
        import gen_models
        _sh = gen_models.FIRST_HAND_SHIFT.copy()
        if kind in gen_models.FIRST_NO_CLOSER:
            _sh[2] = 0.0
        _sh = _sh + gen_models.FIRST_KIND_SHIFT.get(kind, 0.0)
        palmf = vanilla_palm_first() + _sh      # the first-person hand is raised / brought closer a little (see FIRST_HAND_SHIFT)
        df = float(np.linalg.norm(gf - palmf))
        if df > GRIP_TOL_FIRST:
            fail(f"{name}: GRIP NOT IN THE HAND (first person) - {df:.1f} units from where a vanilla item's grip sits (max {GRIP_TOL_FIRST})")
        else:
            ok(f"{name}: grip sits {df:.2f} units from the palm (first person)")
    # first-person: how much of the item is on screen (camera: vertical FOV 70, 16:9, incl. the 0.56,-0.52,-0.72 block offset)
    mfc = first_person_matrix(dispf["translation"], dispf["rotation"], dispf["scale"])
    pts = []
    for el in model["elements"]:
        c4 = element_corners(el)
        pts.extend(c4)
        pts.append(np.mean(c4, axis=0))
    cam = np.array([xf(mfc, p) / 16.0 for p in pts])
    if cam[:, 2].max() > -0.12:
        fail(f"{name}: part of the item is closer than 0.12 blocks to the first-person camera (z={cam[:, 2].max():.2f}) - it would be clipped")
    safe = np.minimum(cam[:, 2], -0.12)
    nx = cam[:, 0] / (-safe) / (math.tan(math.radians(35)) * 16 / 9)
    ny = cam[:, 1] / (-safe) / math.tan(math.radians(35))
    shown = float(((np.abs(nx) <= 1) & (np.abs(ny) <= 1)).mean())
    need = FIRST_MIN_SHOWN_GRIP if kind in FIRST_GRIP_KINDS else FIRST_MIN_SHOWN_CENTER
    import gen_models
    if kind in gen_models.FIRST_KIND_SHIFT:
        need = 0.40          # the Cutlass handle is meant to be off-screen
    if shown < need:
        fail(f"{name}: only {shown:.0%} of the item is on screen in first person (need {need:.0%}) - change FIRST_DIRS/FIRST_SCALE in gen_models.py and run tools/retune_display.py")
    else:
        ok(f"{name}: {shown:.0%} of the item is on screen in first person")
    return longest


def view_fit_checks(name, model, kind):
    for ctxname, target in (("gui", 16.0), ("ground", 14.0), ("fixed", 16.0)):
        d = model["display"][ctxname]
        m = display_matrix(d["translation"], d["rotation"], d["scale"])
        lo, hi = bounds_after(model, m)
        w, h = hi[0] - lo[0], hi[1] - lo[1]
        if ctxname == "gui":
            if lo[0] < -8.6 or hi[0] > 8.6 or lo[1] < -8.6 or hi[1] > 8.6:
                fail(f"{name}: gui view spills out of the 16x16 slot (x {lo[0]:.1f}..{hi[0]:.1f}, y {lo[1]:.1f}..{hi[1]:.1f})")
            elif max(w, h) < 9:
                warn(f"{name}: gui view is small ({max(w, h):.1f} of 16 units)")
            else:
                ok(f"{name}: gui view {w:.1f}x{h:.1f} units inside the 16x16 slot")
        elif max(w, h) > target * 1.25:
            warn(f"{name}: {ctxname} view is large ({max(w, h):.1f} units)")


def check_supporting_files(items_meta):
    lang = load_json(os.path.join(ASSETS, "lang", "en_us.json"))
    for n in ITEM_NAMES:
        d = os.path.join(ITEM_DEFS, n + ".json")
        if not os.path.exists(d):
            fail(f"item definition missing: items/{n}.json")
        else:
            txt = json.dumps(load_json(d))
            if "shrinecraft:item/" not in txt:
                fail(f"items/{n}.json does not point at a shrinecraft model")
            for ref in set(__import__("re").findall(r"shrinecraft:item/([a-z0-9_]+)", txt)):
                if not os.path.exists(os.path.join(ITEM_MODELS, ref + ".json")):
                    fail(f"items/{n}.json references a model that does not exist: {ref}")
        if "item.shrinecraft." + n not in lang:
            fail(f"lang entry missing for item {n}")
    if not os.path.exists(os.path.join(ITEM_DEFS, "shrine.json")):
        fail("items/shrine.json missing")
    if not os.path.exists(os.path.join(ASSETS, "blockstates", "shrine.json")):
        fail("blockstates/shrine.json missing")
    for n in ARMOR_NAMES:
        e = os.path.join(EQUIP_DIR, n + ".json")
        if not os.path.exists(e):
            fail(f"equipment asset missing: equipment/{n}.json")
        for layer in ("humanoid", "humanoid_leggings"):
            p = os.path.join(TEX_EQUIP, layer, n + ".png")
            if not os.path.exists(p):
                fail(f"armor texture missing: {os.path.relpath(p, ROOT)}")
            else:
                im = Image.open(p)
                if im.size != (64, 32):
                    fail(f"armor texture {layer}/{n}.png must be 64x32 (vanilla armor layout), is {im.size}")
    wp = os.path.join(TEX_EQUIP, "wings", "strange_cape.png")
    if not os.path.exists(wp) or Image.open(wp).size != (64, 32):
        fail("cape wing texture textures/entity/equipment/wings/strange_cape.png must exist and be 64x32")
    if not os.path.exists(os.path.join(EQUIP_DIR, "strange_cape.json")):
        fail("equipment/strange_cape.json missing")
    for theme in ("ocean", "lava", "sculk", "arctic", "end"):
        p = os.path.join(TEX_GUI, theme + ".png")
        if not os.path.exists(p):
            fail(f"GUI background missing: {theme}.png")
    # the data-pack bug list from the brief: enchantment tag files must exist
    for tag in ("molten_greataxe_only", "sculk_cannon_only"):
        p = os.path.join(RES, "data", "shrinecraft", "tags", "item", tag + ".json")
        if not os.path.exists(p):
            fail(f"item tag file missing: data/shrinecraft/tags/item/{tag}.json (an enchantment points at it -> server would crash)")
    for ench in os.listdir(os.path.join(RES, "data", "shrinecraft", "enchantment")):
        data = load_json(os.path.join(RES, "data", "shrinecraft", "enchantment", ench))
        sup = data.get("supported_items", "")
        if sup.startswith("#shrinecraft:"):
            p = os.path.join(RES, "data", "shrinecraft", "tags", "item", sup.split(":", 1)[1] + ".json")
            if not os.path.exists(p):
                fail(f"enchantment {ench} supports #{sup} but that tag file does not exist")
            else:
                ok(f"enchantment {ench}: tag file {os.path.basename(p)} exists")
    if not os.path.exists(os.path.join(EXTRAS_DIR, "tuff_extras.json")):
        fail("armor_extras/tuff_extras.json missing")
    ex = load_json(os.path.join(EXTRAS_DIR, "tuff_extras.json"))
    et = os.path.join(TEX_EXTRA, "tuff_extras.png")
    if os.path.exists(et):
        img = np.array(Image.open(et).convert("RGBA"))
        gimg = np.array(Image.open(et.replace(".png", "_glow.png")).convert("RGBA")) if os.path.exists(et.replace(".png", "_glow.png")) else None
        for name, pieces in ex["pieces"].items():
            for pc in pieces:
                u, v = pc["uv"]
                sx, sy, sz = pc["size"]
                w, h = int(2 * (sx + sz)), int(sy + sz)
                # a glow "shell" is blank in the normal texture on purpose: its cracks live in the glow texture
                region = (gimg if (pc.get("material") == "shell" and gimg is not None) else img)[v:v + h, u:u + w]
                if pc.get("material") == "shell":
                    continue            # a shell may legitimately have no crack on it (cracks are few and random)
                if region.size == 0 or region[..., 3].max() == 0:
                    fail(f"armor extra '{pc['name']}' points at an empty area of tuff_extras.png")


def main():
    global OWNER_TUNED
    _ovr = os.path.join(ROOT, "tools", "display_overrides.json")
    OWNER_TUNED = set(load_json(_ovr)) if os.path.exists(_ovr) else set()
    meta = load_json(os.path.join(ROOT, "tools", "model_materials.json"))
    for name, m in meta.items():
        is_block = m["block"]
        path = model_path(("block/" if is_block else "") + name)
        if not os.path.exists(path):
            fail(f"{name}: model file missing")
            continue
        model = load_json(path)
        tex = os.path.join(TEX_BLOCK if is_block else TEX_ITEM, m["texture"] + ".png")
        if m.get("flat"):
            # owner request: these items are plain flat 2D sprites (vanilla's item/generated), no elements to measure
            ok(f"{name}: flat 2D item (item/generated), texture {'present' if os.path.exists(tex) else 'MISSING'}")
            if not os.path.exists(tex) or model.get("parent") != "minecraft:item/generated":
                fail(f"{name}: flat item needs parent minecraft:item/generated and its texture png")
            continue
        check_vanilla_rules(name, model, m)
        if m.get("sprite"):
            check_sprite_texture(name, model, tex)
        else:
            check_texture_match(name, model, tex)
        kind = m["kind"]
        if is_block:
            lo, hi = model_bounds(model)
            if lo[0] < -0.01 or lo[2] < -0.01 or hi[0] > 16.01 or hi[2] > 16.01 or lo[1] < -0.01:
                fail(f"{name}: block model sticks out of its 1x1 footprint (x {lo[0]:.1f}..{hi[0]:.1f}, z {lo[2]:.1f}..{hi[2]:.1f})")
            if hi[1] > 24:
                fail(f"{name}: block model is taller than 1.5 blocks ({hi[1]:.1f})")
            else:
                ok(f"{name}: block fits its footprint, height {hi[1]:.1f}")
            view_fit_checks(name, model, kind)
            continue
        if m.get("sprite"):
            # sprite models use the vanilla hand poses (no 'grip' / palm test); check their size and that they fit the 16x16 slot
            lo, hi = model_bounds(model)
            ok(f"{name}: sprite model, {len(model['elements'])} boxes, x {lo[0]:.1f}..{hi[0]:.1f} y {lo[1]:.1f}..{hi[1]:.1f} z {lo[2]:.1f}..{hi[2]:.1f}")
            if min(lo) < -16 or max(hi) > 32:
                fail(f"{name}: sticks out of the -16..32 model limit")
            continue
        if not find_element(model, "grip") and kind not in ("armor", "cape"):
            fail(f"{name}: held items need an element named 'grip' (what the palm holds)")
        before = len(FAILS)
        hand_checks(name, model, kind)
        view_fit_checks(name, model, kind)
        if name in OWNER_TUNED:
            # positions the owner set by hand in the Display Editor (tools/display_overrides.json) are deliberate: report, do not fail
            moved = FAILS[before:]
            del FAILS[before:]
            for m in moved:
                OKS.append("owner-tuned position, not enforced: " + m)
    check_supporting_files(meta)

    print("=" * 100)
    print("MODEL CHECK  (player = 32 units tall, arm = 12, 16 units = 1 block)")
    print("=" * 100)
    for l in OKS:
        print("  ok    ", l)
    for l in WARNS:
        print("  WARN  ", l)
    for l in FAILS:
        print("  FAIL  ", l)
    print("-" * 100)
    print(f"{len(OKS)} ok, {len(WARNS)} warnings, {len(FAILS)} FAILURES")
    if FAILS:
        print("\n*** CHECK FAILED - fix the items above (or ask Claude to) and run tools/rebuild_all.py ***")
        return 1
    print("\nAll model checks passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
