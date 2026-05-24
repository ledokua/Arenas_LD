package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class MobSpawnerScreenHandler extends AbstractContainerMenu {
    private final BlockPos blockPos;
    private final String mobId;

    public MobSpawnerScreenHandler(int syncId, Inventory playerInventory, MobSpawnerData data) {
        super(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, syncId);
        this.blockPos = data.blockPos();
        this.mobId = data.mobId();
    }

    public MobSpawnerScreenHandler(int syncId, Inventory playerInventory, net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity blockEntity) {
        this(syncId, playerInventory, new MobSpawnerData(blockEntity.getBlockPos(), blockEntity.getEntityDefinition().mobId()));
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public String getMobId() {
        return mobId;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(blockPos) instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity
            && player.distanceToSqr(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5) <= 64;
    }
}
