package dev.tuffsmp.shrinecraft.registry;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.shrine.ShrineBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final BlockEntityType<ShrineBlockEntity> SHRINE = Registry.register(
        Registries.BLOCK_ENTITY_TYPE,
        ShrineCraft.id("shrine"),
        FabricBlockEntityTypeBuilder.create(ShrineBlockEntity::new, ModBlocks.SHRINE).build()
    );

    public static void register() {
        // Touching the class runs the static initialisers above, which registers everything.
    }
}
