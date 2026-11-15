package dev.tuffsmp.shrinecraft.client.render;

import dev.tuffsmp.shrinecraft.item.CloudLanceItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.joml.Quaternionf;

/**
 * First-person Cloud Lance animation correction.
 * The lance is held pointing UP (the owner's idle pose), but vanilla's hold-out (charge) and jab animations are written for a
 * spear that idles pointing FORWARD, so with an upright lance the tip swings round to point at the player / off the edge of the screen.
 * LancingMixin calls apply() right after vanilla has positioned the animation; we then turn the lance about the hand so its tip
 * points forward, like the vanilla spear. Angles are in degrees (X, Y, Z, applied in that order as in model display transforms).
 * ===================  TWEAK ME  ===================
 */
public final class LancePose {
    private LancePose() {}

    /** Extra rotation while HOLDING OUT (right mouse held = charging). */
    public static volatile float[] HOLD = {-45, 180, 0};
    /** Extra rotation during the JAB (left click). */
    public static volatile float[] JAB = {-15, 180, 0};

    /** Is the item in the given arm's hand a Cloud Lance? */
    public static boolean lanceInArm(Arm arm) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) {
            return false;
        }
        ItemStack stack = mc.player.getMainArm() == arm ? mc.player.getMainHandStack() : mc.player.getOffHandStack();
        return stack.getItem() instanceof CloudLanceItem;
    }

    public static void apply(MatrixStack matrices, float[] deg, Arm arm) {
        if (deg[0] == 0 && deg[1] == 0 && deg[2] == 0) {
            return;
        }
        // The left hand is the mirror image of the right, so Y and Z turn the other way round.
        float mirror = arm == Arm.LEFT ? -1.0F : 1.0F;
        matrices.multiply(new Quaternionf().rotationXYZ(
            (float) Math.toRadians(deg[0]), (float) Math.toRadians(deg[1] * mirror), (float) Math.toRadians(deg[2] * mirror)));
    }
}
