package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.DungeonPhase;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerData;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreenHandler;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

public class DungeonControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<DungeonControllerData> {
    private static final int DEFAULT_COOLDOWN_TICKS = 5 * 60 * 20;
    /** How long the front queued lobby keeps priority on a free instance before it rotates to the next. */
    private static final int QUEUE_PRIORITY_TIMEOUT_TICKS = 20 * 20;
    private static final int DEFAULT_CLOSE_TIMER_SECONDS = 30;
    private static final int DEFAULT_RESPAWN_TIME_TICKS = 40;
    private static final int DEFAULT_DEATH_TIME_PENALTY_TICKS = 10 * 20;
    private static final int DEFAULT_MAX_PARTY_SIZE = 4;
    private static final int DEFAULT_INVITE_EXPIRY_TICKS = 30 * 20;
    private static final int DEFAULT_DISCONNECT_GRACE_TICKS = 5 * 60 * 20;
    private static final int DEFAULT_LOBBY_OFFLINE_TIMEOUT_TICKS = 5 * 60 * 20;

    private final List<BlockPos> instances = new ArrayList<>();
    /** Dimension each instance's DBS lives in. May differ from this controller's own dimension. */
    private final Map<BlockPos, ResourceKey<Level>> instanceDimensions = new HashMap<>();
    private final Map<DifficultyTier, TierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);
    private int cooldownTicks = DEFAULT_COOLDOWN_TICKS;
    private int closeTimerSeconds = DEFAULT_CLOSE_TIMER_SECONDS;
    private int respawnTimeTicks = DEFAULT_RESPAWN_TIME_TICKS;
    private int deathTimePenaltyTicks = DEFAULT_DEATH_TIME_PENALTY_TICKS;
    private int maxPartySize = DEFAULT_MAX_PARTY_SIZE;
    private int inviteExpiryTicks = DEFAULT_INVITE_EXPIRY_TICKS;
    private int disconnectGraceTicks = DEFAULT_DISCONNECT_GRACE_TICKS;
    private int lobbyOfflineTimeoutTicks = DEFAULT_LOBBY_OFFLINE_TIMEOUT_TICKS;
    private boolean lootViaInbox = false;
    private String dungeonName = "";
    private final Map<BlockPos, Integer> instanceCooldownTimers = new HashMap<>();
    private final Set<BlockPos> pendingInstanceRemovals = new HashSet<>();
    private final Map<DifficultyTier, List<LeaderboardEntry>> leaderboards = new EnumMap<>(DifficultyTier.class);
    private final List<Lobby> lobbies = new ArrayList<>();
    /** Ordered queue of lobby IDs waiting for a free instance. */
    private final List<UUID> queuedLobbyIds = new ArrayList<>();
    /** While an instance is free: which queued lobby currently holds priority, and since when (server tick). Transient. */
    private UUID priorityFrontLobby = null;
    private long priorityFrontSinceTick = -1L;
    /** Whether this controller's own chunk is force-loaded so it keeps ticking unattended. Transient. */
    private transient boolean chunkForceLoaded = false;
    private final List<PendingInvite> pendingInvites = new ArrayList<>();
    private final List<PendingJoinRequest> pendingJoinRequests = new ArrayList<>();
    private final Map<UUID, Long> lobbyOfflineSinceTicks = new HashMap<>();
    private final Map<BlockPos, DungeonRun> activeRuns = new HashMap<>();

    public DungeonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_CONTROLLER_BLOCK_ENTITY, pos, state);
        initializeDefaults();
    }

    private void initializeDefaults() {
        tierConfigs.putIfAbsent(DifficultyTier.EASY, TierConfig.EASY_DEFAULT);
        tierConfigs.putIfAbsent(DifficultyTier.NORMAL, TierConfig.NORMAL_DEFAULT);
        tierConfigs.putIfAbsent(DifficultyTier.HARD, TierConfig.HARD_DEFAULT);
        tierConfigs.putIfAbsent(DifficultyTier.NIGHTMARE, TierConfig.NIGHTMARE_DEFAULT);
        for (DifficultyTier tier : DifficultyTier.values()) {
            leaderboards.putIfAbsent(tier, new ArrayList<>());
        }
    }

    public List<BlockPos> getInstances() {
        return Collections.unmodifiableList(instances);
    }

    public Map<DifficultyTier, TierConfig> getTierConfigs() {
        return Collections.unmodifiableMap(tierConfigs);
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getCloseTimerSeconds() {
        return closeTimerSeconds;
    }

    public int getRespawnTimeTicks() {
        return respawnTimeTicks;
    }

    public int getDeathTimePenaltyTicks() {
        return deathTimePenaltyTicks;
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public int getInviteExpiryTicks() {
        return inviteExpiryTicks;
    }

    public int getDisconnectGraceTicks() {
        return disconnectGraceTicks;
    }

    public int getLobbyOfflineTimeoutTicks() {
        return lobbyOfflineTimeoutTicks;
    }

    public boolean isLootViaInbox() {
        return lootViaInbox;
    }

    public void setLootViaInbox(boolean lootViaInbox) {
        if (this.lootViaInbox == lootViaInbox) {
            return;
        }
        this.lootViaInbox = lootViaInbox;
        setChanged();
    }

    public String getDungeonName() {
        return dungeonName;
    }

    public void setDungeonName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
        }
        if (this.dungeonName.equals(trimmed)) {
            return;
        }
        this.dungeonName = trimmed;
        setChanged();
    }

    public Map<BlockPos, Integer> getInstanceCooldownTimers() {
        return Collections.unmodifiableMap(instanceCooldownTimers);
    }

    public Set<BlockPos> getPendingInstanceRemovals() {
        return Collections.unmodifiableSet(pendingInstanceRemovals);
    }

    public Map<DifficultyTier, List<LeaderboardEntry>> getLeaderboards() {
        Map<DifficultyTier, List<LeaderboardEntry>> copy = new EnumMap<>(DifficultyTier.class);
        for (Map.Entry<DifficultyTier, List<LeaderboardEntry>> entry : leaderboards.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public List<Lobby> getLobbies() {
        return Collections.unmodifiableList(lobbies);
    }

    public List<PendingInvite> getPendingInvites() {
        return Collections.unmodifiableList(pendingInvites);
    }

    public List<PendingJoinRequest> getPendingJoinRequests() {
        return Collections.unmodifiableList(pendingJoinRequests);
    }

    public Map<BlockPos, DungeonRun> getActiveRuns() {
        return Collections.unmodifiableMap(activeRuns);
    }

    public void setTierConfig(DifficultyTier tier, TierConfig config) {
        tierConfigs.put(tier, config);
        setChanged();
    }

    public boolean setCooldownTicks(int cooldownTicks) {
        if (cooldownTicks < 0) return false;
        this.cooldownTicks = cooldownTicks;
        setChanged();
        return true;
    }

    public boolean setCloseTimerSeconds(int closeTimerSeconds) {
        if (closeTimerSeconds <= 0) return false;
        this.closeTimerSeconds = closeTimerSeconds;
        setChanged();
        return true;
    }

    public boolean setRespawnTimeTicks(int ticks) {
        if (ticks < 0) return false;
        this.respawnTimeTicks = ticks;
        setChanged();
        return true;
    }

    public boolean setDeathTimePenaltyTicks(int ticks) {
        if (ticks < 0) return false;
        this.deathTimePenaltyTicks = ticks;
        setChanged();
        return true;
    }

    public boolean setMaxPartySize(int maxPartySize) {
        if (maxPartySize < 1 || maxPartySize > 16) return false;
        this.maxPartySize = maxPartySize;
        setChanged();
        return true;
    }

    public boolean setInviteExpiryTicks(int ticks) {
        if (ticks <= 0) return false;
        this.inviteExpiryTicks = ticks;
        setChanged();
        return true;
    }

    public boolean setDisconnectGraceTicks(int ticks) {
        if (ticks < 0) return false;
        this.disconnectGraceTicks = ticks;
        setChanged();
        return true;
    }

    public boolean setLobbyOfflineTimeoutTicks(int ticks) {
        if (ticks < 0) return false;
        this.lobbyOfflineTimeoutTicks = ticks;
        setChanged();
        return true;
    }

    /** Mark dirty for saving and push a block update so clients (selection overlay) refresh. */
    private void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean addInstance(BlockPos pos, ResourceKey<Level> dimension) {
        if (instances.contains(pos)) return false;
        instances.add(pos);
        instanceDimensions.put(pos, dimension);
        markDirtyAndSync();
        return true;
    }

    /** The dimension the given instance's DBS lives in; falls back to this controller's own dimension. */
    public ResourceKey<Level> getInstanceDimension(BlockPos pos) {
        ResourceKey<Level> dim = instanceDimensions.get(pos);
        if (dim != null) return dim;
        return level instanceof ServerLevel serverLevel ? serverLevel.dimension() : Level.OVERWORLD;
    }

    public boolean removeInstance(BlockPos pos) {
        if (!instances.contains(pos)) return false;
        if (pendingInstanceRemovals.contains(pos)) {
            pendingInstanceRemovals.remove(pos);
            markDirtyAndSync();
            return true;
        }
        if (isInstanceInActiveRun(pos)) {
            pendingInstanceRemovals.add(pos);
            markDirtyAndSync();
            return true;
        }
        boolean removed = instances.remove(pos);
        if (removed) {
            instanceDimensions.remove(pos);
            markDirtyAndSync();
        }
        return removed;
    }

    public boolean moveInstance(int from, int to) {
        if (from < 0 || from >= instances.size() || to < 0 || to >= instances.size()) return false;
        if (from == to) return false;
        BlockPos moved = instances.remove(from);
        instances.add(to, moved);
        markDirtyAndSync();
        return true;
    }

    private boolean isInstanceInActiveRun(BlockPos pos) {
        return activeRuns.containsKey(pos);
    }

    public void startInstanceCooldown(BlockPos pos) {
        instanceCooldownTimers.put(pos, cooldownTicks);
        setChanged();
    }

    void decrementInstanceCooldown(BlockPos pos) {
        Integer current = instanceCooldownTimers.get(pos);
        if (current == null) return;
        if (current <= 1) {
            instanceCooldownTimers.remove(pos);
        } else {
            instanceCooldownTimers.put(pos, current - 1);
        }
        setChanged();
    }

    void clearInstanceCooldown(BlockPos pos) {
        instanceCooldownTimers.remove(pos);
        setChanged();
    }

    /** Sends a system message to every online member of the lobby. */
    public void notifyLobbyMembers(Lobby lobby, net.minecraft.network.chat.Component message) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (UUID memberUuid : lobby.members()) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(memberUuid);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    public void addLeaderboardEntry(DifficultyTier tier, LeaderboardEntry entry) {
        leaderboards.computeIfAbsent(tier, unused -> new ArrayList<>()).add(entry);
        setChanged();
    }

    void clearPendingRemoval(BlockPos pos) {
        pendingInstanceRemovals.remove(pos);
        setChanged();
    }

    public void executePendingRemoval(BlockPos pos) {
        pendingInstanceRemovals.remove(pos);
        instances.remove(pos);
        instanceDimensions.remove(pos);
        setChanged();
    }

    void addLobby(Lobby lobby) {
        lobbyOfflineSinceTicks.remove(lobby.lobbyId());
        lobbies.add(lobby);
        setChanged();
    }

    void replaceLobby(Lobby lobby) {
        for (int i = 0; i < lobbies.size(); i++) {
            if (lobbies.get(i).lobbyId().equals(lobby.lobbyId())) {
                lobbyOfflineSinceTicks.remove(lobby.lobbyId());
                lobbies.set(i, lobby);
                setChanged();
                return;
            }
        }
    }

    void removeLobby(UUID lobbyId) {
        lobbyOfflineSinceTicks.remove(lobbyId);
        boolean removed = lobbies.removeIf(lobby -> lobby.lobbyId().equals(lobbyId));
        queuedLobbyIds.remove(lobbyId);
        removeJoinRequestsForLobby(lobbyId);
        if (removed) {
            setChanged();
        }
    }

    void addInvite(PendingInvite invite) {
        pendingInvites.add(invite);
        setChanged();
    }

    void removeInvite(UUID lobbyId, UUID invitedUuid) {
        boolean removed = pendingInvites.removeIf(invite ->
            invite.lobbyId().equals(lobbyId) && invite.invitedUuid().equals(invitedUuid));
        if (removed) {
            setChanged();
        }
    }

    void addJoinRequest(PendingJoinRequest request) {
        pendingJoinRequests.add(request);
        setChanged();
    }

    void removeJoinRequest(UUID lobbyId, UUID requesterUuid) {
        boolean removed = pendingJoinRequests.removeIf(req ->
            req.lobbyId().equals(lobbyId) && req.requesterUuid().equals(requesterUuid));
        if (removed) {
            setChanged();
        }
    }

    private void removeJoinRequestsForPlayer(UUID requesterUuid) {
        if (pendingJoinRequests.removeIf(req -> req.requesterUuid().equals(requesterUuid))) {
            setChanged();
        }
    }

    private void removeJoinRequestsForLobby(UUID lobbyId) {
        if (pendingJoinRequests.removeIf(req -> req.lobbyId().equals(lobbyId))) {
            setChanged();
        }
    }

    public Optional<UUID> createLobby(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        if (BusyStateCompat.isBusy(playerUuid)) {
            return Optional.empty();
        }
        if (findLobbyByMember(playerUuid).isPresent()) {
            return Optional.empty();
        }

        UUID lobbyId = UUID.randomUUID();
        String playerName = player.getGameProfile().getName();
        Set<UUID> members = new HashSet<>();
        members.add(playerUuid);
        Map<UUID, String> memberNames = new HashMap<>();
        memberNames.put(playerUuid, playerName);
        Lobby lobby = new Lobby(
            lobbyId,
            playerUuid,
            playerName,
            members,
            memberNames,
            Set.of(),
            DifficultyTier.NORMAL,
            false,
            LobbyVisibility.PUBLIC,
            LobbyStatus.FORMING,
            player.serverLevel().getGameTime()
        );
        addLobby(lobby);
        return Optional.of(lobbyId);
    }

    public boolean invitePlayer(ServerPlayer inviter, UUID inviteeUuid) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(inviter.getUUID());
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(inviter.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN) return false;
        if (BusyStateCompat.isBusy(inviteeUuid)) return false;
        if (findLobbyByMember(inviteeUuid).isPresent()) return false;
        if (lobby.isFull(maxPartySize)) return false;

        long now = inviter.serverLevel().getGameTime();
        pendingInvites.removeIf(invite -> invite.expiresAtTick() <= now);
        PendingInvite invite = new PendingInvite(lobby.lobbyId(), inviteeUuid, inviter.getUUID(), now + inviteExpiryTicks, lobby.selectedTier(), lobby.ownerName());
        pendingInvites.removeIf(existing ->
            existing.lobbyId().equals(invite.lobbyId()) && existing.invitedUuid().equals(invite.invitedUuid()));
        addInvite(invite);
        return true;
    }

    public boolean acceptInvite(ServerPlayer invitee, UUID lobbyId) {
        if (BusyStateCompat.isBusy(invitee.getUUID())) return false;
        long now = invitee.serverLevel().getGameTime();
        Optional<PendingInvite> inviteOpt = findInvite(lobbyId, invitee.getUUID());
        if (inviteOpt.isEmpty()) return false;
        PendingInvite invite = inviteOpt.get();
        if (invite.expiresAtTick() < now) {
            removeInvite(lobbyId, invitee.getUUID());
            return false;
        }
        if (findLobbyByMember(invitee.getUUID()).isPresent()) return false;

        Optional<Lobby> lobbyOpt = findLobbyById(lobbyId);
        if (lobbyOpt.isEmpty()) {
            removeInvite(lobbyId, invitee.getUUID());
            return false;
        }
        Lobby lobby = lobbyOpt.get();
        if (lobby.status() == LobbyStatus.IN_RUN) return false;
        if (lobby.isFull(maxPartySize)) return false;

        Lobby updated = lobby.withMemberAdded(invitee.getUUID(), invitee.getGameProfile().getName());
        updated = recomputeLobbyReadiness(updated);
        replaceLobby(updated);
        removeInvite(lobbyId, invitee.getUUID());
        removeJoinRequestsForPlayer(invitee.getUUID());
        return true;
    }

    public boolean declineInvite(ServerPlayer invitee, UUID lobbyId) {
        Optional<PendingInvite> invite = findInvite(lobbyId, invitee.getUUID());
        if (invite.isEmpty()) return false;
        removeInvite(lobbyId, invitee.getUUID());
        return true;
    }

    public boolean requestJoin(ServerPlayer requester, UUID lobbyId) {
        UUID requesterUuid = requester.getUUID();
        if (BusyStateCompat.isBusy(requesterUuid)) return false;
        if (findLobbyByMember(requesterUuid).isPresent()) return false;

        Optional<Lobby> lobbyOpt = findLobbyById(lobbyId);
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (lobby.visibility() != LobbyVisibility.FRIENDS) return false;
        if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) return false;
        if (lobby.isFull(maxPartySize)) return false;

        long now = requester.serverLevel().getGameTime();
        pendingJoinRequests.removeIf(req -> req.expiresAtTick() <= now);
        if (pendingJoinRequests.stream().anyMatch(req ->
            req.lobbyId().equals(lobbyId) && req.requesterUuid().equals(requesterUuid))) {
            return false;
        }

        PendingJoinRequest request = new PendingJoinRequest(
            lobbyId,
            requesterUuid,
            requester.getGameProfile().getName(),
            now + inviteExpiryTicks,
            lobby.selectedTier()
        );
        addJoinRequest(request);
        return true;
    }

    public boolean leaveLobby(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        Optional<Lobby> lobbyOpt = findLobbyByMember(playerUuid);
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (lobby.status() == LobbyStatus.IN_RUN) return false;

        if (lobby.isOwner(playerUuid)) {
            Optional<Lobby> transferred = transferOrDissolve(lobby, playerUuid);
            if (transferred.isPresent()) {
                replaceLobby(recomputeLobbyReadiness(transferred.get()));
            } else {
                removeLobby(lobby.lobbyId());
            }
        } else {
            replaceLobby(recomputeLobbyReadiness(lobby.withMemberRemoved(playerUuid)));
        }
        removeInvitesForPlayer(playerUuid);
        removeJoinRequestsForPlayer(playerUuid);
        return true;
    }

    public boolean kickFromLobby(ServerPlayer kicker, UUID targetUuid) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(kicker.getUUID());
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(kicker.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN) return false;
        if (kicker.getUUID().equals(targetUuid)) return false;
        if (!lobby.isMember(targetUuid)) return false;

        replaceLobby(recomputeLobbyReadiness(lobby.withMemberRemoved(targetUuid)));
        removeInvitesForPlayer(targetUuid);
        removeJoinRequestsForPlayer(targetUuid);
        return true;
    }

    public boolean setLobbyTier(ServerPlayer player, DifficultyTier tier) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(player.getUUID());
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(player.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN) return false;
        if (!tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier)).enabled()) return false;
        replaceLobby(lobby.withTier(tier));
        return true;
    }

    public boolean setLobbyHardcore(ServerPlayer player, boolean hardcore) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(player.getUUID());
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(player.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN) return false;
        replaceLobby(lobby.withHardcore(hardcore));
        return true;
    }

    public boolean setLobbyVisibility(ServerPlayer player, LobbyVisibility v) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(player.getUUID());
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(player.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN) return false;
        replaceLobby(lobby.withVisibility(v));
        return true;
    }

    public boolean toggleReady(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        Optional<Lobby> lobbyOpt = findLobbyByMember(playerUuid);
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (lobby.status() == LobbyStatus.IN_RUN) return false;

        Lobby updated = lobby.readyMembers().contains(playerUuid)
            ? lobby.withUnready(playerUuid)
            : lobby.withReady(playerUuid);
        replaceLobby(recomputeLobbyReadiness(updated));
        return true;
    }

    public Optional<BlockPos> startRun(ServerPlayer player) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(player.getUUID());
        if (lobbyOpt.isEmpty()) return Optional.empty();
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(player.getUUID())) return Optional.empty();
        if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) return Optional.empty();
        if (lobby.members().size() > maxPartySize) return Optional.empty();
        if (!lobby.allReady()) return Optional.empty();
        if (!allMembersOnline(player, lobby)) return Optional.empty();

        Optional<BlockPos> available = firstAvailableInstance();
        boolean isFront = queuedLobbyIds.isEmpty() || queuedLobbyIds.get(0).equals(lobby.lobbyId());

        // No free instance, or other lobbies are ahead in line → queue this lobby and report.
        if (available.isEmpty() || !isFront) {
            if (!queuedLobbyIds.contains(lobby.lobbyId())) {
                queuedLobbyIds.add(lobby.lobbyId());
            }
            int position = queuedLobbyIds.indexOf(lobby.lobbyId()) + 1;
            if (available.isEmpty()) {
                notifyLobbyMembers(lobby, Component.translatable("message.arenas_ld.lobby.queued",
                    position, formatDuration(getEstimatedWaitSeconds())));
            } else {
                notifyLobbyMembers(lobby, Component.translatable("message.arenas_ld.lobby.queue_position", position));
            }
            setChanged();
            return Optional.empty();
        }

        queuedLobbyIds.remove(lobby.lobbyId());
        removeLobby(lobby.lobbyId());
        return available;
    }

    public List<UUID> getQueuedLobbyIds() {
        return Collections.unmodifiableList(queuedLobbyIds);
    }

    private boolean hasFreeInstance() {
        return firstAvailableInstance().isPresent();
    }

    /** Estimated ticks until the soonest instance becomes free (run remaining + cooldown, or current cooldown). */
    public int estimateWaitTicks() {
        int min = Integer.MAX_VALUE;
        for (BlockPos pos : instances) {
            if (pendingInstanceRemovals.contains(pos)) continue;
            int untilFree;
            DungeonRun run = activeRuns.get(pos);
            Integer cooldown = instanceCooldownTimers.get(pos);
            if (run != null) {
                int remaining = run.phase() == DungeonPhase.CLOSING ? run.closeTimerTicks() : run.dungeonTimerTicks();
                untilFree = Math.max(0, remaining) + cooldownTicks;
            } else if (cooldown != null && cooldown > 0) {
                untilFree = cooldown;
            } else {
                untilFree = 0;
            }
            min = Math.min(min, untilFree);
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    public int getEstimatedWaitSeconds() {
        return estimateWaitTicks() / 20;
    }

    /** 1-based queue position for the given player's lobby, or 0 if not queued. */
    public int getQueuePosition(UUID playerUuid) {
        Optional<Lobby> lobby = findLobbyByMember(playerUuid);
        if (lobby.isEmpty()) return 0;
        int idx = queuedLobbyIds.indexOf(lobby.get().lobbyId());
        return idx < 0 ? 0 : idx + 1;
    }

    /** Notifies the front queued lobby when an instance frees up (does not auto-start). */
    private void notifyFrontOfQueue() {
        if (queuedLobbyIds.isEmpty() || !hasFreeInstance() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Drop stale heads (lobby disbanded/removed) before notifying.
        while (!queuedLobbyIds.isEmpty() && findLobbyById(queuedLobbyIds.get(0)).isEmpty()) {
            queuedLobbyIds.remove(0);
        }
        if (queuedLobbyIds.isEmpty()) {
            return;
        }
        Lobby front = findLobbyById(queuedLobbyIds.get(0)).orElse(null);
        if (front == null) {
            return;
        }
        Component start = net.ledok.arenas_ld.util.LobbyChatActions.button(
            Component.translatable("message.arenas_ld.lobby.button.start"), 0x86D36C,
            net.ledok.arenas_ld.util.LobbyChatActions.startCommand(serverLevel, worldPosition),
            Component.translatable("message.arenas_ld.lobby.button.start.hover"));
        notifyLobbyMembers(front, Component.translatable("message.arenas_ld.lobby.instance_free").append(" ").append(start));
    }

    /**
     * Per-tick queue priority management. While an instance is free, the front lobby has
     * {@link #QUEUE_PRIORITY_TIMEOUT_TICKS} to start; if it doesn't, priority rotates to the next
     * lobby (the laggard goes to the back). Notifications fire on each hand-off.
     */
    private void tickQueuePriority(ServerLevel serverLevel) {
        // Drop stale heads (disbanded/removed lobbies).
        while (!queuedLobbyIds.isEmpty() && findLobbyById(queuedLobbyIds.get(0)).isEmpty()) {
            queuedLobbyIds.remove(0);
            setChanged();
        }
        if (queuedLobbyIds.isEmpty() || !hasFreeInstance()) {
            priorityFrontLobby = null;
            priorityFrontSinceTick = -1L;
            return;
        }

        long now = serverLevel.getGameTime();
        UUID front = queuedLobbyIds.get(0);

        // A new lobby just gained priority (instance freed, or the previous front rotated/launched).
        if (!front.equals(priorityFrontLobby)) {
            priorityFrontLobby = front;
            priorityFrontSinceTick = now;
            notifyFrontOfQueue();
            return;
        }

        if (now - priorityFrontSinceTick < QUEUE_PRIORITY_TIMEOUT_TICKS) {
            return;
        }

        // Timed out without starting.
        if (queuedLobbyIds.size() > 1) {
            UUID demoted = queuedLobbyIds.remove(0);
            queuedLobbyIds.add(demoted);
            findLobbyById(demoted).ifPresent(lobby ->
                notifyLobbyMembers(lobby, Component.translatable("message.arenas_ld.lobby.priority_lost").withStyle(net.minecraft.ChatFormatting.YELLOW)));
            priorityFrontLobby = null; // next tick promotes + notifies the new front
            priorityFrontSinceTick = -1L;
            setChanged();
            broadcastPlayerSnapshots();
        } else {
            // Only one lobby waiting — just remind it again.
            priorityFrontSinceTick = now;
            notifyFrontOfQueue();
        }
    }

    /** Pushes the player-facing controller snapshot to all online players (live queue/lobby UI). */
    private void broadcastPlayerSnapshots() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer() == null) {
            return;
        }
        for (ServerPlayer target : serverLevel.getServer().getPlayerList().getPlayers()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(target,
                new net.ledok.arenas_ld.dungeon.packet.DungeonControllerSnapshotPayload(getScreenOpeningData(target)));
        }
    }

    private static String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) {
            return "soon";
        }
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes > 0 ? (minutes + "m " + seconds + "s") : (seconds + "s");
    }

    public boolean joinLobby(ServerPlayer player, UUID lobbyId) {
        if (BusyStateCompat.isBusy(player.getUUID())) return false;
        if (findLobbyByMember(player.getUUID()).isPresent()) return false;
        Optional<Lobby> lobbyOpt = findLobbyById(lobbyId);
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (lobby.visibility() != LobbyVisibility.PUBLIC) return false;
        if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) return false;
        if (lobby.isFull(maxPartySize)) return false;

        Lobby updated = recomputeLobbyReadiness(lobby.withMemberAdded(player.getUUID(), player.getGameProfile().getName()));
        replaceLobby(updated);
        removeJoinRequestsForPlayer(player.getUUID());
        return true;
    }

    public boolean acceptJoinRequest(ServerPlayer owner, UUID requesterUuid) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(owner.getUUID());
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(owner.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) return false;
        if (lobby.isFull(maxPartySize)) return false;

        Optional<PendingJoinRequest> requestOpt = findJoinRequest(lobby.lobbyId(), requesterUuid);
        if (requestOpt.isEmpty()) return false;
        PendingJoinRequest request = requestOpt.get();
        long now = owner.serverLevel().getGameTime();
        if (request.expiresAtTick() < now) {
            removeJoinRequest(lobby.lobbyId(), requesterUuid);
            return false;
        }
        if (findLobbyByMember(requesterUuid).isPresent()) {
            removeJoinRequest(lobby.lobbyId(), requesterUuid);
            return false;
        }
        if (BusyStateCompat.isBusy(requesterUuid)) {
            removeJoinRequest(lobby.lobbyId(), requesterUuid);
            return false;
        }

        Lobby updated = lobby.withMemberAdded(requesterUuid, request.requesterName());
        updated = recomputeLobbyReadiness(updated);
        replaceLobby(updated);
        removeJoinRequestsForPlayer(requesterUuid);
        return true;
    }

    public boolean declineJoinRequest(ServerPlayer owner, UUID requesterUuid) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(owner.getUUID());
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(owner.getUUID())) return false;
        Optional<PendingJoinRequest> req = findJoinRequest(lobby.lobbyId(), requesterUuid);
        if (req.isEmpty()) return false;
        removeJoinRequest(lobby.lobbyId(), requesterUuid);
        return true;
    }

    private boolean allMembersOnline(ServerPlayer sourcePlayer, Lobby lobby) {
        for (UUID memberUuid : lobby.members()) {
            if (sourcePlayer.serverLevel().getServer().getPlayerList().getPlayer(memberUuid) == null) {
                return false;
            }
        }
        return true;
    }

    private Optional<BlockPos> firstAvailableInstance() {
        for (BlockPos pos : instances) {
            if (pendingInstanceRemovals.contains(pos)) continue;
            if (instanceCooldownTimers.getOrDefault(pos, 0) > 0) continue;
            if (isInstanceInActiveRun(pos)) continue;
            return Optional.of(pos);
        }
        return Optional.empty();
    }

    private Lobby recomputeLobbyReadiness(Lobby lobby) {
        if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) {
            return lobby;
        }
        return lobby.withStatus(lobby.allReady() ? LobbyStatus.READY : LobbyStatus.FORMING);
    }

    private Optional<Lobby> transferOrDissolve(Lobby lobby, UUID leavingOwner) {
        Lobby withoutOwner = lobby.withMemberRemoved(leavingOwner);
        if (withoutOwner.members().isEmpty()) {
            return Optional.empty();
        }
        UUID newOwner = withoutOwner.members().stream()
            .sorted((a, b) -> a.toString().compareTo(b.toString()))
            .findFirst()
            .orElse(withoutOwner.ownerUuid());
        return Optional.of(withoutOwner.withOwner(newOwner));
    }

    private Optional<Lobby> findLobbyById(UUID lobbyId) {
        return lobbies.stream().filter(lobby -> lobby.lobbyId().equals(lobbyId)).findFirst();
    }

    private Optional<Lobby> findLobbyByMember(UUID playerUuid) {
        return lobbies.stream().filter(lobby -> lobby.isMember(playerUuid)).findFirst();
    }

    private Optional<PendingInvite> findInvite(UUID lobbyId, UUID invitedUuid) {
        return pendingInvites.stream()
            .filter(invite -> invite.lobbyId().equals(lobbyId) && invite.invitedUuid().equals(invitedUuid))
            .findFirst();
    }

    private Optional<PendingJoinRequest> findJoinRequest(UUID lobbyId, UUID requesterUuid) {
        return pendingJoinRequests.stream()
            .filter(req -> req.lobbyId().equals(lobbyId) && req.requesterUuid().equals(requesterUuid))
            .findFirst();
    }

    private void removeInvitesForPlayer(UUID invitedUuid) {
        List<PendingInvite> toRemove = pendingInvites.stream()
            .filter(invite -> invite.invitedUuid().equals(invitedUuid))
            .toList();
        for (PendingInvite invite : toRemove) {
            removeInvite(invite.lobbyId(), invite.invitedUuid());
        }
    }

    private boolean isLobbyVisibleTo(Lobby lobby, UUID playerUuid) {
        return switch (lobby.visibility()) {
            case PUBLIC -> true;
            // TODO: replace with real friend checks when/if friend system exists.
            case FRIENDS -> true;
            case PRIVATE -> lobby.isMember(playerUuid);
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.arenas_ld.dungeon_controller");
    }

    @Override
    public DungeonControllerData getScreenOpeningData(ServerPlayer player) {
        UUID playerUuid = player.getUUID();
        List<Lobby> visible = lobbies.stream()
            .filter(lobby -> lobby.status() != LobbyStatus.DISBANDED)
            .filter(lobby -> isLobbyVisibleTo(lobby, playerUuid))
            .toList();
        Optional<Lobby> own = lobbies.stream()
            .filter(lobby -> lobby.isMember(playerUuid))
            .findFirst();
        List<PendingInvite> myInvites = pendingInvites.stream()
            .filter(invite -> invite.invitedUuid().equals(playerUuid))
            .toList();
        Optional<Lobby> ownedLobby = lobbies.stream()
            .filter(lobby -> lobby.isOwner(playerUuid))
            .findFirst();
        List<PendingJoinRequest> myJoinRequests = pendingJoinRequests.stream()
            .filter(req -> req.requesterUuid().equals(playerUuid)
                || (ownedLobby.isPresent() && req.lobbyId().equals(ownedLobby.get().lobbyId())))
            .toList();
        Set<UUID> busyPlayers = new HashSet<>();
        if (player.getServer() != null) {
            for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
                if (BusyStateCompat.isBusy(online.getUUID())) {
                    busyPlayers.add(online.getUUID());
                }
            }
        }
        Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards = new EnumMap<>(DifficultyTier.class);
        for (DifficultyTier tier : DifficultyTier.values()) {
            List<LeaderboardEntry> top = leaderboards.getOrDefault(tier, List.of()).stream()
                .sorted(java.util.Comparator.comparingInt(LeaderboardEntry::timeSeconds))
                .limit(10)
                .toList();
            topLeaderboards.put(tier, top);
        }
        return new DungeonControllerData(worldPosition, visible, own, myInvites, myJoinRequests, maxPartySize, tierConfigs, player.serverLevel().getGameTime(), busyPlayers, topLeaderboards, getQueuePosition(playerUuid), getEstimatedWaitSeconds());
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new DungeonControllerScreenHandler(syncId, playerInventory, this);
    }

    public void startRun(BlockPos dbsPos, DungeonRun run) {
        activeRuns.put(dbsPos, run);
        setChanged();
    }

    public void removeRun(BlockPos dbsPos) {
        activeRuns.remove(dbsPos);
        setChanged();
    }

    public static void tick(Level world, BlockPos pos, BlockState state, DungeonControllerBlockEntity be) {
        if (world.isClientSide || !(world instanceof ServerLevel serverLevel)) {
            return;
        }

        be.ensureChunkForceLoaded(serverLevel);

        Iterator<Map.Entry<BlockPos, Integer>> cdIter = be.instanceCooldownTimers.entrySet().iterator();
        while (cdIter.hasNext()) {
            Map.Entry<BlockPos, Integer> entry = cdIter.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                cdIter.remove();
                be.setChanged();
            } else {
                entry.setValue(remaining);
                be.setChanged();
            }
        }
        be.tickQueuePriority(serverLevel);

        long currentTick = serverLevel.getGameTime();
        if (be.pendingInvites.removeIf(invite -> invite.expiresAtTick() <= currentTick)) {
            be.setChanged();
        }
        if (be.pendingJoinRequests.removeIf(req -> req.expiresAtTick() <= currentTick)) {
            be.setChanged();
        }

        be.tickLobbyTimeouts(serverLevel, currentTick);

        for (DungeonRun run : new ArrayList<>(be.activeRuns.values())) {
            // The run's DBS may live in a different dimension than this controller; tick it there.
            ServerLevel runLevel = serverLevel.getServer().getLevel(run.dbsDimension());
            DungeonRunLifecycle.tick(runLevel != null ? runLevel : serverLevel, be, run);
        }
    }

    /**
     * Force-load this controller's own chunk (once per session) so its block-entity ticker keeps
     * driving active runs even when the whole party has teleported into a far-away instance and the
     * controller chunk would otherwise unload — which previously froze the run (no spawning, no timer,
     * no death routing) until a server restart.
     */
    private void ensureChunkForceLoaded(ServerLevel serverLevel) {
        if (chunkForceLoaded) return;
        ChunkPos chunkPos = new ChunkPos(worldPosition);
        serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
        chunkForceLoaded = true;
    }

    private void tickLobbyTimeouts(ServerLevel serverLevel, long currentTick) {
        boolean changed = false;
        Iterator<Lobby> iterator = lobbies.iterator();
        while (iterator.hasNext()) {
            Lobby lobby = iterator.next();
            if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) {
                if (lobbyOfflineSinceTicks.remove(lobby.lobbyId()) != null) {
                    changed = true;
                }
                continue;
            }

            boolean anyOnline = false;
            for (UUID memberUuid : lobby.members()) {
                if (serverLevel.getServer().getPlayerList().getPlayer(memberUuid) != null) {
                    anyOnline = true;
                    break;
                }
            }

            if (anyOnline) {
                if (lobbyOfflineSinceTicks.remove(lobby.lobbyId()) != null) {
                    changed = true;
                }
                continue;
            }

            long offlineSince = lobbyOfflineSinceTicks.computeIfAbsent(lobby.lobbyId(), ignored -> currentTick);
            if (lobbyOfflineTimeoutTicks == 0 || currentTick - offlineSince >= lobbyOfflineTimeoutTicks) {
                iterator.remove();
                lobbyOfflineSinceTicks.remove(lobby.lobbyId());
                pendingInvites.removeIf(invite -> invite.lobbyId().equals(lobby.lobbyId()));
                pendingJoinRequests.removeIf(req -> req.lobbyId().equals(lobby.lobbyId()));
                changed = true;
            }
        }

        if (changed) {
            setChanged();
        }
    }

    private record InstanceCooldown(BlockPos pos, int ticks) {
        static final Codec<InstanceCooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(InstanceCooldown::pos),
            Codec.INT.fieldOf("ticks").forGetter(InstanceCooldown::ticks)
        ).apply(i, InstanceCooldown::new));
    }

    /**
     * One registered instance: its DBS position and the dimension that DBS lives in (which may differ
     * from the controller's).
     */
    private record InstanceEntry(BlockPos pos, ResourceKey<Level> dimension) {
        static final Codec<InstanceEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(InstanceEntry::pos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(InstanceEntry::dimension)
        ).apply(i, InstanceEntry::new));
    }

    private record InstanceRunEntry(BlockPos pos, DungeonRun run) {
        static final Codec<InstanceRunEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(InstanceRunEntry::pos),
            DungeonRun.CODEC.fieldOf("run").forGetter(InstanceRunEntry::run)
        ).apply(i, InstanceRunEntry::new));
    }

    /** Bundle of int timing settings — kept together so the State codec stays under DFU's 16-field group() limit. */
    private record LifecycleTimings(
        int disconnectGraceTicks,
        int lobbyOfflineTimeoutTicks,
        int respawnTimeTicks,
        int deathTimePenaltyTicks
    ) {
        static final Codec<LifecycleTimings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("disconnectGraceTicks", DEFAULT_DISCONNECT_GRACE_TICKS).forGetter(LifecycleTimings::disconnectGraceTicks),
            Codec.INT.optionalFieldOf("lobbyOfflineTimeoutTicks", DEFAULT_LOBBY_OFFLINE_TIMEOUT_TICKS).forGetter(LifecycleTimings::lobbyOfflineTimeoutTicks),
            Codec.INT.optionalFieldOf("respawnTimeTicks", DEFAULT_RESPAWN_TIME_TICKS).forGetter(LifecycleTimings::respawnTimeTicks),
            Codec.INT.optionalFieldOf("deathTimePenaltyTicks", DEFAULT_DEATH_TIME_PENALTY_TICKS).forGetter(LifecycleTimings::deathTimePenaltyTicks)
        ).apply(i, LifecycleTimings::new));

        static final LifecycleTimings DEFAULT = new LifecycleTimings(
            DEFAULT_DISCONNECT_GRACE_TICKS, DEFAULT_LOBBY_OFFLINE_TIMEOUT_TICKS,
            DEFAULT_RESPAWN_TIME_TICKS, DEFAULT_DEATH_TIME_PENALTY_TICKS
        );
    }

    private record State(
        List<InstanceEntry> instances,
        Map<DifficultyTier, TierConfig> tierConfigs,
        int cooldownTicks,
        int closeTimerSeconds,
        int maxPartySize,
        int inviteExpiryTicks,
        LifecycleTimings lifecycle,
        List<InstanceCooldown> instanceCooldowns,
        List<InstanceRunEntry> activeRuns,
        List<BlockPos> pendingInstanceRemovals,
        Map<DifficultyTier, List<LeaderboardEntry>> leaderboards,
        List<Lobby> lobbies,
        List<PendingInvite> pendingInvites,
        Map<UUID, Long> lobbyOfflineSinceTicks,
        List<PendingJoinRequest> pendingJoinRequests,
        List<UUID> queuedLobbyIds
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            InstanceEntry.CODEC.listOf().fieldOf("instances").forGetter(State::instances),
            Codec.unboundedMap(DifficultyTier.CODEC, TierConfig.CODEC).fieldOf("tierConfigs").forGetter(State::tierConfigs),
            Codec.INT.fieldOf("cooldownTicks").forGetter(State::cooldownTicks),
            Codec.INT.fieldOf("closeTimerSeconds").forGetter(State::closeTimerSeconds),
            Codec.INT.fieldOf("maxPartySize").forGetter(State::maxPartySize),
            Codec.INT.fieldOf("inviteExpiryTicks").forGetter(State::inviteExpiryTicks),
            LifecycleTimings.CODEC.optionalFieldOf("lifecycle", LifecycleTimings.DEFAULT).forGetter(State::lifecycle),
            InstanceCooldown.CODEC.listOf().fieldOf("instanceCooldowns").forGetter(State::instanceCooldowns),
            InstanceRunEntry.CODEC.listOf().fieldOf("activeRuns").forGetter(State::activeRuns),
            BlockPos.CODEC.listOf().fieldOf("pendingInstanceRemovals").forGetter(State::pendingInstanceRemovals),
            Codec.unboundedMap(DifficultyTier.CODEC, LeaderboardEntry.CODEC.listOf()).fieldOf("leaderboards").forGetter(State::leaderboards),
            Lobby.CODEC.listOf().fieldOf("lobbies").forGetter(State::lobbies),
            PendingInvite.CODEC.listOf().fieldOf("pendingInvites").forGetter(State::pendingInvites),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                .optionalFieldOf("lobbyOfflineSinceTicks", Map.of()).forGetter(State::lobbyOfflineSinceTicks),
            PendingJoinRequest.CODEC.listOf().optionalFieldOf("pendingJoinRequests", List.of()).forGetter(State::pendingJoinRequests),
            UUIDUtil.CODEC.listOf().optionalFieldOf("queuedLobbyIds", List.of()).forGetter(State::queuedLobbyIds)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        List<InstanceCooldown> cooldowns = instanceCooldownTimers.entrySet().stream()
            .map(e -> new InstanceCooldown(e.getKey(), e.getValue()))
            .toList();
        List<InstanceRunEntry> runs = activeRuns.entrySet().stream()
            .map(e -> new InstanceRunEntry(e.getKey(), e.getValue()))
            .toList();
        List<InstanceEntry> instanceEntries = instances.stream()
            .map(pos -> new InstanceEntry(pos, getInstanceDimension(pos)))
            .toList();
        State state = new State(
            instanceEntries,
            tierConfigs,
            cooldownTicks,
            closeTimerSeconds,
            maxPartySize,
            inviteExpiryTicks,
            new LifecycleTimings(disconnectGraceTicks, lobbyOfflineTimeoutTicks, respawnTimeTicks, deathTimePenaltyTicks),
            cooldowns,
            runs,
            new ArrayList<>(pendingInstanceRemovals),
            leaderboards,
            lobbies,
            pendingInvites,
            lobbyOfflineSinceTicks,
            pendingJoinRequests,
            new ArrayList<>(queuedLobbyIds)
        );
        State.CODEC.encodeStart(NbtOps.INSTANCE, state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save DungeonController at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
        nbt.putBoolean("LootViaInbox", lootViaInbox);
        nbt.putString("DungeonName", dungeonName);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load DungeonController at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    instances.clear();
                    instanceDimensions.clear();
                    for (InstanceEntry entry : state.instances()) {
                        instances.add(entry.pos());
                        instanceDimensions.put(entry.pos(), entry.dimension());
                    }
                    tierConfigs.clear();
                    tierConfigs.putAll(state.tierConfigs());
                    cooldownTicks = state.cooldownTicks();
                    closeTimerSeconds = state.closeTimerSeconds();
                    maxPartySize = state.maxPartySize();
                    inviteExpiryTicks = state.inviteExpiryTicks();
                    disconnectGraceTicks = state.lifecycle().disconnectGraceTicks();
                    lobbyOfflineTimeoutTicks = state.lifecycle().lobbyOfflineTimeoutTicks();
                    respawnTimeTicks = state.lifecycle().respawnTimeTicks();
                    deathTimePenaltyTicks = state.lifecycle().deathTimePenaltyTicks();
                    instanceCooldownTimers.clear();
                    for (InstanceCooldown c : state.instanceCooldowns()) {
                        instanceCooldownTimers.put(c.pos(), c.ticks());
                    }
                    activeRuns.clear();
                    for (InstanceRunEntry runEntry : state.activeRuns()) {
                        activeRuns.put(runEntry.pos(), runEntry.run());
                        // The manager's player->run map is in-memory only and is rebuilt from
                        // scratch each boot. Without this, after a server restart getRunForPlayer()
                        // returns null for everyone still in a run, so reconnect can't restore or
                        // eject them and they end up stranded inside the dungeon.
                        for (RunParticipant participant : runEntry.run().participants().values()) {
                            if (participant.isEligibleForLoot()) {
                                ArenasLdMod.DUNGEON_MANAGER.registerParticipant(
                                    participant.playerUuid(), runEntry.run());
                            }
                        }
                    }
                    pendingInstanceRemovals.clear();
                    pendingInstanceRemovals.addAll(state.pendingInstanceRemovals());
                    leaderboards.clear();
                    for (Map.Entry<DifficultyTier, List<LeaderboardEntry>> entry : state.leaderboards().entrySet()) {
                        leaderboards.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                    }
                    lobbies.clear();
                    lobbies.addAll(state.lobbies());
                    pendingInvites.clear();
                    pendingInvites.addAll(state.pendingInvites());
                    pendingJoinRequests.clear();
                    pendingJoinRequests.addAll(state.pendingJoinRequests());
                    lobbyOfflineSinceTicks.clear();
                    lobbyOfflineSinceTicks.putAll(state.lobbyOfflineSinceTicks());
                    queuedLobbyIds.clear();
                    queuedLobbyIds.addAll(state.queuedLobbyIds());
                    initializeDefaults();
                });
        } else {
            initializeDefaults();
        }
        lootViaInbox = nbt.getBoolean("LootViaInbox");
        dungeonName = nbt.getString("DungeonName");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        ArenasLdMod.DUNGEON_MANAGER.registerController(this);
    }

    @Override
    public void setRemoved() {
        if (chunkForceLoaded && level instanceof ServerLevel serverLevel) {
            ChunkPos chunkPos = new ChunkPos(worldPosition);
            serverLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
            chunkForceLoaded = false;
        }
        super.setRemoved();
        ArenasLdMod.DUNGEON_MANAGER.unregisterController(this);
    }
}
