package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record AcceptInvitePayload(BlockPos blockPos, UUID lobbyId) implements CustomPacketPayload {
    public static final Type<AcceptInvitePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_accept_invite"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AcceptInvitePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, AcceptInvitePayload::blockPos,
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), AcceptInvitePayload::lobbyId,
            AcceptInvitePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
