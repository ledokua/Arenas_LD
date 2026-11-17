package net.ledok.arenas_ld.rewards;

import net.ledok.arenas_ld.rewards.RewardModifier.Stat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RewardMathTest {
    private static RewardModifier inc(Stat stat, double value) {
        return RewardModifier.increased(stat, value, "t", "t");
    }

    private static RewardModifier more(Stat stat, double value) {
        return RewardModifier.more(stat, value, "t", "t");
    }

    @Test
    void increasedAddsMoreMultiplies() {
        List<RewardModifier> mods = List.of(inc(Stat.LOOT, 0.5), inc(Stat.LOOT, 0.15), more(Stat.LOOT, 2), inc(Stat.XP, 9));
        assertEquals((1 + 0.5 + 0.15) * 2, RewardMath.multiplier(mods, Stat.LOOT), 1e-9);
        assertEquals(1.0, RewardMath.multiplier(mods, Stat.CURRENCY), 1e-9, "other stats untouched");
        assertEquals(4.0, RewardMath.multiplier(List.of(more(Stat.XP, 2), more(Stat.XP, 2)), Stat.XP), 1e-9);
        assertEquals(0.0, RewardMath.multiplier(List.of(inc(Stat.XP, -3)), Stat.XP), 1e-9, "never below 0");
    }

    @Test
    void primeAndHardcoreExamples() {
        // base 3, Prime +50% → 4.5; hardcore Prime → 9; + Treasure Hunter 15% → 9.9
        assertEquals(4.5, 3 * RewardMath.multiplier(List.of(inc(Stat.LOOT, 0.5)), Stat.LOOT), 1e-9);
        assertEquals(9.0, 3 * RewardMath.multiplier(List.of(inc(Stat.LOOT, 0.5), more(Stat.LOOT, 2)), Stat.LOOT), 1e-9);
        assertEquals(9.9, 3 * RewardMath.multiplier(List.of(inc(Stat.LOOT, 0.5), inc(Stat.LOOT, 0.15), more(Stat.LOOT, 2)), Stat.LOOT), 1e-9);
    }

    @Test
    void currencyAndXpRoundHalfUp() {
        assertEquals(150, RewardMath.scale(100L, 1.5));
        assertEquals(3, RewardMath.scale(2, 1.25), "2.5 rounds up");
        assertEquals(2, RewardMath.scale(2, 1.2));
        assertEquals(0, RewardMath.scale(0L, 5));
    }

    @Test
    void stochasticRollsKeepTheMean() {
        assertEquals(4, RewardMath.rolls(4.5, 0.9));
        assertEquals(5, RewardMath.rolls(4.5, 0.1));
        assertEquals(9, RewardMath.rolls(9.0, 0.0), "whole counts are exact");
        assertEquals(0, RewardMath.rolls(-1, 0.0));
        assertEquals(RewardMath.MAX_ROLLS, RewardMath.rolls(1e9, 0.0));
        Random random = new Random(42);
        long total = 0;
        int n = 200_000;
        for (int i = 0; i < n; i++) {
            total += RewardMath.rolls(4.95, random.nextDouble());
        }
        assertEquals(4.95, total / (double) n, 0.01);
    }

    @Test
    void amountsRead() {
        assertEquals("+50%", RewardMath.amount(inc(Stat.LOOT, 0.5)));
        assertEquals("-10%", RewardMath.amount(inc(Stat.LOOT, -0.1)));
        assertEquals("×2", RewardMath.amount(more(Stat.LOOT, 2)));
        assertEquals("+66.67%", RewardMath.amount(inc(Stat.LOOT, 2.0 / 3)));
    }
}
