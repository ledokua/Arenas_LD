package net.ledok.arenas_ld.arena.run;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.UUID;

/**
 * Routes player logout/login events into the arena run lifecycle, which owns the disconnect
 * grace, downed handling, and return-point restore logic. Mirrors
 * {@link net.ledok.arenas_ld.dungeon.run.DungeonConnectionListener}; the run lookup scans the
 * registered arena controllers the same way {@code LivingEntityMixin} does for arena deaths.
 */
public final class ArenaConnectionListener {
    private ArenaConnectionListener() {
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            RunRef ref = findRun(server, handler.player.getUUID(), false);
            if (ref != null) {
                ArenaRunLifecycle.handlePlayerDisconnect(ref.world(), ref.controller(), ref.run(), handler.player);
            }
        });

        // REMOVED participants are included on join so the lifecycle can eject them to their
        // return point (grace expired while they were offline, but the run is still going).
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            RunRef ref = findRun(server, handler.player.getUUID(), true);
            if (ref != null) {
                ArenaRunLifecycle.handlePlayerReconnect(ref.world(), ref.controller(), ref.run(), handler.player);
            }
        });
    }

    private record RunRef(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
    }

    private static RunRef findRun(MinecraftServer server, UUID uuid, boolean includeRemoved) {
        for (ArenaControllerBlockEntity.ControllerKey key : ArenaControllerBlockEntity.getControllers()) {
            ServerLevel controllerLevel = server.getLevel(key.dimension());
            if (controllerLevel == null) continue;
            if (!(controllerLevel.getBlockEntity(key.pos()) instanceof ArenaControllerBlockEntity controller)) continue;
            for (ArenaRun run : controller.getActiveRuns().values()) {
                RunParticipant p = run.participants().get(uuid);
                if (p == null) continue;
                if (!includeRemoved && p.status() == RunParticipant.ParticipantStatus.REMOVED) continue;
                ServerLevel runLevel = server.getLevel(run.spawnerDimension());
                return new RunRef(runLevel != null ? runLevel : controllerLevel, controller, run);
            }
        }
        return null;
    }
}
