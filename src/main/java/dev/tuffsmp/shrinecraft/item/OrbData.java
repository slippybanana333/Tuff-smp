package dev.tuffsmp.shrinecraft.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.Uuids;

/**
 * Everything Silver's Spawn Orb remembers, stored on the orb item itself.
 *
 * @param state            STORED (Silver is inside), RELEASED (Silver is out in the world) or COOLDOWN (recalled too soon after a fight)
 * @param cooldownEndsAt   game time at which a COOLDOWN orb becomes STORED again
 * @param silver           UUID of the horse currently out in the world (only while RELEASED)
 * @param silverHealth     Silver's health when last stored (negative = "full health")
 * @param orbId            unique id of this orb (assigned the first time it is used)
 */
public record OrbData(State state, long cooldownEndsAt, Optional<UUID> silver, float silverHealth, UUID orbId) {
    public static final UUID NO_ID = new UUID(0L, 0L);

    public enum State implements StringIdentifiable {
        STORED("stored"),
        RELEASED("released"),
        COOLDOWN("cooldown");

        public static final Codec<State> CODEC = StringIdentifiable.createCodec(State::values);
        private final String id;

        State(String id) {
            this.id = id;
        }

        @Override
        public String asString() {
            return id;
        }
    }

    public static final OrbData DEFAULT = new OrbData(State.STORED, 0L, Optional.empty(), -1.0F, NO_ID);

    public static final Codec<OrbData> CODEC = RecordCodecBuilder.create(i -> i.group(
        State.CODEC.fieldOf("state").forGetter(OrbData::state),
        Codec.LONG.optionalFieldOf("cooldown_ends_at", 0L).forGetter(OrbData::cooldownEndsAt),
        Uuids.INT_STREAM_CODEC.optionalFieldOf("silver").forGetter(OrbData::silver),
        Codec.FLOAT.optionalFieldOf("silver_health", -1.0F).forGetter(OrbData::silverHealth),
        Uuids.INT_STREAM_CODEC.optionalFieldOf("orb_id", NO_ID).forGetter(OrbData::orbId)
    ).apply(i, OrbData::new));

    public OrbData withState(State s) {
        return new OrbData(s, cooldownEndsAt, silver, silverHealth, orbId);
    }

    public OrbData released(UUID silverUuid) {
        return new OrbData(State.RELEASED, 0L, Optional.of(silverUuid), silverHealth, orbId);
    }

    public OrbData stored(float health) {
        return new OrbData(State.STORED, 0L, Optional.empty(), health, orbId);
    }

    public OrbData cooldown(long endsAt, float health) {
        return new OrbData(State.COOLDOWN, endsAt, Optional.empty(), health, orbId);
    }

    public OrbData withId(UUID id) {
        return new OrbData(state, cooldownEndsAt, silver, silverHealth, id);
    }

    /** A COOLDOWN orb whose timer ran out is really STORED. */
    public OrbData resolved(long now) {
        if (state == State.COOLDOWN && now >= cooldownEndsAt) {
            return new OrbData(State.STORED, 0L, Optional.empty(), silverHealth, orbId);
        }
        return this;
    }
}
