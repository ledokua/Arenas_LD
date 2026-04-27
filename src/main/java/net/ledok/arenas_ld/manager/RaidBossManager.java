package net.ledok.arenas_ld.manager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ledok.arenas_ld.block.entity.BossSpawnerBlockEntity;
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

    private final Set<BossSpawnerBlockEntity> activeSpawners = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<UUID, PendingRestore> pendingRestores = new HashMap<>();

    public void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            BossSpawnerBlockEntity spawner = getSpawnerForPlayer(player);
            if (spawner != null) {
                spawner.handlePlayerDisconnect(player);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            server.execute(() -> {
                BossSpawnerBlockEntity spawner = getSpawnerForOfflinePlayer(player.getUUID());
                if (spawner != null) {
                    spawner.handlePlayerReconnect(player);
                    return;
                }
                handlePostBattleReconnect(player);
            });
        });
    }

    public void registerSpawner(BossSpawnerBlockEntity spawner) {
        if (spawner != null) {
            activeSpawners.add(spawner);
        }
    }

    public void unregisterSpawner(BossSpawnerBlockEntity spawner) {
        if (spawner != null) {
            activeSpawners.remove(spawner);
        }
    }

    public BossSpawnerBlockEntity getSpawnerForPlayer(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        for (BossSpawnerBlockEntity spawner : activeSpawners) {
            if (spawner.isTracked(player.getUUID()) && spawner.isRaidRunning()) {
                return spawner;
            }
        }
        return null;
    }

    public BossSpawnerBlockEntity getSpawnerForOfflinePlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (BossSpawnerBlockEntity spawner : activeSpawners) {
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
