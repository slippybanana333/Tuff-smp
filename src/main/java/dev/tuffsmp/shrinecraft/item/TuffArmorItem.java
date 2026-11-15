package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.registry.ModKeys;
import dev.tuffsmp.shrinecraft.util.Enchants;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.ArmorMaterials;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;

/**
 * The four armor pieces: Helmet, Chestplate, Leggings, Boots. All netherite-tier stats, all unbreakable.
 * The potion effects they give are applied every tick by ArmorEffects (so they vanish the instant you take them off).
 */
public class TuffArmorItem extends Item {
    // ======================= TWEAK ME (armor stats) =======================
    /** Netherite stats: boots / leggings / chestplate / helmet protection points. */
    public static final int DEFENSE_BOOTS = 3;
    public static final int DEFENSE_LEGGINGS = 6;
    public static final int DEFENSE_CHESTPLATE = 8;
    public static final int DEFENSE_HELMET = 3;
    public static final float TOUGHNESS = 3.0F;
    public static final float KNOCKBACK_RESISTANCE = 0.1F;
    public static final int ENCHANTABILITY = 15;
    // ======================================================================

    private final String key;

    public TuffArmorItem(Item.Settings settings, String key) {
        super(settings);
        this.key = key;
    }

    private static ArmorMaterial material(RegistryKey<EquipmentAsset> asset) {
        return new ArmorMaterial(
            37,
            ArmorMaterials.createDefenseMap(DEFENSE_BOOTS, DEFENSE_LEGGINGS, DEFENSE_CHESTPLATE, DEFENSE_HELMET, 19),
            ENCHANTABILITY,
            SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE,
            TOUGHNESS,
            KNOCKBACK_RESISTANCE,
            ItemTags.REPAIRS_NETHERITE_ARMOR,
            asset
        );
    }

    private static Item.Settings settings(RegistryKey<EquipmentAsset> asset, EquipmentType type) {
        return new Item.Settings()
            .armor(material(asset), type)
            // .armor() auto-adds .enchantable(material.enchantmentValue()) - strip it again: the legendaries
            // can never be enchanted at the table / anvil (Protection 4 is written by the create*() stacks).
            .component(DataComponentTypes.ENCHANTABLE, null)
            .fireproof()
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
    }

    public static Item.Settings helmetSettings() {
        return settings(ModKeys.HELMET, EquipmentType.HELMET);
    }

    public static Item.Settings chestplateSettings() {
        return settings(ModKeys.CHESTPLATE, EquipmentType.CHESTPLATE);
    }

    public static Item.Settings leggingsSettings() {
        return settings(ModKeys.LEGGINGS, EquipmentType.LEGGINGS);
    }

    public static Item.Settings bootsSettings() {
        return settings(ModKeys.BOOTS, EquipmentType.BOOTS);
    }

    // ------------- the stacks the Shrine hands out: Protection 4 + the max of everything else (no Thorns) -------------

    public static ItemStack createHelmet(RegistryWrapper.WrapperLookup r) {
        return Enchants.on(new ItemStack(ModItems.SHRINE_HELMET), r)
            .add(Enchantments.PROTECTION, 4).add(Enchantments.UNBREAKING, 3).add(Enchantments.MENDING, 1)
            .add(Enchantments.RESPIRATION, 3).add(Enchantments.AQUA_AFFINITY, 1).apply();
    }

    public static ItemStack createChestplate(RegistryWrapper.WrapperLookup r) {
        return Enchants.on(new ItemStack(ModItems.SHRINE_CHESTPLATE), r)
            .add(Enchantments.PROTECTION, 4).add(Enchantments.UNBREAKING, 3).add(Enchantments.MENDING, 1).apply();
    }

    public static ItemStack createLeggings(RegistryWrapper.WrapperLookup r) {
        return Enchants.on(new ItemStack(ModItems.SHRINE_LEGGINGS), r)
            .add(Enchantments.PROTECTION, 4).add(Enchantments.UNBREAKING, 3).add(Enchantments.MENDING, 1)
            .add(Enchantments.SWIFT_SNEAK, 3).apply();
    }

    /** Boots: Depth Strider 3 (Frost Walker can't be combined with it - we picked Depth Strider). */
    public static ItemStack createBoots(RegistryWrapper.WrapperLookup r) {
        return Enchants.on(new ItemStack(ModItems.SHRINE_BOOTS), r)
            .add(Enchantments.PROTECTION, 4).add(Enchantments.UNBREAKING, 3).add(Enchantments.MENDING, 1)
            .add(Enchantments.FEATHER_FALLING, 4).add(Enchantments.DEPTH_STRIDER, 3).add(Enchantments.SOUL_SPEED, 3).apply();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.translatable("tooltip.shrinecraft." + key + ".1").formatted(Formatting.LIGHT_PURPLE));
        tooltip.accept(Text.translatable("tooltip.shrinecraft." + key + ".2").formatted(Formatting.GRAY));
    }
}
