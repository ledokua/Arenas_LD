package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;

/**
 * The result of a dungeon run. Separate axis from {@link DungeonPhase}: phase tracks lifecycle
 * progression (STARTING → RUNNING → CLOSING → DONE); outcome tracks how the run resolved.
 *
 * <p>An in-progress run has outcome {@link #IN_PROGRESS}. The outcome is set exactly once,
 * when the controller transitions the run to {@link DungeonPhase#CLOSING}.
 */
public enum DungeonOutcome {
    /** Run has not ended yet. Always paired with phase STARTING or RUNNING. */
    IN_PROGRESS,
    /** Boss defeated, run completed successfully. */
    WIN,
    /** Dungeon timer hit zero with the boss still alive. */
    LOSS_TIMEOUT,
    /** All players left the run (disconnected past grace period, or hardcore-removed). */
    LOSS_ABANDONED,
    /** The run's Dungeon Boss Spawner went missing mid-run, so the run could not go on. */
    LOSS_FORCED,
    /** A room objective failed hard — e.g. the PROTECT target died. */
    LOSS_OBJECTIVE,
    /**
     * Staff ended the run ({@code ArenasApi.forceEnd}): no rewards, reported as
     * {@code Outcome.FORCED}. Neither a win nor a loss, so not {@link #isLoss()}. An older build
     * reads it back as {@link #IN_PROGRESS}; the run still closes on its exit timer.
     */
    ENDED_BY_STAFF;

    public static final Codec<DungeonOutcome> CODEC = Codec.STRING.xmap(
        DungeonOutcome::fromStringOrDefault,
        Enum::name
    );

    private static DungeonOutcome fromStringOrDefault(String name) {
        try {
            return DungeonOutcome.valueOf(name);
        } catch (IllegalArgumentException e) {
            return IN_PROGRESS;
        }
    }

    /** True for any LOSS_* outcome. False for IN_PROGRESS, WIN and ENDED_BY_STAFF. */
    public boolean isLoss() {
        return this == LOSS_TIMEOUT
            || this == LOSS_ABANDONED
            || this == LOSS_FORCED
            || this == LOSS_OBJECTIVE;
    }
}
