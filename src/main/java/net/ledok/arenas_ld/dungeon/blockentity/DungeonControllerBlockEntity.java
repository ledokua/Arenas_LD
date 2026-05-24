package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerData;
import net.ledok.arenas_ld.dungeon.screen.DungeonControllerScreenHandler;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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
    private static final int DEFAULT_CLOSE_TIMER_SECONDS = 30;
    private static final int DEFAULT_MAX_PARTY_SIZE = 4;
    private static final int DEFAULT_INVITE_EXPIRY_TICKS = 30 * 20;
    private static final int DEFAULT_DISCONNECT_GRACE_TICKS = 5 * 60 * 20;

    private final List<BlockPos> instances = new ArrayList<>();
    private final Map<DifficultyTier, TierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);
    private int cooldownTicks = DEFAULT_COOLDOWN_TICKS;
    private int closeTimerSeconds = DEFAULT_CLOSE_TIMER_SECONDS;
    private int maxPartySize = DEFAULT_MAX_PARTY_SIZE;
    private int inviteExpiryTicks = DEFAULT_INVITE_EXPIRY_TICKS;
    private int disconnectGraceTicks = DEFAULT_DISCONNECT_GRACE_TICKS;
    private final Map<BlockPos, Integer> instanceCooldownTimers = new HashMap<>();
    private final Set<BlockPos> pendingInstanceRemovals = new HashSet<>();
    private final Map<DifficultyTier, List<LeaderboardEntry>> leaderboards = new EnumMap<>(DifficultyTier.class);
    private final List<Lobby> lobbies = new ArrayList<>();
    private final List<PendingInvite> pendingInvites = new ArrayList<>();
    private final Map<BlockPos, DungeonRun> activeRuns = new HashMap<>();

    public DungeonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_CONTROLLER_BLOCK_ENTITY, pos, state);
        initializeDefaults();
    }

    private void initializeDefaults() {
        tierConfigs.putIfAbsent(DifficultyTier.NORMAL, TierConfig.NORMAL_DEFAULT);
        tierConfigs.putIfAbsent(DifficultyTier.HARD, TierConfig.HARD_DEFAULT);
        tierConfigs.putIfAbsent(DifficultyTier.HELL, TierConfig.HELL_DEFAULT);
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

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public int getInviteExpiryTicks() {
        return inviteExpiryTicks;
    }

    public int getDisconnectGraceTicks() {
        return disconnectGraceTicks;
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

    public boolean addInstance(BlockPos pos) {
        if (instances.contains(pos)) return false;
        instances.add(pos);
        setChanged();
        return true;
    }

    public boolean removeInstance(BlockPos pos) {
        if (!instances.contains(pos)) return false;
        if (isInstanceInActiveRun(pos)) {
            pendingInstanceRemovals.add(pos);
            setChanged();
            return true;
        }
        boolean removed = instances.remove(pos);
        if (removed) setChanged();
        return removed;
    }

    public boolean moveInstance(int from, int to) {
        if (from < 0 || from >= instances.size() || to < 0 || to >= instances.size()) return false;
        if (from == to) return false;
        BlockPos moved = instances.remove(from);
        instances.add(to, moved);
        setChanged();
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
        setChanged();
    }

    void addLobby(Lobby lobby) {
        lobbies.add(lobby);
        setChanged();
    }

    void replaceLobby(Lobby lobby) {
        for (int i = 0; i < lobbies.size(); i++) {
            if (lobbies.get(i).lobbyId().equals(lobby.lobbyId())) {
                lobbies.set(i, lobby);
                setChanged();
                return;
            }
        }
    }

    void removeLobby(UUID lobbyId) {
        boolean removed = lobbies.removeIf(lobby -> lobby.lobbyId().equals(lobbyId));
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
        PendingInvite invite = new PendingInvite(lobby.lobbyId(), inviteeUuid, inviter.getUUID(), now + inviteExpiryTicks);
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
        return true;
    }

    public boolean declineInvite(ServerPlayer invitee, UUID lobbyId) {
        Optional<PendingInvite> invite = findInvite(lobbyId, invitee.getUUID());
        if (invite.isEmpty()) return false;
        removeInvite(lobbyId, invitee.getUUID());
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
        return true;
    }

    public boolean setLobbyTier(ServerPlayer player, DifficultyTier tier) {
        Optional<Lobby> lobbyOpt = findLobbyByMember(player.getUUID());
        if (lobbyOpt.isEmpty()) return false;
        Lobby lobby = lobbyOpt.get();
        if (!lobby.isOwner(player.getUUID())) return false;
        if (lobby.status() == LobbyStatus.IN_RUN) return false;
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
        if (available.isEmpty()) return Optional.empty();

        removeLobby(lobby.lobbyId());
        return available;
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
        return new DungeonControllerData(worldPosition, visible, own, myInvites, maxPartySize, tierConfigs);
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

        long currentTick = serverLevel.getGameTime();
        if (be.pendingInvites.removeIf(invite -> invite.expiresAtTick() <= currentTick)) {
            be.setChanged();
        }

        for (DungeonRun run : new ArrayList<>(be.activeRuns.values())) {
            DungeonRunLifecycle.tick(serverLevel, be, run);
        }
    }

    private record InstanceCooldown(BlockPos pos, int ticks) {
        static final Codec<InstanceCooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(InstanceCooldown::pos),
            Codec.INT.fieldOf("ticks").forGetter(InstanceCooldown::ticks)
        ).apply(i, InstanceCooldown::new));
    }

    private record InstanceRunEntry(BlockPos pos, DungeonRun run) {
        static final Codec<InstanceRunEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(InstanceRunEntry::pos),
            DungeonRun.CODEC.fieldOf("run").forGetter(InstanceRunEntry::run)
        ).apply(i, InstanceRunEntry::new));
    }

    private record State(
        List<BlockPos> instances,
        Map<DifficultyTier, TierConfig> tierConfigs,
        int cooldownTicks,
        int closeTimerSeconds,
        int maxPartySize,
        int inviteExpiryTicks,
        int disconnectGraceTicks,
        List<InstanceCooldown> instanceCooldowns,
        List<InstanceRunEntry> activeRuns,
        List<BlockPos> pendingInstanceRemovals,
        Map<DifficultyTier, List<LeaderboardEntry>> leaderboards,
        List<Lobby> lobbies,
        List<PendingInvite> pendingInvites
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("instances").forGetter(State::instances),
            Codec.unboundedMap(DifficultyTier.CODEC, TierConfig.CODEC).fieldOf("tierConfigs").forGetter(State::tierConfigs),
            Codec.INT.fieldOf("cooldownTicks").forGetter(State::cooldownTicks),
            Codec.INT.fieldOf("closeTimerSeconds").forGetter(State::closeTimerSeconds),
            Codec.INT.fieldOf("maxPartySize").forGetter(State::maxPartySize),
            Codec.INT.fieldOf("inviteExpiryTicks").forGetter(State::inviteExpiryTicks),
            Codec.INT.optionalFieldOf("disconnectGraceTicks", DEFAULT_DISCONNECT_GRACE_TICKS).forGetter(State::disconnectGraceTicks),
            InstanceCooldown.CODEC.listOf().fieldOf("instanceCooldowns").forGetter(State::instanceCooldowns),
            InstanceRunEntry.CODEC.listOf().fieldOf("activeRuns").forGetter(State::activeRuns),
            BlockPos.CODEC.listOf().fieldOf("pendingInstanceRemovals").forGetter(State::pendingInstanceRemovals),
            Codec.unboundedMap(DifficultyTier.CODEC, LeaderboardEntry.CODEC.listOf()).fieldOf("leaderboards").forGetter(State::leaderboards),
            Lobby.CODEC.listOf().fieldOf("lobbies").forGetter(State::lobbies),
            PendingInvite.CODEC.listOf().fieldOf("pendingInvites").forGetter(State::pendingInvites)
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
        State state = new State(
            instances,
            tierConfigs,
            cooldownTicks,
            closeTimerSeconds,
            maxPartySize,
            inviteExpiryTicks,
            disconnectGraceTicks,
            cooldowns,
            runs,
            new ArrayList<>(pendingInstanceRemovals),
            leaderboards,
            lobbies,
            pendingInvites
        );
        State.CODEC.encodeStart(NbtOps.INSTANCE, state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save DungeonController at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load DungeonController at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    instances.clear();
                    instances.addAll(state.instances());
                    tierConfigs.clear();
                    tierConfigs.putAll(state.tierConfigs());
                    cooldownTicks = state.cooldownTicks();
                    closeTimerSeconds = state.closeTimerSeconds();
                    maxPartySize = state.maxPartySize();
                    inviteExpiryTicks = state.inviteExpiryTicks();
                    disconnectGraceTicks = state.disconnectGraceTicks();
                    instanceCooldownTimers.clear();
                    for (InstanceCooldown c : state.instanceCooldowns()) {
                        instanceCooldownTimers.put(c.pos(), c.ticks());
                    }
                    activeRuns.clear();
                    for (InstanceRunEntry runEntry : state.activeRuns()) {
                        activeRuns.put(runEntry.pos(), runEntry.run());
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
                    initializeDefaults();
                });
        } else {
            initializeDefaults();
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

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        ArenasLdMod.DUNGEON_MANAGER.registerController(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ArenasLdMod.DUNGEON_MANAGER.unregisterController(this);
    }
}
