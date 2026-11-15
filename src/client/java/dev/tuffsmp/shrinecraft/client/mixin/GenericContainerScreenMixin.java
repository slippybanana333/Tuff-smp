package dev.tuffsmp.shrinecraft.client.mixin;

import dev.tuffsmp.shrinecraft.client.gui.GuiSettings;
import dev.tuffsmp.shrinecraft.client.gui.GuiTheme;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Same idea as InventoryScreenMixin, for chests, barrels and ender chests (every GenericContainerScreen, 1-6 rows): swaps vanilla's
 * generic_54.png for the themed chest sheet. Vanilla draws it in two slices (header + rows, then the 96px player part from v=126), so the
 * themed sheet MUST keep the vanilla generic_54 layout - which the owner's artwork does.
 */
@Mixin(GenericContainerScreen.class)
public abstract class GenericContainerScreenMixin {
    @ModifyArg(
        method = "drawBackground",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIFFIIII)V"),
        index = 1
    )
    private Identifier shrinecraft$themedChest(Identifier vanilla) {
        GuiTheme theme = GuiSettings.currentTheme();
        return theme != null ? theme.chestTexture : vanilla;
    }
}
