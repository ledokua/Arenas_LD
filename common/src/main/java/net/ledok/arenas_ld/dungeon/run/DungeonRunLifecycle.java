package net.ledok.arenas_ld.dungeon.run;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.room.RoomEffectData;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.ledok.arenas_ld.util.DebugLog;
import net.ledok.arenas_ld.util.LobbyChatActions;
import net.ledok.arenas_ld.util.PartyTeamStore;
import net.ledok.arenas_ld.util.PendingRestoreStore;
import net.ledok.arenas_ld.util.PlayerStatsStore;
import net.ledok.arenas_ld.util.RewardDelivery;
import net.ledok.arenas_ld.util.RunHudSync;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
            } else if (!(world.getBlockEntity(startRoom) instanceof RoomControllerBlockEntity)) {
                errorKey = "message.arenas_ld.dungeon.start_room_no_entrance";
            } else {
                // The way in: the start room needs at least one boundary door (a door no other
                // room shares) — that's the one opened armed at run start.
                BlockPos startRoomPos = startRoom;
                boolean hasBoundaryDoor = RoomGraph.build(world, dbs).doorsOf(startRoomPos).stream()
                    .anyMatch(door -> door.partnerOf(startRoomPos) == null);
                if (!hasBoundaryDoor) {
                    errorKey = "message.arenas_ld.dungeon.start_room_no_entrance";
                }
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
        }

        // Freeze the per-player HP multiplier at run start — players leaving mid-run don't weaken it.
        run.setPartyHealthMultiplier(controller.resolvePartyHealthMultiplier(run.participants().size()));

        for (BlockPos roomPos : dbs.getRooms()) {
            BlockEntity roomBe = world.getBlockEntity(roomPos);
            if (roomBe instanceof RoomControllerBlockEntity roomController) {
                roomController.reset(world);
            }
        }

        // Branching: the start room begins like any other — its boundary doors (spawn-side,
        // shared with no other room) open armed/orange; doors into neighbor rooms stay shut.
        if (isBranching(world, dbs)
                && world.getBlockEntity(dbs.getAbsoluteStartRoomPos()) instanceof RoomControllerBlockEntity startRoom) {
            BlockPos startRoomPos = dbs.getAbsoluteStartRoomPos();
            for (RoomGraph.Door door : RoomGraph.build(world, dbs).doorsOf(startRoomPos)) {
                if (door.partnerOf(startRoomPos) == null) {
                    startRoom.setDoorGroupState(world, door.anchor(), false, true);
                }
            }
        }

        // Register the run before moving anyone: if a teleport throws (e.g. vanilla
        // chunk-tracking corruption), the run must still exist and tick so it can be
        // forfeited normally and reconnect recovery works, instead of leaving players
        // registered to a run no controller knows about.
        controller.startRun(dbsPos, run);

        BlockPos entrance = dbs.getAbsoluteEntrancePos();
        DebugLog.log("startRun: dbs={} in {} -> entrance={} in {}, party={}",
            dbsPos, world.dimension().location(), entrance, targetLevel.dimension().location(), partyUuids.size());
        for (UUID uuid : partyUuids) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            try {
                DebugLog.log("startRun: teleporting {}", DebugLog.describe(player));
                player.setGameMode(GameType.ADVENTURE);
                player.teleportTo(targetLevel, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, 0.0f, 0.0f);
                DebugLog.log("startRun: teleported {}", DebugLog.describe(player));
            } catch (Exception e) {
                ArenasLdMod.LOGGER.error("Failed to teleport {} into dungeon run at {}; relogging will pull them back into the run",
                    player.getGameProfile().getName(), dbsPos, e);
            }
        }

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
            addProtectTargetToPartyTeam(world, run, room);
            for (UUID uuid : room.getAliveMobs()) {
                ArenasLdMod.DUNGEON_MANAGER.registerMob(uuid, run);
            }
        } else {
            room.refreshAliveMobs(world);
            if (room.pollProtectTargetDied()) {
                handleLoss(world, controller, run, DungeonOutcome.LOSS_OBJECTIVE);
                return;
            }
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

    /** Branching mode iff the DBS has a Start or Final room marker set (doors are direction-free
     *  and can't tell the modes apart); legacy marker-less dungeons keep the index walk. */
    private static boolean isBranching(ServerLevel world, DungeonBossSpawnerBlockEntity dbs) {
        return dbs.getAbsoluteStartRoomPos() != null || dbs.getAbsoluteFinalRoomPos() != null;
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
            // Touching an entrance door registers the player on that room (touching another
            // room's door later re-registers them) and marks them glowing so the rest of the
            // party can see who waits where; the room locks in once its configured share of
            // the online party is registered on it. A player who touches a door and turns back
            // stays registered — there is no room geometry to tell "inside" from "outside".
            Map<UUID, BlockPos> registrations = run.roomEntryRegistrations();
            Set<UUID> active = run.activeParticipantUuids();
            boolean changed = false;
            for (var it = registrations.entrySet().iterator(); it.hasNext(); ) {
                var entry = it.next();
                if (!active.contains(entry.getKey())) {
                    setEntryGlow(world, entry.getKey(), false);
                    it.remove();
                    changed = true;
                }
            }
            int online = 0;
            for (UUID uuid : active) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.level() != world) continue;
                online++;
                BlockPos hit = detection.get(player.blockPosition());
                if (hit == null) {
                    hit = detection.get(BlockPos.containing(player.getEyePosition()));
                }
                if (hit != null && !hit.equals(registrations.put(uuid, hit))) {
                    changed = true;
                    setEntryGlow(world, uuid, true);
                }
            }
            // Quotas are cheap but not free: re-evaluate on registration changes and on the
            // 1 s heartbeat (which catches quota shifts with no door touches — a logout, say).
            if (changed || run.entryEvalHeartbeat()) {
                evaluateRoomEntry(world, run, registrations, online);
            }
            sendRunHud(world, run, exploreHudLabel(run, dbs), net.ledok.arenas_ld.packet.RunHudPayload.NO_BOSS_HP);
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
                addProtectTargetToPartyTeam(world, run, room);
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
                sendRunHud(world, run, Component.translatable("hud.arenas_ld.dungeon.ready", graceSeconds),
                    net.ledok.arenas_ld.packet.RunHudPayload.NO_BOSS_HP);
                return;
            }
        }

        // ---- ACTIVE (wave machine, mirrors the legacy activated branch) ----
        room.refreshAliveMobs(world);
        if (room.pollProtectTargetDied()) {
            handleLoss(world, controller, run, DungeonOutcome.LOSS_OBJECTIVE);
            return;
        }
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
        // Lock-in. A door shared with a cleared neighbor closes behind the party — intended.
        room.closeAllDoors(world);

        // A lingering loot popup (a bonus room's reward, say) leaves the screen now: the next
        // fight is starting and the popup's long hold exists for the calm between rooms.
        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer participant = world.getServer().getPlayerList().getPlayer(uuid);
            if (participant != null) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    participant, net.ledok.arenas_ld.packet.LootHudClearPayload.INSTANCE);
            }
        }

        // Rooms can be entered from any side: each player teleports to the respawn point
        // nearest their own position, so a party split across two doors enters on both sides.
        boolean hasRespawns = !room.getRespawnPointOffsets().isEmpty();
        for (UUID uuid : run.activeParticipantUuids()) {
            boolean isEnterer = uuid.equals(enterer.getUUID());
            if (isEnterer && !hasRespawns) {
                continue; // no respawn point: the enterer stays put, others gather on them
            }
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            BlockPos respawn = hasRespawns ? room.closestRespawnPos(player.blockPosition()) : null;
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
            openClearedRoomDoors(world, dbs, roomPos, room);
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

    /**
     * Graph-aware door states when a room clears: a door whose partner room is already cleared
     * (or absent — spawn-side/dead-end) opens fully invisible; a door to a still-pending partner
     * opens ARMED (orange) — passable, and crossing it activates that room. This also retints the
     * shared door of a previously-cleared neighbor from armed to fully open.
     */
    private static void openClearedRoomDoors(ServerLevel world, DungeonBossSpawnerBlockEntity dbs,
                                             BlockPos roomPos, RoomControllerBlockEntity room) {
        for (RoomGraph.Door door : RoomGraph.build(world, dbs).doorsOf(roomPos)) {
            BlockPos partnerPos = door.partnerOf(roomPos);
            boolean partnerPending = partnerPos != null
                && world.getBlockEntity(partnerPos) instanceof RoomControllerBlockEntity partner
                && !partner.isCleared();
            room.setDoorGroupState(world, door.anchor(), false, partnerPending);
        }
    }

    /**
     * Re-evaluates per-room entry quotas: locks in a room that reached its configured share of
     * the online party (each room's own {@code entryPercent}, rounded up, at least 1) and
     * otherwise caches the fullest room's numbers for the HUD's "Waiting players x/y" line.
     */
    private static void evaluateRoomEntry(ServerLevel world, DungeonRun run,
                                          Map<UUID, BlockPos> registrations, int online) {
        Map<BlockPos, Integer> counts = new HashMap<>();
        for (BlockPos roomPos : registrations.values()) {
            counts.merge(roomPos, 1, Integer::sum);
        }
        int bestCount = 0;
        int bestRequired = 1;
        for (Map.Entry<BlockPos, Integer> entry : counts.entrySet()) {
            int percent = world.getBlockEntity(entry.getKey()) instanceof RoomControllerBlockEntity room
                ? room.getObjective().clampedEntryPercent()
                : net.ledok.arenas_ld.dungeon.room.RoomObjectiveConfig.DEFAULT_ENTRY_PERCENT;
            int required = Math.max(1, (int) Math.ceil(online * percent / 100.0));
            if (entry.getValue() >= required) {
                ServerPlayer enterer = pickEnterer(world, registrations, entry.getKey());
                if (enterer != null) {
                    clearEntryRegistrations(world, run);
                    beginRoomEntry(world, run, entry.getKey(), enterer);
                    return;
                }
            }
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestRequired = required;
            }
        }
        run.setEntryWaiting(bestCount, bestRequired);
    }

    /** Clears all door registrations and the waiting HUD numbers, removing each player's glow. */
    private static void clearEntryRegistrations(ServerLevel world, DungeonRun run) {
        for (UUID uuid : run.roomEntryRegistrations().keySet()) {
            setEntryGlow(world, uuid, false);
        }
        run.roomEntryRegistrations().clear();
        run.setEntryWaiting(0, 1);
    }

    /** The waiting-at-a-door glow: outline only, no particles, gone once the room locks in. */
    private static void setEntryGlow(ServerLevel world, UUID uuid, boolean on) {
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            return;
        }
        if (on) {
            if (!player.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) {
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING,
                    net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, false, false));
            }
        } else {
            player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
        }
    }

    /** The registered player to anchor the room entry on — the gather point when the room has
     *  no respawn points. Null only if every player registered on the room is offline. */
    @Nullable
    private static ServerPlayer pickEnterer(ServerLevel world, Map<UUID, BlockPos> registrations,
                                            BlockPos roomPos) {
        for (Map.Entry<UUID, BlockPos> entry : registrations.entrySet()) {
            if (!roomPos.equals(entry.getValue())) continue;
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.level() == world) {
                return player;
            }
        }
        return null;
    }

    private static Component exploreHudLabel(DungeonRun run, DungeonBossSpawnerBlockEntity dbs) {
        Component base = Component.translatable("hud.arenas_ld.dungeon.explore",
            run.clearedRooms().size(), dbs.getRooms().size());
        if (run.entryWaitingRequired() > 1 && run.entryWaitingCount() > 0) {
            return base.copy().append(" · ").append(Component.translatable(
                "hud.arenas_ld.dungeon.waiting", run.entryWaitingCount(), run.entryWaitingRequired()));
        }
        return base;
    }

    /**
     * Puts a freshly spawned PROTECT target on the run's no-friendly-fire party team, so players
     * can't damage the mob they're defending. No-op when the room has no live target.
     */
    private static void addProtectTargetToPartyTeam(ServerLevel world, DungeonRun run, RoomControllerBlockEntity room) {
        UUID targetUuid = room.getProtectTargetUuid();
        if (targetUuid == null) {
            return;
        }
        Entity target = world.getEntity(targetUuid);
        PlayerTeam team = world.getScoreboard().getPlayerTeam(teamNameFor(run));
        if (target != null && team != null) {
            world.getScoreboard().addPlayerToTeam(target.getStringUUID(), team);
        }
    }

    /** The run's current room controller (branching current room or legacy index), or {@code null}. */
    @Nullable
    private static RoomControllerBlockEntity activeRoomController(ServerLevel world, DungeonBossSpawnerBlockEntity dbs, DungeonRun run) {
        BlockPos roomPos = run.currentRoomPos();
        if (roomPos == null) {
            List<BlockPos> rooms = dbs.getRooms();
            int index = run.currentRoomIndex();
            if (index < 0 || index >= rooms.size()) {
                return null;
            }
            roomPos = rooms.get(index);
        }
        return world.getBlockEntity(roomPos) instanceof RoomControllerBlockEntity room ? room : null;
    }

    /** Protect target HP% of the run's active room, or -1 when it has no live protect target. */
    private static int activeProtectTargetPercent(ServerLevel world, DungeonRun run) {
        BlockEntity dbsBe = world.getBlockEntity(run.dbsPos());
        if (!(dbsBe instanceof DungeonBossSpawnerBlockEntity dbs)) {
            return -1;
        }
        RoomControllerBlockEntity room = activeRoomController(world, dbs, run);
        return room != null ? room.getProtectTargetHealthPercent(world) : -1;
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
        boolean puffishLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("puffish_skills");

        for (UUID uuid : run.lootEligibleUuids()) {
            PlayerStatsStore.get(world.getServer()).recordWin(uuid, PlayerStatsStore.Mode.DUNGEON);

            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            // LuckPerms perks (extra rolls, currency/XP multipliers); defaults when offline.
            net.ledok.arenas_ld.util.EndRewardPerks perks = net.ledok.arenas_ld.util.EndRewardPerks.forPlayer(
                player != null ? uuid : null, net.ledok.arenas_ld.util.EndRewardPerks.Mode.DUNGEON);
            List<ItemStack> loot = RewardDelivery.rollLoot(world, lootTableId, player,
                player != null ? player.position() : Vec3.atCenterOf(run.dbsPos()), perks.lootRolls());
            long currency = perks.scaleCurrency(rewardPerPlayer);

            if (player == null) {
                // Offline but loot-eligible: the reward still lands via the economy inbox.
                RewardDelivery.giveStacksOffline(uuid, loot, "DUNGEON_LOOT");
                RewardDelivery.giveCurrency(world.getServer(), uuid, currency, "DUNGEON_REWARD");
                continue;
            }

            controller.addLeaderboardEntry(
                run.tier(),
                new LeaderboardEntry(player.getGameProfile().getName(), runDurationSeconds, System.currentTimeMillis())
            );

            List<net.ledok.arenas_ld.packet.LootRewardPayload.Entry> delivered =
                RewardDelivery.giveStacks(player, loot, "DUNGEON_LOOT");
            RewardDelivery.giveCurrency(world.getServer(), uuid, currency, "DUNGEON_REWARD");

            int grantedXp = 0;
            int scaledXp = perks.scaleXp(xpReward);
            if (scaledXp > 0 && puffishLoaded) {
                net.ledok.arenas_ld.compat.PuffishSkillsCompat.addExperience(player, scaledXp);
                grantedXp = scaledXp;
            }

            RewardDelivery.notify(player, net.ledok.arenas_ld.packet.LootRewardPayload.Source.DUNGEON_WIN,
                delivered, RewardDelivery.displayableCurrency(currency), grantedXp);
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
        // Swap the run timer for the close countdown right away, not on the next second tick.
        RunHudSync.send(world, hudTargets(run), closeHudPayload(run));
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
            case LOSS_OBJECTIVE -> "message.arenas_ld.dungeon.loss_objective";
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
        // Swap the run timer for the close countdown right away, not on the next second tick.
        RunHudSync.send(world, hudTargets(run), closeHudPayload(run));
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
        hideBossBars(world, run);
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
     * Pulls a single player out of their dungeon run before its shared timers elapse.
     * Two situations:
     * <ul>
     *   <li>CLOSING — the run already ended; they skip the close-timer wait.</li>
     *   <li>STARTING/RUNNING — a voluntary mid-run forfeit: they're sent home and removed, and
     *       the run continues for the rest of the party (or ends as abandoned next tick if
     *       they were the last one in).</li>
     * </ul>
     * Triggered by /exit and the "[Exit Now]" chat button.
     *
     * @return true if the player was in a run and removed; false otherwise.
     */
    public static boolean exitEarly(MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
        if (run == null || run.phase() == DungeonPhase.DONE) {
            return false;
        }
        RunParticipant participant = run.participants().get(uuid);
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) {
            return false;
        }
        boolean midRun = run.phase() != DungeonPhase.CLOSING;
        ServerLevel world = server.getLevel(run.dbsDimension());
        long now = world != null ? world.getGameTime() : participant.lastSeenTick();
        DebugLog.log("exitEarly: {} leaves run at {} during {}", player.getScoreboardName(), run.dbsPos(), run.phase());

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

        RunHudSync.hide(player);

        if (midRun) {
            // Forfeit: no rewards for the leaver; their timer HUD is hidden above, and
            // tickRunning ends the run as abandoned if nobody is left in it.
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.exited_mid_run")
                .withStyle(ChatFormatting.YELLOW));
            if (world != null) {
                broadcastToParty(world, run, Component.translatable(
                    "message.arenas_ld.dungeon.party_exited", participant.playerName())
                    .withStyle(ChatFormatting.YELLOW), uuid);
            }
            return true;
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
        // The HUD draws the countdown itself from remainingTicks, so the label only carries
        // objective context. The boss-HP slot shows a protect target's health, or the health
        // of the current wave's boss mob(s) — any mob flagged as boss, vanilla or modded.
        int targetPercent = room.getProtectTargetHealthPercent(world);
        int bossHp = roomBossHpPercent(world, room);
        Component label;
        if (targetPercent >= 0) {
            bossHp = Math.min(100, targetPercent);
            label = room.getTotalWaves() > 1
                ? Component.translatable("hud.arenas_ld.dungeon.protect_waves",
                    room.getWaveDisplay(), room.getTotalWaves(), room.getAliveMobs().size())
                : Component.translatable("hud.arenas_ld.dungeon.protect", room.getAliveMobs().size());
        } else if (room.getObjective().type() == net.ledok.arenas_ld.dungeon.room.RoomObjectiveConfig.Type.SURVIVE
                && room.getSurviveTicksRemaining() > 0) {
            int surviveSeconds = (room.getSurviveTicksRemaining() + 19) / 20;
            label = Component.translatable("hud.arenas_ld.dungeon.survive",
                String.format("%d:%02d", surviveSeconds / 60, surviveSeconds % 60));
        } else if (room.getTotalWaves() > 1) {
            label = Component.translatable("hud.arenas_ld.dungeon.waves",
                room.getWaveDisplay(), room.getTotalWaves(), room.getAliveMobs().size());
        } else {
            label = Component.translatable("hud.arenas_ld.dungeon.kill_all", room.getAliveMobs().size());
        }

        sendRunHud(world, run, label, bossHp);
    }

    /**
     * Combined health of the room's live boss mobs as a whole percent (ceil'd so it never
     * reads 0% while a boss lives), or NO_BOSS_HP when the current wave has no boss.
     */
    private static int roomBossHpPercent(ServerLevel world, RoomControllerBlockEntity room) {
        float hp = 0;
        float max = 0;
        for (UUID uuid : room.getBossMobs()) {
            if (world.getEntity(uuid) instanceof net.minecraft.world.entity.LivingEntity living && living.isAlive()) {
                hp += living.getHealth();
                max += living.getMaxHealth();
            }
        }
        if (max <= 0) {
            return net.ledok.arenas_ld.packet.RunHudPayload.NO_BOSS_HP;
        }
        return Math.min(100, (int) Math.ceil(hp * 100.0f / max));
    }

    /** Once-a-second RUN-kind HUD update carrying the dungeon timer plus the given context line. */
    private static void sendRunHud(ServerLevel world, DungeonRun run, Component label, int bossHp) {
        int totalTicks = run.resolvedTierConfig().dungeonTimeSeconds() * 20;
        RunHudSync.tickSend(world, hudTargets(run), new net.ledok.arenas_ld.packet.RunHudPayload(
            net.ledok.arenas_ld.packet.RunHudPayload.Kind.RUN, label,
            Math.max(0, run.dungeonTimerTicks()), totalTicks, run.hardcoreEnabled(), bossHp));
    }

    private static net.ledok.arenas_ld.packet.RunHudPayload closeHudPayload(DungeonRun run) {
        return new net.ledok.arenas_ld.packet.RunHudPayload(
            net.ledok.arenas_ld.packet.RunHudPayload.Kind.CLOSE,
            Component.translatable("hud.arenas_ld.close"),
            Math.max(0, run.closeTimerTicks()), run.initialCloseTimerTicks(),
            run.hardcoreEnabled(), net.ledok.arenas_ld.packet.RunHudPayload.NO_BOSS_HP);
    }

    private static void updateCloseTimerBossBar(ServerLevel world, DungeonRun run) {
        RunHudSync.tickSend(world, hudTargets(run), closeHudPayload(run));
    }

    private static void hideBossBars(ServerLevel world, DungeonRun run) {
        RunHudSync.hide(world, hudTargets(run));
    }

    /** Online, non-removed participants — the audience for the run HUD bar. */
    private static List<UUID> hudTargets(DungeonRun run) {
        List<UUID> targets = new ArrayList<>();
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() != ParticipantStatus.REMOVED) {
                targets.add(entry.getKey());
            }
        }
        return targets;
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
        boolean puffishLoaded = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("puffish_skills");
        List<MobEffectInstance> effectInstances = resolveRewardEffects(reward.effects());

        for (UUID uuid : run.lootEligibleUuids()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            List<ItemStack> loot = RewardDelivery.rollLoot(world, reward.lootTableId(), player,
                player != null ? player.position() : Vec3.atCenterOf(room.getBlockPos()));

            if (player == null) {
                RewardDelivery.giveStacksOffline(uuid, loot, "DUNGEON_LOOT");
                RewardDelivery.giveCurrency(world.getServer(), uuid, reward.currency(), "DUNGEON_REWARD");
                continue;
            }

            List<net.ledok.arenas_ld.packet.LootRewardPayload.Entry> delivered =
                RewardDelivery.giveStacks(player, loot, "DUNGEON_LOOT");
            RewardDelivery.giveCurrency(world.getServer(), uuid, reward.currency(), "DUNGEON_REWARD");

            int grantedXp = 0;
            if (reward.skillXp() > 0 && puffishLoaded) {
                net.ledok.arenas_ld.compat.PuffishSkillsCompat.addExperience(player, reward.skillXp());
                grantedXp = reward.skillXp();
            }
            for (MobEffectInstance instance : effectInstances) {
                player.addEffect(new MobEffectInstance(instance));
            }

            RewardDelivery.notify(player, net.ledok.arenas_ld.packet.LootRewardPayload.Source.ROOM_CLEAR,
                delivered, effectInstances, RewardDelivery.displayableCurrency(reward.currency()), grantedXp);
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
                DebugLog.log("hardcore death: ejecting {} to {}", DebugLog.describe(player), returnPoint.pos());
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
        // Defending a target makes downed teammates costlier: the revive takes 50% longer.
        int respawnTicks = controller.getRespawnTimeTicks();
        if (activeProtectTargetPercent(world, run) >= 0) {
            respawnTicks = respawnTicks * 3 / 2;
        }
        run.setDowned(new DownedPlayer(player.getUUID(), respawnTicks));
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
            // In a protect room, revived players come back with the target's HP% — a battered
            // target means battered reinforcements.
            int protectPercent = activeProtectTargetPercent(world, run);
            player.setHealth(protectPercent >= 0
                ? Math.max(1.0F, player.getMaxHealth() * protectPercent / 100.0F)
                : player.getMaxHealth());
            teleportToRoomRespawnOrEntrance(world, dbs, run, player);
        }

        RunParticipant participant = run.participants().get(uuid);
        if (participant != null) {
            run.updateParticipant(participant.withStatus(ParticipantStatus.ACTIVE, world.getGameTime()));
        }
    }

    /** Respawn position of the run's active room closest to {@code near}, or {@code null} if the active room has none. */
    @Nullable
    private static BlockPos activeRoomRespawnPos(ServerLevel world, DungeonBossSpawnerBlockEntity dbs, DungeonRun run, BlockPos near) {
        // Branching: the locked/pending room is authoritative; while EXPLORING, fall back to the
        // most recently cleared room that has a respawn point, so respawns track the party's
        // progress instead of dumping players back at the dungeon entrance.
        BlockPos branchingRoom = run.currentRoomPos();
        if (branchingRoom != null) {
            return world.getBlockEntity(branchingRoom) instanceof RoomControllerBlockEntity room
                ? room.closestRespawnPos(near)
                : null;
        }
        if (isBranching(world, dbs)) {
            List<BlockPos> cleared = new ArrayList<>(run.clearedRooms());
            for (int i = cleared.size() - 1; i >= 0; i--) {
                if (world.getBlockEntity(cleared.get(i)) instanceof RoomControllerBlockEntity clearedRoom
                        && !clearedRoom.getRespawnPointOffsets().isEmpty()) {
                    return clearedRoom.closestRespawnPos(near);
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
            ? room.closestRespawnPos(near)
            : null;
    }

    /**
     * Teleports the player to the run's active room respawn point, falling back to the dungeon
     * entrance when the active room has none. The active room is read now (not when the player
     * went down), so clearing rooms while a teammate is away pushes their respawn forward.
     */
    private static void teleportToRoomRespawnOrEntrance(ServerLevel world, DungeonBossSpawnerBlockEntity dbs, DungeonRun run, ServerPlayer player) {
        BlockPos roomRespawn = activeRoomRespawnPos(world, dbs, run, player.blockPosition());
        if (roomRespawn != null) {
            DebugLog.log("teleport to room respawn {} in {}: {}", roomRespawn, world.dimension().location(), DebugLog.describe(player));
            player.teleportTo(world, roomRespawn.getX() + 0.5, roomRespawn.getY(), roomRespawn.getZ() + 0.5, 0.0F, 0.0F);
            return;
        }
        ServerLevel target = world.getServer().getLevel(dbs.getEntranceDimension());
        if (target == null) {
            target = world;
        }
        BlockPos entrance = dbs.getAbsoluteEntrancePos();
        DebugLog.log("teleport to entrance {} in {}: {}", entrance, target.dimension().location(), DebugLog.describe(player));
        player.teleportTo(target, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5, 0.0F, 0.0F);
    }

    /** Teleports a player to a captured return point and restores their pre-run game mode + health. */
    private static void applyReturnPoint(MinecraftServer server, ServerPlayer player, PlayerReturnPoint rp) {
        ServerLevel target = server.getLevel(rp.dimension());
        DebugLog.log("teleport to return point {} in {}: {}", rp.pos(), rp.dimension().location(), DebugLog.describe(player));
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
        DebugLog.log("disconnect: {} leaves run at {} (status was {})", DebugLog.describe(player), run.dbsPos(), participant.status());
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
        DebugLog.log("reconnect: {}", DebugLog.describe(player));

        PlayerReturnPoint pending = PendingRestoreStore.get(server).take(uuid);
        if (pending != null) {
            DebugLog.log("reconnect: pending restore -> {} in {}", pending.pos(), pending.dimension().location());
            applyReturnPoint(server, player, pending);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.run_ended_while_away")
                .withStyle(ChatFormatting.YELLOW));
            return;
        }

        DungeonRun run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(uuid);
        if (run == null) {
            DebugLog.log("reconnect: no run for {}", player.getScoreboardName());
            return;
        }

        // Orphaned run: no loaded controller owns it (controllers keep their own chunk
        // force-loaded, so this means the run isn't ticking — e.g. a crash during start
        // aborted registration). Eject instead of teleporting into a dead dungeon.
        if (ArenasLdMod.DUNGEON_MANAGER.findControllerForRun(server, run) == null) {
            DebugLog.log("reconnect: run at {} is orphaned, ejecting {}", run.dbsPos(), player.getScoreboardName());
            PlayerReturnPoint rp = run.returnPoints().get(uuid);
            if (rp != null) {
                applyReturnPoint(server, player, rp);
                run.removeReturnPoint(uuid);
            } else {
                player.setGameMode(GameType.SURVIVAL);
            }
            removeFromPartyTeam(player.serverLevel(), run, player.getScoreboardName());
            ArenasLdMod.DUNGEON_MANAGER.unregisterParticipant(uuid);
            BusyStateCompat.clearBusy(uuid, BUSY_REASON);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.run_ended_while_away")
                .withStyle(ChatFormatting.YELLOW));
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
            DebugLog.log("reconnect: rejoining run at {} as DOWNED, dbs {}", run.dbsPos(), dbs != null ? "loaded" : "NOT LOADED");
            run.updateParticipant(participant.withStatus(ParticipantStatus.DOWNED, now));
            player.setGameMode(GameType.SPECTATOR);
            player.setHealth(1.0F);
            if (dbs != null) {
                teleportToRoomRespawnOrEntrance(world, dbs, run, player);
            }
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon.reconnected_downed")
                .withStyle(ChatFormatting.YELLOW));
        } else {
            DebugLog.log("reconnect: rejoining run at {} as ACTIVE, dbs {}", run.dbsPos(), dbs != null ? "loaded" : "NOT LOADED");
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
        DebugLog.log("forceLoad: {} chunks in {} for dbs {}: {}", chunks.size(), world.dimension().location(), dbsPos, chunks);
        for (ChunkPos chunkPos : chunks) {
            world.setChunkForced(chunkPos.x, chunkPos.z, true);
        }
        setEntranceChunkForced(world, dbsPos, true);
    }

    private static void unforceChunksForRun(ServerLevel world, BlockPos dbsPos) {
        Set<ChunkPos> chunks = collectRunChunks(world, dbsPos);
        DebugLog.log("unforce: {} chunks in {} for dbs {}", chunks.size(), world.dimension().location(), dbsPos);
        for (ChunkPos chunkPos : chunks) {
            world.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        setEntranceChunkForced(world, dbsPos, false);
    }

    /**
     * The entrance may live in a different dimension than the DBS and its rooms, so
     * {@link #collectRunChunks} can't cover it — force it separately in its own level.
     * Keeps run starts and reconnects from dropping players into an unloaded chunk.
     */
    private static void setEntranceChunkForced(ServerLevel world, BlockPos dbsPos, boolean forced) {
        if (!(world.getBlockEntity(dbsPos) instanceof DungeonBossSpawnerBlockEntity dbs)) {
            return;
        }
        BlockPos entrance = dbs.getAbsoluteEntrancePos();
        if (entrance == null) {
            return;
        }
        ServerLevel entranceLevel = world.getServer().getLevel(dbs.getEntranceDimension());
        if (entranceLevel == null) {
            entranceLevel = world;
        }
        ChunkPos chunkPos = new ChunkPos(entrance);
        DebugLog.log("{} entrance chunk {} in {}", forced ? "forceLoad:" : "unforce:", chunkPos, entranceLevel.dimension().location());
        entranceLevel.setChunkForced(chunkPos.x, chunkPos.z, forced);
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
                BlockPos protectPos = roomController.getProtectPos();
                if (protectPos != null) {
                    chunks.add(new ChunkPos(protectPos));
                }
            }
        }
        return chunks;
    }
}
