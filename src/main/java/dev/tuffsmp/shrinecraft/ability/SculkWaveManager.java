package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.item.SculkMaceItem;
import dev.tuffsmp.shrinecraft.registry.ModKeys;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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

/** Runs the ground shockwaves of the Sculk Mace's "Sculk Wave" ability. */
public final class SculkWaveManager {
    private SculkWaveManager() {}

    private static final class Wave {
        final RegistryKey<World> dimension;
        final UUID owner;
        final Vec3d origin;
        final Vec3d dir;
        double travelled = 0;
        double lastY;
        final Set<UUID> alreadyHit = new HashSet<>();

        Wave(RegistryKey<World> dimension, UUID owner, Vec3d origin, Vec3d dir) {
            this.dimension = dimension;
            this.owner = owner;
            this.origin = origin;
            this.dir = dir;
            this.lastY = origin.y;
        }
    }

    private static final List<Wave> WAVES = new ArrayList<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SculkWaveManager::tick);
    }

    /** Starts a shockwave from the attacker toward the thing they just hit (falls back to where they look). */
    public static void launch(ServerWorld world, ServerPlayerEntity attacker, LivingEntity target) {
        Vec3d toTarget = new Vec3d(target.getX() - attacker.getX(), 0, target.getZ() - attacker.getZ());
        if (toTarget.lengthSquared() < 1.0E-4) {
            Vec3d look = attacker.getRotationVec(1.0F);
            toTarget = new Vec3d(look.x, 0, look.z);
        }
        if (toTarget.lengthSquared() < 1.0E-4) {
            toTarget = new Vec3d(0, 0, 1);
        }
        Vec3d dir = toTarget.normalize();
        WAVES.add(new Wave(world.getRegistryKey(), attacker.getUuid(), attacker.getEntityPos(), dir));
        world.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 0.5F, 1.8F);
    }

    /** Runs the wave simulation by hand (the self-test uses this because it cannot wait for real ticks). */
    public static void tickForTest(MinecraftServer server, int ticks) {
        for (int i = 0; i < ticks; i++) {
            tick(server);
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Wave> it = WAVES.iterator();
        while (it.hasNext()) {
            Wave wave = it.next();
            ServerWorld world = server.getWorld(wave.dimension);
            if (world == null || !advance(world, wave)) {
                it.remove();
            }
        }
    }

    /** @return false when the wave is finished (range reached or blocked). */
    private static boolean advance(ServerWorld world, Wave wave) {
        double steps = SculkMaceItem.WAVE_SPEED;
        double end = Math.min(SculkMaceItem.WAVE_RANGE, wave.travelled + steps);
        Vec3d side = new Vec3d(-wave.dir.z, 0, wave.dir.x);
        ServerPlayerEntity owner = world.getServer().getPlayerManager().getPlayer(wave.owner);
        // Walk the wave forward in 0.5 block increments so it never skips a thin target.
        for (double d = wave.travelled + 0.5; d <= end + 1.0E-6; d += 0.5) {
            Vec3d flat = wave.origin.add(wave.dir.multiply(d));
            Double groundY = findGround(world, flat.x, wave.lastY, flat.z);
            if (groundY == null) {
                return false; // wall or cliff
            }
            wave.lastY = groundY;
            Vec3d p = new Vec3d(flat.x, groundY, flat.z);
            drawWave(world, p, side);
            damage(world, wave, owner, p);
        }
        wave.travelled = end;
        return end < SculkMaceItem.WAVE_RANGE;
    }

    private static void drawWave(ServerWorld world, Vec3d p, Vec3d side) {
        BlockStateParticleEffect sculk = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SCULK.getDefaultState());
        double half = SculkMaceItem.WAVE_WIDTH / 2.0;
        for (double o = -half; o <= half + 1.0E-6; o += 0.75) {
            double x = p.x + side.x * o;
            double z = p.z + side.z * o;
            world.spawnParticles(sculk, x, p.y + 0.15, z, 2, 0.1, 0.1, 0.1, 0.02);
            world.spawnParticles(ParticleTypes.SCULK_SOUL, x, p.y + 0.3, z, 1, 0.1, 0.2, 0.1, 0.02);
        }
    }

    private static void damage(ServerWorld world, Wave wave, ServerPlayerEntity owner, Vec3d p) {
        double half = SculkMaceItem.WAVE_WIDTH / 2.0 + 0.3;
        Box box = new Box(p.x - half, p.y - 0.5, p.z - half, p.x + half, p.y + 2.5, p.z + half);
        DamageSource source = new DamageSource(
            world.getRegistryManager().getOrThrow(RegistryKeys.DAMAGE_TYPE).getOrThrow(ModKeys.SCULK_WAVE), owner, owner);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, box, le -> le.isAlive() && !le.isSpectator() && !le.getUuid().equals(wave.owner))) {
            if (wave.alreadyHit.add(e.getUuid()) && !TrustManager.trusts(owner, e)) {
                e.damage(world, source, SculkMaceItem.WAVE_DAMAGE);
            }
        }
    }

    /** Finds the walkable ground height at (x, z) within a few blocks of lastY; null if the wave should stop. */
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
