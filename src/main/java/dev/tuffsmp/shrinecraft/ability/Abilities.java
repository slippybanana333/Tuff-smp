package dev.tuffsmp.shrinecraft.ability;

import dev.tuffsmp.shrinecraft.item.CloudLanceItem;
import dev.tuffsmp.shrinecraft.item.CutlassOfDoomItem;
import dev.tuffsmp.shrinecraft.item.FrostLongbowItem;
import dev.tuffsmp.shrinecraft.item.MoltenGreataxeItem;
import dev.tuffsmp.shrinecraft.item.OrbData;
import dev.tuffsmp.shrinecraft.item.SculkMaceItem;
import dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * One place that knows every weapon ability's timer. The ability bar at the top of the screen (client/hud/AbilityBarHud) and the
 * items themselves both use it, so the numbers can never disagree. It replaces the old vanilla grey hotbar cooldown overlay.
 *
 * Timers live on the item (components ability_until = "active until", cooldown_until = "ready again at"), so they survive
 * dropping, chests, relogging and are synced to the client automatically.
 */
public final class Abilities {
    private Abilities() {}

    /** What the bar should show for one weapon. */
    public enum Phase {
        /** Ability can be used right now. */
        READY,
        /** The buff / armed window is running (bar drains). */
        ACTIVE,
        /** Waiting to be usable again (bar refills). */
        COOLDOWN,
        /** Silver is out of the orb (nothing to count). */
        OUT
    }

    /** @param remainingTicks ACTIVE: ticks of buff left. COOLDOWN: ticks until ready. @param fill 0..1 bar fill. @param color bar colour (ARGB). */
    public record Status(Text title, Phase phase, long remainingTicks, float fill, int color, String activeWord) {}

    // ======================= TWEAK ME: bar colours per weapon (ARGB) =======================
    private static final int COLOR_GREATAXE = 0xFFFF7A1A; // lava orange
    private static final int COLOR_CUTLASS = 0xFF2FE3C8;  // sea-curse teal
    private static final int COLOR_MACE = 0xFF35C9E0;     // sculk cyan
    private static final int COLOR_LANCE = 0xFF9FDCFF;    // cloud sky blue
    private static final int COLOR_ORB = 0xFFBFEFFF;      // frost white-blue
    private static final int COLOR_BOW = 0xFF7FD8FF;      // ice blue
    // =======================================================================================

    /** Every item that has an ability (the bar scans the inventory for these). */
    public static boolean hasAbility(Item item) {
        return item instanceof MoltenGreataxeItem || item instanceof CutlassOfDoomItem || item instanceof SculkMaceItem
            || item instanceof CloudLanceItem || item instanceof SilversSpawnOrbItem || item instanceof FrostLongbowItem;
    }

    /** Starts the timers: "active" for activeTicks, then ready again totalTicks after NOW (total includes the active part). */
    public static void start(ItemStack stack, long now, int activeTicks, int totalTicks) {
        if (activeTicks > 0) {
            stack.set(ModComponents.ABILITY_UNTIL, now + activeTicks);
        }
        stack.set(ModComponents.COOLDOWN_UNTIL, now + totalTicks);
    }

    /** Is this weapon's ability still recharging? (Used by use() on both sides, so the client does not pretend it worked.) */
    public static boolean onCooldown(ItemStack stack, long now) {
        Long ready = stack.get(ModComponents.COOLDOWN_UNTIL);
        return ready != null && now < ready;
    }

