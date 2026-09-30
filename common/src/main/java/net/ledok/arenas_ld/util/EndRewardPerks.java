package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.compat.LuckPermsCompat;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Per-player LuckPerms reward perks: extra loot-table rolls and currency / skill-XP multipliers.
 * Read for a raid win, a dungeon completion, and the mob arena (per-player wave loot rows and the
 * end-of-run summary); per-room dungeon rewards never read these. A missing or unparseable meta
 * value, no LuckPerms, or an offline player (LuckPerms only has online players loaded) all give
 * the defaults.
 *
 * @param lootRolls          loot-table roll multiplier for the mode, 1..10 (raid/dungeon: the
 *                           per-player table rolls this many times; arena: each per-player
 *                           wave row's rolls are multiplied by it)
 * @param currencyMultiplier applied after the hardcore ×2, 0..10
 * @param xpMultiplier       applied to the skill XP reward, 0..10
 */
public record EndRewardPerks(int lootRolls, double currencyMultiplier, double xpMultiplier) {

    public static final String RAID_LOOT_ROLLS = "arenas_ld.raid_loot_rolls";
    public static final String DUNGEON_LOOT_ROLLS = "arenas_ld.dungeon_loot_rolls";
    public static final String ARENA_LOOT_ROLLS = "arenas_ld.arena_loot_rolls";
    public static final String CURRENCY_MULTIPLIER = "arenas_ld.currency_multiplier";
    public static final String XP_MULTIPLIER = "arenas_ld.xp_multiplier";

    public static final EndRewardPerks DEFAULT = new EndRewardPerks(1, 1.0, 1.0);

    public enum Mode { RAID, DUNGEON, ARENA }

    /** The perks for {@code player}; {@link #DEFAULT} for an offline player ({@code null}). */
    public static EndRewardPerks forPlayer(@Nullable UUID player, Mode mode) {
        if (player == null) {
            return DEFAULT;
        }
        String rollsKey = switch (mode) {
            case RAID -> RAID_LOOT_ROLLS;
            case DUNGEON -> DUNGEON_LOOT_ROLLS;
            case ARENA -> ARENA_LOOT_ROLLS;
        };
        int rolls = Math.clamp(LuckPermsCompat.metaInt(player, rollsKey).orElse(1), 1, 10);
        double currency = Math.clamp(LuckPermsCompat.metaDouble(player, CURRENCY_MULTIPLIER).orElse(1.0), 0.0, 10.0);
        double xp = Math.clamp(LuckPermsCompat.metaDouble(player, XP_MULTIPLIER).orElse(1.0), 0.0, 10.0);
        return new EndRewardPerks(rolls, currency, xp);
    }

    public long scaleCurrency(long amount) {
        return Math.round(amount * currencyMultiplier);
    }

    public int scaleXp(int amount) {
        return (int) Math.round(amount * xpMultiplier);
    }
}
