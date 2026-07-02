package net.ledok.arenas_ld.dungeon.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public record MobSpawnerData(BlockPos blockPos, String mobId, int spawnCount, List<BlockPos> spawnOffsets) {
    public static final StreamCodec<RegistryFriendlyByteBuf, MobSpawnerData> STREAM_CODEC = StreamCodec.of(
        (buf, data) -> {
            buf.writeBlockPos(data.blockPos);
            buf.writeUtf(data.mobId);
            buf.writeVarInt(data.spawnCount);
            buf.writeVarInt(data.spawnOffsets.size());
            for (BlockPos pos : data.spawnOffsets) {
                buf.writeBlockPos(pos);
            }
        },
        buf -> {
            BlockPos blockPos = buf.readBlockPos();
            String mobId = buf.readUtf();
            int spawnCount = buf.readVarInt();
            int offsetCount = buf.readVarInt();
            List<BlockPos> offsets = new ArrayList<>(offsetCount);
            for (int i = 0; i < offsetCount; i++) {
                offsets.add(buf.readBlockPos());
            }
            return new MobSpawnerData(blockPos, mobId, spawnCount, offsets);
        }
    );
}
