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
     * Says what entering needs, so the lobby screens can show it whether or not the player meets it.
     * Registered next to the {@link EntryGate} that enforces it ({@link #registerEntryRequirement}).
     */
    @FunctionalInterface
    public interface EntryRequirement {
        /**
         * @param player     the player looking at the lobby screen
         * @param activity   the activity id
         * @param difficulty the difficulty id, or null for the activity itself (arenas)
         * @return the requirement as players see it once they meet it (green, e.g. "Lv 25+"), or null
         *         for none. Where the gate refuses, the screen shows the refusal instead, as before.
         */
        @Nullable Component describe(ServerPlayer player, ResourceLocation activity, @Nullable String difficulty);
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
     * The lobby a player is in (forming, ready, queued, or in a run), across every activity.
     *
     * @param activity   its activity id (for {@link #openLobbies})
     * @param name       the activity's display name
     * @param members    members now, of {@code maxMembers}
     * @param ready      members marked ready
     * @param owner      whether the player owns it
     * @param inRun      whether its run has started
     */
    public record OwnLobby(ResourceLocation activity, ActivityType type, Component name, int members, int maxMembers,
                           int ready, boolean owner, boolean inRun) {
    }

    /** The player's lobby, or empty when they are in none (e.g. for a "My lobby" button). */
    public static Optional<OwnLobby> ownLobby(ServerPlayer player) {
        var membership = net.ledok.arenas_ld.activity.LobbyMembership.find(player.server, player.getUUID());
        if (membership == null || membership.host().activityId() == null) {
            return Optional.empty();
        }
        var lobby = membership.lobby();
        return Optional.of(new OwnLobby(membership.host().activityId(), membership.host().activityType(),
            membership.activityName(), lobby.members().size(), membership.host().getMaxPartySize(),
            lobby.readyMembers().size(), lobby.isOwner(player.getUUID()),
            lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN));
    }

    // ── Open lobbies, server-wide (4.31.0+) ──────────────────────────────────

    /** How a player gets into an open lobby: PUBLIC ones are joined, FRIENDS ones asked (the owner accepts). */
    public enum LobbyAccess { JOIN, REQUEST }

    /**
     * A lobby forming now (not in a run, not private), for a server-wide overview.
     *
     * @param activity      its activity id (for {@link #openLobbies(ServerPlayer, ResourceLocation)} and
     *                      {@link #joinOpenLobby})
     * @param name          the activity's display name
     * @param members       its members now (the owner included), of {@code maxMembers}
     * @param ready         members marked ready
     * @param difficulty    the picked tier's id: "easy", "normal", "hard", "nightmare"
     * @param hardcore      hardcore on
     * @param modifiers     the run modifier ids the owner picked
     * @param access        JOIN (public) or REQUEST (friends-only: anyone may ask; a hub may show it only to the
     *                      owner's friends)
     * @param createdAtTick the server tick it was made (older first in the list)
     * @param minLevel      minimum level to join or ask (0: none; 4.33.0+), in the {@link #setLevelProvider} level
     */
    public record OpenLobby(ResourceLocation activity, ActivityType type, Component name, UUID lobbyId, UUID owner,
                            String ownerName, List<UUID> members, int maxMembers, int ready, String difficulty,
                            boolean hardcore, List<String> modifiers, LobbyAccess access, long createdAtTick,
                            int minLevel) {
        public boolean full() {
            return members.size() >= maxMembers;
        }
    }

    /**
     * Every open lobby on the server (PUBLIC and FRIENDS, forming or queued, not in a run), across every loaded
     * dungeon, raid and arena controller, by activity id then age. Reads memory only; fine to call on a hub refresh.
     */
    public static List<OpenLobby> listOpenLobbies(MinecraftServer server) {
        return net.ledok.arenas_ld.activity.OpenLobbies.list(server);
    }

    /**
     * Goes up whenever {@link #listOpenLobbies} changes (checked once a second, only while a listener is registered):
     * compare it to the one you showed.
     */
    public static long openLobbiesRevision() {
        return net.ledok.arenas_ld.activity.OpenLobbies.revision();
    }

    /** Called on the server thread (at most once a second) when the open lobbies changed. */
    public static void addOpenLobbiesListener(Runnable listener) {
        net.ledok.arenas_ld.activity.OpenLobbies.addListener(listener);
    }

    // ── Lobby creation and settings for a hub (4.33.0+) ──────────────────────

    /** A lobby's visibility: PUBLIC (anyone joins), FRIENDS (anyone asks, the owner accepts), PRIVATE (invites only). */
    public enum LobbyVisibility { PUBLIC, FRIENDS, PRIVATE }

    /**
     * A lobby's settings, all at once.
     *
     * @param difficulty "easy" / "normal" / "hard" / "nightmare"; empty for arenas (no tiers) or to keep it
     * @param hardcore   the {@code hardcore_run} modifier on or off (also settable through {@code modifiers})
     * @param modifiers  run modifier ids (from {@link #activityInfo}); exclusive groups and conflicts are refused
     * @param minLevel   minimum level to join or ask to join (0: none). Checked only while a level provider is set
     *                   ({@link #setLevelProvider}); an owner's invite skips it
     */
    public record LobbySettings(String difficulty, boolean hardcore, List<String> modifiers, LobbyVisibility visibility,
                                int minLevel) {
    }

    /**
     * Where a player's level for lobby minimum levels comes from (Eden_LD: its activity level). Null removes it;
     * without one, minimum levels are stored but not checked.
     */
    public static void setLevelProvider(@Nullable java.util.function.ToIntFunction<ServerPlayer> levels) {
        net.ledok.arenas_ld.activity.LobbyLevels.setProvider(levels);
    }

    /**
     * Makes a lobby for {@code owner} at the activity with these settings, through the controller's usual flow (busy,
     * entry gate for the tier, one lobby at a time: a forming lobby elsewhere is left). The owner's own forming lobby
     * at that activity just gets the settings. With {@code openScreen} the activity's lobby screen opens.
     *
     * @return null when done; otherwise why not (not told to the player: show it)
     */
    public static @Nullable Component createLobby(ServerPlayer owner, ResourceLocation activity, LobbySettings settings,
                                                  boolean openScreen) {
        return net.ledok.arenas_ld.activity.LobbyCreation.create(owner, activity, settings, openScreen);
    }

    /**
     * Changes the settings of the lobby {@code owner} owns (anywhere). Members get a line naming the change; a change to
     * the run (difficulty, hardcore, modifiers) un-readies everyone but the owner.
     *
     * @return null when done; otherwise why not
     */
    public static @Nullable Component updateLobby(ServerPlayer owner, LobbySettings settings) {
        return net.ledok.arenas_ld.activity.LobbyCreation.update(owner, settings);
    }

    /**
     * The settings of the forming lobby {@code owner} owns (any visibility; private ones too), for an edit form; empty
     * when they own none or its run has started. {@code difficulty} is "" for arenas; {@code hardcore} mirrors the
     * {@code hardcore_run} modifier, which is also in {@code modifiers}. Use {@link #ownLobby} for its activity. (4.33.1+)
     */
    public static Optional<LobbySettings> lobbySettings(ServerPlayer owner) {
        return net.ledok.arenas_ld.activity.LobbyCreation.settings(owner);
    }

    /**
     * Who is in a player's party outside dungeon/raid/arena runs, for the ally HP sidebar (4.34.0+; Eden_LD: its
     * parties). Called a few times a second per online player not in a run: keep it cheap. Return the members (the
     * player may be included); empty when none. Null removes it. Inside a run the sidebar always shows the run's members.
     */
    public static void setPartyProvider(@Nullable java.util.function.Function<ServerPlayer, java.util.Collection<UUID>> party) {
        net.ledok.arenas_ld.util.AllyHealthSync.partyProvider = party;
    }

    /**
     * How many distinct items {@link #possibleDrops} would list without its 64 cap (same rules and cache), for a "+N"
     * counter. 0 when unknown. (4.34.4+)
     */
    public static int possibleDropsCount(MinecraftServer server, ResourceLocation activity, @Nullable String difficulty) {
        return net.ledok.arenas_ld.activity.LobbyCreation.dropsCount(server, activity, difficulty);
    }

    /** A run modifier as the lobby offers it: its name, description, reward effect ("+2 loot · +25% XP") and icon id. */
    public record ModifierInfo(String id, Component name, Component description, Component rewards, String icon,
                               List<String> conflicts) {
    }

    /**
     * What a create panel needs about an activity.
     *
     * @param difficulties the enabled tiers' ids, easiest first (empty for arenas)
     * @param modifiers    the run modifiers its lobbies offer (hardcore is {@code hardcore_run})
     * @param maxMembers   party size limit
     * @param levelsActive whether lobby minimum levels are checked (a level provider is set)
     */
    public record ActivityInfo(ResourceLocation activity, ActivityType type, Component name, List<String> difficulties,
                               List<ModifierInfo> modifiers, int maxMembers, boolean levelsActive) {
    }

    /** The activity's info for a create panel, or empty when it's unknown or its controller isn't loaded. */
    public static Optional<ActivityInfo> activityInfo(MinecraftServer server, ResourceLocation activity) {
        return net.ledok.arenas_ld.activity.LobbyCreation.info(server, activity);
    }

    /**
     * Items a win can give at this activity and difficulty, from its reward loot tables (nested tables and item tags
     * included; no rolls, so it's what CAN drop). Copies, at most 64; cached until the next data reload.
     *
     * @param difficulty a tier id, or null/empty for every tier (arenas: ignored)
     */
    public static List<net.minecraft.world.item.ItemStack> possibleDrops(MinecraftServer server, ResourceLocation activity,
                                                                         @Nullable String difficulty) {
        return net.ledok.arenas_ld.activity.LobbyCreation.drops(server, activity, difficulty);
    }

    /**
     * Joins a JOIN lobby, or asks to join a REQUEST one, from anywhere, through the controller's usual flow: busy check,
     * entry gate, one lobby at a time (the player leaves their forming lobby elsewhere), full lobby. Then that
     * activity's lobby screen opens. Already a member: just opens it.
     *
     * @return null when it went through; otherwise why not (the player is told as well)
     */
    public static @Nullable Component joinOpenLobby(ServerPlayer player, ResourceLocation activity, UUID lobbyId) {
        return net.ledok.arenas_ld.activity.OpenLobbies.join(player, activity, lobbyId);
    }

    /**
     * Opens the lobby screen of the player's own lobby from anywhere (the same remote screen as
     * {@link #openLobbies}: ready, leave, invite, modifiers all work). {@code /arenasld lobby open} does this.
     *
     * @return false when the player is in no lobby, or the entry gate refused (the player is told why)
     */
    public static boolean openOwnLobby(ServerPlayer player) {
        var membership = net.ledok.arenas_ld.activity.LobbyMembership.find(player.server, player.getUUID());
        return membership != null && LobbyScreens.open(player, membership.host().asBlockEntity(), true);
    }

    /**
     * Opens the live run details screen of the dungeon or raid run the player is in: each player's state, deaths,
     * respawn timer, HP, damage dealt and taken, healing; the run's modifiers, phase, time, boss HP and rewards.
     * {@code /arenasld run details} and the client's key do this.
     *
     * @return false when the player is in no dungeon or raid run (or run stats are off)
     */
    public static boolean openRunDetails(ServerPlayer player) {
        var stats = net.ledok.arenas_ld.stats.RunDetails.runOf(player.getUUID());
        if (stats == null) {
            return false;
        }
        net.ledok.arenas_ld.stats.RunDetails.open(player, stats, false);
        return true;
    }

    /** How a finished run ended. */
    public enum HistoryResult { WIN, FAIL, ABANDONED, FORCED }

    /**
     * A finished dungeon or raid run in the history (kept 30 days).
     *
     * @param runId           its id, for {@link #openRunDetails(ServerPlayer, String)}
     * @param activity        its activity id
     * @param name            the activity's name when the run ended
     * @param difficulty      "easy" … "nightmare"
     * @param modifiers       the run modifier ids it had
     * @param start           when it started
     * @param durationSeconds how long it went
     * @param party           the players' names, in join order
     * @param partyUuids      the same players' UUIDs
     * @param runLevel        the difficulty's level (its loot table's {@code level_N}); -1 when it has none (4.24.0+)
     * @param playerLevels    per player, same order: the {@code loot_stage} level at run start; -1 unknown (4.24.0+)
     * @param gearPower       per player: the average Dungeon Difficulty power level of their gear at run start;
     *                        -1 when none of it has one or unknown (4.24.0+)
     * @param classes         per player: their class at run start, the equipped spell book's item id
     *                        ("wizards:arcane_spell_book"), else "skills:&lt;branch&gt;" from the class skill tree, else ""
     *                        (4.24.1+)
     * @param loadouts        per player: the rental build loadout id they ran with, "" for their own gear (4.26.0+)
     */
    public record RunHistoryEntry(String runId, ResourceLocation activity, ActivityType type, String name,
                                  String difficulty, boolean hardcore, List<String> modifiers, java.time.Instant start,
                                  int durationSeconds, HistoryResult result, List<String> party, List<UUID> partyUuids,
                                  int runLevel, List<Integer> playerLevels, List<Float> gearPower, List<String> classes,
                                  List<String> loadouts) {
        /** Without levels (the shape before 4.24.0). */
        public RunHistoryEntry(String runId, ResourceLocation activity, ActivityType type, String name,
                               String difficulty, boolean hardcore, List<String> modifiers, java.time.Instant start,
                               int durationSeconds, HistoryResult result, List<String> party, List<UUID> partyUuids) {
            this(runId, activity, type, name, difficulty, hardcore, modifiers, start, durationSeconds, result, party,
                partyUuids, -1, List.of(), List.of(), List.of(), List.of());
        }

        /** Without loadouts (the shape of 4.24.1–4.25.x). */
        public RunHistoryEntry(String runId, ResourceLocation activity, ActivityType type, String name,
                               String difficulty, boolean hardcore, List<String> modifiers, java.time.Instant start,
                               int durationSeconds, HistoryResult result, List<String> party, List<UUID> partyUuids,
                               int runLevel, List<Integer> playerLevels, List<Float> gearPower, List<String> classes) {
            this(runId, activity, type, name, difficulty, hardcore, modifiers, start, durationSeconds, result, party,
                partyUuids, runLevel, playerLevels, gearPower, classes, List.of());
        }

        /** Without classes (the shape of 4.24.0). */
        public RunHistoryEntry(String runId, ResourceLocation activity, ActivityType type, String name,
                               String difficulty, boolean hardcore, List<String> modifiers, java.time.Instant start,
                               int durationSeconds, HistoryResult result, List<String> party, List<UUID> partyUuids,
                               int runLevel, List<Integer> playerLevels, List<Float> gearPower) {
            this(runId, activity, type, name, difficulty, hardcore, modifiers, start, durationSeconds, result, party,
                partyUuids, runLevel, playerLevels, gearPower, List.of(), List.of());
        }
    }

    /**
     * Finished dungeon and raid runs, newest first (staff history).
     *
     * @param activity only this activity's runs, or null for all
     * @param player   only runs this player was in, or null for anyone
     * @param limit    at most this many
     */
    public static List<RunHistoryEntry> runHistory(MinecraftServer server, @Nullable ResourceLocation activity,
                                                   @Nullable UUID player, int limit) {
        List<RunHistoryEntry> out = new java.util.ArrayList<>();
        for (var entry : net.ledok.arenas_ld.stats.RunRecords.history(activity == null ? null : activity.toString(), player, limit)) {
            ResourceLocation id = ResourceLocation.tryParse(entry.activityId);
            HistoryResult result = switch (entry.result) {
                case "win" -> HistoryResult.WIN;
                case "abandoned" -> HistoryResult.ABANDONED;
                case "forced" -> HistoryResult.FORCED;
                default -> HistoryResult.FAIL;
            };
            List<UUID> uuids = new java.util.ArrayList<>();
            for (String uuid : entry.partyUuids) {
                try {
                    uuids.add(UUID.fromString(uuid));
                } catch (IllegalArgumentException ignored) {
                }
            }
            out.add(new RunHistoryEntry(entry.runId, id == null ? ResourceLocation.withDefaultNamespace("unknown") : id,
                "raid".equals(entry.type) ? ActivityType.RAID : ActivityType.DUNGEON, entry.activityName, entry.difficulty,
                entry.hardcore, List.copyOf(entry.modifiers), java.time.Instant.ofEpochMilli(entry.startEpochMs),
                entry.durationSeconds, result, List.copyOf(entry.party), List.copyOf(uuids), entry.runLevel,
                entry.playerLevels == null ? List.of() : List.copyOf(entry.playerLevels),
                entry.gearPower == null ? List.of() : List.copyOf(entry.gearPower),
                entry.classes == null ? List.of() : List.copyOf(entry.classes),
                entry.loadouts == null ? List.of() : List.copyOf(entry.loadouts)));
        }
        return out;
    }

    /**
     * Staff: opens a finished run's details (final numbers, staff details) from {@link #runHistory}. The run file is
     * read off the server thread; the screen opens when it is in.
     *
     * @return false when the id is unknown
     */
    public static boolean openRunDetails(ServerPlayer staff, String runId) {
        return net.ledok.arenas_ld.stats.RunRecords.open(staff, runId, true);
    }

    /** Opens the details of the last run {@code player} finished (the end screen again); false when there is none. */
    public static boolean openLastRunDetails(ServerPlayer player) {
        String runId = net.ledok.arenas_ld.stats.RunRecords.lastRunOf(player.getUUID());
        return runId != null && net.ledok.arenas_ld.stats.RunRecords.open(player, runId, false);
    }

    /**
     * Staff: opens the run details screen of the run of {@code activity} at {@code instance} (any of its runs when
     * null), with the HP nothing could be credited for. Check staff permission before calling.
     *
     * @return false when no dungeon or raid run of it is going (there)
     */
    public static boolean openRunDetails(ServerPlayer staff, ResourceLocation activity, @Nullable InstanceRef instance) {
        var stats = net.ledok.arenas_ld.stats.RunDetails.runAt(activity, instance);
        if (stats == null) {
            return false;
        }
        net.ledok.arenas_ld.stats.RunDetails.open(staff, stats, true);
        return true;
    }

    /**
     * Adds an entry gate. Several may register; they are asked in registration order and the first
     * refusal wins. A gate that throws is logged and skipped.
     */
    public static void registerEntryGate(EntryGate gate) {
        EntryGates.register(gate);
    }

    /**
     * Adds an entry requirement label (4.16.0+): shown in green on the lobby screen under each
     * difficulty the player may enter (in the header for arenas). Several may register; the first
     * non-null label wins. One that throws is logged and skipped.
     */
    public static void registerEntryRequirement(EntryRequirement requirement) {
        EntryGates.registerRequirement(requirement);
    }

    /**
     * Adds run-wide reward modifiers (4.18.0+), e.g. an event's "+25% XP": asked at every payout of every
     * dungeon, raid and arena run, and stacked with hardcore, the config's run modifiers and each
     * player's LuckPerms bonuses ({@code final = base × (1 + Σ increased) × Π more}). One that throws is
     * logged and skipped. See API.md, "Reward modifiers".
     */
    public static void registerRewardModifierSource(net.ledok.arenas_ld.rewards.RewardModifiers.Source source) {
        net.ledok.arenas_ld.rewards.RewardModifiers.registerSource(source);
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

    // ── Rental builds (4.26.0+) ──────────────────────────────────────────────

    /**
     * Lends players a preset class loadout for a dungeon or raid run ("rental builds"). Arenas_LD has no loadouts of
     * its own: a mod that has them (Eden_LD) registers a provider with {@link #setRentalProvider}. Without one, the
     * lobby offers no rental. All calls come on the server thread.
     */
    public interface RentalProvider {
        /**
         * Every preset (kit id, e.g. {@code falleneden:rental/25/paladin_holy}) a player of this level
         * ({@code loot_stage}) can rent: for each class (the preset name up to the first "_"), all its presets in the
         * highest level folder at or below {@code level} (0 included) that has that class. Sorted; Arenas doesn't modify
         * the list. Empty:
         * none (the lobby then shows no rental option). Called once a second per waiting lobby member: keep it cheap.
         * Arenas reads the level (the folder, "25") and the class (the preset up to the first "_", "paladin") from the
         * id.
         */
        List<String> presets(int level);

        /** A preset's display name as plain text: the kit's own name, else the preset id ("paladin_holy"). */
        String displayName(String kitId);

        /**
         * Puts the player's own items (and whatever else the provider swaps) aside and hands out the loadout.
         * Returns false, changing nothing, when it can't; Arenas then runs them without rental (and without the
         * rental reward cut), or tries again later.
         */
        boolean begin(ServerPlayer player, String sessionId, String kitId);

        /** Ends the player's rental: removes the loadout and gives their own items back. Works for offline players. */
        void end(MinecraftServer server, UUID player);

        /** Hands the loadout out again (after a respawn in the run: a death empties the inventory). */
        void reequip(ServerPlayer player);

        /** Whether the player is renting now (a plain reconnect keeps a rental going). */
        boolean isRenting(ServerPlayer player);

        /**
         * What a loadout hands out, for the lobby's preview (armor, weapons, spell book, jewelry, consumables): copies,
         * never given to anyone. Empty when unknown.
         */
        List<net.minecraft.world.item.ItemStack> preview(String kitId);
    }

    /** Registers the rental provider (null removes it). The last call wins. */
    public static void setRentalProvider(@Nullable RentalProvider provider) {
        net.ledok.arenas_ld.util.RunRentals.provider = provider;
    }

    /** The registered rental provider, or null. */
    public static @Nullable RentalProvider rentalProvider() {
        return net.ledok.arenas_ld.util.RunRentals.provider;
    }

    // ── Invite filter (4.28.0+) ──────────────────────────────────────────────

    /**
     * Decides who may invite a player to a raid, dungeon or arena lobby (e.g. a privacy setting, an ignore list).
     * Asked at the start of every invite, on the server thread, before the invite exists or the invitee hears of it.
     */
    @FunctionalInterface
    public interface InviteFilter {
        /**
         * Null lets the invite through. Otherwise there is no invite, the inviter gets this line (send it styled; Arenas
         * doesn't change it) and the invitee hears nothing. {@code activityId} is the activity's id as in {@link #status}
         * ("" for a controller without one). The invitee may be offline.
         */
        @Nullable Component refuse(ServerPlayer inviter, UUID invitee, String activityId);
    }

    /** Registers the invite filter (null removes it). One filter; the last call wins. */
    public static void setInviteFilter(@Nullable InviteFilter filter) {
        EntryGates.inviteFilter = filter;
    }

    /** How a player stands to another, for the lobby's invite picker (closest first). 4.40.3+. */
    public enum PlayerRelation { FRIEND, PARTY, GUILD, NONE }

    /** Tells Arenas who is whose friend, party member or guildmate (Eden_LD), for sorting the invite picker. */
    @FunctionalInterface
    public interface PlayerRelations {
        /**
         * How {@code other} (online) stands to {@code viewer}, the closest when several apply (a friend in the same
         * guild: FRIEND). Called once per online player each time the picker opens; keep it cheap.
         */
        PlayerRelation relation(ServerPlayer viewer, UUID other);
    }

    /** Registers the relations provider (null removes it: everyone is {@link PlayerRelation#NONE}). 4.40.3+. */
    public static void setPlayerRelations(@Nullable PlayerRelations relations) {
        net.ledok.arenas_ld.util.InviteCandidates.relations = relations;
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
     * The dungeon or raid controller's own breakable blocks list ({@code mode} "add" to the global
     * {@code run_breakable_blocks} or "replace" it, and its block ids / {@code #tags}). Empty for an unknown id
     * or an arena. Runs use it only in difficulties with {@code allowBlockBreaking} on.
     */
    public static Optional<net.ledok.arenas_ld.util.BreakableBlocks> breakableBlocks(MinecraftServer server, ResourceLocation id) {
        return ActivityAdmin.breakableBlocks(server, id);
    }

    /**
     * Sets the dungeon or raid controller's breakable blocks list (takes effect for its players within a
     * second). Refused for an unknown id, an arena, or an entry that is neither a known block id nor a
     * {@code #tag}. {@code actor} is only for the log.
     */
    public static @Nullable Component setBreakableBlocks(MinecraftServer server, ResourceLocation id,
                                                         net.ledok.arenas_ld.util.BreakableBlocks blocks, String actor) {
        return ActivityAdmin.setBreakableBlocks(server, id, blocks, actor);
    }

    /**
     * A dungeon or raid controller's rental mode: {@code "optional"} (each member may rent a build, ×0.5 rewards) or
     * {@code "required"} (everyone must rent one; full rewards). Empty for an unknown id or an arena. 4.39.0+.
     */
    public static Optional<String> rentalMode(MinecraftServer server, ResourceLocation id) {
        return ActivityAdmin.runSettings(server, id).map(net.ledok.arenas_ld.util.RunSettings::rentalMode);
    }

    /** Sets {@link #rentalMode}; returns why it's refused, or null. Lobbies follow at once. 4.39.0+. */
    public static @Nullable Component setRentalMode(MinecraftServer server, ResourceLocation id, String mode, String actor) {
        var settings = ActivityAdmin.runSettings(server, id);
        if (settings.isEmpty()) {
            return Component.translatable("message.arenas_ld.admin.run_settings_not_supported", id.toString());
        }
        return ActivityAdmin.setRunSettings(server, id, settings.get().withRentalMode(mode), actor);
    }

    /**
     * A dungeon or raid controller's own base loot rolls for a completion; -1 when it uses the global
     * {@code completion_loot_rolls}. Empty for an unknown id or an arena. 4.39.0+.
     */
    public static Optional<Double> baseLootRolls(MinecraftServer server, ResourceLocation id) {
        return ActivityAdmin.runSettings(server, id).map(net.ledok.arenas_ld.util.RunSettings::baseLootRolls);
    }

    /** The global base loot rolls ({@code completion_loot_rolls}), what a controller with no own rolls uses. 4.40.4+. */
    public static double completionLootRolls(MinecraftServer server) {
        return net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().completion_loot_rolls;
    }

    /** The rolls a controller's completion really starts from: its own, else the global. Empty as {@link #baseLootRolls}. 4.40.4+. */
    public static Optional<Double> effectiveLootRolls(MinecraftServer server, ResourceLocation id) {
        return ActivityAdmin.runSettings(server, id).map(net.ledok.arenas_ld.util.RunSettings::lootRolls);
    }

    /** Sets {@link #baseLootRolls} (below 0 = the global value again); returns why it's refused, or null. 4.39.0+. */
    public static @Nullable Component setBaseLootRolls(MinecraftServer server, ResourceLocation id, double rolls, String actor) {
        var settings = ActivityAdmin.runSettings(server, id);
        if (settings.isEmpty()) {
            return Component.translatable("message.arenas_ld.admin.run_settings_not_supported", id.toString());
        }
        return ActivityAdmin.setRunSettings(server, id, settings.get().withBaseLootRolls(rolls), actor);
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
     * Staff makes an instance on cooldown free now (or every instance of the activity on cooldown); the next
     * queued lobby can start at once. Logged with {@code actor} (e.g. the staff member's name). Refused when no
     * instance (at that position) is on cooldown. Check permissions before calling, as for {@link #forceEnd}.
     *
     * @param instance the instance, or null for all of the activity's instances on cooldown
     */
    public static @Nullable Component resetCooldowns(MinecraftServer server, ResourceLocation id,
                                                     @Nullable InstanceRef instance, String actor) {
        return ActivityAdmin.resetCooldowns(server, id, instance, actor);
    }

    /**
     * Drops an activity whose controller is gone without its block being broken (a tool that
     * skipped block updates). Refused while the controller is still there.
     */
    public static @Nullable Component forget(MinecraftServer server, ResourceLocation id) {
        return ActivityAdmin.forget(server, id);
    }
}
