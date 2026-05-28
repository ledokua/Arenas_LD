package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetHardcorePayload(BlockPos blockPos, boolean hardcore) implements CustomPacketPayload {
    public static final Type<RaidSetHardcorePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_set_hardcore"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetHardcorePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidSetHardcorePayload::blockPos,
            ByteBufCodecs.BOOL, RaidSetHardcorePayload::hardcore,
            RaidSetHardcorePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
