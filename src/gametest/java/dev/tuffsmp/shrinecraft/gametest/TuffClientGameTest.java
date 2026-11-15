package dev.tuffsmp.shrinecraft.gametest;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.client.gui.GuiSettings;
import dev.tuffsmp.shrinecraft.client.gui.GuiTheme;
import dev.tuffsmp.shrinecraft.item.OrbData;
import dev.tuffsmp.shrinecraft.ability.SilverManager;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.shrine.ShrineBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.text.Text;
import net.minecraft.client.option.Perspective;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * REAL-GAME visual test: boots the actual Minecraft client (software OpenGL under a virtual display), loads a flat world and takes
 * in-game screenshots of everything: every item in first person, third person, ground, item frames and the inventory;
 * the four armor pieces + cape worn; the Shrine in all three states; Silver; and the five GUI themes.
 * Run with:  ./gradlew runClientGameTest   (screenshots land in build/run/clientGameTest/screenshots)
 */
public class TuffClientGameTest implements FabricClientGameTest {
    /** Framing for the full-view shots of the tall hand-built models: {scale, height offset in blocks}, computed from each model's bounds. */
    private static final java.util.Map<String, String[]> MODEL_FIT = java.util.Map.of(
        "molten_greataxe", new String[] {"1.88", "-0.24"}, "cutlass_of_doom", new String[] {"2.03", "-0.47"},
        "sculk_mace", new String[] {"2.02", "-0.57"}, "frost_longbow", new String[] {"1.92", "-0.25"},
        "cloud_lance", new String[] {"1.38", "-0.29"}, "harbinger_of_death", new String[] {"1.92", "-0.49"},
        "anaklusmos", new String[] {"1.7", "-0.52"}, "sculk_cannon", new String[] {"1.7", "0.45"}, "silvers_spawn_orb", new String[] {"3.0", "0.16"});

    private static final String[] ITEMS = {
        "molten_greataxe", "cutlass_of_doom", "sculk_mace", "frost_longbow", "cloud_lance",
        "shrine_helmet", "shrine_chestplate", "shrine_leggings", "shrine_boots",
        "harbinger_of_death", "anaklusmos", "sculk_cannon", "strange_cape", "silvers_spawn_orb", "magic_essence",
    };
    private final List<String> problems = new ArrayList<>();

    private interface Step {
        void run() throws Exception;
    }

    /** Optional filter for quick experiments: TUFF_ONLY="frame,hand" runs only steps whose name contains one of the words. */
    private static final String ONLY = System.getenv("TUFF_ONLY");

