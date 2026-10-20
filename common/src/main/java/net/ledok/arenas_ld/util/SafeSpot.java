package net.ledok.arenas_ld.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a player can be put down: feet and head free, solid ground under them, and no liquid, fire,
 * lava, magma, powder snow, portal or other harmful block at the feet, head or ground (vanilla's bed and
 * respawn anchor stand-up check, plus liquids). Reads blocks, so it may load the chunks it looks at:
 * for one-off admin actions, never for anything that runs every tick.
 */
public final class SafeSpot {
    /** How far out (in blocks, a square ring) {@link #near} looks around its target. */
    private static final int MAX_RING = 3;
    /** How far above its target {@link #near} looks last. */
    private static final int MAX_UP = 6;
    /** Each ring at the target's own height first, then one up (a block set into the floor), then one down. */
    private static final int[] LAYERS = {0, 1, -1};

    private SafeSpot() {
    }

    /**
     * A player put here (feet in this block) wouldn't be stuck in a block. Something low at the feet
     * (a carpet, a slab, snow) only lifts them, so up to half a block there is fine.
     */
    public static boolean fits(BlockGetter level, BlockPos feet) {
        BlockPos head = feet.above();
        VoxelShape feetShape = level.getBlockState(feet).getCollisionShape(level, feet);
        return (feetShape.isEmpty() || feetShape.max(Direction.Axis.Y) <= 0.5)
            && level.getBlockState(head).getCollisionShape(level, head).isEmpty();
    }

    /**
     * The closest safe spot next to {@code target}, as the point a player's feet stand on (the block
     * center, lifted by a slab or carpet there); null when there is none. Looks at the 4 sides, then
     * the diagonals, at the target's height, one up and one down; then the same 2 and 3 blocks out;
     * then on top of the target and straight up from it.
     */
    public static @Nullable Vec3 near(ServerLevel level, BlockPos target) {
        for (int ring = 1; ring <= MAX_RING; ring++) {
            List<int[]> cells = ring(ring);
            for (int dy : LAYERS) {
                for (int[] cell : cells) {
                    Vec3 spot = standAt(level, target.offset(cell[0], dy, cell[1]));
                    if (spot != null) {
                        return spot;
                    }
                }
            }
        }
        for (int up = 1; up <= MAX_UP; up++) {
            Vec3 spot = standAt(level, target.above(up));
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }

    /** Where a player with feet in this block would stand, or null when it isn't safe. */
    public static @Nullable Vec3 standAt(ServerLevel level, BlockPos feet) {
        if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(feet.above()) || !fits(level, feet)) {
            return null;
        }
        for (BlockPos pos : new BlockPos[] {feet, feet.above(), feet.below()}) {
            BlockState state = level.getBlockState(pos);
            if (!level.getFluidState(pos).isEmpty() || EntityType.PLAYER.isBlockDangerous(state)
                || state.is(BlockTags.PORTALS) || state.is(BlockTags.INVALID_SPAWN_INSIDE)) {
                // water, lava, fire, magma, powder snow, cactus, berry bushes, wither roses; portals,
                // which have no collision but would send the player on
                return null;
            }
        }
        // Ground under the feet (or a low block at them), the whole player box free, inside the
        // world border.
        return DismountHelper.findSafeDismountLocation(EntityType.PLAYER, level, feet, true);
    }

    /**
     * The (dx, dz) cells of the square ring {@code radius} out, closest first, ties clockwise from
     * north: for radius 1 the 4 sides, then the 4 diagonals.
     */
    static List<int[]> ring(int radius) {
        List<int[]> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == radius) {
                    cells.add(new int[] {dx, dz});
                }
            }
        }
        cells.sort(Comparator.<int[]>comparingInt(c -> c[0] * c[0] + c[1] * c[1])
            .thenComparingDouble(SafeSpot::clockwiseFromNorth));
        return cells;
    }

    private static double clockwiseFromNorth(int[] cell) {
        double angle = Math.atan2(cell[0], -cell[1]); // north (-z) is 0, east (+x) a quarter turn
        return angle < 0 ? angle + 2 * Math.PI : angle;
    }
}
