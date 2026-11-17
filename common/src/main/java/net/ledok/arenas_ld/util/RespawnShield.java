package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.platform.ArenasEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A short invulnerability for a player who respawns inside a dungeon or raid, so they aren't killed the instant they
 * reappear ({@code respawn_invulnerable_ticks}, 0 = off). It ends early on their first swing or the first damage they
 * deal (no free hits). Damage that bypasses invulnerability (the void, /kill) still lands. Server thread only; with no
 * shield up, a hit costs one empty-map check.
 */
public final class RespawnShield {
    /** Player → server tick the shield ends. */
    private static final Map<UUID, Integer> UNTIL = new HashMap<>();

    private RespawnShield() {
    }

    public static void register() {
        ArenasEvents.SERVER_STOPPED.add(server -> UNTIL.clear());
        ArenasEvents.PLAYER_DISCONNECT.add((player, server) -> UNTIL.remove(player.getUUID()));
    }

    /** A dungeon or raid respawn put the player back in the fight. */
    public static void grant(ServerPlayer player) {
        int ticks = ArenasLdConfig.getInstance().respawn_invulnerable_ticks;
        if (ticks > 0) {
            UNTIL.put(player.getUUID(), player.server.getTickCount() + ticks);
        }
    }

    /** {@code LivingEntity.hurt} HEAD: true cancels the hit. Also ends the attacker's own shield. */
    public static boolean blocks(LivingEntity target, DamageSource source) {
        if (UNTIL.isEmpty()) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != target) {
            UNTIL.remove(attacker.getUUID()); // they're fighting: no shield while dealing damage
        }
        if (!(target instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        Integer until = UNTIL.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (player.server.getTickCount() >= until) {
            UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /** The player swung at something: the shield ends. */
    public static void attacked(ServerPlayer player) {
        if (!UNTIL.isEmpty()) {
            UNTIL.remove(player.getUUID());
        }
    }

    public static boolean isShielded(ServerPlayer player) {
        Integer until = UNTIL.get(player.getUUID());
        return until != null && player.server.getTickCount() < until;
    }
}
