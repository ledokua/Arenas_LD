package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
import net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.SpawnerSelectionDataComponent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Linker-style two-click flow: in any mode, clicking a block entity that is a valid SOURCE for
 * that mode (re)selects it; clicking anything else applies the mode's action at that position.
 * Shift + use on air clears the selection. Shift + scroll cycles modes.
 */
public class SpawnerConfiguratorItem extends Item {
    public SpawnerConfiguratorItem(Properties properties) {
        super(properties);
    }

    public enum Mode {
        MOB_SPAWN_POSITION("item.arenas_ld.configurator.mode.mob_spawn_position"),
        ENTRANCE_POSITION("item.arenas_ld.configurator.mode.entrance_position"),
        RESPAWN_POSITION("item.arenas_ld.configurator.mode.respawn_position");

        private final String translationKey;

        Mode(String translationKey) {
            this.translationKey = translationKey;
        }

        public Component getName() {
            return Component.translatable(translationKey);
        }

        /** True when the block entity can be the selected source of this mode. */
        boolean isValidSource(BlockEntity blockEntity) {
            return switch (this) {
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
                case MOB_SPAWN_POSITION -> "message.arenas_ld.configurator.select_source.mob_spawn";
                case ENTRANCE_POSITION -> "message.arenas_ld.configurator.select_source.entrance";
                case RESPAWN_POSITION -> "message.arenas_ld.configurator.select_source.respawn";
            };
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (world.isClientSide || player == null) {
            return InteractionResult.PASS;
        }
        if (!player.isCreative() && !player.hasPermissions(2)) {
            return InteractionResult.PASS;
        }

        SpawnerSelectionDataComponent data = stack.getOrDefault(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
        Mode currentMode = selectedMode(data);
        BlockEntity clickedBlockEntity = world.getBlockEntity(clickedPos);

        // Linker-style: clicking a valid source for the current mode always (re)selects it.
        if (currentMode.isValidSource(clickedBlockEntity)) {
            stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA,
                new SpawnerSelectionDataComponent(data.mode(), Optional.of(clickedPos), Optional.of(world.dimension())));
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawner_selected", clickedPos.toShortString()));
            return InteractionResult.SUCCESS;
        }

        Optional<BlockPos> selectedSpawnerPosOpt = data.selectedSpawnerPos();
        Optional<ResourceKey<Level>> selectedSpawnerDimOpt = data.selectedSpawnerDimension();
        if (selectedSpawnerPosOpt.isEmpty() || selectedSpawnerDimOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable(currentMode.selectSourceMessageKey()));
            return InteractionResult.FAIL;
        }

        BlockPos selectedSpawnerPos = selectedSpawnerPosOpt.get();
        ResourceKey<Level> selectedSpawnerDim = selectedSpawnerDimOpt.get();
        ServerLevel spawnerWorld = world.getServer().getLevel(selectedSpawnerDim);

        if (spawnerWorld == null) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.invalid_spawner"));
            stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
            return InteractionResult.FAIL;
        }

