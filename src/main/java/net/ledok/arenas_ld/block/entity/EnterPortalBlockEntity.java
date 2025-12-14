package net.ledok.arenas_ld.block.entity;

import net.ledok.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EnterPortalBlockEntity extends BlockEntity {

    private BlockPos destination;

    public EnterPortalBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.ENTER_PORTAL_BLOCK_ENTITY, pos, state);
    }

    public void setDestination(BlockPos destination) {
        this.destination = destination;
        this.setChanged();
    }

    public BlockPos getDestination() {
        return this.destination;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        if(destination != null) {
            nbt.putLong("destination", destination.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if(nbt.contains("destination")) {
            destination = BlockPos.of(nbt.getLong("destination"));
        }
    }
}
