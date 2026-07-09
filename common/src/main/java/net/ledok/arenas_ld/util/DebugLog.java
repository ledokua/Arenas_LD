package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * Config-gated verbose logging ({@code debug_logging} in arenas_ld.json) for run lifecycle
 * forensics: teleports, reconnect decisions, chunk force-loading. Logs at INFO with a
 * {@code [debug]} prefix so it shows up without logger reconfiguration.
 */
public final class DebugLog {
    private DebugLog() {
    }

    public static boolean enabled() {
        return ArenasLdConfig.getInstance().debug_logging;
    }

    public static void log(String message, Object... args) {
        if (enabled()) {
            ArenasLdMod.LOGGER.info("[debug] " + message, args);
        }
    }

    /**
     * One-line dump of the server-side state that matters when a teleport misbehaves:
     * dimension, position, chunk section, game mode, and removal state.
     */
    public static String describe(ServerPlayer player) {
        if (player == null) {
            return "<null player>";
        }
        return player.getScoreboardName()
            + "[id=" + player.getId()
            + " dim=" + player.serverLevel().dimension().location()
            + " pos=(" + String.format(Locale.ROOT, "%.1f %.1f %.1f", player.getX(), player.getY(), player.getZ())
            + ") section=" + SectionPos.of(player)
            + " gameMode=" + player.gameMode.getGameModeForPlayer()
            + " spectator=" + player.isSpectator()
            + " removed=" + player.getRemovalReason()
            + " disconnected=" + player.hasDisconnected() + "]";
    }
}
