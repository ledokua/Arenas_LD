package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** The API's run-finished listeners; the three run lifecycles report through {@link #fire}. */
public final class RunEvents {
    /** Own logger: these run third-party callbacks and must not need the mod bootstrapped. */
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ArenasLdMod.MOD_ID);
    private static final List<Consumer<ArenasApi.RunResult>> LISTENERS = new CopyOnWriteArrayList<>();

    private RunEvents() {
    }

    public static void addListener(Consumer<ArenasApi.RunResult> listener) {
        LISTENERS.add(listener);
    }

    /**
     * Reports a finished run.
     *
     * @param tier  the run's difficulty; null for arenas
     * @param wave  arenas: the last wave reached; 0 otherwise
     */
    public static void fire(ActivityHost host, @Nullable DifficultyTier tier, boolean win, long startTick, long endTick,
                            int wave, Collection<UUID> players) {
        ResourceLocation id = host.activityId();
        if (LISTENERS.isEmpty() || id == null) {
            return;
        }
        ArenasApi.RunResult result = new ArenasApi.RunResult(id, host.activityType(),
            tier == null ? null : ActivityHost.difficultyId(tier),
            win ? ArenasApi.Outcome.WIN : ArenasApi.Outcome.LOSS,
            Math.max(0L, endTick - startTick), Math.max(0, wave), List.copyOf(players));
        for (Consumer<ArenasApi.RunResult> listener : LISTENERS) {
            try {
                listener.accept(result);
            } catch (Throwable t) {
                LOGGER.error("Run listener {} failed for {}", listener, result, t);
            }
        }
    }
}
