package dev.tuffsmp.shrinecraft.shrine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.item.CloudLanceItem;
import dev.tuffsmp.shrinecraft.item.CutlassOfDoomItem;
import dev.tuffsmp.shrinecraft.item.FrostLongbowItem;
import dev.tuffsmp.shrinecraft.item.HarbingerOfDeathItem;
import dev.tuffsmp.shrinecraft.item.MoltenGreataxeItem;
import dev.tuffsmp.shrinecraft.item.SculkCannonItem;
import dev.tuffsmp.shrinecraft.item.SculkMaceItem;
import dev.tuffsmp.shrinecraft.item.TuffArmorItem;
import dev.tuffsmp.shrinecraft.item.AnaklusmosItem;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipe.Ingredient;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * THE central recipe registry (the only place that knows what the Shrine asks for).
 *
 * Two ways to change a recipe, no Java knowledge needed for the second:
 *   1. edit defaults() below and rebuild, or
 *   2. edit config/shrinecraft/shrine_recipes.json (created automatically the first time the game runs, then run
 *      /tuffsmp reload or restart). The file wins over the defaults below. This is where you fill in the
 *      recipes - no code change needed. (Potion ingredients: {"item":"minecraft:potion",...} - see the 'potion' field.)
 */
public final class ShrineRecipes {
    private ShrineRecipes() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile List<ShrineRecipe> recipes = defaults();

    public static List<ShrineRecipe> all() {
        return recipes;
    }

    public static ShrineRecipe byId(String id) {
        for (ShrineRecipe r : recipes) {
            if (r.id().equals(id)) {
                return r;
            }
        }
        return null;
    }

    public static int numberOf(ShrineRecipe recipe) {
        return recipes.indexOf(recipe) + 1;
    }

    // ------------------------------------------------------------------------------------------------------------
    //  THE DEFAULT RECIPES (numbers match the design brief)
    // ------------------------------------------------------------------------------------------------------------

    private static Ingredient i(Item item, int count) {
        return new Ingredient(item, count);
    }

    /** Every recipe ends with 1 Magic Essence (admin-command only item). */
    private static List<Ingredient> withEssence(List<Ingredient> list) {
        return withEssence(list, 1);
    }

    /** Same, with a chosen amount (the Sculk Mace asks for 2). */
    private static List<Ingredient> withEssence(List<Ingredient> list, int amount) {
        List<Ingredient> all = new ArrayList<>(list);
        all.add(new Ingredient(dev.tuffsmp.shrinecraft.registry.ModItems.MAGIC_ESSENCE, amount));
        return Collections.unmodifiableList(all);
    }

