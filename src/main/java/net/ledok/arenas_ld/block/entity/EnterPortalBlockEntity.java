package net.ledok.arenas_ld.block.entity;

import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class EnterPortalBlockEntity extends BlockEntity {

    private BlockPos destination;
    private BlockPos ownerPos;

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

    public void setOwner(BlockPos ownerPos) {
        this.ownerPos = ownerPos;
        this.setChanged();
    }

    public BlockPos getOwner() {
        return this.ownerPos;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        if(destination != null) {
            nbt.putLong("destination", destination.asLong());
        }
        if(ownerPos != null) {
            nbt.putLong("ownerPos", ownerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if(nbt.contains("destination")) {
            destination = BlockPos.of(nbt.getLong("destination"));
        }
        if(nbt.contains("ownerPos")) {
            ownerPos = BlockPos.of(nbt.getLong("ownerPos"));
        }
    }
}
