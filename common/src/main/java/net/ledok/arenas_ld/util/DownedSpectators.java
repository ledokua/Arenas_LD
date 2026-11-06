package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.arena.run.ArenaPhase;
import net.ledok.arenas_ld.arena.run.ArenaRun;
import net.ledok.arenas_ld.dungeon.run.DungeonPhase;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.ledok.arenas_ld.raid.run.RaidPhase;
import net.ledok.arenas_ld.raid.run.RaidRun;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Downed run players are spectators, and a free spectator could fly anywhere, teleport to any player
 * through the spectator menu, or spectate any entity. While a player is a downed participant of a
 * dungeon, raid or arena run (until they respawn, leave, or the run is finalized), this keeps them on
 * their party:
 * <ul>
 *   <li><b>Teammate camera.</b> Their camera is the nearest living party member (same dimension
 *       first). Sneaking switches to the next one instead of leaving the camera. A spectator-menu
 *       teleport or an entity click only works on a living party member, and only switches the
 *       camera. When the watched teammate goes down, leaves, logs out or changes dimension, another
 *       one is picked.</li>
 *   <li><b>Tether.</b> With nobody to watch, they fly freely only within
 *       {@code downed_spectator_radius} blocks of where they went down; further away, or in another
 *       dimension, they are put back on that spot.</li>
 * </ul>
 *
 * <p>The lifecycles register a player where they go down or come back downed after a reconnect, and
 * release them before their game mode is switched back. Every check also reads the run itself, so
 * any way out that a call site misses drops the entry on the next tick. All lookups are by player
 * UUID, so the per-tick work is proportional to the number of downed players only.
 *
 * <p>Server thread only.
 */
public final class DownedSpectators {
    /** How often (ticks) the tether is checked and a self-camera player looks for a teammate again. */
    public static final int TETHER_INTERVAL = 5;
    /** How often (ticks) the action-bar line (respawn countdown, "Watching ...") is sent again. */
    private static final int HINT_INTERVAL = 20;
    /** {@link RunView#respawnLeft}: the player comes back with the next wave (arenas). */
    public static final long NEXT_WAVE = -2;
    /** "Pinned" (radius 0 or less): how far they may drift from the spot before being put back. */
    static final double PIN_SLACK = 1.0;

    /**
     * A downed player's run, read live by the checks.
     *
     * @param respawnLeft ticks until a downed player is back, given the game time: -1 unknown,
     *                    {@link #NEXT_WAVE} with the next wave
     */
    public record RunView(Object run, Supplier<Map<UUID, RunParticipant>> participants, BooleanSupplier over,
                          Predicate<UUID> downed, java.util.function.BiFunction<Long, UUID, Long> respawnLeft) {

        public static RunView of(DungeonRun run) {
            return new RunView(run, run::participants, () -> run.phase() == DungeonPhase.DONE, run::isDowned,
                (time, uuid) -> {
                    var downed = run.downedPlayers().get(uuid);
                    return downed == null ? -1L : (long) downed.ticksRemaining();
                });
        }

        public static RunView of(RaidRun run) {
            return new RunView(run, run::participants, () -> run.phase() == RaidPhase.DONE,
                uuid -> run.downedPlayers().containsKey(uuid),
                (time, uuid) -> net.ledok.arenas_ld.raid.run.RaidRunLifecycle.respawnTicksLeft(time, run, uuid));
        }

        public static RunView of(ArenaRun run) {
            return new RunView(run, run::participants, () -> run.phase() == ArenaPhase.DONE,
                uuid -> run.downedPlayers().containsKey(uuid), (time, uuid) -> NEXT_WAVE);
        }

        /** Whether {@code uuid} is still a downed participant of a run that isn't over. */
        boolean stillDowned(UUID uuid) {
            if (over.getAsBoolean()) {
                return false;
            }
            RunParticipant participant = participants.get().get(uuid);
            return participant != null && participant.status() != ParticipantStatus.REMOVED && downed.test(uuid);
        }

        /** Whether {@code player} is a standing party member: active, online, alive, not a spectator. */
        boolean isLivingMate(ServerPlayer player) {
            RunParticipant participant = participants.get().get(player.getUUID());
            return participant != null && participant.status() == ParticipantStatus.ACTIVE
                && !downed.test(player.getUUID()) && !player.isSpectator() && player.isAlive()
                && !player.isRemoved() && !player.hasDisconnected();
        }
    }

