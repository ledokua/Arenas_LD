package net.ledok.arenas_ld.raid.screen;

import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.ledok.arenas_ld.platform.ExtendedMenuProvider;

public class RaidControllerAdminMenuProvider implements ExtendedMenuProvider<RaidControllerAdminData> {
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
    public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, RaidControllerAdminData> openingDataCodec() {
        return RaidControllerAdminData.STREAM_CODEC;
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory inv, Player player) {
        return new RaidControllerAdminScreenHandler(syncId, inv, controller);
    }
}
