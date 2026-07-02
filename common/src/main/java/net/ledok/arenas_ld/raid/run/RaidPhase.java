package net.ledok.arenas_ld.raid.run;

import com.mojang.serialization.Codec;

public enum RaidPhase {
    STARTING,
    RUNNING,
    CLOSING,
    DONE;

    public static final Codec<RaidPhase> CODEC = Codec.STRING.xmap(
        RaidPhase::fromStringOrDefault,
        Enum::name
    );

    private static RaidPhase fromStringOrDefault(String name) {
        try {
            return RaidPhase.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DONE;
        }
    }
}
