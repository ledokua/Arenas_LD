package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.block.entity.MobArenaControllerBlockEntity;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static net.ledok.arenas_ld.networking.ModPackets.*;

final class ArenaPacketHandlers {
    private ArenaPacketHandlers() {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(MobArenaControllerActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof MobArenaControllerBlockEntity controller) {
                    switch (payload.action()) {
                        case 0: // Start Arena
                            if (ModPackets.isPlayerBusy(player)) {
                                player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                                return;
                            }
                            if (!controller.isLocked && controller.partyMembers.contains(player.getUUID())) {
                                if (controller.arenaSpawnerPos != net.minecraft.core.BlockPos.ZERO) {
                                    ServerLevel spawnerLevel = world.getServer().getLevel(controller.arenaSpawnerDimension);
                                    if (spawnerLevel != null && spawnerLevel.getBlockEntity(controller.arenaSpawnerPos) instanceof MobArenaSpawnerBlockEntity spawner) {
                                        java.util.Set<java.util.UUID> onlinePlayers = new HashSet<>();
                                        for (java.util.UUID uuid : controller.partyMembers) {
                                            if (world.getServer().getPlayerList().getPlayer(uuid) != null) {
                                                onlinePlayers.add(uuid);
                                            }
                                        }
                                        if (onlinePlayers.isEmpty()) {
                                            return;
                                        }
                                        controller.partyMembers.clear();
                                        controller.partyMembers.addAll(onlinePlayers);
                                        spawner.setHardcoreEnabled(controller.hardcoreEnabled);
                                        spawner.startArena(onlinePlayers, payload.pos(), world.dimension());
                                        controller.isLocked = true;
                                        controller.setChanged();
                                        world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                                    }
                                }
                            }
                            break;
                        case 1: // Join Party
                            if (!controller.isLocked) {
                                if (ModPackets.isPlayerBusy(player)) {
                                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                                    return;
                                }
                                ModPackets.removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                                controller.partyMembers.add(player.getUUID());
                                controller.setChanged();
                                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                            }
                            break;
                        case 2: // Leave Party
                            if (!controller.isLocked) {
                                controller.partyMembers.remove(player.getUUID());
                                controller.setChanged();
                                world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                            }
                            break;
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestMobArenaControllerInfoPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof MobArenaControllerBlockEntity controller) {
                    List<String> players = new ArrayList<>();
                    for (java.util.UUID uuid : controller.partyMembers) {
                        ServerPlayer partyPlayer = player.server.getPlayerList().getPlayer(uuid);
                        players.add(partyPlayer != null ? partyPlayer.getGameProfile().getName() : "Unknown");
                    }
                    ServerPlayNetworking.send(player, new MobArenaControllerInfoPayload(
                            payload.pos(),
                            controller.currentWave,
                            controller.hardcoreEnabled,
                            players,
                            new ArrayList<>(controller.leaderboard)
                    ));
                } else {
                    ServerPlayNetworking.send(player, new MobArenaControllerInfoPayload(payload.pos(), 0, false, List.of(), List.of()));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateMobArenaControllerSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof MobArenaControllerBlockEntity controller) {
                    if (controller.isLocked) return;
                    controller.hardcoreEnabled = payload.hardcoreEnabled();
                    controller.setChanged();
                    world.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(), 3);
                }
            });
        });
    }
}
