package dev.tuffsmp.shrinecraft.client.mixin;

import dev.tuffsmp.shrinecraft.client.render.WornDisplay;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The "Strange" Cape is drawn by the elytra layer; this moves / rotates / scales it by the placement saved from the Display Editor (worn_display.json). */
@Mixin(ElytraFeatureRenderer.class)
public abstract class ElytraFeatureRendererMixin {
    private static final String RENDER = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void shrinecraft$wornBefore(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, BipedEntityRenderState state, float f, float g, CallbackInfo ci) {
        if (WornDisplay.has(state.equippedChestStack)) {
            matrices.push();
            WornDisplay.apply(matrices, state.equippedChestStack);
        }
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void shrinecraft$wornAfter(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, BipedEntityRenderState state, float f, float g, CallbackInfo ci) {
        if (WornDisplay.has(state.equippedChestStack)) {
            matrices.pop();
        }
    }
}
