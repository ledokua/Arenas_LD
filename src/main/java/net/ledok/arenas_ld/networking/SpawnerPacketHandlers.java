package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.minecraft.ChatFormatting;
import net.ledok.arenas_ld.dungeon.packet.DbsClearRoomsPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsMoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsRemoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonBossSpawnerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonControllerAdminSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.AddDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.AcceptInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.AcceptJoinRequestPayload;
import net.ledok.arenas_ld.dungeon.packet.CreateLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineJoinRequestPayload;
import net.ledok.arenas_ld.dungeon.packet.DungeonControllerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.InvitePlayerPayload;
import net.ledok.arenas_ld.dungeon.packet.JoinLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.RequestJoinPayload;
import net.ledok.arenas_ld.dungeon.packet.KickFromLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.LeaveLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.MoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.MobSpawnerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.RemoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.blockentity.RoomControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.packet.RoomClearDoorPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearSpawnersPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomControllerSnapshotPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomRemoveSpawnerPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomResetPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyTierPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyHardcorePayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyVisibilityPayload;
import net.ledok.arenas_ld.dungeon.packet.SetCloseTimerSecondsPayload;
import net.ledok.arenas_ld.dungeon.packet.SetCooldownTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetInviteExpiryTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLootViaInboxPayload;
import net.ledok.arenas_ld.dungeon.packet.SetMaxPartySizePayload;
import net.ledok.arenas_ld.dungeon.packet.SetRespawnTimeTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetDeathTimePenaltyPayload;
import net.ledok.arenas_ld.dungeon.packet.SetTierConfigPayload;
import net.ledok.arenas_ld.dungeon.packet.StartRunPayload;
import net.ledok.arenas_ld.dungeon.packet.ToggleReadyPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateDbsEntityDefPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateMobSpawnerEntityDefPayload;
import net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.item.SpawnerConfiguratorItem;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.ledok.arenas_ld.util.LinkerModeDataComponent;
import net.ledok.arenas_ld.util.SpawnerSelectionDataComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
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
                if (be instanceof RaidBossSpawnerBlockEntity blockEntity) {
                    blockEntity.setMobId(payload.mobId());
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
                    broadcastRoomControllerSnapshot(player, room);
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
                    broadcastRoomControllerSnapshot(player, room);
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
                    broadcastRoomControllerSnapshot(player, room);
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
                    broadcastRoomControllerSnapshot(player, room);
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
                    spawner.setEntityDefinition(spawner.getEntityDefinition()
                        .withMobId(payload.mobId())
                        .withSpawnCount(payload.spawnCount())
                        .withSpawnOffsets(payload.spawnOffsets()));
                    markDirtyAndSync(world, spawner);
                    broadcastMobSpawnerSnapshot(player, spawner);
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
                    broadcastDungeonBossSpawnerSnapshot(player, spawner);
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
                    broadcastDungeonBossSpawnerSnapshot(player, spawner);
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
                    broadcastDungeonBossSpawnerSnapshot(player, spawner);
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
                    broadcastDungeonBossSpawnerSnapshot(player, spawner);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestJoinPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.requestJoin(player, payload.lobbyId());
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AcceptJoinRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.acceptJoinRequest(player, payload.requesterUuid());
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DeclineJoinRequestPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.blockPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.declineJoinRequest(player, payload.requesterUuid());
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    broadcastDungeonControllerSnapshot(player, controller);
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
                    Lobby lobby = controller.getLobbies().stream()
                        .filter(l -> l.isOwner(player.getUUID()))
                        .findFirst()
                        .orElse(null);
                    if (lobby == null) {
                        return;
                    }
                    controller.startRun(player).ifPresent(instancePos -> {
                        List<UUID> party = new ArrayList<>(lobby.members());
                        DungeonRun run = DungeonRunLifecycle.startRun(
                            serverLevel,
                            controller,
                            instancePos,
                            party,
                            lobby.selectedTier(),
                            lobby.hardcoreEnabled(),
                            lobby.ownerName()
                        );
                        if (run == null) {
                            ArenasLdMod.LOGGER.warn("startRun failed for lobby {}", lobby.lobbyId());
                        }
                    });
                    markDirtyAndSync(world, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AddDungeonInstancePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.addInstance(payload.instancePos());
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveDungeonInstancePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    boolean removed = controller.removeInstance(payload.instancePos());
                    if (removed && controller.getPendingInstanceRemovals().contains(payload.instancePos())) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_controller_admin.pending_removal")
                            .withStyle(ChatFormatting.YELLOW));
                    }
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerSnapshot(player, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MoveDungeonInstancePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.moveInstance(payload.fromIndex(), payload.toIndex());
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetCooldownTicksPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    boolean accepted = controller.setCooldownTicks(payload.ticks());
                    if (!accepted) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value")
                            .withStyle(ChatFormatting.YELLOW));
                    }
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetCloseTimerSecondsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    boolean accepted = controller.setCloseTimerSeconds(payload.seconds());
                    if (!accepted) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value")
                            .withStyle(ChatFormatting.YELLOW));
                    }
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetMaxPartySizePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    boolean accepted = controller.setMaxPartySize(payload.size());
                    if (!accepted) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value")
                            .withStyle(ChatFormatting.YELLOW));
                    }
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetRespawnTimeTicksPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    boolean accepted = controller.setRespawnTimeTicks(payload.ticks());
                    if (!accepted) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value")
                            .withStyle(ChatFormatting.YELLOW));
                    }
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetDeathTimePenaltyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    boolean accepted = controller.setDeathTimePenaltyTicks(payload.ticks());
                    if (!accepted) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value")
                            .withStyle(ChatFormatting.YELLOW));
                    }
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetInviteExpiryTicksPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    boolean accepted = controller.setInviteExpiryTicks(payload.ticks());
                    if (!accepted) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value")
                            .withStyle(ChatFormatting.YELLOW));
                    }
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetTierConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.setTierConfig(payload.tier(), payload.config());
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetLootViaInboxPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.controllerPos());
                if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
                    controller.setLootViaInbox(payload.lootViaInbox());
                    markDirtyAndSync(world, controller);
                    broadcastDungeonControllerAdminSnapshot(player, controller);
                }
            });
        });
    }

    private static void markDirtyAndSync(Level world, BlockEntity blockEntity) {
        blockEntity.setChanged();
        world.sendBlockUpdated(blockEntity.getBlockPos(), blockEntity.getBlockState(), blockEntity.getBlockState(), 3);
    }

    private static void broadcastDungeonControllerSnapshot(ServerPlayer actor, net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller) {
        if (actor.server == null) {
            return;
        }
        for (ServerPlayer target : actor.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(target, new DungeonControllerSnapshotPayload(controller.getScreenOpeningData(target)));
        }
    }

    private static void broadcastRoomControllerSnapshot(ServerPlayer actor, RoomControllerBlockEntity room) {
        if (actor.server == null) {
            return;
        }
        for (ServerPlayer target : actor.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(target, new RoomControllerSnapshotPayload(room.getScreenOpeningData(target)));
        }
    }

    private static void broadcastMobSpawnerSnapshot(
        ServerPlayer actor,
        net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity spawner
    ) {
        if (actor.server == null) {
            return;
        }
        for (ServerPlayer target : actor.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(target, new MobSpawnerSnapshotPayload(spawner.getScreenOpeningData(target)));
        }
    }

    private static void broadcastDungeonBossSpawnerSnapshot(
        ServerPlayer actor,
        net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity spawner
    ) {
        if (actor.server == null) {
            return;
        }
        for (ServerPlayer target : actor.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(target, new DungeonBossSpawnerSnapshotPayload(spawner.getScreenOpeningData(target)));
        }
    }

    private static void broadcastDungeonControllerAdminSnapshot(
        ServerPlayer actor,
        net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller
    ) {
        if (actor.server == null) {
            return;
        }
        net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminMenuProvider provider =
            new net.ledok.arenas_ld.dungeon.screen.DungeonControllerAdminMenuProvider(controller);
        for (ServerPlayer target : actor.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(target, new DungeonControllerAdminSnapshotPayload(provider.getScreenOpeningData(target)));
        }
    }
}
