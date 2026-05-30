package net.ledok.arenas_ld.arena.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
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
}
