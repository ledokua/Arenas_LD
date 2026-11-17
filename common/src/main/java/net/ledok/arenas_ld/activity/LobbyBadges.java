package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.packet.LobbyBadgePayload;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps every player's "you're in a lobby" HUD badge current: twice a second the lobbies are read, and a player
 * whose badge changed (joined, left, someone readied, the run started) gets the new one. Nothing is sent while
 * nothing changes.
 */
public final class LobbyBadges {
    private static final int INTERVAL = 10;
    private static final Map<UUID, LobbyBadgePayload> SENT = new HashMap<>();

    private LobbyBadges() {
    }

    public static void register() {
        ArenasEvents.SERVER_TICK_END.add(LobbyBadges::tick);
        ArenasEvents.PLAYER_DISCONNECT.add((player, server) -> SENT.remove(player.getUUID()));
        ArenasEvents.SERVER_STOPPED.add(server -> SENT.clear());
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % INTERVAL != 0 || server.getPlayerList().getPlayerCount() == 0) {
            return;
        }
        Map<UUID, LobbyMembership.Membership> lobbies = LobbyMembership.all(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            LobbyBadgePayload badge = badge(lobbies.get(player.getUUID()));
            if (!badge.equals(SENT.getOrDefault(player.getUUID(), LobbyBadgePayload.NONE))) {
                SENT.put(player.getUUID(), badge);
                ArenasNetwork.sendToPlayer(player, badge);
            }
        }
    }

    private static LobbyBadgePayload badge(LobbyMembership.Membership membership) {
        if (membership == null || membership.lobby().status() == LobbyStatus.IN_RUN) {
            return LobbyBadgePayload.NONE;
        }
        Lobby lobby = membership.lobby();
        return new LobbyBadgePayload(true, membership.activityName(), lobby.members().size(),
            membership.host().getMaxPartySize(), lobby.readyMembers().size());
    }
}
