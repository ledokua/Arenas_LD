package net.ledok.arenas_ld.dungeon.packet;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DbsMoveRoomPayload(BlockPos blockPos, int fromIndex, int toIndex) implements CustomPacketPayload {
    public static final Type<DbsMoveRoomPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "dbs_v2_move_room"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DbsMoveRoomPayload> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DbsMoveRoomPayload::blockPos,
        ByteBufCodecs.VAR_INT, DbsMoveRoomPayload::fromIndex,
        ByteBufCodecs.VAR_INT, DbsMoveRoomPayload::toIndex,
        DbsMoveRoomPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
