package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record DungeonControllerData(
    BlockPos blockPos,
    List<Lobby> visibleLobbies,
    Optional<Lobby> ownLobby,
    List<PendingInvite> myInvites,
    List<PendingJoinRequest> myJoinRequests,
    int maxPartySize,
    Map<DifficultyTier, TierConfig> tiers,
    long serverGameTick,
    Set<UUID> busyPlayers,
    Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonControllerData> STREAM_CODEC = StreamCodec.of(
        DungeonControllerData::encode,
        DungeonControllerData::decode
    );

    private static void encode(RegistryFriendlyByteBuf buf, DungeonControllerData data) {
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

        buf.writeVarInt(data.maxPartySize());
        buf.writeVarInt(data.tiers().size());
        for (Map.Entry<DifficultyTier, TierConfig> entry : data.tiers().entrySet()) {
            DifficultyTier.STREAM_CODEC.encode(buf, entry.getKey());
            writeTag(buf, (CompoundTag) TierConfig.CODEC.encodeStart(NbtOps.INSTANCE, entry.getValue()).getOrThrow());
        }
        buf.writeVarLong(data.serverGameTick());

        buf.writeVarInt(data.busyPlayers().size());
        for (UUID busy : data.busyPlayers()) {
            buf.writeUUID(busy);
        }

        buf.writeVarInt(data.topLeaderboards().size());
        for (Map.Entry<DifficultyTier, List<LeaderboardEntry>> entry : data.topLeaderboards().entrySet()) {
            DifficultyTier.STREAM_CODEC.encode(buf, entry.getKey());
            buf.writeVarInt(entry.getValue().size());
            for (LeaderboardEntry leaderboardEntry : entry.getValue()) {
                writeTag(buf, (CompoundTag) LeaderboardEntry.CODEC.encodeStart(NbtOps.INSTANCE, leaderboardEntry).getOrThrow());
            }
        }
    }

    private static DungeonControllerData decode(RegistryFriendlyByteBuf buf) {
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

        int maxPartySize = buf.readVarInt();
        int tierCount = buf.readVarInt();
        Map<DifficultyTier, TierConfig> tiers = new EnumMap<>(DifficultyTier.class);
        for (int i = 0; i < tierCount; i++) {
            DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
            tiers.put(tier, TierConfig.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
        }
        long serverGameTick = buf.readVarLong();

        int busyCount = buf.readVarInt();
        Set<UUID> busyPlayers = new HashSet<>(busyCount);
        for (int i = 0; i < busyCount; i++) {
            busyPlayers.add(buf.readUUID());
        }

        int leaderboardMapSize = buf.readVarInt();
        Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards = new EnumMap<>(DifficultyTier.class);
        for (int i = 0; i < leaderboardMapSize; i++) {
            DifficultyTier tier = DifficultyTier.STREAM_CODEC.decode(buf);
            int entrySize = buf.readVarInt();
            List<LeaderboardEntry> entries = new ArrayList<>(entrySize);
            for (int j = 0; j < entrySize; j++) {
                entries.add(LeaderboardEntry.CODEC.parse(NbtOps.INSTANCE, readTag(buf)).getOrThrow());
            }
            topLeaderboards.put(tier, entries);
        }

        return new DungeonControllerData(blockPos, visible, own, invites, joinReqs, maxPartySize, tiers, serverGameTick, busyPlayers, topLeaderboards);
    }

    private static void writeTag(RegistryFriendlyByteBuf buf, CompoundTag tag) {
        buf.writeNbt(tag);
    }

    private static CompoundTag readTag(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            throw new IllegalStateException("Expected compound tag in DungeonControllerData packet");
        }
        return tag;
    }
}
