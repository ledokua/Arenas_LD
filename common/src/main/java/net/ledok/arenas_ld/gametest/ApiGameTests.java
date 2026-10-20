package net.ledok.arenas_ld.gametest;

import net.ledok.arenas_ld.activity.ActivityRegistry;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * GameTest bodies for the public API, shared by the Fabric and NeoForge gametest holders. They
 * drive real controllers on a server: id assignment and the index, then the entry gate on a lobby
 * with mock players. Only the gametest servers call these.
 */
public final class ApiGameTests {
    private static final BlockPos DUNGEON = new BlockPos(1, 1, 1);
    private static final BlockPos RAID = new BlockPos(3, 1, 1);
    private static final BlockPos ARENA = new BlockPos(5, 1, 1);

    /** Players the test gate refuses everywhere in {@code gt_gate_*} activities. */
    private static final Set<UUID> BLOCKED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger ACTIVITY_EVENTS = new AtomicInteger();
    private static boolean hooksRegistered;

    private ApiGameTests() {
    }

    private static void registerHooksOnce() {
        if (hooksRegistered) {
            return;
        }
        hooksRegistered = true;
        ArenasApi.addActivitiesListener(ACTIVITY_EVENTS::incrementAndGet);
        // Only touches this test's activities: "hard" is locked for everyone, BLOCKED players for all.
        ArenasApi.registerEntryGate((player, activity, difficulty) -> {
            if (!activity.getPath().startsWith("gt_gate_")) {
                return null;
            }
            if ("hard".equals(difficulty)) {
                return Component.literal("Lv 25+");
            }
            return BLOCKED.contains(player.getUUID()) ? Component.literal("blocked") : null;
        });
    }

