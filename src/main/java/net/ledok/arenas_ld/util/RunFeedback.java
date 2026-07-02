package net.ledok.arenas_ld.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Small helpers for run feedback: title/subtitle flashes and personal sound cues sent to a single
 * player. Used by the dungeon/raid/arena lifecycles for wins, losses, wave starts, etc.
 */
public final class RunFeedback {
    private RunFeedback() {
    }

    /** Shows a title (and optional subtitle) with a short fade-in/out. */
    public static void title(ServerPlayer player, Component title, @Nullable Component subtitle) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 10));
        if (subtitle != null) {
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** Plays a UI-style sound cue only this player hears. */
    public static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.MASTER, volume, pitch);
    }

    /** Sends a title flash and/or sound cue to every online player in the given set. */
    public static void toAll(net.minecraft.server.level.ServerLevel world, Iterable<java.util.UUID> uuids,
                             @Nullable Component title, @Nullable Component subtitle,
                             @Nullable SoundEvent sound, float volume, float pitch) {
        for (java.util.UUID uuid : uuids) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) continue;
            if (title != null) {
                title(player, title, subtitle);
            }
            if (sound != null) {
                sound(player, sound, volume, pitch);
            }
        }
    }
}
