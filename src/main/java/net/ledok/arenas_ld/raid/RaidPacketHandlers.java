package net.ledok.arenas_ld.raid;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.raid.blockentity.RaidControllerBlockEntity;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.raid.packet.RaidAcceptInvitePayload;
import net.ledok.arenas_ld.raid.packet.RaidAcceptJoinRequestPayload;
import net.ledok.arenas_ld.raid.packet.RaidAdminSetMaxPartySizePayload;
import net.ledok.arenas_ld.raid.packet.RaidAdminSetRespawnTimePayload;
import net.ledok.arenas_ld.raid.packet.RaidCreateLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidDeclineInvitePayload;
import net.ledok.arenas_ld.raid.packet.RaidDeclineJoinRequestPayload;
import net.ledok.arenas_ld.raid.packet.RaidInvitePlayerPayload;
import net.ledok.arenas_ld.raid.packet.RaidJoinLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidKickPlayerPayload;
import net.ledok.arenas_ld.raid.packet.RaidLeaveLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidRequestJoinPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetHardcorePayload;
import net.ledok.arenas_ld.raid.packet.RaidSetTierPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetVisibilityPayload;
import net.ledok.arenas_ld.raid.packet.RaidStartPayload;
import net.ledok.arenas_ld.raid.packet.RaidToggleReadyPayload;
import net.ledok.arenas_ld.raid.screen.RaidControllerData;
import net.ledok.arenas_ld.raid.packet.RaidControllerSnapshotPayload;
import net.ledok.arenas_ld.raid.run.RaidDifficulty;
import net.ledok.arenas_ld.raid.run.RaidLeaderboardEntry;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static net.ledok.arenas_ld.networking.ModPackets.*;

public final class RaidPacketHandlers {
    private RaidPacketHandlers() {}

