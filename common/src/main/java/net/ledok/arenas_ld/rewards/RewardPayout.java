package net.ledok.arenas_ld.rewards;

import net.ledok.arenas_ld.rewards.RewardModifier.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * One player's reward from one payout, after every modifier: how often the loot table rolls, and the
 * currency and skill XP. {@link #breakdown} is what the player is told.
 *
 * @param baseRolls the rolls before modifiers (fractional allowed); {@code expectedRolls} after them
 */
public record RewardPayout(double baseRolls, double expectedRolls, int rolls, long baseCurrency, long currency,
                           int baseXp, int xp, List<RewardModifier> modifiers, boolean lootConverted,
                           boolean currencyConverted) {

    /**
     * @param completion a run's end payout (dungeon or raid completion, arena summary): only these take
     *                   flat bonuses ("+2 loot rolls"); rooms and arena wave rows don't
     */
    public static RewardPayout compute(double baseRolls, long baseCurrency, int baseXp, List<RewardModifier> modifiers,
                                       boolean completion, RandomSource random) {
        List<RewardModifier> effective = new ArrayList<>(modifiers);
        if (!completion) {
            effective.removeIf(m -> m.kind() == RewardModifier.Kind.FLAT);
        }
        double rollsBase = baseRolls > 0 ? Math.max(0, baseRolls + RewardMath.flat(effective, Stat.LOOT)) : 0;
        long currencyBase = Math.max(0, baseCurrency + Math.round(RewardMath.flat(effective, Stat.CURRENCY)));
        int xpBase = (int) Math.max(0, baseXp + Math.round(RewardMath.flat(effective, Stat.XP)));
        double expected = rollsBase * RewardMath.multiplier(effective, Stat.LOOT);

        // Conversions last: each final loot roll, and the currency, become increased XP.
        boolean lootConverted = false;
        double lootRate = RewardMath.conversion(effective, Stat.LOOT);
        if (lootRate > 0 && expected > 0) {
            effective.add(RewardModifier.increased(Stat.XP, lootRate * expected, "convert:loot", convertName(modifiers, Stat.LOOT)));
            expected = 0;
            lootConverted = true;
        }
        boolean currencyConverted = false;
        double currencyRate = RewardMath.conversion(effective, Stat.CURRENCY);
        if (currencyRate > 0) {
            effective.add(RewardModifier.increased(Stat.XP, currencyRate, "convert:currency", convertName(modifiers, Stat.CURRENCY)));
            currencyConverted = true;
        }
        long currency = currencyConverted ? 0 : RewardMath.scale(currencyBase, RewardMath.multiplier(effective, Stat.CURRENCY));
        int xp = RewardMath.scale(xpBase, RewardMath.multiplier(effective, Stat.XP));
        return new RewardPayout(rollsBase == 0 ? 0 : baseRolls, expected, lootConverted ? 0 : RewardMath.rolls(expected, random.nextDouble()),
            baseCurrency, currency, baseXp, xp, List.copyOf(effective), lootConverted, currencyConverted);
    }

    private static String convertName(List<RewardModifier> modifiers, Stat stat) {
        for (RewardModifier modifier : modifiers) {
            if (modifier.stat() == stat && modifier.kind() == RewardModifier.Kind.CONVERT) {
                return modifier.name();
            }
        }
        return "";
    }

    /** Both lists together: the player's own (LuckPerms) and the run's. */
    public static List<RewardModifier> join(List<RewardModifier> player, List<RewardModifier> run) {
        List<RewardModifier> all = new ArrayList<>(player.size() + run.size());
        all.addAll(player);
        all.addAll(run);
        return all;
    }

    /**
     * The breakdown lines of the stats this payout has, e.g. "Loot: base 3 · Prime +50% · Hardcore ×2 → 9
     * (rolled 9)". Nothing when no modifier changes anything.
     */
    public List<Component> breakdown(boolean withLoot) {
        if (modifiers.stream().allMatch(RewardModifier::isNeutral)) {
            return new ArrayList<>();
        }
        return lines(withLoot);
    }

    /** {@link #breakdown}, also when no modifier changes anything ("Loot: base 3 → 3"): the Rewards tab always shows it. */
    public List<Component> lines(boolean withLoot) {
        List<Component> lines = new ArrayList<>();
        if (withLoot && baseRolls > 0) {
            lines.add(RewardMath.breakdown(Stat.LOOT, RewardMath.number(baseRolls), modifiers, lootConverted
                ? Component.translatable("message.arenas_ld.reward.converted")
                : Component.translatable("message.arenas_ld.reward.rolled", RewardMath.number(expectedRolls), rolls)));
        }
        if (baseCurrency > 0) {
            lines.add(RewardMath.breakdown(Stat.CURRENCY, Long.toString(baseCurrency), modifiers, currencyConverted
                ? Component.translatable("message.arenas_ld.reward.converted")
                : Component.literal(Long.toString(currency))));
        }
        if (baseXp > 0) {
            lines.add(RewardMath.breakdown(Stat.XP, Integer.toString(baseXp), modifiers, Component.literal(Integer.toString(xp))));
        }
        return lines;
    }

    /**
     * What the player got, for the run details' Rewards tab: "6 loot rolls · 840 currency · 1,200 XP" (only the
     * stats this payout has; a converted one is left out, its XP is in the XP).
     */
    public Component summary(boolean withLoot) {
        List<Component> parts = new ArrayList<>();
        if (withLoot && baseRolls > 0 && !lootConverted) {
            parts.add(Component.translatable("gui.arenas_ld.run_details.got.loot", rolls));
        }
        if (baseCurrency > 0 && !currencyConverted) {
            parts.add(Component.translatable("gui.arenas_ld.run_details.got.currency", String.format("%,d", currency)));
        }
        if (xp > 0) {
            parts.add(Component.translatable("gui.arenas_ld.run_details.got.xp", String.format("%,d", xp)));
        }
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            out.append(i == 0 ? Component.empty() : Component.literal(" · ")).append(parts.get(i));
        }
        return out;
    }

    /** Sends {@link #breakdown} in chat, gray. */
    public void tell(ServerPlayer player, boolean withLoot) {
        for (Component line : breakdown(withLoot)) {
            player.sendSystemMessage(line.copy().withStyle(ChatFormatting.GRAY));
        }
    }
}
