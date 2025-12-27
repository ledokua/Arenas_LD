package net.ledok.arenas_ld.screen;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class JoinLobbyScreenHandler extends AbstractContainerMenu {

    public final List<JoinLobbyData.LobbyInfo> lobbies;

    public JoinLobbyScreenHandler(int syncId, Inventory playerInventory, JoinLobbyData data) {
        this(syncId, playerInventory, data.lobbies());
    }

    public JoinLobbyScreenHandler(int syncId, Inventory playerInventory, List<JoinLobbyData.LobbyInfo> lobbies) {
        super(ModScreenHandlers.JOIN_LOBBY_SCREEN_HANDLER, syncId);
        this.lobbies = lobbies;
    }
    
    public JoinLobbyScreenHandler(int syncId, Inventory playerInventory) {
        this(syncId, playerInventory, Collections.emptyList());
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
