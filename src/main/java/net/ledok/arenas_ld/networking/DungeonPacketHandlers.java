package net.ledok.arenas_ld.networking;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.DungeonControllerBlockEntity;
import net.ledok.arenas_ld.util.DifficultyTier;
import net.ledok.arenas_ld.util.DungeonInstanceRef;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.arenas_ld.util.InstanceState;
import net.ledok.arenas_ld.util.Lobby;
import net.ledok.arenas_ld.util.LobbyStatus;
import net.ledok.arenas_ld.util.LobbyVisibility;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.ledok.arenas_ld.networking.ModPackets.*;

final class DungeonPacketHandlers {
    private DungeonPacketHandlers() {
    }

    static void register() {
        ServerPlayNetworking.registerGlobalReceiver(DungeonControllerActionPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonControllerBlockEntity controller) {
                    switch (payload.action()) {
                        case 0: // Start Dungeon
                            if (ModPackets.isPlayerBusy(player)) {
                                player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                                return;
                            }
                            Lobby lobby = controller.getLobbyByMember(player.getUUID());
                            if (lobby == null) {
                                player.sendSystemMessage(Component.translatable("message.arenas_ld.not_in_lobby"));
                                return;
                            }
                            if (!lobby.ownerUuid.equals(player.getUUID())) {
                                player.sendSystemMessage(Component.translatable("message.arenas_ld.only_owner_can_start_dungeon"));
                                return;
                            }
                            DungeonInstanceRef instanceRef = controller.reserveFreeInstance();
                            if (instanceRef == null) {
                                int cooldownSeconds = controller.getNextAvailableCooldownSeconds();
                                if (cooldownSeconds > 0) {
                                    context.player().sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_cooldown", ModPackets.formatSeconds(cooldownSeconds)));
                                }
                                controller.setLobbyStatus(lobby.id, LobbyStatus.QUEUED);
                                return;
                            }
                            ServerLevel spawnerLevel = world.getServer().getLevel(instanceRef.dimension());
                            if (spawnerLevel == null || !(spawnerLevel.getBlockEntity(instanceRef.spawnerPos()) instanceof DungeonBossSpawnerBlockEntity spawner)) {
                                controller.releaseReservedInstance(instanceRef);
                                return;
                            }
                            HashSet<UUID> onlinePlayers = new HashSet<>();
                            if (world.getServer().getPlayerList().getPlayer(lobby.ownerUuid) != null) {
                                onlinePlayers.add(lobby.ownerUuid);
                            }
                            for (UUID uuid : lobby.members) {
                                if (world.getServer().getPlayerList().getPlayer(uuid) != null) {
                                    onlinePlayers.add(uuid);
                                }
                            }
                            if (onlinePlayers.isEmpty()) {
                                controller.releaseReservedInstance(instanceRef);
                                return;
                            }
                            spawner.setHardcoreEnabled(lobby.hardcoreEnabled);
                            if (spawner.startDungeon(onlinePlayers, payload.pos(), world.dimension(), lobby.selectedTier)) {
                                controller.setLobbyStatus(lobby.id, LobbyStatus.IN_DUNGEON);
                                controller.assignLobbyToInstance(lobby.id, instanceRef);
                                controller.setLocked(true);
                            } else {
                                controller.releaseReservedInstance(instanceRef);
                                controller.setLobbyStatus(lobby.id, LobbyStatus.OPEN);
                            }
                            break;
                        case 1: // Join Party
                            if (ModPackets.isPlayerBusy(player)) {
                                player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                                return;
                            }
                            ModPackets.removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                            if (controller.getLobbyByMember(player.getUUID()) != null) {
                                return;
                            }
                            Lobby openLobby = null;
                            for (Lobby candidate : controller.getLobbies()) {
                                if (candidate.status == LobbyStatus.OPEN
                                        && candidate.visibility == LobbyVisibility.OPEN
                                        && (1 + candidate.members.size()) < controller.getMaxPartySize()) {
                                    openLobby = candidate;
                                    break;
                                }
                            }
                            if (openLobby == null) {
                                Lobby created = controller.createLobby(player.getUUID(), player.getGameProfile().getName());
                                if (created == null) {
                                    return;
                                }
                            } else {
                                controller.addMemberToLobby(openLobby.id, player.getUUID());
                            }
                            break;
                        case 2: // Leave Party
                            controller.leaveLobby(player.getUUID());
                            break;
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(JoinDungeonLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller)) {
                    return;
                }
                if (ModPackets.isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
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
                Lobby lobby = controller.getLobbyById(lobbyId);
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

        ServerPlayNetworking.registerGlobalReceiver(RespondDungeonLobbyInvitePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                UUID lobbyId;
                try {
                    lobbyId = UUID.fromString(payload.lobbyId());
                } catch (IllegalArgumentException e) {
                    return;
                }
                DungeonControllerBlockEntity controller = ModPackets.findDungeonControllerByLobbyId(player.server, lobbyId);
                if (controller == null) {
                    return;
                }
                if (payload.accept()) {
                    if (ModPackets.isPlayerBusy(player)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
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

        ServerPlayNetworking.registerGlobalReceiver(CreateDungeonLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller)) {
                    return;
                }
                if (ModPackets.isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(net.minecraft.ChatFormatting.RED));
                    return;
                }
                ModPackets.removePlayerFromOtherLobbies(player, controller.getBlockPos(), world.dimension());
                if (controller.getLobbyByMember(player.getUUID()) != null) {
                    return;
                }
                controller.createLobby(player.getUUID(), player.getGameProfile().getName());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(DisbandDungeonLobbyPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller)) {
                    return;
                }
                controller.disbandLobby(player.getUUID());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(InviteDungeonLobbyPlayerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer owner = context.player();
                Level world = owner.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller)) {
                    return;
                }
                Lobby lobby = controller.getLobbyByMember(owner.getUUID());
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

        ServerPlayNetworking.registerGlobalReceiver(KickDungeonLobbyPlayerPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer owner = context.player();
                Level world = owner.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller)) {
                    return;
                }
                ServerPlayer target = owner.server.getPlayerList().getPlayerByName(payload.playerName());
                if (target == null || target.getUUID().equals(owner.getUUID())) {
                    return;
                }
                controller.kickFromLobby(owner.getUUID(), target.getUUID());
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RequestDungeonControllerInfoPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonControllerBlockEntity controller) {
                    DifficultyTier requestedLeaderboardTier = DifficultyTier.fromNameOrDefault(payload.leaderboardTier(), DifficultyTier.NORMAL);
                    Lobby lobby = controller.getLobbyByMember(player.getUUID());
                    List<String> players = new ArrayList<>();
                    List<DungeonControllerInfoPayload.InstanceView> instances = new ArrayList<>();
                    List<DungeonControllerInfoPayload.LobbyView> lobbies = new ArrayList<>();
                    boolean hardcoreEnabled = controller.isHardcoreEnabled();
                    String selectedTier = controller.getSelectedTier().name();
                    boolean inLobby = lobby != null;
                    boolean isLobbyOwner = false;
                    String lobbyStatus = LobbyStatus.OPEN.name();
                    int queuePosition = 0;
                    String currentLobbyVisibility = LobbyVisibility.OPEN.name();
                    boolean canManageAdmin = player.hasPermissions(2);
                    int controllerRespawnTimeTicks = controller.getRespawnTimeTicks();
                    int controllerMaxPartySize = controller.getMaxPartySize();
                    if (lobby != null) {
                        isLobbyOwner = lobby.ownerUuid.equals(player.getUUID());
                        lobbyStatus = lobby.status.name();
                        currentLobbyVisibility = lobby.visibility.name();
                        if (lobby.status == LobbyStatus.QUEUED) {
                            int pos = 1;
                            for (Lobby candidate : controller.getLobbies()) {
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
                            ServerPlayer partyPlayer = player.server.getPlayerList().getPlayer(uuid);
                            players.add(partyPlayer != null ? partyPlayer.getGameProfile().getName() : "Unknown");
                        }
                        hardcoreEnabled = lobby.hardcoreEnabled;
                        selectedTier = lobby.selectedTier.name();
                    }
                    int queuedPosCounter = 0;
                    for (Lobby candidate : controller.getLobbies()) {
                        int candidateQueuePosition = 0;
                        if (candidate.status == LobbyStatus.QUEUED) {
                            queuedPosCounter++;
                            candidateQueuePosition = queuedPosCounter;
                        }
                        String ownerName = candidate.ownerName != null && !candidate.ownerName.isEmpty() ? candidate.ownerName : "Unknown";
                        lobbies.add(new DungeonControllerInfoPayload.LobbyView(
                                candidate.id.toString(),
                                ownerName,
                                1 + candidate.members.size(),
                                controller.getMaxPartySize(),
                                candidate.visibility.name(),
                                candidate.status.name(),
                                candidateQueuePosition,
                                candidate.selectedTier.name(),
                                candidate.hardcoreEnabled,
                                candidate.pendingInvites.containsKey(player.getUUID())
                        ));
                    }
                    for (InstanceState instance : controller.getInstances()) {
                        int cooldownSeconds = instance.status() == InstanceStatus.COOLDOWN
                                ? (Math.max(0, instance.cooldownTicksRemaining()) + 19) / 20
                                : 0;
                        instances.add(new DungeonControllerInfoPayload.InstanceView(instance.status().name(), cooldownSeconds));
                    }
                    ServerPlayNetworking.send(player, new DungeonControllerInfoPayload(
                            payload.pos(),
                            controller.getRemainingDungeonTimeSeconds(),
                            controller.getDungeonCooldownSeconds(),
                            hardcoreEnabled,
                            selectedTier,
                            inLobby,
                            isLobbyOwner,
                            lobbyStatus,
                            queuePosition,
                            currentLobbyVisibility,
                            canManageAdmin,
                            controllerRespawnTimeTicks,
                            controllerMaxPartySize,
                            controller.isLocked(),
                            players,
                            ModPackets.resolveLeaderboardForTier(controller, requestedLeaderboardTier, player.server),
                            instances,
                            lobbies
                    ));
                } else {
                    ServerPlayNetworking.send(player, new DungeonControllerInfoPayload(
                            payload.pos(), 0, 0, false, DifficultyTier.NORMAL.name(),
                            false, false, LobbyStatus.OPEN.name(), 0, LobbyVisibility.OPEN.name(), false, 6000, 4, false,
                            List.of(), List.of(), List.of(), List.of()
                    ));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonControllerSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (be instanceof DungeonControllerBlockEntity controller) {
                    DifficultyTier tier = DifficultyTier.fromNameOrDefault(payload.selectedTier(), DifficultyTier.NORMAL);
                    Lobby lobby = controller.getLobbyByMember(player.getUUID());
                    if (lobby != null && lobby.ownerUuid.equals(player.getUUID())) {
                        controller.setLobbyTierAndHardcore(player.getUUID(), tier, payload.hardcoreEnabled());
                    } else if (lobby == null) {
                        controller.setHardcoreEnabled(payload.hardcoreEnabled());
                        controller.setSelectedTier(tier);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonLobbyVisibilityPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller)) {
                    return;
                }
                Lobby lobby = controller.getLobbyByMember(player.getUUID());
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

        ServerPlayNetworking.registerGlobalReceiver(UpdateDungeonControllerAdminSettingsPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) {
                    return;
                }
                Level world = player.level();
                BlockEntity be = world.getBlockEntity(payload.pos());
                if (!(be instanceof DungeonControllerBlockEntity controller)) {
                    return;
                }
                controller.setRespawnTimeTicks(payload.respawnTimeTicks());
                controller.setMaxPartySize(payload.maxPartySize());
            });
        });
    }
}
