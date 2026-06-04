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
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.ledok.arenas_ld.util.PartyTeamStore;
import net.ledok.arenas_ld.util.PendingRestoreStore;
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
import java.util.HashMap;
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
            // Owned by the run: never let vanilla despawn the boss.
            if (livingBoss instanceof net.minecraft.world.entity.Mob mob) {
                mob.setPersistenceRequired();
            }
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

        ServerLevel entranceWorld = world.getServer().getLevel(spawner.getEntranceDimension());
        BlockPos absoluteEntrance = spawner.getAbsoluteEntrancePos();
        for (ServerPlayer player : players) {
            player.setGameMode(GameType.ADVENTURE);
            player.setHealth(player.getMaxHealth());
            if (entranceWorld != null) {
                player.teleportTo(entranceWorld,
                    absoluteEntrance.getX() + 0.5, absoluteEntrance.getY(), absoluteEntrance.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            }
            addToPartyTeam(world, run, player);
        }

        run.setBossRef(boss.getUUID(), world.dimension());
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

        // Everyone has left the run for good (hardcore deaths / forfeits past grace). End it now
        // instead of running the raid timer down with nobody inside.
        boolean anyRemaining = run.participants().values().stream()
            .anyMatch(p -> p.status() != ParticipantStatus.REMOVED);
        if (!anyRemaining) {
            bossEntity.discard();
            handleLoss(world, controller, run, "All players eliminated.");
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

        // Downs are detected through the lethal-damage hook in LivingEntityMixin; no health polling here.
        tickDownedPlayers(world, run);
        tickDisconnectedPlayers(world, controller, run);

        if (hardcore) {
            // Only participants still in the run count — a hardcore-dead player is REMOVED but may
            // still be online (and alive, back in survival), so they must not keep the run alive.
            boolean anyFighting = participantIds.stream().anyMatch(id -> {
                RunParticipant participant = run.participants().get(id);
                if (participant == null || participant.status() == ParticipantStatus.REMOVED) return false;
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
        // No grace configured, or nobody left to wait for (e.g. a hardcore wipe) — finalize now
        // instead of ticking through an empty close timer.
        boolean anyRemaining = run.participants().values().stream()
            .anyMatch(p -> p.status() != ParticipantStatus.REMOVED);
        if (closeTicks <= 0 || !anyRemaining) {
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
            restoreToReturnPoint(world, run, uuid);
        }
        teardownPartyTeam(world, run);

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

    /**
     * Send a single participant back to their captured return point, restoring their previous
     * game mode. If the player is offline, queue a pending restore so they're moved on next login.
     */
    private static void restoreToReturnPoint(ServerLevel world, RaidRun run, UUID uuid) {
        // No captured entry point means there's nothing to restore — e.g. a player already sent
        // out on hardcore death (whose return point was consumed). Don't touch their game mode.
        PlayerReturnPoint rp = run.returnPoints().get(uuid);
        if (rp == null) return;

        GameType restoreMode = rp.previousGameMode();
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            // Offline at run end / forfeit — persist the eject so they're sent home on next login.
            PendingRestoreStore.get(world.getServer()).put(uuid, rp);
            return;
        }
        player.setGameMode(restoreMode);
        player.setHealth(player.getMaxHealth());
        ServerLevel returnWorld = world.getServer().getLevel(rp.dimension());
        if (returnWorld != null) {
            player.teleportTo(returnWorld,
                rp.pos().x(), rp.pos().y(), rp.pos().z(), rp.yaw(), rp.pitch());
        }
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
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return;
        if (run.downedPlayers().containsKey(player.getUUID())) return;

        run.updateParticipant(participant.withStatus(ParticipantStatus.DOWNED, world.getGameTime()));
        player.setHealth(1.0F);
        player.setGameMode(GameType.SPECTATOR);
        run.setTimerTicks(Math.max(0, run.timerTicks() - resolveDeathTimePenaltyTicks(controller)));
        run.setDowned(new DownedPlayer(player.getUUID(), resolveRespawnTimeTicks(controller)));
        player.sendSystemMessage(Component.translatable("message.arenas_ld.raid.you_are_downed"));
    }

    public static void handlePlayerDisconnect(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, ServerPlayer player) {
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        // Not a participant, or already removed past the grace window: nothing to track.
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) return;

        if (run.hardcoreEnabled()) {
            run.clearDisconnected(player.getUUID());
            handlePlayerHardcoreDeath(world, run, player);
            return;
        }

        run.markDisconnected(player.getUUID(), world.getGameTime());
        if (!run.downedPlayers().containsKey(player.getUUID())) {
            run.updateParticipant(participant.withStatus(ParticipantStatus.DOWNED, world.getGameTime()));
            if (run.resolvedTierConfig().raidTimeSeconds() > 0) {
                run.setTimerTicks(Math.max(0, run.timerTicks() - resolveDeathTimePenaltyTicks(controller)));
            }
            run.setDowned(new DownedPlayer(player.getUUID(), resolveRespawnTimeTicks(controller)));
        }
        int graceSeconds = Math.max(0, controller.getDisconnectGraceTicks() / 20);
        broadcastToParty(world, run, Component.translatable(
            "message.arenas_ld.raid.party_disconnected", participant.playerName(), graceSeconds)
            .withStyle(ChatFormatting.YELLOW), player.getUUID());
    }

    public static void handlePlayerReconnect(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, ServerPlayer player) {
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null) return;

        run.clearDisconnected(player.getUUID());

        // Stayed offline past the disconnect-grace window: tickDisconnectedPlayers already
        // dropped them from the run. Don't revive them as a participant — send them back to
        // their entry point (restoring their previous game mode) so they aren't stranded as a
        // spectator inside the arena.
        if (participant.status() == ParticipantStatus.REMOVED) {
            run.clearDowned(player.getUUID());
            restoreToReturnPoint(world, run, player.getUUID());
            return;
        }

        DownedPlayer downed = run.downedPlayers().get(player.getUUID());
        if (downed == null) {
            downed = new DownedPlayer(player.getUUID(), resolveRespawnTimeTicks(controller));
            run.setDowned(downed);
        }
        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(player.getMaxHealth());
        if (downed.isReadyToRespawn()) {
            respawnAtEntrance(world, run, player);
            run.clearDowned(player.getUUID());
        }
        broadcastToParty(world, run, Component.translatable(
            "message.arenas_ld.raid.party_reconnected", participant.playerName())
            .withStyle(ChatFormatting.GREEN), player.getUUID());
    }

    public static void handlePlayerHardcoreDeath(ServerLevel world, RaidRun run, ServerPlayer player) {
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) return;

        // Hardcore: the player is out of the run for good. Mark them removed and send them back
        // to their entry point (restoring their previous game mode) rather than leaving them
        // stranded as a spectator inside the arena. Mirrors the dungeon's hardcore handling.
        run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, world.getGameTime()));
        run.clearDowned(player.getUUID());
        run.clearDisconnected(player.getUUID());
        removeFromPartyTeam(world, run, player.getScoreboardName());
        restoreToReturnPoint(world, run, player.getUUID());
        run.removeReturnPoint(player.getUUID());
        BusyStateCompat.clearBusy(player.getUUID(), BUSY_REASON);
        player.sendSystemMessage(Component.translatable("message.arenas_ld.raid.hardcore_death").withStyle(ChatFormatting.RED));
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

    /**
     * Removes participants who have stayed offline past the controller's disconnect grace
     * window. Mirrors {@code DungeonRunLifecycle#tickDisconnectedPlayers}: the participant is
     * marked {@link ParticipantStatus#REMOVED}, its disconnect/downed bookkeeping is dropped,
     * and its busy-state is released so the player isn't stuck busy after rage-quitting a raid.
     */
    private static void tickDisconnectedPlayers(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        if (run.disconnectedAt().isEmpty()) return;

        long now = world.getGameTime();
        int grace = controller.getDisconnectGraceTicks();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(run.disconnectedAt()).entrySet()) {
            if (now - entry.getValue() <= grace) continue;
            UUID uuid = entry.getKey();
            RunParticipant participant = run.participants().get(uuid);
            String name = participant != null ? participant.playerName() : uuid.toString().substring(0, 8);
            if (participant != null) {
                run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, now));
            }
            // Persist the eject so the forfeited player is sent home on next login.
            PlayerReturnPoint rp = run.returnPoints().get(uuid);
            if (rp != null) {
                PendingRestoreStore.get(world.getServer()).put(uuid, rp);
                run.removeReturnPoint(uuid);
            }
            removeFromPartyTeam(world, run, name);
            run.clearDisconnected(uuid);
            run.clearDowned(uuid);
            BusyStateCompat.clearBusy(uuid, BUSY_REASON);
            broadcastToParty(world, run, Component.translatable("message.arenas_ld.raid.party_removed", name)
                .withStyle(ChatFormatting.RED), uuid);
        }
    }

    private static void respawnAtEntrance(ServerLevel world, RaidRun run, ServerPlayer player) {
        if (player == null) return;
        RaidBossSpawnerBlockEntity spawner = spawnerFor(world, run);
        if (spawner == null) return;

        BlockPos target = spawner.getAbsoluteEntrancePos();
        List<BlockPos> respawnOffsets = spawner.getRespawnPointOffsets();
        if (!respawnOffsets.isEmpty()) {
            Vec3 playerPos = player.position();
            target = respawnOffsets.stream()
                    .map(run.spawnerPos()::offset)
                    .min(Comparator.comparingDouble(p ->
                            playerPos.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)))
                    .orElse(target);
        }

        ServerLevel entranceWorld = world.getServer().getLevel(spawner.getEntranceDimension());
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        if (entranceWorld != null) {
            player.teleportTo(entranceWorld,
                target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        }

        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant != null) {
            run.updateParticipant(participant.withStatus(ParticipantStatus.ACTIVE, world.getGameTime()));
        }
    }

    // ── Party team (temporary no-PvP team for the duration of the raid) ─────────

    private static String partyTeamNameFor(RaidRun run) {
        return "ald_r_" + Integer.toHexString(run.spawnerPos().hashCode());
    }

    /** Adds the player to the run's no-PvP party team, remembering their prior team. */
    private static void addToPartyTeam(ServerLevel world, RaidRun run, ServerPlayer player) {
        Scoreboard scoreboard = world.getScoreboard();
        String teamName = partyTeamNameFor(run);
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setAllowFriendlyFire(false);
            team.setSeeFriendlyInvisibles(true);
        }
        String name = player.getScoreboardName();
        PlayerTeam prior = scoreboard.getPlayersTeam(name);
        PartyTeamStore.get(world.getServer()).put(name, prior != null ? prior.getName() : "");
        scoreboard.addPlayerToTeam(name, team);
    }

    /** Removes one member from the run team and restores their prior team (works offline). */
    private static void removeFromPartyTeam(ServerLevel world, RaidRun run, String playerName) {
        Scoreboard scoreboard = world.getScoreboard();
        String priorName = PartyTeamStore.get(world.getServer()).take(playerName);
        if (priorName == null) {
            return;
        }
        PlayerTeam current = scoreboard.getPlayersTeam(playerName);
        if (current != null && current.getName().equals(partyTeamNameFor(run))) {
            scoreboard.removePlayerFromTeam(playerName, current);
        }
        if (!priorName.isEmpty()) {
            PlayerTeam prior = scoreboard.getPlayerTeam(priorName);
            if (prior != null) {
                scoreboard.addPlayerToTeam(playerName, prior);
            }
        }
    }

    /** Restores every member's prior team and deletes the temporary party team. */
    private static void teardownPartyTeam(ServerLevel world, RaidRun run) {
        for (RunParticipant participant : run.participants().values()) {
            removeFromPartyTeam(world, run, participant.playerName());
        }
        PlayerTeam team = world.getScoreboard().getPlayerTeam(partyTeamNameFor(run));
        if (team != null) {
            world.getScoreboard().removePlayerTeam(team);
        }
    }

    /** Sends a message to every online, non-removed participant except {@code except} (nullable). */
    private static void broadcastToParty(ServerLevel world, RaidRun run, Component message, @Nullable UUID except) {
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() == ParticipantStatus.REMOVED) continue;
            if (except != null && entry.getKey().equals(except)) continue;
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(message);
            }
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

    private static int resolveRespawnTimeTicks(@Nullable RaidControllerBlockEntity controller) {
        return controller != null ? controller.getRespawnTimeTicks() : DOWNED_RESPAWN_TICKS;
    }

    private static String formatTime(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        return String.format("%02d:%02d", clamped / 60, clamped % 60);
    }
}
