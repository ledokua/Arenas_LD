package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.util.EquipmentProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class EquipmentScreenHandler extends AbstractContainerMenu {
    public final EquipmentProvider equipmentProvider;
    public final BlockEntity blockEntity;

    public EquipmentScreenHandler(int syncId, Inventory playerInventory, EquipmentScreenData data) {
        this(syncId, playerInventory, playerInventory.player.level().getBlockEntity(data.pos()));
    }

    public EquipmentScreenHandler(int syncId, Inventory playerInventory, BlockEntity blockEntity) {
        super(ModScreenHandlers.EQUIPMENT_SCREEN_HANDLER.get(), syncId);
        this.blockEntity = blockEntity;
        if (blockEntity instanceof EquipmentProvider provider) {
            this.equipmentProvider = provider;
        } else {
            throw new IllegalStateException("BlockEntity is not an EquipmentProvider");
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
