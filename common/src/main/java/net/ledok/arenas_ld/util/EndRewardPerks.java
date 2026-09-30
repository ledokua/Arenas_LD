package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.compat.LuckPermsCompat;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Per-player LuckPerms reward perks: extra loot-table rolls and currency / skill-XP multipliers.
 * Read for a raid win, a dungeon completion, and the mob arena (per-player wave loot rows and the
 * end-of-run summary); per-room dungeon rewards never read these. A missing or unparseable meta
 * value or no LuckPerms gives the defaults. LuckPerms only has online players loaded, so each run
 * snapshots its players' perks at run start; a player offline at payout gets that snapshot.
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

    public static final Codec<EndRewardPerks> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("lootRolls", 1).forGetter(EndRewardPerks::lootRolls),
        Codec.DOUBLE.optionalFieldOf("currencyMultiplier", 1.0).forGetter(EndRewardPerks::currencyMultiplier),
        Codec.DOUBLE.optionalFieldOf("xpMultiplier", 1.0).forGetter(EndRewardPerks::xpMultiplier)
    ).apply(i, EndRewardPerks::new));

    /** A run's per-player snapshots, persisted with the run so a restart keeps them. */
    public static final Codec<Map<UUID, EndRewardPerks>> SNAPSHOT_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, CODEC);

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

    /** Records {@code player}'s current perks in a run's snapshot map. Call while they are online. */
    public static void snapshot(Map<UUID, EndRewardPerks> snapshots, UUID player, Mode mode) {
        snapshots.put(player, forPlayer(player, mode));
    }

    /**
     * The perks to pay out with: live (refreshing the snapshot) while {@code online}, otherwise the
     * snapshot taken while they were last online in this run, or the defaults if there is none.
     */
    public static EndRewardPerks resolve(Map<UUID, EndRewardPerks> snapshots, UUID player, boolean online, Mode mode) {
        if (online) {
            EndRewardPerks live = forPlayer(player, mode);
            snapshots.put(player, live);
            return live;
        }
        return snapshots.getOrDefault(player, DEFAULT);
    }

    public long scaleCurrency(long amount) {
        return Math.round(amount * currencyMultiplier);
    }

    public int scaleXp(int amount) {
        return (int) Math.round(amount * xpMultiplier);
    }
}
