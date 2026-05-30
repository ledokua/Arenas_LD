package net.ledok.arenas_ld.arena;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.packet.ArenaAdminSetBoolPayload;
import net.ledok.arenas_ld.arena.packet.ArenaAdminSetIntPayload;
import net.ledok.arenas_ld.arena.packet.ArenaLobbyActionPayload;
import net.ledok.arenas_ld.arena.packet.ArenaLobbyTargetPayload;
import net.ledok.arenas_ld.arena.packet.ArenaMoveInstancePayload;
import net.ledok.arenas_ld.arena.packet.ArenaRemoveInstancePayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetHardcorePayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetRewardCurvePayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetVisibilityPayload;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.util.BusyStateCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side receivers for arena controller actions. Mirrors
 * {@link net.ledok.arenas_ld.raid.RaidPacketHandlers}; uses compact generic payloads.
 * (Snapshot broadcasting to open screens is added with the screens phase.)
 */
public final class ArenaPacketHandlers {
    private ArenaPacketHandlers() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ArenaLobbyActionPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                switch (payload.action()) {
                    case "create" -> {
                        if (busy(player)) { busyMessage(player); return; }
                        c.createLobby(player);
                    }
                    case "leave" -> c.leaveLobby(player);
                    case "ready" -> c.toggleReady(player);
                    case "start" -> c.startRun(player);
                    default -> {}
                }
            }));

        ServerPlayNetworking.registerGlobalReceiver(ArenaLobbyTargetPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                switch (payload.action()) {
                    case "join" -> { if (guardNotBusy(player)) c.joinLobby(player, payload.target()); }
                    case "request" -> { if (guardNotBusy(player)) c.requestJoin(player, payload.target()); }
                    case "accept_invite" -> { if (guardNotBusy(player)) c.acceptInvite(player, payload.target()); }
                    case "decline_invite" -> c.declineInvite(player, payload.target());
                    case "accept_request" -> c.acceptJoinRequest(player, payload.target());
                    case "decline_request" -> c.declineJoinRequest(player, payload.target());
                    case "invite" -> c.invitePlayer(player, payload.target());
                    case "kick" -> c.kickFromLobby(player, payload.target());
                    default -> {}
                }
            }));

        ServerPlayNetworking.registerGlobalReceiver(ArenaSetVisibilityPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                LobbyVisibility vis;
                try { vis = LobbyVisibility.valueOf(payload.visibility()); } catch (Exception e) { return; }
                c.setVisibility(player, vis);
            }));

        ServerPlayNetworking.registerGlobalReceiver(ArenaSetHardcorePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c != null) c.setHardcore(player, payload.hardcore());
            }));

        // ── Admin ──────────────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(ArenaAdminSetIntPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                int v = payload.value();
                switch (payload.key()) {
                    case "maxPartySize" -> c.setMaxPartySize(v);
                    case "respawnTime" -> c.setRespawnTimeTicks(v);
                    case "cooldown" -> c.setCooldownTicks(v);
                    case "closeTimer" -> c.setCloseTimerSeconds(v);
                    case "inviteExpiry" -> c.setInviteExpiryTicks(v);
                    case "deathPenalty" -> c.setDeathTimePenaltyTicks(v);
                    case "disconnectGrace" -> c.setDisconnectGraceTicks(v);
                    case "lobbyOfflineTimeout" -> c.setLobbyOfflineTimeoutTicks(v);
                    case "maxWave" -> c.setMaxWave(v);
                    default -> {}
                }
            }));

        ServerPlayNetworking.registerGlobalReceiver(ArenaAdminSetBoolPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                if ("lootViaInbox".equals(payload.key())) c.setLootViaInbox(payload.value());
            }));

        ServerPlayNetworking.registerGlobalReceiver(ArenaSetRewardCurvePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c != null) c.setRewardCurve(payload.currencyBase(), payload.currencyExp(), payload.xpBase(), payload.xpExp());
            }));

        ServerPlayNetworking.registerGlobalReceiver(ArenaMoveInstancePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c != null) c.moveInstance(payload.from(), payload.to());
            }));

        ServerPlayNetworking.registerGlobalReceiver(ArenaRemoveInstancePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                ResourceKey<Level> dim = parseDimension(payload.dimension());
                c.removeInstance(payload.spawnerPos(), dim);
            }));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    @Nullable
    private static ArenaControllerBlockEntity findController(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return null;
        BlockEntity be = player.level().getBlockEntity(pos);
        return be instanceof ArenaControllerBlockEntity c ? c : null;
    }

    private static boolean busy(ServerPlayer player) {
        return player != null && BusyStateCompat.isBusy(player.getUUID());
    }

    private static boolean guardNotBusy(ServerPlayer player) {
        if (busy(player)) { busyMessage(player); return false; }
        return true;
    }

    private static void busyMessage(ServerPlayer player) {
        player.sendSystemMessage(Component.translatable("message.arenas_ld.already_in_party").withStyle(ChatFormatting.RED));
    }

    private static boolean isAdmin(ServerPlayer player) {
        return player != null && (player.hasPermissions(2) || player.isCreative());
    }

    private static ResourceKey<Level> parseDimension(String id) {
        if (id == null || id.isBlank()) return Level.OVERWORLD;
        try {
            return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(id));
        } catch (Exception e) {
            return Level.OVERWORLD;
        }
    }
}
