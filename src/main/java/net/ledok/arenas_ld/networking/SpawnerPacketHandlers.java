package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.item.SpawnerConfiguratorItem;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.DifficultyTier;
import net.ledok.arenas_ld.util.DungeonInstanceRef;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.ledok.arenas_ld.util.LinkerModeDataComponent;
import net.ledok.arenas_ld.util.RaidDifficulty;
import net.ledok.arenas_ld.util.RaidTierConfig;
import net.ledok.arenas_ld.util.SpawnerSelectionDataComponent;
import net.ledok.arenas_ld.util.TierConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import static net.ledok.arenas_ld.networking.ModPackets.*;

final class SpawnerPacketHandlers {
    private SpawnerPacketHandlers() {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(UpdateBossSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof BossSpawnerBlockEntity blockEntity) {
                    blockEntity.mobId = payload.mobId();
                    blockEntity.respawnTime = payload.respawnTime();
                    blockEntity.lootTableId = payload.lootTable();
                    blockEntity.perPlayerLootTableId = payload.perPlayerLootTable();
                    blockEntity.exitPosition = payload.exitPortalCoords();
                    blockEntity.exitDimension = ResourceKey.create(Registries.DIMENSION, payload.exitDimension());
                    blockEntity.entrancePosition = payload.enterPortalSpawnCoords();
                    blockEntity.entranceDimension = ResourceKey.create(Registries.DIMENSION, payload.enterPortalSpawnDimension());
                    blockEntity.battleRadius = payload.battleRadius();
                    blockEntity.regeneration = payload.regeneration();
                    blockEntity.skillExperiencePerWin = payload.skillExperiencePerWin();
                    blockEntity.battleTimeLimitTicks = payload.battleTimeLimitTicks();
                    blockEntity.hpScalePerPlayer = payload.hpScalePerPlayer();
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateBossSpawnerTierConfigsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof BossSpawnerBlockEntity blockEntity) {
                    CompoundTag tierConfigsTag = payload.tierConfigs();
                    blockEntity.hpScalePerPlayer = payload.hpScalePerPlayer();
                    blockEntity.getTierConfigs().clear();
                    for (RaidDifficulty tier : RaidDifficulty.values()) {
                        RaidTierConfig config = RaidTierConfig.defaultFor(tier);
                        if (tierConfigsTag.contains(tier.name(), net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                            config = RaidTierConfig.fromNbt(tierConfigsTag.getCompound(tier.name()));
                        }
                        blockEntity.getTierConfigs().put(tier, config);
                    }
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonBossSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonBossSpawnerBlockEntity blockEntity) {
                    blockEntity.applyConfig(
                            payload.mobId(),
                            payload.respawnTime(),
                            payload.dungeonCloseTimer(),
                            payload.dungeonTime(),
                            payload.lootTable(),
                            payload.perPlayerLootTable(),
                            payload.exitPositionCoords(),
                            ResourceKey.create(Registries.DIMENSION, payload.exitDimension()),
                            payload.triggerRadius(),
                            payload.battleRadius(),
                            payload.regeneration(),
                            payload.skillExperiencePerWin(),
                            payload.groupId()
                    );
                    if (world instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        DungeonControllerBlockEntity.updateControllerRespawnTimeForSpawner(
                                serverLevel.getServer(),
                                new DungeonInstanceRef(payload.pos(), serverLevel.dimension()),
                                payload.respawnTime()
                        );
                    }
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonTierConfigsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonBossSpawnerBlockEntity blockEntity) {
                    CompoundTag tierConfigsTag = payload.tierConfigs();
                    blockEntity.getTierConfigs().clear();
                    for (DifficultyTier tier : DifficultyTier.values()) {
                        TierConfig config = new TierConfig();
                        if (tierConfigsTag.contains(tier.name(), net.minecraft.nbt.Tag.TAG_COMPOUND)) {
                            config = TierConfig.fromNbt(tierConfigsTag.getCompound(tier.name()));
                        }
                        blockEntity.getTierConfigs().put(tier, config);
                    }
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobSpawnerBlockEntity blockEntity) {
                    blockEntity.mobId = payload.mobId();
                    blockEntity.respawnTime = payload.respawnTime();
                    blockEntity.lootTableId = payload.lootTable();
                    blockEntity.triggerRadius = payload.triggerRadius();
                    blockEntity.battleRadius = payload.battleRadius();
                    blockEntity.regeneration = payload.regeneration();
                    blockEntity.skillExperiencePerWin = payload.skillExperience();
                    blockEntity.mobCount = payload.mobCount();
                    blockEntity.mobSpread = payload.mobSpread();
                    blockEntity.groupId = payload.groupId();
                    blockEntity.setChanged();
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.applyConfig(
                            payload.triggerRadius(),
                            payload.battleRadius(),
                            payload.spawnDistance(),
                            payload.waveTimer(),
                            payload.additionalTime(),
                            payload.timeBetweenWaves(),
                            payload.attributeScale(),
                            payload.prepareTime(),
                            payload.exitPosition(),
                            ResourceKey.create(Registries.DIMENSION, payload.exitDimension()),
                            payload.arenaEntrancePosition(),
                            ResourceKey.create(Registries.DIMENSION, payload.arenaEntranceDimension()),
                            payload.groupId(),
                            payload.bossWaveAdditionalTime(),
                            payload.entityHighlightTime()
                    );
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaMobsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.setMobs(payload.mobs());
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaRewardsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.setRewards(payload.rewards());
                    world.sendBlockUpdated(payload.pos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateAttributesPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof AttributeProvider provider) {
                    provider.setAttributes(payload.attributes());
                    be.setChanged();
                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateEquipmentPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof EquipmentProvider provider) {
                    provider.setEquipment(payload.equipment());
                    be.setChanged();
                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CycleLinkerModePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ItemStack stack = context.player().getMainHandItem();
                if (stack.getItem() instanceof LinkerItem) {
                    LinkerModeDataComponent data = stack.getOrDefault(DataComponentRegistry.LINKER_MODE_DATA, LinkerModeDataComponent.DEFAULT);
                    int currentMode = data.mode();
                    int newMode = (currentMode + (payload.forward() ? 1 : -1) + LinkerItem.Mode.values().length) % LinkerItem.Mode.values().length;
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(newMode, data.mainSpawnerPos(), data.mainSpawnerDimension()));

                    LinkerItem.Mode mode = LinkerItem.Mode.values()[newMode];
                    context.player().sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.arenas_ld.linker.mode_changed", mode.getName()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CycleConfiguratorModePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ItemStack stack = context.player().getMainHandItem();
                if (stack.getItem() instanceof SpawnerConfiguratorItem) {
                    SpawnerSelectionDataComponent data = stack.getOrDefault(DataComponentRegistry.SPAWNER_SELECTION_DATA, SpawnerSelectionDataComponent.DEFAULT);
                    int currentMode = data.mode();
                    int newMode = (currentMode + (payload.forward() ? 1 : -1) + SpawnerConfiguratorItem.Mode.values().length) % SpawnerConfiguratorItem.Mode.values().length;
                    stack.set(DataComponentRegistry.SPAWNER_SELECTION_DATA, new SpawnerSelectionDataComponent(newMode, data.selectedSpawnerPos(), data.selectedSpawnerDimension()));

                    SpawnerConfiguratorItem.Mode mode = SpawnerConfiguratorItem.Mode.values()[newMode];
                    context.player().sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.arenas_ld.configurator.mode_changed", mode.getName()));
                }
            });
        });
    }
}
