package net.ledok.arenas_ld.activity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Base of the three controller admin menus. Server side, {@link AdminScreens} binds each open menu
 * to its controller's dimension, so admin packets reach the right controller from any dimension,
 * and a remotely opened screen ({@code ArenasApi.openAdmin}) is not closed for being out of reach.
 * Kept apart from {@link LobbyMenuBase} so admin viewers never count as lobby viewers.
 */
public abstract class AdminMenuBase extends AbstractContainerMenu {
    /** Reach for a screen opened at the block; {@code /arenasld admin} picks up to 10 blocks away. */
    private static final double MAX_DISTANCE_SQR = 16.0 * 16.0;

    private @Nullable ResourceKey<Level> boundDimension;
    private boolean remote;

    protected AdminMenuBase(@Nullable MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    /** The controller this menu edits. */
    public abstract BlockPos controllerPos();

    /** Whether {@code be} is a controller of this menu's type. */
    protected abstract boolean isController(@Nullable BlockEntity be);

    void bind(ResourceKey<Level> dimension, boolean remote) {
        this.boundDimension = dimension;
        this.remote = remote;
    }

    public @Nullable ResourceKey<Level> boundDimension() {
        return boundDimension;
    }

    /** Whether this (server-side) menu edits the given controller. */
    public boolean shows(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.equals(boundDimension) && pos.equals(controllerPos());
    }

    @Override
    public boolean stillValid(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true; // the server decides
        }
        if (!serverPlayer.hasPermissions(2)) {
            return false; // deopped while the screen was open
        }
        BlockPos pos = controllerPos();
        if (boundDimension == null) {
            return isController(player.level().getBlockEntity(pos)) && withinReach(player, pos);
        }
        ServerLevel level = serverPlayer.server.getLevel(boundDimension);
        if (level == null || !level.isLoaded(pos) || !isController(level.getBlockEntity(pos))) {
            return false;
        }
        return remote || (player.level().dimension().equals(boundDimension) && withinReach(player, pos));
    }

    private static boolean withinReach(Player player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE_SQR;
    }
}
