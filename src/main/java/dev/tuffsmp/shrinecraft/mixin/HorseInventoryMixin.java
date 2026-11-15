package dev.tuffsmp.shrinecraft.mixin;

import dev.tuffsmp.shrinecraft.ability.SilverManager;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silver's netherite horse armor and saddle are built in and must never come out (taking them out and letting the
 * game re-equip fresh ones = item duplication). Silver's inventory screen therefore cannot be opened at all - neither
 * with sneak + right-click nor with the inventory key while riding (both go through openInventory) - and nothing drops on death.
 */
@Mixin(AbstractHorseEntity.class)
public abstract class HorseInventoryMixin {
    @Inject(method = "openInventory", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$silverHasNoInventory(PlayerEntity player, CallbackInfo ci) {
        if (SilverManager.isSilver((AbstractHorseEntity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "dropInventory", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$silverDropsNothing(ServerWorld world, CallbackInfo ci) {
        if (SilverManager.isSilver((AbstractHorseEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
