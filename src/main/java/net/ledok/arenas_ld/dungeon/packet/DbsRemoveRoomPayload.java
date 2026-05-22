package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DbsRemoveRoomPayload(BlockPos blockPos, BlockPos roomPos) implements CustomPacketPayload {
    public static final Type<DbsRemoveRoomPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dbs_v2_remove_room"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DbsRemoveRoomPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DbsRemoveRoomPayload::blockPos,
        BlockPos.STREAM_CODEC, DbsRemoveRoomPayload::roomPos,
        DbsRemoveRoomPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
