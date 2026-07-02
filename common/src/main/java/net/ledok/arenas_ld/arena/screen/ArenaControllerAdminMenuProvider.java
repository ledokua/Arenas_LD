package net.ledok.arenas_ld.arena.screen;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Opens the arena controller admin screen (used by the admin command). */
public class ArenaControllerAdminMenuProvider implements ExtendedScreenHandlerFactory<ArenaControllerMenuData> {
    private final ArenaControllerBlockEntity controller;

    public ArenaControllerAdminMenuProvider(ArenaControllerBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.arenas_ld.arena_controller_admin.title");
    }

    @Override
    public ArenaControllerMenuData getScreenOpeningData(ServerPlayer player) {
        return new ArenaControllerMenuData(controller.getBlockPos());
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new ArenaControllerAdminScreenHandler(syncId, inv, controller);
    }
}
