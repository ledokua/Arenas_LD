package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class DungeonControllerScreenHandler extends AbstractContainerMenu {
    private final BlockPos blockPos;
    private List<Lobby> visibleLobbies;
    private Optional<Lobby> ownLobby;
    private List<PendingInvite> myInvites;
    private List<PendingJoinRequest> myJoinRequests;
    private int maxPartySize;
    private Map<DifficultyTier, TierConfig> tiers;
    private long serverGameTick;
    private Set<UUID> busyPlayers;
    private Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards;
    private int queuePosition;
    private int estimatedWaitSeconds;

    public DungeonControllerScreenHandler(int syncId, Inventory inventory, DungeonControllerData data) {
        this(syncId, inventory, data.blockPos(), data.visibleLobbies(), data.ownLobby(), data.myInvites(), data.myJoinRequests(), data.maxPartySize(), data.tiers(), data.serverGameTick(), data.busyPlayers(), data.topLeaderboards(), data.queuePosition(), data.estimatedWaitSeconds());
    }

    public DungeonControllerScreenHandler(int syncId, Inventory inventory, DungeonControllerBlockEntity blockEntity) {
        this(syncId, inventory, blockEntity.getBlockPos(), blockEntity.getLobbies(), Optional.empty(), blockEntity.getPendingInvites(), blockEntity.getPendingJoinRequests(), blockEntity.getMaxPartySize(), blockEntity.getTierConfigs(), 0L, Set.of(), Map.of(), 0, 0);
    }

    private DungeonControllerScreenHandler(
        int syncId,
        Inventory inventory,
        BlockPos blockPos,
        List<Lobby> visibleLobbies,
        Optional<Lobby> ownLobby,
        List<PendingInvite> myInvites,
        List<PendingJoinRequest> myJoinRequests,
        int maxPartySize,
        Map<DifficultyTier, TierConfig> tiers,
        long serverGameTick,
        Set<UUID> busyPlayers,
        Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards,
        int queuePosition,
        int estimatedWaitSeconds
    ) {
        super(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER, syncId);
        this.queuePosition = queuePosition;
        this.estimatedWaitSeconds = estimatedWaitSeconds;
        this.blockPos = blockPos;
        this.visibleLobbies = List.copyOf(visibleLobbies);
        this.ownLobby = ownLobby;
        this.myInvites = List.copyOf(myInvites);
        this.myJoinRequests = List.copyOf(myJoinRequests);
        this.maxPartySize = maxPartySize;
        this.tiers = Map.copyOf(tiers);
        this.serverGameTick = serverGameTick;
        this.busyPlayers = Set.copyOf(busyPlayers);
        this.topLeaderboards = Map.copyOf(topLeaderboards);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public List<Lobby> getVisibleLobbies() {
        return List.copyOf(visibleLobbies);
    }

    public Optional<Lobby> getOwnLobby() {
        return ownLobby;
    }

    public List<PendingInvite> getMyInvites() {
        return List.copyOf(myInvites);
    }

    public List<PendingJoinRequest> getMyJoinRequests() {
        return List.copyOf(myJoinRequests);
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public Map<DifficultyTier, TierConfig> getTiers() {
        return Map.copyOf(tiers);
    }

    public long getServerGameTick() {
        return serverGameTick;
    }

    public Set<UUID> getBusyPlayers() {
        return Set.copyOf(busyPlayers);
    }

    public Map<DifficultyTier, List<LeaderboardEntry>> getTopLeaderboards() {
        return Map.copyOf(topLeaderboards);
    }

    public int getQueuePosition() {
        return queuePosition;
    }

    public int getEstimatedWaitSeconds() {
        return estimatedWaitSeconds;
    }

    public void applyData(DungeonControllerData data) {
        this.visibleLobbies = List.copyOf(data.visibleLobbies());
        this.ownLobby = data.ownLobby();
        this.myInvites = List.copyOf(data.myInvites());
        this.myJoinRequests = List.copyOf(data.myJoinRequests());
        this.maxPartySize = data.maxPartySize();
        this.tiers = Map.copyOf(data.tiers());
        this.serverGameTick = data.serverGameTick();
        this.busyPlayers = Set.copyOf(data.busyPlayers());
        this.topLeaderboards = Map.copyOf(data.topLeaderboards());
        this.queuePosition = data.queuePosition();
        this.estimatedWaitSeconds = data.estimatedWaitSeconds();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64;
    }
}
