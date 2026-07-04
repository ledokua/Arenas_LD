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
import net.ledok.arenas_ld.util.LobbyChatActions;
import net.ledok.arenas_ld.util.PartyTeamStore;
import net.ledok.arenas_ld.util.PendingRestoreStore;
import net.ledok.arenas_ld.util.PlayerStatsStore;
import net.minecraft.network.chat.MutableComponent;
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
        updateDungeonTimeBossBar(world, run, room.getAliveMobs().size(), room.getWaveDisplay(), room.getTotalWaves());
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

    private static void updateDungeonTimeBossBar(ServerLevel world, DungeonRun run, int mobsLeftInRoom, int waveDisplay, int totalWaves) {
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
        int secondsLeft = Math.max(0, (run.dungeonTimerTicks() + 19) / 20); // ceil to whole seconds
        String timeLeft = String.format("%d:%02d", secondsLeft / 60, secondsLeft % 60);
        // ServerBossEvent.setName only broadcasts when the component actually changes,
        // so setting this every tick costs a packet at most once per second.
        bar.setName(totalWaves > 1
            ? Component.translatable("boss_bar.arenas_ld.dungeon_time_waves", timeLeft, waveDisplay, totalWaves, mobsLeftInRoom)
            : Component.translatable("boss_bar.arenas_ld.dungeon_time", timeLeft, mobsLeftInRoom));
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
            }
        }
        return chunks;
    }
}
