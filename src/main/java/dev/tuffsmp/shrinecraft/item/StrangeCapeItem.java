package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.registry.ModKeys;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;

/**
 * "Strange" Cape - an Elytra in every way (glides, goes in the chest slot, same equip sound, same flight),
 * just unbreakable and drawn with its own mysterious cape texture. In 1.21.11 an elytra is simply an item with a
 * "glider" component plus an "equippable" component, so that is all this is.
 */
public class StrangeCapeItem extends Item {
    public StrangeCapeItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .maxDamage(432)
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.GLIDER, Unit.INSTANCE)
            .component(
                DataComponentTypes.EQUIPPABLE,
                EquippableComponent.builder(EquipmentSlot.CHEST)
                    .equipSound(SoundEvents.ITEM_ARMOR_EQUIP_ELYTRA)
                    .model(ModKeys.STRANGE_CAPE)
                    .damageOnHurt(false)
                    .build()
            )
            .repairable(Items.PHANTOM_MEMBRANE)
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.translatable("tooltip.shrinecraft.strange_cape.1").formatted(Formatting.DARK_PURPLE));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.strange_cape.2").formatted(Formatting.GRAY));
    }
}
