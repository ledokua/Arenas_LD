package net.ledok.arenas_ld.event;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;

public class PlayerDeathHandler {
    public static boolean onPlayerDeath(ServerPlayer player, DamageSource source) {
        DungeonBossSpawnerBlockEntity dungeonSpawner = ArenasLdMod.DUNGEON_BOSS_MANAGER.getSpawnerForPlayer(player);
        if (dungeonSpawner != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            if (dungeonSpawner.isHardcoreEnabled()) {
                dungeonSpawner.handlePlayerHardcoreDeath(player);
            } else {
                dungeonSpawner.handlePlayerDown(player);
            }
            return true;
        }
        if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player) && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            var arenaInfo = ArenasLdMod.MOB_ARENA_MANAGER.getArenaInfo(player);
            if (arenaInfo != null) {
                var world = player.server.getLevel(arenaInfo.dimension());
                if (world != null && world.getBlockEntity(arenaInfo.pos()) instanceof net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity spawner) {
                    if (spawner.isHardcoreEnabled()) {
                        spawner.handlePlayerHardcoreDeath(player);
                        return true;
                    }
                }
            }
            player.setHealth(1.0F); // Prevent death loop
            player.setGameMode(GameType.SPECTATOR);
            player.setHealth(player.getMaxHealth() * 0.5f);
            return true; // Death was handled
        }
        return false; // Death was not handled
    }
}
