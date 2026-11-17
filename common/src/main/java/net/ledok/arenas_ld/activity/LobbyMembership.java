package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Which lobby a player is in, across every dungeon, raid and arena controller, and the move from one
 * lobby to another: creating or joining a lobby takes the player out of the one they were in (owner
 * passes on, or the lobby closes when they were alone), with a chat line.
 */
public final class LobbyMembership {
    private LobbyMembership() {
    }

    /** A player's lobby and the controller holding it. */
    public record Membership(ActivityHost host, Lobby lobby) {
        public Component activityName() {
            return ActivityRegistry.displayName(host.activityType(), host.activityName());
        }
    }

    /** The loaded controllers the world knows of (controllers keep their chunk loaded). */
    public static List<ActivityHost> liveHosts(MinecraftServer server) {
        List<ActivityHost> hosts = new ArrayList<>();
        for (ActivityIndex.Entry entry : ActivityRegistry.knownControllers(server)) {
            ActivityHost host = ActivityRegistry.liveHost(server, entry);
            if (host != null) {
                hosts.add(host);
            }
        }
        return hosts;
    }

    /** Every lobby member's lobby; a player in several (from before 4.21.0) gets the first found. */
    public static Map<UUID, Membership> all(MinecraftServer server) {
        Map<UUID, Membership> out = new HashMap<>();
        for (ActivityHost host : liveHosts(server)) {
            for (Lobby lobby : host.getLobbies()) {
                for (UUID member : lobby.members()) {
                    out.putIfAbsent(member, new Membership(host, lobby));
                }
            }
        }
        return out;
    }

    /** The player's lobby, a running one first (their run), or null. */
    public static @Nullable Membership find(MinecraftServer server, UUID player) {
        Membership found = null;
        for (ActivityHost host : liveHosts(server)) {
            for (Lobby lobby : host.getLobbies()) {
                if (lobby.isMember(player)) {
                    if (lobby.status() == LobbyStatus.IN_RUN) {
                        return new Membership(host, lobby);
                    }
                    if (found == null) {
                        found = new Membership(host, lobby);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Before {@code player} creates or joins a lobby ({@code keep}: the one they join, null for a new one): takes
     * them out of every other lobby they are in and tells them. Returns false, leaving everything as it was, when
     * one of those lobbies is in a run (they are in it); the caller then refuses.
     */
    public static boolean leaveOthers(MinecraftServer server, UUID player, @Nullable UUID keep) {
        List<Membership> others = new ArrayList<>();
        for (ActivityHost host : liveHosts(server)) {
            for (Lobby lobby : host.getLobbies()) {
                if (lobby.isMember(player) && !lobby.lobbyId().equals(keep)) {
                    if (lobby.status() == LobbyStatus.IN_RUN) {
                        ServerPlayer online = server.getPlayerList().getPlayer(player);
                        if (online != null) {
                            online.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party")
                                .withStyle(net.minecraft.ChatFormatting.RED));
                        }
                        return false;
                    }
                    others.add(new Membership(host, lobby));
                }
            }
        }
        for (Membership other : others) {
            if (other.host().leaveLobby(player)) {
                other.host().syncViewers();
                ServerPlayer online = server.getPlayerList().getPlayer(player);
                if (online != null) {
                    online.sendSystemMessage(Component.translatable("message.arenas_ld.lobby.auto_left", other.activityName())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }
        }
        return true;
    }
}
