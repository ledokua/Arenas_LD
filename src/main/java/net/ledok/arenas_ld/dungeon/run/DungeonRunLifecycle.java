package net.ledok.arenas_ld.dungeon.run;

import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DungeonRunLifecycle {
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

    private static void tickStarting(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        run.setPhase(DungeonPhase.RUNNING);
    }

    private static void tickRunning(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
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
        run.setOutcome(DungeonOutcome.WIN);
        run.setPhase(DungeonPhase.CLOSING);
        run.setCloseTimerTicks(controller.getCloseTimerSeconds() * 20);
        // TODO PE-8: loot and leaderboard.
    }

    static void handleLoss(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run, DungeonOutcome reason) {
        run.setOutcome(reason);
        run.setPhase(DungeonPhase.CLOSING);
        run.setCloseTimerTicks(controller.getCloseTimerSeconds() * 20);
        // TODO PE-9: boss despawn.
    }

    static void finalize(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
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
    }

    private static void tickDownedPlayers(ServerLevel world, DungeonControllerBlockEntity controller, DungeonRun run) {
        // TODO PE-10: downed-player ticking and hardcore handling.
    }

    private static void updateDungeonTimeBossBar(ServerLevel world, DungeonRun run) {
        // TODO PE-6.1: boss bar implementation.
    }

    private static void updateCloseTimerBossBar(ServerLevel world, DungeonRun run) {
        // TODO PE-6.1: boss bar implementation.
    }

    private static void hideBossBars(DungeonRun run) {
        // TODO PE-6.1: boss bar implementation.
    }
}
