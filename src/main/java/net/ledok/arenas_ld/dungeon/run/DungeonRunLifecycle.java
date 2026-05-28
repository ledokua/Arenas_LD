package net.ledok.arenas_ld.dungeon.run;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.level.GameType;
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

            BlockPos entrance = dbs.getAbsoluteEntrancePos();
            player.setGameMode(GameType.ADVENTURE);
            player.teleportTo(targetLevel, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, 0.0f, 0.0f);
        }

        for (BlockPos roomPos : dbs.getRooms()) {
            BlockEntity roomBe = world.getBlockEntity(roomPos);
            if (roomBe instanceof RoomControllerBlockEntity roomController) {
                roomController.reset(world);
            }
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

        run.setDungeonTimerTicks(run.dungeonTimerTicks() - 1);
        if (run.dungeonTimerTicks() <= 0) {
            handleLoss(world, controller, run, DungeonOutcome.LOSS_TIMEOUT);
            return;
        }

        boolean anyOnline = run.participants().keySet().stream().anyMatch(uuid -> world.getPlayerByUUID(uuid) != null);
        if (!anyOnline) {
            handleLoss(world, controller, run, DungeonOutcome.LOSS_ABANDONED);
            return;
        }

        BlockEntity dbsBe = world.getBlockEntity(run.dbsPos());
        if (!(dbsBe instanceof DungeonBossSpawnerBlockEntity dbs)) {
            handleLoss(world, controller, run, DungeonOutcome.LOSS_FORCED);
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
            room.activate(world, run.resolvedTierConfig());
            for (UUID uuid : room.getAliveMobs()) {
                ArenasLdMod.DUNGEON_MANAGER.registerMob(uuid, run);
            }
        } else {
            room.refreshAliveMobs(world);
            if (room.isCleared()) {
                room.openDoor(world);
                int next = run.currentRoomIndex() + 1;
                if (next >= rooms.size()) {
                    handleWin(world, controller, run);
                } else {
                    run.setCurrentRoomIndex(next);
                }
            }
        }

        tickDownedPlayers(world, controller, run);
        tickDisconnectedPlayers(world, controller, run);
        updateDungeonTimeBossBar(world, run);
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
        boolean lootViaInbox = controller.isLootViaInbox();

        for (UUID uuid : run.lootEligibleUuids()) {
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
        }

        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer participant = world.getServer().getPlayerList().getPlayer(uuid);
            if (participant != null) {
                participant.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.win", runDurationSeconds));
            }
        }

        run.setOutcome(DungeonOutcome.WIN);
        run.setPhase(DungeonPhase.CLOSING);
        int closeTicks = controller.getCloseTimerSeconds() * 20;
        run.setInitialCloseTimerTicks(closeTicks);
        run.setCloseTimerTicks(closeTicks);
    }

    static void handleLoss(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, DungeonOutcome reason) {
        BlockEntity dbsBe = world.getBlockEntity(run.dbsPos());
        if (dbsBe instanceof DungeonBossSpawnerBlockEntity dbs && !dbs.getRooms().isEmpty() && run.currentRoomIndex() < dbs.getRooms().size()) {
            BlockPos lastRoomPos = dbs.getRooms().get(dbs.getRooms().size() - 1);
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

        run.setOutcome(reason);
        run.setPhase(DungeonPhase.CLOSING);
        int closeTicks = controller.getCloseTimerSeconds() * 20;
        run.setInitialCloseTimerTicks(closeTicks);
        run.setCloseTimerTicks(closeTicks);
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
                continue;
            }
            PlayerReturnPoint rp = entry.getValue();
            ServerLevel target = world.getServer().getLevel(rp.dimension());
            if (target != null) {
                player.teleportTo(target, rp.pos().x(), rp.pos().y(), rp.pos().z(), rp.yaw(), rp.pitch());
            }
            player.setGameMode(rp.previousGameMode());
        }

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

    private static void tickDownedPlayers(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        Map<UUID, DownedPlayer> downedCopy = new HashMap<>(run.downedPlayers());
        for (Map.Entry<UUID, DownedPlayer> entry : downedCopy.entrySet()) {
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
                if (participant != null) {
                    run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, now));
                }
                run.clearDisconnected(uuid);
                ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(uuid);
                BusyStateCompat.clearBusy(uuid, BUSY_REASON);
            }
        }
    }

    private static void updateDungeonTimeBossBar(ServerLevel world, DungeonRun run) {
        ServerBossEvent bar = run.getDungeonTimeBossBar();
        if (bar == null) {
            bar = new ServerBossEvent(
                Component.translatable("boss_bar.arenas_ld.dungeon_time"),
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

    public static void handlePlayerDown(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, ServerPlayer player) {
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null) {
            return;
        }
        if (run.hardcoreEnabled()) {
            PlayerReturnPoint returnPoint = run.returnPoints().get(player.getUUID());
            run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, world.getGameTime()));
            player.setHealth(player.getMaxHealth());
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
            ServerLevel target = world.getServer().getLevel(dbs.getEntranceDimension());
            if (target == null) {
                target = world;
            }
            BlockPos entrance = dbs.getAbsoluteEntrancePos();
            player.setGameMode(GameType.ADVENTURE);
            player.setHealth(player.getMaxHealth() * 0.5F);
            player.teleportTo(target, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, 0.0F, 0.0F);
        }

        RunParticipant participant = run.participants().get(uuid);
        if (participant != null) {
            run.updateParticipant(participant.withStatus(ParticipantStatus.ACTIVE, world.getGameTime()));
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
                BlockPos doorPos = roomController.getDoorPos();
                if (doorPos != null) {
                    chunks.add(new ChunkPos(doorPos));
                }
            }
        }
        return chunks;
    }
}
