package dev.tuffsmp.shrinecraft.client.mixin;

import dev.tuffsmp.shrinecraft.client.gui.GuiSettings;
import dev.tuffsmp.shrinecraft.client.gui.GuiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla draws the screen titles ("Chest", "Inventory") in dark grey, which disappears on the dark themes. On themed chest screens
 * we draw them light with a dark outline instead (the survival inventory's "Crafting" label is handled in InventoryScreenMixin).
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {
    // dummy constructor: only so the mixin can use Screen's protected fields (title, textRenderer)
    private HandledScreenMixin(Text title) {
        super(title);
    }

    @Shadow protected int titleX;
    @Shadow protected int titleY;
    @Shadow protected int playerInventoryTitleX;
    @Shadow protected int playerInventoryTitleY;
    @Shadow protected Text playerInventoryTitle;

    @Inject(method = "drawForeground", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$readableTitles(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        GuiTheme theme = GuiSettings.currentTheme();
        if (theme == null || !((Object) this instanceof GenericContainerScreen)) {
            return;
        }
        theme.drawLabel(context, this.textRenderer, this.title, this.titleX, this.titleY);
        theme.drawLabel(context, this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY);
        ci.cancel();
    }
}
