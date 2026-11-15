package dev.tuffsmp.shrinecraft.mixin;

import dev.tuffsmp.shrinecraft.ability.SilverManager;
import net.minecraft.block.PowderSnowBlock;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Silver walks ON TOP of powder snow instead of sinking in (like a player in leather boots, or a rabbit / fox).
 * Vanilla asks this one question everywhere - the collision shape of the block, the "stuck in snow" slowdown and the freezing -
 * so answering "yes" for Silver is enough. The rider is carried, so riding Silver over powder snow works too.
 */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
    @Inject(method = "canWalkOnPowderSnow", at = @At("HEAD"), cancellable = true)
    private static void shrinecraft$silverWalksOnSnow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (SilverManager.isSilver(entity)) {
            cir.setReturnValue(true);
        }
    }
}
