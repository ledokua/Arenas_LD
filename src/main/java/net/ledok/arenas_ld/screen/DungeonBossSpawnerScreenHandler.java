package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class DungeonBossSpawnerScreenHandler extends AbstractContainerMenu {
    public final DungeonBossSpawnerBlockEntity blockEntity;
    public final Player player;

    public DungeonBossSpawnerScreenHandler(int syncId, Inventory playerInventory, BossSpawnerData data) {
        this(syncId, playerInventory, (DungeonBossSpawnerBlockEntity) playerInventory.player.level().getBlockEntity(data.blockPos()));
    }

    public DungeonBossSpawnerScreenHandler(int syncId, Inventory playerInventory, DungeonBossSpawnerBlockEntity blockEntity) {
        super(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER.get(), syncId);
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
