package net.ledok.arenas_ld.raid.run;

import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.compat.PuffishSkillsCompat;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
import net.ledok.arenas_ld.dungeon.run.DownedPlayer;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity.ControllerKey;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Static lifecycle helpers for {@link RaidRun}. Mirrors {@link net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle}.
 *
 * <p>This class lives in {@code raid.run} so it has package-private access to the
 * mutators on {@link RaidRun}. Controllers and spawners in other packages call into
 * the public surface here; they must never touch run mutators directly.
 *
 * <p>Methods are added as responsibilities migrate from
 * {@link net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity} into the lifecycle.
 */
public final class RaidRunLifecycle {

    public static final String BUSY_REASON = "arenas_ld:raid";

    private RaidRunLifecycle() {}

    /**
     * Locate the {@link RaidControllerBlockEntity} that owns the run anchored at the given
     * spawner position. Walks the registered controller set and returns the first one whose
     * {@code activeRuns} map contains the spawner key.
     */
    @Nullable
    public static RaidControllerBlockEntity findOwningController(MinecraftServer server, BlockPos spawnerPos) {
        for (ControllerKey key : RaidControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            BlockEntity be = level.getBlockEntity(key.pos());
            if (be instanceof RaidControllerBlockEntity controller
                && controller.getActiveRuns().containsKey(spawnerPos)) {
                return controller;
            }
        }
        return null;
    }

    /** Convenience overload taking a level and resolving the server from it. */
    @Nullable
    public static RaidControllerBlockEntity findOwningController(ServerLevel level, BlockPos spawnerPos) {
        return findOwningController(level.getServer(), spawnerPos);
    }

    /** Look up the live run for a spawner across all loaded controllers. */
    @Nullable
    public static RaidRun findRun(MinecraftServer server, BlockPos spawnerPos) {
        RaidControllerBlockEntity controller = findOwningController(server, spawnerPos);
        return controller == null ? null : controller.getActiveRuns().get(spawnerPos);
    }

    /** Resolve the dimension stored on the run, returning null if the server hasn't loaded it. */
    @Nullable
    public static ServerLevel resolveSpawnerLevel(MinecraftServer server, RaidRun run) {
        ResourceKey<Level> dim = run.spawnerDimension();
        return dim == null ? null : server.getLevel(dim);
    }


    // ─────────────────────────────────────────────────────────────────────────
    //  Raid timer boss bar (transient, lazily created)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the raid-timer boss bar bound to this run, creating it on first call.
     * The bar is transient (not persisted) so it gets recreated after a server restart.
     */
    public static ServerBossEvent ensureRaidTimerBossBar(RaidRun run) {
        ServerBossEvent bar = run.getRaidTimerBossBar();
        if (bar == null) {
            bar = new ServerBossEvent(
                Component.translatable("gui.arenas_ld.raid_timer"),
                BossEvent.BossBarColor.YELLOW,
                BossEvent.BossBarOverlay.PROGRESS
            );
            run.setRaidTimerBossBar(bar);
        }
        return bar;
    }

