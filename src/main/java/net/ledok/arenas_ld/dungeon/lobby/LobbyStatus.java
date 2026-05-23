package net.ledok.arenas_ld.dungeon.lobby;

import com.mojang.serialization.Codec;

/**
 * Lifecycle status of a player lobby.
 */
public enum LobbyStatus {
    FORMING,
    READY,
    IN_RUN,
    DISBANDED;

    public static final Codec<LobbyStatus> CODEC = Codec.STRING.xmap(
        LobbyStatus::fromStringOrDefault,
        Enum::name
    );

    private static LobbyStatus fromStringOrDefault(String name) {
        try {
            return LobbyStatus.valueOf(name);
        } catch (IllegalArgumentException e) {
            return DISBANDED;
        }
    }
}
