package dev.tuffsmp.shrinecraft.client.gui;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import java.util.Locale;
import net.minecraft.util.Identifier;

/**
 * The five GUI themes (the owner's artwork). Each has an inventory panel (<key>.png) and a chest panel (<key>_chest.png) in
 * assets/shrinecraft/textures/gui/backgrounds/, copied from tools/reference/gui/<theme>/ by tools/paint_textures.py.
 *
 * =========================  ZONE -> TEXTURE MAPPING  (edit me)  =========================
 *   zone "ocean"  -> ocean.png     zone "lava"   -> lava.png     zone "sculk" -> sculk.png
 *   zone "arctic" -> arctic.png    zone "end"    -> end.png      (no zone)    -> vanilla background
 * ========================================================================================
 */
public enum GuiTheme {
    // key, text colour, outline colour (screen titles are light text with a dark outline so they read on any part of the artwork)
    OCEAN("ocean", 0xFFE4F8FF, 0xFF04182A),
    LAVA("lava", 0xFFFFE0A8, 0xFF2A0A04),
    SCULK("sculk", 0xFFC8FFF8, 0xFF031014),
    ARCTIC("arctic", 0xFFFFFFFF, 0xFF1C3E66),
    END("end", 0xFFFFF0B0, 0xFF14100A);

    public final String key;
    /** The survival inventory panel (176x166 in a 256x256 sheet, vanilla layout). */
    public final Identifier texture;
    /** The chest / barrel / ender chest panel (vanilla generic_54 layout: 17px header, rows*18, then the 96px player part from v=126). */
    public final Identifier chestTexture;

    /** Colour of the screen titles ("Chest", "Inventory", "Crafting") on this theme, and of the outline drawn around them. */
    public final int textColor;
    public final int outlineColor;

    GuiTheme(String key, int textColor, int outlineColor) {
        this.key = key;
        this.textColor = textColor;
        this.outlineColor = outlineColor;
        this.texture = Identifier.of(ShrineCraft.MOD_ID, "textures/gui/backgrounds/" + key + ".png");
        this.chestTexture = Identifier.of(ShrineCraft.MOD_ID, "textures/gui/backgrounds/" + key + "_chest.png");
    }

    /** Draws a label light-on-dark-outline so it stays readable on the ornate artwork. */
    public void drawLabel(net.minecraft.client.gui.DrawContext ctx, net.minecraft.client.font.TextRenderer font, net.minecraft.text.Text text, int x, int y) {
        for (int[] d : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, 1}, {-1, 1}, {1, -1}}) {
            ctx.drawText(font, text, x + d[0], y + d[1], outlineColor, false);
        }
        ctx.drawText(font, text, x, y, textColor, false);
    }

    /** The other container screens that have a simpler themed copy (textures/gui/themed/<theme>/<name>.png, made by tools/build_themed_screens.py). */
    public static final java.util.Set<String> THEMED_SCREENS = java.util.Set.of(
        "anvil", "beacon", "blast_furnace", "brewing_stand", "cartography_table", "crafter", "crafting_table", "dispenser", "enchanting_table",
        "furnace", "grindstone", "hopper", "horse", "loom", "nautilus", "shulker_box", "smithing", "smoker", "stonecutter", "villager");

    /** Vanilla plain textures that have a themed twin (tools/build_themed_widgets.py MISC): menu backgrounds, separators, advancement tiles. */
    private static final java.util.Set<String> MISC_TEXTURES = java.util.Set.of(
        "menu_background", "header_separator", "footer_separator", "inworld_menu_background", "inworld_header_separator", "inworld_footer_separator",
        "menu_list_background", "inworld_menu_list_background", "tab_header_background",
        "advancements/backgrounds/adventure", "advancements/backgrounds/stone", "advancements/backgrounds/nether", "advancements/backgrounds/end",
        "advancements/backgrounds/husbandry");

    /** Screens whose vanilla texture is NOT in textures/gui/container/ directly: vanilla path -> name in textures/gui/themed/<theme>/. */
    private static final java.util.Map<String, String> SPECIAL_SCREENS = java.util.Map.of(
        "textures/gui/advancements/window.png", "advancements_window",
        "textures/gui/container/creative_inventory/tab_items.png", "creative_tab_items",
        "textures/gui/container/creative_inventory/tab_inventory.png", "creative_tab_inventory",
        "textures/gui/container/creative_inventory/tab_item_search.png", "creative_tab_item_search");

    /** Every vanilla GUI sprite that has a themed twin (assets/shrinecraft/themed_sprites.json, written by tools/build_themed_widgets.py). */
    private static final java.util.Set<String> THEMED_SPRITES = loadSprites();

    private static java.util.Set<String> loadSprites() {
        try (java.io.InputStream in = GuiTheme.class.getResourceAsStream("/assets/" + ShrineCraft.MOD_ID + "/themed_sprites.json")) {
            if (in == null) {
                return java.util.Set.of();
            }
            com.google.gson.JsonArray arr = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            java.util.Set<String> out = new java.util.HashSet<>();
            arr.forEach(e -> out.add(e.getAsString()));
            return out;
        } catch (Exception e) {
            ShrineCraft.LOGGER.warn("Could not read themed_sprites.json: {}", e.toString());
            return java.util.Set.of();
        }
    }

    /** For a vanilla GUI sprite id (minecraft:widget/button) returns this theme's twin, or the same id if there is none. */
    public Identifier themedSprite(Identifier vanilla) {
        if ("minecraft".equals(vanilla.getNamespace()) && THEMED_SPRITES.contains(vanilla.getPath())) {
            return Identifier.of(ShrineCraft.MOD_ID, "themed/" + key + "/" + vanilla.getPath());
        }
        return vanilla;
    }

    public Identifier themedScreen(String name) {
        return Identifier.of(ShrineCraft.MOD_ID, "textures/gui/themed/" + key + "/" + name + ".png");
    }

    /** For a vanilla container texture id (minecraft:textures/gui/container/furnace.png) returns this theme's copy, or the same id if there is none. */
    public Identifier themedVersionOf(Identifier vanilla) {
        String path = vanilla.getPath();
        if ("minecraft".equals(vanilla.getNamespace())) {
            String special = SPECIAL_SCREENS.get(path);
            if (special != null) {
                return themedScreen(special);
            }
            String guiPrefix = "textures/gui/";
            if (path.startsWith(guiPrefix) && path.endsWith(".png") && MISC_TEXTURES.contains(path.substring(guiPrefix.length(), path.length() - 4))) {
                return Identifier.of(ShrineCraft.MOD_ID, "textures/gui/themed/" + key + "/misc/" + path.substring(guiPrefix.length()));
            }
        }
        String prefix = "textures/gui/container/";
        if ("minecraft".equals(vanilla.getNamespace()) && path.startsWith(prefix) && path.endsWith(".png")) {
            String name = path.substring(prefix.length(), path.length() - 4);
            if (THEMED_SCREENS.contains(name)) {
                return themedScreen(name);
            }
        }
        return vanilla;
    }

    public static GuiTheme byKey(String key) {
        for (GuiTheme t : values()) {
            if (t.key.equals(key.toLowerCase(Locale.ROOT))) {
                return t;
            }
        }
        return null;
    }
}
