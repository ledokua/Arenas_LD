package net.ledok.arenas_ld.raid.run;

import com.mojang.serialization.Codec;

public enum RaidOutcome {
    IN_PROGRESS,
    WIN,
    LOSS_TIMEOUT,
    LOSS_WIPE,
    LOSS_ABANDONED,
    /** Every natural loss (time out, wipe, boss gone): reported to the API as a plain loss. */
    LOSS_FORCED,
    /** Ended by staff ({@code ArenasApi.forceEnd}): the one loss reported as {@code Outcome.FORCED}. */
    LOSS_STAFF;

    public static final Codec<RaidOutcome> CODEC = Codec.STRING.xmap(
        RaidOutcome::fromStringOrDefault,
        Enum::name
    );

    /** Unknown names (a save from a newer version) read as {@link #IN_PROGRESS}. */
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
            || this == LOSS_FORCED
            || this == LOSS_STAFF;
    }
}
