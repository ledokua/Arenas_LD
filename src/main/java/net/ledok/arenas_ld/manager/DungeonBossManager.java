package net.ledok.arenas_ld.manager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class DungeonBossManager {
    private final Set<DungeonBossSpawnerBlockEntity> activeSpawners = Collections.newSetFromMap(new WeakHashMap<>());

    public void registerSpawner(DungeonBossSpawnerBlockEntity spawner) {
        activeSpawners.add(spawner);
    }

    public void unregisterSpawner(DungeonBossSpawnerBlockEntity spawner) {
        activeSpawners.remove(spawner);
    }

    public void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            for (DungeonBossSpawnerBlockEntity spawner : activeSpawners) {
                if (spawner.isTracked(player.getUUID())) {
                    spawner.handlePlayerDisconnect(player);
                }
            }
        });
    }
}
