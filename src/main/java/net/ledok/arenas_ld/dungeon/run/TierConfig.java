package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-tier dungeon configuration. Owned by DungeonController, snapshotted onto a DungeonRun
 * at run start (so admin edits during a run don't affect that run).
 *
 * @param healthMultiplier scales max_health of every spawned mob (including the boss). Should be > 0.
 * @param damageMultiplier scales incoming damage to players in this run, applied via LivingEntityMixin. Should be > 0.
 * @param perPlayerLootTable loot table ID rolled per player on win. Empty string = no loot.
 * @param dungeonTimeSeconds total time for the run at this tier. Should be > 0.
 * @param enabled whether this tier appears in the lobby tier picker and can be selected.
 * @param rewardCurrency Economy_LD currency delivered to each loot-eligible player's inbox on win. 0 = none.
 */
public record TierConfig(
    double healthMultiplier,
    double damageMultiplier,
    String perPlayerLootTable,
    int dungeonTimeSeconds,
    boolean enabled,
    long rewardCurrency
) {
    public static final TierConfig EASY_DEFAULT =
        new TierConfig(0.75, 0.75, "", 600, true, 0L);
    public static final TierConfig NORMAL_DEFAULT =
        new TierConfig(1.0, 1.0, "", 600, true, 0L);
    public static final TierConfig HARD_DEFAULT =
        new TierConfig(1.5, 1.5, "", 600, true, 0L);
    public static final TierConfig NIGHTMARE_DEFAULT =
        new TierConfig(2.5, 2.5, "", 600, true, 0L);

    public static final Codec<TierConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.DOUBLE.fieldOf("healthMultiplier").forGetter(TierConfig::healthMultiplier),
            Codec.DOUBLE.fieldOf("damageMultiplier").forGetter(TierConfig::damageMultiplier),
            Codec.STRING.fieldOf("perPlayerLootTable").forGetter(TierConfig::perPlayerLootTable),
            Codec.INT.fieldOf("dungeonTimeSeconds").forGetter(TierConfig::dungeonTimeSeconds),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(TierConfig::enabled),
            Codec.LONG.optionalFieldOf("rewardCurrency", 0L).forGetter(TierConfig::rewardCurrency)
        ).apply(instance, TierConfig::new)
    );

    /**
     * Returns the built-in default configuration for the given tier.
     * Used by controllers on first placement to populate sane initial values.
     */
    public static TierConfig defaultFor(DifficultyTier tier) {
        return switch (tier) {
            case EASY -> EASY_DEFAULT;
            case NORMAL -> NORMAL_DEFAULT;
            case HARD -> HARD_DEFAULT;
            case NIGHTMARE -> NIGHTMARE_DEFAULT;
        };
    }
}