    private static final class Entry {
        final RunView run;
        ResourceKey<Level> dimension;
        Vec3 spot;
        @Nullable UUID watching;
        boolean sneakHeld;
        int hintTicks;

        Entry(RunView run, ResourceKey<Level> dimension, Vec3 spot) {
            this.run = run;
            this.dimension = dimension;
            this.spot = spot;
        }
    }

    /** Downed player UUID → their run, where they went down and whom they watch. */
    private static final Map<UUID, Entry> DOWNED = new HashMap<>();
    /** Run → the boss bar its party sees while anyone in it is downed ("Downed: Bob 12 s · …"). */
    private static final Map<Object, net.minecraft.server.level.ServerBossEvent> PARTY_BARS = new HashMap<>();

    private DownedSpectators() {
    }

    public static void register() {
        ArenasEvents.SERVER_TICK_END.add(DownedSpectators::tick);
        ArenasEvents.SERVER_STOPPED.add(server -> {
            DOWNED.clear();
            PARTY_BARS.values().forEach(net.minecraft.server.level.ServerBossEvent::removeAllPlayers);
            PARTY_BARS.clear();
        });
        // Offline: the entry (and its spot) stays until the run is done with them; others watching
        // them pick someone else on the next tick.
        ArenasEvents.PLAYER_DISCONNECT.add((player, server) -> {
            Entry entry = DOWNED.get(player.getUUID());
            if (entry != null) {
                entry.watching = null;
            }
        });
    }

    // ── pure helpers (unit tested) ───────────────────────────────────────────

    /**
     * The member after {@code current} in {@code order} (wrapping around) that passes {@code eligible};
     * {@code current} itself when it is the only one left that passes, null when none does.
     * {@code current} may be null or missing from the list: the search then starts at the front.
     */
    public static <T> @Nullable T nextInCycle(List<T> order, @Nullable T current, Predicate<T> eligible) {
        int size = order.size();
        int start = current == null ? -1 : order.indexOf(current);
        for (int step = 1; step <= size; step++) {
            T candidate = order.get(Math.floorMod(start + step, size));
            if (!candidate.equals(current) && eligible.test(candidate)) {
                return candidate;
            }
        }
        return current != null && order.contains(current) && eligible.test(current) ? current : null;
    }

    /**
     * Whether a self-camera downed spectator must be put back on their spot: in another dimension, or
     * farther than {@code radius} blocks from it ({@value #PIN_SLACK} block when the radius is 0 or less).
     */
    public static boolean outsideTether(boolean sameDimension, double distanceSqr, double radius) {
        if (!sameDimension) {
            return true;
        }
        double allowed = radius <= 0 ? PIN_SLACK : radius;
        return distanceSqr > allowed * allowed;
    }

    // ── lifecycle calls ──────────────────────────────────────────────────────

    /** {@code player} just went down (and is a spectator now): their spot is here; watch a teammate. */
    public static void down(ServerPlayer player, RunView run) {
        Entry entry = new Entry(run, player.level().dimension(), player.position());
        DOWNED.put(player.getUUID(), entry);
        attach(player, entry, nearestMate(player, entry));
    }

    /**
     * {@code player} came back from a disconnect still downed: the spot they went down on is kept
     * (when this server remembers it for this run), otherwise it is where they are now.
     */
    public static void rejoined(ServerPlayer player, RunView run) {
        Entry known = DOWNED.get(player.getUUID());
        if (known == null || known.run.run() != run.run()) {
            down(player, run);
            return;
        }
        known.watching = null;
        attach(player, known, nearestMate(player, known));
        if (known.watching == null) {
            tether(player, known); // logged out somewhere else (watching a teammate)
        }
    }

