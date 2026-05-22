package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;

/**
 * Explicit phase of a dungeon run. Owned by {@code DungeonRun}.
 *
 * <p>Transitions are unidirectional:
 * <pre>
 *     STARTING -> RUNNING -> CLOSING -> DONE
 * </pre>
 * with {@link #DONE} terminal. Loss paths jump straight to {@link #CLOSING} (no separate
 * FAILED phase — the win/loss outcome is tracked separately in {@link DungeonOutcome}).
 *
 * <p>This deliberately replaces the legacy combination of {@code isBattleActive},
 * {@code isDungeonActive}, and {@code internalDungeonCloseTimer} on the old DBS, which
 * allowed inconsistent states. Phase + outcome together describe the run uniquely.
 */
public enum DungeonPhase {
    /** Setup: chunks loading, rooms resetting, players being teleported in. */
    STARTING,
    /** Run in progress: rooms activating in order, dungeon timer ticking. */
    RUNNING,
    /** Run ended (win or loss): close timer counting down, players still in dungeon. */
    CLOSING,
    /** Run fully finalized: players teleported out, cooldown started. Terminal. */
    DONE;

    public static final Codec<DungeonPhase> CODEC = Codec.STRING.xmap(
        DungeonPhase::fromStringOrDefault,
        Enum::name
    );

    private static DungeonPhase fromStringOrDefault(String name) {
        try {
            return DungeonPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DONE;
        }
    }
}
