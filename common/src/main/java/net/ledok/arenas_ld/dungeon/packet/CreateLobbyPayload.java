package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreateLobbyPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<CreateLobbyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_create_lobby"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateLobbyPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, CreateLobbyPayload::blockPos,
            CreateLobbyPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
