package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record DungeonControllerAdminData(
    BlockPos blockPos,
    List<BlockPos> instances,
    Set<BlockPos> activeRunInstances,
    Map<BlockPos, Integer> instanceCooldownTimers,
    Set<BlockPos> pendingRemovals,
    int cooldownTicks,
    int closeTimerSeconds,
    int maxPartySize,
    int inviteExpiryTicks,
    Map<DifficultyTier, TierConfig> tierConfigs,
    Map<BlockPos, InstanceRun> runningInstances
) {
    /** Tier and party label of the run currently occupying an instance. */
    public record InstanceRun(DifficultyTier tier, String party) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonControllerAdminData> STREAM_CODEC = StreamCodec.of(
        DungeonControllerAdminData::encode,
        DungeonControllerAdminData::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, DungeonControllerAdminData data) {
        buf.writeBlockPos(data.blockPos());

        buf.writeVarInt(data.instances().size());
        for (BlockPos pos : data.instances()) {
            buf.writeBlockPos(pos);
        }

        buf.writeVarInt(data.activeRunInstances().size());
        for (BlockPos pos : data.activeRunInstances()) {
            buf.writeBlockPos(pos);
        }

        buf.writeVarInt(data.instanceCooldownTimers().size());
        for (Map.Entry<BlockPos, Integer> entry : data.instanceCooldownTimers().entrySet()) {
            buf.writeBlockPos(entry.getKey());
            ByteBufCodecs.VAR_INT.encode(buf, entry.getValue());
        }

        buf.writeVarInt(data.pendingRemovals().size());
        for (BlockPos pos : data.pendingRemovals()) {
            buf.writeBlockPos(pos);
        }

        buf.writeVarInt(data.cooldownTicks());
        buf.writeVarInt(data.closeTimerSeconds());
        buf.writeVarInt(data.maxPartySize());
        buf.writeVarInt(data.inviteExpiryTicks());

        buf.writeVarInt(data.tierConfigs().size());
        for (Map.Entry<DifficultyTier, TierConfig> entry : data.tierConfigs().entrySet()) {
            DifficultyTier.STREAM_CODEC.encode(buf, entry.getKey());
            buf.writeNbt((CompoundTag) TierConfig.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue()).getOrThrow());
        }

        buf.writeVarInt(data.runningInstances().size());
        for (Map.Entry<BlockPos, InstanceRun> entry : data.runningInstances().entrySet()) {
            buf.writeBlockPos(entry.getKey());
            DifficultyTier.STREAM_CODEC.encode(buf, entry.getValue().tier());
            buf.writeUtf(entry.getValue().party());
        }
    }

    private static DungeonControllerAdminData decode(RegistryFriendlyByteBuf buf) {
        BlockPos blockPos = buf.readBlockPos();

        int instancesSize = buf.readVarInt();
        List<BlockPos> instances = new ArrayList<>(instancesSize);
        for (int i = 0; i < instancesSize; i++) {
            instances.add(buf.readBlockPos());
        }

        int activeSize = buf.readVarInt();
        Set<BlockPos> activeRunInstances = new HashSet<>();
        for (int i = 0; i < activeSize; i++) {
            activeRunInstances.add(buf.readBlockPos());
        }

        int cooldownSize = buf.readVarInt();
        Map<BlockPos, Integer> instanceCooldownTimers = new HashMap<>();
        for (int i = 0; i < cooldownSize; i++) {
            BlockPos pos = buf.readBlockPos();
            int ticks = ByteBufCodecs.VAR_INT.decode(buf);
            instanceCooldownTimers.put(pos, ticks);
        }

        int pendingSize = buf.readVarInt();
        Set<BlockPos> pendingRemovals = new HashSet<>();
        for (int i = 0; i < pendingSize; i++) {
            pendingRemovals.add(buf.readBlockPos());
        }

        int cooldownTicks = buf.readVarInt();
        int closeTimerSeconds = buf.readVarInt();
        int maxPartySize = buf.readVarInt();
        int inviteExpiryTicks = buf.readVarInt();

        int tierConfigSize = buf.readVarInt();
        Map<DifficultyTier, TierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);
        for (int i = 0; i < tierConfigSize; i++) {
            DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                tierConfigs.put(tier, TierConfig.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
            }
        }

        int runningSize = buf.readVarInt();
        Map<BlockPos, InstanceRun> runningInstances = new HashMap<>();
        for (int i = 0; i < runningSize; i++) {
            BlockPos pos = buf.readBlockPos();
            DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
            String party = buf.readUtf();
            runningInstances.put(pos, new InstanceRun(tier, party));
        }

        return new DungeonControllerAdminData(
            blockPos,
            instances,
            activeRunInstances,
            instanceCooldownTimers,
            pendingRemovals,
            cooldownTicks,
            closeTimerSeconds,
            maxPartySize,
            inviteExpiryTicks,
            tierConfigs,
            runningInstances
        );
    }
}