    /** Hide the bar (if present) and detach it from the run. Safe when bar is already null. */
    public static void clearRaidTimerBossBar(RaidRun run) {
        ServerBossEvent bar = run.getRaidTimerBossBar();
        if (bar == null) return;
        bar.removeAllPlayers();
        bar.setVisible(false);
        run.setRaidTimerBossBar(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Run lifecycle (driven by RaidControllerBlockEntity's tick loop).
    //  Mirrors DungeonRunLifecycle: the controller owns the tick + the run map;
    //  the RaidBossSpawnerBlockEntity is a passive arena/config holder.
    // ─────────────────────────────────────────────────────────────────────────

    private static final int DOWNED_RESPAWN_TICKS = 60;
    private static final int REGEN_INTERVAL_TICKS = 100;
    private static final int DEATH_TIME_PENALTY_TICKS = 10 * 20;

    /**
     * Per-tick entry point, called from the controller for each active run.
     * {@code world} is the SPAWNER's level (resolved from {@link RaidRun#spawnerDimension()}),
     * not the controller's.
     */
    public static void tick(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        switch (run.phase()) {
            case STARTING -> run.setPhase(RaidPhase.RUNNING);
            case RUNNING -> tickRunning(world, controller, run);
            case CLOSING -> tickClosing(world, controller, run);
            case DONE -> {
                // Should already be removed from the controller.
            }
        }
    }

    /** Look up the passive spawner block entity that anchors this run. */
    @Nullable
    private static RaidBossSpawnerBlockEntity spawnerFor(ServerLevel world, RaidRun run) {
        BlockEntity be = world.getBlockEntity(run.spawnerPos());
        return be instanceof RaidBossSpawnerBlockEntity s ? s : null;
    }

    /**
     * Spawn the boss, scale it, teleport the party in, and wire the run's boss reference.
     * Reads arena geometry + mob definition from the (passive) spawner. Called by the
     * controller right after it creates and stores the run.
     */
    public static void startBattle(
            ServerLevel world,
            RaidRun run,
            List<ServerPlayer> players,
            RaidDifficulty difficulty,
            boolean hardcoreEnabled
    ) {
        if (players == null || players.isEmpty()) return;
        RaidBossSpawnerBlockEntity spawner = spawnerFor(world, run);
        if (spawner == null) return;

        EntityDefinition entityDefinition = spawner.getEntityDefinition();
        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(entityDefinition.mobId());
        Component mobDisplayName = entityTypeOpt.map(EntityType::getDescription)
            .orElse(Component.literal(entityDefinition.mobId()));
        Component announcement = Component.translatable(
                "message.arenas_ld.raid_start",
                players.get(0).getDisplayName(),
                mobDisplayName
        ).withStyle(ChatFormatting.GOLD);
        world.getServer().getPlayerList().broadcastSystemMessage(announcement, false);

        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.error("Invalid mob ID in spawner at {}: {}", run.spawnerPos(), entityDefinition.mobId());
            return;
        }

        Entity boss = entityTypeOpt.get().create(world);
        if (boss == null) {
            ArenasLdMod.LOGGER.error("Failed to create entity from ID: {}", entityDefinition.mobId());
            return;
        }

        RaidDifficulty diff = difficulty != null ? difficulty : RaidDifficulty.NORMAL;
        long now = world.getGameTime();
        RaidTierConfig runTier = run.resolvedTierConfig();
        int raidTimeTicks = Math.max(0, runTier.raidTimeSeconds() * 20);

        ServerBossEvent bossBar = ensureRaidTimerBossBar(run);
        for (ServerPlayer p : players) {
            run.addParticipant(new RunParticipant(
                p.getUUID(), p.getGameProfile().getName(), ParticipantStatus.ACTIVE, now));
            run.setReturnPoint(p.getUUID(), PlayerReturnPoint.capture(p));
            bossBar.addPlayer(p);
        }
        if (raidTimeTicks > 0) {
            bossBar.setVisible(true);
            bossBar.setProgress(1.0f);
            bossBar.setName(Component.translatable("gui.arenas_ld.raid_timer_remaining", formatTime(raidTimeTicks / 20)));
        } else {
            bossBar.setVisible(false);
        }

        if (boss instanceof LivingEntity livingBoss) {
            double healthMult = runTier.healthMultiplier();
            double damageMult = runTier.damageMultiplier();
            double perPlayerMult = Math.pow(1.0 + runTier.hpScalePerPlayer(), Math.max(0, players.size() - 1));

            EntityEquipmentHelper.applyScaledAttributes(
                livingBoss, entityDefinition.attributes(), world.registryAccess(), healthMult, damageMult, perPlayerMult);
            EntityEquipmentHelper.applyAllEquipment(livingBoss, entityDefinition.equipment());

            livingBoss.heal(livingBoss.getMaxHealth());
            String groupId = spawner.getGroupId();
            String teamName = groupId == null || groupId.isBlank() ? "arenas_ld" : groupId;
            Scoreboard scoreboard = world.getScoreboard();
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
                team.setAllowFriendlyFire(false);
            }
            scoreboard.addPlayerToTeam(livingBoss.getScoreboardName(), team);
        }

        Vec3 bossSpawnPos = EntityEquipmentHelper.resolveBossSpawnPos(run.spawnerPos(), entityDefinition.spawnOffsets());
        boss.moveTo(bossSpawnPos.x, bossSpawnPos.y, bossSpawnPos.z, 0, 0);
        world.addFreshEntity(boss);

        ServerLevel entranceWorld = world.getServer().getLevel(spawner.entranceDimension);
        BlockPos absoluteEntrance = run.spawnerPos().offset(spawner.entrancePosition);
        for (ServerPlayer player : players) {
            player.setGameMode(GameType.ADVENTURE);
            player.setHealth(player.getMaxHealth());
            if (entranceWorld != null) {
                player.teleportTo(entranceWorld,
                    absoluteEntrance.getX() + 0.5, absoluteEntrance.getY(), absoluteEntrance.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            }
        }

        run.setBossRef(boss.getUUID(), world.dimension());
        run.setBoundsTickCounter(0);
        run.setRegenerationTickTimer(0);
        ArenasLdMod.RAID_BOSS_MANAGER.registerSpawner(spawner);
        ArenasLdMod.LOGGER.info("Raid battle started at {} with boss {}, tier={}, players={}",
                run.spawnerPos(), entityDefinition.mobId(), diff, players.size());
    }

    private static void tickRunning(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        // Idempotent re-registration so a server restart with a persisted run re-links
        // the spawner with the boss manager on first tick.
        RaidBossSpawnerBlockEntity spawner = spawnerFor(world, run);
        if (spawner != null) {
            ArenasLdMod.RAID_BOSS_MANAGER.registerSpawner(spawner);
        }

        UUID bossUuid = run.bossUuid();
        ResourceKey<Level> bossDim = run.bossDimension();
        if (bossUuid == null || bossDim == null) {
            handleLoss(world, controller, run, "Boss reference was null.");
            return;
        }

        ServerLevel bossWorld = world.getServer().getLevel(bossDim);
        if (bossWorld == null) {
            handleLoss(world, controller, run, "Boss world was null.");
            return;
        }

        Entity bossEntity = bossWorld.getEntity(bossUuid);
        if (bossEntity == null) {
            handleLoss(world, controller, run, "Boss entity disappeared.");
            return;
        }

        if (!bossEntity.isAlive()) {
            handleWin(world, controller, run);
            return;
        }

        RaidTierConfig runTier = run.resolvedTierConfig();
        int raidTimeTicks = Math.max(0, runTier.raidTimeSeconds() * 20);
        if (raidTimeTicks > 0) {
            int remaining = run.timerTicks() - 1;
            run.setTimerTicks(Math.max(0, remaining));
            updateRaidTimerBossBar(world, run);
            if (remaining <= 0) {
                bossEntity.discard();
                handleLoss(world, controller, run, "Time ran out.");
                return;
            }
        }

        Set<UUID> participantIds = run.participants().keySet();
        Map<UUID, DownedPlayer> downed = run.downedPlayers();
        boolean hardcore = run.hardcoreEnabled();

        int nextBounds = run.boundsTickCounter() + 1;
        if (nextBounds >= 20) {
            run.setBoundsTickCounter(0);
            for (UUID uuid : participantIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.isSpectator() || downed.containsKey(uuid)) {
                    continue;
                }
                if (player.getHealth() <= 1.0F) {
                    if (hardcore) {
                        handlePlayerHardcoreDeath(world, run, player);
                    } else {
                        handlePlayerDown(world, controller, run, player);
                    }
                }
            }
        } else {
            run.setBoundsTickCounter(nextBounds);
        }

        tickDownedPlayers(world, run);

        if (hardcore) {
            boolean anyFighting = participantIds.stream().anyMatch(id -> {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(id);
                return player != null && player.isAlive() && !player.isSpectator();
            });
            if (!anyFighting && downed.isEmpty()) {
                bossEntity.discard();
                handleLoss(world, controller, run, "All players eliminated.");
                return;
            }
        }

        int regenRate = runTier.regeneration();
        if (regenRate > 0 && bossEntity instanceof LivingEntity livingBoss) {
            int nextRegen = run.regenerationTickTimer() + 1;
            if (nextRegen >= REGEN_INTERVAL_TICKS) {
                livingBoss.heal((float) regenRate);
                run.setRegenerationTickTimer(0);
            } else {
                run.setRegenerationTickTimer(nextRegen);
            }
        }
    }

    static void handleWin(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        ArenasLdMod.LOGGER.info("Raid battle won at spawner {}", run.spawnerPos());
        run.setOutcome(RaidOutcome.WIN);

        Set<UUID> participantIds = run.participants().keySet();
        RaidTierConfig tierCfg = run.resolvedTierConfig();

        int xpReward = tierCfg.skillExperiencePerWin();
        if (xpReward > 0) {
            for (UUID uuid : participantIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && FabricLoader.getInstance().isModLoaded("puffish_skills")) {
                    PuffishSkillsCompat.addExperience(player, xpReward);
                }
            }
        }

        String perPlayerLoot = tierCfg.perPlayerLootTable();
        if (!perPlayerLoot.isEmpty()) {
            boolean viaInbox = controller.isLootViaInbox();
            for (UUID uuid : participantIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
                bundle.set(DataComponentRegistry.LOOT_BUNDLE_DATA, new LootBundleDataComponent(perPlayerLoot));
                if (viaInbox) {
                    net.ledok.arenas_ld.util.EconomyCompat.deliverItem(uuid, bundle, bundle.getCount(), "RAID_LOOT");
                    continue;
                }
                if (player == null) continue;
                if (!player.getInventory().add(bundle)) {
                    player.drop(bundle, false);
                }
            }
        }

        enterClosing(world, controller, run);
    }

