package net.ledok.arenas_ld.stats;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.api.ArenasCombatLog;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.util.DownedSpectators;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Combat log sessions (4.42.0, LeDok 2026-11-18): the run recorder for fights that are not Arenas runs, so far
 * duels ({@code ArenasApi.startCombatLog}). A session is a {@link RunStats} with {@code pvp} on, its own participant
 * list and no controller: this class ticks it, ends it when the caller says, and stores it like a run (type "duel").
 * Its fighters' hits on each other are dealt damage, kills and assists; the event log, gear, spells and healing are
 * recorded as in a run.
 */
public final class CombatSessions {
    private static final List<Session> LIVE = new ArrayList<>();

    private CombatSessions() {
    }

    public static void register() {
        ArenasEvents.SERVER_TICK_END.add(server -> {
            for (Session session : new ArrayList<>(LIVE)) {
                session.stats.tick(server);
            }
        });
        // A server stop cancels the sessions still going: nothing is stored for a fight the stop cut short.
        ArenasEvents.SERVER_STOPPING.add(server -> {
            for (Session session : new ArrayList<>(LIVE)) {
                session.close(server, null, false);
            }
            LIVE.clear();
        });
    }

    /** The session {@code player} fights in, or null. */
    static @Nullable Session of(UUID player) {
        for (Session session : LIVE) {
            RunParticipant participant = session.participants.get(player);
            if (participant != null && participant.status() != RunParticipant.ParticipantStatus.REMOVED) {
                return session;
            }
        }
        return null;
    }

    /** Starts a session; the refusal says why there is none. */
    public static ArenasCombatLog.Start start(MinecraftServer server, String key, Component title,
                                              List<ArenasCombatLog.Fighter> fighters) {
        if (!RunStats.enabled()) {
            return new ArenasCombatLog.Start(null, Component.translatable("message.arenas_ld.combat_log.disabled"));
        }
        if (fighters.isEmpty()) {
            return new ArenasCombatLog.Start(null, Component.translatable("message.arenas_ld.combat_log.no_fighters"));
        }
        for (ArenasCombatLog.Fighter fighter : fighters) {
            if (ArenasCombatLog.inRun(fighter.uuid())) {
                return new ArenasCombatLog.Start(null,
                    Component.translatable("message.arenas_ld.combat_log.in_run", nameOf(server, fighter.uuid())));
            }
            if (of(fighter.uuid()) != null) {
                return new ArenasCombatLog.Start(null,
                    Component.translatable("message.arenas_ld.combat_log.in_session", nameOf(server, fighter.uuid())));
            }
        }
        Session session = new Session(server, key, title, fighters);
        LIVE.add(session);
        session.stats.tick(server); // marks the online fighters at once (the tick does it again each second)
        for (ArenasCombatLog.Fighter fighter : fighters) {
            ServerPlayer player = server.getPlayerList().getPlayer(fighter.uuid());
            if (player != null) {
                PlayerCounters counters = session.stats.counters(fighter.uuid(), player.getGameProfile().getName());
                ((RunStatsHolder) player).arenasLd$setRunStats(counters);
                session.stats.captureStart(server, counters, player); // gear and stats now, not at the next full second
            }
        }
        return new ArenasCombatLog.Start(session, null);
    }

