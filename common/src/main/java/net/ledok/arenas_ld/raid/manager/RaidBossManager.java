package net.ledok.arenas_ld.raid.manager;

import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.util.PendingRestoreStore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.ledok.arenas_ld.platform.ArenasEvents;

public class RaidBossManager {
    private final Set<RaidBossSpawnerBlockEntity> activeSpawners = Collections.newSetFromMap(new WeakHashMap<>());

    public void initialize() {
        ArenasEvents.PLAYER_DISCONNECT.add((player, server) -> {
            RaidBossSpawnerBlockEntity spawner = getSpawnerForPlayer(player);
            if (spawner != null) {
                spawner.handlePlayerDisconnect(player);
            }
        });

        // Deferred to the next tick — server.execute runs inline on the server thread, which
        // would teleport from inside the JOIN event (mid placeNewPlayer) and desync the client.
        ArenasEvents.PLAYER_JOIN.add((joining, server) -> {
            UUID uuid = joining.getUUID();
            net.ledok.arenas_ld.util.ServerTaskScheduler.nextTick(s -> {
                ServerPlayer player = s.getPlayerList().getPlayer(uuid);
                if (player == null) {
                    return;
                }
                RaidBossSpawnerBlockEntity spawner = getSpawnerForOfflinePlayer(uuid);
                if (spawner != null) {
                    spawner.handlePlayerReconnect(player);
                    return;
                }
                handlePostBattleReconnect(player);
            });
        });
    }

    public void registerSpawner(RaidBossSpawnerBlockEntity spawner) {
        if (spawner != null) {
            activeSpawners.add(spawner);
        }
    }

    public void unregisterSpawner(RaidBossSpawnerBlockEntity spawner) {
        if (spawner != null) {
            activeSpawners.remove(spawner);
        }
    }

    /**
     * The raid an online player is still taking part in. Participants who already left it (exit,
     * hardcore death, grace expiry) stay in the run's roster until it ends, but must not have their
     * deaths intercepted (they'd be unkillable), their damage scaled or their items restricted.
     */
    public RaidBossSpawnerBlockEntity getSpawnerForPlayer(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        for (RaidBossSpawnerBlockEntity spawner : activeSpawners) {
            if (spawner.isActiveParticipant(player.getUUID())) {
                return spawner;
            }
        }
        return null;
    }

    /**
     * The running raid that lists this player at all, including participants who already left it —
     * reconnect handling needs those to send them home.
     */
    public RaidBossSpawnerBlockEntity getSpawnerForOfflinePlayer(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (RaidBossSpawnerBlockEntity spawner : activeSpawners) {
            if (spawner.isTracked(uuid) && spawner.isRaidRunning()) {
                return spawner;
            }
        }
        return null;
    }

    /**
     * Ejects a player who was removed from a raid (forfeited past grace, or offline when the run
     * ended) on their next login, using the persisted {@link PendingRestoreStore}.
     */
    public void handlePostBattleReconnect(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerReturnPoint rp = PendingRestoreStore.get(player.server).take(player.getUUID());
        if (rp == null) {
            return;
        }
        // A pending restore from a dungeon or arena run (the store is shared) wipes effects as those exits do.
        net.ledok.arenas_ld.util.ReturnPoints.apply(player.server, player, rp, false);
        player.sendSystemMessage(Component.translatable("message.arenas_ld.raid.run_ended_while_away")
            .withStyle(ChatFormatting.YELLOW));
    }
}