    static void handleLoss(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, String reason) {
        ArenasLdMod.LOGGER.info("Raid battle lost at spawner {}: {}", run.spawnerPos(), reason);
        run.setOutcome(RaidOutcome.LOSS_FORCED);

        UUID bossUuid = run.bossUuid();
        ResourceKey<Level> bossDim = run.bossDimension();
        if (bossUuid != null && bossDim != null) {
            ServerLevel bossWorld = world.getServer().getLevel(bossDim);
            if (bossWorld != null) {
                Entity bossEntity = bossWorld.getEntity(bossUuid);
                if (bossEntity != null && bossEntity.isAlive()) {
                    bossEntity.discard();
                    ArenasLdMod.LOGGER.info("Despawned boss after battle loss at {}.", run.spawnerPos());
                }
            }
        }

        enterClosing(world, controller, run);
    }

    /**
     * Transition a finished battle into the CLOSING grace phase. Players linger for
     * {@code controller.getCloseTimerSeconds()} before being teleported out by
     * {@link #finalizeRun}. The raid-timer bar is swapped for a close-timer bar.
     */
    private static void enterClosing(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        clearRaidTimerBossBar(run);
        run.setPhase(RaidPhase.CLOSING);
        int closeTicks = Math.max(0, controller.getCloseTimerSeconds() * 20);
        run.setInitialCloseTimerTicks(closeTicks);
        run.setCloseTimerTicks(closeTicks);
        if (closeTicks <= 0) {
            // No grace configured — finalize immediately.
            finalizeRun(world, controller, run);
        }
    }

