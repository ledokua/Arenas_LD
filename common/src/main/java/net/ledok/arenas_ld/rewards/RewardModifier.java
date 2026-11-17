package net.ledok.arenas_ld.rewards;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Locale;

/**
 * One reward modifier: "+50% loot" ({@link Kind#INCREASED} 0.5) or "×2 XP" ({@link Kind#MORE} 2).
 * Increased modifiers of a stat add up; more factors multiply on top (see {@link RewardMath}).
 *
 * @param source the id it comes from ({@code luckperms}, {@code hardcore}, a run modifier or event id)
 * @param name   what players see: a translation key or literal text ({@link RewardMath#sourceName})
 */
public record RewardModifier(Stat stat, Kind kind, double value, String source, String name) {

    /** What a modifier scales. */
    public enum Stat {
        LOOT, XP, CURRENCY;

        public static final Codec<Stat> CODEC = Codec.STRING.xmap(
            s -> Stat.valueOf(s.toUpperCase(Locale.ROOT)), s -> s.name().toLowerCase(Locale.ROOT));
    }

    /** How it stacks: {@code (base + Σ flat) × (1 + Σ increased) × Π more}, then conversions. */
    public enum Kind {
        /** Adds to the base before any percentage (completion payouts only): +2 loot rolls. */
        FLAT,
        /** Adds to the other increased bonuses of its stat: +0.5 is +50%. */
        INCREASED,
        /** Multiplies the result: 2 is ×2. */
        MORE,
        /**
         * Converts the stat into increased XP, last: loot = every final roll is worth {@code value} XP
         * (0.1 = +10% each) and no items drop; currency = no currency, +{@code value} XP.
         */
        CONVERT;

        public static final Codec<Kind> CODEC = Codec.STRING.xmap(
            s -> Kind.valueOf(s.toUpperCase(Locale.ROOT)), k -> k.name().toLowerCase(Locale.ROOT));
    }

    public static final Codec<RewardModifier> CODEC = RecordCodecBuilder.create(i -> i.group(
        Stat.CODEC.fieldOf("stat").forGetter(RewardModifier::stat),
        Kind.CODEC.fieldOf("kind").forGetter(RewardModifier::kind),
        Codec.DOUBLE.fieldOf("value").forGetter(RewardModifier::value),
        Codec.STRING.optionalFieldOf("source", "").forGetter(RewardModifier::source),
        Codec.STRING.optionalFieldOf("name", "").forGetter(RewardModifier::name)
    ).apply(i, RewardModifier::new));

    public static RewardModifier increased(Stat stat, double value, String source, String name) {
        return new RewardModifier(stat, Kind.INCREASED, value, source, name);
    }

    public static RewardModifier more(Stat stat, double value, String source, String name) {
        return new RewardModifier(stat, Kind.MORE, value, source, name);
    }

    /** The same change with a neutral source and name (to compare what modifiers do). */
    public RewardModifier withSource() {
        return new RewardModifier(stat, kind, Math.round(value * 1e9) / 1e9, "", "");
    }

    /** A modifier that changes nothing (+0, +0%, ×1): left out of lists and breakdowns. */
    public boolean isNeutral() {
        return switch (kind) {
            case FLAT, INCREASED -> value == 0;
            case MORE -> value == 1;
            case CONVERT -> false;
        };
    }
}
