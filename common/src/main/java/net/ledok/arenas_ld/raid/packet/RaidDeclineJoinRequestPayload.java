package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RaidDeclineJoinRequestPayload(BlockPos blockPos, UUID requesterUuid) implements CustomPacketPayload {
    public static final Type<RaidDeclineJoinRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_decline_join_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidDeclineJoinRequestPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidDeclineJoinRequestPayload::blockPos,
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), RaidDeclineJoinRequestPayload::requesterUuid,
            RaidDeclineJoinRequestPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
