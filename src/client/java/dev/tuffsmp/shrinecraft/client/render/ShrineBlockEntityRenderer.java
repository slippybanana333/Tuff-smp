package dev.tuffsmp.shrinecraft.client.render;

import dev.tuffsmp.shrinecraft.shrine.ShrineBlockEntity;
import dev.tuffsmp.shrinecraft.shrine.ShrineRecipe;
import dev.tuffsmp.shrinecraft.shrine.ShrineState;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.entity.state.ItemStackEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.jspecify.annotations.Nullable;

/**
 * Draws the rotating, bobbing preview of the reward above a CONFIGURED Shrine (modelled on vanilla's Vault renderer).
 * Nothing is drawn for UNCONFIGURED or CLAIMED Shrines.
 */
public class ShrineBlockEntityRenderer implements BlockEntityRenderer<ShrineBlockEntity, ShrineBlockEntityRenderer.State> {
    // ======================= TWEAK ME =======================
    /** Degrees the preview turns per tick (3.0 = a full turn every 6 seconds). */
    private static final float SPIN_DEGREES_PER_TICK = 3.0F;
    /** Height of the preview's centre above the block's bottom, in blocks. */
    private static final float HEIGHT = 1.75F;
    /** Up/down bobbing distance in blocks. */
    private static final float BOB = 0.08F;
    /** Size multiplier (the item's own "ground" scale is small). */
    private static final float SCALE = 1.7F;
    // ========================================================

    public static class State extends BlockEntityRenderState {
        @Nullable
        public ItemStackEntityRenderState item;
        public float rotation;
        public float bob;
    }

    private final ItemModelManager itemModelManager;
    private final Random random = Random.create();

    public ShrineBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
        this.itemModelManager = context.itemModelManager();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void updateRenderState(ShrineBlockEntity shrine, State state, float tickProgress, Vec3d cameraPos, ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumbling) {
        BlockEntityRenderer.super.updateRenderState(shrine, state, tickProgress, cameraPos, crumbling);
        state.item = null;
        if (shrine.getWorld() == null || shrine.getShrineState() != ShrineState.CONFIGURED) {
            return;
        }
        ShrineRecipe recipe = shrine.getRecipe();
        if (recipe == null) {
            return;
        }
        ItemStack stack = recipe.createOutput(shrine.getWorld().getRegistryManager());
        ItemStackEntityRenderState item = new ItemStackEntityRenderState();
        itemModelManager.clearAndUpdate(item.itemRenderState, stack, ItemDisplayContext.GROUND, shrine.getWorld(), null, 0);
        item.renderedAmount = 1;
        item.seed = ItemStackEntityRenderState.getSeed(stack);
        state.item = item;
        float t = shrine.getWorld().getTime() + tickProgress;
        state.rotation = t * SPIN_DEGREES_PER_TICK;
        state.bob = MathHelper.sin(t / 10.0F) * BOB;
    }

    @Override
    public void render(State state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera) {
        if (state.item == null) {
            return;
        }
        matrices.push();
        matrices.translate(0.5F, HEIGHT + state.bob, 0.5F);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(state.rotation));
        matrices.scale(SCALE, SCALE, SCALE);
        ItemEntityRenderer.renderStack(matrices, queue, 15728880, state.item, random);
        matrices.pop();
    }
}
