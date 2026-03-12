package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.block.entity.DungeonControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class DungeonControllerScreenHandler extends AbstractContainerMenu {
    private final BlockPos pos;

    // Client constructor
    public DungeonControllerScreenHandler(int syncId, Inventory playerInventory, DungeonControllerData data) {
        this(syncId, playerInventory, data.pos());
    }

    // Server constructor
    public DungeonControllerScreenHandler(int syncId, Inventory playerInventory, DungeonControllerBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity.getBlockPos());
    }

    private DungeonControllerScreenHandler(int syncId, Inventory playerInventory, BlockPos pos) {
        super(ModScreenHandlers.DUNGEON_CONTROLLER_SCREEN_HANDLER, syncId);
        this.pos = pos;
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
        return player.level().getBlockEntity(pos) instanceof DungeonControllerBlockEntity && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
