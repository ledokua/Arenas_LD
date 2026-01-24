package net.ledok.arenas_ld.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MobSpawnerData(BlockPos blockPos) {
    public static final StreamCodec<FriendlyByteBuf, MobSpawnerData> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBlockPos(value.blockPos()),
            (buf) -> new MobSpawnerData(buf.readBlockPos())
    );
}