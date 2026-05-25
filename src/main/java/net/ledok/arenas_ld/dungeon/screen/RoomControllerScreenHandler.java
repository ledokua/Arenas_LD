package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
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
    private List<BlockPos> spawnerPositions;
    private Optional<BlockPos> doorPos;

    public RoomControllerScreenHandler(int syncId, Inventory playerInventory, RoomControllerData data) {
        this(syncId, playerInventory, data.blockPos(), data.spawnerPositions(), data.doorPos());
    }

    public RoomControllerScreenHandler(int syncId, Inventory playerInventory, RoomControllerBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity.getBlockPos(), blockEntity.getSpawnerPositions(), Optional.ofNullable(blockEntity.getDoorPos()));
    }

    private RoomControllerScreenHandler(
        int syncId,
        Inventory playerInventory,
        BlockPos blockPos,
        List<BlockPos> spawnerPositions,
        Optional<BlockPos> doorPos
    ) {
        super(ModScreenHandlers.ROOM_CONTROLLER_SCREEN_HANDLER, syncId);
        this.blockPos = blockPos;
        this.spawnerPositions = List.copyOf(spawnerPositions);
        this.doorPos = doorPos;
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public List<BlockPos> getSpawnerPositions() {
        return List.copyOf(spawnerPositions);
    }

    public Optional<BlockPos> getDoorPos() {
        return doorPos;
    }

    public void applyData(RoomControllerData data) {
        this.spawnerPositions = List.copyOf(data.spawnerPositions());
        this.doorPos = data.doorPos();
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
