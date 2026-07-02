package net.ledok.arenas_ld.arena.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.run.ArenaRun;
import net.ledok.arenas_ld.arena.run.ArenaRunLifecycle;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.InstanceStatus;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for the wave-survival arena. Mirrors
 * {@link net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity}: owns the lobby model,
 * the pool of arena instances, the launch queue, and the in-progress {@link ArenaRun}s it drives
 * through {@link ArenaRunLifecycle}. Unlike the raid/dungeon controllers there are no difficulty
 * tiers — difficulty comes from per-wave scaling. Adds a {@code maxWave} ceiling and a configurable
 * non-linear reward curve used by the end-of-run summary.
 */
public class ArenaControllerBlockEntity extends BlockEntity
    implements net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.ledok.arenas_ld.arena.screen.ArenaControllerMenuData> {

    public record ControllerKey(BlockPos pos, ResourceKey<Level> dimension) {}

    public record ArenaInstanceState(BlockPos spawnerPos, ResourceKey<Level> dimension,
                                     InstanceStatus status, int cooldownTicksRemaining) {
        public ArenaInstanceState withStatus(InstanceStatus s, int cd) {
            return new ArenaInstanceState(spawnerPos, dimension, s, cd);
        }
    }

    private static final String BUSY_REASON = "arenas_ld:arena";
    private static final int DEFAULT_RESPAWN_TIME_TICKS = 6000;
    private static final int DEFAULT_MAX_PARTY_SIZE = 10;
    private static final int DEFAULT_INVITE_EXPIRY_TICKS = 30 * 20;
    private static final int DEFAULT_COOLDOWN_TICKS = 5 * 60 * 20;
    private static final int DEFAULT_CLOSE_TIMER_SECONDS = 30;
    private static final int DEFAULT_DEATH_TIME_PENALTY_TICKS = 10 * 20;
    private static final int DEFAULT_DISCONNECT_GRACE_TICKS = 5 * 60 * 20;
    private static final int DEFAULT_LOBBY_OFFLINE_TIMEOUT_TICKS = 5 * 60 * 20;
    private static final int DEFAULT_MAX_WAVE = -1;
    private static final double DEFAULT_CURRENCY_BASE = 1.0;
    private static final double DEFAULT_CURRENCY_EXP = 1.5;
    private static final double DEFAULT_XP_BASE = 1.0;
    private static final double DEFAULT_XP_EXP = 1.5;
    private static final int MAX_LEADERBOARD_ENTRIES = 10;
    /** Fraction of base HP added per player beyond the first (0 = disabled). Was a hardcoded 0.10. */
    private static final double DEFAULT_HP_SCALE_PER_PLAYER = 0.10;
    /** Fraction of (party-scaled) base HP added per wave beyond the first (0 = disabled). */
    private static final double DEFAULT_HP_SCALE_PER_WAVE = 0.0;

    private static final Set<ControllerKey> CONTROLLERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static Set<ControllerKey> getControllers() {
        return CONTROLLERS;
    }

    // ── State ──────────────────────────────────────────────────────────────
    private final List<ArenaInstanceState> instances = new ArrayList<>();
    private final List<Lobby> lobbies = new ArrayList<>();
    private final List<UUID> queuedLobbyIds = new ArrayList<>();
    private final List<PendingInvite> pendingInvites = new ArrayList<>();
    private final List<PendingJoinRequest> pendingJoinRequests = new ArrayList<>();
    private final Map<BlockPos, ArenaRun> activeRuns = new HashMap<>();
    private final Map<UUID, Long> offlineSinceTick = new HashMap<>();
    /** Single leaderboard keyed by deepest wave reached (higher is better). */
    private final List<LeaderboardEntry> leaderboard = new ArrayList<>();
    private final Set<BlockPos> pendingInstanceRemovals = new HashSet<>();

    private int respawnTimeTicks = DEFAULT_RESPAWN_TIME_TICKS;
    private int maxPartySize = DEFAULT_MAX_PARTY_SIZE;
    private int inviteExpiryTicks = DEFAULT_INVITE_EXPIRY_TICKS;
    private int cooldownTicks = DEFAULT_COOLDOWN_TICKS;
    private int closeTimerSeconds = DEFAULT_CLOSE_TIMER_SECONDS;
    private int deathTimePenaltyTicks = DEFAULT_DEATH_TIME_PENALTY_TICKS;
    private int disconnectGraceTicks = DEFAULT_DISCONNECT_GRACE_TICKS;
    private int lobbyOfflineTimeoutTicks = DEFAULT_LOBBY_OFFLINE_TIMEOUT_TICKS;
    private boolean lootViaInbox = false;
    private int maxWave = DEFAULT_MAX_WAVE;
    private double rewardCurrencyBase = DEFAULT_CURRENCY_BASE;
    private double rewardCurrencyExp = DEFAULT_CURRENCY_EXP;
    private double rewardXpBase = DEFAULT_XP_BASE;
    private double rewardXpExp = DEFAULT_XP_EXP;
    private double hpScalePerPlayer = DEFAULT_HP_SCALE_PER_PLAYER;
    private double hpScalePerWave = DEFAULT_HP_SCALE_PER_WAVE;

    private transient boolean chunkForceLoaded = false;

    public ArenaControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.ARENA_CONTROLLER_BLOCK_ENTITY, pos, state);
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
        if (level instanceof ServerLevel serverLevel) {
            CONTROLLERS.remove(new ControllerKey(worldPosition, serverLevel.dimension()));
            if (chunkForceLoaded) {
                ChunkPos chunkPos = new ChunkPos(worldPosition);
                serverLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
                chunkForceLoaded = false;
            }
        }
        super.setRemoved();
    }

    // ── Tick ──────────────────────────────────────────────────────────────
    public static void tick(Level level, BlockPos pos, BlockState state, ArenaControllerBlockEntity be) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) return;
        be.ensureChunkForceLoaded(serverLevel);
        be.tickCooldowns(serverLevel);
        be.tickLobbies(serverLevel);
        be.tickActiveRuns(serverLevel);
    }

    private void ensureChunkForceLoaded(ServerLevel serverLevel) {
        if (chunkForceLoaded) return;
        ChunkPos chunkPos = new ChunkPos(worldPosition);
        serverLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
        chunkForceLoaded = true;
    }

    private void tickActiveRuns(ServerLevel controllerLevel) {
        if (activeRuns.isEmpty()) return;
        MinecraftServer server = controllerLevel.getServer();
        for (ArenaRun run : new ArrayList<>(activeRuns.values())) {
            ServerLevel spawnerLevel = server.getLevel(run.spawnerDimension());
            if (spawnerLevel == null) continue;
            ArenaRunLifecycle.tick(spawnerLevel, this, run);
        }
    }

    // ── Accessors ──────────────────────────────────────────────────────────
    public List<ArenaInstanceState> getInstances() { return Collections.unmodifiableList(instances); }
    public List<Lobby> getLobbies() { return Collections.unmodifiableList(lobbies); }
    public List<UUID> getQueuedLobbyIds() { return Collections.unmodifiableList(queuedLobbyIds); }
    public List<PendingInvite> getPendingInvites() { return Collections.unmodifiableList(pendingInvites); }
    public List<PendingJoinRequest> getPendingJoinRequests() { return Collections.unmodifiableList(pendingJoinRequests); }
    public Map<BlockPos, ArenaRun> getActiveRuns() { return Collections.unmodifiableMap(activeRuns); }
    public Set<BlockPos> getPendingInstanceRemovals() { return Collections.unmodifiableSet(pendingInstanceRemovals); }
    public List<LeaderboardEntry> getLeaderboard() { return Collections.unmodifiableList(leaderboard); }

    public int getRespawnTimeTicks() { return Math.max(0, respawnTimeTicks); }
    public void setRespawnTimeTicks(int t) { this.respawnTimeTicks = Math.max(0, t); markDirtyAndSync(); }
    public int getMaxPartySize() { return Math.max(1, Math.min(20, maxPartySize)); }
    public void setMaxPartySize(int s) { this.maxPartySize = Math.max(1, Math.min(20, s)); markDirtyAndSync(); }
    public int getInviteExpiryTicks() { return inviteExpiryTicks; }
    public boolean setInviteExpiryTicks(int t) { if (t <= 0) return false; inviteExpiryTicks = t; markDirtyAndSync(); return true; }
    public int getCooldownTicks() { return cooldownTicks; }
    public boolean setCooldownTicks(int t) { if (t < 0) return false; cooldownTicks = t; markDirtyAndSync(); return true; }
    public int getCloseTimerSeconds() { return closeTimerSeconds; }
    public boolean setCloseTimerSeconds(int s) { if (s <= 0) return false; closeTimerSeconds = s; markDirtyAndSync(); return true; }
    public int getDeathTimePenaltyTicks() { return deathTimePenaltyTicks; }
    public boolean setDeathTimePenaltyTicks(int t) { if (t < 0) return false; deathTimePenaltyTicks = t; markDirtyAndSync(); return true; }
    public int getDisconnectGraceTicks() { return disconnectGraceTicks; }
    public boolean setDisconnectGraceTicks(int t) { if (t < 0) return false; disconnectGraceTicks = t; markDirtyAndSync(); return true; }
    public int getLobbyOfflineTimeoutTicks() { return lobbyOfflineTimeoutTicks; }
    public boolean setLobbyOfflineTimeoutTicks(int t) { if (t < 0) return false; lobbyOfflineTimeoutTicks = t; markDirtyAndSync(); return true; }
    public double getHpScalePerPlayer() { return hpScalePerPlayer; }
    /** Fraction of base HP added per player beyond the first (0 disables the scaling). */
    public boolean setHpScalePerPlayer(double scale) { if (scale < 0.0 || scale > 100.0) return false; hpScalePerPlayer = scale; markDirtyAndSync(); return true; }
    /** Mob HP multiplier for a party of the given size: {@code 1 + (players - 1) * scale}. */
    public double resolvePartyHealthMultiplier(int partySize) {
        return 1.0 + Math.max(0, partySize - 1) * hpScalePerPlayer;
    }
    public double getHpScalePerWave() { return hpScalePerWave; }
    /** Fraction of (party-scaled) base HP added per wave beyond the first (0 disables). */
    public boolean setHpScalePerWave(double scale) { if (scale < 0.0 || scale > 100.0) return false; hpScalePerWave = scale; markDirtyAndSync(); return true; }
    /** Mob HP multiplier for the given wave: {@code 1 + (wave - 1) * scale}. Applied on top of the party multiplier. */
    public double resolveWaveHealthMultiplier(int wave) {
        return 1.0 + Math.max(0, wave - 1) * hpScalePerWave;
    }
    public boolean isLootViaInbox() { return lootViaInbox; }
    public void setLootViaInbox(boolean v) { if (lootViaInbox != v) { lootViaInbox = v; markDirtyAndSync(); } }

    /** Wave ceiling; {@code -1} = endless until wipe or wave-timer expiry. */
    public int getMaxWave() { return maxWave; }
    public void setMaxWave(int w) { this.maxWave = w < 0 ? -1 : w; markDirtyAndSync(); }

    public double getRewardCurrencyBase() { return rewardCurrencyBase; }
    public double getRewardCurrencyExp() { return rewardCurrencyExp; }
    public double getRewardXpBase() { return rewardXpBase; }
    public double getRewardXpExp() { return rewardXpExp; }
    public void setRewardCurve(double currencyBase, double currencyExp, double xpBase, double xpExp) {
        this.rewardCurrencyBase = Math.max(0, currencyBase);
        this.rewardCurrencyExp = Math.max(0, currencyExp);
        this.rewardXpBase = Math.max(0, xpBase);
        this.rewardXpExp = Math.max(0, xpExp);
        markDirtyAndSync();
    }

    /** Total reward over {@code waves} completed using {@code base * Σ_{w=1..waves} w^exp}. */
    public long computeCurrencyReward(int waves) { return Math.round(rewardCurrencyBase * powerSum(waves, rewardCurrencyExp)); }
    public int computeXpReward(int waves) { return (int) Math.round(rewardXpBase * powerSum(waves, rewardXpExp)); }

    private static double powerSum(int waves, double exp) {
        double total = 0;
        for (int w = 1; w <= waves; w++) total += Math.pow(w, exp);
        return total;
    }

    // ── Instance management ──────────────────────────────────────────────────
    public boolean addInstance(BlockPos spawnerPos, ResourceKey<Level> dimension) {
        if (spawnerPos == null || dimension == null) return false;
        for (ArenaInstanceState inst : instances) {
            if (inst.spawnerPos().equals(spawnerPos) && inst.dimension().equals(dimension)) return false;
        }
        instances.add(new ArenaInstanceState(spawnerPos, dimension, InstanceStatus.FREE, 0));
        markDirtyAndSync();
        return true;
    }

    public boolean removeInstance(BlockPos spawnerPos, ResourceKey<Level> dimension) {
        for (Iterator<ArenaInstanceState> it = instances.iterator(); it.hasNext(); ) {
            ArenaInstanceState inst = it.next();
            if (inst.spawnerPos().equals(spawnerPos) && inst.dimension().equals(dimension)) {
                if (inst.status() == InstanceStatus.RUNNING) {
                    if (pendingInstanceRemovals.contains(spawnerPos)) pendingInstanceRemovals.remove(spawnerPos);
                    else pendingInstanceRemovals.add(spawnerPos);
                    markDirtyAndSync();
                    return true;
                }
                it.remove();
                activeRuns.remove(spawnerPos);
                pendingInstanceRemovals.remove(spawnerPos);
                markDirtyAndSync();
                return true;
            }
        }
        return false;
    }

    public boolean moveInstance(int from, int to) {
        if (from < 0 || from >= instances.size() || to < 0 || to >= instances.size() || from == to) return false;
        instances.add(to, instances.remove(from));
        markDirtyAndSync();
        return true;
    }

    private @Nullable ArenaInstanceState reserveFreeInstance() {
        for (int i = 0; i < instances.size(); i++) {
            ArenaInstanceState inst = instances.get(i);
            if (inst.status() == InstanceStatus.FREE) {
                ArenaInstanceState running = inst.withStatus(InstanceStatus.RUNNING, 0);
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
            ArenaInstanceState inst = instances.get(i);
            if (inst.spawnerPos().equals(spawnerPos) && inst.status() == InstanceStatus.RUNNING) {
                int cooldown = Math.max(0, cooldownTicks);
                InstanceStatus next = cooldown > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
                instances.set(i, inst.withStatus(next, cooldown));
                return;
            }
        }
    }

    // ── Lobby lookups ──────────────────────────────────────────────────────
    public @Nullable Lobby getLobbyById(UUID lobbyId) {
        if (lobbyId == null) return null;
        for (Lobby lobby : lobbies) if (lobby.lobbyId().equals(lobbyId)) return lobby;
        return null;
    }

    private int indexOfLobby(UUID lobbyId) {
        for (int i = 0; i < lobbies.size(); i++) if (lobbies.get(i).lobbyId().equals(lobbyId)) return i;
        return -1;
    }

    private void replaceLobby(Lobby updated) {
        int idx = indexOfLobby(updated.lobbyId());
        if (idx >= 0) lobbies.set(idx, updated);
    }

    public @Nullable Lobby getLobbyByMember(UUID playerUuid) {
        if (playerUuid == null) return null;
        for (Lobby lobby : lobbies) if (lobby.isMember(playerUuid)) return lobby;
        return null;
    }

    // ── Lobby operations ──────────────────────────────────────────────────────
    public boolean createLobby(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String name = player.getGameProfile().getName();
        if (getLobbyByMember(uuid) != null) return false;
        Set<UUID> members = new HashSet<>();
        members.add(uuid);
        Map<UUID, String> memberNames = new HashMap<>();
        memberNames.put(uuid, name);
        lobbies.add(new Lobby(
            UUID.randomUUID(), uuid, name, members, memberNames,
            Set.of(), DifficultyTier.NORMAL, false,
            LobbyVisibility.PUBLIC, LobbyStatus.FORMING,
            level != null ? level.getGameTime() : 0L));
        markDirtyAndSync();
        return true;
    }

    public boolean leaveLobby(ServerPlayer player) {
        return leaveLobby(player.getUUID(), true);
    }

    public boolean leaveLobby(UUID uuid) {
        return leaveLobby(uuid, false);
    }

    private boolean leaveLobby(UUID uuid, boolean notify) {
        Lobby lobby = getLobbyByMember(uuid);
        if (lobby == null) return false;
        if (lobby.status() == LobbyStatus.IN_RUN) {
            if (notify && level instanceof ServerLevel sl) {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayer(uuid);
                if (p != null) p.sendSystemMessage(Component.translatable("message.arenas_ld.cannot_leave_running"));
            }
            return false;
        }
        if (lobby.isOwner(uuid)) {
            if (lobby.members().size() <= 1) {
                disbandLobby(lobby);
            } else {
                UUID newOwner = lobby.members().stream().filter(m -> !m.equals(uuid)).findFirst().orElse(uuid);
                Lobby updated = lobby.withMemberRemoved(uuid).withOwner(newOwner);
                replaceLobby(updated);
                if (notify) notifyLobbyMembers(updated, Component.translatable("message.arenas_ld.lobby_owner_transferred"));
            }
        } else {
            replaceLobby(lobby.withMemberRemoved(uuid));
        }
        offlineSinceTick.remove(uuid);
        BusyStateCompat.clearBusy(uuid, BUSY_REASON);
        markDirtyAndSync();
        return true;
    }

    public boolean invitePlayer(ServerPlayer inviter, UUID inviteeUuid) {
        Lobby lobby = getLobbyByMember(inviter.getUUID());
        if (lobby == null || !lobby.isOwner(inviter.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN || lobby.isFull(getMaxPartySize())) return false;
        if (!(level instanceof ServerLevel sl)) return false;
        ServerPlayer target = sl.getServer().getPlayerList().getPlayer(inviteeUuid);
        if (target == null || target.getUUID().equals(inviter.getUUID())) return false;
        if (getLobbyByMember(target.getUUID()) != null) return false;
        for (PendingInvite invite : pendingInvites) {
            if (invite.lobbyId().equals(lobby.lobbyId()) && invite.invitedUuid().equals(target.getUUID())) return false;
        }
        long expiry = (level != null ? level.getGameTime() : 0L) + inviteExpiryTicks;
        pendingInvites.add(new PendingInvite(lobby.lobbyId(), target.getUUID(), inviter.getUUID(),
            expiry, lobby.selectedTier(), inviter.getGameProfile().getName()));
        markDirtyAndSync();
        return true;
    }

    public boolean acceptInvite(ServerPlayer player, UUID lobbyId) {
        UUID uuid = player.getUUID();
        PendingInvite invite = findInvite(lobbyId, uuid);
        if (invite == null) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.invite_expired_or_not_found"));
            return false;
        }
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null || lobby.status() == LobbyStatus.IN_RUN) {
            pendingInvites.remove(invite);
            markDirtyAndSync();
            return false;
        }
        if (getLobbyByMember(uuid) != null || lobby.isFull(getMaxPartySize())) return false;
        if ((level != null ? level.getGameTime() : Long.MAX_VALUE) > invite.expiresAtTick()) {
            pendingInvites.remove(invite);
            markDirtyAndSync();
            return false;
        }
        pendingInvites.remove(invite);
        pendingInvites.removeIf(i -> i.invitedUuid().equals(uuid));
        replaceLobby(lobby.withMemberAdded(uuid, player.getGameProfile().getName()));
        markDirtyAndSync();
        return true;
    }

    public boolean declineInvite(ServerPlayer player, UUID lobbyId) {
        PendingInvite invite = findInvite(lobbyId, player.getUUID());
        if (invite == null) return false;
        pendingInvites.remove(invite);
        markDirtyAndSync();
        return true;
    }

    public boolean kickFromLobby(ServerPlayer owner, UUID targetUuid) {
        Lobby lobby = getLobbyByMember(owner.getUUID());
        if (lobby == null || !lobby.isOwner(owner.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN || owner.getUUID().equals(targetUuid) || !lobby.isMember(targetUuid)) return false;
        replaceLobby(lobby.withMemberRemoved(targetUuid));
        offlineSinceTick.remove(targetUuid);
        BusyStateCompat.clearBusy(targetUuid, BUSY_REASON);
        if (level instanceof ServerLevel sl) {
            ServerPlayer kicked = sl.getServer().getPlayerList().getPlayer(targetUuid);
            if (kicked != null) kicked.sendSystemMessage(Component.translatable("message.arenas_ld.you_were_kicked"));
        }
        markDirtyAndSync();
        return true;
    }

    public boolean setHardcore(ServerPlayer player, boolean hardcore) {
        Lobby lobby = getLobbyByMember(player.getUUID());
        if (lobby == null || !lobby.isOwner(player.getUUID()) || lobby.status() == LobbyStatus.IN_RUN) return false;
        replaceLobby(lobby.withHardcore(hardcore));
        markDirtyAndSync();
        return true;
    }

    public boolean setVisibility(ServerPlayer player, LobbyVisibility visibility) {
        Lobby lobby = getLobbyByMember(player.getUUID());
        if (lobby == null || !lobby.isOwner(player.getUUID()) || lobby.status() == LobbyStatus.IN_RUN) return false;
        replaceLobby(lobby.withVisibility(visibility));
        markDirtyAndSync();
        return true;
    }

    public boolean toggleReady(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Lobby lobby = getLobbyByMember(uuid);
        if (lobby == null || lobby.status() == LobbyStatus.IN_RUN) return false;
        Lobby updated = lobby.readyMembers().contains(uuid) ? lobby.withUnready(uuid) : lobby.withReady(uuid);
        if (updated.allReady()) updated = updated.withStatus(LobbyStatus.READY);
        else if (updated.status() == LobbyStatus.READY) updated = updated.withStatus(LobbyStatus.FORMING);
        replaceLobby(updated);
        markDirtyAndSync();
        return true;
    }

    public boolean joinLobby(ServerPlayer player, UUID lobbyId) {
        UUID uuid = player.getUUID();
        if (getLobbyByMember(uuid) != null) return false;
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null || lobby.visibility() != LobbyVisibility.PUBLIC) return false;
        if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) return false;
        if (lobby.isFull(getMaxPartySize())) return false;
        replaceLobby(lobby.withMemberAdded(uuid, player.getGameProfile().getName()));
        markDirtyAndSync();
        return true;
    }

    public boolean requestJoin(ServerPlayer player, UUID lobbyId) {
        UUID uuid = player.getUUID();
        if (getLobbyByMember(uuid) != null) return false;
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null || lobby.visibility() != LobbyVisibility.FRIENDS) return false;
        if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) return false;
        if (lobby.isFull(getMaxPartySize())) return false;
        for (PendingJoinRequest req : pendingJoinRequests) {
            if (req.lobbyId().equals(lobbyId) && req.requesterUuid().equals(uuid)) return false;
        }
        long expiry = (level != null ? level.getGameTime() : 0L) + inviteExpiryTicks;
        pendingJoinRequests.add(new PendingJoinRequest(lobbyId, uuid, player.getGameProfile().getName(), expiry, lobby.selectedTier()));
        markDirtyAndSync();
        return true;
    }

    public boolean acceptJoinRequest(ServerPlayer owner, UUID requesterUuid) {
        Lobby lobby = getLobbyByMember(owner.getUUID());
        if (lobby == null || !lobby.isOwner(owner.getUUID())) return false;
        PendingJoinRequest req = findJoinRequest(lobby.lobbyId(), requesterUuid);
        if (req == null) return false;
        pendingJoinRequests.remove(req);
        if (getLobbyByMember(requesterUuid) != null || lobby.isFull(getMaxPartySize()) || lobby.status() == LobbyStatus.IN_RUN) return false;
        String requesterName = req.requesterName();
        if (level instanceof ServerLevel sl) {
            ServerPlayer requester = sl.getServer().getPlayerList().getPlayer(requesterUuid);
            if (requester != null) requesterName = requester.getGameProfile().getName();
        }
        replaceLobby(lobby.withMemberAdded(requesterUuid, requesterName));
        markDirtyAndSync();
        return true;
    }

    public boolean declineJoinRequest(ServerPlayer owner, UUID requesterUuid) {
        Lobby lobby = getLobbyByMember(owner.getUUID());
        if (lobby == null || !lobby.isOwner(owner.getUUID())) return false;
        PendingJoinRequest req = findJoinRequest(lobby.lobbyId(), requesterUuid);
        if (req == null) return false;
        pendingJoinRequests.remove(req);
        markDirtyAndSync();
        return true;
    }

    public boolean startRun(ServerPlayer player) {
        Lobby lobby = getLobbyByMember(player.getUUID());
        if (lobby == null || !lobby.isOwner(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.only_owner_can_start_dungeon"));
            return false;
        }
        if (lobby.status() == LobbyStatus.IN_RUN || !(level instanceof ServerLevel serverLevel)) return false;

        ArenaInstanceState freeInst = reserveFreeInstance();
        if (freeInst == null) {
            if (!queuedLobbyIds.contains(lobby.lobbyId())) {
                queuedLobbyIds.add(lobby.lobbyId());
                notifyLobbyMembers(lobby, Component.translatable("message.arenas_ld.lobby_queued"));
            }
            markDirtyAndSync();
            return false;
        }
        return launchOnInstance(serverLevel, lobby, freeInst);
    }

    // ── Run end notification (called by ArenaRunLifecycle) ─────────────────────
    public void onArenaEnded(BlockPos spawnerPos, int wavesReached, List<String> playerNames) {
        if (playerNames != null && wavesReached > 0) {
            long now = System.currentTimeMillis();
            for (String name : playerNames) {
                if (name == null || name.isBlank()) continue;
                // Keep each player's best (deepest) wave only.
                leaderboard.removeIf(e -> e.playerName().equals(name) && e.timeSeconds() <= wavesReached);
                boolean alreadyBetter = leaderboard.stream().anyMatch(e -> e.playerName().equals(name));
                if (!alreadyBetter) leaderboard.add(new LeaderboardEntry(name, wavesReached, now));
            }
            leaderboard.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds).reversed());
            if (leaderboard.size() > MAX_LEADERBOARD_ENTRIES) {
                leaderboard.subList(MAX_LEADERBOARD_ENTRIES, leaderboard.size()).clear();
            }
        }

        ArenaRun endingRun = activeRuns.get(spawnerPos);
        UUID lobbyId = endingRun != null ? endingRun.lobbyId() : null;
        if (lobbyId != null) {
            Lobby lobby = getLobbyById(lobbyId);
            if (lobby != null) {
                for (UUID memberUuid : lobby.members()) BusyStateCompat.clearBusy(memberUuid, BUSY_REASON);
                lobbies.remove(lobby);
                final UUID finalLobbyId = lobbyId;
                pendingInvites.removeIf(inv -> inv.lobbyId().equals(finalLobbyId));
                pendingJoinRequests.removeIf(req -> req.lobbyId().equals(finalLobbyId));
            }
            queuedLobbyIds.remove(lobbyId);
        } else if (playerNames != null && level instanceof ServerLevel sl) {
            for (String name : playerNames) {
                ServerPlayer p = sl.getServer().getPlayerList().getPlayerByName(name);
                if (p != null) BusyStateCompat.clearBusy(p.getUUID(), BUSY_REASON);
            }
        }

        if (pendingInstanceRemovals.remove(spawnerPos)) {
            instances.removeIf(inst -> inst.spawnerPos().equals(spawnerPos));
        } else {
            releaseInstance(spawnerPos);
        }
        activeRuns.remove(spawnerPos);
        promoteNextQueuedLobby();
        markDirtyAndSync();
    }

    // ── Tick helpers ──────────────────────────────────────────────────────────
    private void tickCooldowns(ServerLevel level) {
        boolean changed = false;
        for (int i = 0; i < instances.size(); i++) {
            ArenaInstanceState inst = instances.get(i);
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
        changed |= pendingInvites.removeIf(inv -> now > inv.expiresAtTick());
        changed |= pendingJoinRequests.removeIf(req -> now > req.expiresAtTick());

        List<UUID> toDisband = new ArrayList<>();
        for (Lobby lobby : lobbies) {
            if (lobby.status() == LobbyStatus.IN_RUN) {
                offlineSinceTick.keySet().removeIf(lobby::isMember);
                continue;
            }
            for (UUID memberUuid : new ArrayList<>(lobby.members())) {
                boolean online = serverLevel.getServer().getPlayerList().getPlayer(memberUuid) != null;
                if (online) { offlineSinceTick.remove(memberUuid); continue; }
                long since = offlineSinceTick.computeIfAbsent(memberUuid, id -> now);
                if (now - since >= lobbyOfflineTimeoutTicks) {
                    if (lobby.isOwner(memberUuid)) {
                        if (lobby.members().size() <= 1) {
                            toDisband.add(lobby.lobbyId());
                        } else {
                            UUID newOwner = lobby.members().stream().filter(m -> !m.equals(memberUuid)).findFirst().orElse(memberUuid);
                            replaceLobby(lobby.withMemberRemoved(memberUuid).withOwner(newOwner));
                        }
                    } else {
                        replaceLobby(lobby.withMemberRemoved(memberUuid));
                    }
                    offlineSinceTick.remove(memberUuid);
                    changed = true;
                }
            }
        }
        for (UUID lobbyId : toDisband) {
            Lobby lobby = getLobbyById(lobbyId);
            if (lobby != null) { disbandLobby(lobby); changed = true; }
        }
        if (changed) markDirtyAndSync();
    }

    private void promoteNextQueuedLobby() {
        if (!hasAnyFreeInstance() || queuedLobbyIds.isEmpty() || !(level instanceof ServerLevel serverLevel)) return;
        UUID nextLobbyId = queuedLobbyIds.get(0);
        Lobby lobby = getLobbyById(nextLobbyId);
        if (lobby == null) {
            queuedLobbyIds.remove(0);
            promoteNextQueuedLobby();
            return;
        }
        ArenaInstanceState freeInst = reserveFreeInstance();
        if (freeInst == null) return;
        queuedLobbyIds.remove(0);
        launchOnInstance(serverLevel, lobby, freeInst);
    }

    private boolean launchOnInstance(ServerLevel serverLevel, Lobby lobby, ArenaInstanceState instance) {
        ServerLevel spawnerLevel = serverLevel.getServer().getLevel(instance.dimension());
        if (spawnerLevel == null || !(spawnerLevel.getBlockEntity(instance.spawnerPos()) instanceof ArenaSpawnerBlockEntity)) {
            freeReservedInstance(instance.spawnerPos());
            return false;
        }
        List<ServerPlayer> players = resolveOnlinePlayers(lobby, serverLevel.getServer());
        if (players.isEmpty()) {
            freeReservedInstance(instance.spawnerPos());
            return false;
        }

        ArenaRun run = new ArenaRun(
            lobby.lobbyId(),
            lobby.ownerName(),
            lobby.hardcoreEnabled(),
            instance.spawnerPos(),
            spawnerLevel.dimension(),
            maxWave,
            serverLevel.getGameTime());
        activeRuns.put(instance.spawnerPos(), run);

        ArenaRunLifecycle.beginRun(spawnerLevel, this, run, players);
        for (ServerPlayer p : players) BusyStateCompat.setBusy(p.getUUID(), BUSY_REASON);
        replaceLobby(lobby.withStatus(LobbyStatus.IN_RUN));
        markDirtyAndSync();
        return true;
    }

    private void freeReservedInstance(BlockPos spawnerPos) {
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).spawnerPos().equals(spawnerPos)) {
                instances.set(i, instances.get(i).withStatus(InstanceStatus.FREE, 0));
                break;
            }
        }
    }

    // ── Utilities ──────────────────────────────────────────────────────────
    private void disbandLobby(Lobby lobby) {
        for (UUID memberUuid : lobby.members()) offlineSinceTick.remove(memberUuid);
        pendingInvites.removeIf(inv -> inv.lobbyId().equals(lobby.lobbyId()));
        pendingJoinRequests.removeIf(req -> req.lobbyId().equals(lobby.lobbyId()));
        queuedLobbyIds.remove(lobby.lobbyId());
        lobbies.remove(lobby);
        activeRuns.entrySet().removeIf(entry -> entry.getValue().lobbyId().equals(lobby.lobbyId()));
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

    private List<ServerPlayer> resolveOnlinePlayers(Lobby lobby, MinecraftServer server) {
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

    public void markDirtyAndSync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── Persistence ──────────────────────────────────────────────────────────
    private record LifecycleTimings(int disconnectGraceTicks, int lobbyOfflineTimeoutTicks,
                                    int respawnTimeTicks, int deathTimePenaltyTicks,
                                    double hpScalePerPlayer, double hpScalePerWave) {
        static final LifecycleTimings DEFAULT = new LifecycleTimings(
            DEFAULT_DISCONNECT_GRACE_TICKS, DEFAULT_LOBBY_OFFLINE_TIMEOUT_TICKS,
            DEFAULT_RESPAWN_TIME_TICKS, DEFAULT_DEATH_TIME_PENALTY_TICKS,
            DEFAULT_HP_SCALE_PER_PLAYER, DEFAULT_HP_SCALE_PER_WAVE);
        static final Codec<LifecycleTimings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("disconnectGraceTicks", DEFAULT_DISCONNECT_GRACE_TICKS).forGetter(LifecycleTimings::disconnectGraceTicks),
            Codec.INT.optionalFieldOf("lobbyOfflineTimeoutTicks", DEFAULT_LOBBY_OFFLINE_TIMEOUT_TICKS).forGetter(LifecycleTimings::lobbyOfflineTimeoutTicks),
            Codec.INT.optionalFieldOf("respawnTimeTicks", DEFAULT_RESPAWN_TIME_TICKS).forGetter(LifecycleTimings::respawnTimeTicks),
            Codec.INT.optionalFieldOf("deathTimePenaltyTicks", DEFAULT_DEATH_TIME_PENALTY_TICKS).forGetter(LifecycleTimings::deathTimePenaltyTicks),
            Codec.DOUBLE.optionalFieldOf("hpScalePerPlayer", DEFAULT_HP_SCALE_PER_PLAYER).forGetter(LifecycleTimings::hpScalePerPlayer),
            Codec.DOUBLE.optionalFieldOf("hpScalePerWave", DEFAULT_HP_SCALE_PER_WAVE).forGetter(LifecycleTimings::hpScalePerWave)
        ).apply(i, LifecycleTimings::new));
    }

    private record RewardCurve(double currencyBase, double currencyExp, double xpBase, double xpExp) {
        static final RewardCurve DEFAULT = new RewardCurve(DEFAULT_CURRENCY_BASE, DEFAULT_CURRENCY_EXP, DEFAULT_XP_BASE, DEFAULT_XP_EXP);
        static final Codec<RewardCurve> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("currencyBase", DEFAULT_CURRENCY_BASE).forGetter(RewardCurve::currencyBase),
            Codec.DOUBLE.optionalFieldOf("currencyExp", DEFAULT_CURRENCY_EXP).forGetter(RewardCurve::currencyExp),
            Codec.DOUBLE.optionalFieldOf("xpBase", DEFAULT_XP_BASE).forGetter(RewardCurve::xpBase),
            Codec.DOUBLE.optionalFieldOf("xpExp", DEFAULT_XP_EXP).forGetter(RewardCurve::xpExp)
        ).apply(i, RewardCurve::new));
    }

    private record InstanceEntry(BlockPos spawnerPos, String dimension, String status, int cooldownTicksRemaining) {
        static final Codec<InstanceEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("spawnerPos").forGetter(InstanceEntry::spawnerPos),
            Codec.STRING.fieldOf("dimension").forGetter(InstanceEntry::dimension),
            Codec.STRING.fieldOf("status").forGetter(InstanceEntry::status),
            Codec.INT.fieldOf("cooldownTicksRemaining").forGetter(InstanceEntry::cooldownTicksRemaining)
        ).apply(i, InstanceEntry::new));
    }

    private record InstanceRunEntry(BlockPos spawnerPos, ArenaRun run) {
        static final Codec<InstanceRunEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("spawnerPos").forGetter(InstanceRunEntry::spawnerPos),
            ArenaRun.CODEC.fieldOf("run").forGetter(InstanceRunEntry::run)
        ).apply(i, InstanceRunEntry::new));
    }

    private record State(
        LifecycleTimings lifecycle,
        int maxPartySize,
        int inviteExpiryTicks,
        int cooldownTicks,
        int closeTimerSeconds,
        boolean lootViaInbox,
        int maxWave,
        RewardCurve rewardCurve,
        List<InstanceEntry> instances,
        List<Lobby> lobbies,
        List<UUID> queuedLobbyIds,
        List<PendingInvite> pendingInvites,
        List<PendingJoinRequest> pendingJoinRequests,
        List<LeaderboardEntry> leaderboard,
        List<BlockPos> pendingInstanceRemovals,
        List<InstanceRunEntry> activeRuns
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            LifecycleTimings.CODEC.optionalFieldOf("lifecycle", LifecycleTimings.DEFAULT).forGetter(State::lifecycle),
            Codec.INT.optionalFieldOf("maxPartySize", DEFAULT_MAX_PARTY_SIZE).forGetter(State::maxPartySize),
            Codec.INT.optionalFieldOf("inviteExpiryTicks", DEFAULT_INVITE_EXPIRY_TICKS).forGetter(State::inviteExpiryTicks),
            Codec.INT.optionalFieldOf("cooldownTicks", DEFAULT_COOLDOWN_TICKS).forGetter(State::cooldownTicks),
            Codec.INT.optionalFieldOf("closeTimerSeconds", DEFAULT_CLOSE_TIMER_SECONDS).forGetter(State::closeTimerSeconds),
            Codec.BOOL.optionalFieldOf("lootViaInbox", false).forGetter(State::lootViaInbox),
            Codec.INT.optionalFieldOf("maxWave", DEFAULT_MAX_WAVE).forGetter(State::maxWave),
            RewardCurve.CODEC.optionalFieldOf("rewardCurve", RewardCurve.DEFAULT).forGetter(State::rewardCurve),
            InstanceEntry.CODEC.listOf().optionalFieldOf("instances", List.of()).forGetter(State::instances),
            Lobby.CODEC.listOf().optionalFieldOf("lobbies", List.of()).forGetter(State::lobbies),
            UUIDUtil.CODEC.listOf().optionalFieldOf("queuedLobbyIds", List.of()).forGetter(State::queuedLobbyIds),
            PendingInvite.CODEC.listOf().optionalFieldOf("pendingInvites", List.of()).forGetter(State::pendingInvites),
            PendingJoinRequest.CODEC.listOf().optionalFieldOf("pendingJoinRequests", List.of()).forGetter(State::pendingJoinRequests),
            LeaderboardEntry.CODEC.listOf().optionalFieldOf("leaderboard", List.of()).forGetter(State::leaderboard),
            BlockPos.CODEC.listOf().optionalFieldOf("pendingInstanceRemovals", List.of()).forGetter(State::pendingInstanceRemovals),
            InstanceRunEntry.CODEC.listOf().optionalFieldOf("activeRuns", List.of()).forGetter(State::activeRuns)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        List<InstanceEntry> instanceEntries = new ArrayList<>();
        for (ArenaInstanceState inst : instances) {
            instanceEntries.add(new InstanceEntry(inst.spawnerPos(), inst.dimension().location().toString(),
                inst.status().name(), inst.cooldownTicksRemaining()));
        }
        List<InstanceRunEntry> runEntries = new ArrayList<>(activeRuns.size());
        for (Map.Entry<BlockPos, ArenaRun> entry : activeRuns.entrySet()) {
            runEntries.add(new InstanceRunEntry(entry.getKey(), entry.getValue()));
        }
        State state = new State(
            new LifecycleTimings(disconnectGraceTicks, lobbyOfflineTimeoutTicks, respawnTimeTicks, deathTimePenaltyTicks, hpScalePerPlayer, hpScalePerWave),
            maxPartySize, inviteExpiryTicks, cooldownTicks, closeTimerSeconds, lootViaInbox, maxWave,
            new RewardCurve(rewardCurrencyBase, rewardCurrencyExp, rewardXpBase, rewardXpExp),
            instanceEntries, new ArrayList<>(lobbies), new ArrayList<>(queuedLobbyIds),
            new ArrayList<>(pendingInvites), new ArrayList<>(pendingJoinRequests),
            new ArrayList<>(leaderboard), new ArrayList<>(pendingInstanceRemovals), runEntries);
        State.CODEC.encodeStart(NbtOps.INSTANCE, state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save ArenaController at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if (!nbt.contains("State", Tag.TAG_COMPOUND)) return;
        State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load ArenaController at {}: {}", worldPosition, err))
            .ifPresent(state -> {
                disconnectGraceTicks = state.lifecycle().disconnectGraceTicks();
                lobbyOfflineTimeoutTicks = state.lifecycle().lobbyOfflineTimeoutTicks();
                respawnTimeTicks = state.lifecycle().respawnTimeTicks();
                deathTimePenaltyTicks = state.lifecycle().deathTimePenaltyTicks();
                hpScalePerPlayer = state.lifecycle().hpScalePerPlayer();
                hpScalePerWave = state.lifecycle().hpScalePerWave();
                maxPartySize = state.maxPartySize();
                inviteExpiryTicks = state.inviteExpiryTicks();
                cooldownTicks = state.cooldownTicks();
                closeTimerSeconds = state.closeTimerSeconds();
                lootViaInbox = state.lootViaInbox();
                maxWave = state.maxWave();
                rewardCurrencyBase = state.rewardCurve().currencyBase();
                rewardCurrencyExp = state.rewardCurve().currencyExp();
                rewardXpBase = state.rewardCurve().xpBase();
                rewardXpExp = state.rewardCurve().xpExp();

                instances.clear();
                for (InstanceEntry ie : state.instances()) {
                    InstanceStatus status;
                    try { status = InstanceStatus.valueOf(ie.status()); } catch (Exception e) { status = InstanceStatus.FREE; }
                    instances.add(new ArenaInstanceState(ie.spawnerPos(), parseDimension(ie.dimension()), status, ie.cooldownTicksRemaining()));
                }
                lobbies.clear(); lobbies.addAll(state.lobbies());
                queuedLobbyIds.clear(); queuedLobbyIds.addAll(state.queuedLobbyIds());
                pendingInvites.clear(); pendingInvites.addAll(state.pendingInvites());
                pendingJoinRequests.clear(); pendingJoinRequests.addAll(state.pendingJoinRequests());
                leaderboard.clear(); leaderboard.addAll(state.leaderboard());
                pendingInstanceRemovals.clear(); pendingInstanceRemovals.addAll(state.pendingInstanceRemovals());
                activeRuns.clear();
                for (InstanceRunEntry ire : state.activeRuns()) activeRuns.put(ire.spawnerPos(), ire.run());
            });
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    private static ResourceKey<Level> parseDimension(String id) {
        if (id == null || id.isBlank()) return Level.OVERWORLD;
        try {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
        } catch (Exception e) {
            return Level.OVERWORLD;
        }
    }

    // ── Screen factory (player lobby view) ──────────────────────────────────────
    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arenas_ld.arena_controller");
    }

    @Nullable
    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int syncId, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
        return new net.ledok.arenas_ld.arena.screen.ArenaControllerScreenHandler(syncId, inventory, this);
    }

    @Override
    public net.ledok.arenas_ld.arena.screen.ArenaControllerMenuData getScreenOpeningData(ServerPlayer player) {
        return new net.ledok.arenas_ld.arena.screen.ArenaControllerMenuData(worldPosition);
    }
}
