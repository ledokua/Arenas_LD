package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;

/**
 * Status of a player within an active run. Determines loot eligibility at win.
 *
 * <p>Loot eligibility = status != {@link #REMOVED}. So {@link #ACTIVE}, {@link #DOWNED}, and
 * {@link #DISCONNECTED} all receive loot bundles on win; {@link #REMOVED} (hardcore death or
 * explicit leave) does not.
 */
public enum ParticipantStatus {
    ACTIVE,
    DOWNED,
    DISCONNECTED,
    REMOVED;

    public static final Codec<ParticipantStatus> CODEC = Codec.STRING.xmap(
        ParticipantStatus::fromStringOrDefault,
        Enum::name
    );

    private static ParticipantStatus fromStringOrDefault(String name) {
        try {
            return ParticipantStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return REMOVED;
        }
    }
}
