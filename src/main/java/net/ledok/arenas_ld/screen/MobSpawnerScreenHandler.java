package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class MobSpawnerScreenHandler extends AbstractContainerMenu {
    public final MobSpawnerBlockEntity blockEntity;
    public final Player player;

    // MOJANG MAPPINGS: PlayerInventory is now Inventory.
    public MobSpawnerScreenHandler(int syncId, Inventory playerInventory, MobSpawnerData data) {
        this(syncId, playerInventory, (MobSpawnerBlockEntity) playerInventory.player.level().getBlockEntity(data.blockPos()));
    }

    public MobSpawnerScreenHandler(int syncId, Inventory playerInventory, MobSpawnerBlockEntity blockEntity) {
        super(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, syncId);
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