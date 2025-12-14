package net.ledok.arenas_ld.block.entity;

import net.ledok.YggdrasilLdMod;
import net.ledok.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class PhaseBlockEntity extends BlockEntity {
    private String groupId = "";
    private boolean firstTick = true;

    public PhaseBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.PHASE_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, PhaseBlockEntity be) {
        if (be.firstTick && !world.isClientSide()) {
            YggdrasilLdMod.PHASE_BLOCK_MANAGER.register(be);
            be.firstTick = false;
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            YggdrasilLdMod.PHASE_BLOCK_MANAGER.unregister(this);
        }
        super.setRemoved();
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.groupId = tag.getString("groupId");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("groupId", this.groupId);
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        if (this.level != null && !this.level.isClientSide()) {
            YggdrasilLdMod.PHASE_BLOCK_MANAGER.unregister(this);
        }
        this.groupId = groupId;
        if (this.level != null && !this.level.isClientSide()) {
            YggdrasilLdMod.PHASE_BLOCK_MANAGER.register(this);
        }
        setChanged();
    }
}
