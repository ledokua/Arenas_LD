package net.ledok.arenas_ld.event;

import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;

public class PlayerDeathHandler {
    public static void onPlayerDeath(ServerPlayer player, DamageSource source) {
        if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
            player.setGameMode(GameType.SPECTATOR);
            player.setHealth(player.getMaxHealth() * 0.5f);
        }
    }
}
