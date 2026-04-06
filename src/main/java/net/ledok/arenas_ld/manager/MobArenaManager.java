package net.ledok.arenas_ld.manager;

import net.ledok.busylib.BusyState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobArenaManager {
    public record ArenaInfo(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final String BUSY_REASON = "arenas_ld";

    private final Map<UUID, ArenaInfo> playerArenas = new ConcurrentHashMap<>();
    private final Map<UUID, ArenaInfo> disconnectedArenaPlayers = new ConcurrentHashMap<>();

    public void addPlayer(Player player, BlockPos arenaPos, ResourceKey<Level> dimension) {
        playerArenas.put(player.getUUID(), new ArenaInfo(dimension, arenaPos));
        BusyState.setBusy(player.getUUID(), BUSY_REASON);
    }

    public void removePlayer(Player player) {
        playerArenas.remove(player.getUUID());
        BusyState.clearBusy(player.getUUID(), BUSY_REASON);
    }

    public ArenaInfo getArenaInfo(Player player) {
        return playerArenas.get(player.getUUID());
    }

    public boolean isInArena(Player player) {
        return playerArenas.containsKey(player.getUUID());
    }

    public void onPlayerDisconnect(Player player) {
        if (isInArena(player)) {
            ArenaInfo info = getArenaInfo(player);
            disconnectedArenaPlayers.put(player.getUUID(), info);
            removePlayer(player);
        }
    }

    public ArenaInfo getDisconnectedArenaInfo(UUID playerUUID) {
        return disconnectedArenaPlayers.get(playerUUID);
    }

    public void removeDisconnectedPlayer(UUID playerUUID) {
        disconnectedArenaPlayers.remove(playerUUID);
    }

    public void clear() {
        for (UUID playerId : playerArenas.keySet()) {
            BusyState.clearBusy(playerId, BUSY_REASON);
        }
        playerArenas.clear();
        disconnectedArenaPlayers.clear();
    }
}
