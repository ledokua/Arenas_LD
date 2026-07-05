package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Toggles a room's Start ({@code isFinal=false}) or Final ({@code isFinal=true}) marker on the DBS. */
public record DbsSetRoomMarkerPayload(BlockPos blockPos, BlockPos roomPos, boolean isFinal) implements CustomPacketPayload {
    public static final Type<DbsSetRoomMarkerPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dbs_set_room_marker"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DbsSetRoomMarkerPayload> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, DbsSetRoomMarkerPayload::blockPos,
            BlockPos.STREAM_CODEC, DbsSetRoomMarkerPayload::roomPos,
            ByteBufCodecs.BOOL, DbsSetRoomMarkerPayload::isFinal,
            DbsSetRoomMarkerPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
