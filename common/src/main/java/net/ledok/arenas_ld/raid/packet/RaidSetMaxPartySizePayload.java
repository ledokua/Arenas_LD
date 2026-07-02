package net.ledok.arenas_ld.raid.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RaidSetMaxPartySizePayload(BlockPos controllerPos, int size) implements CustomPacketPayload {
    public static final Type<RaidSetMaxPartySizePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "raid_admin_set_max_party_size"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidSetMaxPartySizePayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RaidSetMaxPartySizePayload::controllerPos,
            ByteBufCodecs.VAR_INT, RaidSetMaxPartySizePayload::size,
            RaidSetMaxPartySizePayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
