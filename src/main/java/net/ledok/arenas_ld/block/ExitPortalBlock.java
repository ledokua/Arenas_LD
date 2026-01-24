package net.ledok.arenas_ld.block;

import com.mojang.serialization.MapCodec;
import net.ledok.arenas_ld.block.entity.ExitPortalBlockEntity;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ExitPortalBlock extends BaseEntityBlock {

    public static final MapCodec<ExitPortalBlock> CODEC = simpleCodec(ExitPortalBlock::new);
    private final Map<UUID, Long> playerCooldowns = new HashMap<>();
    private static final int COOLDOWN_TICKS = 100;

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
        if (!world.isClientSide() && entity instanceof ServerPlayer player) {
            long currentTime = world.getGameTime();
            long cooldownEndTime = playerCooldowns.getOrDefault(player.getUUID(), 0L);

            if (player.canUsePortal(true) && currentTime >= cooldownEndTime) {
                if (world.getBlockEntity(pos) instanceof ExitPortalBlockEntity portalEntity) {
                    BlockPos destination = portalEntity.getDestination();
                    if (destination != null && world instanceof ServerLevel serverLevel) {
                        player.teleportTo(destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5);
                        player.setPortalCooldown();
                        playerCooldowns.put(player.getUUID(), currentTime + COOLDOWN_TICKS);
                    }
                }
            }
        }
        if (!world.isClientSide() && world.getGameTime() % 1200 == 0) { // Every minute
            long currentTime = world.getGameTime();
            playerCooldowns.entrySet().removeIf(entry -> currentTime >= entry.getValue() + (COOLDOWN_TICKS * 10));
        }
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, BlockEntitiesRegistry.EXIT_PORTAL_BLOCK_ENTITY.get(), ExitPortalBlockEntity::tick);
    }
}
