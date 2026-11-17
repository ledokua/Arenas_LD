package net.ledok.arenas_ld.gametest;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.activity.ActivityHost;
import net.ledok.arenas_ld.activity.ActivityRegistry;
import net.ledok.arenas_ld.activity.AdminMenuBase;
import net.ledok.arenas_ld.activity.AdminScreens;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.DownedSpectators;
import net.ledok.arenas_ld.util.RunItemRestrictions;
import net.ledok.arenas_ld.util.RunLeftovers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * GameTest bodies for the public API, shared by the Fabric and NeoForge gametest holders. They
 * drive real controllers on a server: id assignment and the index, the entry gate on a lobby with
 * mock players, and the admin calls (status, rename, teleport, admin screens, a real arena run
 * ended by staff). Only the gametest servers call these.
 */
public final class ApiGameTests {
    private static final BlockPos DUNGEON = new BlockPos(1, 1, 1);
    private static final BlockPos RAID = new BlockPos(3, 1, 1);
    private static final BlockPos ARENA = new BlockPos(5, 1, 1);

    /** Players the test gate refuses everywhere in {@code gt_gate_*} activities. */
    private static final Set<UUID> BLOCKED = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger ACTIVITY_EVENTS = new AtomicInteger();
    /** Every run result reported while the tests run; tests pick theirs by activity id. */
    private static final List<ArenasApi.RunResult> RUN_RESULTS = new CopyOnWriteArrayList<>();
    private static final ResourceLocation MISSING = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_missing");
    private static boolean hooksRegistered;

    private ApiGameTests() {
    }

    private static void registerHooksOnce() {
        if (hooksRegistered) {
            return;
        }
        hooksRegistered = true;
        ArenasApi.addActivitiesListener(ACTIVITY_EVENTS::incrementAndGet);
        ArenasApi.addRunListener(RUN_RESULTS::add);
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
        // Every tier of a gate test activity "needs" Lv 5+, met by everyone.
        ArenasApi.registerEntryRequirement((player, activity, difficulty) ->
            activity.getPath().startsWith("gt_gate_") && difficulty != null ? Component.literal("Lv 5+") : null);
    }

    /** Runs {@code then} on the first tick {@code ready} holds, failing after {@code ticks} ticks. */
    private static void whenTrue(GameTestHelper helper, int ticks, java.util.function.BooleanSupplier ready, String what,
                                 Runnable then) {
        whenTrue(helper, ticks, ready, () -> what, then);
    }

