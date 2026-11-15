package dev.tuffsmp.shrinecraft.item;

import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;

/**
 * Magic Essence - a wisp of raw magic. Every Shrine recipe asks for one.
 *
 * It has NO crafting recipe, drops from nothing and is not in the creative tab: the only way to get it is the admin command
 *   /tuffsmp essence <player> [amount]
 * (which is exactly the point - admins decide who may even attempt a Shrine).
 */
public class MagicEssenceItem extends Item {
    // ----------------------------------------------- TWEAK ME -----------------------------------------------
    /** How many essences fit in one stack. */
    public static final int MAX_STACK = 64;
    // ---------------------------------------------------------------------------------------------------------

    public MagicEssenceItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .maxCount(MAX_STACK)
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);   // shimmers like magic
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.translatable("tooltip.shrinecraft.magic_essence.1").formatted(Formatting.LIGHT_PURPLE));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.magic_essence.2").formatted(Formatting.GRAY));
    }
}
