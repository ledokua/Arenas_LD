package net.ledok.arenas_ld.raid.screen;

import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record RaidControllerAdminData(
    BlockPos blockPos,
    List<InstanceEntry> instances,
    Set<BlockPos> pendingRemovals,
    int cooldownTicks,
    int closeTimerSeconds,
    int maxPartySize,
    int respawnTimeTicks,
    int inviteExpiryTicks,
    int deathTimePenaltyTicks,
    boolean lootViaInbox,
    Map<DifficultyTier, RaidTierConfig> tierConfigs,
    Map<BlockPos, InstanceRun> runningInstances
) {
    /** One registered raid instance with its current status. */
    public record InstanceEntry(BlockPos spawnerPos, String dimension, InstanceStatus status, int cooldownTicksRemaining) {}

    /** Tier and party label of the run currently occupying an instance. */
    public record InstanceRun(DifficultyTier tier, String party) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidControllerAdminData> STREAM_CODEC = StreamCodec.of(
        RaidControllerAdminData::encode,
        RaidControllerAdminData::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, RaidControllerAdminData data) {
        buf.writeBlockPos(data.blockPos());

        buf.writeVarInt(data.instances().size());
        InstanceStatus[] statuses = InstanceStatus.values();
        for (InstanceEntry inst : data.instances()) {
            buf.writeBlockPos(inst.spawnerPos());
            buf.writeUtf(inst.dimension());
            buf.writeVarInt(inst.status().ordinal());
            buf.writeVarInt(inst.cooldownTicksRemaining());
        }

        buf.writeVarInt(data.pendingRemovals().size());
        for (BlockPos pos : data.pendingRemovals()) {
            buf.writeBlockPos(pos);
        }

        buf.writeVarInt(data.cooldownTicks());
        buf.writeVarInt(data.closeTimerSeconds());
        buf.writeVarInt(data.maxPartySize());
        buf.writeVarInt(data.respawnTimeTicks());
        buf.writeVarInt(data.inviteExpiryTicks());
        buf.writeVarInt(data.deathTimePenaltyTicks());
        buf.writeBoolean(data.lootViaInbox());

        buf.writeVarInt(data.tierConfigs().size());
        for (Map.Entry<DifficultyTier, RaidTierConfig> entry : data.tierConfigs().entrySet()) {
            DifficultyTier.STREAM_CODEC.encode(buf, entry.getKey());
            buf.writeNbt((CompoundTag) RaidTierConfig.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue()).getOrThrow());
        }

        buf.writeVarInt(data.runningInstances().size());
        for (Map.Entry<BlockPos, InstanceRun> entry : data.runningInstances().entrySet()) {
            buf.writeBlockPos(entry.getKey());
            DifficultyTier.STREAM_CODEC.encode(buf, entry.getValue().tier());
            buf.writeUtf(entry.getValue().party());
        }
    }

    private static RaidControllerAdminData decode(RegistryFriendlyByteBuf buf) {
        BlockPos blockPos = buf.readBlockPos();

        int instancesSize = buf.readVarInt();
        List<InstanceEntry> instances = new ArrayList<>(instancesSize);
        InstanceStatus[] statuses = InstanceStatus.values();
        for (int i = 0; i < instancesSize; i++) {
            BlockPos pos = buf.readBlockPos();
            String dim = buf.readUtf();
            int ordinal = buf.readVarInt();
            InstanceStatus status = (ordinal >= 0 && ordinal < statuses.length) ? statuses[ordinal] : InstanceStatus.FREE;
            int cooldown = buf.readVarInt();
            instances.add(new InstanceEntry(pos, dim, status, cooldown));
        }

        int pendingSize = buf.readVarInt();
        Set<BlockPos> pendingRemovals = new HashSet<>();
        for (int i = 0; i < pendingSize; i++) {
            pendingRemovals.add(buf.readBlockPos());
        }

        int cooldownTicks = buf.readVarInt();
        int closeTimerSeconds = buf.readVarInt();
        int maxPartySize = buf.readVarInt();
        int respawnTimeTicks = buf.readVarInt();
        int inviteExpiryTicks = buf.readVarInt();
        int deathTimePenaltyTicks = buf.readVarInt();
        boolean lootViaInbox = buf.readBoolean();

        int tierConfigSize = buf.readVarInt();
        Map<DifficultyTier, RaidTierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);
        for (int i = 0; i < tierConfigSize; i++) {
            DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
            CompoundTag tag = buf.readNbt();
            if (tag != null) {
                tierConfigs.put(tier, RaidTierConfig.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
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

        return new RaidControllerAdminData(
            blockPos,
            instances,
            pendingRemovals,
            cooldownTicks,
            closeTimerSeconds,
            maxPartySize,
            respawnTimeTicks,
            inviteExpiryTicks,
            deathTimePenaltyTicks,
            lootViaInbox,
            tierConfigs,
            runningInstances
        );
    }
}
