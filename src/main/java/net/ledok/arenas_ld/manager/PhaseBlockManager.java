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

    private String getUniqueGroupId(String groupId, BlockPos pos) {
        long chunkX = pos.getX() >> 9; // 512 blocks
        long chunkZ = pos.getZ() >> 9;
        return groupId + "@" + chunkX + "," + chunkZ;
    }

    public void register(PhaseBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        
        String uniqueGroupId = getUniqueGroupId(groupId, be.getBlockPos());

        BlockPos pos = be.getBlockPos();
        ResourceKey<Level> worldKey = be.getLevel().dimension();
        groupPositions.computeIfAbsent(uniqueGroupId, k -> new ArrayList<>()).add(pos);
        groupWorld.put(uniqueGroupId, worldKey);
        if (!groupSolidState.containsKey(uniqueGroupId)) {
            groupSolidState.put(uniqueGroupId, be.getBlockState().getValue(PhaseBlock.SOLID));
        }
    }

    public void unregister(PhaseBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        
        String uniqueGroupId = getUniqueGroupId(groupId, be.getBlockPos());

        BlockPos pos = be.getBlockPos();
        if (groupPositions.containsKey(uniqueGroupId)) {
            List<BlockPos> positions = groupPositions.get(uniqueGroupId);
            positions.remove(pos);
            if (positions.isEmpty()) {
                groupPositions.remove(uniqueGroupId);
                groupSolidState.remove(uniqueGroupId);
                groupWorld.remove(uniqueGroupId);
            }
        }
    }

    public void registerSpawner(MobSpawnerBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        
        String uniqueGroupId = getUniqueGroupId(groupId, be.getBlockPos());

        BlockPos pos = be.getBlockPos();
        mobSpawnersByGroup.computeIfAbsent(uniqueGroupId, k -> ConcurrentHashMap.newKeySet()).add(pos);
        spawnerWinStatus.put(pos, false);
        groupWorld.putIfAbsent(uniqueGroupId, be.getLevel().dimension());
        setGroupSolid(uniqueGroupId, true);
    }

    public void unregisterSpawner(MobSpawnerBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        
        String uniqueGroupId = getUniqueGroupId(groupId, be.getBlockPos());

        BlockPos pos = be.getBlockPos();
        spawnerWinStatus.remove(pos);
        if (mobSpawnersByGroup.containsKey(uniqueGroupId)) {
            Set<BlockPos> spawners = mobSpawnersByGroup.get(uniqueGroupId);
            spawners.remove(pos);
            if (spawners.isEmpty()) {
                mobSpawnersByGroup.remove(uniqueGroupId);
                if (!groupPositions.containsKey(uniqueGroupId)) {
                    groupSolidState.remove(uniqueGroupId);
                    groupWorld.remove(uniqueGroupId);
                }
            }
        }
    }

    public void onSpawnerBattleWon(String groupId, BlockPos spawnerPos) {
        if (groupId == null || groupId.isEmpty()) return;
        
        String uniqueGroupId = getUniqueGroupId(groupId, spawnerPos);

        spawnerWinStatus.put(spawnerPos, true);

        Set<BlockPos> spawners = mobSpawnersByGroup.get(uniqueGroupId);
        if (spawners != null) {
            boolean allWon = spawners.stream().allMatch(pos -> spawnerWinStatus.getOrDefault(pos, false));
            if (allWon) {
                setGroupSolid(uniqueGroupId, false);
            }
        }
    }

    public void onSpawnerReset(String groupId, BlockPos spawnerPos) {
        if (groupId == null || groupId.isEmpty()) return;
        
        String uniqueGroupId = getUniqueGroupId(groupId, spawnerPos);

        spawnerWinStatus.put(spawnerPos, false);
        setGroupSolid(uniqueGroupId, true);
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
                        if (world.isLoaded(pos)) {
                            BlockState currentState = world.getBlockState(pos);
                            if (currentState.getBlock() instanceof PhaseBlock && currentState.getValue(PhaseBlock.SOLID) != solid) {
                                world.setBlock(pos, currentState.setValue(PhaseBlock.SOLID, solid), 3);
                            }
                        }
                    }
                }
            }
        });
    }
}
