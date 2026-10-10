package net.ledok.arenas_ld.arena.screen;

import net.ledok.arenas_ld.activity.LobbyMenuBase;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Player-facing arena controller menu. Client side it holds the latest {@link ArenaControllerData}
 * (opening data, then {@code ArenaControllerSnapshotPayload} pushes); the screen reads only that.
 */
public class ArenaControllerScreenHandler extends LobbyMenuBase {
    private final BlockPos pos;
    private ArenaControllerData data;

    /** Client side: built from the opening data. */
    public ArenaControllerScreenHandler(int syncId, Inventory playerInventory, ArenaControllerData data) {
        super(ModScreenHandlers.ARENA_CONTROLLER_SCREEN_HANDLER, syncId);
        this.pos = data.blockPos();
        this.data = data;
    }

    /** Server side: the controller's own menu (the data is only sent, never read here). */
    public ArenaControllerScreenHandler(int syncId, Inventory playerInventory, ArenaControllerBlockEntity blockEntity) {
        super(ModScreenHandlers.ARENA_CONTROLLER_SCREEN_HANDLER, syncId);
        this.pos = blockEntity.getBlockPos();
        this.data = new ArenaControllerData(pos, List.of(), 0, List.of(), List.of(), List.of(), List.of(),
            Set.of(), Set.of(), blockEntity.getMaxPartySize(), blockEntity.getMaxWave());
    }

    public BlockPos getBlockPos() {
        return pos;
    }

    public ArenaControllerData data() {
        return data;
    }

    public void applyData(ArenaControllerData data) {
        this.data = data;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public BlockPos lobbyPos() {
        return pos;
    }

    @Override
    protected boolean isController(@Nullable BlockEntity be) {
        return be instanceof ArenaControllerBlockEntity;
    }

    @Override
    protected double maxDistanceSqr() {
        return 64 * 64;
    }
}
