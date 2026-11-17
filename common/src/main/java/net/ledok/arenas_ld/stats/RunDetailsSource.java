package net.ledok.arenas_ld.stats;

import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the run details and the run file show about the run itself, read from the run and its controller when
 * needed (not on the hot path).
 *
 * @param type   "dungeon" or "raid"
 * @param result   "win", "fail", "abandoned", "forced"; empty while it runs
 * @param runLevel the difficulty's level ({@link RunGear#levelOf} of its loot table); -1 when it has none
 * @param timeLimitSeconds the difficulty's time limit (0 = none)
 */
public record RunDetailsSource(@Nullable ResourceLocation activityId, Component activityName, String type,
                               DifficultyTier tier, ArenasApi.InstanceRef instance, boolean hardcore,
                               List<String> modifiers, long startTick, long baseCurrency, int baseXp, String result,
                               int runLevel, int timeLimitSeconds, double baseLootRolls) {
    /** With the global base loot rolls (the shape of 4.25.0–4.38.x). */
    public RunDetailsSource(@Nullable ResourceLocation activityId, Component activityName, String type,
                            DifficultyTier tier, ArenasApi.InstanceRef instance, boolean hardcore,
                            List<String> modifiers, long startTick, long baseCurrency, int baseXp, String result,
                            int runLevel, int timeLimitSeconds) {
        this(activityId, activityName, type, tier, instance, hardcore, modifiers, startTick, baseCurrency, baseXp, result,
            runLevel, timeLimitSeconds, -1);
    }

    /** The base loot rolls a completion rolls: the controller's own ({@code baseLootRolls} ≥ 0) or the global. */
    public double lootRolls() {
        return baseLootRolls >= 0 ? baseLootRolls
            : net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().completion_loot_rolls;
    }

    /** Without a time limit (the shape of 4.24.x). */
    public RunDetailsSource(@Nullable ResourceLocation activityId, Component activityName, String type,
                            DifficultyTier tier, ArenasApi.InstanceRef instance, boolean hardcore,
                            List<String> modifiers, long startTick, long baseCurrency, int baseXp, String result,
                            int runLevel) {
        this(activityId, activityName, type, tier, instance, hardcore, modifiers, startTick, baseCurrency, baseXp, result,
            runLevel, 0);
    }

    /** Without a run level (the shape before 4.24.0). */
    public RunDetailsSource(@Nullable ResourceLocation activityId, Component activityName, String type,
                            DifficultyTier tier, ArenasApi.InstanceRef instance, boolean hardcore,
                            List<String> modifiers, long startTick, long baseCurrency, int baseXp, String result) {
        this(activityId, activityName, type, tier, instance, hardcore, modifiers, startTick, baseCurrency, baseXp, result, -1, 0);
    }
}
