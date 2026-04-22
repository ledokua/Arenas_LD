package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.block.entity.*;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.LinkableSpawner;
import net.ledok.arenas_ld.util.LinkerDataComponent;
import net.ledok.arenas_ld.util.LinkerModeDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
        PHASE_BLOCK_LINKING("item.arenas_ld.linker.mode.phase_block_linking"),
        ARENA_CONTROLLER_LINKING("item.arenas_ld.linker.mode.arena_controller_linking"),
        DUNGEON_CONTROLLER_LINKING("item.arenas_ld.linker.mode.dungeon_controller_linking");

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
        } else if (currentMode == Mode.ARENA_CONTROLLER_LINKING) {
            return handleArenaControllerLinking(world, pos, player, stack, blockEntity, isShiftDown, modeData);
        } else if (currentMode == Mode.DUNGEON_CONTROLLER_LINKING) {
            return handleDungeonControllerLinking(world, pos, player, stack, blockEntity, isShiftDown, modeData);
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
                String groupId = spawner.getGroupId();
                stack.set(DataComponentRegistry.LINKER_DATA, new LinkerDataComponent(groupId));
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.copied_group_id", groupId));
                return InteractionResult.SUCCESS;
            } else {
                LinkerDataComponent data = stack.get(DataComponentRegistry.LINKER_DATA);
                if (data != null) {
                    spawner.setGroupId(data.groupId());
                    world.sendBlockUpdated(pos, state, state, 3);
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.pasted_group_id", data.groupId()));
                    return InteractionResult.SUCCESS;
                }
            }
        }
        return InteractionResult.PASS;
    }

    private InteractionResult handleSpawnerLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, boolean isShiftDown, LinkerModeDataComponent modeData) {
        if (!(blockEntity instanceof LinkableSpawner)) {
            return InteractionResult.PASS;
        }

        Optional<BlockPos> mainPosOpt = modeData.mainSpawnerPos();
        if (mainPosOpt.isEmpty()) {
            stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.of(pos), Optional.of(world.dimension())));
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_spawner", pos.toShortString()));
            return InteractionResult.SUCCESS;
        }

        if (modeData.mainSpawnerDimension().isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_spawner_invalid"));
            stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
            return InteractionResult.FAIL;
        }

        BlockPos mainPos = mainPosOpt.get();
        ServerLevel mainWorld = world.getServer().getLevel(modeData.mainSpawnerDimension().get());
        if (mainWorld == null) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_spawner_invalid"));
            stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
            return InteractionResult.FAIL;
        }

        BlockEntity mainBe = mainWorld.getBlockEntity(mainPos);
        if (!(mainBe instanceof LinkableSpawner mainSpawner)) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_spawner_invalid"));
            stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
            return InteractionResult.FAIL;
        }

        if (mainPos.equals(pos) && modeData.mainSpawnerDimension().get().equals(world.dimension())) {
            List<BlockPos> linked = mainSpawner.getLinkedSpawners();
            if (linked.isEmpty()) {
                player.sendSystemMessage(Component.literal("No linked spawners."));
            } else {
                player.sendSystemMessage(Component.literal("Linked spawners:"));
                for (BlockPos offset : linked) {
                    player.sendSystemMessage(Component.literal("- " + mainPos.offset(offset).toShortString()));
                }
            }
            return InteractionResult.SUCCESS;
        }

        BlockPos relativePos = pos.subtract(mainPos);
        if (isShiftDown) {
            boolean removed = mainSpawner.removeLinkedSpawner(relativePos);
            if (removed) {
                player.sendSystemMessage(Component.literal("Unlinked spawner " + pos.toShortString() + " from " + mainPos.toShortString()));
            } else {
                player.sendSystemMessage(Component.literal("Spawner was not linked to selected main spawner."));
            }
            return InteractionResult.SUCCESS;
        }

        mainSpawner.addLinkedSpawner(relativePos);
        player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.linked_spawner", pos.toShortString(), mainPos.toShortString()));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handlePhaseBlockLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, boolean isShiftDown, LinkerModeDataComponent modeData) {
        Optional<BlockPos> mainPosOpt = modeData.mainSpawnerPos();

        if (blockEntity instanceof PhaseBlockEntity phaseBlock) {
            if (mainPosOpt.isEmpty() || !mainPosOpt.get().equals(pos)) {
                // Select phase block.
                phaseBlock.setIsMain(true);
                stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.of(pos), Optional.of(world.dimension())));
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_phase_block", pos.toShortString()));
                return InteractionResult.SUCCESS;
            }
            // Selected phase block clicked again: list watched spawners.
            List<BlockPos> watched = phaseBlock.getWatchedSpawnerOffsets();
            if (watched.isEmpty()) {
                player.sendSystemMessage(Component.literal("No watched spawners linked."));
            } else {
                player.sendSystemMessage(Component.literal("Watched spawners:"));
                for (BlockPos offset : watched) {
                    BlockPos absolutePos = pos.offset(offset);
                    player.sendSystemMessage(Component.literal("- " + absolutePos.toShortString()));
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (blockEntity instanceof MobSpawnerBlockEntity || blockEntity instanceof BossSpawnerBlockEntity || blockEntity instanceof DungeonBossSpawnerBlockEntity) {
            if (mainPosOpt.isPresent()) {
                BlockPos mainPos = mainPosOpt.get();
                ServerLevel mainWorld = world.getServer().getLevel(modeData.mainSpawnerDimension().get());
                if (mainWorld == null) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_phase_block_invalid"));
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
                    return InteractionResult.FAIL;
                }
                
                BlockEntity mainBe = mainWorld.getBlockEntity(mainPos);
                if (mainBe instanceof PhaseBlockEntity mainPhaseBlock) {
                    BlockPos relativePos = pos.subtract(mainPos);
                    if (isShiftDown) {
                        boolean removed = mainPhaseBlock.removeWatchedSpawnerOffset(relativePos);
                        if (removed) {
                            player.sendSystemMessage(Component.literal("Unlinked spawner " + pos.toShortString() + " from phase block " + mainPos.toShortString()));
                        } else {
                            player.sendSystemMessage(Component.literal("Spawner was not linked to selected phase block."));
                        }
                        return InteractionResult.SUCCESS;
                    }
                    mainPhaseBlock.addWatchedSpawnerOffset(relativePos);
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.linked_spawner_to_phase_block", pos.toShortString(), mainPos.toShortString()));
                    return InteractionResult.SUCCESS;
                } else {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_phase_block_invalid"));
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
                    return InteractionResult.FAIL;
                }
            }
        }

        return InteractionResult.PASS;
    }

    private InteractionResult handleArenaControllerLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, boolean isShiftDown, LinkerModeDataComponent modeData) {
        Optional<BlockPos> mainPosOpt = modeData.mainSpawnerPos();

        if (blockEntity instanceof MobArenaSpawnerBlockEntity) {
            if (mainPosOpt.isPresent() && mainPosOpt.get().equals(pos) && modeData.mainSpawnerDimension().isPresent() && modeData.mainSpawnerDimension().get().equals(world.dimension())) {
                player.sendSystemMessage(Component.literal("Selected arena spawner: " + pos.toShortString()));
                return InteractionResult.SUCCESS;
            }
            stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.of(pos), Optional.of(world.dimension())));
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_spawner", pos.toShortString()));
            return InteractionResult.SUCCESS;
        }

        if (blockEntity instanceof MobArenaControllerBlockEntity controller) {
            if (mainPosOpt.isEmpty() || modeData.mainSpawnerDimension().isEmpty()) {
                if (!controller.arenaSpawnerPos.equals(BlockPos.ZERO)) {
                    player.sendSystemMessage(Component.literal("Controller linked to: " + controller.arenaSpawnerPos.toShortString() + " @ " + controller.arenaSpawnerDimension.location()));
                } else {
                    player.sendSystemMessage(Component.literal("No arena spawner selected. Click a spawner first."));
                }
                return InteractionResult.SUCCESS;
            }

            BlockPos mainPos = mainPosOpt.get();
            ServerLevel mainWorld = world.getServer().getLevel(modeData.mainSpawnerDimension().get());
            if (mainWorld == null) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_spawner_invalid"));
                stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
                return InteractionResult.FAIL;
            }

            BlockEntity mainBe = mainWorld.getBlockEntity(mainPos);
            if (!(mainBe instanceof MobArenaSpawnerBlockEntity)) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.main_spawner_invalid"));
                stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
                return InteractionResult.FAIL;
            }

            if (isShiftDown) {
                if (controller.arenaSpawnerPos.equals(mainPos) && controller.arenaSpawnerDimension.equals(modeData.mainSpawnerDimension().get())) {
                    controller.arenaSpawnerPos = BlockPos.ZERO;
                    controller.arenaSpawnerDimension = Level.OVERWORLD;
                    controller.setChanged();
                    player.sendSystemMessage(Component.literal("Unlinked controller from spawner " + mainPos.toShortString()));
                } else {
                    player.sendSystemMessage(Component.literal("Selected spawner is not linked to this controller."));
                }
                return InteractionResult.SUCCESS;
            }

            controller.arenaSpawnerPos = mainPos;
            controller.arenaSpawnerDimension = modeData.mainSpawnerDimension().get();
            controller.setChanged();
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.linked_controller_to_spawner", pos.toShortString(), mainPos.toShortString()));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    private InteractionResult handleDungeonControllerLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, boolean isShiftDown, LinkerModeDataComponent modeData) {
        Optional<BlockPos> mainPosOpt = modeData.mainSpawnerPos();

        if (blockEntity instanceof DungeonControllerBlockEntity controller) {
            if (mainPosOpt.isEmpty() || modeData.mainSpawnerDimension().isEmpty() || !mainPosOpt.get().equals(pos) || !modeData.mainSpawnerDimension().get().equals(world.dimension())) {
                // Select controller first.
                stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.of(pos), Optional.of(world.dimension())));
                player.sendSystemMessage(Component.literal("Selected dungeon controller: " + pos.toShortString()));
                return InteractionResult.SUCCESS;
            }

            // Selected controller clicked again: list instances.
            if (controller.instances.isEmpty()) {
                player.sendSystemMessage(Component.literal("No dungeon instances linked."));
            } else {
                player.sendSystemMessage(Component.literal("Dungeon instances:"));
                for (var instance : controller.instances) {
                    int cooldownSec = (instance.cooldownTicksRemaining() + 19) / 20;
                    String suffix = instance.status() == net.ledok.arenas_ld.util.InstanceStatus.COOLDOWN
                            ? ", " + String.format("%02d:%02d", cooldownSec / 60, cooldownSec % 60)
                            : "";
                    player.sendSystemMessage(Component.literal(
                            "- " + instance.ref().spawnerPos().toShortString() + " @ " + instance.ref().dimension().location()
                                    + " (" + instance.status().name() + suffix + ")"
                    ));
                }
            }
            return InteractionResult.SUCCESS;
        }

        if (blockEntity instanceof DungeonBossSpawnerBlockEntity) {
            if (mainPosOpt.isEmpty() || modeData.mainSpawnerDimension().isEmpty()) {
                player.sendSystemMessage(Component.literal("No dungeon controller selected. Click a controller first."));
                return InteractionResult.SUCCESS;
            }

            BlockPos controllerPos = mainPosOpt.get();
            ServerLevel controllerWorld = world.getServer().getLevel(modeData.mainSpawnerDimension().get());
            if (controllerWorld == null) {
                player.sendSystemMessage(Component.literal("Selected dungeon controller is invalid."));
                stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
                return InteractionResult.FAIL;
            }

            BlockEntity controllerBe = controllerWorld.getBlockEntity(controllerPos);
            if (!(controllerBe instanceof DungeonControllerBlockEntity controller)) {
                player.sendSystemMessage(Component.literal("Selected dungeon controller is invalid."));
                stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
                return InteractionResult.FAIL;
            }

            var ref = new net.ledok.arenas_ld.util.DungeonInstanceRef(pos, world.dimension());
            if (isShiftDown) {
                boolean removed = controller.removeInstance(ref);
                if (removed) {
                    player.sendSystemMessage(Component.literal("Removed dungeon instance " + pos.toShortString() + " from controller " + controllerPos.toShortString()));
                } else {
                    player.sendSystemMessage(Component.literal("Selected instance is not linked (or running)."));
                }
                return InteractionResult.SUCCESS;
            }

            boolean added = controller.addInstance(ref);
            if (added) {
                player.sendSystemMessage(Component.literal("Added dungeon instance " + pos.toShortString() + " to controller " + controllerPos.toShortString()));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.literal("Could not add instance. It may already be linked or running."));
            return InteractionResult.SUCCESS;
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

                if (Mode.values()[modeData.mode()] == Mode.SPAWNER_LINKING || Mode.values()[modeData.mode()] == Mode.PHASE_BLOCK_LINKING || Mode.values()[modeData.mode()] == Mode.ARENA_CONTROLLER_LINKING || Mode.values()[modeData.mode()] == Mode.DUNGEON_CONTROLLER_LINKING) {
                    // Clear Main Spawner/Phase Block selection
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(modeData.mode(), Optional.empty(), Optional.empty()));
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
        } else if ((currentMode == Mode.ARENA_CONTROLLER_LINKING || currentMode == Mode.DUNGEON_CONTROLLER_LINKING) && modeData.mainSpawnerPos().isPresent()) {
            tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.main_spawner", modeData.mainSpawnerPos().get().toShortString()).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        
        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.shift_scroll").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
