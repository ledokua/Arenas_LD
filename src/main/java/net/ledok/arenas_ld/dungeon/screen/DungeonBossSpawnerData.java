package net.ledok.arenas_ld.dungeon.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

public record DungeonBossSpawnerData(
    BlockPos blockPos,
    String mobId,
    BlockPos entrancePos,
    String entranceDimension,
    List<BlockPos> rooms
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonBossSpawnerData> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, DungeonBossSpawnerData::blockPos,
        ByteBufCodecs.STRING_UTF8, DungeonBossSpawnerData::mobId,
        BlockPos.STREAM_CODEC, DungeonBossSpawnerData::entrancePos,
        ByteBufCodecs.STRING_UTF8, DungeonBossSpawnerData::entranceDimension,
        BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), DungeonBossSpawnerData::rooms,
        DungeonBossSpawnerData::new
    );
}
