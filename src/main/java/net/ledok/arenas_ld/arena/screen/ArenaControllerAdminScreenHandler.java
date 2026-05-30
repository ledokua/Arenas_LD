package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Admin menu for the arena controller. Holds the client-synced controller; the screen reads settings/instances from it. */
public class ArenaControllerAdminScreenHandler extends AbstractContainerMenu {
    private final BlockPos pos;
    @Nullable public final ArenaControllerBlockEntity controller;

    public ArenaControllerAdminScreenHandler(int syncId, Inventory inventory, ArenaControllerMenuData data) {
        this(syncId, inventory,
            inventory.player.level().getBlockEntity(data.blockPos()) instanceof ArenaControllerBlockEntity c ? c : null,
            data.blockPos());
    }

    public ArenaControllerAdminScreenHandler(int syncId, Inventory inventory, ArenaControllerBlockEntity controller) {
        this(syncId, inventory, controller, controller.getBlockPos());
    }

    private ArenaControllerAdminScreenHandler(int syncId, Inventory inventory, @Nullable ArenaControllerBlockEntity controller, BlockPos pos) {
        super(ModScreenHandlers.ARENA_CONTROLLER_ADMIN_SCREEN_HANDLER, syncId);
        this.controller = controller;
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
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64 * 64;
    }
}
