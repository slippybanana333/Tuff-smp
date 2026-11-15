package dev.tuffsmp.shrinecraft.shrine;

import dev.tuffsmp.shrinecraft.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Remembers which recipe this Shrine is set to and whether its reward was taken. Saved with the world,
 * so a claimed Shrine stays claimed across restarts.
 */
public class ShrineBlockEntity extends BlockEntity {
    /** "" = not configured yet. */
    private String recipeId = "";
    private boolean claimed = false;
    @Nullable
    private UUID claimedBy = null;

    public ShrineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SHRINE, pos, state);
    }

    public ShrineState getShrineState() {
        if (claimed) {
            return ShrineState.CLAIMED;
        }
        return recipeId.isEmpty() ? ShrineState.UNCONFIGURED : ShrineState.CONFIGURED;
    }

    public String getRecipeId() {
        return recipeId;
    }

    @Nullable
    public ShrineRecipe getRecipe() {
        return recipeId.isEmpty() ? null : ShrineRecipes.byId(recipeId);
    }

    public void configure(String recipeId) {
        this.recipeId = recipeId;
        this.claimed = false;
        this.claimedBy = null;
        changed();
    }

    public void claim(UUID player) {
        this.claimed = true;
        this.claimedBy = player;
        changed();
    }

    /** Admin escape hatch (used by the reset command). */
    public void reset() {
        this.recipeId = "";
        this.claimed = false;
        this.claimedBy = null;
        changed();
    }

    /** Saves, mirrors the stage into the block state, and tells nearby clients. */
    private void changed() {
        markDirty();
        if (world != null && !world.isClient()) {
            BlockState current = getCachedState();
            BlockState wanted = current.with(ShrineBlock.STATE, getShrineState());
            if (wanted != current) {
                world.setBlockState(pos, wanted, 3);
            } else {
                world.updateListeners(pos, current, current, 3);
            }
        }
    }

    @Override
    protected void writeData(WriteView view) {
        super.writeData(view);
        view.putString("Recipe", recipeId);
        view.putBoolean("Claimed", claimed);
        if (claimedBy != null) {
            view.put("ClaimedBy", Uuids.INT_STREAM_CODEC, claimedBy);
        }
    }

    @Override
    protected void readData(ReadView view) {
        super.readData(view);
        recipeId = view.getString("Recipe", "");
        claimed = view.getBoolean("Claimed", false);
        claimedBy = view.read("ClaimedBy", Uuids.INT_STREAM_CODEC).orElse(null);
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }
}
