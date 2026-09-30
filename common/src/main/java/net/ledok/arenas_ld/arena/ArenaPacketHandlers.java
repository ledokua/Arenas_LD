package net.ledok.arenas_ld.arena;

import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.arena.run.ObjectiveType;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerMobsPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerRewardsPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSpawnerSettingsPayload;
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

import java.util.ArrayList;
import java.util.List;
import net.ledok.arenas_ld.platform.ArenasNetwork;

/**
 * Server-side receivers for arena controller actions. Mirrors
 * {@link net.ledok.arenas_ld.raid.RaidPacketHandlers}; uses compact generic payloads.
 * (Snapshot broadcasting to open screens is added with the screens phase.)
 */
public final class ArenaPacketHandlers {
    private ArenaPacketHandlers() {}

    public static void register() {
        ArenasNetwork.registerServerReceiver(ArenaLobbyActionPayload.TYPE, (payload, context) ->
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

        ArenasNetwork.registerServerReceiver(ArenaLobbyTargetPayload.TYPE, (payload, context) ->
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

        ArenasNetwork.registerServerReceiver(ArenaSetVisibilityPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                LobbyVisibility vis;
                try { vis = LobbyVisibility.valueOf(payload.visibility()); } catch (Exception e) { return; }
                c.setVisibility(player, vis);
            }));

        ArenasNetwork.registerServerReceiver(ArenaSetHardcorePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c != null) c.setHardcore(player, payload.hardcore());
            }));

        // ── Admin ──────────────────────────────────────────────────────────────
        ArenasNetwork.registerServerReceiver(ArenaAdminSetIntPayload.TYPE, (payload, context) ->
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
                    case "hpScalePct" -> c.setHpScalePerPlayer(Math.max(0, v) / 100.0);
                    case "hpWavePct" -> c.setHpScalePerWave(Math.max(0, v) / 100.0);
                    default -> {}
                }
            }));

        ArenasNetwork.registerServerReceiver(ArenaAdminSetBoolPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                // No bool keys currently handled.
            }));

        ArenasNetwork.registerServerReceiver(ArenaSetRewardCurvePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c != null) c.setRewardCurve(payload.currencyBase(), payload.currencyExp(), payload.xpBase(), payload.xpExp());
            }));

        ArenasNetwork.registerServerReceiver(ArenaMoveInstancePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c != null) c.moveInstance(payload.spawnerPos(), parseDimension(payload.dimension()), payload.direction());
            }));

        ArenasNetwork.registerServerReceiver(ArenaRemoveInstancePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaControllerBlockEntity c = findController(player, payload.controllerPos());
                if (c == null) return;
                ResourceKey<Level> dim = parseDimension(payload.dimension());
                c.removeInstance(payload.spawnerPos(), dim);
            }));

        // ── Spawner config ──────────────────────────────────────────────────────
        ArenasNetwork.registerServerReceiver(ArenaSpawnerSettingsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaSpawnerBlockEntity s = findSpawner(player, payload.spawnerPos());
                if (s == null) return;
                s.setBattleRadius(payload.battleRadius());
                s.setSpawnDistance(payload.spawnDistance());
                s.setAttributeScale(payload.attributeScale());
                s.setEntityHighlightTime(payload.entityHighlightTime());
                s.setWaveTimer(payload.waveTimer());
                s.setAdditionalTime(payload.additionalTime());
                s.setTimeBetweenWaves(payload.timeBetweenWaves());
                s.setPrepareTime(payload.prepareTime());
                s.setBossWaveAdditionalTime(payload.bossWaveAdditionalTime());
                s.setBossEveryNWaves(payload.bossEveryN());
                s.setEliteEveryNWaves(payload.eliteEveryN());
                s.setObjectiveEveryNWaves(payload.objectiveEveryN());
                List<ObjectiveType> objs = new ArrayList<>();
                for (String o : payload.objectives()) {
                    try { objs.add(ObjectiveType.valueOf(o)); } catch (Exception ignored) {}
                }
                s.setEnabledObjectives(objs);
            }));

        ArenasNetwork.registerServerReceiver(ArenaSpawnerMobsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaSpawnerBlockEntity s = findSpawner(player, payload.spawnerPos());
                if (s != null) s.setMobs(payload.mobs());
            }));

        ArenasNetwork.registerServerReceiver(ArenaSpawnerRewardsPayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                ServerPlayer player = context.player();
                if (!isAdmin(player)) return;
                ArenaSpawnerBlockEntity s = findSpawner(player, payload.spawnerPos());
                if (s != null) s.setRewards(payload.rewards());
            }));
    }

    @Nullable
    private static ArenaSpawnerBlockEntity findSpawner(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) return null;
        BlockEntity be = player.level().getBlockEntity(pos);
        return be instanceof ArenaSpawnerBlockEntity s ? s : null;
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
