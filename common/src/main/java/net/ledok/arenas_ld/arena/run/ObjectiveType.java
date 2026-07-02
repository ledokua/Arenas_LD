package net.ledok.arenas_ld.arena.run;

import com.mojang.serialization.Codec;

/**
 * The objective imposed on an {@link WaveArchetype#OBJECTIVE} wave.
 */
public enum ObjectiveType {
    /** No objective (used when a wave is not an objective wave). */
    NONE,
    /** Keep at least one party member inside the marked zone until the wave clears. */
    DEFEND_ZONE,
    /** Clear the wave without any party member taking damage. */
    SURVIVE_UNTOUCHED,
    /** Kill the single marked target to clear the wave. */
    KILL_MARKED;

    public static final Codec<ObjectiveType> CODEC = Codec.STRING.xmap(
        ObjectiveType::fromStringOrDefault,
        Enum::name
    );

    private static ObjectiveType fromStringOrDefault(String name) {
        try {
            return ObjectiveType.valueOf(name);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