    private static void whenTrue(GameTestHelper helper, int ticks, java.util.function.BooleanSupplier ready,
                                 Supplier<String> what, Runnable then) {
        if (ready.getAsBoolean()) {
            then.run();
        } else if (ticks <= 0) {
            helper.fail("timed out waiting: " + what.get());
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
     * Minions: what a run mob spawns (owned by it, or while it acts) joins its team and scaling and
     * drops nothing; a mob owned by someone else is left alone; a dying run mob spawns nothing; and
     * the minions vanish when it dies.
     */
    public static void runMinions(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        var scoreboard = level.getScoreboard();
        String teamName = "ald_gt_" + suffix();
        var team = scoreboard.addPlayerTeam(teamName);
        team.setAllowFriendlyFire(false);

        var boss = net.minecraft.world.entity.EntityType.HUSK.create(level);
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(boss);
        net.ledok.arenas_ld.util.RunMinions.recordScale(boss, 2.0, 1.5);
        scoreboard.addPlayerToTeam(boss.getScoreboardName(), team);
        boss.setNoAi(true);
        level.addFreshEntity(boss);

        var vex = net.minecraft.world.entity.EntityType.VEX.create(level);
        vex.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5);
        vex.setOwner(boss);
        vex.setNoAi(true);
        level.addFreshEntity(vex);
        helper.assertTrue(net.ledok.arenas_ld.util.RunMinions.isMinion(vex), "an owned mob is a minion");
        helper.assertTrue(vex.getTags().contains(net.ledok.arenas_ld.util.EntityEquipmentHelper.RUN_MOB_TAG)
            && vex.getTags().contains(net.ledok.arenas_ld.util.EntityEquipmentHelper.NO_NATURAL_LOOT_TAG), "a run mob without loot");
        helper.assertTrue(vex.getTeam() == team, "on its summoner's team");
        helper.assertTrue(Math.abs(vex.getMaxHealth() - 28.0f) < 0.01f && Math.abs(vex.getHealth() - 28.0f) < 0.01f,
            "health scaled like its summoner: " + vex.getMaxHealth());

        var fish = net.minecraft.world.entity.EntityType.SILVERFISH.create(level);
        fish.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
        fish.setNoAi(true);
        net.ledok.arenas_ld.util.RunMinions.actingAs(boss, () -> level.addFreshEntity(fish));
        double damage = fish.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        helper.assertTrue(net.ledok.arenas_ld.util.RunMinions.isMinion(fish) && Math.abs(damage - 1.5) < 0.01,
            "spawned while the run mob acts: a minion with its damage scaling (" + damage + ")");

        var stranger = net.minecraft.world.entity.EntityType.HUSK.create(level);
        stranger.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 2.5);
        stranger.setNoAi(true);
        level.addFreshEntity(stranger);
        var theirVex = net.minecraft.world.entity.EntityType.VEX.create(level);
        theirVex.moveTo(at.getX() + 0.5, at.getY() + 1, at.getZ() + 2.5);
        theirVex.setOwner(stranger);
        theirVex.setNoAi(true);
        net.ledok.arenas_ld.util.RunMinions.actingAs(boss, () -> level.addFreshEntity(theirVex));
        helper.assertTrue(!net.ledok.arenas_ld.util.RunMinions.isMinion(theirVex), "someone else's mob is left alone");

        // A spawner with "Scale minions" off: its boss's minions are adopted but not scaled.
        BlockPos spawnerRel = new BlockPos(1, 1, 5);
        helper.setBlock(spawnerRel, net.ledok.arenas_ld.registry.BlockRegistry.MOB_SPAWNER_BLOCK);
        var spawner = (net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity) helper.getBlockEntity(spawnerRel);
        spawner.setScalesMinions(false);
        var saved = spawner.saveWithoutMetadata(level.registryAccess());
        helper.assertTrue(!net.ledok.arenas_ld.util.MinionScalingHost.load(saved)
            && net.ledok.arenas_ld.util.MinionScalingHost.load(new net.minecraft.nbt.CompoundTag()),
            "the switch is saved, and older spawners count as on");
        var selfScaling = net.minecraft.world.entity.EntityType.HUSK.create(level);
        selfScaling.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(selfScaling);
        net.ledok.arenas_ld.util.RunMinions.recordScale(selfScaling, spawner, 2.0, 1.5);
        selfScaling.setNoAi(true);
        level.addFreshEntity(selfScaling);
        var ownVex = net.minecraft.world.entity.EntityType.VEX.create(level);
        ownVex.moveTo(at.getX() + 2.5, at.getY() + 1, at.getZ() + 0.5);
        ownVex.setOwner(selfScaling);
        ownVex.setNoAi(true);
        level.addFreshEntity(ownVex);
        helper.assertTrue(net.ledok.arenas_ld.util.RunMinions.isMinion(ownVex) && Math.abs(ownVex.getMaxHealth() - 14.0f) < 0.01f,
            "scaling off: still a minion, health unscaled (" + ownVex.getMaxHealth() + ")");

        // Outside a run, spawnAsMinions just runs the code.
        var plain = net.minecraft.world.entity.EntityType.HUSK.create(level);
        plain.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 2.5);
        plain.setNoAi(true);
        level.addFreshEntity(plain);
        var plainAdd = net.minecraft.world.entity.EntityType.SILVERFISH.create(level);
        plainAdd.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 3.5);
        plainAdd.setNoAi(true);
        net.ledok.arenas_ld.api.ArenasApi.spawnAsMinions(plain, () -> level.addFreshEntity(plainAdd));
        helper.assertTrue(!net.ledok.arenas_ld.util.RunMinions.isMinion(plainAdd) && !plainAdd.isRemoved()
                && !plainAdd.getTags().contains(net.ledok.arenas_ld.util.EntityEquipmentHelper.RUN_MOB_TAG),
            "outside a run, spawnAsMinions adopts nothing");

        // A summoner discarded by run cleanup takes its minions at once (they may never tick again).
        var second = net.minecraft.world.entity.EntityType.HUSK.create(level);
        second.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 4.5);
        net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(second);
        second.setNoAi(true);
        level.addFreshEntity(second);
        var secondFish = net.minecraft.world.entity.EntityType.SILVERFISH.create(level);
        secondFish.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 4.5);
        secondFish.setNoAi(true);
        net.ledok.arenas_ld.api.ArenasApi.spawnAsMinions(second, () -> level.addFreshEntity(secondFish));
        helper.assertTrue(net.ledok.arenas_ld.util.RunMinions.isMinion(secondFish), "spawnAsMinions adopts for a run mob");
        second.discard();
        helper.assertTrue(secondFish.isRemoved(), "a discarded summoner takes its minions at once");

        boss.kill();
        var late = net.minecraft.world.entity.EntityType.SILVERFISH.create(level);
        late.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 1.5);
        boolean added = net.ledok.arenas_ld.util.RunMinions.actingAs(boss, () -> level.addFreshEntity(late));
        helper.assertTrue(!added, "a dying run mob spawns nothing");

        whenTrue(helper, 200, () -> vex.isRemoved() && fish.isRemoved(), "the minions vanish with their summoner", () -> {
            helper.assertTrue(!theirVex.isRemoved() && !plainAdd.isRemoved(), "mobs that aren't minions stay: vex "
                + theirVex.getRemovalReason() + ", silverfish " + plainAdd.getRemovalReason() + " " + plainAdd.getTags());
            theirVex.discard();
            stranger.discard();
            plain.discard();
            plainAdd.discard();
            scoreboard.removePlayerTeam(team);
            helper.succeed();
        });
    }

    /** A vanilla evoker run boss: the vexes its spell summons become its minions and vanish with it. */
    public static void evokerMinions(GameTestHelper helper) {
        bossMinions(helper, net.minecraft.world.entity.EntityType.EVOKER, 1700);
    }

    /**
     * A Bosses of Mass Destruction lich run boss: the phantoms its minion attack summons (from its
     * own scheduled events) become its minions and vanish with it. Passes at once without BOMD.
     */
    public static void lichMinions(GameTestHelper helper) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
            .getOptional(ResourceLocation.parse("bosses_of_mass_destruction:lich"));
        if (type.isEmpty()) {
            helper.succeed(); // BOMD not installed
            return;
        }
        bossMinions(helper, type.get(), 1200, (boss, player) -> {
            // Its attack cycle is random and needs a clear view; start its minion attack directly
            // (BOMD's own MinionAction: it schedules the summons on the lich's tick events).
            ((net.minecraft.world.entity.Mob) boss).setTarget(player);
            try {
                var field = boss.getClass().getDeclaredField("minionAction");
                field.setAccessible(true);
                Object action = field.get(boss);
                action.getClass().getMethod("perform").invoke(action);
            } catch (ReflectiveOperationException e) {
                helper.fail("BOMD's lich has no minionAction to start: " + e);
            }
        });
    }

    /**
     * UI rework D2: the owner may change the settings with members in the lobby. A change to the run (tier, modifiers,
     * hardcore) un-readies everyone but the owner; a visibility change doesn't.
     */
    public static void lobbySettingsChange(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ServerPlayer owner = helper.makeMockServerPlayerInLevel();
            ServerPlayer member = helper.makeMockServerPlayerInLevel();
            try {
                UUID lobbyId = controller.createLobby(owner).orElseThrow();
                helper.assertTrue(controller.joinLobby(member, lobbyId), "the member joins");
                controller.toggleReady(owner);
                controller.toggleReady(member);
                Supplier<net.ledok.arenas_ld.dungeon.lobby.Lobby> lobby = () -> controller.getLobbies().stream()
                    .filter(l -> l.lobbyId().equals(lobbyId)).findFirst().orElseThrow();
                helper.assertTrue(lobby.get().readyMembers().size() == 2, "both ready");
                controller.setLobbyVisibility(owner, net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.FRIENDS);
                helper.assertTrue(lobby.get().readyMembers().size() == 2, "a visibility change keeps everyone ready");
                helper.assertTrue(controller.setLobbyTier(owner, DifficultyTier.EASY), "the owner changes the tier with a member in");
                helper.assertTrue(lobby.get().readyMembers().equals(Set.of(owner.getUUID())),
                    "a tier change un-readies everyone but the owner: " + lobby.get().readyMembers());
            } finally {
                server.getPlayerList().remove(owner);
                server.getPlayerList().remove(member);
            }
            helper.succeed();
        });
    }

    /**
     * 4.33.0, lobbies from a hub: createLobby with settings (friends-only, hardcore, min level 10), the minimum level
     * checked through a level provider on asking to join, updateLobby, refusals, activityInfo and possibleDrops.
     */
    public static void hubLobbyApi(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_hub_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            ServerPlayer owner = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 2)));
            ServerPlayer low = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 2)));
            moddedClient.accept(owner);
            moddedClient.accept(low);
            Supplier<ArenasApi.OpenLobby> mine = () -> ArenasApi.listOpenLobbies(server).stream()
                .filter(l -> l.activity().equals(id)).findFirst().orElse(null);
            try {
                var info = ArenasApi.activityInfo(server, id).orElseThrow();
                helper.assertTrue(!info.difficulties().isEmpty() && !info.modifiers().isEmpty() && info.maxMembers() > 0,
                    "activityInfo has tiers, modifiers and a party size: " + info.difficulties());
                String tier = info.difficulties().get(0);
                helper.assertTrue(ArenasApi.createLobby(owner, id, new ArenasApi.LobbySettings(tier, false,
                    List.of("no_such_modifier"), ArenasApi.LobbyVisibility.PUBLIC, 0), false) != null,
                    "an unknown modifier is refused before anything is made");
                helper.assertTrue(mine.get() == null, "no lobby was made");
                helper.assertTrue(ArenasApi.createLobby(owner, id, new ArenasApi.LobbySettings(tier, true, List.of(),
                    ArenasApi.LobbyVisibility.FRIENDS, 10), false) == null, "createLobby with settings");
                ArenasApi.OpenLobby made = mine.get();
                helper.assertTrue(made != null && made.hardcore() && made.minLevel() == 10
                    && made.access() == ArenasApi.LobbyAccess.REQUEST && made.difficulty().equals(tier),
                    "the lobby has the settings: " + made);

                ArenasApi.setLevelProvider(player -> player == low ? 5 : 50);
                helper.assertTrue(ArenasApi.joinOpenLobby(low, id, made.lobbyId()) != null, "level 5 can't ask to join a Lv 10 lobby");
                ArenasApi.setLevelProvider(player -> 50);
                helper.assertTrue(ArenasApi.joinOpenLobby(low, id, made.lobbyId()) == null, "level 50 can ask");

                helper.assertTrue(ArenasApi.updateLobby(owner, new ArenasApi.LobbySettings(tier, false, List.of(),
                    ArenasApi.LobbyVisibility.PUBLIC, 0)) == null, "updateLobby");
                ArenasApi.OpenLobby updated = mine.get();
                helper.assertTrue(!updated.hardcore() && updated.minLevel() == 0 && updated.access() == ArenasApi.LobbyAccess.JOIN,
                    "the update took: " + updated);
                helper.assertTrue(ArenasApi.updateLobby(low, new ArenasApi.LobbySettings(tier, false, List.of(),
                    ArenasApi.LobbyVisibility.PUBLIC, 0)) != null, "a non-owner can't update");
                DifficultyTier dropTier = DifficultyTier.valueOf(tier.toUpperCase(java.util.Locale.ROOT));
                controller.setTierConfig(dropTier, controller.getTierConfigs().get(dropTier)
                    .withRewardCommands(List.of("loot give @p loot minecraft:chests/simple_dungeon",
                        "loot give @p loot minecraft:gameplay/fishing")));
                var drops = ArenasApi.possibleDrops(server, id, tier);
                helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.SADDLE)),
                    "possibleDrops reads the tier's reward-command loot table (a dungeon chest has saddles): " + drops.size());
                helper.assertTrue(drops.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.COD)),
                    "and follows nested tables (fishing → fish → cod)");
                helper.assertTrue(ArenasApi.possibleDropsCount(server, id, tier) >= drops.size() && !drops.isEmpty(),
                    "possibleDropsCount counts at least what's listed: " + ArenasApi.possibleDropsCount(server, id, tier));
                helper.assertTrue(!ArenasApi.possibleDrops(server, id, null).isEmpty(), "and with no tier: all tiers");
                ArenasApi.updateLobby(owner, new ArenasApi.LobbySettings(tier, true, List.of(),
                    ArenasApi.LobbyVisibility.PRIVATE, 7));
                var read = ArenasApi.lobbySettings(owner).orElseThrow();
                helper.assertTrue(read.hardcore() && read.minLevel() == 7 && read.visibility() == ArenasApi.LobbyVisibility.PRIVATE
                    && read.difficulty().equals(tier), "lobbySettings reads a private lobby back: " + read);
                helper.assertTrue(ArenasApi.lobbySettings(low).isEmpty(), "a member who doesn't own it gets nothing");
            } finally {
                ArenasApi.setLevelProvider(null);
                server.getPlayerList().remove(owner);
                server.getPlayerList().remove(low);
            }
            helper.succeed();
        });
    }

    /**
     * 4.31.0, open lobbies server-wide: a forming public lobby is listed (JOIN), a friends-only one too (REQUEST), a
     * private one isn't; another player joins it from anywhere through {@code joinOpenLobby}; the revision moves.
     */
    public static void openLobbiesList(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_open_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            ServerPlayer owner = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 2)));
            ServerPlayer joiner = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 2)));
            moddedClient.accept(owner);
            moddedClient.accept(joiner);
            Runnable cleanup = () -> {
                server.getPlayerList().remove(owner);
                server.getPlayerList().remove(joiner);
            };
            AtomicInteger changes = new AtomicInteger();
            ArenasApi.addOpenLobbiesListener(changes::incrementAndGet);
            Supplier<List<ArenasApi.OpenLobby>> mine = () -> ArenasApi.listOpenLobbies(server).stream()
                .filter(l -> l.activity().equals(id)).toList();
            helper.runAfterDelay(25, () -> { // past the baseline check
                long before = ArenasApi.openLobbiesRevision();
                UUID lobbyId;
                try {
                    lobbyId = controller.createLobby(owner).orElseThrow();
                    List<ArenasApi.OpenLobby> listed = mine.get();
                    helper.assertTrue(listed.size() == 1 && listed.get(0).lobbyId().equals(lobbyId)
                        && listed.get(0).access() == ArenasApi.LobbyAccess.JOIN && listed.get(0).members().size() == 1
                        && listed.get(0).owner().equals(owner.getUUID()), "a public lobby is listed: " + listed);
                    helper.assertTrue(ArenasApi.joinOpenLobby(joiner, id, lobbyId) == null, "another player joins from anywhere");
                    helper.assertTrue(mine.get().get(0).members().size() == 2, "the list shows both members");
                } catch (RuntimeException e) {
                    cleanup.run();
                    throw e;
                }
                whenTrue(helper, 60, () -> ArenasApi.openLobbiesRevision() > before && changes.get() > 0,
                    "the revision goes up and the listeners hear it", () -> {
                    try {
                        controller.setLobbyVisibility(owner, net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.FRIENDS);
                        helper.assertTrue(mine.get().get(0).access() == ArenasApi.LobbyAccess.REQUEST,
                            "a friends-only lobby is REQUEST");
                        controller.setLobbyVisibility(owner, net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PRIVATE);
                        helper.assertTrue(mine.get().isEmpty(), "a private lobby isn't listed");
                        helper.assertTrue(ArenasApi.joinOpenLobby(joiner, id, UUID.randomUUID()) != null,
                            "an unknown lobby is refused");
                    } finally {
                        cleanup.run();
                    }
                    helper.succeed();
                });
            });
        });
    }

    /**
     * 4.30.0, summon extra data and split parts ({@link net.ledok.arenas_ld.util.RunSplits}):
     * <ol>
     *   <li>the summon's SNBT reaches the mob ({@code {Size:3}} is a size-4 slime); bad SNBT is reported;</li>
     *   <li>a run slime's parts are run mobs (not minions) with the parent's tags and size-scaled health and attack
     *       (50% split strength here), and its run takes them;</li>
     *   <li>a room keeps the dying slime until it has split; the group's bar only goes down.</li>
     * </ol>
     */
    public static void summonSplits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos roomRel = new BlockPos(1, 1, 1);
        helper.setBlock(roomRel, BlockRegistry.ROOM_CONTROLLER_BLOCK);
        helper.assertTrue(net.ledok.arenas_ld.util.RunSplits.nbtProblem("{Size:3") != null, "bad SNBT is reported");
        helper.assertTrue(net.ledok.arenas_ld.util.RunSplits.nbtProblem(" ") == null, "blank is fine");

        var slime = net.minecraft.world.entity.EntityType.SLIME.create(level);
        net.ledok.arenas_ld.util.RunSplits.applyNbt(slime, "{Size:3, Pos:[0d,500d,0d]}");
        helper.assertTrue(slime.getSize() == 4, "{Size:3} makes a size-4 slime: " + slime.getSize());
        slime.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(400);
        slime.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(20);
        slime.setHealth(400);
        BlockPos at = helper.absolutePos(new BlockPos(4, 1, 4));
        slime.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        helper.assertTrue(slime.getY() < 100, "Pos in the extra data is ignored");
        net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(slime);
        net.ledok.arenas_ld.stats.RunStats.markBoss(slime);
        net.ledok.arenas_ld.util.RunSplits.markRoot(slime, 50);
        slime.setNoAi(true);
        level.addFreshEntity(slime);
        UUID parentId = slime.getUUID();

        List<net.minecraft.world.entity.LivingEntity> parts = new CopyOnWriteArrayList<>();
        List<UUID> parents = new CopyOnWriteArrayList<>();
        net.ledok.arenas_ld.util.RunSplits.PartListener listener = (lvl, parent, part) -> {
            parents.add(parent);
            parts.add(part);
        };
        net.ledok.arenas_ld.util.RunSplits.LISTENERS.add(listener);

        helper.runAfterDelay(2, () -> {
            net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity room = helper.getBlockEntity(roomRel);
            room.trackBossMob(parentId);
            double[] before = net.ledok.arenas_ld.util.RunSplits.health(List.of(slime));
            helper.assertTrue(Math.abs(before[0] - before[1]) < 0.5 && before[1] > 400,
                "the tree's total counts the parts to come: " + before[0] + "/" + before[1]);
            slime.hurt(level.damageSources().genericKill(), 10000);
            helper.runAfterDelay(5, () -> {
                room.refreshAliveMobs(level);
                helper.assertTrue(room.getAliveMobs().contains(parentId), "the room keeps the dying slime until it splits");
            });
            whenTrue(helper, 60, () -> !parts.isEmpty() && slime.isRemoved(), "the slime splits", () -> {
                try {
                    helper.assertTrue(parents.stream().allMatch(parentId::equals), "the parts' parent is the slime");
                    for (var part : parts) {
                        var s = (net.minecraft.world.entity.monster.Slime) part;
                        helper.assertTrue(s.getSize() == 2, "a part is size 2: " + s.getSize());
                        helper.assertTrue(part.getTags().contains(net.ledok.arenas_ld.util.EntityEquipmentHelper.RUN_MOB_TAG)
                            && part.getTags().contains(net.ledok.arenas_ld.util.RunSplits.PART_TAG)
                            && !net.ledok.arenas_ld.util.RunMinions.isMinion(part), "a part is a run mob, not a minion");
                        // 400 × (2/4)² × 50% = 50; 20 × (2/4) × 50% = 5
                        helper.assertTrue(Math.abs(part.getMaxHealth() - 50) < 0.01, "part health: " + part.getMaxHealth());
                        helper.assertTrue(Math.abs(part.getAttributeValue(
                            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - 5) < 0.01,
                            "part attack: " + part.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
                        helper.assertTrue(part.getTags().contains(net.ledok.arenas_ld.stats.RunStats.BOSS_TAG), "a boss's part is a boss");
                        room.trackBossMob(part.getUUID());
                    }
                    double[] after = net.ledok.arenas_ld.util.RunSplits.health(parts);
                    helper.assertTrue(Math.abs(after[1] - before[1]) < 0.5 && after[0] < before[0] && after[0] > 0,
                        "one bar: same total, lower now: " + after[0] + "/" + after[1]);
                    room.refreshAliveMobs(level);
                    helper.assertTrue(!room.getAliveMobs().contains(parentId) && room.getAliveMobs().size() == parts.size(),
                        "the room holds the parts now");
                } finally {
                    net.ledok.arenas_ld.util.RunSplits.LISTENERS.remove(listener);
                    parts.forEach(net.minecraft.world.entity.Entity::discard);
                }
                helper.succeed();
            });
        });
    }

    /** Spawns {@code bossType} as a run boss next to a survival player and waits for its minions. */
    private static void bossMinions(GameTestHelper helper, net.minecraft.world.entity.EntityType<?> bossType, int ticks) {
        bossMinions(helper, bossType, ticks, (boss, player) -> {
        });
    }

    private static void bossMinions(GameTestHelper helper, net.minecraft.world.entity.EntityType<?> bossType, int ticks,
                                    java.util.function.BiConsumer<net.minecraft.world.entity.LivingEntity, ServerPlayer> start) {
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        var scoreboard = level.getScoreboard();
        var team = scoreboard.addPlayerTeam("ald_gt_" + suffix());
        team.setAllowFriendlyFire(false);

        var boss = (net.minecraft.world.entity.LivingEntity) bossType.create(level);
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(boss);
        net.ledok.arenas_ld.util.RunMinions.recordScale(boss, 2.0, 1.0);
        scoreboard.addPlayerToTeam(boss.getScoreboardName(), team);
        level.addFreshEntity(boss);
        ServerPlayer player = survivalPlayer(helper, at.offset(4, 0, 4)); // inside the test area's barrier walls
        start.accept(boss, player);

        UUID bossId = boss.getUUID();
        net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(at).inflate(48);
        Supplier<List<net.minecraft.world.entity.Mob>> minions = () -> level.getEntitiesOfClass(
            net.minecraft.world.entity.Mob.class, area, mob -> net.ledok.arenas_ld.util.RunMinions.isMinionOf(mob, bossId));
        whenTrue(helper, ticks, () -> !minions.get().isEmpty(), () -> boss.getType() + " summons minions (boss alive="
                + boss.isAlive() + " removed=" + boss.isRemoved() + " target="
                + (boss instanceof net.minecraft.world.entity.Mob m ? m.getTarget() : null) + " at " + boss.blockPosition()
                + "; player " + player.blockPosition() + " creative=" + player.isCreative() + " alive=" + player.isAlive()
                + "; difficulty " + level.getDifficulty() + "; vanilla targeting finds "
                + level.getNearestPlayer(net.minecraft.world.entity.ai.targeting.TargetingConditions.forCombat().range(32),
                    boss) + "; seeAsEnemy=" + player.canBeSeenAsEnemy() + " los=" + boss.hasLineOfSight(player)
                + " inLevelPlayers=" + level.players().contains(player) + ")", () -> {
            List<net.minecraft.world.entity.Mob> summoned = minions.get();
            for (var mob : summoned) {
                helper.assertTrue(mob.getTeam() == team, mob.getType() + " is on its summoner's team");
                helper.assertTrue(mob.getTags().contains(net.ledok.arenas_ld.util.EntityEquipmentHelper.NO_NATURAL_LOOT_TAG),
                    mob.getType() + " drops no loot");
                var health = mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                double defaultHealth = net.minecraft.world.entity.ai.attributes.DefaultAttributes
                    .getSupplier((net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.LivingEntity>) mob.getType())
                    .getBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
                helper.assertTrue(health != null && Math.abs(health.getBaseValue() - defaultHealth * 2.0) < 0.01,
                    mob.getType() + " health is scaled like its summoner: " + (health == null ? "-" : health.getBaseValue()));
            }
            boss.kill();
            whenTrue(helper, 200, () -> summoned.stream().allMatch(net.minecraft.world.entity.Entity::isRemoved),
                "the minions vanish with their summoner", () -> {
                    level.getServer().getPlayerList().remove(player);
                    scoreboard.removePlayerTeam(team);
                    helper.succeed();
                });
        });
    }

    /**
     * 4.37.0: the run event log. A hit taken with HP before/after, a hit inside the half-second invulnerability only
     * counted, effects added once (a refresh counted) and removed, a swap, a Spell Engine passive pinned onto the hit
     * it fired in (Cheat Death: the hit refused), and the cap.
     */
    public static void runEventLog(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 2)));
        var difficulty = level.getDifficulty();
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL, true); // peaceful zeroes mob hits on players
        try {
            player.removeAllEffects(); // the test player's Resistance V would stop every hit
            player.setHealth(20);
            try { // a player who just joined is spawn-protected for 3 s (dev names: the field is private)
                var protection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                protection.setAccessible(true);
                protection.setInt(player, 0);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            var participants = new java.util.LinkedHashMap<UUID, net.ledok.arenas_ld.dungeon.run.RunParticipant>();
            participants.put(player.getUUID(), new net.ledok.arenas_ld.dungeon.run.RunParticipant(player.getUUID(),
                player.getScoreboardName(), net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE, 0L, 0));
            var view = new net.ledok.arenas_ld.util.DownedSpectators.RunView(new Object(), () -> participants, () -> false,
                uuid -> false, (time, uuid) -> -1L);
            var stats = new net.ledok.arenas_ld.stats.RunStats(view, () -> new net.ledok.arenas_ld.stats.RunDetailsSource(
                null, net.minecraft.network.chat.Component.literal("Test"), "dungeon", net.ledok.arenas_ld.dungeon.run.DifficultyTier.NORMAL,
                new ArenasApi.InstanceRef(level.dimension(), BlockPos.ZERO), false, List.of(), level.getGameTime(), 100, 50, ""));
            var counters = stats.counters(player.getUUID(), player.getScoreboardName());
            ((net.ledok.arenas_ld.stats.RunStatsHolder) player).arenasLd$setRunStats(counters);
            java.util.function.Function<String, List<List<Object>>> of = type -> stats.snapshot(level.getServer()).players.get(0)
                .events.stream().filter(e -> type.equals(e.get(1))).toList();

            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3, 2, 2)));
            level.addFreshEntity(zombie);
            player.invulnerableTime = 0;
            player.hurt(level.damageSources().mobAttack(zombie), 4);
            var hits = of.apply("hit_taken");
            helper.assertTrue(hits.size() == 1, "one hit_taken: " + hits + " counts " + stats.snapshot(level.getServer())
                .players.get(0).eventCounts + " hp " + player.getHealth() + " mode " + player.gameMode.getGameModeForPlayer()
                + " mark " + ((net.ledok.arenas_ld.stats.RunStatsHolder) player).arenasLd$runStats() + " diff " + level.getDifficulty());
            var hit = hits.get(0);
            helper.assertTrue("minecraft:zombie".equals(hit.get(2)) && ((Number) hit.get(4)).floatValue() == 4f
                    && ((Number) hit.get(8)).floatValue() == 20f && ((Number) hit.get(9)).floatValue() < 20f,
                "attacker, raw 4, HP 20 → less: " + hit);
            float hpLost = ((Number) hit.get(8)).floatValue() - ((Number) hit.get(9)).floatValue();
            helper.assertTrue(hit.size() == 14 && Math.abs(((Number) hit.get(12)).floatValue() - hpLost) < 0.11f
                    && "".equals(hit.get(11)),
                "applied = HP really lost, and a normal hit has no flag: " + hit);

            player.hurt(level.damageSources().mobAttack(zombie), 1); // inside the half-second invulnerability
            var record = stats.snapshot(level.getServer()).players.get(0);
            helper.assertTrue(record.eventCounts.getOrDefault("hit_ignored", 0) == 1 && of.apply("hit_taken").size() == 1,
                "a hit lost to invulnerability is only counted: " + record.eventCounts);

            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 200, 1));
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 200, 1));
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            record = stats.snapshot(level.getServer()).players.get(0);
            helper.assertTrue(of.apply("effect_add").size() == 1 && of.apply("effect_remove").size() == 1
                    && record.eventCounts.getOrDefault("effect_refresh", 0) == 1,
                "effect added once, the refresh counted, the removal logged: " + record.eventCounts);

            net.ledok.arenas_ld.stats.RunEvents.swap(player, "mainhand", net.minecraft.world.item.ItemStack.EMPTY,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            net.ledok.arenas_ld.stats.RunEvents.swap(player, "mainhand", new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD),
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
            var swaps = of.apply("swap");
            helper.assertTrue(swaps.size() == 1 && "".equals(swaps.get(0).get(3)) && "minecraft:bow".equals(swaps.get(0).get(4)),
                "two swaps in the same tick are one, from where it started to where it ended: " + swaps);

            // Cheat Death as Spell Engine Expansion reports it: a DAMAGE_TAKEN passive inside a hit that is refused.
            player.invulnerableTime = 0;
            net.ledok.arenas_ld.stats.RunEvents.hurtStart(player, level.damageSources().mobAttack(zombie), 100);
            net.ledok.arenas_ld.stats.RunEvents.spell(player, level.getGameTime(), "passive",
                "skill_tree_rpgs:rogue_tier_3_passive_1", "DAMAGE_TAKEN", "PRE", "skill_tree", "", 0);
            net.ledok.arenas_ld.stats.RunEvents.hurtEnd(player);
            var cancelled = of.apply("hit_cancelled");
            helper.assertTrue(cancelled.size() == 1 && String.valueOf(cancelled.get(0).get(8)).contains("rogue_tier_3_passive_1")
                    && of.apply("passive").size() == 1,
                "the refused hit carries the passive that fired in it: " + cancelled);

            for (int i = 0; i < net.ledok.arenas_ld.stats.RunEvents.CAP + 10; i++) {
                net.ledok.arenas_ld.stats.RunEvents.spell(player, level.getGameTime(), "cast_end", "test:spell", "INSTANT", "main_hand", "", 0, 1f, 0, -1);
            }
            record = stats.snapshot(level.getServer()).players.get(0);
            helper.assertTrue(record.events.size() == net.ledok.arenas_ld.stats.RunEvents.CAP && record.eventsDropped > 0
                    && record.eventCounts.get("cast_end") == net.ledok.arenas_ld.stats.RunEvents.CAP + 10,
                "capped at " + net.ledok.arenas_ld.stats.RunEvents.CAP + ", the rest counted: " + record.events.size() + " / " + record.eventsDropped);
            helper.assertTrue(net.ledok.arenas_ld.stats.RunRecord.VERSION == 2, "record version 2");
            zombie.discard();
        } finally {
            level.getServer().setDifficulty(difficulty, true);
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** 4.36.2: the class book is kept with its components, so the run details draw that class's own book. */
    public static void runDetailsClassBook(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK);
        book.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
            net.minecraft.network.chat.Component.literal("Arcane Spell Book"));
        var record = new net.ledok.arenas_ld.stats.RunRecord();
        var p = new net.ledok.arenas_ld.stats.RunRecord.Player();
        p.uuid = UUID.randomUUID().toString();
        p.name = "ald_gt_book";
        p.state = "won";
        p.gear = new net.ledok.arenas_ld.stats.RunRecord.Gear();
        p.gear.playerClass = "minecraft:book";
        p.gear.playerClassStack = book.save(registries).toString();
        record.players.add(p);
        var row = net.ledok.arenas_ld.stats.RunDetails.fromRecord(record, false, false, registries).rows().get(0);
        helper.assertTrue(row.classIcon().is(net.minecraft.world.item.Items.BOOK)
                && row.classIcon().getHoverName().getString().equals("Arcane Spell Book"),
            "the row carries the book with its name component: " + row.classIcon());
        var plain = net.ledok.arenas_ld.stats.RunDetails.fromRecord(record, false, false).rows().get(0);
        helper.assertTrue(plain.classIcon().isEmpty(), "without registries: no stack (the client falls back to the id)");
        helper.succeed();
    }

    /**
     * 4.36.1: securing a player before a run (Start, teleport in/out) puts the cursor stack and the crafting grid back
     * into the inventory, and nothing lands on the ground.
     */
    public static void runPlayerSafety(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 2)));
        try {
            var menu = player.inventoryMenu;
            menu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3));
            menu.getSlot(1).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, 4));
            net.ledok.arenas_ld.util.RunPlayerSafety.secure(player);
            helper.assertTrue(menu.getCarried().isEmpty(), "the cursor stack left the cursor");
            helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "the crafting grid is empty");
            helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == 3
                    && player.getInventory().countItem(net.minecraft.world.item.Items.OAK_PLANKS) == 4,
                "both went into the inventory");
            var dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                player.getBoundingBox().inflate(4));
            helper.assertTrue(dropped.isEmpty(), "nothing on the ground: " + dropped);
            helper.assertTrue(player.containerMenu == player.inventoryMenu, "no menu left open");
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * A survival test player that mobs attack (the GameTest mock player counts as creative, which
     * mobs never target), kept alive by Resistance V and Regeneration.
     */
    private static ServerPlayer survivalPlayer(GameTestHelper helper, BlockPos at) {
        return survivalPlayer(helper, at, UUID.randomUUID());
    }

    /** {@link #survivalPlayer(GameTestHelper, BlockPos)} joining with a given UUID (a returning player). */
    private static ServerPlayer survivalPlayer(GameTestHelper helper, BlockPos at, UUID uuid) {
        var level = helper.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
            new com.mojang.authlib.GameProfile(uuid, "ald_gt_" + suffix()), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20 * 600, 4, false, false));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.REGENERATION, 20 * 600, 4, false, false));
        return player;
    }

    /**
     * Doors are set as a whole: a block already in the target state no longer stops the fill, so a
     * door left half-changed (part of it was in an unloaded chunk) heals on the next change, and the
     * door group the run logic sees is the whole connected doorway.
     */
    public static void doorGroups(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos[] door = new BlockPos[4];
        for (int i = 0; i < door.length; i++) {
            helper.setBlock(new BlockPos(1 + i, 1, 1), BlockRegistry.PHASE_BLOCK);
            door[i] = helper.absolutePos(new BlockPos(1 + i, 1, 1));
        }
        // Block 2 already open/armed, as an earlier change that reached only part of the door left it.
        level.setBlockAndUpdate(door[1], level.getBlockState(door[1])
            .setValue(net.ledok.arenas_ld.block.PhaseBlock.SOLID, false)
            .setValue(net.ledok.arenas_ld.block.PhaseBlock.ARMED, true));

        var group = net.ledok.arenas_ld.block.entity.PhaseBlockEntity.connectedGroup(level, door[0], true);
        helper.assertTrue(group.size() == 4, "the group is the whole doorway, got " + group.size());
        helper.assertTrue(net.ledok.arenas_ld.dungeon.run.RoomGraph.expandDoorGroup(level, door[3]).size() == 4,
            "the run graph sees the whole doorway from any block");
        helper.assertTrue(net.ledok.arenas_ld.block.entity.PhaseBlockEntity.connectedGroup(
            level, helper.absolutePos(new BlockPos(1, 2, 1)), true).isEmpty(), "no group from a non-door block");

        int changed = net.ledok.arenas_ld.block.entity.PhaseBlockEntity.applyToDoor(level, door[0], false, true, true);
        helper.assertTrue(changed == 3, "the three blocks in the old state change, got " + changed);
        for (BlockPos pos : door) {
            var state = level.getBlockState(pos);
            helper.assertTrue(!state.getValue(net.ledok.arenas_ld.block.PhaseBlock.SOLID)
                && state.getValue(net.ledok.arenas_ld.block.PhaseBlock.ARMED), "every block is open and armed: " + pos);
        }
        helper.assertTrue(net.ledok.arenas_ld.block.entity.PhaseBlockEntity.applyToDoor(level, door[2], true, false, true) == 4,
            "closing from any block closes the whole door");
        helper.assertTrue(net.ledok.arenas_ld.util.RunChunkTickets.entitiesLoaded(level,
            net.ledok.arenas_ld.util.RunChunkTickets.owner(level.dimension(), door[0])), "an owner with no tickets is settled");
        helper.succeed();
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

            // Pass 3: a player left on another run's team (a missed exit) never gets it back.
            var store = net.ledok.arenas_ld.util.PartyTeamStore.get(level.getServer());
            String stale = "ald_r_gt" + suffix();
            scoreboard.addPlayerToTeam(name, priorTeam);
            net.ledok.arenas_ld.util.RunPartyTeams.add(level, stale, player); // stores priorTeam, never removed
            String thirdRun = "ald_r_gt" + suffix();
            net.ledok.arenas_ld.util.RunPartyTeams.add(level, thirdRun, player);
            net.ledok.arenas_ld.util.RunPartyTeams.teardown(level, thirdRun, List.of(name));
            helper.assertTrue(scoreboard.getPlayersTeam(name) == priorTeam,
                "the stale run team is skipped: the real prior team comes back");
            // Same, with the real prior team already lost (what live had): no team at all.
            store.take(name);
            scoreboard.addPlayerToTeam(name, scoreboard.getPlayerTeam(stale));
            String fourthRun = "ald_r_gt" + suffix();
            net.ledok.arenas_ld.util.RunPartyTeams.add(level, fourthRun, player);
            net.ledok.arenas_ld.util.RunPartyTeams.teardown(level, fourthRun, List.of(name));
            helper.assertTrue(scoreboard.getPlayersTeam(name) == null, "a stale run team is never given back");
            // An old run ending late doesn't take a player off the run they're in now.
            String current = "ald_r_gt" + suffix();
            net.ledok.arenas_ld.util.RunPartyTeams.add(level, current, player);
            net.ledok.arenas_ld.util.RunPartyTeams.teardown(level, stale, List.of(name));
            helper.assertTrue(scoreboard.getPlayersTeam(name) == scoreboard.getPlayerTeam(current),
                "a late teardown of another run leaves the current run team alone");
            net.ledok.arenas_ld.util.RunPartyTeams.teardown(level, current, List.of(name));
            helper.assertTrue(scoreboard.getPlayersTeam(name) == null, "the current run still gives back no team");
            scoreboard.addPlayerTeam(stale);

            // release: off a stale run team, which goes once empty.
            scoreboard.addPlayerToTeam(name, scoreboard.getPlayerTeam(stale));
            helper.assertTrue(stale.equals(net.ledok.arenas_ld.util.RunPartyTeams.release(level, name)), "release names the team");
            helper.assertTrue(scoreboard.getPlayersTeam(name) == null && scoreboard.getPlayerTeam(stale) == null,
                "released, and the empty stale team is deleted");
            helper.assertTrue(net.ledok.arenas_ld.util.RunPartyTeams.release(level, name) == null, "nothing to release");

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
                var met = controller.getScreenOpeningData(owner).metRequirements();
                helper.assertFalse(met.containsKey(DifficultyTier.HARD), "a locked tier shows its refusal, not the met label");
                helper.assertTrue(met.containsKey(DifficultyTier.EASY) && met.get(DifficultyTier.EASY).getString().equals("Lv 5+"),
                    "an open tier carries its met requirement label");

                // 4.28.0: the invite filter (Eden's privacy setting) refuses before the invite exists.
                ServerPlayer guest = helper.makeMockServerPlayerInLevel();
                try {
                    String[] asked = new String[1];
                    ArenasApi.setInviteFilter((inviter, invitee, activity) -> {
                        asked[0] = activity;
                        return invitee.equals(guest.getUUID()) && inviter == owner ? Component.literal("no invites") : null;
                    });
                    helper.assertFalse(controller.invitePlayer(owner, guest.getUUID()), "the filter refuses the invite");
                    helper.assertTrue(controller.getPendingInvites().stream().noneMatch(i -> i.invitedUuid().equals(guest.getUUID())), "no invite exists");
                    helper.assertTrue(id.toString().equals(asked[0]), "the filter gets the activity id: " + asked[0]);
                    ArenasApi.setInviteFilter(null);
                    helper.assertTrue(controller.invitePlayer(owner, guest.getUUID()), "without a filter the invite goes");
                } finally {
                    ArenasApi.setInviteFilter(null);
                    server.getPlayerList().remove(guest);
                }

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

    /**
     * Run modifiers: a lobby owner picks built-in ones (not unknown ones, not ones the activity type
     * doesn't allow), a run mob spawned while a run with Tanky mobs + Speed run ticks gets ×2 health and
     * ×1.5 speed, and damage factors add their bonuses (×4 and ×2 make ×5).
     */
    public static void runModifiers(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ServerPlayer owner = helper.makeMockServerPlayerInLevel();
            try {
                helper.assertTrue(controller.createLobby(owner).isPresent(), "lobby created");
                helper.assertTrue(controller.setLobbyModifier(owner, "tanky_mobs", true), "built-in modifier picked");
                helper.assertTrue(controller.setLobbyModifier(owner, "items_to_xp", true), "items to XP is allowed in dungeons");
                helper.assertFalse(controller.setLobbyModifier(owner, "gt_no_such_modifier", true), "unknown id refused");
                var picked = controller.getLobbies().stream().filter(l -> l.isOwner(owner.getUUID())).findFirst()
                    .orElseThrow().modifiers();
                helper.assertTrue(picked.equals(Set.of("tanky_mobs", "items_to_xp")), "the lobby holds the picks: " + picked);
                helper.assertTrue(controller.setLobbyModifier(owner, "items_to_xp", false), "switched off again");

                var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
                double baseHealth = zombie.getMaxHealth();
                double baseSpeed = zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
                net.ledok.arenas_ld.util.RunEffects.during(List.of("tanky_mobs", "speed_run"),
                    () -> net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(zombie));
                helper.assertTrue(Math.abs(zombie.getMaxHealth() - baseHealth * 2) < 1e-3, "tanky: ×2 health");
                helper.assertTrue(Math.abs(zombie.getHealth() - zombie.getMaxHealth()) < 1e-3, "and full health");
                helper.assertTrue(Math.abs(zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    - baseSpeed * 1.5) < 1e-6, "speed run: ×1.5 speed");
                var outside = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
                net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(outside);
                helper.assertTrue(Math.abs(outside.getMaxHealth() - baseHealth) < 1e-3, "no run modifiers: unchanged");

                // Factors add their bonuses (LeDok 2026-11-17): Glass cannon (×4) + Bloody mobs (×2) take ×5, not ×8.
                var both = net.ledok.arenas_ld.rewards.RunModifierCatalog.combined(List.of("glass_cannon", "bloody_mobs"));
                helper.assertTrue(Math.abs(both.damage_taken - 5) < 1e-9, "glass + bloody: ×5 taken, got " + both.damage_taken);
                helper.assertTrue(Math.abs(both.damage_dealt - 4) < 1e-9, "glass + bloody: ×4 dealt, got " + both.damage_dealt);
                var none = net.ledok.arenas_ld.rewards.RunModifierCatalog.combined(List.of());
                helper.assertTrue(none.damage_taken == 1 && none.mob_health == 1 && none.player_speed == 1, "none: ×1");
            } finally {
                server.getPlayerList().remove(owner);
            }
            helper.succeed();
        });
    }

    /**
     * Hardcore is the hardcore_run modifier: the old switch and the toggle agree, the lobby is hardcore while it
     * is picked, a lobby saved with only the old flag loads with it, and its rewards (×2 loot and currency, +25%
     * XP) come once whether the run has the modifier, the old flag, or both.
     */
    public static void hardcoreRun(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ServerPlayer owner = helper.makeMockServerPlayerInLevel();
            String hardcore = net.ledok.arenas_ld.rewards.RunModifierCatalog.HARDCORE;
            java.util.function.Supplier<net.ledok.arenas_ld.dungeon.lobby.Lobby> lobby = () -> controller.getLobbies().stream()
                .filter(l -> l.isOwner(owner.getUUID())).findFirst().orElseThrow();
            try {
                helper.assertTrue(controller.createLobby(owner).isPresent(), "lobby created");
                helper.assertTrue(controller.setLobbyHardcore(owner, true), "old hardcore switch");
                helper.assertTrue(lobby.get().hardcoreEnabled() && lobby.get().modifiers().contains(hardcore),
                    "the old switch picks hardcore_run: " + lobby.get().modifiers());
                helper.assertTrue(controller.setLobbyModifier(owner, hardcore, false), "toggle off");
                helper.assertFalse(lobby.get().hardcoreEnabled(), "not hardcore without it");
                helper.assertTrue(controller.setLobbyModifier(owner, hardcore, true), "toggle on");
                helper.assertTrue(lobby.get().hardcoreEnabled(), "hardcore with it");

                var old = new net.ledok.arenas_ld.dungeon.lobby.Lobby(UUID.randomUUID(), owner.getUUID(), "gt",
                    Set.of(owner.getUUID()), java.util.Map.of(owner.getUUID(), "gt"), Set.of(),
                    net.ledok.arenas_ld.dungeon.run.DifficultyTier.values()[0], true,
                    net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.values()[0],
                    net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.values()[0], 0L);
                var tag = net.ledok.arenas_ld.dungeon.lobby.Lobby.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
                var loaded = net.ledok.arenas_ld.dungeon.lobby.Lobby.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).getOrThrow();
                helper.assertTrue(loaded.hardcoreEnabled() && loaded.modifiers().equals(Set.of(hardcore)),
                    "an old hardcore lobby loads with hardcore_run: " + loaded.modifiers());

                var mode = net.ledok.arenas_ld.rewards.RewardModifiers.Mode.DUNGEON;
                for (var mods : List.of(
                    net.ledok.arenas_ld.rewards.RewardModifiers.forRun(null, null, true, mode, List.of()),
                    net.ledok.arenas_ld.rewards.RewardModifiers.forRun(null, null, false, mode, List.of(hardcore)),
                    net.ledok.arenas_ld.rewards.RewardModifiers.forRun(null, null, true, mode, List.of(hardcore)))) {
                    var expected = Set.of(
                        net.ledok.arenas_ld.rewards.RewardModifier.Stat.LOOT + " MORE 2.0",
                        net.ledok.arenas_ld.rewards.RewardModifier.Stat.CURRENCY + " MORE 2.0",
                        net.ledok.arenas_ld.rewards.RewardModifier.Stat.XP + " INCREASED 0.25");
                    var got = mods.stream().map(m -> m.stat() + " " + m.kind() + " " + m.value()).toList();
                    helper.assertTrue(got.size() == 3 && Set.copyOf(got).equals(expected), "hardcore rewards once: " + got);
                }
            } finally {
                server.getPlayerList().remove(owner);
            }
            helper.succeed();
        });
    }

    /**
     * Run modifier conflicts: a conflicting pick is refused (either side's list counts), the group-free other
     * still goes on, a lobby that holds a conflicting pair anyway can't start, and every built-in is written into
     * the config.
     */
    public static void runModifierConflicts(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ServerPlayer owner = helper.makeMockServerPlayerInLevel();
            java.util.function.Supplier<net.ledok.arenas_ld.dungeon.lobby.Lobby> lobby = () -> controller.getLobbies().stream()
                .filter(l -> l.isOwner(owner.getUUID())).findFirst().orElseThrow();
            try {
                var configured = net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().run_modifiers.keySet();
                helper.assertTrue(configured.containsAll(List.of("hardcore_run", "frozen_ground", "items_to_xp",
                    "xp_to_loot", "currency_to_loot")), "the built-ins are in the config: " + configured);

                helper.assertTrue(controller.createLobby(owner).isPresent(), "lobby created");
                helper.assertTrue(controller.setLobbyModifier(owner, "items_to_xp", true), "items to XP on");
                helper.assertFalse(controller.setLobbyModifier(owner, "xp_to_loot", true), "XP to loot conflicts");
                helper.assertFalse(controller.setLobbyModifier(owner, "currency_to_loot", true),
                    "currency to loot conflicts with items to XP (listed on items to XP's side)");
                helper.assertTrue(controller.setLobbyModifier(owner, "currency_to_xp", true), "currency to XP doesn't");
                helper.assertTrue(lobby.get().modifiers().equals(Set.of("items_to_xp", "currency_to_xp")),
                    "refused picks change nothing: " + lobby.get().modifiers());
                helper.assertTrue(net.ledok.arenas_ld.activity.EntryGates.partyRefusal(server, lobby.get(), controller,
                    lobby.get().selectedTier()) == null, "no conflict: the start isn't refused for it");

                var forced = lobby.get().withModifier("xp_to_loot", true, List.of());
                helper.assertTrue(net.ledok.arenas_ld.activity.EntryGates.partyRefusal(server, forced, controller,
                    forced.selectedTier()) != null, "a lobby holding a conflicting pair can't start");

                var mods = net.ledok.arenas_ld.rewards.RewardModifiers.definition("xp_to_loot");
                double xp = net.ledok.arenas_ld.rewards.RewardMath.multiplier(mods, net.ledok.arenas_ld.rewards.RewardModifier.Stat.XP);
                double loot = net.ledok.arenas_ld.rewards.RewardMath.flat(mods, net.ledok.arenas_ld.rewards.RewardModifier.Stat.LOOT);
                helper.assertTrue(xp == 0 && loot == 2, "XP to loot: no XP, +2 loot (" + xp + ", " + loot + ")");
            } finally {
                server.getPlayerList().remove(owner);
            }
            helper.succeed();
        });
    }

    /**
     * Removing a player from a chunk they were never registered in (what left returned players without chunks
     * on live) is skipped instead of throwing, so the move that asked for it can register them where they are.
     */
    public static void unregisteredPlayerRemoval(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            var distance = helper.getLevel().getChunkSource().chunkMap.getDistanceManager();
            distance.removePlayer(net.minecraft.core.SectionPos.of(new BlockPos(1_000_000, 64, 1_000_000)), player);
        } catch (RuntimeException e) {
            helper.fail("removing an unregistered player threw: " + e);
        } finally {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Moving between lobbies: creating a raid lobby takes the player out of their dungeon lobby (ownership passes
     * on), joining a full lobby leaves them where they were, and a lone owner's old lobby closes. ArenasApi.ownLobby
     * follows them.
     */
    public static void lobbyAutoLeave(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.setBlock(RAID, BlockRegistry.RAID_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity dungeon = helper.getBlockEntity(DUNGEON);
            net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity raid = helper.getBlockEntity(RAID);
            ServerPlayer owner = helper.makeMockServerPlayerInLevel();
            ServerPlayer mate = helper.makeMockServerPlayerInLevel();
            try {
                UUID dungeonLobby = dungeon.createLobby(owner).orElseThrow();
                helper.assertTrue(dungeon.joinLobby(mate, dungeonLobby), "mate joins the dungeon lobby");
                helper.assertTrue(ArenasApi.ownLobby(owner).map(l -> l.type() == ArenasApi.ActivityType.DUNGEON
                    && l.members() == 2 && l.owner()).orElse(false), "ownLobby: the dungeon lobby, owned, 2 members");

                helper.assertTrue(raid.createLobby(owner), "owner creates a raid lobby");
                var left = dungeon.getLobbies().stream().filter(l -> l.lobbyId().equals(dungeonLobby)).findFirst().orElse(null);
                helper.assertTrue(left != null && !left.isMember(owner.getUUID()) && left.isOwner(mate.getUUID()),
                    "the dungeon lobby keeps the mate, now its owner");
                helper.assertTrue(ArenasApi.ownLobby(owner).map(l -> l.type() == ArenasApi.ActivityType.RAID).orElse(false),
                    "ownLobby follows to the raid");

                UUID raidLobby = raid.getLobbyByMember(owner.getUUID()).lobbyId();
                raid.setMaxPartySize(1);
                helper.assertFalse(raid.joinLobby(mate, raidLobby), "the full raid lobby refuses the mate");
                helper.assertTrue(dungeon.getLobbies().stream().anyMatch(l -> l.isMember(mate.getUUID())),
                    "a refused join leaves the mate in their lobby");

                helper.assertTrue(dungeon.joinLobby(owner, dungeonLobby), "owner goes back to the dungeon lobby");
                helper.assertTrue(raid.getLobbyById(raidLobby) == null, "their lone raid lobby closed");
            } finally {
                server.getPlayerList().remove(owner);
                server.getPlayerList().remove(mate);
            }
            helper.succeed();
        });
    }

    /**
     * Run stats counting: a run player's hit and the poison they applied count as their damage (HP really lost,
     * capped at the mob's health), their HP lost and healed are theirs, a mob outside the run isn't counted, the
     * details payload carries the numbers, and an ended run counts nothing more.
     */
    public static void runStatsCounting(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        try {
            var participants = new java.util.LinkedHashMap<UUID, net.ledok.arenas_ld.dungeon.run.RunParticipant>();
            participants.put(player.getUUID(), new net.ledok.arenas_ld.dungeon.run.RunParticipant(player.getUUID(),
                player.getScoreboardName(), net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE, 0L, 0));
            var view = new net.ledok.arenas_ld.util.DownedSpectators.RunView(new Object(), () -> participants, () -> false,
                uuid -> false, (time, uuid) -> -1L);
            var stats = new net.ledok.arenas_ld.stats.RunStats(view, () -> new net.ledok.arenas_ld.stats.RunDetailsSource(
                null, net.minecraft.network.chat.Component.literal("Test"), "dungeon", net.ledok.arenas_ld.dungeon.run.DifficultyTier.NORMAL,
                new ArenasApi.InstanceRef(level.dimension(), BlockPos.ZERO), false, List.of(), level.getGameTime(), 100, 50, ""));
            stats.tick(level.getServer()); // not on a 20th tick: attach by hand below
            var counters = stats.counters(player.getUUID(), player.getScoreboardName());
            ((net.ledok.arenas_ld.stats.RunStatsHolder) player).arenasLd$setRunStats(counters);

            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            zombie.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
            level.addFreshEntity(zombie);
            net.ledok.arenas_ld.stats.RunStats.during(stats, () -> net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(zombie));
            helper.assertTrue(((net.ledok.arenas_ld.stats.RunStatsHolder) zombie).arenasLd$runStats() == stats, "the run's mob is marked");

            float hp0 = zombie.getHealth();
            zombie.hurt(level.damageSources().playerAttack(player), 5);
            float lost1 = hp0 - zombie.getHealth();
            helper.assertTrue(lost1 > 0 && Math.abs(counters.dealt - lost1) < 1e-3,
                "a hit counts the HP it took (after armor): " + counters.dealt + " vs " + lost1);

            zombie.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100), player);
            zombie.invulnerableTime = 0;
            float hp1 = zombie.getHealth();
            zombie.hurt(level.damageSources().magic(), 2);
            float lost2 = hp1 - zombie.getHealth();
            helper.assertTrue(lost2 > 0 && Math.abs(counters.dealt - (lost1 + lost2)) < 1e-3,
                "damage over time goes to who applied it: " + counters.dealt);

            zombie.invulnerableTime = 0;
            float left = zombie.getHealth();
            zombie.hurt(level.damageSources().playerAttack(player), 1000);
            float total = lost1 + lost2 + left;
            helper.assertTrue(Math.abs(counters.dealt - total) < 1e-3, "overkill isn't counted: " + counters.dealt);

            // Biggest hits and damage per source (4.24.0): fists are "item:minecraft:air", the poison tick its damage type.
            var hitsNow = counters.toRecord(0, "alive");
            helper.assertTrue(hitsNow.topHits.size() == 3 && hitsNow.topHits.get(0).amount >= 500
                    && hitsNow.topHits.get(0).source.equals("item:minecraft:air")
                    && hitsNow.topHits.get(0).target.equals("minecraft:zombie"),
                "the killing blow is the biggest hit, from the fists: " + (hitsNow.topHits.isEmpty() ? "none" : hitsNow.topHits.get(0).source));
            helper.assertTrue(hitsNow.damageBySource.containsKey("item:minecraft:air")
                    && hitsNow.damageBySource.containsKey("damage:minecraft:magic"),
                "damage per source: " + hitsNow.damageBySource.keySet());
            var spellTarget = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            spellTarget.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 4)));
            level.addFreshEntity(spellTarget);
            net.ledok.arenas_ld.stats.RunStats.during(stats, () -> net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(spellTarget));
            net.ledok.arenas_ld.stats.SpellContext.push(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .wrapAsHolder(net.minecraft.world.item.Items.STICK)); // stands in for a Spell Engine spell holder
            float beforeSpell = counters.dealt;
            try {
                spellTarget.hurt(level.damageSources().playerAttack(player), 3);
            } finally {
                net.ledok.arenas_ld.stats.SpellContext.pop();
            }
            total += counters.dealt - beforeSpell; // the later checks compare against the run's whole total
            helper.assertTrue(counters.toRecord(0, "alive").damageBySource.containsKey("spell:minecraft:stick"),
                "a hit during a spell impact counts for the spell");
            spellTarget.discard();

            var head = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET);
            var data = new net.minecraft.nbt.CompoundTag();
            data.putInt("dd.rsf", 42);
            data.putInt("limit_break_level", 3);
            head.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(data));
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
            var gear = net.ledok.arenas_ld.stats.RunGear.capture(player);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.item.ItemStack.EMPTY);
            helper.assertTrue(gear.slots.size() == 6 && gear.slots.get(0).power == 42 && gear.slots.get(0).limitBreak == 3
                    && gear.powerAvg == 42 && gear.powerMax == 42 && gear.slots.get(4).power == -1,
                "gear: per slot power and Limit Break, average of the slots that have one");
            helper.assertTrue(gear.playerClass.isEmpty() && gear.spellPool.isEmpty(),
                "no spell book and no class tree in the test world: no class (" + gear.playerClass + ")");
            helper.assertTrue(net.ledok.arenas_ld.stats.RunGear.levelOf("falleneden:arenas/level_40") == 40
                    && net.ledok.arenas_ld.stats.RunGear.levelOf("minecraft:chests/simple_dungeon") == -1
                    && net.ledok.arenas_ld.stats.RunGear.levelOf("") == -1, "run level from the loot table");

            var outsider = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            outsider.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(3, 2, 3)));
            level.addFreshEntity(outsider);
            outsider.hurt(level.damageSources().playerAttack(player), 4);
            helper.assertTrue(Math.abs(counters.dealt - total) < 1e-3, "a mob outside the run isn't counted");
            outsider.discard();

            player.hurt(level.damageSources().fellOutOfWorld(), 4);
            helper.assertTrue(Math.abs(counters.taken - 4) < 1e-3, "HP lost counts as taken: " + counters.taken);
            helper.assertTrue(counters.kills == 1 && counters.overkill > 0, "the killing blow is a kill, its excess overkill: "
                + counters.kills + ", " + counters.overkill);

            // Absorption is capped by the max absorption attribute (0 unless an effect raises it).
            player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_ABSORPTION).setBaseValue(20);
            player.setAbsorptionAmount(6);
            player.invulnerableTime = 0;
            float absorptionBefore = player.getAbsorptionAmount();
            float healthBefore = player.getHealth();
            player.hurt(level.damageSources().fellOutOfWorld(), 4);
            helper.assertTrue(Math.abs(counters.blockedAbsorption - 4) < 1e-3 && Math.abs(counters.taken - 4) < 1e-3,
                "absorption soaks and counts as blocked: " + counters.blockedAbsorption + " (absorption " + absorptionBefore
                    + " -> " + player.getAbsorptionAmount() + ", health " + healthBefore + " -> " + player.getHealth() + ")");

            var boss = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            boss.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4, 2, 4)));
            level.addFreshEntity(boss);
            net.ledok.arenas_ld.stats.RunStats.during(stats, () -> net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(boss));
            net.ledok.arenas_ld.stats.RunStats.markBoss(boss);
            boss.hurt(level.damageSources().playerAttack(player), 1000);
            helper.assertTrue(counters.bossKills == 1 && counters.bossDealt > 0, "boss damage and boss kill counted");
            var saved = stats.saveTotals();
            helper.assertTrue(saved.contains("\"bossKills\":1"), "the totals go into the run's save");

            var reloaded = new net.ledok.arenas_ld.stats.RunStats(view, () -> new net.ledok.arenas_ld.stats.RunDetailsSource(
                null, net.minecraft.network.chat.Component.literal("Test"), "dungeon", net.ledok.arenas_ld.dungeon.run.DifficultyTier.NORMAL,
                new ArenasApi.InstanceRef(level.dimension(), BlockPos.ZERO), false, List.of(), level.getGameTime(), 100, 50, ""));
            reloaded.load(saved);
            var back = reloaded.players().get(player.getUUID());
            helper.assertTrue(back != null && back.kills == counters.kills && Math.abs(back.dealt - counters.dealt) < 1e-3,
                "the saved totals come back after a restart");
            reloaded.end(level.getServer());
            player.heal(3);
            helper.assertTrue(Math.abs(counters.healed() - 3) < 1e-3, "healing counts: " + counters.healed());
            // Healing by source (4.26.3): an effect tick and an instant health potion name their effect.
            player.invulnerableTime = 0;
            player.hurt(level.damageSources().fellOutOfWorld(), 10);
            var regen = new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 100, 5);
            regen.tick(player, () -> { });
            net.minecraft.world.effect.MobEffects.HEAL.value().applyInstantenousEffect(null, null, player, 0, 1.0);
            var healRecord = counters.toRecord(0, "alive");
            helper.assertTrue(healRecord.healBySource.containsKey("effect:minecraft:regeneration")
                    && healRecord.healBySource.containsKey("effect:minecraft:instant_health"),
                "heals by source: " + healRecord.healBySource);
            helper.assertTrue(!healRecord.topHeals.isEmpty() && healRecord.topHeals.get(0).source.equals("effect:minecraft:instant_health"),
                "the biggest heal (instant health, 4 HP) leads the top heals: " + (healRecord.topHeals.isEmpty() ? "none"
                    : healRecord.topHeals.get(0).source));

            // (Opening sends a packet a NeoForge mock player can't take; finding the run is what is tested.)
            helper.assertTrue(net.ledok.arenas_ld.stats.RunDetails.runOf(player.getUUID()) == stats,
                "the player's run is found for the details screen");

            var record = stats.snapshot(level.getServer());
            helper.assertTrue(record.phases.stream().anyMatch(ph -> ph.percent == 0), "the boss's death is a phase mark");
            // Balance data (4.25.0): the boss's max HP and HP left, the time limit, seconds on the boss, fight stats.
            helper.assertTrue(record.bosses.size() == 1 && record.bosses.get(0).killed && record.bosses.get(0).healthLeft == 0
                    && record.bosses.get(0).maxHealth == 20 && record.bossMaxHealth == 20 && record.bossHealthLeft == 0,
                "the boss: max HP 20, killed: " + record.bossMaxHealth + " / " + record.bossHealthLeft);
            helper.assertTrue(record.timeLimitSeconds == 0, "no time limit given: 0");
            var me = record.players.stream().filter(p -> p.uuid.equals(player.getUUID().toString())).findFirst().orElseThrow();
            helper.assertTrue(me.bossActiveSeconds == 1, "one second hitting the boss: " + me.bossActiveSeconds);
            helper.assertTrue(me.statsStart != null && me.statsStart.attributes.containsKey("minecraft:generic.max_health")
                    && me.statsBestMinute != null && me.bestMinuteDealt > 0,
                "stats when the fight began and in the best minute");
            helper.assertTrue(me.skills != null, "skills listed (empty without skill trees)");
            // Fight stats (4.27.0): the boss hit is filed under the weapon pair in hand, with stat ranges.
            helper.assertTrue(me.weapons.size() == 1 && me.weapons.get(0).mainHand.equals("minecraft:air")
                    && me.weapons.get(0).hits == 1 && me.weapons.get(0).stats.containsKey("attack_speed")
                    && me.weapons.get(0).stats.containsKey("attack_damage"),
                "boss hits per weapon pair: " + (me.weapons.isEmpty() ? "none" : me.weapons.get(0).mainHand + " " + me.weapons.get(0).stats.keySet()));
            // Rewards tab (4.36.0, D5): a win's breakdown is kept for the run details instead of chat.
            helper.assertTrue(net.ledok.arenas_ld.stats.RunStats.recordRewards(player.getUUID(),
                    net.minecraft.network.chat.Component.literal("6 loot rolls"),
                    List.of(net.minecraft.network.chat.Component.literal("Loot: base 3 · Hardcore ×2 → 6"))),
                "a run with stats keeps the reward lines");
            var rewardRecord = stats.snapshot(level.getServer());
            var rewardRow = net.ledok.arenas_ld.stats.RunDetails.fromRecord(rewardRecord, false, true).rows().stream()
                .filter(r -> r.uuid().equals(player.getUUID())).findFirst().orElseThrow();
            helper.assertTrue(rewardRow.rewards().size() == 2 && rewardRow.rewards().get(0).getString().equals("6 loot rolls")
                    && rewardRow.rewards().get(1).getString().contains("Hardcore"),
                "the row carries the totals then the breakdown: " + rewardRow.rewards());
            helper.assertTrue(!net.ledok.arenas_ld.stats.RunStats.recordRewards(UUID.randomUUID(),
                    net.minecraft.network.chat.Component.empty(), List.of()),
                "someone in no run: false (the caller says it in chat)");
            level.getServer().getPlayerList().remove(player); // (a NeoForge mock player can't take the end screen packet)
            stats.end(level.getServer());
            var history = ArenasApi.runHistory(level.getServer(), null, player.getUUID(), 5);
            helper.assertTrue(!history.isEmpty() && history.get(0).party().contains(player.getScoreboardName()),
                "the finished run is in the history: " + history);
            float dealt = counters.dealt;
            var after = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            after.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 3)));
            level.addFreshEntity(after);
            ((net.ledok.arenas_ld.stats.RunStatsHolder) after).arenasLd$setRunStats(stats);
            after.hurt(level.damageSources().playerAttack(player), 3);
            helper.assertTrue(counters.dealt == dealt && ((net.ledok.arenas_ld.stats.RunStatsHolder) after).arenasLd$runStats() == null,
                "an ended run counts nothing more and lets go of its mobs");
            after.discard();
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * Staff cooldown reset (what Eden's Moderation buttons call): a dungeon and a raid instance on cooldown, reset by
     * the instance the API lists and by "all", are free a tick later; with nothing on cooldown it is refused.
     */
    public static void resetCooldowns(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.setBlock(RAID, BlockRegistry.RAID_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity dungeon = helper.getBlockEntity(DUNGEON);
            net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity raid = helper.getBlockEntity(RAID);
            BlockPos instancePos = helper.absolutePos(new BlockPos(1, 1, 5));
            var dim = helper.getLevel().dimension();
            dungeon.addInstance(instancePos, dim);
            dungeon.startInstanceCooldown(instancePos);
            raid.addInstance(instancePos, dim);
            try {
                var field = net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity.class.getDeclaredField("instances");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                var list = (List<net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity.RaidInstanceState>) field.get(raid);
                list.set(0, list.get(0).withStatus(net.ledok.arenas_ld.util.InstanceStatus.COOLDOWN, 6000));
            } catch (ReflectiveOperationException e) {
                helper.fail("couldn't put the raid instance on cooldown: " + e);
            }
            var dungeonRef = dungeon.liveStatus().instances().get(0);
            helper.assertTrue(dungeonRef.state() == ArenasApi.InstanceState.COOLDOWN, "the dungeon instance is on cooldown");
            helper.assertTrue(ArenasApi.resetCooldowns(server, dungeon.activityId(), dungeonRef.instance(), "gt") == null,
                "resetting the dungeon instance listed by the API is accepted");
            helper.assertTrue(raid.liveStatus().instances().get(0).state() == ArenasApi.InstanceState.COOLDOWN,
                "the raid instance is on cooldown");
            helper.assertTrue(ArenasApi.resetCooldowns(server, raid.activityId(), null, "gt") == null,
                "resetting all of the raid's is accepted");
            helper.runAfterDelay(3, () -> {
                helper.assertTrue(dungeon.liveStatus().instances().get(0).state() == ArenasApi.InstanceState.FREE,
                    "the dungeon instance is free");
                helper.assertTrue(raid.liveStatus().instances().get(0).state() == ArenasApi.InstanceState.FREE,
                    "the raid instance is free");
                helper.assertTrue(ArenasApi.resetCooldowns(server, raid.activityId(), null, "gt") != null,
                    "nothing on cooldown: refused");
                helper.succeed();
            });
        });
    }

    /**
     * The run details pick the player's current run: of two listed runs the newest wins, and a run that stopped
     * ticking (its controller took it away) is ended and no longer listed.
     */
    public static void runDetailsNewestRun(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var level = helper.getLevel();
        try {
            var participants = new java.util.LinkedHashMap<UUID, net.ledok.arenas_ld.dungeon.run.RunParticipant>();
            participants.put(player.getUUID(), new net.ledok.arenas_ld.dungeon.run.RunParticipant(player.getUUID(),
                player.getScoreboardName(), net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE, 0L, 0));
            java.util.function.Supplier<net.ledok.arenas_ld.stats.RunStats> make = () -> new net.ledok.arenas_ld.stats.RunStats(
                new net.ledok.arenas_ld.util.DownedSpectators.RunView(new Object(), () -> participants, () -> false,
                    uuid -> false, (time, uuid) -> -1L),
                () -> new net.ledok.arenas_ld.stats.RunDetailsSource(null, net.minecraft.network.chat.Component.literal("Test"),
                    "dungeon", net.ledok.arenas_ld.dungeon.run.DifficultyTier.NORMAL,
                    new ArenasApi.InstanceRef(level.dimension(), BlockPos.ZERO), false, List.of(), level.getGameTime(), 0, 0, ""));
            var old = make.get();
            var current = make.get();
            helper.assertTrue(net.ledok.arenas_ld.stats.RunDetails.runOf(player.getUUID()) == current, "the newest run is picked");
            old.tick(level.getServer()); // ticked once, then taken away by its controller
            helper.runAfterDelay(45, () -> {
                current.tick(level.getServer());
                net.ledok.arenas_ld.stats.RunDetails.endStopped(level.getServer());
                helper.assertTrue(old.ended() && !current.ended(), "a run that stopped ticking is ended, a ticking one isn't");
                current.end(level.getServer());
                helper.assertTrue(net.ledok.arenas_ld.stats.RunDetails.runOf(player.getUUID()) == null, "no run listed after both end");
                level.getServer().getPlayerList().remove(player);
                helper.succeed();
            });
        } catch (RuntimeException e) {
            level.getServer().getPlayerList().remove(player);
            throw e;
        }
    }

    /**
     * Breakable run blocks: an Adventure run player may break only what the run allows (the global list, ids and
     * #tags), only when the difficulty has block breaking on; such a break drops nothing. The controller's own
     * list adds to or replaces the global one, and the admin API refuses unknown blocks.
     */
    public static void runBlockBreaking(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        BlockPos glassRel = new BlockPos(2, 1, 2);
        BlockPos stoneRel = new BlockPos(3, 1, 2);
        BlockPos woolRel = new BlockPos(4, 1, 2);
        helper.setBlock(glassRel, net.minecraft.world.level.block.Blocks.GLASS);
        helper.setBlock(stoneRel, net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(woolRel, net.minecraft.world.level.block.Blocks.WHITE_WOOL);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            var config = net.ledok.arenas_ld.config.ArenasLdConfig.getInstance();
            List<String> globalBefore = config.run_breakable_blocks;
            config.run_breakable_blocks = new java.util.ArrayList<>(List.of("minecraft:glass", "#minecraft:wool"));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            var adventure = net.minecraft.world.level.GameType.ADVENTURE;
            BlockPos glass = helper.absolutePos(glassRel);
            BlockPos stone = helper.absolutePos(stoneRel);
            BlockPos wool = helper.absolutePos(woolRel);
            java.util.function.Function<Boolean, net.ledok.arenas_ld.dungeon.run.DungeonRun> run = allow ->
                new net.ledok.arenas_ld.dungeon.run.DungeonRun(DifficultyTier.NORMAL,
                    TierConfig.NORMAL_DEFAULT.withAllowBlockBreaking(allow), false, helper.absolutePos(new BlockPos(6, 1, 6)),
                    level.dimension(), level.getGameTime(), player.getScoreboardName());
            try {
                helper.assertTrue(player.blockActionRestricted(level, glass, adventure), "outside a run nothing breaks");

                net.ledok.arenas_ld.ArenasLdMod.DUNGEON_MANAGER.registerParticipant(player.getUUID(), run.apply(false));
                helper.assertTrue(player.blockActionRestricted(level, glass, adventure),
                    "a difficulty without block breaking allows nothing");
                helper.assertTrue(java.util.Objects.equals(net.ledok.arenas_ld.util.RunBlockBreaking.entriesFor(player), List.of()),
                    "in the run, with an empty list");

                net.ledok.arenas_ld.ArenasLdMod.DUNGEON_MANAGER.registerParticipant(player.getUUID(), run.apply(true));
                helper.assertFalse(player.blockActionRestricted(level, glass, adventure), "a listed block id breaks");
                helper.assertFalse(player.blockActionRestricted(level, wool, adventure), "a block of a listed tag breaks");
                helper.assertTrue(player.blockActionRestricted(level, stone, adventure), "an unlisted block doesn't");

                helper.assertTrue(player.gameMode.destroyBlock(glass), "the glass is broken");
                helper.assertTrue(level.getBlockState(glass).isAir(), "the glass is gone");
                helper.assertFalse(player.gameMode.destroyBlock(stone), "the stone isn't");
                helper.assertTrue(level.getBlockState(stone).is(net.minecraft.world.level.block.Blocks.STONE), "the stone stays");
                helper.assertTrue(player.gameMode.destroyBlock(wool), "the wool is broken");
                var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(glass).inflate(4));
                helper.assertTrue(drops.isEmpty(), "nothing dropped: " + drops);

                var add = new net.ledok.arenas_ld.util.BreakableBlocks("add", List.of("minecraft:stone", "minecraft:glass"));
                helper.assertTrue(add.effective(config.run_breakable_blocks)
                        .equals(List.of("minecraft:glass", "#minecraft:wool", "minecraft:stone")),
                    "add: the global list plus the controller's, no duplicates: " + add.effective(config.run_breakable_blocks));
                var replace = new net.ledok.arenas_ld.util.BreakableBlocks("replace", List.of());
                helper.assertTrue(replace.effective(config.run_breakable_blocks).isEmpty(), "replace with nothing: nothing");

                DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_break_" + suffix());
                helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
                helper.assertTrue(isKey(ArenasApi.setBreakableBlocks(server, id,
                        new net.ledok.arenas_ld.util.BreakableBlocks("add", List.of("minecraft:no_such_block")), "gt"),
                    "message.arenas_ld.admin.breakable_bad_entry"), "an unknown block is refused");
                helper.assertTrue(ArenasApi.setBreakableBlocks(server, id, add, "gt") == null, "a valid list is set");
                helper.assertTrue(ArenasApi.breakableBlocks(server, id).orElseThrow().equals(add), "and read back");
                var saved = new net.minecraft.nbt.CompoundTag();
                add.save(saved);
                helper.assertTrue(net.ledok.arenas_ld.util.BreakableBlocks.load(saved).equals(add), "it saves and loads");
            } finally {
                net.ledok.arenas_ld.ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(player.getUUID());
                config.run_breakable_blocks = globalBefore;
                server.getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * Hardcore death: the player is out of the fight but stays in the run (downed, spectating, still eligible for
     * the rewards), never respawns, and the run is lost only once every player in it is out.
     */
    public static void hardcoreOut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ServerPlayer mate = helper.makeMockServerPlayerInLevel();
        var run = new net.ledok.arenas_ld.dungeon.run.DungeonRun(DifficultyTier.NORMAL, TierConfig.NORMAL_DEFAULT, true,
            helper.absolutePos(new BlockPos(6, 1, 6)), level.dimension(), level.getGameTime(), player.getScoreboardName());
        try {
            var add = net.ledok.arenas_ld.dungeon.run.DungeonRun.class.getDeclaredMethod("addParticipant",
                net.ledok.arenas_ld.dungeon.run.RunParticipant.class);
            add.setAccessible(true); // package-private: runs get their players from the lobby
            for (ServerPlayer p : List.of(player, mate)) {
                add.invoke(run, new net.ledok.arenas_ld.dungeon.run.RunParticipant(p.getUUID(), p.getScoreboardName(),
                    net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE, 0L, 0));
            }
            player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
            net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.handlePlayerDown(level, null, run, player);
            var participant = run.participants().get(player.getUUID());
            helper.assertTrue(participant.status() == net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.DOWNED
                && participant.isEligibleForLoot(), "still in the run, still rewarded on a win: " + participant.status());
            // The mock player hard-codes isSpectator() false: read the game mode it was given.
            helper.assertTrue(player.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR,
                "spectating the party");
            var downed = run.downedPlayers().get(player.getUUID());
            helper.assertTrue(downed != null && downed.isOut() && downed.tick().equals(downed) && !downed.isReadyToRespawn(),
                "out for good: no countdown, no respawn");
            helper.assertFalse(net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.allOut(run), "a teammate still fights");
            net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.handlePlayerDown(level, null, run, mate);
            helper.assertTrue(net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.allOut(run), "everyone out: the run is lost");
            helper.assertTrue(new net.ledok.arenas_ld.dungeon.run.DownedPlayer(player.getUUID(), 1).tick().isReadyToRespawn(),
                "a normal downed player still respawns");
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        } finally {
            DownedSpectators.release(player);
            DownedSpectators.release(mate);
            level.getServer().getPlayerList().remove(player);
            level.getServer().getPlayerList().remove(mate);
        }
        helper.succeed();
    }

    /**
     * Respawn shield: right after a run respawn a player can't be hurt (void damage still lands), and their own first
     * hit ends it; with the config at 0 nothing is granted.
     */
    public static void respawnShield(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 2)));
        // A freshly joined player has vanilla's 60-tick spawn invulnerability: test after it.
        helper.runAfterDelay(65, () -> {
            var config = net.ledok.arenas_ld.config.ArenasLdConfig.getInstance();
            int before = config.respawn_invulnerable_ticks;
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
            try {
                player.removeAllEffects(); // survivalPlayer's Resistance would hide the damage
                zombie.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(4, 2, 4)));
                zombie.setNoAi(true);
                level.addFreshEntity(zombie);
                config.respawn_invulnerable_ticks = 20;
                float hp = player.getHealth();
                helper.assertTrue(player.hurt(level.damageSources().generic(), 1) && player.getHealth() < hp,
                    "without a shield a hit lands (the check below means something)");
                player.invulnerableTime = 0;
                net.ledok.arenas_ld.util.RespawnShield.grant(player);
                hp = player.getHealth();
                helper.assertFalse(player.hurt(level.damageSources().generic(), 4), "a hit is refused");
                // (Generic damage: the test world is on Peaceful, where mob hits on players do nothing anyway.)
                helper.assertTrue(player.getHealth() == hp, "no HP lost while shielded");
                player.invulnerableTime = 0;
                helper.assertTrue(player.hurt(level.damageSources().fellOutOfWorld(), 1) && player.getHealth() < hp,
                    "the void still hurts");
                helper.assertTrue(net.ledok.arenas_ld.util.RespawnShield.isShielded(player), "still shielded after that");
                zombie.hurt(level.damageSources().playerAttack(player), 1);
                helper.assertFalse(net.ledok.arenas_ld.util.RespawnShield.isShielded(player), "dealing damage ends it");
                player.invulnerableTime = 0;
                float now = player.getHealth();
                helper.assertTrue(player.hurt(level.damageSources().generic(), 4) && player.getHealth() < now,
                    "then hits land again");
                config.respawn_invulnerable_ticks = 0;
                net.ledok.arenas_ld.util.RespawnShield.grant(player);
                helper.assertFalse(net.ledok.arenas_ld.util.RespawnShield.isShielded(player), "0 turns it off");
            } finally {
                config.respawn_invulnerable_ticks = before;
                zombie.discard();
                level.getServer().getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * Rental builds (4.26.0): level bands, the renter's reward cut, a pick refused outside a lobby, a rental begun
     * again after a restart (same session id), reequip after a respawn, and the rental ended once the run is over.
     */
    public static void rentalBuilds(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        var calls = new java.util.ArrayList<String>();
        boolean[] renting = {false};
        net.ledok.arenas_ld.api.ArenasApi.RentalProvider before = ArenasApi.rentalProvider();
        ArenasApi.setRentalProvider(new ArenasApi.RentalProvider() {
            public List<String> presets(int level) { return List.of("test:rental/" + (level >= 20 ? 20 : 0) + "/rogue_shadow"); }
            public String displayName(String kit) { return "Shadow rogue"; }
            public boolean begin(ServerPlayer p, String session, String kit) { calls.add("begin " + session + " " + kit); renting[0] = true; return true; }
            public void end(MinecraftServer server, UUID uuid) { calls.add("end"); renting[0] = false; }
            public void reequip(ServerPlayer p) { calls.add("reequip"); }
            public boolean isRenting(ServerPlayer p) { return renting[0]; }
            public List<net.minecraft.world.item.ItemStack> preview(String kit) { return List.of(); }
        });
        try {
            helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.kitLevel("falleneden:rental/25/paladin_holy") == 25
                && net.ledok.arenas_ld.util.RunRentals.kitLevel("falleneden:rental/0/warrior") == 0
                && net.ledok.arenas_ld.util.RunRentals.kitLevel("falleneden:rental/test/paladin_lvl_5") == -1
                && net.ledok.arenas_ld.util.RunRentals.kitLevel(null) == -1,
                "a preset's level is its folder");
            helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.classOf("falleneden:rental/25/paladin_holy").equals("paladin")
                && net.ledok.arenas_ld.util.RunRentals.classOf("falleneden:rental/25/rogue").equals("rogue"),
                "a preset's class is its name up to the first _");
            helper.assertTrue(isKey(net.ledok.arenas_ld.util.RunRentals.choose(player, BlockPos.ZERO, "rogue"),
                "message.arenas_ld.rental.not_in_lobby"), "no pick outside a waiting lobby");

            var participants = new java.util.LinkedHashMap<UUID, net.ledok.arenas_ld.dungeon.run.RunParticipant>();
            participants.put(player.getUUID(), new net.ledok.arenas_ld.dungeon.run.RunParticipant(player.getUUID(),
                player.getScoreboardName(), net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE, 0L, 0));
            boolean[] over = {false};
            var stats = new net.ledok.arenas_ld.stats.RunStats(
                new net.ledok.arenas_ld.util.DownedSpectators.RunView(new Object(), () -> participants, () -> over[0],
                    uuid -> false, (time, uuid) -> -1L),
                () -> new net.ledok.arenas_ld.stats.RunDetailsSource(null, net.minecraft.network.chat.Component.literal("Test"),
                    "raid", DifficultyTier.NORMAL, new ArenasApi.InstanceRef(level.dimension(), helper.absolutePos(BlockPos.ZERO)),
                    false, List.of(), level.getGameTime(), 0, 0, ""));
            var counters = stats.counters(player.getUUID(), player.getScoreboardName());
            helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.rewardModifiers(player.getUUID()).isEmpty(),
                "own gear: no reward cut");

            counters.loadoutId = "test:rental/20/rogue_shadow"; // rented before a restart (saved with the run)
            counters.rentalClass = "rogue";
            counters.rentalBand = 20;
            var cut = net.ledok.arenas_ld.util.RunRentals.rewardModifiers(player.getUUID());
            helper.assertTrue(cut.size() == 3 && cut.stream().allMatch(m -> m.kind() == net.ledok.arenas_ld.rewards.RewardModifier.Kind.MORE
                && m.value() == 0.5), "a renter's loot, XP and currency are halved: " + cut);
            player.tickCount = 0;
            net.ledok.arenas_ld.util.RunRentals.onRunPlayer(stats, counters, player);
            helper.assertTrue(calls.isEmpty(), "not right at login: Eden's own login check goes first");
            player.tickCount = 10;
            net.ledok.arenas_ld.util.RunRentals.onRunPlayer(stats, counters, player);
            helper.assertTrue(calls.equals(List.of("begin arenas:" + stats.sessionKey() + " test:rental/20/rogue_shadow")),
                "begun again with the run's session: " + calls);
            net.ledok.arenas_ld.util.RunRentals.onRunPlayer(stats, counters, player);
            helper.assertTrue(calls.size() == 1, "renting already: not begun twice");
            net.ledok.arenas_ld.util.RunRentals.reequip(player);
            helper.assertTrue(calls.get(calls.size() - 1).equals("reequip"), "a respawn hands the loadout out again");

            var saved = counters.toRecord(0, "alive");
            helper.assertTrue(saved.loadoutId.equals("test:rental/20/rogue_shadow") && saved.rentalClass.equals("rogue")
                && saved.rentalBand == 20, "the run record names the loadout, class and band");
            over[0] = true;
            stats.end(level.getServer());
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(calls.contains("end") && !renting[0], "the run is over: the rental ends: " + calls);
                ArenasApi.setRentalProvider(before);
                level.getServer().getPlayerList().remove(player);
                helper.succeed();
            });
        } catch (RuntimeException e) {
            ArenasApi.setRentalProvider(before);
            level.getServer().getPlayerList().remove(player);
            throw e;
        }
    }

    /**
     * Rental in a dungeon (4.26.4 fix): a dungeon removes its lobby when the run starts, so the pick made in the lobby
     * must survive that and be taken by the run's stats tick; a pick whose player left the lobby without a run goes.
     */
    public static void rentalDungeonPick(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            String kit = "test:rental/0/rogue_shadow";
            var calls = new java.util.ArrayList<String>();
            boolean[] renting = {false};
            ArenasApi.RentalProvider before = ArenasApi.rentalProvider();
            ArenasApi.setRentalProvider(new ArenasApi.RentalProvider() {
                public List<String> presets(int lvl) { return List.of(kit); }
                public String displayName(String k) { return "Shadow rogue"; }
                public boolean begin(ServerPlayer p, String session, String k) { calls.add("begin " + k); renting[0] = true; return true; }
                public void end(MinecraftServer s, UUID uuid) { calls.add("end"); renting[0] = false; }
                public void reequip(ServerPlayer p) { }
                public boolean isRenting(ServerPlayer p) { return renting[0]; }
                public List<net.minecraft.world.item.ItemStack> preview(String k) { return List.of(); }
            });
            Runnable cleanup = () -> {
                ArenasApi.setRentalProvider(before);
                server.getPlayerList().remove(player);
            };
            try {
                helper.assertTrue(controller.createLobby(player).isPresent(), "lobby created");
                helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.choose(player, helper.absolutePos(DUNGEON), kit) == null,
                    "a preset is picked in the dungeon lobby");

                // The run starts: the dungeon drops its lobby and the player is in the run.
                var participants = new java.util.LinkedHashMap<UUID, net.ledok.arenas_ld.dungeon.run.RunParticipant>();
                participants.put(player.getUUID(), new net.ledok.arenas_ld.dungeon.run.RunParticipant(player.getUUID(),
                    player.getScoreboardName(), net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE, 0L, 0));
                var stats = new net.ledok.arenas_ld.stats.RunStats(
                    new net.ledok.arenas_ld.util.DownedSpectators.RunView(new Object(), () -> participants, () -> false,
                        uuid -> false, (time, uuid) -> -1L),
                    () -> new net.ledok.arenas_ld.stats.RunDetailsSource(null, net.minecraft.network.chat.Component.literal("Test"),
                        "dungeon", DifficultyTier.NORMAL, new ArenasApi.InstanceRef(level.dimension(), helper.absolutePos(DUNGEON)),
                        false, List.of(), level.getGameTime(), 0, 0, ""));
                helper.assertTrue(controller.leaveLobby(player.getUUID()), "the lobby is gone (as at a dungeon start)");
            } catch (RuntimeException e) {
                cleanup.run();
                throw e;
            }
            // Past a lobby sync sweep: the pick must still be there for the run.
            helper.runAfterDelay(25, () -> {
                try {
                    var stats = net.ledok.arenas_ld.stats.RunDetails.runOf(player.getUUID());
                    helper.assertTrue(stats != null, "the player is in the run");
                    var counters = stats.counters(player.getUUID(), player.getScoreboardName());
                    net.ledok.arenas_ld.util.RunRentals.onRunPlayer(stats, counters, player);
                    helper.assertTrue(calls.equals(List.of("begin " + kit)) && kit.equals(counters.loadoutId)
                        && counters.rentalClass.equals("rogue") && counters.rentalBand == 0,
                        "the dungeon run begins the rental: " + calls);
                    stats.end(server);
                } finally {
                    cleanup.run();
                }
                helper.succeed();
            });
        });
    }

    /**
     * 4.39.0 run settings: a dungeon controller's rental mode and base loot rolls save and load; "rental kits only"
     * refuses own gear, getting ready and the start until every member picked; a required-rental run pays renters in
     * full; the run details' base rolls follow the controller.
     */
    public static void runSettings(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            BlockPos pos = helper.absolutePos(DUNGEON);
            String kit = "test:rental/0/rogue_shadow";
            ArenasApi.RentalProvider before = ArenasApi.rentalProvider();
            ArenasApi.setRentalProvider(new ArenasApi.RentalProvider() {
                public List<String> presets(int lvl) { return List.of(kit); }
                public String displayName(String k) { return "Shadow rogue"; }
                public boolean begin(ServerPlayer p, String session, String k) { return true; }
                public void end(MinecraftServer s, UUID uuid) { }
                public void reequip(ServerPlayer p) { }
                public boolean isRenting(ServerPlayer p) { return false; }
                public List<net.minecraft.world.item.ItemStack> preview(String k) { return List.of(); }
            });
            try {
                var settings = new net.ledok.arenas_ld.util.RunSettings(net.ledok.arenas_ld.util.RunSettings.REQUIRED, 9);
                var tag = new net.minecraft.nbt.CompoundTag();
                settings.save(tag);
                helper.assertTrue(net.ledok.arenas_ld.util.RunSettings.load(tag).equals(settings), "settings save and load");
                var none = new net.minecraft.nbt.CompoundTag();
                net.ledok.arenas_ld.util.RunSettings.DEFAULT.save(none);
                helper.assertTrue(none.isEmpty() && net.ledok.arenas_ld.util.RunSettings.load(none).equals(
                    net.ledok.arenas_ld.util.RunSettings.DEFAULT), "the default writes nothing");
                helper.assertTrue(new net.ledok.arenas_ld.util.RunSettings("junk", -5).equals(
                    net.ledok.arenas_ld.util.RunSettings.DEFAULT), "unknown mode / negative rolls = the default");
                helper.assertTrue(net.ledok.arenas_ld.util.RunSettings.DEFAULT.lootRolls()
                    == net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().completion_loot_rolls, "no own rolls: the global");
                controller.setRunSettings(settings);

                helper.assertTrue(controller.createLobby(player).isPresent(), "lobby created");
                var lobby = controller.getLobbies().stream().filter(l -> l.isOwner(player.getUUID())).findFirst().orElseThrow();
                helper.assertTrue(isKey(net.ledok.arenas_ld.util.RunRentals.choose(player, pos, ""),
                    "message.arenas_ld.rental.required_no_own_gear"), "rental only: own gear is refused");
                helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.missingPicks(controller, lobby).size() == 1,
                    "nobody picked yet");
                helper.assertTrue(isKey(net.ledok.arenas_ld.activity.EntryGates.partyRefusal(server, lobby, controller,
                    DifficultyTier.NORMAL), "message.arenas_ld.rental.start_needs_picks"), "the start waits for picks");
                helper.assertFalse(controller.toggleReady(player), "no ready without a pick");
                helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.choose(player, pos, kit) == null, "a kit is picked");
                helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.missingPicks(controller, lobby).isEmpty(),
                    "everyone picked");
                helper.assertTrue(controller.toggleReady(player), "ready after the pick");

                // A required-rental run pays its renters in full; its record says so; the details use 9 base rolls.
                var participants = new java.util.LinkedHashMap<UUID, net.ledok.arenas_ld.dungeon.run.RunParticipant>();
                participants.put(player.getUUID(), new net.ledok.arenas_ld.dungeon.run.RunParticipant(player.getUUID(),
                    player.getScoreboardName(), net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE, 0L, 0));
                var stats = new net.ledok.arenas_ld.stats.RunStats(
                    new net.ledok.arenas_ld.util.DownedSpectators.RunView(new Object(), () -> participants, () -> false,
                        uuid -> false, (time, uuid) -> -1L),
                    () -> new net.ledok.arenas_ld.stats.RunDetailsSource(null, net.minecraft.network.chat.Component.literal("Test"),
                        "dungeon", DifficultyTier.NORMAL, new ArenasApi.InstanceRef(level.dimension(), pos),
                        false, List.of(), level.getGameTime(), 0, 0, "", -1, 0, 9));
                stats.load("{\"rentalMode\":\"required\"}");
                var counters = stats.counters(player.getUUID(), player.getScoreboardName());
                counters.loadoutId = kit;
                helper.assertTrue(stats.rentalRequired(), "the run keeps the controller's mode");
                helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.rewardModifiers(player.getUUID()).isEmpty(),
                    "rental only: no reward cut");
                var record = stats.snapshot(server);
                helper.assertTrue(record.rentalMode.equals("required") && record.baseLootRolls == 9,
                    "the record has the mode and base rolls: " + record.rentalMode + " " + record.baseLootRolls);
                helper.assertTrue(stats.source().lootRolls() == 9, "the details use the controller's base rolls");
                stats.end(server);

                // 4.40.0: "settings" is its own branch of /arenasld activity (4.39.0 hung it under "breakable").
                var source = server.createCommandSourceStack();
                for (String command : List.of("arenasld activity settings arenas_ld:gt_x",
                    "arenasld activity settings arenas_ld:gt_x rental required",
                    "arenasld activity settings arenas_ld:gt_x loot_rolls 4.5",
                    "arenasld activity settings arenas_ld:gt_x loot_rolls default")) {
                    var parse = server.getCommands().getDispatcher().parse(command, source);
                    helper.assertTrue(parse.getExceptions().isEmpty() && !parse.getReader().canRead()
                        && parse.getContext().getCommand() != null, "the command parses: " + command);
                }

                // Rental round 2: the kit level never goes above the run level; spell picks stay inside the book.
                helper.assertTrue(net.ledok.arenas_ld.util.RunRentals.capped(player, 15)
                    <= 15 && net.ledok.arenas_ld.util.RunRentals.capped(player, -1) >= 0, "the kit level cap");
                helper.assertTrue(net.ledok.arenas_ld.stats.RunGear.levelOf("falleneden:arenas/level_20") == 20,
                    "a difficulty's run level is its loot table's");
                var book = new net.ledok.arenas_ld.util.RentalSpells.Choice("ns:pool", 3, List.of("ns:a", "ns:b", "ns:c"),
                    List.of("ns:a", "ns:b", "ns:c", "ns:d", "ns:e"));
                helper.assertTrue(net.ledok.arenas_ld.util.RentalSpells.validate(
                        List.of("ns:d", "ns:x", "ns:d", "ns:a", "ns:b", "ns:e"), book).equals(List.of("ns:d", "ns:a", "ns:b")),
                    "picks keep the pool's spells, no repeats, at most the book's size");
                var tiers = net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().rental_spell_max_tier;
                var tiersBefore = new java.util.LinkedHashMap<>(tiers);
                try {
                    tiers.clear();
                    helper.assertTrue(net.ledok.arenas_ld.util.RentalSpells.maxTierFor(30) == -1, "no rule: the whole pool");
                    tiers.put("0", 1);
                    tiers.put("20", 2);
                    helper.assertTrue(net.ledok.arenas_ld.util.RentalSpells.maxTierFor(15) == 1
                        && net.ledok.arenas_ld.util.RentalSpells.maxTierFor(25) == 2, "the highest folder at or below the kit's level");
                } finally {
                    tiers.clear();
                    tiers.putAll(tiersBefore);
                }
            } finally {
                controller.setRunSettings(net.ledok.arenas_ld.util.RunSettings.DEFAULT);
                net.ledok.arenas_ld.util.RunRentals.choose(player, pos, "");
                ArenasApi.setRentalProvider(before);
                server.getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * 4.27.1: a long run's stats JSON (over 65,535 bytes) goes into the save as parts, the save's NBT writes to disk
     * (an NBT string over the limit threw and the chunk didn't save), and it reads back the same. Short JSON stays a
     * plain string, as older saves have it.
     */
    public static void runStatsSave(GameTestHelper helper) {
        StringBuilder big = new StringBuilder("{\"version\":1,\"players\":[");
        while (big.length() < 150_000) {
            big.append("{\"name\":\"Лицар\u00e9\uD83D\uDE00\"},");
        }
        String json = big.append("{}]}").toString();
        var ops = net.minecraft.nbt.NbtOps.INSTANCE;
        var tag = net.ledok.arenas_ld.stats.RunStatsSave.CODEC.encodeStart(ops, json).getOrThrow();
        helper.assertTrue(tag instanceof net.minecraft.nbt.ListTag, "long stats JSON should be saved as parts");
        net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
        root.put("runStats", tag);
        try {
            java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.ByteArrayOutputStream());
            net.minecraft.nbt.NbtIo.write(root, out);
        } catch (java.io.IOException e) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("stats save failed to write: " + e);
        }
        String back = net.ledok.arenas_ld.stats.RunStatsSave.CODEC.parse(ops, tag).getOrThrow();
        helper.assertTrue(json.equals(back), "stats JSON should read back unchanged");
        var small = net.ledok.arenas_ld.stats.RunStatsSave.CODEC.encodeStart(ops, "{\"version\":1}").getOrThrow();
        helper.assertTrue(small instanceof net.minecraft.nbt.StringTag, "short stats JSON should stay a string");
        helper.assertTrue("{\"version\":1}".equals(net.ledok.arenas_ld.stats.RunStatsSave.CODEC.parse(ops,
            net.minecraft.nbt.StringTag.valueOf("{\"version\":1}")).getOrThrow()), "an old string save should read");
        helper.succeed();
    }

    /** Run states (4.27.0): /exit mid-run is "forfeited" even after the run ends; everyone else in a won run "won". */
    public static void runStates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer stayer = helper.makeMockServerPlayerInLevel();
        ServerPlayer leaver = helper.makeMockServerPlayerInLevel();
        try {
            var participants = new java.util.LinkedHashMap<UUID, net.ledok.arenas_ld.dungeon.run.RunParticipant>();
            for (ServerPlayer p : List.of(stayer, leaver)) {
                participants.put(p.getUUID(), new net.ledok.arenas_ld.dungeon.run.RunParticipant(p.getUUID(),
                    p.getScoreboardName(), net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE, 0L, 0));
            }
            String[] result = {""};
            var stats = new net.ledok.arenas_ld.stats.RunStats(
                new net.ledok.arenas_ld.util.DownedSpectators.RunView(new Object(), () -> participants, () -> false,
                    uuid -> false, (time, uuid) -> -1L),
                () -> new net.ledok.arenas_ld.stats.RunDetailsSource(null, net.minecraft.network.chat.Component.literal("Test"),
                    "dungeon", DifficultyTier.NORMAL, new ArenasApi.InstanceRef(level.dimension(), helper.absolutePos(BlockPos.ZERO)),
                    false, List.of(), level.getGameTime(), 0, 0, result[0]));
            net.ledok.arenas_ld.stats.RunStats.markForfeit(leaver.getUUID());
            participants.put(leaver.getUUID(), participants.get(leaver.getUUID())
                .withStatus(net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.REMOVED, 0L));
            java.util.function.Function<ServerPlayer, String> state = p -> stats.snapshot(level.getServer()).players.stream()
                .filter(r -> r.uuid.equals(p.getUUID().toString())).findFirst().orElseThrow().state;
            helper.assertTrue(state.apply(leaver).equals("forfeited") && state.apply(stayer).equals("alive"),
                "while it runs: forfeited / alive");
            result[0] = "win";
            helper.assertTrue(state.apply(leaver).equals("forfeited") && state.apply(stayer).equals("won"),
                "a won run: the leaver stays forfeited, the rest won");
            result[0] = "fail";
            helper.assertTrue(state.apply(stayer).equals("lost"), "a failed run: lost");
            stats.end(level.getServer());
        } finally {
            level.getServer().getPlayerList().remove(stayer);
            level.getServer().getPlayerList().remove(leaver);
        }
        helper.succeed();
    }

    // ── Admin API ────────────────────────────────────────────────────────────

    /** Location from the index; rename's limits and its single listener call; forceEnd with nothing running. */
    public static void adminRename(GameTestHelper helper) {
        registerHooksOnce();
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = helper.getLevel().getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_admin_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");

            ArenasApi.ControllerLocation location = ArenasApi.location(server, id).orElse(null);
            helper.assertTrue(location != null && location.dimension().equals(helper.getLevel().dimension())
                && location.pos().equals(helper.absolutePos(DUNGEON)), "location from the index: " + location);
            helper.assertTrue(ArenasApi.location(server, MISSING).isEmpty(), "no location for an unknown id");

            helper.assertTrue(ArenasApi.rename(server, id, "   ") != null, "a blank name is refused");
            helper.assertTrue(ArenasApi.rename(server, id, "x".repeat(49)) != null, "a name over 48 characters is refused");
            helper.assertTrue(controller.getDungeonName().isEmpty(), "a refused name changes nothing");
            int before = ACTIVITY_EVENTS.get();
            helper.assertTrue(ArenasApi.rename(server, id, "  Sunken Crypt  ") == null, "a valid name is accepted");
            int fired = ACTIVITY_EVENTS.get() - before;
            helper.assertTrue(fired == 1, "the activities listener fired once, not " + fired);
            helper.assertTrue(controller.getDungeonName().equals("Sunken Crypt"), "the controller has the trimmed name");
            helper.assertTrue(ArenasApi.activity(server, id).orElseThrow().name().getString().equals("Sunken Crypt"),
                "the API lists the new name");
            int beforeSame = ACTIVITY_EVENTS.get();
            helper.assertTrue(ArenasApi.rename(server, id, "Sunken Crypt") == null && ACTIVITY_EVENTS.get() == beforeSame,
                "the same name again fires nothing");
            helper.assertTrue(ArenasApi.rename(server, id, "x".repeat(48)) == null
                && controller.getDungeonName().length() == 48, "48 characters fit");
            helper.assertTrue(ArenasApi.rename(server, MISSING, "Crypt") != null, "an unknown id is refused");

            ArenasApi.InstanceRef nowhere = new ArenasApi.InstanceRef(helper.getLevel().dimension(),
                helper.absolutePos(new BlockPos(1, 1, 5)));
            helper.assertTrue(isKey(ArenasApi.forceEnd(server, id, null), "message.arenas_ld.admin.nothing_running"),
                "nothing to end: refused");
            helper.assertTrue(isKey(ArenasApi.forceEnd(server, id, nowhere), "message.arenas_ld.admin.no_run_at"),
                "no run at that instance: refused");
            helper.assertTrue(isKey(ArenasApi.forceEnd(server, MISSING, null), "message.arenas_ld.activity.unknown"),
                "an unknown id is refused");
            helper.succeed();
        });
    }

    /** Forget refuses while the controller is there and drops an entry whose controller is gone. */
    public static void adminForget(GameTestHelper helper) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_forget_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            helper.assertTrue(isKey(ArenasApi.forget(server, id), "message.arenas_ld.admin.controller_present"),
                "refused while the controller is there");
            helper.assertTrue(ArenasApi.activity(server, id).isPresent(), "still listed");

            // A controller removed without a block update leaves its entry behind. Right above the
            // real one, so letting go of the gone one's chunk keeps the test area's chunk loaded.
            ResourceLocation ghostId = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_ghost_" + suffix());
            ghostController(level, helper.absolutePos(DUNGEON.above()), ghostId);
            ArenasApi.ActivityStatus gone = ArenasApi.status(server, ghostId).orElse(null);
            helper.assertTrue(gone != null && gone.warnings().contains(ArenasApi.Warning.CONTROLLER_MISSING)
                && gone.instances().isEmpty() && gone.runs().isEmpty(), "a gone controller is reported missing: " + gone);
            helper.assertTrue(ArenasApi.forget(server, ghostId) == null, "a gone controller's activity is forgotten");
            helper.assertTrue(ArenasApi.activity(server, ghostId).isEmpty() && ArenasApi.status(server, ghostId).isEmpty()
                && ArenasApi.location(server, ghostId).isEmpty(), "unknown afterwards");
            helper.assertTrue(isKey(ArenasApi.forget(server, ghostId), "message.arenas_ld.activity.unknown"),
                "forgetting it again: unknown");
            helper.assertTrue(level.getBlockEntity(helper.absolutePos(DUNGEON)) == controller
                && ArenasApi.activity(server, id).isPresent(), "the real controller is untouched");
            helper.succeed();
        });
    }

    /**
     * Status of fresh controllers (generated id, no name, no instance; no difficulties only for a
     * dungeon or raid with none enabled), and of a dungeon set up by staff with one free instance.
     */
    public static void adminStatus(GameTestHelper helper) {
        BlockPos dbsRel = new BlockPos(1, 1, 5);
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.setBlock(RAID, BlockRegistry.RAID_CONTROLLER_BLOCK);
        helper.setBlock(ARENA, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.setBlock(dbsRel, BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            DungeonControllerBlockEntity dungeon = helper.getBlockEntity(DUNGEON);
            for (BlockPos rel : List.of(DUNGEON, RAID, ARENA)) {
                ActivityHost host = (ActivityHost) level.getBlockEntity(helper.absolutePos(rel));
                ArenasApi.Activity activity = ArenasApi.activity(server, host.activityId()).orElseThrow();
                EnumSet<ArenasApi.Warning> expected = EnumSet.of(ArenasApi.Warning.GENERATED_ID,
                    ArenasApi.Warning.UNNAMED, ArenasApi.Warning.NO_INSTANCES);
                if (activity.type() != ArenasApi.ActivityType.ARENA && activity.difficulties().isEmpty()) {
                    expected.add(ArenasApi.Warning.NO_DIFFICULTIES);
                }
                ArenasApi.ActivityStatus status = ArenasApi.status(server, host.activityId()).orElse(null);
                helper.assertTrue(status != null && status.activity().equals(host.activityId()), "a status for " + activity.id());
                helper.assertTrue(status.warnings().equals(expected),
                    activity.type() + " fresh: " + status.warnings() + ", expected " + expected);
                helper.assertTrue(status.instances().isEmpty() && status.runs().isEmpty() && status.openLobbies() == 0
                    && status.queuedLobbies() == 0 && status.waitingPlayers() == 0, activity.type() + " is idle: " + status);
            }
            helper.assertTrue(ArenasApi.status(server, MISSING).isEmpty(), "no status for an unknown id");

            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_status_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(dungeon, id.toString()) == null, "test id set");
            helper.assertTrue(!ArenasApi.status(server, id).orElseThrow().warnings().contains(ArenasApi.Warning.GENERATED_ID),
                "an id set by staff is not generated");
            dungeon.setActivityName("Status Crypt");
            BlockPos dbsPos = helper.absolutePos(dbsRel);
            helper.assertTrue(dungeon.addInstance(dbsPos, level.dimension()), "instance linked");
            for (DifficultyTier tier : DifficultyTier.values()) {
                TierConfig config = dungeon.getTierConfigs().get(tier);
                dungeon.setTierConfig(tier, new TierConfig(config.healthMultiplier(), config.damageMultiplier(),
                    config.perPlayerLootTable(), config.dungeonTimeSeconds(), false, config.rewardCurrency(),
                    config.skillExperiencePerWin()));
            }
            ArenasApi.ActivityStatus set = ArenasApi.status(server, id).orElseThrow();
            helper.assertTrue(set.warnings().equals(EnumSet.of(ArenasApi.Warning.NO_DIFFICULTIES)),
                "named, linked, every difficulty off: " + set.warnings());
            helper.assertTrue(set.instances().equals(List.of(new ArenasApi.InstanceStatus(
                    new ArenasApi.InstanceRef(level.dimension(), dbsPos), ArenasApi.InstanceState.FREE, 0, false))),
                "one free instance: " + set.instances());
            helper.assertTrue(set.runs().isEmpty() && set.pendingInstanceRemovals() == 0, "nothing running");
            helper.succeed();
        });
    }

    /** An entry pointing into an unloaded chunk reports CONTROLLER_NOT_LOADED and leaves the chunk unloaded. */
    public static void adminStatusUnloaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos near = helper.absolutePos(BlockPos.ZERO);
        BlockPos far = new BlockPos(near.getX() + 20000, near.getY(), near.getZ() - 20000);
        helper.assertTrue(!level.isLoaded(far), "the far chunk starts unloaded");
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_unloaded_" + suffix());
        ghostController(level, far, id);
        try {
            ArenasApi.ActivityStatus status = ArenasApi.status(server, id).orElse(null);
            helper.assertTrue(status != null && status.warnings().contains(ArenasApi.Warning.CONTROLLER_NOT_LOADED)
                && !status.warnings().contains(ArenasApi.Warning.CONTROLLER_MISSING), "not loaded: " + status);
            helper.assertTrue(status.instances().isEmpty() && status.runs().isEmpty() && status.openLobbies() == 0,
                "nothing else is known about it");
            helper.assertTrue(far.equals(ArenasApi.location(server, id).map(ArenasApi.ControllerLocation::pos).orElse(null)),
                "its location is still known");
            helper.assertTrue(!level.isLoaded(far) && level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null,
                "status and location loaded no chunk");
        } finally {
            ActivityRegistry.detach(level, far); // drops the entry without touching the chunk
        }
        helper.succeed();
    }

    /**
     * openAdmin: refused for a non-op; an op gets a bound remote admin screen that stays valid out
     * of reach (one opened at the block does not), and every screen closes once deopped.
     *
     * @param moddedClient lets the test player's connection take Arenas_LD's payloads (NeoForge
     *                     refuses payloads a client never registered)
     */
    public static void adminScreen(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        helper.setBlock(DUNGEON, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(DUNGEON);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_screen_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(5, 1, 5)));
            ServerPlayer op = survivalPlayer(helper, helper.absolutePos(new BlockPos(4, 1, 4)));
            moddedClient.accept(op);
            var ops = server.getPlayerList().getOps();
            try {
                helper.assertTrue(isKey(ArenasApi.openAdmin(player, id), "message.arenas_ld.admin.no_permission"),
                    "a non-op is refused");
                helper.assertTrue(!(player.containerMenu instanceof AdminMenuBase), "and gets no screen");

                // The gametest server makes ops level 0; give this one level 2 by hand.
                ops.add(new ServerOpListEntry(op.getGameProfile(), 2, false));
                helper.assertTrue(op.hasPermissions(2), "op level 2");
                helper.assertTrue(isKey(ArenasApi.openAdmin(op, MISSING), "message.arenas_ld.activity.unknown"),
                    "an unknown id is refused");
                helper.assertTrue(ArenasApi.openAdmin(op, id) == null, "an op opens it");
                AdminMenuBase remote = op.containerMenu instanceof AdminMenuBase menu ? menu : null;
                helper.assertTrue(remote != null && remote.shows(level.dimension(), controller.getBlockPos())
                    && level.dimension().equals(remote.boundDimension()), "an admin screen bound to the controller: " + op.containerMenu);
                helper.assertTrue(AdminScreens.viewers(controller).contains(op), "the op gets its admin snapshots");
                Vec3 home = op.position();
                op.setPos(home.x, home.y + 40, home.z); // well out of reach, above the test area
                helper.assertTrue(remote.stillValid(op), "a remote screen stays open out of reach");

                helper.assertTrue(AdminScreens.open(op, controller, false) == null, "opened as at the block");
                AdminMenuBase local = op.containerMenu instanceof AdminMenuBase menu ? menu : null;
                helper.assertTrue(local != null && local != remote && !local.stillValid(op),
                    "a screen opened at the block closes out of reach");
                op.setPos(home.x, home.y, home.z);
                helper.assertTrue(local.stillValid(op), "and stays open within reach");

                ops.remove(op.getGameProfile());
                helper.assertTrue(!local.stillValid(op) && !remote.stillValid(op), "deopped: every admin screen closes");
                helper.assertTrue(!AdminScreens.viewers(controller).contains(op), "and gets no more snapshots");
            } finally {
                ops.remove(op.getGameProfile());
                server.getPlayerList().remove(player);
                server.getPlayerList().remove(op);
            }
            helper.succeed();
        });
    }

    /** Teleport: next to the controller on a free 2-high spot, facing it; diagonals once the sides are walled; busy players stay. */
    public static void adminTeleport(GameTestHelper helper) {
        BlockPos controllerRel = new BlockPos(3, 1, 3);
        // A floor around the controller: the test area's own ground is a layer lower.
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(controllerRel, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ArenaControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_tp_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            BlockPos controllerPos = helper.absolutePos(controllerRel);
            List<BlockPos> sides = Direction.Plane.HORIZONTAL.stream().map(controllerPos::relative).toList();
            ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(6, 1, 6)));
            String busyReason = "arenas_ld:gametest_" + suffix();
            try {
                helper.assertTrue(ArenasApi.teleportToController(player, id) == null, "teleported");
                BlockPos feet = player.blockPosition();
                helper.assertTrue(sides.contains(feet), "beside the controller at " + controllerPos + ": " + feet);
                assertStandsFacing(helper, player, controllerPos);

                // Walls on all four sides, two high: a diagonal comes next.
                for (Direction side : Direction.Plane.HORIZONTAL) {
                    helper.setBlock(controllerRel.relative(side), Blocks.STONE);
                    helper.setBlock(controllerRel.relative(side).above(), Blocks.STONE);
                }
                helper.assertTrue(ArenasApi.teleportToController(player, id) == null, "teleported again");
                feet = player.blockPosition();
                helper.assertTrue(feet.getY() == controllerPos.getY() && Math.abs(feet.getX() - controllerPos.getX()) == 1
                    && Math.abs(feet.getZ() - controllerPos.getZ()) == 1, "on a diagonal: " + feet);
                assertStandsFacing(helper, player, controllerPos);

                helper.assertTrue(isKey(ArenasApi.teleportToController(player, MISSING), "message.arenas_ld.activity.unknown"),
                    "an unknown id is refused");

                BusyStateCompat.setBusy(player.getUUID(), busyReason);
                if (BusyStateCompat.isBusy(player.getUUID())) { // BusyLib is in both dev runs
                    Vec3 before = player.position();
                    Component refusal = ArenasApi.teleportToController(player, id);
                    helper.assertTrue(isKey(refusal, "message.arenas_ld.admin.teleport_busy_because")
                            && Arrays.asList(((TranslatableContents) refusal.getContents()).getArgs()).contains(busyReason),
                        "a busy player is refused, with the reason: " + refusal);
                    helper.assertTrue(player.position().equals(before), "and stays put");
                }
            } finally {
                BusyStateCompat.clearBusy(player.getUUID(), busyReason);
                server.getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    private static void assertStandsFacing(GameTestHelper helper, ServerPlayer player, BlockPos controllerPos) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = player.blockPosition();
        helper.assertTrue(Math.abs(player.getX() - (feet.getX() + 0.5)) < 1.0E-6 && Math.abs(player.getZ() - (feet.getZ() + 0.5)) < 1.0E-6
            && player.getY() == feet.getY(), "at the block center, on the ground: " + player.position());
        helper.assertTrue(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir()
            && level.getBlockState(feet.below()).is(Blocks.STONE), "two free blocks over solid ground at " + feet);
        Vec3 toController = Vec3.atCenterOf(controllerPos).subtract(player.getEyePosition()).normalize();
        double facing = player.getViewVector(1.0F).dot(toController);
        helper.assertTrue(facing > 0.99, "facing the controller (" + facing + ")");
    }

    /**
     * A real arena run ended by staff: status shows it, a player in it can't teleport, another
     * instance is refused; ending it reports FORCED once and leaves it closing, and ending it again
     * closes it at once without a second report.
     *
     * @param moddedClient see {@link #adminScreen}: the run player gets the run HUD
     */
    public static void adminForceEnd(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        registerHooksOnce();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        BlockPos spawnerRel = new BlockPos(5, 1, 5);
        helper.setBlock(controllerRel, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.ARENA_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ArenaControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ArenaSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_stop_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            BlockPos spawnerPos = helper.absolutePos(spawnerRel);
            spawner.setEntrancePosition(helper.absolutePos(new BlockPos(5, 1, 3)), level.dimension());
            helper.assertTrue(controller.addInstance(spawnerPos, level.dimension()), "instance linked");
            ArenasApi.InstanceRef instance = new ArenasApi.InstanceRef(level.dimension(), spawnerPos);
            ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 6)));
            moddedClient.accept(player);
            try {
                helper.assertTrue(controller.createLobby(player), "lobby created");
                ArenasApi.ActivityStatus waiting = ArenasApi.status(server, id).orElseThrow();
                helper.assertTrue(waiting.openLobbies() == 1 && waiting.waitingPlayers() == 1 && waiting.runs().isEmpty(),
                    "one open lobby, nobody playing: " + waiting);

                helper.assertTrue(controller.startRun(player), "the run starts");
                ArenasApi.ActivityStatus running = ArenasApi.status(server, id).orElseThrow();
                helper.assertTrue(running.runs().size() == 1 && running.runs().get(0).instance().equals(instance)
                    && running.runs().get(0).players().equals(List.of(player.getUUID())) && !running.runs().get(0).closing()
                    && running.runs().get(0).difficulty() == null, "one arena run going: " + running.runs());
                helper.assertTrue(running.instances().size() == 1
                    && running.instances().get(0).state() == ArenasApi.InstanceState.RUNNING, "its instance runs: " + running.instances());
                helper.assertTrue(running.openLobbies() == 0, "its lobby is in the run, not open");
                helper.assertTrue(isKey(ArenasApi.teleportToController(player, id), "message.arenas_ld.admin.teleport_in_run"),
                    "a player in a run can't teleport");

                ArenasApi.InstanceRef other = new ArenasApi.InstanceRef(level.dimension(), spawnerPos.above(3));
                helper.assertTrue(isKey(ArenasApi.forceEnd(server, id, other), "message.arenas_ld.admin.no_run_at"),
                    "another instance: refused");
                helper.assertTrue(results(id).isEmpty() && !ArenasApi.status(server, id).orElseThrow().runs().get(0).closing(),
                    "and nothing ended");

                helper.assertTrue(ArenasApi.forceEnd(server, id, instance) == null, "staff end the run");
                List<ArenasApi.RunResult> results = results(id);
                helper.assertTrue(results.size() == 1 && results.get(0).outcome() == ArenasApi.Outcome.FORCED
                    && results.get(0).players().contains(player.getUUID()), "reported once, as FORCED: " + results);
                ArenasApi.ActivityStatus closing = ArenasApi.status(server, id).orElseThrow();
                helper.assertTrue(closing.runs().size() == 1 && closing.runs().get(0).closing(),
                    "on its exit timer: " + closing.runs());

                helper.assertTrue(ArenasApi.forceEnd(server, id, null) == null, "a closing run is closed at once");
                helper.assertTrue(results(id).size() == 1, "closing it reports nothing more");
                ArenasApi.ActivityStatus done = ArenasApi.status(server, id).orElseThrow();
                helper.assertTrue(done.runs().isEmpty() && done.instances().size() == 1
                    && done.instances().get(0).state() != ArenasApi.InstanceState.RUNNING, "no run left: " + done);
                helper.assertTrue(!RunItemRestrictions.isPlayerInRun(player), "the player is out of the run");
                helper.assertTrue(isKey(ArenasApi.forceEnd(server, id, null), "message.arenas_ld.admin.nothing_running"),
                    "nothing left to end");
            } finally {
                server.getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * The only player of an arena run disconnects: the run holds still within disconnect grace
     * instead of being lost on the spot, and when they come back they stand in it again (not
     * downed, which with nobody else there would read as a wipe).
     */
    public static void arenaDisconnectGrace(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        registerHooksOnce();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        BlockPos spawnerRel = new BlockPos(5, 1, 5);
        helper.setBlock(controllerRel, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.ARENA_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ArenaControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ArenaSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_grace_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            spawner.setEntrancePosition(helper.absolutePos(new BlockPos(5, 1, 3)), level.dimension());
            helper.assertTrue(controller.addInstance(helper.absolutePos(spawnerRel), level.dimension()), "instance linked");
            BlockPos at = helper.absolutePos(new BlockPos(3, 1, 6));
            ServerPlayer player = survivalPlayer(helper, at);
            moddedClient.accept(player);
            UUID uuid = player.getUUID();
            helper.assertTrue(controller.createLobby(player) && controller.startRun(player), "the run starts");
            server.getPlayerList().remove(player); // the connection drops
            helper.runAfterDelay(40, () -> {
                helper.assertTrue(results(id).isEmpty(), "the run is not lost while its player is away: " + results(id));
                helper.assertTrue(controller.getActiveRuns().size() == 1, "the run is still going");
                ServerPlayer back = survivalPlayer(helper, at, uuid);
                moddedClient.accept(back);
                java.util.function.Supplier<net.ledok.arenas_ld.dungeon.run.RunParticipant> participant = () ->
                    controller.getActiveRuns().values().iterator().next().participants().get(uuid);
                whenTrue(helper, 40, () -> participant.get() != null && participant.get().status()
                        == net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus.ACTIVE,
                    () -> "back in the run standing: " + participant.get(), () -> {
                        try {
                            helper.assertTrue(back.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SURVIVAL,
                                "in survival, not a spectator");
                            helper.assertTrue(results(id).isEmpty(), "still not lost: " + results(id));
                            helper.assertTrue(net.ledok.arenas_ld.arena.run.ArenaRunLifecycle.exitEarly(server, back), "they can leave");
                        } finally {
                            server.getPlayerList().remove(back);
                        }
                        helper.succeed();
                    });
            });
        });
    }

    /**
     * Breaking an arena controller mid-run ends its run properly: the player is sent home and out of
     * the run, the run reports a result, and its chunk tickets are given back.
     */
    public static void controllerBrokenMidRun(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        registerHooksOnce();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        BlockPos spawnerRel = new BlockPos(5, 1, 5);
        helper.setBlock(controllerRel, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.ARENA_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ArenaControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ArenaSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_broken_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            spawner.setEntrancePosition(helper.absolutePos(new BlockPos(5, 1, 3)), level.dimension());
            BlockPos spawnerPos = helper.absolutePos(spawnerRel);
            helper.assertTrue(controller.addInstance(spawnerPos, level.dimension()), "instance linked");
            BlockPos home = helper.absolutePos(new BlockPos(3, 1, 6));
            ServerPlayer player = survivalPlayer(helper, home);
            moddedClient.accept(player);
            try {
                helper.assertTrue(controller.createLobby(player) && controller.startRun(player), "the run starts");
                helper.assertTrue(!player.blockPosition().equals(home), "the player is in the arena");
                var owner = net.ledok.arenas_ld.util.RunChunkTickets.owner(level.dimension(), spawnerPos);
                helper.assertTrue(net.ledok.arenas_ld.util.RunChunkTickets.isHeld(owner), "the run holds its chunks");
                helper.setBlock(controllerRel, Blocks.AIR); // broken mid-run
                helper.assertTrue(player.blockPosition().equals(home),
                    "the player is sent back to where they stood: " + player.blockPosition() + " vs " + home);
                helper.assertTrue(!net.ledok.arenas_ld.util.RunChunkTickets.isHeld(owner), "its chunk tickets are given back");
                List<ArenasApi.RunResult> results = results(id);
                helper.assertTrue(results.size() == 1 && results.get(0).outcome() != ArenasApi.Outcome.WIN,
                    "the run reported one non-winning result: " + results);
            } finally {
                server.getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * 4.29.0, the dungeon start countdown (started from the packet handler, as the Start button does): the lobby stays
     * and prepares with the instance's chunks held; un-readying cancels it; readying and starting again launches the
     * run when the countdown ends.
     */
    public static void dungeonPreparation(GameTestHelper helper) {
        registerHooksOnce();
        BlockPos dbsRel = new BlockPos(1, 1, 1);
        BlockPos roomRel = new BlockPos(3, 1, 1);
        BlockPos spawnerRel = new BlockPos(3, 1, 3);
        BlockPos entranceRel = new BlockPos(1, 1, 4);
        BlockPos controllerRel = new BlockPos(6, 1, 6);
        helper.setBlock(dbsRel, BlockRegistry.DUNGEON_BOSS_SPAWNER_BLOCK);
        helper.setBlock(roomRel, BlockRegistry.ROOM_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.MOB_SPAWNER_BLOCK);
        helper.setBlock(controllerRel, BlockRegistry.DUNGEON_CONTROLLER_BLOCK);
        ServerLevel level = helper.getLevel();
        net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity dbs = helper.getBlockEntity(dbsRel);
        net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity room = helper.getBlockEntity(roomRel);
        helper.assertTrue(dbs.addRoom(helper.absolutePos(roomRel)), "room linked");
        helper.assertTrue(room.toggleSpawner(helper.absolutePos(spawnerRel)), "spawner linked");
        dbs.setEntrancePosition(helper.absolutePos(entranceRel), level.dimension());
        helper.runAfterDelay(3, () -> {
            MinecraftServer server = level.getServer();
            DungeonControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_dprepare_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            controller.addInstance(helper.absolutePos(dbsRel), level.dimension());
            ServerPlayer owner = survivalPlayer(helper, helper.absolutePos(new BlockPos(5, 1, 3)));
            Runnable start = () -> {
                net.ledok.arenas_ld.util.RunPreparation.secondsOverride = 10;
                try {
                    net.ledok.arenas_ld.networking.SpawnerPacketHandlers.performStartRun(owner, controller);
                } finally {
                    net.ledok.arenas_ld.util.RunPreparation.secondsOverride = 0;
                }
            };
            UUID lobbyId;
            try {
                lobbyId = controller.createLobby(owner).orElseThrow();
                controller.toggleReady(owner);
                start.run();
                helper.assertTrue(net.ledok.arenas_ld.util.RunPreparation.isPreparing(lobbyId), "the lobby prepares");
                helper.assertTrue(controller.getLobbies().stream().anyMatch(l -> l.lobbyId().equals(lobbyId)),
                    "the lobby stays while preparing");
                helper.assertTrue(net.ledok.arenas_ld.util.RunPreparation.held(lobbyId).contains(
                    new net.ledok.arenas_ld.util.RunPreparation.HeldChunk(level.dimension(),
                        new net.minecraft.world.level.ChunkPos(helper.absolutePos(dbsRel)))), "the instance's chunks are held");
                helper.assertTrue(ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(owner.getUUID()) == null, "no run yet");
                controller.toggleReady(owner);
            } catch (RuntimeException e) {
                server.getPlayerList().remove(owner);
                throw e;
            }
            whenTrue(helper, 10, () -> !net.ledok.arenas_ld.util.RunPreparation.isPreparing(lobbyId),
                "un-readying cancels the start", () -> {
                helper.assertTrue(net.ledok.arenas_ld.util.RunPreparation.held(lobbyId).isEmpty(), "the tickets are let go");
                controller.toggleReady(owner);
                start.run();
                helper.assertTrue(net.ledok.arenas_ld.util.RunPreparation.isPreparing(lobbyId),
                    "the instance is offered again after the cancel");
                whenTrue(helper, 230, () -> ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(owner.getUUID()) != null,
                    "the run starts", () -> {
                    try {
                        helper.assertTrue(controller.getLobbies().stream().noneMatch(l -> l.lobbyId().equals(lobbyId)),
                            "the lobby is taken off at the start");
                        net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.exitEarly(server, owner);
                    } finally {
                        server.getPlayerList().remove(owner);
                    }
                    helper.succeed();
                });
            });
        });
    }

    /**
     * 4.29.0, the start countdown (arena; dungeons and raids use the same {@link net.ledok.arenas_ld.util.RunPreparation}):
     * <ol>
     *   <li>a start waits instead of teleporting: the lobby prepares, its chunks are held, the instance stays free but
     *       isn't offered; a member going offline cancels it and lets everything go;</li>
     *   <li>another party's start launches only when the whole countdown is over (the area is loaded long before),
     *       and the preload tickets are kept a little after the start.</li>
     * </ol>
     */
    public static void runPreparation(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        registerHooksOnce();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        BlockPos spawnerRel = new BlockPos(5, 1, 5);
        helper.setBlock(controllerRel, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.ARENA_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ArenaControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ArenaSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_prepare_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            spawner.setEntrancePosition(helper.absolutePos(new BlockPos(5, 1, 3)), level.dimension());
            helper.assertTrue(controller.addInstance(helper.absolutePos(spawnerRel), level.dimension()), "instance linked");
            Supplier<Boolean> instanceFree = () -> ArenasApi.status(server, id).orElseThrow().instances().stream()
                .allMatch(i -> i.state() == ArenasApi.InstanceState.FREE);

            ServerPlayer leaver = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 6)));
            moddedClient.accept(leaver);
            helper.assertTrue(controller.createLobby(leaver), "lobby created");
            UUID leaverLobby = controller.getLobbyByMember(leaver.getUUID()).lobbyId();
            net.ledok.arenas_ld.util.RunPreparation.secondsOverride = 10;
            try {
                helper.assertTrue(controller.startRun(leaver), "the start is accepted");
            } finally {
                net.ledok.arenas_ld.util.RunPreparation.secondsOverride = 0;
            }
            helper.assertTrue(net.ledok.arenas_ld.util.RunPreparation.isPreparing(leaverLobby), "the lobby prepares");
            helper.assertTrue(!RunItemRestrictions.isPlayerInRun(leaver), "nobody is in the run yet");
            helper.assertTrue(!net.ledok.arenas_ld.util.RunPreparation.held(leaverLobby).isEmpty(), "its chunks are held");
            helper.assertTrue(instanceFree.get(), "the instance stays free while preparing");
            helper.assertTrue(!controller.startRun(leaver), "a second start while preparing does nothing");
            server.getPlayerList().remove(leaver);

            whenTrue(helper, 10, () -> !net.ledok.arenas_ld.util.RunPreparation.isPreparing(leaverLobby),
                "an offline member cancels the start", () -> {
                helper.assertTrue(net.ledok.arenas_ld.util.RunPreparation.held(leaverLobby).isEmpty(), "the tickets are let go");
                helper.assertTrue(instanceFree.get() && ArenasApi.status(server, id).orElseThrow().runs().isEmpty(),
                    "no run, the instance free");

                ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 6)));
                moddedClient.accept(player);
                helper.assertTrue(controller.createLobby(player), "a second lobby");
                UUID lobbyId = controller.getLobbyByMember(player.getUUID()).lobbyId();
                long started = server.getTickCount();
                net.ledok.arenas_ld.util.RunPreparation.secondsOverride = 10;
                try {
                    helper.assertTrue(controller.startRun(player), "its start is accepted");
                } finally {
                    net.ledok.arenas_ld.util.RunPreparation.secondsOverride = 0;
                }
                whenTrue(helper, 230, () -> RunItemRestrictions.isPlayerInRun(player), "the run starts", () -> {
                    try {
                        long waited = server.getTickCount() - started;
                        helper.assertTrue(waited >= 200,
                            "it always waits the whole countdown, even with the area loaded: " + waited + " ticks");
                        helper.assertTrue(!net.ledok.arenas_ld.util.RunPreparation.isPreparing(lobbyId), "no longer preparing");
                        helper.assertTrue(!net.ledok.arenas_ld.util.RunPreparation.held(lobbyId).isEmpty(),
                            "the preload tickets stay a little after the start");
                        helper.assertTrue(net.ledok.arenas_ld.arena.run.ArenaRunLifecycle.exitEarly(server, player), "/exit");
                    } finally {
                        server.getPlayerList().remove(player);
                    }
                    helper.succeed();
                });
            });
        });
    }

    /**
     * /exit in the middle of an arena run: the player is sent out of it, and with nobody left the
     * run is lost as abandoned on its next tick (reported once, as a LOSS).
     *
     * @param moddedClient see {@link #adminScreen}: the run player gets the run HUD
     */
    public static void arenaForfeit(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        registerHooksOnce();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        BlockPos spawnerRel = new BlockPos(5, 1, 5);
        helper.setBlock(controllerRel, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.ARENA_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ArenaControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ArenaSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_exit_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            BlockPos spawnerPos = helper.absolutePos(spawnerRel);
            spawner.setEntrancePosition(helper.absolutePos(new BlockPos(5, 1, 3)), level.dimension());
            helper.assertTrue(controller.addInstance(spawnerPos, level.dimension()), "instance linked");
            ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 6)));
            moddedClient.accept(player);
            try {
                helper.assertTrue(controller.createLobby(player) && controller.startRun(player), "the run starts");
                helper.assertTrue(RunItemRestrictions.isPlayerInRun(player), "the player is in the run");
                // 4.27.1: a respawn anchor can't be right-clicked in a run; other blocks can.
                BlockPos anchor = helper.absolutePos(new BlockPos(1, 1, 6));
                level.setBlockAndUpdate(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState());
                helper.assertTrue(net.ledok.arenas_ld.platform.ArenasEvents.isBlockUseBlocked(player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, anchor), "a respawn anchor is blocked in a run");
                level.setBlockAndUpdate(anchor, Blocks.CHEST.defaultBlockState());
                helper.assertTrue(!net.ledok.arenas_ld.platform.ArenasEvents.isBlockUseBlocked(player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, anchor), "a chest is not blocked");
                level.setBlockAndUpdate(anchor, Blocks.RESPAWN_ANCHOR.defaultBlockState());
                helper.assertTrue(net.ledok.arenas_ld.arena.run.ArenaRunLifecycle.exitEarly(server, player),
                    "/exit works mid-run");
                helper.assertTrue(!RunItemRestrictions.isPlayerInRun(player), "the player is out of the run");
                helper.assertTrue(!net.ledok.arenas_ld.platform.ArenasEvents.isBlockUseBlocked(player,
                    net.minecraft.world.InteractionHand.MAIN_HAND, anchor), "a respawn anchor is fine outside a run");
                helper.assertTrue(ArenasApi.status(server, id).orElseThrow().runs().get(0).players().isEmpty(),
                    "the run has nobody in it");
            } catch (RuntimeException e) {
                server.getPlayerList().remove(player);
                throw e;
            }
            whenTrue(helper, 40, () -> !results(id).isEmpty(), () -> "the empty run ends", () -> {
                server.getPlayerList().remove(player);
                List<ArenasApi.RunResult> results = results(id);
                helper.assertTrue(results.size() == 1 && results.get(0).outcome() == ArenasApi.Outcome.LOSS,
                    "lost once, as abandoned: " + results);
                helper.succeed();
            });
        });
    }

    /**
     * A downed arena player watches their party instead of flying free: their camera is the nearest
     * teammate; a spectator-menu teleport or a click on someone outside the party is refused, one to
     * the other teammate switches the camera; a sneak press switches to the next teammate once. With
     * both teammates gone the camera is their own, and moved 30 blocks away they are pulled back
     * within {@code downed_spectator_radius} of where they went down. {@code /exit} frees them.
     *
     * @param moddedClient see {@link #adminScreen}: the run players get the run HUD
     */
    public static void downedSpectators(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        registerHooksOnce();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        BlockPos spawnerRel = new BlockPos(5, 1, 5);
        helper.setBlock(controllerRel, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.ARENA_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ArenaControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ArenaSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_down_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            BlockPos spawnerPos = helper.absolutePos(spawnerRel);
            spawner.setEntrancePosition(helper.absolutePos(new BlockPos(5, 1, 3)), level.dimension());
            spawner.setPrepareTime(60); // no wave (and so no revive) while the test runs
            helper.assertTrue(controller.addInstance(spawnerPos, level.dimension()), "instance linked");
            ServerPlayer downed = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 2)));
            ServerPlayer near = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 2)));
            ServerPlayer far = survivalPlayer(helper, helper.absolutePos(new BlockPos(4, 1, 2)));
            ServerPlayer outsider = survivalPlayer(helper, helper.absolutePos(new BlockPos(1, 1, 7)));
            List<ServerPlayer> all = List.of(downed, near, far, outsider);
            all.forEach(moddedClient);
            Runnable cleanup = () -> all.forEach(p -> server.getPlayerList().remove(p));
            // After their join handling (deferred a tick), which would take them for returning players.
            helper.runAfterDelay(3, () -> {
                try {
                    helper.assertTrue(controller.createLobby(downed), "lobby created");
                    UUID lobbyId = controller.getLobbyByMember(downed.getUUID()).lobbyId();
                    helper.assertTrue(controller.joinLobby(near, lobbyId) && controller.joinLobby(far, lobbyId),
                        "teammates joined");
                    helper.assertTrue(controller.startRun(downed), "the run starts");
                    // Spread out from the entrance: near is next to the downed player, far a few blocks off.
                    place(helper, downed, new BlockPos(3, 1, 3));
                    place(helper, near, new BlockPos(4, 1, 3));
                    place(helper, far, new BlockPos(7, 1, 7));

                    helper.assertTrue(net.ledok.arenas_ld.util.LivingEntityHooks.handlePlayerLethal(downed),
                        "the lethal hit is taken by the run");
                    helper.assertTrue(downed.isSpectator(), "downed: a spectator");
                    helper.assertTrue(DownedSpectators.isRestricted(downed), "held as a downed spectator");
                    helper.assertTrue(downed.getCamera() == near, "watching the nearest teammate: " + downed.getCamera());

                    Vec3 before = downed.position();
                    downed.connection.handleTeleportToEntityPacket(
                        new net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket(outsider.getUUID()));
                    helper.assertTrue(downed.getCamera() == near && downed.position().distanceTo(before) < 0.01,
                        "a spectator-menu teleport to a stranger is refused: " + downed.position());
                    downed.attack(outsider);
                    helper.assertTrue(downed.getCamera() == near, "spectating a stranger by clicking is refused");

                    downed.connection.handleTeleportToEntityPacket(
                        new net.minecraft.network.protocol.game.ServerboundTeleportToEntityPacket(far.getUUID()));
                    helper.assertTrue(downed.getCamera() == far, "a teleport to a teammate switches the camera");
                    helper.assertTrue(far.getUUID().equals(DownedSpectators.watching(downed)), "and the registry knows it");
                    downed.setShiftKeyDown(true);
                } catch (RuntimeException e) {
                    cleanup.run();
                    throw e;
                }
                // Two teammates: the next one after far is near (the downed player is never in the cycle).
                whenTrue(helper, 20, () -> downed.getCamera() == near, () -> "sneaking switches to the next teammate: "
                    + downed.getCamera(), () -> helper.runAfterDelay(5, () -> {
                    try {
                        helper.assertTrue(downed.getCamera() == near, "a held sneak switches once");
                        downed.setShiftKeyDown(false);
                        helper.assertTrue(net.ledok.arenas_ld.arena.run.ArenaRunLifecycle.exitEarly(server, near)
                            && net.ledok.arenas_ld.arena.run.ArenaRunLifecycle.exitEarly(server, far), "the teammates leave");
                    } catch (RuntimeException e) {
                        cleanup.run();
                        throw e;
                    }
                    whenTrue(helper, 20, () -> downed.getCamera() == downed, "no teammate left: the own camera", () -> {
                        Vec3 spot = DownedSpectators.downSpot(downed);
                        double radius = net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().downed_spectator_radius;
                        try {
                            helper.assertTrue(spot != null, "still held, with the spot they went down on");
                            downed.teleportTo(level, spot.x + 30, spot.y, spot.z, 0f, 0f);
                        } catch (RuntimeException e) {
                            cleanup.run();
                            throw e;
                        }
                        whenTrue(helper, 30, () -> downed.position().distanceTo(spot) <= Math.max(radius, 1.0),
                            () -> "pulled back near the spot: " + downed.position() + " vs " + spot, () -> {
                            try {
                                helper.assertTrue(net.ledok.arenas_ld.arena.run.ArenaRunLifecycle.exitEarly(server, downed),
                                    "/exit while downed");
                                helper.assertTrue(!DownedSpectators.isRestricted(downed) && !downed.isSpectator()
                                    && downed.getCamera() == downed, "free again, out of spectator");
                            } finally {
                                cleanup.run();
                            }
                            helper.succeed();
                        });
                    });
                }));
            });
        });
    }

    private static void place(GameTestHelper helper, ServerPlayer player, BlockPos rel) {
        BlockPos at = helper.absolutePos(rel);
        player.teleportTo(helper.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
    }

    /**
     * What a player leaves in an arena run is cleaned up when the run ends: their arrow and
     * snowball, a tamed wolf and a thrown item are tagged for the run while it goes; after staff end
     * and close it, the projectiles are gone, the wolf (sitting, so it can't walk over by itself) is
     * next to its owner at home, and the item is back in their inventory.
     *
     * @param moddedClient see {@link #adminScreen}: the run player gets the run HUD
     */
    public static void runLeftovers(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        registerHooksOnce();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        BlockPos spawnerRel = new BlockPos(5, 1, 5);
        helper.setBlock(controllerRel, BlockRegistry.ARENA_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.ARENA_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            ArenaControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            ArenaSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_left_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            BlockPos spawnerPos = helper.absolutePos(spawnerRel);
            spawner.setEntrancePosition(helper.absolutePos(new BlockPos(5, 1, 3)), level.dimension());
            helper.assertTrue(controller.addInstance(spawnerPos, level.dimension()), "instance linked");
            ArenasApi.InstanceRef instance = new ArenasApi.InstanceRef(level.dimension(), spawnerPos);
            ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 6)));
            moddedClient.accept(player);
            try {
                helper.assertTrue(controller.createLobby(player) && controller.startRun(player), "the run starts");
                helper.assertTrue(RunLeftovers.isInRun(player.getUUID()), "the participant index has the player");

                net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                    level, player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
                arrow.setNoGravity(true);
                arrow.setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(level.addFreshEntity(arrow), "arrow added");
                net.minecraft.world.entity.projectile.Snowball snowball =
                    new net.minecraft.world.entity.projectile.Snowball(level, player);
                snowball.setNoGravity(true);
                snowball.setDeltaMovement(Vec3.ZERO);
                helper.assertTrue(level.addFreshEntity(snowball), "snowball added");
                net.minecraft.world.entity.animal.Wolf wolf = net.minecraft.world.entity.EntityType.WOLF.create(level);
                wolf.tame(player);
                wolf.setOrderedToSit(true);
                wolf.setInSittingPose(true);
                BlockPos wolfAt = helper.absolutePos(new BlockPos(7, 1, 2));
                wolf.moveTo(wolfAt.getX() + 0.5, wolfAt.getY(), wolfAt.getZ() + 0.5, 0, 0);
                helper.assertTrue(level.addFreshEntity(wolf), "wolf added");
                net.minecraft.world.entity.item.ItemEntity thrown = player.drop(
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 3), false, true);
                helper.assertTrue(thrown != null, "item thrown");
                thrown.setPickUpDelay(32767);

                for (net.minecraft.world.entity.Entity entity : List.of(arrow, snowball, wolf, thrown)) {
                    RunLeftovers.OwnedTag tag = RunLeftovers.ownedTag(entity);
                    helper.assertTrue(tag != null && tag.owner().equals(player.getUUID()),
                        entity.getType() + " tagged for the player's run: " + entity.getTags());
                }

                helper.assertTrue(ArenasApi.forceEnd(server, id, instance) == null, "staff end the run");
                helper.assertTrue(ArenasApi.forceEnd(server, id, null) == null, "and close it");
                helper.assertTrue(!RunLeftovers.isInRun(player.getUUID()), "the player is out of the index");

                helper.assertTrue(arrow.isRemoved() && snowball.isRemoved(), "the projectiles are gone");
                net.minecraft.world.entity.Entity wolfNow = level.getEntity(wolf.getUUID());
                helper.assertTrue(wolfNow != null && !wolfNow.isRemoved(), "the wolf is kept");
                helper.assertTrue(wolfNow.distanceTo(player) < 1.5f,
                    "the wolf is next to its owner: " + wolfNow.position() + " vs " + player.position());
                helper.assertTrue(RunLeftovers.ownedTag(wolfNow) == null, "the wolf's tag is gone");
                helper.assertTrue(thrown.isRemoved(), "the thrown item entity is gone");
                helper.assertTrue(player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND) == 3,
                    "the diamonds are back in the inventory");
                wolfNow.discard();
            } finally {
                server.getPlayerList().remove(player);
            }
            helper.succeed();
        });
    }

    /**
     * Leftovers that load from disk after their run is gone: tagged for a run nobody is in, an arrow
     * is discarded, the online owner's wolf comes to them and a thrown item goes back into their
     * inventory, while an offline owner's wolf stays where it is with its tag (tried again later).
     */
    public static void runLeftoversOnLoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 2)));
        UUID offline = UUID.randomUUID();
        String oldRun = "0ffbea70";
        try {
            net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(
                level, player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ARROW), null);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(arrow);
            net.minecraft.world.entity.animal.Wolf wolf = sittingWolf(helper, new BlockPos(6, 1, 6));
            wolf.tame(player);
            level.addFreshEntity(wolf);
            net.minecraft.world.entity.animal.Wolf strayWolf = sittingWolf(helper, new BlockPos(7, 1, 2));
            strayWolf.setOwnerUUID(offline);
            strayWolf.setTame(true, true);
            level.addFreshEntity(strayWolf);
            BlockPos itemAt = helper.absolutePos(new BlockPos(6, 1, 2));
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level,
                itemAt.getX() + 0.5, itemAt.getY(), itemAt.getZ() + 0.5,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD, 5));
            item.setThrower(player);
            item.setPickUpDelay(32767);
            level.addFreshEntity(item);

            arrow.addTag(RunLeftovers.tag(oldRun, player.getUUID()));
            wolf.addTag(RunLeftovers.tag(oldRun, player.getUUID()));
            item.addTag(RunLeftovers.tag(oldRun, player.getUUID()));
            strayWolf.addTag(RunLeftovers.tag(oldRun, offline));
            for (net.minecraft.world.entity.Entity entity : List.of(arrow, wolf, item, strayWolf)) {
                RunLeftovers.onLoaded(entity); // as if read back from disk with its chunk
            }
            helper.assertTrue(!arrow.isRemoved(), "nothing happens inside the load itself");

            whenTrue(helper, 5, arrow::isRemoved, "the leftover arrow is discarded at the end of the tick", () -> {
                try {
                    helper.assertTrue(!wolf.isRemoved() && wolf.distanceTo(player) < 1.5f,
                        "the wolf came to its owner: " + wolf.position() + " vs " + player.position());
                    helper.assertTrue(RunLeftovers.ownedTag(wolf) == null, "and lost its tag");
                    helper.assertTrue(item.isRemoved()
                            && player.getInventory().countItem(net.minecraft.world.item.Items.EMERALD) == 5,
                        "the thrown emeralds are back in the inventory");
                    BlockPos strayAt = helper.absolutePos(new BlockPos(7, 1, 2));
                    helper.assertTrue(!strayWolf.isRemoved() && strayWolf.blockPosition().equals(strayAt),
                        "an offline owner's wolf stays: " + strayWolf.blockPosition());
                    helper.assertTrue(RunLeftovers.ownedTag(strayWolf) != null, "and keeps its tag");
                } finally {
                    wolf.discard();
                    strayWolf.discard();
                    server.getPlayerList().remove(player);
                }
                helper.succeed();
            });
        } catch (RuntimeException e) {
            server.getPlayerList().remove(player);
            throw e;
        }
    }

    private static net.minecraft.world.entity.animal.Wolf sittingWolf(GameTestHelper helper, BlockPos rel) {
        net.minecraft.world.entity.animal.Wolf wolf = net.minecraft.world.entity.EntityType.WOLF.create(helper.getLevel());
        wolf.setOrderedToSit(true);
        wolf.setInSittingPose(true);
        BlockPos at = helper.absolutePos(rel);
        wolf.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return wolf;
    }

    /**
     * A real raid keeps its chunks with tickets: the spawner's and the boss's, and the boss ticket
     * follows the boss into another chunk. Starting it discards a stray run-mob of the boss's type
     * left by an earlier run (but not an untagged one), and ending it discards the boss and gives
     * the tickets back. The boss is an iron golem: no other test uses one, and the sweep covers
     * nearby test areas too.
     *
     * @param moddedClient see {@link #adminScreen}: the run player gets the run HUD
     */
    public static void raidBossChunks(GameTestHelper helper, Consumer<ServerPlayer> moddedClient) {
        registerHooksOnce();
        BlockPos controllerRel = new BlockPos(1, 1, 1);
        BlockPos spawnerRel = new BlockPos(5, 1, 5);
        helper.setBlock(controllerRel, BlockRegistry.RAID_CONTROLLER_BLOCK);
        helper.setBlock(spawnerRel, BlockRegistry.RAID_BOSS_SPAWNER_BLOCK);
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            RaidControllerBlockEntity controller = helper.getBlockEntity(controllerRel);
            net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity spawner = helper.getBlockEntity(spawnerRel);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("arenas_ld", "gt_raid_" + suffix());
            helper.assertTrue(ActivityRegistry.changeId(controller, id.toString()) == null, "test id set");
            BlockPos spawnerPos = helper.absolutePos(spawnerRel);
            spawner.setMobId("minecraft:iron_golem");
            spawner.setEntrancePosition(helper.absolutePos(new BlockPos(5, 1, 2)), level.dimension());
            helper.assertTrue(controller.addInstance(spawnerPos, level.dimension()), "instance linked");
            ArenasApi.InstanceRef instance = new ArenasApi.InstanceRef(level.dimension(), spawnerPos);

            var stray = helper.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM, new BlockPos(2, 1, 6));
            net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(stray);
            var wild = helper.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM, new BlockPos(6, 1, 2));
            ServerPlayer player = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 6)));
            moddedClient.accept(player);
            net.ledok.arenas_ld.raid.run.RaidRun run;
            net.minecraft.world.entity.Entity boss;
            net.ledok.arenas_ld.util.RunChunkTickets.Owner owner;
            net.minecraft.world.level.ChunkPos moved;
            try {
                helper.assertTrue(controller.createLobby(player) && controller.startRaid(player), "the raid starts");
                helper.assertTrue(stray.isRemoved(), "the stray tagged golem left by an earlier run was discarded");
                helper.assertTrue(!wild.isRemoved(), "an untagged golem is left alone");
                run = controller.getActiveRuns().get(new net.ledok.arenas_ld.activity.InstanceKey(level.dimension(), spawnerPos));
                helper.assertTrue(run != null && run.bossUuid() != null, "the run has a boss");
                boss = level.getEntity(run.bossUuid());
                helper.assertTrue(boss != null && boss.isAlive(), "the boss is loaded");
                owner = net.ledok.arenas_ld.raid.run.RaidRunLifecycle.chunkOwner(run);
                var held = net.ledok.arenas_ld.util.RunChunkTickets.held(owner);
                helper.assertTrue(held.contains(new net.ledok.arenas_ld.util.RunChunkTickets.HeldChunk(
                        level.dimension(), boss.chunkPosition()))
                    && held.contains(new net.ledok.arenas_ld.util.RunChunkTickets.HeldChunk(
                        level.dimension(), new net.minecraft.world.level.ChunkPos(spawnerPos))),
                    "tickets on the boss's and the spawner's chunks: " + held);
                helper.assertTrue(net.ledok.arenas_ld.raid.run.RaidRunLifecycle.sweepStrayBosses(level, spawnerPos,
                    boss.getType()) == 0, "the run's own boss is never swept");

                ((net.minecraft.world.entity.Mob) boss).setNoAi(true);
                moved = new net.minecraft.world.level.ChunkPos(boss.chunkPosition().x + 1, boss.chunkPosition().z);
            } catch (RuntimeException e) {
                server.getPlayerList().remove(player);
                throw e;
            }
            var movedChunk = new net.ledok.arenas_ld.util.RunChunkTickets.HeldChunk(level.dimension(), moved);
            // The neighbor chunk is in the boss ticket's loaded area, but a fresh test world
            // generates it over a few ticks: move the boss there once its entities are loaded.
            whenTrue(helper, 200, () -> level.areEntitiesLoaded(moved.toLong()), "the next chunk loads", () -> {
                // Frozen: a boss that wanders back over the border before the next tracking tick
                // rightly keeps its ticket on the old chunk, which made this step flaky.
                if (boss instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setNoAi(true);
                }
                boss.teleportTo(boss.getX() + 16, boss.getY(), boss.getZ());
                helper.assertTrue(boss.chunkPosition().equals(moved), "the boss moved to " + moved);
            whenTrue(helper, 60, () -> net.ledok.arenas_ld.util.RunChunkTickets.held(owner).contains(movedChunk),
                () -> "the boss ticket follows the boss to " + moved + ": " + net.ledok.arenas_ld.util.RunChunkTickets.held(owner),
                () -> {
                    try {
                        helper.assertTrue(run.bossLastPos() != null
                            && new net.minecraft.world.level.ChunkPos(run.bossLastPos()).equals(moved),
                            "its last position is noted: " + run.bossLastPos());
                        helper.assertTrue(ArenasApi.forceEnd(server, id, instance) == null, "staff end the raid");
                        helper.assertTrue(boss.isRemoved(), "the boss is discarded");
                        helper.assertTrue(ArenasApi.forceEnd(server, id, null) == null, "a closing raid is closed at once");
                        helper.assertTrue(controller.getActiveRuns().isEmpty(), "no raid left");
                        helper.assertTrue(net.ledok.arenas_ld.util.RunChunkTickets.held(owner).isEmpty(),
                            "its tickets are given back");
                    } finally {
                        server.getPlayerList().remove(player);
                    }
                    helper.succeed();
                });
            });
        });
    }

    /**
     * Tier reward commands: a winner online gets the per-player commands at once, a command without
     * a placeholder runs once, and an offline winner's per-player commands wait in the store until
     * they join (the join event runs them two ticks later).
     */
    public static void rewardCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        String objective = "gt_once_" + suffix();
        ServerPlayer online = survivalPlayer(helper, helper.absolutePos(new BlockPos(2, 1, 2)));
        UUID offline = UUID.randomUUID();
        List<String> commands = List.of(
            "tag @dungeonplayer add gt_reward",
            "/tag @s[tag=gt_reward] add gt_self",
            "scoreboard objectives add " + objective + " dummy",
            "this_is_not_a_command @s");
        ServerPlayer[] joined = new ServerPlayer[1];
        try {
            net.ledok.arenas_ld.util.RewardCommands.run(level, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)),
                List.of(online.getUUID(), offline), commands, true);
            helper.assertTrue(online.getTags().contains("gt_reward") && online.getTags().contains("gt_self"),
                "the online winner got both per-player commands: " + online.getTags());
            helper.assertTrue(server.getScoreboard().getObjective(objective) != null, "the plain command ran");
            var store = net.ledok.arenas_ld.util.PendingRewardCommandsStore.get(server);
            helper.assertTrue(store.peek(offline).equals(List.of("tag @dungeonplayer add gt_reward",
                "tag @s[tag=gt_reward] add gt_self", "this_is_not_a_command @s")),
                "only the offline winner's per-player commands are queued: " + store.peek(offline));
            helper.assertTrue(store.peek(online.getUUID()).isEmpty(), "nothing queued for the online winner");
        } catch (RuntimeException e) {
            server.getPlayerList().remove(online);
            throw e;
        }
        server.getPlayerList().remove(online);

        joined[0] = survivalPlayer(helper, helper.absolutePos(new BlockPos(3, 1, 3)), offline);
        whenTrue(helper, 20, () -> joined[0].getTags().contains("gt_self"),
            () -> "queued commands run on join: " + joined[0].getTags(), () -> {
                try {
                    helper.assertTrue(joined[0].getTags().contains("gt_reward"), "both queued commands ran");
                    helper.assertTrue(net.ledok.arenas_ld.util.PendingRewardCommandsStore.get(server).peek(offline).isEmpty(),
                        "and the queue entry is gone");
                } finally {
                    server.getPlayerList().remove(joined[0]);
                    var made = server.getScoreboard().getObjective(objective);
                    if (made != null) {
                        server.getScoreboard().removeObjective(made);
                    }
                }
                helper.succeed();
            });
    }

    private static List<ArenasApi.RunResult> results(ResourceLocation activity) {
        return RUN_RESULTS.stream().filter(result -> result.activity().equals(activity)).toList();
    }

    /** Whether a refusal is the given translation key (the server has no translations to compare text with). */
    private static boolean isKey(Component message, String key) {
        return message != null && message.getContents() instanceof TranslatableContents contents && contents.getKey().equals(key);
    }

    /**
     * An index entry for a dungeon controller that isn't in the world, as one removed without a
     * block update leaves behind. Touches no block or chunk at {@code pos}.
     */
    private static void ghostController(ServerLevel level, BlockPos pos, ResourceLocation id) {
        DungeonControllerBlockEntity ghost = new DungeonControllerBlockEntity(pos,
            BlockRegistry.DUNGEON_CONTROLLER_BLOCK.defaultBlockState());
        ghost.storeActivityId(id); // before it has a level: nothing to sync
        ghost.setLevel(level);
        ActivityRegistry.attach(ghost);
    }
}
