package dev.tuffsmp.shrinecraft.registry;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/** All registry keys for the data-driven things this mod ships (enchantments, damage types, equipment assets). */
public final class ModKeys {
    private ModKeys() {}

    // ---- custom enchantments (data/shrinecraft/enchantment/*.json) ----
    /** Fire Aspect 2 that only the Molten Greataxe can ever have. */
    public static final RegistryKey<Enchantment> MOLTEN_FIRE_ASPECT = enchant("molten_fire_aspect");
    /** "Infinity-equivalent" for the Sculk Cannon: never consumes any arrow type (plain or tipped). */
    public static final RegistryKey<Enchantment> ENDLESS_QUIVER = enchant("endless_quiver");

    // ---- custom damage types (data/shrinecraft/damage_type/*.json) ----
    public static final RegistryKey<DamageType> SCULK_WAVE = damage("sculk_wave");
    public static final RegistryKey<DamageType> SCULK_WAIL = damage("sculk_wail");
    public static final RegistryKey<DamageType> FROST_ARROW = damage("frost_arrow");

    // ---- equipment assets (assets/shrinecraft/equipment/*.json) ----
    public static final RegistryKey<EquipmentAsset> HELMET = asset("shrine_helmet");
    public static final RegistryKey<EquipmentAsset> CHESTPLATE = asset("shrine_chestplate");
    public static final RegistryKey<EquipmentAsset> LEGGINGS = asset("shrine_leggings");
    public static final RegistryKey<EquipmentAsset> BOOTS = asset("shrine_boots");
    public static final RegistryKey<EquipmentAsset> STRANGE_CAPE = asset("strange_cape");

    private static RegistryKey<Enchantment> enchant(String name) {
        return RegistryKey.of(RegistryKeys.ENCHANTMENT, ShrineCraft.id(name));
    }

    private static RegistryKey<DamageType> damage(String name) {
        return RegistryKey.of(RegistryKeys.DAMAGE_TYPE, ShrineCraft.id(name));
    }

    private static RegistryKey<EquipmentAsset> asset(String name) {
        return RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, ShrineCraft.id(name));
    }
}
