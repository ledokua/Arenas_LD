package net.ledok.arenas_ld.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobArenaManager {
    private final Map<UUID, BlockPos> playerArenas = new ConcurrentHashMap<>();

    public void addPlayer(Player player, BlockPos arenaPos) {
        playerArenas.put(player.getUUID(), arenaPos);
    }

    public void removePlayer(Player player) {
        playerArenas.remove(player.getUUID());
    }

    public BlockPos getArenaPos(Player player) {
        return playerArenas.get(player.getUUID());
    }

    public boolean isInArena(Player player) {
        return playerArenas.containsKey(player.getUUID());
    }
}
