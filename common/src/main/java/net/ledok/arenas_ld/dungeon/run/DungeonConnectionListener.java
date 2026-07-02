package net.ledok.arenas_ld.dungeon.run;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Routes player logout/login events into the dungeon run lifecycle, which owns the disconnect
 * grace, downed-timer pausing, party feedback, and persisted eject logic.
 */
public final class DungeonConnectionListener {
    private DungeonConnectionListener() {
    }

    public static void register() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            DungeonRunLifecycle.handlePlayerDisconnect(server, handler.player));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            DungeonRunLifecycle.handlePlayerReconnect(server, handler.player));
    }
}

