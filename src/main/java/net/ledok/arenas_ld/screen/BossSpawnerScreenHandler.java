package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class BossSpawnerScreenHandler extends AbstractContainerMenu {
    public final BossSpawnerBlockEntity blockEntity;
    public final Player player;

    // MOJANG MAPPINGS: PlayerInventory is now Inventory.
    public BossSpawnerScreenHandler(int syncId, Inventory playerInventory, BossSpawnerData data) {
        this(syncId, playerInventory, (BossSpawnerBlockEntity) playerInventory.player.level().getBlockEntity(data.blockPos()));
    }

    public BossSpawnerScreenHandler(int syncId, Inventory playerInventory, BossSpawnerBlockEntity blockEntity) {
        super(ModScreenHandlers.BOSS_SPAWNER_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
    }

    // MOJANG MAPPINGS: quickMove is now quickMoveStack, PlayerEntity is Player.
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    // MOJANG MAPPINGS: canUse is now stillValid.
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}