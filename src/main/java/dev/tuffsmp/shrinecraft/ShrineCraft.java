package dev.tuffsmp.shrinecraft;

import dev.tuffsmp.shrinecraft.ability.ArmorEffects;
import dev.tuffsmp.shrinecraft.ability.CannonHandler;
import dev.tuffsmp.shrinecraft.ability.CombatHooks;
import dev.tuffsmp.shrinecraft.ability.HarbingerHandler;
import dev.tuffsmp.shrinecraft.ability.LanceHandler;
import dev.tuffsmp.shrinecraft.ability.LavaBombManager;
import dev.tuffsmp.shrinecraft.ability.SculkWaveManager;
import dev.tuffsmp.shrinecraft.ability.SilverManager;
import dev.tuffsmp.shrinecraft.command.ShrineCommands;
import dev.tuffsmp.shrinecraft.registry.ModBlockEntities;
import dev.tuffsmp.shrinecraft.registry.ModBlocks;
import dev.tuffsmp.shrinecraft.registry.ModComponents;
import dev.tuffsmp.shrinecraft.registry.ModItemGroups;
import dev.tuffsmp.shrinecraft.registry.ModItems;
import dev.tuffsmp.shrinecraft.shrine.ShrineInteraction;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipes;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tuff SMP - main entry point (runs on both the server and the client).
 *
 * Order matters: components first (items refer to them), then items, then the block + block entity,
 * then the creative tab, then all the gameplay hooks.
 */
public class ShrineCraft implements ModInitializer {
    public static final String MOD_ID = "shrinecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger("TuffSMP");

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ModComponents.register();
        ModItems.register();
        ModBlocks.register();
        ModBlockEntities.register();
        ModItemGroups.register();

        ShrineRecipes.bootstrap();
        ShrineInteraction.register();

        ArmorEffects.register();
        CombatHooks.register();
        LavaBombManager.register();
        dev.tuffsmp.shrinecraft.ability.DeepFreezeManager.register();
        SculkWaveManager.register();
        HarbingerHandler.register();
        CannonHandler.register();
        LanceHandler.register();
        SilverManager.register();
        dev.tuffsmp.shrinecraft.ability.TrustManager.register();

        ShrineCommands.register();
        LOGGER.info("Tuff SMP loaded: 14 items + Shrine block registered.");
    }
}
