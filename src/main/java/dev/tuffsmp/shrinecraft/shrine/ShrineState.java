package dev.tuffsmp.shrinecraft.shrine;

import net.minecraft.util.StringIdentifiable;

/** The three life stages of a Shrine. Stored in the block entity; mirrored into a block-state property for models and light. */
public enum ShrineState implements StringIdentifiable {
    /** Nobody has chosen what this Shrine produces yet. */
    UNCONFIGURED("unconfigured"),
    /** A recipe is assigned and the reward has not been taken. */
    CONFIGURED("configured"),
    /** The reward was taken. Permanently finished. */
    CLAIMED("claimed");

    private final String id;

    ShrineState(String id) {
        this.id = id;
    }

    @Override
    public String asString() {
        return id;
    }
}
