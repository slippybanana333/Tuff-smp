"""
MODEL SPECIFICATIONS  (used ONCE by gen_models.py to create the model JSON files).

After the JSON files exist they are the single source of truth - edit THEM (or ask Claude to), then run
    python3 tools/rebuild_all.py
Do not re-run gen_models.py unless you want to throw away hand edits (it refuses unless you pass --force).

Conventions for every item model (units: 16 = 1 block, a Minecraft player is 32 units tall, an arm is 12 long):
  * +Y is the "business end" (blade tip / axe head / spear tip / hat top); the grip is near the middle or bottom
  * the model is centred on x = 8, z = 8
  * the detailed FRONT is the south (+Z) face
  * every held item has one element named "grip" - that element is what sits in the player's palm

Each element: box(name, from, to, material, rot=...)   - 'material' picks the pixel-art paint (see paint_textures.py)
"""
import math


class Spec:
    def __init__(self, name, kind, texture=None, group=None):
        self.name = name              # model file name (no extension)
        self.kind = kind              # sword | axe | pick | mace | bow | crossbow | spear | trident | orb | armor | cape | block
        self.texture = texture or name
        self.group = group or name    # models in a group share one texture atlas
        self.els = []
        self.default_mat = "netherite"
        self.display_override = {}
        self.gui_rot = None           # optional (x, y, z) override for the inventory view
        self.scale = 1.0              # whole-model scale about (x=8, y=0, z=8) applied when the JSON is written
        self.last_end = None

    def box(self, name, frm, to, mat, rot=None, skip=()):
        e = {"name": name, "from": [float(v) for v in frm], "to": [float(v) for v in to], "mat": mat}
        if rot:
            origin, axis, angle = rot
            e["rotation"] = {"origin": [float(v) for v in origin], "axis": axis, "angle": float(angle)}
        if skip:
            e["skip"] = list(skip)
        self.els.append(e)
        return e

    # ---- a chain of boxes that bends (blade, bow limb, pick arm ...) ----
    def chain_z(self, prefix, base, a0, n, seg, w0, w1, thick, zc, dtheta, mat, mat_last=None):
        """Bends in the XY plane (rotation about Z). base=(x,y); a0 = start angle in degrees (0 = +Y, positive = leans toward -X)."""
        bx, by = base
        a = a0
        out = []
        for i in range(n):
            w = w0 + (w1 - w0) * (i / max(1, n - 1))
            m = mat_last if (mat_last and i == n - 1) else mat
            out.append(self.box(f"{prefix}_{i + 1}", (bx - w / 2, by, zc - thick / 2), (bx + w / 2, by + seg, zc + thick / 2), m,
                                rot=((bx, by, zc), "z", a)))
            ar = math.radians(a)
            bx += -seg * math.sin(ar) * 0.98
            by += seg * math.cos(ar) * 0.98
            a += dtheta
        self.last_end = (bx, by)
        return out

    def chain_x(self, prefix, base, a0, n, seg, w0, w1, thick, xc, dtheta, mat, mat_last=None):
        """Bends in the YZ plane (rotation about X). base=(y,z); a0 in degrees (0 = +Y, positive = leans toward +Z)."""
        by, bz = base
        a = a0
        out = []
        for i in range(n):
            w = w0 + (w1 - w0) * (i / max(1, n - 1))
            m = mat_last if (mat_last and i == n - 1) else mat
            out.append(self.box(f"{prefix}_{i + 1}", (xc - w / 2, by, bz - thick / 2), (xc + w / 2, by + seg, bz + thick / 2), m,
                                rot=((xc, by, bz), "x", a)))
            ar = math.radians(a)
            by += seg * math.cos(ar) * 0.98
            bz += seg * math.sin(ar) * 0.98
            a += dtheta
        self.last_end = (by, bz)
        return out


def mirror_x(spec, names, suffix_from="_l", suffix_to="_r"):
    """Not used for geometry generation; kept for hand-editing convenience."""
    raise NotImplementedError


SPECS = []


def add(spec):
    SPECS.append(spec)
    return spec


# =====================================================================================================
# 1. MOLTEN GREATAXE - dark netherite battle axe, lava core in the blade, cracked glowing obsidian edges, flames at the tip
# =====================================================================================================
def molten_greataxe():
    """Double-headed war axe (concept art: spiked back blade on one side, a big crescent blade on the other, spiked ring on the haft,
    hooked pommel). LAVA THEME: blackened obsidian plates with glowing lava cracks, the right blade molten with a burning edge."""
    s = Spec("molten_greataxe", "axe")
    s.default_mat = "netherite"
    s.scale = 0.76
    o = -3  # shift everything down so the model stays inside the -16..32 limit comfortably
    def B(name, a, b, mat, **kw):
        return s.box(name, (a[0], a[1] + o, a[2]), (b[0], b[1] + o, b[2]), mat, **kw)
    def R(origin, axis, ang):
        return ((origin[0], origin[1] + o, origin[2]), axis, ang)
    def both(name, x0, x1, y0, y1, z0, z1, mat, ang=0.0, side_mats=("netherite", "netherite")):
        pass

    # --- hooked pommel with a spike (the concept's foot) ---
    B("pommel_spike", (7.35, -2.8, 7.35), (8.65, 0.2, 8.65), "edge_glow")
    B("pommel_cap", (6.6, 0.2, 6.6), (9.4, 2.4, 9.4), "netherite")
    B("pommel_claw_left", (5.4, 0.6, 7.35), (6.8, 1.8, 8.65), "netherite")
    B("pommel_claw_left_tip", (4.4, 1.4, 7.55), (5.6, 3.4, 8.45), "ember_metal", rot=R((5.6, 1.4, 8), "z", 14))
    B("pommel_claw_right", (9.2, 0.6, 7.35), (10.6, 1.8, 8.65), "netherite")
    B("pommel_claw_right_tip", (10.4, 1.4, 7.55), (11.6, 3.4, 8.45), "ember_metal", rot=R((10.4, 1.4, 8), "z", -14))
    # --- haft: wrapped grip, strap bands and the spiked ring ---
    B("grip", (7.05, 2.4, 7.05), (8.95, 19.5, 8.95), "leather_dark")
    for i, y in enumerate((4.0, 8.0, 12.0, 17.0), 1):
        B("strap_band_%d" % i, (6.55, y, 6.55), (9.45, y + 1.0, 9.45), "netherite")
    B("ring_spike_front", (7.3, 13.8, 9.4), (8.7, 15.0, 10.9), "netherite")
    B("ring_spike_back", (7.3, 13.8, 5.1), (8.7, 15.0, 6.6), "netherite")
    B("ring_spike_left", (5.1, 13.8, 7.3), (6.6, 15.0, 8.7), "netherite")
    B("ring_spike_right", (9.4, 13.8, 7.3), (10.9, 15.0, 8.7), "netherite")
    B("ring_ember", (6.3, 14.6, 6.3), (9.7, 15.0, 9.7), "lava")
    # --- the head block: neck, carved core, crown with two horns and a spike ---
    B("head_neck", (6.2, 19.5, 6.2), (9.8, 21.6, 9.8), "netherite")
    B("head_core", (5.5, 21.6, 6.1), (10.5, 29.4, 9.9), "obsidian_crack")
    B("head_core_face_front", (6.4, 23.0, 9.9), (9.6, 27.6, 10.4), "lava")
    B("head_core_face_back", (6.4, 23.0, 5.6), (9.6, 27.6, 6.1), "lava")
    B("head_cap", (6.3, 29.4, 6.3), (9.7, 30.6, 9.7), "netherite")
    B("crown_spike", (7.25, 30.6, 7.25), (8.75, 33.8, 8.75), "netherite")
    B("crown_tip", (7.65, 33.8, 7.65), (8.35, 35.2, 8.35), "edge_glow")
    B("horn_left", (5.6, 29.6, 7.3), (7.0, 32.4, 8.7), "netherite", rot=R((6.3, 29.6, 8), "z", 16))
    B("horn_left_tip", (5.2, 32.2, 7.6), (6.2, 34.0, 8.4), "edge_glow", rot=R((5.7, 32.2, 8), "z", 16))
    B("horn_right", (9.0, 29.6, 7.3), (10.4, 32.4, 8.7), "netherite", rot=R((9.7, 29.6, 8), "z", -16))
    B("horn_right_tip", (9.8, 32.2, 7.6), (10.8, 34.0, 8.4), "edge_glow", rot=R((10.3, 32.2, 8), "z", -16))
    # --- LEFT blade: blackened, spiked back edge (the concept's grey spiked blade) ---
    B("blade_left_root", (2.4, 22.4, 7.1), (5.5, 28.4, 8.9), "obsidian_crack")
    B("blade_left_mid", (-1.4, 21.4, 7.3), (2.4, 29.4, 8.7), "obsidian_crack")
    B("blade_left_top", (-2.6, 27.4, 7.4), (0.8, 31.8, 8.6), "obsidian_crack", rot=R((0.8, 27.4, 8), "z", 24))
    B("blade_left_bottom", (-2.6, 19.2, 7.4), (0.8, 23.2, 8.6), "obsidian_crack", rot=R((0.8, 23.2, 8), "z", -24))
    for i, (y, w) in enumerate(((19.8, 1.6), (23.0, 1.9), (26.2, 1.9), (29.4, 1.7)), 1):
        B("blade_left_spike_%d" % i, (-1.4 - 1.5 - w * 0.3, y, 7.6), (-1.4 - 0.3, y + 1.1, 8.4), "netherite")
    B("blade_left_vein", (0.0, 24.0, 6.9), (3.6, 26.6, 9.1), "lava")
    # --- RIGHT blade: molten crescent with a burning edge and flames ---
    B("blade_right_root", (10.5, 22.4, 7.1), (13.6, 28.4, 8.9), "obsidian_crack")
    B("blade_right_mid", (13.6, 21.4, 7.3), (17.4, 29.4, 8.7), "obsidian_crack")
    B("blade_right_top", (15.2, 27.4, 7.4), (18.6, 31.8, 8.6), "obsidian_crack", rot=R((15.2, 27.4, 8), "z", -24))
    B("blade_right_bottom", (15.2, 19.2, 7.4), (18.6, 23.2, 8.6), "obsidian_crack", rot=R((15.2, 23.2, 8), "z", 24))
    B("edge_right_mid", (17.4, 22.0, 7.5), (18.9, 28.8, 8.5), "edge_glow")
    B("edge_right_top", (17.0, 28.0, 7.5), (18.4, 32.4, 8.5), "edge_glow", rot=R((17.0, 28.0, 8), "z", -24))
    B("edge_right_bottom", (17.0, 18.6, 7.5), (18.4, 22.6, 8.5), "edge_glow", rot=R((17.0, 22.6, 8), "z", 24))
    B("blade_right_core", (11.8, 23.4, 6.7), (16.2, 27.4, 9.3), "lava")
    B("flame_1", (16.4, 31.0, 7.55), (17.8, 34.6, 8.45), "flame", rot=R((16.4, 31.0, 8), "z", -18))
    B("flame_2", (13.8, 29.8, 7.6), (15.0, 32.8, 8.4), "flame", rot=R((13.8, 29.8, 8), "z", -30))
    B("flame_3", (19.0, 26.0, 7.65), (20.2, 29.2, 8.35), "flame", rot=R((19.0, 26.0, 8), "z", -42))
    return s


