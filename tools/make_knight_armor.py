#!/usr/bin/env python3
"""make_knight_armor.py - the 3D GEOMETRY of the golden knight armor: every raised plate, rim, crest and gem is a real box standing
out from the body (Minecraft's own armor is only a flat skin). Writes assets/shrinecraft/armor_extras/tuff_extras.json, which the game
(ArmorExtrasModel) and the editor page both read; the texture for it is painted by paint_textures.py (paint_extra_texture).

Edit the lists below to reshape the armor, then run:  python3 tools/make_knight_armor.py && python3 tools/rebuild_all.py
Box = (name, part, [x, y, z] min corner, [sx, sy, sz] size, material, optional dict(rot=[pitch, yaw, roll], pivot=[x, y, z], inflate=f)).
Coordinates are player-model pixels relative to the body part's pivot; Y points DOWN (head pivot 0,0,0 | body 0,0,0 |
right_arm -5,2,0 | right_leg -1.9,12,0).  Parts written for the RIGHT arm / leg are mirrored onto the left automatically ("@arm", "@leg").
Materials: I ivory plate, G gold trim, A glowing amber, S dark steel, M chainmail, C cream cloth.
Look = the owner's reference painting: ivory plates edged in gold, amber gems / slits, layered pauldrons and tassets, pointed visor, chain skirt."""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "shrinecraft", "armor_extras", "tuff_extras.json")
MAT = {"G": "kn_gold_px", "S": "kn_steel_px", "C": "kn_cloth_px", "A": "kn_amber_px", "I": "kn_ivory_px", "M": "kn_chain_px"}
TEX = 128

