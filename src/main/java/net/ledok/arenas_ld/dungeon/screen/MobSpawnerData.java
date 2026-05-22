package net.ledok.arenas_ld.dungeon.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record MobSpawnerData(BlockPos blockPos, String mobId) {
    public static final StreamCodec<RegistryFriendlyByteBuf, MobSpawnerData> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeBlockPos(data.blockPos);
            buf.writeUtf(data.mobId);
        },
        buf -> new MobSpawnerData(buf.readBlockPos(), buf.readUtf())
    );
}
