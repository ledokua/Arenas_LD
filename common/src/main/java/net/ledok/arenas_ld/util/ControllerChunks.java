package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.activity.ActivityHost;
import net.ledok.arenas_ld.activity.ActivityIndex;
import net.ledok.arenas_ld.activity.ActivityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Dungeon, Raid and Arena Controllers keep their own chunk force-loaded, so lobbies, queues and the
 * runs they tick go on with nobody near. A force-load (not a ticket) because it also keeps an
 * otherwise empty level ticking block entities.
 *
 * <p>The chunk is let go only when the controller block itself is broken. It used to be let go in
 * {@code setRemoved}, which also runs when the chunk unloads, and every clean server stop unloads
 * every chunk: the release was saved, so after a restart controllers stayed unloaded (with the
 * runs saved in them) until someone came near.
 */
public final class ControllerChunks {
    private ControllerChunks() {
    }

    /** Force-loads the controller's chunk. A no-op when it already is (see {@code setChunkForced}). */
    public static void hold(ServerLevel level, BlockPos controllerPos) {
        ChunkPos chunk = new ChunkPos(controllerPos);
        if (!level.getForcedChunks().contains(chunk.toLong())) {
            // setChunkForced with no change still resets the saved data's dirty flag, which could
            // drop another unsaved force-load change; only call it when something changes.
            level.setChunkForced(chunk.x, chunk.z, true);
        }
    }

    /** The controller block was broken: let go of its chunk unless another controller shares it. */
    public static void release(Level level, BlockPos controllerPos) {
        if (level instanceof ServerLevel serverLevel) {
            unforce(serverLevel, new ChunkPos(controllerPos), controllerPos);
        }
    }

    /**
     * An index entry whose controller is gone without its block being broken (a tool that skipped
     * block updates): let go of its chunk as breaking it would have.
     */
    public static void releaseIfGone(MinecraftServer server, ActivityIndex.Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level != null && !(level.getBlockEntity(entry.pos()) instanceof ActivityHost)) {
            unforce(level, new ChunkPos(entry.pos()), entry.pos());
        }
    }

    /**
     * Un-forces a chunk Arenas_LD force-loaded for something else (a run before 4.7.0), unless a
     * controller lives in it: that controller's hold must stay.
     */
    public static void unforceUnlessController(ServerLevel level, ChunkPos chunk) {
        unforce(level, chunk, null);
    }

    private static void unforce(ServerLevel level, ChunkPos chunk, @Nullable BlockPos ignoredController) {
        if (!level.getForcedChunks().contains(chunk.toLong())) {
            return; // nothing to do, and a no-op setChunkForced would reset the dirty flag
        }
        for (BlockEntity be : level.getChunk(chunk.x, chunk.z).getBlockEntities().values()) {
            if (be instanceof ActivityHost && !be.isRemoved() && !be.getBlockPos().equals(ignoredController)) {
                return;
            }
        }
        level.setChunkForced(chunk.x, chunk.z, false);
    }

    /**
     * Server start: force-loads the chunk of every known controller. Repairs worlds whose controller
     * chunks were let go by earlier clean stops, so saved runs restore at boot again.
     */
    public static void holdKnown(MinecraftServer server) {
        int held = 0;
        for (var entry : ActivityRegistry.knownControllers(server)) {
            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(entry.pos());
            if (be instanceof ActivityHost) {
                hold(level, entry.pos());
                held++;
            } else {
                releaseIfGone(server, entry);
            }
        }
        if (held > 0) {
            ArenasLdMod.LOGGER.info("Keeping {} activity controller chunk(s) loaded", held);
        }
    }
}