    /**
     * {@code player} is no longer a restricted downed spectator (respawn, exit, run end): their camera
     * is their own again. Call before their game mode is switched back.
     */
    public static void release(ServerPlayer player) {
        DOWNED.remove(player.getUUID());
        if (player.getCamera() != player) {
            player.setCamera(player);
        }
    }

    /** An offline player's run is over for them. */
    public static void forget(UUID player) {
        DOWNED.remove(player);
    }

    /** Whether {@code player} is held as a downed spectator right now (O(1)). */
    public static boolean isRestricted(ServerPlayer player) {
        return live(player) != null;
    }

    /** Whom {@code player} watches, or null (not downed, or on their own camera). */
    public static @Nullable UUID watching(ServerPlayer player) {
        Entry entry = live(player);
        return entry == null ? null : entry.watching;
    }

    /** Where {@code player} went down, or null when they aren't held. */
    public static @Nullable Vec3 downSpot(ServerPlayer player) {
        Entry entry = live(player);
        return entry == null ? null : entry.spot;
    }

    // ── hooks (mixins) ───────────────────────────────────────────────────────

    /**
     * {@code ServerPlayer.tick} asks whether a spectator with another camera wants to leave it
     * (sneaking). A held downed spectator never leaves it: a fresh sneak press switches to the next
     * living teammate instead.
     *
     * @return what vanilla should see
     */
    public static boolean wantsToLeaveCamera(ServerPlayer player, boolean sneaking) {
        Entry entry = live(player);
        if (entry == null) {
            return sneaking;
        }
        boolean pressed = sneaking && !entry.sneakHeld;
        entry.sneakHeld = sneaking;
        if (pressed && entry.watching != null) {
            List<UUID> order = mateOrder(player, entry);
            UUID next = nextInCycle(order, entry.watching, uuid -> livingMate(player.server, entry, uuid) != null);
            ServerPlayer target = next == null ? null : livingMate(player.server, entry, next);
            attach(player, entry, target);
        }
        return false;
    }