HELMET = [
    # domed helmet, dark face opening
    ("dome_cap", "head", [-4.0, -9.6, -4.0], [8, 1, 8], "I"),
    ("brow_back", "head", [-5.5, -7.2, 4.7], [11, 2, 1], "G"),
    ("brow_right", "head", [-5.7, -7.2, -5.0], [1, 2, 10], "G"),
    ("brow_left", "head", [4.7, -7.2, -5.0], [1, 2, 10], "G"),
    ("face_void", "head", [-3.0, -5.2, -5.8], [6, 4, 1], "S"),
    ("cheek_right", "head", [-5.2, -4.8, -5.0], [1, 5, 4], "I"),
    ("cheek_left", "head", [4.2, -4.8, -5.0], [1, 5, 4], "I"),
    ("neck_guard", "head", [-5.0, -0.6, 4.6], [10, 3, 1], "I", dict(rot=[20, 0, 0], pivot=[0, -0.6, 4.6])),
    # TALL CROWN PLATE standing up in front (as in the reference): a stepped pentagon rising to a peak, a white chevron pointing down to the face
    ("crown_0", "head", [-4.5, -9.4, -6.0], [9, 2, 1], "G"),
    ("crown_1", "head", [-3.5, -11.4, -6.0], [7, 2, 1], "I"),
    ("crown_2", "head", [-2.5, -13.4, -6.0], [5, 2, 1], "G"),
    ("crown_3", "head", [-1.5, -15.4, -6.0], [3, 2, 1], "I"),
    ("crown_4", "head", [-0.5, -17.4, -6.0], [1, 2, 1], "G"),
    ("crown_diamond", "head", [-1.8, -13.8, -6.6], [3.6, 3.6, 1], "G", dict(rot=[0, 0, 45], pivot=[0, -12.0, -6.6])),
    ("chevron_0", "head", [-4.5, -7.8, -6.4], [9, 1, 1], "I"),
    ("chevron_1", "head", [-3.5, -6.8, -6.4], [7, 1, 1], "I"),
    ("chevron_2", "head", [-2.5, -5.9, -6.4], [5, 1, 1], "I"),
    ("chevron_3", "head", [-1.5, -5.0, -6.4], [3, 1, 1], "I"),
    # HORNS: a thick lower body at each side of the head flaring outward, then an upper prong angled up and out (the reference "Y" shape)
    ("horn_r_base", "head", [-8.0, -11.8, -2.0], [4, 7, 4], "G", dict(rot=[0, 0, -8], pivot=[-6.0, -5.0, 0])),
    ("horn_r_prong", "head", [-9.4, -17.8, -1.5], [3.5, 7, 3], "I", dict(rot=[0, 0, -14], pivot=[-7.0, -11.0, 0])),
    ("horn_r_tip", "head", [-10.4, -19.6, -1.5], [3, 3, 3], "G", dict(rot=[0, 0, -22], pivot=[-8.5, -15.5, 0])),
    ("horn_l_base", "head", [4.0, -11.8, -2.0], [4, 7, 4], "G", dict(rot=[0, 0, 8], pivot=[6.0, -5.0, 0])),
    ("horn_l_prong", "head", [5.9, -17.8, -1.5], [3.5, 7, 3], "I", dict(rot=[0, 0, 14], pivot=[7.0, -11.0, 0])),
    ("horn_l_tip", "head", [7.4, -19.6, -1.5], [3, 3, 3], "G", dict(rot=[0, 0, 22], pivot=[8.5, -15.5, 0])),
]
CHEST = [
    ("gorget", "body", [-4.5, -2.0, -3.6], [9, 2, 7], "G"),
    ("chest_plate", "body", [-4.0, 0.4, -4.0], [8, 8, 1], "I"),
    ("back_plate", "body", [-4.0, 0.4, 3.0], [8, 8, 1], "I"),
    ("chest_emblem", "body", [-1.5, 2.6, -4.7], [3, 3, 1], "G", dict(rot=[0, 0, 45], pivot=[0, 4.1, -4.2])),
    ("belt", "body", [-4.5, 9.4, -3.9], [9, 2, 8], "G"),
    ("chest_band_1", "body", [-4.3, 1.8, -4.3], [8.6, 1, 1], "G"),
    ("chest_band_2", "body", [-4.3, 6.0, -4.3], [8.6, 1, 1], "G"),
    ("chest_spine", "body", [-0.5, 5.4, -4.5], [1, 4, 1], "G"),
    ("chest_diamond_core", "body", [-0.5, 3.4, -5.2], [1, 1, 1], "S"),
    # shoulders: big ROUNDED pauldrons (a big block, a rounding cap on top, a second layer, a lower layer), bright and angled outward
    ("pauldron_main", "@arm", [-7.8, -5.0, -4.4], [8, 4, 8.8], "G", dict(rot=[0, 0, -20], pivot=[-1.0, -2.0, 0])),
    ("pauldron_cap", "@arm", [-6.6, -6.2, -3.4], [6, 1, 6.8], "I", dict(rot=[0, 0, -20], pivot=[-1.0, -2.0, 0])),
    ("pauldron_round_front", "@arm", [-8.4, -4.4, -4.9], [7, 3, 1], "I", dict(rot=[0, 0, -20], pivot=[-1.0, -2.0, 0])),
    ("pauldron_round_back", "@arm", [-8.4, -4.4, 3.9], [7, 3, 1], "I", dict(rot=[0, 0, -20], pivot=[-1.0, -2.0, 0])),
    ("pauldron_mid", "@arm", [-8.8, -0.8, -4.6], [7, 4, 9.2], "I", dict(rot=[0, 0, -12], pivot=[-1.0, -2.0, 0])),
    ("pauldron_mid_cap", "@arm", [-8.0, -1.6, -3.6], [5, 1, 7.2], "G", dict(rot=[0, 0, -12], pivot=[-1.0, -2.0, 0])),
    ("pauldron_low", "@arm", [-8.0, 3.2, -4.2], [6, 3, 8.4], "G", dict(rot=[0, 0, -6], pivot=[-1.0, -2.0, 0])),
    ("elbow_ring", "@arm", [-4.4, 4.2, -3.4], [6.8, 1.6, 6.8], "G"),
    ("bracer", "@arm", [-4.5, 6.4, -3.5], [7, 3, 7], "I"),
    ("gauntlet_cuff", "@arm", [-4.5, 9.2, -3.5], [7, 1, 7], "G", dict(inflate=0.3)),
]
def skirt():
    """A long gold skirt hanging from the belt to the ankles, flaring wider at each of three tiers (front, back and both sides)."""
    out = []
    for k, (y, w, d) in enumerate([(11.6, 9.0, 4.4), (14.6, 10.4, 5.0), (17.6, 11.8, 5.6)]):
        m = "I" if k % 2 == 0 else "G"
        out.append((f"skirt_front_{k}", "body", [-w / 2, y, -d], [w, 3, 1], m))
        out.append((f"skirt_back_{k}", "body", [-w / 2, y, d - 1], [w, 3, 1], m))
        out.append((f"skirt_right_{k}", "body", [-w / 2, y, -d + 1], [1, 3, 2 * d - 2], m))
        out.append((f"skirt_left_{k}", "body", [w / 2 - 1, y, -d + 1], [1, 3, 2 * d - 2], m))
    out.append(("skirt_hem", "body", [-6.4, 20.4, -5.9], [12.8, 1, 1], "G"))
    out.append(("skirt_center", "body", [-1.0, 11.6, -4.5], [2, 8, 1], "G"))
    return out


