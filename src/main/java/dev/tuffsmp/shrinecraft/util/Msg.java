package dev.tuffsmp.shrinecraft.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Tiny chat helpers: coloured text, action-bar messages. */
public final class Msg {
    private Msg() {}

    public static MutableText colored(String text, Formatting color) {
        return Text.literal(text).formatted(color);
    }

    /** Short message above the hotbar. */
    public static void actionBar(PlayerEntity player, Text text) {
        player.sendMessage(text, true);
    }

    /** Normal chat line. */
    public static void chat(PlayerEntity player, Text text) {
        player.sendMessage(text, false);
    }

    public static String seconds(long ticks) {
        return String.format("%.1fs", Math.max(0, ticks) / 20.0);
    }
}