    /** The bar's view of a weapon, or null if the bar should not show it (not an ability item, or an ability not unlocked yet). */
    public static Status status(ItemStack stack, long now) {
        Item item = stack.getItem();
        if (item instanceof MoltenGreataxeItem) {
            return timed(stack, now, MoltenGreataxeItem.ARM_DURATION_SECONDS * 20, MoltenGreataxeItem.COOLDOWN_SECONDS * 20, COLOR_GREATAXE, "ARMED");
        }
        if (item instanceof CutlassOfDoomItem) {
            return timed(stack, now, CutlassOfDoomItem.BUFF_SECONDS * 20, (CutlassOfDoomItem.BUFF_SECONDS + CutlassOfDoomItem.COOLDOWN_SECONDS) * 20, COLOR_CUTLASS, "DOOM SURGE");
        }
        if (item instanceof FrostLongbowItem) {
            return timed(stack, now, FrostLongbowItem.ARM_SECONDS * 20, FrostLongbowItem.COOLDOWN_SECONDS * 20, COLOR_BOW, "DEEP FREEZE");
        }
        if (item instanceof SculkMaceItem) {
            if (SculkMaceItem.getTier(stack) < SculkMaceItem.WAVE_UNLOCK_TIER) {
                return null; // Sculk Wave is not unlocked yet: nothing to show
            }
            return timed(stack, now, SculkMaceItem.WAVE_DURATION_SECONDS * 20, (SculkMaceItem.WAVE_DURATION_SECONDS + SculkMaceItem.WAVE_COOLDOWN_SECONDS) * 20, COLOR_MACE, "SCULK WAVE");
        }
        if (item instanceof CloudLanceItem) {
            long ready = CloudLanceItem.readyTimes(stack).get(0);
            long total = CloudLanceItem.DASH_RECHARGE_SECONDS * 20L;
            if (now < ready) {
                return new Status(stack.getName(), Phase.COOLDOWN, ready - now, 1.0F - clamp((ready - now) / (float) total), COLOR_LANCE, "");
            }
            return new Status(stack.getName(), Phase.READY, 0, 1.0F, COLOR_LANCE, "");
        }
        if (item instanceof SilversSpawnOrbItem) {
            OrbData d = SilversSpawnOrbItem.getData(stack).resolved(now);
            long total = SilversSpawnOrbItem.COOLDOWN_SECONDS * 20L;
            return switch (d.state()) {
                case COOLDOWN -> new Status(stack.getName(), Phase.COOLDOWN, d.cooldownEndsAt() - now, 1.0F - clamp((d.cooldownEndsAt() - now) / (float) total), COLOR_ORB, "");
                case RELEASED -> new Status(stack.getName(), Phase.OUT, 0, 1.0F, COLOR_ORB, "");
                case STORED -> new Status(stack.getName(), Phase.READY, 0, 1.0F, COLOR_ORB, "");
            };
        }
        return null;
    }

    /** The shared "active window, then cooldown" logic of the three sneak + right-click weapons. */
    private static Status timed(ItemStack stack, long now, int activeTicks, int totalTicks, int color, String activeWord) {
        Long until = stack.get(ModComponents.ABILITY_UNTIL);
        Long ready = stack.get(ModComponents.COOLDOWN_UNTIL);
        if (until != null && now < until) {
            return new Status(stack.getName(), Phase.ACTIVE, until - now, clamp((until - now) / (float) activeTicks), color, activeWord);
        }
        if (ready != null && now < ready) {
            // The refill part of the timer is (total - active) long. If the ability ended early (Lava Bomb landed), the bar simply stays empty until it catches up.
            float coolPart = Math.max(1, totalTicks - activeTicks);
            return new Status(stack.getName(), Phase.COOLDOWN, ready - now, 1.0F - clamp((ready - now) / coolPart), color, activeWord);
        }
        return new Status(stack.getName(), Phase.READY, 0, 1.0F, color, activeWord);
    }

    private static float clamp(float v) {
        return Math.max(0.0F, Math.min(1.0F, v));
    }

    /** All stacks the bar should look at, in the order given. (Helper for the client: the 36 inventory slots + offhand.) */
    public static List<ItemStack> candidates(net.minecraft.entity.player.PlayerEntity player) {
        java.util.ArrayList<ItemStack> out = new java.util.ArrayList<>();
        out.add(player.getMainHandStack());
        out.add(player.getOffHandStack());
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s != player.getMainHandStack()) {
                out.add(s);
            }
        }
        return out;
    }
}
