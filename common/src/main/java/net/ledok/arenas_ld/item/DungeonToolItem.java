package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.RoomGraph;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.DungeonToolDataComponent;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The one builder tool: the old Linker and Spawner Configurator merged. Every mode is
 * two-click ("select a source, then apply at a target"); RMB in air opens the mode-picker
 * screen, shift+scroll still cycles, shift+RMB in air clears the selection.
 *
 * <p>LINK mode replaces three old Linker modes: with a source selected, the (source type,
 * target type) pair determines the relationship — controller→instance, DBS→room,
 * room→spawner — so there is nothing to pick. When no pair matches, clicking a valid
 * source (re)selects it instead.
 */
public class DungeonToolItem extends Item {

    /** Set by client init; opens the client-side mode-picker screen for the given hand. */
    public static Consumer<InteractionHand> clientModeScreenOpener;

    public DungeonToolItem(Properties properties) {
        super(properties);
    }

    public enum Mode {
        LINK("item.arenas_ld.dungeon_tool.mode.link"),
        ROOM_DOOR("item.arenas_ld.dungeon_tool.mode.room_door"),
        MOB_SPAWN_POSITION("item.arenas_ld.dungeon_tool.mode.mob_spawn_position"),
        ENTRANCE_POSITION("item.arenas_ld.dungeon_tool.mode.entrance_position"),
        RESPAWN_POSITION("item.arenas_ld.dungeon_tool.mode.respawn_position"),
        PROTECT_POSITION("item.arenas_ld.dungeon_tool.mode.protect_position");

        private final String translationKey;

        Mode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getName() {
            return Component.translatable(translationKey);
        }

        /** True when the block entity can be the selected source of this mode. */
        public boolean isValidSource(BlockEntity blockEntity) {
            return switch (this) {
                case LINK -> blockEntity instanceof DungeonControllerBlockEntity
                    || blockEntity instanceof RaidControllerBlockEntity
                    || blockEntity instanceof ArenaControllerBlockEntity
                    || blockEntity instanceof DungeonBossSpawnerBlockEntity
                    || blockEntity instanceof RoomControllerBlockEntity;
                case ROOM_DOOR, PROTECT_POSITION -> blockEntity instanceof RoomControllerBlockEntity;
                case MOB_SPAWN_POSITION -> blockEntity instanceof MobSpawnerBlockEntity
                    || blockEntity instanceof DungeonBossSpawnerBlockEntity
                    || blockEntity instanceof RaidBossSpawnerBlockEntity;
                case ENTRANCE_POSITION -> blockEntity instanceof DungeonBossSpawnerBlockEntity
                    || blockEntity instanceof RaidBossSpawnerBlockEntity
                    || blockEntity instanceof ArenaSpawnerBlockEntity;
                case RESPAWN_POSITION -> blockEntity instanceof RaidBossSpawnerBlockEntity
                    || blockEntity instanceof ArenaSpawnerBlockEntity
                    || blockEntity instanceof RoomControllerBlockEntity;
            };
        }

        String selectSourceMessageKey() {
            return switch (this) {
                case LINK -> "message.arenas_ld.dungeon_tool.link.select_source";
                case ROOM_DOOR -> "message.arenas_ld.dungeon_tool.door.select_source";
                case MOB_SPAWN_POSITION -> "message.arenas_ld.configurator.select_source.mob_spawn";
                case ENTRANCE_POSITION -> "message.arenas_ld.configurator.select_source.entrance";
                case RESPAWN_POSITION -> "message.arenas_ld.configurator.select_source.respawn";
                case PROTECT_POSITION -> "message.arenas_ld.configurator.select_source.protect";
            };
        }
    }

