package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.block.entity.*;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.LinkableSpawner;
import net.ledok.arenas_ld.util.LinkerDataComponent;
import net.ledok.arenas_ld.util.LinkerModeDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class LinkerItem extends Item {
    public LinkerItem(Properties properties) {
        super(properties);
    }

    public enum Mode {
        GROUP_CONFIG("item.arenas_ld.linker.mode.group_config"),
        SPAWNER_LINKING("item.arenas_ld.linker.mode.spawner_linking"),
        PHASE_BLOCK_LINKING("item.arenas_ld.linker.mode.phase_block_linking");

        private final String translationKey;

        Mode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getName() {
            return Component.translatable(translationKey);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = world.getBlockState(pos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (world.isClientSide || player == null) {
            return InteractionResult.PASS;
        }

        if (!player.isCreative() && !player.hasPermissions(2)) {
            return InteractionResult.PASS;
        }

        LinkerModeDataComponent modeData = stack.getOrDefault(DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
        Mode currentMode = Mode.values()[modeData.mode()];
        boolean isShiftDown = player.isShiftKeyDown();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (currentMode == Mode.GROUP_CONFIG) {
            return handleGroupConfig(world, pos, state, player, stack, blockEntity, isShiftDown);
        } else if (currentMode == Mode.SPAWNER_LINKING) {
            return handleSpawnerLinking(world, pos, player, stack, blockEntity, isShiftDown, modeData);
        } else if (currentMode == Mode.PHASE_BLOCK_LINKING) {
            return handlePhaseBlockLinking(world, pos, player, stack, blockEntity, isShiftDown, modeData);
        }

        return super.useOn(context);
    }

    private InteractionResult handleGroupConfig(Level world, BlockPos pos, BlockState state, Player player, ItemStack stack, BlockEntity blockEntity, boolean isShiftDown) {
        if (blockEntity instanceof MobSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                String groupId = spawner.groupId;
                stack.set(DataComponentRegistry.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.copied_group_id", groupId));
                return InteractionResult.SUCCESS;
            } else {
                LinkerDataComponent data = stack.get(DataComponentRegistry.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.pasted_group_id", data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (blockEntity instanceof BossSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                String groupId = spawner.groupId;
                stack.set(DataComponentRegistry.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.copied_group_id", groupId));
                return InteractionResult.SUCCESS;
            } else {
                LinkerDataComponent data = stack.get(DataComponentRegistry.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.pasted_group_id", data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (blockEntity instanceof DungeonBossSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                String groupId = spawner.groupId;
                stack.set(DataComponentRegistry.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.copied_group_id", groupId));
                return InteractionResult.SUCCESS;
            } else {
                LinkerDataComponent data = stack.get(DataComponentRegistry.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.pasted_group_id", data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    private InteractionResult handleSpawnerLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, boolean isShiftDown, LinkerModeDataComponent modeData) {
        if (isShiftDown && blockEntity instanceof LinkableSpawner) {
            Optional<BlockPos> mainPosOpt = modeData.mainSpawnerPos();

            if (mainPosOpt.isEmpty()) {
                // Set Main Spawner
                stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.of(pos)));
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_spawner", pos.toShortString()));
                return InteractionResult.SUCCESS;
            } else {
                BlockPos mainPos = mainPosOpt.get();
                if (mainPos.equals(pos)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.cannot_link_self"));
                    return InteractionResult.FAIL;
                }

                BlockEntity mainBe = world.getBlockEntity(mainPos);
                if (mainBe instanceof LinkableSpawner mainSpawner) {
                    BlockPos relativePos = pos.subtract(mainPos);
                    mainSpawner.addLinkedSpawner(relativePos);
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.linked_spawner", pos.toShortString(), mainPos.toShortString()));
                    return InteractionResult.SUCCESS;
                } else {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_spawner_invalid"));
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty()));
                    return InteractionResult.FAIL;
                }
            }
        }
        return InteractionResult.PASS;
    }

    private InteractionResult handlePhaseBlockLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, boolean isShiftDown, LinkerModeDataComponent modeData) {
        if (!isShiftDown) return InteractionResult.PASS;

        Optional<BlockPos> mainPosOpt = modeData.mainSpawnerPos();

        if (blockEntity instanceof PhaseBlockEntity phaseBlock) {
            if (mainPosOpt.isEmpty()) {
                // Set Main Phase Block
                phaseBlock.setIsMain(true);
                stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.of(pos)));
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_phase_block", pos.toShortString()));
                return InteractionResult.SUCCESS;
            }
        }

        if (blockEntity instanceof MobSpawnerBlockEntity || blockEntity instanceof BossSpawnerBlockEntity || blockEntity instanceof DungeonBossSpawnerBlockEntity) {
            if (mainPosOpt.isPresent()) {
                BlockPos mainPos = mainPosOpt.get();
                BlockEntity mainBe = world.getBlockEntity(mainPos);
                if (mainBe instanceof PhaseBlockEntity mainPhaseBlock) {
                    BlockPos relativePos = pos.subtract(mainPos);
                    mainPhaseBlock.addLinkedSpawner(relativePos);
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.linked_spawner_to_phase_block", pos.toShortString(), mainPos.toShortString()));
                    return InteractionResult.SUCCESS;
                } else {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_phase_block_invalid"));
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty()));
                    return InteractionResult.FAIL;
                }
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!world.isClientSide && player.isShiftKeyDown()) {
            BlockHitResult hitResult = getPlayerPOVHitResult(world, player, ClipContext.Fluid.NONE);
            if (hitResult.getType() == BlockHitResult.Type.MISS) {
                LinkerModeDataComponent modeData = stack.getOrDefault(DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);

                if (Mode.values()[modeData.mode()] == Mode.SPAWNER_LINKING || Mode.values()[modeData.mode()] == Mode.PHASE_BLOCK_LINKING) {
                    // Clear Main Spawner/Phase Block selection
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty()));
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.cleared_selection"));
                    return InteractionResultHolder.success(stack);
                }
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        LinkerModeDataComponent modeData = stack.getOrDefault(DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
        Mode currentMode = Mode.values()[modeData.mode()];
        
        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.mode", currentMode.getName()).withStyle(net.minecraft.ChatFormatting.GRAY));
        
        if (currentMode == Mode.SPAWNER_LINKING && modeData.mainSpawnerPos().isPresent()) {
            tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.main_spawner", modeData.mainSpawnerPos().get().toShortString()).withStyle(net.minecraft.ChatFormatting.GOLD));
        } else if (currentMode == Mode.PHASE_BLOCK_LINKING && modeData.mainSpawnerPos().isPresent()) {
            tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.main_phase_block", modeData.mainSpawnerPos().get().toShortString()).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        
        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.shift_scroll").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
