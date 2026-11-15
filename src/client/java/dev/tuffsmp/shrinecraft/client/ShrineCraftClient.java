package dev.tuffsmp.shrinecraft.client;

import dev.tuffsmp.shrinecraft.ShrineCraft;
import dev.tuffsmp.shrinecraft.client.gui.GuiBackgrounds;
import dev.tuffsmp.shrinecraft.client.gui.GuiCommand;
import dev.tuffsmp.shrinecraft.client.gui.GuiSettings;
import dev.tuffsmp.shrinecraft.client.hud.AbilityBarHud;
import dev.tuffsmp.shrinecraft.client.render.ArmorExtrasFeatureRenderer;
import dev.tuffsmp.shrinecraft.client.render.ShrineBlockEntityRenderer;
import dev.tuffsmp.shrinecraft.registry.ModBlockEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;

/** Client-only setup: Shrine preview renderer, /gui command + backgrounds, extra armor geometry. */
public class ShrineCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Floating, rotating preview of the Shrine's reward.
        BlockEntityRendererRegistry.register(ModBlockEntities.SHRINE, ShrineBlockEntityRenderer::new);

        // Extra worn geometry (helmet slouch, chestplate lapels, baggy legs, shoe tongues) on players.
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer<?> playerRenderer) {
                ArmorExtrasFeatureRenderer.register(playerRenderer, registrationHelper, context);
            }
        });

        // GUI backgrounds: load the settings file and load all five textures ONCE at startup (never a resource reload).
        GuiSettings.load();
        GuiCommand.register();
        ClientLifecycleEvents.CLIENT_STARTED.register(GuiBackgrounds::preload);

        AbilityBarHud.register();
        ShrineCraft.LOGGER.info("Tuff SMP client ready.");
    }
}