# =====================================================================================================
# 2. CUTLASS OF DOOM - curved pirate cutlass, blackened cursed steel, ghostly teal edge, barnacle-crusted golden hilt, sea-foam mist
# =====================================================================================================
def cutlass_of_doom():
    """Curved kopis-style blade with a big golden knuckle guard (concept art): belly widening toward the tip, spine with notches, glowing
    flowing fullers. SEA-CURSE THEME: blackened cursed steel, glowing teal fullers and edge, barnacle-crusted gold guard, sea mist at the tip."""
    import math
    s = Spec("cutlass_of_doom", "sword")
    s.default_mat = "cursed_steel"
    s.scale = 0.84
    # pommel + wrapped grip
    s.box("pommel", (6.8, -1.2, 7.1), (9.2, 1.0, 8.9), "gold_barnacle")
    s.box("pommel_gem", (7.5, -2.0, 7.5), (8.5, -1.2, 8.5), "teal_edge")
    s.box("grip", (7.2, 1.0, 7.25), (8.8, 7.2, 8.75), "black_wrap")
    s.box("grip_ring_low", (6.9, 2.4, 6.95), (9.1, 3.3, 9.05), "gold_barnacle")
    s.box("grip_ring_high", (6.9, 5.0, 6.95), (9.1, 5.9, 9.05), "gold_barnacle")
    # cross guard with teal inlay and the big knuckle-bow loop on the spine side (-X), as in the concept
    s.box("guard", (5.0, 7.2, 6.7), (11.4, 8.6, 9.3), "gold_barnacle")
    s.box("guard_inlay_front", (6.2, 7.5, 9.3), (10.2, 8.3, 9.55), "teal_edge")
    s.box("guard_inlay_back", (6.2, 7.5, 6.45), (10.2, 8.3, 6.7), "teal_edge")
    s.box("guard_tip_right", (11.4, 7.5, 7.3), (12.8, 8.5, 8.7), "gold_barnacle")
    s.box("knuckle_bow_bottom", (3.2, -0.4, 7.35), (7.0, 0.9, 8.65), "gold_barnacle")
    s.box("knuckle_bow_low", (2.8, 0.6, 7.35), (4.2, 4.2, 8.65), "gold_barnacle")
    s.box("knuckle_bow_mid", (2.6, 3.8, 7.35), (4.0, 7.4, 8.65), "gold_barnacle")
    s.box("knuckle_bow_top", (3.0, 7.0, 7.35), (5.4, 8.8, 8.65), "gold_barnacle")
    s.box("knuckle_ring", (2.9, 5.2, 8.65), (3.9, 6.2, 9.25), "teal_edge")
    for i, (x, y, z) in enumerate([(5.4, 8.6, 6.8), (10.0, 8.6, 8.2), (3.0, 2.0, 8.6), (9.2, 6.4, 6.7)], 1):
        s.box("barnacle_%d" % i, (x, y, z), (x + 1.2, y + 1.1, z + 1.1), "barnacle")
    # the blade: 4 segments widening, 4 narrowing, curving toward +X (belly side)
    base_x, base_y, a, step = 8.0, 8.6, -2.0, -4.0
    segs = 4
    s.chain_z("blade_a", (base_x, base_y), a, segs, 2.6, 3.0, 4.8, 1.0, 8, step, "cursed_steel")
    bx, by = s.last_end
    a2 = a + segs * step
    s.chain_z("blade_b", (bx, by), a2, segs, 2.6, 4.8, 0.9, 1.0, 8, step, "cursed_steel")
    ex, ey = s.last_end
    # glowing fuller + belly edge following the same curve
    s.chain_z("fuller", (base_x + 0.3, base_y + 0.4), a, segs, 2.6, 0.9, 1.3, 1.25, 8, step, "teal_edge")
    s.chain_z("fuller_tip", (s.last_end[0], s.last_end[1]), a2, segs, 2.6, 1.3, 0.4, 1.25, 8, step, "teal_edge")
    # notched spine: small teeth along the -X side, placed on each segment of the lower blade
    px, py, ang = base_x, base_y, a
    for i in range(segs):
        w = 3.0 + (4.8 - 3.0) * (i / max(1, segs - 1))
        s.box("spine_notch_%d" % (i + 1), (px - w / 2 - 0.55, py + 0.7, 7.4), (px - w / 2 + 0.25, py + 1.7, 8.6), "gold_barnacle",
              rot=((px, py, 8), "z", ang))
        r = math.radians(ang)
        px += -2.6 * math.sin(r) * 0.98
        py += 2.6 * math.cos(r) * 0.98
        ang += step
    s.box("tip_mist_1", (ex + 0.4, ey - 0.8, 7.5), (ex + 1.4, ey + 0.2, 8.5), "foam")
    s.box("tip_mist_2", (ex + 2.2, ey - 2.6, 7.0), (ex + 3.0, ey - 1.8, 7.8), "foam")
    s.box("tip_mist_3", (ex + 1.3, ey + 1.2, 8.3), (ex + 2.1, ey + 2.0, 9.1), "foam")
    return s


