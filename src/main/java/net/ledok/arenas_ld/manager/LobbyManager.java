package net.ledok.arenas_ld.manager;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class LobbyManager {

    private static final ConcurrentHashMap<UUID, Lobby> lobbies = new ConcurrentHashMap<>(); // Lobby ID -> Lobby
    private static final ConcurrentHashMap<UUID, UUID> playerLobbyMap = new ConcurrentHashMap<>(); // Player UUID -> Lobby ID
    private static final Set<String> lockedDungeons = ConcurrentHashMap.newKeySet();

    public static Lobby createLobby(ServerPlayer owner, DungeonManager.DungeonEntry dungeon) {
        if (isDungeonLocked(dungeon.name())) {
            return null;
        }
        Lobby lobby = new Lobby(owner.getUUID(), dungeon);
        lobbies.put(lobby.getId(), lobby);
        lockedDungeons.add(dungeon.name());
        addPlayer(owner, lobby.getId());
        ArenasLdMod.LOGGER.info("Player {} created lobby {} for dungeon '{}'", owner.getName().getString(), lobby.getId(), dungeon.name());
        return lobby;
    }

    public static void disbandLobby(UUID lobbyId) {
        Lobby lobby = lobbies.remove(lobbyId);
        if (lobby != null) {
            lockedDungeons.remove(lobby.getDungeon().name());
            for (UUID playerId : lobby.getPlayers()) {
                playerLobbyMap.remove(playerId);
            }
            ArenasLdMod.LOGGER.info("Lobby {} was disbanded.", lobbyId);
        }
    }

    public static void addPlayer(ServerPlayer player, UUID lobbyId) {
        Lobby lobby = lobbies.get(lobbyId);
        if (lobby != null) {
            // Ensure player is not in another lobby
            removePlayerFromCurrentLobby(player);

            lobby.addPlayer(player.getUUID());
            playerLobbyMap.put(player.getUUID(), lobbyId);
            ArenasLdMod.LOGGER.info("Player {} joined lobby {}", player.getName().getString(), lobbyId);
        }
    }

    public static void removePlayerFromCurrentLobby(ServerPlayer player) {
        UUID lobbyId = playerLobbyMap.remove(player.getUUID());
        if (lobbyId != null) {
            Lobby lobby = lobbies.get(lobbyId);
            if (lobby != null) {
                lobby.removePlayer(player.getUUID());
                ArenasLdMod.LOGGER.info("Player {} left lobby {}", player.getName().getString(), lobbyId);
                // If the owner leaves or the lobby becomes empty, disband it
                if (lobby.getOwnerId().equals(player.getUUID()) || lobby.getPlayers().isEmpty()) {
                    disbandLobby(lobbyId);
                }
            }
        }
    }

    public static Lobby getLobby(UUID lobbyId) {
        return lobbies.get(lobbyId);
    }
    
    public static Lobby getLobbyForPlayer(UUID playerId) {
        UUID lobbyId = playerLobbyMap.get(playerId);
        return lobbyId != null ? lobbies.get(lobbyId) : null;
    }

    public static Collection<Lobby> getLobbies() {
        return lobbies.values();
    }

    public static boolean isDungeonLocked(String dungeonName) {
        return lockedDungeons.contains(dungeonName);
    }

    public static class Lobby {
        private final UUID id;
        private final UUID ownerId;
        private final DungeonManager.DungeonEntry dungeon;
        private final List<UUID> players;

        public Lobby(UUID ownerId, DungeonManager.DungeonEntry dungeon) {
            this.id = UUID.randomUUID();
            this.ownerId = ownerId;
            this.dungeon = dungeon;
            this.players = new CopyOnWriteArrayList<>();
        }

        public UUID getId() {
            return id;
        }

        public UUID getOwnerId() {
            return ownerId;
        }

        public DungeonManager.DungeonEntry getDungeon() {
            return dungeon;
        }

        public List<UUID> getPlayers() {
            return players;
        }

        public void addPlayer(UUID playerId) {
            if (!players.contains(playerId)) {
                players.add(playerId);
            }
        }

        public void removePlayer(UUID playerId) {
            players.remove(playerId);
        }
    }
}
