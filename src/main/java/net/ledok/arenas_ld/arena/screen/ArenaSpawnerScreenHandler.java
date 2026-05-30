package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ArenaSpawnerScreenHandler extends AbstractContainerMenu {
    private final BlockPos pos;
    @Nullable public final ArenaSpawnerBlockEntity spawner;

    public ArenaSpawnerScreenHandler(int syncId, Inventory inventory, ArenaSpawnerMenuData data) {
        this(syncId, inventory,
            inventory.player.level().getBlockEntity(data.blockPos()) instanceof ArenaSpawnerBlockEntity s ? s : null,
            data.blockPos());
    }

    public ArenaSpawnerScreenHandler(int syncId, Inventory inventory, ArenaSpawnerBlockEntity spawner) {
        this(syncId, inventory, spawner, spawner.getBlockPos());
    }

    private ArenaSpawnerScreenHandler(int syncId, Inventory inventory, @Nullable ArenaSpawnerBlockEntity spawner, BlockPos pos) {
        super(ModScreenHandlers.ARENA_SPAWNER_SCREEN_HANDLER, syncId);
        this.spawner = spawner;
        this.pos = pos;
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof ArenaSpawnerBlockEntity
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 * 64;
    }
}
