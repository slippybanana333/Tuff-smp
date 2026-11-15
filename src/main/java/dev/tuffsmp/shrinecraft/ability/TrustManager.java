package dev.tuffsmp.shrinecraft.ability;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.registry.ModKeys;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;

/**
 * "/tuffsmp trust <player>": friendly fire OFF for YOUR weapon abilities. Players you trust are never hurt or affected by the
 * extra effects of your weapons (Lava Bomb, Doom Surge bonus damage, Sculk Wave, Sculk Cannon blind + wail, Frost Longbow freeze + true damage).
 * One-way: if A trusts B, A's abilities spare B, but B's abilities still hit A until B trusts A back.
 * Plain vanilla damage (a normal sword hit, a normal arrow) is NOT changed - this is only about the ability extras.
 * Saved in config/shrinecraft/trust.json  (owner uuid -> list of trusted uuids), so it survives restarts.
 */
public final class TrustManager {
    private TrustManager() {}

    private static final Map<UUID, Set<UUID>> TRUST = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("shrinecraft").resolve("trust.json");
    }

    public static void register() {
        load();
        // The ability damage types never hurt a trusted player at all.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (source.isOf(ModKeys.SCULK_WAVE) || source.isOf(ModKeys.SCULK_WAIL) || source.isOf(ModKeys.FROST_ARROW)) {
                return !trusts(source.getAttacker(), entity);
            }
            return true;
        });
    }

    /** Does the owner (an attacker entity) trust the target? Only players can be trusted. */
    public static boolean trusts(Entity owner, Entity target) {
        if (owner == null || target == null) {
            return false;
        }
        Set<UUID> set = TRUST.get(owner.getUuid());
        return set != null && set.contains(target.getUuid());
    }

    public static boolean trusts(DamageSource source, Entity target) {
        return trusts(source.getAttacker(), target);
    }

    /** Flips the trust and saves. Returns true if the target is trusted afterwards. */
    public static boolean toggle(UUID owner, UUID target) {
        Set<UUID> set = TRUST.computeIfAbsent(owner, k -> new HashSet<>());
        boolean nowTrusted = set.add(target);
        if (!nowTrusted) {
            set.remove(target);
        }
        save();
        return nowTrusted;
    }

    public static Set<UUID> trustedBy(UUID owner) {
        return TRUST.getOrDefault(owner, Set.of());
    }

    private static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            for (var e : o.entrySet()) {
                Set<UUID> set = new HashSet<>();
                e.getValue().getAsJsonArray().forEach(x -> set.add(UUID.fromString(x.getAsString())));
                TRUST.put(UUID.fromString(e.getKey()), set);
            }
        } catch (Exception e) {
            ShrineCraft.LOGGER.warn("Could not read {}: {}", f, e.toString());
        }
    }

    private static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            JsonObject o = new JsonObject();
            TRUST.forEach((owner, set) -> {
                if (!set.isEmpty()) {
                    JsonArray a = new JsonArray();
                    set.forEach(u -> a.add(u.toString()));
                    o.add(owner.toString(), a);
                }
            });
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(o, w);
            }
        } catch (Exception e) {
            ShrineCraft.LOGGER.warn("Could not save the trust list: {}", e.toString());
        }
    }

    /** Test helper: forget everything (not saved). */
    public static void clearForTest() {
        TRUST.clear();
    }
}
