package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.block.entity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
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

public class RaidControllerScreenHandler extends AbstractContainerMenu {
    private final BlockPos pos;
    private List<Lobby> visibleLobbies;
    private Optional<Lobby> ownLobby;
    private List<PendingInvite> myInvites;
    private List<PendingJoinRequest> myJoinRequests;
    private List<RaidControllerData.RaidInstanceState> instances;
    private int queuePosition;
    private int maxPartySize;
    private int respawnTimeTicks;
    private long serverGameTick;
    private Set<UUID> busyPlayers;
    private Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards;

    /** Client-side constructor — called when opening screen from packet data. */
    public RaidControllerScreenHandler(int syncId, Inventory playerInventory, RaidControllerData data) {
        super(ModScreenHandlers.RAID_CONTROLLER_SCREEN_HANDLER, syncId);
        this.pos = data.blockPos();
        applyData(data);
    }

    /** Server-side constructor — called when the block entity opens the screen. */
    public RaidControllerScreenHandler(int syncId, Inventory playerInventory, RaidControllerBlockEntity blockEntity) {
        super(ModScreenHandlers.RAID_CONTROLLER_SCREEN_HANDLER, syncId);
        this.pos = blockEntity.getBlockPos();
        this.visibleLobbies = List.of();
        this.ownLobby = Optional.empty();
        this.myInvites = List.of();
        this.myJoinRequests = List.of();
        this.instances = List.of();
        this.queuePosition = -1;
        this.maxPartySize = blockEntity.getMaxPartySize();
        this.respawnTimeTicks = blockEntity.getRespawnTimeTicks();
        this.serverGameTick = 0L;
        this.busyPlayers = Set.of();
        this.topLeaderboards = Map.of();
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockPos getBlockPos() {
        return pos;
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

    public List<RaidControllerData.RaidInstanceState> getInstances() {
        return List.copyOf(instances);
    }

    /** Returns -1 if not in queue, otherwise 1-based position. */
    public int getQueuePosition() {
        return queuePosition;
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public int getRespawnTimeTicks() {
        return respawnTimeTicks;
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

    public void applyData(RaidControllerData data) {
        this.visibleLobbies = List.copyOf(data.visibleLobbies());
        this.ownLobby = data.ownLobby();
        this.myInvites = List.copyOf(data.myInvites());
        this.myJoinRequests = List.copyOf(data.myJoinRequests());
        this.instances = List.copyOf(data.instances());
        this.queuePosition = data.queuePosition();
        this.maxPartySize = data.maxPartySize();
        this.respawnTimeTicks = data.respawnTimeTicks();
        this.serverGameTick = data.serverGameTick();
        this.busyPlayers = Set.copyOf(data.busyPlayers());
        this.topLeaderboards = Map.copyOf(data.topLeaderboards());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof RaidControllerBlockEntity
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 * 64;
    }
}
