package net.ledok.arenas_ld.dungeon.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record RoomControllerData(
    BlockPos blockPos,
    List<BlockPos> spawnerPositions,
    List<BlockPos> doorPositions
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, RoomControllerData> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC, RoomControllerData::blockPos,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), RoomControllerData::spawnerPositions,
            BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), RoomControllerData::doorPositions,
            RoomControllerData::new
        );
}
