package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetLobbyHardcorePayload(BlockPos blockPos, boolean hardcore) implements CustomPacketPayload {
    public static final Type<RaidSetLobbyHardcorePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_set_hardcore"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetLobbyHardcorePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidSetLobbyHardcorePayload::blockPos,
            ByteBufCodecs.BOOL, RaidSetLobbyHardcorePayload::hardcore,
            RaidSetLobbyHardcorePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
