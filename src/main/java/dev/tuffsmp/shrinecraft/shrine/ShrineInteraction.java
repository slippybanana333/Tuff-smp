package dev.tuffsmp.shrinecraft.shrine;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipe.Ingredient;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * All the right-click rules of the Shrine, in one place.
 *
 *  UNCONFIGURED : admin (permission level 2+) sneak + right-click with an EMPTY hand -> recipe-picker GUI
 *  CONFIGURED   : right-click               -> list the required materials (green = have, red = missing)
 *                 sneak + right-click        -> try to craft: consume everything + give the item + become CLAIMED,
 *                                               or consume NOTHING and show what is missing
 *  CLAIMED      : any right-click            -> "already used"
 */
public final class ShrineInteraction {
    private ShrineInteraction() {}

    public static void register() {
        // Vanilla skips a block's onUse when you sneak while holding an item. Crafting needs sneak + right-click while holding
        // materials, so we step in before vanilla and call the same code.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != Hand.MAIN_HAND || !player.isSneaking()) {
                return ActionResult.PASS;
            }
            if (!(world.getBlockState(hit.getBlockPos()).getBlock() instanceof ShrineBlock)) {
                return ActionResult.PASS;
            }
            boolean holdingSomething = !player.getMainHandStack().isEmpty() || !player.getOffHandStack().isEmpty();
            if (!holdingSomething) {
                return ActionResult.PASS; // vanilla will call ShrineBlock.onUse for us
            }
            return handleUse(world, hit.getBlockPos(), player);
        });
    }

    public static boolean isAdmin(PlayerEntity player) {
        return player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS);
    }

    /** Entry point used by ShrineBlock.onUse (and the callback above). */
    public static ActionResult handleUse(World world, BlockPos pos, PlayerEntity player) {
        if (world.isClient()) {
            return ActionResult.SUCCESS;
        }
        if (!(world.getBlockEntity(pos) instanceof ShrineBlockEntity shrine) || !(player instanceof ServerPlayerEntity sp)) {
            return ActionResult.PASS;
        }
        switch (shrine.getShrineState()) {
            case UNCONFIGURED -> onUnconfigured(sp, shrine);
            case CONFIGURED -> {
                ShrineRecipe recipe = shrine.getRecipe();
                if (recipe == null) { // saved recipe id no longer exists - treat as unconfigured so an admin can fix it
                    sp.sendMessage(Text.literal("This Shrine points to a recipe that no longer exists. Ask an admin to replace this Shrine.").formatted(Formatting.RED), false);
                } else if (sp.isSneaking()) {
                    tryCraft(sp, shrine, recipe);
                } else {
                    showRequirements(sp, recipe, false);
                }
            }
            case CLAIMED -> {
                sp.sendMessage(Text.literal("This Shrine has already been used.").formatted(Formatting.DARK_GRAY), true);
                sp.getEntityWorld().playSound(null, pos, SoundEvents.BLOCK_DEEPSLATE_BRICKS_HIT, SoundCategory.BLOCKS, 0.8F, 0.6F);
            }
        }
        return ActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ UNCONFIGURED

    private static void onUnconfigured(ServerPlayerEntity player, ShrineBlockEntity shrine) {
        boolean emptyHand = player.getMainHandStack().isEmpty();
        if (player.isSneaking() && emptyHand) {
            if (!isAdmin(player)) {
                player.sendMessage(Text.literal("Only admins (permission level 2+) can configure a Shrine.").formatted(Formatting.RED), true);
                return;
            }
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inventory, p) -> new ShrineMenu(syncId, inventory, shrine),
                Text.literal("Shrine - choose the reward")));
        } else {
            player.sendMessage(Text.literal("The Shrine is dormant. An admin must sneak + right-click it with an empty hand to choose its reward.").formatted(Formatting.LIGHT_PURPLE), true);
        }
    }

    // ------------------------------------------------------------------ CONFIGURED

    /** Chat list of the required materials; "have / need" per line, green when enough, red when short. */
    public static void showRequirements(ServerPlayerEntity player, ShrineRecipe recipe, boolean failedCraft) {
        player.sendMessage(header(recipe), false);
        if (recipe.placeholder()) {
            player.sendMessage(Text.literal("  [PLACEHOLDER RECIPE - the real recipe has not been decided yet]").formatted(Formatting.RED, Formatting.BOLD), false);
        }
        if (!recipe.isCraftable()) {
            player.sendMessage(Text.literal("  This recipe has not been set up yet - it cannot be crafted. Ask an admin.").formatted(Formatting.RED), false);
            return;
        }
        if (failedCraft) {
            player.sendMessage(Text.literal("  Missing materials - NOTHING was taken:").formatted(Formatting.RED), false);
        } else {
            player.sendMessage(Text.literal("  Required (sneak + right-click to craft):").formatted(Formatting.GRAY), false);
        }
        for (Ingredient in : recipe.ingredients()) {
            int have = count(player, in);
            boolean enough = have >= in.count();
            MutableText line = Text.literal("   " + (enough ? "✔ " : "✘ ")).formatted(enough ? Formatting.GREEN : Formatting.RED);
            line.append(Text.literal(Math.min(have, 9999) + "/" + in.count() + "  ").formatted(enough ? Formatting.GREEN : Formatting.RED));
            line.append(in.displayName().copy().formatted(enough ? Formatting.GREEN : Formatting.RED));
            player.sendMessage(line, false);
        }
    }

    private static Text header(ShrineRecipe recipe) {
        return Text.literal("== Shrine: " + recipe.displayName() + " ==").formatted(Formatting.GOLD, Formatting.BOLD);
    }

    private static void tryCraft(ServerPlayerEntity player, ShrineBlockEntity shrine, ShrineRecipe recipe) {
        if (!recipe.isCraftable()) {
            showRequirements(player, recipe, false);
            return;
        }
        // 1) check EVERYTHING first - nothing is consumed unless the whole list is covered
        for (Ingredient in : recipe.ingredients()) {
            if (count(player, in) < in.count()) {
                showRequirements(player, recipe, true);
                player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.BLOCKS, 0.8F, 0.6F);
                return;
            }
        }
        // 2) consume
        for (Ingredient in : recipe.ingredients()) {
            remove(player, in, in.count());
        }
        // 3) reward + permanently claim
        ServerWorld world = player.getEntityWorld();
        ItemStack reward = recipe.createOutput(world.getRegistryManager());
        player.getInventory().offerOrDrop(reward);
        shrine.claim(player.getUuid());
        ShrineCraft.LOGGER.info("{} claimed '{}' from the Shrine at {}", player.getName().getString(), recipe.id(), shrine.getPos().toShortString());

        BlockPos pos = shrine.getPos();
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0F, 0.8F);
        world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 0.7F);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 60, 0.5, 0.7, 0.5, 0.3);
        world.getServer().getPlayerManager().broadcast(
            Text.literal(player.getName().getString() + " claimed ").formatted(Formatting.GOLD)
                .append(reward.toHoverableText().copy())
                .append(Text.literal(" from a Shrine!").formatted(Formatting.GOLD)),
            false);
    }

    // ------------------------------------------------------------------ inventory helpers (main inventory + offhand only)

    private static List<ItemStack> usableStacks(PlayerEntity player) {
        PlayerInventory inv = player.getInventory();
        List<ItemStack> list = new ArrayList<>(37);
        for (int slot = 0; slot < 36; slot++) {
            list.add(inv.getStack(slot));
        }
        list.add(player.getOffHandStack());
        return list;
    }

    public static int count(PlayerEntity player, Item item) {
        int total = 0;
        for (ItemStack stack : usableStacks(player)) {
            if (stack.isOf(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** How many of this ingredient (item + optional potion type) the player holds (main inventory + offhand). */
    public static int count(PlayerEntity player, Ingredient ingredient) {
        int total = 0;
        for (ItemStack stack : usableStacks(player)) {
            if (ingredient.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void remove(PlayerEntity player, Ingredient ingredient, int amount) {
        int left = amount;
        for (ItemStack stack : usableStacks(player)) {
            if (left <= 0) {
                return;
            }
            if (ingredient.matches(stack)) {
                int take = Math.min(left, stack.getCount());
                stack.decrement(take);
                left -= take;
            }
        }
    }

    private static void remove(PlayerEntity player, Item item, int amount) {
        int left = amount;
        for (ItemStack stack : usableStacks(player)) {
            if (left <= 0) {
                return;
            }
            if (stack.isOf(item)) {
                int take = Math.min(left, stack.getCount());
                stack.decrement(take);
                left -= take;
            }
        }
    }
}
