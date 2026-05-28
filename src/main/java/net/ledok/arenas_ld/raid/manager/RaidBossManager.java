package net.ledok.arenas_ld.raid.manager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.WeakHashMap;

public class RaidBossManager {
    public record PendingRestore(BlockPos exitPos, ResourceKey<Level> exitDim, GameType gameMode) {}

    private final Set<RaidBossSpawnerBlockEntity> activeSpawners = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<UUID, PendingRestore> pendingRestores = new HashMap<>();

    public void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            RaidBossSpawnerBlockEntity spawner = getSpawnerForPlayer(player);
            if (spawner != null) {
                spawner.handlePlayerDisconnect(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            server.execute(() -> {
                RaidBossSpawnerBlockEntity spawner = getSpawnerForOfflinePlayer(player.getUUID());
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
        for (RaidBossSpawnerBlockEntity spawner : activeSpawners) {
            if (spawner.isTracked(player.getUUID()) && spawner.isRaidRunning()) {
                return spawner;
            }
        }
        return null;
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

    public void addPendingRestore(UUID uuid, BlockPos exitPos, ResourceKey<Level> exitDim, GameType gameMode) {
        if (uuid == null || exitPos == null || exitDim == null || gameMode == null) {
            return;
        }
        pendingRestores.put(uuid, new PendingRestore(exitPos.immutable(), exitDim, gameMode));
    }

    public void handlePostBattleReconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PendingRestore restore = pendingRestores.remove(player.getUUID());
        if (restore == null) {
            return;
        }
        var exitLevel = player.server.getLevel(restore.exitDim());
        player.setGameMode(restore.gameMode());
        player.setHealth(player.getMaxHealth());
        if (exitLevel != null) {
            BlockPos exitPos = restore.exitPos();
            player.teleportTo(
                    exitLevel,
                    exitPos.getX() + 0.5,
                    exitPos.getY(),
                    exitPos.getZ() + 0.5,
                    player.getYRot(),
                    player.getXRot()
            );
        }
    }
}
