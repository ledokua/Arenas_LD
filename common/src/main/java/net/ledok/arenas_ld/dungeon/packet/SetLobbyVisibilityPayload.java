package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetLobbyVisibilityPayload(BlockPos blockPos, LobbyVisibility visibility) implements CustomPacketPayload {
    public static final Type<SetLobbyVisibilityPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_set_lobby_visibility"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLobbyVisibilityPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetLobbyVisibilityPayload::blockPos,
            ByteBufCodecs.STRING_UTF8.map(LobbyVisibility::valueOf, LobbyVisibility::name), SetLobbyVisibilityPayload::visibility,
            SetLobbyVisibilityPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
