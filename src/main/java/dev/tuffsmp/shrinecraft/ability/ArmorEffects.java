package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Applies the potion effects of the four armor pieces.
 * Every tick we (re)apply each effect for only a few ticks, so taking a piece off removes its effect almost instantly.
 * If two worn pieces give the same effect, it is applied ONCE at the highest level (never stacked above spec).
 */
public final class ArmorEffects {
    private ArmorEffects() {}

    // ======================= TWEAK ME =======================
    /** How long each re-applied effect lasts, in ticks (2-3 so it disappears right after unequipping). */
    public static final int EFFECT_TICKS = 3;

    // Levels are the "roman numeral" level: 2 = Strength II, 1 = Speed I, and so on. 0 = off.
    public static final int HELMET_STRENGTH_LEVEL = 2;
    public static final int HELMET_FIRE_RESISTANCE_LEVEL = 1;
    /** Chestplate: Hero of the Village (was Strength II). */
    public static final int CHESTPLATE_HERO_OF_THE_VILLAGE_LEVEL = 3;
    public static final int CHESTPLATE_RESISTANCE_LEVEL = 1;
    public static final int LEGGINGS_DOLPHINS_GRACE_LEVEL = 1;
    /** Leggings: while RAIN is falling on you (open sky, a rainy biome - not snow, not just standing in water): Strength III and Speed II. */
    public static final int LEGGINGS_RAIN_STRENGTH_LEVEL = 3;
    public static final int LEGGINGS_RAIN_SPEED_LEVEL = 2;
    public static final int BOOTS_SPEED_LEVEL = 3;
    /** Boots: true = no fall damage at all. */
    public static final boolean BOOTS_CANCEL_FALL_DAMAGE = true;
    // ========================================================

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ArmorEffects::tick);
        if (BOOTS_CANCEL_FALL_DAMAGE) {
            // Fall damage is cancelled in the damage event (not with a potion): the hit simply never happens.
            ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity instanceof ServerPlayerEntity player
                    && source.isIn(DamageTypeTags.IS_FALL)
                    && player.getEquippedStack(EquipmentSlot.FEET).isOf(ModItems.SHRINE_BOOTS)));
        }
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            apply(player);
        }
    }

    /** Which effects the player's worn pieces want, as effect -> amplifier (level - 1), highest wins. */
    public static Map<RegistryEntry<StatusEffect>, Integer> desiredEffects(ServerPlayerEntity player) {
        Map<RegistryEntry<StatusEffect>, Integer> want = new HashMap<>();
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.SHRINE_HELMET)) {
            want(want, StatusEffects.STRENGTH, HELMET_STRENGTH_LEVEL);
            want(want, StatusEffects.FIRE_RESISTANCE, HELMET_FIRE_RESISTANCE_LEVEL);
        }
        if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.SHRINE_CHESTPLATE)) {
            want(want, StatusEffects.HERO_OF_THE_VILLAGE, CHESTPLATE_HERO_OF_THE_VILLAGE_LEVEL);
            want(want, StatusEffects.RESISTANCE, CHESTPLATE_RESISTANCE_LEVEL);
        }
        if (player.getEquippedStack(EquipmentSlot.LEGS).isOf(ModItems.SHRINE_LEGGINGS)) {
            want(want, StatusEffects.DOLPHINS_GRACE, LEGGINGS_DOLPHINS_GRACE_LEVEL);
            if (player.getEntityWorld().hasRain(player.getBlockPos())) {
                want(want, StatusEffects.STRENGTH, LEGGINGS_RAIN_STRENGTH_LEVEL);
                want(want, StatusEffects.SPEED, LEGGINGS_RAIN_SPEED_LEVEL);
            }
        }
        if (player.getEquippedStack(EquipmentSlot.FEET).isOf(ModItems.SHRINE_BOOTS)) {
            want(want, StatusEffects.SPEED, BOOTS_SPEED_LEVEL);
        }
        return want;
    }

    private static void want(Map<RegistryEntry<StatusEffect>, Integer> map, RegistryEntry<StatusEffect> effect, int level) {
        if (level > 0) {
            map.merge(effect, level - 1, Math::max);
        }
    }

    private static void apply(ServerPlayerEntity player) {
        for (Map.Entry<RegistryEntry<StatusEffect>, Integer> e : desiredEffects(player).entrySet()) {
            // ambient=true, no particles, no HUD icon: invisible, "beacon-like"
            player.addStatusEffect(new StatusEffectInstance(e.getKey(), EFFECT_TICKS, e.getValue(), true, false, false));
        }
    }
}
