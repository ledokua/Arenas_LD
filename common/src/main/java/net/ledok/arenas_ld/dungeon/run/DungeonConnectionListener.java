package net.ledok.arenas_ld.dungeon.run;

import net.ledok.arenas_ld.util.DebugLog;
import net.ledok.arenas_ld.util.ServerTaskScheduler;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import net.ledok.arenas_ld.platform.ArenasEvents;

/**
 * Routes player logout/login events into the dungeon run lifecycle, which owns the disconnect
 * grace, downed-timer pausing, party feedback, and persisted eject logic.
 *
 * <p>Reconnect handling is deferred to the next tick: it may teleport the player, and a
 * teleport fired from inside the JOIN event (mid {@code placeNewPlayer}) can leave the client
 * in limbo and desync chunk tracking.
 */
public final class DungeonConnectionListener {
    private DungeonConnectionListener() {
    }

    public static void register() {
        ArenasEvents.PLAYER_DISCONNECT.add((disconnecting, server) ->
            DungeonRunLifecycle.handlePlayerDisconnect(server, disconnecting));

        ArenasEvents.PLAYER_JOIN.add((joining, server) -> {
            UUID uuid = joining.getUUID();
            ServerTaskScheduler.nextTick(s -> {
                ServerPlayer player = s.getPlayerList().getPlayer(uuid);
                if (player == null) {
                    DebugLog.log("reconnect(dungeon): {} vanished before deferred handling", uuid);
                    return;
                }
                DungeonRunLifecycle.handlePlayerReconnect(s, player);
            });
        });
    }
}
