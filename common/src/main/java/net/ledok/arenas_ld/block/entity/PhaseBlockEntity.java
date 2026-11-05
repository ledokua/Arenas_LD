package net.ledok.arenas_ld.block.entity;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

public class PhaseBlockEntity extends BlockEntity {
    /**
     * Loaded legacy raid doors: main blocks made by the old Linker (before May 2026), which close
     * while a linked raid runs. Phase blocks have no ticker, so these few are ticked from here.
     */
    private static final Set<PhaseBlockEntity> LEGACY_MAINS = Collections.newSetFromMap(new WeakHashMap<>());

    private boolean isMain = false;
    private final List<BlockPos> watchedSpawnerOffsets = new ArrayList<>();
    private int checkTick = 0;

    public PhaseBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.PHASE_BLOCK_ENTITY, pos, state);
    }

    private void markDirty() {
        super.setChanged();
    }

    private void markDirtyAndSync() {
        markDirty();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Server tick end: runs the legacy raid-door check of every loaded main block. */
    public static void tickLegacyMains(MinecraftServer server) {
        if (LEGACY_MAINS.isEmpty()) {
            return;
        }
        for (PhaseBlockEntity be : List.copyOf(LEGACY_MAINS)) {
            Level world = be.getLevel();
            if (be.isRemoved() || world == null || !be.isMain) {
                LEGACY_MAINS.remove(be);
                continue;
            }
            BlockState state = world.getBlockState(be.getBlockPos());
            if (state.getBlock() instanceof PhaseBlock) {
                tick(world, be.getBlockPos(), state, be);
            }
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        trackIfMain();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LEGACY_MAINS.remove(this);
    }

    private void trackIfMain() {
        if (isMain && level != null && !level.isClientSide()) {
            LEGACY_MAINS.add(this);
        } else {
            LEGACY_MAINS.remove(this);
        }
    }

    private static void tick(Level world, BlockPos pos, BlockState state, PhaseBlockEntity be) {
        if (world.isClientSide() || !be.isMain) {
            return;
        }

        if (++be.checkTick < 20) {
            return;
        }
        be.checkTick = 0;

        boolean allSpawnersWon = be.checkSpawnerConditions();
        boolean shouldBeSolid = !allSpawnersWon;

        if (state.getValue(PhaseBlock.SOLID) != shouldBeSolid) {
            // Ticked again every second, so loaded blocks only: a part beyond an unloaded chunk is
            // caught up on a later check once it loads.
            applyToDoor(world, pos, shouldBeSolid, false, false);
        }
    }

    private boolean checkSpawnerConditions() {
        if (watchedSpawnerOffsets.isEmpty()) {
            return false; // If no spawners are linked, it can never be "won"
        }
        for (BlockPos relativePos : watchedSpawnerOffsets) {
            BlockPos spawnerPos = getBlockPos().offset(relativePos);
            if (!level.isLoaded(spawnerPos)) {
                // If a spawner is missing or not a spawner, treat it as not won
                return false;
            }
            BlockEntity spawnerEntity = level.getBlockEntity(spawnerPos);
            if (spawnerEntity instanceof RaidBossSpawnerBlockEntity bossSpawner) {
                if (bossSpawner.isRaidRunning()) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true; // All watched spawners are cleared.
    }

    /** Backstop against runaway phase-block walls; a real doorway is far smaller. */
    public static final int MAX_DOOR_BLOCKS = 1024;

    /**
     * Every Phase Block face-connected to {@code start} (empty when {@code start} isn't one).
     *
     * @param loadChunks load chunks the door reaches into. Server-driven door changes need the
     *                   whole door: a part in an unloaded chunk would silently keep its old state.
     *                   Only for trusted callers (run logic, tools, admin commands) — never for a
     *                   position a client sent. With {@code false} the fill stops at unloaded chunks.
     */
    public static Set<BlockPos> connectedGroup(Level level, BlockPos start, boolean loadChunks) {
        Set<BlockPos> group = new HashSet<>();
        if (!isPhaseBlock(level, start, loadChunks)) {
            return group;
        }
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        group.add(start.immutable());
        queue.add(start.immutable());
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                if (group.size() >= MAX_DOOR_BLOCKS) {
                    return group;
                }
                BlockPos neighbor = pos.relative(direction);
                if (!group.contains(neighbor) && isPhaseBlock(level, neighbor, loadChunks)) {
                    group.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return group;
    }

    private static boolean isPhaseBlock(Level level, BlockPos pos, boolean loadChunks) {
        if (!loadChunks && !level.isLoaded(pos)) {
            return false;
        }
        return level.getBlockState(pos).getBlock() instanceof PhaseBlock;
    }

    /**
     * Puts a whole door (the group connected to {@code anyBlock}) into the given state. Every block
     * is checked, not only the ones next to a block that changed: a door left partly in another
     * state (an earlier change that couldn't reach an unloaded part) heals on the next change.
     *
     * @return how many blocks changed
     */
    public static int applyToDoor(Level level, BlockPos anyBlock, boolean solid, boolean armed, boolean loadChunks) {
        int changed = 0;
        for (BlockPos pos : connectedGroup(level, anyBlock, loadChunks)) {
            BlockState state = level.getBlockState(pos);
            if (state.getValue(PhaseBlock.SOLID) != solid || state.getValue(PhaseBlock.ARMED) != armed) {
                level.setBlock(pos, state.setValue(PhaseBlock.SOLID, solid).setValue(PhaseBlock.ARMED, armed), 3);
                changed++;
            }
        }
        return changed;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.isMain = tag.getBoolean("IsMain");
        this.watchedSpawnerOffsets.clear();
        if (tag.contains("WatchedSpawnerOffsets", Tag.TAG_LONG_ARRAY)) {
            long[] offsetsArray = tag.getLongArray("WatchedSpawnerOffsets");
            for (long posLong : offsetsArray) {
                watchedSpawnerOffsets.add(BlockPos.of(posLong));
            }
        }
        trackIfMain();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("IsMain", this.isMain);
        tag.putLongArray("WatchedSpawnerOffsets", watchedSpawnerOffsets.stream().mapToLong(BlockPos::asLong).toArray());
    }

    public void setIsMain(boolean isMain) {
        this.isMain = isMain;
        trackIfMain();
        markDirtyAndSync();
    }

    public void addWatchedSpawnerOffset(BlockPos relativePos) {
        if (!this.watchedSpawnerOffsets.contains(relativePos)) {
            this.watchedSpawnerOffsets.add(relativePos);
            markDirtyAndSync();
        }
    }

    public boolean removeWatchedSpawnerOffset(BlockPos relativePos) {
        if (this.watchedSpawnerOffsets.remove(relativePos)) {
            markDirtyAndSync();
            return true;
        }
        return false;
    }

    public List<BlockPos> getWatchedSpawnerOffsets() {
        return Collections.unmodifiableList(watchedSpawnerOffsets);
    }
}
