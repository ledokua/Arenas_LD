package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public class RoomControllerScreenHandler extends AbstractContainerMenu {
    private final BlockPos blockPos;
    private List<RoomControllerData.SpawnerEntry> spawners;
    private List<BlockPos> doorPositions;
    private String roomName;
    private Optional<BlockPos> respawnPos;
    private RoomRewardConfig roomReward;
    private List<String> knownLootTableIds;

    public RoomControllerScreenHandler(int syncId, Inventory playerInventory, RoomControllerData data) {
        super(ModScreenHandlers.ROOM_CONTROLLER_SCREEN_HANDLER, syncId);
        this.blockPos = data.blockPos();
        applyData(data);
    }

    public RoomControllerScreenHandler(int syncId, Inventory playerInventory, RoomControllerBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity.getScreenOpeningData(null));
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public List<RoomControllerData.SpawnerEntry> getSpawners() {
        return List.copyOf(spawners);
    }

    public List<BlockPos> getDoorPositions() {
        return List.copyOf(doorPositions);
    }

    public String getRoomName() {
        return roomName;
    }

    public Optional<BlockPos> getRespawnPos() {
        return respawnPos;
    }

    public RoomRewardConfig getRoomReward() {
        return roomReward;
    }

    public List<String> getKnownLootTableIds() {
        return knownLootTableIds;
    }

    public void applyData(RoomControllerData data) {
        this.spawners = List.copyOf(data.spawners());
        this.doorPositions = List.copyOf(data.doorPositions());
        this.roomName = data.roomName();
        this.respawnPos = data.respawnPos();
        this.roomReward = data.roomReward();
        this.knownLootTableIds = List.copyOf(data.knownLootTableIds());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(blockPos) instanceof RoomControllerBlockEntity
            && player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64;
    }
}