# =====================================================================================================
# 3. SCULK MACE - dwarven war hammer look (double-drum head, wrapped haft, clawed pommel) in sculk stone / veins / sensors
# =====================================================================================================
def sculk_mace():
    """A two-handed DWARVEN WAR HAMMER in sculk colours: wrapped haft with glowing rings, clawed pommel, an octagonal
    double-drum head (one drum each side of a carved core), crown spike on top. The sculk theme: sculk-stone drums with
    glowing veins where the original has gold, vein-metal rims, and sensor clusters growing on the drums."""
    s = Spec("sculk_mace", "mace")
    s.default_mat = "sculk_stone"
    s.scale = 0.80

    def both(name, x0, x1, y0, y1, z0, z1, mat):          # one box on each side of the head (mirrored in X around x=8)
        s.box(name + "_left", (x0, y0, z0), (x1, y1, z1), mat)
        s.box(name + "_right", (16 - x1, y0, z0), (16 - x0, y1, z1), mat)

    # pommel: ball + downward spike + two curved claws
    s.box("pommel", (6.9, 0.6, 6.9), (9.1, 2.8, 9.1), "sculk_stone")
    s.box("pommel_spike", (7.3, -1.2, 7.3), (8.7, 0.6, 8.7), "vein_glow")
    both("claw", 5.6, 7.4, 0.6, 1.8, 7.2, 8.8, "vein_metal")
    both("claw_tip", 4.8, 5.8, 1.6, 3.6, 7.4, 8.6, "vein_glow")
    # haft with metal rings
    s.box("grip", (7.1, 2.8, 7.1), (8.9, 17.8, 8.9), "black_wrap")
    for i, y in enumerate((4.2, 9.0, 13.8), 1):
        s.box("grip_ring_%d" % i, (6.3, y, 6.3), (9.7, y + 1.0, 9.7), "vein_metal")
    # socket under the head
    s.box("collar_low", (6.4, 17.8, 6.4), (9.6, 19.0, 9.6), "vein_metal")
    s.box("collar_high", (5.4, 19.0, 5.4), (10.6, 20.4, 10.6), "sculk_stone")
    # carved core + front/back relief plates + crown
    s.box("core", (5.4, 20.4, 5.0), (10.6, 28.0, 11.0), "sculk_stone")
    s.box("core_face_front", (6.2, 21.6, 11.0), (9.8, 26.8, 11.5), "vein_glow")
    s.box("core_face_back", (6.2, 21.6, 4.5), (9.8, 26.8, 5.0), "vein_glow")
    s.box("crown_base", (6.0, 28.0, 6.0), (10.0, 29.2, 10.0), "vein_metal")
    s.box("crown_spike", (7.2, 29.2, 7.2), (8.8, 31.0, 8.8), "sculk_stone")
    s.box("crown_tip", (7.6, 31.0, 7.6), (8.4, 32.4, 8.4), "vein_glow")
    # the two drums: octagon = a tall box + a wide box crossed; vein-metal rim at the outer end, glowing boss, rune plate
    both("drum_tall", 1.4, 5.4, 20.6, 27.8, 5.4, 10.6, "sculk_stone")
    both("drum_wide", 1.4, 5.4, 21.6, 26.8, 4.4, 11.6, "sculk_stone")
    both("rim_tall", 0.6, 1.4, 20.3, 28.1, 5.1, 10.9, "vein_metal")
    both("rim_wide", 0.6, 1.4, 21.3, 27.1, 4.1, 11.9, "vein_metal")
    both("boss", 0.2, 0.6, 22.2, 26.2, 6.0, 10.0, "vein_glow")
    both("rune_plate", 2.2, 4.4, 22.8, 25.6, 11.6, 11.9, "vein_glow")
    # sculk sensor clusters sprouting on top of the drums
    both("sensor_cluster", 2.4, 4.0, 27.8, 29.4, 7.0, 9.0, "sensor_cluster")
    return s


# =====================================================================================================
# 4. FROST LONGBOW - icy longbow, pale-blue crystalline limbs, white wrapped grip with silver band, frost mist
# =====================================================================================================
def frost_longbow_parts(s, pull=None):
    """Adds the bow geometry. pull = None (idle) or 0/1/2 (how far the string is drawn back)."""
    s.default_mat = "ice_crystal"
    # riser: wrapped grip between two silver collars with ice gems, as in the concept art's ornamented handle
    s.box("grip", (7.1, 11.6, 6.8), (8.9, 16.4, 9.2), "white_wrap")
    s.box("grip_band", (6.8, 13.5, 6.6), (9.2, 14.5, 9.4), "silver")
    s.box("collar_upper", (6.6, 16.4, 6.4), (9.4, 17.8, 9.6), "silver")
    s.box("collar_lower", (6.6, 10.2, 6.4), (9.4, 11.6, 9.6), "silver")
    s.box("gem_upper", (7.3, 16.7, 9.6), (8.7, 17.5, 9.9), "ice_tip")
    s.box("gem_lower", (7.3, 10.5, 9.6), (8.7, 11.3, 9.9), "ice_tip")
    s.box("gem_upper_back", (7.3, 16.7, 6.1), (8.7, 17.5, 6.4), "ice_tip")
    s.box("gem_lower_back", (7.3, 10.5, 6.1), (8.7, 11.3, 6.4), "ice_tip")
    # RECURVE limbs (concept): belly curves toward the archer (+Z), the last segments flick back away from the archer, ending in wrapped hooks
    s.chain_x("limb_upper", (17.8, 8), 6, 4, 3.0, 2.3, 1.4, 2.0, 8, 9, "ice_crystal")
    s.chain_x("limb_upper_tip", s.last_end, 42, 2, 2.2, 1.4, 0.9, 1.8, 8, -24, "ice_crystal", mat_last="ice_tip")
    ytop, zt = s.last_end
    s.chain_x("limb_lower", (10.2, 8), 174, 4, 3.0, 2.3, 1.4, 2.0, 8, -9, "ice_crystal")
    s.chain_x("limb_lower_tip", s.last_end, 138, 2, 2.2, 1.4, 0.9, 1.8, 8, 24, "ice_crystal", mat_last="ice_tip")
    ybot, zb = s.last_end
    # glowing frost inlay running along both limbs + thorn-like ice shards (the concept's silver spikes) on the belly side
    s.chain_x("inlay_upper", (18.0, 8), 6, 4, 3.0, 2.5, 1.6, 0.9, 8, 9, "ice_tip")
    s.chain_x("inlay_lower", (10.0, 8), 174, 4, 3.0, 2.5, 1.6, 0.9, 8, -9, "ice_tip")
    s.box("thorn_upper", (7.5, 21.6, 6.2), (8.5, 25.2, 7.3), "ice_tip", rot=((8, 21.6, 7.3), "x", -28))
    s.box("thorn_lower", (7.5, 3.2, 6.2), (8.5, 6.8, 7.3), "ice_tip", rot=((8, 6.8, 7.3), "x", 28))
    s.box("hook_wrap_upper", (7.2, ytop - 0.9, zt - 0.9), (8.8, ytop + 0.5, zt + 0.9), "white_wrap")
    s.box("hook_wrap_lower", (7.2, ybot - 0.5, zb - 0.9), (8.8, ybot + 0.9, zb + 0.9), "white_wrap")
    s.box("frost_mist_1", (7.2, ytop + 0.3, zt - 0.8), (8.4, ytop + 1.3, zt + 0.4), "frost_mist")
    s.box("frost_mist_2", (8.6, ytop - 1.2, zt + 1.6), (9.6, ytop - 0.2, zt + 2.6), "frost_mist")
    s.box("frost_mist_3", (7.0, ybot - 1.3, zb - 0.6), (8.2, ybot - 0.3, zb + 0.6), "frost_mist")
    s.box("frost_mist_4", (8.6, ybot + 1.0, zb + 1.8), (9.5, ybot + 2.0, zb + 2.8), "frost_mist")
    # string
    ytop, ybot = ytop, ybot
    if pull is None:
        s.box("string", (7.85, ybot, zt - 0.15), (8.15, ytop, zt + 0.15), "bowstring")
    else:
        back = [0.0, 2.2, 4.4][pull]
        zn = zt + back
        # two string halves meeting at the nock point (y = 14.5)
        top = (ytop, zt)
        bot = (ybot, zt)
        nock = ((ytop + ybot) / 2, zn)
        def half(name, p0, p1):
            import math as m
            dy, dz = p1[0] - p0[0], p1[1] - p0[1]
            ln = m.hypot(dy, dz)
            ang = m.degrees(m.atan2(dz, dy))
            # box along +Y from p0 of length ln, rotated about X by (angle from +Y toward +Z)
            s.box(name, (7.85, p0[0], p0[1] - 0.15), (8.15, p0[0] + ln, p0[1] + 0.15), "bowstring", rot=((8, p0[0], p0[1]), "x", ang))
        half("string_lower", bot, nock)
        half("string_upper", nock, top)
        # nocked arrow
        ny = nock[0]
        s.box("arrow_shaft", (7.88, ny - 0.3, zn - 11.5), (8.12, ny + 0.3, zn + 0.5), "arrow_wood")
        s.box("arrow_head", (7.7, ny - 0.6, zn - 14.0), (8.3, ny + 0.6, zn - 11.5), "arrow_head")
        s.box("arrow_fletch", (7.6, ny - 0.6, zn - 1.2), (8.4, ny + 0.6, zn + 1.0), "arrow_fletch")


