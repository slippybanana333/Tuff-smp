package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.item.SculkCannonItem;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Flips the Sculk Cannon's mode when the player swaps it into the offhand with the vanilla swap key (F).
 * (We watch for "was in the main hand last tick, now in the offhand", so it also works for any other way of moving it.)
 */
public final class CannonHandler {
    private CannonHandler() {}

    /** Per player: [cannon in main hand last tick, cannon in offhand last tick]. */
    private static final Map<UUID, boolean[]> LAST = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CannonHandler::tick);
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            boolean inMain = player.getMainHandStack().isOf(ModItems.SCULK_CANNON);
            boolean inOff = player.getOffHandStack().isOf(ModItems.SCULK_CANNON);
            boolean[] prev = LAST.computeIfAbsent(player.getUuid(), k -> new boolean[]{false, false});
            if (!inMain && inOff && prev[0] && !prev[1]) {
                flip(player, player.getOffHandStack());
            }
            prev[0] = inMain;
            prev[1] = inOff;
        }
    }

    private static void flip(ServerPlayerEntity player, ItemStack cannon) {
        int newMode = SculkCannonItem.flipMode(cannon, player.getEntityWorld().getRegistryManager());   // also unloads: charge it again in the new mode
        announce(player, newMode);
    }

    /** The clear mode indicator: big title text + action bar + sound + sculk particles. */
    public static void announce(ServerPlayerEntity player, int mode) {
        boolean wail = mode == SculkCannonItem.MODE_WAIL;
        Formatting color = wail ? Formatting.DARK_PURPLE : Formatting.DARK_AQUA;
        Text title = Text.literal("MODE " + mode).formatted(color, Formatting.BOLD);
        Text subtitle = Text.literal(SculkCannonItem.modeName(mode)).formatted(color);
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(3, 25, 8));
        player.networkHandler.sendPacket(new SubtitleS2CPacket(subtitle));
        player.networkHandler.sendPacket(new TitleS2CPacket(title));
        player.sendMessage(Text.literal("Sculk Cannon: " + SculkCannonItem.modeName(mode) + (wail ? "  (tipped arrows, +3 true damage)" : "  (multishot, blinds 5s)") + "  - charge it again").formatted(color), true);
        var world = player.getEntityWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
            wail ? SoundEvents.ENTITY_WARDEN_SONIC_CHARGE : SoundEvents.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.PLAYERS, 0.9F, wail ? 1.6F : 1.0F);
        world.spawnParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.2, player.getZ(), 14, 0.4, 0.4, 0.4, 0.03);
    }
}
