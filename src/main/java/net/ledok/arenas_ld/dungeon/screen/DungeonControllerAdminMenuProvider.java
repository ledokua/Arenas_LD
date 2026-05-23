package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class DungeonControllerAdminMenuProvider implements ExtendedScreenHandlerFactory<DungeonControllerAdminData> {
    private final DungeonControllerBlockEntity controller;

    public DungeonControllerAdminMenuProvider(DungeonControllerBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.arenas_ld.dungeon_controller_admin.title");
    }

    @Override
    public DungeonControllerAdminData getScreenOpeningData(ServerPlayer player) {
        return new DungeonControllerAdminData(
            controller.getBlockPos(),
            controller.getInstances(),
            controller.getActiveRuns().keySet(),
            controller.getInstanceCooldownTimers(),
            controller.getPendingInstanceRemovals(),
            controller.getCooldownTicks(),
            controller.getCloseTimerSeconds(),
            controller.getMaxPartySize(),
            controller.getInviteExpiryTicks()
        );
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new DungeonControllerAdminScreenHandler(syncId, inv, controller);
    }
}
