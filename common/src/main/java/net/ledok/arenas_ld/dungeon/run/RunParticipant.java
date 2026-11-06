package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * A player's participation record within a dungeon run.
 *
 * <p>Immutable: status transitions use {@link #withStatus(ParticipantStatus, long)} which
 * returns a new record.
 *
 * @param playerUuid   the player's UUID (durable identifier)
 * @param playerName   cached display name; the player may be offline when the run ends so we
 *                     can't always look it up live. Used for leaderboards and chat messages.
 * @param status       current participation status (see {@link ParticipantStatus})
 * @param lastSeenTick server tick when this participant was last confirmed present. Used by
 *                     the controller to expire DISCONNECTED participants past the grace period.
 * @param deaths       how often they went down in this run so far (the respawn delay grows with it)
 */
public record RunParticipant(
    UUID playerUuid,
    String playerName,
    ParticipantStatus status,
    long lastSeenTick,
    int deaths
) {
    public RunParticipant(UUID playerUuid, String playerName, ParticipantStatus status, long lastSeenTick) {
        this(playerUuid, playerName, status, lastSeenTick, 0);
    }

    public enum ParticipantStatus {
        ACTIVE,
        DOWNED,
        DISCONNECTED,
        REMOVED;

        public static final Codec<ParticipantStatus> CODEC = Codec.STRING.xmap(
            ParticipantStatus::fromStringOrDefault,
            Enum::name
        );

        private static ParticipantStatus fromStringOrDefault(String name) {
            try {
                return ParticipantStatus.valueOf(name);
            } catch (IllegalArgumentException e) {
                return REMOVED;
            }
        }
    }

    public static final Codec<RunParticipant> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(RunParticipant::playerUuid),
            Codec.STRING.fieldOf("name").forGetter(RunParticipant::playerName),
            ParticipantStatus.CODEC.fieldOf("status").forGetter(RunParticipant::status),
            Codec.LONG.fieldOf("lastSeenTick").forGetter(RunParticipant::lastSeenTick),
            Codec.INT.optionalFieldOf("deaths", 0).forGetter(RunParticipant::deaths)
        ).apply(instance, RunParticipant::new)
    );

    /** Returns a copy with a new status and updated lastSeenTick. */
    public RunParticipant withStatus(ParticipantStatus newStatus, long tick) {
        return new RunParticipant(playerUuid, playerName, newStatus, tick, deaths);
    }

    /** Returns a copy that went down once more: DOWNED, with one more death. */
    public RunParticipant downed(long tick) {
        return new RunParticipant(playerUuid, playerName, ParticipantStatus.DOWNED, tick, deaths + 1);
    }

    /** True for any status other than REMOVED. */
    public boolean isEligibleForLoot() {
        return status != ParticipantStatus.REMOVED;
    }
}
