package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.ability.Abilities;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.util.Enchants;
import dev.tuffsmp.shrinecraft.util.Msg;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
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
 * Cutlass of Doom - a plain netherite sword with a heavy enchant set and the Doom Surge ability (it is just a sword: no pickaxe / shovel mining).
 *
 * Ability "Doom Surge": sneak + right-click for 1.5x melee damage for 5 seconds (glowing teal sparks),
 * then a 45 second cooldown.
 */
public class CutlassOfDoomItem extends Item {
    // ======================= TWEAK ME (all numbers for this item) =======================
    /** Damage multiplier while Doom Surge is on (1.5 = +50%). */
    public static final float DAMAGE_MULTIPLIER = 1.5F;
    /** How long Doom Surge lasts, in seconds. */
    public static final int BUFF_SECONDS = 5;
    /** Cooldown AFTER the buff ends, in seconds (the cooldown timer starts at activation and is BUFF + this long). */
    public static final int COOLDOWN_SECONDS = 45;
    /** Also give the player the vanilla "Glowing" outline while the buff is on. */
    public static final boolean GLOWING_OUTLINE = true;
    /** Sword damage bonus (vanilla netherite sword = 3.0 + 4.0 material bonus = 7). */
    public static final float ATTACK_DAMAGE = 3.0F;
    /** Sword attack speed modifier (vanilla netherite sword = -2.4). */
    public static final float ATTACK_SPEED = -2.4F;
    // ===================================================================================

    private static final DustParticleEffect TEAL_DUST = new DustParticleEffect(0x2FE3C8, 1.2F);

    public CutlassOfDoomItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .sword(ToolMaterial.NETHERITE, ATTACK_DAMAGE, ATTACK_SPEED)
            // .sword() auto-adds .enchantable(material.enchantmentValue()) - strip it again: the legendaries
            // can never be enchanted at the table / anvil (their own enchants are written by createStack()).
            .component(DataComponentTypes.ENCHANTABLE, null)
            .fireproof()
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
    }

    /**
     * The stack the Shrine hands out.
     * Sword enchantments only (it is just a sword): Smite / Bane of Arthropods can't coexist with Sharpness, so we keep Sharpness.
     * No Knockback / Fire Aspect, no curses, and no mining enchantments (Efficiency / Fortune do nothing for a sword).
     */
    public static ItemStack createStack(RegistryWrapper.WrapperLookup registries) {
        ItemStack stack = new ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.CUTLASS_OF_DOOM);
        return Enchants.on(stack, registries)
            .add(Enchantments.SHARPNESS, 5)
            .add(Enchantments.LOOTING, 6)
            .add(Enchantments.SWEEPING_EDGE, 3)
            .add(Enchantments.UNBREAKING, 3)
            .add(Enchantments.MENDING, 1)
            .apply();
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        // MAIN HAND ONLY: with another melee weapon in the main hand a sword's use() passes, and vanilla then tries the off-hand - a Cutlass sitting
        // there would fire Doom Surge when you sneak + right-click with the OTHER weapon. Damage only ever counts for the main-hand weapon anyway.
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        if (!user.isSneaking()) {
            return super.use(world, user, hand);
        }
        ItemStack stack = user.getStackInHand(hand);
        long now = world.getTime();
        if (Abilities.onCooldown(stack, now)) {
            return ActionResult.FAIL; // the ability bar at the top of the screen shows the time left
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        Abilities.start(stack, now, BUFF_SECONDS * 20, (BUFF_SECONDS + COOLDOWN_SECONDS) * 20);
        if (GLOWING_OUTLINE) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, BUFF_SECONDS * 20, 0, true, false, true));
        }
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.8F, 1.4F);
        Msg.actionBar(user, Text.literal("Doom Surge! x" + DAMAGE_MULTIPLIER + " damage for " + BUFF_SECONDS + "s").formatted(Formatting.AQUA));
        return ActionResult.SUCCESS;
    }

    public static boolean isActive(ItemStack stack, long now) {
        Long until = stack.get(ModComponents.ABILITY_UNTIL);
        return until != null && now < until;
    }

    /**
     * Vanilla asks the weapon for extra attack damage when the player swings. We add exactly the amount
     * that turns (base damage + enchantment damage) into x1.5 of itself.
     */
    @Override
    public float getBonusAttackDamage(Entity target, float baseAttackDamage, DamageSource damageSource) {
        if (damageSource.getAttacker() instanceof LivingEntity attacker && attacker.getEntityWorld() instanceof ServerWorld world) {
            ItemStack weapon = attacker.getWeaponStack();
            if (weapon.getItem() instanceof CutlassOfDoomItem && isActive(weapon, world.getTime())
                && !dev.tuffsmp.shrinecraft.ability.TrustManager.trusts(attacker, target)) {
                float withEnchants = EnchantmentHelper.getDamage(world, weapon, target, damageSource, baseAttackDamage);
                return (DAMAGE_MULTIPLIER - 1.0F) * withEnchants;
            }
        }
        return 0.0F;
    }

    /** Teal sparks while the buff is on. */
    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && entity instanceof ServerPlayerEntity player && isActive(stack, world.getTime())) {
            world.spawnParticles(TEAL_DUST, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.4, 0.6, 0.4, 0.0);
            if (world.getTime() % 3 == 0) {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SOUL_FIRE_FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0.3, 0.05, 0.3, 0.01);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.translatable("tooltip.shrinecraft.cutlass_of_doom.1").formatted(Formatting.AQUA));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.cutlass_of_doom.2").formatted(Formatting.GRAY));
    }
}
