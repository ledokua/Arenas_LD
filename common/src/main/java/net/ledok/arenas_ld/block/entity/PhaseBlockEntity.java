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

import java.util.ArrayList;
import java.util.Collections;
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
            world.setBlock(pos, state.setValue(PhaseBlock.SOLID, shouldBeSolid).setValue(PhaseBlock.ARMED, false), 3);
            be.propagateState(shouldBeSolid, false, new ArrayList<>());
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

    public void propagateState(boolean solid, List<BlockPos> visited) {
        propagateState(solid, false, visited);
    }

    public void propagateState(boolean solid, boolean armed, List<BlockPos> visited) {
        if (visited.contains(getBlockPos())) {
            return;
        }
        visited.add(getBlockPos());

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(direction, 1);
            if (level.isLoaded(neighborPos) && level.getBlockEntity(neighborPos) instanceof PhaseBlockEntity neighbor) {
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getValue(PhaseBlock.SOLID) != solid
                        || neighborState.getValue(PhaseBlock.ARMED) != armed) {
                    level.setBlock(neighborPos,
                        neighborState.setValue(PhaseBlock.SOLID, solid).setValue(PhaseBlock.ARMED, armed), 3);
                    neighbor.propagateState(solid, armed, visited);
                }
            }
        }
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
