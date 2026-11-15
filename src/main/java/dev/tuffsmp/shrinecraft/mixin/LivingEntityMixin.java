package dev.tuffsmp.shrinecraft.mixin;

import dev.tuffsmp.shrinecraft.item.CloudLanceItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cloud Lance: the Slowness potion effect simply cannot be applied to someone holding the lance. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "canHaveStatusEffect", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$lanceBlocksSlowness(StatusEffectInstance effect, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PlayerEntity player
            && effect.getEffectType().matches(StatusEffects.SLOWNESS)
            && CloudLanceItem.isHeldBy(player)) {
            cir.setReturnValue(false);
        }
    }
}
