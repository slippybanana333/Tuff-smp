package dev.tuffsmp.shrinecraft.client.gui;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import net.minecraft.client.MinecraftClient;

/** Loads the five background textures once at startup so switching themes never loads anything (and never reloads resources). */
public final class GuiBackgrounds {
    private GuiBackgrounds() {}

    public static void preload(MinecraftClient client) {
        for (GuiTheme theme : GuiTheme.values()) {
            // getTexture() registers + uploads the png the first time it is asked for; later calls are free.
            client.getTextureManager().getTexture(theme.texture);
            client.getTextureManager().getTexture(theme.chestTexture);
            for (String screen : GuiTheme.THEMED_SCREENS) {
                client.getTextureManager().getTexture(theme.themedScreen(screen));
            }
        }
        ShrineCraft.LOGGER.info("GUI backgrounds preloaded ({} themes)", GuiTheme.values().length);
    }
}
