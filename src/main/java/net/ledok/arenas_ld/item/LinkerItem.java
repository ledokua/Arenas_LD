package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.LinkerModeDataComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;
import java.util.Optional;

public class LinkerItem extends Item {
    public LinkerItem(Properties properties) {
        super(properties);
    }

    public enum Mode {
        CONTROLLER_INSTANCE("item.arenas_ld.linker.mode.controller_instance"),
        DBS_ROOM("item.arenas_ld.linker.mode.dbs_room"),
        ROOM_SPAWNER("item.arenas_ld.linker.mode.room_spawner"),
        ROOM_DOOR("item.arenas_ld.linker.mode.room_door");

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
        Player player = context.getPlayer();
        if (world.isClientSide || player == null) {
            return InteractionResult.PASS;
        }
        if (!player.isCreative() && !player.hasPermissions(2)) {
            return InteractionResult.PASS;
        }

        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = world.getBlockEntity(pos);
        ItemStack stack = context.getItemInHand();
        LinkerModeDataComponent modeData = stack.getOrDefault(DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);

        return switch (selectedMode(modeData)) {
            case CONTROLLER_INSTANCE -> handleControllerInstanceLinking(world, pos, player, stack, blockEntity, modeData);
            case DBS_ROOM -> handleDbsRoomLinking(world, pos, player, stack, blockEntity, modeData);
            case ROOM_SPAWNER -> handleRoomSpawnerLinking(world, pos, player, stack, blockEntity, modeData);
            case ROOM_DOOR -> handleRoomDoorLinking(world, pos, player, stack, blockEntity, modeData);
        };
    }

