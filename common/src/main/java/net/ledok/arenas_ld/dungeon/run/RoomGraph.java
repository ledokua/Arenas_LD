package net.ledok.arenas_ld.dungeon.run;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.PhaseBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Door-derived directed graph of a dungeon's rooms. Edge A→B exists when a block of one of A's
 * exit-door groups coincides with a block of one of B's entrance-door groups (builders link the
 * same PhaseBlock from both sides; groups are flood-filled so multi-block doors match on any
 * block). Deliberately standalone so future features (e.g. dynamically generated room maps) can
 * build on {@link #edges()} without touching the run lifecycle.
 */
public final class RoomGraph {

    public record Edge(BlockPos fromRoom, BlockPos toRoom) {
    }

    private final List<BlockPos> roomPositions;
    private final Map<BlockPos, Set<BlockPos>> entranceGroupBlocks;
    private final List<Edge> edges;
    private final boolean anyEntranceDoors;

    private RoomGraph(List<BlockPos> roomPositions, Map<BlockPos, Set<BlockPos>> entranceGroupBlocks,
                      List<Edge> edges, boolean anyEntranceDoors) {
        this.roomPositions = roomPositions;
        this.entranceGroupBlocks = entranceGroupBlocks;
        this.edges = edges;
        this.anyEntranceDoors = anyEntranceDoors;
    }

    public static RoomGraph build(ServerLevel world, DungeonBossSpawnerBlockEntity dbs) {
        List<BlockPos> roomPositions = new ArrayList<>();
        Map<BlockPos, Set<BlockPos>> exitBlocks = new HashMap<>();
        Map<BlockPos, Set<BlockPos>> entranceBlocks = new HashMap<>();
        boolean anyEntrances = false;

        for (BlockPos roomPos : dbs.getRooms()) {
            if (!(world.getBlockEntity(roomPos) instanceof RoomControllerBlockEntity room)) {
                continue;
            }
            roomPositions.add(roomPos);
            exitBlocks.put(roomPos, expandAnchors(world, room.getDoorPositions()));
            Set<BlockPos> entrances = expandAnchors(world, room.getEntrancePositions());
            entranceBlocks.put(roomPos, entrances);
            anyEntrances |= !entrances.isEmpty();
        }

        List<Edge> edges = new ArrayList<>();
        for (BlockPos from : roomPositions) {
            Set<BlockPos> exits = exitBlocks.get(from);
            if (exits.isEmpty()) continue;
            for (BlockPos to : roomPositions) {
                if (from.equals(to)) continue;
                if (!Collections.disjoint(exits, entranceBlocks.get(to))) {
                    edges.add(new Edge(from, to));
                }
            }
        }
        return new RoomGraph(List.copyOf(roomPositions), Map.copyOf(entranceBlocks),
            List.copyOf(edges), anyEntrances);
    }

    /** True when at least one room has entrance doors — i.e. the dungeon uses branching mode. */
    public boolean hasAnyEntranceDoors() {
        return anyEntranceDoors;
    }

    public List<BlockPos> roomPositions() {
        return roomPositions;
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<BlockPos> successors(BlockPos roomPos) {
        List<BlockPos> out = new ArrayList<>();
        for (Edge edge : edges) {
            if (edge.fromRoom().equals(roomPos)) {
                out.add(edge.toRoom());
            }
        }
        return out;
    }

    /**
     * Per-tick crossing-detection lookup: every entrance-door block of rooms passing
     * {@code eligible} (lifecycle passes: not activated && not cleared), mapped to the room
     * it lets into. A block claimed by two rooms' entrances is a builder error — first wins,
     * with a warning.
     */
    public Map<BlockPos, BlockPos> buildEntranceDetectionMap(Predicate<BlockPos> eligible) {
        Map<BlockPos, BlockPos> detection = new HashMap<>();
        for (BlockPos roomPos : roomPositions) {
            if (!eligible.test(roomPos)) continue;
            for (BlockPos doorBlock : entranceGroupBlocks.get(roomPos)) {
                BlockPos previous = detection.putIfAbsent(doorBlock, roomPos);
                if (previous != null && !previous.equals(roomPos)) {
                    ArenasLdMod.LOGGER.warn(
                        "RoomGraph: door block {} is an entrance of both {} and {}; using the first",
                        doorBlock, previous, roomPos);
                }
            }
        }
        return detection;
    }

    private static Set<BlockPos> expandAnchors(ServerLevel world, List<BlockPos> anchors) {
        Set<BlockPos> union = new HashSet<>();
        for (BlockPos anchor : anchors) {
            if (union.contains(anchor)) continue;
            union.addAll(expandDoorGroup(world, anchor));
        }
        return union;
    }

    /**
     * All PhaseBlocks connected to the anchor, mirroring {@link PhaseBlockEntity#propagateState}'s
     * 6-neighbor flood fill, so multi-block doors detect a crossing on any of their blocks.
     * Capped as a backstop against runaway phase-block walls.
     */
    public static Set<BlockPos> expandDoorGroup(ServerLevel world, BlockPos anchor) {
        Set<BlockPos> group = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (world.isLoaded(anchor) && world.getBlockEntity(anchor) instanceof PhaseBlockEntity) {
            queue.add(anchor);
        } else {
            // Anchor missing or not a phase block (broken link): still detect on the anchor itself.
            group.add(anchor);
            return group;
        }
        while (!queue.isEmpty() && group.size() < 1024) {
            BlockPos pos = queue.poll();
            if (!group.add(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (!group.contains(neighbor) && world.isLoaded(neighbor)
                        && world.getBlockEntity(neighbor) instanceof PhaseBlockEntity) {
                    queue.add(neighbor);
                }
            }
        }
        return group;
    }
}
