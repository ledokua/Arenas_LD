package net.ledok.arenas_ld.arena.screen;

import io.netty.buffer.ByteBuf;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What the arena admin screen shows, sent when it opens and pushed to open admin screens after
 * every change. The screen reads only this, so it works when opened remotely
 * ({@code ArenasApi.openAdmin}), where the controller's chunk isn't on the client.
 *
 * @param dimension      the controller's dimension (with {@code blockPos}, what a snapshot is matched by)
 * @param activityId     the stable API id; blank until the controller's first tick assigns one
 * @param pendingRemovals instances an admin unlinked while their run was going
 */
public record ArenaControllerAdminData(
    BlockPos blockPos,
    ResourceKey<Level> dimension,
    String name,
    String activityId,
    int maxPartySize,
    int maxWave,
    int closeTimerSeconds,
    int cooldownTicks,
    int respawnTimeTicks,
    int inviteExpiryTicks,
    int deathTimePenaltyTicks,
    double hpScalePerPlayer,
    double hpScalePerWave,
    double rewardCurrencyBase,
    double rewardCurrencyExp,
    double rewardXpBase,
    double rewardXpExp,
    List<InstanceEntry> instances,
    Set<BlockPos> pendingRemovals,
    List<LeaderboardEntry> leaderboard
) {
    /** One linked Arena Spawner, in the controller's order. */
    public record InstanceEntry(BlockPos spawnerPos, ResourceKey<Level> dimension, InstanceStatus status,
                                int cooldownTicksRemaining) {
    }

    private static final StreamCodec<ByteBuf, ResourceKey<Level>> DIMENSION =
        ResourceKey.streamCodec(Registries.DIMENSION);
    private static final StreamCodec<RegistryFriendlyByteBuf, List<LeaderboardEntry>> LEADERBOARD =
        ByteBufCodecs.fromCodecWithRegistries(LeaderboardEntry.CODEC).apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaControllerAdminData> STREAM_CODEC = StreamCodec.of(
        ArenaControllerAdminData::encode, ArenaControllerAdminData::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ArenaControllerAdminData data) {
        buf.writeBlockPos(data.blockPos());
        DIMENSION.encode(buf, data.dimension());
        buf.writeUtf(data.name());
        buf.writeUtf(data.activityId());
        buf.writeVarInt(data.maxPartySize());
        buf.writeVarInt(data.maxWave());
        buf.writeVarInt(data.closeTimerSeconds());
        buf.writeVarInt(data.cooldownTicks());
        buf.writeVarInt(data.respawnTimeTicks());
        buf.writeVarInt(data.inviteExpiryTicks());
        buf.writeVarInt(data.deathTimePenaltyTicks());
        buf.writeDouble(data.hpScalePerPlayer());
        buf.writeDouble(data.hpScalePerWave());
        buf.writeDouble(data.rewardCurrencyBase());
        buf.writeDouble(data.rewardCurrencyExp());
        buf.writeDouble(data.rewardXpBase());
        buf.writeDouble(data.rewardXpExp());
        buf.writeVarInt(data.instances().size());
        for (InstanceEntry inst : data.instances()) {
            buf.writeBlockPos(inst.spawnerPos());
            DIMENSION.encode(buf, inst.dimension());
            buf.writeVarInt(inst.status().ordinal());
            buf.writeVarInt(inst.cooldownTicksRemaining());
        }
        buf.writeVarInt(data.pendingRemovals().size());
        for (BlockPos pos : data.pendingRemovals()) {
            buf.writeBlockPos(pos);
        }
        LEADERBOARD.encode(buf, data.leaderboard());
    }

    private static ArenaControllerAdminData decode(RegistryFriendlyByteBuf buf) {
        BlockPos blockPos = buf.readBlockPos();
        ResourceKey<Level> dimension = DIMENSION.decode(buf);
        String name = buf.readUtf();
        String activityId = buf.readUtf();
        int maxPartySize = buf.readVarInt();
        int maxWave = buf.readVarInt();
        int closeTimerSeconds = buf.readVarInt();
        int cooldownTicks = buf.readVarInt();
        int respawnTimeTicks = buf.readVarInt();
        int inviteExpiryTicks = buf.readVarInt();
        int deathTimePenaltyTicks = buf.readVarInt();
        double hpScalePerPlayer = buf.readDouble();
        double hpScalePerWave = buf.readDouble();
        double rewardCurrencyBase = buf.readDouble();
        double rewardCurrencyExp = buf.readDouble();
        double rewardXpBase = buf.readDouble();
        double rewardXpExp = buf.readDouble();
        int instanceCount = buf.readVarInt();
        InstanceStatus[] statuses = InstanceStatus.values();
        List<InstanceEntry> instances = new ArrayList<>(instanceCount);
        for (int i = 0; i < instanceCount; i++) {
            BlockPos spawnerPos = buf.readBlockPos();
            ResourceKey<Level> instanceDimension = DIMENSION.decode(buf);
            int ordinal = buf.readVarInt();
            InstanceStatus status = ordinal >= 0 && ordinal < statuses.length ? statuses[ordinal] : InstanceStatus.FREE;
            instances.add(new InstanceEntry(spawnerPos, instanceDimension, status, buf.readVarInt()));
        }
        int pendingCount = buf.readVarInt();
        Set<BlockPos> pendingRemovals = new HashSet<>(pendingCount);
        for (int i = 0; i < pendingCount; i++) {
            pendingRemovals.add(buf.readBlockPos());
        }
        List<LeaderboardEntry> leaderboard = LEADERBOARD.decode(buf);
        return new ArenaControllerAdminData(blockPos, dimension, name, activityId, maxPartySize, maxWave,
            closeTimerSeconds, cooldownTicks, respawnTimeTicks, inviteExpiryTicks, deathTimePenaltyTicks,
            hpScalePerPlayer, hpScalePerWave, rewardCurrencyBase, rewardCurrencyExp, rewardXpBase, rewardXpExp,
            List.copyOf(instances), Set.copyOf(pendingRemovals), List.copyOf(leaderboard));
    }
}
