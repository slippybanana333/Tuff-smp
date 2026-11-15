package dev.tuffsmp.shrinecraft.item;

import dev.tuffsmp.shrinecraft.ability.SilverManager;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.util.Msg;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;
import net.minecraft.world.World;

/**
 * Silver's Spawn Orb - carries a single, unique horse named Silver.
 *
 *  STORED   : right-click summons Silver next to you; the orb becomes RELEASED.
 *  RELEASED : sneak + right-click Silver (with the orb in hand) to take Silver back. If Silver was hurt or in a fight
 *             recently, the orb goes on COOLDOWN first; otherwise it is immediately STORED again.
 *  COOLDOWN : right-click only tells you how long is left. When the timer ends it quietly becomes STORED.
 *
 * All the Silver behaviour (stats, armor, trail, capture) lives in SilverManager.
 */
public class SilversSpawnOrbItem extends Item {
    // ======================= TWEAK ME (all numbers for this item) =======================
    /** Silver's stats relative to a "normal" horse. 1.5 = 50% better. */
    public static final double SPEED_MULTIPLIER = 1.5;
    public static final double JUMP_MULTIPLIER = 1.5;
    public static final double HEALTH_MULTIPLIER = 1.5;
    /** What a "normal" horse is worth (the average of vanilla's random horse stats). */
    public static final double NORMAL_HORSE_SPEED = 0.225;
    public static final double NORMAL_HORSE_JUMP = 0.7;
    public static final double NORMAL_HORSE_HEALTH = 22.5;
    /** Silver counts as "in combat" if it took damage (or was being targeted by a monster) within this many seconds. */
    public static final int COMBAT_WINDOW_SECONDS = 10;
    /** How long the orb is on cooldown after recalling Silver from a fight (also used if Silver dies), in seconds. */
    public static final int COOLDOWN_SECONDS = 1200; // 20 minutes
    /** Draw the cosmetic ice trail behind a moving Silver. */
    public static final boolean ICE_TRAIL = true;
    // ===================================================================================

    public SilversSpawnOrbItem(Item.Settings settings) {
        super(settings);
    }

    public static Item.Settings settings() {
        return new Item.Settings()
            .maxCount(1)
            .rarity(Rarity.EPIC)
            .fireproof()
            .component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE)
            .component(ModComponents.ORB_DATA, OrbData.DEFAULT)
            .component(DataComponentTypes.CUSTOM_MODEL_DATA, modelFlag(OrbData.State.STORED));
    }

    /** The resource pack picks the orb's 3D model from this string (stored / released / cooldown) - see assets/shrinecraft/items/silvers_spawn_orb.json. */
    private static CustomModelDataComponent modelFlag(OrbData.State state) {
        return new CustomModelDataComponent(List.of(), List.of(), List.of(state.asString()), List.of());
    }

    /** ALWAYS use this instead of stack.set(ORB_DATA, ...): it also updates the model flag so the orb looks right in every state. */
    public static void setData(ItemStack stack, OrbData data) {
        stack.set(ModComponents.ORB_DATA, data);
        stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, modelFlag(data.state()));
    }

    public static OrbData getData(ItemStack stack) {
        return stack.getOrDefault(ModComponents.ORB_DATA, OrbData.DEFAULT);
    }

    /** Gives a brand-new orb its unique id the first time anything touches it. */
    public static OrbData ensureId(ItemStack stack) {
        OrbData data = getData(stack);
        if (OrbData.NO_ID.equals(data.orbId())) {
            data = data.withId(UUID.randomUUID());
            SilversSpawnOrbItem.setData(stack, data);
        }
        return data;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) user;
        ServerWorld serverWorld = (ServerWorld) world;
        ItemStack stack = user.getStackInHand(hand);
        OrbData data = ensureId(stack).resolved(world.getTime());
        SilversSpawnOrbItem.setData(stack, data);

        switch (data.state()) {
            case STORED -> {
                SilverManager.summon(serverWorld, player, stack, data);
                return ActionResult.SUCCESS;
            }
            case COOLDOWN -> {
                Msg.actionBar(user, Text.literal("Silver is resting - ready in " + Msg.seconds(data.cooldownEndsAt() - world.getTime())).formatted(Formatting.AQUA));
                return ActionResult.FAIL;
            }
            case RELEASED -> {
                SilverManager.describeReleased(serverWorld, player, stack, data);
                return ActionResult.FAIL;
            }
        }
        return ActionResult.PASS;
    }

    /** While in an inventory: finish a cooldown quietly and tell the owner. */
    @Override
    public void inventoryTick(ItemStack stack, ServerWorld world, Entity entity, EquipmentSlot slot) {
        OrbData data = getData(stack);
        if (data.state() == OrbData.State.COOLDOWN && world.getTime() >= data.cooldownEndsAt()) {
            SilversSpawnOrbItem.setData(stack, data.resolved(world.getTime()));
            if (entity instanceof ServerPlayerEntity player) {
                Msg.actionBar(player, Text.literal("Silver is ready again!").formatted(Formatting.AQUA));
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, TooltipDisplayComponent display, Consumer<Text> tooltip, TooltipType type) {
        OrbData d = getData(stack);
        String state = switch (d.state()) {
            case STORED -> "Stored - right-click to summon Silver";
            case RELEASED -> "Silver is out - sneak + right-click Silver to recall";
            case COOLDOWN -> "Cooldown";
        };
        tooltip.accept(Text.literal(state).formatted(Formatting.AQUA));
        tooltip.accept(Text.translatable("tooltip.shrinecraft.silvers_spawn_orb.1").formatted(Formatting.GRAY));
    }
}
