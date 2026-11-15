package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.ability.SculkWaveManager;
import dev.tuffsmp.shrinecraft.ability.Abilities;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.util.Enchants;
import dev.tuffsmp.shrinecraft.util.Msg;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.component.type.WeaponComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.MaceItem;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
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
 * Sculk Mace - a vanilla-behaving mace that grows stronger every time you kill a Warden with it.
 *
 * The tier (0-5) is stored on the item (data component "shrinecraft:sculk_tier"). Each tier re-applies a
 * specific set of enchantments (see TIER_ENCHANTS). At tier 5 you unlock "Sculk Wave".
 */
public class SculkMaceItem extends MaceItem {
    // ======================= TWEAK ME (all numbers for this item) =======================
    /** Chance (0.0 - 1.0) that killing a Warden with this mace advances it one tier. */
    public static final float TIER_UP_CHANCE = 0.5F;
    /** Highest tier. */
    public static final int MAX_TIER = 5;
    /** Tier at which Sculk Wave unlocks. */
    public static final int WAVE_UNLOCK_TIER = 5;
    /** How long Sculk Wave stays on after sneak + right-click, in seconds. */
    public static final int WAVE_DURATION_SECONDS = 15;
    /** Cooldown AFTER the wave window ends, in seconds (timer starts at activation and is DURATION + this long). */
    public static final int WAVE_COOLDOWN_SECONDS = 45;
    /** Damage dealt by each shockwave to everything it passes through, in hit points (6 = 3 hearts). Ignores armor. */
    public static final float WAVE_DAMAGE = 6.0F;
    /** Blast radius of each shockwave, in blocks from the thing you hit: EVERYTHING alive inside it takes the damage. */
    public static final double WAVE_RADIUS = 4.0;
    /** How fast the visible sculk ring expands outward, in blocks per tick (visual only; the damage is instant). */
    public static final double WAVE_SPEED = 1.0;

    /**
     * Enchantments that each tier applies. Index = tier. Each tier is the COMPLETE set for that tier: when the tier changes,
     * Density and Wind Burst are removed and the new set is applied (vanilla allows Density and Wind Burst together).
     * Owner's ladder (2026-11-15, supersedes the earlier list): tier 1 = Density I; tier 2 = Density II; tier 3 = Density II + Wind Burst I;
     * tier 4 = Density III + Wind Burst I; tier 5 = Density III + Wind Burst II and unlocks the Sculk Wave ability.
     * Format: { enchantment, level }.
     */
    public static final Object[][][] TIER_ENCHANTS = {
        /* tier 0 */ {},
        /* tier 1 */ {{Enchantments.DENSITY, 1}},
        /* tier 2 */ {{Enchantments.DENSITY, 2}},
        /* tier 3 */ {{Enchantments.DENSITY, 2}, {Enchantments.WIND_BURST, 1}},
        /* tier 4 */ {{Enchantments.DENSITY, 3}, {Enchantments.WIND_BURST, 1}},
        /* tier 5 */ {{Enchantments.DENSITY, 3}, {Enchantments.WIND_BURST, 2}},
    };
    // ===================================================================================