    private void step(String name, Step s) {
        if (ONLY != null && !ONLY.isBlank()) {
            boolean run = false;
            for (String w : ONLY.split(",")) {
                if (!w.isBlank() && name.contains(w.trim())) run = true;
            }
            if (!run) return;
        }
        try {
            s.run();
        } catch (Throwable t) {
            ShrineCraft.LOGGER.error("[GAMETEST] step '{}' failed", name, t);
            problems.add(name + ": " + t);
        }
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            var server = world.getServer();
            server.runCommand("gamemode survival @p");
            server.runCommand("time set noon");
            server.runCommand("weather clear");
            server.runCommand("tp @p 0 -60 0 0 8");
            context.waitTicks(10);

            // ---- every item: first person + third person (front) ----
            for (String id : ITEMS) {
                step("hand " + id, () -> {
                    server.runCommand("clear @p");
                    server.runCommand("item replace entity @p weapon.mainhand with shrinecraft:" + id);
                    context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                    // Vanilla keeps a freshly-swapped item lowered until the attack cooldown has recharged (slow weapons
                    // like the greataxe need ~1 s), so wait long enough or the first-person shot shows an empty hand.
                    context.waitTicks(45);
                    context.takeScreenshot("a_1p_" + id);
                    context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
                    context.waitTicks(6);
                    context.takeScreenshot("a_3p_" + id);
                    context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
                    context.waitTicks(6);
                    context.takeScreenshot("a_3pb_" + id);
                    context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                });
            }

            // ---- vanilla reference items in first person (to calibrate our own first-person transforms against) ----
            step("hand vanilla", () -> {
                context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                for (String id : new String[] {"netherite_sword", "netherite_axe", "netherite_pickaxe", "mace", "trident", "bow", "netherite_spear", "crossbow"}) {
                    server.runCommand("clear @p");
                    server.runCommand("item replace entity @p weapon.mainhand with minecraft:" + id);
                    context.waitTicks(45);
                    context.takeScreenshot("v_1p_" + id);
                    context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
                    context.waitTicks(6);
                    context.takeScreenshot("v_3p_" + id);
                    context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
                    context.waitTicks(6);
                    context.takeScreenshot("v_3pb_" + id);
                    context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                }
            });

            // ---- SIDE VIEW of player-shaped mannequins holding vanilla tools next to ours (how the item sits in a real hand) ----
            step("mannequin side", () -> {
                String[][] pairs = {
                    {"minecraft:netherite_sword", "shrinecraft:cutlass_of_doom"}, {"minecraft:netherite_axe", "shrinecraft:molten_greataxe"},
                    {"minecraft:netherite_pickaxe", "shrinecraft:harbinger_of_death"}, {"minecraft:netherite_spear", "shrinecraft:cloud_lance"},
                    {"minecraft:trident", "shrinecraft:anaklusmos"}, {"minecraft:bow", "shrinecraft:frost_longbow"},
                    {"minecraft:crossbow", "shrinecraft:sculk_cannon"}, {"minecraft:mace", "shrinecraft:sculk_mace"}};
                server.runCommand("clear @p");
                context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                for (int g = 0; g < pairs.length; g++) {
                    server.runCommand("kill @e[type=minecraft:mannequin]");
                    server.runCommand("tp @p 0 -60 0 0 0");
                    context.waitTicks(15);
                    for (int k = 0; k < 2; k++) {
                        server.runCommand("summon minecraft:mannequin " + (0.8 - k * 1.6) + " -60 3.5 {Rotation:[90.0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{mainhand:{id:\"" + pairs[g][k] + "\",count:1}}}");
                    }
                    context.waitTicks(8);
                    context.takeScreenshot("m_" + g);
                }
                server.runCommand("kill @e[type=minecraft:mannequin]");
            });

            // ---- ground items + item frames in a row ----
            step("ground + frames", () -> {
                server.runCommand("clear @p");
                server.runCommand("tp @p 0 -60 0 0 10");
                server.runCommand("fill -9 -60 8 9 -57 8 minecraft:smooth_stone");
                for (int i = 0; i < 14; i++) {
                    String id = ITEMS[i];
                    double x = -6.0 + (i % 7) * 2.0;
                    int row = i / 7;
                    server.runCommand("summon minecraft:item_frame " + (int) x + " " + (-59 + row * 2) + " 7 {Facing:3b,Invulnerable:1b,Item:{id:\"shrinecraft:" + id + "\",count:1}}");
                    server.runCommand("summon minecraft:item " + x + " -60 3 {Item:{id:\"shrinecraft:" + id + "\",count:1},PickupDelay:32767s,Age:-32768s,Motion:[0.0,0.0,0.0]}");
                }
                context.waitTicks(10);
                context.takeScreenshot("b_frames_and_ground");
                server.runCommand("kill @e[type=item]");
                server.runCommand("kill @e[type=item_frame]");
            });

            // ---- close-ups: each item alone in an item frame (the 'fixed' context). Invisible frames on purpose: under the software
            // OpenGL used for this test, the frame's back plate hides thin items (even vanilla swords), which a real GPU does not do. ----
            step("frame closeups", () -> {
                server.runCommand("tp @p 0 -60 5 0 0");
                for (String id : ITEMS) {
                    server.runCommand("kill @e[type=item_frame]");
                    server.runCommand("summon minecraft:item_frame 0 -59 7 {Facing:3b,Invulnerable:1b,Invisible:1b,Item:{id:\"shrinecraft:" + id + "\",count:1}}");
                    context.waitTicks(4);
                    context.takeScreenshot("g_frame_" + id);
                }
                server.runCommand("kill @e[type=item_frame]");
                server.runCommand("tp @p 0 -60 0 0 10");
            });

            // ---- frame debug: normal vs invisible frame, oblique view, vanilla comparison items ----
            step("frame debug", () -> {
                server.runCommand("kill @e[type=item_frame]");
                server.runCommand("fill -9 -60 8 9 -57 8 minecraft:smooth_stone");
                String[][] rows = {
                    {"-3", "molten_greataxe", "0"}, {"-1", "molten_greataxe", "1"},
                    {"1", "minecraft:netherite_sword", "0"}, {"3", "minecraft:bow", "0"},
                };
                for (String[] r : rows) {
                    String id = r[1].contains(":") ? r[1] : "shrinecraft:" + r[1];
                    server.runCommand("summon minecraft:item_frame " + r[0] + " -59 7 {Facing:3b,Invulnerable:1b,Invisible:" + r[2] + "b,Item:{id:\"" + id + "\",count:1}}");
                }
                server.runCommand("tp @p 0 -60 4 0 0");
                context.waitTicks(6);
                context.takeScreenshot("h_frames_straight");
                server.runCommand("tp @p 3 -60 4 25 5");
                context.waitTicks(6);
                context.takeScreenshot("h_frames_oblique");
                server.runCommand("kill @e[type=item_frame]");
                server.runCommand("tp @p 0 -60 0 0 10");
            });

            // ---- jab sequence: our lance (or the vanilla spear with TUFF_JAB_ITEM=minecraft:netherite_spear), first person, every tick after the left click ----
            step("lance jab seq", () -> {
                String id = System.getenv("TUFF_JAB_ITEM") != null ? System.getenv("TUFF_JAB_ITEM") : "shrinecraft:cloud_lance";
                server.runCommand("tp @p 0 -60 0 0 0");
                server.runCommand("clear @p");
                server.runCommand("item replace entity @p weapon.mainhand with " + id);
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = false; });
                context.waitTicks(30);
                context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                for (int t = 1; t <= 8; t++) {
                    context.waitTicks(1);
                    context.takeScreenshot("jab_" + id.substring(id.indexOf(':') + 1) + "_t" + t);
                }
                server.runCommand("clear @p");
            });

            // ---- ability bar: several states at once (held greataxe ACTIVE, cutlass + mace + lance + orb recharging), then a READY bar ----
            step("ability bar", () -> {
                server.runCommand("tp @p 0 -60 0 0 0");
                server.runCommand("clear @p");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = false; });
                server.runOnServer(sv -> {
                    var p = sv.getPlayerManager().getPlayerList().get(0);
                    var w = p.getEntityWorld();
                    long now = w.getTime();
                    var reg = w.getRegistryManager();
                    var axe = dev.tuffsmp.shrinecraft.item.MoltenGreataxeItem.createStack(reg);
                    dev.tuffsmp.shrinecraft.ability.Abilities.start(axe, now - 3 * 20, 10 * 20, 60 * 20);         // armed 3 s ago: 7 s of the 10 s window left
                    var cut = dev.tuffsmp.shrinecraft.item.CutlassOfDoomItem.createStack(reg);
                    dev.tuffsmp.shrinecraft.ability.Abilities.start(cut, now - 20 * 20, 5 * 20, 50 * 20);          // 30 s left
                    var mace = dev.tuffsmp.shrinecraft.item.SculkMaceItem.createStack(reg);
                    dev.tuffsmp.shrinecraft.item.SculkMaceItem.setTier(mace, 5, reg);
                    dev.tuffsmp.shrinecraft.ability.Abilities.start(mace, now - 56 * 20, 15 * 20, 60 * 20);        // 4 s left (shows the decimals)
                    var lance = new net.minecraft.item.ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.CLOUD_LANCE);
                    lance.set(dev.tuffsmp.shrinecraft.registry.ModComponents.DASH_READY_AT, java.util.List.of(now + 12 * 20L));
                    var orb = new net.minecraft.item.ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.SILVERS_SPAWN_ORB);
                    dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem.setData(orb, dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem.ensureId(orb)
                        .cooldown(now + 1100 * 20L, 10.0F));
                    p.getInventory().setStack(0, axe);
                    p.getInventory().setStack(1, cut);
                    p.getInventory().setStack(2, mace);
                    p.getInventory().setStack(3, lance);
                    p.getInventory().setStack(4, orb);
                    p.getInventory().setSelectedSlot(0);
                });
                context.waitTicks(50);          // the cards fade / glide in, give them time
                context.takeScreenshot("ab_states");
                server.runOnServer(sv -> {
                    var p = sv.getPlayerManager().getPlayerList().get(0);
                    p.getInventory().clear();
                    p.getInventory().setStack(0, new net.minecraft.item.ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.CLOUD_LANCE));
                    p.getInventory().setSelectedSlot(0);
                });
                context.waitTicks(60);
                context.takeScreenshot("ab_ready");
                server.runCommand("clear @p");
            });

            // ---- ability bar, ONE BAR PER SHOT: every weapon in each of its states (ACTIVE / RECHARGING / READY, orb: stored / out / recharging) ----
            step("ability each", () -> {
                server.runCommand("tp @p 0 -60 0 0 0");
                server.runCommand("clear @p");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = false; });
                // {weapon, state}
                String[][] jobs = {
                    {"greataxe", "active"}, {"greataxe", "recharging"}, {"greataxe", "ready"},
                    {"cutlass", "active"}, {"cutlass", "recharging"}, {"cutlass", "ready"},
                    {"mace", "active"}, {"mace", "recharging"}, {"mace", "ready"},
                    {"lance", "recharging"}, {"lance", "ready"},
                    {"orb", "ready"}, {"orb", "out"}, {"orb", "recharging"},
                };
                for (String[] job : jobs) {
                    final String weapon = job[0];
                    final String state = job[1];
                    server.runOnServer(sv -> {
                        var p = sv.getPlayerManager().getPlayerList().get(0);
                        var w = p.getEntityWorld();
                        long now = w.getTime();
                        var reg = w.getRegistryManager();
                        net.minecraft.item.ItemStack st;
                        switch (weapon) {
                            case "greataxe" -> {
                                st = dev.tuffsmp.shrinecraft.item.MoltenGreataxeItem.createStack(reg);
                                if (state.equals("active")) dev.tuffsmp.shrinecraft.ability.Abilities.start(st, now - 3 * 20, 10 * 20, 60 * 20);
                                if (state.equals("recharging")) dev.tuffsmp.shrinecraft.ability.Abilities.start(st, now - 34 * 20, 10 * 20, 60 * 20);
                            }
                            case "cutlass" -> {
                                st = dev.tuffsmp.shrinecraft.item.CutlassOfDoomItem.createStack(reg);
                                if (state.equals("active")) dev.tuffsmp.shrinecraft.ability.Abilities.start(st, now - 2 * 20, 5 * 20, 50 * 20);
                                if (state.equals("recharging")) dev.tuffsmp.shrinecraft.ability.Abilities.start(st, now - 24 * 20, 5 * 20, 50 * 20);
                            }
                            case "mace" -> {
                                st = dev.tuffsmp.shrinecraft.item.SculkMaceItem.createStack(reg);
                                dev.tuffsmp.shrinecraft.item.SculkMaceItem.setTier(st, 5, reg);
                                if (state.equals("active")) dev.tuffsmp.shrinecraft.ability.Abilities.start(st, now - 5 * 20, 15 * 20, 60 * 20);
                                if (state.equals("recharging")) dev.tuffsmp.shrinecraft.ability.Abilities.start(st, now - 38 * 20, 15 * 20, 60 * 20);
                            }
                            case "lance" -> {
                                st = new net.minecraft.item.ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.CLOUD_LANCE);
                                if (state.equals("recharging")) st.set(dev.tuffsmp.shrinecraft.registry.ModComponents.DASH_READY_AT, java.util.List.of(now + 18 * 20L));
                            }
                            default -> {
                                st = new net.minecraft.item.ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.SILVERS_SPAWN_ORB);
                                var base = dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem.ensureId(st);
                                if (state.equals("out")) dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem.setData(st, base.released(java.util.UUID.randomUUID()));
                                if (state.equals("recharging")) dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem.setData(st, base.cooldown(now + 780 * 20L, 10.0F));
                            }
                        }
                        p.getInventory().clear();
                        p.getInventory().setStack(0, st);
                        p.getInventory().setSelectedSlot(0);
                    });
                    context.waitTicks(weapon.equals("greataxe") && state.equals("active") ? 70 : 45);   // the bar glides + fades in, the very first needs a bit longer
                    context.takeScreenshot("each_" + weapon + "_" + state);
                }
                server.runCommand("clear @p");
            });

            // ---- READY bar fades away: shown right after recharging, gone ~5 s later; comes back when the next recharge ends ----
            step("ability ready fade", () -> {
                server.runCommand("tp @p 0 -60 0 0 0");
                server.runCommand("clear @p");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = false; });
                server.runOnServer(sv -> {
                    var p = sv.getPlayerManager().getPlayerList().get(0);
                    var lance = new net.minecraft.item.ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.CLOUD_LANCE);
                    lance.set(dev.tuffsmp.shrinecraft.registry.ModComponents.DASH_READY_AT, java.util.List.of(p.getEntityWorld().getTime() + 3 * 20L));
                    p.getInventory().setStack(0, lance);
                    p.getInventory().setSelectedSlot(0);
                });
                context.waitTicks(20);
                context.takeScreenshot("fade_1_recharging");
                context.waitTicks(55);           // the 3 s recharge is over, READY just appeared
                context.takeScreenshot("fade_2_ready_shown");
                context.waitTicks(100);          // ~5 s later
                context.takeScreenshot("fade_3_gone");
                server.runCommand("clear @p");
            });

            // ---- GUI tour: creative menu, advancements, pause menu, options, crafting table, inventory + the HUD, in one theme (TUFF_GUI_THEME, default ocean) ----
            step("gui tour", () -> {
                GuiTheme theme = GuiTheme.byKey(System.getenv("TUFF_GUI_THEME") != null ? System.getenv("TUFF_GUI_THEME") : "ocean");
                String tag = theme.key;
                server.runCommand("tp @p 0 -60 0 0 0");
                server.runCommand("clear @p");
                server.runCommand("gamemode survival @p");
                server.runCommand("give @p minecraft:diamond_sword");
                server.runCommand("setblock 0 -60 2 minecraft:crafting_table");
                GuiSettings.setLocked(theme);
                context.runOnClient(c -> { c.options.getGuiScale().setValue(2); c.options.hudHidden = false; });
                context.waitTicks(10);
                context.takeScreenshot("gt_" + tag + "_hud");
                // crafting table
                server.runOnServer(srv -> {
                    ServerPlayerEntity p = srv.getPlayerManager().getPlayerList().get(0);
                    BlockPos pos = new BlockPos(0, -60, 2);
                    p.openHandledScreen(srv.getOverworld().getBlockState(pos).createScreenHandlerFactory(srv.getOverworld(), pos));
                });
                context.waitTicks(8);
                context.takeScreenshot("gt_" + tag + "_crafting");
                server.runOnServer(srv -> srv.getPlayerManager().getPlayerList().get(0).closeHandledScreen());
                context.waitTicks(4);
                // creative menu
                server.runCommand("gamemode creative @p");
                context.waitTicks(6);
                context.setScreen(() -> new net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen(net.minecraft.client.MinecraftClient.getInstance().player,
                    net.minecraft.client.MinecraftClient.getInstance().player.networkHandler.getEnabledFeatures(), true));
                context.waitTicks(10);
                context.takeScreenshot("gt_" + tag + "_creative");
                context.setScreen(() -> null);
                server.runCommand("gamemode survival @p");
                context.waitTicks(4);
                // advancements
                context.setScreen(() -> new net.minecraft.client.gui.screen.advancement.AdvancementsScreen(
                    net.minecraft.client.MinecraftClient.getInstance().player.networkHandler.getAdvancementHandler()));
                context.waitTicks(10);
                context.takeScreenshot("gt_" + tag + "_advancements");
                context.setScreen(() -> null);
                // pause menu + options
                context.setScreen(() -> new net.minecraft.client.gui.screen.GameMenuScreen(true));
                context.waitTicks(6);
                context.takeScreenshot("gt_" + tag + "_pause");
                // simulate OTHER MODS adding buttons the vanilla way (Mod Menu style: a wide button below; Replay Mod style: a small square one at the side)
                context.runOnClient(c -> {
                    var sc = c.currentScreen;
                    net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(sc).add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                        net.minecraft.text.Text.literal("Mods (test)"), b -> { }).dimensions(sc.width / 2 - 102, sc.height / 4 + 152, 204, 20).build());
                    net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(sc).add(net.minecraft.client.gui.widget.ButtonWidget.builder(
                        net.minecraft.text.Text.literal("R"), b -> { }).dimensions(sc.width / 2 + 110, sc.height / 4 + 56, 20, 20).build());
                });
                context.waitTicks(6);
                context.takeScreenshot("gt_" + tag + "_pause_mods");
                context.setScreen(() -> new net.minecraft.client.gui.screen.option.OptionsScreen(new net.minecraft.client.gui.screen.GameMenuScreen(true), net.minecraft.client.MinecraftClient.getInstance().options));
                context.waitTicks(6);
                context.takeScreenshot("gt_" + tag + "_options");
                context.setScreen(() -> null);
                GuiSettings.setOff();
                context.runOnClient(c -> c.options.getGuiScale().setValue(0));
                server.runCommand("setblock 0 -60 2 minecraft:air");
                server.runCommand("clear @p");
            });

            // ---- lance sweep: tries many LancePose rotations (TUFF_SWEEP="hold|jab", angles are "x,y,z;x,y,z;...") in ONE run, first person ----
            step("lance sweep", () -> {
                String mode = System.getenv("TUFF_SWEEP") != null ? System.getenv("TUFF_SWEEP") : "jab";
                String[] sets = (System.getenv("TUFF_SWEEP_SETS") != null ? System.getenv("TUFF_SWEEP_SETS") : "0,0,0").split(";");
                server.runCommand("tp @p 0 -60 0 0 0");
                server.runCommand("clear @p");
                server.runCommand("item replace entity @p weapon.mainhand with shrinecraft:cloud_lance");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = false; });
                context.waitTicks(30);
                int n = 0;
                for (String set : sets) {
                    String[] a = set.split(",");
                    float[] deg = {Float.parseFloat(a[0]), Float.parseFloat(a[1]), Float.parseFloat(a[2])};
                    if (mode.equals("hold")) dev.tuffsmp.shrinecraft.client.render.LancePose.HOLD = deg; else dev.tuffsmp.shrinecraft.client.render.LancePose.JAB = deg;
                    if (mode.equals("hold")) {
                        context.getInput().holdMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        context.waitTicks(14);
                        context.takeScreenshot("sw_" + mode + "_" + (n++));
                        context.getInput().releaseMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        context.waitTicks(25);
                    } else {
                        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                        context.waitTicks(5);
                        context.takeScreenshot("sw_" + mode + "_" + (n++));
                        context.waitTicks(30);
                    }
                }
                server.runCommand("clear @p");
            });

            // ---- lance poses: idle / charging (right mouse held) / jab (left click), our lance vs the vanilla netherite spear, 3 camera views ----
            step("lance poses", () -> {
                boolean fast = System.getenv("TUFF_LP_FAST") != null;         // only our lance, only the front view
                String[] ids = fast ? new String[] {"shrinecraft:cloud_lance"} : new String[] {"shrinecraft:cloud_lance", "minecraft:netherite_spear"};
                Perspective[] views = fast ? new Perspective[] {Perspective.THIRD_PERSON_FRONT} : new Perspective[] {Perspective.FIRST_PERSON, Perspective.THIRD_PERSON_FRONT, Perspective.THIRD_PERSON_BACK};
                String[] viewNames = fast ? new String[] {"front"} : new String[] {"first", "front", "back"};
                server.runCommand("tp @p 0 -60 0 0 0");
                for (String id : ids) {
                    server.runCommand("clear @p");
                    server.runCommand("item replace entity @p weapon.mainhand with " + id);
                    String tag = id.substring(id.indexOf(':') + 1);
                    for (int v = 0; v < views.length; v++) {
                        final Perspective pv = views[v];
                        context.runOnClient(c -> { c.options.setPerspective(pv); c.options.hudHidden = false; });
                        context.waitTicks(45);          // long enough for the equip animation to finish
                        context.takeScreenshot("lp_" + tag + "_" + viewNames[v] + "_idle");
                        context.getInput().holdMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        context.waitTicks(14);
                        context.takeScreenshot("lp_" + tag + "_" + viewNames[v] + "_charge");
                        context.getInput().releaseMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
                        context.waitTicks(20);
                        context.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
                        context.waitTicks(2);
                        context.takeScreenshot("lp_" + tag + "_" + viewNames[v] + "_jab");
                        context.waitTicks(30);
                    }
                }
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = false; });
                server.runCommand("clear @p");
            });

            // ---- typed commands: /tuffsmp gui <biome> must work, and normal /tuffsmp server commands must still reach the server ----
            step("gui command", () -> {
                server.runCommand("clear @p");
                String[][] cases = {{"tuffsmp gui lava", "LOCKED:lava"}, {"tuffsmp gui ocean", "LOCKED:ocean"},
                    {"gui sculk", "LOCKED:sculk"}, {"tuffsmp gui off", "OFF:sculk"}};
                for (String[] c : cases) {
                    context.runOnClient(mc -> mc.player.networkHandler.sendChatCommand(c[0]));
                    context.waitTicks(3);
                    String got = GuiSettings.mode() + ":" + GuiSettings.lockedTheme().key;
                    if (!got.equals(c[1])) {
                        throw new AssertionError("'/" + c[0] + "' gave " + got + " but expected " + c[1]);
                    }
                }
                // the server's /tuffsmp needs permission level 2 (the "op" command is not available on this test server)
                server.runOnServer(sv -> sv.getPlayerManager().addToOperators(sv.getPlayerManager().getPlayerList().get(0).getPlayerConfigEntry()));
                context.waitTicks(4);
                context.runOnClient(mc -> mc.player.networkHandler.sendChatCommand("tuffsmp essence @p 3"));
                context.waitTicks(6);
                boolean has = server.computeOnServer(sv -> sv.getPlayerManager().getPlayerList().get(0).getInventory()
                    .count(dev.tuffsmp.shrinecraft.registry.ModItems.MAGIC_ESSENCE) == 3);
                if (!has) {
                    throw new AssertionError("'/tuffsmp essence' did not reach the server (client command swallowed it)");
                }
                server.runCommand("clear @p");
                server.runOnServer(sv -> sv.getPlayerManager().removeFromOperators(sv.getPlayerManager().getPlayerList().get(0).getPlayerConfigEntry()));
                GuiSettings.setOff();
                System.out.println("GUI COMMAND TEST OK");
            });


            // ---- inventory GUI with every item (vanilla + the five themes) ----
            step("inventory", () -> {
                server.runCommand("clear @p");
                for (String id : ITEMS) {
                    server.runCommand("give @p shrinecraft:" + id);
                }
                server.runCommand("give @p shrinecraft:shrine");
                context.runOnClient(c -> c.options.getGuiScale().setValue(4));
                context.waitTicks(4);
                context.setScreen(() -> new InventoryScreen(net.minecraft.client.MinecraftClient.getInstance().player));
                context.waitTicks(8);
                context.takeScreenshot("c_gui_vanilla");
                for (GuiTheme theme : GuiTheme.values()) {
                    GuiSettings.setLocked(theme);
                    context.waitTicks(3);
                    context.takeScreenshot("c_gui_" + theme.key);
                }
                GuiSettings.setOff();
                context.setScreen(() -> null);
                context.runOnClient(c -> c.options.getGuiScale().setValue(0));
            });

            // ---- the themed chest screens (6-row double chest, 3-row single chest) in every theme ----
            step("chest gui", () -> {
                server.runCommand("clear @p");
                server.runCommand("setblock 0 -60 2 minecraft:chest[type=left,facing=south]");
                server.runCommand("setblock 1 -60 2 minecraft:chest[type=right,facing=south]");
                server.runCommand("setblock -3 -60 2 minecraft:chest[facing=south]");
                context.runOnClient(c -> c.options.getGuiScale().setValue(3));
                for (GuiTheme theme : GuiTheme.values()) {
                    GuiSettings.setLocked(theme);
                    for (String which : new String[] {"double", "single"}) {
                        final BlockPos pos = which.equals("double") ? new BlockPos(0, -60, 2) : new BlockPos(-3, -60, 2);
                        server.runOnServer(srv -> {
                            ServerPlayerEntity p = srv.getPlayerManager().getPlayerList().get(0);
                            net.minecraft.block.BlockState st = srv.getOverworld().getBlockState(pos);
                            p.openHandledScreen(st.createScreenHandlerFactory(srv.getOverworld(), pos));
                        });
                        context.waitTicks(8);
                        context.takeScreenshot("h_chest_" + theme.key + "_" + which);
                        server.runOnServer(srv -> srv.getPlayerManager().getPlayerList().get(0).closeHandledScreen());
                        context.waitTicks(4);
                    }
                }
                GuiSettings.setOff();
                context.runOnClient(c -> c.options.getGuiScale().setValue(0));
                server.runCommand("fill -3 -60 2 1 -60 2 minecraft:air");
            });

            // ---- the other container screens, themed (End and Frost) ----
            step("other screens", () -> {
                String[] blocks = {"crafting_table", "furnace", "dispenser", "hopper", "shulker_box", "anvil", "brewing_stand", "enchanting_table",
                    "grindstone", "loom", "stonecutter", "cartography_table", "smithing_table", "blast_furnace"};
                context.runOnClient(c -> c.options.getGuiScale().setValue(3));
                for (GuiTheme theme : GuiTheme.values()) {
                    GuiSettings.setLocked(theme);
                    for (String b : blocks) {
                        server.runCommand("setblock 0 -60 2 minecraft:" + b);
                        final BlockPos pos = new BlockPos(0, -60, 2);
                        server.runOnServer(srv -> {
                            ServerPlayerEntity p = srv.getPlayerManager().getPlayerList().get(0);
                            net.minecraft.block.BlockState st = srv.getOverworld().getBlockState(pos);
                            var f = st.createScreenHandlerFactory(srv.getOverworld(), pos);
                            if (f != null) p.openHandledScreen(f);
                        });
                        context.waitTicks(6);
                        context.takeScreenshot("k_" + theme.key + "_" + b);
                        server.runOnServer(srv -> srv.getPlayerManager().getPlayerList().get(0).closeHandledScreen());
                        context.waitTicks(3);
                    }
                }
                GuiSettings.setOff();
                context.runOnClient(c -> c.options.getGuiScale().setValue(0));
                server.runCommand("setblock 0 -60 2 minecraft:air");
            });

            // ---- worn armor (front, back) and the cape ----
            step("armor", () -> {
                server.runCommand("clear @p");
                server.runCommand("item replace entity @p armor.head with shrinecraft:shrine_helmet");
                server.runCommand("item replace entity @p armor.chest with shrinecraft:shrine_chestplate");
                server.runCommand("item replace entity @p armor.legs with shrinecraft:shrine_leggings");
                server.runCommand("item replace entity @p armor.feet with shrinecraft:shrine_boots");
                server.runCommand("tp @p 0 -60 0 0 8");
                context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
                context.waitTicks(8);
                context.takeScreenshot("d_armor_front");
                context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
                context.waitTicks(8);
                context.takeScreenshot("d_armor_back");
                server.runCommand("item replace entity @p armor.chest with shrinecraft:strange_cape");
                context.waitTicks(8);
                context.takeScreenshot("d_cape_back");
                context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
                context.waitTicks(6);
                context.takeScreenshot("d_cape_front");
                context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                server.runCommand("clear @p");
            });

            // ---- FULL VIEW of every model (not held): the model floats in space with NO display transform, seen from 4 sides ----
            step("model views", () -> {
                server.runCommand("clear @p");
                server.runCommand("fill -9 -62 6 9 -52 6 minecraft:light_gray_concrete");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = true; });
                server.runCommand("tp @p 0 -60 0 0 0");
                int[] yaws = {180, 135, 90, 0};
                int[] bowYaws = {90, 135, 180, 270};            // the bow lies in the Y/Z plane: its broad side is seen from the side
                String[] names = {"front", "threequarter", "side", "back"};
                java.util.List<String[]> jobs = new java.util.ArrayList<>();
                String onlyModel = System.getenv("TUFF_MODEL");
                for (String id : ITEMS) if (onlyModel == null || java.util.Arrays.asList(onlyModel.split(",")).contains(id)) jobs.add(new String[] {id, "", id});
                if (onlyModel == null || onlyModel.contains("silvers_spawn_orb")) for (String st : new String[] {"released", "cooldown"}) jobs.add(new String[] {"silvers_spawn_orb", st, "silvers_spawn_orb_" + st});
                for (String[] job : jobs) {
                    for (int k = 0; k < yaws.length; k++) {
                        if (!job[1].isEmpty() && k > 0) continue;             // orb states: front view only
                        int yawNow = (job[0].equals("frost_longbow") || job[0].equals("sculk_cannon")) ? bowYaws[k] : yaws[k];
                        server.runCommand("kill @e[type=minecraft:item_display]");
                        String comp = job[1].isEmpty() ? "" : ",components:{\"minecraft:custom_model_data\":{strings:[\"" + job[1] + "\"]}}";
                        float pitch = k == 1 ? 12f : 0f;
                        String[] fit = MODEL_FIT.getOrDefault(job[0], new String[] {"2.5", "0"});      // framing scale + height offset per model (see MODEL_FIT)
                        String sc = fit[0];
                        String ty = fit[1];
                        server.runCommand("summon minecraft:item_display 0 -58.4 3.4 {item:{id:\"shrinecraft:" + job[0] + "\",count:1" + comp + "},item_display:\"none\","
                            + "Rotation:[" + yawNow + ".0f," + pitch + "f],brightness:{sky:15,block:15},"
                            + "transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],scale:[" + sc + "f," + sc + "f," + sc + "f],translation:[0f," + ty + "f,0f]}}");
                        context.waitTicks(4);
                        context.takeScreenshot("mv_" + job[2] + "_" + names[k]);
                    }
                }
                server.runCommand("kill @e[type=minecraft:item_display]");
                context.runOnClient(c -> c.options.hudHidden = false);
                server.runCommand("fill -9 -62 6 9 -52 6 minecraft:air");
            });

            // ---- Sculk Cannon: NOT charged (hangs down) vs CHARGED (level), mannequins + first person ----
            step("cannon states", () -> {
                server.runCommand("clear @p");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = true; });
                String idle = "shrinecraft:sculk_cannon";
                String charged = "shrinecraft:sculk_cannon[charged_projectiles=[{id:\"minecraft:arrow\",count:1}]]";
                String[] yawNames = {"front", "threequarter", "side"};
                int[] yaws = {180, 135, 90};
                for (int r = 0; r < yaws.length; r++) {
                    server.runCommand("kill @e[type=minecraft:mannequin]");
                    server.runCommand("tp @p 0 -60 0 0 0");
                    context.waitTicks(12);
                    server.runCommand("summon minecraft:mannequin -0.9 -60 3.2 {Rotation:[" + yaws[r] + ".0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{mainhand:{id:\"" + idle.replace("shrinecraft:", "shrinecraft:") + "\",count:1}}}");
                    server.runCommand("summon minecraft:mannequin 0.9 -60 3.2 {Rotation:[" + yaws[r] + ".0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{mainhand:{id:\"shrinecraft:sculk_cannon\",count:1,components:{\"minecraft:charged_projectiles\":[{id:\"minecraft:arrow\",count:1}]}}}}");
                    context.waitTicks(10);
                    context.takeScreenshot("cs_mannequins_" + yawNames[r]);
                }
                server.runCommand("kill @e[type=minecraft:mannequin]");
                context.runOnClient(c -> c.options.hudHidden = false);        // the first-person hand is not drawn while the HUD is hidden
                server.runCommand("item replace entity @p weapon.mainhand with " + idle);
                context.waitTicks(45);
                context.takeScreenshot("cs_1p_idle");
                context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
                context.waitTicks(6);
                context.takeScreenshot("cs_3p_idle");
                context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                server.runCommand("item replace entity @p weapon.mainhand with " + charged);
                context.waitTicks(45);
                context.takeScreenshot("cs_1p_charged");
                context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT));
                context.waitTicks(6);
                context.takeScreenshot("cs_3p_charged");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = false; });
            });

            // ---- Sculk Cannon AIM check: vanilla charged crossbow (left) vs charged cannon (right), mannequins facing +z (south), seen from straight above and from the side ----
            step("cannon aim", () -> {
                server.runCommand("clear @p");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = true; });
                String vanillaCharged = "{id:\"minecraft:crossbow\",count:1,components:{\"minecraft:charged_projectiles\":[{id:\"minecraft:arrow\",count:1}]}}";
                String cannonCharged = "{id:\"shrinecraft:sculk_cannon\",count:1,components:{\"minecraft:charged_projectiles\":[{id:\"minecraft:arrow\",count:1}]}}";
                server.runCommand("kill @e[type=minecraft:mannequin]");
                server.runCommand("gamemode spectator @p");
                server.runCommand("summon minecraft:mannequin 1.2 -60 3.2 {Rotation:[0.0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{mainhand:" + vanillaCharged + "}}");
                server.runCommand("summon minecraft:mannequin -1.2 -60 3.2 {Rotation:[0.0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{mainhand:" + cannonCharged + "}}");
                // from straight above (image top = south = the way the mannequins face)
                server.runCommand("tp @p 0 -52 3.2 0 90");
                context.waitTicks(20);
                context.takeScreenshot("ca_top");
                // from the side, level with their shoulders (they face south, camera stands to the west looking east)
                server.runCommand("tp @p -5 -58.6 3.2 -90 0");
                context.waitTicks(20);
                context.takeScreenshot("ca_side");
                // from behind (looking south, the way they face) so left/right and up/down aim errors show
                server.runCommand("tp @p 0 -58.6 -1.5 0 0");
                context.waitTicks(20);
                context.takeScreenshot("ca_behind");
                server.runCommand("kill @e[type=minecraft:mannequin]");
                server.runCommand("gamemode creative @p");
                context.runOnClient(c -> c.options.hudHidden = false);
            });

            // ---- the altar (Shrine) set to the Molten Greataxe: from the front, close up, from the side, and the requirement list in chat ----
            step("altar greataxe", () -> {
                server.runCommand("clear @p");
                server.runCommand("setblock 0 -60 3 shrinecraft:shrine");
                server.runOnServer(s -> ((ShrineBlockEntity) s.getOverworld().getBlockEntity(new BlockPos(0, -60, 3))).configure("molten_greataxe"));
                context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                server.runCommand("tp @p 0 -60 -1 0 12");
                context.waitTicks(25);
                context.takeScreenshot("s_altar_front");
                server.runCommand("tp @p 0.6 -59.2 1.3 0 -2");
                context.waitTicks(10);
                context.takeScreenshot("s_altar_close");
                server.runCommand("tp @p -3.2 -59.4 -0.6 -52 12");
                context.waitTicks(10);
                context.takeScreenshot("s_altar_side");
                server.runCommand("tp @p 0 -60 -1 0 12");
                context.waitTicks(6);
                server.runOnServer(s -> dev.tuffsmp.shrinecraft.shrine.ShrineInteraction.handleUse(s.getOverworld(), new BlockPos(0, -60, 3), s.getPlayerManager().getPlayerList().get(0)));
                context.waitTicks(8);
                context.takeScreenshot("s_altar_requirements");
                server.runCommand("setblock 0 -60 3 minecraft:air");
            });

            // ---- held items from the front / 3-4 / side on player-shaped mannequins: vanilla crossbow next to the cannon, and the lamp ----
            step("held compare", () -> {
                server.runCommand("clear @p");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = true; });
                String[][] sets = {{"minecraft:crossbow", "shrinecraft:sculk_cannon"}, {"shrinecraft:silvers_spawn_orb", "shrinecraft:silvers_spawn_orb"}};
                String[] names = {"crossbow", "lamp"};
                int[] rots = {180, 135, 90, 45};
                String[] rn = {"front", "threequarter", "side", "back34"};
                for (int si = 0; si < sets.length; si++) {
                    for (int r = 0; r < rots.length; r++) {
                        server.runCommand("kill @e[type=minecraft:mannequin]");
                        server.runCommand("tp @p 0 -60 0 0 0");
                        context.waitTicks(12);
                        for (int k = 0; k < 2; k++) {
                            if (si == 1 && k == 0) continue;           // the lamp: one mannequin, centred
                            double x = si == 1 ? 0.0 : (0.9 - k * 1.8);
                            server.runCommand("summon minecraft:mannequin " + x + " -60 3.2 {Rotation:[" + rots[r] + ".0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{mainhand:{id:\"" + sets[si][k] + "\",count:1}}}");
                        }
                        context.waitTicks(10);
                        context.takeScreenshot("hc_" + names[si] + "_" + rn[r]);
                    }
                }
                server.runCommand("kill @e[type=minecraft:mannequin]");
                context.runOnClient(c -> c.options.hudHidden = false);
            });

            // ---- the Shrine, three states ----
            step("shrine", () -> {
                server.runCommand("tp @p 0 -60 0 0 22");
                server.runCommand("setblock 0 -60 3 shrinecraft:shrine");
                context.waitTicks(10);
                context.takeScreenshot("e_shrine_unconfigured");
                server.runOnServer(s -> ((ShrineBlockEntity) s.getOverworld().getBlockEntity(new BlockPos(0, -60, 3))).configure("molten_greataxe"));
                context.waitTicks(20);
                context.takeScreenshot("e_shrine_configured");
                server.runOnServer(s -> ((ShrineBlockEntity) s.getOverworld().getBlockEntity(new BlockPos(0, -60, 3))).configure("cloud_lance"));
                context.waitTicks(25);
                context.takeScreenshot("e_shrine_configured_lance");
                server.runOnServer(s -> ((ShrineBlockEntity) s.getOverworld().getBlockEntity(new BlockPos(0, -60, 3))).claim(java.util.UUID.randomUUID()));
                context.waitTicks(10);
                context.takeScreenshot("e_shrine_claimed");
                server.runCommand("setblock 0 -60 3 minecraft:air");
            });

            // ---- Silver ----
            // ---- every item big in the inventory (gui context) ----
            step("gui zoom", () -> {
                for (String id : ITEMS) {
                    final ItemStack st = new ItemStack(net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("shrinecraft", id)));
                    context.setScreen(() -> new BigItemScreen(st));
                    context.waitTicks(4);
                    context.takeScreenshot("z_gui_" + id);
                }
                context.setScreen(() -> null);
            });

            // ---- every item lying on the ground, close up ----
            step("ground close", () -> {
                server.runCommand("clear @p");
                server.runCommand("fill -4 -60 -2 4 -58 6 minecraft:air");
                String onlyGround = System.getenv("TUFF_MODEL");
                for (String id : ITEMS) {
                    if (onlyGround != null && !java.util.Arrays.asList(onlyGround.split(",")).contains(id)) continue;
                    server.runCommand("kill @e[type=item]");
                    server.runCommand("tp @p 0 -60 0 0 40");
                    server.runCommand("summon minecraft:item 0 -60 1.6 {Item:{id:\"shrinecraft:" + id + "\",count:1},PickupDelay:32767s,Age:-32768s,Motion:[0.0,0.0,0.0]}");
                    context.waitTicks(6);
                    context.takeScreenshot("y_ground_" + id);
                }
                server.runCommand("kill @e[type=item]");
            });

            // ---- worn armor close up, front and back, on a player-shaped mannequin ----
            step("worn closeup", () -> {
                server.runCommand("clear @p");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = true; });
                String[][] outfits = {
                    {"outfit", "shrine_helmet", "shrine_chestplate", "shrine_leggings", "shrine_boots"},
                    {"cape", "", "strange_cape", "", ""}};
                for (String[] o : outfits) {
                    for (int rot : new int[] {180, 140, 90, 0}) {
                        server.runCommand("tp @e[type=minecraft:mannequin] 0 -300 0");    // (no kill here: the death puff would be in the next shot)
                        server.runCommand("tp @p 0 -60 0.4 0 8");
                        context.waitTicks(12);
                        StringBuilder eq = new StringBuilder();
                        String[] slots = {"head", "chest", "legs", "feet"};
                        for (int i = 0; i < 4; i++) {
                            if (!o[i + 1].isEmpty()) eq.append(slots[i]).append(":{id:\"shrinecraft:").append(o[i + 1]).append("\",count:1},");
                        }
                        server.runCommand("summon minecraft:mannequin 0 -60 3.4 {Rotation:[" + rot + ".0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{" + eq + "}}");
                        context.waitTicks(10);
                        context.takeScreenshot("w_" + o[0] + (rot == 180 ? "_front" : rot == 0 ? "_back" : rot == 90 ? "_side" : "_angle"));
                    }
                }
                // night shots: the tuff armor's veins and crystals must still shine in the dark (glow pass = RenderLayers.eyes)
                server.runCommand("time set midnight");
                for (int rot : new int[] {180, 140}) {
                    server.runCommand("tp @e[type=minecraft:mannequin] 0 -300 0");
                    server.runCommand("tp @p 0 -60 0.4 0 8");
                    context.waitTicks(12);
                    server.runCommand("summon minecraft:mannequin 0 -60 3.4 {Rotation:[" + rot + ".0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{head:{id:\"shrinecraft:shrine_helmet\",count:1},chest:{id:\"shrinecraft:shrine_chestplate\",count:1},legs:{id:\"shrinecraft:shrine_leggings\",count:1},feet:{id:\"shrinecraft:shrine_boots\",count:1}}}");
                    context.waitTicks(10);
                    context.takeScreenshot("w_night" + (rot == 180 ? "_front" : "_angle"));
                }
                server.runCommand("time set noon");
                server.runCommand("kill @e[type=minecraft:mannequin]");
                context.runOnClient(c -> c.options.hudHidden = false);
            });


            // ---- armor TRIMS on the Shrine armor (owner: "make tuff armor trimable"): two different trims on all four pieces, 3 views each ----
            step("worn trim", () -> {
                server.runCommand("clear @p");
                context.runOnClient(c -> { c.options.setPerspective(Perspective.FIRST_PERSON); c.options.hudHidden = true; });
                String[][] trims = {{"sentry", "gold"}, {"coast", "amethyst"}};
                for (String[] t : trims) {
                    for (int rot : new int[] {180, 140, 0}) {
                        server.runCommand("tp @e[type=minecraft:mannequin] 0 -300 0");
                        server.runCommand("tp @p 0 -60 0.4 0 8");
                        context.waitTicks(12);
                        String tr = "components:{\"minecraft:trim\":{pattern:\"minecraft:" + t[0] + "\",material:\"minecraft:" + t[1] + "\"}}";
                        server.runCommand("summon minecraft:mannequin 0 -60 3.4 {Rotation:[" + rot + ".0f,0.0f],NoAI:1b,Invulnerable:1b,equipment:{"
                            + "head:{id:\"shrinecraft:shrine_helmet\",count:1," + tr + "},chest:{id:\"shrinecraft:shrine_chestplate\",count:1," + tr + "},"
                            + "legs:{id:\"shrinecraft:shrine_leggings\",count:1," + tr + "},feet:{id:\"shrinecraft:shrine_boots\",count:1," + tr + "}}}");
                        context.waitTicks(10);
                        context.takeScreenshot("t_" + t[0] + "_" + t[1] + (rot == 180 ? "_front" : rot == 0 ? "_back" : "_angle"));
                    }
                }
                server.runCommand("kill @e[type=minecraft:mannequin]");
                context.runOnClient(c -> c.options.hudHidden = false);
            });

            // ---- the orb's three looks: horse inside / Silver released / recharging ----
            step("orb states", () -> {
                server.runCommand("tp @p 0 -60 0 0 8");
                context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
                for (String st : new String[] {"stored", "released", "cooldown"}) {
                    server.runOnServer(srv -> {
                        ServerPlayerEntity p = srv.getPlayerManager().getPlayerList().get(0);
                        ItemStack orb = new ItemStack(ModItems.SILVERS_SPAWN_ORB);
                        OrbData d = OrbData.DEFAULT.withId(java.util.UUID.randomUUID());
                        if (st.equals("released")) d = d.released(java.util.UUID.randomUUID());
                        if (st.equals("cooldown")) d = d.cooldown(srv.getOverworld().getTime() + 100000L, 10.0F);
                        com.google.common.base.Preconditions.checkNotNull(p);
                        dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem.setData(orb, d);
                        p.equipStack(net.minecraft.entity.EquipmentSlot.MAINHAND, orb);
                    });
                    context.waitTicks(25);
                    context.takeScreenshot("o_orb_" + st);
                }
            });

            step("silver", () -> {
                server.runCommand("tp @p 0 -60 0 0 8");
                server.runOnServer(s -> {
                    ServerPlayerEntity p = s.getPlayerManager().getPlayerList().get(0);
                    ItemStack orb = new ItemStack(ModItems.SILVERS_SPAWN_ORB);
                    SilverManager.summon(s.getOverworld(), p, orb, OrbData.DEFAULT.withId(java.util.UUID.randomUUID()));
                });
                context.runOnClient(c -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK));
                context.waitTicks(20);
                context.takeScreenshot("f_silver");
                context.runOnClient(c -> c.options.setPerspective(Perspective.FIRST_PERSON));
            });
        }
        if (!problems.isEmpty()) {
            throw new AssertionError("Client game test problems:\n" + String.join("\n", problems));
        }
    }

    /** A bare screen that draws one item stack very large in an inventory-slot style (the 'gui' display context, zoomed in). */
    private static final class BigItemScreen extends Screen {
        private final ItemStack stack;

        BigItemScreen(ItemStack stack) {
            super(Text.literal("item"));
            this.stack = stack;
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(0, 0, this.width, this.height, 0xFF8B8B8B);
            int size = Math.min(this.width, this.height) * 3 / 4;
            float scale = size / 16.0F;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate((this.width - size) / 2.0F, (this.height - size) / 2.0F);
            ctx.getMatrices().scale(scale, scale);
            ctx.drawItem(this.stack, 0, 0);
            ctx.getMatrices().popMatrix();
        }
    }
}
