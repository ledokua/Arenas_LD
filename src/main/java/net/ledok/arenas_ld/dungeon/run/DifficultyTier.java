package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Difficulty tiers for a dungeon run. Each tier's effects (HP/damage multipliers, loot, time)
 * are defined in {@link TierConfig} on the controller, not on the enum itself.
 *
 * <p>Both {@link #CODEC} and {@link #STREAM_CODEC} deserialize unknown strings to {@link #NORMAL}
 * rather than throwing — this protects against renaming/removing tiers in the future.
 */
public enum DifficultyTier {
    NORMAL,
    HARD,
    HELL;

    public static final Codec<DifficultyTier> CODEC = Codec.STRING.xmap(
        DifficultyTier::fromStringOrDefault,
        Enum::name
    );

    public static final StreamCodec<ByteBuf, DifficultyTier> STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.map(
            DifficultyTier::fromStringOrDefault,
            Enum::name
        );

    private static DifficultyTier fromStringOrDefault(String name) {
        try {
            return DifficultyTier.valueOf(name);
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }
}
