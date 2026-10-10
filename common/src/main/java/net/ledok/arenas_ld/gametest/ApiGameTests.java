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
