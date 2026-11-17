package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.run.ArenaRun;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.packet.AllyHealthPayload;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.run.RaidRun;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Sends each run member (dungeon, raid, arena) and each party member ({@code ArenasApi.setPartyProvider}: Eden's
 * parties) their teammates' health for the ally sidebar (UI rework step 4, TODO §7). Every
 * {@value #PERIOD} ticks, only to players whose list changed; health is rounded to 0.5 so regeneration doesn't
 * resend every time. A player with no teammates gets one empty list (the sidebar hides).
 */
public final class AllyHealthSync {
    private static final int PERIOD = 5;

    /** Who is in a player's party outside runs (the player included or not); null: no parties. */
    public static volatile @Nullable Function<ServerPlayer, Collection<UUID>> partyProvider;

    private static final Map<UUID, Integer> LAST = new HashMap<>();

    private AllyHealthSync() {
    }

    /** One run as the sidebar sees it. */
    private record Group(Map<UUID, RunParticipant> participants, DownedSpectators.RunView view) {
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % PERIOD != 0) {
            return;
        }
        Map<UUID, Group> runOf = new HashMap<>();
        for (DungeonRun run : ArenasLdMod.DUNGEON_MANAGER.activeRuns()) {
            add(runOf, DownedSpectators.RunView.of(run));
        }
        for (RaidControllerBlockEntity.ControllerKey key : RaidControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null && level.getBlockEntity(key.pos()) instanceof RaidControllerBlockEntity controller) {
                for (RaidRun run : controller.getActiveRuns().values()) {
                    add(runOf, DownedSpectators.RunView.of(run));
                }
            }
        }
        for (ArenaControllerBlockEntity.ControllerKey key : ArenaControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null && level.getBlockEntity(key.pos()) instanceof ArenaControllerBlockEntity controller) {
                for (ArenaRun run : controller.getActiveRuns().values()) {
                    add(runOf, DownedSpectators.RunView.of(run));
                }
            }
        }
        long now = server.overworld().getGameTime();
        Function<ServerPlayer, Collection<UUID>> parties = partyProvider;
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            List<AllyHealthPayload.Ally> allies = new ArrayList<>();
            Group group = runOf.get(viewer.getUUID());
            if (group != null) {
                for (Map.Entry<UUID, RunParticipant> entry : group.participants().entrySet()) {
                    if (!entry.getKey().equals(viewer.getUUID()) && allies.size() < AllyHealthPayload.MAX) {
                        allies.add(runAlly(server, group, entry.getValue(), now));
                    }
                }
            } else if (parties != null) {
                Collection<UUID> party;
                try {
                    party = parties.apply(viewer);
                } catch (RuntimeException e) {
                    party = List.of();
                }
                for (UUID member : party == null ? List.<UUID>of() : party) {
                    if (!member.equals(viewer.getUUID()) && allies.size() < AllyHealthPayload.MAX) {
                        ServerPlayer online = server.getPlayerList().getPlayer(member);
                        allies.add(online != null ? alive(online)
                            : new AllyHealthPayload.Ally(member, "?", 0, 0, 0, AllyHealthPayload.State.OFFLINE, -1));
                    }
                }
            }
            send(viewer, allies);
        }
        LAST.keySet().removeIf(uuid -> server.getPlayerList().getPlayer(uuid) == null);
    }

    private static void add(Map<UUID, Group> runOf, DownedSpectators.RunView view) {
        Map<UUID, RunParticipant> participants = new LinkedHashMap<>(view.participants().get());
        Group group = new Group(participants, view);
        for (UUID uuid : participants.keySet()) {
            runOf.putIfAbsent(uuid, group);
        }
    }

    private static AllyHealthPayload.Ally runAlly(MinecraftServer server, Group group, RunParticipant participant, long now) {
        UUID uuid = participant.playerUuid();
        ServerPlayer online = server.getPlayerList().getPlayer(uuid);
        if (participant.status() == RunParticipant.ParticipantStatus.REMOVED) {
            return new AllyHealthPayload.Ally(uuid, participant.playerName(), 0, 0, 0, AllyHealthPayload.State.OUT, -1);
        }
        if (online == null) {
            return new AllyHealthPayload.Ally(uuid, participant.playerName(), 0, 0, 0, AllyHealthPayload.State.OFFLINE, -1);
        }
        if (group.view().downed().test(uuid)) {
            Long left = group.view().respawnLeft().apply(now, uuid);
            int seconds = left == null || left < 0 ? -1 : (int) ((left + 19) / 20);
            return new AllyHealthPayload.Ally(uuid, participant.playerName(), 0, online.getMaxHealth(), 0,
                AllyHealthPayload.State.DOWNED, seconds);
        }
        return alive(online);
    }

    private static AllyHealthPayload.Ally alive(ServerPlayer player) {
        return new AllyHealthPayload.Ally(player.getUUID(), player.getGameProfile().getName(), half(player.getHealth()),
            half(player.getMaxHealth()), half(player.getAbsorptionAmount()), AllyHealthPayload.State.ALIVE, -1);
    }

    private static float half(float value) {
        return Math.round(Math.max(0, value) * 2) / 2.0f;
    }

    private static void send(ServerPlayer viewer, List<AllyHealthPayload.Ally> allies) {
        int hash = allies.hashCode();
        Integer last = LAST.get(viewer.getUUID());
        if (allies.isEmpty() && last == null) {
            return; // never had any, nothing to clear
        }
        if (last != null && last == hash) {
            return;
        }
        ArenasNetwork.sendToPlayer(viewer, new AllyHealthPayload(List.copyOf(allies)));
        if (allies.isEmpty()) {
            LAST.remove(viewer.getUUID());
        } else {
            LAST.put(viewer.getUUID(), hash);
        }
    }

    public static void clear() {
        LAST.clear();
    }
}
