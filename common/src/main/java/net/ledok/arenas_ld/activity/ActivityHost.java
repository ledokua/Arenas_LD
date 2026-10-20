package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A controller block entity that is one activity of the public API: the dungeon, raid and arena
 * controllers. Holds the stable id and reports what the API lists.
 */
public interface ActivityHost {
    ArenasApi.ActivityType activityType();

    /** Null only until the controller's first server tick assigns one. */
    @Nullable ResourceLocation activityId();

    /** True when the controller was loaded from a save that predates activity ids (it gets one, with a warning). */
    boolean loadedWithoutId();

    /** Stores the id without validation; {@link ActivityRegistry} owns uniqueness. */
    void storeActivityId(ResourceLocation id);

    /** The admin-entered name; may be blank. */
    String activityName();

    void setActivityName(String name);

    /**
     * This controller is a copy of another (a copied block brings the original's saved data): keep
     * the settings, drop everything that belongs to the original — instances, lobbies, queue,
     * invites, runs, leaderboards. Called before the copy's first tick, so copied runs never tick.
     *
     * @param original the original's controller when it is loaded, else null
     */
    void clearCopiedState(@Nullable ActivityHost original);

    /** The enabled difficulties, lowest first; empty for arenas. */
    List<DifficultyTier> enabledTiers();

    /**
     * What the controller is doing now, for {@code ArenasApi.status}. Polled often: read only what
     * the controller holds in memory, never a block entity or entity whose chunk may not be loaded
     * (guard with {@code level.isLoaded}), and never load a chunk.
     */
    LiveStatus liveStatus();

    /**
     * Staff ends runs ({@code ArenasApi.forceEnd}): a run still going ends as a forced loss
     * ({@code Outcome.FORCED}, players returned after the exit timer); a run already on its exit
     * timer is closed now, keeping its result. Safe to call from outside the controller's tick.
     *
     * @param instance the instance whose run to end, or null for every run
     * @return how many runs were ended or closed
     */
    int forceEnd(@Nullable ArenasApi.InstanceRef instance);

    /**
     * Sends fresh data to everyone looking at this controller after a change made from outside its
     * screens (a rename through the API): a block update, the lobby screens' snapshot and the admin
     * screens' snapshot ({@link AdminScreens#viewers}).
     */
    void syncViewers();

    /**
     * A controller's in-memory state for {@code ArenasApi.status}; {@link ActivityAdmin} adds the
     * id-level warnings.
     *
     * @param openLobbies    lobbies forming or ready (queued included), not those whose run started
     * @param queuedLobbies  lobbies waiting for a free instance (stale queue ids not counted)
     * @param waitingPlayers members of the open lobbies
     */
    record LiveStatus(List<ArenasApi.InstanceStatus> instances, List<ArenasApi.RunStatus> runs,
                      int openLobbies, int queuedLobbies, int waitingPlayers) {
        public LiveStatus {
            instances = List.copyOf(instances);
            runs = List.copyOf(runs);
        }
    }

    default BlockEntity asBlockEntity() {
        return (BlockEntity) this;
    }

    /** The difficulty id the API uses for a tier ({@code "easy"} … {@code "nightmare"}). */
    static String difficultyId(DifficultyTier tier) {
        return tier.name().toLowerCase(java.util.Locale.ROOT);
    }
}
