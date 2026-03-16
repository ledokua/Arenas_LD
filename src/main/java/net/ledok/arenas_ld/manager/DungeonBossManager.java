package net.ledok.arenas_ld.manager;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class DungeonBossManager {
    public record DungeonExitInfo(ResourceKey<Level> dimension, BlockPos pos) {}

    private final Set<DungeonBossSpawnerBlockEntity> activeSpawners = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<String, DungeonKey> registeredDungeons = new HashMap<>();
    private final Map<java.util.UUID, Set<String>> subscriptions = new HashMap<>();
    private final Map<String, Integer> lastCooldownSeconds = new HashMap<>();
    private final Set<DungeonKey> forcedDungeons = new HashSet<>();
    private final Map<java.util.UUID, DungeonExitInfo> disconnectedDungeonPlayers = new HashMap<>();
    private boolean loaded = false;

    public void registerSpawner(DungeonBossSpawnerBlockEntity spawner) {
        activeSpawners.add(spawner);
    }

    public void unregisterSpawner(DungeonBossSpawnerBlockEntity spawner) {
        activeSpawners.remove(spawner);
    }

    public DungeonBossSpawnerBlockEntity getSpawnerForPlayer(ServerPlayer player) {
        ensureLoaded(player.server);
        for (DungeonBossSpawnerBlockEntity spawner : activeSpawners) {
            if (spawner.isTracked(player.getUUID()) && spawner.isDungeonRunning()) {
                return spawner;
            }
        }
        return null;
    }

    public boolean registerDungeon(MinecraftServer server, String name, BlockPos pos, ResourceKey<Level> dimension) {
        ensureLoaded(server);
        if (name == null || name.isEmpty()) return false;
        if (registeredDungeons.containsKey(name)) return false;
        registeredDungeons.put(name, new DungeonKey(pos, dimension));
        save(server);
        return true;
    }

    public boolean unregisterDungeon(MinecraftServer server, String name) {
        ensureLoaded(server);
        DungeonKey key = registeredDungeons.get(name);
        if (key != null) {
            registeredDungeons.remove(name);
            unforceDungeonChunk(server, key);
            for (Set<String> sub : subscriptions.values()) {
                sub.remove(name);
            }
            lastCooldownSeconds.remove(name);
            save(server);
            return true;
        }
        return false;
    }

    public boolean isRegistered(MinecraftServer server, String name) {
        ensureLoaded(server);
        return registeredDungeons.containsKey(name);
    }

    public boolean subscribe(ServerPlayer player, String name) {
        ensureLoaded(player.server);
        if (!registeredDungeons.containsKey(name)) return false;
        subscriptions.computeIfAbsent(player.getUUID(), k -> new HashSet<>()).add(name);
        save(player.server);
        return true;
    }

    public boolean unsubscribe(ServerPlayer player, String name) {
        ensureLoaded(player.server);
        Set<String> set = subscriptions.get(player.getUUID());
        if (set == null) return false;
        boolean removed = set.remove(name);
        save(player.server);
        return removed;
    }

    public void unsubscribeAll(ServerPlayer player) {
        ensureLoaded(player.server);
        subscriptions.remove(player.getUUID());
        save(player.server);
    }

    public Set<String> getSubscriptions(ServerPlayer player) {
        ensureLoaded(player.server);
        return subscriptions.getOrDefault(player.getUUID(), Set.of());
    }

    public DungeonKey getRegisteredDungeon(MinecraftServer server, String name) {
        ensureLoaded(server);
        return registeredDungeons.get(name);
    }

    public Map<String, DungeonKey> getRegisteredDungeons(MinecraftServer server) {
        ensureLoaded(server);
        return registeredDungeons;
    }

    public void initialize() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            for (DungeonBossSpawnerBlockEntity spawner : new java.util.ArrayList<>(activeSpawners)) {
                if (spawner.isTracked(player.getUUID())) {
                    if (spawner.exitPositionCoords != null && !spawner.exitPositionCoords.equals(BlockPos.ZERO)) {
                        BlockPos absoluteExitPos = spawner.getBlockPos().offset(spawner.exitPositionCoords);
                        disconnectedDungeonPlayers.put(player.getUUID(), new DungeonExitInfo(spawner.exitPositionDimension, absoluteExitPos));
                    }
                    spawner.handlePlayerDisconnect(player, net.minecraft.network.chat.Component.translatable("message.arenas_ld.dungeon_left.reason.disconnected"));
                }
            }
        });
    }

    public record DungeonKey(BlockPos pos, ResourceKey<Level> dimension) { }

    public DungeonExitInfo getDisconnectedDungeonInfo(java.util.UUID playerId) {
        return disconnectedDungeonPlayers.get(playerId);
    }

    public void removeDisconnectedDungeonPlayer(java.util.UUID playerId) {
        disconnectedDungeonPlayers.remove(playerId);
    }

    public void tick(MinecraftServer server) {
        ensureLoaded(server);
        if (registeredDungeons.isEmpty()) {
            clearForcedChunks(server);
            return;
        }

        syncForcedChunks(server);
        for (Map.Entry<String, DungeonKey> entry : registeredDungeons.entrySet()) {
            String name = entry.getKey();
            DungeonKey key = entry.getValue();
            int cooldownSeconds = -1;
            var level = server.getLevel(key.dimension());
            if (level != null && level.getBlockEntity(key.pos()) instanceof DungeonBossSpawnerBlockEntity spawner) {
                cooldownSeconds = spawner.getRespawnCooldownSeconds();
            }

            int last = lastCooldownSeconds.getOrDefault(name, -1);
            lastCooldownSeconds.put(name, cooldownSeconds);

            if (cooldownSeconds == 0 && last != 0) {
                notifySubscribers(server, name);
            }
        }
    }

    private void syncForcedChunks(MinecraftServer server) {
        Set<DungeonKey> current = new HashSet<>(registeredDungeons.values());
        for (DungeonKey key : new HashSet<>(forcedDungeons)) {
            if (!current.contains(key)) {
                ServerLevel level = server.getLevel(key.dimension());
                if (level != null) {
                    ChunkPos chunkPos = new ChunkPos(key.pos());
                    level.setChunkForced(chunkPos.x, chunkPos.z, false);
                }
                forcedDungeons.remove(key);
            }
        }
        for (DungeonKey key : current) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null) {
                ChunkPos chunkPos = new ChunkPos(key.pos());
                level.setChunkForced(chunkPos.x, chunkPos.z, true);
                forcedDungeons.add(key);
            }
        }
    }

    private void clearForcedChunks(MinecraftServer server) {
        for (DungeonKey key : new HashSet<>(forcedDungeons)) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level != null) {
                ChunkPos chunkPos = new ChunkPos(key.pos());
                level.setChunkForced(chunkPos.x, chunkPos.z, false);
            }
        }
        forcedDungeons.clear();
    }

    private void unforceDungeonChunk(MinecraftServer server, DungeonKey key) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level != null) {
            ChunkPos chunkPos = new ChunkPos(key.pos());
            level.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        forcedDungeons.remove(key);
    }

    private void notifySubscribers(MinecraftServer server, String name) {
        for (Map.Entry<java.util.UUID, Set<String>> entry : subscriptions.entrySet()) {
            if (!entry.getValue().contains(name)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_ready", name)
                        .withStyle(ChatFormatting.GREEN));
            }
        }
    }

    private void ensureLoaded(MinecraftServer server) {
        if (loaded) return;
        DungeonSubscriptionsData data = getData(server);
        registeredDungeons.clear();
        subscriptions.clear();
        registeredDungeons.putAll(data.registeredDungeons);
        subscriptions.putAll(data.subscriptions);
        loaded = true;
    }

    private void save(MinecraftServer server) {
        if (!loaded) return;
        DungeonSubscriptionsData data = getData(server);
        data.registeredDungeons.clear();
        data.registeredDungeons.putAll(registeredDungeons);
        data.subscriptions.clear();
        data.subscriptions.putAll(subscriptions);
        data.setDirty();
    }

    private DungeonSubscriptionsData getData(MinecraftServer server) {
        SavedData.Factory<DungeonSubscriptionsData> factory = new SavedData.Factory<>(
                DungeonSubscriptionsData::new,
                DungeonSubscriptionsData::load,
                null
        );
        return server.overworld().getDataStorage().computeIfAbsent(factory, DungeonSubscriptionsData.DATA_NAME);
    }
}
