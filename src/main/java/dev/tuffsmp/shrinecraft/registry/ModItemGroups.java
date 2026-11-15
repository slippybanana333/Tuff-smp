package dev.tuffsmp.shrinecraft.registry;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipe;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipes;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;

public final class ModItemGroups {
    private ModItemGroups() {}

    /**
     * TWEAK ME: show the 14 finished items in the creative tab (handy for testing).
     * Set to false if the Shrine block should be the ONLY thing in the tab; admins can still /give the items.
     */
    public static final boolean SHOW_ITEMS_IN_CREATIVE_TAB = true;

    public static final RegistryKey<ItemGroup> TUFF_SMP = RegistryKey.of(RegistryKeys.ITEM_GROUP, ShrineCraft.id("tuff_smp"));

    public static void register() {
        Registry.register(Registries.ITEM_GROUP, TUFF_SMP, FabricItemGroup.builder()
            .displayName(Text.translatable("itemGroup.shrinecraft.tuff_smp"))
            .icon(() -> new ItemStack(ModBlocks.SHRINE))
            .entries((context, entries) -> {
                entries.add(ModBlocks.SHRINE);
                if (SHOW_ITEMS_IN_CREATIVE_TAB) {
                    for (ShrineRecipe recipe : ShrineRecipes.all()) {
                        entries.add(recipe.createOutput(context.lookup()));
                    }
                }
            })
            .build());
    }
}