LEGS = skirt() + [
    ("knee_cop", "@leg", [-2.0, 4.8, -3.7], [4, 2, 1], "G"),
]
BOOTS = [
    ("boot_cuff", "@leg", [-2.8, 6.0, -2.8], [5.6, 1, 5.6], "G", dict(inflate=0.2)),
    ("toe_cap", "@leg", [-2.5, 10.4, -4.6], [5, 2, 2], "I"),
]
SETS = {"shrine_helmet": HELMET, "shrine_chestplate": CHEST, "shrine_leggings": LEGS, "shrine_boots": BOOTS}


class Packer:
    """Shelf packer for the unfolded boxes; identical (size, material) boxes share one piece of texture."""

    def __init__(self):
        self.x = self.y = self.row = 0
        self.seen = {}

    def place(self, size, mat):
        key = (tuple(size), mat)
        if key in self.seen:
            return self.seen[key]
        sx, sy, sz = size
        w, h = 2 * (sx + sz), sz + sy
        if self.x + w > TEX:
            self.x, self.y, self.row = 0, self.y + self.row + 1, 0
        if self.y + h > TEX:
            raise SystemExit("extras texture is full - raise TEX")
        uv = [self.x, self.y]
        self.x += w + 1
        self.row = max(self.row, h)
        self.seen[key] = uv
        return uv


def main():
    pack = Packer()
    pieces = {}
    count = 0
    for item, boxes in SETS.items():
        out = []
        for b in boxes:
            name, part, org, size, mat = b[:5]
            size = [max(1, int(round(v))) for v in size]          # texture boxes need whole-pixel sizes
            opt = b[5] if len(b) > 5 else {}
            uv = pack.place(size, MAT[mat])
            sides = [("", False)] if not part.startswith("@") else [("_right", False), ("_left", True)]
            for suffix, mirror in sides:
                p = part
                if part == "@arm":
                    p = "left_arm" if mirror else "right_arm"
                elif part == "@leg":
                    p = "left_leg" if mirror else "right_leg"
                elif part == "@body":
                    p = "body"
                o = list(org)
                rot = list(opt.get("rot", [0, 0, 0]))
                piv = list(opt.get("pivot", [0, 0, 0]))
                if mirror:
                    o[0] = -(org[0] + size[0])
                    rot = [rot[0], -rot[1], -rot[2]]
                    piv[0] = -piv[0]
                e = {"name": name + suffix, "part": p, "origin": o, "size": list(size), "uv": uv, "inflate": opt.get("inflate", 0.0), "material": MAT[mat]}
                if any(rot):
                    e["rotation"], e["rotation_origin"] = rot, piv
                if mirror:
                    e["mirror"] = True
                out.append(e)
                count += 1
        pieces[item] = out
    data = {
        "_help": "GENERATED by tools/make_knight_armor.py (edit that script, not this file). 3D parts of the golden knight armor: boxes standing "
                 "out from the vanilla armor skin. Read by the game's client renderer AND by the editor page. Positions are player-model pixels "
                 "relative to the pivot of 'part' (head 0,0,0 | body 0,0,0 | right_arm -5,2,0 | left_arm 5,2,0 | right_leg -1.9,12,0 | left_leg 1.9,12,0). "
                 "Y points DOWN. 'uv' = top-left texel of the box's unfolded texture in tuff_extras.png. 'rotation' = [pitch, yaw, roll] degrees about 'rotation_origin'.",
        "texture": "shrinecraft:textures/entity/armor_extra/tuff_extras.png",
        "texture_size": [TEX, TEX],
        "pieces": pieces,
    }
    with open(OUT, "w") as f:
        json.dump(data, f, indent=1)
    print("armor 3D parts:", count, "boxes ->", os.path.relpath(OUT, ROOT))


if __name__ == "__main__":
    main()
