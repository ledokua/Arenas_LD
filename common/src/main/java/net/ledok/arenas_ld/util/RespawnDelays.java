package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.config.ArenasLdConfig;

/**
 * How long a downed dungeon or raid player waits, from the config ({@code dungeon_respawn_min_ticks},
 * {@code respawn_per_death_*}, {@code raid_respawn_*}). The arithmetic is pure and unit tested; the
 * config-reading overloads are what the lifecycles call.
 */
public final class RespawnDelays {
    private RespawnDelays() {
    }

    /** The extra wait for a player who already went down {@code priorDeaths} times in this run. */
    public static int perDeathExtra(int priorDeaths, int perDeathTicks, int maxTicks) {
        long extra = (long) Math.max(0, perDeathTicks) * Math.max(0, priorDeaths);
        return (int) Math.min(extra, Math.max(0, maxTicks));
    }

    public static int perDeathExtra(int priorDeaths) {
        ArenasLdConfig config = ArenasLdConfig.getInstance();
        return perDeathExtra(priorDeaths, config.respawn_per_death_ticks, config.respawn_per_death_max_ticks);
    }

    /** A dungeon's base respawn time: the controller's, but at least the configured floor. */
    public static int dungeonBase(int controllerTicks) {
        return Math.max(Math.max(0, controllerTicks), ArenasLdConfig.getInstance().dungeon_respawn_min_ticks);
    }

    /** Whether downed raid players come back together on the shared clock. */
    public static boolean raidTogether() {
        return ArenasLdConfig.getInstance().raid_respawn_together;
    }

    /** Whether earlier deaths delay a downed raid player past the shared mark ({@code raid_per_death_extra}). */
    public static boolean raidPerDeathExtra() {
        return ArenasLdConfig.getInstance().raid_per_death_extra;
    }

    /** The raid's shared respawn interval: the controller's (its admin screen), else the configured default. */
    public static int raidWave(int controllerTicks) {
        int configured = ArenasLdConfig.getInstance().raid_respawn_wave_ticks;
        return Math.max(1, controllerTicks > 0 ? controllerTicks : configured > 0 ? configured : 1200);
    }

    /**
     * Ticks until a downed raid player respawns on the shared clock, which fires whenever
     * {@code elapsed} (ticks since the raid started) is a multiple of {@code wave}: the first such
     * moment once their own wait {@code ownTicksLeft} is over. 0 = now.
     */
    public static long ticksToTogetherRespawn(long elapsed, int ownTicksLeft, int wave) {
        int interval = Math.max(1, wave);
        long readyAt = Math.max(0, elapsed) + Math.max(0, ownTicksLeft);
        long fireAt = ((readyAt + interval - 1) / interval) * interval;
        return fireAt - Math.max(0, elapsed);
    }
}
