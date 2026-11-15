package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.item.SculkMaceItem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Runs the shockwaves of the Sculk Mace's "Sculk Wave" ability.
 *
 * Each melee hit DURING the wave window blasts a RADIAL shockwave from the thing that was hit: everything alive
 * inside SculkMaceItem.WAVE_RADIUS takes SculkMaceItem.WAVE_DAMAGE instantly (the "sculk_wave" damage type is in
 * the vanilla bypasses_armor tag, so armor does not reduce it). You, your trusted players and spectators are never
 * touched. The visible sculk ring then expands outward for looks only.
 *
 * (It used to be a travelling line-wave that only hurt what lay in its path - the owner wanted a real blast radius.)
 */
public final class SculkWaveManager {
    private SculkWaveManager() {}

    /** Just the visible ring: it expands and then goes away; the damage already happened at launch. */
    private static final class Ring {
        final RegistryKey<World> dimension;
        final Vec3d center;
        double travelled = 0;

        Ring(RegistryKey<World> dimension, Vec3d center) {
            this.dimension = dimension;
            this.center = center;
        }
    }

    private static final List<Ring> RINGS = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SculkWaveManager::tick);
    }

    /** Blasts a radial shockwave from the thing the attacker just hit. */
    public static void launch(ServerWorld world, ServerPlayerEntity attacker, LivingEntity target) {
        Vec3d center = target.getEntityPos();
        damage(world, attacker, center);
        RINGS.add(new Ring(world.getRegistryKey(), center));
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.5F, 1.8F);
    }

    /** Runs the blast visuals by hand (the self-test uses this because it cannot wait for real ticks). */
    public static void tickForTest(MinecraftServer server, int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick(server);
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Ring> it = RINGS.iterator();
        while (it.hasNext()) {
            Ring ring = it.next();
            ServerWorld world = server.getWorld(ring.dimension);
            if (world == null) {
                it.remove();
                continue;
            }
            ring.travelled += SculkMaceItem.WAVE_SPEED;
            if (ring.travelled > SculkMaceItem.WAVE_RADIUS) {
                it.remove();
                continue;
            }
            drawRing(world, ring.center, ring.travelled);
        }
    }

    /** The blast itself: everything alive within WAVE_RADIUS of the hit (except the attacker + trusted) takes the damage. */
    private static void damage(ServerWorld world, ServerPlayerEntity owner, Vec3d center) {
        double r = SculkMaceItem.WAVE_RADIUS;
        Box box = new Box(center.x - r, center.y - r, center.z - r, center.x + r, center.y + r, center.z + r);
        DamageSource source = new DamageSource(
            world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(dev.tuffsmp.shrinecraft.registry.ModKeys.SCULK_WAVE), owner, owner);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, le -> le.isAlive() && !le.isSpectator()
                && !le.getUuid().equals(owner.getUuid()))) {
            if (!TrustManager.trusts(owner, e)) {
                e.damage(world, source, SculkMaceItem.WAVE_DAMAGE);
            }
        }
    }

    /** A ring of sculk particles at walkable ground height, expanding outward from the blast centre. */
    private static void drawRing(ServerWorld world, Vec3d center, double radius) {
        BlockStateParticleEffect sculk = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SCULK.getDefaultState());
        Double groundY = findGround(world, center.x, center.y, center.z);
        double y = groundY == null ? center.y : groundY;
        // 24 points around the circle; close enough that the ring looks solid.
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            world.spawnParticles(sculk, x, y + 0.15, z, 2, 0.1, 0.1, 0.1, 0.02);
            world.spawnParticles(ParticleTypes.SCULK_SOUL, x, y + 0.3, z, 1, 0.1, 0.2, 0.1, 0.02);
        }
    }

    /** Finds the walkable ground height at (x, z) within a few blocks of lastY; null if there is none. */
    private static Double findGround(ServerWorld world, double x, double lastY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int startY = (int) Math.floor(lastY) + 1;
        for (int y = startY; y >= startY - 3; y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState below = world.getBlockState(pos);
            BlockState here = world.getBlockState(pos.up());
            BlockState above = world.getBlockState(pos.up(2));
            if (!below.getCollisionShape(world, pos).isEmpty() && here.getCollisionShape(world, pos.up()).isEmpty() && above.getCollisionShape(world, pos.up(2)).isEmpty()) {
                return pos.getY() + below.getCollisionShape(world, pos).getMax(net.minecraft.util.math.Direction.Axis.Y);
            }
        }
        return null;
    }
}
