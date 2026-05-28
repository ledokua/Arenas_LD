package net.ledok.arenas_ld.block.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.screen.RaidControllerData;
import net.ledok.arenas_ld.screen.RaidControllerScreenHandler;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.arenas_ld.util.RaidDifficulty;
import net.ledok.arenas_ld.util.RaidRunCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class RaidControllerBlockEntity extends BlockEntity
    implements RaidRunCallback, ExtendedScreenHandlerFactory<RaidControllerData> {

    // ─────────────────────────────────────────────────────────────────────────
    //  Inner types
    // ─────────────────────────────────────────────────────────────────────────

    public record ControllerKey(BlockPos pos, ResourceKey<Level> dimension) {}

    /**
     * Legacy compatibility alias — maps the old per-controller LobbyStatus names to the new
     * shared {@link net.ledok.arenas_ld.dungeon.lobby.LobbyStatus} equivalents.
     * Code that previously imported {@code RaidControllerBlockEntity.LobbyStatus} still compiles.
     */
    public enum LobbyStatus {
        /** Lobby is being formed — not everyone ready yet. */
        OPEN,
        /** Lobby is waiting in the launch queue. */
        QUEUED,
        /** Lobby is currently in a raid instance. */
        IN_DUNGEON;

        /** Convert to the canonical {@link net.ledok.arenas_ld.dungeon.lobby.LobbyStatus}. */
        public net.ledok.arenas_ld.dungeon.lobby.LobbyStatus toCanonical() {
            return switch (this) {
                case OPEN -> net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.FORMING;
                case QUEUED -> net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.FORMING;
                case IN_DUNGEON -> net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN;
            };
        }

        /** Convert from the canonical status. */
        public static LobbyStatus fromCanonical(net.ledok.arenas_ld.dungeon.lobby.LobbyStatus status) {
            return switch (status) {
                case FORMING, READY -> OPEN;
                case IN_RUN -> IN_DUNGEON;
                case DISBANDED -> OPEN;
            };
        }
    }

    /**
     * Legacy compatibility alias for the old per-controller LobbyVisibility enum.
     */
    public enum LobbyVisibility {
        OPEN,
        INVITE_ONLY;

        public net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility toCanonical() {
            return switch (this) {
                case OPEN -> net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PUBLIC;
                case INVITE_ONLY -> net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PRIVATE;
            };
        }

        public static LobbyVisibility fromCanonical(net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility vis) {
            return switch (vis) {
                case PUBLIC, FRIENDS -> OPEN;
                case PRIVATE -> INVITE_ONLY;
            };
        }
    }

    public record RaidInstanceState(
        BlockPos spawnerPos,
        ResourceKey<Level> dimension,
        InstanceStatus status,
        int cooldownTicksRemaining
    ) {
        public RaidInstanceState withStatus(InstanceStatus nextStatus, int nextCooldownTicks) {
            return new RaidInstanceState(spawnerPos, dimension, nextStatus, nextCooldownTicks);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constants
    // ─────────────────────────────────────────────────────────────────────────

    private static final String BUSY_REASON = "arenas_ld:raid";
    private static final int DEFAULT_RESPAWN_TIME_TICKS = 6000;
    private static final int DEFAULT_MAX_PARTY_SIZE = 10;
    private static final int DEFAULT_INVITE_EXPIRY_TICKS = 30 * 20;
    private static final int OFFLINE_GRACE_TICKS = 5 * 60 * 20;
    private static final int MAX_LEADERBOARD_ENTRIES = 10;

    /** Global registry of all loaded raid controllers (server-side). */
    private static final Set<ControllerKey> CONTROLLERS = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public static Set<ControllerKey> getControllers() {
        return CONTROLLERS;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fields
    // ─────────────────────────────────────────────────────────────────────────

    /** Pool of all registered raid instances. */
    private final List<RaidInstanceState> instances = new ArrayList<>();
    /** All active lobbies. */
    private final List<Lobby> lobbies = new ArrayList<>();
    /** Ordered queue of lobby IDs waiting for a free instance. */
    private final List<UUID> queuedLobbyIds = new ArrayList<>();
    /** All pending invites across all lobbies. */
    private final List<PendingInvite> pendingInvites = new ArrayList<>();
    /** All pending join requests across all lobbies. */
    private final List<PendingJoinRequest> pendingJoinRequests = new ArrayList<>();
    /** Maps a running lobbyId → the spawnerPos of the instance it is running on. */
    private final Map<UUID, BlockPos> lobbyToInstance = new HashMap<>();
    /** Per-player offline timestamp for grace-period tracking. */
    private final Map<UUID, Long> offlineSinceTick = new HashMap<>();
    /** Leaderboard entries, top 10 per tier. */
    private final Map<DifficultyTier, List<LeaderboardEntry>> leaderboards = new EnumMap<>(DifficultyTier.class);

    private int respawnTimeTicks = DEFAULT_RESPAWN_TIME_TICKS;
    private int maxPartySize = DEFAULT_MAX_PARTY_SIZE;
    private int inviteExpiryTicks = DEFAULT_INVITE_EXPIRY_TICKS;

    // ─────────────────────────────────────────────────────────────────────────
    //  NBT codecs
    // ─────────────────────────────────────────────────────────────────────────

    private record InstanceEntry(
        BlockPos spawnerPos,
        String dimension,
        String status,
        int cooldownTicksRemaining
    ) {
        static final Codec<InstanceEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("spawnerPos").forGetter(InstanceEntry::spawnerPos),
            Codec.STRING.fieldOf("dimension").forGetter(InstanceEntry::dimension),
            Codec.STRING.fieldOf("status").forGetter(InstanceEntry::status),
            Codec.INT.fieldOf("cooldownTicksRemaining").forGetter(InstanceEntry::cooldownTicksRemaining)
        ).apply(i, InstanceEntry::new));
    }

    private record LobbyInstanceEntry(String lobbyId, String spawnerPos) {
        static final Codec<LobbyInstanceEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("lobbyId").forGetter(LobbyInstanceEntry::lobbyId),
            Codec.STRING.fieldOf("spawnerPos").forGetter(LobbyInstanceEntry::spawnerPos)
        ).apply(i, LobbyInstanceEntry::new));
    }

    private record State(
        int respawnTimeTicks,
        int maxPartySize,
        int inviteExpiryTicks,
        List<InstanceEntry> instances,
        List<Lobby> lobbies,
        List<UUID> queuedLobbyIds,
        List<PendingInvite> pendingInvites,
        List<PendingJoinRequest> pendingJoinRequests,
        List<LobbyInstanceEntry> lobbyToInstance,
        Map<DifficultyTier, List<LeaderboardEntry>> leaderboards
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("respawnTimeTicks", DEFAULT_RESPAWN_TIME_TICKS).forGetter(State::respawnTimeTicks),
            Codec.INT.optionalFieldOf("maxPartySize", DEFAULT_MAX_PARTY_SIZE).forGetter(State::maxPartySize),
            Codec.INT.optionalFieldOf("inviteExpiryTicks", DEFAULT_INVITE_EXPIRY_TICKS).forGetter(State::inviteExpiryTicks),
            InstanceEntry.CODEC.listOf().optionalFieldOf("instances", List.of()).forGetter(State::instances),
            Lobby.CODEC.listOf().optionalFieldOf("lobbies", List.of()).forGetter(State::lobbies),
            UUIDUtil.CODEC.listOf().optionalFieldOf("queuedLobbyIds", List.of()).forGetter(State::queuedLobbyIds),
            PendingInvite.CODEC.listOf().optionalFieldOf("pendingInvites", List.of()).forGetter(State::pendingInvites),
            PendingJoinRequest.CODEC.listOf().optionalFieldOf("pendingJoinRequests", List.of()).forGetter(State::pendingJoinRequests),
            LobbyInstanceEntry.CODEC.listOf().optionalFieldOf("lobbyToInstance", List.of()).forGetter(State::lobbyToInstance),
            Codec.unboundedMap(DifficultyTier.CODEC, LeaderboardEntry.CODEC.listOf())
                .optionalFieldOf("leaderboards", Map.of()).forGetter(State::leaderboards)
        ).apply(i, State::new));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Constructor
    // ─────────────────────────────────────────────────────────────────────────

    public RaidControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.RAID_CONTROLLER_BLOCK_ENTITY, pos, state);
        for (DifficultyTier tier : DifficultyTier.values()) {
            leaderboards.putIfAbsent(tier, new ArrayList<>());
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide) {
            CONTROLLERS.add(new ControllerKey(worldPosition, level.dimension()));
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            CONTROLLERS.remove(new ControllerKey(worldPosition, level.dimension()));
        }
        super.setRemoved();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Static tick entry-point
    // ─────────────────────────────────────────────────────────────────────────

    public static void tick(Level level, BlockPos pos, BlockState state, RaidControllerBlockEntity be) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickCooldowns(serverLevel);
        be.tickLobbies(serverLevel);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Accessors
    // ─────────────────────────────────────────────────────────────────────────

    public List<RaidInstanceState> getInstances() {
        return Collections.unmodifiableList(instances);
    }

    public List<Lobby> getLobbies() {
        return Collections.unmodifiableList(lobbies);
    }

    public List<UUID> getQueuedLobbyIds() {
        return Collections.unmodifiableList(queuedLobbyIds);
    }

    public int getRespawnTimeTicks() {
        return Math.max(0, respawnTimeTicks);
    }

    public void setRespawnTimeTicks(int ticks) {
        this.respawnTimeTicks = Math.max(0, ticks);
        markDirtyAndSync();
    }

    public int getMaxPartySize() {
        return Math.max(1, Math.min(20, maxPartySize));
    }

    public void setMaxPartySize(int size) {
        this.maxPartySize = Math.max(1, Math.min(20, size));
        markDirtyAndSync();
    }

    /**
     * Convenience UUID-only leave, used by cross-controller lobby cleanup.
     * No server-player reference is needed; member is simply removed.
     */
    public boolean leaveLobby(UUID uuid) {
        Lobby lobby = getLobbyByMember(uuid);
        if (lobby == null) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;
        if (lobby.isOwner(uuid)) {
            if (lobby.members().size() <= 1) {
                disbandLobby(lobby);
            } else {
                UUID newOwner = lobby.members().stream().filter(m -> !m.equals(uuid)).findFirst().orElse(uuid);
                replaceLobby(lobby.withMemberRemoved(uuid).withOwner(newOwner));
            }
        } else {
            replaceLobby(lobby.withMemberRemoved(uuid));
        }
        offlineSinceTick.remove(uuid);
        BusyStateCompat.clearBusy(uuid, BUSY_REASON);
        markDirtyAndSync();
        return true;
    }

    /**
     * Returns top leaderboard entries for the given RaidDifficulty (legacy helper for ModPackets).
     */
    public java.util.List<net.ledok.arenas_ld.util.RaidLeaderboardEntry> getLeaderboardForDifficulty(net.ledok.arenas_ld.util.RaidDifficulty difficulty) {
        if (difficulty == null) return java.util.List.of();
        DifficultyTier tier = difficulty.toDifficultyTier();
        List<LeaderboardEntry> entries = leaderboards.getOrDefault(tier, java.util.List.of());
        return entries.stream()
            .map(e -> new net.ledok.arenas_ld.util.RaidLeaderboardEntry(e.playerName(), e.timeSeconds()))
            .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Instance management
    // ─────────────────────────────────────────────────────────────────────────

    public boolean addInstance(BlockPos spawnerPos, ResourceKey<Level> dimension) {
        if (spawnerPos == null || dimension == null) return false;
        for (RaidInstanceState inst : instances) {
            if (inst.spawnerPos().equals(spawnerPos) && inst.dimension().equals(dimension)) return false;
        }
        instances.add(new RaidInstanceState(spawnerPos, dimension, InstanceStatus.FREE, 0));
        markDirtyAndSync();
        return true;
    }

    public boolean removeInstance(BlockPos spawnerPos, ResourceKey<Level> dimension) {
        for (Iterator<RaidInstanceState> it = instances.iterator(); it.hasNext(); ) {
            RaidInstanceState inst = it.next();
            if (inst.spawnerPos().equals(spawnerPos) && inst.dimension().equals(dimension)) {
                if (inst.status() == InstanceStatus.RUNNING) return false;
                it.remove();
                lobbyToInstance.values().removeIf(p -> p.equals(spawnerPos));
                markDirtyAndSync();
                return true;
            }
        }
        return false;
    }

    private @Nullable RaidInstanceState reserveFreeInstance() {
        for (int i = 0; i < instances.size(); i++) {
            RaidInstanceState inst = instances.get(i);
            if (inst.status() == InstanceStatus.FREE) {
                RaidInstanceState running = inst.withStatus(InstanceStatus.RUNNING, 0);
                instances.set(i, running);
                return running;
            }
        }
        return null;
    }

    private boolean hasAnyFreeInstance() {
        return instances.stream().anyMatch(i -> i.status() == InstanceStatus.FREE);
    }

    private void releaseInstance(BlockPos spawnerPos) {
        for (int i = 0; i < instances.size(); i++) {
            RaidInstanceState inst = instances.get(i);
            if (inst.spawnerPos().equals(spawnerPos) && inst.status() == InstanceStatus.RUNNING) {
                int cooldown = Math.max(0, respawnTimeTicks);
                InstanceStatus next = cooldown > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
                instances.set(i, inst.withStatus(next, cooldown));
                return;
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lobby lookups
    // ─────────────────────────────────────────────────────────────────────────

    public @Nullable Lobby getLobbyById(UUID lobbyId) {
        if (lobbyId == null) return null;
        for (Lobby lobby : lobbies) {
            if (lobby.lobbyId().equals(lobbyId)) return lobby;
        }
        return null;
    }

    private int indexOfLobby(UUID lobbyId) {
        for (int i = 0; i < lobbies.size(); i++) {
            if (lobbies.get(i).lobbyId().equals(lobbyId)) return i;
        }
        return -1;
    }

    private void replaceLobby(Lobby updated) {
        int idx = indexOfLobby(updated.lobbyId());
        if (idx >= 0) lobbies.set(idx, updated);
    }

    public @Nullable Lobby getLobbyByMember(UUID playerUuid) {
        if (playerUuid == null) return null;
        for (Lobby lobby : lobbies) {
            if (lobby.isMember(playerUuid)) return lobby;
        }
        return null;
    }

    /**
     * Returns pending invites targeting the given player UUID (for building the legacy info payload).
     */
    public List<PendingInvite> getPendingInvitesForPlayer(UUID playerUuid) {
        List<PendingInvite> result = new ArrayList<>();
        for (PendingInvite inv : pendingInvites) {
            if (inv.invitedUuid().equals(playerUuid)) result.add(inv);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Lobby operations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new lobby owned by the given player.
     * @return true if created, false if player already has a lobby or is busy.
     */
    public boolean createLobby(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();
        if (getLobbyByMember(uuid) != null) return false;
        Set<UUID> members = new HashSet<>();
        members.add(uuid);
        Map<UUID, String> memberNames = new HashMap<>();
        memberNames.put(uuid, name);
        Lobby lobby = new Lobby(
            UUID.randomUUID(), uuid, name, members, memberNames,
            Set.of(), DifficultyTier.NORMAL, false,
            net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PUBLIC, net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.FORMING,
            level != null ? level.getGameTime() : 0L
        );
        lobbies.add(lobby);
        markDirtyAndSync();
        return true;
    }

    /**
     * Removes player from their lobby; transfers ownership or disbands as needed.
     */
    public boolean leaveLobby(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Lobby lobby = getLobbyByMember(uuid);
        if (lobby == null) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.cannot_leave_running"));
            return false;
        }

        if (lobby.isOwner(uuid)) {
            if (lobby.members().size() <= 1) {
                // Only owner left → disband
                disbandLobby(lobby);
            } else {
                // Transfer to first other member
                UUID newOwner = lobby.members().stream()
                    .filter(m -> !m.equals(uuid))
                    .findFirst().orElse(uuid);
                Lobby updated = lobby.withMemberRemoved(uuid).withOwner(newOwner);
                replaceLobby(updated);
                notifyLobbyMembers(updated, Component.translatable("message.arenas_ld.lobby_owner_transferred"));
            }
        } else {
            Lobby updated = lobby.withMemberRemoved(uuid);
            replaceLobby(updated);
        }
        offlineSinceTick.remove(uuid);
        BusyStateCompat.clearBusy(uuid, BUSY_REASON);
        markDirtyAndSync();
        return true;
    }

    /**
     * Invites a player by name to the inviter's lobby.
     */
    public boolean invitePlayer(ServerPlayer inviter, String targetName) {
        Lobby lobby = getLobbyByMember(inviter.getUUID());
        if (lobby == null || !lobby.isOwner(inviter.getUUID())) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;
        if (lobby.isFull(getMaxPartySize())) return false;
        if (!(level instanceof ServerLevel sl)) return false;

        ServerPlayer target = sl.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null || target.getUUID().equals(inviter.getUUID())) return false;
        if (getLobbyByMember(target.getUUID()) != null) return false;
        // Check if already has pending invite
        for (PendingInvite invite : pendingInvites) {
            if (invite.lobbyId().equals(lobby.lobbyId()) && invite.invitedUuid().equals(target.getUUID())) return false;
        }

        long expiry = (level != null ? level.getGameTime() : 0L) + inviteExpiryTicks;
        pendingInvites.add(new PendingInvite(
            lobby.lobbyId(), target.getUUID(), inviter.getUUID(),
            expiry, lobby.selectedTier(), inviter.getGameProfile().getName()
        ));
        markDirtyAndSync();
        return true;
    }

    /**
     * Accepts an invite and adds the player to the lobby.
     */
    public boolean acceptInvite(ServerPlayer player, UUID lobbyId) {
        UUID uuid = player.getUUID();
        PendingInvite invite = findInvite(lobbyId, uuid);
        if (invite == null) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.invite_expired_or_not_found"));
            return false;
        }
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null || lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) {
            pendingInvites.remove(invite);
            markDirtyAndSync();
            return false;
        }
        if (getLobbyByMember(uuid) != null) return false;
        if (lobby.isFull(getMaxPartySize())) return false;
        if ((level != null ? level.getGameTime() : Long.MAX_VALUE) > invite.expiresAtTick()) {
            pendingInvites.remove(invite);
            markDirtyAndSync();
            return false;
        }

        pendingInvites.remove(invite);
        // Also remove other invites for this player
        pendingInvites.removeIf(i -> i.invitedUuid().equals(uuid));
        Lobby updated = lobby.withMemberAdded(uuid, player.getGameProfile().getName());
        replaceLobby(updated);
        markDirtyAndSync();
        return true;
    }

    /**
     * Declines an invite.
     */
    public boolean declineInvite(ServerPlayer player, UUID lobbyId) {
        PendingInvite invite = findInvite(lobbyId, player.getUUID());
        if (invite == null) return false;
        pendingInvites.remove(invite);
        markDirtyAndSync();
        return true;
    }

    /**
     * Owner kicks a member from their lobby.
     */
    public boolean kickFromLobby(ServerPlayer owner, UUID targetUuid) {
        Lobby lobby = getLobbyByMember(owner.getUUID());
        if (lobby == null || !lobby.isOwner(owner.getUUID())) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;
        if (owner.getUUID().equals(targetUuid)) return false;
        if (!lobby.isMember(targetUuid)) return false;

        Lobby updated = lobby.withMemberRemoved(targetUuid);
        replaceLobby(updated);
        offlineSinceTick.remove(targetUuid);
        BusyStateCompat.clearBusy(targetUuid, BUSY_REASON);
        // Notify kicked player
        if (level instanceof ServerLevel sl) {
            ServerPlayer kicked = sl.getServer().getPlayerList().getPlayer(targetUuid);
            if (kicked != null) {
                kicked.sendSystemMessage(Component.translatable("message.arenas_ld.you_were_kicked"));
            }
        }
        markDirtyAndSync();
        return true;
    }

    public boolean setTier(ServerPlayer player, DifficultyTier tier) {
        Lobby lobby = getLobbyByMember(player.getUUID());
        if (lobby == null || !lobby.isOwner(player.getUUID())) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;
        replaceLobby(lobby.withTier(tier));
        markDirtyAndSync();
        return true;
    }

    public boolean setHardcore(ServerPlayer player, boolean hardcore) {
        Lobby lobby = getLobbyByMember(player.getUUID());
        if (lobby == null || !lobby.isOwner(player.getUUID())) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;
        replaceLobby(lobby.withHardcore(hardcore));
        markDirtyAndSync();
        return true;
    }

    public boolean setVisibility(ServerPlayer player, net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility visibility) {
        Lobby lobby = getLobbyByMember(player.getUUID());
        if (lobby == null || !lobby.isOwner(player.getUUID())) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;
        replaceLobby(lobby.withVisibility(visibility));
        markDirtyAndSync();
        return true;
    }

    /**
     * Toggles the player's ready state. Updates lobby status to READY when all members ready,
     * back to FORMING when any unready.
     */
    public boolean toggleReady(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Lobby lobby = getLobbyByMember(uuid);
        if (lobby == null) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;

        Lobby updated;
        if (lobby.readyMembers().contains(uuid)) {
            updated = lobby.withUnready(uuid);
        } else {
            updated = lobby.withReady(uuid);
        }
        // Update status
        if (updated.allReady()) {
            updated = updated.withStatus(net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.READY);
        } else if (updated.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.READY) {
            updated = updated.withStatus(net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.FORMING);
        }
        replaceLobby(updated);
        markDirtyAndSync();
        return true;
    }

    /**
     * Directly joins a PUBLIC lobby.
     */
    public boolean joinLobby(ServerPlayer player, UUID lobbyId) {
        UUID uuid = player.getUUID();
        if (getLobbyByMember(uuid) != null) return false;
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null) return false;
        if (lobby.visibility() != net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PUBLIC) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN || lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.DISBANDED) return false;
        if (lobby.isFull(getMaxPartySize())) return false;

        Lobby updated = lobby.withMemberAdded(uuid, player.getGameProfile().getName());
        replaceLobby(updated);
        markDirtyAndSync();
        return true;
    }

    /**
     * Sends a join request to a FRIENDS lobby.
     */
    public boolean requestJoin(ServerPlayer player, UUID lobbyId) {
        UUID uuid = player.getUUID();
        if (getLobbyByMember(uuid) != null) return false;
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null) return false;
        if (lobby.visibility() != net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.FRIENDS) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN || lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.DISBANDED) return false;
        if (lobby.isFull(getMaxPartySize())) return false;
        // No duplicate requests
        for (PendingJoinRequest req : pendingJoinRequests) {
            if (req.lobbyId().equals(lobbyId) && req.requesterUuid().equals(uuid)) return false;
        }

        long expiry = (level != null ? level.getGameTime() : 0L) + inviteExpiryTicks;
        pendingJoinRequests.add(new PendingJoinRequest(
            lobbyId, uuid, player.getGameProfile().getName(), expiry, lobby.selectedTier()
        ));
        markDirtyAndSync();
        return true;
    }

    /**
     * Owner accepts a join request.
     */
    public boolean acceptJoinRequest(ServerPlayer owner, UUID requesterUuid) {
        Lobby lobby = getLobbyByMember(owner.getUUID());
        if (lobby == null || !lobby.isOwner(owner.getUUID())) return false;

        PendingJoinRequest req = findJoinRequest(lobby.lobbyId(), requesterUuid);
        if (req == null) return false;
        pendingJoinRequests.remove(req);

        if (getLobbyByMember(requesterUuid) != null) return false;
        if (lobby.isFull(getMaxPartySize())) return false;
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;

        String requesterName = req.requesterName();
        if (level instanceof ServerLevel sl) {
            ServerPlayer requester = sl.getServer().getPlayerList().getPlayer(requesterUuid);
            if (requester != null) requesterName = requester.getGameProfile().getName();
        }
        Lobby updated = lobby.withMemberAdded(requesterUuid, requesterName);
        replaceLobby(updated);
        markDirtyAndSync();
        return true;
    }

    /**
     * Owner declines a join request.
     */
    public boolean declineJoinRequest(ServerPlayer owner, UUID requesterUuid) {
        Lobby lobby = getLobbyByMember(owner.getUUID());
        if (lobby == null || !lobby.isOwner(owner.getUUID())) return false;

        PendingJoinRequest req = findJoinRequest(lobby.lobbyId(), requesterUuid);
        if (req == null) return false;
        pendingJoinRequests.remove(req);
        markDirtyAndSync();
        return true;
    }

    /**
     * Owner starts the raid. If a free instance exists the run starts immediately;
     * otherwise the lobby is queued.
     */
    public boolean startRaid(ServerPlayer player) {
        Lobby lobby = getLobbyByMember(player.getUUID());
        if (lobby == null || !lobby.isOwner(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.only_owner_can_start_dungeon"));
            return false;
        }
        if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) return false;
        if (!(level instanceof ServerLevel serverLevel)) return false;

        RaidInstanceState freeInst = reserveFreeInstance();
        if (freeInst == null) {
            // Queue it
            if (!queuedLobbyIds.contains(lobby.lobbyId())) {
                queuedLobbyIds.add(lobby.lobbyId());
                notifyLobbyMembers(lobby, Component.translatable("message.arenas_ld.lobby_queued"));
            }
            markDirtyAndSync();
            return false;
        }

        return launchRaidOnInstance(serverLevel, lobby, freeInst);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RaidRunCallback
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onRaidEnded(
        BlockPos spawnerPos,
        boolean wasWin,
        RaidDifficulty difficulty,
        List<String> playerNames,
        int timeSeconds
    ) {
        // Update leaderboard
        if (wasWin && playerNames != null && difficulty != null) {
            DifficultyTier tier = difficulty.toDifficultyTier();
            long now = System.currentTimeMillis();
            List<LeaderboardEntry> list = leaderboards.computeIfAbsent(tier, t -> new ArrayList<>());
            for (String name : playerNames) {
                if (name == null || name.isBlank()) continue;
                list.removeIf(e -> e.playerName().equals(name) && e.timeSeconds() >= timeSeconds);
                boolean alreadyBetter = list.stream().anyMatch(e -> e.playerName().equals(name));
                if (!alreadyBetter) {
                    list.add(new LeaderboardEntry(name, timeSeconds, now));
                }
            }
            list.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds));
            if (list.size() > MAX_LEADERBOARD_ENTRIES) {
                list.subList(MAX_LEADERBOARD_ENTRIES, list.size()).clear();
            }
        }

        // Find which lobby this spawner belonged to
        UUID lobbyId = null;
        for (Map.Entry<UUID, BlockPos> entry : lobbyToInstance.entrySet()) {
            if (entry.getValue().equals(spawnerPos)) {
                lobbyId = entry.getKey();
                break;
            }
        }

        // Clear busy state
        if (lobbyId != null) {
            Lobby lobby = getLobbyById(lobbyId);
            if (lobby != null) {
                for (UUID memberUuid : lobby.members()) {
                    BusyStateCompat.clearBusy(memberUuid, BUSY_REASON);
                }
                lobbies.remove(lobby);
                // Clean up related invites, requests, queue
                final UUID finalLobbyId = lobbyId;
                pendingInvites.removeIf(inv -> inv.lobbyId().equals(finalLobbyId));
                pendingJoinRequests.removeIf(req -> req.lobbyId().equals(finalLobbyId));
            }
            lobbyToInstance.remove(lobbyId);
            queuedLobbyIds.remove(lobbyId);
        } else if (playerNames != null && level instanceof ServerLevel sl) {
            for (String name : playerNames) {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayerByName(name);
                if (p != null) BusyStateCompat.clearBusy(p.getUUID(), BUSY_REASON);
            }
        }

        // Release instance
        releaseInstance(spawnerPos);

        // Promote next queued lobby
        promoteNextQueuedLobby();
        markDirtyAndSync();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Tick helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void tickCooldowns(ServerLevel level) {
        boolean changed = false;
        for (int i = 0; i < instances.size(); i++) {
            RaidInstanceState inst = instances.get(i);
            if (inst.status() != InstanceStatus.COOLDOWN) continue;
            int remaining = Math.max(0, inst.cooldownTicksRemaining() - 1);
            InstanceStatus next = remaining > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
            instances.set(i, inst.withStatus(next, remaining));
            changed = true;
        }
        if (changed) {
            promoteNextQueuedLobby();
            markDirtyAndSync();
        }
    }

    private void tickLobbies(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        boolean changed = false;

        // Expire invites
        changed |= pendingInvites.removeIf(inv -> now > inv.expiresAtTick());

        // Expire join requests
        changed |= pendingJoinRequests.removeIf(req -> now > req.expiresAtTick());

        // Clean offline members
        List<UUID> toDisband = new ArrayList<>();
        for (Lobby lobby : lobbies) {
            if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN) {
                offlineSinceTick.keySet().removeIf(uuid -> lobby.isMember(uuid));
                continue;
            }
            for (UUID memberUuid : new ArrayList<>(lobby.members())) {
                boolean online = serverLevel.getServer().getPlayerList().getPlayer(memberUuid) != null;
                if (online) {
                    offlineSinceTick.remove(memberUuid);
                    continue;
                }
                long since = offlineSinceTick.computeIfAbsent(memberUuid, id -> now);
                if (now - since >= OFFLINE_GRACE_TICKS) {
                    if (lobby.isOwner(memberUuid)) {
                        if (lobby.members().size() <= 1) {
                            toDisband.add(lobby.lobbyId());
                        } else {
                            UUID newOwner = lobby.members().stream()
                                .filter(m -> !m.equals(memberUuid))
                                .findFirst().orElse(memberUuid);
                            Lobby updated = lobby.withMemberRemoved(memberUuid).withOwner(newOwner);
                            replaceLobby(updated);
                        }
                    } else {
                        Lobby updated = lobby.withMemberRemoved(memberUuid);
                        replaceLobby(updated);
                    }
                    offlineSinceTick.remove(memberUuid);
                    changed = true;
                }
            }
        }

        // Disband empty lobbies
        for (UUID lobbyId : toDisband) {
            Lobby lobby = getLobbyById(lobbyId);
            if (lobby != null) {
                disbandLobby(lobby);
                changed = true;
            }
        }

        if (changed) {
            markDirtyAndSync();
        }
    }

    private void promoteNextQueuedLobby() {
        if (!hasAnyFreeInstance()) return;
        if (queuedLobbyIds.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        UUID nextLobbyId = queuedLobbyIds.get(0);
        Lobby lobby = getLobbyById(nextLobbyId);
        if (lobby == null) {
            queuedLobbyIds.remove(0);
            promoteNextQueuedLobby();
            return;
        }

        RaidInstanceState freeInst = reserveFreeInstance();
        if (freeInst == null) return;

        queuedLobbyIds.remove(0);
        launchRaidOnInstance(serverLevel, lobby, freeInst);
    }

    private boolean launchRaidOnInstance(ServerLevel serverLevel, Lobby lobby, RaidInstanceState instance) {
        ServerLevel spawnerLevel = serverLevel.getServer().getLevel(instance.dimension());
        if (spawnerLevel == null) {
            // Release the reserved instance
            for (int i = 0; i < instances.size(); i++) {
                if (instances.get(i).spawnerPos().equals(instance.spawnerPos())) {
                    instances.set(i, instances.get(i).withStatus(InstanceStatus.FREE, 0));
                    break;
                }
            }
            return false;
        }

        var be = spawnerLevel.getBlockEntity(instance.spawnerPos());
        if (!(be instanceof BossSpawnerBlockEntity spawner)) {
            for (int i = 0; i < instances.size(); i++) {
                if (instances.get(i).spawnerPos().equals(instance.spawnerPos())) {
                    instances.set(i, instances.get(i).withStatus(InstanceStatus.FREE, 0));
                    break;
                }
            }
            return false;
        }

        List<ServerPlayer> players = resolveOnlinePlayers(lobby, serverLevel.getServer());
        if (players.isEmpty()) {
            for (int i = 0; i < instances.size(); i++) {
                if (instances.get(i).spawnerPos().equals(instance.spawnerPos())) {
                    instances.set(i, instances.get(i).withStatus(InstanceStatus.FREE, 0));
                    break;
                }
            }
            return false;
        }

        RaidDifficulty difficulty = RaidDifficulty.from(lobby.selectedTier());
        spawner.startBattle(
            spawnerLevel,
            players,
            difficulty,
            this.worldPosition,
            serverLevel.dimension(),
            lobby.hardcoreEnabled(),
            spawner.battleTimeLimitTicks
        );

        for (ServerPlayer p : players) {
            BusyStateCompat.setBusy(p.getUUID(), BUSY_REASON);
        }

        Lobby running = lobby.withStatus(net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN);
        replaceLobby(running);
        lobbyToInstance.put(lobby.lobbyId(), instance.spawnerPos());
        markDirtyAndSync();
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private void disbandLobby(Lobby lobby) {
        for (UUID memberUuid : lobby.members()) {
            offlineSinceTick.remove(memberUuid);
        }
        pendingInvites.removeIf(inv -> inv.lobbyId().equals(lobby.lobbyId()));
        pendingJoinRequests.removeIf(req -> req.lobbyId().equals(lobby.lobbyId()));
        queuedLobbyIds.remove(lobby.lobbyId());
        lobbies.remove(lobby);
        lobbyToInstance.remove(lobby.lobbyId());
    }

    private @Nullable PendingInvite findInvite(UUID lobbyId, UUID invitedUuid) {
        for (PendingInvite inv : pendingInvites) {
            if (inv.lobbyId().equals(lobbyId) && inv.invitedUuid().equals(invitedUuid)) return inv;
        }
        return null;
    }

    private @Nullable PendingJoinRequest findJoinRequest(UUID lobbyId, UUID requesterUuid) {
        for (PendingJoinRequest req : pendingJoinRequests) {
            if (req.lobbyId().equals(lobbyId) && req.requesterUuid().equals(requesterUuid)) return req;
        }
        return null;
    }

    private List<ServerPlayer> resolveOnlinePlayers(Lobby lobby, net.minecraft.server.MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID memberUuid : lobby.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberUuid);
            if (p != null) players.add(p);
        }
        return players;
    }

    private void notifyLobbyMembers(Lobby lobby, Component message) {
        if (!(level instanceof ServerLevel sl)) return;
        for (UUID memberUuid : lobby.members()) {
            ServerPlayer p = sl.getServer().getPlayerList().getPlayer(memberUuid);
            if (p != null) p.sendSystemMessage(message);
        }
    }

    private void markDirtyAndSync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  NBT persistence
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);

        List<InstanceEntry> instanceEntries = new ArrayList<>();
        for (RaidInstanceState inst : instances) {
            instanceEntries.add(new InstanceEntry(
                inst.spawnerPos(),
                inst.dimension().location().toString(),
                inst.status().name(),
                inst.cooldownTicksRemaining()
            ));
        }

        List<LobbyInstanceEntry> lobbyInstanceEntries = new ArrayList<>();
        for (Map.Entry<UUID, BlockPos> entry : lobbyToInstance.entrySet()) {
            lobbyInstanceEntries.add(new LobbyInstanceEntry(
                entry.getKey().toString(),
                entry.getValue().getX() + "," + entry.getValue().getY() + "," + entry.getValue().getZ()
            ));
        }

        State state = new State(
            respawnTimeTicks,
            maxPartySize,
            inviteExpiryTicks,
            instanceEntries,
            new ArrayList<>(lobbies),
            new ArrayList<>(queuedLobbyIds),
            new ArrayList<>(pendingInvites),
            new ArrayList<>(pendingJoinRequests),
            lobbyInstanceEntries,
            new EnumMap<>(leaderboards)
        );

        State.CODEC.encodeStart(NbtOps.INSTANCE, state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save RaidController at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);

        if (nbt.contains("State", Tag.TAG_COMPOUND)) {
            State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load RaidController at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    respawnTimeTicks = state.respawnTimeTicks();
                    maxPartySize = state.maxPartySize();
                    inviteExpiryTicks = state.inviteExpiryTicks();

                    instances.clear();
                    for (InstanceEntry ie : state.instances()) {
                        ResourceKey<Level> dim = parseDimension(ie.dimension());
                        InstanceStatus status;
                        try { status = InstanceStatus.valueOf(ie.status()); } catch (Exception e) { status = InstanceStatus.FREE; }
                        instances.add(new RaidInstanceState(ie.spawnerPos(), dim, status, ie.cooldownTicksRemaining()));
                    }

                    lobbies.clear();
                    lobbies.addAll(state.lobbies());

                    queuedLobbyIds.clear();
                    queuedLobbyIds.addAll(state.queuedLobbyIds());

                    pendingInvites.clear();
                    pendingInvites.addAll(state.pendingInvites());

                    pendingJoinRequests.clear();
                    pendingJoinRequests.addAll(state.pendingJoinRequests());

                    lobbyToInstance.clear();
                    for (LobbyInstanceEntry lie : state.lobbyToInstance()) {
                        try {
                            UUID lobbyId = UUID.fromString(lie.lobbyId());
                            String[] parts = lie.spawnerPos().split(",");
                            if (parts.length == 3) {
                                BlockPos spawnerPos = new BlockPos(
                                    Integer.parseInt(parts[0].trim()),
                                    Integer.parseInt(parts[1].trim()),
                                    Integer.parseInt(parts[2].trim())
                                );
                                lobbyToInstance.put(lobbyId, spawnerPos);
                            }
                        } catch (Exception ignored) {}
                    }

                    leaderboards.clear();
                    for (Map.Entry<DifficultyTier, List<LeaderboardEntry>> entry : state.leaderboards().entrySet()) {
                        leaderboards.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                    }
                    // Ensure all tiers exist
                    for (DifficultyTier tier : DifficultyTier.values()) {
                        leaderboards.putIfAbsent(tier, new ArrayList<>());
                    }
                });
        } else {
            // Legacy NBT fallback — migrate old-format data
            loadLegacyNbt(nbt);
        }
    }

    /** Attempts to read the legacy (pre-rework) NBT format so existing worlds don't lose data. */
    private void loadLegacyNbt(CompoundTag nbt) {
        respawnTimeTicks = nbt.contains("RespawnTimeTicks") ? nbt.getInt("RespawnTimeTicks") : DEFAULT_RESPAWN_TIME_TICKS;
        maxPartySize = nbt.contains("MaxPartySize") ? nbt.getInt("MaxPartySize") : DEFAULT_MAX_PARTY_SIZE;
        for (DifficultyTier tier : DifficultyTier.values()) {
            leaderboards.putIfAbsent(tier, new ArrayList<>());
        }
        // Instance data
        instances.clear();
        if (nbt.contains("Instances", Tag.TAG_LIST)) {
            var instancesList = nbt.getList("Instances", Tag.TAG_COMPOUND);
            for (Tag t : instancesList) {
                var tag = (CompoundTag) t;
                BlockPos spawnerPos = BlockPos.of(tag.getLong("SpawnerPos"));
                ResourceKey<Level> dim = parseDimension(tag.getString("Dimension"));
                InstanceStatus status;
                try { status = InstanceStatus.valueOf(tag.getString("Status")); } catch (Exception e) { status = InstanceStatus.FREE; }
                int cooldown = tag.getInt("CooldownTicksRemaining");
                instances.add(new RaidInstanceState(spawnerPos, dim, status, cooldown));
            }
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Screen factory
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arenas_ld.raid_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new RaidControllerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public RaidControllerData getScreenOpeningData(ServerPlayer player) {
        return buildDataFor(player);
    }

    private RaidControllerData buildDataFor(ServerPlayer player) {
        UUID uuid = player.getUUID();

        // Visible lobbies: PUBLIC + FRIENDS that are not IN_RUN or DISBANDED
        List<Lobby> visible = new ArrayList<>();
        for (Lobby lobby : lobbies) {
            if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN || lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.DISBANDED) continue;
            if (lobby.visibility() == net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PRIVATE) continue;
            visible.add(lobby);
        }

        // Own lobby
        Optional<Lobby> ownLobby = Optional.ofNullable(getLobbyByMember(uuid));

        // My invites
        List<PendingInvite> myInvites = new ArrayList<>();
        for (PendingInvite inv : pendingInvites) {
            if (inv.invitedUuid().equals(uuid)) myInvites.add(inv);
        }

        // My join requests: requests I sent + requests to lobbies I own
        List<PendingJoinRequest> myJoinRequests = new ArrayList<>();
        for (PendingJoinRequest req : pendingJoinRequests) {
            if (req.requesterUuid().equals(uuid)) {
                myJoinRequests.add(req);
            } else {
                Lobby lobby = getLobbyById(req.lobbyId());
                if (lobby != null && lobby.isOwner(uuid)) {
                    myJoinRequests.add(req);
                }
            }
        }

        // Instances as flat data records
        List<RaidControllerData.RaidInstanceState> instanceData = new ArrayList<>();
        for (RaidInstanceState inst : instances) {
            instanceData.add(new RaidControllerData.RaidInstanceState(
                inst.spawnerPos(),
                inst.dimension().location().toString(),
                inst.status(),
                inst.cooldownTicksRemaining()
            ));
        }

        // Queue position (-1 = not in queue)
        int queuePosition = -1;
        if (ownLobby.isPresent()) {
            int idx = queuedLobbyIds.indexOf(ownLobby.get().lobbyId());
            if (idx >= 0) queuePosition = idx + 1; // 1-based
        }

        // Busy players (online only)
        Set<UUID> busyPlayers = new HashSet<>();
        if (level instanceof ServerLevel sl) {
            for (ServerPlayer online : sl.getServer().getPlayerList().getPlayers()) {
                if (BusyStateCompat.isBusy(online.getUUID())) {
                    busyPlayers.add(online.getUUID());
                }
            }
        }

        // Top 10 per tier
        Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards = new EnumMap<>(DifficultyTier.class);
        for (DifficultyTier tier : DifficultyTier.values()) {
            List<LeaderboardEntry> entries = new ArrayList<>(leaderboards.getOrDefault(tier, List.of()));
            entries.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds));
            if (entries.size() > MAX_LEADERBOARD_ENTRIES) entries = entries.subList(0, MAX_LEADERBOARD_ENTRIES);
            topLeaderboards.put(tier, entries);
        }

        long serverTick = level != null ? level.getGameTime() : 0L;

        return new RaidControllerData(
            worldPosition,
            visible,
            ownLobby,
            myInvites,
            myJoinRequests,
            instanceData,
            queuePosition,
            getMaxPartySize(),
            getRespawnTimeTicks(),
            serverTick,
            busyPlayers,
            topLeaderboards
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static ResourceKey<Level> parseDimension(String id) {
        if (id == null || id.isBlank()) return Level.OVERWORLD;
        try {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
        } catch (Exception e) {
            return Level.OVERWORLD;
        }
    }
}
