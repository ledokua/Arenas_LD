package net.ledok.arenas_ld.api;

import net.ledok.arenas_ld.stats.CombatSessions;
import net.ledok.arenas_ld.stats.RunRecords;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Combat log sessions (4.42.0): Arenas' run recorder for fights that are not Arenas runs, so far duels. A session
 * records what a run records for each fighter (the event log with hits, blocks, spells, effects, swaps and deaths;
 * damage and healing by source; top hits; the best minute; gear and skills at the start) and counts the fighters'
 * hits on each other as dealt damage, with a kill and a death on a hit that leaves someone at 0 HP (the duel mod
 * stops the death itself). When it ends it is stored like a run (type "duel", kept 30 days, left out of the run
 * history and of {@code /arenasld run last}) and opens in the run details screen (Party, Damage, Healing, Timeline).
 *
 * <p>Call everything on the server thread. A player is in one session at most, and never in a session and an Arenas
 * run at once.
 */
public final class ArenasCombatLog {
    private ArenasCombatLog() {
    }

    /**
     * One fighter.
     *
     * @param side    their team label ("A", "B", …): the details list fighters side by side, and the winning side's
     *                fighters are "won", the rest "lost"
     * @param loadout the rental kit id they fight with, "" for their own gear (shown to staff, stored per player)
     */
    public record Fighter(UUID uuid, String side, String loadout) {
    }

    /** What {@link #start} gives: the session, or why there is none. */
    public record Start(@Nullable Session session, @Nullable Component refusal) {
    }

    /**
     * One fighter's totals when the session ended.
     *
     * @param healed     effective healing done, on themselves and others
     * @param blocked    damage stopped by shield, armor, Resistance, protection enchantments and absorption
     * @param topHit     their biggest hit (before overkill is cut off), 0 when none
     * @param deaths     hits that left them at 0 HP
     * @param assists    others' deaths they did 10%+ of the max HP toward without the killing blow
     * @param bestMinute their best damage minute
     */
    public record Summary(UUID uuid, String name, String side, float dealt, float taken, float healed, float blocked,
                          float topHit, int kills, int deaths, int assists, float bestMinute, int durationSeconds) {
    }

    /** A stored session: its record id (for {@link #openDetails}) and every fighter's totals. */
    public record Result(String recordId, List<Summary> players) {
    }

    /** A running session. */
    public interface Session {
        /** The key it was started with. */
        String key();

        boolean ended();

        /**
         * A fighter reconnected. Not needed: the session marks its online fighters again within a second, a relog
         * included. Safe to call any time.
         */
        void rejoin(ServerPlayer player);

        /** A fighter is out for good (their row shows "out"); their numbers so far stay. */
        void remove(UUID player);

        /**
         * Ends and stores it. Call before cleaning up the arena or giving inventories back, so that isn't logged.
         *
         * @param winnerSide the winning side's label, or "draw" / "" / null for a draw
         * @return the record id and totals; null when it had already ended (or was cancelled)
         */
        @Nullable Result end(MinecraftServer server, @Nullable String winnerSide);

        /** Ends it without storing anything (a cancelled fight). */
        void cancel(MinecraftServer server);
    }

    /** Whether the player is in a dungeon or raid run now (a session would be refused). */
    public static boolean inRun(UUID player) {
        return net.ledok.arenas_ld.util.RunLeftovers.isInRun(player)
            || net.ledok.arenas_ld.stats.RunDetails.runOf(player) != null;
    }

    /**
     * Starts recording. Refused when run stats are off, there are no fighters, or a fighter is in a run or another
     * session. A server stop cancels sessions still going (nothing is stored).
     *
     * @param key   the caller's id for it, e.g. "duels:1a2b3c4d" (the record id is built from it)
     * @param title the details screen's title, e.g. "Ranked duel · 2v2"
     */
    public static Start start(MinecraftServer server, String key, Component title, List<Fighter> fighters) {
        return CombatSessions.start(server, key, title, fighters);
    }

    /**
     * Opens a stored session's details for {@code viewer}: Party, Damage, Healing and Timeline, final numbers.
     * Without {@code staff} only a fighter of that session may open it; with it anyone (check the permission first),
     * with the staff lines. The file is read off the server thread when it isn't in memory.
     *
     * @return false when the id is unknown, or the viewer wasn't in it and isn't staff
     */
    public static boolean openDetails(ServerPlayer viewer, String recordId, boolean staff) {
        if (!staff && !RunRecords.wasIn(recordId, viewer.getUUID())) {
            return false;
        }
        return RunRecords.open(viewer, recordId, staff);
    }
}
