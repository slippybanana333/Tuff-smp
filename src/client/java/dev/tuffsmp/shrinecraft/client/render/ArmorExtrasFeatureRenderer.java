package dev.tuffsmp.shrinecraft.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import java.io.Reader;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

/** Draws ArmorExtrasModel on players (only when one of the four Tuff SMP pieces is worn). */
public class ArmorExtrasFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
    public static final Identifier DEF = Identifier.of(ShrineCraft.MOD_ID, "armor_extras/tuff_extras.json");

    private final java.util.List<ArmorExtrasModel> extras;       // one model per armor piece
    private final java.util.List<ArmorExtrasModel> glowExtras;   // the same geometry again, drawn full-bright with the glow texture (only the glowing texels are painted)
    private final Identifier glowTexture;
    private final java.util.List<net.minecraft.item.Item> items;
    private final Identifier texture;

    private ArmorExtrasFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context, java.util.List<ArmorExtrasModel> extras, java.util.List<ArmorExtrasModel> glowExtras, java.util.List<net.minecraft.item.Item> items, Identifier texture, Identifier glowTexture) {
        super(context);
        this.extras = extras;
        this.glowExtras = glowExtras;
        this.glowTexture = glowTexture;
        this.items = items;
        this.texture = texture;
    }

    @SuppressWarnings("unchecked")
    public static void register(PlayerEntityRenderer<?> renderer, LivingEntityFeatureRendererRegistrationCallback.RegistrationHelper helper, EntityRendererFactory.Context context) {
        try {
            Optional<Resource> res = context.getResourceManager().getResource(DEF);
            if (res.isEmpty()) {
                ShrineCraft.LOGGER.warn("armor_extras/tuff_extras.json missing - extra armor geometry disabled");
                return;
            }
            JsonObject def;
            try (Reader reader = res.get().getReader()) {
                def = JsonParser.parseReader(reader).getAsJsonObject();
            }
            TexturedModelData modelData = ArmorExtrasModel.createModelData(def);
            java.util.List<ArmorExtrasModel> models = new java.util.ArrayList<>();
            java.util.List<ArmorExtrasModel> glowModels = new java.util.ArrayList<>();
            java.util.List<net.minecraft.item.Item> items = new java.util.ArrayList<>();
            for (String piece : def.getAsJsonObject("pieces").keySet()) {
                net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(Identifier.of(ShrineCraft.MOD_ID, piece));
                PlayerEntityModel pose = new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false);
                models.add(new ArmorExtrasModel(modelData.createModel(), pose, def, item));
                glowModels.add(new ArmorExtrasModel(modelData.createModel(), new PlayerEntityModel(context.getPart(EntityModelLayers.PLAYER), false), def, item));
                items.add(item);
            }
            Identifier tex = Identifier.of(def.get("texture").getAsString());
            Identifier glowTex = def.has("glow_texture") ? Identifier.of(def.get("glow_texture").getAsString()) : null;
            helper.register(new ArmorExtrasFeatureRenderer((FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel>) (Object) renderer, models, glowModels, items, tex, glowTex));
        } catch (Exception e) {
            ShrineCraft.LOGGER.error("Could not set up the extra armor geometry (armor still works, it just loses the slouch/collar/baggy extras)", e);
        }
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state, float limbAngle, float limbDistance) {
        if (state.invisible) {
            return;
        }
        net.minecraft.item.ItemStack[] worn = {state.equippedHeadStack, state.equippedChestStack, state.equippedLegsStack, state.equippedFeetStack};
        for (int i = 0; i < extras.size(); i++) {
            ArmorExtrasModel model = extras.get(i);
            if (!model.anythingWorn(state)) {
                continue;
            }
            net.minecraft.item.ItemStack stack = null;
            for (net.minecraft.item.ItemStack w : worn) {
                if (w.isOf(items.get(i))) {
                    stack = w;
                }
            }
            boolean moved = stack != null && WornDisplay.has(stack);
            if (moved) {
                matrices.push();
                WornDisplay.apply(matrices, stack);
            }
            FeatureRenderer.render(model, texture, matrices, queue, light, state, -1, 1);
            if (glowTexture != null) {
                // the glow pass: same boxes, glow-only texture, "eyes" render layer = ignores darkness (like spider eyes), so the veins and crystals shine at night
                queue.getBatchingQueue(2).submitModel(glowExtras.get(i), state, matrices, RenderLayers.eyes(glowTexture), light, OverlayTexture.DEFAULT_UV, -1, null, state.outlineColor, null);
            }
            if (moved) {
                matrices.pop();
            }
        }
    }
}