    private InteractionResult handleControllerInstanceLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, LinkerModeDataComponent modeData) {
        Optional<BlockPos> sourcePosOpt = modeData.mainSpawnerPos();
        Optional<ResourceKey<Level>> sourceDimOpt = modeData.mainSpawnerDimension();
        if (sourcePosOpt.isEmpty() || sourceDimOpt.isEmpty()) {
            if (blockEntity instanceof DungeonControllerBlockEntity) {
                selectSource(stack, pos, world.dimension(), modeData);
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.dungeon_controller_selected", pos.toShortString()));
                return InteractionResult.SUCCESS;
            }
            if (blockEntity instanceof RaidControllerBlockEntity) {
                selectSource(stack, pos, world.dimension(), modeData);
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.raid_controller_selected", pos.toShortString()));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.controller_instance.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }
        if (!sourceDimOpt.get().equals(world.dimension())) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.controller_instance.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        ServerLevel sourceWorld = world.getServer().getLevel(sourceDimOpt.get());
        if (sourceWorld == null) {
            clearSelection(stack, modeData);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.controller_instance.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        BlockEntity sourceBe = sourceWorld.getBlockEntity(sourcePosOpt.get());
        if (sourceBe instanceof DungeonControllerBlockEntity dungeonController) {
            if (!(blockEntity instanceof DungeonBossSpawnerBlockEntity)) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.controller_instance.wrong_blocks"));
                return InteractionResult.SUCCESS;
            }
            boolean added = dungeonController.addInstance(pos);
            player.sendSystemMessage(Component.translatable(
                    added ? "message.arenas_ld.linker.controller_instance.added"
                            : "message.arenas_ld.linker.controller_instance.duplicate"
            ));
            return InteractionResult.SUCCESS;
        }
        if (sourceBe instanceof RaidControllerBlockEntity raidController) {
            if (!(blockEntity instanceof RaidBossSpawnerBlockEntity)) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.controller_instance.wrong_blocks"));
                return InteractionResult.SUCCESS;
            }
            boolean added = raidController.addInstance(pos, world.dimension());
            player.sendSystemMessage(Component.translatable(
                    added ? "message.arenas_ld.linker.controller_instance.added"
                            : "message.arenas_ld.linker.controller_instance.duplicate"
            ));
            return InteractionResult.SUCCESS;
        }
        player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.controller_instance.wrong_blocks"));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleDbsRoomLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, LinkerModeDataComponent modeData) {
        Optional<BlockPos> sourcePosOpt = modeData.mainSpawnerPos();
        Optional<ResourceKey<Level>> sourceDimOpt = modeData.mainSpawnerDimension();
        if (sourcePosOpt.isEmpty() || sourceDimOpt.isEmpty()) {
            if (blockEntity instanceof DungeonBossSpawnerBlockEntity) {
                selectSource(stack, pos, world.dimension(), modeData);
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_spawner", pos.toShortString()));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.dbs_room.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }
        if (!sourceDimOpt.get().equals(world.dimension())) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.dbs_room.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        ServerLevel sourceWorld = world.getServer().getLevel(sourceDimOpt.get());
        if (sourceWorld == null) {
            clearSelection(stack, modeData);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.dbs_room.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        BlockEntity sourceBe = sourceWorld.getBlockEntity(sourcePosOpt.get());
        if (!(sourceBe instanceof DungeonBossSpawnerBlockEntity dbs) || !(blockEntity instanceof RoomControllerBlockEntity)) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.dbs_room.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        boolean added = dbs.addRoom(pos);
        player.sendSystemMessage(Component.translatable(
                added ? "message.arenas_ld.linker.dbs_room.added"
                        : "message.arenas_ld.linker.dbs_room.duplicate"
        ));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleRoomSpawnerLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, LinkerModeDataComponent modeData) {
        Optional<BlockPos> sourcePosOpt = modeData.mainSpawnerPos();
        Optional<ResourceKey<Level>> sourceDimOpt = modeData.mainSpawnerDimension();
        if (sourcePosOpt.isEmpty() || sourceDimOpt.isEmpty()) {
            if (blockEntity instanceof RoomControllerBlockEntity) {
                selectSource(stack, pos, world.dimension(), modeData);
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_spawner", pos.toShortString()));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_spawner.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }
        if (!sourceDimOpt.get().equals(world.dimension())) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_spawner.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        ServerLevel sourceWorld = world.getServer().getLevel(sourceDimOpt.get());
        if (sourceWorld == null) {
            clearSelection(stack, modeData);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_spawner.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        BlockEntity sourceBe = sourceWorld.getBlockEntity(sourcePosOpt.get());
        boolean validTarget = blockEntity instanceof MobSpawnerBlockEntity || blockEntity instanceof DungeonBossSpawnerBlockEntity;
        if (!(sourceBe instanceof RoomControllerBlockEntity room) || !validTarget) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_spawner.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        boolean added = room.addSpawner(pos);
        player.sendSystemMessage(Component.translatable(
                added ? "message.arenas_ld.linker.room_spawner.added"
                        : "message.arenas_ld.linker.room_spawner.duplicate"
        ));
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleRoomDoorLinking(Level world, BlockPos pos, Player player, ItemStack stack, BlockEntity blockEntity, LinkerModeDataComponent modeData) {
        Optional<BlockPos> sourcePosOpt = modeData.mainSpawnerPos();
        Optional<ResourceKey<Level>> sourceDimOpt = modeData.mainSpawnerDimension();
        if (sourcePosOpt.isEmpty() || sourceDimOpt.isEmpty()) {
            if (blockEntity instanceof RoomControllerBlockEntity) {
                selectSource(stack, pos, world.dimension(), modeData);
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_spawner", pos.toShortString()));
                return InteractionResult.SUCCESS;
            }
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_door.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }
        if (!sourceDimOpt.get().equals(world.dimension())) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_door.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        ServerLevel sourceWorld = world.getServer().getLevel(sourceDimOpt.get());
        if (sourceWorld == null) {
            clearSelection(stack, modeData);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_door.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        BlockEntity sourceBe = sourceWorld.getBlockEntity(sourcePosOpt.get());
        if (!(sourceBe instanceof RoomControllerBlockEntity room) || !(world.getBlockState(pos).getBlock() instanceof PhaseBlock)) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_door.wrong_blocks"));
            return InteractionResult.SUCCESS;
        }

        boolean replaced = room.getDoorPos() != null && !room.getDoorPos().equals(pos);
        room.setDoorPos(pos);
        if (replaced) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_door.cleared_first"));
        }
        player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_door.set"));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!world.isClientSide && player.isShiftKeyDown()) {
            BlockHitResult hitResult = getPlayerPOVHitResult(world, player, ClipContext.Fluid.NONE);
            if (hitResult.getType() == BlockHitResult.Type.MISS) {
                LinkerModeDataComponent modeData = stack.getOrDefault(DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
                clearSelection(stack, modeData);
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.cleared_selection"));
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        LinkerModeDataComponent modeData = stack.getOrDefault(DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
        Mode currentMode = selectedMode(modeData);
        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.mode", currentMode.getName()).withStyle(ChatFormatting.GRAY));
        modeData.mainSpawnerPos().ifPresent(pos ->
                tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.main_spawner", pos.toShortString()).withStyle(ChatFormatting.GOLD)));
        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.shift_scroll").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static Mode selectedMode(LinkerModeDataComponent modeData) {
        Mode[] modes = Mode.values();
        return modes[Math.floorMod(modeData.mode(), modes.length)];
    }

    private static void selectSource(ItemStack stack, BlockPos pos, ResourceKey<Level> dimension, LinkerModeDataComponent modeData) {
        stack.set(DataComponentRegistry.LINKER_MODE_DATA,
                new LinkerModeDataComponent(Math.floorMod(modeData.mode(), Mode.values().length), Optional.of(pos), Optional.of(dimension)));
    }

    private static void clearSelection(ItemStack stack, LinkerModeDataComponent modeData) {
        stack.set(DataComponentRegistry.LINKER_MODE_DATA,
                new LinkerModeDataComponent(Math.floorMod(modeData.mode(), Mode.values().length), Optional.empty(), Optional.empty()));
    }
}