        BlockEntity selectedBlockEntity = spawnerWorld.getBlockEntity(selectedSpawnerPos);
        if (selectedBlockEntity == null) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.invalid_spawner"));
            stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
            return InteractionResult.FAIL;
        }
        // Selection survives mode switches, so re-validate against the current mode with a
        // mode-specific hint instead of silently no-opping.
        if (!currentMode.isValidSource(selectedBlockEntity)) {
            player.sendSystemMessage(Component.translatable(currentMode.selectSourceMessageKey()));
            return InteractionResult.FAIL;
        }

        ResourceKey<Level> clickedDimension = world.dimension();

        switch (currentMode) {
            case ENTRANCE_POSITION: {
                // Players enter standing ON the clicked block (one block above it), matching how
                // mob-spawn and respawn positions are placed.
                BlockPos entranceAbsolute = clickedPos.above();
                if (selectedBlockEntity instanceof DungeonBossSpawnerBlockEntity newDbs) {
                    newDbs.setEntrancePosition(entranceAbsolute, clickedDimension);
                } else if (selectedBlockEntity instanceof RaidBossSpawnerBlockEntity bossSpawner) {
                    bossSpawner.setEntrancePosition(entranceAbsolute, clickedDimension);
                } else if (selectedBlockEntity instanceof ArenaSpawnerBlockEntity arenaSpawner) {
                    arenaSpawner.setEntrancePosition(entranceAbsolute, clickedDimension);
                }
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.entrance_pos_set", clickedPos.toShortString(), clickedDimension.location().toString()));
                break;
            }
            case MOB_SPAWN_POSITION: {
                if (!selectedSpawnerDim.equals(clickedDimension)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_wrong_dimension"));
                    return InteractionResult.FAIL;
                }
                // Mob spawns ON the clicked block (one block above it), stored relative to the spawner.
                BlockPos spawnOffset = clickedPos.above().subtract(selectedSpawnerPos);
                if (selectedBlockEntity instanceof MobSpawnerBlockEntity mobSpawner) {
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
                } else if (selectedBlockEntity instanceof DungeonBossSpawnerBlockEntity bossSpawner) {
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
                } else if (selectedBlockEntity instanceof RaidBossSpawnerBlockEntity raidBossSpawner) {
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
                if (!selectedSpawnerDim.equals(clickedDimension)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_wrong_dimension"));
                    return InteractionResult.FAIL;
                }
                // Players respawn standing ON the clicked block, stored relative to the owner.
                BlockPos respawnAbsolute = clickedPos.above();
                if (selectedBlockEntity instanceof RaidBossSpawnerBlockEntity raidBossSpawner) {
                    // Raid spawner holds multiple respawn points: clicking toggles add/remove.
                    BlockPos respawnOffset = respawnAbsolute.subtract(selectedSpawnerPos);
                    if (raidBossSpawner.removeRespawnPointOffset(respawnOffset)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        raidBossSpawner.addRespawnPointOffset(respawnOffset);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_added", clickedPos.toShortString(), raidBossSpawner.getRespawnPointOffsets().size()));
                    }
                } else if (selectedBlockEntity instanceof ArenaSpawnerBlockEntity arenaSpawner) {
                    // Arena spawner holds multiple respawn points: clicking toggles add/remove.
                    BlockPos respawnOffset = respawnAbsolute.subtract(selectedSpawnerPos);
                    if (arenaSpawner.removeRespawnPointOffset(respawnOffset)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        arenaSpawner.addRespawnPointOffset(respawnOffset);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_added", clickedPos.toShortString(), arenaSpawner.getRespawnPointOffsets().size()));
                    }
                } else if (selectedBlockEntity instanceof RoomControllerBlockEntity room) {
                    // Room holds a single respawn point: placing replaces it, clicking the same one clears it.
                    if (respawnAbsolute.equals(room.getRespawnPos())) {
                        room.setRespawnPos(null);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        room.setRespawnPos(respawnAbsolute);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_added", clickedPos.toShortString(), 1));
                    }
                }
                break;
            }
        }

        selectedBlockEntity.setChanged();
        spawnerWorld.sendBlockUpdated(selectedSpawnerPos, selectedBlockEntity.getBlockState(), selectedBlockEntity.getBlockState(), 3);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!world.isClientSide && player.isShiftKeyDown()) {
            BlockHitResult hitResult = getPlayerPOVHitResult(world, player, ClipContext.Fluid.NONE);
            if (hitResult.getType() == BlockHitResult.Type.MISS) {
                stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.cleared_selection"));
                return InteractionResultHolder.success(stack);
            }
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        SpawnerSelectionDataComponent data = stack.getOrDefault(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
        Mode currentMode = selectedMode(data);

        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.configurator.mode", currentMode.getName()).withStyle(net.minecraft.ChatFormatting.GRAY));

        data.selectedSpawnerPos().ifPresent(pos ->
            tooltipComponents.add(Component.translatable("tooltip.arenas_ld.configurator.selected_spawner", pos.toShortString()).withStyle(net.minecraft.ChatFormatting.GOLD)));

        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.shift_scroll").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }

    private static Mode selectedMode(SpawnerSelectionDataComponent data) {
        Mode[] modes = Mode.values();
        return modes[Math.floorMod(data.mode(), modes.length)];
    }
}
