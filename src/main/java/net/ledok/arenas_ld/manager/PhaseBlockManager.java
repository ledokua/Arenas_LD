package net.ledok.arenas_ld.manager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ledok.block.PhaseBlock;
import net.ledok.block.entity.PhaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PhaseBlockManager {
    private final Map<String, List<BlockPos>> groupPositions = new ConcurrentHashMap<>();
    private final Map<String, Boolean> groupSolidState = new ConcurrentHashMap<>();
    private final Map<String, ResourceKey<Level>> groupWorld = new ConcurrentHashMap<>();

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
