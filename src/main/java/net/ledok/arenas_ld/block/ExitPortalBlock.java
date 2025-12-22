package net.ledok.arenas_ld.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.block.entity.ExitPortalBlockEntity;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class ExitPortalBlock extends BaseEntityBlock {

    public static final MapCodec<ExitPortalBlock> CODEC = simpleCodec(ExitPortalBlock::new);

    public ExitPortalBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ExitPortalBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        if (!world.isClientSide() && entity instanceof Player player) {
            if (player.canUsePortal(true)) {
                if (world.getBlockEntity(pos) instanceof ExitPortalBlockEntity portalEntity) {
                    BlockPos destination = portalEntity.getDestination();
                    if (destination != null && world instanceof ServerLevel serverLevel) {
                        player.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
                    }
                }
            }
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntitiesRegistry.EXIT_PORTAL_BLOCK_ENTITY, ExitPortalBlockEntity::tick);
    }
}
