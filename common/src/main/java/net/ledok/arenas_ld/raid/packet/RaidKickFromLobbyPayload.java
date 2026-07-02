package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RaidKickFromLobbyPayload(BlockPos blockPos, UUID targetUuid) implements CustomPacketPayload {
    public static final Type<RaidKickFromLobbyPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_kick_player"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidKickFromLobbyPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidKickFromLobbyPayload::blockPos,
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), RaidKickFromLobbyPayload::targetUuid,
            RaidKickFromLobbyPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
