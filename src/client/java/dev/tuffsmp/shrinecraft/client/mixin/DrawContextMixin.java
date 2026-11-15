package dev.tuffsmp.shrinecraft.client.mixin;

import dev.tuffsmp.shrinecraft.client.gui.GuiSettings;
import dev.tuffsmp.shrinecraft.client.gui.GuiTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.OrderedText;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * One hook for ALL the other container screens (crafting table, furnace, dispenser, hopper, shulker box, anvil, brewing stand, enchanting
 * table, villager ...): every drawTexture overload ends in this method, so when the texture being drawn is one of vanilla's
 * textures/gui/container/*.png that we have a themed copy of, the themed copy is drawn instead. Same size and layout as vanilla, so nothing
 * else changes. (The inventory and chest panels have their own mixins because they use the owner's full artwork.)
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @ModifyVariable(
        method = "drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V",
        at = @At("HEAD"),
        argsOnly = true
    )
    private Identifier shrinecraft$themedContainer(Identifier texture) {
        GuiTheme theme = GuiSettings.currentTheme();
        return theme == null ? texture : theme.themedVersionOf(texture);
    }

    /**
     * Every GUI SPRITE (buttons, text fields, sliders, tooltips, hotbar, advancement widgets, recipe book, creative tabs, toasts ...) goes through
     * one of the drawGuiTexture overloads; the first Identifier argument is swapped for the theme's twin (same size and 9-slice, recoloured).
     */
    @ModifyVariable(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V", at = @At("HEAD"), argsOnly = true)
    private Identifier shrinecraft$themedSpriteA(Identifier sprite) {
        GuiTheme theme = GuiSettings.currentTheme();
        return theme == null ? sprite : theme.themedSprite(sprite);
    }

    @ModifyVariable(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIF)V", at = @At("HEAD"), argsOnly = true)
    private Identifier shrinecraft$themedSpriteB(Identifier sprite) {
        GuiTheme theme = GuiSettings.currentTheme();
        return theme == null ? sprite : theme.themedSprite(sprite);
    }

    @ModifyVariable(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V", at = @At("HEAD"), argsOnly = true)
    private Identifier shrinecraft$themedSpriteC(Identifier sprite) {
        GuiTheme theme = GuiSettings.currentTheme();
        return theme == null ? sprite : theme.themedSprite(sprite);
    }

    @ModifyVariable(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIIIII)V", at = @At("HEAD"), argsOnly = true)
    private Identifier shrinecraft$themedSpriteD(Identifier sprite) {
        GuiTheme theme = GuiSettings.currentTheme();
        return theme == null ? sprite : theme.themedSprite(sprite);
    }

    @ModifyVariable(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIIIIIII)V", at = @At("HEAD"), argsOnly = true)
    private Identifier shrinecraft$themedSpriteE(Identifier sprite) {
        GuiTheme theme = GuiSettings.currentTheme();
        return theme == null ? sprite : theme.themedSprite(sprite);
    }

    /** Pause menu: report every button sprite that is drawn, so MenuPanel can size its frame round buttons other mods draw themselves. */
    @Inject(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIIII)V", at = @At("HEAD"))
    private void shrinecraft$trackButtons(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, int color, CallbackInfo ci) {
        if (sprite.getPath().contains("widget/button") && MinecraftClient.getInstance().currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
            dev.tuffsmp.shrinecraft.client.gui.MenuPanel.track(x, y, width, height);
        }
    }

    @Inject(method = "drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V", at = @At("HEAD"))
    private void shrinecraft$trackButtons4(com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, CallbackInfo ci) {
        if (sprite.getPath().contains("widget/button") && MinecraftClient.getInstance().currentScreen instanceof net.minecraft.client.gui.screen.GameMenuScreen) {
            dev.tuffsmp.shrinecraft.client.gui.MenuPanel.track(x, y, width, height);
        }
    }

    /**
     * Vanilla draws every container title / label ("Crafting", "Furnace", "Inventory", "Repair & Name" ...) in dark grey (#404040), which is
     * unreadable on the dark themes. While a themed container screen is open, that one colour is drawn light with a dark outline instead.
     * (The creative inventory is themed too now.)
     */
    @Inject(
        method = "drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;IIIZ)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void shrinecraft$readableLabels(TextRenderer font, OrderedText text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if ((color & 0xFFFFFF) != 0x404040) {
            return;
        }
        GuiTheme theme = GuiSettings.currentTheme();
        var screen = MinecraftClient.getInstance().currentScreen;
        if (theme == null || !(screen instanceof HandledScreen<?>)) {
            return;
        }
        DrawContext self = (DrawContext) (Object) this;
        for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
            self.drawText(font, text, x + d[0], y + d[1], theme.outlineColor, false);
        }
        self.drawText(font, text, x, y, theme.textColor, false);
        ci.cancel();
    }
}
