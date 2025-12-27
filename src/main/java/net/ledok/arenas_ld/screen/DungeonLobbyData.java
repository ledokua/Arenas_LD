package net.ledok.arenas_ld.screen;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

public record DungeonLobbyData(UUID lobbyId, UUID ownerId, List<String> playerNames) {
    private static final StreamCodec<FriendlyByteBuf, UUID> UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> buf.writeUUID(uuid),
            buf -> buf.readUUID()
    );

    public static final StreamCodec<FriendlyByteBuf, DungeonLobbyData> CODEC = StreamCodec.composite(
            UUID_CODEC, DungeonLobbyData::lobbyId,
            UUID_CODEC, DungeonLobbyData::ownerId,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DungeonLobbyData::playerNames,
            DungeonLobbyData::new
    );
}
