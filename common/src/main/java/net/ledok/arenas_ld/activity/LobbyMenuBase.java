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
 * Base of the three lobby menus. Server side, {@link LobbyScreens} binds each open menu to its
 * controller's dimension, so lobby packets reach the right controller from any dimension and a
 * remotely opened screen (hub, {@code openLobbies}) is not closed for being out of reach.
 */
public abstract class LobbyMenuBase extends AbstractContainerMenu {
    private @Nullable ResourceKey<Level> boundDimension;
    private boolean remote;

    protected LobbyMenuBase(@Nullable MenuType<?> type, int syncId) {
        super(type, syncId);
    }

    /** The controller this menu shows. */
    public abstract BlockPos lobbyPos();

    protected abstract boolean isController(@Nullable BlockEntity be);

    /** Squared reach for a menu opened at the block. */
    protected abstract double maxDistanceSqr();

    void bind(ResourceKey<Level> dimension, boolean remote) {
        this.boundDimension = dimension;
        this.remote = remote;
    }

    public @Nullable ResourceKey<Level> boundDimension() {
        return boundDimension;
    }

    /** Whether this (server-side) menu shows the given controller. */
    public boolean shows(ResourceKey<Level> dimension, BlockPos pos) {
        return dimension.equals(boundDimension) && pos.equals(lobbyPos());
    }

    @Override
    public boolean stillValid(Player player) {
        BlockPos pos = lobbyPos();
        if (boundDimension == null || !(player instanceof ServerPlayer serverPlayer)) {
            return isController(player.level().getBlockEntity(pos)) && withinReach(player, pos);
        }
        ServerLevel level = serverPlayer.server.getLevel(boundDimension);
        if (level == null || !level.isLoaded(pos) || !isController(level.getBlockEntity(pos))) {
            return false;
        }
        return remote || (player.level().dimension().equals(boundDimension) && withinReach(player, pos));
    }

    private boolean withinReach(Player player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxDistanceSqr();
    }
}