    /**
     * A spectator-menu teleport (or an entity click) by a held downed spectator: a living teammate
     * becomes their camera, anything else is refused.
     *
     * @return false when {@code player} isn't held (vanilla handles it)
     */
    public static boolean spectateRequest(ServerPlayer player, @Nullable Entity target) {
        Entry entry = live(player);
        if (entry == null) {
            return false;
        }
        if (target instanceof ServerPlayer mate && !mate.getUUID().equals(player.getUUID())
                && entry.run.isLivingMate(mate)) {
            attach(player, entry, mate);
        } else {
            player.displayClientMessage(Component.translatableWithFallback("message.arenas_ld.run.downed_party_only",
                    "While downed, you can only watch your party.")
                .withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    // ── internals ────────────────────────────────────────────────────────────

    /** The player's entry while they are still a downed spectator of a running run; drops stale ones. */
    private static @Nullable Entry live(ServerPlayer player) {
        Entry entry = DOWNED.get(player.getUUID());
        if (entry == null) {
            return null;
        }
        if (!player.isSpectator() || !entry.run.stillDowned(player.getUUID())) {
            release(player);
            return null;
        }
        return entry;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % HINT_INTERVAL == 0) {
            updatePartyBars(server);
        }
        if (DOWNED.isEmpty()) {
            return;
        }
        boolean tetherTick = server.getTickCount() % TETHER_INTERVAL == 0;
        for (Map.Entry<UUID, Entry> pair : new ArrayList<>(DOWNED.entrySet())) {
            UUID uuid = pair.getKey();
            Entry entry = pair.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player == null) {
                if (!entry.run.stillDowned(uuid)) {
                    DOWNED.remove(uuid);
                }
                continue;
            }
            if (live(player) == null) {
                continue; // released
            }
            Entity camera = player.getCamera();
            if (entry.watching != null) {
                ServerPlayer watched = server.getPlayerList().getPlayer(entry.watching);
                if (watched == null || camera != watched || watched.level() != player.level()
                        || !entry.run.isLivingMate(watched)) {
                    attach(player, entry, nearestMate(player, entry));
                } else if (--entry.hintTicks <= 0) {
                    hint(player, entry, watched);
                }
                continue;
            }
            if (--entry.hintTicks <= 0) {
                hint(player, entry, null); // the countdown alone
            }
            if (camera != player) {
                player.setCamera(player); // nothing else may be spectated
            } else if (tetherTick) {
                ServerPlayer mate = nearestMate(player, entry);
                if (mate != null) {
                    attach(player, entry, mate);
                } else {
                    tether(player, entry);
                }
            }
        }
    }

    /** Points the camera at {@code target}, or back at the player when null. */
    private static void attach(ServerPlayer player, Entry entry, @Nullable ServerPlayer target) {
        if (target == null) {
            entry.watching = null;
            if (player.getCamera() != player) {
                player.setCamera(player);
            }
            return;
        }
        if (player.getCamera() == target && target.level() != player.level()) {
            player.setCamera(player); // same camera, new dimension: setCamera only moves on a change
        }
        player.setCamera(target);
        entry.watching = target.getUUID();
        entry.sneakHeld = player.isShiftKeyDown(); // a held sneak doesn't switch at once
        hint(player, entry, target);
    }

    /** The action-bar line: the respawn countdown, then whom they watch (when they watch someone). */
    private static void hint(ServerPlayer player, Entry entry, @Nullable ServerPlayer target) {
        entry.hintTicks = HINT_INTERVAL;
        net.minecraft.network.chat.MutableComponent line = Component.empty();
        Component status = respawnStatus(entry.run.respawnLeft().apply(player.serverLevel().getGameTime(), player.getUUID()));
        if (status != null) {
            line.append(status);
        }
        if (target != null) {
            if (status != null) {
                line.append(" · ");
            }
            line.append(Component.translatableWithFallback("message.arenas_ld.run.downed_watching",
                "Watching %s — sneak to switch", target.getDisplayName()));
        }
        if (status != null || target != null) {
            player.displayClientMessage(line.withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** "Respawn in 12 s" / "Respawn with the next wave"; null when unknown. */
    private static @Nullable Component respawnStatus(long ticksLeft) {
        if (ticksLeft == NEXT_WAVE) {
            return Component.translatableWithFallback("message.arenas_ld.run.respawn_next_wave", "Respawn with the next wave");
        }
        if (ticksLeft < 0) {
            return null;
        }
        return Component.translatableWithFallback("message.arenas_ld.run.respawn_in", "Respawn in %s s",
            secondsLeft(ticksLeft));
    }

    /** Whole seconds shown for {@code ticks} left, rounded up (1 tick left still shows 1 s). */
    public static long secondsLeft(long ticks) {
        return (Math.max(0, ticks) + 19) / 20;
    }

    /**
     * Every second: each run with someone downed shows its party a boss bar naming them and their time
     * left; a run with nobody downed any more loses its bar. Plain vanilla boss bars: no client change.
     */
    private static void updatePartyBars(MinecraftServer server) {
        Map<Object, List<UUID>> downedByRun = new HashMap<>();
        Map<Object, RunView> views = new HashMap<>();
        for (Map.Entry<UUID, Entry> pair : DOWNED.entrySet()) {
            RunView view = pair.getValue().run;
            if (view.stillDowned(pair.getKey())) {
                downedByRun.computeIfAbsent(view.run(), k -> new ArrayList<>()).add(pair.getKey());
                views.put(view.run(), view);
            }
        }
        for (Object run : new ArrayList<>(PARTY_BARS.keySet())) {
            if (!downedByRun.containsKey(run)) {
                PARTY_BARS.remove(run).removeAllPlayers();
            }
        }
        long time = server.overworld().getGameTime();
        for (Map.Entry<Object, List<UUID>> group : downedByRun.entrySet()) {
            RunView view = views.get(group.getKey());
            Map<UUID, RunParticipant> participants = view.participants().get();
            net.minecraft.network.chat.MutableComponent title = Component.translatableWithFallback(
                "message.arenas_ld.run.party_downed", "Downed:").copy();
            for (UUID uuid : group.getValue()) {
                RunParticipant participant = participants.get(uuid);
                title.append(" ").append(participant != null ? participant.playerName() : "?").append(" ");
                long left = view.respawnLeft().apply(time, uuid);
                if (left == NEXT_WAVE) {
                    title.append(Component.translatableWithFallback("message.arenas_ld.run.party_downed_next_wave", "next wave"));
                } else if (left >= 0) {
                    title.append(Component.translatableWithFallback("message.arenas_ld.run.party_downed_seconds", "%s s",
                        secondsLeft(left)));
                }
            }
            net.minecraft.server.level.ServerBossEvent bar = PARTY_BARS.computeIfAbsent(group.getKey(),
                k -> new net.minecraft.server.level.ServerBossEvent(Component.empty(),
                    net.minecraft.world.BossEvent.BossBarColor.RED, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS));
            bar.setName(title);
            bar.setProgress(1.0F);
            java.util.Set<ServerPlayer> shown = new java.util.HashSet<>();
            for (RunParticipant participant : participants.values()) {
                if (participant.status() == ParticipantStatus.REMOVED) {
                    continue;
                }
                ServerPlayer member = server.getPlayerList().getPlayer(participant.playerUuid());
                if (member != null) {
                    shown.add(member);
                }
            }
            for (ServerPlayer viewer : new ArrayList<>(bar.getPlayers())) {
                if (!shown.contains(viewer)) {
                    bar.removePlayer(viewer);
                }
            }
            shown.forEach(bar::addPlayer);
        }
    }

    /** Puts a self-camera player back on their spot when they strayed too far. */
    private static void tether(ServerPlayer player, Entry entry) {
        boolean sameDimension = player.level().dimension().equals(entry.dimension);
        double distanceSqr = sameDimension ? player.position().distanceToSqr(entry.spot) : 0;
        if (!outsideTether(sameDimension, distanceSqr, net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().downed_spectator_radius)) {
            return;
        }
        ServerLevel level = player.server.getLevel(entry.dimension);
        if (level == null) {
            // The dimension is gone: hold them where they are instead.
            entry.dimension = player.level().dimension();
            entry.spot = player.position();
            return;
        }
        DebugLog.log("downed tether: {} pulled back to {} in {}", player.getScoreboardName(), entry.spot,
            entry.dimension.location());
        player.teleportTo(level, entry.spot.x, entry.spot.y, entry.spot.z, player.getYRot(), player.getXRot());
    }

    /** Living teammates in the run's participant order (the sneak cycle order), without the player. */
    private static List<UUID> mateOrder(ServerPlayer player, Entry entry) {
        List<UUID> order = new ArrayList<>();
        for (UUID uuid : entry.run.participants().get().keySet()) {
            if (!uuid.equals(player.getUUID())) {
                order.add(uuid);
            }
        }
        return order;
    }

    private static @Nullable ServerPlayer livingMate(MinecraftServer server, Entry entry, UUID uuid) {
        ServerPlayer mate = server.getPlayerList().getPlayer(uuid);
        return mate != null && entry.run.isLivingMate(mate) ? mate : null;
    }

    /** The nearest living teammate, one in the player's dimension first. */
    private static @Nullable ServerPlayer nearestMate(ServerPlayer player, Entry entry) {
        ServerPlayer best = null;
        boolean bestSameLevel = false;
        double bestDistance = Double.MAX_VALUE;
        for (UUID uuid : mateOrder(player, entry)) {
            ServerPlayer mate = livingMate(player.server, entry, uuid);
            if (mate == null) {
                continue;
            }
            boolean sameLevel = mate.level() == player.level();
            double distance = sameLevel ? mate.distanceToSqr(player) : Double.MAX_VALUE;
            if (best == null || (sameLevel && !bestSameLevel) || (sameLevel == bestSameLevel && distance < bestDistance)) {
                best = mate;
                bestSameLevel = sameLevel;
                bestDistance = distance;
            }
        }
        return best;
    }
}
