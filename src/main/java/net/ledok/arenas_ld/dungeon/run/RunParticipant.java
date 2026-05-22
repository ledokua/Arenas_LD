package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * Status of a player within an active dungeon run. Determines loot eligibility at win.
 *
 * <p>Loot eligibility = status != {@link #REMOVED}. So {@link #ACTIVE}, {@link #DOWNED}, and
 * {@link #DISCONNECTED} all receive loot bundles on win; {@link #REMOVED} (hardcore death or
 * explicit leave) does not.
 */
enum ParticipantStatus {
    /** Alive, in the dungeon, surviving. */
    ACTIVE,
    /** In spectator mode, will respawn after a countdown (see {@link DownedPlayer}). */
    DOWNED,
    /** Logged out; may rejoin within the grace period. Counts for loot at win. */
    DISCONNECTED,
    /** Hardcore death or explicit leave. Will not rejoin. No loot at win. */
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
