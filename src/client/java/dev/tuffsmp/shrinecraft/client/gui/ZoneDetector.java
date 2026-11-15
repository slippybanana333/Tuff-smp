package dev.tuffsmp.shrinecraft.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * Decides which GUI theme "/tuffsmp gui auto" shows, from where the player stands.
 *
 * The map is FIVE ZONES ARRANGED IN A CIRCLE (like slices of a pie) around one centre point. Everything is read from
 * config/shrinecraft/zones.json (created with example values the first time; edit it, then "/tuffsmp gui reload"):
 *
 *   "dimension"      which world the pie is in (normally minecraft:overworld)
 *   "center"         [x, z] of the middle of the circle
 *   "start_angle"    compass direction (degrees, 0 = north, 90 = east, 180 = south, 270 = west) where the FIRST zone begins
 *   "zones"          the themes in CLOCKWISE order, each with how many degrees wide it is (5 equal slices = 72 each)
 *   "inner_radius"   blocks from the centre that count as the hub (theme "hub_theme", "off" = vanilla look)
 *   "outer_radius"   beyond this many blocks from the centre: "outside_theme"
 *   "other_dimensions"  theme per other dimension (the Nether -> lava, the End -> end by default)
 *   "islands"        OPTIONAL list of islands {name, theme, x, z, radius}. The nearest island (by distance to its EDGE) within
 *                    "island_margin" blocks decides the theme - so every island keeps its own biome look even where it crosses a
 *                    pie border; everything not on an island falls back to the pie slice. The hub (inner_radius) still wins.
 *                    Fill it with "/tuffsmp gui mark <theme>" while standing on an island, or with tools/zones_from_map.py.
 *   "island_margin"  how many blocks of water around an island still count as that island (default 40)
 *   "map"            OPTIONAL zone PICTURE that replaces the pie + islands: {"image": "zones_mask.png", "center_pixel": [x, y], "blocks_per_pixel": S}.
 *                    Every pixel of the picture is one zone (theme colours below, transparent = no GUI); "center" is the world x,z of
 *                    "center_pixel". Made from the owner's hand-drawn borders by tools/zones_from_drawing.py. The hub still wins; outside the
 *                    picture the "outside_theme" applies.
 *
 * "/tuffsmp gui where" prints your x / z, distance and compass angle from the centre and the zone you are in - walk to
 * each border and read the numbers off to fill the file in.
 */
public final class ZoneDetector {
    private ZoneDetector() {}

    private record Slice(GuiTheme theme, double degrees) {}

