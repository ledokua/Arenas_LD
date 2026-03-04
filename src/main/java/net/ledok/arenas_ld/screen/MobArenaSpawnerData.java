package net.ledok.arenas_ld.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MobArenaSpawnerData(BlockPos blockPos) {
    public static final StreamCodec<FriendlyByteBuf, MobArenaSpawnerData> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MobArenaSpawnerData::blockPos,
            MobArenaSpawnerData::new
    );
}
