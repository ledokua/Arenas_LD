package net.ledok.arenas_ld.arena.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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

    @Override
    public InteractionResult useWithoutItem(BlockState blockState, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().getItem() instanceof LinkerItem || player.getOffhandItem().getItem() instanceof LinkerItem) {
            return InteractionResult.PASS;
        }
        if (!world.isClientSide && world.getBlockEntity(pos) instanceof ArenaControllerBlockEntity controller) {
            player.openMenu(controller);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (world.isClientSide) return null;
        return createTickerHelper(type, BlockEntitiesRegistry.ARENA_CONTROLLER_BLOCK_ENTITY, ArenaControllerBlockEntity::tick);
    }
}
