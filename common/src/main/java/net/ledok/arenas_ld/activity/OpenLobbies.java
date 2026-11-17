package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code ArenasApi.openLobbies(server)} and friends (4.31.0): every lobby that is forming now on the server, a revision
 * that changes when that list changes, and joining one from anywhere.
 */
public final class OpenLobbies {
    /** How often (ticks) the list is checked for changes; only while someone listens. */
    private static final int CHECK_TICKS = 20;

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();
    private static long revision;
    private static int lastSignature;
    private static boolean signed;

    private OpenLobbies() {
    }

    /** Every PUBLIC or FRIENDS lobby that isn't in a run, across every loaded controller, by activity then age. */
    public static List<ArenasApi.OpenLobby> list(MinecraftServer server) {
        List<ArenasApi.OpenLobby> out = new ArrayList<>();
        for (ActivityHost host : LobbyMembership.liveHosts(server)) {
            ResourceLocation activity = host.activityId();
            if (activity == null) {
                continue;
            }
            Component name = ActivityRegistry.displayName(host.activityType(), host.activityName());
            for (Lobby lobby : host.getLobbies()) {
                if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED
                    || lobby.visibility() == LobbyVisibility.PRIVATE || lobby.members().isEmpty()) {
                    continue;
                }
                out.add(new ArenasApi.OpenLobby(activity, host.activityType(), name, lobby.lobbyId(), lobby.ownerUuid(),
                    lobby.ownerName(), List.copyOf(lobby.members()), host.getMaxPartySize(), lobby.readyMembers().size(),
                    ActivityHost.difficultyId(lobby.selectedTier()), lobby.hardcoreEnabled(),
                    List.copyOf(lobby.modifiers()), lobby.visibility() == LobbyVisibility.PUBLIC
                        ? ArenasApi.LobbyAccess.JOIN : ArenasApi.LobbyAccess.REQUEST,
                    lobby.createdAtTick(), lobby.minLevel()));
            }
        }
        out.sort(Comparator.comparing((ArenasApi.OpenLobby l) -> l.activity().toString())
            .thenComparingLong(ArenasApi.OpenLobby::createdAtTick));
        return out;
    }

    public static long revision() {
        return revision;
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    /** Once a second while anyone listens: a new revision (and the listeners) when the list changed. */
    public static void tick(MinecraftServer server) {
        if (LISTENERS.isEmpty() || server.getTickCount() % CHECK_TICKS != 0) {
            return;
        }
        int signature = list(server).hashCode();
        if (signed && signature == lastSignature) {
            return;
        }
        boolean first = !signed;
        signed = true;
        lastSignature = signature;
        if (first) {
            return; // the baseline
        }
        revision++;
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }

    /**
     * Joins (PUBLIC) or asks to join (FRIENDS) a lobby from anywhere, through the controller's usual flow, then opens
     * that activity's lobby screen. Null when it went through; otherwise why not (also told to the player).
     */
    public static @Nullable Component join(ServerPlayer player, ResourceLocation activity, UUID lobbyId) {
        Component refusal = tryJoin(player, activity, lobbyId);
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED));
        }
        return refusal;
    }

    private static @Nullable Component tryJoin(ServerPlayer player, ResourceLocation activity, UUID lobbyId) {
        ActivityHost host = ActivityRegistry.resolve(player.server, activity);
        if (host == null) {
            return Component.translatable("message.arenas_ld.open_lobbies.gone");
        }
        Lobby lobby = host.getLobbies().stream().filter(l -> l.lobbyId().equals(lobbyId)).findFirst().orElse(null);
        if (lobby == null || lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED
            || lobby.visibility() == LobbyVisibility.PRIVATE) {
            return Component.translatable("message.arenas_ld.open_lobbies.gone");
        }
        if (lobby.isMember(player.getUUID())) {
            LobbyScreens.open(player, host.asBlockEntity(), true);
            return null;
        }
        if (BusyStateCompat.isBusy(player.getUUID())) {
            return Component.translatable("message.arenas_ld.already_in_party");
        }
        Component gate = EntryGates.check(player, host, lobby.selectedTier());
        if (gate != null) {
            return gate;
        }
        Component level = LobbyLevels.refusal(player, lobby);
        if (level != null) {
            return level;
        }
        boolean ok = lobby.visibility() == LobbyVisibility.PUBLIC
            ? host.joinLobby(player, lobbyId)
            : host.requestJoin(player, lobbyId);
        host.asBlockEntity().setChanged();
        host.syncViewers();
        if (!ok) {
            return Component.translatable("message.arenas_ld.open_lobbies.cant_join");
        }
        LobbyScreens.open(player, host.asBlockEntity(), true);
        return null;
    }

    public static void clear() {
        signed = false;
    }
}
