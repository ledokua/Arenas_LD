package net.ledok.arenas_ld.raid.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

/**
 * Per-tier raid configuration. Mirrors {@link net.ledok.arenas_ld.dungeon.run.TierConfig}.
 * Owned by {@link net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity}.
 */
public record RaidTierConfig(
    double healthMultiplier,
    double damageMultiplier,
    String perPlayerLootTable,
    int raidTimeSeconds,
    boolean enabled,
    long rewardCurrency
) {
    public static final RaidTierConfig EASY_DEFAULT =
        new RaidTierConfig(0.75, 0.75, "", 600, true, 0L);
    public static final RaidTierConfig NORMAL_DEFAULT =
        new RaidTierConfig(1.0, 1.0, "", 600, true, 0L);
    public static final RaidTierConfig HARD_DEFAULT =
        new RaidTierConfig(1.5, 1.5, "", 600, true, 0L);
    public static final RaidTierConfig NIGHTMARE_DEFAULT =
        new RaidTierConfig(2.5, 2.5, "", 600, true, 0L);

    public static final Codec<RaidTierConfig> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.DOUBLE.fieldOf("healthMultiplier").forGetter(RaidTierConfig::healthMultiplier),
            Codec.DOUBLE.fieldOf("damageMultiplier").forGetter(RaidTierConfig::damageMultiplier),
            Codec.STRING.fieldOf("perPlayerLootTable").forGetter(RaidTierConfig::perPlayerLootTable),
            Codec.INT.fieldOf("raidTimeSeconds").forGetter(RaidTierConfig::raidTimeSeconds),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(RaidTierConfig::enabled),
            Codec.LONG.optionalFieldOf("rewardCurrency", 0L).forGetter(RaidTierConfig::rewardCurrency)
        ).apply(instance, RaidTierConfig::new)
    );

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