    public static void register() {

        // ── Create Lobby ──────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidCreateLobbyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                if (controller.getLobbyByMember(player.getUUID()) != null) return;
                controller.createLobby(player);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Leave Lobby ───────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidLeaveLobbyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.leaveLobby(player);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Start Raid ────────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidStartPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                if (!(player.level() instanceof ServerLevel sl)) return;
                controller.startRaid(player);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Invite Player ─────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidInvitePlayerPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.invitePlayer(player, payload.playerName());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Accept Invite ─────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidAcceptInvitePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                // Find the controller that holds this lobby
                RaidControllerBlockEntity controller = findControllerByLobbyId(player.server, payload.lobbyId());
                if (controller == null) return;
                controller.acceptInvite(player, payload.lobbyId());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Decline Invite ────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidDeclineInvitePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findControllerByLobbyId(player.server, payload.lobbyId());
                if (controller == null) return;
                controller.declineInvite(player, payload.lobbyId());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Kick Player ───────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidKickPlayerPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.kickFromLobby(player, payload.targetUuid());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Set Tier ──────────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetTierPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.setTier(player, payload.tier());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Set Hardcore ──────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetHardcorePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.setHardcore(player, payload.hardcore());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Set Visibility ────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetVisibilityPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.setVisibility(player, payload.visibility());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Toggle Ready ──────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidToggleReadyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.toggleReady(player);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Join Lobby (PUBLIC) ───────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidJoinLobbyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.joinLobby(player, payload.lobbyId());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Request Join (FRIENDS) ────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidRequestJoinPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.requestJoin(player, payload.lobbyId());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Accept Join Request ───────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidAcceptJoinRequestPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.acceptJoinRequest(player, payload.requesterUuid());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Decline Join Request ──────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidDeclineJoinRequestPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.declineJoinRequest(player, payload.requesterUuid());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Respawn Time ───────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidAdminSetRespawnTimePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.setRespawnTimeTicks(payload.ticks());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Max Party Size ─────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidAdminSetMaxPartySizePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.setMaxPartySize(payload.size());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Legacy: Request Raid Controller Info (used by RaidControllerScreen) ─
        ServerPlayNetworking.registerGlobalReceiver(RequestRaidControllerInfoPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                RaidControllerInfoPayload info = buildRaidControllerInfoPayload(player, controller, payload.leaderboardDifficulty());
                ServerPlayNetworking.send(player, info);
            })
        );

        // ── Legacy: Update Raid Controller Settings (used by RaidControllerScreen) ─
        ServerPlayNetworking.registerGlobalReceiver(UpdateRaidControllerSettingsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                controller.setHardcore(player, payload.hardcoreEnabled());
                DifficultyTier tier;
                try {
                    RaidDifficulty rd = RaidDifficulty.valueOf(payload.selectedDifficulty());
                    tier = rd.toDifficultyTier();
                } catch (Exception e) {
                    tier = DifficultyTier.NORMAL;
                }
                controller.setTier(player, tier);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Legacy: Update Raid Lobby Visibility (used by RaidControllerScreen) ─
        ServerPlayNetworking.registerGlobalReceiver(UpdateRaidLobbyVisibilityPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility vis;
                if ("INVITE_ONLY".equalsIgnoreCase(payload.visibility())) {
                    vis = net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PRIVATE;
                } else {
                    vis = net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PUBLIC;
                }
                controller.setVisibility(player, vis);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Legacy: Update Raid Controller Admin Settings (used by RaidControllerScreen) ─
        ServerPlayNetworking.registerGlobalReceiver(UpdateRaidControllerAdminSettingsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                controller.setRespawnTimeTicks(payload.respawnTimeTicks());
                controller.setMaxPartySize(payload.maxPartySize());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Legacy: Raid Controller Action (used by RaidControllerScreen) ─────
        ServerPlayNetworking.registerGlobalReceiver(RaidControllerActionPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                switch (payload.action()) {
                    case 0 -> controller.startRaid(player);
                    case 1 -> controller.leaveLobby(player);
                    case 2 -> controller.leaveLobby(player);
                }
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Legacy: Create Raid Lobby (old payload, used by RaidControllerScreen) ─
        ServerPlayNetworking.registerGlobalReceiver(CreateRaidLobbyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                if (controller.getLobbyByMember(player.getUUID()) != null) return;
                controller.createLobby(player);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Legacy: Disband Raid Lobby (used by RaidControllerScreen) ────────
        ServerPlayNetworking.registerGlobalReceiver(DisbandRaidLobbyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                controller.leaveLobby(player);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Legacy: Invite Raid Lobby Player (used by RaidControllerScreen) ──
        ServerPlayNetworking.registerGlobalReceiver(InviteRaidLobbyPlayerPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                controller.invitePlayer(player, payload.playerName());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Legacy: Join Raid Lobby (old payload, used by RaidControllerScreen) ─
        ServerPlayNetworking.registerGlobalReceiver(JoinRaidLobbyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                RaidControllerBlockEntity controller = findController(player, payload.pos());
                if (controller == null) return;
                try {
                    controller.joinLobby(player, UUID.fromString(payload.lobbyId()));
                    broadcastRaidControllerSnapshot(player, controller);
                } catch (IllegalArgumentException ignored) {}
            })
        );

        // ── Legacy: Respond Raid Lobby Invite (used by RaidControllerScreen) ─
        ServerPlayNetworking.registerGlobalReceiver(RespondRaidLobbyInvitePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                UUID lobbyId;
                try {
                    lobbyId = UUID.fromString(payload.lobbyId());
                } catch (IllegalArgumentException e) {
                    return;
                }
                RaidControllerBlockEntity controller = findControllerByLobbyId(player.server, lobbyId);
                if (controller == null) return;
                if (payload.accept()) {
                    if (isPlayerBusy(player)) {
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                        return;
                    }
                    controller.acceptInvite(player, lobbyId);
                } else {
                    controller.declineInvite(player, lobbyId);
                }
                broadcastRaidControllerSnapshot(player, controller);
            })
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static RaidControllerBlockEntity findController(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        BlockEntity be = player.level().getBlockEntity(pos);
        return (be instanceof RaidControllerBlockEntity rc) ? rc : null;
    }

    private static RaidControllerBlockEntity findControllerByLobbyId(net.minecraft.server.MinecraftServer server, UUID lobbyId) {
        return ModPackets.findRaidControllerByLobbyId(server, lobbyId);
    }

    public static void broadcastRaidControllerSnapshot(ServerPlayer actor, RaidControllerBlockEntity controller) {
        if (actor.server == null) return;
        for (net.minecraft.server.level.ServerPlayer target : actor.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(target, new RaidControllerSnapshotPayload(controller.getScreenOpeningData(target)));
        }
    }

    /**
     * Builds the legacy {@link RaidControllerInfoPayload} from the new block-entity state so
     * the old {@link net.ledok.arenas_ld.screen.RaidControllerScreen} keeps working.
     */
    private static RaidControllerInfoPayload buildRaidControllerInfoPayload(
            ServerPlayer player,
            RaidControllerBlockEntity controller,
            String leaderboardDifficulty
    ) {
        UUID uuid = player.getUUID();

        // Own lobby
        net.ledok.arenas_ld.dungeon.lobby.Lobby ownLobby = controller.getLobbyByMember(uuid);

        boolean inLobby = ownLobby != null;
        boolean isOwner = inLobby && ownLobby.isOwner(uuid);

        // Lobby status → legacy name
        String lobbyStatus = "OPEN";
        if (inLobby) {
            net.ledok.arenas_ld.dungeon.lobby.LobbyStatus ls = ownLobby.status();
            lobbyStatus = switch (ls) {
                case FORMING, READY -> "OPEN";
                case IN_RUN -> "IN_DUNGEON";
                case DISBANDED -> "OPEN";
            };
            // If queued, override
            if (controller.getQueuedLobbyIds().contains(ownLobby.lobbyId())) {
                lobbyStatus = "QUEUED";
            }
        }

        // Queue position
        int queuePosition = -1;
        if (inLobby) {
            int idx = controller.getQueuedLobbyIds().indexOf(ownLobby.lobbyId());
            if (idx >= 0) queuePosition = idx + 1;
        }

        // Selected difficulty
        String selectedDifficulty = RaidDifficulty.NORMAL.name();
        boolean hardcoreEnabled = false;
        String lobbyVis = "OPEN";
        if (inLobby) {
            selectedDifficulty = RaidDifficulty.from(ownLobby.selectedTier()).name();
            hardcoreEnabled = ownLobby.hardcoreEnabled();
            net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility vis = ownLobby.visibility();
            lobbyVis = switch (vis) {
                case PUBLIC, FRIENDS -> "OPEN";
                case PRIVATE -> "INVITE_ONLY";
            };
        }

        boolean canAdmin = player.hasPermissions(2);

        // Member names (owner first)
        List<String> memberNames = new ArrayList<>();
        if (inLobby) {
            // Put owner first
            String ownerName = ownLobby.ownerName();
            memberNames.add(ownerName);
            for (UUID memberUuid : ownLobby.members()) {
                if (!memberUuid.equals(ownLobby.ownerUuid())) {
                    String name = ownLobby.memberNames().getOrDefault(memberUuid, memberUuid.toString());
                    memberNames.add(name);
                }
            }
        }

        // Leaderboard for requested difficulty
        RaidDifficulty lbDifficulty;
        try {
            lbDifficulty = RaidDifficulty.valueOf(leaderboardDifficulty);
        } catch (Exception e) {
            lbDifficulty = RaidDifficulty.NORMAL;
        }
        List<RaidLeaderboardEntry> leaderboard = resolveRaidLeaderboardForDifficulty(controller, lbDifficulty);

        // Instances
        List<RaidControllerInfoPayload.InstanceView> instanceViews = new ArrayList<>();
        for (RaidControllerBlockEntity.RaidInstanceState inst : controller.getInstances()) {
            int cooldownSecs = inst.status() == InstanceStatus.COOLDOWN
                ? inst.cooldownTicksRemaining() / 20
                : 0;
            instanceViews.add(new RaidControllerInfoPayload.InstanceView(inst.status().name(), cooldownSecs));
        }

        // Lobby views (visible: PUBLIC + FRIENDS, not IN_RUN/DISBANDED)
        List<RaidControllerInfoPayload.LobbyView> lobbyViews = new ArrayList<>();
        for (net.ledok.arenas_ld.dungeon.lobby.Lobby lobby : controller.getLobbies()) {
            if (lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.IN_RUN
                    || lobby.status() == net.ledok.arenas_ld.dungeon.lobby.LobbyStatus.DISBANDED) {
                continue;
            }
            if (lobby.visibility() == net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility.PRIVATE) {
                continue;
            }
            // Determine legacy status
            String lStatus;
            if (controller.getQueuedLobbyIds().contains(lobby.lobbyId())) {
                lStatus = "QUEUED";
            } else {
                lStatus = switch (lobby.status()) {
                    case FORMING, READY -> "OPEN";
                    case IN_RUN -> "IN_DUNGEON";
                    case DISBANDED -> "OPEN";
                };
            }
            String lVis = switch (lobby.visibility()) {
                case PUBLIC, FRIENDS -> "OPEN";
                case PRIVATE -> "INVITE_ONLY";
            };
            int lQueuePos = controller.getQueuedLobbyIds().indexOf(lobby.lobbyId());
            // Check if player has invite for this lobby
            boolean invited = false;
            for (net.ledok.arenas_ld.dungeon.lobby.PendingInvite inv : controller.getPendingInvitesForPlayer(uuid)) {
                if (inv.lobbyId().equals(lobby.lobbyId())) {
                    invited = true;
                    break;
                }
            }
            lobbyViews.add(new RaidControllerInfoPayload.LobbyView(
                lobby.lobbyId().toString(),
                lobby.ownerName(),
                lobby.members().size(),
                controller.getMaxPartySize(),
                lVis,
                lStatus,
                lQueuePos >= 0 ? lQueuePos + 1 : -1,
                RaidDifficulty.from(lobby.selectedTier()).name(),
                invited
            ));
        }

        return new RaidControllerInfoPayload(
            controller.getBlockPos(),
            inLobby,
            isOwner,
            lobbyStatus,
            queuePosition,
            selectedDifficulty,
            hardcoreEnabled,
            lobbyVis,
            canAdmin,
            controller.getRespawnTimeTicks(),
            controller.getMaxPartySize(),
            memberNames,
            leaderboard,
            instanceViews,
            lobbyViews
        );
    }

    private static List<RaidLeaderboardEntry> resolveRaidLeaderboardForDifficulty(
            RaidControllerBlockEntity controller,
            RaidDifficulty difficulty
    ) {
        return ModPackets.resolveRaidLeaderboardForDifficulty(controller, difficulty);
    }
}
