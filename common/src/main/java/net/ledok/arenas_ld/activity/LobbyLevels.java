package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToIntFunction;

/**
 * A lobby's minimum join level ({@link Lobby#minLevel}). The level comes from a provider another mod registers
 * ({@code ArenasApi.setLevelProvider}: Eden_LD's activity level). Without one, minimum levels are ignored, so a
 * "level" never means two different numbers. Checked on join and on asking to join; an owner's invite skips it
 * (LeDok).
 */
public final class LobbyLevels {
    static volatile @Nullable ToIntFunction<ServerPlayer> provider;

    private LobbyLevels() {
    }

    public static void setProvider(@Nullable ToIntFunction<ServerPlayer> levels) {
        provider = levels;
    }

    public static boolean active() {
        return provider != null;
    }

    /** The player's level, or -1 without a provider (or when it fails). */
    public static int levelOf(ServerPlayer player) {
        ToIntFunction<ServerPlayer> levels = provider;
        if (levels == null) {
            return -1;
        }
        try {
            return levels.applyAsInt(player);
        } catch (RuntimeException e) {
            net.ledok.arenas_ld.ArenasLdMod.LOGGER.warn("[Arenas] The level provider failed", e);
            return -1;
        }
    }

    /** Why the player can't join this lobby for its level, or null when they can. */
    public static @Nullable Component refusal(ServerPlayer player, Lobby lobby) {
        if (lobby.minLevel() <= 0 || provider == null) {
            return null;
        }
        int level = levelOf(player);
        if (level < 0 || level >= lobby.minLevel()) {
            return null;
        }
        return Component.translatable("message.arenas_ld.lobby.min_level", lobby.minLevel());
    }

    /** {@link #refusal}, telling the player; false when refused. */
    public static boolean allow(ServerPlayer player, Lobby lobby) {
        Component refusal = refusal(player, lobby);
        if (refusal != null) {
            player.sendSystemMessage(refusal.copy().withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }
}