def frost_longbow():
    s = Spec("frost_longbow", "bow", group="frost_longbow")
    s.scale = 0.72
    frost_longbow_parts(s, None)
    return s


def frost_longbow_pulling(i):
    s = Spec(f"frost_longbow_pulling_{i}", "bow", texture="frost_longbow", group="frost_longbow")
    s.scale = 0.72
    frost_longbow_parts(s, i)
    return s


# =====================================================================================================
# 5. CLOUD LANCE - long slender spear, white-gold tip, wind-swirl etchings down the shaft, pale sky-blue glow
# =====================================================================================================
def cloud_lance():
    """Slender ceremonial lance (concept art): twin long prongs on a glowing gem, ornate leaf guard, twisted shaft, a ribbon streaming in the
    wind. CLOUD THEME: white-and-gold metal, sky-blue glow, cloud-white ribbon (instead of red), a pair of feathers by the guard."""
    s = Spec("cloud_lance", "spear")
    s.default_mat = "sky_shaft"
    s.scale = 0.74
    s.box("butt_tassel", (7.45, -3.6, 7.45), (8.55, -0.2, 8.55), "sky_glow")
    s.box("butt_spike", (7.35, -0.2, 7.35), (8.65, 1.6, 8.65), "white_gold")
    s.box("butt_cap", (6.9, 1.6, 6.9), (9.1, 3.2, 9.1), "white_gold")
    s.box("shaft_lower", (7.3, 3.2, 7.3), (8.7, 14.5, 8.7), "sky_shaft")
    # twisted grip: wrapped section with alternating bands
    s.box("grip", (7.0, 14.5, 7.0), (9.0, 24.5, 9.0), "leather_white")
    for i, y in enumerate((15.6, 17.8, 20.0, 22.2), 1):
        s.box("grip_twist_%d" % i, (6.75, y, 6.75), (9.25, y + 0.8, 9.25), "white_gold")
    s.box("shaft_upper", (7.3, 24.5, 7.3), (8.7, 33.0, 8.7), "sky_shaft")
    s.box("collar_low", (6.6, 33.0, 6.6), (9.4, 34.4, 9.4), "white_gold")
    # ornate leaf guard around a glowing gem
    s.box("guard_gem", (7.2, 34.4, 7.2), (8.8, 36.4, 8.8), "sky_glow")
    s.box("guard_leaf_left", (4.8, 34.6, 7.55), (7.2, 37.6, 8.45), "white_gold", rot=((7.2, 34.6, 8), "z", 18))
    s.box("guard_leaf_right", (8.8, 34.6, 7.55), (11.2, 37.6, 8.45), "white_gold", rot=((8.8, 34.6, 8), "z", -18))
    s.box("guard_leaf_left_tip", (4.4, 37.0, 7.75), (5.8, 39.4, 8.25), "white_gold", rot=((5.8, 37.0, 8), "z", 28))
    s.box("guard_leaf_right_tip", (10.2, 37.0, 7.75), (11.6, 39.4, 8.25), "white_gold", rot=((10.2, 37.0, 8), "z", -28))
    s.box("collar_high", (6.6, 36.4, 6.6), (9.4, 37.6, 9.4), "white_gold")
    # twin prongs with a glowing slit between them (concept: two long narrow blades)
    s.box("prong_left", (6.8, 37.6, 7.35), (7.85, 47.0, 8.65), "white_gold")
    s.box("prong_right", (8.15, 37.6, 7.35), (9.2, 47.0, 8.65), "white_gold")
    s.box("prong_glow", (7.85, 38.0, 7.6), (8.15, 46.0, 8.4), "sky_glow")
    s.box("prong_left_tip", (7.05, 47.0, 7.6), (7.85, 49.6, 8.4), "white_gold", rot=((7.85, 47.0, 8), "z", -3))
    s.box("prong_right_tip", (8.15, 47.0, 7.6), (8.95, 49.6, 8.4), "white_gold", rot=((8.15, 47.0, 8), "z", 3))
    # feathers by the guard
    s.box("feather_left", (4.2, 28.0, 7.7), (6.6, 34.0, 8.3), "feather", rot=((6.6, 28.0, 8), "z", 16))
    s.box("feather_right", (9.4, 28.0, 7.7), (11.8, 34.0, 8.3), "feather", rot=((9.4, 28.0, 8), "z", -16))
    # the ribbon streaming off to the side (cloud-white)
    s.box("ribbon_1", (8.7, 27.5, 7.7), (13.4, 28.7, 8.3), "feather", rot=((8.7, 27.5, 8), "z", -12))
    s.box("ribbon_2", (12.6, 25.2, 7.7), (17.6, 26.4, 8.3), "feather", rot=((12.6, 25.2, 8), "z", -34))
    s.box("ribbon_3", (16.0, 20.0, 7.7), (18.4, 25.6, 8.3), "feather", rot=((16.0, 25.6, 8), "z", -14))
    s.box("ribbon_4", (17.2, 15.2, 7.7), (19.2, 20.6, 8.3), "feather", rot=((17.2, 20.6, 8), "z", 10))
    return s


# =====================================================================================================
# 6-9. ARMOR ITEMS (the 3D inventory/hand versions; the worn visuals are separate - see paint_textures.py)
# =====================================================================================================
def shrine_helmet():
    s = Spec("shrine_helmet", "armor")
    s.default_mat = "knit"
    s.box("cuff", (3, 0, 3), (13, 4.5, 13), "knit_cuff")
    s.box("cuff_fold_back", (2.6, 0.5, 4), (3, 4, 12), "knit_cuff")
    s.box("body", (3.5, 4.5, 3.5), (12.5, 9.5, 12.5), "knit")
    s.box("crown", (4.5, 9.5, 4.5), (11.5, 11.5, 11.5), "knit")
    s.box("slouch", (5, 8.5, 9.5), (11, 13.5, 15), "knit", rot=((8, 9, 11), "x", 25))
    s.box("slouch_tip", (6, 8, 13.4), (10, 11, 16.6), "knit", rot=((8, 9, 13), "x", 45))
    s.box("ember_stitch", (3.1, 4.5, 3.1), (12.9, 5.0, 12.9), "ember_thread")
    s.box("grip", (7, 3, 12.9), (9, 6, 13.2), "knit_cuff")  # the little tag on the back
    return s


