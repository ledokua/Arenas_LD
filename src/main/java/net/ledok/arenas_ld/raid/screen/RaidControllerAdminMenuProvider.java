package net.ledok.arenas_ld.raid.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

public class RaidControllerAdminMenuProvider implements ExtendedScreenHandlerFactory<RaidControllerAdminData> {
    private final RaidControllerBlockEntity controller;

    public RaidControllerAdminMenuProvider(RaidControllerBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.arenas_ld.raid_controller_admin.title");
    }

    @Override
    public RaidControllerAdminData getScreenOpeningData(ServerPlayer player) {
        return controller.buildAdminData();
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new RaidControllerAdminScreenHandler(syncId, inv, controller);
    }
}
