package net.ledok.arenas_ld.dungeon.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.item.LinkerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
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

public class RoomControllerBlock extends BaseEntityBlock {

    public static final MapCodec<RoomControllerBlock> CODEC = simpleCodec(RoomControllerBlock::new);

    public RoomControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoomControllerBlockEntity(pos, state);
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
                player.sendSystemMessage(Component.literal("You don't have permission to configure this block.")
                    .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof RoomControllerBlockEntity room) {
                player.openMenu(room);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.SUCCESS;
    }
}
