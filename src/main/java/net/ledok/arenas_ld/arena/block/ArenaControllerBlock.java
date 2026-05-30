package net.ledok.arenas_ld.arena.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Arena controller block. Mirrors {@link net.ledok.arenas_ld.raid.block.RaidControllerBlock}: a
 * server-side ticker drives active runs and instance cooldowns. (The player/admin screens are
 * wired in a later phase.)
 */
public class ArenaControllerBlock extends BaseEntityBlock {

    public static final MapCodec<ArenaControllerBlock> CODEC = simpleCodec(ArenaControllerBlock::new);

    public ArenaControllerBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArenaControllerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide) return null;
        return createTickerHelper(type, BlockEntitiesRegistry.ARENA_CONTROLLER_BLOCK_ENTITY, ArenaControllerBlockEntity::tick);
    }
}
