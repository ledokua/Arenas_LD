package net.ledok.arenas_ld.block.entity;

import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RespawnPointBlockEntity extends BlockEntity {
    public RespawnPointBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.RESPAWN_POINT_BLOCK_ENTITY, pos, state);
    }
}
