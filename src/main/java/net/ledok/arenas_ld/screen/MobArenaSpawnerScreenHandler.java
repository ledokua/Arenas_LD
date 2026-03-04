package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class MobArenaSpawnerScreenHandler extends AbstractContainerMenu {
    public final MobArenaSpawnerBlockEntity blockEntity;
    public final Player player;

    public MobArenaSpawnerScreenHandler(int syncId, Inventory playerInventory, MobArenaSpawnerData data) {
        this(syncId, playerInventory, (MobArenaSpawnerBlockEntity) playerInventory.player.level().getBlockEntity(data.blockPos()));
    }

    public MobArenaSpawnerScreenHandler(int syncId, Inventory playerInventory, MobArenaSpawnerBlockEntity blockEntity) {
        super(ModScreenHandlers.MOB_ARENA_SPAWNER_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
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
