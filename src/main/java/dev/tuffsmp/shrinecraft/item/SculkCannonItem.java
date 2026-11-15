package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.registry.ModKeys;
import dev.tuffsmp.shrinecraft.util.Enchants;
import dev.tuffsmp.shrinecraft.util.Msg;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import net.minecraft.world.World;

/**
 * Sculk Cannon - an unbreakable crossbow with two modes saved on the item.
 *
 *  Mode 1 "Blinding Volley": Multishot + endless ammo, plain arrows only, hits blind for 5s.
 *  Mode 2 "Piercing Wail"  : Quick Charge III + Piercing IV + endless ammo, tipped arrows allowed, hits deal +3 hp armor-ignoring damage.
 *
 * Swap hands (F) with the cannon in your main hand to flip the mode (the cannon lands in your offhand).
 * Swap back to the main hand to fire. The flip + indicator are handled in CannonHandler.
 * The hit effects are handled in CombatHooks (they look for the tags below on the arrow).
 */
public class SculkCannonItem extends CrossbowItem {
    // ======================= TWEAK ME (all numbers for this item) =======================
    /** Mode 1: Blindness duration on hit, in seconds. */
    public static final int BLIND_SECONDS = 5;
    /** Mode 2: extra armor-ignoring damage on every hit, in hit points (3 = 1.5 hearts). */
    public static final float WAIL_BONUS_DAMAGE = 3.0F;
    /** Mode 2 enchantment levels. */
    public static final int WAIL_QUICK_CHARGE_LEVEL = 3;
    public static final int WAIL_PIERCING_LEVEL = 4;
    // ===================================================================================

    public static final String BLIND_ARROW_TAG = "shrinecraft_cannon_blind";
    public static final String WAIL_ARROW_TAG = "shrinecraft_cannon_wail";

    public static final int MODE_VOLLEY = 1;
    public static final int MODE_WAIL = 2;

    /** Tells the (stack-blind) ammo predicates which mode is currently being used. Set around use()/usageTick(). */
    private static final ThreadLocal<Integer> CONTEXT_MODE = ThreadLocal.withInitial(() -> 0);

    public SculkCannonItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .maxCount(1)
            .maxDamage(465)
            .component(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)
            // NO .enchantable(): the legendaries can never be enchanted at the table / anvil.
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE)
            .component(ModComponents.CANNON_MODE, MODE_VOLLEY);
    }

    public static ItemStack createStack(RegistryWrapper.WrapperLookup registries) {
        ItemStack stack = new ItemStack(ModItems.SCULK_CANNON);
        setMode(stack, MODE_VOLLEY, registries);
        return stack;
    }

    public static int getMode(ItemStack stack) {
        return stack.getOrDefault(ModComponents.CANNON_MODE, MODE_VOLLEY);
    }

    public static String modeName(int mode) {
        return mode == MODE_WAIL ? "Piercing Wail" : "Blinding Volley";
    }

    /** Sets the mode and swaps the enchantments to match (does NOT touch what is loaded - the in-game flip uses flipMode, which unloads). */
    public static void setMode(ItemStack stack, int mode, RegistryWrapper.WrapperLookup registries) {
        stack.set(ModComponents.CANNON_MODE, mode);
        Enchants.Builder b = Enchants.on(stack, registries).add(ModKeys.ENDLESS_QUIVER, 1);
        if (mode == MODE_WAIL) {
            b.add(Enchantments.QUICK_CHARGE, WAIL_QUICK_CHARGE_LEVEL).add(Enchantments.PIERCING, WAIL_PIERCING_LEVEL);
        } else {
            b.add(Enchantments.MULTISHOT, 1);
        }
        b.replace(Enchantments.MULTISHOT, Enchantments.QUICK_CHARGE, Enchantments.PIERCING);
    }

    /**
     * The player flipped the mode (swap-hand key). Returns the new mode. Whatever is already loaded is UNLOADED: a Blinding Volley charge is 3 arrows,
     * and keeping them would let you fire 3 arrows with the other mode's effects (and a Piercing Wail charge is 1 tipped arrow that would fire as a
     * blinding one). Endless Quiver means nothing is lost - you simply have to charge it again in the new mode.
     */
    public static int flipMode(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        int newMode = getMode(stack) == MODE_VOLLEY ? MODE_WAIL : MODE_VOLLEY;
        setMode(stack, newMode, registries);
        stack.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT);
        return newMode;
    }

    // ---------------- which ammo each mode accepts ----------------

    private static boolean accepts(int mode, ItemStack ammo) {
        if (mode == MODE_VOLLEY) {
            return ammo.isOf(Items.ARROW); // plain, untipped arrows only
        }
        return ammo.isIn(ItemTags.ARROWS); // plain, tipped and spectral arrows
    }

    @Override
    public Predicate<ItemStack> getHeldProjectiles() {
        int mode = CONTEXT_MODE.get();
        return ammo -> accepts(mode, ammo);
    }

    @Override
    public Predicate<ItemStack> getProjectiles() {
        int mode = CONTEXT_MODE.get();
        return ammo -> accepts(mode, ammo);
    }

    private static <T> T withMode(ItemStack stack, Supplier<T> action) {
        int previous = CONTEXT_MODE.get();
        CONTEXT_MODE.set(getMode(stack));
        try {
            return action.get();
        } finally {
            CONTEXT_MODE.set(previous);
        }
    }

    // ---------------- using ----------------

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (hand == Hand.OFF_HAND) {
            if (!world.isClient()) {
                Msg.actionBar(user, Text.literal("Swap back to your main hand to fire the Sculk Cannon").formatted(Formatting.DARK_AQUA));
            }
            return ActionResult.FAIL;
        }
        return withMode(stack, () -> super.use(world, user, hand));
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        withMode(stack, () -> {
            super.usageTick(world, user, stack, remainingUseTicks);
            return null;
        });
    }

    /** Marks every arrow with the mode it was fired in, so the hit effects know what to do. */
    @Override
    protected ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical) {
        ProjectileEntity arrow = super.createArrowEntity(world, shooter, weaponStack, projectileStack, critical);
        arrow.addCommandTag(getMode(weaponStack) == MODE_WAIL ? WAIL_ARROW_TAG : BLIND_ARROW_TAG);
        return arrow;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        int mode = getMode(stack);
        tooltip.accept(Text.literal("Mode " + mode + ": " + modeName(mode)).formatted(mode == MODE_WAIL ? Formatting.DARK_PURPLE : Formatting.DARK_AQUA));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.sculk_cannon.1").formatted(Formatting.GRAY));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.sculk_cannon.2").formatted(Formatting.GRAY));
    }
}
