package net.ledok.arenas_ld.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class DungeonBossSpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<DungeonBossSpawnerBlock> CODEC = simpleCodec(DungeonBossSpawnerBlock::new);

    public DungeonBossSpawnerBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DungeonBossSpawnerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof DungeonBossSpawnerBlockEntity spawner) {
                spawner.placedByPlayer = true;
                spawner.setChanged();
            }
        }
    }

    @Override
    public InteractionResult useWithoutItem(BlockState blockState, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (!world.isClientSide) {
            if (!player.isCreative() && !player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("You don't have permission to configure this block.").withStyle(net.minecraft.ChatFormatting.RED));
                return InteractionResult.FAIL;
            }

            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof DungeonBossSpawnerBlockEntity) {
                player.openMenu((DungeonBossSpawnerBlockEntity) blockEntity);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntitiesRegistry.DUNGEON_BOSS_SPAWNER_BLOCK_ENTITY, DungeonBossSpawnerBlockEntity::tick);
    }
}
