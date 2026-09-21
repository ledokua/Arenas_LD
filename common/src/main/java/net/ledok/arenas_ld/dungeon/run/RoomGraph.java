package net.ledok.arenas_ld.dungeon.run;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.PhaseBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Undirected door graph of a dungeon's rooms. A door is a connected group of PhaseBlocks
 * (flood-filled, so linking any block of a multi-block doorway means the whole doorway) and
 * belongs to the rooms that linked it — usually two (a connection), sometimes one (a boundary
 * door: the spawn-side entrance of the start room, or a dead end). Doors carry no direction:
 * whichever side clears first opens the door armed toward the other, and crossing it activates
 * the uncleared side. Deliberately standalone so future features (validation, map views) can
 * build on {@link #doors()} without touching the run lifecycle.
 */
public final class RoomGraph {

    /**
     * One door: its full block group, canonical anchor (lowest x,y,z block — the identity every
     * room's link resolves to), and the rooms that linked it. More than two owning rooms is a
     * builder error (warned at build).
     */
    public record Door(BlockPos anchor, Set<BlockPos> blocks, List<BlockPos> rooms) {
        /** The room on the other side, or null for a boundary door (spawn-side or dead end). */
        @Nullable
        public BlockPos partnerOf(BlockPos room) {
            for (BlockPos other : rooms) {
                if (!other.equals(room)) {
                    return other;
                }
            }
            return null;
        }
    }

    private final List<BlockPos> roomPositions;
    private final Map<BlockPos, Door> doorsByAnchor;

    private RoomGraph(List<BlockPos> roomPositions, Map<BlockPos, Door> doorsByAnchor) {
        this.roomPositions = roomPositions;
        this.doorsByAnchor = doorsByAnchor;
    }

    public static RoomGraph build(ServerLevel world, DungeonBossSpawnerBlockEntity dbs) {
        List<BlockPos> roomPositions = new ArrayList<>();
        // LinkedHashMap: door iteration order follows discovery order, keeping logs/UI stable.
        Map<BlockPos, Door> doorsByAnchor = new LinkedHashMap<>();

        for (BlockPos roomPos : dbs.getRooms()) {
            if (!(world.getBlockEntity(roomPos) instanceof RoomControllerBlockEntity room)) {
                continue;
            }
            roomPositions.add(roomPos);
            Set<BlockPos> seen = new HashSet<>();
            for (BlockPos anchor : room.getDoorPositions()) {
                if (seen.contains(anchor)) {
                    continue; // two stored anchors of the same group (legacy data)
                }
                Set<BlockPos> group = expandDoorGroup(world, anchor);
                seen.addAll(group);
                BlockPos key = canonicalAnchor(group);
                Door existing = doorsByAnchor.get(key);
                if (existing == null) {
                    List<BlockPos> owners = new ArrayList<>(2);
                    owners.add(roomPos);
                    doorsByAnchor.put(key, new Door(key, group, owners));
                } else if (!existing.rooms().contains(roomPos)) {
                    existing.rooms().add(roomPos);
                    if (existing.rooms().size() > 2) {
                        ArenasLdMod.LOGGER.warn(
                            "RoomGraph: door {} is linked by {} rooms ({}); a door connects at most two",
                            key, existing.rooms().size(), existing.rooms());
                    }
                }
            }
        }
        return new RoomGraph(List.copyOf(roomPositions), doorsByAnchor);
    }

    public List<BlockPos> roomPositions() {
        return roomPositions;
    }

    public java.util.Collection<Door> doors() {
        return doorsByAnchor.values();
    }

    public List<Door> doorsOf(BlockPos roomPos) {
        List<Door> out = new ArrayList<>();
        for (Door door : doorsByAnchor.values()) {
            if (door.rooms().contains(roomPos)) {
                out.add(door);
            }
        }
        return out;
    }

    /**
     * Per-tick crossing-detection lookup: every door block mapped to the one room passing
     * {@code eligible} (lifecycle passes: not activated && not cleared) that owns the door.
     * A door whose owners are both still eligible is skipped — it is closed (neither side has
     * cleared), so it cannot be crossed and mapping it would be an arbitrary pick.
     */
    public Map<BlockPos, BlockPos> buildEntranceDetectionMap(Predicate<BlockPos> eligible) {
        Map<BlockPos, BlockPos> detection = new HashMap<>();
        for (Door door : doorsByAnchor.values()) {
            BlockPos eligibleRoom = null;
            boolean ambiguous = false;
            for (BlockPos roomPos : door.rooms()) {
                if (!eligible.test(roomPos)) {
                    continue;
                }
                if (eligibleRoom != null) {
                    ambiguous = true;
                    break;
                }
                eligibleRoom = roomPos;
            }
            if (eligibleRoom == null || ambiguous) {
                continue;
            }
            for (BlockPos doorBlock : door.blocks()) {
                detection.put(doorBlock, eligibleRoom);
            }
        }
        return detection;
    }

    /**
     * All PhaseBlocks connected to the anchor, mirroring {@link PhaseBlockEntity#propagateState}'s
     * 6-neighbor flood fill, so multi-block doors detect a crossing on any of their blocks.
     * Capped as a backstop against runaway phase-block walls. Takes a plain {@link Level} so the
     * client-side selection overlay can expand door groups from synced block entities too.
     */
    public static Set<BlockPos> expandDoorGroup(Level world, BlockPos anchor) {
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

    /**
     * Deterministic representative of a door group: its lowest (x, y, z) block. Rooms store this
     * anchor instead of the block the builder happened to click, so linking <em>any</em> block of a
     * multi-block door from either side resolves to the same position — builders never have to
     * remember which phase block is the door's "parent".
     */
    public static BlockPos canonicalAnchor(Set<BlockPos> group) {
        return group.stream()
            .min(Comparator.<BlockPos>comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ))
            .orElseThrow();
    }
}
