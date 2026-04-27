package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.block.entity.RaidControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RaidControllerScreenHandler extends AbstractContainerMenu {
    private final BlockPos pos;

    // Client constructor
    public RaidControllerScreenHandler(int syncId, Inventory playerInventory, RaidControllerData data) {
        this(syncId, playerInventory, data.pos());
    }

    // Server constructor
    public RaidControllerScreenHandler(int syncId, Inventory playerInventory, RaidControllerBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity.getBlockPos());
    }

    private RaidControllerScreenHandler(int syncId, Inventory playerInventory, BlockPos pos) {
        super(ModScreenHandlers.RAID_CONTROLLER_SCREEN_HANDLER, syncId);
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
        return player.level().getBlockEntity(pos) instanceof RaidControllerBlockEntity
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
