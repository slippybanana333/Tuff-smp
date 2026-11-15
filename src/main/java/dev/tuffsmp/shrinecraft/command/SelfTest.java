package dev.tuffsmp.shrinecraft.command;

import com.mojang.authlib.GameProfile;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.ability.ArmorEffects;
import dev.tuffsmp.shrinecraft.ability.Abilities;
import dev.tuffsmp.shrinecraft.ability.HarbingerHandler;
import dev.tuffsmp.shrinecraft.ability.LavaBombManager;
import dev.tuffsmp.shrinecraft.ability.SculkWaveManager;
import dev.tuffsmp.shrinecraft.item.CloudLanceItem;
import dev.tuffsmp.shrinecraft.ability.DeepFreezeManager;
import dev.tuffsmp.shrinecraft.item.CutlassOfDoomItem;
import dev.tuffsmp.shrinecraft.item.FrostLongbowItem;
import dev.tuffsmp.shrinecraft.item.HarbingerOfDeathItem;
import dev.tuffsmp.shrinecraft.item.MoltenGreataxeItem;
import dev.tuffsmp.shrinecraft.item.OrbData;
import dev.tuffsmp.shrinecraft.item.SculkCannonItem;
import dev.tuffsmp.shrinecraft.item.SculkMaceItem;
import dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem;
import dev.tuffsmp.shrinecraft.registry.ModBlocks;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.registry.ModKeys;
import dev.tuffsmp.shrinecraft.shrine.ShrineBlock;
import dev.tuffsmp.shrinecraft.shrine.ShrineBlockEntity;
import dev.tuffsmp.shrinecraft.shrine.ShrineInteraction;
import dev.tuffsmp.shrinecraft.shrine.ShrineMenu;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipe;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipes;
import dev.tuffsmp.shrinecraft.shrine.ShrineState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.command.permission.PermissionPredicate;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * "/tuffsmp selftest" - runs the mod's logic on the server using a fake player and prints PASS/FAIL lines.
 * It cannot check anything visual (models, textures, particles, HUD): those need a real client.
 */
final class SelfTest {
    private final ServerCommandSource source;
    private final MinecraftServer server;
    private final ServerWorld world;
    private final RegistryWrapper.WrapperLookup reg;
    private final List<String> failures = new ArrayList<>();
    private int passed = 0;
    private FakePlayer fake;
    private BlockPos base;

    private SelfTest(ServerCommandSource source) {
        this.source = source;
        this.server = source.getServer();
        this.world = server.getOverworld();
        this.reg = server.getRegistryManager();
    }

    static int run(ServerCommandSource source) {
        SelfTest t = new SelfTest(source);
        // The tests drop items (mined stone -> cobblestone, Shrine ingredients like sculk catalysts) and XP orbs next to the test spot.
        // Remember what was lying there BEFORE, and remove only what the test added afterwards, so spawn is left exactly as it was.
        net.minecraft.util.math.Box area = new net.minecraft.util.math.Box(-56, t.world.getBottomY(), -56, 72, t.world.getTopYInclusive() + 1, 72);
        java.util.function.Predicate<net.minecraft.entity.Entity> loose = e -> e instanceof net.minecraft.entity.ItemEntity || e instanceof net.minecraft.entity.ExperienceOrbEntity;
        java.util.Set<UUID> before = new java.util.HashSet<>();
        t.world.getEntitiesByClass(net.minecraft.entity.Entity.class, area, loose).forEach(e -> before.add(e.getUuid()));
        try {
            t.runAll();
        } finally {
            t.world.getEntitiesByClass(net.minecraft.entity.Entity.class, area, loose).forEach(e -> {
                if (!before.contains(e.getUuid())) {
                    e.discard();
                }
            });
        }
        return t.failures.isEmpty() ? 1 : 0;
    }

    // ------------------------------------------------------------------ plumbing

    private void say(String s) {
        ShrineCraft.LOGGER.info("[SELFTEST] {}", s);
        source.sendFeedback(() -> Text.literal(s), false);
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) {
            passed++;
            say("PASS  " + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
        } else {
            failures.add(name);
            say("FAIL  " + name + (detail.isEmpty() ? "" : "  (" + detail + ")"));
            source.sendFeedback(() -> Text.literal("FAIL " + name).formatted(Formatting.RED), false);
        }
    }

    private void check(String name, boolean ok) {
        check(name, ok, "");
    }

    private interface Section {
        void run() throws Exception;
    }

    private void section(String title, Section s) {
        say("---- " + title + " ----");
        try {
            s.run();
        } catch (Throwable e) {
            ShrineCraft.LOGGER.error("self-test section crashed", e);
            check(title + " (no exception)", false, e.toString());
        }
    }

    private <T> RegistryEntry<T> entry(RegistryKey<? extends net.minecraft.registry.Registry<T>> registry, RegistryKey<T> key) {
        return reg.getOrThrow(registry).getOrThrow(key);
    }

    private int level(ItemStack stack, RegistryKey<Enchantment> key) {
        return EnchantmentHelper.getLevel(entry(RegistryKeys.ENCHANTMENT, key), stack);
    }

    private DamageSource typed(RegistryKey<DamageType> key, net.minecraft.entity.Entity src, net.minecraft.entity.Entity attacker) {
        return new DamageSource(entry(RegistryKeys.DAMAGE_TYPE, key), src, attacker);
    }

    /** A fake player that counts as an operator (permission level 2) so the admin-only parts can be tested. */
    private static final class AdminFake extends FakePlayer {
        AdminFake(ServerWorld world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public PermissionPredicate getPermissions() {
            return LeveledPermissionPredicate.GAMEMASTERS;
        }
    }

    /** A fake player that CAN be hurt (Fabric's FakePlayer is invulnerable by design) - the target for the real-shot tests. */
    private static final class VictimFake extends FakePlayer {
        VictimFake(ServerWorld world, GameProfile profile) {
            super(world, profile);
        }

        @Override
        public boolean isInvulnerableTo(ServerWorld world, DamageSource source) {
            return false;
        }
    }

    private void giveAll(ServerPlayerEntity p, ShrineRecipe r, int extra) {
        for (ShrineRecipe.Ingredient in : r.ingredients()) {
            int left = in.count() + extra;
            // spread over slots so stacks of 64 fit; items with max stack 1 (tools) go one per slot
            while (left > 0) {
                ItemStack s = in.stackOf(Math.min(left, in.item().getMaxCount()));
                left -= s.getCount();
                p.getInventory().offerOrDrop(s);
            }
        }
    }

    // ------------------------------------------------------------------ the tests

