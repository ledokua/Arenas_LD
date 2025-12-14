package net.ledok.arenas_ld.block;

import com.mojang.serialization.MapCodec;
import net.ledok.block.entity.BossSpawnerBlockEntity;
import net.ledok.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

public class BossSpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<BossSpawnerBlock> CODEC = simpleCodec(BossSpawnerBlock::new);

    public BossSpawnerBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BossSpawnerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState blockState, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!world.isClientSide) {
            if (!player.isCreative() && !player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("You don't have permission to configure this block.").withStyle(net.minecraft.ChatFormatting.RED));
                return InteractionResult.FAIL;
            }

            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof BossSpawnerBlockEntity) {
                player.openMenu((BossSpawnerBlockEntity) blockEntity);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntitiesRegistry.BOSS_SPAWNER_BLOCK_ENTITY, BossSpawnerBlockEntity::tick);
    }
}

