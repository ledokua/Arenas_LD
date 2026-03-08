package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.block.entity.MobArenaControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class MobArenaControllerScreenHandler extends AbstractContainerMenu {
    private final BlockPos pos;
    private final Player player;

    // Client constructor
    public MobArenaControllerScreenHandler(int syncId, Inventory playerInventory, MobArenaControllerData data) {
        this(syncId, playerInventory, data.pos());
    }

    // Server constructor
    public MobArenaControllerScreenHandler(int syncId, Inventory playerInventory, MobArenaControllerBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity.getBlockPos());
    }

    private MobArenaControllerScreenHandler(int syncId, Inventory playerInventory, BlockPos pos) {
        super(ModScreenHandlers.MOB_ARENA_CONTROLLER_SCREEN_HANDLER, syncId);
        this.pos = pos;
        this.player = playerInventory.player;
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
        return player.level().getBlockEntity(pos) instanceof MobArenaControllerBlockEntity && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
