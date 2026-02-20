package net.ledok.arenas_ld.manager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.block.entity.MobSpawnerBlockEntity;
import net.ledok.arenas_ld.block.entity.PhaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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

    public String getEffectiveGroup(String instanceId, String templateGroupId) {
        if (instanceId == null || instanceId.isEmpty()) {
            return templateGroupId;
        }
        return instanceId + ":" + templateGroupId;
    }

    public void register(PhaseBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        String effectiveGroup = getEffectiveGroup(be.getInstanceId(), groupId);
        
        BlockPos pos = be.getBlockPos();
        ResourceKey<Level> worldKey = be.getLevel().dimension();
        groupPositions.computeIfAbsent(effectiveGroup, k -> new ArrayList<>()).add(pos);
        groupWorld.put(effectiveGroup, worldKey);
        if (!groupSolidState.containsKey(effectiveGroup)) {
            groupSolidState.put(effectiveGroup, be.getBlockState().getValue(PhaseBlock.SOLID));
        }
    }

    public void unregister(PhaseBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        String effectiveGroup = getEffectiveGroup(be.getInstanceId(), groupId);

        BlockPos pos = be.getBlockPos();
        if (groupPositions.containsKey(effectiveGroup)) {
            List<BlockPos> positions = groupPositions.get(effectiveGroup);
            positions.remove(pos);
            if (positions.isEmpty()) {
                groupPositions.remove(effectiveGroup);
                groupSolidState.remove(effectiveGroup);
                groupWorld.remove(effectiveGroup);
            }
        }
    }

    public void registerSpawner(MobSpawnerBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        
        String effectiveGroup = getEffectiveGroup(be.getInstanceId(), groupId);

        BlockPos pos = be.getBlockPos();
        mobSpawnersByGroup.computeIfAbsent(effectiveGroup, k -> ConcurrentHashMap.newKeySet()).add(pos);
        spawnerWinStatus.put(pos, false);
        groupWorld.putIfAbsent(effectiveGroup, be.getLevel().dimension());
        setGroupSolid(effectiveGroup, true);
    }

    public void unregisterSpawner(MobSpawnerBlockEntity be) {
        if (be.getLevel() == null || be.getLevel().isClientSide()) return;
        String groupId = be.getGroupId();
        if (groupId.isEmpty()) {
            return;
        }
        
        String effectiveGroup = getEffectiveGroup(be.getInstanceId(), groupId);

        BlockPos pos = be.getBlockPos();
        spawnerWinStatus.remove(pos);
        if (mobSpawnersByGroup.containsKey(effectiveGroup)) {
            Set<BlockPos> spawners = mobSpawnersByGroup.get(effectiveGroup);
            spawners.remove(pos);
            if (spawners.isEmpty()) {
                mobSpawnersByGroup.remove(effectiveGroup);
                if (!groupPositions.containsKey(effectiveGroup)) {
                    groupSolidState.remove(effectiveGroup);
                    groupWorld.remove(effectiveGroup);
                }
            }
        }
    }

    public void onSpawnerBattleWon(String groupId, String instanceId, BlockPos spawnerPos) {
        if (groupId == null || groupId.isEmpty()) return;
        String effectiveGroup = getEffectiveGroup(instanceId, groupId);
        
        spawnerWinStatus.put(spawnerPos, true);

        Set<BlockPos> spawners = mobSpawnersByGroup.get(effectiveGroup);
        if (spawners != null) {
            boolean allWon = spawners.stream().allMatch(pos -> spawnerWinStatus.getOrDefault(pos, false));
            if (allWon) {
                setGroupSolid(effectiveGroup, false);
            }
        }
    }

    public void onSpawnerReset(String groupId, String instanceId, BlockPos spawnerPos) {
        if (groupId == null || groupId.isEmpty()) return;
        String effectiveGroup = getEffectiveGroup(instanceId, groupId);
        
        spawnerWinStatus.put(spawnerPos, false);
        setGroupSolid(effectiveGroup, true);
    }

    public void setGroupSolid(String effectiveGroup, boolean solid) {
        if (effectiveGroup == null || effectiveGroup.isEmpty()) return;
        groupSolidState.put(effectiveGroup, solid);
    }

    public void claimOrphans(ServerLevel world, String groupId, String instanceId) {
        // This is a heuristic method. It finds Phase Blocks that have the same groupId but no instanceId
        // and assigns them the new instanceId.
        // Since we don't track orphans globally, we rely on the fact that they are registered with empty instanceId.
        
        String orphanKey = getEffectiveGroup("", groupId);
        if (groupPositions.containsKey(orphanKey)) {
            List<BlockPos> orphans = new ArrayList<>(groupPositions.get(orphanKey));
            for (BlockPos pos : orphans) {
                if (world.isLoaded(pos)) {
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be instanceof PhaseBlockEntity phaseBlock) {
                        // Double check it's the right world and group
                        if (phaseBlock.getGroupId().equals(groupId) && (phaseBlock.getInstanceId() == null || phaseBlock.getInstanceId().isEmpty())) {
                            phaseBlock.setInstanceId(instanceId);
                        }
                    }
                }
            }
        }
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