    private static void tickClosing(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        run.setCloseTimerTicks(run.closeTimerTicks() - 1);
        updateCloseTimerBossBar(world, run);
        if (run.closeTimerTicks() <= 0) {
            finalizeRun(world, controller, run);
        }
    }

    private static void updateCloseTimerBossBar(ServerLevel world, RaidRun run) {
        ServerBossEvent bar = run.getCloseTimerBossBar();
        if (bar == null) {
            bar = new ServerBossEvent(
                Component.translatable("boss_bar.arenas_ld.close_timer"),
                BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS
            );
            run.setCloseTimerBossBar(bar);
        }
        int totalTicks = run.initialCloseTimerTicks();
        float progress = totalTicks > 0
            ? Mth.clamp((float) run.closeTimerTicks() / (float) totalTicks, 0.0f, 1.0f)
            : 0.0f;
        bar.setProgress(progress);
        bar.setVisible(true);

        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && !bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
    }

    /** Detach the close-timer bar (if present). Safe when null. */
    private static void clearCloseTimerBossBar(RaidRun run) {
        ServerBossEvent bar = run.getCloseTimerBossBar();
        if (bar == null) return;
        bar.removeAllPlayers();
        bar.setVisible(false);
        run.setCloseTimerBossBar(null);
    }

