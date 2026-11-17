package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.rewards.RewardModifier;
import net.ledok.arenas_ld.rewards.RewardModifiers;
import net.minecraft.core.UUIDUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A player's LuckPerms reward modifiers ({@link RewardModifiers#luckPerms}). LuckPerms only has online
 * players loaded, so each run snapshots its players' modifiers at run start and refreshes them at payout
 * while they are online; a player offline at payout gets the snapshot.
 *
 * <p>Saves from before 4.18.0 hold {@code lootRolls}, {@code currencyMultiplier} and {@code xpMultiplier};
 * they load as the same bonuses.
 */
public record EndRewardPerks(List<RewardModifier> modifiers) {

    public static final EndRewardPerks DEFAULT = new EndRewardPerks(List.of());
    /** What a pre-4.18 snapshot's {@code lootRolls} counts against. */
    static final double LEGACY_BASE_ROLLS = 3;

    public static final Codec<EndRewardPerks> CODEC = RecordCodecBuilder.create(i -> i.group(
        RewardModifier.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(EndRewardPerks::modifiers),
        Codec.INT.optionalFieldOf("lootRolls", 0).forGetter(p -> 0),
        Codec.DOUBLE.optionalFieldOf("currencyMultiplier", 1.0).forGetter(p -> 1.0),
        Codec.DOUBLE.optionalFieldOf("xpMultiplier", 1.0).forGetter(p -> 1.0)
    ).apply(i, EndRewardPerks::fromSave));

    /** A run's per-player snapshots, persisted with the run so a restart keeps them. */
    public static final Codec<Map<UUID, EndRewardPerks>> SNAPSHOT_CODEC =
        Codec.unboundedMap(UUIDUtil.STRING_CODEC, CODEC);

    /** The run type: LuckPerms keys can differ per mode. */
    public enum Mode { RAID, DUNGEON, ARENA }

    private static EndRewardPerks fromSave(List<RewardModifier> modifiers, int lootRolls, double currency, double xp) {
        if (!modifiers.isEmpty() || (lootRolls <= 0 && currency == 1.0 && xp == 1.0)) {
            return new EndRewardPerks(List.copyOf(modifiers));
        }
        // A pre-4.18 snapshot: the same perks as increased bonuses, rolls over the default 3 base rolls
        // (only runs going while the server updates have one).
        List<RewardModifier> converted = new ArrayList<>();
        double base = LEGACY_BASE_ROLLS;
        if (lootRolls > 0) {
            converted.add(RewardModifier.increased(RewardModifier.Stat.LOOT, lootRolls / base - 1, "luckperms", RewardModifiers.RANK_NAME));
        }
        converted.add(RewardModifier.increased(RewardModifier.Stat.CURRENCY, currency - 1, "luckperms", RewardModifiers.RANK_NAME));
        converted.add(RewardModifier.increased(RewardModifier.Stat.XP, xp - 1, "luckperms", RewardModifiers.RANK_NAME));
        converted.removeIf(RewardModifier::isNeutral);
        return new EndRewardPerks(List.copyOf(converted));
    }

    /** The modifiers for {@code player}; {@link #DEFAULT} for an offline player ({@code null}). */
    public static EndRewardPerks forPlayer(@Nullable UUID player, Mode mode) {
        if (player == null) {
            return DEFAULT;
        }
        return new EndRewardPerks(List.copyOf(RewardModifiers.luckPerms(player, RewardModifiers.Mode.valueOf(mode.name()))));
    }

    /** Records {@code player}'s current modifiers in a run's snapshot map. Call while they are online. */
    public static void snapshot(Map<UUID, EndRewardPerks> snapshots, UUID player, Mode mode) {
        snapshots.put(player, forPlayer(player, mode));
    }

    /**
     * The modifiers to pay out with: live (refreshing the snapshot) while {@code online}, otherwise the
     * snapshot taken while they were last online in this run, or none.
     */
    public static EndRewardPerks resolve(Map<UUID, EndRewardPerks> snapshots, UUID player, boolean online, Mode mode) {
        if (online) {
            EndRewardPerks live = forPlayer(player, mode);
            snapshots.put(player, live);
            return live;
        }
        return snapshots.getOrDefault(player, DEFAULT);
    }
}
