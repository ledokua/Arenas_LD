package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.screen.DungeonControllerData;
import net.ledok.arenas_ld.screen.DungeonControllerScreenHandler;
import net.ledok.arenas_ld.util.DifficultyTier;
import net.ledok.arenas_ld.util.DungeonInstanceRef;
import net.ledok.arenas_ld.util.DungeonLeaderboardEntry;
import net.ledok.arenas_ld.util.InstanceState;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.arenas_ld.util.Lobby;
import net.ledok.arenas_ld.util.LobbyStatus;
import net.ledok.arenas_ld.util.LobbyVisibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
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

public class DungeonControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<DungeonControllerData> {
    public record ControllerKey(BlockPos pos, ResourceKey<Level> dimension) {}
    private static final Set<ControllerKey> CONTROLLERS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final long INVITE_EXPIRY_TICKS = 5L * 60L * 20L;
    private static final long LOBBY_CLEANUP_INTERVAL_TICKS = 30L * 20L;
    private static final int INSTANCE_VALIDATION_INTERVAL_TICKS = 100;
    private static final int DEFAULT_RESPAWN_TIME_TICKS = 6000;
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    public boolean isLocked = false;
    public int remainingDungeonTimeSeconds = 0;
    public int dungeonCooldownSeconds = 0;
    public boolean hardcoreEnabled = false;
    public DifficultyTier selectedTier = DifficultyTier.NORMAL;
    private final Map<DifficultyTier, List<DungeonLeaderboardEntry>> leaderboardByTier = new EnumMap<>(DifficultyTier.class);
    public List<InstanceState> instances = new ArrayList<>();
    public int respawnTimeTicks = DEFAULT_RESPAWN_TIME_TICKS;
    public List<Lobby> lobbies = new ArrayList<>();
    public Map<UUID, DungeonInstanceRef> lobbyInstanceMap = new HashMap<>();
    public int maxPartySize = 4;
    private final Map<UUID, Long> offlineSinceTick = new HashMap<>();
    private long nextLobbyCleanupTick = 0L;
    private int instanceValidationTickCounter = 0;

