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
import net.ledok.arenas_ld.raid.packet.RaidSetMaxPartySizePayload;
import net.ledok.arenas_ld.raid.packet.RaidSetRespawnTimeTicksPayload;
import net.ledok.arenas_ld.raid.packet.RaidMoveInstancePayload;
import net.ledok.arenas_ld.raid.packet.RaidRemoveInstancePayload;
import net.ledok.arenas_ld.raid.packet.RaidSetCloseTimerSecondsPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetCooldownTicksPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetInviteExpiryTicksPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetTierConfigPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetDeathTimePenaltyPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLootViaInboxPayload;
import net.ledok.arenas_ld.raid.packet.RaidCreateLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidDeclineInvitePayload;
import net.ledok.arenas_ld.raid.packet.RaidDeclineJoinRequestPayload;
import net.ledok.arenas_ld.raid.packet.RaidInvitePlayerPayload;
import net.ledok.arenas_ld.raid.packet.RaidJoinLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidKickFromLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidLeaveLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidRequestJoinPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLobbyHardcorePayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLobbyTierPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLobbyVisibilityPayload;
import net.ledok.arenas_ld.raid.packet.RaidStartRunPayload;
import net.ledok.arenas_ld.raid.packet.RaidToggleReadyPayload;
import net.ledok.arenas_ld.raid.packet.RaidControllerSnapshotPayload;
import net.ledok.arenas_ld.raid.run.RaidDifficulty;
import net.ledok.arenas_ld.raid.run.RaidLeaderboardEntry;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.ledok.arenas_ld.networking.ModPackets;
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
        ServerPlayNetworking.registerGlobalReceiver(RaidStartRunPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (isPlayerBusy(player)) {
                    player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
                    return;
                }
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                if (!(player.level() instanceof ServerLevel sl)) return;
                performStartRaid(player, controller);
            })
        );

        // ── Invite Player ─────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidInvitePlayerPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                boolean invited = controller.invitePlayer(player, payload.inviteeUuid());
                broadcastRaidControllerSnapshot(player, controller);
                if (invited) {
                    Lobby lobby = controller.getLobbyByMember(player.getUUID());
                    ServerPlayer invitee = player.server.getPlayerList().getPlayer(payload.inviteeUuid());
                    if (lobby != null && invitee != null) {
                        notifyInviteRaid(controller, player, invitee, lobby);
                    }
                }
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
        ServerPlayNetworking.registerGlobalReceiver(RaidKickFromLobbyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.kickFromLobby(player, payload.targetUuid());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Set Tier ──────────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetLobbyTierPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.setTier(player, payload.tier());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Set Hardcore ──────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetLobbyHardcorePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                RaidControllerBlockEntity controller = findController(player, payload.blockPos());
                if (controller == null) return;
                controller.setHardcore(player, payload.hardcore());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Set Visibility ────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetLobbyVisibilityPayload.TYPE, (payload, context) ->
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
                performToggleReadyRaid(player, controller);
            })
        );

        // ── Admin: Set Raid Name ──────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(net.ledok.arenas_ld.raid.packet.RaidSetNamePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setRaidName(payload.name());
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
        ServerPlayNetworking.registerGlobalReceiver(RaidSetRespawnTimeTicksPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setRespawnTimeTicks(payload.ticks());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Max Party Size ─────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetMaxPartySizePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setMaxPartySize(payload.size());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Remove Instance ────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidRemoveInstancePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                ResourceKey<Level> dim = parseDim(payload.dimension());
                if (dim == null) return;
                controller.removeInstance(payload.instancePos(), dim);
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Move Instance ──────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidMoveInstancePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.moveInstance(payload.fromIndex(), payload.toIndex());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Cooldown ───────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetCooldownTicksPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setCooldownTicks(payload.cooldownTicks());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Close Timer ────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetCloseTimerSecondsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setCloseTimerSeconds(payload.closeTimerSeconds());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Invite Expiry ──────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetInviteExpiryTicksPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setInviteExpiryTicks(payload.inviteExpiryTicks());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Tier Config ────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetTierConfigPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setTierConfig(payload.tier(), payload.config());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Death Time Penalty ─────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetDeathTimePenaltyPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setDeathTimePenaltyTicks(payload.ticks());
                broadcastRaidControllerSnapshot(player, controller);
            })
        );

        // ── Admin: Set Loot Via Inbox ─────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(RaidSetLootViaInboxPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!player.hasPermissions(2)) return;
                RaidControllerBlockEntity controller = findController(player, payload.controllerPos());
                if (controller == null) return;
                controller.setLootViaInbox(payload.lootViaInbox());
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

    private static final int CHAT_ACCENT = 0xA98BE8;
    private static final int CHAT_GOOD = 0x86D36C;
    private static final int CHAT_DANGER = 0xE8624A;

    /** Owner starts the raid (or queues); snapshot re-broadcast afterwards. */
    public static void performStartRaid(ServerPlayer player, RaidControllerBlockEntity controller) {
        controller.startRaid(player);
        broadcastRaidControllerSnapshot(player, controller);
    }

    /** Toggles ready, syncs, and chats the "(ready/total)" result with a clickable toggle to the party. */
    public static void performToggleReadyRaid(ServerPlayer player, RaidControllerBlockEntity controller) {
        if (!controller.toggleReady(player)) {
            return;
        }
        broadcastRaidControllerSnapshot(player, controller);
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Lobby lobby = controller.getLobbyByMember(player.getUUID());
        if (lobby == null) {
            return;
        }
        boolean nowReady = lobby.readyMembers().contains(player.getUUID());
        Component toggle = net.ledok.arenas_ld.util.LobbyChatActions.button(
            Component.translatable("message.arenas_ld.lobby.button.toggle_ready"), CHAT_ACCENT,
            net.ledok.arenas_ld.util.LobbyChatActions.readyCommand(level, controller.getBlockPos()),
            Component.translatable("message.arenas_ld.lobby.button.toggle_ready.hover"));
        Component msg = Component.translatable(
            nowReady ? "message.arenas_ld.lobby.party_ready" : "message.arenas_ld.lobby.party_unready",
            player.getGameProfile().getName(), lobby.readyMembers().size(), lobby.members().size())
            .copy().append(" ").append(toggle);
        controller.notifyLobbyMembers(lobby, msg);
    }

    /** Chats a clickable invite to the invitee so they see it even without the controller screen open. */
    public static void notifyInviteRaid(RaidControllerBlockEntity controller, ServerPlayer inviter, ServerPlayer invitee, Lobby lobby) {
        if (!(controller.getLevel() instanceof ServerLevel level)) {
            return;
        }
        net.minecraft.core.BlockPos pos = controller.getBlockPos();
        Component accept = net.ledok.arenas_ld.util.LobbyChatActions.button(
            Component.translatable("message.arenas_ld.lobby.button.accept"), CHAT_GOOD,
            net.ledok.arenas_ld.util.LobbyChatActions.acceptCommand(level, pos, lobby.lobbyId()),
            Component.translatable("message.arenas_ld.lobby.button.accept.hover"));
        Component decline = net.ledok.arenas_ld.util.LobbyChatActions.button(
            Component.translatable("message.arenas_ld.lobby.button.decline"), CHAT_DANGER,
            net.ledok.arenas_ld.util.LobbyChatActions.declineCommand(level, pos, lobby.lobbyId()),
            Component.translatable("message.arenas_ld.lobby.button.decline.hover"));
        Component open = net.ledok.arenas_ld.util.LobbyChatActions.button(
            Component.translatable("message.arenas_ld.lobby.button.open"), CHAT_ACCENT,
            net.ledok.arenas_ld.util.LobbyChatActions.openCommand(level, pos),
            Component.translatable("message.arenas_ld.lobby.button.open.hover"));

        String raidName = controller.getRaidName();
        Component nameDisplay = raidName.isBlank()
            ? Component.translatable("message.arenas_ld.lobby.unnamed_raid")
            : Component.literal(raidName);
        net.minecraft.network.chat.MutableComponent invited = Component.translatable("message.arenas_ld.lobby.invited",
            inviter.getGameProfile().getName(), nameDisplay, lobby.selectedTier().name());
        if (lobby.hardcoreEnabled()) {
            invited.append(" ").append(Component.translatable("message.arenas_ld.lobby.invited.hardcore").withStyle(ChatFormatting.RED));
        }
        invitee.sendSystemMessage(invited);
        invitee.sendSystemMessage(Component.empty().append(accept).append(" ").append(decline).append(" ").append(open));
    }

    public static void broadcastRaidControllerSnapshot(ServerPlayer actor, RaidControllerBlockEntity controller) {
        if (actor.server == null) return;
        net.ledok.arenas_ld.raid.packet.RaidControllerAdminSnapshotPayload adminSnap =
            new net.ledok.arenas_ld.raid.packet.RaidControllerAdminSnapshotPayload(controller.buildAdminData());
        for (net.minecraft.server.level.ServerPlayer target : actor.server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(target, new RaidControllerSnapshotPayload(controller.getScreenOpeningData(target)));
            if (target.hasPermissions(2)) {
                ServerPlayNetworking.send(target, adminSnap);
            }
        }
    }

    /**
     * Builds the legacy {@link RaidControllerInfoPayload} from the new block-entity state so
     * the old {@link net.ledok.arenas_ld.raid.screen.RaidControllerScreen} keeps working.
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

    private static ResourceKey<Level> parseDim(String id) {
        if (id == null || id.isEmpty()) return null;
        try {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
        } catch (Exception e) {
            return null;
        }
    }
}