    /** Runs {@code then} on the first tick {@code ready} holds, failing after {@code ticks} ticks. */
    private static void whenTrue(GameTestHelper helper, int ticks, java.util.function.BooleanSupplier ready, String what,
                                 Runnable then) {
        if (ready.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            helper.fail("timed out waiting: " + what);
        } else {
            helper.runAfterDelay(1, () -> whenTrue(helper, ticks - 1, ready, what, then));
        }
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** Ids are assigned and unique, can be changed, and admin edits show in ArenasApi.activities. */
    public static void activityIndex(GameTestHelper helper) {
        registerHooksOnce();
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.setBlock(RAID, BlockRegistry.RAID_CONTROLLER_BLOCK);
        helper.setBlock(ARENA, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        int eventsBefore = ACTIVITY_EVENTS.get();
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity dungeon = helper.getBlockEntity(DUNGEON);
            RaidControllerBlockEntity raid = helper.getBlockEntity(RAID);
            ArenaControllerBlockEntity arena = helper.getBlockEntity(ARENA);
            helper.assertTrue(dungeon.activityId() != null && raid.activityId() != null && arena.activityId() != null,
                "every controller got an id on its first tick");
            helper.assertTrue(Set.of(dungeon.activityId(), raid.activityId(), arena.activityId()).size() == 3, "ids are distinct");
            helper.assertTrue(ACTIVITY_EVENTS.get() > eventsBefore, "activities listeners fired");

            ArenasApi.Activity listed = ArenasApi.activity(server, dungeon.activityId()).orElse(null);
            helper.assertTrue(listed != null && listed.type() == ArenasApi.ActivityType.DUNGEON, "dungeon listed by id");
            helper.assertTrue(listed.difficulties().size() == 4, "all four tiers enabled by default");

            ResourceLocation oldId = dungeon.activityId();
            ResourceLocation newId = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_crypt_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(dungeon, newId.toString()) == null, "free id accepted");
            helper.assertTrue(ArenasApi.activity(server, oldId).isEmpty(), "old id dropped");
            helper.assertTrue(ArenasApi.activity(server, newId).isPresent(), "new id listed");
            helper.assertTrue(ActivityRegistry.changeId(raid, newId.toString()) != null, "taken id refused");
            helper.assertTrue(ActivityRegistry.changeId(raid, "Not Valid!") != null, "invalid id refused");

            TierConfig nightmare = dungeon.getTierConfigs().get(DifficultyTier.NIGHTMARE);
            dungeon.setTierConfig(DifficultyTier.NIGHTMARE, new TierConfig(nightmare.healthMultiplier(), nightmare.damageMultiplier(),
                nightmare.perPlayerLootTable(), nightmare.dungeonTimeSeconds(), false, nightmare.rewardCurrency(),
                nightmare.skillExperiencePerWin()));
            int eventsBeforeEdit = ACTIVITY_EVENTS.get();
            dungeon.setActivityName("Old Crypt");
            helper.assertTrue(ACTIVITY_EVENTS.get() > eventsBeforeEdit, "a rename fires the activities listener");
            ArenasApi.Activity updated = ArenasApi.activity(server, newId).orElseThrow();
            helper.assertTrue(updated.difficulties().equals(List.of(new ArenasApi.Difficulty("easy"),
                new ArenasApi.Difficulty("normal"), new ArenasApi.Difficulty("hard"))), "enabled tiers: " + updated.difficulties());
            helper.assertTrue(updated.name().getString().equals("Old Crypt"), "name reported");

            arena.setArenaName("The Pit");
            ArenasApi.Activity arenaActivity = ArenasApi.activity(server, arena.activityId()).orElseThrow();
            helper.assertTrue(arenaActivity.difficulties().isEmpty() && arenaActivity.name().getString().equals("The Pit"),
                "arena reports its name and no difficulties");

            helper.setBlock(DUNGEON, Blocks.AIR);
            helper.assertTrue(ArenasApi.activity(server, newId).isEmpty(), "breaking the controller removes the activity");
            helper.succeed();
        });
    }

    /**
     * A controller keeps its chunk force-loaded when its block entity is unloaded (every clean server
     * stop does that), and lets go of it only when the last controller in the chunk is broken.
     * Far from the test area, whose chunks the GameTest framework force-loads itself.
     */
    public static void controllerChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos near = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos first = new BlockPos(((near.getX() + 4096) & ~15) + 2, near.getY(), ((near.getZ() + 4096) & ~15) + 2);
        BlockPos second = first.offset(3, 0, 0);
        long chunk = new net.minecraft.world.level.ChunkPos(first).toLong();
        level.setBlockAndUpdate(first, BlockRegistry.DUNGEON_CONTROLLER_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(second, BlockRegistry.RAID_CONTROLLER_BLOCK.defaultBlockState());
        helper.assertTrue(!level.getForcedChunks().contains(chunk), "the far chunk starts unforced");

        net.ledok.arenas_ld.util.ControllerChunks.hold(level, first);
        helper.assertTrue(level.getForcedChunks().contains(chunk), "held");

        // Let both controllers tick (their first tick takes their own hold, as on a real server); the
        // far chunk becomes a ticking chunk once its neighbors are generated (registered with a
        // 1200-tick timeout for that).
        whenTrue(helper, 1150, () -> level.getBlockEntity(first) instanceof DungeonControllerBlockEntity c
                && c.activityId() != null && level.getBlockEntity(second) instanceof RaidControllerBlockEntity r
                && r.activityId() != null, "the far controllers tick", () -> {
            level.getBlockEntity(first).setRemoved(); // what a chunk unload does
            helper.assertTrue(level.getForcedChunks().contains(chunk), "an unloaded controller keeps its chunk");

            level.removeBlock(first, false);
            helper.assertTrue(level.getForcedChunks().contains(chunk), "kept while another controller shares the chunk");

            level.removeBlock(second, false);
            helper.assertTrue(!level.getForcedChunks().contains(chunk), "breaking the last controller lets go of it");
            helper.succeed();
        });
    }

    /**
     * {@code /arenasld instance check}: a small wired dungeon instance checks clean and names its
     * controller; a missing spawner and a blocked entrance are reported as errors.
     */
    public static void instanceCheck(GameTestHelper helper) {
        BlockPos dbsRel = new BlockPos(1, 1, 1);
        BlockPos roomRel = new BlockPos(3, 1, 1);
        BlockPos spawnerRel = new BlockPos(3, 1, 3);
        BlockPos doorRel = new BlockPos(5, 1, 1);
        BlockPos entranceRel = new BlockPos(1, 1, 4);
        BlockPos controllerRel = new BlockPos(6, 1, 6);
        helper.setBlock(dbsRel, BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK);
        helper.setBlock(roomRel, BlockRegistry.ROOM_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.MOB_SPAWNER_BLOCK);
        helper.setBlock(doorRel, BlockRegistry.PHASE_BLOCK);
        helper.setBlock(controllerRel, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        var level = helper.getLevel();
        BlockPos dbsPos = helper.absolutePos(dbsRel);

        net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity dbs = helper.getBlockEntity(dbsRel);
        net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity room = helper.getBlockEntity(roomRel);
        helper.assertTrue(dbs.addRoom(helper.absolutePos(roomRel)), "room linked");
        helper.assertTrue(room.toggleSpawner(helper.absolutePos(spawnerRel)), "spawner linked");
        helper.assertTrue(room.toggleDoor(helper.absolutePos(doorRel)), "door linked");
        room.addRespawnPointOffset(new BlockPos(0, 0, 4));
        dbs.setEntrancePosition(helper.absolutePos(entranceRel), level.dimension());

        helper.runAfterDelay(3, () -> {
            DungeonControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            helper.assertTrue(controller.activityId() != null, "the controller has an activity id");
            helper.assertTrue(net.ledok.arenas_ld.activity.InstanceCheck.check(level, helper.absolutePos(new BlockPos(1, 1, 6))) == null,
                "air is not an instance");

            var unlinked = net.ledok.arenas_ld.activity.InstanceCheck.check(level, dbsPos);
            helper.assertTrue(unlinked != null && unlinked.problems().isEmpty(),
                "a wired instance has no problems: " + (unlinked == null ? "null" : unlinked.problems()));
            helper.assertTrue(unlinked.linkedTo().isEmpty(), "not linked to a controller yet");

            controller.addInstance(dbsPos, level.dimension());
            var linked = net.ledok.arenas_ld.activity.InstanceCheck.check(level, dbsPos);
            helper.assertTrue(linked.linkedTo().equals(List.of(controller.activityId())), "names its controller");

            helper.setBlock(spawnerRel, Blocks.AIR);
            helper.setBlock(entranceRel, Blocks.STONE);
            var broken = net.ledok.arenas_ld.activity.InstanceCheck.check(level, dbsPos);
            helper.assertTrue(broken.errors() == 2,
                "a missing spawner and a blocked entrance are errors: " + broken.problems());
            helper.succeed();
        });
    }

    /**
     * A copied controller (its saved data cloned onto another block, as structure tools do) gets its
     * own id and keeps the settings, but not the original's instances or leaderboard.
     */
    public static void copiedController(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            DungeonControllerBlockEntity original = helper.getBlockEntity(DUNGEON);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_copy_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(original, id.toString()) == null, "test id set");
            original.setActivityName("Crypt");
            original.addInstance(helper.absolutePos(new BlockPos(6, 1, 6)), helper.getLevel().dimension());
            original.addLeaderboardEntry(DifficultyTier.NORMAL,
                new net.ledok.arenas_ld.dungeon.run.LeaderboardEntry("Steve", 120, 0L));

            var registries = helper.getLevel().registryAccess();
            net.minecraft.nbt.CompoundTag saved = original.saveWithoutMetadata(registries);
            helper.setBlock(RAID, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
            DungeonControllerBlockEntity copy = helper.getBlockEntity(RAID);
            copy.loadWithComponents(saved, registries);
            helper.assertTrue(id.equals(copy.activityId()), "the copy starts with the original's id");

            helper.runAfterDelay(3, () -> {
                helper.assertTrue(copy.activityId() != null && !copy.activityId().equals(id), "the copy got its own id");
                helper.assertTrue(copy.getDungeonName().equals("Crypt"),
                    "the copy keeps its settings");
                helper.assertTrue(copy.getInstances().isEmpty(), "the copy has none of the original's instances");
                helper.assertTrue(copy.getLeaderboards().get(DifficultyTier.NORMAL).isEmpty(), "the copy has no leaderboard");
                helper.assertTrue(id.equals(original.activityId()) && original.getInstances().size() == 1
                    && !original.getLeaderboards().get(DifficultyTier.NORMAL).isEmpty(), "the original is untouched");
                helper.succeed();
            });
        });
    }

    /**
     * Run teams: a player joins the run's no-friendly-fire team and gets their prior team back, but
     * a player who was on the mobs' team never gets it back, and is taken off it at login.
     */
    public static void runTeams(GameTestHelper helper) {
        var level = helper.getLevel();
        var scoreboard = level.getScoreboard();
        // Mock players all share one name, and teams are keyed by name: one player, two passes.
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String name = player.getScoreboardName();
        var priorTeam = scoreboard.addPlayerTeam("gt_prior_" + suffix());
        var mobTeam = scoreboard.getPlayerTeam("arenas_ld") != null
            ? scoreboard.getPlayerTeam("arenas_ld") : scoreboard.addPlayerTeam("arenas_ld");
        try {
            // Pass 1: an ordinary prior team is given back.
            String runTeam = "ald_t_" + suffix();
            scoreboard.addPlayerToTeam(name, priorTeam);
            net.ledok.arenas_ld.util.RunPartyTeams.add(level, runTeam, player);
            var team = scoreboard.getPlayerTeam(runTeam);
            helper.assertTrue(team != null && !team.isAllowFriendlyFire(), "run team has friendly fire off");
            helper.assertTrue(scoreboard.getPlayersTeam(name) == team, "player is on the run team");
            net.ledok.arenas_ld.util.RunPartyTeams.teardown(level, runTeam, List.of(name));
            helper.assertTrue(scoreboard.getPlayerTeam(runTeam) == null, "run team deleted");
            helper.assertTrue(scoreboard.getPlayersTeam(name) == priorTeam, "prior team given back");

            // Pass 2: the mobs' team is never given back.
            String secondRun = "ald_t_" + suffix();
            scoreboard.addPlayerToTeam(name, mobTeam);
            net.ledok.arenas_ld.util.RunPartyTeams.add(level, secondRun, player);
            net.ledok.arenas_ld.util.RunPartyTeams.teardown(level, secondRun, List.of(name));
            helper.assertTrue(scoreboard.getPlayersTeam(name) == null, "the mob team is never given back");

            // Login takes a player off the mob team.
            scoreboard.addPlayerToTeam(name, mobTeam);
            net.ledok.arenas_ld.util.RunPartyTeams.leaveMobTeamOnLogin(player);
            helper.assertTrue(scoreboard.getPlayersTeam(name) == null, "login takes a player off the mob team");
        } finally {
            scoreboard.removePlayerTeam(priorTeam);
            if (scoreboard.getPlayersTeam(name) != null) {
                scoreboard.removePlayerFromTeam(name);
            }
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** The gate guards picking a difficulty, joining and starting; lobby data carries the locks. */
    public static void entryGate(GameTestHelper helper) {
        registerHooksOnce();
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_gate_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            ServerPlayer owner = helper.makeMockServerPlayerInLevel();
            ServerPlayer member = helper.makeMockServerPlayerInLevel();
            try {
                Optional<UUID> lobbyId = controller.createLobby(owner);
                helper.assertTrue(lobbyId.isPresent(), "owner may create a lobby");
                helper.assertFalse(controller.setLobbyTier(owner, DifficultyTier.HARD), "locked tier refused");
                helper.assertTrue(controller.setLobbyTier(owner, DifficultyTier.EASY), "open tier accepted");
                helper.assertTrue(controller.getScreenOpeningData(owner).lockedTiers().keySet().equals(Set.of(DifficultyTier.HARD)),
                    "lobby data locks hard for the viewer");

                BLOCKED.add(member.getUUID());
                helper.assertFalse(controller.joinLobby(member, lobbyId.get()), "gated player can't join");
                helper.assertFalse(ArenasApi.openLobbies(member, id), "gated player can't open the lobby screen");
                BLOCKED.remove(member.getUUID());
                helper.assertTrue(controller.joinLobby(member, lobbyId.get()), "allowed player joins");

                controller.toggleReady(owner);
                controller.toggleReady(member);
                BLOCKED.add(member.getUUID());
                helper.assertTrue(controller.startRun(owner).isEmpty(), "start refused while a member fails the gate");
                helper.assertFalse(controller.getQueuedLobbyIds().contains(lobbyId.get()), "refused start doesn't queue");
                BLOCKED.remove(member.getUUID());
                helper.assertFalse(ArenasApi.openLobbies(owner, ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_missing")),
                    "unknown id");
            } finally {
                BLOCKED.remove(member.getUUID());
                server.getPlayerList().remove(owner);
                server.getPlayerList().remove(member);
            }
            helper.succeed();
        });
    }
}
