package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.ability.LavaBombManager;
import dev.tuffsmp.shrinecraft.ability.Abilities;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.registry.ModKeys;
import dev.tuffsmp.shrinecraft.util.Enchants;
import dev.tuffsmp.shrinecraft.util.Msg;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
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
 * Molten Greataxe - a netherite axe (Silk Touch, Sharpness 5, Unbreaking 3, plus its private Fire Aspect 2).
 *
 * Ability "Lava Bomb": sneak + right-click arms it. The NEXT melee hit on a living thing within the
 * arming window drops a small cluster of temporary lava at the target's feet.
 */
public class MoltenGreataxeItem extends AxeItem {
    // ======================= TWEAK ME (all numbers for this item) =======================
    /** How long Lava Bomb stays armed after sneak + right-click, in seconds. */
    public static final int ARM_DURATION_SECONDS = 10;
    /** Cooldown that starts the moment you arm it (whether or not it ever lands), in seconds. */
    public static final int COOLDOWN_SECONDS = 60;
    /** How long each lava block lasts before turning back into air, in seconds. */
    public static final int LAVA_LIFETIME_SECONDS = 5;
    /**
     * Shape of the lava cluster, as {x, z} offsets from the block at the target's feet.
     * Default is a "plus" of 5 blocks. Add or remove pairs to change the shape.
     */
    public static final int[][] LAVA_CLUSTER_OFFSETS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    /** Axe damage bonus (vanilla netherite axe = 5.0). */
    public static final float ATTACK_DAMAGE = 5.0F;
    /** Axe attack speed modifier (vanilla netherite axe = -3.0). */
    public static final float ATTACK_SPEED = -3.0F;
    // ===================================================================================

    public MoltenGreataxeItem(Item.Settings settings) {
        super(ToolMaterial.NETHERITE, ATTACK_DAMAGE, ATTACK_SPEED, settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings().fireproof().rarity(Rarity.EPIC).component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
    }

    /** The fully enchanted stack the Shrine hands out. */
    public static ItemStack createStack(RegistryWrapper.WrapperLookup registries) {
        ItemStack stack = new ItemStack(dev.tuffsmp.shrinecraft.registry.ModItems.MOLTEN_GREATAXE);
        return Enchants.on(stack, registries)
            .add(Enchantments.SILK_TOUCH, 1)
            .add(Enchantments.SHARPNESS, 5)
            .add(Enchantments.UNBREAKING, 3)
            .add(ModKeys.MOLTEN_FIRE_ASPECT, 2)
            .apply();
    }

    /** Sneaking must NOT strip logs, otherwise you could never arm the ability while looking at a tree. */
    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player != null && player.isSneaking()) {
            return ActionResult.PASS;
        }
        return super.useOnBlock(context);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
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
        Abilities.start(stack, now, ARM_DURATION_SECONDS * 20, COOLDOWN_SECONDS * 20);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0F, 0.7F);
        Msg.actionBar(user, Text.literal("Lava Bomb armed - your next hit erupts! (" + ARM_DURATION_SECONDS + "s)").formatted(Formatting.GOLD));
        return ActionResult.SUCCESS;
    }

    /** Called on the server right after the axe successfully hits a living target. */
    @Override
    public void postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHit(stack, target, attacker);
        if (!(attacker instanceof ServerPlayerEntity player) || !(player.getEntityWorld() instanceof net.minecraft.server.world.ServerWorld world)) {
            return;
        }
        if (isArmed(stack, world.getTime())) {
            stack.remove(ModComponents.ABILITY_UNTIL); // one bomb per arming
            LavaBombManager.spawnCluster(world, player, target);
        }
    }

    public static boolean isArmed(ItemStack stack, long now) {
        Long until = stack.get(ModComponents.ABILITY_UNTIL);
        return until != null && now < until;
    }

    /** Armed glow: a few flame particles around the player every few ticks while armed. */
    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.world.ServerWorld world, net.minecraft.entity.Entity entity, net.minecraft.entity.EquipmentSlot slot) {
        if (slot == net.minecraft.entity.EquipmentSlot.MAINHAND && entity instanceof ServerPlayerEntity player && world.getTime() % 4 == 0) {
            if (isArmed(stack, world.getTime())) {
                world.spawnParticles(net.minecraft.particle.ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.35, 0.5, 0.35, 0.01);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.translatable("tooltip.shrinecraft.molten_greataxe.1").formatted(Formatting.GOLD));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.molten_greataxe.2").formatted(Formatting.GRAY));
    }
}
