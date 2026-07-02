package net.ledok.arenas_ld.raid.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class RaidBossSpawnerBlock extends BaseEntityBlock {

    public static final MapCodec<RaidBossSpawnerBlock> CODEC = simpleCodec(RaidBossSpawnerBlock::new);

    public RaidBossSpawnerBlock(Properties settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RaidBossSpawnerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState blockState, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (isHoldingLinker(player)) {
            return InteractionResult.PASS;
        }
        if (!world.isClientSide) {
            if (!player.isCreative() && !player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("You don't have permission to configure this block.").withStyle(net.minecraft.ChatFormatting.RED));
                return InteractionResult.FAIL;
            }

            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof RaidBossSpawnerBlockEntity) {
                player.openMenu((RaidBossSpawnerBlockEntity) blockEntity);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isHoldingLinker(Player player) {
        return player.getMainHandItem().getItem() instanceof LinkerItem
                || player.getOffhandItem().getItem() instanceof LinkerItem;
    }

    // No ticker: the raid controller drives active runs via RaidRunLifecycle.
    // This spawner is a passive arena/config holder.
}
