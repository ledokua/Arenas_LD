package net.ledok.arenas_ld.event;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

public class PlayerTickHandler {
    public static void onPlayerTick(ServerPlayer player) {
        if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player) && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            BlockPos arenaPos = ArenasLdMod.MOB_ARENA_MANAGER.getArenaPos(player);
            if (arenaPos != null) {
                Level world = player.level();
                if (world.getBlockEntity(arenaPos) instanceof MobArenaSpawnerBlockEntity spawner) {
                    if (player.distanceToSqr(arenaPos.getX(), arenaPos.getY(), arenaPos.getZ()) > spawner.battleRadius * spawner.battleRadius) {
                        BlockPos enterPos = arenaPos.offset(spawner.enterPortalDestCoords);
                        player.teleportTo(enterPos.getX() + 0.5, enterPos.getY(), enterPos.getZ() + 0.5);
                    }
                }
            }
        }
    }
}
