package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.screen.RaidControllerData;
import net.ledok.arenas_ld.screen.RaidControllerScreenHandler;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.arenas_ld.util.RaidDifficulty;
import net.ledok.arenas_ld.util.RaidLeaderboardEntry;
import net.ledok.arenas_ld.util.RaidRunCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RaidControllerBlockEntity extends BlockEntity implements RaidRunCallback, ExtendedScreenHandlerFactory<RaidControllerData> {
    public record ControllerKey(BlockPos pos, ResourceKey<Level> dimension) {}

    public enum LobbyStatus {
        OPEN,
        QUEUED,
        IN_DUNGEON
    }

    public enum LobbyVisibility {
        OPEN,
        INVITE_ONLY
    }

    public static final class Lobby {
        public UUID id = UUID.randomUUID();
        public UUID ownerUuid = EMPTY_UUID;
        public String ownerName = "";
        public final List<UUID> members = new ArrayList<>();
        public final Map<UUID, Long> pendingInvites = new HashMap<>();
        public LobbyVisibility visibility = LobbyVisibility.OPEN;
        public LobbyStatus status = LobbyStatus.OPEN;
        public RaidDifficulty selectedDifficulty = RaidDifficulty.NORMAL;
        public boolean hardcoreEnabled = false;

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putUUID("OwnerUuid", ownerUuid);
            tag.putString("OwnerName", ownerName);
            tag.putString("Visibility", visibility.name());
            tag.putString("Status", status.name());
            tag.putString("SelectedDifficulty", selectedDifficulty.name());
            tag.putBoolean("HardcoreEnabled", hardcoreEnabled);

            ListTag membersList = new ListTag();
            for (UUID member : members) {
                CompoundTag memberTag = new CompoundTag();
                memberTag.putUUID("Uuid", member);
                membersList.add(memberTag);
            }
            tag.put("Members", membersList);

            ListTag invitesList = new ListTag();
            for (Map.Entry<UUID, Long> entry : pendingInvites.entrySet()) {
                CompoundTag inviteTag = new CompoundTag();
                inviteTag.putUUID("Uuid", entry.getKey());
                inviteTag.putLong("ExpireAt", entry.getValue());
                invitesList.add(inviteTag);
            }
            tag.put("PendingInvites", invitesList);
            return tag;
        }

        public static Lobby fromNbt(CompoundTag tag) {
            Lobby lobby = new Lobby();
            if (tag.hasUUID("Id")) {
                lobby.id = tag.getUUID("Id");
            }
            if (tag.hasUUID("OwnerUuid")) {
                lobby.ownerUuid = tag.getUUID("OwnerUuid");
            }
            lobby.ownerName = tag.getString("OwnerName");
            try {
                lobby.visibility = LobbyVisibility.valueOf(tag.getString("Visibility"));
            } catch (Exception ignored) {
                lobby.visibility = LobbyVisibility.OPEN;
            }
            try {
                lobby.status = LobbyStatus.valueOf(tag.getString("Status"));
            } catch (Exception ignored) {
                lobby.status = LobbyStatus.OPEN;
            }
            lobby.selectedDifficulty =
                    RaidDifficulty.fromNameOrDefault(tag.getString("SelectedDifficulty"), RaidDifficulty.NORMAL);
            lobby.hardcoreEnabled = tag.getBoolean("HardcoreEnabled");

            lobby.members.clear();
            if (tag.contains("Members", Tag.TAG_LIST)) {
                ListTag membersList = tag.getList("Members", Tag.TAG_COMPOUND);
                for (Tag t : membersList) {
                    CompoundTag memberTag = (CompoundTag) t;
                    if (memberTag.hasUUID("Uuid")) {
                        lobby.members.add(memberTag.getUUID("Uuid"));
                    }
                }
            }

            lobby.pendingInvites.clear();
            if (tag.contains("PendingInvites", Tag.TAG_LIST)) {
                ListTag invitesList = tag.getList("PendingInvites", Tag.TAG_COMPOUND);
                for (Tag t : invitesList) {
                    CompoundTag inviteTag = (CompoundTag) t;
                    if (inviteTag.hasUUID("Uuid")) {
                        lobby.pendingInvites.put(inviteTag.getUUID("Uuid"), inviteTag.getLong("ExpireAt"));
                    }
                }
            }
            return lobby;
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

    private static final UUID EMPTY_UUID = new UUID(0L, 0L);
    private static final int DEFAULT_RESPAWN_TIME_TICKS = 6000;
    private static final int DEFAULT_MAX_PARTY_SIZE = 10;
    private static final String BUSY_REASON = net.ledok.busylib.BusyReasons.IN_RAID;
    private static final long INVITE_EXPIRY_TICKS = 5L * 60L * 20L;
    private static final long OFFLINE_GRACE_TICKS = 5L * 60L * 20L;
    private static final long LOBBY_CLEANUP_INTERVAL_TICKS = 30L * 20L;
    private static final int INSTANCE_VALIDATION_INTERVAL_TICKS = 100;
    private static final Set<ControllerKey> CONTROLLERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Instance pool
    private final List<RaidInstanceState> instances = new ArrayList<>();
    // Lobby management
    private final List<Lobby> lobbies = new ArrayList<>();
    private final Map<UUID, RaidInstanceState> lobbyInstanceMap = new HashMap<>();
    // Config
    private int respawnTimeTicks = DEFAULT_RESPAWN_TIME_TICKS;
    private int maxPartySize = DEFAULT_MAX_PARTY_SIZE;
    private long rewardCurrencyPerPlayer = 0L;
    // Leaderboard
    private final Map<RaidDifficulty, List<RaidLeaderboardEntry>> leaderboardByDifficulty = new EnumMap<>(RaidDifficulty.class);

    private final Map<UUID, Long> offlineSinceTick = new HashMap<>();
    private long nextLobbyCleanupTick = 0L;
    private int instanceValidationTickCounter = 0;

    public RaidControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.RAID_CONTROLLER_BLOCK_ENTITY, pos, state);
    }

    public static Set<ControllerKey> getControllers() {
        return CONTROLLERS;
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

    public static void tick(Level level, BlockPos pos, BlockState state, RaidControllerBlockEntity be) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickCooldowns(serverLevel);
        be.tickLobbies(serverLevel);
        be.pruneMissingInstances(serverLevel);
    }

    public List<RaidInstanceState> getInstances() {
        return Collections.unmodifiableList(instances);
    }

    public List<Lobby> getLobbies() {
        return Collections.unmodifiableList(lobbies);
    }

    public Map<UUID, RaidInstanceState> getLobbyInstanceMap() {
        return Collections.unmodifiableMap(lobbyInstanceMap);
    }

    public int getRespawnTimeTicks() {
        return Math.max(0, respawnTimeTicks);
    }

    public long getRewardCurrencyPerPlayer() {
        return Math.max(0L, rewardCurrencyPerPlayer);
    }

    public void setRewardCurrencyPerPlayer(long amount) {
        long clamped = Math.max(0L, amount);
        if (this.rewardCurrencyPerPlayer == clamped) {
            return;
        }
        this.rewardCurrencyPerPlayer = clamped;
        markDirtyAndSync();
    }

    public int getMaxPartySize() {
        return Math.max(1, Math.min(20, maxPartySize));
    }

    public void setMaxPartySize(int maxPartySize) {
        int clamped = Math.max(1, Math.min(20, maxPartySize));
        if (this.maxPartySize == clamped) {
            return;
        }
        this.maxPartySize = clamped;
        markDirtyAndSync();
    }

    public void setRespawnTimeTicks(int respawnTimeTicks) {
        int clamped = Math.max(0, respawnTimeTicks);
        if (this.respawnTimeTicks == clamped) {
            return;
        }
        this.respawnTimeTicks = clamped;
        boolean changed = false;
        for (int i = 0; i < instances.size(); i++) {
            RaidInstanceState state = instances.get(i);
            if (state.status() != InstanceStatus.COOLDOWN) {
                continue;
            }
            int remaining = Math.min(state.cooldownTicksRemaining(), clamped);
            InstanceStatus next = remaining > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
            if (remaining != state.cooldownTicksRemaining() || next != state.status()) {
                instances.set(i, state.withStatus(next, remaining));
                changed = true;
            }
        }
        if (changed) {
            promoteNextQueuedLobby();
        }
        markDirtyAndSync();
    }

    public List<RaidLeaderboardEntry> getLeaderboardForDifficulty(RaidDifficulty difficulty) {
        return leaderboardByDifficulty.getOrDefault(difficulty, List.of());
    }

    public void upsertLeaderboardEntry(RaidDifficulty difficulty, String playerName, int timeSeconds) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        List<RaidLeaderboardEntry> list =
                leaderboardByDifficulty.computeIfAbsent(difficulty, d -> new ArrayList<>());
        list.removeIf(e -> e.playerName().equals(playerName) && e.timeSeconds() <= timeSeconds);
        boolean alreadyBetter = list.stream().anyMatch(e -> e.playerName().equals(playerName));
        if (alreadyBetter) {
            return;
        }
        int idx = 0;
        while (idx < list.size() && list.get(idx).timeSeconds() <= timeSeconds) {
            idx++;
        }
        list.add(idx, new RaidLeaderboardEntry(playerName, timeSeconds));
        if (list.size() > 20) {
            list.remove(list.size() - 1);
        }
        markDirtyAndSync();
    }

    public boolean hasAnyFreeInstance() {
        return instances.stream().anyMatch(instance -> instance.status() == InstanceStatus.FREE);
    }

    public RaidInstanceState reserveFreeInstance() {
        for (int i = 0; i < instances.size(); i++) {
            RaidInstanceState instance = instances.get(i);
            if (instance.status() != InstanceStatus.FREE) {
                continue;
            }
            RaidInstanceState running = instance.withStatus(InstanceStatus.RUNNING, 0);
            instances.set(i, running);
            markDirtyAndSync();
            return running;
        }
        return null;
    }

    public boolean addInstance(BlockPos spawnerPos, ResourceKey<Level> dimension) {
        if (spawnerPos == null || dimension == null) {
            return false;
        }
        if (indexOfInstance(spawnerPos, dimension) >= 0) {
            return false;
        }
        instances.add(new RaidInstanceState(spawnerPos, dimension, InstanceStatus.FREE, 0));
        markDirtyAndSync();
        return true;
    }

    public boolean removeInstance(BlockPos spawnerPos, ResourceKey<Level> dimension) {
        int idx = indexOfInstance(spawnerPos, dimension);
        if (idx < 0) {
            return false;
        }
        RaidInstanceState state = instances.get(idx);
        if (state.status() == InstanceStatus.RUNNING) {
            return false;
        }
        instances.remove(idx);
        lobbyInstanceMap.values().removeIf(mapped -> sameInstance(mapped, spawnerPos, dimension));
        markDirtyAndSync();
        return true;
    }

    public Lobby getLobbyById(UUID lobbyId) {
        if (lobbyId == null) {
            return null;
        }
        for (Lobby lobby : lobbies) {
            if (lobby.id.equals(lobbyId)) {
                return lobby;
            }
        }
        return null;
    }

    public Lobby getLobbyByMember(UUID playerUuid) {
        if (playerUuid == null) {
            return null;
        }
        for (Lobby lobby : lobbies) {
            if (lobby.ownerUuid.equals(playerUuid) || lobby.members.contains(playerUuid)) {
                return lobby;
            }
        }
        return null;
    }

    public Lobby createLobby(UUID ownerUuid, String ownerName) {
        if (ownerUuid == null || ownerName == null || getLobbyByMember(ownerUuid) != null) {
            return null;
        }
        Lobby lobby = new Lobby();
        lobby.ownerUuid = ownerUuid;
        lobby.ownerName = ownerName;
        lobby.selectedDifficulty = RaidDifficulty.NORMAL;
        lobby.hardcoreEnabled = false;
        lobbies.add(lobby);
        markDirtyAndSync();
        return lobby;
    }

    public boolean invitePlayer(UUID ownerUuid, UUID targetUuid, long expireAtTick) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid) || targetUuid == null) {
            return false;
        }
        if (lobby.status == LobbyStatus.IN_DUNGEON || getLobbyByMember(targetUuid) != null) {
            return false;
        }
        int currentSize = 1 + lobby.members.size();
        if (currentSize >= getMaxPartySize()) {
            return false;
        }
        lobby.pendingInvites.put(targetUuid, expireAtTick);
        markDirtyAndSync();
        return true;
    }

    public boolean acceptInvite(UUID lobbyId, UUID playerUuid, String playerName, long nowTick) {
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null || playerUuid == null) {
            return false;
        }
        if (lobby.status == LobbyStatus.IN_DUNGEON) {
            return false;
        }
        Long expireAt = lobby.pendingInvites.get(playerUuid);
        if (expireAt == null || expireAt < nowTick) {
            lobby.pendingInvites.remove(playerUuid);
            markDirtyAndSync();
            return false;
        }
        if (getLobbyByMember(playerUuid) != null) {
            return false;
        }
        int currentSize = 1 + lobby.members.size();
        if (currentSize >= getMaxPartySize()) {
            return false;
        }
        lobby.pendingInvites.remove(playerUuid);
        lobby.members.add(playerUuid);
        if (lobby.ownerName.isEmpty() && playerName != null) {
            lobby.ownerName = playerName;
        }
        markDirtyAndSync();
        return true;
    }

    public boolean addMemberToLobby(UUID lobbyId, UUID playerUuid) {
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null || playerUuid == null) {
            return false;
        }
        if (lobby.status != LobbyStatus.OPEN) {
            return false;
        }
        if (getLobbyByMember(playerUuid) != null) {
            return false;
        }
        int currentSize = 1 + lobby.members.size();
        if (currentSize >= getMaxPartySize()) {
            return false;
        }
        lobby.members.add(playerUuid);
        markDirtyAndSync();
        return true;
    }

    public boolean declineInvite(UUID lobbyId, UUID playerUuid) {
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null || playerUuid == null) {
            return false;
        }
        boolean removed = lobby.pendingInvites.remove(playerUuid) != null;
        if (removed) {
            markDirtyAndSync();
        }
        return removed;
    }

    public boolean leaveLobby(UUID playerUuid) {
        Lobby lobby = getLobbyByMember(playerUuid);
        if (lobby == null || playerUuid == null) {
            return false;
        }
        if (lobby.status == LobbyStatus.IN_DUNGEON) {
            return false;
        }
        if (lobby.ownerUuid.equals(playerUuid)) {
            if (transferOrDissolveLobby(lobby, playerUuid)) {
                lobbies.remove(lobby);
                lobbyInstanceMap.remove(lobby.id);
            }
        } else {
            lobby.members.remove(playerUuid);
        }
        offlineSinceTick.remove(playerUuid);
        markDirtyAndSync();
        return true;
    }

    public boolean disbandLobby(UUID ownerUuid) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid)) {
            return false;
        }
        if (lobby.status == LobbyStatus.IN_DUNGEON) {
            return false;
        }
        for (UUID member : lobby.members) {
            offlineSinceTick.remove(member);
        }
        offlineSinceTick.remove(ownerUuid);
        boolean removed = lobbies.remove(lobby);
        if (removed) {
            lobbyInstanceMap.remove(lobby.id);
            markDirtyAndSync();
        }
        return removed;
    }

    public boolean kickFromLobby(UUID ownerUuid, UUID targetUuid) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid) || targetUuid == null) {
            return false;
        }
        if (lobby.status == LobbyStatus.IN_DUNGEON || ownerUuid.equals(targetUuid)) {
            return false;
        }
        boolean removed = lobby.members.remove(targetUuid);
        if (removed) {
            offlineSinceTick.remove(targetUuid);
            markDirtyAndSync();
        }
        return removed;
    }

    public boolean setLobbyVisibility(UUID ownerUuid, LobbyVisibility visibility) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid) || visibility == null) {
            return false;
        }
        if (lobby.status == LobbyStatus.IN_DUNGEON) {
            return false;
        }
        if (lobby.visibility == visibility) {
            return true;
        }
        lobby.visibility = visibility;
        markDirtyAndSync();
        return true;
    }

    public boolean setLobbyStatus(UUID lobbyId, LobbyStatus status) {
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null || status == null) {
            return false;
        }
        if (lobby.status == status) {
            return true;
        }
        lobby.status = status;
        markDirtyAndSync();
        return true;
    }

    public boolean setLobbyDifficulty(UUID ownerUuid, RaidDifficulty difficulty) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid) || difficulty == null) {
            return false;
        }
        if (lobby.status == LobbyStatus.IN_DUNGEON) {
            return false;
        }
        if (lobby.selectedDifficulty == difficulty) {
            return true;
        }
        lobby.selectedDifficulty = difficulty;
        markDirtyAndSync();
        return true;
    }

    public boolean setLobbyHardcoreEnabled(UUID ownerUuid, boolean hardcoreEnabled) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid)) {
            return false;
        }
        if (lobby.status == LobbyStatus.IN_DUNGEON) {
            return false;
        }
        if (lobby.hardcoreEnabled == hardcoreEnabled) {
            return true;
        }
        lobby.hardcoreEnabled = hardcoreEnabled;
        markDirtyAndSync();
        return true;
    }

    public boolean startRaid(ServerLevel world, Lobby lobby) {
        if (world == null || lobby == null || lobby.status == LobbyStatus.IN_DUNGEON) {
            return false;
        }
        RaidInstanceState instance = reserveFreeInstance();
        if (instance == null) {
            if (lobby.status != LobbyStatus.QUEUED) {
                lobby.status = LobbyStatus.QUEUED;
                markDirtyAndSync();
            }
            return false;
        }

        ServerLevel spawnerLevel = world.getServer().getLevel(instance.dimension());
        if (spawnerLevel == null || !spawnerLevel.isLoaded(instance.spawnerPos())) {
            releaseReservedInstance(instance);
            return false;
        }
        BlockEntity be = spawnerLevel.getBlockEntity(instance.spawnerPos());
        if (!(be instanceof BossSpawnerBlockEntity spawner)) {
            releaseReservedInstance(instance);
            return false;
        }

        List<ServerPlayer> players = resolveOnlineLobbyPlayers(lobby, world.getServer());
        if (players.isEmpty()) {
            releaseReservedInstance(instance);
            return false;
        }

        spawner.startBattle(
                spawnerLevel,
                players,
                lobby.selectedDifficulty,
                this.worldPosition,
                world.dimension(),
                lobby.hardcoreEnabled,
                spawner.battleTimeLimitTicks
        );

        for (ServerPlayer player : players) {
            BusyStateCompat.setBusy(player.getUUID(), BUSY_REASON);
        }

        lobby.status = LobbyStatus.IN_DUNGEON;
        lobbyInstanceMap.put(lobby.id, instance);
        markDirtyAndSync();
        return true;
    }

    @Override
    public void onRaidEnded(
            BlockPos spawnerPos,
            boolean wasWin,
            RaidDifficulty difficulty,
            List<String> playerNames,
            int timeSeconds
    ) {
        UUID lobbyId = null;
        RaidInstanceState mappedInstance = null;
        for (Map.Entry<UUID, RaidInstanceState> entry : lobbyInstanceMap.entrySet()) {
            RaidInstanceState state = entry.getValue();
            if (state != null && state.spawnerPos().equals(spawnerPos)) {
                lobbyId = entry.getKey();
                mappedInstance = state;
                break;
            }
        }

        if (wasWin && playerNames != null) {
            for (String name : playerNames) {
                upsertLeaderboardEntry(difficulty != null ? difficulty : RaidDifficulty.NORMAL, name, Math.max(0, timeSeconds));
            }
            payRewardToPlayers(playerNames);
        }

        RaidInstanceState target = mappedInstance != null ? mappedInstance : findInstanceByPos(spawnerPos);
        if (target != null) {
            int idx = indexOfInstance(target.spawnerPos(), target.dimension());
            if (idx >= 0) {
                int cooldownTicks = wasWin ? Math.max(0, respawnTimeTicks) : 0;
                InstanceStatus nextStatus = cooldownTicks > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
                instances.set(idx, target.withStatus(nextStatus, cooldownTicks));
            }
        }

        Lobby lobby = lobbyId != null ? getLobbyById(lobbyId) : null;
        if (lobby != null) {
            clearBusyForLobbyPlayers(lobby);
            lobbies.remove(lobby);
            lobbyInstanceMap.remove(lobby.id);
        } else {
            clearBusyByNames(playerNames);
            if (lobbyId != null) {
                lobbyInstanceMap.remove(lobbyId);
            }
        }

        promoteNextQueuedLobby();
        markDirtyAndSync();
    }

    private void tickCooldowns(ServerLevel level) {
        boolean changed = false;
        for (int i = 0; i < instances.size(); i++) {
            RaidInstanceState instance = instances.get(i);
            if (instance.status() != InstanceStatus.COOLDOWN) {
                continue;
            }
            int remaining = Math.max(0, instance.cooldownTicksRemaining() - 1);
            InstanceStatus next = remaining > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
            instances.set(i, instance.withStatus(next, remaining));
            changed = true;
        }
        if (changed) {
            promoteNextQueuedLobby();
            markDirtyAndSync();
        }
    }

    private void tickLobbies(ServerLevel level) {
        long now = level.getGameTime();
        boolean changed = cleanupExpiredInvites(now);
        if (now < nextLobbyCleanupTick) {
            if (changed) {
                markDirtyAndSync();
            }
            return;
        }
        nextLobbyCleanupTick = now + LOBBY_CLEANUP_INTERVAL_TICKS;
        changed |= cleanupStaleOfflineMembers(level, now);
        if (changed) {
            markDirtyAndSync();
        }
    }

    private void pruneMissingInstances(ServerLevel level) {
        instanceValidationTickCounter++;
        if (instanceValidationTickCounter < INSTANCE_VALIDATION_INTERVAL_TICKS) {
            return;
        }
        instanceValidationTickCounter = 0;
        boolean changed = false;
        for (int i = instances.size() - 1; i >= 0; i--) {
            RaidInstanceState instance = instances.get(i);
            if (instance.status() == InstanceStatus.RUNNING) {
                continue;
            }
            ServerLevel spawnerLevel = level.getServer().getLevel(instance.dimension());
            if (spawnerLevel == null) {
                instances.remove(i);
                changed = true;
                continue;
            }
            if (!spawnerLevel.isLoaded(instance.spawnerPos())) {
                continue;
            }
            if (!(spawnerLevel.getBlockEntity(instance.spawnerPos()) instanceof BossSpawnerBlockEntity)) {
                instances.remove(i);
                changed = true;
            }
        }
        if (changed) {
            markDirtyAndSync();
        }
    }

    private boolean cleanupExpiredInvites(long now) {
        boolean changed = false;
        for (Lobby lobby : lobbies) {
            Iterator<Map.Entry<UUID, Long>> iterator = lobby.pendingInvites.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> invite = iterator.next();
                long expireAt = invite.getValue() != null ? invite.getValue() : (now - INVITE_EXPIRY_TICKS);
                if (expireAt <= now) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        return changed;
    }

    private boolean cleanupStaleOfflineMembers(ServerLevel level, long now) {
        boolean changed = false;
        List<Lobby> dissolved = new ArrayList<>();
        for (Lobby lobby : lobbies) {
            if (isPlayerStaleOffline(level, lobby.ownerUuid, now)) {
                changed = true;
                if (transferOrDissolveLobby(lobby, lobby.ownerUuid)) {
                    dissolved.add(lobby);
                    continue;
                }
            }
            Iterator<UUID> memberIt = lobby.members.iterator();
            while (memberIt.hasNext()) {
                UUID memberUuid = memberIt.next();
                if (isPlayerStaleOffline(level, memberUuid, now)) {
                    memberIt.remove();
                    offlineSinceTick.remove(memberUuid);
                    changed = true;
                }
            }
            if (lobby.ownerUuid.equals(EMPTY_UUID)) {
                dissolved.add(lobby);
            }
        }
        if (!dissolved.isEmpty()) {
            for (Lobby lobby : dissolved) {
                lobbyInstanceMap.remove(lobby.id);
            }
            lobbies.removeAll(dissolved);
            changed = true;
        }
        return changed;
    }

    private boolean isPlayerStaleOffline(ServerLevel level, UUID playerUuid, long now) {
        if (playerUuid == null || playerUuid.equals(EMPTY_UUID)) {
            return true;
        }
        if (level.getServer().getPlayerList().getPlayer(playerUuid) != null) {
            offlineSinceTick.remove(playerUuid);
            return false;
        }
        long offlineSince = offlineSinceTick.computeIfAbsent(playerUuid, ignored -> now);
        return now - offlineSince >= OFFLINE_GRACE_TICKS;
    }

    private boolean transferOrDissolveLobby(Lobby lobby, UUID leavingOwner) {
        if (lobby == null) {
            return true;
        }
        if (leavingOwner != null) {
            offlineSinceTick.remove(leavingOwner);
        }
        while (!lobby.members.isEmpty()) {
            UUID candidate = lobby.members.remove(0);
            lobby.ownerUuid = candidate;
            ServerPlayer newOwner =
                    level instanceof ServerLevel serverLevel ? serverLevel.getServer().getPlayerList().getPlayer(candidate) : null;
            lobby.ownerName = newOwner != null ? newOwner.getGameProfile().getName() : "Unknown";
            if (newOwner != null) {
                newOwner.sendSystemMessage(Component.translatable("message.arenas_ld.lobby_owner_transferred"));
            }
            return false;
        }
        lobby.ownerUuid = EMPTY_UUID;
        lobby.ownerName = "";
        return true;
    }

    private void clearBusyForLobbyPlayers(Lobby lobby) {
        if (!(level instanceof ServerLevel serverLevel) || lobby == null) {
            return;
        }
        List<UUID> players = new ArrayList<>();
        if (!lobby.ownerUuid.equals(EMPTY_UUID)) {
            players.add(lobby.ownerUuid);
        }
        players.addAll(lobby.members);

        for (UUID playerId : players) {
            BusyStateCompat.clearBusy(playerId, BUSY_REASON);
        }
    }

    private void payRewardToPlayers(List<String> playerNames) {
        long amount = getRewardCurrencyPerPlayer();
        if (amount <= 0L || playerNames == null || playerNames.isEmpty()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var server = serverLevel.getServer();
        for (String name : playerNames) {
            if (name == null || name.isBlank()) {
                continue;
            }
            net.ledok.arenas_ld.util.EconomyCompat.deliverCurrencyByName(server, name, amount, "RAID_REWARD");
        }
    }

    private void clearBusyByNames(List<String> playerNames) {
        if (!(level instanceof ServerLevel serverLevel) || playerNames == null || playerNames.isEmpty()) {
            return;
        }
        for (String playerName : playerNames) {
            if (playerName == null || playerName.isBlank()) {
                continue;
            }
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayerByName(playerName);
            if (player == null) {
                continue;
            }
            BusyStateCompat.clearBusy(player.getUUID(), BUSY_REASON);
        }
    }

    private List<ServerPlayer> resolveOnlineLobbyPlayers(Lobby lobby, net.minecraft.server.MinecraftServer server) {
        List<ServerPlayer> players = new ArrayList<>();
        ServerPlayer owner = server.getPlayerList().getPlayer(lobby.ownerUuid);
        if (owner != null) {
            players.add(owner);
        }
        for (UUID member : lobby.members) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                players.add(player);
            }
        }
        return players;
    }

    private void promoteNextQueuedLobby() {
        if (!hasAnyFreeInstance()) {
            return;
        }
        Lobby nextQueued = null;
        for (Lobby lobby : lobbies) {
            if (lobby.status == LobbyStatus.QUEUED) {
                nextQueued = lobby;
                break;
            }
        }
        if (nextQueued == null) {
            return;
        }
        nextQueued.status = LobbyStatus.OPEN;
        notifyLobbyMembers(nextQueued, Component.translatable("message.arenas_ld.lobby_slot_available"));
    }

    private void notifyLobbyMembers(Lobby lobby, Component message) {
        if (!(level instanceof ServerLevel serverLevel) || lobby == null) {
            return;
        }
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(lobby.ownerUuid);
        if (owner != null) {
            owner.sendSystemMessage(message);
        }
        for (UUID member : lobby.members) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    private void releaseReservedInstance(RaidInstanceState reserved) {
        int idx = indexOfInstance(reserved.spawnerPos(), reserved.dimension());
        if (idx < 0) {
            return;
        }
        RaidInstanceState current = instances.get(idx);
        if (current.status() == InstanceStatus.RUNNING) {
            instances.set(idx, current.withStatus(InstanceStatus.FREE, 0));
            markDirtyAndSync();
        }
    }

    private RaidInstanceState findInstanceByPos(BlockPos spawnerPos) {
        if (spawnerPos == null) {
            return null;
        }
        for (RaidInstanceState instance : instances) {
            if (instance.spawnerPos().equals(spawnerPos)) {
                return instance;
            }
        }
        return null;
    }

    private int indexOfInstance(BlockPos spawnerPos, ResourceKey<Level> dimension) {
        for (int i = 0; i < instances.size(); i++) {
            RaidInstanceState state = instances.get(i);
            if (state.spawnerPos().equals(spawnerPos) && state.dimension().equals(dimension)) {
                return i;
            }
        }
        return -1;
    }

    private boolean sameInstance(RaidInstanceState state, BlockPos spawnerPos, ResourceKey<Level> dimension) {
        return state != null
                && state.spawnerPos().equals(spawnerPos)
                && state.dimension().equals(dimension);
    }

    private void markDirtyAndSync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putInt("RespawnTimeTicks", respawnTimeTicks);
        nbt.putInt("MaxPartySize", maxPartySize);
        nbt.putLong("RewardCurrencyPerPlayer", rewardCurrencyPerPlayer);

        ListTag instancesList = new ListTag();
        for (RaidInstanceState instance : instances) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("SpawnerPos", instance.spawnerPos().asLong());
            tag.putString("Dimension", instance.dimension().location().toString());
            tag.putString("Status", instance.status().name());
            tag.putInt("CooldownTicksRemaining", instance.cooldownTicksRemaining());
            instancesList.add(tag);
        }
        nbt.put("Instances", instancesList);

        ListTag lobbiesList = new ListTag();
        for (Lobby lobby : lobbies) {
            lobbiesList.add(lobby.toNbt());
        }
        nbt.put("Lobbies", lobbiesList);

        ListTag lobbyMapList = new ListTag();
        for (Map.Entry<UUID, RaidInstanceState> entry : lobbyInstanceMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putUUID("LobbyId", entry.getKey());
            tag.putLong("SpawnerPos", entry.getValue().spawnerPos().asLong());
            tag.putString("Dimension", entry.getValue().dimension().location().toString());
            tag.putString("Status", entry.getValue().status().name());
            tag.putInt("CooldownTicksRemaining", entry.getValue().cooldownTicksRemaining());
            lobbyMapList.add(tag);
        }
        nbt.put("LobbyInstanceMap", lobbyMapList);

        CompoundTag lbTag = new CompoundTag();
        for (RaidDifficulty difficulty : RaidDifficulty.values()) {
            List<RaidLeaderboardEntry> list = leaderboardByDifficulty.getOrDefault(difficulty, List.of());
            ListTag tierList = new ListTag();
            for (RaidLeaderboardEntry entry : list) {
                tierList.add(entry.toNbt());
            }
            lbTag.put(difficulty.name(), tierList);
        }
        nbt.put("LeaderboardByDifficulty", lbTag);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        respawnTimeTicks = nbt.contains("RespawnTimeTicks") ? nbt.getInt("RespawnTimeTicks") : DEFAULT_RESPAWN_TIME_TICKS;
        maxPartySize = nbt.contains("MaxPartySize") ? nbt.getInt("MaxPartySize") : DEFAULT_MAX_PARTY_SIZE;
        rewardCurrencyPerPlayer = nbt.contains("RewardCurrencyPerPlayer") ? Math.max(0L, nbt.getLong("RewardCurrencyPerPlayer")) : 0L;

        instances.clear();
        if (nbt.contains("Instances", Tag.TAG_LIST)) {
            ListTag instancesList = nbt.getList("Instances", Tag.TAG_COMPOUND);
            for (Tag t : instancesList) {
                CompoundTag tag = (CompoundTag) t;
                BlockPos spawnerPos = BlockPos.of(tag.getLong("SpawnerPos"));
                ResourceKey<Level> dimension = readDimensionOrDefault(tag.getString("Dimension"), Level.OVERWORLD);
                InstanceStatus status;
                try {
                    status = InstanceStatus.valueOf(tag.getString("Status"));
                } catch (Exception ignored) {
                    status = InstanceStatus.FREE;
                }
                int cooldown = tag.getInt("CooldownTicksRemaining");
                instances.add(new RaidInstanceState(spawnerPos, dimension, status, cooldown));
            }
        }

        lobbies.clear();
        if (nbt.contains("Lobbies", Tag.TAG_LIST)) {
            ListTag lobbiesList = nbt.getList("Lobbies", Tag.TAG_COMPOUND);
            for (Tag t : lobbiesList) {
                lobbies.add(Lobby.fromNbt((CompoundTag) t));
            }
        }

        lobbyInstanceMap.clear();
        if (nbt.contains("LobbyInstanceMap", Tag.TAG_LIST)) {
            ListTag mappingList = nbt.getList("LobbyInstanceMap", Tag.TAG_COMPOUND);
            for (Tag t : mappingList) {
                CompoundTag tag = (CompoundTag) t;
                if (!tag.hasUUID("LobbyId")) {
                    continue;
                }
                UUID lobbyId = tag.getUUID("LobbyId");
                if (getLobbyById(lobbyId) == null) {
                    continue;
                }
                BlockPos spawnerPos = BlockPos.of(tag.getLong("SpawnerPos"));
                ResourceKey<Level> dimension = readDimensionOrDefault(tag.getString("Dimension"), Level.OVERWORLD);
                InstanceStatus status;
                try {
                    status = InstanceStatus.valueOf(tag.getString("Status"));
                } catch (Exception ignored) {
                    status = InstanceStatus.FREE;
                }
                int cooldown = tag.getInt("CooldownTicksRemaining");
                lobbyInstanceMap.put(lobbyId, new RaidInstanceState(spawnerPos, dimension, status, cooldown));
            }
        }

        leaderboardByDifficulty.clear();
        if (nbt.contains("LeaderboardByDifficulty", Tag.TAG_COMPOUND)) {
            CompoundTag lbTag = nbt.getCompound("LeaderboardByDifficulty");
            for (RaidDifficulty difficulty : RaidDifficulty.values()) {
                if (!lbTag.contains(difficulty.name(), Tag.TAG_LIST)) {
                    continue;
                }
                ListTag tierList = lbTag.getList(difficulty.name(), Tag.TAG_COMPOUND);
                List<RaidLeaderboardEntry> entries = new ArrayList<>();
                for (Tag t : tierList) {
                    entries.add(RaidLeaderboardEntry.fromNbt((CompoundTag) t));
                }
                leaderboardByDifficulty.put(difficulty, entries);
            }
        }
    }

    private static ResourceKey<Level> readDimensionOrDefault(String id, ResourceKey<Level> fallback) {
        if (id == null || id.isBlank()) {
            return fallback;
        }
        try {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
        } catch (Exception ignored) {
            return fallback;
        }
    }

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
        return new RaidControllerData(this.worldPosition);
    }
}