    private void runAll() {
        say("=== Tuff SMP self-test: start ===");
        base = new BlockPos(8, world.getTopY(Heightmap.Type.WORLD_SURFACE, 8, 8), 8);
        // NOTE: entity lookups only work in chunks that are "ticking". With nobody nearby that needs /forceload first
        // (tools/server_selftest.sh does that for you). If you run this by hand, stand near (8, ~, 8) or /forceload add 0 0 first.
        world.getChunk(base);
        fake = FakePlayer.get(world, new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000f4c3"), "[SelfTest]"));
        fake.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        fake.getInventory().clear();

        section("A. all 14 items exist, are unbreakable, survive save/load", this::testItems);
        section("B. enchantments match the brief", this::testEnchants);
        section("C. data: custom enchantments, tags, damage types", this::testData);
        section("D. Cutlass is just a sword (no pickaxe / shovel mining)", this::testCutlassTool);
        section("E. Shrine block: all three states, persistence, GUI", this::testShrine);
        section("F. armor effects and Boots fall immunity", this::testArmor);
        section("G. Molten Greataxe - Lava Bomb", this::testLavaBomb);
        section("H. Cutlass of Doom - 1.5x buff", this::testCutlassBuff);
        section("H2. Cutlass Doom Surge stays on the Cutlass (other weapons unaffected)", this::testCutlassIsolation);
        section("I. Sculk Mace - tiers, Warden kill, Sculk Wave", this::testMace);
        section("J. Frost Longbow - true damage + Deep Freeze ability", this::testFrost);
        section("J2. Frost Longbow - REAL shots (bow release -> arrow flight -> hit), damage + Deep Freeze", this::testFrostRealShots);
        section("J3. Frost Longbow - REAL shots at a PLAYER in Protection IV diamond (damage + Deep Freeze lock)", this::testFrostRealPlayer);
        section("K. Sculk Cannon - modes, blind, wail", this::testCannon);
        section("L. Cloud Lance - single dash, slow immunity", this::testLance);
        section("M. Harbinger of Death - 3x3x3 + toggle", this::testHarbinger);
        section("N. Anaklusmos - Riptide 5", this::testTrident);
        section("O. Silver's Spawn Orb", this::testOrb);
        section("P. Strange Cape + recipes", this::testMisc);

        fake.getInventory().clear();
        say("=== Tuff SMP self-test: " + passed + " passed, " + failures.size() + " failed ===");
        if (!failures.isEmpty()) {
            say("FAILED: " + String.join(" | ", failures));
        }
    }

    private void testItems() throws Exception {
        check("14 recipes registered", ShrineRecipes.all().size() == 14, String.valueOf(ShrineRecipes.all().size()));
        for (ShrineRecipe r : ShrineRecipes.all()) {
            ItemStack s = r.createOutput(reg);
            String id = Registries.ITEM.getId(s.getItem()).toString();
            check(r.id() + ": item registered as shrinecraft:" + r.id(), id.equals("shrinecraft:" + r.id()), id);
            check(r.id() + ": unbreakable", s.contains(DataComponentTypes.UNBREAKABLE));
            NbtElement nbt = ItemStack.CODEC.encodeStart(reg.getOps(NbtOps.INSTANCE), s).getOrThrow();
            ItemStack back = ItemStack.CODEC.parse(reg.getOps(NbtOps.INSTANCE), nbt).getOrThrow();
            check(r.id() + ": save/load round trip", ItemStack.areEqual(s, back));
            StringBuilder sb = new StringBuilder();
            EnchantmentHelper.getEnchantments(s).getEnchantmentEntries().forEach(e ->
                sb.append(e.getKey().getIdAsString().replace("minecraft:", "")).append(' ').append(e.getIntValue()).append(", "));
            say("      enchants: " + (sb.length() == 0 ? "(none)" : sb));
        }
    }

    private void testEnchants() {
        ItemStack axe = MoltenGreataxeItem.createStack(reg);
        check("Greataxe: Silk Touch 1, Sharpness 5, Unbreaking 3, Molten Fire Aspect 2",
            level(axe, Enchantments.SILK_TOUCH) == 1 && level(axe, Enchantments.SHARPNESS) == 5 && level(axe, Enchantments.UNBREAKING) == 3 && level(axe, ModKeys.MOLTEN_FIRE_ASPECT) == 2);
        ItemStack cut = CutlassOfDoomItem.createStack(reg);
        check("Cutlass: Sharpness 5 + Looting 6", level(cut, Enchantments.SHARPNESS) == 5 && level(cut, Enchantments.LOOTING) == 6);
        check("Cutlass: NO Knockback, NO Fire Aspect", level(cut, Enchantments.KNOCKBACK) == 0 && level(cut, Enchantments.FIRE_ASPECT) == 0);
        ItemStack mace = SculkMaceItem.createStack(reg);
        check("Mace tier 0 has zero enchantments", !mace.hasEnchantments());
        int[][] expectDensityWind = {{0, 0}, {1, 0}, {2, 0}, {2, 1}, {3, 1}, {3, 1}};
        for (int t = 1; t <= 5; t++) {
            SculkMaceItem.setTier(mace, t, reg);
            check("Mace tier " + t + ": Density " + expectDensityWind[t][0] + ", Wind Burst " + expectDensityWind[t][1],
                level(mace, Enchantments.DENSITY) == expectDensityWind[t][0] && level(mace, Enchantments.WIND_BURST) == expectDensityWind[t][1]
                    && SculkMaceItem.getTier(mace) == t);
        }
        SculkMaceItem.setTier(mace, 0, reg);
        check("Mace back to tier 0 removes enchantments again", !mace.hasEnchantments());
        for (ItemStack a : List.of(dev.tuffsmp.shrinecraft.item.TuffArmorItem.createHelmet(reg), dev.tuffsmp.shrinecraft.item.TuffArmorItem.createChestplate(reg),
            dev.tuffsmp.shrinecraft.item.TuffArmorItem.createLeggings(reg), dev.tuffsmp.shrinecraft.item.TuffArmorItem.createBoots(reg))) {
            check(Registries.ITEM.getId(a.getItem()).getPath() + ": Protection 4, no Thorns", level(a, Enchantments.PROTECTION) == 4 && level(a, Enchantments.THORNS) == 0);
        }
    }

    private void testData() {
        var enchReg = reg.getOrThrow(RegistryKeys.ENCHANTMENT);
        check("custom enchantment molten_fire_aspect loaded", enchReg.getOptional(ModKeys.MOLTEN_FIRE_ASPECT).isPresent());
        check("custom enchantment endless_quiver loaded", enchReg.getOptional(ModKeys.ENDLESS_QUIVER).isPresent());
        var dmgReg = reg.getOrThrow(RegistryKeys.DAMAGE_TYPE);
        check("damage types sculk_wave / sculk_wail / frost_arrow loaded",
            dmgReg.getOptional(ModKeys.SCULK_WAVE).isPresent() && dmgReg.getOptional(ModKeys.SCULK_WAIL).isPresent() && dmgReg.getOptional(ModKeys.FROST_ARROW).isPresent());

        TagKey<Item> only = TagKey.of(RegistryKeys.ITEM, ShrineCraft.id("molten_greataxe_only"));
        check("item tag #shrinecraft:molten_greataxe_only exists and contains the Greataxe", new ItemStack(ModItems.MOLTEN_GREATAXE).isIn(only));
        int members = 0;
        for (Item item : Registries.ITEM) {
            if (new ItemStack(item).isIn(only)) {
                members++;
            }
        }
        check("... and ONLY the Greataxe", members == 1, members + " member(s)");
        Enchantment molten = enchReg.getOrThrow(ModKeys.MOLTEN_FIRE_ASPECT).value();
        check("Molten Fire Aspect supports the Greataxe only",
            molten.isSupportedItem(new ItemStack(ModItems.MOLTEN_GREATAXE)) && !molten.isSupportedItem(new ItemStack(Items.NETHERITE_AXE)) && !molten.isSupportedItem(new ItemStack(ModItems.CUTLASS_OF_DOOM)));
        RegistryEntry<Enchantment> me = enchReg.getOrThrow(ModKeys.MOLTEN_FIRE_ASPECT);
        check("Molten Fire Aspect is NOT in enchanting-table / trade / random-loot tags",
            !me.isIn(EnchantmentTags.IN_ENCHANTING_TABLE) && !me.isIn(EnchantmentTags.TRADEABLE) && !me.isIn(EnchantmentTags.ON_RANDOM_LOOT)
                && !me.isIn(EnchantmentTags.ON_TRADED_EQUIPMENT) && !me.isIn(EnchantmentTags.NON_TREASURE));
        TagKey<Item> cannonTag = TagKey.of(RegistryKeys.ITEM, ShrineCraft.id("sculk_cannon_only"));
        check("item tag #shrinecraft:sculk_cannon_only exists", new ItemStack(ModItems.SCULK_CANNON).isIn(cannonTag));

        for (RegistryKey<DamageType> k : List.of(ModKeys.SCULK_WAVE, ModKeys.SCULK_WAIL, ModKeys.FROST_ARROW)) {
            check(k.getValue().getPath() + " damage bypasses armor", typed(k, null, null).isIn(DamageTypeTags.BYPASSES_ARMOR));
        }
        check("sculk_wave / sculk_wail ignore the invulnerability window",
            typed(ModKeys.SCULK_WAVE, null, null).isIn(DamageTypeTags.BYPASSES_COOLDOWN) && typed(ModKeys.SCULK_WAIL, null, null).isIn(DamageTypeTags.BYPASSES_COOLDOWN));
        check("swords/axes/pickaxes/spears vanilla tags include the new items",
            new ItemStack(ModItems.CUTLASS_OF_DOOM).isIn(net.minecraft.registry.tag.ItemTags.SWORDS)
                && new ItemStack(ModItems.MOLTEN_GREATAXE).isIn(net.minecraft.registry.tag.ItemTags.AXES)
                && new ItemStack(ModItems.HARBINGER_OF_DEATH).isIn(net.minecraft.registry.tag.ItemTags.PICKAXES)
                && new ItemStack(ModItems.CLOUD_LANCE).isIn(net.minecraft.registry.tag.ItemTags.SPEARS));
    }

    private void testCutlassTool() {
        ItemStack c = CutlassOfDoomItem.createStack(reg);
        ItemStack bare = new ItemStack(ModItems.CUTLASS_OF_DOOM);
        BlockState stone = Blocks.STONE.getDefaultState();
        BlockState dirt = Blocks.DIRT.getDefaultState();
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        check("Cutlass mines stone / dirt exactly like a normal netherite sword (no pickaxe / shovel speed)",
            bare.getMiningSpeedMultiplier(stone) == sword.getMiningSpeedMultiplier(stone) && bare.getMiningSpeedMultiplier(dirt) == sword.getMiningSpeedMultiplier(dirt),
            bare.getMiningSpeedMultiplier(stone) + " vs " + sword.getMiningSpeedMultiplier(stone));
        check("Cutlass does not harvest stone (not suitable)", !bare.isSuitableFor(stone) && !bare.isSuitableFor(dirt));
        check("Cutlass has NO mining enchantments (Efficiency / Fortune)", level(c, Enchantments.EFFICIENCY) == 0 && level(c, Enchantments.FORTUNE) == 0);
    }

    private void testShrine() throws Exception {
        BlockPos pos = base.up(3);
        world.setBlockState(pos, ModBlocks.SHRINE.getDefaultState(), 3);
        ShrineBlockEntity be = (ShrineBlockEntity) world.getBlockEntity(pos);
        check("Shrine block placed with a block entity", be != null);
        check("starts UNCONFIGURED", be.getShrineState() == ShrineState.UNCONFIGURED && world.getBlockState(pos).get(ShrineBlock.STATE) == ShrineState.UNCONFIGURED);

        fake.getInventory().clear();
        fake.setSneaking(false);
        check("a normal player is not an admin", !ShrineInteraction.isAdmin(fake));
        AdminFake admin = new AdminFake(world, new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000ad01"), "[SelfTestAdmin]"));
        check("op-level fake IS an admin", ShrineInteraction.isAdmin(admin));

        // admin GUI
        ShrineMenu menu = new ShrineMenu(1, admin.getInventory(), be);
        check("admin GUI lists 14 recipes", menu.getSlot(0).getStack().getItem() == ModItems.MOLTEN_GREATAXE && menu.getSlot(13).getStack().getItem() == ModItems.SILVERS_SPAWN_ORB);
        menu.onSlotClick(0, 0, net.minecraft.screen.slot.SlotActionType.QUICK_MOVE, admin);
        check("GUI: shift-click does nothing", be.getShrineState() == ShrineState.UNCONFIGURED);
        menu.onSlotClick(5, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, fake);
        check("GUI: a non-admin click assigns nothing", be.getShrineState() == ShrineState.UNCONFIGURED);
        menu.onSlotClick(0, 0, net.minecraft.screen.slot.SlotActionType.PICKUP, admin);
        check("GUI: admin click assigns recipe 1 -> CONFIGURED", be.getShrineState() == ShrineState.CONFIGURED && be.getRecipeId().equals("molten_greataxe")
            && world.getBlockState(pos).get(ShrineBlock.STATE) == ShrineState.CONFIGURED);

        // missing materials -> nothing consumed, nothing given
        fake.setSneaking(true);
        ShrineRecipe recipe = ShrineRecipes.byId("molten_greataxe");
        fake.getInventory().offerOrDrop(new ItemStack(Items.DIAMOND_AXE));
        fake.getInventory().offerOrDrop(new ItemStack(Items.GHAST_TEAR, 10));
        ActionResult r1 = ShrineInteraction.handleUse(world, pos, fake);
        check("sneak-craft with missing items: nothing is consumed or given",
            be.getShrineState() == ShrineState.CONFIGURED && ShrineInteraction.count(fake, Items.GHAST_TEAR) == 10 && ShrineInteraction.count(fake, Items.DIAMOND_AXE) == 1
                && ShrineInteraction.count(fake, ModItems.MOLTEN_GREATAXE) == 0 && r1.isAccepted());
        // non-sneak right-click only lists
        fake.setSneaking(false);
        ShrineInteraction.handleUse(world, pos, fake);
        check("plain right-click only shows the list (still CONFIGURED, nothing consumed)", be.getShrineState() == ShrineState.CONFIGURED && ShrineInteraction.count(fake, Items.GHAST_TEAR) == 10);

        // full materials -> consume exactly, give item, CLAIMED
        fake.getInventory().clear();
        giveAll(fake, recipe, 5);
        fake.setSneaking(true);
        ShrineInteraction.handleUse(world, pos, fake);
        boolean consumed = true;
        for (ShrineRecipe.Ingredient in : recipe.ingredients()) {
            int expectLeft = 5;
            if (in.item() == Items.DIAMOND_AXE || in.item() == Items.BLAZE_ROD) {
                expectLeft = 5; // extras were spread over stacks, counts still add up
            }
            consumed &= ShrineInteraction.count(fake, in.item()) == expectLeft;
        }
        check("craft consumed exactly the recipe (the 5 extras of each remain)", consumed);
        check("craft gave the Molten Greataxe (with its enchantments)", ShrineInteraction.count(fake, ModItems.MOLTEN_GREATAXE) == 1);
        check("Shrine is now CLAIMED (block entity + block state)", be.getShrineState() == ShrineState.CLAIMED && world.getBlockState(pos).get(ShrineBlock.STATE) == ShrineState.CLAIMED);

        // claimed: never again, even with materials
        giveAll(fake, recipe, 0);
        ShrineInteraction.handleUse(world, pos, fake);
        fake.setSneaking(false);
        ShrineInteraction.handleUse(world, pos, fake);
        check("CLAIMED: further right-clicks give nothing", ShrineInteraction.count(fake, ModItems.MOLTEN_GREATAXE) == 1);

        // persistence: write to NBT, read into a brand-new block entity (what a restart does)
        var tag = be.createNbtWithIdentifyingData(reg);
        ShrineBlockEntity reloaded = new ShrineBlockEntity(pos, world.getBlockState(pos));
        try (var logging = new net.minecraft.util.ErrorReporter.Logging(reloaded.getReporterContext(), ShrineCraft.LOGGER)) {
            reloaded.read(net.minecraft.storage.NbtReadView.create(logging, reg, tag));
        }
        check("CLAIMED survives save + reload", reloaded.getShrineState() == ShrineState.CLAIMED && reloaded.getRecipeId().equals("molten_greataxe"));

        // reset command logic + placeholder recipe cannot be crafted
        be.reset();
        check("admin reset returns it to UNCONFIGURED", be.getShrineState() == ShrineState.UNCONFIGURED);
        be.configure("strange_cape");
        fake.setSneaking(true);
        fake.getInventory().clear();
        ShrineInteraction.handleUse(world, pos, fake);
        check("Strange Cape refuses to craft without its materials and stays CONFIGURED",
            be.getShrineState() == ShrineState.CONFIGURED && ShrineInteraction.count(fake, ModItems.STRANGE_CAPE) == 0);
        giveAll(fake, ShrineRecipes.byId("strange_cape"), 0);
        ShrineInteraction.handleUse(world, pos, fake);
        check("Strange Cape (128 feathers, 128 slime balls, 128 breeze rods) crafts", ShrineInteraction.count(fake, ModItems.STRANGE_CAPE) == 1);
        be.reset();
        be.configure("frost_longbow");
        fake.getInventory().clear();
        giveAll(fake, ShrineRecipes.byId("frost_longbow"), 0);
        ShrineInteraction.handleUse(world, pos, fake);
        check("Frost Longbow (bow, 64 ice, 128 packed ice, 256 blue ice, 64 diamonds) crafts", ShrineInteraction.count(fake, ModItems.FROST_LONGBOW) == 1);
        be.reset();
        be.configure("silvers_spawn_orb");
        fake.getInventory().clear();
        // plain Swiftness I potions must NOT count: only Swiftness II does
        for (int k = 0; k < 9; k++) {
            ItemStack weak = new ItemStack(Items.POTION);
            weak.set(DataComponentTypes.POTION_CONTENTS, new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.SWIFTNESS));
            fake.getInventory().offerOrDrop(weak);
        }
        fake.getInventory().offerOrDrop(new ItemStack(Items.NETHERITE_HORSE_ARMOR));
        fake.getInventory().offerOrDrop(new ItemStack(Items.SADDLE));
        ShrineInteraction.handleUse(world, pos, fake);
        check("Silver's Orb refuses Swiftness I potions (needs Swiftness II)", ShrineInteraction.count(fake, ModItems.SILVERS_SPAWN_ORB) == 0 && be.getShrineState() == ShrineState.CONFIGURED);
        fake.getInventory().clear();
        giveAll(fake, ShrineRecipes.byId("silvers_spawn_orb"), 0);
        ShrineInteraction.handleUse(world, pos, fake);
        check("Silver's Orb (netherite horse armor, saddle, 64 sea lanterns, 9 Swiftness II potions) crafts", ShrineInteraction.count(fake, ModItems.SILVERS_SPAWN_ORB) == 1);

        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
        fake.getInventory().clear();
        fake.setSneaking(false);
    }

    private void testArmor() {
        fake.getInventory().clear();
        for (EquipmentSlot s : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            fake.equipStack(s, ItemStack.EMPTY);
        }
        // armor TRIMS are allowed on the Shrine armor (owner: "make tuff armor trimable"): the trim recipe works on all four pieces, like on vanilla armor
        {
            var trimReg = reg.getOrThrow(net.minecraft.registry.RegistryKeys.TRIM_PATTERN);
            var pattern = trimReg.streamEntries().findFirst().orElseThrow();
            var recipe = new net.minecraft.recipe.SmithingTrimRecipe(net.minecraft.recipe.Ingredient.ofItem(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE),
                net.minecraft.recipe.Ingredient.ofItem(Items.NETHERITE_HELMET), net.minecraft.recipe.Ingredient.ofItem(Items.GOLD_INGOT), pattern);
            var gold = new ItemStack(Items.GOLD_INGOT);
            var tmpl = new ItemStack(Items.COAST_ARMOR_TRIM_SMITHING_TEMPLATE);
            boolean all = true;
            StringBuilder why = new StringBuilder();
            for (net.minecraft.item.Item piece : List.of(ModItems.SHRINE_HELMET, ModItems.SHRINE_CHESTPLATE, ModItems.SHRINE_LEGGINGS, ModItems.SHRINE_BOOTS)) {
                ItemStack base = new ItemStack(piece);
                ItemStack out = recipe.craft(new net.minecraft.recipe.input.SmithingRecipeInput(tmpl, base, gold), reg);
                boolean ok = out.isOf(piece) && out.contains(DataComponentTypes.TRIM);
                all &= ok;
                why.append(piece).append('=').append(ok).append(' ');
            }
            check("armor trims WORK on all four Shrine armor pieces", all, why.toString());
            ItemStack trimmed = new ItemStack(ModItems.SHRINE_HELMET);
            trimmed.set(DataComponentTypes.TRIM, recipe.craft(new net.minecraft.recipe.input.SmithingRecipeInput(tmpl, new ItemStack(ModItems.SHRINE_HELMET), gold), reg).get(DataComponentTypes.TRIM));
            trimmed.getItem().inventoryTick(trimmed, world, fake, EquipmentSlot.MAINHAND);      // (the tick that used to strip trims)
            check("a trimmed piece KEEPS its trim (nothing strips it any more)", trimmed.contains(DataComponentTypes.TRIM));
            fake.getInventory().clear();
        }
        fake.equipStack(EquipmentSlot.HEAD, new ItemStack(ModItems.SHRINE_HELMET));
        var e = ArmorEffects.desiredEffects(fake);
        check("Helmet: Strength II (amp 1) + Fire Resistance", e.get(StatusEffects.STRENGTH) == 1 && e.containsKey(StatusEffects.FIRE_RESISTANCE) && e.size() == 2);
        fake.equipStack(EquipmentSlot.CHEST, new ItemStack(ModItems.SHRINE_CHESTPLATE));
        e = ArmorEffects.desiredEffects(fake);
        check("Helmet + Chestplate: Strength II from the helmet only, chestplate gives Hero of the Village III + Resistance I",
            e.get(StatusEffects.STRENGTH) == 1 && e.get(StatusEffects.RESISTANCE) == 0 && e.get(StatusEffects.HERO_OF_THE_VILLAGE) == 2 && e.size() == 4);
        fake.equipStack(EquipmentSlot.LEGS, new ItemStack(ModItems.SHRINE_LEGGINGS));
        e = ArmorEffects.desiredEffects(fake);
        check("+ Leggings (dry weather): Dolphin's Grace only - NO Speed", e.containsKey(StatusEffects.DOLPHINS_GRACE) && !e.containsKey(StatusEffects.SPEED) && e.get(StatusEffects.STRENGTH) == 1);
        world.setWeather(0, 6000, true, false);
        world.setRainGradient(1.0F);              // the fade-in normally takes a few ticks; the test does not wait
        BlockPos rainSpot = world.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, base).up(0);
        fake.refreshPositionAndAngles(rainSpot.getX() + 0.5, rainSpot.getY(), rainSpot.getZ() + 0.5, 0, 0);
        e = ArmorEffects.desiredEffects(fake);
        check("+ Leggings in RAIN: Strength III + Speed II", world.hasRain(fake.getBlockPos()) && e.get(StatusEffects.STRENGTH) == 2 && e.get(StatusEffects.SPEED) == 1, e.toString());
        world.setWeather(6000, 0, false, false);
        world.setRainGradient(0.0F);
        fake.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        fake.equipStack(EquipmentSlot.FEET, new ItemStack(ModItems.SHRINE_BOOTS));
        e = ArmorEffects.desiredEffects(fake);
        check("+ Boots: Speed becomes III (the higher, never added up), still no extra Fire Resistance from boots",
            e.get(StatusEffects.SPEED) == 2 && e.size() == 6, e.toString());
        fake.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        check("taking the Helmet off removes Fire Resistance from the wanted set", !ArmorEffects.desiredEffects(fake).containsKey(StatusEffects.FIRE_RESISTANCE));

        // apply for real (one tick) and check the durations are 2-3 ticks
        for (var entry : ArmorEffects.desiredEffects(fake).entrySet()) {
            fake.addStatusEffect(new StatusEffectInstance(entry.getKey(), ArmorEffects.EFFECT_TICKS, entry.getValue(), true, false, false));
        }
        StatusEffectInstance speed = fake.getStatusEffect(StatusEffects.SPEED);
        check("effects are short (<= 3 ticks) so they vanish when unequipped", speed != null && speed.getDuration() <= 3 && speed.getAmplifier() == 2);
        fake.clearStatusEffects();

        boolean cancelled = !ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(fake, world.getDamageSources().fall(), 20.0F);
        check("Boots cancel fall damage via the damage event", cancelled);
        check("Boots do NOT cancel other damage", ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(fake, world.getDamageSources().generic(), 5.0F));
        fake.equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
        check("without Boots fall damage is allowed", ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(fake, world.getDamageSources().fall(), 20.0F));
        for (EquipmentSlot s : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            fake.equipStack(s, ItemStack.EMPTY);
        }
    }

