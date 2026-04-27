package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.block.entity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.arenas_ld.util.LobbyStatus;
import net.ledok.arenas_ld.util.LobbyVisibility;
import net.ledok.arenas_ld.util.RaidDifficulty;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.ledok.arenas_ld.networking.ModPackets.*;

final class RaidPacketHandlers {
    private RaidPacketHandlers() {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(RaidControllerActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof RaidControllerBlockEntity controller)) {
                    return;
                }
                switch (payload.action()) {
                    case 0: // Start/Queue Raid
                        if (ModPackets.isPlayerBusy(player)) {
                            player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                            return;
                        }
                        RaidControllerBlockEntity.Lobby lobby = controller.getLobbyByMember(player.getUUID());
                        if (lobby == null) {
                            player.sendSystemMessage(Component.translatable("message.arenas_ld.not_in_lobby"));
                            return;
                        }
                        if (!lobby.ownerUuid.equals(player.getUUID())) {
                            player.sendSystemMessage(Component.translatable("message.arenas_ld.only_owner_can_start_dungeon"));
                            return;
                        }
                        if (!(world instanceof ServerLevel serverLevel)) {
                            return;
                        }
                        controller.startRaid(serverLevel, lobby);
                        break;
                    case 1: // Leave Queue
                        RaidControllerBlockEntity.Lobby queueLobby = controller.getLobbyByMember(player.getUUID());
                        if (queueLobby == null || !queueLobby.ownerUuid.equals(player.getUUID())) {
                            return;
                        }
                        if (queueLobby.status == LobbyStatus.QUEUED) {
                            controller.setLobbyStatus(queueLobby.id, LobbyStatus.OPEN);
                        }
                        break;
                    case 2: // Leave Lobby
                        controller.leaveLobby(player.getUUID());
                        break;
                    case 3: // Create Lobby
                        if (ModPackets.isPlayerBusy(player)) {
                            player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                            return;
                        }
                        ModPackets.removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                        if (controller.getLobbyByMember(player.getUUID()) != null) {
                            return;
                        }
                        controller.createLobby(player.getUUID(), player.getGameProfile().getName());
                        break;
                    default:
                        break;
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JoinRaidLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof RaidControllerBlockEntity controller)) {
                    return;
                }
                if (ModPackets.isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                ModPackets.removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                if (controller.getLobbyByMember(player.getUUID()) != null) {
                    return;
                }
                UUID lobbyId;
                try {
                    lobbyId = UUID.fromString(payload.lobbyId());
                } catch (IllegalArgumentException e) {
                    return;
                }
                RaidControllerBlockEntity.Lobby lobby = controller.getLobbyById(lobbyId);
                if (lobby == null || lobby.status != LobbyStatus.OPEN) {
                    return;
                }
                if (lobby.visibility == LobbyVisibility.INVITE_ONLY) {
                    long nowTick = player.serverLevel().getGameTime();
                    boolean accepted = controller.acceptInvite(lobby.id, player.getUUID(), player.getGameProfile().getName(), nowTick);
                    if (!accepted) {
                        return;
                    }
                    return;
                }
                controller.addMemberToLobby(lobby.id, player.getUUID());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RespondRaidLobbyInvitePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                UUID lobbyId;
                try {
                    lobbyId = UUID.fromString(payload.lobbyId());
                } catch (IllegalArgumentException e) {
                    return;
                }
                RaidControllerBlockEntity controller = ModPackets.findRaidControllerByLobbyId(player.server, lobbyId);
                if (controller == null) {
                    return;
                }
                if (payload.accept()) {
                    if (ModPackets.isPlayerBusy(player)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                        return;
                    }
                    ModPackets.removePlayerFromOtherLobbies(
                            player,
                            controller.getBlockPos(),
                            controller.getLevel() != null ? controller.getLevel().dimension() : Level.OVERWORLD
                    );
                    long nowTick = player.serverLevel().getGameTime();
                    controller.acceptInvite(lobbyId, player.getUUID(), player.getGameProfile().getName(), nowTick);
                } else {
                    controller.declineInvite(lobbyId, player.getUUID());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(CreateRaidLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof RaidControllerBlockEntity controller)) {
                    return;
                }
                if (ModPackets.isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                ModPackets.removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                if (controller.getLobbyByMember(player.getUUID()) != null) {
                    return;
                }
                controller.createLobby(player.getUUID(), player.getGameProfile().getName());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DisbandRaidLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof RaidControllerBlockEntity controller)) {
                    return;
                }
                controller.disbandLobby(player.getUUID());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(InviteRaidLobbyPlayerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer owner = context.player();
                Level world = owner.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof RaidControllerBlockEntity controller)) {
                    return;
                }
                RaidControllerBlockEntity.Lobby lobby = controller.getLobbyByMember(owner.getUUID());
                if (lobby == null || !lobby.ownerUuid.equals(owner.getUUID())) {
                    return;
                }
                ServerPlayer target = owner.server.getPlayerList().getPlayerByName(payload.playerName());
                if (target == null || target.getUUID().equals(owner.getUUID())) {
                    return;
                }
                long nowTick = owner.serverLevel().getGameTime();
                long expireAt = nowTick + (5L * 60L * 20L);
                if (!controller.invitePlayer(owner.getUUID(), target.getUUID(), expireAt)) {
                    return;
                }
                ModPackets.sendClickableLobbyInvite(target, owner.getGameProfile().getName(), lobby.id);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestRaidControllerInfoPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof RaidControllerBlockEntity controller) {
                    RaidDifficulty requestedLeaderboardDifficulty =
                            RaidDifficulty.fromNameOrDefault(payload.leaderboardDifficulty(), RaidDifficulty.NORMAL);
                    RaidControllerBlockEntity.Lobby lobby = controller.getLobbyByMember(player.getUUID());
                    List<String> players = new ArrayList<>();
                    List<RaidControllerInfoPayload.InstanceView> instances = new ArrayList<>();
                    List<RaidControllerInfoPayload.LobbyView> lobbies = new ArrayList<>();
                    boolean inLobby = lobby != null;
                    boolean isLobbyOwner = false;
                    String lobbyStatus = LobbyStatus.OPEN.name();
                    int queuePosition = 0;
                    String selectedDifficulty = RaidDifficulty.NORMAL.name();
                    boolean hardcoreEnabled = false;
                    String currentLobbyVisibility = LobbyVisibility.OPEN.name();
                    boolean canManageAdmin = player.hasPermissions(2);
                    int controllerRespawnTimeTicks = controller.getRespawnTimeTicks();
                    int controllerMaxPartySize = controller.getMaxPartySize();
                    if (lobby != null) {
                        isLobbyOwner = lobby.ownerUuid.equals(player.getUUID());
                        lobbyStatus = lobby.status.name();
                        selectedDifficulty = lobby.selectedDifficulty.name();
                        hardcoreEnabled = lobby.hardcoreEnabled;
                        currentLobbyVisibility = lobby.visibility.name();
                        if (lobby.status == LobbyStatus.QUEUED) {
                            int pos = 1;
                            for (RaidControllerBlockEntity.Lobby candidate : controller.getLobbies()) {
                                if (candidate.status != LobbyStatus.QUEUED) {
                                    continue;
                                }
                                if (candidate.id.equals(lobby.id)) {
                                    queuePosition = pos;
                                    break;
                                }
                                pos++;
                            }
                        }
                        ServerPlayer owner = player.server.getPlayerList().getPlayer(lobby.ownerUuid);
                        String ownerName = owner != null ? owner.getGameProfile().getName() : lobby.ownerName;
                        if (ownerName == null || ownerName.isEmpty()) {
                            ownerName = "Unknown";
                        }
                        players.add(ownerName);
                        for (UUID uuid : lobby.members) {
                            ServerPlayer lobbyPlayer = player.server.getPlayerList().getPlayer(uuid);
                            players.add(lobbyPlayer != null ? lobbyPlayer.getGameProfile().getName() : "Unknown");
                        }
                    }

                    int queuedPosCounter = 0;
                    for (RaidControllerBlockEntity.Lobby candidate : controller.getLobbies()) {
                        int candidateQueuePosition = 0;
                        if (candidate.status == LobbyStatus.QUEUED) {
                            queuedPosCounter++;
                            candidateQueuePosition = queuedPosCounter;
                        }
                        String ownerName = candidate.ownerName != null && !candidate.ownerName.isEmpty()
                                ? candidate.ownerName : "Unknown";
                        lobbies.add(new RaidControllerInfoPayload.LobbyView(
                                candidate.id.toString(),
                                ownerName,
                                1 + candidate.members.size(),
                                controller.getMaxPartySize(),
                                candidate.visibility.name(),
                                candidate.status.name(),
                                candidateQueuePosition,
                                candidate.selectedDifficulty.name(),
                                candidate.pendingInvites.containsKey(player.getUUID())
                        ));
                    }

                    for (RaidControllerBlockEntity.RaidInstanceState instance : controller.getInstances()) {
                        int cooldownSeconds = instance.status() == InstanceStatus.COOLDOWN
                                ? (Math.max(0, instance.cooldownTicksRemaining()) + 19) / 20
                                : 0;
                        instances.add(new RaidControllerInfoPayload.InstanceView(instance.status().name(), cooldownSeconds));
                    }

                    ServerPlayNetworking.send(player, new RaidControllerInfoPayload(
                            payload.pos(),
                            inLobby,
                            isLobbyOwner,
                            lobbyStatus,
                            queuePosition,
                            selectedDifficulty,
                            hardcoreEnabled,
                            currentLobbyVisibility,
                            canManageAdmin,
                            controllerRespawnTimeTicks,
                            controllerMaxPartySize,
                            players,
                            ModPackets.resolveRaidLeaderboardForDifficulty(controller, requestedLeaderboardDifficulty),
                            instances,
                            lobbies
                    ));
                } else {
                    ServerPlayNetworking.send(player, new RaidControllerInfoPayload(
                            payload.pos(),
                            false,
                            false,
                            LobbyStatus.OPEN.name(),
                            0,
                            RaidDifficulty.NORMAL.name(),
                            false,
                            LobbyVisibility.OPEN.name(),
                            false,
                            6000,
                            10,
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of()
                    ));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateRaidControllerSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof RaidControllerBlockEntity controller)) {
                    return;
                }
                RaidDifficulty difficulty = RaidDifficulty.fromNameOrDefault(payload.selectedDifficulty(), RaidDifficulty.NORMAL);
                RaidControllerBlockEntity.Lobby lobby = controller.getLobbyByMember(player.getUUID());
                if (lobby != null && lobby.ownerUuid.equals(player.getUUID())) {
                    controller.setLobbyDifficulty(player.getUUID(), difficulty);
                    controller.setLobbyHardcoreEnabled(player.getUUID(), payload.hardcoreEnabled());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateRaidLobbyVisibilityPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof RaidControllerBlockEntity controller)) {
                    return;
                }
                RaidControllerBlockEntity.Lobby lobby = controller.getLobbyByMember(player.getUUID());
                if (lobby == null || !lobby.ownerUuid.equals(player.getUUID())) {
                    return;
                }
                LobbyVisibility visibility;
                try {
                    visibility = LobbyVisibility.valueOf(payload.visibility().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return;
                }
                controller.setLobbyVisibility(player.getUUID(), visibility);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateRaidControllerAdminSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof RaidControllerBlockEntity controller)) {
                    return;
                }
                controller.setRespawnTimeTicks(payload.respawnTimeTicks());
                controller.setMaxPartySize(payload.maxPartySize());
            });
        });
    }
}
