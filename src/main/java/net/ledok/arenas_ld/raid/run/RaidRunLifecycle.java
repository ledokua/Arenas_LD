package net.ledok.arenas_ld.raid.run;

import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity.ControllerKey;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

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
    //  Participant + return-point mutators (cross-package gateways to RaidRun's
    //  package-private setters).
    // ─────────────────────────────────────────────────────────────────────────

    public static void addParticipant(RaidRun run, RunParticipant participant) {
        run.addParticipant(participant);
    }

    public static void updateParticipant(RaidRun run, RunParticipant participant) {
        run.updateParticipant(participant);
    }

    public static void removeParticipant(RaidRun run, UUID uuid) {
        run.removeParticipant(uuid);
    }

    public static void setReturnPoint(RaidRun run, UUID uuid, PlayerReturnPoint rp) {
        run.setReturnPoint(uuid, rp);
    }

    public static void removeReturnPoint(RaidRun run, UUID uuid) {
        run.removeReturnPoint(uuid);
    }

    public static void markDisconnected(RaidRun run, UUID uuid, long tick) {
        run.markDisconnected(uuid, tick);
    }

    public static void clearDisconnected(RaidRun run, UUID uuid) {
        run.clearDisconnected(uuid);
    }
}