    public SculkMaceItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .rarity(Rarity.EPIC)
            .maxDamage(500)
            .component(DataComponentTypes.TOOL, MaceItem.createToolComponent())
            .repairable(net.minecraft.item.Items.BREEZE_ROD)
            .attributeModifiers(MaceItem.createAttributeModifiers())
            // NO .enchantable(): the legendaries can never be enchanted at the table / anvil.
            // The tier ladder (Density / Wind Burst) is applied programmatically by setTier().
            .component(DataComponentTypes.WEAPON, new WeaponComponent(1))
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE)
            .component(ModComponents.SCULK_TIER, 0);
    }

    /** A fresh tier-0 mace: no enchantments. */
    public static ItemStack createStack(RegistryWrapper.WrapperLookup registries) {
        return new ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.SCULK_MACE);
    }

    public static int getTier(ItemStack stack) {
        return stack.getOrDefault(ModComponents.SCULK_TIER, 0);
    }

    /** Sets the tier and re-applies the matching enchantments (removes old Density / Wind Burst first). */
    @SuppressWarnings("unchecked")
    public static void setTier(ItemStack stack, int tier, RegistryWrapper.WrapperLookup registries) {
        tier = Math.max(0, Math.min(MAX_TIER, tier));
        stack.set(ModComponents.SCULK_TIER, tier);
        Enchants.Builder b = Enchants.on(stack, registries);
        for (Object[] pair : TIER_ENCHANTS[tier]) {
            b.add((RegistryKey<Enchantment>) pair[0], (Integer) pair[1]);
        }
        b.replace(Enchantments.DENSITY, Enchantments.WIND_BURST);
    }

    /** Called when a Warden dies to this mace. Returns true if it advanced. */
    public static boolean tryAdvance(ServerPlayerEntity player, ItemStack stack, ServerWorld world) {
        int tier = getTier(stack);
        if (tier >= MAX_TIER) {
            return false;
        }
        if (world.getRandom().nextFloat() >= TIER_UP_CHANCE) {
            Msg.actionBar(player, Text.literal("The Sculk Mace hungers... (no advance this time)").formatted(Formatting.DARK_AQUA));
            return false;
        }
        setTier(stack, tier + 1, world.getRegistryManager());
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.PLAYERS, 0.6F, 1.6F);
        world.spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.8, 0.5, 0.05);
        Msg.chat(player, Text.literal("Sculk Mace advanced to tier " + (tier + 1) + "!").formatted(Formatting.AQUA, Formatting.BOLD));
        if (tier + 1 == WAVE_UNLOCK_TIER) {
            Msg.chat(player, Text.literal("Sculk Wave unlocked - sneak + right-click to unleash it.").formatted(Formatting.DARK_AQUA));
        }
        return true;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!user.isSneaking()) {
            return super.use(world, user, hand);
        }
        ItemStack stack = user.getStackInHand(hand);
        if (getTier(stack) >= WAVE_UNLOCK_TIER && Abilities.onCooldown(stack, world.getTime())) {
            return ActionResult.FAIL; // the ability bar at the top of the screen shows the time left
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        int tier = getTier(stack);
        if (tier < WAVE_UNLOCK_TIER) {
            Msg.actionBar(user, Text.literal("Sculk Wave unlocks at tier " + WAVE_UNLOCK_TIER + " (this mace is tier " + tier + ")").formatted(Formatting.DARK_AQUA));
            return ActionResult.FAIL;
        }
        Abilities.start(stack, world.getTime(), WAVE_DURATION_SECONDS * 20, (WAVE_DURATION_SECONDS + WAVE_COOLDOWN_SECONDS) * 20);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.PLAYERS, 1.0F, 1.2F);
        Msg.actionBar(user, Text.literal("Sculk Wave! Every hit sends a shockwave (" + WAVE_DURATION_SECONDS + "s)").formatted(Formatting.AQUA));
        return ActionResult.SUCCESS;
    }

    public static boolean isWaveActive(ItemStack stack, long now) {
        Long until = stack.get(ModComponents.ABILITY_UNTIL);
        return until != null && now < until && getTier(stack) >= WAVE_UNLOCK_TIER;
    }

    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHit(stack, target, attacker);
        if (attacker instanceof ServerPlayerEntity player && player.getEntityWorld() instanceof ServerWorld world && isWaveActive(stack, world.getTime())) {
            SculkWaveManager.launch(world, player, target);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        if (slot == EquipmentSlot.MAINHAND && entity instanceof ServerPlayerEntity player && isWaveActive(stack, world.getTime()) && world.getTime() % 3 == 0) {
            world.spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 0.3, player.getZ(), 2, 0.4, 0.1, 0.4, 0.01);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        int tier = getTier(stack);
        tooltip.accept(Text.literal("Tier " + tier + " / " + MAX_TIER).formatted(Formatting.DARK_AQUA));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.sculk_mace.1").formatted(Formatting.GRAY));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.sculk_mace.2").formatted(Formatting.GRAY));
    }
}
