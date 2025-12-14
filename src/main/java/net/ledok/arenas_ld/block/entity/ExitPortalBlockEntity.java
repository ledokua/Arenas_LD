package net.ledok.arenas_ld.block.entity;

import net.ledok.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExitPortalBlockEntity extends BlockEntity {

    private int lifetime;
    private BlockPos destination;

    public ExitPortalBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.EXIT_PORTAL_BLOCK_ENTITY, pos, state);
    }

    public void setDetails(int lifetime, BlockPos destination) {
        this.lifetime = lifetime;
        this.destination = destination;
        this.setChanged();
    }

    public static void tick(Level world, BlockPos pos, BlockState state, ExitPortalBlockEntity be) {
        if(world.isClientSide()) return;

        be.lifetime--;
        if (be.lifetime <= 0) {
            world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public BlockPos getDestination() {
        return this.destination;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putInt("lifetime", lifetime);
        if(destination != null) {
            nbt.putInt("destX", destination.getX());
            nbt.putInt("destY", destination.getY());
            nbt.putInt("destZ", destination.getZ());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        lifetime = nbt.getInt("lifetime");
        if(nbt.contains("destX")) {
            destination = new BlockPos(nbt.getInt("destX"), nbt.getInt("destY"), nbt.getInt("destZ"));
        }
    }
}
