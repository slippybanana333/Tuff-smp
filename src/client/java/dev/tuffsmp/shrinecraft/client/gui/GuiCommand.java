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
 *   /tuffsmp gui off                                   vanilla GUI (the default)
 *   /gui ...                                               short alias with the same words
 *
 * There is NO "auto" any more: the old pie-slice zone following (ZoneDetector + config/shrinecraft/zones.json)
 * was removed on the owner's request - the GUI is either vanilla (off) or one locked biome theme.
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
            dispatcher.register(gui);
        });
    }

    /** Every accepted word: off and the five biome themes. */
    private static final java.util.List<String> WORDS = java.util.stream.Stream.concat(
        java.util.stream.Stream.of("off"),
        java.util.Arrays.stream(GuiTheme.values()).map(t -> t.key)).toList();

    private static void say(Text text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(text, false);
        }
    }

    private static void apply(String[] args, Consumer<Text> out) {
        String word = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (word) {
            case "off" -> {
                GuiSettings.setOff();
                out.accept(Text.literal("GUI theme: vanilla").formatted(Formatting.GRAY));
            }
            // "auto"/"dynamic" existed once (zone following); they are gone - fall through to the error line.
            default -> {
                GuiTheme theme = word.isEmpty() ? null : GuiTheme.byKey(word);
                if (theme == null) {
                    out.accept(Text.literal("Use: /tuffsmp gui <ocean|lava|sculk|arctic|end|off>").formatted(Formatting.RED));
                    return;
                }
                GuiSettings.setLocked(theme);
                out.accept(Text.literal("GUI theme set to: " + theme.key).formatted(Formatting.AQUA));
            }
        }
    }
}
