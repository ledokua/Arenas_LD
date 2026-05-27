package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
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
    private int maxPartySize;
    private Map<DifficultyTier, TierConfig> tiers;
    private long serverGameTick;
    private Set<UUID> busyPlayers;

    public DungeonControllerScreenHandler(int syncId, Inventory inventory, DungeonControllerData data) {
        this(syncId, inventory, data.blockPos(), data.visibleLobbies(), data.ownLobby(), data.myInvites(), data.maxPartySize(), data.tiers(), data.serverGameTick(), data.busyPlayers());
    }

    public DungeonControllerScreenHandler(int syncId, Inventory inventory, DungeonControllerBlockEntity blockEntity) {
        this(syncId, inventory, blockEntity.getBlockPos(), blockEntity.getLobbies(), Optional.empty(), blockEntity.getPendingInvites(), blockEntity.getMaxPartySize(), blockEntity.getTierConfigs(), 0L, Set.of());
    }

    private DungeonControllerScreenHandler(
        int syncId,
        Inventory inventory,
        BlockPos blockPos,
        List<Lobby> visibleLobbies,
        Optional<Lobby> ownLobby,
        List<PendingInvite> myInvites,
        int maxPartySize,
        Map<DifficultyTier, TierConfig> tiers,
        long serverGameTick,
        Set<UUID> busyPlayers
    ) {
        super(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER, syncId);
        this.blockPos = blockPos;
        this.visibleLobbies = List.copyOf(visibleLobbies);
        this.ownLobby = ownLobby;
        this.myInvites = List.copyOf(myInvites);
        this.maxPartySize = maxPartySize;
        this.tiers = Map.copyOf(tiers);
        this.serverGameTick = serverGameTick;
        this.busyPlayers = Set.copyOf(busyPlayers);
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

    public void applyData(DungeonControllerData data) {
        this.visibleLobbies = List.copyOf(data.visibleLobbies());
        this.ownLobby = data.ownLobby();
        this.myInvites = List.copyOf(data.myInvites());
        this.maxPartySize = data.maxPartySize();
        this.tiers = Map.copyOf(data.tiers());
        this.serverGameTick = data.serverGameTick();
        this.busyPlayers = Set.copyOf(data.busyPlayers());
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