def shrine_chestplate():
    s = Spec("shrine_chestplate", "armor")
    s.default_mat = "leather"
    s.box("torso", (4, 1, 6), (12, 14, 10), "leather")
    s.box("hem", (3.7, 0, 5.7), (12.3, 1.6, 10.3), "leather_dark")
    s.box("sleeve_left", (11.6, 3, 6.4), (15.4, 13.5, 9.6), "leather", rot=((12, 13, 8), "z", -10))
    s.box("sleeve_right", (0.6, 3, 6.4), (4.4, 13.5, 9.6), "leather", rot=((4, 13, 8), "z", 10))
    s.box("cuff_left", (12.9, 2, 6.2), (16.3, 3.8, 9.8), "leather_dark", rot=((12, 13, 8), "z", -10))
    s.box("cuff_right", (-0.3, 2, 6.2), (3.1, 3.8, 9.8), "leather_dark", rot=((4, 13, 8), "z", 10))
    s.box("collar_back", (4.4, 14, 8.6), (11.6, 17, 10.2), "leather")
    s.box("collar_left", (4.2, 14, 6.2), (5.8, 16.6, 9), "leather")
    s.box("collar_right", (10.2, 14, 6.2), (11.8, 16.6, 9), "leather")
    s.box("zipper", (7.45, 1.6, 5.7), (8.55, 14.4, 6.1), "zipper")
    s.box("pocket_left", (9.4, 4, 5.8), (11.6, 6.2, 6.2), "leather_dark")
    s.box("pocket_right", (4.4, 4, 5.8), (6.6, 6.2, 6.2), "leather_dark")
    s.box("aura_collar", (4.1, 16.6, 6.0), (11.9, 17.2, 10.4), "aura_glow")
    s.box("grip", (7.5, 7, 5.6), (8.5, 9, 5.8), "zipper")  # zipper pull
    return s


def shrine_leggings():
    s = Spec("shrine_leggings", "armor")
    s.default_mat = "denim"
    s.box("waist", (3.6, 12.5, 5.6), (12.4, 16, 10.4), "denim_dark")
    s.box("belt", (3.4, 13.2, 5.4), (12.6, 14.4, 10.6), "denim_belt")
    s.box("leg_left", (8.2, 1.5, 5.8), (12.4, 12.8, 10.2), "denim")
    s.box("leg_right", (3.6, 1.5, 5.8), (7.8, 12.8, 10.2), "denim")
    s.box("cuff_left", (7.8, 0, 5.2), (13.0, 2.6, 10.8), "denim_frayed")
    s.box("cuff_right", (3.0, 0, 5.2), (8.2, 2.6, 10.8), "denim_frayed")
    s.box("pocket_left", (10.0, 9.2, 4.9), (12.2, 11.8, 5.8), "denim_dark")
    s.box("pocket_right", (3.8, 9.2, 4.9), (6.0, 11.8, 5.8), "denim_dark")
    s.box("sag_fold_left", (8.6, 5.2, 4.9), (12.0, 7.0, 5.8), "denim_dark")
    s.box("sag_fold_right", (4.0, 5.2, 4.9), (7.4, 7.0, 5.8), "denim_dark")
    s.box("button", (7.4, 14.2, 5.2), (8.6, 15.4, 5.5), "rivet")
    s.box("grip", (7.4, 11.0, 5.0), (8.6, 12.0, 5.4), "rivet")
    return s


def shrine_boots():
    s = Spec("shrine_boots", "armor")
    s.default_mat = "boot_white"
    for tag, cx in (("left", 11.0), ("right", 5.0)):
        x1, x2 = cx - 2.6, cx + 2.6
        s.box(f"sole_{tag}", (x1 - 0.2, 0, 1.6), (x2 + 0.2, 1.6, 14.8), "sole")
        s.box(f"sole_air_{tag}", (x1 - 0.25, 0.4, 9.5), (x2 + 0.25, 1.1, 14.0), "air_window")
        s.box(f"toe_{tag}", (x1, 1.6, 8.6), (x2, 5.4, 14.2), "boot_white")
        s.box(f"toe_cap_{tag}", (x1 - 0.05, 1.6, 12.6), (x2 + 0.05, 3.2, 14.5), "boot_trim")
        s.box(f"mid_{tag}", (x1, 1.6, 4.6), (x2, 8.6, 8.6), "boot_white")
        s.box(f"heel_{tag}", (x1, 1.6, 2.0), (x2, 11.0, 4.6), "boot_trim")
        s.box(f"ankle_{tag}", (x1 + 0.2, 8.6, 4.6), (x2 - 0.2, 11.0, 8.6), "boot_white")
        s.box(f"collar_{tag}", (x1 - 0.2, 10.4, 1.8), (x2 + 0.2, 11.6, 9.0), "boot_trim")
        s.box(f"tongue_{tag}", (cx - 1.5, 5.4, 8.4), (cx + 1.5, 11.8, 9.8), "tongue", rot=((cx, 5.4, 9.0), "x", 12))
        s.box(f"bolt_{tag}", (x2, 3.4, 4.0), (x2 + 0.3, 8.2, 8.0), "bolt")
        s.box(f"lace_{tag}", (cx - 1.7, 5.0, 10.0), (cx + 1.7, 5.6, 12.6), "lace")
    # the 'grip' of this item (what the palm holds) is a patch on the left shoe's heel
    s.box("grip", (9.4, 4.0, 1.6), (12.6, 8.0, 5.0), "boot_trim")
    return s


# =====================================================================================================
# 10. HARBINGER OF DEATH - dark, ominous pickaxe
# =====================================================================================================
def harbinger_of_death():
    """Bird-skull war pick (concept art): two long curved blades, bone skull caps with horns and eye holes, crossed straps, a glowing capsule
    drum, a bent wooden haft with rings and wraps. DEATH THEME: blackened iron blades, bone-white skull plates, crimson glowing capsules/eyes."""
    s = Spec("harbinger_of_death", "pick")
    s.default_mat = "dark_iron"
    s.scale = 0.74
    # foot: spike + ring
    s.box("foot_spike", (7.35, -2.8, 7.35), (8.65, -0.2, 8.65), "bone_plate")
    s.box("foot_ring", (6.5, -0.2, 6.5), (9.5, 1.6, 9.5), "dark_iron")
    # bent wooden-style haft with bone wraps and rings
    s.box("haft_low", (7.1, 1.6, 7.1), (8.9, 10.0, 8.9), "bone_wrap")
    s.box("grip", (7.45, 10.0, 7.1), (9.25, 17.0, 8.9), "bone_wrap")
    s.box("haft_up", (7.1, 17.0, 7.1), (8.9, 20.0, 8.9), "bone_wrap")
    s.box("wrap_low", (6.7, 4.5, 6.7), (9.3, 5.7, 9.3), "dark_iron")
    s.box("wrap_mid", (6.9, 12.4, 6.7), (9.7, 13.6, 9.3), "dark_iron")
    s.box("rope_knot", (9.2, 14.2, 7.55), (10.6, 15.6, 8.45), "bone_plate")
    # the glowing capsule drum under the head (3 capsules on the front, 3 on the back)
    s.box("drum", (6.0, 20.0, 6.0), (10.0, 25.6, 10.0), "dark_iron")
    for i, x in enumerate((6.45, 7.65, 8.85), 1):
        s.box("capsule_front_%d" % i, (x, 21.0, 10.0), (x + 0.75, 24.4, 10.4), "rune_glow")
        s.box("capsule_back_%d" % i, (x, 21.0, 5.6), (x + 0.75, 24.4, 6.0), "rune_glow")
    s.box("drum_ring_low", (5.7, 19.6, 5.7), (10.3, 20.6, 10.3), "bone_plate")
    s.box("drum_ring_high", (5.7, 25.0, 5.7), (10.3, 26.2, 10.3), "bone_plate")
    # head hub and the two long curved blades (they sweep outward and down)
    s.box("head_hub", (5.2, 26.2, 6.2), (10.8, 29.6, 9.8), "dark_iron")
    s.chain_z("blade_left", (5.2, 28.0), 84, 4, 3.0, 3.6, 0.9, 1.9, 8, 17, "dark_iron", mat_last="rune_glow")
    s.chain_z("blade_right", (10.8, 28.0), -84, 4, 3.0, 3.6, 0.9, 1.9, 8, -17, "dark_iron", mat_last="rune_glow")
    # bird-skull caps with beaks, horns and eye holes
    s.box("skull_left", (3.4, 29.0, 6.5), (8.0, 31.6, 9.5), "bone_plate")
    s.box("skull_right", (8.0, 29.0, 6.5), (12.6, 31.6, 9.5), "bone_plate")
    s.box("beak_left", (0.8, 28.6, 7.1), (3.6, 30.4, 8.9), "bone_plate", rot=((3.6, 28.6, 8), "z", 14))
    s.box("beak_right", (12.4, 28.6, 7.1), (15.2, 30.4, 8.9), "bone_plate", rot=((12.4, 28.6, 8), "z", -14))
    s.box("horn_left", (4.4, 31.4, 7.2), (5.8, 34.8, 8.8), "bone_plate", rot=((5.1, 31.4, 8), "z", 10))
    s.box("horn_right", (10.2, 31.4, 7.2), (11.6, 34.8, 8.8), "bone_plate", rot=((10.9, 31.4, 8), "z", -10))
    s.box("crest_mid", (7.3, 29.6, 7.3), (8.7, 33.6, 8.7), "bone_plate")
    s.box("crest_tip", (7.65, 33.6, 7.65), (8.35, 35.4, 8.35), "rune_glow")
    s.box("eye_left", (5.0, 29.8, 9.5), (6.4, 31.0, 9.85), "rune_glow")
    s.box("eye_right", (9.6, 29.8, 9.5), (11.0, 31.0, 9.85), "rune_glow")
    # crossed straps over the head (front + back)
    s.box("strap_front_a", (5.4, 27.4, 9.8), (10.6, 28.5, 10.15), "bone_wrap", rot=((8, 28, 10), "z", 28))
    s.box("strap_front_b", (5.4, 27.4, 9.8), (10.6, 28.5, 10.15), "bone_wrap", rot=((8, 28, 10), "z", -28))
    s.box("strap_back_a", (5.4, 27.4, 5.85), (10.6, 28.5, 6.2), "bone_wrap", rot=((8, 28, 6), "z", 28))
    s.box("strap_back_b", (5.4, 27.4, 5.85), (10.6, 28.5, 6.2), "bone_wrap", rot=((8, 28, 6), "z", -28))
    return s


