package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SetLobbyHardcorePayload(BlockPos blockPos, boolean hardcore) implements CustomPacketPayload {
    public static final Type<SetLobbyHardcorePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_set_lobby_hardcore"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetLobbyHardcorePayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetLobbyHardcorePayload::blockPos,
        ByteBufCodecs.BOOL, SetLobbyHardcorePayload::hardcore,
        SetLobbyHardcorePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
