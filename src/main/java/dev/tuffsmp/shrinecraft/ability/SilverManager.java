package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.item.OrbData;
import dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.util.Msg;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.HorseColor;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/** Summons, protects, decorates and recalls Silver. See SilversSpawnOrbItem for the rules. */
public final class SilverManager {
    private SilverManager() {}

    /** Command tag that marks the one special horse. */
    public static final String SILVER_TAG = "shrinecraft_silver";

    /** Loaded Silvers (uuid -> entity), so the per-tick work only touches them. */
    private static final Map<UUID, HorseEntity> LOADED = new HashMap<>();
    private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();
    /** Silver uuid -> game time of the last damage it took. */
    private static final Map<UUID, Long> LAST_DAMAGED = new HashMap<>();

    private static final DustParticleEffect FROST_DUST = new DustParticleEffect(0xBFEFFF, 1.1F);

    public static boolean isSilver(Entity entity) {
        return entity instanceof HorseEntity && entity.getCommandTags().contains(SILVER_TAG);
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (isSilver(entity)) {
                LOADED.put(entity.getUuid(), (HorseEntity) entity);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof HorseEntity) {
                LOADED.remove(entity.getUuid());
                LAST_POS.remove(entity.getUuid());
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(SilverManager::tick);

        // Remember when Silver last took damage ("recently in combat").
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (isSilver(entity)) {
                LAST_DAMAGED.put(entity.getUuid(), entity.getEntityWorld().getTime());
            }
        });
        // Silver is about to die: take the built-in gear off first so it can never drop (no stolen netherite armor / saddle).
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (isSilver(entity)) {
                entity.equipStack(EquipmentSlot.BODY, ItemStack.EMPTY);
                entity.equipStack(EquipmentSlot.SADDLE, ItemStack.EMPTY);
            }
            return true;
        });
        // Silver died: put the owner's orb on cooldown (Silver comes back after the cooldown).
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (isSilver(entity) && entity.getEntityWorld() instanceof ServerWorld world) {
                onSilverDied(world, (HorseEntity) entity);
            }
        });

        // Right-click interactions with Silver.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!isSilver(entity)) {
                return ActionResult.PASS;
            }
            ItemStack held = player.getStackInHand(hand);
            // No extra armor / saddles can be put on Silver by hand.
            // (Shears are blocked too, in case a future version lets them take saddles off.)
            if ((held.contains(DataComponentTypes.EQUIPPABLE) || held.isOf(Items.SHEARS)) && !held.isOf(ModItems.SILVERS_SPAWN_ORB)) {
                return world.isClient() ? ActionResult.FAIL : ActionResult.FAIL;
            }
            if (held.isOf(ModItems.SILVERS_SPAWN_ORB) && player.isSneaking()) {
                if (world.isClient()) {
                    return ActionResult.SUCCESS;
                }
                return capture((ServerPlayerEntity) player, (ServerWorld) world, hand, held, (HorseEntity) entity);
            }
            return ActionResult.PASS;
        });
    }

    // ---------------------------------------------------------------- summon

    public static void summon(ServerWorld world, ServerPlayerEntity player, ItemStack orb, OrbData data) {
        HorseEntity horse = EntityType.HORSE.create(world, SpawnReason.TRIGGERED);
        if (horse == null) {
            Msg.actionBar(player, Text.literal("Silver could not be summoned here.").formatted(Formatting.RED));
            return;
        }
        Vec3d look = player.getRotationVec(1.0F);
        double x = player.getX() + look.x * 1.5;
        double z = player.getZ() + look.z * 1.5;
        horse.refreshPositionAndAngles(x, player.getY(), z, player.getYaw(), 0.0F);
        horse.addCommandTag(SILVER_TAG);
        horse.setCustomName(Text.literal("Silver").formatted(Formatting.AQUA));
        horse.setCustomNameVisible(true);
        horse.setComponent(DataComponentTypes.HORSE_VARIANT, HorseColor.WHITE);
        horse.bondWithPlayer(player);
        horse.setPersistent();
        applyStats(horse);
        equipSilver(horse);
        float max = horse.getMaxHealth();
        horse.setHealth(data.silverHealth() > 0 ? Math.min(max, data.silverHealth()) : max);
        if (!world.spawnEntity(horse)) {
            ShrineCraft.LOGGER.warn("Silver could not be added to the world at {}", horse.getBlockPos().toShortString());
            Msg.actionBar(player, Text.literal("Silver could not be summoned here.").formatted(Formatting.RED));
            return;
        }
        LOADED.put(horse.getUuid(), horse);

        SilversSpawnOrbItem.setData(orb, data.released(horse.getUuid()));
        world.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.ENTITY_HORSE_ARMOR, SoundCategory.NEUTRAL, 1.0F, 1.2F);
        world.playSound(null, horse.getX(), horse.getY(), horse.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.NEUTRAL, 1.0F, 1.0F);
        world.spawnParticles(ParticleTypes.SNOWFLAKE, horse.getX(), horse.getY() + 1.0, horse.getZ(), 40, 0.6, 0.8, 0.6, 0.05);
        Msg.actionBar(player, Text.literal("Silver arrives!").formatted(Formatting.AQUA));
    }

    private static void applyStats(HorseEntity horse) {
        horse.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED).setBaseValue(SilversSpawnOrbItem.NORMAL_HORSE_SPEED * SilversSpawnOrbItem.SPEED_MULTIPLIER);
        horse.getAttributeInstance(EntityAttributes.JUMP_STRENGTH).setBaseValue(SilversSpawnOrbItem.NORMAL_HORSE_JUMP * SilversSpawnOrbItem.JUMP_MULTIPLIER);
        horse.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(SilversSpawnOrbItem.NORMAL_HORSE_HEALTH * SilversSpawnOrbItem.HEALTH_MULTIPLIER);
    }

    /** Built-in netherite horse armor + saddle that can never be taken off or dropped. */
    private static void equipSilver(HorseEntity horse) {
        horse.equipStack(EquipmentSlot.BODY, new ItemStack(Items.NETHERITE_HORSE_ARMOR));
        horse.equipStack(EquipmentSlot.SADDLE, new ItemStack(Items.SADDLE));
        horse.setEquipmentDropChance(EquipmentSlot.BODY, 0.0F);
        horse.setEquipmentDropChance(EquipmentSlot.SADDLE, 0.0F);
    }

    // ---------------------------------------------------------------- recall

    private static ActionResult capture(ServerPlayerEntity player, ServerWorld world, Hand hand, ItemStack orb, HorseEntity silver) {
        OrbData data = SilversSpawnOrbItem.ensureId(orb).resolved(world.getTime());
        if (data.state() != OrbData.State.RELEASED || !data.silver().map(silver.getUuid()::equals).orElse(false)) {
            Msg.actionBar(player, Text.literal("This is not the Silver bound to this orb.").formatted(Formatting.RED));
            return ActionResult.FAIL;
        }
        long now = world.getTime();
        boolean recentlyHurt = now - LAST_DAMAGED.getOrDefault(silver.getUuid(), Long.MIN_VALUE / 2) < SilversSpawnOrbItem.COMBAT_WINDOW_SECONDS * 20L;
        boolean beingHunted = !world.getEntitiesByClass(MobEntity.class, silver.getBoundingBox().expand(16.0), m -> m.getTarget() == silver).isEmpty();
        float health = silver.getHealth();

        world.spawnParticles(ParticleTypes.SNOWFLAKE, silver.getX(), silver.getY() + 1.0, silver.getZ(), 30, 0.5, 0.7, 0.5, 0.05);
        world.playSound(null, silver.getX(), silver.getY(), silver.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.NEUTRAL, 1.0F, 1.4F);
        silver.removeAllPassengers();
        silver.discard();
        LOADED.remove(silver.getUuid());
        LAST_DAMAGED.remove(silver.getUuid());

        if (recentlyHurt || beingHunted) {
            long end = now + SilversSpawnOrbItem.COOLDOWN_SECONDS * 20L;
            SilversSpawnOrbItem.setData(orb, data.cooldown(end, health));
            Msg.actionBar(player, Text.literal("Silver was in a fight - the orb needs " + SilversSpawnOrbItem.COOLDOWN_SECONDS + "s to recover.").formatted(Formatting.AQUA));
        } else {
            SilversSpawnOrbItem.setData(orb, data.stored(health));
            Msg.actionBar(player, Text.literal("Silver returns to the orb.").formatted(Formatting.AQUA));
        }
        return ActionResult.SUCCESS;
    }

    /** Right-click on a RELEASED orb in the air: just report where Silver is. */
    public static void describeReleased(ServerWorld world, ServerPlayerEntity player, ItemStack orb, OrbData data) {
        Entity found = data.silver().map(uuid -> LOADED.containsKey(uuid) ? LOADED.get(uuid) : findEntity(world.getServer(), uuid)).orElse(null);
        if (found != null) {
            double dist = found.distanceTo(player);
            Msg.actionBar(player, Text.literal("Silver is out there (" + Math.round(dist) + " blocks away). Sneak + right-click Silver to recall.").formatted(Formatting.AQUA));
        } else {
            Msg.actionBar(player, Text.literal("Silver is not in a loaded area right now. Go find Silver, then sneak + right-click.").formatted(Formatting.AQUA));
        }
    }

    private static Entity findEntity(MinecraftServer server, UUID uuid) {
        for (ServerWorld w : server.getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e != null) {
                return e;
            }
        }
        return null;
    }

    private static void onSilverDied(ServerWorld world, HorseEntity silver) {
        LOADED.remove(silver.getUuid());
        var owner = silver.getOwner();
        if (owner instanceof ServerPlayerEntity player) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack s = player.getInventory().getStack(i);
                if (s.isOf(ModItems.SILVERS_SPAWN_ORB)) {
                    OrbData d = SilversSpawnOrbItem.getData(s);
                    if (d.state() == OrbData.State.RELEASED && d.silver().map(silver.getUuid()::equals).orElse(false)) {
                        SilversSpawnOrbItem.setData(s, d.cooldown(world.getTime() + SilversSpawnOrbItem.COOLDOWN_SECONDS * 20L, -1.0F));
                        Msg.chat(player, Text.literal("Silver has fallen. The orb will bring Silver back in " + SilversSpawnOrbItem.COOLDOWN_SECONDS + "s.").formatted(Formatting.AQUA));
                        return;
                    }
                }
            }
        }
        ShrineCraft.LOGGER.info("Silver died but its owner's orb was not in the owner's inventory; the orb will report Silver as missing.");
    }

    // ---------------------------------------------------------------- per-tick: trail + armor guard

    private static void tick(MinecraftServer server) {
        if (LOADED.isEmpty()) {
            return;
        }
        Set<UUID> gone = new HashSet<>();
        for (Map.Entry<UUID, HorseEntity> e : LOADED.entrySet()) {
            HorseEntity horse = e.getValue();
            if (horse.isRemoved() || !(horse.getEntityWorld() instanceof ServerWorld world)) {
                gone.add(e.getKey());
                continue;
            }
            Vec3d now = horse.getEntityPos();
            Vec3d before = LAST_POS.put(e.getKey(), now);
            if (SilversSpawnOrbItem.ICE_TRAIL && before != null && before.squaredDistanceTo(now) > 0.0004) {
                world.spawnParticles(ParticleTypes.SNOWFLAKE, now.x, now.y + 0.15, now.z, 3, 0.35, 0.05, 0.35, 0.01);
                world.spawnParticles(FROST_DUST, now.x, now.y + 0.1, now.z, 2, 0.3, 0.03, 0.3, 0.0);
            }
            if (world.getTime() % 20 == 0) {
                // "Built-in" armor: whatever happens, Silver wears exactly netherite horse armor + saddle.
                if (!horse.getEquippedStack(EquipmentSlot.BODY).isOf(Items.NETHERITE_HORSE_ARMOR)
                    || !horse.getEquippedStack(EquipmentSlot.SADDLE).isOf(Items.SADDLE)) {
                    equipSilver(horse);
                }
            }
        }
        gone.forEach(LOADED::remove);
    }
}
