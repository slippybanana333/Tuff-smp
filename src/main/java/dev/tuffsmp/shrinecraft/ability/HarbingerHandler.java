package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.item.HarbingerOfDeathItem;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.util.Msg;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Harbinger of Death behaviours, all with the Harbinger in the MAIN hand:
 *  - 3x3x3 mining (when its mode is "3x3x3")
 *  - swap-hands key (F)  = switch 3x3x3 <-> single block
 *  - Shift + swap-hands  = switch Fortune 3 <-> Silk Touch
 * The swap key is intercepted by ServerPlayNetworkHandlerMixin -> onSwapKey(), so the pickaxe stays in the main hand.
 */
public final class HarbingerHandler {
    private HarbingerHandler() {}

    /** Guards against the extra breaks triggering more 3x3x3 breaks. */
    private static boolean breaking = false;

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(HarbingerHandler::afterBreak);
    }

    // ------------------------------------------------------------ swap key (F)

    /** Called from the packet mixin. Returns true if the swap was used up as a mode change (the packet must then be ignored). */
    public static boolean onSwapKey(ServerPlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (!main.isOf(ModItems.HARBINGER_OF_DEATH)) {
            return false;
        }
        if (player.isSneaking()) {
            toggle(player, main);
        } else {
            toggleWide(player, main);
        }
        return true;
    }

    // ------------------------------------------------------------ 3x3x3 mining

    private static void afterBreak(World world, net.minecraft.entity.player.PlayerEntity player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (breaking || !(world instanceof ServerWorld) || !(player instanceof ServerPlayerEntity sp)) {
            return;
        }
        ItemStack main = sp.getMainHandStack();
        if (!main.isOf(ModItems.HARBINGER_OF_DEATH) || !HarbingerOfDeathItem.isWide(main)) {
            return;
        }
        int r = HarbingerOfDeathItem.AREA_RADIUS;
        breaking = true;
        try {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos p = pos.add(dx, dy, dz);
                        BlockState s = world.getBlockState(p);
                        // Only blocks a pickaxe can break: skip air, bedrock-like blocks and anything not pickaxe-mineable.
                        if (s.isAir() || s.getHardness(world, p) < 0.0F || !s.isIn(BlockTags.PICKAXE_MINEABLE)) {
                            continue;
                        }
                        sp.interactionManager.tryBreakBlock(p);
                        if (main.isEmpty()) {
                            return; // pickaxe broke
                        }
                    }
                }
            }
        } finally {
            breaking = false;
        }
    }

    // ------------------------------------------------------------ mode switches

    public static void toggleWide(ServerPlayerEntity player, ItemStack stack) {
        boolean wide = !HarbingerOfDeathItem.isWide(stack);
        stack.set(ModComponents.HARBINGER_WIDE, wide);
        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, wide ? 1.2F : 0.7F);
        Msg.actionBar(player, Text.literal("Harbinger: " + (wide ? "3x3x3 mining" : "single block mining")).formatted(Formatting.DARK_PURPLE));
    }

    public static void toggle(ServerPlayerEntity player, ItemStack stack) {
        boolean silk = !HarbingerOfDeathItem.isSilk(stack);
        HarbingerOfDeathItem.applyMode(stack, silk, player.getEntityWorld().getRegistryManager());
        player.getEntityWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, silk ? 1.5F : 0.8F);
        Msg.actionBar(player, Text.literal("Harbinger: " + (silk ? "Silk Touch" : "Fortune " + HarbingerOfDeathItem.FORTUNE_LEVEL)).formatted(Formatting.DARK_PURPLE));
    }
}
