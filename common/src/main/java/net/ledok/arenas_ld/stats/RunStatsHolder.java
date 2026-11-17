package net.ledok.arenas_ld.stats;

import net.minecraft.world.damagesource.DamageSource;
import org.jetbrains.annotations.Nullable;

/**
 * The run-stats field every living entity carries (a mixin on {@code LivingEntity}): null outside runs, which is
 * the one check a hit outside a run costs. A run mob holds its {@link RunStats}; a run player, and a summon or pet
 * a run player owns, holds that player's {@link PlayerCounters}.
 */
public interface RunStatsHolder {
    @Nullable Object arenasLd$runStats();

    void arenasLd$setRunStats(@Nullable Object stats);

    /** On a run mob: the player who last put a harmful effect on it (credit for its damage over time). */
    @Nullable PlayerCounters arenasLd$applier();

    void arenasLd$setApplier(@Nullable PlayerCounters applier);

    /** On a run mob: one of the run's bosses (boss damage, boss kills, phase marks). */
    boolean arenasLd$boss();

    void arenasLd$setBoss(boolean boss);

    /** On a run mob: who has hit it how much (assists), made on its first credited hit. */
    @Nullable RunStats.Hits arenasLd$hits();

    void arenasLd$setHits(@Nullable RunStats.Hits hits);

    /** Start of {@code actuallyHurt} (LivingEntity's, or Player's override): remembers health and absorption. */
    void arenasLd$hurtStart(float amount);

    /** End of {@code actuallyHurt}: counts what the hit did since {@link #arenasLd$hurtStart}. */
    void arenasLd$hurtEnd(DamageSource source);
}
