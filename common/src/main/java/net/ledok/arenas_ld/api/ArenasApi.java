package net.ledok.arenas_ld.api;

import net.ledok.arenas_ld.activity.ActivityAdmin;
import net.ledok.arenas_ld.activity.ActivityRegistry;
import net.ledok.arenas_ld.activity.EntryGates;
import net.ledok.arenas_ld.activity.LobbyScreens;
import net.ledok.arenas_ld.activity.RunEvents;
import net.ledok.arenas_ld.util.RunMinions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
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

    /**
     * How a run ended. Arenas: WIN when the run reached its maximum wave, LOSS otherwise. FORCED
     * when staff ended it ({@link #forceEnd}, {@code /arenasld activity stop}): neither a win nor a
     * loss for the players, so leave it out of their stats.
     */
    public enum Outcome { WIN, LOSS, FORCED }

    /**
     * A finished run, reported at the win or loss, after its rewards are paid (API.md says when
     * leaderboards are written).
     *
     * @param activity      the activity id
     * @param type          dungeon, raid or arena
     * @param difficulty    the difficulty id the run was played on; null for arenas
     * @param outcome       win, loss, or forced (ended by staff)
     * @param durationTicks game ticks from the run's start to its end
     * @param wave          arenas: the last wave reached; 0 for dungeons and raids
     * @param players       everyone who took part, including players who left or died out on hardcore
     */
    public record RunResult(ResourceLocation activity, ActivityType type, @Nullable String difficulty,
                            Outcome outcome, long durationTicks, int wave, List<UUID> players) {
    }

    /** Where an activity's controller block is. */
    public record ControllerLocation(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /**
     * One instance of an activity: the Dungeon Boss Spawner, Raid Boss Spawner or Arena Spawner a
     * run plays at. Several instances let several parties play the same activity at once.
     */
    public record InstanceRef(ResourceKey<Level> dimension, BlockPos pos) {
    }

    /** Whether an instance can take a run now. */
    public enum InstanceState { FREE, RUNNING, COOLDOWN }

    /**
     * One instance and what it is doing.
     *
     * @param cooldownTicksLeft ticks until it is free again while {@link InstanceState#COOLDOWN}; 0 otherwise
     * @param pendingRemoval    an admin unlinked it while a run was going: it goes once that run ends
     */
    public record InstanceStatus(InstanceRef instance, InstanceState state, int cooldownTicksLeft, boolean pendingRemoval) {
    }

    /**
     * A run going on, including one that is over and counting down its exit timer.
     *
     * @param instance     where it plays (what {@link #forceEnd} takes)
     * @param difficulty   the difficulty id; null for arenas
     * @param players      the players taking part now (not those who left or died out on hardcore)
     * @param phase        a short line for staff, e.g. "Room 3", "Boss 42%", "Wave 4/10", "Closing"
     * @param ticksElapsed game ticks since the run started
     * @param ticksLeft    ticks left on the timer that is running now (run timer, wave timer, exit
     *                     timer), or -1 when none is
     * @param closing      the run is won or lost and its players are on the exit timer
     */
    public record RunStatus(InstanceRef instance, @Nullable String difficulty, List<UUID> players, Component phase,
                            long ticksElapsed, long ticksLeft, boolean closing) {
    }

    /** A setup problem worth a staff member's look. */
    public enum Warning {
        /**
         * The id was made up by Arenas_LD (from the controller's position or name), not set by staff;
         * gone once staff set one on the admin screen. Don't store it yet.
         */
        GENERATED_ID,
        /** No display name set. */
        UNNAMED,
        /** A dungeon or raid with no difficulty enabled: nobody can start it. */
        NO_DIFFICULTIES,
        /** No instance linked: nobody can start it. */
        NO_INSTANCES,
        /** Instances waiting to be unlinked once their runs end. */
        PENDING_REMOVALS,
        /** The controller is gone (or its dimension is): {@link #forget} drops the activity. */
        CONTROLLER_MISSING,
        /**
         * The controller's chunk isn't loaded, so nothing is known about its runs. Controllers keep
         * their own chunk loaded, so this usually means it was broken without a block update.
         */
        CONTROLLER_NOT_LOADED
    }

    /**
     * What an activity is doing right now.
     *
     * @param instances      every instance, in the controller's order
     * @param runs           the runs going on now
     * @param openLobbies    lobbies forming or ready to start, queued ones included
     * @param queuedLobbies  of those, the ones waiting for an instance to come free
     * @param waitingPlayers the members of the open lobbies
     * @param warnings       setup problems; with {@link Warning#CONTROLLER_MISSING} or
     *                       {@link Warning#CONTROLLER_NOT_LOADED} the lists are empty and the counts 0
     */
    public record ActivityStatus(ResourceLocation activity, List<InstanceStatus> instances, List<RunStatus> runs,
                                 int openLobbies, int queuedLobbies, int waitingPlayers, Set<Warning> warnings) {
        /** Instances waiting to be unlinked once their runs end. */
        public int pendingInstanceRemovals() {
            return (int) instances.stream().filter(InstanceStatus::pendingRemoval).count();
        }
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

    // ── Admin ────────────────────────────────────────────────────────────────
    // For staff tools. Arenas_LD checks op (permission level 2) only where noted; the caller decides
    // who may use the others. A refusal is returned as the reason to show; null means done. An
    // unknown id is refused ("Unknown activity: ..."), or gives an empty location or status.

    /** Where the activity's controller is, straight from the saved index (loads nothing). */
    public static Optional<ControllerLocation> location(MinecraftServer server, ResourceLocation id) {
        return ActivityAdmin.location(server, id);
    }

    /**
     * Teleports the player next to (or onto) the activity's controller, facing it, from any
     * dimension: the closest spot within 3 blocks with room to stand, solid ground and no liquid,
     * fire or other harmful block, else on top of the controller or straight above it. Refused
     * while the player is in a run or busy (BusyLib, naming why), when the controller's dimension
     * no longer exists, or when there is no safe spot. May load the chunks around the controller.
     */
    public static @Nullable Component teleportToController(ServerPlayer player, ResourceLocation id) {
        return ActivityAdmin.teleportToController(player, id);
    }

    /**
     * Sets the activity's display name on its controller, as its admin screen would: the activities
     * listener fires (once) when it changed, and open lobby and admin screens are updated. Refused
     * when the name is blank or longer than 48 characters (it is trimmed first).
     */
    public static @Nullable Component rename(MinecraftServer server, ResourceLocation id, String name) {
        return ActivityAdmin.rename(server, id, name);
    }

    /**
     * Opens the controller's admin screen for the player from any distance or dimension, the same
     * screen as {@code /arenasld admin}. Refused unless the player is an op (permission level 2).
     */
    public static @Nullable Component openAdmin(ServerPlayer player, ResourceLocation id) {
        return ActivityAdmin.openAdmin(player, id);
    }

    /**
     * What the activity is doing right now. Cheap enough to poll every second or two for every
     * activity: it reads the saved index and what the controller holds in memory, and never loads
     * a chunk (an unloaded controller reports {@link Warning#CONTROLLER_NOT_LOADED}). Empty for an
     * unknown id.
     */
    public static Optional<ActivityStatus> status(MinecraftServer server, ResourceLocation id) {
        return ActivityAdmin.status(server, id);
    }

    /**
     * Ends a run as staff: no rewards (arenas still pay for the waves reached, as on a loss), the
     * run reports {@link Outcome#FORCED}, and its players are returned after the usual exit timer.
     * A run already on its exit timer is closed at once instead, keeping its result. Refused when
     * nothing is running (at that instance).
     *
     * @param instance the instance whose run to end, or null for every run of the activity
     */
    public static @Nullable Component forceEnd(MinecraftServer server, ResourceLocation id, @Nullable InstanceRef instance) {
        return ActivityAdmin.forceEnd(server, id, instance);
    }

    /**
     * Drops an activity whose controller is gone without its block being broken (a tool that
     * skipped block updates). Refused while the controller is still there.
     */
    public static @Nullable Component forget(MinecraftServer server, ResourceLocation id) {
        return ActivityAdmin.forget(server, id);
    }
}
