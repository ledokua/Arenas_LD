package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.block.BossSpawnerBlock;
import net.ledok.arenas_ld.block.DungeonBossSpawnerBlock;
import net.ledok.arenas_ld.block.MobSpawnerBlock;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.PhaseBlockEntity;
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
        GROUP_CONFIG("Group Configuration"),
        SPAWNER_LINKING("Spawner Linking");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
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

        LinkerModeDataComponent modeData = stack.getOrDefault(LinkerModeDataComponent.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
        Mode currentMode = Mode.values()[modeData.mode()];
        boolean isShiftDown = player.isShiftKeyDown();
        BlockEntity blockEntity = world.getBlockEntity(pos);

        if (currentMode == Mode.GROUP_CONFIG) {
            return handleGroupConfig(world, pos, state, player, stack, blockEntity, isShiftDown);
        } else if (currentMode == Mode.SPAWNER_LINKING) {
            return handleSpawnerLinking(world, pos, player, stack, blockEntity, isShiftDown, modeData);
        }

        return super.useOn(context);
    }

    private InteractionResult handleGroupConfig(Level world, BlockPos pos, BlockState state, Player player, ItemStack stack, BlockEntity blockEntity, boolean isShiftDown) {
        if (blockEntity instanceof MobSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                String groupId = spawner.groupId;
                stack.set(LinkerDataComponent.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.literal("Copied GroupID: " + groupId));
                return InteractionResult.SUCCESS;
            } else {
                LinkerDataComponent data = stack.get(LinkerDataComponent.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.literal("Pasted GroupID: " + data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (blockEntity instanceof PhaseBlockEntity phaseBlock) {
            if (isShiftDown) {
                String groupId = phaseBlock.getGroupId();
                stack.set(LinkerDataComponent.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.literal("Copied GroupID: " + groupId));
                return InteractionResult.SUCCESS;
            } else {
                LinkerDataComponent data = stack.get(LinkerDataComponent.LINKER_DATA);
                if (data != null) {
                    phaseBlock.setGroupId(data.groupId());
                    player.sendSystemMessage(Component.literal("Pasted GroupID: " + data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (blockEntity instanceof BossSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                String groupId = spawner.groupId;
                stack.set(LinkerDataComponent.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.literal("Copied GroupID: " + groupId));
                return InteractionResult.SUCCESS;
            } else {
                LinkerDataComponent data = stack.get(LinkerDataComponent.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.literal("Pasted GroupID: " + data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        } else if (blockEntity instanceof DungeonBossSpawnerBlockEntity spawner) {
            if (isShiftDown) {
                String groupId = spawner.groupId;
                stack.set(LinkerDataComponent.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.literal("Copied GroupID: " + groupId));
                return InteractionResult.SUCCESS;
            } else {
                LinkerDataComponent data = stack.get(LinkerDataComponent.LINKER_DATA);
                if (data != null) {
                    spawner.groupId = data.groupId();
                    spawner.setChanged();
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.literal("Pasted GroupID: " + data.groupId()));
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
                stack.set(LinkerModeDataComponent.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.of(pos)));
                player.sendSystemMessage(Component.literal("Set Main Spawner at " + pos.toShortString()));
                return InteractionResult.SUCCESS;
            } else {
                BlockPos mainPos = mainPosOpt.get();
                if (mainPos.equals(pos)) {
                    player.sendSystemMessage(Component.literal("Cannot link spawner to itself."));
                    return InteractionResult.FAIL;
                }

                BlockEntity mainBe = world.getBlockEntity(mainPos);
                if (mainBe instanceof LinkableSpawner mainSpawner) {
                    mainSpawner.addLinkedSpawner(pos);
                    player.sendSystemMessage(Component.literal("Linked spawner at " + pos.toShortString() + " to Main Spawner at " + mainPos.toShortString()));
                    return InteractionResult.SUCCESS;
                } else {
                    player.sendSystemMessage(Component.literal("Main Spawner is no longer valid."));
                    stack.set(LinkerModeDataComponent.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty()));
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
                LinkerModeDataComponent modeData = stack.getOrDefault(LinkerModeDataComponent.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
                
                if (Mode.values()[modeData.mode()] == Mode.SPAWNER_LINKING) {
                    // Clear Main Spawner selection
                    stack.set(LinkerModeDataComponent.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty()));
                    player.sendSystemMessage(Component.literal("Cleared Main Spawner selection."));
                    return InteractionResultHolder.success(stack);
                }
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        LinkerModeDataComponent modeData = stack.getOrDefault(LinkerModeDataComponent.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
        Mode currentMode = Mode.values()[modeData.mode()];
        
        tooltipComponents.add(Component.literal("Mode: " + currentMode.getName()).withStyle(net.minecraft.ChatFormatting.GRAY));
        
        if (currentMode == Mode.SPAWNER_LINKING && modeData.mainSpawnerPos().isPresent()) {
            tooltipComponents.add(Component.literal("Main Spawner: " + modeData.mainSpawnerPos().get().toShortString()).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        
        tooltipComponents.add(Component.literal("Shift + Scroll to change mode").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