    private static List<ShrineRecipe> defaults() {
        List<ShrineRecipe> list = new ArrayList<>();

        list.add(new ShrineRecipe("molten_greataxe", "Molten Greataxe", withEssence(List.of(
            i(Items.DIAMOND_AXE, 1), i(Items.GHAST_TEAR, 64),
            i(Items.MAGMA_CREAM, 64), i(Items.FIRE_CHARGE, 16), i(Items.NETHERRACK, 64), i(Items.MAGMA_BLOCK, 64), i(Items.BLAZE_ROD, 64))),
            MoltenGreataxeItem::createStack, false));

        list.add(new ShrineRecipe("cutlass_of_doom", "Cutlass of Doom", withEssence(List.of(
            i(Items.DIAMOND_SWORD, 1), i(Items.CONDUIT, 3), i(Items.TRIDENT, 1),
            i(Items.PRISMARINE_CRYSTALS, 64), i(Items.PRISMARINE_SHARD, 64), i(Items.NAUTILUS_SHELL, 10))),
            CutlassOfDoomItem::createStack, false));

        list.add(new ShrineRecipe("sculk_mace", "Sculk Mace", withEssence(List.of(
            i(Items.HEAVY_CORE, 1), i(Items.ECHO_SHARD, 16), i(Items.SCULK_CATALYST, 64),
            i(Items.SCULK_SENSOR, 32), i(Items.SCULK_SHRIEKER, 16), i(Items.MUSIC_DISC_5, 1)), 2),       // 2 Magic Essence (owner)
            SculkMaceItem::createStack, false));

        list.add(new ShrineRecipe("frost_longbow", "Frost Longbow", withEssence(List.of(
            i(Items.BOW, 1), i(Items.ICE, 64), i(Items.PACKED_ICE, 128), i(Items.BLUE_ICE, 256), i(Items.DIAMOND, 64))),
            FrostLongbowItem::createStack, false));

        list.add(new ShrineRecipe("cloud_lance", "Cloud Lance", withEssence(List.of(
            i(Items.DIAMOND_SPEAR, 1), i(Items.BREEZE_ROD, 64),
            i(Items.FEATHER, 32), i(Items.GHAST_TEAR, 16))),
            r -> new ItemStack(ModItems.CLOUD_LANCE), false));

        list.add(new ShrineRecipe("shrine_helmet", "Shrine Helmet", withEssence(List.of(
            i(Items.DIAMOND_HELMET, 1), i(Items.TUFF, 64), i(Items.GLASS_BOTTLE, 64), i(Items.GLOWSTONE, 64), i(Items.NETHER_WART, 64),
            i(Items.BLAZE_POWDER, 32), i(Items.MAGMA_CREAM, 16))),
            TuffArmorItem::createHelmet, false));

        list.add(new ShrineRecipe("shrine_chestplate", "Shrine Chestplate", withEssence(List.of(
            i(Items.DIAMOND_CHESTPLATE, 1), i(Items.TUFF, 64), i(Items.DIAMOND, 32), i(Items.EMERALD_BLOCK, 256), i(Items.AMETHYST_SHARD, 32))),
            TuffArmorItem::createChestplate, false));

        list.add(new ShrineRecipe("shrine_leggings", "Shrine Leggings", withEssence(List.of(
            i(Items.DIAMOND_LEGGINGS, 1), i(Items.TUFF, 64), i(Items.PRISMARINE_CRYSTALS, 32), i(Items.NAUTILUS_SHELL, 16), i(Items.CONDUIT, 1))),
            TuffArmorItem::createLeggings, false));

        list.add(new ShrineRecipe("shrine_boots", "Shrine Boots", withEssence(List.of(
            i(Items.DIAMOND_BOOTS, 1), i(Items.TUFF, 64), i(Items.SUGAR, 64), i(Items.RABBIT_FOOT, 8), i(Items.FEATHER, 16))),
            TuffArmorItem::createBoots, false));

        list.add(new ShrineRecipe("harbinger_of_death", "Harbinger of Death", withEssence(List.of(
            i(Items.DIAMOND_PICKAXE, 1), i(Items.LAPIS_LAZULI, 64), i(Items.REDSTONE, 64), i(Items.GOLD_INGOT, 64),
            i(Items.DIAMOND, 64), i(Items.COPPER_INGOT, 64), i(Items.COAL, 64), i(Items.IRON_INGOT, 64))),
            HarbingerOfDeathItem::createStack, false));

        list.add(new ShrineRecipe("anaklusmos", "Anaklusmos", withEssence(List.of(
            i(Items.TRIDENT, 1), i(Items.CONDUIT, 1), i(Items.PRISMARINE_SHARD, 64), i(Items.GOLD_BLOCK, 32), i(Items.NAUTILUS_SHELL, 16))),
            AnaklusmosItem::createStack, false));

        list.add(new ShrineRecipe("sculk_cannon", "Sculk Cannon", withEssence(List.of(
            i(Items.CROSSBOW, 1), i(Items.ECHO_SHARD, 16), i(Items.SCULK_CATALYST, 32), i(Items.AMETHYST_SHARD, 16))),
            SculkCannonItem::createStack, false));

        list.add(new ShrineRecipe("strange_cape", "\"Strange\" Cape", withEssence(List.of(
            i(Items.FEATHER, 128), i(Items.SLIME_BALL, 128), i(Items.BREEZE_ROD, 128))),
            r -> new ItemStack(ModItems.STRANGE_CAPE), false));

        // 9 Potions of Swiftness II (strong_swiftness), the NETHERITE horse armor (the ONLY netherite item in any recipe), the saddle and 64 sea lanterns
        list.add(new ShrineRecipe("silvers_spawn_orb", "Silver's Spawn Orb", withEssence(List.of(
            i(Items.NETHERITE_HORSE_ARMOR, 1), i(Items.SADDLE, 1), i(Items.SEA_LANTERN, 64),
            new Ingredient(Items.POTION, 9, Identifier.of("minecraft", "strong_swiftness")))),
            r -> new ItemStack(ModItems.SILVERS_SPAWN_ORB), false));

        return Collections.unmodifiableList(list);
    }

