package dev.tuffsmp.shrinecraft.client.mixin;

import dev.tuffsmp.shrinecraft.client.render.LancePose;
import net.minecraft.client.render.entity.state.Lancing;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla's first-person spear animations: method_75396 = hold-out (kinetic charge), method_75391 = jab (stab swing).
 * (Yarn has no names for them yet.) After either one we turn an upright Cloud Lance so its tip points forward - see LancePose.
 */
@Mixin(Lancing.class)
public abstract class LancingMixin {
    @Inject(method = "method_75396", at = @At("TAIL"))
    private static void shrinecraft$holdOut(float f, MatrixStack matrices, float g, Arm arm, ItemStack stack, CallbackInfo ci) {
        if (stack.getItem() instanceof dev.tuffsmp.shrinecraft.item.CloudLanceItem) {
            LancePose.apply(matrices, LancePose.HOLD, arm);
        }
    }

    @Inject(method = "method_75391", at = @At("TAIL"))
    private static void shrinecraft$jab(float f, MatrixStack matrices, int i, Arm arm, CallbackInfo ci) {
        if (LancePose.lanceInArm(arm)) {
            LancePose.apply(matrices, LancePose.JAB, arm);
        }
    }
}
