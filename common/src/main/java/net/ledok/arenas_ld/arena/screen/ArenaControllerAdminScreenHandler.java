package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.activity.AdminMenuBase;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Admin menu for the arena controller. Client side it holds the opening data, replaced by every
 * admin snapshot; the screen reads only that, never the client-side block entity, so it works
 * remotely and in another dimension.
 */
public class ArenaControllerAdminScreenHandler extends AdminMenuBase {
    private final BlockPos pos;
    private ArenaControllerAdminData data;

    public ArenaControllerAdminScreenHandler(int syncId, Inventory inventory, ArenaControllerAdminData data) {
        super(ModScreenHandlers.ARENA_CONTROLLER_ADMIN_SCREEN_HANDLER, syncId);
        this.pos = data.blockPos();
        this.data = data;
    }

    public ArenaControllerAdminScreenHandler(int syncId, Inventory inventory, ArenaControllerBlockEntity controller) {
        this(syncId, inventory, controller.buildAdminData());
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    /** The latest data from the server. */
    public ArenaControllerAdminData data() {
        return data;
    }

    /** Takes a snapshot for this controller (the client receiver checks position and dimension). */
    public void applyData(ArenaControllerAdminData data) {
        this.data = data;
    }

    @Override
    public BlockPos controllerPos() {
        return pos;
    }

    @Override
    protected boolean isController(@Nullable BlockEntity be) {
        return be instanceof ArenaControllerBlockEntity;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
