package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.util.Enchants;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import net.minecraft.world.World;

/**
 * Anaklusmos - an unbreakable trident with Riptide 5. It melees and rip-tide launches like a normal trident
 * (in water or rain, like vanilla), but it can NEVER be thrown.
 */
public class AnaklusmosItem extends TridentItem {
    // ======================= TWEAK ME =======================
    /** Riptide level. Launch speed = 1.5 + 0.75 * (level - 1), so 5 -> 4.5 blocks per tick. */
    public static final int RIPTIDE_LEVEL = 5;
    // ========================================================

    public AnaklusmosItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .rarity(Rarity.EPIC)
            .maxDamage(250)
            .attributeModifiers(TridentItem.createAttributeModifiers())
            .component(DataComponentTypes.TOOL, TridentItem.createToolComponent())
            // NO .enchantable(): the legendaries can never be enchanted at the table / anvil.
            // Riptide 5 comes built in via createStack() (a direct component write needs no enchantable component).
            .component(DataComponentTypes.WEAPON, new WeaponComponent(1))
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
    }

    public static ItemStack createStack(RegistryWrapper.WrapperLookup registries) {
        return Enchants.on(new ItemStack(ModItems.ANAKLUSMOS), registries).add(Enchantments.RIPTIDE, RIPTIDE_LEVEL).apply();
    }

    private static boolean hasRiptide(ItemStack stack, LivingEntity user) {
        return EnchantmentHelper.getTridentSpinAttackStrength(stack, user) > 0.0F;
    }

    /** Right-click only does something if the Riptide launch is possible; otherwise nothing (never a throw). */
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!hasRiptide(user.getStackInHand(hand), user)) {
            return ActionResult.FAIL;
        }
        return super.use(world, user, hand);
    }

    /** Safety net: if Riptide were ever removed, releasing the button still never throws the trident. */
    @Override
    public boolean onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!hasRiptide(stack, user)) {
            return false;
        }
        return super.onStoppedUsing(stack, world, user, remainingUseTicks);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        // NOTE: no "Riptide V" line here - the real enchantment already shows its own line; ours would duplicate it.
        tooltip.accept(Text.translatable("tooltip.shrinecraft.anaklusmos.2").formatted(Formatting.GRAY));
    }
}
