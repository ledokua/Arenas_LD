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
    /** Admin used /arenasld debug endDungeon (or equivalent) to terminate the run. */
    LOSS_FORCED;

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

    /** True for any LOSS_* outcome. False for IN_PROGRESS and WIN. */
    public boolean isLoss() {
        return this == LOSS_TIMEOUT
            || this == LOSS_ABANDONED
            || this == LOSS_FORCED;
    }
}
