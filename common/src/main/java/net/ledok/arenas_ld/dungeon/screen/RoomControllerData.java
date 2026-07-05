package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.room.RoomObjectiveConfig;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record RoomControllerData(
    BlockPos blockPos,
    List<SpawnerEntry> spawners,
    List<BlockPos> doorPositions,
    List<BlockPos> entrancePositions,
    String roomName,
    Optional<BlockPos> respawnPos,
    RoomRewardConfig roomReward,
    RoomObjectiveConfig objective,
    List<String> knownLootTableIds
) {
    /** One linked spawner as shown in the room screen. {@code missing} = no spawner BE at the position. */
    public record SpawnerEntry(BlockPos pos, int wave, boolean isBoss, boolean missing) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SpawnerEntry> STREAM_CODEC =
            StreamCodec.composite(
                BlockPos.STREAM_CODEC, SpawnerEntry::pos,
                ByteBufCodecs.VAR_INT, SpawnerEntry::wave,
                ByteBufCodecs.BOOL, SpawnerEntry::isBoss,
                ByteBufCodecs.BOOL, SpawnerEntry::missing,
                SpawnerEntry::new
            );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RoomControllerData> STREAM_CODEC = StreamCodec.of(
        RoomControllerData::encode,
        RoomControllerData::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, RoomControllerData data) {
        buf.writeBlockPos(data.blockPos());
        SpawnerEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, data.spawners());
        BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, data.doorPositions());
        BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, data.entrancePositions());
        buf.writeUtf(data.roomName());
        ByteBufCodecs.optional(BlockPos.STREAM_CODEC).encode(buf, data.respawnPos());
        RoomRewardConfig.STREAM_CODEC.encode(buf, data.roomReward());
        RoomObjectiveConfig.STREAM_CODEC.encode(buf, data.objective());
        buf.writeVarInt(data.knownLootTableIds().size());
        for (String id : data.knownLootTableIds()) {
            buf.writeUtf(id);
        }
    }

    private static RoomControllerData decode(RegistryFriendlyByteBuf buf) {
        BlockPos blockPos = buf.readBlockPos();
        List<SpawnerEntry> spawners = SpawnerEntry.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
        List<BlockPos> doorPositions = BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
        List<BlockPos> entrancePositions = BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf);
        String roomName = buf.readUtf();
        Optional<BlockPos> respawnPos = ByteBufCodecs.optional(BlockPos.STREAM_CODEC).decode(buf);
        RoomRewardConfig roomReward = RoomRewardConfig.STREAM_CODEC.decode(buf);
        RoomObjectiveConfig objective = RoomObjectiveConfig.STREAM_CODEC.decode(buf);
        int lootIdCount = buf.readVarInt();
        List<String> knownLootTableIds = new ArrayList<>(lootIdCount);
        for (int i = 0; i < lootIdCount; i++) {
            knownLootTableIds.add(buf.readUtf());
        }
        return new RoomControllerData(blockPos, spawners, doorPositions, entrancePositions, roomName, respawnPos, roomReward, objective, knownLootTableIds);
    }
}
