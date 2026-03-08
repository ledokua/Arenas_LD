package net.ledok.arenas_ld.event;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.manager.MobArenaManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class PlayerTickHandler {
    public static void onPlayerTick(ServerPlayer player) {
        if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
            MobArenaManager.ArenaInfo arenaInfo = ArenasLdMod.MOB_ARENA_MANAGER.getArenaInfo(player);
            if (arenaInfo != null) {
                ServerLevel world = player.server.getLevel(arenaInfo.dimension());
                if (world != null && world.getBlockEntity(arenaInfo.pos()) instanceof MobArenaSpawnerBlockEntity spawner) {
                    if (player.distanceToSqr(arenaInfo.pos().getX(), arenaInfo.pos().getY(), arenaInfo.pos().getZ()) > spawner.battleRadius * spawner.battleRadius) {
                        BlockPos enterPos = arenaInfo.pos().offset(spawner.arenaEntrancePosition);
                        ServerLevel entranceLevel = player.server.getLevel(spawner.arenaEntranceDimension);
                        if (entranceLevel != null) {
                            player.teleportTo(entranceLevel, enterPos.getX() + 0.5, enterPos.getY(), enterPos.getZ() + 0.5, player.getYRot(), player.getXRot());
                        }
                    }
                }
            }
        }
    }
}
