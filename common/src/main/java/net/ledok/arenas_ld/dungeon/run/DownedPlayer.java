package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * State of a downed (but not eliminated) player during a dungeon run.
 *
 * <p>While downed, the player is in spectator mode. When {@link #ticksRemaining} reaches 0,
 * the controller respawns them at the dungeon entrance at reduced HP (the exact fraction is
 * a controller config in Phase E).
 *
 * <p>This record is immutable; {@link #tick()} returns a new instance with one fewer tick.
 *
 * <p>Position is intentionally not stored — respawn always happens at the dungeon entrance,
 * not where the player died.
 */
public record DownedPlayer(UUID playerUuid, int ticksRemaining) {

    public static final Codec<DownedPlayer> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(DownedPlayer::playerUuid),
            Codec.INT.fieldOf("ticksRemaining").forGetter(DownedPlayer::ticksRemaining)
        ).apply(instance, DownedPlayer::new)
    );

    /** Returns a new DownedPlayer with ticksRemaining decremented by 1, clamped at 0. */
    public DownedPlayer tick() {
        return new DownedPlayer(playerUuid, Math.max(0, ticksRemaining - 1));
    }

    /** True when the countdown has finished and the player should be respawned. */
    public boolean isReadyToRespawn() {
        return ticksRemaining <= 0;
    }
}
