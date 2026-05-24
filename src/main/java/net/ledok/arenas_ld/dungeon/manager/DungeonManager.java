package net.ledok.arenas_ld.dungeon.manager;

import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Server-side singleton tracking all v4.0 dungeon controllers. Provides cross-controller
 * lookup APIs used by the mixin, death listener, and admin commands.
 *
 * <p>Lifecycle: instance is created at mod init; state is cleared on server stop via
 * {@link #clearForServerStop()}. Per-server state is rebuilt as controllers load.
 *
 * <p>Threading: all mutators are server-thread-only. No synchronization.
 */
public final class DungeonManager {

    /** Controllers registered by their dimension + position. */
    private final Map<ResourceKey<Level>, Set<BlockPos>> controllersByDimension = new HashMap<>();

    public DungeonManager() {
    }

    public void registerController(DungeonControllerBlockEntity be) {
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ResourceKey<Level> dim = serverLevel.dimension();
        controllersByDimension.computeIfAbsent(dim, ignored -> new HashSet<>()).add(be.getBlockPos());
    }

    public void unregisterController(DungeonControllerBlockEntity be) {
        Level level = be.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ResourceKey<Level> dim = serverLevel.dimension();
        Set<BlockPos> set = controllersByDimension.get(dim);
        if (set != null) {
            set.remove(be.getBlockPos());
            if (set.isEmpty()) {
                controllersByDimension.remove(dim);
            }
        }
    }

    /**
     * Look up a controller BE by (dimension, position). Returns null if the chunk isn't loaded
     * or no controller exists at that position.
     */
    public DungeonControllerBlockEntity getController(MinecraftServer server, ResourceKey<Level> dim, BlockPos pos) {
        ServerLevel level = server.getLevel(dim);
        if (level == null) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof DungeonControllerBlockEntity controller ? controller : null;
    }

    /** All registered controller positions in the given dimension. */
    public Set<BlockPos> getControllersIn(ResourceKey<Level> dim) {
        return controllersByDimension.getOrDefault(dim, Set.of());
    }

    /** Clear all state on server stop. Re-population happens organically as controllers load. */
    public void clearForServerStop() {
        controllersByDimension.clear();
    }
}
