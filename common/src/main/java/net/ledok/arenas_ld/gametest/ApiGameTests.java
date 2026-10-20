package net.ledok.arenas_ld.gametest;

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
import net.ledok.arenas_ld.util.RunItemRestrictions;
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
            helper.assertTrue(!theirVex.isRemoved() && !plainAdd.isRemoved(), "mobs that aren't minions stay");
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
     * A survival test player that mobs attack (the GameTest mock player counts as creative, which
     * mobs never target), kept alive by Resistance V and Regeneration.
     */
    private static ServerPlayer survivalPlayer(GameTestHelper helper, BlockPos at) {
        var level = helper.getLevel();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
            new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ald_gt_" + suffix()), false);
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
