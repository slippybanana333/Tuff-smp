package dev.tuffsmp.shrinecraft.registry;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.item.AnaklusmosItem;
import dev.tuffsmp.shrinecraft.item.CloudLanceItem;
import dev.tuffsmp.shrinecraft.item.CutlassOfDoomItem;
import dev.tuffsmp.shrinecraft.item.FrostLongbowItem;
import dev.tuffsmp.shrinecraft.item.HarbingerOfDeathItem;
import dev.tuffsmp.shrinecraft.item.MagicEssenceItem;
import dev.tuffsmp.shrinecraft.item.MoltenGreataxeItem;
import dev.tuffsmp.shrinecraft.item.SculkCannonItem;
import dev.tuffsmp.shrinecraft.item.SculkMaceItem;
import dev.tuffsmp.shrinecraft.item.SilversSpawnOrbItem;
import dev.tuffsmp.shrinecraft.item.StrangeCapeItem;
import dev.tuffsmp.shrinecraft.item.TuffArmorItem;
import java.util.function.Function;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/**
 * The 14 items. Ids are shrinecraft:<snake_case_name>. The numbers in the comments are the item numbers from the design brief.
 */
public final class ModItems {
    private ModItems() {}

    public static final Item MOLTEN_GREATAXE = register("molten_greataxe", MoltenGreataxeItem::new, MoltenGreataxeItem.settings());             // 1
    public static final Item CUTLASS_OF_DOOM = register("cutlass_of_doom", CutlassOfDoomItem::new, CutlassOfDoomItem.settings());                // 2
    public static final Item SCULK_MACE = register("sculk_mace", SculkMaceItem::new, SculkMaceItem.settings());                                  // 3
    public static final Item FROST_LONGBOW = register("frost_longbow", FrostLongbowItem::new, FrostLongbowItem.settings());                      // 4
    public static final Item CLOUD_LANCE = register("cloud_lance", CloudLanceItem::new, CloudLanceItem.settings());                              // 5
    public static final Item SHRINE_HELMET = register("shrine_helmet", s -> new TuffArmorItem(s, "shrine_helmet"), TuffArmorItem.helmetSettings());   // 6
    public static final Item SHRINE_CHESTPLATE = register("shrine_chestplate", s -> new TuffArmorItem(s, "shrine_chestplate"), TuffArmorItem.chestplateSettings());       // 7
    public static final Item SHRINE_LEGGINGS = register("shrine_leggings", s -> new TuffArmorItem(s, "shrine_leggings"), TuffArmorItem.leggingsSettings());       // 8
    public static final Item SHRINE_BOOTS = register("shrine_boots", s -> new TuffArmorItem(s, "shrine_boots"), TuffArmorItem.bootsSettings());                          // 9
    public static final Item HARBINGER_OF_DEATH = register("harbinger_of_death", HarbingerOfDeathItem::new, HarbingerOfDeathItem.settings());   // 10
    public static final Item ANAKLUSMOS = register("anaklusmos", AnaklusmosItem::new, AnaklusmosItem.settings());                                // 11
    public static final Item SCULK_CANNON = register("sculk_cannon", SculkCannonItem::new, SculkCannonItem.settings());                          // 12
    public static final Item STRANGE_CAPE = register("strange_cape", StrangeCapeItem::new, StrangeCapeItem.settings());                          // 13
    public static final Item SILVERS_SPAWN_ORB = register("silvers_spawn_orb", SilversSpawnOrbItem::new, SilversSpawnOrbItem.settings());       // 14

    /** Not one of the 14: the crafting material every Shrine recipe needs. Admin-command only (no recipe, no loot, no creative tab). */
    public static final Item MAGIC_ESSENCE = register("magic_essence", MagicEssenceItem::new, MagicEssenceItem.settings());

    private static <T extends Item> T register(String name, Function<Item.Settings, T> factory, Item.Settings settings) {
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, ShrineCraft.id(name));
        // Since 1.21.2 every Item.Settings must know its registry key BEFORE the item is constructed.
        T item = factory.apply(settings.registryKey(key));
        return Registry.register(Registries.ITEM, key, item);
    }

    public static void register() {
        // Touching the class runs the static initialisers above, which registers everything.
    }
}