    // ------------------------------------------------------------------------------------------------------------
    //  Config file support (optional override)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Version of the DEFAULT recipes written into the config file ("version" field). Bump it whenever the default recipes change: a config file with an
     * older (or missing) version is replaced by the new defaults on the next start / "/tuffsmp reload" (the old file is kept as shrine_recipes.json.old-vN),
     * so an out-of-date file can never keep asking for items that were removed from the recipes (owner: "it still listed ancient debris and netherite").
     * 1 = no version field (everything before the owner's recipe changes), 2 = the owner's recipe changes of Nov 14,
     * 3 = Sculk Mace costs 2 Magic Essence + the Orb takes netherite horse armor again.
     */
    public static final int CONFIG_VERSION = 3;

    /** Replaces an outdated config file by the current defaults (keeping a backup). Returns a chat line when it did, else null. */
    private static String upgradeIfOutdated(Path path) {
        try {
            if (!Files.exists(path)) {
                return null;
            }
            int version = 1;
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("version")) {
                    version = root.get("version").getAsInt();
                }
            } catch (Exception broken) {
                version = 0;                                   // unreadable: treat as outdated
            }
            if (version >= CONFIG_VERSION) {
                return null;
            }
            Path backup = path.resolveSibling("shrine_recipes.json.old-v" + version);
            Files.copy(path, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            writeTemplate(path, defaults());
            ShrineCraft.LOGGER.warn("shrine_recipes.json was from an older version ({}): replaced by the current recipes, old file kept as {}", version, backup.getFileName());
            return "Your recipe file was outdated, so it was replaced by the current recipes (the old one is saved as " + backup.getFileName() + "). ";
        } catch (IOException e) {
            ShrineCraft.LOGGER.warn("Could not update {}: {}", path, e.toString());
            return null;
        }
    }

