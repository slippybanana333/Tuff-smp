package dev.tuffsmp.shrinecraft.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.joml.Quaternionf;

/**
 * Where each WORN Tuff SMP piece (the cape and the four armor pieces) sits on the player, edited with the Display Editor and stored in
 * assets/shrinecraft/worn_display.json: per piece a pivot, translation, rotation and scale in the player's MODEL space
 * (1/16-block units; x = the player's left, y = DOWN, z = BACK). The piece is moved / rotated / scaled around its pivot:
 *     W = translate(pivot + t) * rotationXYZ(r) * scale(s) * translate(-pivot)
 * The mixins ElytraFeatureRendererMixin (cape) and ArmorFeatureRendererMixin (armor layers) push W before the piece is drawn, and
 * ArmorExtrasFeatureRenderer applies it to the extra geometry. A piece with no entry, or an identity entry, is drawn exactly as vanilla does.
 * The file is re-read every few seconds, so editing it inside a resource pack shows up without restarting.
 */
public final class WornDisplay {
    private WornDisplay() {}

    private record Entry(float[] pivot, float[] t, float[] r, float[] s, boolean identity) {}

    /** Per-part edits of one armor piece (horns, shoulders, skirt ...): item path -> group -> {tx, ty, tz, sx, sy, sz} (model pixels / factors). */
    private static volatile Map<String, Map<String, float[]>> groups = new HashMap<>();

    private static final Identifier FILE = Identifier.of(ShrineCraft.MOD_ID, "worn_display.json");
    private static final long RELOAD_MS = 3000L;
    private static volatile Map<String, Entry> entries = new HashMap<>();
    private static long lastLoad = 0L;

    private static void refresh() {
        long now = System.currentTimeMillis();
        if (now - lastLoad < RELOAD_MS && lastLoad != 0L) {
            return;
        }
        lastLoad = now;
        Map<String, Entry> fresh = new HashMap<>();
        Map<String, Map<String, float[]>> freshGroups = new HashMap<>();
        try {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(FILE);
            if (res.isPresent()) {
                try (Reader reader = res.get().getReader()) {
                    JsonObject pieces = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("pieces");
                    for (Map.Entry<String, JsonElement> e : pieces.entrySet()) {
                        JsonObject o = e.getValue().getAsJsonObject();
                        float[] pivot = arr(o, "pivot", 0), t = arr(o, "translation", 0), r = arr(o, "rotation", 0), s = arr(o, "scale", 1);
                        boolean identity = isZero(t) && isZero(r) && s[0] == 1 && s[1] == 1 && s[2] == 1;
                        fresh.put(e.getKey(), new Entry(pivot, t, r, s, identity));
                        if (o.has("groups")) {
                            Map<String, float[]> gm = new HashMap<>();
                            for (Map.Entry<String, JsonElement> ge : o.getAsJsonObject("groups").entrySet()) {
                                JsonObject go = ge.getValue().getAsJsonObject();
                                float[] gt = arr(go, "translation", 0), gs = arr(go, "scale", 1);
                                if (!isZero(gt) || gs[0] != 1 || gs[1] != 1 || gs[2] != 1) {
                                    gm.put(ge.getKey(), new float[] {gt[0], gt[1], gt[2], gs[0], gs[1], gs[2]});
                                }
                            }
                            if (!gm.isEmpty()) {
                                freshGroups.put(e.getKey(), gm);
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ShrineCraft.LOGGER.warn("Could not read worn_display.json - worn pieces are drawn the vanilla way: {}", ex.toString());
        }
        entries = fresh;
        groups = freshGroups;
    }

    private static float[] arr(JsonObject o, String key, float def) {
        float[] out = {def, def, def};
        if (o.has(key)) {
            for (int i = 0; i < 3; i++) {
                out[i] = o.getAsJsonArray(key).get(i).getAsFloat();
            }
        }
        return out;
    }

    private static boolean isZero(float[] a) {
        return a[0] == 0 && a[1] == 0 && a[2] == 0;
    }

    private static Entry entryFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (!ShrineCraft.MOD_ID.equals(id.getNamespace())) {
            return null;
        }
        refresh();
        Entry e = entries.get(id.getPath());
        return e == null || e.identity() ? null : e;
    }

    /** The edit of one part (group) of a worn piece as {tx, ty, tz, sx, sy, sz}, or null when it is not edited. */
    public static float[] group(net.minecraft.item.Item item, String group) {
        Identifier id = Registries.ITEM.getId(item);
        refresh();
        Map<String, float[]> m = groups.get(id.getPath());
        return m == null ? null : m.get(group);
    }

    /** True when this worn stack has a custom placement (so the caller must push before and pop after drawing). */
    public static boolean has(ItemStack stack) {
        return entryFor(stack) != null;
    }

    /** Applies the placement to the matrix stack (call only when {@link #has} is true, after pushing). */
    public static void apply(MatrixStack matrices, ItemStack stack) {
        Entry e = entryFor(stack);
        if (e == null) {
            return;
        }
        float k = 1.0F / 16.0F;
        matrices.translate((e.pivot()[0] + e.t()[0]) * k, (e.pivot()[1] + e.t()[1]) * k, (e.pivot()[2] + e.t()[2]) * k);
        matrices.multiply(new Quaternionf().rotationXYZ((float) Math.toRadians(e.r()[0]), (float) Math.toRadians(e.r()[1]), (float) Math.toRadians(e.r()[2])));
        matrices.scale(e.s()[0], e.s()[1], e.s()[2]);
        matrices.translate(-e.pivot()[0] * k, -e.pivot()[1] * k, -e.pivot()[2] * k);
    }
}
