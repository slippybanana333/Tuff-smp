package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.util.Enchants;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;

/**
 * Harbinger of Death - a netherite pickaxe with Efficiency 5. Hold it in your MAIN hand and mine.
 *
 *  Swap-hands key (F)          : flips between "3x3x3" mode (mines a whole cube) and "single block" mode.
 *  Sneak + swap-hands (Shift+F): flips between Fortune 3 and Silk Touch (never both).
 *  The item never actually moves to the offhand - the swap key is caught on the server (ServerPlayNetworkHandlerMixin)
 *  and turned into a mode change. Both choices are stored on the item (silk as real enchantments).
 */
public class HarbingerOfDeathItem extends Item {
    // ======================= TWEAK ME (all numbers for this item) =======================
    public static final int EFFICIENCY_LEVEL = 5;
    public static final int FORTUNE_LEVEL = 3;
    /** Half-size of the mined cube: 1 = 3x3x3, 2 = 5x5x5. */
    public static final int AREA_RADIUS = 1;
    /** Pickaxe damage bonus / speed (vanilla netherite pickaxe = 1.0 / -2.8). */
    public static final float ATTACK_DAMAGE = 1.0F;
    public static final float ATTACK_SPEED = -2.8F;
    // ===================================================================================

    public HarbingerOfDeathItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .pickaxe(ToolMaterial.NETHERITE, ATTACK_DAMAGE, ATTACK_SPEED)
            // .pickaxe() auto-adds .enchantable(material.enchantmentValue()) - strip it again: the legendaries
            // can never be enchanted at the table / anvil (Efficiency/Fortune/... are written by createStack()).
            .component(DataComponentTypes.ENCHANTABLE, null)
            .fireproof()
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE)
            .component(ModComponents.HARBINGER_SILK, false)
            .component(ModComponents.HARBINGER_WIDE, true);
    }

    public static ItemStack createStack(RegistryWrapper.WrapperLookup registries) {
        ItemStack stack = new ItemStack(ModItems.HARBINGER_OF_DEATH);
        stack.set(ModComponents.HARBINGER_SILK, false);
        Enchants.on(stack, registries).add(Enchantments.EFFICIENCY, EFFICIENCY_LEVEL).apply();
        applyMode(stack, false, registries);
        return stack;
    }

    /** True = mines the 3x3x3 cube (default), false = single block. */
    public static boolean isWide(ItemStack stack) {
        return stack.getOrDefault(ModComponents.HARBINGER_WIDE, true);
    }

    public static boolean isSilk(ItemStack stack) {
        return stack.getOrDefault(ModComponents.HARBINGER_SILK, false);
    }

    /** Sets Silk Touch (silk = true) or Fortune 3 (silk = false) - exactly one of them, never both. */
    public static void applyMode(ItemStack stack, boolean silk, RegistryWrapper.WrapperLookup registries) {
        stack.set(ModComponents.HARBINGER_SILK, silk);
        Enchants.Builder b = Enchants.on(stack, registries);
        if (silk) {
            b.add(Enchantments.SILK_TOUCH, 1);
        } else {
            b.add(Enchantments.FORTUNE, FORTUNE_LEVEL);
        }
        b.replace(Enchantments.FORTUNE, Enchantments.SILK_TOUCH);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.literal("Mode: " + (isWide(stack) ? "3x3x3" : "Single block") + " / " + (isSilk(stack) ? "Silk Touch" : "Fortune " + FORTUNE_LEVEL)).formatted(Formatting.DARK_PURPLE));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.harbinger_of_death.1").formatted(Formatting.GRAY));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.harbinger_of_death.2").formatted(Formatting.GRAY));
    }
}
