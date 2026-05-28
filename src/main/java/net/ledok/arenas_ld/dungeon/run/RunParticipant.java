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
 */
public record RunParticipant(
    UUID playerUuid,
    String playerName,
    ParticipantStatus status,
    long lastSeenTick
) {
    public static final Codec<RunParticipant> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(RunParticipant::playerUuid),
            Codec.STRING.fieldOf("name").forGetter(RunParticipant::playerName),
            ParticipantStatus.CODEC.fieldOf("status").forGetter(RunParticipant::status),
            Codec.LONG.fieldOf("lastSeenTick").forGetter(RunParticipant::lastSeenTick)
        ).apply(instance, RunParticipant::new)
    );

    /** Returns a copy with a new status and updated lastSeenTick. */
    public RunParticipant withStatus(ParticipantStatus newStatus, long tick) {
        return new RunParticipant(playerUuid, playerName, newStatus, tick);
    }

    /** True for any status other than REMOVED. */
    public boolean isEligibleForLoot() {
        return status != ParticipantStatus.REMOVED;
    }
}
