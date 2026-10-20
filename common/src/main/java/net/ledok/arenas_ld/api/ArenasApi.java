package net.ledok.arenas_ld.api;

import net.ledok.arenas_ld.activity.ActivityRegistry;
import net.ledok.arenas_ld.activity.EntryGates;
import net.ledok.arenas_ld.activity.LobbyScreens;
import net.ledok.arenas_ld.activity.RunEvents;
import net.ledok.arenas_ld.util.RunMinions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Arenas_LD's public API: the dungeons, raids and mob arenas on this server, an entry gate other
 * mods can veto entry with, and run-finished events. Hub mods build their activity lists on it and
 * keep their own settings (visibility, art, level requirements) per activity id; nothing in
 * Arenas_LD depends on who calls it.
 *
 * <p>Every method is server-side and must be called on the server thread. Activities are keyed by
 * their stable id (set in each controller's admin screen), never by block position, so callers
 * keep working if the owner moves a controller or replaces the block with something else later.
 *
 * <p>See {@code API.md} in the repository for when the gate is asked and worked examples.
 */
public final class ArenasApi {
    private ArenasApi() {
    }

    /** What kind of controller an activity is. */
    public enum ActivityType { DUNGEON, RAID, ARENA }

    /**
     * One enabled difficulty of a dungeon or raid.
     *
     * @param id the difficulty id: {@code "easy"}, {@code "normal"}, {@code "hard"} or
     *           {@code "nightmare"} (raids show the top tier as Legendary but use the same id)
     */
    public record Difficulty(String id) {
    }

    /**
     * One dungeon, raid or arena.
     *
     * @param id            the stable id set in the admin screen, e.g. {@code arenas_ld:crypt}
     * @param type          dungeon, raid or arena
     * @param name          the display name (a fallback like "Unnamed dungeon" when the admin left it blank)
     * @param difficulties  the enabled difficulties, lowest first; empty for arenas, which have none
     */
    public record Activity(ResourceLocation id, ActivityType type, Component name, List<Difficulty> difficulties) {
    }

    /** Asked before a player gets into an activity. */
    @FunctionalInterface
    public interface EntryGate {
        /**
         * @param player     the player who wants in
         * @param activity   the activity id
         * @param difficulty the difficulty id ({@link Difficulty#id()}), or null for the activity
         *                   itself: opening its lobby screen at all, and every check for arenas
         * @return null to allow, or the reason to refuse with; it is shown to players as is
         */
        @Nullable Component check(ServerPlayer player, ResourceLocation activity, @Nullable String difficulty);
    }

    /** How a run ended. Arenas: WIN when the run reached its maximum wave, LOSS otherwise. */
    public enum Outcome { WIN, LOSS }

    /**
     * A finished run, reported at the win or loss, after its rewards are paid (API.md says when
     * leaderboards are written).
     *
     * @param activity      the activity id
     * @param type          dungeon, raid or arena
     * @param difficulty    the difficulty id the run was played on; null for arenas
     * @param outcome       win or loss
     * @param durationTicks game ticks from the run's start to its end
     * @param wave          arenas: the last wave reached; 0 for dungeons and raids
     * @param players       everyone who took part, including players who left or died out on hardcore
     */
    public record RunResult(ResourceLocation activity, ActivityType type, @Nullable String difficulty,
                            Outcome outcome, long durationTicks, int wave, List<UUID> players) {
    }

    /** Every activity on the server, sorted by type then id. */
    public static List<Activity> activities(MinecraftServer server) {
        return ActivityRegistry.activities(server);
    }

    /** One activity by id. */
    public static Optional<Activity> activity(MinecraftServer server, ResourceLocation id) {
        return ActivityRegistry.activity(server, id);
    }

    /**
     * Called (on the server thread) after an activity is added or removed, or after an activity's id,
     * name or enabled difficulties change. Re-read {@link #activities}.
     */
    public static void addActivitiesListener(Runnable listener) {
        ActivityRegistry.addListener(listener);
    }

    /**
     * Opens the activity's lobby screen for the player (lobbies, my lobby, leaderboard, invites),
     * exactly as using the controller block would, but from anywhere and from any dimension.
     *
     * @return false if the id is unknown, or the entry gate refused (the player is told why)
     */
    public static boolean openLobbies(ServerPlayer player, ResourceLocation activity) {
        return LobbyScreens.openRemote(player, activity);
    }

    /**
     * Adds an entry gate. Several may register; they are asked in registration order and the first
     * refusal wins. A gate that throws is logged and skipped.
     */
    public static void registerEntryGate(EntryGate gate) {
        EntryGates.register(gate);
    }

    /** Called (on the server thread) once per finished run of any type. */
    public static void addRunListener(Consumer<RunResult> listener) {
        RunEvents.addListener(listener);
    }

    /**
     * Runs {@code spawning} as {@code summoner}. Every mob it adds to the world becomes the
     * summoner's minion when the summoner is a run mob (a boss or wave mob of a dungeon, raid or
     * arena): on its team, with its health and damage scaling, without loot or experience, and gone
     * when the summoner dies. Outside a run it just runs {@code spawning}.
     *
     * <p>Not needed for mobs spawned from the summoner's own tick or while it is hurt, or whose
     * owner ({@code OwnableEntity}, {@code TraceableEntity}) is the summoner: those are recognized
     * on their own. Use it for spawns from delayed tasks or from other entities.
     */
    public static void spawnAsMinions(LivingEntity summoner, Runnable spawning) {
        RunMinions.actingAs(summoner, () -> {
            spawning.run();
            return null;
        });
    }
}
