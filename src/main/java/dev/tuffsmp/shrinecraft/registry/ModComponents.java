package dev.tuffsmp.shrinecraft.registry;

import com.mojang.serialization.Codec;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.item.OrbData;
import java.util.List;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

/**
 * Custom item data components. These are saved on the item itself, survive restarts, and are
 * automatically synced to the client (the Cloud Lance HUD and tooltips rely on that).
 */
public final class ModComponents {
    private ModComponents() {}

    /** Game time (ticks) until which a sneak+right-click ability is "on" (Lava Bomb armed, Cutlass buff, Sculk Wave). */
    public static final ComponentType<Long> ABILITY_UNTIL = register("ability_until", Codec.LONG);
    /** Game time (ticks) at which a sneak+right-click ability is ready to use again (Lava Bomb, Doom Surge, Sculk Wave). Shown by the ability bar. */
    public static final ComponentType<Long> COOLDOWN_UNTIL = register("cooldown_until", Codec.LONG);
    /** Sculk Mace tier 0-5. */
    public static final ComponentType<Integer> SCULK_TIER = register("sculk_tier", Codec.intRange(0, 5));
    /** Cloud Lance: for each of the 3 dash charges, the game time at which it is ready again (0 = ready now). */
    public static final ComponentType<List<Long>> DASH_READY_AT = register("dash_ready_at", Codec.LONG.listOf());
    /** Sculk Cannon firing mode: 1 = Blinding Volley, 2 = Piercing Wail. */
    public static final ComponentType<Integer> CANNON_MODE = register("cannon_mode", Codec.intRange(1, 2));
    /** Harbinger of Death: true = Silk Touch is active, false = Fortune 3 is active. */
    public static final ComponentType<Boolean> HARBINGER_SILK = register("harbinger_silk", Codec.BOOL);
    /** Harbinger of Death: true (default) = mines a 3x3x3 area, false = mines one block at a time. */
    public static final ComponentType<Boolean> HARBINGER_WIDE = register("harbinger_wide", Codec.BOOL);
    /** Silver's Spawn Orb state (stored / released / cooldown + which horse). */
    public static final ComponentType<OrbData> ORB_DATA = register("orb_data", OrbData.CODEC);

    private static <T> ComponentType<T> register(String name, Codec<T> codec) {
        return Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            ShrineCraft.id(name),
            ComponentType.<T>builder().codec(codec).build()
        );
    }

    public static void register() {
        // Touching the class runs the static initialisers above, which registers everything.
    }
}
