package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.util.Msg;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Cloud Lance - a vanilla 1.21.11 netherite spear (same jab, same charge attack, same damage and reach).
 *
 * Extras:
 *  - while you hold it nothing slows you down (cobwebs, sweet berry bushes, soul sand, honey, Slowness) - see EntityMixin / LivingEntityMixin
 *  - sneak + right-click: ONE dash (launches you like Riptide 3, no water needed), then a 20 second cooldown
 */
public class CloudLanceItem extends Item {
    // ======================= TWEAK ME (all numbers for this item) =======================
    /** How many dash charges the lance holds (1 = a single dash, then the cooldown). There is no HUD for it any more. */
    public static final int DASH_CHARGES = 1;
    /** Seconds for a used charge to come back. */
    public static final int DASH_RECHARGE_SECONDS = 20;
    /** Riptide level the dash imitates. Launch speed = 1.5 + 0.75 * (level - 1) blocks per tick. Riptide 3 = 3.0. */
    public static final int DASH_RIPTIDE_LEVEL = 3;
    /** Damage the spinning dash does to things you plough through (vanilla Riptide = 8.0). */
    public static final float DASH_SPIN_DAMAGE = 8.0F;
    /** How long the spin animation / spin damage lasts, in ticks (vanilla Riptide = 20). */
    public static final int DASH_SPIN_TICKS = 20;
    // ===================================================================================

    public CloudLanceItem(Item.Settings settings) {
        super(settings);
    }

    /** Exactly the vanilla netherite spear numbers, plus unbreakable and a ready dash. */
    public static Item.Settings settings() {
        return new Item.Settings()
            .spear(ToolMaterial.NETHERITE, 1.15F, 1.2F, 0.4F, 2.5F, 7.0F, 5.5F, 5.1F, 8.75F, 4.6F)
            .fireproof()
            .rarity(Rarity.EPIC)
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE)
            .component(ModComponents.DASH_READY_AT, fullCharges());
    }

    public static List<Long> fullCharges() {
        List<Long> list = new ArrayList<>();
        for (int i = 0; i < DASH_CHARGES; i++) {
            list.add(0L);
        }
        return list;
    }

    /** Is the player holding a Cloud Lance in either hand? Used by the slow-immunity mixins (runs on client and server). */
    public static boolean isHeldBy(PlayerEntity player) {
        return player.getMainHandStack().getItem() instanceof CloudLanceItem || player.getOffHandStack().getItem() instanceof CloudLanceItem;
    }

    /** Ready-at list normalised to exactly DASH_CHARGES entries. */
    public static List<Long> readyTimes(ItemStack stack) {
        List<Long> stored = stack.getOrDefault(ModComponents.DASH_READY_AT, List.of());
        List<Long> out = new ArrayList<>(stored);
        while (out.size() < DASH_CHARGES) {
            out.add(0L);
        }
        return out.subList(0, DASH_CHARGES);
    }

    public static float dashStrength() {
        return 1.5F + 0.75F * (DASH_RIPTIDE_LEVEL - 1);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (!user.isSneaking()) {
            return super.use(world, user, hand); // normal spear: starts the charge attack
        }
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        ItemStack stack = user.getStackInHand(hand);
        long now = world.getTime();
        List<Long> ready = new ArrayList<>(readyTimes(stack));
        int chosen = -1;
        long soonest = Long.MAX_VALUE;
        for (int i = 0; i < ready.size(); i++) {
            if (ready.get(i) <= now) {
                chosen = i;
                break;
            }
            soonest = Math.min(soonest, ready.get(i));
        }
        if (chosen < 0) {
            Msg.actionBar(user, Text.literal("No dash charges - next in " + Msg.seconds(soonest - now)).formatted(Formatting.GRAY));
            return ActionResult.FAIL;
        }
        ready.set(chosen, now + DASH_RECHARGE_SECONDS * 20L);
        stack.set(ModComponents.DASH_READY_AT, List.copyOf(ready));
        dash((ServerPlayerEntity) user, stack);
        return ActionResult.SUCCESS;
    }

    /** Same maths vanilla uses for a Riptide throw (see TridentItem), minus the water requirement. */
    private static void dash(ServerPlayerEntity player, ItemStack stack) {
        float strength = dashStrength();
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        float x = -MathHelper.sin(yaw * (float) (Math.PI / 180.0)) * MathHelper.cos(pitch * (float) (Math.PI / 180.0));
        float y = -MathHelper.sin(pitch * (float) (Math.PI / 180.0));
        float z = MathHelper.cos(yaw * (float) (Math.PI / 180.0)) * MathHelper.cos(pitch * (float) (Math.PI / 180.0));
        float len = MathHelper.sqrt(x * x + y * y + z * z);
        player.addVelocity(x * strength / len, y * strength / len, z * strength / len);
        player.useRiptide(DASH_SPIN_TICKS, DASH_SPIN_DAMAGE, stack);
        if (player.isOnGround()) {
            player.move(MovementType.SELF, new Vec3d(0.0, 1.1999999F, 0.0));
        }
        player.velocityDirty = true;
        player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        ServerWorld world = player.getEntityWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
        world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 25, 0.4, 0.4, 0.4, 0.05);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        tooltip.accept(Text.translatable("tooltip.shrinecraft.cloud_lance.1").formatted(Formatting.AQUA));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.cloud_lance.2").formatted(Formatting.GRAY));
    }
}