# =====================================================================================================
# 11. ANAKLUSMOS - sea-bronze trident
# =====================================================================================================
def anaklusmos():
    """Sea-god trident (concept art): gold three-pronged head (curved outer prongs, straight centre), grey-blue sea-dragon fins fanned out
    around the head and at the foot, a plain steel shaft with gold collars, a gold spearhead at the bottom. SEA THEME: sea-bronze gold,
    teal-patina fins, wave-wrapped shaft, a sea-blue gem and a little dragon-head emblem on the hub."""
    s = Spec("anaklusmos", "trident")
    s.default_mat = "sea_bronze"
    s.scale = 0.56
    # foot: gold spearhead with two curling fins (concept's lower end)
    s.box("foot_tip", (7.4, -3.8, 7.5), (8.6, 0.0, 8.5), "bronze_bright")
    s.box("foot_blade", (6.7, 0.0, 7.35), (9.3, 3.6, 8.65), "bronze_bright")
    s.box("foot_fin_left", (4.8, 1.4, 7.45), (6.9, 5.4, 8.55), "teal_patina", rot=((6.9, 1.4, 8), "z", 22))
    s.box("foot_fin_right", (9.1, 1.4, 7.45), (11.2, 5.4, 8.55), "teal_patina", rot=((9.1, 1.4, 8), "z", -22))
    s.box("collar_low", (6.5, 3.6, 6.5), (9.5, 5.8, 9.5), "sea_bronze")
    # shaft with a wave-wrapped grip
    s.box("shaft_lower", (7.1, 5.8, 7.1), (8.9, 11.0, 8.9), "wave_wrap")
    s.box("grip", (6.95, 11.0, 6.95), (9.05, 20.0, 9.05), "wave_wrap")
    s.box("grip_band_low", (6.6, 11.6, 6.6), (9.4, 12.4, 9.4), "sea_bronze")
    s.box("grip_band_high", (6.6, 18.6, 6.6), (9.4, 19.4, 9.4), "sea_bronze")
    s.box("shaft_upper", (7.1, 20.0, 7.1), (8.9, 30.0, 8.9), "wave_wrap")
    s.box("collar_high", (6.4, 30.0, 6.4), (9.6, 32.6, 9.6), "sea_bronze")
    s.box("collar_gem", (7.35, 30.8, 9.6), (8.65, 32.0, 9.95), "sea_gem")
    s.box("collar_gem_back", (7.35, 30.8, 6.05), (8.65, 32.0, 6.4), "sea_gem")
    # head hub with a little sea-dragon emblem
    s.box("head_hub", (6.7, 32.6, 6.9), (9.3, 36.2, 9.1), "bronze_bright")
    s.box("emblem_front", (7.2, 33.4, 9.1), (8.8, 35.2, 9.5), "teal_patina")
    s.box("emblem_back", (7.2, 33.4, 6.5), (8.8, 35.2, 6.9), "teal_patina")
    s.box("emblem_eye", (7.55, 34.2, 9.5), (8.45, 34.8, 9.7), "sea_gem")
    # three prongs: straight centre, two curved outer arms (they swing out and sweep back up)
    s.box("prong_center", (7.2, 36.2, 7.35), (8.8, 46.0, 8.65), "bronze_bright")
    s.box("prong_center_tip", (7.55, 46.0, 7.6), (8.45, 50.0, 8.4), "bronze_bright")
    s.box("prong_center_barb_l", (6.3, 43.4, 7.6), (7.3, 46.4, 8.4), "bronze_bright", rot=((7.3, 43.4, 8), "z", 22))
    s.box("prong_center_barb_r", (8.7, 43.4, 7.6), (9.7, 46.4, 8.4), "bronze_bright", rot=((8.7, 43.4, 8), "z", -22))
    s.chain_z("prong_left", (7.2, 36.6), 86, 4, 3.4, 2.1, 1.3, 1.25, 8, -29, "bronze_bright", mat_last="bronze_bright")
    s.chain_z("prong_right", (8.8, 36.6), -86, 4, 3.4, 2.1, 1.3, 1.25, 8, 29, "bronze_bright", mat_last="bronze_bright")
    # sea-dragon fins fanned around the head
    s.box("fin_left_1", (2.6, 36.6, 7.55), (6.9, 40.8, 8.45), "teal_patina", rot=((6.9, 36.6, 8), "z", 36))
    s.box("fin_left_2", (3.4, 32.2, 7.6), (7.0, 36.0, 8.4), "teal_patina", rot=((7.0, 32.2, 8), "z", 54))
    s.box("fin_left_3", (4.6, 28.6, 7.65), (7.0, 31.8, 8.35), "teal_patina", rot=((7.0, 28.6, 8), "z", 70))
    s.box("fin_right_1", (9.1, 36.6, 7.55), (13.4, 40.8, 8.45), "teal_patina", rot=((9.1, 36.6, 8), "z", -36))
    s.box("fin_right_2", (9.0, 32.2, 7.6), (12.6, 36.0, 8.4), "teal_patina", rot=((9.0, 32.2, 8), "z", -54))
    s.box("fin_right_3", (9.0, 28.6, 7.65), (11.4, 31.8, 8.35), "teal_patina", rot=((9.0, 28.6, 8), "z", -70))
    return s


