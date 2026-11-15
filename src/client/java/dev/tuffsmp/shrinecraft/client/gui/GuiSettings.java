package dev.tuffsmp.shrinecraft.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/** The player's "/gui" choice, saved in config/shrinecraft_gui.json so it survives restarts. Default = OFF (vanilla). */
public final class GuiSettings {
    private GuiSettings() {}

    public enum Mode { OFF, DYNAMIC, LOCKED }

    private static Mode mode = Mode.OFF;
    private static GuiTheme lockedTheme = GuiTheme.OCEAN;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("shrinecraft_gui.json");
    }

    public static Mode mode() {
        return mode;
    }

    public static GuiTheme lockedTheme() {
        return lockedTheme;
    }

    public static void setOff() {
        mode = Mode.OFF;
        save();
    }

    public static void setDynamic() {
        mode = Mode.DYNAMIC;
        save();
    }

    public static void setLocked(GuiTheme theme) {
        mode = Mode.LOCKED;
        lockedTheme = theme;
        save();
    }

    /** The theme to draw right now, or null for the vanilla background. This is called while drawing, so it must stay cheap. */
    public static GuiTheme currentTheme() {
        switch (mode) {
            case LOCKED:
                return lockedTheme;
            case DYNAMIC:
                ClientPlayerEntity player = MinecraftClient.getInstance().player;
                return player == null ? null : ZoneDetector.detectZone(player);
            default:
                return null;
        }
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) {
            return;
        }
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            String m = o.has("mode") ? o.get("mode").getAsString() : "off";
            GuiTheme t = o.has("theme") ? GuiTheme.byKey(o.get("theme").getAsString()) : null;
            if (t != null) {
                lockedTheme = t;
            }
            mode = switch (m) {
                case "dynamic" -> Mode.DYNAMIC;
                case "locked" -> Mode.LOCKED;
                default -> Mode.OFF;
            };
        } catch (Exception e) {
            ShrineCraft.LOGGER.warn("Could not read {}: {}", f, e.toString());
        }
    }

    private static void save() {
        try (Writer w = Files.newBufferedWriter(file(), StandardCharsets.UTF_8)) {
            JsonObject o = new JsonObject();
            o.addProperty("mode", mode.name().toLowerCase());
            o.addProperty("theme", lockedTheme.key);
            GSON.toJson(o, w);
        } catch (Exception e) {
            ShrineCraft.LOGGER.warn("Could not save the /gui setting: {}", e.toString());
        }
    }
}
