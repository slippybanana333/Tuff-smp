package dev.tuffsmp.shrinecraft.client.gui;

import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * GUI theme commands (purely client-side: nothing is sent to the server, nothing is reloaded):
 *   /tuffsmp gui ocean | lava | sculk | arctic | end   lock the inventory/chest/container GUI to that biome theme
 *   /tuffsmp gui auto                                  AUTOMATIC mode: follows the five pie-slice zones (config/shrinecraft/zones.json, see ZoneDetector)
 *   /tuffsmp gui where                                 prints your x / z / distance / compass angle from the centre + the zone (for filling in zones.json)
 *   /tuffsmp gui reload                                re-reads zones.json
 *   /tuffsmp gui mark <theme> [radius] [name]          marks the island you stand on (nearest island within its edge + margin wins; see ZoneDetector)
 *   /tuffsmp gui unmark                                removes the island nearest to you
 *   /tuffsmp gui islands                               lists the marked islands
 *   /tuffsmp gui map [size] [blocks]                   draws the whole zone map (pie + islands + hub + you) to config/shrinecraft/zones_map.png
 *   /tuffsmp gui off                                   vanilla GUI (the default)
 *   /gui ...                                               short alias with the same words
 *
 * WHY "/tuffsmp gui" is caught as typed text instead of being a normal client command: a client command with the
 * root "tuffsmp" would hide the server's own /tuffsmp sub-commands (give, essence, reload ...) because Fabric
 * only passes a command on to the server when the client does not know the root word. Catching the typed text first
 * avoids that completely. (The server also lists "gui" in ShrineCommands purely so tab-completion offers it.)
 */
public final class GuiCommand {
    private GuiCommand() {}

