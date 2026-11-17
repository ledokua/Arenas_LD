package net.ledok.arenas_ld.raid.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import java.util.List;

/**
 * Per-tier raid configuration. Owned by {@link net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity}.
 *
 * <p>All raid-time mechanics that used to live on RaidBossSpawnerBlockEntity are configured
 * here: HP/damage scaling, time limit, per-player loot table, skill XP reward, boss regeneration (HP per second for
 * each player in the run, 4.37.0; the old flat {@code regeneration} is no longer read),
 * and per-player HP scale. The spawner is a passive arena. {@code rewardCommands} run as the
 * server on a win (see {@link net.ledok.arenas_ld.util.RewardCommands}).
 */
public record RaidTierConfig(
    double healthMultiplier,
    double damageMultiplier,
    String perPlayerLootTable,
    int raidTimeSeconds,
    boolean enabled,
    long rewardCurrency,
    int skillExperiencePerWin,
    double regenerationPerPlayer,
    double hpScalePerPlayer,
    List<String> rewardCommands,
    boolean allowBlockBreaking
) {
    public RaidTierConfig {
        rewardCommands = rewardCommands == null ? List.of() : List.copyOf(rewardCommands);
    }

    /** A tier without block breaking (the shape before 4.23.0). */
    public RaidTierConfig(double healthMultiplier, double damageMultiplier, String perPlayerLootTable,
                          int raidTimeSeconds, boolean enabled, long rewardCurrency, int skillExperiencePerWin,
                          double regenerationPerPlayer, double hpScalePerPlayer, List<String> rewardCommands) {
        this(healthMultiplier, damageMultiplier, perPlayerLootTable, raidTimeSeconds, enabled, rewardCurrency,
            skillExperiencePerWin, regenerationPerPlayer, hpScalePerPlayer, rewardCommands, false);
    }

    /** A tier with no reward commands (the shape before 4.11.0). */
    public RaidTierConfig(double healthMultiplier, double damageMultiplier, String perPlayerLootTable,
                          int raidTimeSeconds, boolean enabled, long rewardCurrency, int skillExperiencePerWin,
                          double regenerationPerPlayer, double hpScalePerPlayer) {
        this(healthMultiplier, damageMultiplier, perPlayerLootTable, raidTimeSeconds, enabled, rewardCurrency,
            skillExperiencePerWin, regenerationPerPlayer, hpScalePerPlayer, List.of());
    }

    public static final RaidTierConfig EASY_DEFAULT =
        new RaidTierConfig(0.75, 0.75, "", 600, true, 0L, 100, 0, 0.10);
    public static final RaidTierConfig NORMAL_DEFAULT =
        new RaidTierConfig(1.0, 1.0, "", 600, true, 0L, 100, 0, 0.10);
    public static final RaidTierConfig HARD_DEFAULT =
        new RaidTierConfig(1.5, 1.5, "", 600, true, 0L, 100, 0, 0.10);
    public static final RaidTierConfig NIGHTMARE_DEFAULT =
        new RaidTierConfig(2.5, 2.5, "", 600, true, 0L, 100, 0, 0.10);

    public static final Codec<RaidTierConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.DOUBLE.fieldOf("healthMultiplier").forGetter(RaidTierConfig::healthMultiplier),
            Codec.DOUBLE.fieldOf("damageMultiplier").forGetter(RaidTierConfig::damageMultiplier),
            Codec.STRING.fieldOf("perPlayerLootTable").forGetter(RaidTierConfig::perPlayerLootTable),
            Codec.INT.fieldOf("raidTimeSeconds").forGetter(RaidTierConfig::raidTimeSeconds),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(RaidTierConfig::enabled),
            Codec.LONG.optionalFieldOf("rewardCurrency", 0L).forGetter(RaidTierConfig::rewardCurrency),
            Codec.INT.optionalFieldOf("skillExperiencePerWin", 100).forGetter(RaidTierConfig::skillExperiencePerWin),
            // 4.37.0: per player (HP/s × the run's players). The old flat "regeneration" (HP per 5 s) is dropped, not
            // converted: a controller that had it heals nothing until this is set (LeDok: regen per player instead).
            Codec.DOUBLE.optionalFieldOf("regenerationPerPlayer", 0.0).forGetter(RaidTierConfig::regenerationPerPlayer),
            Codec.DOUBLE.optionalFieldOf("hpScalePerPlayer", 0.10).forGetter(RaidTierConfig::hpScalePerPlayer),
            Codec.STRING.listOf().optionalFieldOf("rewardCommands", List.of()).forGetter(RaidTierConfig::rewardCommands),
            Codec.BOOL.optionalFieldOf("allowBlockBreaking", false).forGetter(RaidTierConfig::allowBlockBreaking)
        ).apply(instance, RaidTierConfig::new)
    );

    /** This tier with other reward commands. */
    public RaidTierConfig withRewardCommands(List<String> commands) {
        return new RaidTierConfig(healthMultiplier, damageMultiplier, perPlayerLootTable, raidTimeSeconds, enabled,
            rewardCurrency, skillExperiencePerWin, regenerationPerPlayer, hpScalePerPlayer, commands, allowBlockBreaking);
    }

    /** This tier with block breaking on or off. */
    public RaidTierConfig withAllowBlockBreaking(boolean allow) {
        return new RaidTierConfig(healthMultiplier, damageMultiplier, perPlayerLootTable, raidTimeSeconds, enabled,
            rewardCurrency, skillExperiencePerWin, regenerationPerPlayer, hpScalePerPlayer, rewardCommands, allow);
    }

    public static RaidTierConfig defaultFor(RaidDifficulty tier) {
        return switch (tier) {
            case EASY -> EASY_DEFAULT;
            case NORMAL -> NORMAL_DEFAULT;
            case HARD -> HARD_DEFAULT;
            case LEGENDARY -> NIGHTMARE_DEFAULT;
        };
    }

    public static RaidTierConfig defaultFor(net.ledok.arenas_ld.dungeon.run.DifficultyTier tier) {
        return switch (tier) {
            case EASY -> EASY_DEFAULT;
            case NORMAL -> NORMAL_DEFAULT;
            case HARD -> HARD_DEFAULT;
            case NIGHTMARE -> NIGHTMARE_DEFAULT;
        };
    }

    public CompoundTag toNbt() {
        return (CompoundTag) CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
    }

    public static RaidTierConfig fromNbt(CompoundTag tag) {
        return CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(NORMAL_DEFAULT);
    }
}
