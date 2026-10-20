package net.ledok.arenas_ld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a run's chunks loaded with region tickets owned by its instance, instead of force-loading
 * them. A force-load is one shared flag per chunk, so the first run (or controller) to let go of a
 * chunk released it for everyone, admin {@code /forceload} included, and anything left forced after
 * a crash or a broken boss spawner stayed forced for good. Tickets are counted per owner and are
 * not saved: a run restored after a restart takes them again on its first tick.
 */
public final class RunChunkTickets {
    /** Ticket level 31, the same as a force-load: the chunk ticks entities, its 5×5 area stays loaded. */
    private static final int RADIUS = 2;

    /** Who holds a ticket: the instance, by dimension and position (positions repeat across dimensions). */
    public record Owner(ResourceLocation dimension, BlockPos instance) {
        private static final Comparator<Owner> ORDER = Comparator
            .comparing((Owner owner) -> owner.dimension().toString())
            .thenComparing(Owner::instance);
    }

    /** One held chunk, in the level that holds it (an entrance can be in another dimension). */
    public record HeldChunk(ResourceKey<Level> level, ChunkPos chunk) {
    }

    private static final TicketType<Owner> TYPE = TicketType.create("arenas_ld_run", Owner.ORDER);

    private static final Map<Owner, Set<HeldChunk>> HELD = new HashMap<>();

    private RunChunkTickets() {
    }

    public static Owner owner(ResourceKey<Level> instanceDimension, BlockPos instance) {
        return new Owner(instanceDimension.location(), instance.immutable());
    }

    public static boolean isHeld(Owner owner) {
        return HELD.containsKey(owner);
    }

    /** Takes tickets on {@code chunks}, replacing whatever this owner held before. */
    public static void hold(MinecraftServer server, Owner owner, Set<HeldChunk> chunks) {
        release(server, owner);
        for (HeldChunk held : chunks) {
            ServerLevel level = server.getLevel(held.level());
            if (level != null) {
                level.getChunkSource().addRegionTicket(TYPE, held.chunk(), RADIUS, owner);
            }
        }
        HELD.put(owner, Set.copyOf(chunks));
    }

    /** Gives back exactly the tickets this owner took (even if its rooms were edited since). */
    public static void release(MinecraftServer server, Owner owner) {
        Set<HeldChunk> chunks = HELD.remove(owner);
        if (chunks == null) {
            return;
        }
        for (HeldChunk held : chunks) {
            ServerLevel level = server.getLevel(held.level());
            if (level != null) {
                level.getChunkSource().removeRegionTicket(TYPE, held.chunk(), RADIUS, owner);
            }
        }
    }

    /** Server stopped: the tickets are gone with the levels; forget them (singleplayer reopens a world). */
    public static void clear() {
        HELD.clear();
    }
}
