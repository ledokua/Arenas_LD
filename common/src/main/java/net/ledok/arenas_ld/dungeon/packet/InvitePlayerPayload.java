package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record InvitePlayerPayload(BlockPos blockPos, UUID inviteeUuid) implements CustomPacketPayload {
    public static final Type<InvitePlayerPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dungeon_v2_invite_player"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InvitePlayerPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, InvitePlayerPayload::blockPos,
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), InvitePlayerPayload::inviteeUuid,
            InvitePlayerPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
