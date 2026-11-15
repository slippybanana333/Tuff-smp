package dev.tuffsmp.shrinecraft.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.tuffsmp.shrinecraft.ShrineCraft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * The extra worn geometry (assets/shrinecraft/armor_extras/tuff_extras.json): helmet slouch, chestplate lapels + back collar,
 * baggy leg bulk, shoe tongues. Vanilla armor boxes cannot show these, so they are separate little boxes attached to the
 * player's head / body / legs and posed by running the real player model's animation on the same render state.
 */
public class ArmorExtrasModel extends EntityModel<PlayerEntityRenderState> {
    public record Piece(ModelPart holder, Item item) {}

    /** One editable part of a piece (e.g. the helmet's horns) on one anchor: scaled / moved by WornDisplay.group about its pivot (in fine units). */
    private record GroupRef(ModelPart part, Item item, String group, float px, float py, float pz, boolean mirrorX, float fine) {}

    private final List<GroupRef> groupRefs = new ArrayList<>();

    private final PlayerEntityModel pose;
    private final Map<String, ModelPart> anchors = new HashMap<>();
    private final List<Piece> pieces = new ArrayList<>();

    private final Item only;       // when set, only this item's extra pieces are drawn (one model per armor piece, so each can have its own placement)

    public ArmorExtrasModel(ModelPart root, PlayerEntityModel pose, JsonObject def, Item only) {
        super(root);
        this.pose = pose;
        this.only = only;
        for (String part : List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
            anchors.put(part, root.getChild(part));
        }
        JsonObject all = def.getAsJsonObject("pieces");
        float fine = def.has("fine_scale") ? def.get("fine_scale").getAsFloat() : 1.0F;
        java.util.Set<String> madeGroups = new java.util.HashSet<>();
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            Item item = Registries.ITEM.get(net.minecraft.util.Identifier.of(ShrineCraft.MOD_ID, e.getKey()));
            for (JsonElement pe : e.getValue().getAsJsonArray()) {
                JsonObject pc = pe.getAsJsonObject();
                String partName = pc.get("part").getAsString();
                String group = groupOf(pc);
                ModelPart groupPart = anchors.get(partName).getChild("fine").getChild(groupName(e.getKey(), group));
                if (madeGroups.add(partName + "|" + e.getKey() + "|" + group)) {
                    float[] pv = pivotOf(def, group);
                    groupRefs.add(new GroupRef(groupPart, item, group, pv[0] / fine, pv[1] / fine, pv[2] / fine, partName.equals("right_arm"), fine));
                }
                pieces.add(new Piece(groupPart.getChild(pc.get("name").getAsString()), item));
            }
        }
    }

    private static String groupOf(JsonObject pc) {
        return pc.has("group") ? pc.get("group").getAsString() : "all";
    }

    private static String groupName(String piece, String group) {
        return "g_" + piece + "_" + group;
    }

    private static float[] pivotOf(JsonObject def, String group) {
        if (def.has("groups") && def.getAsJsonObject("groups").has(group)) {
            return arr(def.getAsJsonObject("groups").getAsJsonObject(group).getAsJsonArray("pivot"));
        }
        return new float[] {0, 0, 0};
    }

    /** Builds the model definition from the JSON. Positions are relative to the pivot of the named body part. */
    public static TexturedModelData createModelData(JsonObject def) {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        Map<String, ModelPartData> anchorData = new HashMap<>();
        anchorData.put("head", root.addChild("head", ModelPartBuilder.create(), ModelTransform.origin(0, 0, 0)));
        anchorData.put("body", root.addChild("body", ModelPartBuilder.create(), ModelTransform.origin(0, 0, 0)));
        anchorData.put("right_arm", root.addChild("right_arm", ModelPartBuilder.create(), ModelTransform.origin(-5.0F, 2.0F, 0)));
        anchorData.put("left_arm", root.addChild("left_arm", ModelPartBuilder.create(), ModelTransform.origin(5.0F, 2.0F, 0)));
        anchorData.put("right_leg", root.addChild("right_leg", ModelPartBuilder.create(), ModelTransform.origin(-1.9F, 12.0F, 0)));
        anchorData.put("left_leg", root.addChild("left_leg", ModelPartBuilder.create(), ModelTransform.origin(1.9F, 12.0F, 0)));
        // Every anchor gets a child group "fine" that can shrink everything in it (json "fine_scale", e.g. 0.5 = every unit is half a
        // model pixel), so the armor built from the owner's picture can use blocks smaller than a Minecraft pixel. Default 1 = no change.
        float fine = def.has("fine_scale") ? def.get("fine_scale").getAsFloat() : 1.0F;
        Map<String, ModelPartData> fineData = new HashMap<>();
        for (Map.Entry<String, ModelPartData> a : anchorData.entrySet()) {
            fineData.put(a.getKey(), a.getValue().addChild("fine", ModelPartBuilder.create(), ModelTransform.NONE.scaled(fine)));
        }
        JsonObject all = def.getAsJsonObject("pieces");
        Map<String, ModelPartData> groupData = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            for (JsonElement pe : e.getValue().getAsJsonArray()) {
                JsonObject pc = pe.getAsJsonObject();
                String partName = pc.get("part").getAsString();
                String group = groupOf(pc);
                ModelPartData gd = groupData.get(partName + "|" + e.getKey() + "|" + group);
                if (gd == null) {
                    float[] pv = pivotOf(def, group);
                    gd = fineData.get(partName).addChild(groupName(e.getKey(), group), ModelPartBuilder.create(), ModelTransform.origin(pv[0] / fine, pv[1] / fine, pv[2] / fine));
                    groupData.put(partName + "|" + e.getKey() + "|" + group, gd);
                }
                float[] gp = pivotOf(def, group);
                float[] origin = arr(pc.getAsJsonArray("origin"));
                float[] size = arr(pc.getAsJsonArray("size"));
                int[] uv = {pc.getAsJsonArray("uv").get(0).getAsInt(), pc.getAsJsonArray("uv").get(1).getAsInt()};
                float inflate = pc.has("inflate") ? pc.get("inflate").getAsFloat() : 0.0F;
                boolean mirror = pc.has("mirror") && pc.get("mirror").getAsBoolean();
                float[] rot = pc.has("rotation") ? arr(pc.getAsJsonArray("rotation")) : new float[]{0, 0, 0};
                float[] ro = pc.has("rotation_origin") ? arr(pc.getAsJsonArray("rotation_origin")) : new float[]{0, 0, 0};
                gd.addChild(
                    pc.get("name").getAsString(),
                    ModelPartBuilder.create().uv(uv[0], uv[1]).mirrored(mirror)
                        .cuboid(origin[0] - ro[0] - gp[0] / fine, origin[1] - ro[1] - gp[1] / fine, origin[2] - ro[2] - gp[2] / fine, size[0], size[1], size[2], new Dilation(inflate)),
                    ModelTransform.of(ro[0], ro[1], ro[2], (float) Math.toRadians(rot[0]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[2]))
                );
            }
        }
        JsonArray ts = def.getAsJsonArray("texture_size");
        return TexturedModelData.of(data, ts.get(0).getAsInt(), ts.get(1).getAsInt());
    }

    private static float[] arr(JsonArray a) {
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    public boolean anythingWorn(PlayerEntityRenderState state) {
        for (Piece p : pieces) {
            if (isWorn(state, p.item()) && (only == null || p.item() == only)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWorn(PlayerEntityRenderState state, Item item) {
        ItemStack[] worn = {state.equippedHeadStack, state.equippedChestStack, state.equippedLegsStack, state.equippedFeetStack};
        for (ItemStack s : worn) {
            if (s.isOf(item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setAngles(PlayerEntityRenderState state) {
        super.setAngles(state);
        // Run the real player animation (walking, sneaking, riding ...) and copy the result onto our anchors.
        pose.setAngles(state);
        anchors.get("head").setTransform(pose.head.getTransform());
        anchors.get("body").setTransform(pose.body.getTransform());
        anchors.get("right_arm").setTransform(pose.rightArm.getTransform());
        anchors.get("left_arm").setTransform(pose.leftArm.getTransform());
        anchors.get("right_leg").setTransform(pose.rightLeg.getTransform());
        anchors.get("left_leg").setTransform(pose.leftLeg.getTransform());
        for (Piece p : pieces) {
            p.holder().visible = isWorn(state, p.item()) && (only == null || p.item() == only);
        }
        // per-part edits from the Display Editor (worn_display.json "groups"): move + scale each part about its own pivot
        for (GroupRef g : groupRefs) {
            if (only != null && g.item() != only) {
                continue;
            }
            float[] e = WornDisplay.group(g.item(), g.group());
            ModelTransform t = ModelTransform.origin(g.px(), g.py(), g.pz());
            if (e != null) {
                float sx = g.mirrorX() ? -1.0F : 1.0F;   // arm parts: a positive X edit means "outward" for both arms (the right arm sits at -x)
                t = ModelTransform.origin(g.px() + e[0] * sx / g.fine(), g.py() + e[1] / g.fine(), g.pz() + e[2] / g.fine()).scaled(e[3], e[4], e[5]);
            }
            g.part().setTransform(t);
        }
    }
}
