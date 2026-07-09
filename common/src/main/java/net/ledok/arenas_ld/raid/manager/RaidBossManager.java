package net.ledok.arenas_ld.raid.manager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.util.PendingRestoreStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class RaidBossManager {
    private final Set<RaidBossSpawnerBlockEntity> activeSpawners = Collections.newSetFromMap(new WeakHashMap<>());

    public void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            RaidBossSpawnerBlockEntity spawner = getSpawnerForPlayer(player);
            if (spawner != null) {
                spawner.handlePlayerDisconnect(player);
            }
        });

        // Deferred to the next tick — server.execute runs inline on the server thread, which
        // would teleport from inside the JOIN event (mid placeNewPlayer) and desync the client.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            net.ledok.arenas_ld.util.ServerTaskScheduler.nextTick(s -> {
                ServerPlayer player = s.getPlayerList().getPlayer(uuid);
                if (player == null) {
                    return;
                }
                RaidBossSpawnerBlockEntity spawner = getSpawnerForOfflinePlayer(uuid);
                if (spawner != null) {
                    spawner.handlePlayerReconnect(player);
                    return;
                }
                handlePostBattleReconnect(player);
            });
        });
    }

    public void registerSpawner(RaidBossSpawnerBlockEntity spawner) {
        if (spawner != null) {
            activeSpawners.add(spawner);
        }
    }

    public void unregisterSpawner(RaidBossSpawnerBlockEntity spawner) {
        if (spawner != null) {
            activeSpawners.remove(spawner);
        }
    }

    public RaidBossSpawnerBlockEntity getSpawnerForPlayer(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        return getSpawnerForOfflinePlayer(player.getUUID());
    }

    public RaidBossSpawnerBlockEntity getSpawnerForOfflinePlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (RaidBossSpawnerBlockEntity spawner : activeSpawners) {
            if (spawner.isTracked(uuid) && spawner.isRaidRunning()) {
                return spawner;
            }
        }
        return null;
    }

    /**
     * Ejects a player who was removed from a raid (forfeited past grace, or offline when the run
     * ended) on their next login, using the persisted {@link PendingRestoreStore}.
     */
    public void handlePostBattleReconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerReturnPoint rp = PendingRestoreStore.get(player.server).take(player.getUUID());
        if (rp == null) {
            return;
        }
        player.setGameMode(rp.previousGameMode());
        player.setHealth(player.getMaxHealth());
        ServerLevel target = player.server.getLevel(rp.dimension());
        if (target != null) {
            player.teleportTo(target, rp.pos().x(), rp.pos().y(), rp.pos().z(), rp.yaw(), rp.pitch());
        }
        player.sendSystemMessage(Component.translatable("message.arenas_ld.raid.run_ended_while_away")
            .withStyle(ChatFormatting.YELLOW));
    }
}
