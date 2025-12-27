package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.block.BossSpawnerBlock;
import net.ledok.arenas_ld.block.DungeonBossSpawnerBlock;
import net.ledok.arenas_ld.block.MobSpawnerBlock;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.PhaseBlockEntity;
import net.ledok.arenas_ld.util.LinkerDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LinkerItem extends Item {
    public LinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        Player player = context.getPlayer();

        if (world.isClientSide || player == null) {
            return InteractionResult.PASS;
        }

        if (!player.isCreative() && !player.hasPermissions(2)) {
            return InteractionResult.PASS;
        }

        BlockEntity blockEntity = world.getBlockEntity(pos);
        boolean isShiftDown = player.isShiftKeyDown();

        if (state.getBlock() instanceof MobSpawnerBlock && blockEntity instanceof MobSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                // Copy GroupID
                String groupId = spawner.groupId;
                context.getItemInHand().set(LinkerDataComponent.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.literal("Copied GroupID: " + groupId));
                return InteractionResult.SUCCESS;
            } else {
                // Paste GroupID
                LinkerDataComponent data = context.getItemInHand().get(LinkerDataComponent.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.literal("Pasted GroupID: " + data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (state.getBlock() instanceof PhaseBlock && blockEntity instanceof PhaseBlockEntity phaseBlock) {
            if (isShiftDown) {
                // Copy GroupID
                String groupId = phaseBlock.getGroupId();
                context.getItemInHand().set(LinkerDataComponent.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.literal("Copied GroupID: " + groupId));
                return InteractionResult.SUCCESS;
            } else {
                // Paste GroupID
                LinkerDataComponent data = context.getItemInHand().get(LinkerDataComponent.LINKER_DATA);
                if (data != null) {
                    phaseBlock.setGroupId(data.groupId());
                    player.sendSystemMessage(Component.literal("Pasted GroupID: " + data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (state.getBlock() instanceof BossSpawnerBlock && blockEntity instanceof BossSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                // Copy GroupID
                String groupId = spawner.groupId;
                context.getItemInHand().set(LinkerDataComponent.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.literal("Copied GroupID: " + groupId));
                return InteractionResult.SUCCESS;
            } else {
                // Paste GroupID
                LinkerDataComponent data = context.getItemInHand().get(LinkerDataComponent.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.literal("Pasted GroupID: " + data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (state.getBlock() instanceof DungeonBossSpawnerBlock && blockEntity instanceof DungeonBossSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                // Copy GroupID
                String groupId = spawner.groupId;
                context.getItemInHand().set(LinkerDataComponent.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.literal("Copied GroupID: " + groupId));
                return InteractionResult.SUCCESS;
            } else {
                // Paste GroupID
                LinkerDataComponent data = context.getItemInHand().get(LinkerDataComponent.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.literal("Pasted GroupID: " + data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        }


        return super.useOn(context);
    }
}
