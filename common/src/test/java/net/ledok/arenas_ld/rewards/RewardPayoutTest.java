package net.ledok.arenas_ld.rewards;

import net.ledok.arenas_ld.rewards.RewardModifier.Kind;
import net.ledok.arenas_ld.rewards.RewardModifier.Stat;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardPayoutTest {
    private static RewardModifier mod(Stat stat, Kind kind, double value) {
        return new RewardModifier(stat, kind, value, "t", "t");
    }

    private static final RewardModifier HARDCORE_LOOT = mod(Stat.LOOT, Kind.MORE, 2);
    private static final RewardModifier HARDCORE_XP = mod(Stat.XP, Kind.MORE, 2);

    @Test
    void flatAddsToTheBaseOnCompletionsOnly() {
        List<RewardModifier> mods = List.of(mod(Stat.LOOT, Kind.FLAT, 2), HARDCORE_LOOT);
        assertEquals(10.0, RewardPayout.compute(3, 0, 0, mods, true, RandomSource.create(1)).expectedRolls(), 1e-9,
            "glass cannon + hardcore: (3 + 2) × 2");
        assertEquals(6.0, RewardPayout.compute(3, 0, 0, mods, false, RandomSource.create(1)).expectedRolls(), 1e-9,
            "rooms and wave rows take no flat bonus");
        assertEquals(0.0, RewardPayout.compute(0, 0, 0, mods, true, RandomSource.create(1)).expectedRolls(), 1e-9,
            "no loot table, no rolls");
    }

    @Test
    void itemsToXpConvertsEveryFinalRollBeforeXpMoreFactors() {
        // Prime +50% loot, +25% XP, hardcore ×2 on both: 9 rolls → +90% XP, then ×2.
        List<RewardModifier> mods = List.of(mod(Stat.LOOT, Kind.INCREASED, 0.5), HARDCORE_LOOT,
            mod(Stat.XP, Kind.INCREASED, 0.25), HARDCORE_XP, mod(Stat.LOOT, Kind.CONVERT, 0.10));
        RewardPayout payout = RewardPayout.compute(3, 100, 100, mods, true, RandomSource.create(1));
        assertEquals(0, payout.rolls());
        assertTrue(payout.lootConverted());
        assertEquals(Math.round(100 * (1 + 0.25 + 0.9) * 2), payout.xp());
        assertEquals(100, payout.currency(), "currency untouched");
    }

    @Test
    void currencyToXpDropsTheCurrency() {
        List<RewardModifier> mods = List.of(mod(Stat.CURRENCY, Kind.CONVERT, 0.25), mod(Stat.XP, Kind.INCREASED, 0.10));
        RewardPayout payout = RewardPayout.compute(3, 500, 100, mods, true, RandomSource.create(1));
        assertEquals(0, payout.currency());
        assertTrue(payout.currencyConverted());
        assertEquals(135, payout.xp(), "100 × (1 + 0.10 + 0.25)");
        assertEquals(3, payout.rolls(), "loot untouched");
    }

    @Test
    void totalsSumPerStat() {
        List<RewardModifier> total = RewardModifiers.total(List.of(mod(Stat.LOOT, Kind.FLAT, 2), mod(Stat.LOOT, Kind.FLAT, 1),
            mod(Stat.XP, Kind.INCREASED, 0.25), mod(Stat.XP, Kind.INCREASED, 0.10)));
        assertEquals(List.of(mod(Stat.LOOT, Kind.FLAT, 3).withSource(), mod(Stat.XP, Kind.INCREASED, 0.35).withSource()),
            total.stream().map(RewardModifier::withSource).toList());
    }
}
