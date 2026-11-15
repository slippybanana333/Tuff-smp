package dev.tuffsmp.shrinecraft.client.mixin;

import dev.tuffsmp.shrinecraft.client.render.WornDisplay;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Moves / rotates / scales each worn Tuff SMP armor piece by the placement saved from the Display Editor (worn_display.json). */
@Mixin(ArmorFeatureRenderer.class)
public abstract class ArmorFeatureRendererMixin {
    private static final String RENDER_ARMOR = "renderArmor(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;)V";

    @Inject(method = RENDER_ARMOR, at = @At("HEAD"))
    private void shrinecraft$wornBefore(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack, EquipmentSlot slot, int light, BipedEntityRenderState state, CallbackInfo ci) {
        if (WornDisplay.has(stack)) {
            matrices.push();
            WornDisplay.apply(matrices, stack);
        }
    }

    @Inject(method = RENDER_ARMOR, at = @At("RETURN"))
    private void shrinecraft$wornAfter(MatrixStack matrices, OrderedRenderCommandQueue queue, ItemStack stack, EquipmentSlot slot, int light, BipedEntityRenderState state, CallbackInfo ci) {
        if (WornDisplay.has(stack)) {
            matrices.pop();
        }
    }
}