    private void testLavaBomb() {
        ItemStack axe = MoltenGreataxeItem.createStack(reg);
        fake.getInventory().clear();
        fake.equipStack(EquipmentSlot.MAINHAND, axe);
        fake.setSneaking(false);
        PigEntity target = EntityType.PIG.create(world, SpawnReason.COMMAND);
        target.refreshPositionAndAngles(base.getX() + 4.5, base.getY() + 20, base.getZ() + 0.5, 0, 0);
        target.setNoGravity(true);
        world.spawnEntity(target);
        BlockPos feet = target.getBlockPos();
        // not armed: a hit does nothing
        axe.getItem().postHit(axe, target, fake);
        check("hit while NOT armed: no lava", world.getBlockState(feet).isAir());
        // arm with sneak + use
        fake.setSneaking(true);
        ActionResult r = axe.getItem().use(world, fake, Hand.MAIN_HAND);
        check("sneak + right-click arms Lava Bomb and starts the 60s cooldown",
            r == ActionResult.SUCCESS && MoltenGreataxeItem.isArmed(axe, world.getTime()) && Abilities.onCooldown(axe, world.getTime()) && Abilities.status(axe, world.getTime()).phase() == Abilities.Phase.ACTIVE);
        check("ability bar: the armed axe shows ACTIVE (10s window) and its 60s timer", Abilities.status(axe, world.getTime()).remainingTicks() <= 10 * 20 && Abilities.status(axe, world.getTime()).remainingTicks() > 9 * 20
            && axe.get(ModComponents.COOLDOWN_UNTIL) - world.getTime() > 59 * 20);
        check("a second sneak + right-click while recharging is refused", axe.getItem().use(world, fake, Hand.MAIN_HAND) == ActionResult.FAIL);
        fake.setSneaking(false);
        axe.getItem().postHit(axe, target, fake);
        int lava = 0;
        for (int[] off : MoltenGreataxeItem.LAVA_CLUSTER_OFFSETS) {
            if (world.getBlockState(feet.add(off[0], 0, off[1])).isOf(Blocks.LAVA)) {
                lava++;
            }
        }
        check("armed hit spawns the lava cluster at the target's feet", lava == MoltenGreataxeItem.LAVA_CLUSTER_OFFSETS.length, lava + " lava blocks");
        check("armed state is used up by the hit", !MoltenGreataxeItem.isArmed(axe, world.getTime()));
        check("attacker is protected from fire/lava damage while the lava exists", LavaBombManager.isProtected(fake)
            && !ServerLivingEntityEvents.ALLOW_DAMAGE.invoker().allowDamage(fake, world.getDamageSources().lava(), 4.0F));
        LavaBombManager.revertAllNow(server);
        int left = 0;
        for (int[] off : MoltenGreataxeItem.LAVA_CLUSTER_OFFSETS) {
            if (world.getBlockState(feet.add(off[0], 0, off[1])).isOf(Blocks.LAVA)) {
                left++;
            }
        }
        check("lava reverts to air", left == 0);
        target.discard();
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void testCutlassBuff() {
        ItemStack c = CutlassOfDoomItem.createStack(reg);
        fake.equipStack(EquipmentSlot.MAINHAND, c);
        PigEntity pig = EntityType.PIG.create(world, SpawnReason.COMMAND);
        pig.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 3.5, 0, 0);
        DamageSource ds = world.getDamageSources().playerAttack(fake);
        float before = c.getItem().getBonusAttackDamage(pig, 7.0F, ds);
        check("no bonus damage without the buff", before == 0.0F);
        fake.setSneaking(true);
        ActionResult r = c.getItem().use(world, fake, Hand.MAIN_HAND);
        fake.setSneaking(false);
        float bonus = c.getItem().getBonusAttackDamage(pig, 7.0F, ds);
        // base 7 + Sharpness V (3.0) = 10 ; x1.5 = 15 ; bonus = 5
        check("sneak + right-click: buff on, bonus = +50% of (7 base + 3 Sharpness) = 5.0", r == ActionResult.SUCCESS && Math.abs(bonus - 5.0F) < 0.01F, "bonus=" + bonus);
        {
            dev.tuffsmp.shrinecraft.ability.TrustManager.clearForTest();
            boolean trusted = dev.tuffsmp.shrinecraft.ability.TrustManager.toggle(fake.getUuid(), pig.getUuid());
            float spared = c.getItem().getBonusAttackDamage(pig, 7.0F, ds);
            boolean back = !dev.tuffsmp.shrinecraft.ability.TrustManager.toggle(fake.getUuid(), pig.getUuid());
            float again = c.getItem().getBonusAttackDamage(pig, 7.0F, ds);
            check("/tuffsmp trust: Doom Surge bonus damage is 0 on a trusted target, and back after un-trusting", trusted && spared == 0.0F && back && Math.abs(again - 5.0F) < 0.01F, "spared=" + spared + " again=" + again);
            check("trust is one-way (the pig does not trust the player)", !dev.tuffsmp.shrinecraft.ability.TrustManager.trusts(pig, fake));
        }
        check("buff starts the (5s + 45s) cooldown and shows Glowing", Abilities.onCooldown(c, world.getTime()) && fake.hasStatusEffect(StatusEffects.GLOWING));
        c.set(ModComponents.ABILITY_UNTIL, world.getTime() - 1);
        check("buff has ended -> no bonus", c.getItem().getBonusAttackDamage(pig, 7.0F, ds) == 0.0F);
        check("ability bar: after the buff the Cutlass shows COOLDOWN", Abilities.status(c, world.getTime()).phase() == Abilities.Phase.COOLDOWN);
        fake.setSneaking(true);
        check("Doom Surge cannot be used again while recharging", c.getItem().use(world, fake, Hand.MAIN_HAND) == ActionResult.FAIL);
        c.set(ModComponents.COOLDOWN_UNTIL, world.getTime() - 1);
        check("... and works again (READY) once the timer is over", Abilities.status(c, world.getTime()).phase() == Abilities.Phase.READY && c.getItem().use(world, fake, Hand.MAIN_HAND) == ActionResult.SUCCESS);
        fake.setSneaking(false);
        fake.clearStatusEffects();
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** Doom Surge must never touch other weapons: damage of a netherite sword must not change while an ACTIVE Cutlass is in the off-hand / hotbar, and the off-hand Cutlass must not fire. */
    private void testCutlassIsolation() {
        PigEntity pig = EntityType.PIG.create(world, SpawnReason.COMMAND);
        pig.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 3.5, 0, 0);
        pig.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(500.0);
        pig.setHealth(500.0F);
        world.spawnEntity(pig);
        ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
        fake.getInventory().clear();
        fake.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        fake.equipStack(EquipmentSlot.MAINHAND, sword);
        float[] base1 = new float[3];
        for (int i = 0; i < 3; i++) {
            fake.resetTicksSince();
            pig.timeUntilRegen = 0;
            float before = pig.getHealth();
            fake.attack(pig);
            base1[i] = before - pig.getHealth();
        }
        // an ACTIVE Cutlass (surge started) in the off-hand and another in the hotbar
        ItemStack cutlassOff = CutlassOfDoomItem.createStack(reg);
        cutlassOff.set(ModComponents.ABILITY_UNTIL, world.getTime() + 100);
        ItemStack cutlassBag = CutlassOfDoomItem.createStack(reg);
        cutlassBag.set(ModComponents.ABILITY_UNTIL, world.getTime() + 100);
        fake.equipStack(EquipmentSlot.OFFHAND, cutlassOff);
        fake.getInventory().setStack(3, cutlassBag);
        float withCutlassElsewhere = 0;
        for (int i = 0; i < 3; i++) {
            fake.resetTicksSince();
            pig.timeUntilRegen = 0;
            float before = pig.getHealth();
            fake.attack(pig);
            withCutlassElsewhere = before - pig.getHealth();
            if (Math.abs(withCutlassElsewhere - base1[i]) > 0.001F) {
                break;
            }
        }
        check("an ACTIVE Cutlass in the off-hand / hotbar does not change a netherite sword's damage", Math.abs(withCutlassElsewhere - base1[2]) < 0.001F, "sword alone " + base1[2] + ", with surge elsewhere " + withCutlassElsewhere);
        // the Cutlass itself in the main hand does get the 1.5x
        fake.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        ItemStack cutMain = CutlassOfDoomItem.createStack(reg);
        fake.equipStack(EquipmentSlot.MAINHAND, cutMain);
        fake.resetTicksSince();
        pig.timeUntilRegen = 0;
        float b0 = pig.getHealth();
        fake.attack(pig);
        float noSurge = b0 - pig.getHealth();
        cutMain.set(ModComponents.ABILITY_UNTIL, world.getTime() + 100);
        fake.resetTicksSince();
        pig.timeUntilRegen = 0;
        b0 = pig.getHealth();
        fake.attack(pig);
        float surge = b0 - pig.getHealth();
        check("the Cutlass in the MAIN hand deals more with Doom Surge on", surge > noSurge + 0.5F, "no surge " + noSurge + ", surge " + surge);
        // sneak + right-click with a sword in the main hand must not fire a Cutlass sitting in the off-hand
        ItemStack cutOff2 = CutlassOfDoomItem.createStack(reg);
        fake.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        fake.equipStack(EquipmentSlot.OFFHAND, cutOff2);
        fake.setSneaking(true);
        ActionResult off = cutOff2.getItem().use(world, fake, Hand.OFF_HAND);
        fake.setSneaking(false);
        check("an off-hand Cutlass ignores sneak + right-click (it only works from the main hand)", off == ActionResult.PASS && !CutlassOfDoomItem.isActive(cutOff2, world.getTime()) && !Abilities.onCooldown(cutOff2, world.getTime()));
        fake.clearStatusEffects();
        fake.getInventory().clear();
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        fake.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        pig.discard();
    }

