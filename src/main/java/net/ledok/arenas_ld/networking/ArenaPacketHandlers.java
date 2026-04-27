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
                            if (controller.isPartyMember(player.getUUID()) && controller.hasLinkedSpawner()) {
                                ServerLevel spawnerLevel = world.getServer().getLevel(controller.getArenaSpawnerDimension());
                                if (spawnerLevel != null && spawnerLevel.getBlockEntity(controller.getArenaSpawnerPos()) instanceof MobArenaSpawnerBlockEntity spawner) {
                                    if (spawner.isArenaActive()) {
                                        return;
                                    }
                                    java.util.Set<java.util.UUID> onlinePlayers = new HashSet<>();
                                    for (java.util.UUID uuid : controller.getPartyMembers()) {
                                        if (world.getServer().getPlayerList().getPlayer(uuid) != null) {
                                            onlinePlayers.add(uuid);
                                        }
                                    }
                                    if (onlinePlayers.isEmpty()) {
                                        return;
                                    }
                                    controller.setPartyMembers(onlinePlayers);
                                    spawner.setHardcoreEnabled(controller.isHardcoreEnabled());
                                    spawner.startArena(onlinePlayers, payload.pos(), world.dimension());
                                    controller.setLocked(true);
                                }
                            }
                            break;
                        case 1: // Join Party
                            if (ModPackets.isPlayerBusy(player)) {
                                player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                                return;
                            }
                            ModPackets.removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                            controller.addPartyMember(player.getUUID());
                            break;
                        case 2: // Leave Party
                            controller.removePartyMember(player.getUUID());
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
                    for (java.util.UUID uuid : controller.getPartyMembers()) {
                        ServerPlayer partyPlayer = player.server.getPlayerList().getPlayer(uuid);
                        players.add(partyPlayer != null ? partyPlayer.getGameProfile().getName() : "Unknown");
                    }
                    ServerPlayNetworking.send(player, new MobArenaControllerInfoPayload(
                            payload.pos(),
                            controller.getCurrentWave(),
                            controller.isHardcoreEnabled(),
                            players,
                            new ArrayList<>(controller.getLeaderboard())
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
                    controller.setHardcoreEnabled(payload.hardcoreEnabled());
                }
            });
        });
    }
}