    public static Mode selectedMode(DungeonToolDataComponent data) {
        Mode[] modes = Mode.values();
        return modes[Math.floorMod(data.mode(), modes.length)];
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
        ItemStack stack = context.getItemInHand();
        DungeonToolDataComponent data = stack.getOrDefault(DataComponentRegistry.DUNGEON_TOOL_DATA, DungeonToolDataComponent.DEFAULT);
        BlockEntity clicked = world.getBlockEntity(pos);

        return switch (selectedMode(data)) {
            case LINK -> handleLink(world, pos, player, stack, clicked, data);
            case ROOM_DOOR -> handleDoor(world, pos, player, stack, clicked, data);
            case MOB_SPAWN_POSITION, ENTRANCE_POSITION, RESPAWN_POSITION, PROTECT_POSITION ->
                handlePosition(world, pos, player, stack, clicked, data);
        };
    }

    // ---- LINK mode ----

    private InteractionResult handleLink(Level world, BlockPos pos, Player player, ItemStack stack,
                                         BlockEntity clicked, DungeonToolDataComponent data) {
        if (data.sourcePos().isPresent() && data.sourceDimension().isPresent()) {
            ServerLevel sourceWorld = world.getServer().getLevel(data.sourceDimension().get());
            BlockEntity source = sourceWorld != null ? sourceWorld.getBlockEntity(data.sourcePos().get()) : null;
            if (source != null) {
                InteractionResult linked = tryLink(world, pos, player, source, data.sourceDimension().get(), clicked);
                if (linked != null) {
                    return linked;
                }
            }
        }
        // No link resolved: clicking a valid source (re)selects it.
        if (Mode.LINK.isValidSource(clicked)) {
            selectSource(stack, pos, world.dimension(), data);
            player.sendSystemMessage(Component.translatable(linkSelectedMessageKey(clicked), pos.toShortString()));
            return InteractionResult.SUCCESS;
        }
        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_tool.link.wrong_blocks"));
        return InteractionResult.SUCCESS;
    }

