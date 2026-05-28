package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record RaidControllerData(
    BlockPos blockPos,
    List<Lobby> visibleLobbies,
    Optional<Lobby> ownLobby,
    List<PendingInvite> myInvites,
    List<PendingJoinRequest> myJoinRequests,
    List<RaidInstanceState> instances,
    int queuePosition,
    int maxPartySize,
    long serverGameTick,
    Set<UUID> busyPlayers,
    Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards
) {
    /**
     * Flat data representation of a single raid instance, suitable for sending to the client.
     */
    public record RaidInstanceState(BlockPos spawnerPos, String dimension, InstanceStatus status, int cooldownTicks) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, RaidControllerData> STREAM_CODEC = StreamCodec.of(
        RaidControllerData::encode,
        RaidControllerData::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, RaidControllerData data) {
        buf.writeBlockPos(data.blockPos());

        buf.writeVarInt(data.visibleLobbies().size());
        for (Lobby lobby : data.visibleLobbies()) {
            writeTag(buf, (CompoundTag) Lobby.CODEC.encodeStart(NbtOps.INSTANCE, lobby).getOrThrow());
        }

        buf.writeBoolean(data.ownLobby().isPresent());
        data.ownLobby().ifPresent(lobby ->
            writeTag(buf, (CompoundTag) Lobby.CODEC.encodeStart(NbtOps.INSTANCE, lobby).getOrThrow()));

        buf.writeVarInt(data.myInvites().size());
        for (PendingInvite invite : data.myInvites()) {
            writeTag(buf, (CompoundTag) PendingInvite.CODEC.encodeStart(NbtOps.INSTANCE, invite).getOrThrow());
        }

        buf.writeVarInt(data.myJoinRequests().size());
        for (PendingJoinRequest req : data.myJoinRequests()) {
            writeTag(buf, (CompoundTag) PendingJoinRequest.CODEC.encodeStart(NbtOps.INSTANCE, req).getOrThrow());
        }

        buf.writeVarInt(data.instances().size());
        for (RaidInstanceState inst : data.instances()) {
            buf.writeBlockPos(inst.spawnerPos());
            buf.writeUtf(inst.dimension());
            buf.writeVarInt(inst.status().ordinal());
            buf.writeVarInt(inst.cooldownTicks());
        }

        buf.writeVarInt(data.queuePosition());
        buf.writeVarInt(data.maxPartySize());
        buf.writeVarLong(data.serverGameTick());

        buf.writeVarInt(data.busyPlayers().size());
        for (UUID uuid : data.busyPlayers()) {
            buf.writeUUID(uuid);
        }

        // Write all DifficultyTier entries in fixed order
        DifficultyTier[] tiers = DifficultyTier.values();
        buf.writeVarInt(tiers.length);
        for (DifficultyTier tier : tiers) {
            DifficultyTier.STREAM_CODEC.encode(buf, tier);
            List<LeaderboardEntry> entries = data.topLeaderboards().getOrDefault(tier, List.of());
            buf.writeVarInt(entries.size());
            for (LeaderboardEntry entry : entries) {
                writeTag(buf, (CompoundTag) LeaderboardEntry.CODEC.encodeStart(NbtOps.INSTANCE, entry).getOrThrow());
            }
        }
    }

    private static RaidControllerData decode(RegistryFriendlyByteBuf buf) {
        BlockPos blockPos = buf.readBlockPos();

        int visibleCount = buf.readVarInt();
        List<Lobby> visible = new ArrayList<>(visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            visible.add(Lobby.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }

        Optional<Lobby> own = Optional.empty();
        if (buf.readBoolean()) {
            own = Optional.of(Lobby.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }

        int inviteCount = buf.readVarInt();
        List<PendingInvite> invites = new ArrayList<>(inviteCount);
        for (int i = 0; i < inviteCount; i++) {
            invites.add(PendingInvite.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }

        int joinReqCount = buf.readVarInt();
        List<PendingJoinRequest> joinReqs = new ArrayList<>(joinReqCount);
        for (int i = 0; i < joinReqCount; i++) {
            joinReqs.add(PendingJoinRequest.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }

        int instanceCount = buf.readVarInt();
        List<RaidInstanceState> instances = new ArrayList<>(instanceCount);
        InstanceStatus[] statusValues = InstanceStatus.values();
        for (int i = 0; i < instanceCount; i++) {
            BlockPos spawnerPos = buf.readBlockPos();
            String dimension = buf.readUtf();
            int statusOrdinal = buf.readVarInt();
            InstanceStatus status = (statusOrdinal >= 0 && statusOrdinal < statusValues.length)
                ? statusValues[statusOrdinal]
                : InstanceStatus.FREE;
            int cooldownTicks = buf.readVarInt();
            instances.add(new RaidInstanceState(spawnerPos, dimension, status, cooldownTicks));
        }

        int queuePosition = buf.readVarInt();
        int maxPartySize = buf.readVarInt();
        long serverGameTick = buf.readVarLong();

        int busyCount = buf.readVarInt();
        Set<UUID> busyPlayers = new HashSet<>(busyCount);
        for (int i = 0; i < busyCount; i++) {
            busyPlayers.add(buf.readUUID());
        }

        int leaderboardTierCount = buf.readVarInt();
        Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards = new EnumMap<>(DifficultyTier.class);
        for (int i = 0; i < leaderboardTierCount; i++) {
            DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
            int entryCount = buf.readVarInt();
            List<LeaderboardEntry> entries = new ArrayList<>(entryCount);
            for (int j = 0; j < entryCount; j++) {
                entries.add(LeaderboardEntry.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
            }
            topLeaderboards.put(tier, entries);
        }

        return new RaidControllerData(
            blockPos, visible, own, invites, joinReqs, instances,
            queuePosition, maxPartySize, serverGameTick, busyPlayers, topLeaderboards
        );
    }

    private static void writeTag(RegistryFriendlyByteBuf buf, CompoundTag tag) {
        buf.writeNbt(tag);
    }

    private static CompoundTag readTag(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            throw new IllegalStateException("Expected compound tag in RaidControllerData packet");
        }
        return tag;
    }
}
