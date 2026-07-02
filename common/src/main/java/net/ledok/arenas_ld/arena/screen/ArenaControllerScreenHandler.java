package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Player-facing arena controller menu. Holds the (client-synced) controller block entity; the owo
 * screen reads live lobby/instance state from it each refresh.
 */
public class ArenaControllerScreenHandler extends AbstractContainerMenu {
    private final BlockPos pos;
    @Nullable public final ArenaControllerBlockEntity controller;
    public final Player player;

    public ArenaControllerScreenHandler(int syncId, Inventory playerInventory, ArenaControllerMenuData data) {
        this(syncId, playerInventory,
            playerInventory.player.level().getBlockEntity(data.blockPos()) instanceof ArenaControllerBlockEntity c ? c : null,
            data.blockPos());
    }

    public ArenaControllerScreenHandler(int syncId, Inventory playerInventory, ArenaControllerBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity, blockEntity.getBlockPos());
    }

    private ArenaControllerScreenHandler(int syncId, Inventory playerInventory, @Nullable ArenaControllerBlockEntity controller, BlockPos pos) {
        super(ModScreenHandlers.ARENA_CONTROLLER_SCREEN_HANDLER, syncId);
        this.controller = controller;
        this.player = playerInventory.player;
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
        return player.level().getBlockEntity(pos) instanceof ArenaControllerBlockEntity
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 * 64;
    }
}
