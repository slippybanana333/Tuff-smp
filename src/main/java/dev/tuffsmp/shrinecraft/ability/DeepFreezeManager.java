package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.item.FrostLongbowItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/**
 * The Frost Longbow's Deep Freeze: the target is frozen SOLID - it cannot move at all for FrostLongbowItem.FREEZE_SECONDS.
 *  - mobs: their AI is switched off (so no walking, no attacking) and restored afterwards (never restored if it was already off),
 *  - players: walking speed and jump strength are cut to zero with temporary attribute modifiers AND the server pins them to the freeze spot every tick
 *    (their own client moves them, so the server cannot just stop them). They can still look around, eat (gap) and use items,
 *  - every tick: all sideways velocity (knockback too) is cancelled and the blue frost overlay is held just below the freeze-damage point
 *    (so it looks frozen but does NOT deal powder-snow damage).
 */
public final class DeepFreezeManager {
    private DeepFreezeManager() {}

    /**
     * Players move themselves on their own client, so the server cannot just stop them. Instead two TEMPORARY attribute modifiers
     * (never saved, so nobody can log out frozen) cut walking speed and jump strength to zero. (The old Jump Boost "128" trick is wrong in
     * 1.21: there it ADDS jump velocity and would have launched the frozen player into the sky.)
     */
    /** How far (blocks) a frozen player may drift from the pin spot before being pulled back - small, so it never looks like rubber-banding. */
    private static final double PIN_TOLERANCE = 0.08;
    private static final Identifier FREEZE_MODIFIER = Identifier.of("shrinecraft", "deep_freeze");

    private static final class Frozen {
        final LivingEntity entity;
        final int endTick;
        final boolean aiWasDisabled;
        /** Where a frozen PLAYER is pinned (sideways); they can still turn their head, eat, use items - just not move. */
        final double anchorX;
        final double anchorZ;

        Frozen(LivingEntity entity, int endTick, boolean aiWasDisabled, double anchorX, double anchorZ) {
            this.entity = entity;
            this.endTick = endTick;
            this.aiWasDisabled = aiWasDisabled;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
        }
    }

    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DeepFreezeManager::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FROZEN.clear());
    }

    public static boolean isFrozen(Entity entity) {
        return FROZEN.containsKey(entity.getUuid());
    }

    /** Freezes the target solid for `seconds` (a second hit simply restarts the timer). */
    public static void freeze(LivingEntity target, float seconds) {
        if (!(target.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        int ticks = Math.max(1, Math.round(seconds * 20.0F));
        Frozen old = FROZEN.get(target.getUuid());
        boolean aiWasDisabled = old != null ? old.aiWasDisabled : (target instanceof MobEntity mob && mob.isAiDisabled());
        // a second hit restarts the timer but keeps the ORIGINAL pin spot (so you cannot creep along by being re-frozen)
        double ax = old != null ? old.anchorX : target.getX();
        double az = old != null ? old.anchorZ : target.getZ();
        FROZEN.put(target.getUuid(), new Frozen(target, world.getServer().getTicks() + ticks, aiWasDisabled, ax, az));
        if (target instanceof MobEntity mob) {
            mob.setAiDisabled(true);
        }
        if (target instanceof ServerPlayerEntity) {
            setPlayerLock(target, true);
        }
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.2F, 0.7F);
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.BLUE_ICE.getDefaultState()),
            target.getX(), target.getY() + target.getHeight() / 2.0, target.getZ(), 30, target.getWidth() / 2.0, target.getHeight() / 2.0, target.getWidth() / 2.0, 0.05);
    }

    /** Adds / removes the "cannot walk, cannot jump" attribute modifiers on a player. */
    private static void setPlayerLock(LivingEntity player, boolean on) {
        var speed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        var jump = player.getAttributeInstance(EntityAttributes.JUMP_STRENGTH);
        for (EntityAttributeInstance inst : new EntityAttributeInstance[] {speed, jump}) {
            if (inst == null) {
                continue;
            }
            inst.removeModifier(FREEZE_MODIFIER);
            if (on) {
                // -100 % of the current value (multiplied total) = exactly zero, whatever other effects the player has
                inst.addTemporaryModifier(new EntityAttributeModifier(FREEZE_MODIFIER, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** Ends the freeze early / normally and gives the target back its AI and its movement. */
    public static void release(LivingEntity target) {
        Frozen f = FROZEN.remove(target.getUuid());
        if (f == null) {
            return;
        }
        restore(f);
    }

    private static void restore(Frozen f) {
        LivingEntity e = f.entity;
        if (e instanceof MobEntity mob && !f.aiWasDisabled) {
            mob.setAiDisabled(false);
        }
        if (e instanceof ServerPlayerEntity) {
            setPlayerLock(e, false);
        }
        if (e.getEntityWorld() instanceof ServerWorld world) {
            world.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 0.8F, 1.3F);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getHeight() / 2.0, e.getZ(), 20, e.getWidth() / 2.0, e.getHeight() / 2.0, e.getWidth() / 2.0, 0.05);
        }
    }

    /** Public so the self-test can step it. */
    public static void tick(MinecraftServer server) {
        if (FROZEN.isEmpty()) {
            return;
        }
        int now = server.getTicks();
        Iterator<Frozen> it = FROZEN.values().iterator();
        while (it.hasNext()) {
            Frozen f = it.next();
            LivingEntity e = f.entity;
            if (e.isRemoved() || !e.isAlive()) {
                it.remove();                    // dead or gone: nothing to restore
                continue;
            }
            if (now >= f.endTick) {
                it.remove();
                restore(f);
                continue;
            }
            // Hold still: cancel sideways motion (knockback included); gravity still works.
            e.setVelocity(0.0, Math.min(e.getVelocity().y, 0.0), 0.0);
            e.velocityDirty = true;
            // Players move on their own client, so the speed/jump lock alone can still be beaten by knockback, an ender pearl, chorus fruit,
            // momentum ... Pin them: if they ended up sideways of the freeze spot, put them back (looking around / eating / using items stay free).
            if (e instanceof ServerPlayerEntity sp) {
                double dx = sp.getX() - f.anchorX;
                double dz = sp.getZ() - f.anchorZ;
                if (dx * dx + dz * dz > PIN_TOLERANCE * PIN_TOLERANCE) {
                    if (sp.networkHandler != null) {
                        sp.networkHandler.requestTeleport(f.anchorX, sp.getY(), f.anchorZ, sp.getYaw(), sp.getPitch());
                    } else {
                        sp.setPosition(f.anchorX, sp.getY(), f.anchorZ);   // (the self-test's fake players have no connection)
                    }
                    sp.setVelocity(0.0, Math.min(sp.getVelocity().y, 0.0), 0.0);
                }
            }
            if (e.canFreeze()) {
                e.setFrozenTicks(e.getMinFreezeDamageTicks() - 1);   // full frost look, but just below the point where it would hurt
            }
            if (now % 4 == 0 && e.getEntityWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.SNOWFLAKE, e.getX(), e.getY() + e.getHeight() / 2.0, e.getZ(), 2, e.getWidth() / 2.0, e.getHeight() / 2.0, e.getWidth() / 2.0, 0.0);
            }
        }
    }

    /** Self-test helper. */
    public static void clearForTest() {
        FROZEN.clear();
    }
}