    /**
     * The (source type, target type) pair determines the link; returns null when no pair matches
     * so the caller can fall back to reselecting. Click order disambiguates the two-sided types:
     * DBS→room adds a room, room→DBS links the boss spawner into the room.
     */
    @Nullable
    private InteractionResult tryLink(Level world, BlockPos pos, Player player,
                                      BlockEntity source, ResourceKey<Level> sourceDim, BlockEntity target) {
        // Controller → instance links may cross dimensions; the controller stores each instance's dimension.
        if (source instanceof DungeonControllerBlockEntity controller && target instanceof DungeonBossSpawnerBlockEntity) {
            return instanceResult(player, controller.addInstance(pos, world.dimension()));
        }
        if (source instanceof RaidControllerBlockEntity controller && target instanceof RaidBossSpawnerBlockEntity) {
            return instanceResult(player, controller.addInstance(pos, world.dimension()));
        }
        if (source instanceof ArenaControllerBlockEntity controller && target instanceof ArenaSpawnerBlockEntity) {
            return instanceResult(player, controller.addInstance(pos, world.dimension()));
        }
        if (source instanceof DungeonBossSpawnerBlockEntity dbs && target instanceof RoomControllerBlockEntity) {
            if (!sourceDim.equals(world.dimension())) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.dbs_room.wrong_blocks"));
                return InteractionResult.SUCCESS;
            }
            boolean added = dbs.addRoom(pos);
            player.sendSystemMessage(Component.translatable(
                added ? "message.arenas_ld.linker.dbs_room.added"
                    : "message.arenas_ld.linker.dbs_room.duplicate"));
            return InteractionResult.SUCCESS;
        }
        if (source instanceof RoomControllerBlockEntity room
                && (target instanceof MobSpawnerBlockEntity || target instanceof DungeonBossSpawnerBlockEntity)) {
            if (!sourceDim.equals(world.dimension())) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.room_spawner.wrong_blocks"));
                return InteractionResult.SUCCESS;
            }
            boolean added = room.addSpawner(pos);
            player.sendSystemMessage(Component.translatable(
                added ? "message.arenas_ld.linker.room_spawner.added"
                    : "message.arenas_ld.linker.room_spawner.duplicate"));
            return InteractionResult.SUCCESS;
        }
        return null;
    }

    private static InteractionResult instanceResult(Player player, boolean added) {
        player.sendSystemMessage(Component.translatable(
            added ? "message.arenas_ld.linker.controller_instance.added"
                : "message.arenas_ld.linker.controller_instance.duplicate"));
        return InteractionResult.SUCCESS;
    }

    private static String linkSelectedMessageKey(BlockEntity blockEntity) {
        if (blockEntity instanceof DungeonControllerBlockEntity) {
            return "message.arenas_ld.linker.dungeon_controller_selected";
        }
        if (blockEntity instanceof RaidControllerBlockEntity) {
            return "message.arenas_ld.linker.raid_controller_selected";
        }
        if (blockEntity instanceof ArenaControllerBlockEntity) {
            return "message.arenas_ld.linker.arena_controller_selected";
        }
        return "message.arenas_ld.linker.set_main_spawner";
    }

    // ---- Door mode ----

    /**
     * Doors are direction-free: link the same doorway from both rooms and the graph connects
     * them; whichever side clears first opens it toward the other. A door linked by only one
     * room is a boundary door (the start room's spawn-side way in, or a dead end).
     */
    private InteractionResult handleDoor(Level world, BlockPos pos, Player player, ItemStack stack,
                                         BlockEntity clicked, DungeonToolDataComponent data) {
        if (clicked instanceof RoomControllerBlockEntity) {
            selectSource(stack, pos, world.dimension(), data);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.set_main_spawner", pos.toShortString()));
            return InteractionResult.SUCCESS;
        }

        if (data.sourcePos().isEmpty() || data.sourceDimension().isEmpty()
                || !data.sourceDimension().get().equals(world.dimension())) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_tool.door.select_source"));
            return InteractionResult.SUCCESS;
        }
        BlockEntity source = world.getBlockEntity(data.sourcePos().get());
        if (!(source instanceof RoomControllerBlockEntity room) || !(world.getBlockState(pos).getBlock() instanceof PhaseBlock)) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_tool.door.select_source"));
            return InteractionResult.SUCCESS;
        }

        // Toggle: clicking any block of an already-linked door unlinks the whole door.
        boolean linked = room.toggleDoor(pos);
        player.sendSystemMessage(linked
            ? Component.translatable("message.arenas_ld.dungeon_tool.door.linked", RoomGraph.expandDoorGroup(world, pos).size())
            : Component.translatable("message.arenas_ld.dungeon_tool.door.unlinked"));
        return InteractionResult.SUCCESS;
    }

    // ---- Position modes (ported from the Spawner Configurator) ----

    private InteractionResult handlePosition(Level world, BlockPos clickedPos, Player player, ItemStack stack,
                                             BlockEntity clickedBlockEntity, DungeonToolDataComponent data) {
        Mode currentMode = selectedMode(data);

        // Clicking a valid source for the current mode always (re)selects it.
        if (currentMode.isValidSource(clickedBlockEntity)) {
            selectSource(stack, clickedPos, world.dimension(), data);
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawner_selected", clickedPos.toShortString()));
            return InteractionResult.SUCCESS;
        }

        Optional<BlockPos> sourcePosOpt = data.sourcePos();
        Optional<ResourceKey<Level>> sourceDimOpt = data.sourceDimension();
        if (sourcePosOpt.isEmpty() || sourceDimOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable(currentMode.selectSourceMessageKey()));
            return InteractionResult.FAIL;
        }

        BlockPos sourcePos = sourcePosOpt.get();
        ResourceKey<Level> sourceDim = sourceDimOpt.get();
        ServerLevel sourceWorld = world.getServer().getLevel(sourceDim);
        if (sourceWorld == null) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.invalid_spawner"));
            stack.set(DataComponentRegistry.DUNGEON_TOOL_DATA, data.withoutSource());
            return InteractionResult.FAIL;
        }

        BlockEntity sourceBlockEntity = sourceWorld.getBlockEntity(sourcePos);
        if (sourceBlockEntity == null) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.invalid_spawner"));
            stack.set(DataComponentRegistry.DUNGEON_TOOL_DATA, data.withoutSource());
            return InteractionResult.FAIL;
        }
        // Selection survives mode switches, so re-validate against the current mode with a
        // mode-specific hint instead of silently no-opping.
        if (!currentMode.isValidSource(sourceBlockEntity)) {
            player.sendSystemMessage(Component.translatable(currentMode.selectSourceMessageKey()));
            return InteractionResult.FAIL;
        }

        ResourceKey<Level> clickedDimension = world.dimension();

        switch (currentMode) {
            case ENTRANCE_POSITION: {
                // Players enter standing ON the clicked block (one block above it), matching how
                // mob-spawn and respawn positions are placed.
                BlockPos entranceAbsolute = clickedPos.above();
                if (sourceBlockEntity instanceof DungeonBossSpawnerBlockEntity dbs) {
                    dbs.setEntrancePosition(entranceAbsolute, clickedDimension);
                } else if (sourceBlockEntity instanceof RaidBossSpawnerBlockEntity bossSpawner) {
                    bossSpawner.setEntrancePosition(entranceAbsolute, clickedDimension);
                } else if (sourceBlockEntity instanceof ArenaSpawnerBlockEntity arenaSpawner) {
                    arenaSpawner.setEntrancePosition(entranceAbsolute, clickedDimension);
                }
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.entrance_pos_set", clickedPos.toShortString(), clickedDimension.location().toString()));
                break;
            }
            case MOB_SPAWN_POSITION: {
                if (!sourceDim.equals(clickedDimension)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_wrong_dimension"));
                    return InteractionResult.FAIL;
                }
                // Mob spawns ON the clicked block (one block above it), stored relative to the spawner.
                BlockPos spawnOffset = clickedPos.above().subtract(sourcePos);
                if (sourceBlockEntity instanceof MobSpawnerBlockEntity mobSpawner) {
                    EntityDefinition def = mobSpawner.getEntityDefinition();
                    List<BlockPos> offsets = new ArrayList<>(def.spawnOffsets());
                    int spawnCount = def.spawnCount();
                    if (offsets.remove(spawnOffset)) {
                        spawnCount = Math.max(0, spawnCount - 1);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        offsets.add(spawnOffset);
                        spawnCount = Math.min(64, spawnCount + 1);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_added", clickedPos.toShortString(), offsets.size()));
                    }
                    mobSpawner.setEntityDefinition(def.withSpawnOffsets(offsets).withSpawnCount(spawnCount));
                } else if (sourceBlockEntity instanceof DungeonBossSpawnerBlockEntity bossSpawner) {
                    // Boss spawner holds a single spawn position: placing replaces it, clicking the same one clears it.
                    EntityDefinition def = bossSpawner.getEntityDefinition();
                    List<BlockPos> current = def.spawnOffsets();
                    if (current.size() == 1 && current.get(0).equals(spawnOffset)) {
                        bossSpawner.setEntityDefinition(def.withSpawnOffsets(List.of()));
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        bossSpawner.setEntityDefinition(def.withSpawnOffsets(List.of(spawnOffset)));
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_added", clickedPos.toShortString(), 1));
                    }
                } else if (sourceBlockEntity instanceof RaidBossSpawnerBlockEntity raidBossSpawner) {
                    // Raid boss spawner also holds a single spawn position: placing replaces it, clicking the same one clears it.
                    EntityDefinition def = raidBossSpawner.getEntityDefinition();
                    List<BlockPos> current = def.spawnOffsets();
                    if (current.size() == 1 && current.get(0).equals(spawnOffset)) {
                        raidBossSpawner.setEntityDefinition(def.withSpawnOffsets(List.of()));
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        raidBossSpawner.setEntityDefinition(def.withSpawnOffsets(List.of(spawnOffset)));
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_added", clickedPos.toShortString(), 1));
                    }
                }
                break;
            }
            case RESPAWN_POSITION: {
                if (!sourceDim.equals(clickedDimension)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_wrong_dimension"));
                    return InteractionResult.FAIL;
                }
                // Players respawn standing ON the clicked block, stored relative to the owner.
                BlockPos respawnAbsolute = clickedPos.above();
                if (sourceBlockEntity instanceof RaidBossSpawnerBlockEntity raidBossSpawner) {
                    // Raid spawner holds multiple respawn points: clicking toggles add/remove.
                    BlockPos respawnOffset = respawnAbsolute.subtract(sourcePos);
                    if (raidBossSpawner.removeRespawnPointOffset(respawnOffset)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        raidBossSpawner.addRespawnPointOffset(respawnOffset);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_added", clickedPos.toShortString(), raidBossSpawner.getRespawnPointOffsets().size()));
                    }
                } else if (sourceBlockEntity instanceof ArenaSpawnerBlockEntity arenaSpawner) {
                    // Arena spawner holds multiple respawn points: clicking toggles add/remove.
                    BlockPos respawnOffset = respawnAbsolute.subtract(sourcePos);
                    if (arenaSpawner.removeRespawnPointOffset(respawnOffset)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        arenaSpawner.addRespawnPointOffset(respawnOffset);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_added", clickedPos.toShortString(), arenaSpawner.getRespawnPointOffsets().size()));
                    }
                } else if (sourceBlockEntity instanceof RoomControllerBlockEntity room) {
                    // Rooms hold multiple respawn points (players spawn at the closest one):
                    // clicking toggles add/remove, like the raid and arena spawners.
                    BlockPos respawnOffset = respawnAbsolute.subtract(sourcePos);
                    if (room.removeRespawnPointOffset(respawnOffset)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        room.addRespawnPointOffset(respawnOffset);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_added", clickedPos.toShortString(), room.getRespawnPointOffsets().size()));
                    }
                }
                break;
            }
            case PROTECT_POSITION: {
                if (!sourceDim.equals(clickedDimension)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_wrong_dimension"));
                    return InteractionResult.FAIL;
                }
                // The target spawns ON the clicked block; single position — placing replaces it,
                // clicking the same one clears it (same semantics as the room respawn point).
                BlockPos protectAbsolute = clickedPos.above();
                if (sourceBlockEntity instanceof RoomControllerBlockEntity room) {
                    if (protectAbsolute.equals(room.getProtectPos())) {
                        room.setProtectPos(null);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.protect_pos_removed", clickedPos.toShortString()));
                    } else {
                        room.setProtectPos(protectAbsolute);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.protect_pos_set", clickedPos.toShortString()));
                    }
                }
                break;
            }
            default:
                throw new IllegalStateException("handlePosition called for non-position mode " + currentMode);
        }

        sourceBlockEntity.setChanged();
        sourceWorld.sendBlockUpdated(sourcePos, sourceBlockEntity.getBlockState(), sourceBlockEntity.getBlockState(), 3);
        return InteractionResult.SUCCESS;
    }

    // ---- Air use: mode screen / clear selection ----

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        BlockHitResult hitResult = getPlayerPOVHitResult(world, player, ClipContext.Fluid.NONE);
        if (hitResult.getType() != BlockHitResult.Type.MISS) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            if (!world.isClientSide) {
                DungeonToolDataComponent data = stack.getOrDefault(DataComponentRegistry.DUNGEON_TOOL_DATA, DungeonToolDataComponent.DEFAULT);
                stack.set(DataComponentRegistry.DUNGEON_TOOL_DATA, data.withoutSource());
                player.sendSystemMessage(Component.translatable("message.arenas_ld.linker.cleared_selection"));
            }
            return InteractionResultHolder.success(stack);
        }
        if (world.isClientSide && clientModeScreenOpener != null) {
            clientModeScreenOpener.accept(usedHand);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        DungeonToolDataComponent data = stack.getOrDefault(DataComponentRegistry.DUNGEON_TOOL_DATA, DungeonToolDataComponent.DEFAULT);
        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.mode", selectedMode(data).getName()).withStyle(ChatFormatting.GRAY));
        data.sourcePos().ifPresent(pos ->
            tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.main_spawner", pos.toShortString()).withStyle(ChatFormatting.GOLD)));
        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.dungeon_tool.open_screen").withStyle(ChatFormatting.DARK_GRAY));
        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.shift_scroll").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void selectSource(ItemStack stack, BlockPos pos, ResourceKey<Level> dimension, DungeonToolDataComponent data) {
        stack.set(DataComponentRegistry.DUNGEON_TOOL_DATA, data.withSource(pos, dimension));
    }
}
