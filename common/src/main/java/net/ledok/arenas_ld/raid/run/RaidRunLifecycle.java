package net.ledok.arenas_ld.raid.run;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.activity.ActivityHost;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.compat.PuffishSkillsCompat;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
import net.ledok.arenas_ld.dungeon.run.DownedPlayer;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity.ControllerKey;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.ledok.arenas_ld.util.RunPartyTeams;
import net.ledok.arenas_ld.util.PendingRestoreStore;
import net.ledok.arenas_ld.util.PlayerStatsStore;
import net.ledok.arenas_ld.util.RunChunkTickets;
import net.ledok.arenas_ld.util.RunHudSync;
import net.ledok.arenas_ld.util.RunMinions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.ledok.arenas_ld.platform.ArenasPlatform;

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
     * Locate the {@link RaidControllerBlockEntity} that owns the run in the given instance (spawner
     * dimension and position). Walks the registered controller set and returns the first one whose
     * {@code activeRuns} map contains the instance.
     */
    @Nullable
    public static RaidControllerBlockEntity findOwningController(MinecraftServer server, net.ledok.arenas_ld.activity.InstanceKey instance) {
        for (ControllerKey key : RaidControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            BlockEntity be = level.getBlockEntity(key.pos());
            if (be instanceof RaidControllerBlockEntity controller
                && controller.getActiveRuns().containsKey(instance)) {
                return controller;
            }
        }
        return null;
    }

    /** Convenience overload: the spawner at {@code spawnerPos} in {@code level}. */
    @Nullable
    public static RaidControllerBlockEntity findOwningController(ServerLevel level, BlockPos spawnerPos) {
        return findOwningController(level.getServer(), new net.ledok.arenas_ld.activity.InstanceKey(level.dimension(), spawnerPos));
    }

    /** Look up the live run in an instance across all loaded controllers. */
    @Nullable
    public static RaidRun findRun(MinecraftServer server, net.ledok.arenas_ld.activity.InstanceKey instance) {
        RaidControllerBlockEntity controller = findOwningController(server, instance);
        return controller == null ? null : controller.getActiveRuns().get(instance);
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

    /** RUN-kind HUD payload for the raid timer: countdown + boss HP row. */
    private static net.ledok.arenas_ld.packet.RunHudPayload raidHudPayload(RaidRun run, int bossHpPercent) {
        int raidTimeTicks = Math.max(0, run.resolvedTierConfig().raidTimeSeconds() * 20);
        return new net.ledok.arenas_ld.packet.RunHudPayload(
            net.ledok.arenas_ld.packet.RunHudPayload.Kind.RUN,
            Component.translatable("hud.arenas_ld.raid.objective"),
            Math.max(0, run.timerTicks()), raidTimeTicks, run.hardcoreEnabled(),
            Math.min(100, Math.max(0, bossHpPercent)));
    }

    /** CLOSE-kind HUD payload for the post-battle "returning home" countdown. */
    private static net.ledok.arenas_ld.packet.RunHudPayload closeHudPayload(RaidRun run) {
        return new net.ledok.arenas_ld.packet.RunHudPayload(
            net.ledok.arenas_ld.packet.RunHudPayload.Kind.CLOSE,
            Component.translatable("hud.arenas_ld.close"),
            Math.max(0, run.closeTimerTicks()), run.initialCloseTimerTicks(),
            run.hardcoreEnabled(), net.ledok.arenas_ld.packet.RunHudPayload.NO_BOSS_HP);
    }

    /** Online, non-removed participants — the audience for the run HUD bar. */
    private static List<UUID> hudTargets(RaidRun run) {
        List<UUID> targets = new ArrayList<>();
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() != ParticipantStatus.REMOVED) {
                targets.add(entry.getKey());
            }
        }
        return targets;
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
        if (run.phase() != RaidPhase.DONE && !RunChunkTickets.isHeld(chunkOwner(run))) {
            // A run restored after a restart: tickets aren't saved, so take them again.
            holdChunks(world, run);
        }
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

        net.ledok.arenas_ld.util.RunLeftovers.RunKey leftoversKey = net.ledok.arenas_ld.util.RunLeftovers.RunKey.of(run);
        RaidControllerBlockEntity owningController = findOwningController(world.getServer(), run.instanceKey());
        for (ServerPlayer p : players) {
            run.addParticipant(new RunParticipant(
                p.getUUID(), p.getGameProfile().getName(), ParticipantStatus.ACTIVE, now));
            net.ledok.arenas_ld.util.RunLeftovers.enter(owningController, leftoversKey, p.getUUID());
            run.setReturnPoint(p.getUUID(), PlayerReturnPoint.capture(p, false));
            // Everyone is online now; the snapshot pays an offline winner their perks later.
            net.ledok.arenas_ld.util.EndRewardPerks.snapshot(run.perkSnapshots(), p.getUUID(), net.ledok.arenas_ld.util.EndRewardPerks.Mode.RAID);
            PlayerStatsStore.get(world.getServer()).recordRunStart(p.getUUID(), PlayerStatsStore.Mode.RAID);
        }
        if (raidTimeTicks > 0) {
            RunHudSync.send(world, hudTargets(run), raidHudPayload(run, 100));
        }

        if (boss instanceof LivingEntity livingBoss) {
            double healthMult = runTier.healthMultiplier();
            double damageMult = runTier.damageMultiplier();
            // Linear per-player HP scaling: each player beyond the first adds hpScalePerPlayer of base HP.
            double perPlayerMult = 1.0 + Math.max(0, players.size() - 1) * runTier.hpScalePerPlayer();

            EntityEquipmentHelper.applyScaledAttributes(
                livingBoss, entityDefinition.attributes(), world.registryAccess(), healthMult, damageMult, perPlayerMult);
            EntityEquipmentHelper.applyAllEquipment(livingBoss, entityDefinition.equipment());

            livingBoss.heal(livingBoss.getMaxHealth());
            // Owned by the run: never let vanilla despawn or convert the boss.
            EntityEquipmentHelper.markRunMob(livingBoss);
            // For its minions: what reached its own health. Damage to raid players is already
            // multiplied by the tier for every source, so minions get no attack-damage factor.
            net.ledok.arenas_ld.util.RunMinions.recordScale(livingBoss, spawner,
                EntityEquipmentHelper.setsMaxHealth(entityDefinition.attributes()) ? healthMult * perPlayerMult : 1.0, 1.0);
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

        // A boss left behind by an earlier run (its chunk was unloaded when that run ended) must not
        // fight alongside this one.
        sweepStrayBosses(world, run.spawnerPos(), entityTypeOpt.get());

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
        // Their spell summons left here by an earlier run go; this run's own and real pets stay.
        net.ledok.arenas_ld.util.RunLeftovers.startSweep(leftoversKey, players);

        // Spawn drama: smoke burst at the boss and a title/sting for the party.
        world.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
            bossSpawnPos.x, bossSpawnPos.y + 1.0, bossSpawnPos.z, 40, 0.8, 1.0, 0.8, 0.02);
        for (ServerPlayer player : players) {
            net.ledok.arenas_ld.util.RunFeedback.title(player,
                Component.translatable("title.arenas_ld.raid_start").withStyle(ChatFormatting.GOLD),
                mobDisplayName.copy());
            net.ledok.arenas_ld.util.RunFeedback.sound(player,
                net.minecraft.sounds.SoundEvents.WITHER_SPAWN, 0.7f, 1.0f);
        }

        run.setBossRef(boss.getUUID(), world.dimension());
        run.setBossLastPos(boss.blockPosition());
        run.setRegenerationTickTimer(0);
        holdChunks(world, run);
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
        if (bossEntity != null && bossEntity.isRemoved() && !bossEntity.getRemovalReason().shouldDestroy()) {
            bossEntity = null; // unloaded to its chunk (or moved away), not dead
        }
        if (bossEntity == null) {
            if (waitForMissingBoss(world, bossWorld, controller, run)) {
                // The fight is paused (no timer, no regeneration) until the boss is back.
                tickDownedPlayers(world, run);
                tickDisconnectedPlayers(world, controller, run);
            }
            return;
        }
        if (run.bossMissingSince() >= 0) {
            ArenasLdMod.LOGGER.info("Raid at {}: boss {} is loaded again after {} ticks, the fight goes on.",
                run.spawnerPos(), bossUuid, world.getGameTime() - run.bossMissingSince());
            run.setBossMissingSince(-1L);
            run.setBossMissingLoadedSince(-1L);
        }
        trackBoss(world, controller, run, bossEntity);

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
            updateRaidTimerBossBar(world, run, bossEntity);
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

    /**
     * Whether the run's result is still open. A run is won or lost once: a second win or loss
     * (a run listener ending it again from inside the first) would pay, report and close twice.
     */
    private static boolean undecided(RaidRun run) {
        return run.outcome() == RaidOutcome.IN_PROGRESS
            && (run.phase() == RaidPhase.STARTING || run.phase() == RaidPhase.RUNNING);
    }

    static void handleWin(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        if (!undecided(run)) return;
        ArenasLdMod.LOGGER.info("Raid battle won at spawner {}", run.spawnerPos());
        run.setOutcome(RaidOutcome.WIN);
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            Component.translatable("title.arenas_ld.victory").withStyle(ChatFormatting.GREEN), null,
            net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        Set<UUID> rewardIds = run.lootEligibleUuids();
        RaidTierConfig tierCfg = run.resolvedTierConfig();

        for (UUID uuid : rewardIds) {
            PlayerStatsStore.get(world.getServer()).recordWin(uuid, PlayerStatsStore.Mode.RAID);
        }

        int xpReward = tierCfg.skillExperiencePerWin();
        long rewardPerPlayer = tierCfg.rewardCurrency();
        if (run.hardcoreEnabled()) {
            rewardPerPlayer *= 2;
        }
        String perPlayerLoot = tierCfg.perPlayerLootTable();
        boolean puffishLoaded = ArenasPlatform.INSTANCE.isModLoaded("puffish_skills");

        for (UUID uuid : rewardIds) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            // LuckPerms perks (extra rolls, currency/XP multipliers): live when online, else the
            // run-start snapshot.
            net.ledok.arenas_ld.util.EndRewardPerks perks = net.ledok.arenas_ld.util.EndRewardPerks.resolve(
                run.perkSnapshots(), uuid, player != null, net.ledok.arenas_ld.util.EndRewardPerks.Mode.RAID);
            List<ItemStack> loot = net.ledok.arenas_ld.util.RewardDelivery.rollLoot(world, perPlayerLoot, player,
                player != null ? player.position() : Vec3.atCenterOf(run.spawnerPos()), perks.lootRolls());
            long currency = perks.scaleCurrency(rewardPerPlayer);

            if (player == null) {
                // Offline but loot-eligible: the reward still lands via the economy inbox.
                net.ledok.arenas_ld.util.RewardDelivery.giveStacksOffline(uuid, loot, "RAID_LOOT");
                net.ledok.arenas_ld.util.RewardDelivery.giveCurrency(world.getServer(), uuid, currency, "RAID_REWARD");
                continue;
            }

            List<net.ledok.arenas_ld.packet.LootRewardPayload.Entry> delivered =
                net.ledok.arenas_ld.util.RewardDelivery.giveStacks(player, loot, "RAID_LOOT");
            net.ledok.arenas_ld.util.RewardDelivery.giveCurrency(world.getServer(), uuid, currency, "RAID_REWARD");

            int grantedXp = 0;
            int scaledXp = perks.scaleXp(xpReward);
            if (scaledXp > 0 && puffishLoaded) {
                PuffishSkillsCompat.addExperience(player, scaledXp);
                grantedXp = scaledXp;
            }

            net.ledok.arenas_ld.util.RewardDelivery.notify(player,
                net.ledok.arenas_ld.packet.LootRewardPayload.Source.RAID_WIN, delivered,
                net.ledok.arenas_ld.util.RewardDelivery.displayableCurrency(currency), grantedXp);
        }

        // Offline winners get their per-player commands on their next join.
        net.ledok.arenas_ld.util.RewardCommands.run(world, Vec3.atCenterOf(run.spawnerPos()), rewardIds,
            tierCfg.rewardCommands(), true);

        // Before enterClosing: with no close timer it finalizes and releases the run right away.
        fireRunResult(world, controller, run, ArenasApi.Outcome.WIN);
        enterClosing(world, controller, run);
    }

    /** Reports the finished run to {@code ArenasApi} run listeners. */
    private static void fireRunResult(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run,
                                      ArenasApi.Outcome outcome) {
        net.ledok.arenas_ld.activity.RunEvents.fire(controller, run.tier(), outcome,
            run.startTick(), world.getGameTime(), 0,
            run.participants().keySet());
    }

    static void handleLoss(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, String reason) {
        handleLoss(world, controller, run, reason, false);
    }

    /**
     * Ends the fight as a loss: no rewards, the boss discarded, then the exit timer.
     *
     * @param byStaff ended by staff ({@link #forceEnd}): players are told so, and it is reported as
     *                {@code Outcome.FORCED} instead of a loss
     */
    private static void handleLoss(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, String reason,
                                   boolean byStaff) {
        if (!undecided(run)) return;
        ArenasLdMod.LOGGER.info("Raid battle lost at spawner {}: {}", run.spawnerPos(), reason);
        run.setOutcome(byStaff ? RaidOutcome.LOSS_STAFF : RaidOutcome.LOSS_FORCED);
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            Component.translatable("title.arenas_ld.defeat").withStyle(ChatFormatting.RED), null,
            net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.6f, 0.7f);
        if (byStaff) {
            broadcastToParty(world, run, Component.translatable("message.arenas_ld.raid.ended_by_admin")
                .withStyle(ChatFormatting.RED), null);
        }

        discardBoss(world, run);

        fireRunResult(world, controller, run, byStaff ? ArenasApi.Outcome.FORCED : ArenasApi.Outcome.LOSS);
        enterClosing(world, controller, run);
    }

    /**
     * Staff ends one run ({@code ArenasApi.forceEnd}). A run still being fought is lost as
     * {@link #handleLoss} would (no rewards, boss discarded, the usual exit timer) but reported as
     * {@code Outcome.FORCED}; a run on its exit timer is closed now, keeping its result; a finished
     * run that was never released (an unreadable saved phase reads as DONE) is released without
     * being reported. Safe outside the controller's tick, on the server thread.
     *
     * @return whether the run was ended or closed
     */
    public static boolean forceEnd(MinecraftServer server, RaidControllerBlockEntity controller, RaidRun run) {
        if (controller.getActiveRuns().get(run.instanceKey()) != run) {
            return false; // already released
        }
        ServerLevel world = resolveSpawnerLevel(server, run);
        if (world == null) {
            // Its dimension is gone (a removed dimension mod): the controller never ticks it, so
            // release it now from the controller's level. Its boss can't be reached any more.
            if (!(controller.getLevel() instanceof ServerLevel home)) {
                return false;
            }
            if (undecided(run)) {
                run.setOutcome(RaidOutcome.LOSS_STAFF);
                fireRunResult(home, controller, run, ArenasApi.Outcome.FORCED);
            }
            finalizeRun(home, controller, run, false);
            return true;
        }
        switch (run.phase()) {
            case STARTING, RUNNING -> {
                if (!undecided(run)) {
                    return false; // being won or lost right now (a run listener calling back in)
                }
                handleLoss(world, controller, run, "Ended by an admin.", true);
            }
            case CLOSING -> finalizeRun(world, controller, run);
            case DONE -> finalizeRun(world, controller, run); // discards the boss too
        }
        return true;
    }

    /** Discards the run's boss if it is loaded and alive (its minions go with it). */
    private static void discardBoss(ServerLevel world, RaidRun run) {
        Entity bossEntity = loadedBoss(world.getServer(), run);
        if (bossEntity != null && bossEntity.isAlive()) {
            bossEntity.discard();
            ArenasLdMod.LOGGER.info("Despawned boss of the ended raid at {}.", run.spawnerPos());
        }
    }

    /** The run's boss when it is loaded; an in-memory lookup that never loads a chunk. */
    private static @Nullable Entity loadedBoss(MinecraftServer server, RaidRun run) {
        UUID bossUuid = run.bossUuid();
        ResourceKey<Level> bossDim = run.bossDimension();
        if (bossUuid == null || bossDim == null) {
            return null;
        }
        ServerLevel bossWorld = server.getLevel(bossDim);
        return bossWorld == null ? null : bossWorld.getEntity(bossUuid);
    }

    /**
     * Transition a finished battle into the CLOSING grace phase. Players linger for
     * {@code controller.getCloseTimerSeconds()} before being teleported out by
     * {@link #finalizeRun}. The raid-timer bar is swapped for a close-timer bar.
     */
    private static void enterClosing(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        run.setPhase(RaidPhase.CLOSING);
        int closeTicks = Math.max(0, controller.getCloseTimerSeconds() * 20);
        run.setInitialCloseTimerTicks(closeTicks);
        run.setCloseTimerTicks(closeTicks);
        if (closeTicks > 0) {
            // Swap the raid timer for the close countdown right away, not on the next second tick.
            RunHudSync.send(world, hudTargets(run), closeHudPayload(run));
        }
        // No grace configured, or nobody left to wait for (e.g. a hardcore wipe) — finalize now
        // instead of ticking through an empty close timer.
        boolean anyRemaining = run.participants().values().stream()
            .anyMatch(p -> p.status() != ParticipantStatus.REMOVED);
        if (closeTicks <= 0 || !anyRemaining) {
            finalizeRun(world, controller, run);
            return;
        }
        sendExitPrompt(world, run);
    }

    /** Offers each remaining participant a clickable "[Exit Now]" to skip the close timer. */
    private static void sendExitPrompt(ServerLevel world, RaidRun run) {
        net.minecraft.network.chat.MutableComponent button = net.ledok.arenas_ld.util.LobbyChatActions.button(
            Component.translatable("message.arenas_ld.run.exit_button"),
            0x55FF55,
            "/exit",
            Component.translatable("message.arenas_ld.run.exit_hover"));
        Component line = Component.translatable("message.arenas_ld.run.exit_prompt").append(button);
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() == ParticipantStatus.REMOVED) continue;
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(line);
            }
        }
    }

    /**
     * Pulls a single player out of their raid before its shared timers elapse. Mirrors
     * {@code DungeonRunLifecycle#exitEarly}:
     * <ul>
     *   <li>CLOSING — the battle already ended; they skip the close-timer wait.</li>
     *   <li>STARTING/RUNNING — a voluntary mid-run forfeit: they're sent home and removed (no
     *       rewards), and the raid goes on for the rest of the party, or is lost next tick if
     *       nobody is left in it.</li>
     * </ul>
     *
     * @return true if the player was in a raid and was removed; false otherwise.
     */
    public static boolean exitEarly(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        for (RaidControllerBlockEntity.ControllerKey key : RaidControllerBlockEntity.getControllers()) {
            ServerLevel world = server.getLevel(key.dimension());
            if (world == null) continue;
            if (!(world.getBlockEntity(key.pos()) instanceof RaidControllerBlockEntity controller)) continue;
            for (RaidRun run : controller.getActiveRuns().values()) {
                if (run.phase() == RaidPhase.DONE) continue;
                RunParticipant participant = run.participants().get(uuid);
                if (participant == null || participant.status() == ParticipantStatus.REMOVED) continue;
                boolean midRun = run.phase() != RaidPhase.CLOSING;
                // The run lives in its spawner's dimension, which needn't be the controller's: using
                // the controller's level here looked the spawner up (loading a chunk) at the right
                // coordinates in the wrong dimension, and finalize then missed it.
                ServerLevel runWorld = server.getLevel(run.spawnerDimension());
                if (runWorld == null) runWorld = world;

                restoreToReturnPoint(runWorld, run, uuid);
                run.removeReturnPoint(uuid);
                run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, runWorld.getGameTime()));
                run.clearDowned(uuid);
                run.clearDisconnected(uuid);
                BusyStateCompat.clearBusy(uuid, BUSY_REASON);
                removeFromPartyTeam(runWorld, run, participant.playerName());
                RunHudSync.hide(player);

                if (midRun) {
                    // Forfeit: no rewards for the leaver; tickRunning loses the raid if nobody is left.
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.raid.exited_mid_run")
                        .withStyle(ChatFormatting.YELLOW));
                    broadcastToParty(runWorld, run, Component.translatable("message.arenas_ld.raid.party_exited",
                        participant.playerName()).withStyle(ChatFormatting.YELLOW), uuid);
                    return true;
                }

                boolean anyRemaining = run.participants().values().stream()
                    .anyMatch(p -> p.status() != ParticipantStatus.REMOVED);
                if (!anyRemaining) {
                    finalizeRun(runWorld, controller, run);
                }
                return true;
            }
        }
        return false;
    }

    private static void tickClosing(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        run.setCloseTimerTicks(run.closeTimerTicks() - 1);
        updateCloseTimerBossBar(world, run);
        if (run.closeTimerTicks() <= 0) {
            finalizeRun(world, controller, run);
        }
    }

    private static void updateCloseTimerBossBar(ServerLevel world, RaidRun run) {
        RunHudSync.tickSend(world, hudTargets(run), closeHudPayload(run));
    }

    /** Teleport players back to their captured return points and tear down the run's transient state. */
    static void finalizeRun(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run) {
        finalizeRun(world, controller, run, true);
    }

    /**
     * @param spawnerLevel {@code world} is the run's spawner level; false when that dimension is
     *                     gone and {@code world} is the controller's (the spawner can't be looked up)
     */
    private static void finalizeRun(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run,
                                    boolean spawnerLevel) {
        // Whatever ended the run, its boss must not outlive it (a loss already discarded it; a won
        // boss is dead). One not loaded now is swept by the next battle here.
        discardBoss(world, run);
        for (UUID uuid : run.participants().keySet()) {
            // Players return to where they were when the run started (captured on entry).
            restoreToReturnPoint(world, run, uuid);
        }
        teardownPartyTeam(world, run);

        RunHudSync.hide(world, hudTargets(run));
        run.setPhase(RaidPhase.DONE);
        RaidBossSpawnerBlockEntity spawner = spawnerLevel ? spawnerFor(world, run) : null;
        if (spawner != null) {
            ArenasLdMod.RAID_BOSS_MANAGER.unregisterSpawner(spawner);
        }
        RunChunkTickets.release(world.getServer(), chunkOwner(run));
        run.setHeldBossChunk(null);
        // Notify the controller LAST: onRaidEnded removes the run from activeRuns and
        // releases the instance, so everything above must have run first.
        notifyController(world, controller, run, run.outcome() == RaidOutcome.WIN);
    }

    /**
     * Send a single participant back to their captured return point, restoring their previous
     * game mode. If the player is offline, queue a pending restore so they're moved on next login.
     */
    private static void restoreToReturnPoint(ServerLevel world, RaidRun run, UUID uuid) {
        sendHome(world, run, uuid);
        // Then what they leave behind: summons go, pets and thrown items follow them home.
        net.ledok.arenas_ld.util.RunLeftovers.left(world.getServer(), uuid, net.ledok.arenas_ld.util.RunLeftovers.RunKey.of(run));
    }

    private static void sendHome(ServerLevel world, RaidRun run, UUID uuid) {
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
        net.ledok.arenas_ld.util.DownedSpectators.release(player);
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
        controller.onRaidEnded(run.instanceKey(), wasWin, diff, new ArrayList<>(names), elapsed);
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
        // Watching a teammate, or held near where they fell (not a free spectator).
        net.ledok.arenas_ld.util.DownedSpectators.down(player, net.ledok.arenas_ld.util.DownedSpectators.RunView.of(run));
        player.sendSystemMessage(Component.translatable("message.arenas_ld.raid.you_are_downed"));
    }

    public static void handlePlayerDisconnect(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, ServerPlayer player) {
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        // Not a participant, or already removed past the grace window: nothing to track.
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) return;

        // Every player is disconnected when the server stops: that is no rage-quit, so it neither
        // counts as a hardcore death (wiping every hardcore raid on restart) nor costs time.
        boolean serverStopping = !world.getServer().isRunning();
        if (run.hardcoreEnabled() && !serverStopping) {
            run.clearDisconnected(player.getUUID());
            handlePlayerHardcoreDeath(world, run, player);
            return;
        }

        run.markDisconnected(player.getUUID(), world.getGameTime());
        controller.setChanged(); // after the last tick at shutdown, so it reaches the save
        if (!run.downedPlayers().containsKey(player.getUUID())) {
            run.updateParticipant(participant.withStatus(ParticipantStatus.DOWNED, world.getGameTime()));
            if (run.resolvedTierConfig().raidTimeSeconds() > 0 && !serverStopping) {
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
        } else {
            net.ledok.arenas_ld.util.DownedSpectators.rejoined(player, net.ledok.arenas_ld.util.DownedSpectators.RunView.of(run));
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
            net.ledok.arenas_ld.util.DownedSpectators.forget(uuid);
            BusyStateCompat.clearBusy(uuid, BUSY_REASON);
            // Offline: their summons go now; pets and thrown items wait for them.
            net.ledok.arenas_ld.util.RunLeftovers.left(world.getServer(), uuid, net.ledok.arenas_ld.util.RunLeftovers.RunKey.of(run));
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
        net.ledok.arenas_ld.util.DownedSpectators.release(player);
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
        return RunPartyTeams.teamName("ald_r_", run.spawnerDimension(), run.spawnerPos());
    }

    /** Moves the player onto the run's no-PvP party team, remembering their prior team. */
    private static void addToPartyTeam(ServerLevel world, RaidRun run, ServerPlayer player) {
        RunPartyTeams.add(world, partyTeamNameFor(run), player);
    }

    /** Takes one member off the run team and gives back their prior team (works offline). */
    private static void removeFromPartyTeam(ServerLevel world, RaidRun run, String playerName) {
        RunPartyTeams.remove(world, partyTeamNameFor(run), playerName);
    }

    /** Gives every member back their prior team and deletes the temporary party team. */
    private static void teardownPartyTeam(ServerLevel world, RaidRun run) {
        RunPartyTeams.teardown(world, partyTeamNameFor(run), RunPartyTeams.legacyTeamName("ald_r_", run.spawnerPos()),
            run.participants().values().stream().map(RunParticipant::playerName).toList());
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

    private static void updateRaidTimerBossBar(ServerLevel world, RaidRun run, @Nullable Entity boss) {
        int raidTimeTicks = Math.max(0, run.resolvedTierConfig().raidTimeSeconds() * 20);
        if (raidTimeTicks <= 0) {
            return; // untimed raid: no clock to show (matches the old hidden bar)
        }
        RunHudSync.tickSend(world, hudTargets(run), raidHudPayload(run, bossHealthPercent(boss)));
    }

    /** Death-time penalty from the controller, falling back to the legacy constant. */
    private static int resolveDeathTimePenaltyTicks(@Nullable RaidControllerBlockEntity controller) {
        return controller != null ? controller.getDeathTimePenaltyTicks() : DEATH_TIME_PENALTY_TICKS;
    }

    private static int resolveRespawnTimeTicks(@Nullable RaidControllerBlockEntity controller) {
        return controller != null ? controller.getRespawnTimeTicks() : DOWNED_RESPAWN_TICKS;
    }

    // ── Chunk tickets and the boss's whereabouts ────────────────────────────────

    /** How often (ticks) the boss's position is noted and its chunk ticket moved after it. */
    private static final int BOSS_TRACK_INTERVAL = 20;
    /** How often (ticks) a running raid sweeps its arena for bosses left by earlier runs. */
    private static final int STRAY_SWEEP_INTERVAL = 100;
    /** The stray-boss sweep box around the spawner: blocks each way horizontally / vertically. */
    public static final int SWEEP_HORIZONTAL = 96;
    public static final int SWEEP_VERTICAL = 64;

    /** Who holds this run's chunk tickets: its instance (spawner dimension + position). */
    public static RunChunkTickets.Owner chunkOwner(RaidRun run) {
        return RunChunkTickets.owner(run.spawnerDimension(), run.spawnerPos());
    }

    /**
     * Keeps the run's chunks loaded with tickets owned by its instance: the spawner, the entrance
     * (in its own dimension) and the chunk the boss was last seen in (in the boss's dimension). A
     * raid can't rely on its players for this: downed players are spectators, which may not load
     * chunks, and the boss may stand far from the spawner.
     */
    static void holdChunks(ServerLevel world, RaidRun run) {
        Set<RunChunkTickets.HeldChunk> chunks = new HashSet<>();
        chunks.add(new RunChunkTickets.HeldChunk(run.spawnerDimension(), new ChunkPos(run.spawnerPos())));
        RaidBossSpawnerBlockEntity spawner = spawnerFor(world, run);
        if (spawner != null) {
            ResourceKey<Level> entranceDim = spawner.getEntranceDimension() != null
                ? spawner.getEntranceDimension() : run.spawnerDimension();
            chunks.add(new RunChunkTickets.HeldChunk(entranceDim, new ChunkPos(spawner.getAbsoluteEntrancePos())));
        }
        RunChunkTickets.HeldChunk bossChunk = bossChunk(run);
        chunks.add(bossChunk);
        run.setHeldBossChunk(bossChunk);
        RunChunkTickets.hold(world.getServer(), chunkOwner(run), chunks);
    }

    /** The chunk the boss was last seen in, in its dimension (the spawner's when not known yet). */
    private static RunChunkTickets.HeldChunk bossChunk(RaidRun run) {
        ResourceKey<Level> dim = run.bossDimension() != null ? run.bossDimension() : run.spawnerDimension();
        BlockPos pos = run.bossLastPos() != null ? run.bossLastPos() : run.spawnerPos();
        return new RunChunkTickets.HeldChunk(dim, new ChunkPos(pos));
    }

    /** Moves the boss-chunk ticket when the boss's last-known chunk is not the one held. */
    private static void followBossChunk(ServerLevel world, RaidRun run) {
        if (!Objects.equals(bossChunk(run), run.heldBossChunk()) || !RunChunkTickets.isHeld(chunkOwner(run))) {
            holdChunks(world, run);
        }
    }

    /** Once a second: notes where the boss is (saved with the run) and keeps its chunk held. */
    private static void trackBoss(ServerLevel world, RaidControllerBlockEntity controller, RaidRun run, Entity boss) {
        long now = world.getGameTime();
        if (run.bossLastPos() != null && now % BOSS_TRACK_INTERVAL != 0) {
            return;
        }
        BlockPos pos = boss.blockPosition();
        if (!pos.equals(run.bossLastPos())) {
            run.setBossLastPos(pos);
            controller.setChanged();
        }
        followBossChunk(world, run);
        if ((now - run.startTick()) % STRAY_SWEEP_INTERVAL < BOSS_TRACK_INTERVAL
                && world.dimension().equals(boss.level().dimension())) {
            // A boss left by an earlier run whose chunk only loaded now (players walked near it).
            sweepStrayBosses(world, run.spawnerPos(), boss.getType());
        }
    }

    /**
     * The boss isn't found. It is usually not gone but unloaded with its chunk (saved to disk):
     * take a ticket on its last-known chunk and wait for the entities there to load, which takes a
     * few ticks. Lost only as {@link BossPresence#decide} says.
     *
     * @return true while waiting; false when the raid was lost
     */
    private static boolean waitForMissingBoss(ServerLevel world, ServerLevel bossWorld,
                                              RaidControllerBlockEntity controller, RaidRun run) {
        long now = world.getGameTime();
        BlockPos last = run.bossLastPos() != null ? run.bossLastPos() : run.spawnerPos();
        ChunkPos chunk = new ChunkPos(last);
        boolean entitiesLoaded = bossWorld.areEntitiesLoaded(chunk.toLong());
        if (run.bossMissingSince() < 0) {
            run.setBossMissingSince(now);
            run.setBossMissingLoadedSince(-1L);
            ArenasLdMod.LOGGER.info("Raid at {}: boss {} is not loaded (last seen at {} in {}, entities there loaded: {});"
                    + " waiting for it to load.", run.spawnerPos(), run.bossUuid(),
                run.bossLastPos() != null ? run.bossLastPos().toShortString() : "unknown",
                bossWorld.dimension().location(), entitiesLoaded);
        }
        if (entitiesLoaded) {
            if (run.bossMissingLoadedSince() < 0) {
                run.setBossMissingLoadedSince(now);
            }
        } else {
            run.setBossMissingLoadedSince(-1L);
            followBossChunk(world, run);
        }
        long missingFor = now - run.bossMissingSince();
        long loadedFor = entitiesLoaded ? now - run.bossMissingLoadedSince() : -1L;
        if (BossPresence.decide(missingFor, loadedFor) == BossPresence.Verdict.LOSE) {
            handleLoss(world, controller, run, "Boss entity disappeared (not found for " + missingFor
                + " ticks; last seen at " + (run.bossLastPos() != null ? run.bossLastPos().toShortString() : "unknown")
                + ", entities there loaded: " + entitiesLoaded + ").");
            return false;
        }
        return true;
    }

    /** The bosses of every raid run going on now, on every loaded raid controller. */
    private static Set<UUID> activeRaidBosses(MinecraftServer server) {
        Set<UUID> bosses = new HashSet<>();
        for (ControllerKey key : RaidControllerBlockEntity.getControllers()) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) continue;
            if (level.getBlockEntity(key.pos()) instanceof RaidControllerBlockEntity controller) {
                for (RaidRun run : controller.getActiveRuns().values()) {
                    if (run.bossUuid() != null) {
                        bosses.add(run.bossUuid());
                    }
                }
            }
        }
        return bosses;
    }

    /**
     * Discards loaded mobs of the boss's type around a raid spawner that were spawned by a run
     * (they carry the run-mob tag) but are the boss of no raid going on now: bosses left behind
     * by runs that ended while their chunk was unloaded. Minions are left to {@code RunMinions}
     * (they vanish with their discarded summoner). The box is {@link #SWEEP_HORIZONTAL} blocks
     * each way horizontally and {@link #SWEEP_VERTICAL} up and down, within the build height.
     *
     * @return how many were discarded
     */
    public static int sweepStrayBosses(ServerLevel level, BlockPos spawnerPos, EntityType<?> bossType) {
        Set<UUID> active = activeRaidBosses(level.getServer());
        AABB box = new AABB(
            spawnerPos.getX() - SWEEP_HORIZONTAL, Math.max(level.getMinBuildHeight(), spawnerPos.getY() - SWEEP_VERTICAL),
            spawnerPos.getZ() - SWEEP_HORIZONTAL,
            spawnerPos.getX() + SWEEP_HORIZONTAL + 1, Math.min(level.getMaxBuildHeight(), spawnerPos.getY() + SWEEP_VERTICAL + 1),
            spawnerPos.getZ() + SWEEP_HORIZONTAL + 1);
        List<? extends Entity> strays = level.getEntities(bossType, box, entity -> entity.isAlive()
            && entity.getTags().contains(EntityEquipmentHelper.RUN_MOB_TAG)
            && !RunMinions.isMinion(entity)
            && !active.contains(entity.getUUID()));
        for (Entity stray : strays) {
            ArenasLdMod.LOGGER.info("Raid at {}: discarding a stray {} left by an earlier run at {}.",
                spawnerPos, BuiltInRegistries.ENTITY_TYPE.getKey(bossType), stray.blockPosition().toShortString());
            stray.discard();
        }
        if (!strays.isEmpty()) {
            ArenasLdMod.LOGGER.info("Raid at {}: discarded {} stray boss(es).", spawnerPos, strays.size());
        }
        return strays.size();
    }

    // ── Status (ArenasApi.status) ───────────────────────────────────────────────

    /**
     * The run as {@code ArenasApi.status} reports it. Reads memory only (the boss through
     * {@code ServerLevel.getEntity}), so it is cheap and never loads a chunk.
     */
    public static ArenasApi.RunStatus status(MinecraftServer server, RaidRun run, long gameTime) {
        List<UUID> players = new ArrayList<>();
        for (RunParticipant participant : run.participants().values()) {
            if (participant.status() != ParticipantStatus.REMOVED) {
                players.add(participant.playerUuid());
            }
        }
        boolean closing = run.phase() == RaidPhase.CLOSING;
        Component phase;
        long ticksLeft;
        if (closing) {
            phase = Component.translatable(switch (run.outcome()) {
                case WIN -> "status.arenas_ld.raid.closing_won";
                case LOSS_STAFF -> "status.arenas_ld.raid.closing_forced";
                default -> "status.arenas_ld.raid.closing_lost";
            });
            ticksLeft = Math.max(0, run.closeTimerTicks());
        } else {
            if (run.phase() == RaidPhase.STARTING) {
                phase = Component.translatable("status.arenas_ld.raid.starting");
            } else {
                Entity boss = loadedBoss(server, run);
                phase = boss instanceof LivingEntity && boss.isAlive()
                    ? Component.translatable("status.arenas_ld.raid.boss_health", bossHealthPercent(boss))
                    : Component.translatable("status.arenas_ld.raid.boss");
            }
            // Untimed raids have no clock (see updateRaidTimerBossBar).
            ticksLeft = run.resolvedTierConfig().raidTimeSeconds() > 0 ? Math.max(0, run.timerTicks()) : -1;
        }
        return new ArenasApi.RunStatus(new ArenasApi.InstanceRef(run.spawnerDimension(), run.spawnerPos()),
            ActivityHost.difficultyId(run.tier()), List.copyOf(players), phase,
            Math.max(0L, gameTime - run.startTick()), ticksLeft, closing);
    }

    /** Whole-percent remaining boss health, ceil'd so the bar never reads 0% while the boss lives. */
    private static int bossHealthPercent(@Nullable Entity boss) {
        if (boss instanceof LivingEntity living && living.getMaxHealth() > 0.0f) {
            return Mth.ceil(living.getHealth() * 100.0f / living.getMaxHealth());
        }
        return 100;
    }

}
