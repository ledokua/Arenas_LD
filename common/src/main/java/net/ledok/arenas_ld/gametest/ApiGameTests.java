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
import java.util.function.Supplier;

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
}