# =====================================================================================================
# 12. SCULK CANNON - sculk-themed crossbow, teal veins, dark body
# =====================================================================================================
def sculk_cannon_parts(s, state):
    """state: 'idle', 0/1/2 (pulling), 'charged'.
    Heavy sci-fi crossbow (concept art): bullpup thumbhole stock, pistol grip, slotted receiver with a rail and a glass reflex sight, compound
    limbs with cams, a foot stirrup in front. SCULK THEME: near-black body, glowing teal vein slots, sensor clusters, teal cams."""
    s.default_mat = "sculk_wood"
    s.scale = 0.5
    # (the rear stock was removed at the owner's request so the weapon can be held level and straight from the hand without the arm going through it)
    # pistol grip + trigger
    s.box("grip", (7.1, 0.6, 8.0), (8.9, 1.9, 10.4), "black_wrap", rot=((8, 5.8, 9.2), "x", -10))        # lowest part of the handle: this is where the palm closes
    s.box("grip_upper", (7.1, 1.9, 8.0), (8.9, 5.8, 10.4), "black_wrap", rot=((8, 5.8, 9.2), "x", -10))
    s.box("trigger_guard", (7.3, 3.4, 10.6), (8.7, 4.0, 13.6), "vein_metal")
    s.box("trigger", (7.6, 4.0, 11.2), (8.4, 5.8, 11.8), "vein_glow")
    # receiver: slotted body with glowing vein slots on both sides, a top rail and the reflex sight
    s.box("receiver", (6.4, 5.8, 8.0), (9.6, 10.6, 24.0), "sculk_wood")
    for i, z in enumerate((10.0, 12.4, 14.8, 17.2, 19.6, 22.0), 1):
        s.box("slot_left_%d" % i, (6.15, 7.0, z), (6.45, 9.6, z + 1.0), "vein_glow")
        s.box("slot_right_%d" % i, (9.55, 7.0, z), (9.85, 9.6, z + 1.0), "vein_glow")
    s.box("rail", (7.3, 10.6, 8.0), (8.7, 11.4, 24.0), "vein_metal")
    s.box("sight_base", (7.0, 11.4, 12.0), (9.0, 12.2, 15.2), "vein_metal")
    s.box("sight_glass", (7.2, 12.2, 12.2), (8.8, 14.2, 12.9), "sensor_cluster")
    s.box("sight_post", (7.2, 12.2, 14.4), (8.8, 13.0, 15.0), "vein_metal")
    # sculk sensor clusters growing on the body
    s.box("sensor_cluster", (5.5, 8.6, 15.4), (6.4, 10.6, 17.8), "sensor_cluster")
    s.box("sensor_cluster_2", (9.6, 5.4, 18.4), (10.5, 7.6, 20.6), "sensor_cluster")
    # front: prod hub + foot stirrup
    s.box("prod_hub", (4.6, 5.4, 24.0), (11.4, 10.8, 27.0), "sculk_wood")
    s.box("hub_vein", (6.6, 6.4, 27.0), (9.4, 9.6, 27.4), "vein_glow")
    s.box("stirrup_left", (5.0, 2.6, 25.4), (6.2, 6.0, 26.8), "vein_metal")
    s.box("stirrup_right", (9.8, 2.6, 25.4), (11.0, 6.0, 26.8), "vein_metal")
    s.box("stirrup_bottom", (5.0, 2.0, 25.4), (11.0, 3.0, 26.8), "vein_metal")
    # compound limbs sweeping out and forward, ending in glowing cams
    tips = {}
    for side, sgn in (("left", 1), ("right", -1)):
        x0 = 11.4 if sgn == 1 else 4.6
        z = 25.4
        a = 0.0
        for i in range(6):
            seg = 3.0
            w = 2.8 - 0.34 * i
            if sgn == 1:
                s.box(f"limb_{side}_{i + 1}", (x0, 6.4, z - w / 2), (x0 + seg, 9.4, z + w / 2), "sculk_wood" if i < 5 else "vein_glow",
                      rot=((x0, 7.9, z), "y", -a))
                x0 += seg * math.cos(math.radians(a)) * 0.98
                z += seg * math.sin(math.radians(a)) * 0.98
            else:
                s.box(f"limb_{side}_{i + 1}", (x0 - seg, 6.4, z - w / 2), (x0, 9.4, z + w / 2), "sculk_wood" if i < 5 else "vein_glow",
                      rot=((x0, 7.9, z), "y", a))
                x0 -= seg * math.cos(math.radians(a)) * 0.98
                z += seg * math.sin(math.radians(a)) * 0.98
            a += 14
        tips[side] = (x0, z)
        s.box(f"cam_{side}", (x0 - 1.2, 6.9, z - 1.2), (x0 + 1.2, 8.9, z + 1.2), "vein_glow")
    tipx = tips["left"][0]
    tipz = tips["left"][1]
    pull = {"idle": 0.0, 0: 0.0, 1: 14.0, 2: 18.0, "charged": 22.0}[state]       # the longer limbs reach further forward, so the string is drawn back further
    nockz = tipz - pull if pull > 0 else tipz
    if pull == 0:
        s.box("string", (16 - tipx, 8.0, tipz - 0.15), (tipx, 8.3, tipz + 0.15), "cannon_string")
    else:
        ang = math.degrees(math.atan2(tipz - nockz, tipx - 8))
        ln = math.hypot(tipx - 8, tipz - nockz)
        s.box("string_left", (8, 8.0, nockz - 0.15), (8 + ln, 8.3, nockz + 0.15), "cannon_string", rot=((8, 8.1, nockz), "y", -ang))
        s.box("string_right", (8 - ln, 8.0, nockz - 0.15), (8, 8.3, nockz + 0.15), "cannon_string", rot=((8, 8.1, nockz), "y", ang))
    if state == "charged" or state in (1, 2):
        s.box("bolt_shaft", (7.88, 11.4, nockz - 0.2), (8.12, 12.0, 24.0), "arrow_wood")
        s.box("bolt_head", (7.7, 11.2, 24.0), (8.3, 12.2, 27.6), "arrow_head")
        s.box("bolt_fletch", (7.6, 11.2, nockz - 0.4), (8.4, 12.4, nockz + 1.6), "arrow_fletch")


def sculk_cannon():
    s = Spec("sculk_cannon", "crossbow", group="sculk_cannon")
    sculk_cannon_parts(s, "idle")
    return s


def sculk_cannon_variant(state):
    nm = "sculk_cannon_charged" if state == "charged" else f"sculk_cannon_pulling_{state}"
    s = Spec(nm, "crossbow", texture="sculk_cannon", group="sculk_cannon")
    sculk_cannon_parts(s, state)
    return s


# =====================================================================================================
# 13. "STRANGE" CAPE - mysterious, odd-looking cape (as an item: a hanging cloak with a glowing clasp)
# =====================================================================================================
def strange_cape():
    s = Spec("strange_cape", "cape")
    s.default_mat = "cape_cloth"
    s.box("yoke", (2.5, 13, 5.6), (13.5, 16, 9.0), "cape_cloth")
    heights = [14, 11.5, 13, 9.5, 15.5, 10.5, 12.5]
    n = len(heights)
    for i, h in enumerate(heights):
        x1 = 3 + i * (10.0 / n)
        s.box(f"drape_{i + 1}", (x1, 13 - h, 6.0 + (0.3 if i % 2 else 0.0)), (x1 + 10.0 / n + 0.05, 13, 8.6 + (0.3 if i % 2 else 0.0)), "cape_cloth")
    s.box("clasp_ring", (6.4, 12.9, 4.8), (9.6, 15.9, 5.8), "cape_clasp")
    s.box("clasp_eye", (7.1, 13.6, 4.5), (8.9, 15.2, 4.9), "strange_eye")
    s.box("hood_fold", (4.2, 16, 6.2), (11.8, 18, 9.6), "cape_cloth_dark")
    s.box("star_speck_1", (1.0, 8, 7), (1.9, 8.9, 7.9), "star_speck")
    s.box("star_speck_2", (14.0, 4, 6.5), (14.8, 4.8, 7.3), "star_speck")
    s.box("star_speck_3", (7.0, -1.2, 7.0), (7.8, -0.4, 7.8), "star_speck")
    s.box("grip", (6.8, 12.6, 5.6), (9.2, 13.4, 6.4), "cape_clasp")  # where you'd hold it
    return s


