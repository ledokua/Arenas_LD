package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import net.ledok.arenas_ld.platform.ArenasEvents;

/**
 * Runs tasks at the end of the NEXT server tick. Unlike {@link MinecraftServer#execute},
 * which runs inline when called from the server thread, tasks queued here always wait for
 * a tick boundary — needed to move teleports out of the login phase: teleporting a player
 * from inside the JOIN event (mid {@code placeNewPlayer}) can leave the client in limbo
 * and desync chunk tracking.
 */
public final class ServerTaskScheduler {
    private static final Queue<Consumer<MinecraftServer>> NEXT_TICK = new ArrayDeque<>();

    private ServerTaskScheduler() {
    }

    public static void register() {
        ArenasEvents.SERVER_TICK_END.add(server -> {
            if (NEXT_TICK.isEmpty()) {
                return;
            }
            // Snapshot first: a task may schedule follow-ups, which must wait for the next tick.
            List<Consumer<MinecraftServer>> tasks = new ArrayList<>(NEXT_TICK);
            NEXT_TICK.clear();
            for (Consumer<MinecraftServer> task : tasks) {
                try {
                    task.accept(server);
                } catch (Exception e) {
                    ArenasLdMod.LOGGER.error("Scheduled server task failed", e);
                }
            }
        });
        ArenasEvents.SERVER_STOPPED.add(server -> NEXT_TICK.clear());
    }

    /** Queues a task for the end of the next server tick. Server thread only. */
    public static void nextTick(Consumer<MinecraftServer> task) {
        NEXT_TICK.add(task);
    }
}
