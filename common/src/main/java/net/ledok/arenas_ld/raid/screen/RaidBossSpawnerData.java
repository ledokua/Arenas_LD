package net.ledok.arenas_ld.raid.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record RaidBossSpawnerData(BlockPos blockPos) {
    public static final StreamCodec<FriendlyByteBuf, RaidBossSpawnerData> CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBlockPos(value.blockPos),
            (buf) -> new RaidBossSpawnerData(buf.readBlockPos())
    );
}
