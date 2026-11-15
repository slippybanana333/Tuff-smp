package dev.tuffsmp.shrinecraft.registry;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.shrine.ShrineBlock;
import dev.tuffsmp.shrinecraft.shrine.ShrineState;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Rarity;

public final class ModBlocks {
    private ModBlocks() {}

    /**
     * The Shrine. Unbreakable and blast-proof in survival (like bedrock) on purpose: if players could break and re-place it,
     * they could reset a used Shrine. Creative-mode operators can still remove it.
     */
    public static final Block SHRINE = registerShrine("shrine");

    private static Block registerShrine(String name) {
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, ShrineCraft.id(name));
        AbstractBlock.Settings settings = AbstractBlock.Settings.create()
            .registryKey(key)
            .mapColor(MapColor.DEEPSLATE_GRAY)
            .strength(-1.0F, 3600000.0F)
            .sounds(BlockSoundGroup.DEEPSLATE_BRICKS)
            .nonOpaque()
            .dropsNothing()
            .pistonBehavior(PistonBehavior.BLOCK)
            .luminance(state -> switch (state.get(ShrineBlock.STATE)) {
                case UNCONFIGURED -> 7;
                case CONFIGURED -> 12;
                case CLAIMED -> 3;
            });
        Block block = Registry.register(Registries.BLOCK, key, new ShrineBlock(settings));
        RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, ShrineCraft.id(name));
        Registry.register(Registries.ITEM, itemKey,
            new BlockItem(block, new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey().rarity(Rarity.EPIC)));
        return block;
    }

    public static void register() {
        // Touching the class runs the static initialisers above, which registers everything.
    }
}
