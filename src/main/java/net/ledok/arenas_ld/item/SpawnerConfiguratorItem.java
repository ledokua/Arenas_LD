package net.ledok.arenas_ld.item;

import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
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

import java.util.List;
import java.util.Optional;

public class SpawnerConfiguratorItem extends Item {
    public SpawnerConfiguratorItem(Properties properties) {
        super(properties);
    }

    public enum Mode {
        SPAWNER_SELECTION("item.arenas_ld.configurator.mode.spawner_selection"),
        ENTRANCE_POSITION("item.arenas_ld.configurator.mode.entrance_position"),
        MOB_SPAWN_POSITION("item.arenas_ld.configurator.mode.mob_spawn_position"),
        RESPAWN_POSITION("item.arenas_ld.configurator.mode.respawn_position");

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
        Mode currentMode = Mode.values()[data.mode()];
        BlockEntity clickedBlockEntity = world.getBlockEntity(clickedPos);

        if (player.isShiftKeyDown()) {
            if (clickedBlockEntity instanceof RaidBossSpawnerBlockEntity
                || clickedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity
                || clickedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity
                || clickedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity
                || clickedBlockEntity instanceof MobArenaSpawnerBlockEntity) {
                stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, new SpawnerSelectionDataComponent(data.mode(), Optional.of(clickedPos), Optional.of(world.dimension())));
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawner_selected", clickedPos.toShortString()));
                return InteractionResult.SUCCESS;
            }
        }

        Optional<BlockPos> selectedSpawnerPosOpt = data.selectedSpawnerPos();
        Optional<ResourceKey<Level>> selectedSpawnerDimOpt = data.selectedSpawnerDimension();
        if (selectedSpawnerPosOpt.isEmpty() || selectedSpawnerDimOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.no_spawner_selected"));
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

        if (!(selectedBlockEntity instanceof RaidBossSpawnerBlockEntity)
            && !(selectedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity)
            && !(selectedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity)
            && !(selectedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity)
            && !(selectedBlockEntity instanceof MobArenaSpawnerBlockEntity)) {
            player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.invalid_spawner"));
            stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
            return InteractionResult.FAIL;
        }

        // Calculate relative position
        BlockPos relativePos = clickedPos.subtract(selectedSpawnerPos);
        ResourceKey<Level> clickedDimension = world.dimension();

        switch (currentMode) {
            case ENTRANCE_POSITION:
                if (selectedBlockEntity instanceof MobArenaSpawnerBlockEntity mobArenaSpawner) {
                    mobArenaSpawner.setArenaEntrancePosition(relativePos, clickedDimension);
                } else if (selectedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity newDbs) {
                    newDbs.setEntrancePosition(clickedPos, clickedDimension);
                } else if (selectedBlockEntity instanceof RaidBossSpawnerBlockEntity bossSpawner) {
                    bossSpawner.setEntrancePosition(clickedPos, clickedDimension);
                }
                player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.entrance_pos_set", clickedPos.toShortString(), clickedDimension.location().toString()));
                break;
            case MOB_SPAWN_POSITION:
                if (!selectedSpawnerDim.equals(clickedDimension)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_wrong_dimension"));
                    return InteractionResult.FAIL;
                }
                // Mob spawns ON the clicked block (one block above it), stored relative to the spawner.
                BlockPos spawnOffset = clickedPos.above().subtract(selectedSpawnerPos);
                if (selectedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity mobSpawner) {
                    EntityDefinition def = mobSpawner.getEntityDefinition();
                    java.util.List<BlockPos> offsets = new java.util.ArrayList<>(def.spawnOffsets());
                    int spawnCount = def.spawnCount();
                    if (offsets.remove(spawnOffset)) {
                        spawnCount = Math.max(1, spawnCount - 1);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        offsets.add(spawnOffset);
                        spawnCount = Math.min(64, spawnCount + 1);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_added", clickedPos.toShortString(), offsets.size()));
                    }
                    mobSpawner.setEntityDefinition(def.withSpawnOffsets(offsets).withSpawnCount(spawnCount));
                } else if (selectedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity bossSpawner) {
                    // Boss spawner holds a single spawn position: placing replaces it, clicking the same one clears it.
                    EntityDefinition def = bossSpawner.getEntityDefinition();
                    java.util.List<BlockPos> current = def.spawnOffsets();
                    if (current.size() == 1 && current.get(0).equals(spawnOffset)) {
                        bossSpawner.setEntityDefinition(def.withSpawnOffsets(java.util.List.of()));
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        bossSpawner.setEntityDefinition(def.withSpawnOffsets(java.util.List.of(spawnOffset)));
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_added", clickedPos.toShortString(), 1));
                    }
                } else if (selectedBlockEntity instanceof RaidBossSpawnerBlockEntity raidBossSpawner) {
                    // Raid boss spawner also holds a single spawn position: placing replaces it, clicking the same one clears it.
                    EntityDefinition def = raidBossSpawner.getEntityDefinition();
                    java.util.List<BlockPos> current = def.spawnOffsets();
                    if (current.size() == 1 && current.get(0).equals(spawnOffset)) {
                        raidBossSpawner.setEntityDefinition(def.withSpawnOffsets(java.util.List.of()));
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        raidBossSpawner.setEntityDefinition(def.withSpawnOffsets(java.util.List.of(spawnOffset)));
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_added", clickedPos.toShortString(), 1));
                    }
                } else {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.spawn_pos_needs_mob_spawner"));
                    return InteractionResult.FAIL;
                }
                break;
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
                } else if (selectedBlockEntity instanceof net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity room) {
                    // Room holds a single respawn point: placing replaces it, clicking the same one clears it.
                    if (respawnAbsolute.equals(room.getRespawnPos())) {
                        room.setRespawnPos(null);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_removed", clickedPos.toShortString()));
                    } else {
                        room.setRespawnPos(respawnAbsolute);
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_added", clickedPos.toShortString(), 1));
                    }
                } else {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.configurator.respawn_pos_needs_owner"));
                    return InteractionResult.FAIL;
                }
                break;
            }
            default:
                return InteractionResult.PASS;
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
        Mode currentMode = Mode.values()[data.mode()];

        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.configurator.mode", currentMode.getName()).withStyle(net.minecraft.ChatFormatting.GRAY));

        data.selectedSpawnerPos().ifPresent(pos -> {
            tooltipComponents.add(Component.translatable("tooltip.arenas_ld.configurator.selected_spawner", pos.toShortString()).withStyle(net.minecraft.ChatFormatting.GOLD));
        });

        tooltipComponents.add(Component.translatable("tooltip.arenas_ld.linker.shift_scroll").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
