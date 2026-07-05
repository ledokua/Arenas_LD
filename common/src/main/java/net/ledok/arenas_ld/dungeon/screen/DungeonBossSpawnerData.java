package net.ledok.arenas_ld.dungeon.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;
import java.util.Optional;

public record DungeonBossSpawnerData(
    BlockPos blockPos,
    String mobId,
    int wave,
    List<BlockPos> rooms,
    List<String> roomNames,
    Optional<BlockPos> startRoom,
    Optional<BlockPos> finalRoom
) {
    // 7 fields: past StreamCodec.composite's arity, so hand-written like RoomControllerData.
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonBossSpawnerData> STREAM_CODEC = StreamCodec.of(
        DungeonBossSpawnerData::encode,
        DungeonBossSpawnerData::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, DungeonBossSpawnerData data) {
        buf.writeBlockPos(data.blockPos());
        buf.writeUtf(data.mobId());
        buf.writeVarInt(data.wave());
        BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, data.rooms());
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, data.roomNames());
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buf, data.startRoom());
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buf, data.finalRoom());
    }

    private static DungeonBossSpawnerData decode(RegistryFriendlyByteBuf buf) {
        BlockPos blockPos = buf.readBlockPos();
        String mobId = buf.readUtf();
        int wave = buf.readVarInt();
        List<BlockPos> rooms = BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
        List<String> roomNames = ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf);
        Optional<BlockPos> startRoom = ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buf);
        Optional<BlockPos> finalRoom = ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buf);
        return new DungeonBossSpawnerData(blockPos, mobId, wave, rooms, roomNames, startRoom, finalRoom);
    }
}
