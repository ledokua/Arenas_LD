package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.minecraft.ChatFormatting;
import net.ledok.arenas_ld.dungeon.packet.DbsClearRoomsPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsMoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsRemoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.AcceptInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.CreateLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.InvitePlayerPayload;
import net.ledok.arenas_ld.dungeon.packet.JoinLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.KickFromLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.LeaveLobbyPayload;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.packet.RoomClearDoorPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearSpawnersPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomRemoveSpawnerPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomResetPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyTierPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyHardcorePayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyVisibilityPayload;
import net.ledok.arenas_ld.dungeon.packet.StartRunPayload;
import net.ledok.arenas_ld.dungeon.packet.ToggleReadyPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateDbsEntrancePayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateDbsEntityDefPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateMobSpawnerEntityDefPayload;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle;
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
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
                    blockEntity.battleRadius = payload.battleRadius();
                    blockEntity.regeneration = payload.regeneration();
                    blockEntity.skillExperiencePerWin = payload.skillExperiencePerWin();
                    blockEntity.battleTimeLimitTicks = payload.battleTimeLimitTicks();
                    blockEntity.hpScalePerPlayer = payload.hpScalePerPlayer();
                    markDirtyAndSync(world, blockEntity);
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
                    markDirtyAndSync(world, blockEntity);
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
                    markDirtyAndSync(world, blockEntity);
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
                    markDirtyAndSync(world, blockEntity);
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
                    markDirtyAndSync(world, blockEntity);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.applyConfig(
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
                            payload.bossWaveAdditionalTime(),
                            payload.entityHighlightTime()
                    );
                    markDirtyAndSync(world, blockEntity);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaMobsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.setMobs(payload.mobs());
                    markDirtyAndSync(world, blockEntity);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaRewardsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                if (world.getBlockEntity(payload.pos()) instanceof MobArenaSpawnerBlockEntity blockEntity) {
                    blockEntity.setRewards(payload.rewards());
                    markDirtyAndSync(world, blockEntity);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateAttributesPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof AttributeProvider provider) {
                    provider.setAttributes(payload.attributes());
                    markDirtyAndSync(world, be);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateEquipmentPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                Level world = context.player().level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof EquipmentProvider provider) {
                    provider.setEquipment(payload.equipment());
                    markDirtyAndSync(world, be);
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
                    stack.set(DataComponentRegistry.LINKER_MODE_DATA, new LinkerModeDataComponent(newMode, data.mainSpawnerPos(), data.mainSpawnerDimension(), data.respawnPointCount()));

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

        ServerPlayNetworking.registerGlobalReceiver(RoomRemoveSpawnerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof RoomControllerBlockEntity room) {
                    room.removeSpawner(payload.spawnerPos());
                    markDirtyAndSync(world, room);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RoomClearSpawnersPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof RoomControllerBlockEntity room) {
                    room.clearSpawners();
                    markDirtyAndSync(world, room);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RoomClearDoorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof RoomControllerBlockEntity room) {
                    room.setDoorPos(null);
                    markDirtyAndSync(world, room);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RoomResetPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof RoomControllerBlockEntity room && world instanceof ServerLevel serverLevel) {
                    room.reset(serverLevel);
                    markDirtyAndSync(world, room);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobSpawnerEntityDefPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity spawner) {
                    spawner.setEntityDefinition(spawner.getEntityDefinition().withMobId(payload.mobId()));
                    markDirtyAndSync(world, spawner);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDbsEntityDefPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity spawner) {
                    spawner.setEntityDefinition(spawner.getEntityDefinition().withMobId(payload.mobId()));
                    markDirtyAndSync(world, spawner);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDbsEntrancePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity spawner) {
                    if (payload.setToPlayer()) {
                        spawner.setEntrance(player.blockPosition(), player.level().dimension());
                    } else {
                        ResourceLocation parsedDim = ResourceLocation.tryParse(payload.dimension());
                        ResourceKey<Level> dim;
                        if (parsedDim != null) {
                            dim = ResourceKey.create(Registries.DIMENSION, parsedDim);
                        } else {
                            dim = spawner.getEntranceDimension();
                            player.sendSystemMessage(Component.translatable("gui.arenas_ld.spawner_v2.invalid_dimension")
                                .withStyle(ChatFormatting.YELLOW));
                        }
                        spawner.setEntrance(new net.minecraft.core.BlockPos(payload.x(), payload.y(), payload.z()), dim);
                    }
                    markDirtyAndSync(world, spawner);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DbsRemoveRoomPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity spawner) {
                    spawner.removeRoom(payload.roomPos());
                    markDirtyAndSync(world, spawner);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DbsMoveRoomPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity spawner) {
                    spawner.moveRoom(payload.fromIndex(), payload.toIndex());
                    markDirtyAndSync(world, spawner);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DbsClearRoomsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.room_controller.no_permission"));
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity spawner) {
                    spawner.clearRooms();
                    markDirtyAndSync(world, spawner);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CreateLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.createLobby(player);
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JoinLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.joinLobby(player, payload.lobbyId());
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(InvitePlayerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.invitePlayer(player, payload.inviteeUuid());
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AcceptInvitePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.acceptInvite(player, payload.lobbyId());
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DeclineInvitePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.declineInvite(player, payload.lobbyId());
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(LeaveLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.leaveLobby(player);
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(KickFromLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.kickFromLobby(player, payload.targetUuid());
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetLobbyTierPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.setLobbyTier(player, payload.tier());
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetLobbyHardcorePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.setLobbyHardcore(player, payload.hardcore());
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetLobbyVisibilityPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.setLobbyVisibility(player, payload.visibility());
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ToggleReadyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.toggleReady(player);
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(StartRunPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller
                    && world instanceof ServerLevel serverLevel) {
                    controller.startRun(player).ifPresent(instancePos -> {
                        Lobby lobby = controller.getLobbies().stream()
                            .filter(l -> l.isOwner(player.getUUID()))
                            .findFirst()
                            .orElse(null);
                        if (lobby == null) {
                            return;
                        }
                        List<UUID> party = new ArrayList<>(lobby.members());
                        DungeonRun run = DungeonRunLifecycle.startRun(
                            serverLevel,
                            controller,
                            instancePos,
                            party,
                            lobby.selectedTier(),
                            lobby.hardcoreEnabled()
                        );
                        if (run == null) {
                            ArenasLdMod.LOGGER.warn("startRun failed for lobby {}", lobby.lobbyId());
                        }
                    });
                    markDirtyAndSync(world, controller);
                }
            });
        });
    }

    private static void markDirtyAndSync(Level world, BlockEntity blockEntity) {
        blockEntity.setChanged();
        world.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
    }
}
