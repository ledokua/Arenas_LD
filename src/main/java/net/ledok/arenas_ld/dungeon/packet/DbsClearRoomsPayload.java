package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DbsClearRoomsPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<DbsClearRoomsPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dbs_v2_clear_rooms"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DbsClearRoomsPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DbsClearRoomsPayload::blockPos,
        DbsClearRoomsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
