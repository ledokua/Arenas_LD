package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Opens the equipment editor as a real, server-synced menu (required for the interactive ghost
 * slots). Built from any {@link net.ledok.arenas_ld.util.EquipmentProvider} block entity.
 */
public class EquipmentMenuProvider implements ExtendedScreenHandlerFactory<EquipmentScreenData> {
    private final BlockEntity blockEntity;

    public EquipmentMenuProvider(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.arenas_ld.equipment");
    }

    @Override
    public EquipmentScreenData getScreenOpeningData(ServerPlayer player) {
        return new EquipmentScreenData(blockEntity.getBlockPos());
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
        return new EquipmentScreenHandler(syncId, inventory, blockEntity);
    }
}
