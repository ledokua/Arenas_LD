package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Player handling shared by the dungeon, raid and arena lifecycles. These used to be four copies
 * (one per lifecycle, plus the raid's post-battle reconnect) that drifted apart — in the order of
 * teleport and game mode, in effect clearing, in whether an offline player's eject was persisted.
 */
public final class ReturnPoints {
    private ReturnPoints() {
    }

    /**
     * Puts an online player back where they entered the run: out of the downed camera, teleported
     * to the spot, in their old game mode, at full health, with every status effect gone (buffs and
     * debuffs alike, in every mode), so nothing from the run follows them home.
     */
    public static void apply(MinecraftServer server, ServerPlayer player, PlayerReturnPoint rp) {
        RunPlayerSafety.secure(player); // screens closed, held items back in the inventory (4.36.1)
        DownedSpectators.release(player);
        // Game mode first: a downed spectator crosses dimensions as a normal player, so the destination's chunk
        // map registers them as one from the start (no spectator-to-player switch there afterwards).
        player.setGameMode(rp.previousGameMode());
        ServerLevel target = server.getLevel(rp.dimension());
        DebugLog.log("teleport to return point {} in {}: {}", rp.pos(), rp.dimension().location(), DebugLog.describe(player));
        if (target != null) {
            // The 8-argument teleport also holds the destination chunk loaded for the arrival (POST_TELEPORT ticket).
            player.teleportTo(target, rp.pos().x(), rp.pos().y(), rp.pos().z(), java.util.Set.of(), rp.yaw(), rp.pitch());
        }
        player.setHealth(player.getMaxHealth());
        player.removeAllEffects();
    }

    /**
     * {@link #apply} if the player is online; otherwise the eject is persisted
     * ({@link PendingRestoreStore}) and carried out when they next log in.
     */
    public static void sendHome(MinecraftServer server, UUID uuid, PlayerReturnPoint rp) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null) {
            PendingRestoreStore.get(server).put(uuid, rp);
            return;
        }
        apply(server, player, rp);
    }

    /** Sends a message to every online participant still in the run, except {@code except} (nullable). */
    public static void broadcast(MinecraftServer server, Map<UUID, RunParticipant> participants, Component message,
                                 @Nullable UUID except) {
        for (Map.Entry<UUID, RunParticipant> entry : participants.entrySet()) {
            if (entry.getValue().status() == RunParticipant.ParticipantStatus.REMOVED || entry.getKey().equals(except)) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(message);
            }
        }
    }
}
