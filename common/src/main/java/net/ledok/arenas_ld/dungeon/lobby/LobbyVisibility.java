package net.ledok.arenas_ld.dungeon.lobby;

import com.mojang.serialization.Codec;

/**
 * Visibility policy for a lobby.
 */
public enum LobbyVisibility {
    PUBLIC,
    FRIENDS,
    PRIVATE;

    public static final Codec<LobbyVisibility> CODEC = Codec.STRING.xmap(
        LobbyVisibility::fromStringOrDefault,
        Enum::name
    );

    private static LobbyVisibility fromStringOrDefault(String name) {
        try {
            return LobbyVisibility.valueOf(name);
        } catch (IllegalArgumentException e) {
            return PRIVATE;
        }
    }
}
