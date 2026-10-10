package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One player's view of an arena controller, sent when the lobby screen opens and pushed to open
 * screens when the lobby state changes. The screen reads only this, so it works when opened
 * remotely (hub, {@code ArenasApi.openLobbies}) where the controller's chunk isn't on the client.
 *
 * @param lobbies       the visible (non-private) lobbies plus the viewer's own
 * @param queuePosition the viewer's lobby's 1-based place in the launch queue; 0 = not queued
 * @param myJoinRequests requests to the viewer's lobby, when they own it
 * @param inLobby       everyone in a forming lobby (for the invite picker)
 * @param inRun         everyone in a running lobby
 */
public record ArenaControllerData(
    BlockPos blockPos,
    List<Lobby> lobbies,
    int queuePosition,
    List<InstanceView> instances,
    List<LeaderboardEntry> leaderboard,
    List<PendingInvite> myInvites,
    List<PendingJoinRequest> myJoinRequests,
    Set<UUID> inLobby,
    Set<UUID> inRun,
    int maxPartySize,
    int maxWave
) {
    /** One arena instance as the lobby strip shows it. */
    public record InstanceView(InstanceStatus status, int cooldownTicksRemaining) {
        static final StreamCodec<RegistryFriendlyByteBuf, InstanceView> STREAM_CODEC = StreamCodec.of(
            (buf, v) -> {
                buf.writeVarInt(v.status().ordinal());
                buf.writeVarInt(v.cooldownTicksRemaining());
            },
            buf -> {
                int ordinal = buf.readVarInt();
                InstanceStatus[] values = InstanceStatus.values();
                InstanceStatus status = ordinal >= 0 && ordinal < values.length ? values[ordinal] : InstanceStatus.FREE;
                return new InstanceView(status, buf.readVarInt());
            });
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Lobby>> LOBBIES =
        ByteBufCodecs.fromCodecWithRegistries(Lobby.CODEC).apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<LeaderboardEntry>> LEADERBOARD =
        ByteBufCodecs.fromCodecWithRegistries(LeaderboardEntry.CODEC).apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<PendingInvite>> INVITES =
        ByteBufCodecs.fromCodecWithRegistries(PendingInvite.CODEC).apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<PendingJoinRequest>> REQUESTS =
        ByteBufCodecs.fromCodecWithRegistries(PendingJoinRequest.CODEC).apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<InstanceView>> INSTANCES =
        InstanceView.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaControllerData> STREAM_CODEC = StreamCodec.of(
        ArenaControllerData::encode, ArenaControllerData::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ArenaControllerData data) {
        buf.writeBlockPos(data.blockPos());
        LOBBIES.encode(buf, data.lobbies());
        buf.writeVarInt(data.queuePosition());
        INSTANCES.encode(buf, data.instances());
        LEADERBOARD.encode(buf, data.leaderboard());
        INVITES.encode(buf, data.myInvites());
        REQUESTS.encode(buf, data.myJoinRequests());
        writeUuids(buf, data.inLobby());
        writeUuids(buf, data.inRun());
        buf.writeVarInt(data.maxPartySize());
        buf.writeVarInt(data.maxWave());
    }

    private static ArenaControllerData decode(RegistryFriendlyByteBuf buf) {
        return new ArenaControllerData(
            buf.readBlockPos(),
            LOBBIES.decode(buf),
            buf.readVarInt(),
            INSTANCES.decode(buf),
            LEADERBOARD.decode(buf),
            INVITES.decode(buf),
            REQUESTS.decode(buf),
            readUuids(buf),
            readUuids(buf),
            buf.readVarInt(),
            buf.readVarInt());
    }

    private static void writeUuids(RegistryFriendlyByteBuf buf, Set<UUID> uuids) {
        buf.writeVarInt(uuids.size());
        for (UUID uuid : uuids) {
            UUIDUtil.STREAM_CODEC.encode(buf, uuid);
        }
    }

    private static Set<UUID> readUuids(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        Set<UUID> uuids = new HashSet<>(count);
        for (int i = 0; i < count; i++) {
            uuids.add(UUIDUtil.STREAM_CODEC.decode(buf));
        }
        return uuids;
    }
}