    /** Teleport players back to their captured return points and tear down the run's transient state. */
    static void finalizeRun(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        for (UUID uuid : run.participants().keySet()) {
            // Players return to where they were when the run started (captured on entry).
            PlayerReturnPoint rp = run.returnPoints().get(uuid);
            GameType restoreMode = rp != null ? rp.previousGameMode() : GameType.SURVIVAL;
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                if (rp != null) {
                    ArenasLdMod.RAID_BOSS_MANAGER.addPendingRestore(
                            uuid, BlockPos.containing(rp.pos()), rp.dimension(), restoreMode);
                }
                continue;
            }
            player.setGameMode(restoreMode);
            player.setHealth(player.getMaxHealth());
            if (rp != null) {
                ServerLevel returnWorld = world.getServer().getLevel(rp.dimension());
                if (returnWorld != null) {
                    player.teleportTo(returnWorld,
                        rp.pos().x(), rp.pos().y(), rp.pos().z(), rp.yaw(), rp.pitch());
                }
            }
        }

        clearRaidTimerBossBar(run);
        clearCloseTimerBossBar(run);
        run.setPhase(RaidPhase.DONE);
        RaidBossSpawnerBlockEntity spawner = spawnerFor(world, run);
        if (spawner != null) {
            ArenasLdMod.RAID_BOSS_MANAGER.unregisterSpawner(spawner);
        }
        // Notify the controller LAST: onRaidEnded removes the run from activeRuns and
        // releases the instance, so everything above must have run first.
        notifyController(world, controller, run, run.outcome() == RaidOutcome.WIN);
    }

    /** Tell the controller the run ended so it records the leaderboard and releases the instance. */
    private static void notifyController(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, boolean wasWin) {
        List<String> names = run.participants().values().stream().map(RunParticipant::playerName).toList();
        int elapsed = Math.max(0, (int) ((world.getGameTime() - run.startTick()) / 20L));
        RaidDifficulty diff = RaidDifficulty.from(run.tier());
        controller.onRaidEnded(run.spawnerPos(), wasWin, diff, new ArrayList<>(names), elapsed);
    }

    // ── Per-player handlers (called from mixins/manager via spawner shims) ──────

    public static void handlePlayerDown(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, ServerPlayer player) {
        if (player == null || run == null || !run.participants().containsKey(player.getUUID())) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return;
        if (run.downedPlayers().containsKey(player.getUUID())) return;

        player.setHealth(1.0F);
        player.setGameMode(GameType.SPECTATOR);
        run.setTimerTicks(Math.max(0, run.timerTicks() - resolveDeathTimePenaltyTicks(controller)));
        run.setDowned(new DownedPlayer(player.getUUID(), DOWNED_RESPAWN_TICKS));
    }

    public static void handlePlayerDisconnect(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, ServerPlayer player) {
        if (player == null || run == null || !run.participants().containsKey(player.getUUID())) return;

        if (run.hardcoreEnabled()) {
            run.clearDisconnected(player.getUUID());
            handlePlayerHardcoreDeath(world, run, player);
            return;
        }

        run.markDisconnected(player.getUUID(), world.getGameTime());
        if (!run.downedPlayers().containsKey(player.getUUID())) {
            if (run.resolvedTierConfig().raidTimeSeconds() > 0) {
                run.setTimerTicks(Math.max(0, run.timerTicks() - resolveDeathTimePenaltyTicks(controller)));
            }
            run.setDowned(new DownedPlayer(player.getUUID(), DOWNED_RESPAWN_TICKS));
        }
    }

    public static void handlePlayerReconnect(ServerLevel world, RaidRun run, ServerPlayer player) {
        if (player == null || run == null || !run.participants().containsKey(player.getUUID())) return;

        run.clearDisconnected(player.getUUID());

        DownedPlayer downed = run.downedPlayers().get(player.getUUID());
        if (downed == null) {
            downed = new DownedPlayer(player.getUUID(), DOWNED_RESPAWN_TICKS);
            run.setDowned(downed);
        }
        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(player.getMaxHealth());
        if (downed.isReadyToRespawn()) {
            respawnAtEntrance(world, run, player);
            run.clearDowned(player.getUUID());
        }
    }

    public static void handlePlayerHardcoreDeath(ServerLevel world, RaidRun run, ServerPlayer player) {
        if (player == null || run == null || !run.participants().containsKey(player.getUUID())) return;

        player.setGameMode(GameType.SPECTATOR);
        run.clearDowned(player.getUUID());
        run.removeParticipant(player.getUUID());
    }

    private static void tickDownedPlayers(ServerLevel world, RaidRun run) {
        if (run.downedPlayers().isEmpty()) return;

        for (UUID uuid : new ArrayList<>(run.downedPlayers().keySet())) {
            DownedPlayer downed = run.downedPlayers().get(uuid);
            if (downed == null) continue;
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                run.setDowned(downed.tick());
                continue;
            }
            player.setDeltaMovement(0, 0, 0);
            DownedPlayer ticked = downed.tick();
            if (ticked.isReadyToRespawn()) {
                respawnAtEntrance(world, run, player);
                run.clearDowned(uuid);
            } else {
                run.setDowned(ticked);
            }
        }
    }

    private static void respawnAtEntrance(ServerLevel world, RaidRun run, ServerPlayer player) {
        if (player == null) return;
        RaidBossSpawnerBlockEntity spawner = spawnerFor(world, run);
        if (spawner == null) return;

        BlockPos target = run.spawnerPos().offset(spawner.entrancePosition);
        List<BlockPos> respawnOffsets = spawner.getRespawnPointOffsets();
        if (!respawnOffsets.isEmpty()) {
            Vec3 playerPos = player.position();
            target = respawnOffsets.stream()
                    .map(run.spawnerPos()::offset)
                    .min(Comparator.comparingDouble(p ->
                            playerPos.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)))
                    .orElse(target);
        }

        ServerLevel entranceWorld = world.getServer().getLevel(spawner.entranceDimension);
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        if (entranceWorld != null) {
            player.teleportTo(entranceWorld,
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        }
    }

    private static void updateRaidTimerBossBar(ServerLevel world, RaidRun run) {
        ServerBossEvent bossBar = ensureRaidTimerBossBar(run);
        int raidTimeTicks = Math.max(0, run.resolvedTierConfig().raidTimeSeconds() * 20);
        if (raidTimeTicks <= 0) {
            bossBar.setVisible(false);
            return;
        }
        if (!bossBar.isVisible()) {
            bossBar.setVisible(true);
        }
        int remainingTicks = Math.max(0, run.timerTicks());
        float progress = (float) remainingTicks / (float) raidTimeTicks;
        bossBar.setProgress(Mth.clamp(progress, 0.0f, 1.0f));
        bossBar.setName(Component.translatable("gui.arenas_ld.raid_timer_remaining", formatTime(remainingTicks / 20)));

        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && !bossBar.getPlayers().contains(player)) {
                bossBar.addPlayer(player);
            }
        }
    }

    /** Death-time penalty from the controller, falling back to the legacy constant. */
    private static int resolveDeathTimePenaltyTicks(@Nullable RaidControllerBlockEntity controller) {
        return controller != null ? controller.getDeathTimePenaltyTicks() : DEATH_TIME_PENALTY_TICKS;
    }

    private static String formatTime(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        return String.format("%02d:%02d", clamped / 60, clamped % 60);
    }
}
