package net.ledok.arenas_ld.screen;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class LobbyScreenHandler extends AbstractContainerMenu {

    public LobbyScreenHandler(int syncId, Inventory playerInventory, LobbyData data) {
        this(syncId, playerInventory);
    }

    public LobbyScreenHandler(int syncId, Inventory playerInventory) {
        super(ModScreenHandlers.LOBBY_SCREEN_HANDLER, syncId);
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
