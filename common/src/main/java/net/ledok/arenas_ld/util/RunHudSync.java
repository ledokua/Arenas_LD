package net.ledok.arenas_ld.util;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.packet.RunHudPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Pushes {@link RunHudPayload} timer-bar state to run participants. The lifecycles call
 * {@link #tickSend} from their per-tick bar updates; it only actually sends on whole-second
 * boundaries so the wire cost matches the old boss-bar name updates. The client counts down
 * locally between packets, so once a second is enough for a smooth timer.
 */
public final class RunHudSync {
    /** Ticks between sends; also the client's staleness baseline for auto-hiding. */
    public static final int SEND_INTERVAL_TICKS = 20;

    private RunHudSync() {
    }

    /** Sends {@code payload} to every listed online player when the world clock hits a send tick. */
    public static void tickSend(ServerLevel world, Iterable<UUID> uuids, RunHudPayload payload) {
        if (world.getGameTime() % SEND_INTERVAL_TICKS != 0) {
            return;
        }
        send(world, uuids, payload);
    }

    /** Sends unconditionally — for transitions (run start, close start, hide). */
    public static void send(ServerLevel world, Iterable<UUID> uuids, RunHudPayload payload) {
        for (UUID uuid : uuids) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    /** Clears the bar for every listed online player. */
    public static void hide(ServerLevel world, Iterable<UUID> uuids) {
        send(world, uuids, RunHudPayload.HIDDEN);
    }

    /** Clears the bar for one player (e.g. when they are removed from a run mid-flight). */
    public static void hide(ServerPlayer player) {
        ServerPlayNetworking.send(player, RunHudPayload.HIDDEN);
    }
}