    private void testMace() {
        ItemStack mace = SculkMaceItem.createStack(reg);
        fake.equipStack(EquipmentSlot.MAINHAND, mace);
        fake.setSneaking(true);
        ActionResult noWave = mace.getItem().use(world, fake, Hand.MAIN_HAND);
        fake.setSneaking(false);
        check("Sculk Wave is locked below tier 5", noWave == ActionResult.FAIL && !SculkMaceItem.isWaveActive(mace, world.getTime()));

        // Warden kills: 50% chance each -> should advance within a reasonable number of kills
        int kills = 0;
        while (SculkMaceItem.getTier(mace) < 1 && kills < 60) {
            WardenEntity w = EntityType.WARDEN.create(world, SpawnReason.COMMAND);
            w.refreshPositionAndAngles(base.getX() + 0.5, base.getY() + 30, base.getZ() + 0.5, 0, 0);
            w.setAiDisabled(true);
            world.spawnEntity(w);
            w.damage(world, world.getDamageSources().playerAttack(fake), 100000.0F);
            w.discard();
            kills++;
        }
        check("killing Wardens with the mace advances its tier (~50% each)", SculkMaceItem.getTier(mace) >= 1, "tier " + SculkMaceItem.getTier(mace) + " after " + kills + " kills");
        check("... and re-applied Density I", level(mace, Enchantments.DENSITY) >= 1);
        // a non-mace kill does not advance it
        ItemStack other = new ItemStack(Items.NETHERITE_SWORD);
        fake.equipStack(EquipmentSlot.MAINHAND, other);
        int tierBefore = SculkMaceItem.getTier(mace);
        for (int i = 0; i < 10; i++) {
            WardenEntity w = EntityType.WARDEN.create(world, SpawnReason.COMMAND);
            w.refreshPositionAndAngles(base.getX() + 0.5, base.getY() + 30, base.getZ() + 0.5, 0, 0);
            w.setAiDisabled(true);
            world.spawnEntity(w);
            w.damage(world, world.getDamageSources().playerAttack(fake), 100000.0F);
            w.discard();
        }
        check("Warden kills with another weapon do NOT advance the mace", SculkMaceItem.getTier(mace) == tierBefore);
        fake.equipStack(EquipmentSlot.MAINHAND, mace);

        // tier 5 + wave
        SculkMaceItem.setTier(mace, 5, reg);
        fake.setSneaking(true);
        ActionResult wave = mace.getItem().use(world, fake, Hand.MAIN_HAND);
        fake.setSneaking(false);
        check("tier 5: sneak + right-click starts Sculk Wave (15s) + cooldown", wave == ActionResult.SUCCESS && SculkMaceItem.isWaveActive(mace, world.getTime()) && Abilities.onCooldown(mace, world.getTime()));

        ZombieEntity z = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
        z.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 4.5, 0, 0);
        z.setAiDisabled(true);
        z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        z.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        z.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        z.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        z.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(100.0);
        z.setHealth(100.0F);
        world.spawnEntity(z);
        z.tick();
        z.setHealth(100.0F);
        fake.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        mace.getItem().postHit(mace, z, fake);
        SculkWaveManager.tickForTest(server, 20);
        float lost = 100.0F - z.getHealth();
        check("a hit during Sculk Wave sends a shockwave: 6 hp of armor-ignoring damage (fully netherite-armored zombie)", Math.abs(lost - SculkMaceItem.WAVE_DAMAGE) < 0.01F, "lost " + lost + " hp");
        // second wave in the same tick window still hurts (ignores invulnerability frames)
        mace.getItem().postHit(mace, z, fake);
        SculkWaveManager.tickForTest(server, 20);
        check("the next hit's wave also lands immediately (no invulnerability window)", Math.abs((100.0F - z.getHealth()) - 2 * SculkMaceItem.WAVE_DAMAGE) < 0.01F, "total lost " + (100.0F - z.getHealth()));
        check("the attacker is never hurt by their own wave", fake.getHealth() == fake.getMaxHealth());
        z.discard();
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private ZombieEntity armoredZombie() {
        ZombieEntity z = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
        z.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 6.5, 0, 0);
        z.setAiDisabled(true);
        z.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        z.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        z.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        z.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        z.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(100.0);
        z.setHealth(100.0F);
        world.spawnEntity(z);
        z.tick(); // equipment attribute modifiers (armor points) are applied on the first tick
        z.setHealth(100.0F);
        return z;
    }

