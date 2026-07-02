package net.ledok.arenas_ld.raid.run;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;

/**
 * Difficulty tiers for a raid. Each tier's effects (HP/damage multipliers, loot, time, etc.)
 * are defined in {@link RaidTierConfig} on the controller, not on the enum itself.
 *
 * <p>Both {@link #CODEC} and {@link #STREAM_CODEC} deserialize unknown strings to {@link #NORMAL}
 * rather than throwing — this protects against renaming/removing tiers in the future. Mirrors
 * {@link DifficultyTier}.
 */
public enum RaidDifficulty {
    EASY,
    NORMAL,
    HARD,
    LEGENDARY;

    public static final Codec<RaidDifficulty> CODEC = Codec.STRING.xmap(
        RaidDifficulty::fromStringOrDefault,
        Enum::name
    );

    public static final StreamCodec<ByteBuf, RaidDifficulty> STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.map(
            RaidDifficulty::fromStringOrDefault,
            Enum::name
        );

    private static RaidDifficulty fromStringOrDefault(String name) {
        try {
            return RaidDifficulty.valueOf(name);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }

    public String translationKey() {
        return "raid_difficulty.arenas_ld." + name().toLowerCase();
    }

    public static RaidDifficulty fromNameOrDefault(String name, RaidDifficulty def) {
        try {
            return valueOf(name.toUpperCase());
        } catch (Exception e) {
            return def;
        }
    }

    public DifficultyTier toDifficultyTier() {
        return switch (this) {
            case EASY -> DifficultyTier.EASY;
            case NORMAL -> DifficultyTier.NORMAL;
            case HARD -> DifficultyTier.HARD;
            case LEGENDARY -> DifficultyTier.NIGHTMARE;
        };
    }

    public static RaidDifficulty from(DifficultyTier tier) {
        return switch (tier) {
            case EASY -> EASY;
            case NORMAL -> NORMAL;
            case HARD -> HARD;
            case NIGHTMARE -> LEGENDARY;
        };
    }
}
