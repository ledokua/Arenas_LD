package net.ledok.arenas_ld.manager;

import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseBlockManager {
    private final Map<String, ResourceKey<Level>> groupWorld = new ConcurrentHashMap<>();
    private final Map<String, Set<BlockPos>> mobSpawnersByGroup = new ConcurrentHashMap<>();
    private final Map<BlockPos, Boolean> spawnerWinStatus = new ConcurrentHashMap<>();

    public void registerSpawner(MobSpawnerBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        mobSpawnersByGroup.computeIfAbsent(groupId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        spawnerWinStatus.put(pos, false);
        groupWorld.putIfAbsent(groupId, be.getLevel().dimension());
    }

    public void unregisterSpawner(MobSpawnerBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        spawnerWinStatus.remove(pos);
        if (mobSpawnersByGroup.containsKey(groupId)) {
            Set<BlockPos> spawners = mobSpawnersByGroup.get(groupId);
            spawners.remove(pos);
            if (spawners.isEmpty()) {
                mobSpawnersByGroup.remove(groupId);
                groupWorld.remove(groupId);
            }
        }
    }

    public void onSpawnerBattleWon(String groupId, BlockPos spawnerPos) {
        if (groupId == null || groupId.isEmpty()) return;
        spawnerWinStatus.put(spawnerPos, true);
    }

    public void onSpawnerReset(String groupId, BlockPos spawnerPos) {
        if (groupId == null || groupId.isEmpty()) return;
        spawnerWinStatus.put(spawnerPos, false);
    }

    public void start() {
        // No-op, the tick logic has been moved to the PhaseBlockEntity
    }
}
