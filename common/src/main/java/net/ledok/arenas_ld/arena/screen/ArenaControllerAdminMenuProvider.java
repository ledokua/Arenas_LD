package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.ledok.arenas_ld.platform.ExtendedMenuProvider;

/** Opens the arena controller admin screen; open it through {@code AdminScreens.open}, which checks op and binds it. */
public class ArenaControllerAdminMenuProvider implements ExtendedMenuProvider<ArenaControllerAdminData> {
    private final ArenaControllerBlockEntity controller;

    public ArenaControllerAdminMenuProvider(ArenaControllerBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.arenas_ld.arena_controller_admin.title");
    }

    @Override
    public ArenaControllerAdminData getScreenOpeningData(ServerPlayer player) {
        return controller.buildAdminData();
    }

    @Override
    public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, ArenaControllerAdminData> openingDataCodec() {
        return ArenaControllerAdminData.STREAM_CODEC;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new ArenaControllerAdminScreenHandler(syncId, inv, controller);
    }
}
