package net.ledok.arenas_ld.raid.run;

import com.mojang.serialization.Codec;

public enum RaidOutcome {
    IN_PROGRESS,
    WIN,
    LOSS_TIMEOUT,
    LOSS_WIPE,
    LOSS_ABANDONED,
    LOSS_FORCED;

    public static final Codec<RaidOutcome> CODEC = Codec.STRING.xmap(
        RaidOutcome::fromStringOrDefault,
        Enum::name
    );

    private static RaidOutcome fromStringOrDefault(String name) {
        try {
            return RaidOutcome.valueOf(name);
        } catch (IllegalArgumentException e) {
            return IN_PROGRESS;
        }
    }

    public boolean isLoss() {
        return this == LOSS_TIMEOUT
            || this == LOSS_WIPE
            || this == LOSS_ABANDONED
            || this == LOSS_FORCED;
    }
}
