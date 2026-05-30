package net.ledok.arenas_ld.arena.run;

import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Controller-driven lifecycle for an arena run. Mirrors
 * {@link net.ledok.arenas_ld.raid.run.RaidRunLifecycle}.
 *
 * <p><b>Phase 3 stub.</b> Only the entry points the controller needs exist so far; the wave loop,
 * archetypes/objectives, downed/respawn, disconnect-grace, win/loss and the reward summary are
 * implemented in the next phase.
 */
public final class ArenaRunLifecycle {

    private ArenaRunLifecycle() {}

    /** Begin a run: capture return points, teleport the party in, and start the first wave. */
    public static void beginRun(ServerLevel world, ArenaControllerBlockEntity controller,
                                ArenaRun run, List<ServerPlayer> players) {
        // TODO(phase 4): capture return points, teleport to entrance, spawn the first wave.
        run.setPhase(ArenaPhase.RUNNING);
    }

    /** Drive a run forward by one tick. */
    public static void tick(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        switch (run.phase()) {
            case STARTING -> run.setPhase(ArenaPhase.RUNNING);
            case RUNNING -> { /* TODO(phase 4): wave loop. */ }
            case CLOSING -> { /* TODO(phase 4): grace countdown → finalize. */ }
            case DONE -> { /* Already removed from the controller. */ }
        }
    }
}
