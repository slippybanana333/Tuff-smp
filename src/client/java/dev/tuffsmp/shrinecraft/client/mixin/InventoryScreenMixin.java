package dev.tuffsmp.shrinecraft.client.mixin;

import dev.tuffsmp.shrinecraft.client.gui.GuiSettings;
import dev.tuffsmp.shrinecraft.client.gui.GuiTheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Swaps the survival inventory's panel texture for the themed one chosen with /gui.
 * Only the texture id changes; the panel size/layout is identical (the themes are painted on the vanilla panel), and no
 * resources are reloaded - the five textures were loaded once at startup.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin extends HandledScreen<PlayerScreenHandler> {
    // dummy constructor: only needed so this mixin may use HandledScreen's protected fields (titleX, textRenderer ...)
    private InventoryScreenMixin(PlayerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    /** The survival inventory's "Crafting" label: light with a dark outline on themed screens (dark grey is unreadable on most themes). */
    @Inject(method = "drawForeground", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$readableTitle(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
        GuiTheme theme = GuiSettings.currentTheme();
        if (theme != null) {
            theme.drawLabel(context, this.textRenderer, this.title, this.titleX, this.titleY);
            ci.cancel();
        }
    }

    @ModifyArg(
        method = "drawBackground",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V"),
        index = 1
    )
    private Identifier shrinecraft$themedBackground(Identifier vanilla) {
        GuiTheme theme = GuiSettings.currentTheme();
        return theme != null ? theme.texture : vanilla;
    }
}
