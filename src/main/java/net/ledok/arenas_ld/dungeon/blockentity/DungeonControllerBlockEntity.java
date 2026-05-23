package net.ledok.arenas_ld.dungeon.blockentity;

import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DungeonControllerBlockEntity extends BlockEntity {
    public DungeonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_CONTROLLER_V2_BLOCK_ENTITY, pos, state);
    }
}
