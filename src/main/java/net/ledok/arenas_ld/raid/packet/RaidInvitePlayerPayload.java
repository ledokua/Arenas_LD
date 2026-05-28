package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidInvitePlayerPayload(BlockPos blockPos, String playerName) implements CustomPacketPayload {
    public static final Type<RaidInvitePlayerPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_invite_player"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidInvitePlayerPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidInvitePlayerPayload::blockPos,
            ByteBufCodecs.STRING_UTF8, RaidInvitePlayerPayload::playerName,
            RaidInvitePlayerPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
