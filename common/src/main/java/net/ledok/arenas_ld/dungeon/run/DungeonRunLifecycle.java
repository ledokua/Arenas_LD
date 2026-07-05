package net.ledok.arenas_ld.dungeon.run;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.room.RoomEffectData;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.ledok.arenas_ld.util.LobbyChatActions;
import net.ledok.arenas_ld.util.PartyTeamStore;
import net.ledok.arenas_ld.util.PendingRestoreStore;
import net.ledok.arenas_ld.util.PlayerStatsStore;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DungeonRunLifecycle {
    public static final String BUSY_REASON = net.ledok.busylib.BusyReasons.IN_DUNGEON;

    private DungeonRunLifecycle() {
    }

    public static void tick(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        switch (run.phase()) {
            case STARTING -> tickStarting(world, controller, run);
            case RUNNING -> tickRunning(world, controller, run);
            case CLOSING -> tickClosing(world, controller, run);
            case DONE -> {
                // Should be removed from controller already.
            }
        }
    }

    @Nullable
    public static DungeonRun startRun(
        ServerLevel world,
        DungeonControllerBlockEntity controller,
        BlockPos dbsPos,
        List<UUID> partyUuids,
        DifficultyTier tier,
        boolean hardcore,
        String ownerName
    ) {
        if (controller.getActiveRuns().containsKey(dbsPos)) return null;
        if (controller.getInstanceCooldownTimers().containsKey(dbsPos)) return null;

        BlockEntity dbsBe = world.getBlockEntity(dbsPos);
        if (!(dbsBe instanceof DungeonBossSpawnerBlockEntity dbs)) return null;

        // Branching dungeons need explicit Start/Final markers before a run may begin.
        if (isBranching(world, dbs)) {
            BlockPos startRoom = dbs.getAbsoluteStartRoomPos();
            BlockPos finalRoom = dbs.getAbsoluteFinalRoomPos();
            String errorKey = null;
            if (startRoom == null || finalRoom == null) {
                errorKey = "message.arenas_ld.dungeon.no_start_final_marker";
            } else if (!(world.getBlockEntity(startRoom) instanceof RoomControllerBlockEntity startController)
                    || startController.getEntranceOffsets().isEmpty()) {
                errorKey = "message.arenas_ld.dungeon.start_room_no_entrance";
            }
            if (errorKey != null) {
                for (UUID uuid : partyUuids) {
                    ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable(errorKey).withStyle(ChatFormatting.RED));
                    }
                }
                return null;
            }
        }

        forceLoadChunksForRun(world, dbsPos);

        TierConfig tierConfig = controller.getTierConfigs().getOrDefault(tier, TierConfig.defaultFor(tier));
        DungeonRun run = new DungeonRun(tier, tierConfig, hardcore, dbsPos, world.dimension(), world.getGameTime(), ownerName);

        ServerLevel targetLevel = world.getServer().getLevel(dbs.getEntranceDimension());
        if (targetLevel == null) {
            targetLevel = world;
        }

        for (UUID uuid : partyUuids) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;

            run.addParticipant(new RunParticipant(
                uuid,
                player.getGameProfile().getName(),
                ParticipantStatus.ACTIVE,
                world.getGameTime()
            ));
            run.setReturnPoint(uuid, PlayerReturnPoint.capture(player));
            ArenasLdMod.DUNGEON_MANAGER.registerParticipant(uuid, run);
            BusyStateCompat.setBusy(uuid, BUSY_REASON);
            addToPartyTeam(world, run, player);
            PlayerStatsStore.get(world.getServer()).recordRunStart(uuid, PlayerStatsStore.Mode.DUNGEON);

            BlockPos entrance = dbs.getAbsoluteEntrancePos();
            player.setGameMode(GameType.ADVENTURE);
            player.teleportTo(targetLevel, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, 0.0f, 0.0f);
        }

        // Freeze the per-player HP multiplier at run start — players leaving mid-run don't weaken it.
        run.setPartyHealthMultiplier(controller.resolvePartyHealthMultiplier(run.participants().size()));

        for (BlockPos roomPos : dbs.getRooms()) {
            BlockEntity roomBe = world.getBlockEntity(roomPos);
            if (roomBe instanceof RoomControllerBlockEntity roomController) {
                roomController.reset(world);
            }
        }

        // Branching: the start room begins like any other — its entrance opens (armed/orange:
        // the room beyond isn't cleared), players walk in.
        if (isBranching(world, dbs)
                && world.getBlockEntity(dbs.getAbsoluteStartRoomPos()) instanceof RoomControllerBlockEntity startRoom) {
            startRoom.openEntranceDoorsArmed(world);
        }

        controller.startRun(dbsPos, run);
        return run;
    }

    private static void tickStarting(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        run.setPhase(DungeonPhase.RUNNING);
    }

    private static void tickRunning(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        if (!run.participants().isEmpty()) {
            UUID anyUuid = run.participants().keySet().iterator().next();
            if (ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(anyUuid) == null) {
                for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
                    if (entry.getValue().status() != ParticipantStatus.REMOVED) {
                        ArenasLdMod.DUNGEON_MANAGER.registerParticipant(entry.getKey(), run);
                    }
                }
                BlockEntity bootstrapDbsBe = world.getBlockEntity(run.dbsPos());
                if (bootstrapDbsBe instanceof DungeonBossSpawnerBlockEntity bootstrapDbs) {
                    for (BlockPos roomPos : bootstrapDbs.getRooms()) {
                        BlockEntity roomBe = world.getBlockEntity(roomPos);
                        if (roomBe instanceof RoomControllerBlockEntity roomController) {
                            for (UUID mobUuid : roomController.getAliveMobs()) {
                                ArenasLdMod.DUNGEON_MANAGER.registerMob(mobUuid, run);
                            }
                        }
                    }
                }
            }
        }

        // Everyone has left the run for good (hardcore deaths / forfeits past grace). End it now
        // instead of letting the dungeon timer count down to zero with nobody inside.
        boolean anyRemaining = run.participants().values().stream()
            .anyMatch(p -> p.status() != ParticipantStatus.REMOVED);
        if (!anyRemaining) {
            handleLoss(world, controller, run, DungeonOutcome.LOSS_ABANDONED);
            return;
        }

        run.setDungeonTimerTicks(run.dungeonTimerTicks() - 1);
        if (run.dungeonTimerTicks() <= 0) {
            handleLoss(world, controller, run, DungeonOutcome.LOSS_TIMEOUT);
            return;
        }

        // Only non-removed participants count: a hardcore-dead player is still online but no longer
        // in the run, so they must not keep an empty run alive.
        boolean anyOnline = run.participants().entrySet().stream()
            .anyMatch(e -> e.getValue().status() != ParticipantStatus.REMOVED
                && world.getPlayerByUUID(e.getKey()) != null);
        if (!anyOnline) {
            handleLoss(world, controller, run, DungeonOutcome.LOSS_ABANDONED);
            return;
        }

        BlockEntity dbsBe = world.getBlockEntity(run.dbsPos());
        if (!(dbsBe instanceof DungeonBossSpawnerBlockEntity dbs)) {
            handleLoss(world, controller, run, DungeonOutcome.LOSS_FORCED);
            return;
        }

        if (isBranching(world, dbs)) {
            tickBranching(world, controller, run, dbs);
            tickDownedPlayers(world, controller, run);
            tickDisconnectedPlayers(world, controller, run);
            return;
        }

        List<BlockPos> rooms = dbs.getRooms();
        if (run.currentRoomIndex() >= rooms.size()) {
            handleWin(world, controller, run);
            return;
        }

        BlockPos currentRoomPos = rooms.get(run.currentRoomIndex());
        BlockEntity roomBe = world.getBlockEntity(currentRoomPos);
        if (!(roomBe instanceof RoomControllerBlockEntity room)) {
            return;
        }

        if (!room.isActivated()) {
            room.activate(world, run.resolvedTierConfig(), run.partyHealthMultiplier());
            for (UUID uuid : room.getAliveMobs()) {
                ArenasLdMod.DUNGEON_MANAGER.registerMob(uuid, run);
            }
        } else {
            room.refreshAliveMobs(world);
            for (UUID uuid : room.tickWaveProgression(world, run.resolvedTierConfig(), run.partyHealthMultiplier())) {
                ArenasLdMod.DUNGEON_MANAGER.registerMob(uuid, run);
            }
            if (room.isCleared()) {
                grantRoomReward(world, controller, run, room);
                room.openDoor(world);
                int next = run.currentRoomIndex() + 1;
                if (next >= rooms.size()) {
                    handleWin(world, controller, run);
                } else {
                    run.setCurrentRoomIndex(next);
                    net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
                        null, null, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
                }
            }
        }

        tickDownedPlayers(world, controller, run);
        tickDisconnectedPlayers(world, controller, run);
        updateDungeonTimeBossBar(world, run, room);
    }

    /** Branching mode iff any room has entrance doors linked; legacy dungeons keep the index walk. */
    private static boolean isBranching(ServerLevel world, DungeonBossSpawnerBlockEntity dbs) {
        for (BlockPos roomPos : dbs.getRooms()) {
            if (world.getBlockEntity(roomPos) instanceof RoomControllerBlockEntity room
                    && !room.getEntranceOffsets().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static final int ROOM_GRACE_TICKS = 60; // 3 s between lock-in and the first wave

    /**
     * Graph-based progression: EXPLORING (no current room; watch entrance doors) → PENDING
     * (party locked in, grace countdown, spawn telegraph) → ACTIVE (wave machine) → cleared:
     * open all doors and either win (final room) or return to EXPLORING.
     */
    private static void tickBranching(ServerLevel world, DungeonControllerBlockEntity controller,
                                      DungeonRun run, DungeonBossSpawnerBlockEntity dbs) {
        BlockPos current = run.currentRoomPos();

        // ---- EXPLORING ----
        if (current == null) {
            Map<BlockPos, BlockPos> detection = run.entranceDetectionCache();
            if (detection == null) {
                RoomGraph graph = RoomGraph.build(world, dbs);
                detection = graph.buildEntranceDetectionMap(roomPos ->
                    world.getBlockEntity(roomPos) instanceof RoomControllerBlockEntity room
                        && !room.isActivated() && !room.isCleared());
                run.setEntranceDetectionCache(detection);
            }
            for (UUID uuid : run.activeParticipantUuids()) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.level() != world) continue;
                BlockPos hit = detection.get(player.blockPosition());
                if (hit == null) {
                    hit = detection.get(BlockPos.containing(player.getEyePosition()));
                }
                if (hit != null) {
                    beginRoomEntry(world, run, hit, player);
                    break; // one room per run; next tick we're PENDING
                }
            }
            updateDungeonTimeBossBarNamed(world, run, exploreBossBarName(run, dbs));
            return;
        }

        BlockEntity roomBe = world.getBlockEntity(current);
        if (!(roomBe instanceof RoomControllerBlockEntity room)) {
            // Controller broken/removed mid-run: treat as cleared so the party isn't caged.
            ArenasLdMod.LOGGER.warn("Dungeon run at {}: current room {} lost its controller; auto-clearing",
                run.dbsPos(), current);
            finishRoom(world, controller, run, dbs, current, null);
            return;
        }

        // ---- PENDING (grace countdown) ----
        if (run.pendingGraceTicks() > 0) {
            run.setPendingGraceTicks(run.pendingGraceTicks() - 1);
            if (run.pendingGraceTicks() == 0) {
                run.setPendingGraceTicks(-1);
                room.activateOrAutoClear(world, run.resolvedTierConfig(), run.partyHealthMultiplier());
                if (room.isCleared()) {
                    finishRoom(world, controller, run, dbs, current, room);
                    return;
                }
                for (UUID uuid : room.getAliveMobs()) {
                    ArenasLdMod.DUNGEON_MANAGER.registerMob(uuid, run);
                }
                net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
                    Component.translatable("title.arenas_ld.dungeon.go").withStyle(ChatFormatting.RED), null,
                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.6f);
            } else {
                int graceSeconds = (run.pendingGraceTicks() + 19) / 20;
                updateDungeonTimeBossBarNamed(world, run, Component.translatable(
                    "boss_bar.arenas_ld.dungeon_time_ready", formatBossBarTime(run), graceSeconds));
                return;
            }
        }

        // ---- ACTIVE (wave machine, mirrors the legacy activated branch) ----
        room.refreshAliveMobs(world);
        for (UUID uuid : room.tickWaveProgression(world, run.resolvedTierConfig(), run.partyHealthMultiplier())) {
            ArenasLdMod.DUNGEON_MANAGER.registerMob(uuid, run);
        }
        if (room.pollWaveCountdownStarted()) {
            sendTelegraph(world, run, room.getUpcomingWaveSpawnPositions(world), ROOM_GRACE_TICKS);
        }
        if (room.isCleared()) {
            finishRoom(world, controller, run, dbs, current, room);
            return;
        }
        updateDungeonTimeBossBar(world, run, room);
    }

    /** Locks the party into the entered room and starts the grace countdown. */
    private static void beginRoomEntry(ServerLevel world, DungeonRun run, BlockPos roomPos, ServerPlayer enterer) {
        if (!(world.getBlockEntity(roomPos) instanceof RoomControllerBlockEntity room)) {
            return;
        }
        run.setCurrentRoomPos(roomPos);
        run.setPendingGraceTicks(ROOM_GRACE_TICKS);
        run.invalidateEntranceDetectionCache();
        // Lock-in. Shared entrance blocks also close the previous room's exit — intended.
        room.closeAllDoors(world);

        BlockPos respawn = room.getRespawnPos();
        for (UUID uuid : run.activeParticipantUuids()) {
            boolean isEnterer = uuid.equals(enterer.getUUID());
            if (isEnterer && respawn == null) {
                continue; // no respawn point: the enterer stays put, others gather on them
            }
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            if (respawn != null) {
                player.teleportTo(world, respawn.getX() + 0.5, respawn.getY(), respawn.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            } else if (!isEnterer) {
                player.teleportTo(world, enterer.getX(), enterer.getY(), enterer.getZ(),
                    player.getYRot(), player.getXRot());
            }
        }

        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            Component.translatable("title.arenas_ld.dungeon.get_ready").withStyle(ChatFormatting.YELLOW), null,
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 0.8f, 0.9f);
        sendTelegraph(world, run, room.getFirstWaveSpawnPositions(world), ROOM_GRACE_TICKS);
    }

    /** Room cleared (or unrecoverable): grant reward, unlock, and advance — win if it was the final room. */
    private static void finishRoom(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run,
                                   DungeonBossSpawnerBlockEntity dbs, BlockPos roomPos,
                                   @Nullable RoomControllerBlockEntity room) {
        if (room != null) {
            grantRoomReward(world, controller, run, room);
            room.openAllDoors(world);
        }
        run.addClearedRoom(roomPos);
        run.setCurrentRoomPos(null);
        run.setPendingGraceTicks(-1);
        run.invalidateEntranceDetectionCache();
        if (roomPos.equals(dbs.getAbsoluteFinalRoomPos())) {
            handleWin(world, controller, run);
            return;
        }
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            null, null, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
    }

    private static Component exploreBossBarName(DungeonRun run, DungeonBossSpawnerBlockEntity dbs) {
        return Component.translatable("boss_bar.arenas_ld.dungeon_time_explore",
            formatBossBarTime(run), run.clearedRooms().size(), dbs.getRooms().size());
    }

    private static String formatBossBarTime(DungeonRun run) {
        int secondsLeft = Math.max(0, (run.dungeonTimerTicks() + 19) / 20);
        return String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60);
    }

    /** Sends the spawn-telegraph highlight to every non-removed online participant. */
    private static void sendTelegraph(ServerLevel world, DungeonRun run, List<BlockPos> positions, int ticks) {
        if (positions.isEmpty()) {
            return;
        }
        net.ledok.arenas_ld.dungeon.packet.SpawnTelegraphPayload payload =
            new net.ledok.arenas_ld.dungeon.packet.SpawnTelegraphPayload(positions, ticks);
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() == ParticipantStatus.REMOVED) continue;
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static void tickClosing(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        run.setCloseTimerTicks(run.closeTimerTicks() - 1);
        updateCloseTimerBossBar(world, run);
        if (run.closeTimerTicks() <= 0) {
            finalize(world, controller, run);
        }
    }

    static void handleWin(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        long runDurationTicks = world.getGameTime() - run.startTick();
        int runDurationSeconds = (int) (runDurationTicks / 20);
        String lootTableId = run.resolvedTierConfig().perPlayerLootTable();
        long rewardPerPlayer = run.resolvedTierConfig().rewardCurrency();
        if (run.hardcoreEnabled()) {
            rewardPerPlayer *= 2;
        }
        int xpReward = run.resolvedTierConfig().skillExperiencePerWin();
        boolean lootViaInbox = controller.isLootViaInbox();
        boolean puffishLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("puffish_skills");

        for (UUID uuid : run.lootEligibleUuids()) {
            PlayerStatsStore.get(world.getServer()).recordWin(uuid, PlayerStatsStore.Mode.DUNGEON);

            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                continue;
            }

            controller.addLeaderboardEntry(
                run.tier(),
                new LeaderboardEntry(player.getGameProfile().getName(), runDurationSeconds, System.currentTimeMillis())
            );

            if (!lootTableId.isEmpty()) {
                ItemStack bundle = createLootBundle(lootTableId);
                if (lootViaInbox) {
                    net.ledok.arenas_ld.util.EconomyCompat.deliverItem(uuid, bundle, bundle.getCount(), "DUNGEON_LOOT");
                } else if (!player.getInventory().add(bundle)) {
                    player.drop(bundle, false);
                }
            }

            if (rewardPerPlayer > 0L) {
                net.ledok.arenas_ld.util.EconomyCompat.deliverCurrency(uuid, rewardPerPlayer, "DUNGEON_REWARD");
            }

            if (xpReward > 0 && puffishLoaded) {
                net.ledok.arenas_ld.compat.PuffishSkillsCompat.addExperience(player, xpReward);
            }
        }

        clearRunEffects(world, run);

        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer participant = world.getServer().getPlayerList().getPlayer(uuid);
            if (participant != null) {
                participant.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.win", runDurationSeconds));
            }
        }
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            Component.translatable("title.arenas_ld.victory").withStyle(ChatFormatting.GREEN), null,
            net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        run.setOutcome(DungeonOutcome.WIN);
        run.setPhase(DungeonPhase.CLOSING);
        int closeTicks = controller.getCloseTimerSeconds() * 20;
        run.setInitialCloseTimerTicks(closeTicks);
        run.setCloseTimerTicks(closeTicks);
        sendExitPrompt(world, run);
    }

    static void handleLoss(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, DungeonOutcome reason) {
        BlockEntity dbsBe = world.getBlockEntity(run.dbsPos());
        if (dbsBe instanceof DungeonBossSpawnerBlockEntity dbs && !dbs.getRooms().isEmpty() && run.currentRoomIndex() < dbs.getRooms().size()) {
            // Branching dungeons name their boss room explicitly; legacy = last room in the list.
            BlockPos lastRoomPos = dbs.getAbsoluteFinalRoomPos() != null
                ? dbs.getAbsoluteFinalRoomPos()
                : dbs.getRooms().get(dbs.getRooms().size() - 1);
            BlockEntity bossRoomBe = world.getBlockEntity(lastRoomPos);
            if (bossRoomBe instanceof RoomControllerBlockEntity bossRoom) {
                for (UUID bossUuid : new ArrayList<>(bossRoom.getAliveMobs())) {
                    Entity entity = world.getEntity(bossUuid);
                    if (entity != null && entity.isAlive()) {
                        entity.discard();
                    }
                }
            }
        }

        clearRunEffects(world, run);

        String messageKey = switch (reason) {
            case LOSS_TIMEOUT -> "message.arenas_ld.dungeon.loss_timeout";
            case LOSS_ABANDONED -> "message.arenas_ld.dungeon.loss_abandoned";
            case LOSS_FORCED -> "message.arenas_ld.dungeon.loss_forced";
            default -> "message.arenas_ld.dungeon.loss_timeout";
        };
        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer participant = world.getServer().getPlayerList().getPlayer(uuid);
            if (participant != null) {
                participant.sendSystemMessage(Component.translatable(messageKey));
            }
        }
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            Component.translatable("title.arenas_ld.defeat").withStyle(ChatFormatting.RED), null,
            net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.6f, 0.7f);

        run.setOutcome(reason);

        // If nobody is left in the run, there's no one to wait for — close out immediately
        // rather than ticking through the (pointless) close timer.
        boolean anyRemaining = run.participants().values().stream()
            .anyMatch(p -> p.status() != ParticipantStatus.REMOVED);
        if (!anyRemaining) {
            finalize(world, controller, run);
            return;
        }

        run.setPhase(DungeonPhase.CLOSING);
        int closeTicks = controller.getCloseTimerSeconds() * 20;
        run.setInitialCloseTimerTicks(closeTicks);
        run.setCloseTimerTicks(closeTicks);
        sendExitPrompt(world, run);
    }

    static void finalize(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        for (UUID uuid : run.participants().keySet()) {
            ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(uuid);
            BusyStateCompat.clearBusy(uuid, BUSY_REASON);
        }
        BlockEntity managerDbsBe = world.getBlockEntity(run.dbsPos());
        if (managerDbsBe instanceof DungeonBossSpawnerBlockEntity managerDbs) {
            for (BlockPos roomPos : managerDbs.getRooms()) {
                BlockEntity roomBe = world.getBlockEntity(roomPos);
                if (roomBe instanceof RoomControllerBlockEntity roomController) {
                    for (UUID mobUuid : roomController.getAliveMobs()) {
                        ArenasLdMod.DUNGEON_MANAGER.unregisterMob(mobUuid);
                    }
                }
            }
        }

        for (Map.Entry<UUID, PlayerReturnPoint> entry : run.returnPoints().entrySet()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                // Offline at run end — persist the eject so they're sent home on next login.
                PendingRestoreStore.get(world.getServer()).put(entry.getKey(), entry.getValue());
                continue;
            }
            applyReturnPoint(world.getServer(), player, entry.getValue());
        }

        teardownPartyTeam(world, run);
        hideBossBars(run);
        run.setPhase(DungeonPhase.DONE);
        controller.removeRun(run.dbsPos());
        controller.startInstanceCooldown(run.dbsPos());

        if (controller.getPendingInstanceRemovals().contains(run.dbsPos())) {
            controller.executePendingRemoval(run.dbsPos());
        }

        BlockEntity dbsBe = world.getBlockEntity(run.dbsPos());
        if (dbsBe instanceof DungeonBossSpawnerBlockEntity dbs) {
            for (BlockPos roomPos : dbs.getRooms()) {
                BlockEntity roomBe = world.getBlockEntity(roomPos);
                if (roomBe instanceof RoomControllerBlockEntity roomController) {
                    roomController.reset(world);
                }
            }
        }
        unforceChunksForRun(world, run.dbsPos());
    }

    /**
     * Sends every still-present participant a clickable "[Exit Now]" chat button so they can leave
     * the moment the run ends instead of waiting out the close timer. Click routes to
     * {@code /arenasld exit} → {@link #exitEarly}.
     */
    private static void sendExitPrompt(ServerLevel world, DungeonRun run) {
        MutableComponent button = LobbyChatActions.button(
            Component.translatable("message.arenas_ld.dungeon.exit_button"),
            0x55FF55,
            "/exit",
            Component.translatable("message.arenas_ld.dungeon.exit_hover"));
        Component line = Component.translatable("message.arenas_ld.dungeon.exit_prompt").append(button);
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() == ParticipantStatus.REMOVED) {
                continue;
            }
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(line);
            }
        }
    }

    /**
     * Pulls a single player out of a run that has already ended (CLOSING phase) before the shared
     * close timer elapses. Teleports them home, removes them from the run, and — if nobody is left
     * to wait on — finalizes the run immediately. Triggered by the "[Exit Now]" chat button.
     *
     * @return true if the player was eligible and removed; false if they had no finished run.
     */
    public static boolean exitEarly(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
        if (run == null || run.phase() != DungeonPhase.CLOSING) {
            return false;
        }
        RunParticipant participant = run.participants().get(uuid);
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) {
            return false;
        }
        ServerLevel world = server.getLevel(run.dbsDimension());
        long now = world != null ? world.getGameTime() : participant.lastSeenTick();

        PlayerReturnPoint rp = run.returnPoints().get(uuid);
        if (rp != null) {
            applyReturnPoint(server, player, rp);
            run.removeReturnPoint(uuid);
        }
        run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, now));
        run.clearDowned(uuid);
        run.clearDisconnected(uuid);
        ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(uuid);
        BusyStateCompat.clearBusy(uuid, BUSY_REASON);
        if (world != null) {
            removeFromPartyTeam(world, run, participant.playerName());
        }

        // If nobody is left to wait on, close the run out now instead of ticking the timer down.
        boolean anyRemaining = run.participants().values().stream()
            .anyMatch(p -> p.status() != ParticipantStatus.REMOVED);
        if (!anyRemaining && world != null) {
            DungeonControllerBlockEntity controller = ArenasLdMod.DUNGEON_MANAGER.findControllerForRun(server, run);
            if (controller != null) {
                finalize(world, controller, run);
            }
        }
        return true;
    }

    private static void tickDownedPlayers(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        Map<UUID, DownedPlayer> downedCopy = new HashMap<>(run.downedPlayers());
        for (Map.Entry<UUID, DownedPlayer> entry : downedCopy.entrySet()) {
            // Pause the respawn countdown while the player is offline; it resumes on reconnect.
            if (run.isDisconnected(entry.getKey())) {
                continue;
            }
            DownedPlayer ticked = entry.getValue().tick();
            if (ticked.isReadyToRespawn()) {
                respawnDownedPlayer(world, controller, run, entry.getKey());
                run.clearDowned(entry.getKey());
            } else {
                run.setDowned(ticked);
            }
        }
    }

    private static void tickDisconnectedPlayers(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        long now = world.getGameTime();
        int grace = controller.getDisconnectGraceTicks();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(run.disconnectedAt()).entrySet()) {
            if (now - entry.getValue() > grace) {
                UUID uuid = entry.getKey();
                RunParticipant participant = run.participants().get(uuid);
                String name = participant != null ? participant.playerName() : shortUuid(uuid);
                if (participant != null) {
                    run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, now));
                }
                // Persist the eject so the forfeited player is sent home on next login.
                PlayerReturnPoint rp = run.returnPoints().get(uuid);
                if (rp != null) {
                    PendingRestoreStore.get(world.getServer()).put(uuid, rp);
                    run.removeReturnPoint(uuid);
                }
                run.clearDowned(uuid);
                run.clearDisconnected(uuid);
                removeFromPartyTeam(world, run, name);
                ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(uuid);
                BusyStateCompat.clearBusy(uuid, BUSY_REASON);
                broadcastToParty(world, run,
                    Component.translatable("message.arenas_ld.dungeon.party_removed", name).withStyle(ChatFormatting.RED), uuid);
            }
        }
    }

    private static void updateDungeonTimeBossBar(ServerLevel world, DungeonRun run, RoomControllerBlockEntity room) {
        String timeLeft = formatBossBarTime(run);
        Component name;
        if (room.getObjective().type() == net.ledok.arenas_ld.dungeon.room.RoomObjectiveConfig.Type.SURVIVE
                && room.getSurviveTicksRemaining() > 0) {
            int surviveSeconds = (room.getSurviveTicksRemaining() + 19) / 20;
            name = Component.translatable("boss_bar.arenas_ld.dungeon_time_survive", timeLeft,
                String.format("%d:%02d", surviveSeconds / 60, surviveSeconds % 60));
        } else if (room.getTotalWaves() > 1) {
            name = Component.translatable("boss_bar.arenas_ld.dungeon_time_waves", timeLeft,
                room.getWaveDisplay(), room.getTotalWaves(), room.getAliveMobs().size());
        } else {
            name = Component.translatable("boss_bar.arenas_ld.dungeon_time", timeLeft, room.getAliveMobs().size());
        }
        updateDungeonTimeBossBarNamed(world, run, name);
    }

    private static void updateDungeonTimeBossBarNamed(ServerLevel world, DungeonRun run, Component name) {
        ServerBossEvent bar = run.getDungeonTimeBossBar();
        if (bar == null) {
            bar = new ServerBossEvent(
                Component.translatable("boss_bar.arenas_ld.dungeon_time", "0:00", 0),
                BossEvent.BossBarColor.BLUE,
                BossEvent.BossBarOverlay.PROGRESS
            );
            run.setDungeonTimeBossBar(bar);
        }
        int totalTicks = run.resolvedTierConfig().dungeonTimeSeconds() * 20;
        float progress = totalTicks > 0
            ? Math.max(0.0F, Math.min(1.0F, (float) run.dungeonTimerTicks() / (float) totalTicks))
            : 0.0F;
        bar.setProgress(progress);
        // ServerBossEvent.setName only broadcasts when the component actually changes,
        // so setting this every tick costs a packet at most once per second.
        bar.setName(name);
        syncBarViewers(world, run, bar);
    }

    private static void updateCloseTimerBossBar(ServerLevel world, DungeonRun run) {
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
            ? Math.max(0.0F, Math.min(1.0F, (float) run.closeTimerTicks() / (float) totalTicks))
            : 0.0F;
        bar.setProgress(progress);
        int secondsLeft = (run.closeTimerTicks() + 19) / 20; // ceil to whole seconds
        bar.setName(Component.translatable("boss_bar.arenas_ld.close_timer", secondsLeft));
        syncBarViewers(world, run, bar);

        ServerBossEvent dungeonBar = run.getDungeonTimeBossBar();
        if (dungeonBar != null) {
            dungeonBar.removeAllPlayers();
            run.setDungeonTimeBossBar(null);
        }
    }

    private static void hideBossBars(DungeonRun run) {
        ServerBossEvent dungeonBar = run.getDungeonTimeBossBar();
        if (dungeonBar != null) {
            dungeonBar.removeAllPlayers();
            run.setDungeonTimeBossBar(null);
        }
        ServerBossEvent closeBar = run.getCloseTimerBossBar();
        if (closeBar != null) {
            closeBar.removeAllPlayers();
            run.setCloseTimerBossBar(null);
        }
    }

    private static void syncBarViewers(ServerLevel world, DungeonRun run, ServerBossEvent bar) {
        Set<ServerPlayer> targetViewers = new HashSet<>();
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() == ParticipantStatus.REMOVED) {
                continue;
            }
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                targetViewers.add(player);
            }
        }

        Set<ServerPlayer> currentViewers = new HashSet<>(bar.getPlayers());
        for (ServerPlayer existing : currentViewers) {
            if (!targetViewers.contains(existing)) {
                bar.removePlayer(existing);
            }
        }
        for (ServerPlayer target : targetViewers) {
            if (!currentViewers.contains(target)) {
                bar.addPlayer(target);
            }
        }
    }

    private static ItemStack createLootBundle(String lootTableId) {
        ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
        bundle.set(DataComponentRegistry.LOOT_BUNDLE_DATA, new LootBundleDataComponent(lootTableId));
        return bundle;
    }

    /**
     * Grants the room's configured clear reward to every loot-eligible player. No-op when the
     * room has no reward configured (the default). Loot and currency reach offline players via
     * the economy inbox when available; effects and skill XP require the player to be online.
     */
    private static void grantRoomReward(ServerLevel world, DungeonControllerBlockEntity controller,
                                        DungeonRun run, RoomControllerBlockEntity room) {
        RoomRewardConfig reward = room.getRoomReward();
        if (reward.isEmpty()) {
            return;
        }
        boolean lootViaInbox = controller.isLootViaInbox();
        boolean puffishLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("puffish_skills");
        List<MobEffectInstance> effectInstances = resolveRewardEffects(reward.effects());

        for (UUID uuid : run.lootEligibleUuids()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);

            if (!reward.lootTableId().isEmpty()) {
                ItemStack bundle = createLootBundle(reward.lootTableId());
                if (lootViaInbox || player == null) {
                    net.ledok.arenas_ld.util.EconomyCompat.deliverItem(uuid, bundle, bundle.getCount(), "DUNGEON_LOOT");
                } else if (!player.getInventory().add(bundle)) {
                    player.drop(bundle, false);
                }
            }

            if (reward.currency() > 0L) {
                net.ledok.arenas_ld.util.EconomyCompat.deliverCurrency(uuid, reward.currency(), "DUNGEON_REWARD");
            }

            if (player == null) {
                continue;
            }
            if (reward.skillXp() > 0 && puffishLoaded) {
                net.ledok.arenas_ld.compat.PuffishSkillsCompat.addExperience(player, reward.skillXp());
            }
            for (MobEffectInstance instance : effectInstances) {
                player.addEffect(new MobEffectInstance(instance));
            }
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.room_reward"));
        }

        executeRewardCommands(world, run, room, reward.commands());
    }

    /**
     * Runs reward commands as the server (command-block permission level, output suppressed),
     * positioned at the room controller. A command containing {@code @dungeonplayer} or {@code @s}
     * has the placeholder replaced with each eligible online player's name and runs once per
     * player; a command without a placeholder runs once.
     */
    private static void executeRewardCommands(ServerLevel world, DungeonRun run,
                                              RoomControllerBlockEntity room, List<String> commands) {
        if (commands.isEmpty()) {
            return;
        }
        MinecraftServer server = world.getServer();
        CommandSourceStack source = server.createCommandSourceStack()
            .withLevel(world)
            .withPosition(Vec3.atCenterOf(room.getBlockPos()))
            .withPermission(2)
            .withSuppressedOutput();
        for (String command : commands) {
            if (command.isBlank()) {
                continue;
            }
            if (command.contains("@dungeonplayer") || command.contains("@s")) {
                for (UUID uuid : run.lootEligibleUuids()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(uuid);
                    if (player == null) {
                        continue;
                    }
                    String name = player.getGameProfile().getName();
                    // \b keeps "@s[...]" working while leaving longer selectors like "@a" untouched.
                    String resolved = command.replace("@dungeonplayer", name).replaceAll("@s\\b", name);
                    server.getCommands().performPrefixedCommand(source, resolved);
                }
            } else {
                server.getCommands().performPrefixedCommand(source, command);
            }
        }
    }

    /** Resolves configured effect IDs to instances; unknown or malformed IDs are logged and skipped. */
    private static List<MobEffectInstance> resolveRewardEffects(List<RoomEffectData> effects) {
        List<MobEffectInstance> resolved = new ArrayList<>();
        for (RoomEffectData data : effects) {
            ResourceLocation id = ResourceLocation.tryParse(data.effectId().trim());
            if (id == null) {
                ArenasLdMod.LOGGER.warn("Invalid room reward effect id: {}", data.effectId());
                continue;
            }
            ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT, id);
            BuiltInRegistries.MOB_EFFECT.getHolder(key).ifPresentOrElse(
                // ambient=false, visible=false (no particles), showIcon=true so the buff still shows in the HUD
                holder -> resolved.add(new MobEffectInstance(holder,
                    Math.max(1, data.durationSeconds()) * 20, Math.max(0, data.amplifier()),
                    false, false, true)),
                () -> ArenasLdMod.LOGGER.warn("Unknown room reward effect: {}", id));
        }
        return resolved;
    }

    /** Strips all status effects from every online participant — a run ending wipes buffs and debuffs alike. */
    private static void clearRunEffects(ServerLevel world, DungeonRun run) {
        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.removeAllEffects();
            }
        }
    }

    public static void handlePlayerDown(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, ServerPlayer player) {
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null) {
            return;
        }
        if (run.hardcoreEnabled()) {
            PlayerReturnPoint returnPoint = run.returnPoints().get(player.getUUID());
            run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, world.getGameTime()));
            player.setHealth(player.getMaxHealth());
            // The run is over for this player — same effect wipe as applyReturnPoint at run end.
            player.removeAllEffects();
            player.setGameMode(returnPoint != null ? returnPoint.previousGameMode() : GameType.SURVIVAL);
            if (returnPoint != null) {
                ServerLevel target = world.getServer().getLevel(returnPoint.dimension());
                if (target == null) {
                    target = world;
                }
                player.teleportTo(
                    target,
                    returnPoint.pos().x(),
                    returnPoint.pos().y(),
                    returnPoint.pos().z(),
                    returnPoint.yaw(),
                    returnPoint.pitch()
                );
            }
            run.removeReturnPoint(player.getUUID());
            removeFromPartyTeam(world, run, player.getScoreboardName());
            ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(player.getUUID());
            BusyStateCompat.clearBusy(player.getUUID(), BUSY_REASON);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.hardcore_death").withStyle(ChatFormatting.RED));
            return;
        }

        run.updateParticipant(participant.withStatus(ParticipantStatus.DOWNED, world.getGameTime()));
        run.setDowned(new DownedPlayer(player.getUUID(), controller.getRespawnTimeTicks()));
        int penalty = controller.getDeathTimePenaltyTicks();
        if (penalty > 0) {
            run.setDungeonTimerTicks(Math.max(0, run.dungeonTimerTicks() - penalty));
        }
        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(1.0F);
        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.you_are_downed"));
    }

    private static void respawnDownedPlayer(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, UUID uuid) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            return;
        }

        BlockEntity dbsBe = world.getBlockEntity(run.dbsPos());
        if (dbsBe instanceof DungeonBossSpawnerBlockEntity dbs) {
            player.setGameMode(GameType.ADVENTURE);
            player.setHealth(player.getMaxHealth());
            teleportToRoomRespawnOrEntrance(world, dbs, run, player);
        }

        RunParticipant participant = run.participants().get(uuid);
        if (participant != null) {
            run.updateParticipant(participant.withStatus(ParticipantStatus.ACTIVE, world.getGameTime()));
        }
    }

    /** Absolute respawn position of the run's active room, or {@code null} if the active room has none. */
    @Nullable
    private static BlockPos activeRoomRespawnPos(ServerLevel world, DungeonBossSpawnerBlockEntity dbs, DungeonRun run) {
        // Branching: the locked/pending room is authoritative; while EXPLORING, fall back to the
        // most recently cleared room that has a respawn point, so respawns track the party's
        // progress instead of dumping players back at the dungeon entrance.
        BlockPos branchingRoom = run.currentRoomPos();
        if (branchingRoom != null) {
            return world.getBlockEntity(branchingRoom) instanceof RoomControllerBlockEntity room
                ? room.getRespawnPos()
                : null;
        }
        if (isBranching(world, dbs)) {
            List<BlockPos> cleared = new ArrayList<>(run.clearedRooms());
            for (int i = cleared.size() - 1; i >= 0; i--) {
                if (world.getBlockEntity(cleared.get(i)) instanceof RoomControllerBlockEntity clearedRoom
                        && clearedRoom.getRespawnPos() != null) {
                    return clearedRoom.getRespawnPos();
                }
            }
            return null;
        }
        List<BlockPos> rooms = dbs.getRooms();
        int index = run.currentRoomIndex();
        if (index < 0 || index >= rooms.size()) {
            return null;
        }
        return world.getBlockEntity(rooms.get(index)) instanceof RoomControllerBlockEntity room
            ? room.getRespawnPos()
            : null;
    }

    /**
     * Teleports the player to the run's active room respawn point, falling back to the dungeon
     * entrance when the active room has none. The active room is read now (not when the player
     * went down), so clearing rooms while a teammate is away pushes their respawn forward.
     */
    private static void teleportToRoomRespawnOrEntrance(ServerLevel world, DungeonBossSpawnerBlockEntity dbs, DungeonRun run, ServerPlayer player) {
        BlockPos roomRespawn = activeRoomRespawnPos(world, dbs, run);
        if (roomRespawn != null) {
            player.teleportTo(world, roomRespawn.getX() + 0.5, roomRespawn.getY(), roomRespawn.getZ() + 0.5, 0.0F, 0.0F);
            return;
        }
        ServerLevel target = world.getServer().getLevel(dbs.getEntranceDimension());
        if (target == null) {
            target = world;
        }
        BlockPos entrance = dbs.getAbsoluteEntrancePos();
        player.teleportTo(target, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, 0.0F, 0.0F);
    }

    /** Teleports a player to a captured return point and restores their pre-run game mode + health. */
    private static void applyReturnPoint(MinecraftServer server, ServerPlayer player, PlayerReturnPoint rp) {
        ServerLevel target = server.getLevel(rp.dimension());
        if (target != null) {
            player.teleportTo(target, rp.pos().x(), rp.pos().y(), rp.pos().z(), rp.yaw(), rp.pitch());
        }
        player.setGameMode(rp.previousGameMode());
        player.setHealth(player.getMaxHealth());
        // Leaving a run wipes all status effects — covers players who were offline when the run
        // ended (pending-restore on next login) as well as early exits during CLOSING.
        player.removeAllEffects();
    }

    /** Sends a message to every online, non-removed participant except {@code except} (nullable). */
    private static void broadcastToParty(ServerLevel world, DungeonRun run, Component message, @Nullable UUID except) {
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() == ParticipantStatus.REMOVED) {
                continue;
            }
            if (except != null && entry.getKey().equals(except)) {
                continue;
            }
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    // ── Party team (temporary no-PvP team for the duration of the run) ──────────

    private static String teamNameFor(DungeonRun run) {
        return "ald_d_" + Integer.toHexString(run.dbsPos().hashCode());
    }

    /** Creates the run's no-PvP team and adds the player, remembering their prior team. */
    private static void addToPartyTeam(ServerLevel world, DungeonRun run, ServerPlayer player) {
        Scoreboard scoreboard = world.getScoreboard();
        String teamName = teamNameFor(run);
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

    /** Removes one member from the run team and restores their prior team (works for offline players). */
    private static void removeFromPartyTeam(ServerLevel world, DungeonRun run, String playerName) {
        Scoreboard scoreboard = world.getScoreboard();
        String priorName = PartyTeamStore.get(world.getServer()).take(playerName);
        if (priorName == null) {
            return; // not in this run's team
        }
        PlayerTeam current = scoreboard.getPlayersTeam(playerName);
        if (current != null && current.getName().equals(teamNameFor(run))) {
            scoreboard.removePlayerFromTeam(playerName, current);
        }
        if (!priorName.isEmpty()) {
            PlayerTeam prior = scoreboard.getPlayerTeam(priorName);
            if (prior != null) {
                scoreboard.addPlayerToTeam(playerName, prior);
            }
        }
    }

    /** Restores every member's prior team and deletes the temporary run team. */
    private static void teardownPartyTeam(ServerLevel world, DungeonRun run) {
        for (RunParticipant participant : run.participants().values()) {
            removeFromPartyTeam(world, run, participant.playerName());
        }
        PlayerTeam team = world.getScoreboard().getPlayerTeam(teamNameFor(run));
        if (team != null) {
            world.getScoreboard().removePlayerTeam(team);
        }
    }

    /**
     * Marks a participant DISCONNECTED and starts their grace countdown. Any DOWNED state is left
     * intact (its respawn timer is paused while offline) and the party is notified. Called from
     * the connection listener on logout.
     */
    public static void handlePlayerDisconnect(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
        if (run == null) {
            return;
        }
        ServerLevel world = server.getLevel(run.dbsDimension());
        if (world == null) {
            return;
        }
        RunParticipant participant = run.participants().get(uuid);
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) {
            return;
        }
        long now = world.getGameTime();
        run.updateParticipant(participant.withStatus(ParticipantStatus.DISCONNECTED, now));
        run.markDisconnected(uuid, now);

        DungeonControllerBlockEntity controller = ArenasLdMod.DUNGEON_MANAGER.findControllerForRun(server, run);
        int graceSeconds = controller != null ? Math.max(0, controller.getDisconnectGraceTicks() / 20) : 0;
        broadcastToParty(world, run, Component.translatable(
            "message.arenas_ld.dungeon.party_disconnected", participant.playerName(), graceSeconds)
            .withStyle(ChatFormatting.YELLOW), uuid);
    }

    /**
     * Restores a player on login. Resolution order: a persisted eject (forfeited or run ended
     * while offline) wins; otherwise, if they're still within their grace window in an active run,
     * they're put back into the run (kept downed if they went down before quitting). Called from
     * the connection listener on join.
     */
    public static void handlePlayerReconnect(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();

        PlayerReturnPoint pending = PendingRestoreStore.get(server).take(uuid);
        if (pending != null) {
            applyReturnPoint(server, player, pending);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.run_ended_while_away")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }

        DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
        if (run == null) {
            return;
        }
        RunParticipant participant = run.participants().get(uuid);
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) {
            PlayerReturnPoint rp = run.returnPoints().get(uuid);
            if (rp != null) {
                applyReturnPoint(server, player, rp);
            }
            return;
        }

        ServerLevel world = server.getLevel(run.dbsDimension());
        long now = world != null ? world.getGameTime() : participant.lastSeenTick();
        run.clearDisconnected(uuid);
        ArenasLdMod.DUNGEON_MANAGER.registerParticipant(uuid, run);
        BusyStateCompat.setBusy(uuid, BUSY_REASON);

        DungeonBossSpawnerBlockEntity dbs = world != null
            && world.getBlockEntity(run.dbsPos()) instanceof DungeonBossSpawnerBlockEntity d ? d : null;

        if (run.isDowned(uuid)) {
            run.updateParticipant(participant.withStatus(ParticipantStatus.DOWNED, now));
            player.setGameMode(GameType.SPECTATOR);
            player.setHealth(1.0F);
            if (dbs != null) {
                teleportToRoomRespawnOrEntrance(world, dbs, run, player);
            }
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.reconnected_downed")
                .withStyle(ChatFormatting.YELLOW));
        } else {
            run.updateParticipant(participant.withStatus(ParticipantStatus.ACTIVE, now));
            player.setGameMode(GameType.ADVENTURE);
            player.setHealth(player.getMaxHealth());
            if (dbs != null) {
                teleportToRoomRespawnOrEntrance(world, dbs, run, player);
            }
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.reconnected"));
        }

        if (world != null) {
            broadcastToParty(world, run, Component.translatable(
                "message.arenas_ld.dungeon.party_reconnected", participant.playerName())
                .withStyle(ChatFormatting.GREEN), uuid);
        }
    }

    public static void forceLoadChunksForRun(ServerLevel world, BlockPos dbsPos) {
        Set<ChunkPos> chunks = collectRunChunks(world, dbsPos);
        for (ChunkPos chunkPos : chunks) {
            world.setChunkForced(chunkPos.x, chunkPos.z, true);
        }
    }

    private static void unforceChunksForRun(ServerLevel world, BlockPos dbsPos) {
        Set<ChunkPos> chunks = collectRunChunks(world, dbsPos);
        for (ChunkPos chunkPos : chunks) {
            world.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
    }

    private static Set<ChunkPos> collectRunChunks(ServerLevel world, BlockPos dbsPos) {
        Set<ChunkPos> chunks = new HashSet<>();
        chunks.add(new ChunkPos(dbsPos));

        BlockEntity dbsBe = world.getBlockEntity(dbsPos);
        if (!(dbsBe instanceof DungeonBossSpawnerBlockEntity dbs)) {
            return chunks;
        }

        for (BlockPos roomPos : dbs.getRooms()) {
            chunks.add(new ChunkPos(roomPos));
            BlockEntity roomBe = world.getBlockEntity(roomPos);
            if (roomBe instanceof RoomControllerBlockEntity roomController) {
                for (BlockPos spawnerPos : roomController.getSpawnerPositions()) {
                    chunks.add(new ChunkPos(spawnerPos));
                }
                for (BlockPos doorPos : roomController.getDoorPositions()) {
                    chunks.add(new ChunkPos(doorPos));
                }
                for (BlockPos entrancePos : roomController.getEntrancePositions()) {
                    chunks.add(new ChunkPos(entrancePos));
                }
            }
        }
        return chunks;
    }
}