    public static void register() {
        // /tuffsmp gui <word>  (works for everybody, op or not)
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            String[] w = command.trim().split("\\s+");
            if (w.length >= 2 && w[0].equalsIgnoreCase("tuffsmp") && w[1].equalsIgnoreCase("gui")) {
                apply(java.util.Arrays.copyOfRange(w, 2, w.length), GuiCommand::say);
                return false; // handled here, do not send to the server
            }
            return true;
        });

        // /gui <word>  (short alias, also gives tab-completion on the client)
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var gui = ClientCommandManager.literal("gui");
            for (String word : WORDS) {
                gui.then(ClientCommandManager.literal(word).executes(ctx -> {
                    apply(new String[] {word}, t -> ctx.getSource().sendFeedback(t));
                    return 1;
                }));
            }
            gui.then(ClientCommandManager.literal("islands").executes(ctx -> {
                apply(new String[] {"islands"}, t -> ctx.getSource().sendFeedback(t));
                return 1;
            }));
            gui.then(ClientCommandManager.literal("unmark").executes(ctx -> {
                apply(new String[] {"unmark"}, t -> ctx.getSource().sendFeedback(t));
                return 1;
            }));
            gui.then(ClientCommandManager.literal("map").executes(ctx -> {
                apply(new String[] {"map"}, t -> ctx.getSource().sendFeedback(t));
                return 1;
            }));
            var mark = ClientCommandManager.literal("mark");
            for (GuiTheme th : GuiTheme.values()) {
                mark.then(ClientCommandManager.literal(th.key)
                    .executes(ctx -> {
                        apply(new String[] {"mark", th.key}, t -> ctx.getSource().sendFeedback(t));
                        return 1;
                    })
                    .then(ClientCommandManager.argument("radius", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 20000))
                        .executes(ctx -> {
                            apply(new String[] {"mark", th.key, String.valueOf(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "radius"))},
                                t -> ctx.getSource().sendFeedback(t));
                            return 1;
                        })));
            }
            gui.then(mark);
            dispatcher.register(gui);
        });
    }

    /** Every accepted word: off, auto (+ the old name dynamic) and the five biome themes. */
    private static final java.util.List<String> WORDS = java.util.stream.Stream.concat(
        java.util.stream.Stream.of("off", "auto", "dynamic", "where", "reload"),
        java.util.Arrays.stream(GuiTheme.values()).map(t -> t.key)).toList();

    private static void say(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(text, false);
        }
    }

    private static void apply(String[] args, Consumer<Text> out) {
        String word = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        MinecraftClient mcc = MinecraftClient.getInstance();
        switch (word) {
            case "mark" -> {
                GuiTheme th = args.length >= 2 ? GuiTheme.byKey(args[1]) : null;
                if (th == null || mcc.player == null) {
                    out.accept(Text.literal("Use: /tuffsmp gui mark <ocean|lava|sculk|arctic|end> [radius] [name]  (stand in the middle of the island)").formatted(Formatting.RED));
                    return;
                }
                double radius = 150;
                int nameFrom = 2;
                if (args.length >= 3) {
                    try {
                        radius = Double.parseDouble(args[2]);
                        nameFrom = 3;
                    } catch (NumberFormatException ignored) {
                        // no radius given: the third word already belongs to the name
                    }
                }
                String name = args.length > nameFrom ? String.join(" ", java.util.Arrays.copyOfRange(args, nameFrom, args.length)) : null;
                out.accept(Text.literal(ZoneDetector.markIsland(th, mcc.player.getX(), mcc.player.getZ(), radius, name)).formatted(Formatting.GREEN));
            }
            case "unmark" -> {
                if (mcc.player != null) {
                    out.accept(Text.literal(ZoneDetector.unmarkNearest(mcc.player.getX(), mcc.player.getZ())).formatted(Formatting.YELLOW));
                }
            }
            case "islands" -> {
                java.util.List<String> lines = ZoneDetector.listIslands();
                out.accept(Text.literal(lines.isEmpty() ? "no islands marked yet - stand on one and use /tuffsmp gui mark <theme>" : lines.size() + " islands:").formatted(Formatting.AQUA));
                for (String l : lines) {
                    out.accept(Text.literal("  " + l).formatted(Formatting.GRAY));
                }
            }
            case "map" -> {
                int size = 900;
                double half = 0;
                if (args.length >= 2) {
                    try {
                        size = Math.max(200, Math.min(3000, Integer.parseInt(args[1])));
                    } catch (NumberFormatException ignored) {
                        // keep the default size
                    }
                }
                if (args.length >= 3) {
                    try {
                        half = Double.parseDouble(args[2]);       // optional: how many blocks from the centre to the edge of the picture
                    } catch (NumberFormatException ignored) {
                        // automatic extent
                    }
                }
                try {
                    double px = mcc.player != null ? mcc.player.getX() : 0, pz = mcc.player != null ? mcc.player.getZ() : 0;
                    java.nio.file.Path p = ZoneDetector.renderMap(size, half, px, pz);
                    out.accept(Text.literal("zone map drawn: " + p).formatted(Formatting.GREEN));
                } catch (Exception e) {
                    out.accept(Text.literal("could not draw the map: " + e.getMessage()).formatted(Formatting.RED));
                }
            }
            case "off" -> {
                GuiSettings.setOff();
                out.accept(Text.literal("GUI theme: vanilla").formatted(Formatting.GRAY));
            }
            case "auto", "dynamic" -> {
                // Follows the five pie-slice zones of config/shrinecraft/zones.json (see ZoneDetector). "dynamic" is the old name, kept working.
                GuiSettings.setDynamic();
                out.accept(Text.literal("GUI theme: AUTO - follows the zone you stand in (config/shrinecraft/zones.json). /tuffsmp gui where shows your position.").formatted(Formatting.AQUA));
            }
            case "where" -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) {
                    out.accept(Text.literal(ZoneDetector.describe(mc.player)).formatted(Formatting.AQUA));
                }
            }
            case "reload" -> out.accept(Text.literal(ZoneDetector.load()).formatted(Formatting.GREEN));
            default -> {
                GuiTheme theme = word.isEmpty() ? null : GuiTheme.byKey(word);
                if (theme == null) {
                    out.accept(Text.literal("Use: /tuffsmp gui <ocean|lava|sculk|arctic|end|auto|off|where|reload|mark|unmark|islands|map>").formatted(Formatting.RED));
                    return;
                }
                GuiSettings.setLocked(theme);
                out.accept(Text.literal("GUI theme set to: " + theme.key).formatted(Formatting.AQUA));
            }
        }
    }
}
