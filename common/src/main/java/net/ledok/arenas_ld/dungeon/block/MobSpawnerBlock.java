package net.ledok.arenas_ld.dungeon.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.item.LinkerItem;
import net.minecraft.ChatFormatting;
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

public class MobSpawnerBlock extends BaseEntityBlock {
    public static final MapCodec<MobSpawnerBlock> CODEC = simpleCodec(MobSpawnerBlock::new);

    public MobSpawnerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobSpawnerBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().getItem() instanceof LinkerItem || player.getOffhandItem().getItem() instanceof LinkerItem) {
            return InteractionResult.PASS;
        }
        if (!world.isClientSide) {
            if (!player.isCreative() && !player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission").withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof MobSpawnerBlockEntity mobSpawner) {
                player.openMenu(mobSpawner);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.SUCCESS;
    }
}