    public static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("shrinecraft").resolve("shrine_recipes.json");
    }

    /** Called once at startup: writes the template config if missing, then applies it. */
    public static void bootstrap() {
        Path path = configPath();
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
                writeTemplate(path, defaults());
                ShrineCraft.LOGGER.info("Wrote default Shrine recipe file: {}", path);
            }
        } catch (IOException e) {
            ShrineCraft.LOGGER.warn("Could not write {}: {}", path, e.toString());
        }
        reload();
    }

    /** Re-reads the config file (used by /tuffsmp reload). Returns a human-readable summary. */
    public static String reload() {
        List<ShrineRecipe> base = defaults();
        Path path = configPath();
        String upgraded = upgradeIfOutdated(path);
        if (upgraded != null) {
            return upgraded + reloadFile(base, path);
        }
        return reloadFile(base, path);
    }

    private static String reloadFile(List<ShrineRecipe> base, Path path) {
        if (!Files.exists(path)) {
            recipes = base;
            return "No config file; using built-in recipes.";
        }
        List<ShrineRecipe> result = new ArrayList<>(base);
        int applied = 0;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement el : root.getAsJsonArray("recipes")) {
                JsonObject o = el.getAsJsonObject();
                String id = o.get("id").getAsString();
                int idx = indexOf(result, id);
                if (idx < 0) {
                    ShrineCraft.LOGGER.warn("shrine_recipes.json: unknown recipe id '{}' (ids cannot be added, only edited) - skipped", id);
                    continue;
                }
                ShrineRecipe old = result.get(idx);
                String name = o.has("name") ? o.get("name").getAsString() : old.displayName();
                boolean placeholder = o.has("placeholder") && o.get("placeholder").getAsBoolean();
                List<Ingredient> ingredients = new ArrayList<>();
                boolean ok = true;
                JsonArray arr = o.has("ingredients") ? o.getAsJsonArray("ingredients") : new JsonArray();
                for (JsonElement ie : arr) {
                    JsonObject io = ie.getAsJsonObject();
                    Identifier itemId = Identifier.tryParse(io.get("item").getAsString());
                    int count = io.get("count").getAsInt();
                    Item item = itemId == null ? null : Registries.ITEM.getOptionalValue(itemId).orElse(null);
                    if (item == null || item == Items.AIR || count < 1) {
                        ShrineCraft.LOGGER.warn("shrine_recipes.json: recipe '{}' has a bad ingredient ({} x{}) - whole recipe skipped", id, io.get("item"), count);
                        ok = false;
                        break;
                    }
                    Identifier potionId = io.has("potion") ? Identifier.tryParse(io.get("potion").getAsString()) : null;
                    if (io.has("potion") && (potionId == null || Registries.POTION.getOptionalValue(potionId).isEmpty())) {
                        ShrineCraft.LOGGER.warn("shrine_recipes.json: recipe '{}' has an unknown potion '{}' - whole recipe skipped", id, io.get("potion"));
                        ok = false;
                        break;
                    }
                    ingredients.add(new Ingredient(item, count, potionId));
                }
                if (ok) {
                    result.set(idx, old.with(name, ingredients, placeholder));
                    applied++;
                }
            }
        } catch (Exception e) {
            ShrineCraft.LOGGER.error("Could not read {} - using built-in recipes instead: {}", path, e.toString());
            recipes = base;
            return "Config file is broken (" + e.getMessage() + "); using built-in recipes.";
        }
        recipes = Collections.unmodifiableList(result);
        return "Loaded " + applied + " recipe(s) from " + path.getFileName() + ".";
    }

    private static int indexOf(List<ShrineRecipe> list, String id) {
        for (int k = 0; k < list.size(); k++) {
            if (list.get(k).id().equals(id)) {
                return k;
            }
        }
        return -1;
    }

    private static void writeTemplate(Path path, List<ShrineRecipe> defs) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", CONFIG_VERSION);
        root.addProperty("_help", "Edit ingredients here, then run /tuffsmp reload (or restart). 'item' is an item id like minecraft:diamond. "
            + "An empty ingredients list = recipe not set up yet (cannot be crafted). Set placeholder to false once a recipe is final. "
            + "You can edit name/ingredients/placeholder but not the ids.");
        JsonArray arr = new JsonArray();
        for (ShrineRecipe r : defs) {
            JsonObject o = new JsonObject();
            o.addProperty("id", r.id());
            o.addProperty("name", r.displayName());
            o.addProperty("placeholder", r.placeholder());
            JsonArray ing = new JsonArray();
            for (Ingredient in : r.ingredients()) {
                JsonObject io = new JsonObject();
                io.addProperty("item", Registries.ITEM.getId(in.item()).toString());
                io.addProperty("count", in.count());
                if (in.potion() != null) {
                    io.addProperty("potion", in.potion().toString());
                }
                ing.add(io);
            }
            o.add("ingredients", ing);
            arr.add(o);
        }
        root.add("recipes", arr);
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            GSON.toJson(root, w);
        }
    }
}