# =====================================================================================================
# 14. SILVER'S SPAWN ORB - frosty ice-blue orb with a horse motif
# =====================================================================================================
def silvers_spawn_orb(state="stored"):
    """Silver's Spawn Orb as a magic LAMP (concept art: ornate footed lamp with a lid, a long curving spout, a ring handle, and a swirl of
    spirit smoke). Three looks, picked by the orb's state (SilversSpawnOrbItem.setData):
       stored   = frosty silver-and-ice lamp, spirit smoke curling out with a glowing horse head in it (Silver is inside)
       released = dark steel lamp, empty (Silver is out in the world)
       cooldown = pale, chalky lamp with a faint ghost-smoke wisp (Silver fell / is recharging)"""
    name = "silvers_spawn_orb" if state == "stored" else "silvers_spawn_orb_" + state
    s = Spec(name, "orb")      # own texture per state: the three looks use different materials on the same UV layout
    s.scale = 0.72
    body, metal, trim, smoke, plate = {
        "stored": ("orb_ice", "silver", "ice_rim", None, None),        # owner: no smoke / energy coming out of the lamp
        "released": ("orb_dark", "silver", "silver", None, None),
        "cooldown": ("orb_pale", "silver", "silver", None, None),
    }[state]
    s.default_mat = body
    # footed base + stem
    s.box("foot", (4.8, 0.0, 5.8), (11.2, 1.2, 10.2), metal)
    s.box("foot_rim", (5.4, 1.2, 6.2), (10.6, 1.8, 9.8), trim)
    s.box("stem", (6.6, 1.8, 7.0), (9.4, 3.8, 9.0), metal)
    # the bowl (wide, lens-shaped) - 'grip' is the part the palm holds
    s.box("bowl_low", (4.2, 3.8, 5.8), (11.8, 5.6, 10.2), body)
    s.box("bowl_mid", (3.0, 5.6, 5.4), (13.0, 8.0, 10.6), body)
    s.box("bowl_up", (4.0, 8.0, 5.8), (12.0, 9.2, 10.2), body)
    s.box("bowl_band", (3.2, 5.2, 5.2), (12.8, 5.9, 10.8), trim)
    # engraved swirl on both faces (a little curl of trim)
    s.box("swirl_front_a", (5.2, 6.2, 10.6), (9.6, 6.9, 10.9), trim)
    s.box("swirl_front_b", (8.8, 6.9, 10.6), (9.5, 8.0, 10.9), trim)
    s.box("swirl_front_c", (6.4, 7.5, 10.6), (9.5, 8.1, 10.9), trim)
    s.box("swirl_back_a", (6.4, 6.2, 5.1), (10.8, 6.9, 5.4), trim)
    s.box("swirl_back_b", (6.5, 6.9, 5.1), (7.2, 8.0, 5.4), trim)
    # ring handle on the -X side
    s.box("handle_top", (1.2, 7.2, 7.4), (3.2, 8.2, 8.6), metal)
    s.box("grip", (0.4, 5.2, 7.3), (1.7, 8.2, 8.7), metal)          # the hand holds the ring handle, the lamp hangs forward from it
    s.box("handle_bottom", (1.2, 4.6, 7.4), (3.6, 5.6, 8.6), metal)
    # long curving spout on the +X side
    s.chain_z("spout", (12.6, 6.0), -62, 3, 1.9, 2.2, 1.2, 1.5, 8, 16, metal, mat_last=trim)
    # neck, lid dome and finial
    s.box("neck", (5.4, 9.2, 6.2), (10.6, 10.6, 9.8), metal)
    s.box("lid", (6.0, 10.6, 6.6), (10.0, 12.4, 9.4), metal)
    s.box("lid_band", (5.8, 10.6, 6.4), (10.2, 11.1, 9.6), trim)
    s.box("finial", (7.3, 12.4, 7.3), (8.7, 14.0, 8.7), trim)
    s.box("finial_tip", (7.65, 14.0, 7.65), (8.35, 15.2, 8.35), metal)
    if smoke:
        # spirit smoke curling out of the spout and spiralling up (stored: bright ice swirl; cooldown: faint wisp)
        small = state == "cooldown"
        s.box("smoke_1", (13.4, 9.0, 7.4), (15.4, 11.0, 8.6), smoke)
        s.box("smoke_2", (14.4, 11.0, 7.5), (16.4, 12.8, 8.5), smoke)
        s.box("smoke_3", (13.4, 12.6, 7.5), (15.6, 14.0, 8.5), smoke)
        if not small:
            s.box("smoke_4", (11.0, 13.4, 7.5), (13.6, 14.8, 8.5), smoke)
            s.box("smoke_5", (9.8, 11.8, 7.6), (11.4, 14.2, 8.4), smoke)
            s.box("smoke_6", (10.8, 10.4, 7.6), (12.2, 12.2, 8.4), smoke)
    if plate:
        # the horse spirit in the smoke: a small plate showing the horse-head silhouette on both sides
        s.box("horse_front", (11.2, 13.6, 7.2), (16.4, 18.8, 8.0), plate, skip=("down", "up", "east", "west", "north"))
        s.box("horse_back", (11.2, 13.6, 8.0), (16.4, 18.8, 8.8), plate, skip=("down", "up", "east", "west", "south"))
    return s


# =====================================================================================================
# THE SHRINE BLOCK  (three states share one geometry; only the texture differs)
# =====================================================================================================
def shrine(state):
    s = Spec(f"shrine_{state}", "block", texture=f"shrine_{state}", group="shrine")
    s.default_mat = "shrine_stone"
    s.state = state
    s.box("base_plate", (0, 0, 0), (16, 2, 16), "shrine_stone")
    s.box("base_step", (2, 2, 2), (14, 4, 14), "shrine_stone_light")
    s.box("pillar", (5, 4, 5), (11, 12, 11), "shrine_stone")
    s.box("pillar_collar_low", (4, 4, 4), (12, 5.5, 12), "shrine_stone_light")
    s.box("pillar_collar_high", (4, 10.5, 4), (12, 12, 12), "shrine_stone_light")
    s.box("pillar_rune_north", (6, 6, 4.4), (10, 10, 5), "shrine_rune", skip=("up", "down", "east", "west", "south"))
    s.box("pillar_rune_south", (6, 6, 11), (10, 10, 11.6), "shrine_rune", skip=("up", "down", "east", "west", "north"))
    s.box("pillar_rune_east", (11, 6, 6), (11.6, 10, 10), "shrine_rune", skip=("up", "down", "north", "south", "west"))
    s.box("pillar_rune_west", (4.4, 6, 6), (5, 10, 10), "shrine_rune", skip=("up", "down", "north", "south", "east"))
    s.box("basin_floor", (2, 12, 2), (14, 14, 14), "shrine_stone")
    s.box("basin_rim_north", (2, 14, 2), (14, 16, 4), "shrine_stone_light")
    s.box("basin_rim_south", (2, 14, 12), (14, 16, 14), "shrine_stone_light")
    s.box("basin_rim_east", (12, 14, 4), (14, 16, 12), "shrine_stone_light")
    s.box("basin_rim_west", (2, 14, 4), (4, 16, 12), "shrine_stone_light")
    s.box("basin_glow", (4, 14, 4), (12, 14.6, 12), "shrine_glow")
    s.box("crystal", (6.8, 14.6, 6.8), (9.2, 17.2, 9.2), "shrine_crystal")
    s.box("crystal_tip", (7.4, 17.2, 7.4), (8.6, 18.6, 8.6), "shrine_crystal")
    for nm, (x, z) in {"pylon_nw": (0.6, 0.6), "pylon_ne": (13.0, 0.6), "pylon_sw": (0.6, 13.0), "pylon_se": (13.0, 13.0)}.items():
        s.box(nm, (x, 2, z), (x + 2.4, 7, z + 2.4), "shrine_stone_light")
        s.box(nm + "_cap", (x - 0.2, 7, z - 0.2), (x + 2.6, 7.8, z + 2.6), "shrine_stone")
        s.box(nm + "_gem", (x + 0.6, 7.8, z + 0.6), (x + 1.8, 9.2, z + 1.8), "shrine_crystal")
    return s


def all_specs():
    out = [
        molten_greataxe(), cutlass_of_doom(), sculk_mace(),
        frost_longbow(), frost_longbow_pulling(0), frost_longbow_pulling(1), frost_longbow_pulling(2),
        cloud_lance(),
        shrine_helmet(), shrine_chestplate(), shrine_leggings(), shrine_boots(),
        harbinger_of_death(), anaklusmos(),
        sculk_cannon(), sculk_cannon_variant(0), sculk_cannon_variant(1), sculk_cannon_variant(2), sculk_cannon_variant("charged"),
        strange_cape(), silvers_spawn_orb(), silvers_spawn_orb("released"), silvers_spawn_orb("cooldown"),
        shrine("unconfigured"), shrine("configured"), shrine("claimed"),
    ]
    return out
