package net.ledok.arenas_ld.arena.run;

import com.mojang.serialization.Codec;

/**
 * Terminal result of an arena run. Mirrors {@link net.ledok.arenas_ld.raid.run.RaidOutcome},
 * adapted for wave-survival: {@link #COMPLETED} means the configured max wave was reached;
 * the loss outcomes record how the run ended (rewards still pay out by waves completed).
 */
public enum ArenaOutcome {
    IN_PROGRESS,
    COMPLETED,
    WIPED,
    TIMEOUT,
    ABANDONED,
    FORCED;

    public static final Codec<ArenaOutcome> CODEC = Codec.STRING.xmap(
        ArenaOutcome::fromStringOrDefault,
        Enum::name
    );

    private static ArenaOutcome fromStringOrDefault(String name) {
        try {
            return ArenaOutcome.valueOf(name);
        } catch (IllegalArgumentException e) {
            return IN_PROGRESS;
        }
    }

    public boolean isLoss() {
        return this == WIPED || this == TIMEOUT || this == ABANDONED || this == FORCED;
    }
}
