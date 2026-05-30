package net.ledok.arenas_ld.arena.run;

import com.mojang.serialization.Codec;

/**
 * The flavour of a single arena wave. Chosen per wave by the spawner's cadence config
 * (boss / elite / objective every N waves, horde otherwise).
 */
public enum WaveArchetype {
    /** Many low-strength mobs (default). */
    HORDE,
    /** Fewer mobs with an HP/damage buff. */
    ELITE,
    /** {@code isBoss} mobs only — few, tougher, with a boss bar and a longer timer. */
    BOSS,
    /** Normal spawn plus an {@link ObjectiveType} the party must satisfy. */
    OBJECTIVE;

    public static final Codec<WaveArchetype> CODEC = Codec.STRING.xmap(
        WaveArchetype::fromStringOrDefault,
        Enum::name
    );

    private static WaveArchetype fromStringOrDefault(String name) {
        try {
            return WaveArchetype.valueOf(name);
        } catch (IllegalArgumentException e) {
            return HORDE;
        }
    }
}
