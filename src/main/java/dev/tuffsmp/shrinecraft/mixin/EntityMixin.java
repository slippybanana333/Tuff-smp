package dev.tuffsmp.shrinecraft.mixin;

import dev.tuffsmp.shrinecraft.item.CloudLanceItem;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cloud Lance: while it is held, blocks cannot slow the player down.
 * (Cobwebs and sweet berry bushes call slowMovement; soul sand / honey use the block velocity multiplier.)
 * Runs on both the client and the server, so the player's own movement prediction agrees with the server.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "slowMovement", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$lanceIgnoresSlowingBlocks(BlockState state, Vec3d multiplier, CallbackInfo ci) {
        if ((Object) this instanceof PlayerEntity player && CloudLanceItem.isHeldBy(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "getVelocityMultiplier", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$lanceIgnoresSoulSand(CallbackInfoReturnable<Float> cir) {
        if ((Object) this instanceof PlayerEntity player && CloudLanceItem.isHeldBy(player)) {
            cir.setReturnValue(1.0F);
        }
    }
}
