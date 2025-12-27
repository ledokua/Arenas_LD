package net.ledok.arenas_ld.screen;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.UUID;

public record JoinLobbyData(List<LobbyInfo> lobbies) {

    public record LobbyInfo(UUID lobbyId, String dungeonName, String ownerName, int playerCount, int maxPlayers) {
        public static final StreamCodec<FriendlyByteBuf, LobbyInfo> CODEC = StreamCodec.composite(
                StreamCodec.of((buf, uuid) -> buf.writeUUID(uuid), buf -> buf.readUUID()), LobbyInfo::lobbyId,
                ByteBufCodecs.STRING_UTF8, LobbyInfo::dungeonName,
                ByteBufCodecs.STRING_UTF8, LobbyInfo::ownerName,
                ByteBufCodecs.INT, LobbyInfo::playerCount,
                ByteBufCodecs.INT, LobbyInfo::maxPlayers,
                LobbyInfo::new
        );
    }

    public static final StreamCodec<FriendlyByteBuf, JoinLobbyData> CODEC = StreamCodec.composite(
            LobbyInfo.CODEC.apply(ByteBufCodecs.list()), JoinLobbyData::lobbies,
            JoinLobbyData::new
    );
}