    static String nameOf(MinecraftServer server, UUID uuid) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        var cache = server.getProfileCache();
        return cache == null ? uuid.toString() : cache.get(uuid).map(com.mojang.authlib.GameProfile::getName).orElse(uuid.toString());
    }

    static final class Session implements ArenasCombatLog.Session {
        final String key;
        final RunStats stats;
        final Map<UUID, RunParticipant> participants = new LinkedHashMap<>();
        private boolean over;

        Session(MinecraftServer server, String key, Component title, List<ArenasCombatLog.Fighter> fighters) {
            this.key = key;
            long startTick = server.overworld().getGameTime();
            ServerPlayer first = server.getPlayerList().getPlayer(fighters.get(0).uuid());
            ArenasApi.InstanceRef where = first == null
                ? new ArenasApi.InstanceRef(net.minecraft.world.level.Level.OVERWORLD, net.minecraft.core.BlockPos.ZERO)
                : new ArenasApi.InstanceRef(first.level().dimension(), first.blockPosition());
            ResourceLocation id = ResourceLocation.tryParse(key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.:/-]", "_"));
            // Fighters grouped by side (the details list them team by team), in the caller's order within a side.
            List<ArenasCombatLog.Fighter> ordered = new ArrayList<>();
            for (ArenasCombatLog.Fighter fighter : fighters) {
                if (ordered.stream().noneMatch(f -> f.side().equals(fighter.side()))) {
                    fighters.stream().filter(f -> f.side().equals(fighter.side())).forEach(ordered::add);
                }
            }
            DownedSpectators.RunView view = new DownedSpectators.RunView(this, this::participantsNow, () -> over,
                uuid -> false, (time, uuid) -> -1L);
            this.stats = new RunStats(view, () -> new RunDetailsSource(id, title, "duel", DifficultyTier.EASY, where,
                false, List.of(), startTick, 0, 0, result(), -1, 0, -1), true);
            for (ArenasCombatLog.Fighter fighter : ordered) {
                String name = nameOf(server, fighter.uuid());
                participants.put(fighter.uuid(), new RunParticipant(fighter.uuid(), name,
                    RunParticipant.ParticipantStatus.ACTIVE, startTick));
                PlayerCounters counters = stats.counters(fighter.uuid(), name);
                counters.side = fighter.side();
                counters.loadoutId = fighter.loadout() == null || fighter.loadout().isEmpty() ? null : fighter.loadout();
            }
        }

        private String result() {
            return stats == null || stats.winnerSide == null ? "" : stats.winnerSide.isEmpty() ? "draw" : "win";
        }

        /** The fighters with their deaths so far (the record's deaths column). */
        private Map<UUID, RunParticipant> participantsNow() {
            Map<UUID, RunParticipant> out = new LinkedHashMap<>(participants.size());
            for (Map.Entry<UUID, RunParticipant> entry : participants.entrySet()) {
                RunParticipant p = entry.getValue();
                PlayerCounters counters = stats == null ? null : stats.players().get(entry.getKey());
                out.put(entry.getKey(), new RunParticipant(p.playerUuid(), p.playerName(), p.status(), p.lastSeenTick(),
                    counters == null ? 0 : counters.pvpDeaths));
            }
            return out;
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public boolean ended() {
            return over;
        }

        @Override
        public void rejoin(ServerPlayer player) {
            RunParticipant p = participants.get(player.getUUID());
            if (p != null && !over && p.status() != RunParticipant.ParticipantStatus.REMOVED) {
                ((RunStatsHolder) player).arenasLd$setRunStats(stats.counters(player.getUUID(), p.playerName()));
            }
        }

        @Override
        public void remove(UUID player) {
            RunParticipant p = participants.get(player);
            if (p != null && !over) {
                participants.put(player, p.withStatus(RunParticipant.ParticipantStatus.REMOVED, p.lastSeenTick()));
            }
        }

        @Override
        public @Nullable ArenasCombatLog.Result end(MinecraftServer server, @Nullable String winnerSide) {
            return close(server, winnerSide == null || "draw".equalsIgnoreCase(winnerSide) ? "" : winnerSide, true);
        }

        @Override
        public void cancel(MinecraftServer server) {
            close(server, "", false);
        }

        @Nullable ArenasCombatLog.Result close(MinecraftServer server, @Nullable String winnerSide, boolean keep) {
            if (over) {
                return null;
            }
            stats.winnerSide = winnerSide == null ? "" : winnerSide;
            RunRecord record;
            try {
                record = stats.finish(server, keep);
            } finally {
                over = true;
                LIVE.remove(this);
            }
            if (record == null || !keep) {
                return null;
            }
            List<ArenasCombatLog.Summary> players = new ArrayList<>();
            for (RunRecord.Player p : record.players) {
                players.add(new ArenasCombatLog.Summary(UUID.fromString(p.uuid), p.name, p.side, p.dealt, p.taken,
                    p.healedSelf + p.healedOthers, p.blockedShield + p.blockedArmor + p.blockedResistance
                    + p.blockedEnchant + p.blockedAbsorption,
                    p.topHits == null || p.topHits.isEmpty() ? 0 : p.topHits.get(0).amount, p.kills, p.deaths, p.assists,
                    p.bestMinuteDealt, record.durationSeconds));
            }
            ArenasLdMod.LOGGER.info("[Arenas] Combat log {} stored as {} ({} s, winner side '{}')", key, record.runId,
                record.durationSeconds, record.winnerSide);
            return new ArenasCombatLog.Result(record.runId, List.copyOf(players));
        }
    }
}
