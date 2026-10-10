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

    default BlockEntity asBlockEntity() {
        return (BlockEntity) this;
    }

    /** The difficulty id the API uses for a tier ({@code "easy"} … {@code "nightmare"}). */
    static String difficultyId(DifficultyTier tier) {
        return tier.name().toLowerCase(java.util.Locale.ROOT);
    }
}
