package net.ledok.arenas_ld.arena.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.item.LinkerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Passive arena anchor block. No ticker — the arena controller drives active runs via
 * {@code ArenaRunLifecycle}. Mirrors {@link net.ledok.arenas_ld.raid.block.RaidBossSpawnerBlock}.
 * (The config screen is wired in a later phase.)
 */
public class ArenaSpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<ArenaSpawnerBlock> CODEC = simpleCodec(ArenaSpawnerBlock::new);

    public ArenaSpawnerBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArenaSpawnerBlockEntity(pos, state);
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
        if (!world.isClientSide) {
            if (!player.isCreative() && !player.hasPermissions(2)) {
                return InteractionResult.FAIL;
            }
            if (world.getBlockEntity(pos) instanceof ArenaSpawnerBlockEntity spawner) {
                player.openMenu(spawner);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.SUCCESS;
    }
}