    public DungeonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_CONTROLLER_BLOCK_ENTITY, pos, state);
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

    public void reset() {
        isLocked = false;
        remainingDungeonTimeSeconds = 0;
        dungeonCooldownSeconds = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void setHardcoreEnabled(boolean hardcoreEnabled) {
        this.hardcoreEnabled = hardcoreEnabled;
        setChanged();
    }

    public void setSelectedTier(DifficultyTier selectedTier) {
        this.selectedTier = selectedTier != null ? selectedTier : DifficultyTier.NORMAL;
        setChanged();
    }

    public List<DungeonLeaderboardEntry> getLeaderboardForTier(DifficultyTier tier) {
        return leaderboardByTier.getOrDefault(tier, List.of());
    }

    public void upsertLeaderboardEntry(DifficultyTier tier, String playerName, int timeSeconds) {
        List<DungeonLeaderboardEntry> list =
                leaderboardByTier.computeIfAbsent(tier, t -> new ArrayList<>());
        list.removeIf(e -> e.playerName.equals(playerName) && e.timeSeconds <= timeSeconds);
        boolean alreadyBetter = list.stream().anyMatch(e -> e.playerName.equals(playerName));
        if (alreadyBetter) return;
        int idx = 0;
        while (idx < list.size() && list.get(idx).timeSeconds <= timeSeconds) idx++;
        list.add(idx, new DungeonLeaderboardEntry(playerName, timeSeconds));
        if (list.size() > 20) list.remove(list.size() - 1);
        setChanged();
        if (level instanceof ServerLevel sl) {
            sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasAnyFreeInstance() {
        return instances.stream().anyMatch(instance -> instance.status() == InstanceStatus.FREE);
    }

    public DungeonInstanceRef reserveFreeInstance() {
        for (int i = 0; i < instances.size(); i++) {
            InstanceState instance = instances.get(i);
            if (instance.status() == InstanceStatus.FREE) {
                instances.set(i, new InstanceState(instance.ref(), InstanceStatus.RUNNING, 0));
                this.dungeonCooldownSeconds = getNextAvailableCooldownSeconds();
                setChanged();
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
                return instance.ref();
            }
        }
        return null;
    }

    public void releaseReservedInstance(DungeonInstanceRef ref) {
        if (ref == null) {
            return;
        }
        int idx = indexOfInstance(ref);
        if (idx < 0) {
            return;
        }
        InstanceState current = instances.get(idx);
        if (current.status() == InstanceStatus.RUNNING) {
            instances.set(idx, new InstanceState(ref, InstanceStatus.FREE, 0));
            dungeonCooldownSeconds = getNextAvailableCooldownSeconds();
            setChanged();
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public void onRunEnded(DungeonInstanceRef ref, boolean wasWin) {
        if (ref == null) {
            return;
        }
        int idx = indexOfInstance(ref);
        if (idx < 0) {
            return;
        }
        int cooldownTicks = wasWin ? Math.max(0, respawnTimeTicks) : 0;
        InstanceStatus nextStatus = cooldownTicks > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
        instances.set(idx, new InstanceState(ref, nextStatus, cooldownTicks));
        disbandLobbyForInstance(ref);
        this.isLocked = instances.stream().anyMatch(instance -> instance.status() == InstanceStatus.RUNNING);
        if (!this.isLocked) {
            this.remainingDungeonTimeSeconds = 0;
        }
        this.dungeonCooldownSeconds = getNextAvailableCooldownSeconds();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            if (nextStatus == InstanceStatus.FREE && promoteNextQueuedLobby(serverLevel)) {
                setChanged();
            }
            if (nextStatus == InstanceStatus.FREE) {
                ArenasLdMod.DUNGEON_BOSS_MANAGER.onControllerInstanceFreed(serverLevel.getServer(), worldPosition, serverLevel.dimension());
            }
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void disbandLobbyForInstance(DungeonInstanceRef ref) {
        UUID lobbyIdToDisband = null;
        for (Map.Entry<UUID, DungeonInstanceRef> entry : lobbyInstanceMap.entrySet()) {
            DungeonInstanceRef mappedRef = entry.getValue();
            if (mappedRef != null
                    && mappedRef.spawnerPos().equals(ref.spawnerPos())
                    && mappedRef.dimension().equals(ref.dimension())) {
                lobbyIdToDisband = entry.getKey();
                break;
            }
        }
        if (lobbyIdToDisband == null) {
            return;
        }
        Lobby lobby = getLobbyById(lobbyIdToDisband);
        if (lobby != null) {
            for (UUID member : lobby.members) {
                offlineSinceTick.remove(member);
            }
            if (lobby.ownerUuid != null) {
                offlineSinceTick.remove(lobby.ownerUuid);
            }
            lobbies.remove(lobby);
        }
        lobbyInstanceMap.remove(lobbyIdToDisband);
    }

    public int getNextAvailableCooldownSeconds() {
        if (instances.stream().anyMatch(instance -> instance.status() == InstanceStatus.FREE)) {
            return 0;
        }
        int minTicks = Integer.MAX_VALUE;
        for (InstanceState instance : instances) {
            if (instance.status() == InstanceStatus.COOLDOWN) {
                minTicks = Math.min(minTicks, Math.max(0, instance.cooldownTicksRemaining()));
            }
        }
        if (minTicks == Integer.MAX_VALUE) {
            return -1;
        }
        return (minTicks + 19) / 20;
    }

    public int getRespawnTimeTicks() {
        return Math.max(0, respawnTimeTicks);
    }

    public void setRespawnTimeTicks(int respawnTimeTicks) {
        int clampedTicks = Math.max(0, respawnTimeTicks);
        if (this.respawnTimeTicks == clampedTicks) {
            return;
        }
        this.respawnTimeTicks = clampedTicks;

        boolean freedAnyInstance = false;
        for (int i = 0; i < instances.size(); i++) {
            InstanceState instance = instances.get(i);
            if (instance.status() != InstanceStatus.COOLDOWN) {
                continue;
            }
            int updatedRemaining = Math.min(instance.cooldownTicksRemaining(), clampedTicks);
            InstanceStatus updatedStatus = updatedRemaining > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
            if (updatedStatus == InstanceStatus.FREE) {
                freedAnyInstance = true;
            }
            if (updatedRemaining != instance.cooldownTicksRemaining() || updatedStatus != instance.status()) {
                instances.set(i, new InstanceState(instance.ref(), updatedStatus, updatedRemaining));
            }
        }

        int nextCooldown = getNextAvailableCooldownSeconds();
        if (dungeonCooldownSeconds != nextCooldown) {
            dungeonCooldownSeconds = nextCooldown;
        }
        if (freedAnyInstance && level instanceof ServerLevel serverLevel) {
            promoteNextQueuedLobby(serverLevel);
        }
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (freedAnyInstance && level instanceof ServerLevel serverLevel) {
            ArenasLdMod.DUNGEON_BOSS_MANAGER.onControllerInstanceFreed(serverLevel.getServer(), worldPosition, serverLevel.dimension());
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, DungeonControllerBlockEntity be) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        be.tickCooldowns(serverLevel);
        be.tickLobbies(serverLevel);
        be.pruneMissingInstances(serverLevel);
    }

    private void tickCooldowns(ServerLevel level) {
        boolean changed = false;
        boolean freedAnyInstance = false;
        for (int i = 0; i < instances.size(); i++) {
            InstanceState instance = instances.get(i);
            if (instance.status() != InstanceStatus.COOLDOWN) {
                continue;
            }
            int remaining = Math.max(0, instance.cooldownTicksRemaining() - 1);
            InstanceStatus nextStatus = remaining > 0 ? InstanceStatus.COOLDOWN : InstanceStatus.FREE;
            if (nextStatus == InstanceStatus.FREE) {
                freedAnyInstance = true;
            }
            instances.set(i, new InstanceState(instance.ref(), nextStatus, remaining));
            changed = true;
        }
        int nextCooldown = getNextAvailableCooldownSeconds();
        if (dungeonCooldownSeconds != nextCooldown) {
            dungeonCooldownSeconds = nextCooldown;
            changed = true;
        }
        if (freedAnyInstance && promoteNextQueuedLobby(level)) {
            changed = true;
        }
        if (changed) {
            setChanged();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (freedAnyInstance) {
            ArenasLdMod.DUNGEON_BOSS_MANAGER.onControllerInstanceFreed(level.getServer(), worldPosition, level.dimension());
        }
    }

    private boolean promoteNextQueuedLobby(ServerLevel level) {
        if (!hasAnyFreeInstance()) {
            return false;
        }
        Lobby nextQueued = null;
        for (Lobby lobby : lobbies) {
            if (lobby.status == LobbyStatus.QUEUED) {
                nextQueued = lobby;
                break;
            }
        }
        if (nextQueued == null) {
            return false;
        }
        nextQueued.status = LobbyStatus.OPEN;
        notifyLobbyMembers(level, nextQueued, Component.translatable("message.arenas_ld.lobby_slot_available"));
        return true;
    }

    private void notifyLobbyMembers(ServerLevel level, Lobby lobby, Component message) {
        if (lobby.ownerUuid != null && !lobby.ownerUuid.equals(EMPTY_UUID)) {
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(lobby.ownerUuid);
            if (owner != null) {
                owner.sendSystemMessage(message);
            }
        }
        for (UUID member : lobby.members) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(member);
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    private int indexOfInstance(DungeonInstanceRef ref) {
        for (int i = 0; i < instances.size(); i++) {
            InstanceState instance = instances.get(i);
            if (instance.ref().spawnerPos().equals(ref.spawnerPos()) && instance.ref().dimension().equals(ref.dimension())) {
                return i;
            }
        }
        return -1;
    }

    public boolean addInstance(DungeonInstanceRef ref) {
        if (ref == null) {
            return false;
        }
        if (indexOfInstance(ref) >= 0) {
            return false;
        }
        instances.add(new InstanceState(ref, InstanceStatus.FREE, 0));
        dungeonCooldownSeconds = getNextAvailableCooldownSeconds();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public boolean removeInstance(DungeonInstanceRef ref) {
        if (ref == null) {
            return false;
        }
        int idx = indexOfInstance(ref);
        if (idx < 0) {
            return false;
        }
        InstanceState instance = instances.get(idx);
        if (instance.status() == InstanceStatus.RUNNING) {
            return false;
        }
        instances.remove(idx);
        lobbyInstanceMap.values().removeIf(mapped -> mapped != null
                && mapped.spawnerPos().equals(ref.spawnerPos())
                && mapped.dimension().equals(ref.dimension()));
        dungeonCooldownSeconds = getNextAvailableCooldownSeconds();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public boolean removeInstanceForce(DungeonInstanceRef ref) {
        if (ref == null) {
            return false;
        }
        int idx = indexOfInstance(ref);
        if (idx < 0) {
            return false;
        }
        instances.remove(idx);
        lobbyInstanceMap.values().removeIf(mapped -> mapped != null
                && mapped.spawnerPos().equals(ref.spawnerPos())
                && mapped.dimension().equals(ref.dimension()));
        dungeonCooldownSeconds = getNextAvailableCooldownSeconds();
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        return true;
    }

    public static int unlinkSpawnerFromAllControllers(MinecraftServer server, DungeonInstanceRef ref) {
        if (server == null || ref == null) {
            return 0;
        }
        int removedCount = 0;
        for (ControllerKey key : getControllers()) {
            ServerLevel controllerLevel = server.getLevel(key.dimension());
            if (controllerLevel == null) {
                continue;
            }
            BlockEntity be = controllerLevel.getBlockEntity(key.pos());
            if (be instanceof DungeonControllerBlockEntity controller && controller.removeInstanceForce(ref)) {
                removedCount++;
            }
        }
        return removedCount;
    }

    public static int updateControllerRespawnTimeForSpawner(MinecraftServer server, DungeonInstanceRef ref, int respawnTimeTicks) {
        if (server == null || ref == null) {
            return 0;
        }
        int updatedCount = 0;
        for (ControllerKey key : getControllers()) {
            ServerLevel controllerLevel = server.getLevel(key.dimension());
            if (controllerLevel == null) {
                continue;
            }
            BlockEntity be = controllerLevel.getBlockEntity(key.pos());
            if (!(be instanceof DungeonControllerBlockEntity controller)) {
                continue;
            }
            if (controller.indexOfInstance(ref) < 0) {
                continue;
            }
            controller.setRespawnTimeTicks(respawnTimeTicks);
            updatedCount++;
        }
        return updatedCount;
    }

    public void assignLobbyToInstance(UUID lobbyId, DungeonInstanceRef instanceRef) {
        if (lobbyId == null || instanceRef == null) {
            return;
        }
        lobbyInstanceMap.put(lobbyId, instanceRef);
        setChanged();
    }

    public Lobby createLobby(UUID ownerUuid, String ownerName) {
        if (ownerUuid == null || ownerName == null) {
            return null;
        }
        if (getLobbyByMember(ownerUuid) != null) {
            return null;
        }
        Lobby lobby = new Lobby();
        lobby.ownerUuid = ownerUuid;
        lobby.ownerName = ownerName;
        lobby.selectedTier = selectedTier;
        lobby.hardcoreEnabled = hardcoreEnabled;
        lobby.visibility = LobbyVisibility.OPEN;
        lobby.status = LobbyStatus.OPEN;
        lobbies.add(lobby);
        markDirtyAndSync();
        return lobby;
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

    public boolean invitePlayer(UUID ownerUuid, UUID targetUuid, long expireAtTick) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid)) {
            return false;
        }
        if (getLobbyByMember(targetUuid) != null) {
            return false;
        }
        int currentSize = 1 + lobby.members.size();
        if (currentSize >= Math.max(1, maxPartySize)) {
            return false;
        }
        lobby.pendingInvites.put(targetUuid, expireAtTick);
        markDirtyAndSync();
        return true;
    }

    public boolean declineInvite(UUID lobbyId, UUID playerUuid) {
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null) {
            return false;
        }
        boolean removed = lobby.pendingInvites.remove(playerUuid) != null;
        if (removed) {
            markDirtyAndSync();
        }
        return removed;
    }

    public boolean acceptInvite(UUID lobbyId, UUID playerUuid, String playerName, long nowTick) {
        Lobby lobby = getLobbyById(lobbyId);
        if (lobby == null) {
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
        if (currentSize >= Math.max(1, maxPartySize)) {
            return false;
        }
        lobby.pendingInvites.remove(playerUuid);
        lobby.members.add(playerUuid);
        if (lobby.ownerName == null || lobby.ownerName.isEmpty()) {
            lobby.ownerName = playerName != null ? playerName : "Unknown";
        }
        markDirtyAndSync();
        return true;
    }

    public boolean leaveLobby(UUID playerUuid) {
        Lobby lobby = getLobbyByMember(playerUuid);
        if (lobby == null) {
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

    public boolean kickFromLobby(UUID ownerUuid, UUID targetUuid) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid)) {
            return false;
        }
        if (ownerUuid.equals(targetUuid)) {
            return false;
        }
        boolean removed = lobby.members.remove(targetUuid);
        if (removed) {
            offlineSinceTick.remove(targetUuid);
            markDirtyAndSync();
        }
        return removed;
    }

    public boolean disbandLobby(UUID ownerUuid) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid)) {
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

    public boolean setLobbyVisibility(UUID ownerUuid, LobbyVisibility visibility) {
        Lobby lobby = getLobbyByMember(ownerUuid);
        if (lobby == null || !lobby.ownerUuid.equals(ownerUuid) || visibility == null) {
            return false;
        }
        if (lobby.visibility == visibility) {
            return true;
        }
        lobby.visibility = visibility;
        markDirtyAndSync();
        return true;
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
            InstanceState instance = instances.get(i);
            if (instance.status() == InstanceStatus.RUNNING) {
                continue;
            }
            ServerLevel spawnerLevel = level.getServer().getLevel(instance.ref().dimension());
            if (spawnerLevel == null) {
                instances.remove(i);
                changed = true;
                continue;
            }
            if (!spawnerLevel.isLoaded(instance.ref().spawnerPos())) {
                continue;
            }
            if (!(spawnerLevel.getBlockEntity(instance.ref().spawnerPos()) instanceof DungeonBossSpawnerBlockEntity)) {
                instances.remove(i);
                changed = true;
            }
        }
        if (changed) {
            dungeonCooldownSeconds = getNextAvailableCooldownSeconds();
            markDirtyAndSync();
        }
    }

    private boolean cleanupExpiredInvites(long now) {
        boolean changed = false;
        for (Lobby lobby : lobbies) {
            Iterator<Map.Entry<UUID, Long>> iterator = lobby.pendingInvites.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                long expireAt = entry.getValue() != null ? entry.getValue() : (now - INVITE_EXPIRY_TICKS);
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
        if (playerUuid == null) {
            return true;
        }
        if (level.getServer().getPlayerList().getPlayer(playerUuid) != null) {
            offlineSinceTick.remove(playerUuid);
            return false;
        }
        return true;
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
            ServerPlayer newOwner = level instanceof ServerLevel serverLevel ? serverLevel.getServer().getPlayerList().getPlayer(candidate) : null;
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

    private void markDirtyAndSync() {
        setChanged();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putBoolean("IsLocked", isLocked);
        nbt.putInt("RemainingDungeonTimeSeconds", remainingDungeonTimeSeconds);
        nbt.putInt("DungeonCooldownSeconds", dungeonCooldownSeconds);
        nbt.putBoolean("HardcoreEnabled", hardcoreEnabled);
        nbt.putString("SelectedTier", selectedTier.name());
        nbt.putInt("RespawnTimeTicks", respawnTimeTicks);
        nbt.putInt("MaxPartySize", maxPartySize);

        ListTag instancesList = new ListTag();
        for (InstanceState instance : instances) {
            CompoundTag tag = new CompoundTag();
            tag.putLong("SpawnerPos", instance.ref().spawnerPos().asLong());
            tag.putString("Dimension", instance.ref().dimension().location().toString());
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

        ListTag lobbyInstanceList = new ListTag();
        for (Map.Entry<UUID, DungeonInstanceRef> entry : lobbyInstanceMap.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.putUUID("LobbyId", entry.getKey());
            tag.putLong("SpawnerPos", entry.getValue().spawnerPos().asLong());
            tag.putString("Dimension", entry.getValue().dimension().location().toString());
            lobbyInstanceList.add(tag);
        }
        nbt.put("LobbyInstanceMap", lobbyInstanceList);

        CompoundTag lbTag = new CompoundTag();
        for (DifficultyTier tier : DifficultyTier.values()) {
            List<DungeonLeaderboardEntry> list = leaderboardByTier.getOrDefault(tier, List.of());
            ListTag tierList = new ListTag();
            for (DungeonLeaderboardEntry entry : list) tierList.add(entry.toNbt());
            lbTag.put(tier.name(), tierList);
        }
        nbt.put("LeaderboardByTier", lbTag);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        isLocked = nbt.getBoolean("IsLocked");
        remainingDungeonTimeSeconds = nbt.getInt("RemainingDungeonTimeSeconds");
        dungeonCooldownSeconds = nbt.getInt("DungeonCooldownSeconds");
        hardcoreEnabled = nbt.getBoolean("HardcoreEnabled");
        selectedTier = DifficultyTier.fromNameOrDefault(nbt.getString("SelectedTier"), DifficultyTier.NORMAL);
        respawnTimeTicks = nbt.contains("RespawnTimeTicks") ? nbt.getInt("RespawnTimeTicks") : DEFAULT_RESPAWN_TIME_TICKS;
        maxPartySize = nbt.contains("MaxPartySize") ? nbt.getInt("MaxPartySize") : 4;
        List<UUID> legacyPartyMembers = new ArrayList<>();
        if (nbt.contains("PartyMembers", Tag.TAG_LIST)) {
            ListTag membersList = nbt.getList("PartyMembers", Tag.TAG_COMPOUND);
            for (Tag t : membersList) {
                legacyPartyMembers.add(((CompoundTag) t).getUUID("uuid"));
            }
        }

        leaderboardByTier.clear();
        if (nbt.contains("LeaderboardByTier", Tag.TAG_COMPOUND)) {
            CompoundTag lbTag = nbt.getCompound("LeaderboardByTier");
            for (DifficultyTier tier : DifficultyTier.values()) {
                if (lbTag.contains(tier.name(), Tag.TAG_LIST)) {
                    ListTag tierList = lbTag.getList(tier.name(), Tag.TAG_COMPOUND);
                    List<DungeonLeaderboardEntry> list = new ArrayList<>();
                    for (Tag t : tierList) list.add(DungeonLeaderboardEntry.fromNbt((CompoundTag) t));
                    leaderboardByTier.put(tier, list);
                }
            }
        }

        instances.clear();
        if (nbt.contains("Instances", Tag.TAG_LIST)) {
            ListTag instancesList = nbt.getList("Instances", Tag.TAG_COMPOUND);
            for (Tag tag : instancesList) {
                CompoundTag instanceTag = (CompoundTag) tag;
                BlockPos spawnerPos = BlockPos.of(instanceTag.getLong("SpawnerPos"));
                ResourceKey<Level> dimension;
                try {
                    dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(instanceTag.getString("Dimension")));
                } catch (Exception ignored) {
                    dimension = Level.OVERWORLD;
                }
                InstanceStatus status = InstanceStatus.FREE;
                if (instanceTag.contains("Status")) {
                    try {
                        status = InstanceStatus.valueOf(instanceTag.getString("Status"));
                    } catch (IllegalArgumentException ignored) {
                        status = InstanceStatus.FREE;
                    }
                }
                int cooldownTicksRemaining = instanceTag.getInt("CooldownTicksRemaining");
                instances.add(new InstanceState(new DungeonInstanceRef(spawnerPos, dimension), status, cooldownTicksRemaining));
            }
        } else if (nbt.contains("DungeonSpawnerPos", Tag.TAG_LONG) && nbt.contains("DungeonSpawnerDimension", Tag.TAG_STRING)) {
            // Migration: old single-spawner controller becomes a one-slot pool.
            BlockPos legacyPos = BlockPos.of(nbt.getLong("DungeonSpawnerPos"));
            ResourceKey<Level> legacyDim;
            try {
                legacyDim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("DungeonSpawnerDimension")));
            } catch (Exception ignored) {
                legacyDim = Level.OVERWORLD;
            }
            if (!legacyPos.equals(BlockPos.ZERO)) {
                instances.add(new InstanceState(new DungeonInstanceRef(legacyPos, legacyDim), InstanceStatus.FREE, 0));
            }
        }

        lobbies.clear();
        if (nbt.contains("Lobbies", Tag.TAG_LIST)) {
            ListTag lobbiesList = nbt.getList("Lobbies", Tag.TAG_COMPOUND);
            for (Tag tag : lobbiesList) {
                lobbies.add(Lobby.fromNbt((CompoundTag) tag));
            }
        } else if (!legacyPartyMembers.isEmpty()) {
            // Migration from old party-members model into a single OPEN lobby.
            Lobby migratedLobby = new Lobby();
            List<UUID> membersSnapshot = new ArrayList<>(legacyPartyMembers);
            membersSnapshot.sort((a, b) -> a.toString().compareToIgnoreCase(b.toString()));
            migratedLobby.ownerUuid = membersSnapshot.get(0);
            migratedLobby.ownerName = "Unknown";
            migratedLobby.visibility = LobbyVisibility.OPEN;
            migratedLobby.selectedTier = selectedTier;
            migratedLobby.hardcoreEnabled = hardcoreEnabled;
            migratedLobby.status = isLocked ? LobbyStatus.IN_DUNGEON : LobbyStatus.OPEN;
            for (int i = 1; i < membersSnapshot.size(); i++) {
                migratedLobby.members.add(membersSnapshot.get(i));
            }
            lobbies.add(migratedLobby);
        }

        lobbyInstanceMap.clear();
        if (nbt.contains("LobbyInstanceMap", Tag.TAG_LIST)) {
            ListTag mappings = nbt.getList("LobbyInstanceMap", Tag.TAG_COMPOUND);
            for (Tag tag : mappings) {
                CompoundTag mapping = (CompoundTag) tag;
                if (!mapping.hasUUID("LobbyId")) {
                    continue;
                }
                UUID lobbyId = mapping.getUUID("LobbyId");
                if (getLobbyById(lobbyId) == null) {
                    continue;
                }
                BlockPos spawnerPos = BlockPos.of(mapping.getLong("SpawnerPos"));
                ResourceKey<Level> dimension;
                try {
                    dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(mapping.getString("Dimension")));
                } catch (Exception ignored) {
                    continue;
                }
                lobbyInstanceMap.put(lobbyId, new DungeonInstanceRef(spawnerPos, dimension));
            }
        }
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arenas_ld.dungeon_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new DungeonControllerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public DungeonControllerData getScreenOpeningData(ServerPlayer player) {
        return new DungeonControllerData(this.worldPosition);
    }
}
