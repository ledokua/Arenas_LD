package net.ledok.arenas_ld.util;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;

import java.util.List;

/**
 * Strips blacklisted potion/mob effects ({@link RunEffectBlacklist}) from players who are in an
 * active raid, dungeon, or arena run, every server tick — so the effects can't be brought in or
 * re-applied mid-run.
 *
 * <p>Cheap in the common case: when the blacklist is empty it returns immediately, and otherwise the
 * only per-player work is a couple of {@code hasEffect} map lookups. The heavier "is in a run" check
 * runs only for the rare player who actually has a blacklisted effect.
 */
public final class RunEffectRestrictions {
    private RunEffectRestrictions() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RunEffectRestrictions::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        List<Holder<MobEffect>> blacklist = RunEffectBlacklist.effects();
        if (blacklist.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!hasAnyBlacklisted(player, blacklist)) {
                continue;
            }
            if (!RunItemRestrictions.isPlayerInRun(player)) {
                continue;
            }
            for (Holder<MobEffect> effect : blacklist) {
                if (player.hasEffect(effect)) {
                    player.removeEffect(effect);
                }
            }
        }
    }

    private static boolean hasAnyBlacklisted(ServerPlayer player, List<Holder<MobEffect>> blacklist) {
        for (Holder<MobEffect> effect : blacklist) {
            if (player.hasEffect(effect)) {
                return true;
            }
        }
        return false;
    }
}
