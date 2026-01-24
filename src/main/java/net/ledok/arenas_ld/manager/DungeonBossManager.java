package net.ledok.arenas_ld.manager;

import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

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
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggedOut);
    }

    private void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            for (DungeonBossSpawnerBlockEntity spawner : activeSpawners) {
                if (spawner.isTracked(player.getUUID())) {
                    spawner.handlePlayerDisconnect(player);
                }
            }
        }
    }
}