    private static String dimension = "minecraft:overworld";
    private static double centerX = 0, centerZ = 0;
    private static double startAngle = 0;
    private static double innerRadius = 0, outerRadius = 1.0E9;
    private static GuiTheme hubTheme = null, outsideTheme = null;
    private static List<Slice> slices = new ArrayList<>();
    private record Island(String name, GuiTheme theme, double x, double z, double radius) {}
    private static List<Island> islands = new ArrayList<>();
    // the optional zone picture (see "map" above): ARGB pixels, size, which pixel is the centre, how many blocks one pixel is
    private static int[] mapPixels = null;
    private static int mapW = 0, mapH = 0;
    private static double mapCx = 0, mapCy = 0, mapBlocksPerPixel = 1;
    private static double islandMargin = 40;
    private static final Map<String, GuiTheme> OTHER = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("shrinecraft").resolve("zones.json");
    }

    /** null for "off" / unknown names (= vanilla look). */
    private static GuiTheme theme(String key) {
        return key == null ? null : GuiTheme.byKey(key);
    }

    /** Reads zones.json (writes the example file first if there is none). Returns a one-line summary for the chat. */
    public static String load() {
        Path f = file();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                    GSON.toJson(example(), w);
                }
            }
            JsonObject o;
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                o = JsonParser.parseReader(r).getAsJsonObject();
            }
            dimension = o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld";
            JsonArray c = o.getAsJsonArray("center");
            centerX = c.get(0).getAsDouble();
            centerZ = c.get(1).getAsDouble();
            startAngle = o.has("start_angle") ? o.get("start_angle").getAsDouble() : 0;
            innerRadius = o.has("inner_radius") ? o.get("inner_radius").getAsDouble() : 0;
            outerRadius = o.has("outer_radius") ? o.get("outer_radius").getAsDouble() : 1.0E9;
            hubTheme = theme(o.has("hub_theme") ? o.get("hub_theme").getAsString() : "off");
            outsideTheme = theme(o.has("outside_theme") ? o.get("outside_theme").getAsString() : "off");
            List<Slice> list = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("zones")) {
                JsonObject z = e.getAsJsonObject();
                list.add(new Slice(theme(z.get("theme").getAsString()), z.has("degrees") ? z.get("degrees").getAsDouble() : 0));
            }
            // slices without a width share whatever is left of the 360 degrees equally
            double given = list.stream().mapToDouble(Slice::degrees).sum();
            long open = list.stream().filter(s -> s.degrees() <= 0).count();
            double share = open > 0 ? Math.max(0, 360 - given) / open : 0;
            List<Slice> done = new ArrayList<>();
            for (Slice s : list) {
                done.add(s.degrees() > 0 ? s : new Slice(s.theme(), share));
            }
            slices = done;
            islandMargin = o.has("island_margin") ? o.get("island_margin").getAsDouble() : 40;
            List<Island> isl = new ArrayList<>();
            if (o.has("islands")) {
                for (JsonElement e : o.getAsJsonArray("islands")) {
                    JsonObject i = e.getAsJsonObject();
                    GuiTheme t = theme(i.get("theme").getAsString());
                    if (t != null) {
                        isl.add(new Island(i.has("name") ? i.get("name").getAsString() : t.key, t, i.get("x").getAsDouble(), i.get("z").getAsDouble(),
                            i.has("radius") ? i.get("radius").getAsDouble() : 150));
                    }
                }
            }
            islands = isl;
            loadMap(o, f);
            OTHER.clear();
            if (o.has("other_dimensions")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("other_dimensions").entrySet()) {
                    OTHER.put(e.getKey(), theme(e.getValue().getAsString()));
                }
            }
            return "zones loaded: " + (mapPixels != null ? "zone picture " + mapW + "x" + mapH : slices.size() + " slices + " + islands.size() + " islands")
                + " around " + (int) centerX + ", " + (int) centerZ;
        } catch (Exception e) {
            ShrineCraft.LOGGER.warn("Could not read {}: {}", f, e.toString());
            return "zones.json has a mistake: " + e.getMessage();
        }
    }

    /** Reads the optional zone picture named in "map" (null/missing = pie + islands). */
    private static void loadMap(JsonObject o, Path zonesFile) {
        mapPixels = null;
        if (!o.has("map")) {
            return;
        }
        try {
            JsonObject m = o.getAsJsonObject("map");
            Path img = zonesFile.resolveSibling(m.get("image").getAsString());
            try (java.io.InputStream in = Files.newInputStream(img); net.minecraft.client.texture.NativeImage image = net.minecraft.client.texture.NativeImage.read(in)) {
                int w = image.getWidth(), h = image.getHeight();
                int[] px = new int[w * h];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        px[y * w + x] = image.getColorArgb(x, y);
                    }
                }
                JsonArray c = m.getAsJsonArray("center_pixel");
                mapCx = c.get(0).getAsDouble();
                mapCy = c.get(1).getAsDouble();
                mapBlocksPerPixel = m.has("blocks_per_pixel") ? m.get("blocks_per_pixel").getAsDouble() : 1;
                mapW = w;
                mapH = h;
                mapPixels = px;
            }
        } catch (Exception e) {
            ShrineCraft.LOGGER.warn("Could not read the zone picture from zones.json: {}", e.toString());
        }
    }

    /** The colour a theme has in the zone picture (RGB). */
    public static int[] pictureColor(GuiTheme t) {
        return switch (t) {
            case SCULK -> new int[] {30, 140, 140};
            case OCEAN -> new int[] {46, 125, 181};
            case ARCTIC -> new int[] {207, 227, 242};
            case LAVA -> new int[] {224, 112, 26};
            case END -> new int[] {201, 187, 122};
        };
    }

    /** Theme of one picture pixel: the nearest theme colour, or null for transparent / unknown colours. */
    private static GuiTheme pictureTheme(int argb) {
        if ((argb >>> 24) < 128) {
            return null;
        }
        int r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        GuiTheme best = null;
        int bestD = 40 * 40;
        for (GuiTheme t : GuiTheme.values()) {
            int[] c = pictureColor(t);
            int d = (r - c[0]) * (r - c[0]) + (g - c[1]) * (g - c[1]) + (b - c[2]) * (b - c[2]);
            if (d < bestD) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }

    private static JsonObject example() {
        JsonObject o = new JsonObject();
        o.addProperty("_help", "Five zones round one centre, like a pie. Angles are compass degrees: 0 = north, 90 = east, 180 = south, 270 = west. "
            + "'zones' go CLOCKWISE starting at start_angle. Use /tuffsmp gui where in the game to read your x, z and angle. Then /tuffsmp gui reload.");
        o.addProperty("dimension", "minecraft:overworld");
        JsonArray c = new JsonArray();
        c.add(0);
        c.add(0);
        o.add("center", c);
        // measured from the owner's map: five equal 72 degree slices, the first (sculk, centred on north) starts at 324 degrees
        o.addProperty("start_angle", 324);
        o.addProperty("inner_radius", 0);        // the hub in the middle shows NO theme (vanilla look): put its radius in blocks here
        o.addProperty("hub_theme", "off");
        o.addProperty("outer_radius", 100000);
        o.addProperty("outside_theme", "off");
        o.addProperty("island_margin", 40);
        o.add("islands", new JsonArray());
        JsonArray zones = new JsonArray();
        for (String t : new String[] {"sculk", "ocean", "arctic", "lava", "end"}) {
            JsonObject z = new JsonObject();
            z.addProperty("theme", t);
            z.addProperty("degrees", 72);
            zones.add(z);
        }
        o.add("zones", zones);
        JsonObject other = new JsonObject();
        other.addProperty("minecraft:the_nether", "lava");
        other.addProperty("minecraft:the_end", "end");
        o.add("other_dimensions", other);
        return o;
    }

    /** Compass angle (0 = north, clockwise) of a position seen from the centre. North is -z in Minecraft, east is +x. */
    public static double angleOf(double x, double z) {
        double a = Math.toDegrees(Math.atan2(x - centerX, -(z - centerZ)));
        return a < 0 ? a + 360 : a;
    }

    public static double distanceOf(double x, double z) {
        return Math.hypot(x - centerX, z - centerZ);
    }

    /** The theme at a spot in a dimension, or null = vanilla look. Cheap: called while drawing. */
    public static GuiTheme themeAt(String dim, double x, double z) {
        if (!dim.equals(dimension)) {
            return OTHER.get(dim);
        }
        double dist = distanceOf(x, z);
        if (dist < innerRadius) {
            return hubTheme;
        }
        if (dist > outerRadius) {
            return outsideTheme;
        }
        if (mapPixels != null) {
            // the zone picture is the authority: one pixel = one zone (outside the picture = outside_theme)
            int px = (int) Math.floor(mapCx + (x - centerX) / mapBlocksPerPixel), py = (int) Math.floor(mapCy + (z - centerZ) / mapBlocksPerPixel);
            if (px < 0 || py < 0 || px >= mapW || py >= mapH) {
                return outsideTheme;
            }
            return pictureTheme(mapPixels[py * mapW + px]);
        }
        // islands: the nearest one (distance to its edge) within the margin decides; otherwise the pie slice below
        Island best = null;
        double bestEdge = islandMargin;
        for (Island i : islands) {
            double edgeDist = Math.hypot(x - i.x(), z - i.z()) - i.radius();
            if (edgeDist <= bestEdge) {
                best = i;
                bestEdge = edgeDist;
            }
        }
        if (best != null) {
            return best.theme();
        }
        double a = (angleOf(x, z) - startAngle) % 360;
        if (a < 0) {
            a += 360;
        }
        double edge = 0;
        for (Slice s : slices) {
            edge += s.degrees();
            if (a < edge) {
                return s.theme();
            }
        }
        return slices.isEmpty() ? null : slices.get(slices.size() - 1).theme();
    }


    // ------------------------------------------------------------------------------------------------------------------ island tools

    /** Adds an island at a spot to zones.json (the "/tuffsmp gui mark" command) and reloads. Returns a chat line. */
    public static String markIsland(GuiTheme theme, double x, double z, double radius, String name) {
        try {
            load();                                          // makes sure the file exists
            JsonObject o = readJson();
            JsonArray arr = o.has("islands") ? o.getAsJsonArray("islands") : new JsonArray();
            JsonObject i = new JsonObject();
            i.addProperty("name", name == null || name.isBlank() ? theme.key + "-" + (arr.size() + 1) : name);
            i.addProperty("theme", theme.key);
            i.addProperty("x", Math.round(x));
            i.addProperty("z", Math.round(z));
            i.addProperty("radius", Math.round(radius));
            arr.add(i);
            o.add("islands", arr);
            writeJson(o);
            load();
            return "marked island '" + i.get("name").getAsString() + "' (" + theme.key + ") at " + Math.round(x) + ", " + Math.round(z) + " radius " + Math.round(radius)
                + " - " + islands.size() + " islands now. /tuffsmp gui map draws them.";
        } catch (Exception e) {
            return "could not save zones.json: " + e.getMessage();
        }
    }

    /** Removes the island nearest to a spot (the "/tuffsmp gui unmark" command). */
    public static String unmarkNearest(double x, double z) {
        try {
            load();
            JsonObject o = readJson();
            if (!o.has("islands") || o.getAsJsonArray("islands").isEmpty()) {
                return "there are no islands to remove";
            }
            JsonArray arr = o.getAsJsonArray("islands");
            int bestIdx = -1;
            double bestD = Double.MAX_VALUE;
            for (int k = 0; k < arr.size(); k++) {
                JsonObject i = arr.get(k).getAsJsonObject();
                double d = Math.hypot(x - i.get("x").getAsDouble(), z - i.get("z").getAsDouble()) - (i.has("radius") ? i.get("radius").getAsDouble() : 0);
                if (d < bestD) {
                    bestD = d;
                    bestIdx = k;
                }
            }
            String name = arr.get(bestIdx).getAsJsonObject().get("name").getAsString();
            arr.remove(bestIdx);
            writeJson(o);
            load();
            return "removed island '" + name + "' - " + islands.size() + " islands left";
        } catch (Exception e) {
            return "could not save zones.json: " + e.getMessage();
        }
    }

    /** One line per island, for "/tuffsmp gui islands". */
    public static List<String> listIslands() {
        List<String> out = new ArrayList<>();
        for (Island i : islands) {
            out.add(i.name() + "  " + i.theme().key + "  x " + Math.round(i.x()) + " z " + Math.round(i.z()) + "  radius " + Math.round(i.radius()));
        }
        return out;
    }

    private static JsonObject readJson() throws java.io.IOException {
        try (Reader r = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static void writeJson(JsonObject o) throws java.io.IOException {
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            GSON.toJson(o, w);
        }
    }

    /** Colour of a theme on the drawn map (ARGB). */
    private static int mapColor(GuiTheme t) {
        if (t == null) {
            return 0xFF2A2A2E;
        }
        return switch (t) {
            case OCEAN -> 0xFF2E7DB5;
            case LAVA -> 0xFFE0701A;
            case ARCTIC -> 0xFFCFE3F2;
            case SCULK -> 0xFF1E8C8C;
            case END -> 0xFFC9BB7A;
        };
    }

    private static int shade(int argb, double f) {
        int r = (int) Math.min(255, ((argb >> 16) & 255) * f), g = (int) Math.min(255, ((argb >> 8) & 255) * f), b = (int) Math.min(255, (argb & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Draws what the game will do, using the SAME themeAt() the GUI uses (so the picture can never disagree with the game):
     * pie slices dark, islands bright with a white outline, hub dark, the centre as a red dot and YOU as a white dot. North is up, east is right.
     * Written to config/shrinecraft/zones_map.png; returns the path. halfBlocks <= 0 = automatic extent.
     */
    public static Path renderMap(int size, double halfBlocks, double playerX, double playerZ) throws java.io.IOException {
        // extent: what the caller asked for, else the outer radius (when it is a sensible size), else wide enough for every island
        double half = halfBlocks;
        if (half <= 0) {
            half = outerRadius <= 20000 ? outerRadius * 1.05 : 0;
            for (Island i : islands) {
                half = Math.max(half, Math.max(Math.abs(i.x() - centerX), Math.abs(i.z() - centerZ)) + i.radius());
            }
            half = Math.max(half * 1.1, islands.isEmpty() && outerRadius > 20000 ? 1500 : 500);
        }
        double per = half * 2 / size;                      // blocks per pixel
        String dim = dimension;
        net.minecraft.client.texture.NativeImage img = new net.minecraft.client.texture.NativeImage(size, size, false);
        try {
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    double wx = centerX - half + (px + 0.5) * per, wz = centerZ - half + (py + 0.5) * per;
                    GuiTheme t = themeAt(dim, wx, wz);
                    boolean onIsland = false;
                    for (Island i : islands) {
                        if (Math.hypot(wx - i.x(), wz - i.z()) <= i.radius()) {
                            onIsland = true;
                            break;
                        }
                    }
                    int c = mapColor(t);
                    img.setColorArgb(px, py, onIsland ? c : shade(c, 0.45));
                }
            }
            // island outlines
            for (Island i : islands) {
                double r = i.radius() / per;
                int cx = (int) ((i.x() - (centerX - half)) / per), cy = (int) ((i.z() - (centerZ - half)) / per);
                for (int k = 0; k < 360; k++) {
                    int ox = cx + (int) Math.round(Math.cos(Math.toRadians(k)) * r), oy = cy + (int) Math.round(Math.sin(Math.toRadians(k)) * r);
                    if (ox >= 0 && oy >= 0 && ox < size && oy < size) {
                        img.setColorArgb(ox, oy, 0xFFFFFFFF);
                    }
                }
            }
            dot(img, size, (centerX - (centerX - half)) / per, (centerZ - (centerZ - half)) / per, 4, 0xFFFF2020);
            dot(img, size, (playerX - (centerX - half)) / per, (playerZ - (centerZ - half)) / per, 3, 0xFFFFFFFF);
            Path out = file().resolveSibling("zones_map.png");
            img.writeTo(out);
            return out;
        } finally {
            img.close();
        }
    }

    private static void dot(net.minecraft.client.texture.NativeImage img, int size, double cx, double cy, int r, int argb) {
        for (int dy = -r; dy <= r; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                int x = (int) cx + dx, y = (int) cy + dy;
                if (dx * dx + dy * dy <= r * r && x >= 0 && y >= 0 && x < size && y < size) {
                    img.setColorArgb(x, y, argb);
                }
            }
        }
    }

    public static GuiTheme detectZone(ClientPlayerEntity player) {
        return themeAt(player.getEntityWorld().getRegistryKey().getValue().toString(), player.getX(), player.getZ());
    }

    /** The text "/tuffsmp gui where" prints. */
    public static String describe(ClientPlayerEntity player) {
        String dim = player.getEntityWorld().getRegistryKey().getValue().toString();
        GuiTheme t = themeAt(dim, player.getX(), player.getZ());
        return String.format("x %.0f, z %.0f  |  %.0f blocks from the centre (%.0f, %.0f)  |  compass angle %.1f  |  zone: %s",
            player.getX(), player.getZ(), distanceOf(player.getX(), player.getZ()), centerX, centerZ, angleOf(player.getX(), player.getZ()),
            t == null ? "none (vanilla)" : t.key);
    }
}
