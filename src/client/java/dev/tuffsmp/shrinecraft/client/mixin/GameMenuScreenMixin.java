package dev.tuffsmp.shrinecraft.client.mixin;

import dev.tuffsmp.shrinecraft.client.gui.MenuPanel;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pause (Esc) menu: right after the blurred background, draw the themed framed panel behind the buttons (sized round whatever buttons exist). */
@Mixin(GameMenuScreen.class)
public abstract class GameMenuScreenMixin {
    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void shrinecraft$themedPanel(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        MenuPanel.draw(context, (Screen) (Object) this);
    }
}
