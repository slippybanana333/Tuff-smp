package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.item.CloudLanceItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;

/** Cloud Lance: if Slowness was already on when the lance is picked up, strip it right away. */
public final class LanceHandler {
    private LanceHandler() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (CloudLanceItem.isHeldBy(player) && player.hasStatusEffect(StatusEffects.SLOWNESS)) {
                    player.removeStatusEffect(StatusEffects.SLOWNESS);
                }
            }
        });
    }
}
