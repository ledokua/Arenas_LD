package net.ledok.arenas_ld.screen;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record LobbyData() {
    public static final StreamCodec<FriendlyByteBuf, LobbyData> CODEC = StreamCodec.of(
            (buf, value) -> {},
            (buf) -> new LobbyData()
    );
}
