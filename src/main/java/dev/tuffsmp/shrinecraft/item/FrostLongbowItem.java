package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.ability.Abilities;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.util.Msg;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import net.minecraft.world.World;

/**
 * Frost Longbow - behaves like a bow. Two things:
 *  1. A FULLY drawn shot hurts with armor-ignoring "true" damage (damage type in DamageSourcesMixin). It no longer freezes anything.
 *  2. ACTIVATED ABILITY "Deep Freeze": sneak + right-click arms it for ARM_SECONDS; the next arrow you shoot (any draw) makes the target STOP
 *     COMPLETELY (frozen solid, FREEZE_SECONDS) - see ability/DeepFreezeManager. The cooldown (COOLDOWN_SECONDS) starts when you arm it.
 * (Hit effects live in CombatHooks.)
 */
public class FrostLongbowItem extends BowItem {
    // ======================= TWEAK ME (all numbers for this item) =======================
    /** How long Deep Freeze stays armed after you activate it, in seconds (the next arrow you shoot in that time is the freezing one). */
    public static final int ARM_SECONDS = 10;
    /** Cooldown in seconds, counted from the moment you arm it (so it is ready again 45 s after activation). */
    public static final int COOLDOWN_SECONDS = 45;
    /** How long the target is frozen solid (cannot move at all, but can still look around / eat / use items) after a Deep Freeze hit, in seconds. */
    public static final float FREEZE_SECONDS = 5.0F;
    /** True = a full-draw hit ignores armor (true damage). False = normal arrow damage. */
    public static final boolean TRUE_DAMAGE = true;
    // ===================================================================================

    /** Marker put on arrows that came from a full draw (armor-ignoring damage). */
    public static final String FROST_ARROW_TAG = "shrinecraft_frost_arrow";
    /** Marker put on the one arrow shot while Deep Freeze is armed (freezes the target solid). */
    public static final String DEEP_FREEZE_ARROW_TAG = "shrinecraft_deep_freeze_arrow";

    public FrostLongbowItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .maxDamage(384)
            // NO .enchantable(): the legendaries can never be enchanted at the table / anvil (Power etc. are built in).
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
    }

    public static boolean isArmed(ItemStack stack, long now) {
        Long until = stack.get(ModComponents.ABILITY_UNTIL);
        return until != null && now < until;
    }

    /** Sneak + right-click (ready, not already armed) arms Deep Freeze. Otherwise the bow draws normally (even while sneaking). */
    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        long now = world.getTime();
        if (!user.isSneaking() || isArmed(stack, now) || Abilities.onCooldown(stack, now)) {
            return super.use(world, user, hand);
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        Abilities.start(stack, now, ARM_SECONDS * 20, COOLDOWN_SECONDS * 20);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_POWDER_SNOW_BREAK, SoundCategory.PLAYERS, 1.0F, 0.6F);
        Msg.actionBar(user, Text.literal("Deep Freeze armed! Your next arrow freezes the target solid (" + ARM_SECONDS + "s)").formatted(Formatting.AQUA));
        return ActionResult.SUCCESS;
    }

    /**
     * Tags a freshly created arrow: full draw (critical) = armor-ignoring frost arrow; and if Deep Freeze is armed, THIS arrow is the Deep
     * Freeze arrow (and the arming is used up; the cooldown keeps running). Public + static so the self-test can run it.
     */
    public static ProjectileEntity tagArrow(ProjectileEntity arrow, ItemStack bow, long now, boolean critical) {
        if (critical) {
            arrow.addCommandTag(FROST_ARROW_TAG);
        }
        if (isArmed(bow, now)) {
            arrow.addCommandTag(DEEP_FREEZE_ARROW_TAG);
            bow.set(ModComponents.ABILITY_UNTIL, now);
        }
        return arrow;
    }

    /** In vanilla's BowItem the "critical" flag is true exactly when the bow was fully drawn. */
    @Override
    protected ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical) {
        ProjectileEntity arrow = super.createArrowEntity(world, shooter, weaponStack, projectileStack, critical);
        return tagArrow(arrow, weaponStack, world.getTime(), critical);
    }

    /** Snowflakes around the archer while Deep Freeze is armed. */
    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && entity instanceof ServerPlayerEntity player && isArmed(stack, world.getTime())) {
            world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.6, 0.4, 0.01);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.translatable("tooltip.shrinecraft.frost_longbow.1").formatted(Formatting.AQUA));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.frost_longbow.2").formatted(Formatting.GRAY));
    }
}
