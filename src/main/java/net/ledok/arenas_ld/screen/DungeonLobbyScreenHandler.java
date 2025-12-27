package net.ledok.arenas_ld.screen;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.UUID;

public class DungeonLobbyScreenHandler extends AbstractContainerMenu {

    public final UUID lobbyId;
    public final UUID ownerId;
    public List<String> playerNames;

    public DungeonLobbyScreenHandler(int syncId, Inventory playerInventory, DungeonLobbyData data) {
        super(ModScreenHandlers.DUNGEON_LOBBY_SCREEN_HANDLER, syncId);
        this.lobbyId = data.lobbyId();
        this.ownerId = data.ownerId();
        this.playerNames = data.playerNames();
    }

    public void setPlayerNames(List<String> playerNames) {
        this.playerNames = playerNames;
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
