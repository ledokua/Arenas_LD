package net.ledok.arenas_ld.block.entity;

import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class PhaseBlockEntity extends BlockEntity {
    private boolean isMain = false;
    private final List<BlockPos> linkedSpawners = new ArrayList<>();
    private boolean lastKnownState = true; // Default to solid

    public PhaseBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.PHASE_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, PhaseBlockEntity be) {
        if (world.isClientSide() || !be.isMain) {
            return;
        }

        boolean allSpawnersWon = be.checkSpawnerConditions();
        boolean shouldBeSolid = !allSpawnersWon;

        if (state.getValue(PhaseBlock.SOLID) != shouldBeSolid) {
            world.setBlock(pos, state.setValue(PhaseBlock.SOLID, shouldBeSolid), 3);
            be.propagateState(shouldBeSolid, new ArrayList<>());
        }
    }

    private boolean checkSpawnerConditions() {
        if (linkedSpawners.isEmpty()) {
            return false; // If no spawners are linked, it can never be "won"
        }
        for (BlockPos relativePos : linkedSpawners) {
            BlockPos spawnerPos = getBlockPos().offset(relativePos);
            if (level.isLoaded(spawnerPos) && level.getBlockEntity(spawnerPos) instanceof MobSpawnerBlockEntity spawner) {
                // If the battle is active OR the spawner is ready to spawn (cooldown <= 0), then the condition is not met.
                if (spawner.isBattleActive() || spawner.getRespawnCooldown() <= 0) {
                    return false; // At least one spawner is active or ready, so the group is not "won"
                }
            } else {
                // If a spawner is missing or not a spawner, treat it as not won
                return false;
            }
        }
        return true; // All spawners are on cooldown and not active
    }

    public void propagateState(boolean solid, List<BlockPos> visited) {
        if (visited.contains(getBlockPos())) {
            return;
        }
        visited.add(getBlockPos());

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = getBlockPos().relative(direction, 1);
            if (level.isLoaded(neighborPos) && level.getBlockEntity(neighborPos) instanceof PhaseBlockEntity neighbor) {
                BlockState neighborState = level.getBlockState(neighborPos);
                if (neighborState.getValue(PhaseBlock.SOLID) != solid) {
                    level.setBlock(neighborPos, neighborState.setValue(PhaseBlock.SOLID, solid), 3);
                    neighbor.propagateState(solid, visited);
                }
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.isMain = tag.getBoolean("IsMain");
        this.linkedSpawners.clear();
        if (tag.contains("LinkedSpawners", Tag.TAG_LONG_ARRAY)) {
            long[] linkedSpawnersArray = tag.getLongArray("LinkedSpawners");
            for (long posLong : linkedSpawnersArray) {
                linkedSpawners.add(BlockPos.of(posLong));
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("IsMain", this.isMain);
        tag.putLongArray("LinkedSpawners", linkedSpawners.stream().mapToLong(BlockPos::asLong).toArray());
    }

    public void setIsMain(boolean isMain) {
        this.isMain = isMain;
        setChanged();
    }

    public void addLinkedSpawner(BlockPos relativePos) {
        if (!this.linkedSpawners.contains(relativePos)) {
            this.linkedSpawners.add(relativePos);
            setChanged();
        }
    }
}
