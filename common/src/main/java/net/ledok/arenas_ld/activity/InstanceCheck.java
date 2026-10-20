package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.RoomGraph;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks one instance (a Dungeon Boss Spawner, Raid Boss Spawner or Arena Spawner and everything
 * linked to it): every link points at the right block, the entrance and respawn points are free,
 * and which controllers list it. Made for copies pasted with WorldEdit or Axiom: every link inside
 * an instance is an offset from its own block, so a paste keeps them, but an entrance in another
 * dimension keeps the same offset there, which is rarely the right spot.
 *
 * <p>Loads the chunks it looks at, so it is an admin command, not something to run every tick.
 */
public final class InstanceCheck {
    private InstanceCheck() {
    }

    public enum Severity { ERROR, WARNING }

    public record Problem(Severity severity, Component message) {
    }

    /** What was checked, the problems found and the activities whose controller lists this instance. */
    public record Report(Component what, BlockPos pos, ResourceKey<Level> dimension, Component summary,
                         List<Problem> problems, List<ResourceLocation> linkedTo) {
        public long errors() {
            return problems.stream().filter(p -> p.severity() == Severity.ERROR).count();
        }
    }

    /** Checks the instance block at {@code pos}; null when no instance block is there. */
    public static @Nullable Report check(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DungeonBossSpawnerBlockEntity dbs) {
            return checkDungeon(level, pos, dbs);
        }
        if (be instanceof RaidBossSpawnerBlockEntity raid) {
            return checkSpawner(level, pos, be, raid.getEntranceDimension(), raid.getAbsoluteEntrancePos(),
                raid.getRespawnPointOffsets());
        }
        if (be instanceof ArenaSpawnerBlockEntity arena) {
            return checkSpawner(level, pos, be, arena.getEntranceDimension(), arena.getAbsoluteEntrancePos(),
                arena.getRespawnPointOffsets());
        }
        return null;
    }

    /** Raid and arena spawners: the fight is at the spawner, so the entrance must share its dimension. */
    private static Report checkSpawner(ServerLevel level, BlockPos pos, BlockEntity be, ResourceKey<Level> entranceDimension,
                                       BlockPos entrance, List<BlockPos> respawnOffsets) {
        List<Problem> problems = new ArrayList<>();
        ServerLevel entranceLevel = checkEntrance(level, pos, entranceDimension, entrance, true, problems);
        // Their respawn points are used in the entrance's dimension.
        int respawns = checkRespawns(entranceLevel != null ? entranceLevel : level, pos, respawnOffsets, problems);
        return report(level, pos, be, Component.translatable("message.arenas_ld.instance.summary_spawner", respawns),
            problems);
    }

    private static Report checkDungeon(ServerLevel level, BlockPos pos, DungeonBossSpawnerBlockEntity dbs) {
        List<Problem> problems = new ArrayList<>();
        List<BlockPos> rooms = dbs.getRooms();
        if (rooms.isEmpty()) {
            problems.add(error("message.arenas_ld.instance.no_rooms"));
        }
        int spawners = 0;
        int doors = 0;
        int respawns = 0;
        for (int i = 0; i < rooms.size(); i++) {
            BlockPos roomPos = rooms.get(i);
            if (!(level.getBlockEntity(roomPos) instanceof RoomControllerBlockEntity room)) {
                problems.add(error("message.arenas_ld.instance.room_missing", i + 1, roomPos.toShortString()));
                continue;
            }
            String label = room.getRoomName().isBlank() ? "#" + (i + 1) : room.getRoomName();
            for (BlockPos spawnerPos : room.getSpawnerPositions()) {
                spawners++;
                BlockEntity spawner = level.getBlockEntity(spawnerPos);
                if (!(spawner instanceof MobSpawnerBlockEntity) && !(spawner instanceof DungeonBossSpawnerBlockEntity)) {
                    problems.add(error("message.arenas_ld.instance.spawner_missing", label, spawnerPos.toShortString()));
                }
            }
            for (BlockPos doorPos : room.getDoorPositions()) {
                doors++;
                if (!(level.getBlockState(doorPos).getBlock() instanceof PhaseBlock)) {
                    problems.add(error("message.arenas_ld.instance.door_missing", label, doorPos.toShortString()));
                }
            }
            for (BlockPos respawn : room.getRespawnPositions()) {
                respawns++;
                if (!isFree(level, respawn)) {
                    problems.add(warning("message.arenas_ld.instance.respawn_blocked", label, respawn.toShortString()));
                }
            }
        }

        BlockPos start = dbs.getAbsoluteStartRoomPos();
        BlockPos fin = dbs.getAbsoluteFinalRoomPos();
        if (start != null || fin != null) {
            if (start == null || fin == null) {
                problems.add(error("message.arenas_ld.instance.markers_incomplete"));
            }
            boolean startIsRoom = start != null && rooms.contains(start);
            if (start != null && !startIsRoom) {
                problems.add(error("message.arenas_ld.instance.marker_not_room",
                    Component.translatable("message.arenas_ld.instance.marker_start"), start.toShortString()));
            }
            if (fin != null && !rooms.contains(fin)) {
                problems.add(error("message.arenas_ld.instance.marker_not_room",
                    Component.translatable("message.arenas_ld.instance.marker_final"), fin.toShortString()));
            }
            if (startIsRoom && level.getBlockEntity(start) instanceof RoomControllerBlockEntity) {
                boolean hasWayIn = RoomGraph.build(level, dbs).doorsOf(start).stream()
                    .anyMatch(door -> door.partnerOf(start) == null);
                if (!hasWayIn) {
                    problems.add(error("message.arenas_ld.instance.start_no_way_in"));
                }
            }
        }

        // A dungeon's entrance may be in another dimension: only a warning (a copy keeps the same offset there).
        checkEntrance(level, pos, dbs.getEntranceDimension(), dbs.getAbsoluteEntrancePos(), false, problems);
        return report(level, pos, dbs,
            Component.translatable("message.arenas_ld.instance.summary_dungeon", rooms.size(), spawners, doors, respawns),
            problems);
    }

    /** Checks the entrance; returns its level, or null when there is none to use. */
    private static @Nullable ServerLevel checkEntrance(ServerLevel level, BlockPos pos, ResourceKey<Level> dimension,
                                                       BlockPos entrance, boolean mustShareDimension,
                                                       List<Problem> problems) {
        if (entrance.equals(pos)) {
            // A zero offset: never set (whatever dimension it names, players would go to these coordinates).
            problems.add(error("message.arenas_ld.instance.entrance_unset"));
            return null;
        }
        ServerLevel entranceLevel = level.getServer().getLevel(dimension);
        if (entranceLevel == null) {
            problems.add(error("message.arenas_ld.instance.entrance_no_dimension", dimension.location().toString()));
            return null;
        }
        if (!dimension.equals(level.dimension())) {
            problems.add(mustShareDimension
                ? error("message.arenas_ld.instance.entrance_wrong_dimension",
                    dimension.location().toString(), entrance.toShortString())
                : warning("message.arenas_ld.instance.entrance_other_dimension",
                    dimension.location().toString(), entrance.toShortString()));
        }
        if (!isFree(entranceLevel, entrance)) {
            problems.add(error("message.arenas_ld.instance.entrance_blocked", entrance.toShortString()));
        }
        return entranceLevel;
    }

    private static int checkRespawns(ServerLevel level, BlockPos pos, List<BlockPos> offsets, List<Problem> problems) {
        Component label = Component.translatable("message.arenas_ld.instance.spawner_label");
        for (BlockPos offset : offsets) {
            BlockPos respawn = pos.offset(offset);
            if (!isFree(level, respawn)) {
                problems.add(warning("message.arenas_ld.instance.respawn_blocked", label, respawn.toShortString()));
            }
        }
        return offsets.size();
    }

    /**
     * A player put here (feet in this block) wouldn't be stuck in a block. Something low at the feet
     * (a carpet, a slab, snow) only lifts them, so up to half a block there is fine.
     */
    private static boolean isFree(ServerLevel level, BlockPos feet) {
        BlockPos head = feet.above();
        VoxelShape feetShape = level.getBlockState(feet).getCollisionShape(level, feet);
        return (feetShape.isEmpty() || feetShape.max(Direction.Axis.Y) <= 0.5)
            && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    private static Report report(ServerLevel level, BlockPos pos, BlockEntity be, Component summary, List<Problem> problems) {
        // Errors first (a stable sort keeps the order they were found in), so a cut-off list never hides one.
        List<Problem> sorted = new ArrayList<>(problems);
        sorted.sort(java.util.Comparator.comparing(Problem::severity));
        return new Report(be.getBlockState().getBlock().getName(), pos.immutable(), level.dimension(), summary,
            List.copyOf(sorted), linkedTo(level.getServer(), level.dimension(), pos));
    }

    /** The activities whose controller lists this instance (controllers keep their chunks loaded). */
    public static List<ResourceLocation> linkedTo(MinecraftServer server, ResourceKey<Level> dimension, BlockPos pos) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (var entry : ActivityRegistry.knownControllers(server)) {
            ServerLevel level = server.getLevel(entry.dimension());
            if (level == null || !level.isLoaded(entry.pos())) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(entry.pos());
            boolean listed = switch (be) {
                case DungeonControllerBlockEntity c ->
                    c.getInstances().contains(pos) && c.getInstanceDimension(pos).equals(dimension);
                case RaidControllerBlockEntity c -> c.getInstances().stream()
                    .anyMatch(inst -> inst.spawnerPos().equals(pos) && inst.dimension().equals(dimension));
                case ArenaControllerBlockEntity c -> c.getInstances().stream()
                    .anyMatch(inst -> inst.spawnerPos().equals(pos) && inst.dimension().equals(dimension));
                case null, default -> false;
            };
            if (listed) {
                ids.add(entry.id());
            }
        }
        return ids;
    }

    private static Problem error(String key, Object... args) {
        return new Problem(Severity.ERROR, Component.translatable(key, args));
    }

    private static Problem warning(String key, Object... args) {
        return new Problem(Severity.WARNING, Component.translatable(key, args));
    }
}
