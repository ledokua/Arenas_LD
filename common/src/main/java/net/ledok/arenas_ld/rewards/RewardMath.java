package net.ledok.arenas_ld.rewards;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Collection;
import java.util.Locale;

/**
 * The one reward formula: {@code final = base × (1 + Σ increased) × Π more}, per stat. Currency and XP
 * round half up after every modifier; loot rolls are fractional, the fraction being the chance of one
 * more roll (4.5 = 4 rolls and a 50% chance of a fifth), so the average is exact.
 */
public final class RewardMath {
    /** No payout rolls a table more often than this, whatever stacks up. */
    public static final int MAX_ROLLS = 100;

    private RewardMath() {
    }

    /** The factor {@code stat} is scaled by: (1 + the increased bonuses) × the more factors, never below 0. */
    public static double multiplier(Collection<RewardModifier> modifiers, RewardModifier.Stat stat) {
        double increased = 0;
        double more = 1;
        for (RewardModifier modifier : modifiers) {
            if (modifier.stat() != stat) {
                continue;
            }
            switch (modifier.kind()) {
                case INCREASED -> increased += modifier.value();
                case MORE -> more *= Math.max(0, modifier.value());
                default -> {
                }
            }
        }
        return Math.max(0, 1 + increased) * more;
    }

    /** The flat amounts of {@code stat} added to the base (completion payouts). */
    public static double flat(Collection<RewardModifier> modifiers, RewardModifier.Stat stat) {
        double sum = 0;
        for (RewardModifier modifier : modifiers) {
            if (modifier.stat() == stat && modifier.kind() == RewardModifier.Kind.FLAT) {
                sum += modifier.value();
            }
        }
        return sum;
    }

    /** The conversion rate of {@code stat} into increased XP (0 = not converted). */
    public static double conversion(Collection<RewardModifier> modifiers, RewardModifier.Stat stat) {
        double sum = 0;
        for (RewardModifier modifier : modifiers) {
            if (modifier.stat() == stat && modifier.kind() == RewardModifier.Kind.CONVERT) {
                sum += Math.max(0, modifier.value());
            }
        }
        return sum;
    }

    /** {@code base × multiplier}, rounded half up (currency). */
    public static long scale(long base, double multiplier) {
        return (long) Math.floor(Math.max(0, base) * multiplier + 0.5);
    }

    /** {@code base × multiplier}, rounded half up (skill XP). */
    public static int scale(int base, double multiplier) {
        return (int) Math.min(Integer.MAX_VALUE, scale((long) base, multiplier));
    }

    /**
     * The rolls to make for an expected (fractional) count: the whole part, plus one more when
     * {@code random01} (uniform in [0, 1)) falls below the fraction. Capped at {@link #MAX_ROLLS}.
     */
    public static int rolls(double expected, double random01) {
        double clamped = Math.min(MAX_ROLLS, Math.max(0, expected));
        int whole = (int) Math.floor(clamped);
        return Math.min(MAX_ROLLS, whole + (random01 < clamped - whole ? 1 : 0));
    }

    /**
     * One stat's breakdown for players, e.g. "Loot: base 3 · Prime +50% · Hardcore ×2 → 9":
     * only modifiers of that stat that change something.
     */
    public static Component breakdown(RewardModifier.Stat stat, String base, Collection<RewardModifier> modifiers,
                                      Component result) {
        MutableComponent line = Component.translatable("message.arenas_ld.reward.stat." + stat.name().toLowerCase(Locale.ROOT))
            .append(": ")
            .append(Component.translatable("message.arenas_ld.reward.base", base));
        for (RewardModifier modifier : modifiers) {
            if (modifier.stat() != stat || modifier.isNeutral()) {
                continue;
            }
            line.append(" · ").append(sourceName(modifier.name())).append(" ").append(amount(modifier));
        }
        return line.append(" → ").append(result);
    }

    /** "+2", "+50%", "-10%", "×2" or "→ XP". */
    public static String amount(RewardModifier modifier) {
        if (modifier.kind() == RewardModifier.Kind.MORE) {
            return "×" + number(modifier.value());
        }
        if (modifier.kind() == RewardModifier.Kind.FLAT) {
            return (modifier.value() >= 0 ? "+" : "") + number(modifier.value());
        }
        if (modifier.kind() == RewardModifier.Kind.CONVERT) {
            return "→ XP";
        }
        double percent = modifier.value() * 100;
        return (percent >= 0 ? "+" : "") + number(percent) + "%";
    }

    /** A source name: a translation key (no spaces, has a dot) or literal text. */
    public static Component sourceName(String name) {
        if (name == null || name.isEmpty()) {
            return Component.translatable("message.arenas_ld.reward.source.unnamed");
        }
        return name.indexOf(' ') < 0 && name.indexOf('.') > 0 ? Component.translatable(name) : Component.literal(name);
    }

    /** Up to two decimals, no trailing zeros: 3, 4.5, 9.9, 66.67. */
    public static String number(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        text = text.replaceAll("0+$", "");
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
