package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.item.MoltenGreataxeItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * Owns every temporary lava block created by the Molten Greataxe's Lava Bomb:
 *  - places the cluster of lava SOURCE blocks,
 *  - turns each one back into air after LAVA_LIFETIME_SECONDS,
 *  - keeps the attacker immune to fire / lava damage while their lava is alive.
 */
public final class LavaBombManager {
    private LavaBombManager() {}

    private static final class Cluster {
        final RegistryKey<World> dimension;
        final UUID owner;
        final List<BlockPos> positions;
        final long revertAt;

        Cluster(RegistryKey<World> dimension, UUID owner, List<BlockPos> positions, long revertAt) {
            this.dimension = dimension;
            this.owner = owner;
            this.positions = positions;
            this.revertAt = revertAt;
        }
    }

    private static final List<Cluster> ACTIVE = new ArrayList<>();
    /** Owner -> game time until which they are protected (lava lifetime + a short grace period). */
    private static final Map<UUID, Long> PROTECTED_UNTIL = new HashMap<>();
    /** Extra ticks of fire protection after the lava is gone (so flames/lava drips can't hurt the owner). */
    private static final int GRACE_TICKS = 60;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LavaBombManager::tick);
        // Never let lava/fire hurt the attacker while their own bomb is alive.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && source.isIn(DamageTypeTags.IS_FIRE) && isProtected(player)) {
                player.extinguish();
                return false;
            }
            return true;
        });
        // If the server stops with lava still pending, clean it up so no permanent lava is left behind.
        ServerLifecycleEvents.SERVER_STOPPING.register(LavaBombManager::revertAllNow);
    }

    /** Turns every pending lava block back into air right now (server shutdown, and the self-test). */
    public static void revertAllNow(MinecraftServer server) {
        for (Cluster c : ACTIVE) {
            ServerWorld world = server.getWorld(c.dimension);
            if (world != null) {
                revert(world, c);
            }
        }
        ACTIVE.clear();
    }

    public static boolean isProtected(ServerPlayerEntity player) {
        Long until = PROTECTED_UNTIL.get(player.getUuid());
        return until != null && player.getEntityWorld().getTime() < until;
    }

    /** Places the lava cluster at the target's feet. Only replaces air and replaceable plants - never solid blocks. */
    public static void spawnCluster(ServerWorld world, ServerPlayerEntity attacker, LivingEntity target) {
        if (TrustManager.trusts(attacker, target)) {
            return; // friendly fire off for trusted players (/tuffsmp trust)
        }
        BlockPos center = target.getBlockPos();
        List<BlockPos> placed = new ArrayList<>();
        for (int[] off : MoltenGreataxeItem.LAVA_CLUSTER_OFFSETS) {
            BlockPos pos = center.add(off[0], 0, off[1]);
            var state = world.getBlockState(pos);
            boolean free = state.isAir() || (state.isReplaceable() && state.getFluidState().isEmpty());
            if (free) {
                world.setBlockState(pos, Blocks.LAVA.getDefaultState(), 3);
                placed.add(pos.toImmutable());
                world.spawnParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.2, 0.3, 0.0);
            }
        }
        long lifetime = MoltenGreataxeItem.LAVA_LIFETIME_SECONDS * 20L;
        long now = world.getTime();
        world.playSound(null, center, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.PLAYERS, 1.0F, 0.6F);
        world.playSound(null, center, SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0F, 0.6F);
        if (!placed.isEmpty()) {
            ACTIVE.add(new Cluster(world.getRegistryKey(), attacker.getUuid(), placed, now + lifetime));
        }
        // Protect even if nothing could be placed (cheap, and avoids edge-case self damage from burning targets).
        PROTECTED_UNTIL.merge(attacker.getUuid(), now + lifetime + GRACE_TICKS, Math::max);
    }

    private static void tick(MinecraftServer server) {
        // Keep protected players from being set on fire by their own lava.
        if (!PROTECTED_UNTIL.isEmpty()) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (isProtected(player) && player.isOnFire()) {
                    player.extinguish();
                }
            }
            PROTECTED_UNTIL.values().removeIf(until -> until < server.getOverworld().getTime() - 200);
        }
        Iterator<Cluster> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Cluster c = it.next();
            ServerWorld world = server.getWorld(c.dimension);
            if (world == null) {
                it.remove();
                continue;
            }
            if (world.getTime() >= c.revertAt) {
                revert(world, c);
                if (c.positions.isEmpty()) {
                    it.remove(); // anything in unloaded chunks stays queued and is retried until it loads
                }
            }
        }
    }

    /** Turns every still-lava block of the cluster back into air. Blocks in unloaded chunks stay queued. */
    private static void revert(ServerWorld world, Cluster c) {
        Iterator<BlockPos> it = c.positions.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!world.isChunkLoaded(pos)) {
                continue;
            }
            if (world.getBlockState(pos).isOf(Blocks.LAVA)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                world.spawnParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.25, 0.1, 0.25, 0.01);
            }
            it.remove();
        }
    }
}
