package dev.tuffsmp.shrinecraft.mixin;

import dev.tuffsmp.shrinecraft.ability.HarbingerHandler;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Harbinger of Death: the swap-hands key (F) changes its mode instead of moving it to the offhand.
 * The client does not move the item by itself (it only sends this packet), so cancelling here is enough.
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayerEntity player;

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void shrinecraft$harbingerSwapKey(PlayerActionC2SPacket packet, CallbackInfo ci) {
        if (packet.getAction() == PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND && HarbingerHandler.onSwapKey(this.player)) {
            ci.cancel();
        }
    }
}
