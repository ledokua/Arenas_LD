package net.ledok.arenas_ld.screen;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class CreateLobbyScreenHandler extends AbstractContainerMenu {
    public final List<CreateLobbyData.DungeonStatus> dungeons;

    public CreateLobbyScreenHandler(int syncId, Inventory playerInventory, CreateLobbyData data) {
        this(syncId, playerInventory, data.dungeons());
    }

    public CreateLobbyScreenHandler(int syncId, Inventory playerInventory, List<CreateLobbyData.DungeonStatus> dungeons) {
        super(ModScreenHandlers.CREATE_LOBBY_SCREEN_HANDLER, syncId);
        this.dungeons = dungeons;
    }
    
    public CreateLobbyScreenHandler(int syncId, Inventory playerInventory) {
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