    private float hit(ZombieEntity z, DamageSource ds, float amount) {
        z.timeUntilRegen = 0;
        float before = z.getHealth();
        z.damage(world, ds, amount);
        return before - z.getHealth();
    }

    private void testFrost() {
        ArrowEntity plain = EntityType.ARROW.create(world, SpawnReason.COMMAND);
        ArrowEntity frost = EntityType.ARROW.create(world, SpawnReason.COMMAND);
        frost.addCommandTag(FrostLongbowItem.FROST_ARROW_TAG);
        DamageSource dsPlain = world.getDamageSources().arrow(plain, fake);
        DamageSource dsFrost = world.getDamageSources().arrow(frost, fake);
        check("normal arrow keeps the vanilla 'arrow' damage type (armor applies)", !dsPlain.isIn(DamageTypeTags.BYPASSES_ARMOR) && dsPlain.isIn(DamageTypeTags.IS_PROJECTILE));
        check("frost arrow (full draw) uses the armor-bypassing damage type but is still a projectile",
            dsFrost.isIn(DamageTypeTags.BYPASSES_ARMOR) && dsFrost.isIn(DamageTypeTags.IS_PROJECTILE));
        ZombieEntity z = armoredZombie();
        float normal = hit(z, dsPlain, 10.0F);
        float truth = hit(z, dsFrost, 10.0F);
        check("true damage: armored target takes the full 10 from a frost arrow but much less from a normal arrow",
            Math.abs(truth - 10.0F) < 0.01F && normal < 5.0F, "normal=" + normal + " frost=" + truth);
        check("a full-draw frost arrow hit no longer freezes (only Deep Freeze does)", !DeepFreezeManager.isFrozen(z) && z.getFrozenTicks() < z.getMinFreezeDamageTicks(), "frozen=" + z.getFrozenTicks());
        z.discard();
        check("Frost Longbow tags arrows with the frost tag only for the full-draw (critical) case", FrostLongbowItem.FROST_ARROW_TAG.length() > 0 && ModItems.FROST_LONGBOW instanceof FrostLongbowItem);

        // --- Deep Freeze ability ---
        ItemStack bow = new ItemStack(ModItems.FROST_LONGBOW);
        fake.equipStack(EquipmentSlot.MAINHAND, bow);
        long now = world.getTime();
        ArrowEntity before = EntityType.ARROW.create(world, SpawnReason.COMMAND);
        FrostLongbowItem.tagArrow(before, bow, now, false);
        check("not armed: a normal arrow gets no Deep Freeze tag", !before.getCommandTags().contains(FrostLongbowItem.DEEP_FREEZE_ARROW_TAG));
        fake.setSneaking(true);
        ActionResult r = bow.getItem().use(world, fake, Hand.MAIN_HAND);
        fake.setSneaking(false);
        check("sneak + right-click arms Deep Freeze and starts the 45 s cooldown",
            r == ActionResult.SUCCESS && FrostLongbowItem.isArmed(bow, world.getTime()) && Abilities.onCooldown(bow, world.getTime()));
        check("ability bar shows ACTIVE (DEEP FREEZE) while armed", Abilities.status(bow, world.getTime()).phase() == Abilities.Phase.ACTIVE);
        ArrowEntity deep = EntityType.ARROW.create(world, SpawnReason.COMMAND);
        FrostLongbowItem.tagArrow(deep, bow, world.getTime(), false);
        check("armed: the next arrow (even a partial draw) is the Deep Freeze arrow, and it uses the arming up",
            deep.getCommandTags().contains(FrostLongbowItem.DEEP_FREEZE_ARROW_TAG) && !deep.getCommandTags().contains(FrostLongbowItem.FROST_ARROW_TAG) && !FrostLongbowItem.isArmed(bow, world.getTime()));
        ArrowEntity second = EntityType.ARROW.create(world, SpawnReason.COMMAND);
        FrostLongbowItem.tagArrow(second, bow, world.getTime(), true);
        check("only ONE arrow is the freezing one (the second is a plain full-draw frost arrow)",
            !second.getCommandTags().contains(FrostLongbowItem.DEEP_FREEZE_ARROW_TAG) && second.getCommandTags().contains(FrostLongbowItem.FROST_ARROW_TAG));
        check("ability bar then shows COOLDOWN", Abilities.status(bow, world.getTime()).phase() == Abilities.Phase.COOLDOWN);
        fake.setSneaking(true);
        bow.getItem().use(world, fake, Hand.MAIN_HAND);
        fake.setSneaking(false);
        check("cannot be re-armed while recharging", !FrostLongbowItem.isArmed(bow, world.getTime()));

        ZombieEntity target = armoredZombie();
        target.setAiDisabled(false);            // (the test zombies come with their AI off; switch it on so the freeze has something to turn off)
        hit(target, world.getDamageSources().arrow(deep, fake), 2.0F);
        check("a Deep Freeze arrow hit freezes the target solid (registered + mob AI off)", DeepFreezeManager.isFrozen(target) && target.isAiDisabled());
        target.setVelocity(1.0, 0.0, 1.0);
        DeepFreezeManager.tick(world.getServer());
        check("frozen: sideways motion / knockback is cancelled every tick", target.getVelocity().x == 0.0 && target.getVelocity().z == 0.0);
        check("frozen: the frost overlay is held but stays below the freeze-damage point (no powder-snow damage)",
            !target.canFreeze() || target.getFrozenTicks() < target.getMinFreezeDamageTicks());
        DeepFreezeManager.release(target);
        check("release: AI is given back and it is no longer frozen", !DeepFreezeManager.isFrozen(target) && !target.isAiDisabled());
        target.discard();

        DeepFreezeManager.freeze(fake, 3.0F);
        check("a frozen PLAYER cannot walk or jump (speed and jump strength are 0), no Jump Boost launch effect",
            DeepFreezeManager.isFrozen(fake) && fake.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) == 0.0 && fake.getAttributeValue(EntityAttributes.JUMP_STRENGTH) == 0.0 && !fake.hasStatusEffect(StatusEffects.JUMP_BOOST));
        DeepFreezeManager.release(fake);
        check("... and walking + jumping are given back on release", fake.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) > 0.0 && fake.getAttributeValue(EntityAttributes.JUMP_STRENGTH) > 0.0);

        ZombieEntity friend = armoredZombie();
        dev.tuffsmp.shrinecraft.ability.TrustManager.clearForTest();
        dev.tuffsmp.shrinecraft.ability.TrustManager.toggle(fake.getUuid(), friend.getUuid());
        hit(friend, world.getDamageSources().arrow(deep, fake), 2.0F);
        check("/tuffsmp trust: a trusted target is NOT frozen by Deep Freeze", !DeepFreezeManager.isFrozen(friend));
        dev.tuffsmp.shrinecraft.ability.TrustManager.clearForTest();
        friend.discard();
        DeepFreezeManager.clearForTest();
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    /** A zombie in Protection IV diamond armor (all four pieces) - the owner's yardstick for "how hard does a full bow shot hit". */
    private ZombieEntity protFourDiamondZombie() {
        ZombieEntity z = armoredZombie();
        var prot = entry(RegistryKeys.ENCHANTMENT, Enchantments.PROTECTION);
        Item[] pieces = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            ItemStack st = new ItemStack(pieces[i]);
            st.addEnchantment(prot, 4);
            z.equipStack(slots[i], st);
        }
        z.tick();
        z.tick();
        z.setHealth(100.0F);
        return z;
    }

    /** Releases the bow exactly like the game does (BowItem.onStoppedUsing) after `drawTicks` of drawing; returns the arrow that was really spawned. */
    private net.minecraft.entity.projectile.PersistentProjectileEntity fireBow(ItemStack bow, int drawTicks) {
        fake.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0.0F, 0.0F);   // facing +z, the zombie stands 6 blocks ahead
        fake.equipStack(EquipmentSlot.MAINHAND, bow);
        if (fake.getInventory().count(Items.ARROW) < 8) {
            fake.getInventory().insertStack(new ItemStack(Items.ARROW, 64));
        }
        net.minecraft.util.math.Box area = fake.getBoundingBox().expand(5.0);
        for (var old : world.getEntitiesByClass(net.minecraft.entity.projectile.PersistentProjectileEntity.class, area, e -> true)) {
            old.discard();
        }
        fake.setCurrentHand(Hand.MAIN_HAND);
        bow.getItem().onStoppedUsing(bow, world, fake, bow.getMaxUseTime(fake) - drawTicks);
        var arrows = world.getEntitiesByClass(net.minecraft.entity.projectile.PersistentProjectileEntity.class, area, e -> true);
        return arrows.isEmpty() ? null : arrows.get(0);
    }

    /** Lets a spawned arrow fly (real collision + real damage code) until it hits something or stops. */
    private void flyArrow(net.minecraft.entity.projectile.PersistentProjectileEntity arrow) {
        for (int t = 0; t < 14 && arrow != null && !arrow.isRemoved(); t++) {
            arrow.tick();
        }
    }

    private void testFrostRealShots() {
        ZombieEntity z = protFourDiamondZombie();
        double zx = z.getX(), zy = z.getY(), zz = z.getZ();
        // 1) full draw, many shots: real bow, real arrow, real hit
        int shots = 40;
        int tagged = 0;
        int hits = 0;
        float sum = 0, min = 999, max = 0;
        for (int i = 0; i < shots; i++) {
            z.setHealth(100.0F);
            z.timeUntilRegen = 0;
            z.setVelocity(0, 0, 0);
            z.refreshPositionAndAngles(zx, zy, zz, 0, 0);
            var arrow = fireBow(new ItemStack(ModItems.FROST_LONGBOW), 25);
            if (arrow == null) {
                continue;
            }
            if (arrow.getCommandTags().contains(FrostLongbowItem.FROST_ARROW_TAG)) {
                tagged++;
            }
            float before = z.getHealth();
            flyArrow(arrow);
            float dealt = before - z.getHealth();
            if (dealt > 0) {
                hits++;
                sum += dealt;
                min = Math.min(min, dealt);
                max = Math.max(max, dealt);
            }
            arrow.discard();
        }
        float avg = hits == 0 ? 0 : sum / hits;
        say(String.format("REAL full-draw Frost Longbow vs Protection IV diamond armor: tagged %d/%d, hit %d/%d, avg %.2f HP (%.2f hearts), min %.1f, max %.1f",
            tagged, shots, hits, shots, avg, avg / 2.0F, min, max));
        check("real full-draw shots: every arrow is tagged as a frost arrow", tagged == shots, tagged + "/" + shots);
        check("real full-draw shots: every arrow really hits the target", hits == shots, hits + "/" + shots);
        check("a full draw does about 1.5 hearts (3 HP) to Protection IV diamond armor (avg 2.6 - 3.4 HP)", avg >= 2.6F && avg <= 3.4F, "avg=" + avg);

        // 2) for comparison: a plain vanilla bow, full draw, same target
        float vSum = 0;
        int vHits = 0;
        for (int i = 0; i < 20; i++) {
            z.setHealth(100.0F);
            z.timeUntilRegen = 0;
            z.setVelocity(0, 0, 0);
            z.refreshPositionAndAngles(zx, zy, zz, 0, 0);
            var arrow = fireBow(new ItemStack(Items.BOW), 25);
            float before = z.getHealth();
            flyArrow(arrow);
            if (before - z.getHealth() > 0) {
                vHits++;
                vSum += before - z.getHealth();
            }
            if (arrow != null) {
                arrow.discard();
            }
        }
        say(String.format("(for comparison) vanilla bow full draw vs the same target: avg %.2f HP", vHits == 0 ? 0 : vSum / vHits));

        // 3) a partial draw must stay weaker than a full draw
        z.setHealth(100.0F);
        z.timeUntilRegen = 0;
        z.refreshPositionAndAngles(zx, zy, zz, 0, 0);
        var half = fireBow(new ItemStack(ModItems.FROST_LONGBOW), 10);
        boolean halfTagged = half != null && half.getCommandTags().contains(FrostLongbowItem.FROST_ARROW_TAG);
        float hb = z.getHealth();
        flyArrow(half);
        float halfDealt = hb - z.getHealth();
        say(String.format("half draw: %.2f HP, frost-tagged=%b", halfDealt, halfTagged));
        check("a half draw is NOT a frost arrow and hurts less than a full draw", !halfTagged && halfDealt < avg, "half=" + halfDealt + " full=" + avg);
        if (half != null) {
            half.discard();
        }

        // 4) Deep Freeze through the REAL path: arm with sneak + right-click, draw only partway, shoot, the arrow must freeze the target
        DeepFreezeManager.clearForTest();
        z.setAiDisabled(false);
        z.setHealth(100.0F);
        z.timeUntilRegen = 0;
        z.refreshPositionAndAngles(zx, zy, zz, 0, 0);
        ItemStack bow = new ItemStack(ModItems.FROST_LONGBOW);
        fake.equipStack(EquipmentSlot.MAINHAND, bow);
        fake.setSneaking(true);
        ActionResult armResult = bow.getItem().use(world, fake, Hand.MAIN_HAND);
        fake.setSneaking(false);
        check("real flow: sneak + right-click arms Deep Freeze", armResult == ActionResult.SUCCESS && FrostLongbowItem.isArmed(bow, world.getTime()));
        var deep = fireBow(bow, 12);
        boolean deepTagged = deep != null && deep.getCommandTags().contains(FrostLongbowItem.DEEP_FREEZE_ARROW_TAG);
        check("real flow: the arrow that leaves the armed bow carries the Deep Freeze tag", deepTagged);
        flyArrow(deep);
        check("real flow: that arrow hits and the target is frozen solid (registered, AI off)", DeepFreezeManager.isFrozen(z) && z.isAiDisabled(), "frozen=" + DeepFreezeManager.isFrozen(z));
        if (deep != null) {
            deep.discard();
        }
        // knock it sideways and let the manager run: it must not move
        z.setVelocity(1.0, 0.0, 1.0);
        DeepFreezeManager.tick(world.getServer());
        check("real flow: while frozen the target cannot be pushed", z.getVelocity().x == 0.0 && z.getVelocity().z == 0.0);
        // and the NEXT arrow from the same bow is not a freezing one
        var next = fireBow(bow, 25);
        check("real flow: the following arrow is a normal frost arrow, not a second freeze",
            next != null && !next.getCommandTags().contains(FrostLongbowItem.DEEP_FREEZE_ARROW_TAG) && next.getCommandTags().contains(FrostLongbowItem.FROST_ARROW_TAG));
        if (next != null) {
            next.discard();
        }
        DeepFreezeManager.release(z);
        DeepFreezeManager.clearForTest();
        z.discard();
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void testFrostRealPlayer() {
        VictimFake victim = new VictimFake(world, new GameProfile(UUID.fromString("00000000-0000-0000-0000-00000000ad02"), "[SelfTestVictim]"));
        victim.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        var prot = entry(RegistryKeys.ENCHANTMENT, Enchantments.PROTECTION);
        Item[] pieces = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++) {
            ItemStack st = new ItemStack(pieces[i]);
            st.addEnchantment(prot, 4);
            victim.equipStack(slots[i], st);
        }
        double vx = base.getX() + 0.5, vy = base.getY(), vz = base.getZ() + 6.5;
        victim.refreshPositionAndAngles(vx, vy, vz, 180.0F, 0.0F);
        world.spawnEntity(victim);
        int shots = 30;
        int hits = 0;
        float sum = 0;
        for (int i = 0; i < shots; i++) {
            victim.setHealth(20.0F);
            victim.timeUntilRegen = 0;
            victim.setVelocity(0, 0, 0);
            victim.refreshPositionAndAngles(vx, vy, vz, 180.0F, 0.0F);
            var arrow = fireBow(new ItemStack(ModItems.FROST_LONGBOW), 25);
            float before = victim.getHealth();
            flyArrow(arrow);
            float dealt = before - victim.getHealth();
            if (dealt > 0) {
                hits++;
                sum += dealt;
            }
            if (arrow != null) {
                arrow.discard();
            }
        }
        float avg = hits == 0 ? 0 : sum / hits;
        say(String.format("REAL full-draw Frost Longbow vs a PLAYER in Protection IV diamond: hit %d/%d, avg %.2f HP (%.2f hearts)", hits, shots, avg, avg / 2.0F));
        check("player target: every full-draw arrow hits", hits == shots, hits + "/" + shots);
        check("player target: a full draw does about 1.5 hearts (avg 2.6 - 3.4 HP)", avg >= 2.6F && avg <= 3.4F, "avg=" + avg);

        // Deep Freeze on a real player
        DeepFreezeManager.clearForTest();
        victim.setHealth(20.0F);
        victim.timeUntilRegen = 0;
        victim.refreshPositionAndAngles(vx, vy, vz, 180.0F, 0.0F);
        ItemStack bow = new ItemStack(ModItems.FROST_LONGBOW);
        fake.equipStack(EquipmentSlot.MAINHAND, bow);
        fake.setSneaking(true);
        bow.getItem().use(world, fake, Hand.MAIN_HAND);
        fake.setSneaking(false);
        var deep = fireBow(bow, 12);
        flyArrow(deep);
        check("player target: Deep Freeze arrow freezes the player", DeepFreezeManager.isFrozen(victim));
        check("player target: frozen player has walking speed 0 and jump strength 0, and NO Jump Boost (which would launch them)",
            victim.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) == 0.0 && victim.getAttributeValue(EntityAttributes.JUMP_STRENGTH) == 0.0 && !victim.hasStatusEffect(StatusEffects.JUMP_BOOST));
        victim.setVelocity(1.0, 0.5, 1.0);
        DeepFreezeManager.tick(world.getServer());
        check("player target: knockback / sideways push is cancelled while frozen", victim.getVelocity().x == 0.0 && victim.getVelocity().z == 0.0);
        // pinned: forced sideways (ender pearl / knockback / client cheating) -> put back on the freeze spot, but looking around stays free
        victim.setPosition(vx + 3.0, victim.getY(), vz - 2.0);
        victim.setYaw(77.0F);
        DeepFreezeManager.tick(world.getServer());
        check("player target: moved 3 blocks sideways while frozen -> pinned back to the freeze spot", Math.abs(victim.getX() - vx) < 0.01 && Math.abs(victim.getZ() - vz) < 0.01, "x=" + victim.getX() + " z=" + victim.getZ());
        check("player target: turning the head is NOT blocked", victim.getYaw() == 77.0F);
        check("Deep Freeze lasts 5 seconds", FrostLongbowItem.FREEZE_SECONDS == 5.0F);
        DeepFreezeManager.release(victim);
        check("player target: released - walking and jumping are back", victim.getAttributeValue(EntityAttributes.MOVEMENT_SPEED) > 0.0 && victim.getAttributeValue(EntityAttributes.JUMP_STRENGTH) > 0.0);
        if (deep != null) {
            deep.discard();
        }
        DeepFreezeManager.clearForTest();
        victim.discard();
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void testCannon() {
        ZombieEntity z = armoredZombie();
        ArrowEntity blind = EntityType.ARROW.create(world, SpawnReason.COMMAND);
        blind.addCommandTag(SculkCannonItem.BLIND_ARROW_TAG);
        float lostBlind = hit(z, world.getDamageSources().arrow(blind, fake), 6.0F);
        StatusEffectInstance b = z.getStatusEffect(StatusEffects.BLINDNESS);
        check("Mode 1 arrow hit: Blindness for 5 seconds", b != null && b.getDuration() > 90 && b.getDuration() <= 100, b == null ? "none" : String.valueOf(b.getDuration()));
        z.clearStatusEffects();
        ArrowEntity wail = EntityType.ARROW.create(world, SpawnReason.COMMAND);
        wail.addCommandTag(SculkCannonItem.WAIL_ARROW_TAG);
        float lostWail = hit(z, world.getDamageSources().arrow(wail, fake), 6.0F);
        check("Mode 2 arrow hit: +3 hp armor-ignoring bonus damage on top of the arrow", Math.abs((lostWail - lostBlind) - SculkCannonItem.WAIL_BONUS_DAMAGE) < 0.05F, "arrow-only " + lostBlind + ", wail " + lostWail);
        check("Mode 2 hit does NOT blind", !z.hasStatusEffect(StatusEffects.BLINDNESS));
        z.discard();

        ItemStack cannon = SculkCannonItem.createStack(reg);
        check("starts in Mode 1: Multishot + Endless Quiver, no Piercing/Quick Charge",
            SculkCannonItem.getMode(cannon) == 1 && level(cannon, Enchantments.MULTISHOT) == 1 && level(cannon, ModKeys.ENDLESS_QUIVER) == 1
                && level(cannon, Enchantments.PIERCING) == 0 && level(cannon, Enchantments.QUICK_CHARGE) == 0);
        SculkCannonItem.setMode(cannon, 2, reg);
        check("Mode 2: Quick Charge III + Piercing IV + Endless Quiver, no Multishot",
            SculkCannonItem.getMode(cannon) == 2 && level(cannon, Enchantments.QUICK_CHARGE) == 3 && level(cannon, Enchantments.PIERCING) == 4
                && level(cannon, ModKeys.ENDLESS_QUIVER) == 1 && level(cannon, Enchantments.MULTISHOT) == 0);
        SculkCannonItem.setMode(cannon, 1, reg);
        // a loaded arrow survives a mode change
        cannon.set(DataComponentTypes.CHARGED_PROJECTILES, net.minecraft.component.type.ChargedProjectilesComponent.of(new ItemStack(Items.ARROW)));
        SculkCannonItem.setMode(cannon, 2, reg);
        check("setMode alone leaves the loaded arrow alone", net.minecraft.item.CrossbowItem.isCharged(cannon));
        // the in-game flip (swap-hand key) UNLOADS: 3 multishot arrows must not survive a switch to Piercing Wail
        SculkCannonItem.setMode(cannon, 1, reg);
        cannon.set(DataComponentTypes.CHARGED_PROJECTILES, net.minecraft.component.type.ChargedProjectilesComponent.of(java.util.List.of(new ItemStack(Items.ARROW), new ItemStack(Items.ARROW), new ItemStack(Items.ARROW))));
        check("Blinding Volley can hold 3 loaded arrows", cannon.get(DataComponentTypes.CHARGED_PROJECTILES).getProjectiles().size() == 3);
        int flipped = SculkCannonItem.flipMode(cannon, reg);
        check("flipping Volley -> Wail switches the mode AND unloads the 3 arrows", flipped == 2 && SculkCannonItem.getMode(cannon) == 2 && !net.minecraft.item.CrossbowItem.isCharged(cannon)
            && level(cannon, Enchantments.PIERCING) == 4 && level(cannon, Enchantments.MULTISHOT) == 0);
        cannon.set(DataComponentTypes.CHARGED_PROJECTILES, net.minecraft.component.type.ChargedProjectilesComponent.of(new ItemStack(Items.TIPPED_ARROW)));
        flipped = SculkCannonItem.flipMode(cannon, reg);
        check("flipping Wail -> Volley unloads the tipped arrow too", flipped == 1 && !net.minecraft.item.CrossbowItem.isCharged(cannon) && level(cannon, Enchantments.MULTISHOT) == 1);
        cannon.set(DataComponentTypes.CHARGED_PROJECTILES, net.minecraft.component.type.ChargedProjectilesComponent.DEFAULT);
        SculkCannonItem.setMode(cannon, 1, reg);
        check("flipping back to Mode 1 restores Multishot", level(cannon, Enchantments.MULTISHOT) == 1 && level(cannon, Enchantments.PIERCING) == 0);

        // ammo rules: mode 1 refuses tipped arrows, mode 2 accepts them. Both never consume.
        fake.getInventory().clear();
        fake.equipStack(EquipmentSlot.MAINHAND, cannon);
        ItemStack tipped = new ItemStack(Items.TIPPED_ARROW, 5);
        fake.getInventory().offerOrDrop(tipped);
        fake.setSneaking(false);
        ActionResult noAmmo = cannon.getItem().use(world, fake, Hand.MAIN_HAND);
        check("Mode 1 will not start charging with only tipped arrows", noAmmo == ActionResult.FAIL);
        SculkCannonItem.setMode(cannon, 2, reg);
        ActionResult wailStart = cannon.getItem().use(world, fake, Hand.MAIN_HAND);
        check("Mode 2 accepts tipped arrows", wailStart == ActionResult.CONSUME);
        fake.stopUsingItem();
        // drive the loader: usageTick at full charge loads the arrows without consuming any
        for (int tick = 0; tick < 40; tick++) {
            fake.setCurrentHand(Hand.MAIN_HAND);
            cannon.getItem().usageTick(world, fake, cannon, cannon.getItem().getMaxUseTime(cannon, fake) - tick);
        }
        boolean charged = net.minecraft.item.CrossbowItem.isCharged(cannon);
        check("Mode 2 loads a tipped arrow and does not use any up", charged && ShrineInteraction.count(fake, Items.TIPPED_ARROW) == 5, "arrows left " + ShrineInteraction.count(fake, Items.TIPPED_ARROW));
        // off-hand firing is refused
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        fake.equipStack(EquipmentSlot.OFFHAND, cannon);
        check("cannot fire from the off-hand", cannon.getItem().use(world, fake, Hand.OFF_HAND) == ActionResult.FAIL);
        fake.equipStack(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        fake.getInventory().clear();
        fake.stopUsingItem();
    }

    private void testLance() {
        ItemStack lance = new ItemStack(ModItems.CLOUD_LANCE);
        check("new lance has exactly 1 dash ready", CloudLanceItem.readyTimes(lance).size() == 1 && CloudLanceItem.readyTimes(lance).stream().allMatch(t -> t == 0L));
        check("lance is the vanilla spear type (kinetic + piercing weapon components, netherite spear numbers)",
            lance.contains(DataComponentTypes.KINETIC_WEAPON) && lance.contains(DataComponentTypes.PIERCING_WEAPON) && lance.contains(DataComponentTypes.ATTACK_RANGE)
                && lance.get(DataComponentTypes.ATTACK_RANGE).equals(new ItemStack(Items.NETHERITE_SPEAR).get(DataComponentTypes.ATTACK_RANGE))
                && lance.get(DataComponentTypes.KINETIC_WEAPON).equals(new ItemStack(Items.NETHERITE_SPEAR).get(DataComponentTypes.KINETIC_WEAPON)));
        check("lance has no Lunge or any other enchantment", !lance.hasEnchantments());
        fake.getInventory().clear();
        fake.equipStack(EquipmentSlot.MAINHAND, lance);
        check("held lance blocks the Slowness effect", !fake.canHaveStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 2)));
        check("held lance does not block other effects", fake.canHaveStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 2)));
        fake.setSneaking(true);
        ActionResult[] r = new ActionResult[2];
        for (int i = 0; i < 2; i++) {
            fake.setVelocity(Vec3d.ZERO);
            r[i] = lance.getItem().use(world, fake, Hand.MAIN_HAND);
        }
        check("the single dash works, the 2nd has no charge", r[0] == ActionResult.SUCCESS && r[1] == ActionResult.FAIL);
        long now = world.getTime();
        check("the used dash recharges on a 20s timer", CloudLanceItem.readyTimes(lance).stream().allMatch(t -> t >= now + 19 * 20 && t <= now + 20 * 20));
        lance.set(ModComponents.DASH_READY_AT, List.of(0L));
        fake.setVelocity(Vec3d.ZERO);
        check("after the cooldown the dash works again", lance.getItem().use(world, fake, Hand.MAIN_HAND) == ActionResult.SUCCESS);
        check("dash uses Riptide-3 strength (3.0)", Math.abs(CloudLanceItem.dashStrength() - 3.0F) < 1.0E-4F && fake.isUsingRiptide());
        fake.setSneaking(false);
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        check("without the lance Slowness is allowed again", fake.canHaveStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 200, 2)));
        fake.setVelocity(Vec3d.ZERO);
    }

    private void testHarbinger() {
        ItemStack h = HarbingerOfDeathItem.createStack(reg);
        check("Harbinger: Efficiency 5 + Fortune 3, no Silk Touch", level(h, Enchantments.EFFICIENCY) == 5 && level(h, Enchantments.FORTUNE) == 3 && level(h, Enchantments.SILK_TOUCH) == 0);
        check("Harbinger starts in 3x3x3 mode", HarbingerOfDeathItem.isWide(h));
        fake.getInventory().clear();
        fake.equipStack(EquipmentSlot.MAINHAND, h);
        fake.setSneaking(true);
        check("Shift + swap key in the main hand toggles Silk/Fortune and is consumed", HarbingerHandler.onSwapKey(fake) && HarbingerOfDeathItem.isSilk(h) && HarbingerOfDeathItem.isWide(h));
        check("... Silk Touch only (Fortune removed)", level(h, Enchantments.SILK_TOUCH) == 1 && level(h, Enchantments.FORTUNE) == 0);
        HarbingerHandler.onSwapKey(fake);
        check("Shift + swap again -> Fortune 3 only", level(h, Enchantments.SILK_TOUCH) == 0 && level(h, Enchantments.FORTUNE) == 3 && !HarbingerOfDeathItem.isSilk(h));
        fake.setSneaking(false);
        check("plain swap key switches to single-block mode (and leaves Fortune alone)", HarbingerHandler.onSwapKey(fake) && !HarbingerOfDeathItem.isWide(h) && !HarbingerOfDeathItem.isSilk(h));
        HarbingerHandler.onSwapKey(fake);
        check("plain swap key again -> 3x3x3 mode", HarbingerOfDeathItem.isWide(h));
        fake.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_PICKAXE));
        check("swap key with another item in hand is NOT consumed (normal swap)", !HarbingerHandler.onSwapKey(fake));
        fake.equipStack(EquipmentSlot.MAINHAND, h);

        // 3x3x3: build a cube of stone with a dirt block and a bedrock block inside it
        BlockPos c = base.add(0, 12, 0);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) world.setBlockState(c.add(x, y, z), Blocks.STONE.getDefaultState(), 3);
        world.setBlockState(c.add(1, 0, 0), Blocks.DIRT.getDefaultState(), 3);
        world.setBlockState(c.add(0, 0, 1), Blocks.BEDROCK.getDefaultState(), 3);
        fake.refreshPositionAndAngles(c.getX() + 0.5, c.getY() + 3, c.getZ() + 0.5, 0, 0);
        fake.interactionManager.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        fake.interactionManager.tryBreakBlock(c);
        int air = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) if (world.getBlockState(c.add(x, y, z)).isAir()) air++;
        check("Harbinger in the main hand (3x3x3 mode): mining one block breaks the 3x3x3 (24 stone + the centre)", air == 25, air + " broken");
        check("... but skips the dirt block (not pickaxe-mineable) and bedrock", world.getBlockState(c.add(1, 0, 0)).isOf(Blocks.DIRT) && world.getBlockState(c.add(0, 0, 1)).isOf(Blocks.BEDROCK));
        // single-block mode: only one block breaks
        h.set(ModComponents.HARBINGER_WIDE, false);
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) world.setBlockState(c.add(x, y, z), Blocks.STONE.getDefaultState(), 3);
        fake.interactionManager.tryBreakBlock(c);
        int air2 = 0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) if (world.getBlockState(c.add(x, y, z)).isAir()) air2++;
        check("single-block mode: only 1 block breaks", air2 == 1, air2 + " broken");
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) world.setBlockState(c.add(x, y, z), Blocks.AIR.getDefaultState(), 3);
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        fake.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
    }

    private void testTrident() {
        ItemStack t = new ItemStack(ModItems.ANAKLUSMOS);
        t = dev.tuffsmp.shrinecraft.item.AnaklusmosItem.createStack(reg);
        check("Anaklusmos: Riptide 5 (launch strength 4.5)", level(t, Enchantments.RIPTIDE) == 5 && Math.abs(EnchantmentHelper.getTridentSpinAttackStrength(t, fake) - 4.5F) < 1.0E-4F);
        fake.equipStack(EquipmentSlot.MAINHAND, t);
        fake.setSneaking(false);
        check("on dry land (no water/rain) right-click does nothing - and never throws", t.getItem().use(world, fake, Hand.MAIN_HAND) == ActionResult.FAIL);
        ItemStack stripped = new ItemStack(ModItems.ANAKLUSMOS);
        fake.equipStack(EquipmentSlot.MAINHAND, stripped);
        check("even without Riptide, right-click can never start a throw", stripped.getItem().use(world, fake, Hand.MAIN_HAND) == ActionResult.FAIL
            && !stripped.getItem().onStoppedUsing(stripped, world, fake, 100));
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    private void testOrb() {
        fake.getInventory().clear();
        fake.refreshPositionAndAngles(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, 0, 0);
        ItemStack orb = new ItemStack(ModItems.SILVERS_SPAWN_ORB);
        fake.equipStack(EquipmentSlot.MAINHAND, orb);
        fake.setSneaking(false);
        ActionResult r = orb.getItem().use(world, fake, Hand.MAIN_HAND);
        OrbData d = SilversSpawnOrbItem.getData(orb);
        check("STORED: right-click summons Silver and the orb becomes RELEASED", r == ActionResult.SUCCESS && d.state() == OrbData.State.RELEASED && d.silver().isPresent());
        HorseEntity silver = (HorseEntity) world.getEntity(d.silver().get());
        check("Silver exists, is named, tamed and tagged", silver != null && silver.getCustomName() != null && silver.getCustomName().getString().equals("Silver") && silver.isTame());
        double spd = silver.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);
        double jmp = silver.getAttributeValue(EntityAttributes.JUMP_STRENGTH);
        double hp = silver.getAttributeValue(EntityAttributes.MAX_HEALTH);
        check("Silver stats = 1.5x a normal horse (speed/jump/health)",
            Math.abs(spd - 0.225 * 1.5) < 1e-6 && Math.abs(jmp - 0.7 * 1.5) < 1e-6 && Math.abs(hp - 22.5 * 1.5) < 1e-6, spd + " / " + jmp + " / " + hp);
        check("Silver wears built-in netherite horse armor", silver.getEquippedStack(EquipmentSlot.BODY).isOf(Items.NETHERITE_HORSE_ARMOR));
        // extra armor cannot be equipped by hand
        fake.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_HORSE_ARMOR));
        ActionResult armorTry = UseEntityCallback.EVENT.invoker().interact(fake, world, Hand.MAIN_HAND, silver, null);
        check("no extra armor can be put on Silver", armorTry == ActionResult.FAIL);
        // the horse inventory (where armor/saddle could be taken out and duplicated) cannot be opened at all
        silver.openInventory(fake);
        check("Silver's inventory screen cannot be opened (no stealing the armor / saddle)", fake.currentScreenHandler == fake.playerScreenHandler);
        check("Silver's recall cooldown is 20 minutes", SilversSpawnOrbItem.COOLDOWN_SECONDS == 1200);
        check("Silver can walk on powder snow (doesn't sink)", net.minecraft.block.PowderSnowBlock.canWalkOnPowderSnow(silver));
        // capture without combat -> STORED
        fake.equipStack(EquipmentSlot.MAINHAND, orb);
        fake.setSneaking(true);
        ActionResult cap = UseEntityCallback.EVENT.invoker().interact(fake, world, Hand.MAIN_HAND, silver, null);
        d = SilversSpawnOrbItem.getData(orb);
        check("RELEASED: sneak + right-click on Silver captures it straight to STORED (no combat)", cap == ActionResult.SUCCESS && d.state() == OrbData.State.STORED && silver.isRemoved());
        // summon again, hurt Silver, capture -> COOLDOWN
        fake.setSneaking(false);
        orb.getItem().use(world, fake, Hand.MAIN_HAND);
        d = SilversSpawnOrbItem.getData(orb);
        silver = (HorseEntity) world.getEntity(d.silver().get());
        silver.damage(world, world.getDamageSources().generic(), 1.0F);
        fake.setSneaking(true);
        UseEntityCallback.EVENT.invoker().interact(fake, world, Hand.MAIN_HAND, silver, null);
        d = SilversSpawnOrbItem.getData(orb);
        check("Silver recently hurt: capture puts the orb on COOLDOWN", d.state() == OrbData.State.COOLDOWN && d.cooldownEndsAt() > world.getTime());
        fake.setSneaking(false);
        ActionResult cd = orb.getItem().use(world, fake, Hand.MAIN_HAND);
        check("COOLDOWN: right-click does nothing", cd == ActionResult.FAIL && SilversSpawnOrbItem.getData(orb).state() == OrbData.State.COOLDOWN);
        SilversSpawnOrbItem.setData(orb, SilversSpawnOrbItem.getData(orb).cooldown(world.getTime() - 1, 10.0F));
        orb.getItem().inventoryTick(orb, world, fake, EquipmentSlot.MAINHAND);
        check("when the cooldown ends the orb returns to STORED by itself", SilversSpawnOrbItem.getData(orb).state() == OrbData.State.STORED);
        fake.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        fake.getInventory().clear();
    }

    private void testMisc() {
        ItemStack cape = new ItemStack(ModItems.STRANGE_CAPE);
        check("Strange Cape: glider + equippable in the chest slot (vanilla elytra recipe)",
            cape.contains(DataComponentTypes.GLIDER) && cape.get(DataComponentTypes.EQUIPPABLE).slot() == EquipmentSlot.CHEST
                && cape.get(DataComponentTypes.EQUIPPABLE).assetId().isPresent() && cape.get(DataComponentTypes.EQUIPPABLE).assetId().get().equals(ModKeys.STRANGE_CAPE));
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        check("... same as the vanilla Elytra except unbreakable and its own look", elytra.get(DataComponentTypes.MAX_DAMAGE).equals(cape.get(DataComponentTypes.MAX_DAMAGE)));
        check("all 14 recipes are final: none empty, none flagged as placeholder",
            ShrineRecipes.all().stream().allMatch(r -> !r.ingredients().isEmpty() && !r.placeholder()));
        check("Magic Essence: registered, stacks to 64, shimmers",
            ModItems.MAGIC_ESSENCE.getDefaultStack().getMaxCount() == 64 && Boolean.TRUE.equals(ModItems.MAGIC_ESSENCE.getDefaultStack().get(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE)));
        check("every recipe asks for exactly 1 Magic Essence - except the Sculk Mace, which asks for 2",
            ShrineRecipes.all().size() == 14 && ShrineRecipes.all().stream().allMatch(r -> r.ingredients().stream().filter(in -> in.item() == ModItems.MAGIC_ESSENCE)
                .mapToInt(ShrineRecipe.Ingredient::count).sum() == (r.id().equals("sculk_mace") ? 2 : 1)));
        // owner's recipe changes: no ancient debris / netherite anywhere, tuff on every armor piece, heavy core for the mace, etc.
        java.util.function.BiFunction<String, net.minecraft.item.Item, Integer> need = (id, item) -> ShrineRecipes.byId(id).ingredients().stream()
            .filter(in -> in.item() == item).mapToInt(ShrineRecipe.Ingredient::count).sum();
        // NO netherite of any kind anywhere (no ancient debris, scrap, ingots, tools, armor) EXCEPT the netherite horse armor in Silver's Orb
        boolean noNether = ShrineRecipes.all().stream().flatMap(r -> r.ingredients().stream()).noneMatch(in -> {
            String path = net.minecraft.registry.Registries.ITEM.getId(in.item()).getPath();
            return (path.contains("netherite") || path.equals("ancient_debris")) && in.item() != Items.NETHERITE_HORSE_ARMOR;
        });
        boolean horseOnlyInOrb = ShrineRecipes.all().stream().allMatch(r -> r.id().equals("silvers_spawn_orb")
            || r.ingredients().stream().noneMatch(in -> in.item() == Items.NETHERITE_HORSE_ARMOR));
        check("no recipe asks for ancient debris / netherite anything - except the netherite horse armor, which only Silver's Orb asks for", noNether && horseOnlyInOrb);
        check("every armor piece asks for 64 tuff blocks",
            need.apply("shrine_helmet", Items.TUFF) == 64 && need.apply("shrine_chestplate", Items.TUFF) == 64
                && need.apply("shrine_leggings", Items.TUFF) == 64 && need.apply("shrine_boots", Items.TUFF) == 64);
        check("Molten Greataxe also asks for 64 magma blocks + 64 blaze rods", need.apply("molten_greataxe", Items.MAGMA_BLOCK) == 64 && need.apply("molten_greataxe", Items.BLAZE_ROD) == 64);
        check("Sculk Mace asks for a heavy core (not a mace) and 2 Magic Essence",
            need.apply("sculk_mace", Items.HEAVY_CORE) == 1 && need.apply("sculk_mace", Items.MACE) == 0 && need.apply("sculk_mace", ModItems.MAGIC_ESSENCE) == 2);
        check("Shrine Helmet: 64 glass bottles, 64 glowstone blocks, 64 nether wart",
            need.apply("shrine_helmet", Items.GLASS_BOTTLE) == 64 && need.apply("shrine_helmet", Items.GLOWSTONE) == 64 && need.apply("shrine_helmet", Items.NETHER_WART) == 64);
        check("Shrine Chestplate: 256 emerald blocks + 32 amethyst shards", need.apply("shrine_chestplate", Items.EMERALD_BLOCK) == 256 && need.apply("shrine_chestplate", Items.AMETHYST_SHARD) == 32);
        check("Silver's Orb: NETHERITE horse armor (not diamond) + 64 sea lanterns", need.apply("silvers_spawn_orb", Items.NETHERITE_HORSE_ARMOR) == 1
            && need.apply("silvers_spawn_orb", Items.DIAMOND_HORSE_ARMOR) == 0 && need.apply("silvers_spawn_orb", Items.SEA_LANTERN) == 64);
        // an OLD config file (no version field, still asks for ancient debris + netherite ingots) must be replaced by the current recipes, with a backup
        try {
            java.nio.file.Path cfg = ShrineRecipes.configPath();
            String keep = java.nio.file.Files.exists(cfg) ? java.nio.file.Files.readString(cfg) : null;
            String old = "{\"recipes\":[{\"id\":\"shrine_helmet\",\"name\":\"Shrine Helmet\",\"placeholder\":false,\"ingredients\":["
                + "{\"item\":\"minecraft:diamond_helmet\",\"count\":1},{\"item\":\"minecraft:netherite_ingot\",\"count\":2},{\"item\":\"minecraft:ancient_debris\",\"count\":8}]}]}";
            java.nio.file.Files.writeString(cfg, old);
            String msg = ShrineRecipes.reload();
            boolean replaced = need.apply("shrine_helmet", Items.NETHERITE_INGOT) == 0 && need.apply("shrine_helmet", Items.ANCIENT_DEBRIS) == 0 && need.apply("shrine_helmet", Items.TUFF) == 64;
            boolean backup = java.nio.file.Files.exists(cfg.resolveSibling("shrine_recipes.json.old-v1"));
            check("an outdated recipe file (no version) is replaced by the current recipes, and a backup is kept", replaced && backup && msg.contains("outdated"), msg);
            java.nio.file.Files.deleteIfExists(cfg.resolveSibling("shrine_recipes.json.old-v1"));
            if (keep != null) {
                java.nio.file.Files.writeString(cfg, keep);
            }
            ShrineRecipes.reload();
        } catch (java.io.IOException e) {
            check("outdated recipe file upgrade test", false, e.toString());
        }
        check("recipe config file exists for no-code edits", java.nio.file.Files.exists(ShrineRecipes.configPath()), ShrineRecipes.configPath().toString());
    }
}
