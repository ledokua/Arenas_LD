package net.ledok.arenas_ld.arena.run;

import com.mojang.serialization.Codec;

/**
 * Lifecycle phase of an active arena run. Mirrors
 * {@link net.ledok.arenas_ld.raid.run.RaidPhase}.
 */
public enum ArenaPhase {
    STARTING,
    RUNNING,
    CLOSING,
    DONE;

    public static final Codec<ArenaPhase> CODEC = Codec.STRING.xmap(
        ArenaPhase::fromStringOrDefault,
        Enum::name
    );

    private static ArenaPhase fromStringOrDefault(String name) {
        try {
            return ArenaPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DONE;
        }
    }
}
