package net.ledok.arenas_ld.block.entity;

import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ExitPortalBlockEntity extends BlockEntity {

    private int lifetime;
    private BlockPos destination;
    private ResourceKey<Level> destinationDimension = Level.OVERWORLD; // Default to overworld

    public ExitPortalBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.EXIT_PORTAL_BLOCK_ENTITY, pos, state);
    }

    public void setDetails(int lifetime, BlockPos destination, ResourceKey<Level> destinationDimension) {
        this.lifetime = lifetime;
        this.destination = destination;
        this.destinationDimension = destinationDimension;
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

    public ResourceKey<Level> getDestinationDimension() {
        return this.destinationDimension;
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
        nbt.putString("destinationDimension", destinationDimension.location().toString());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        lifetime = nbt.getInt("lifetime");
        if(nbt.contains("destX")) {
            destination = new BlockPos(nbt.getInt("destX"), nbt.getInt("destY"), nbt.getInt("destZ"));
        }
        if (nbt.contains("destinationDimension")) {
            destinationDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("destinationDimension")));
        }
    }
}
