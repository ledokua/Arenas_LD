package net.ledok.arenas_ld.manager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.PhaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseBlockManager {
    private final Map<String, List<BlockPos>> groupPositions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> groupSolidState = new ConcurrentHashMap<>();
    private final Map<String, ResourceKey<Level>> groupWorld = new ConcurrentHashMap<>();

    private final Map<String, Set<BlockPos>> mobSpawnersByGroup = new ConcurrentHashMap<>();
    private final Map<BlockPos, Boolean> spawnerWinStatus = new ConcurrentHashMap<>();

    public void register(PhaseBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        ResourceKey<Level> worldKey = be.getLevel().dimension();
        groupPositions.computeIfAbsent(groupId, k -> new ArrayList<>()).add(pos);
        groupWorld.put(groupId, worldKey);
        if (!groupSolidState.containsKey(groupId)) {
            groupSolidState.put(groupId, be.getBlockState().getValue(PhaseBlock.SOLID));
        }
    }

    public void unregister(PhaseBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        if (groupPositions.containsKey(groupId)) {
            List<BlockPos> positions = groupPositions.get(groupId);
            positions.remove(pos);
            if (positions.isEmpty()) {
                groupPositions.remove(groupId);
                groupSolidState.remove(groupId);
                groupWorld.remove(groupId);
            }
        }
    }

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
        setGroupSolid(groupId, true);
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
                if (!groupPositions.containsKey(groupId)) {
                    groupSolidState.remove(groupId);
                    groupWorld.remove(groupId);
                }
            }
        }
    }

    public void onSpawnerBattleWon(String groupId, BlockPos spawnerPos) {
        if (groupId == null || groupId.isEmpty()) return;
        spawnerWinStatus.put(spawnerPos, true);

        Set<BlockPos> spawners = mobSpawnersByGroup.get(groupId);
        if (spawners != null) {
            boolean allWon = spawners.stream().allMatch(pos -> spawnerWinStatus.getOrDefault(pos, false));
            if (allWon) {
                setGroupSolid(groupId, false);
            }
        }
    }

    public void onSpawnerReset(String groupId, BlockPos spawnerPos) {
        if (groupId == null || groupId.isEmpty()) return;
        spawnerWinStatus.put(spawnerPos, false);
        setGroupSolid(groupId, true);
    }

    public void setGroupSolid(String groupId, boolean solid) {
        if (groupId == null || groupId.isEmpty()) return;
        groupSolidState.put(groupId, solid);
    }

    public void start() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (String groupId : groupPositions.keySet()) {
                if(!groupPositions.containsKey(groupId)) continue;

                Boolean solid = groupSolidState.get(groupId);
                if (solid == null) continue;

                if (!groupWorld.containsKey(groupId)) continue;
                ServerLevel world = server.getLevel(groupWorld.get(groupId));
                if (world != null) {
                    for (BlockPos pos : new ArrayList<>(groupPositions.get(groupId))) {
                        BlockState currentState = world.getBlockState(pos);
                        if (currentState.getBlock() instanceof PhaseBlock && currentState.getValue(PhaseBlock.SOLID) != solid) {
                            world.setBlock(pos, currentState.setValue(PhaseBlock.SOLID, solid), 3);
                        }
                    }
                }
            }
        });
    }
}
