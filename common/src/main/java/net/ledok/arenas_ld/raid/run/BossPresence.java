package net.ledok.arenas_ld.raid.run;

/**
 * Whether a raid whose boss isn't found ({@code ServerLevel.getEntity} is null) waits or is lost.
 *
 * <p>{@code getEntity} only sees loaded entities: a boss whose chunk was entity-unloaded is saved
 * to disk, not gone, and comes back a few ticks after its chunk is loaded again (entity loading
 * is asynchronous). So the run waits while the boss's last-known chunk has no entities loaded,
 * and loses only when that chunk has its entities loaded and the boss still isn't there for
 * {@link #LOADED_GRACE_TICKS}, or when the boss stays missing for {@link #HARD_CAP_TICKS} in all.
 */
public final class BossPresence {
    /** The boss's chunk has its entities loaded and the boss isn't in it: lost after this long. */
    public static final int LOADED_GRACE_TICKS = 100;
    /** Missing this long, whatever the chunk's state: lost. */
    public static final int HARD_CAP_TICKS = 600;

    public enum Verdict { WAIT, LOSE }

    private BossPresence() {
    }

    /**
     * @param missingTicks       how long the boss has not been found
     * @param loadedMissingTicks how long its last-known chunk has had its entities loaded while it
     *                           was missing, or a negative number when they aren't loaded now
     */
    public static Verdict decide(long missingTicks, long loadedMissingTicks) {
        if (missingTicks >= HARD_CAP_TICKS) {
            return Verdict.LOSE;
        }
        if (loadedMissingTicks >= LOADED_GRACE_TICKS) {
            return Verdict.LOSE;
        }
        return Verdict.WAIT;
    }
}
