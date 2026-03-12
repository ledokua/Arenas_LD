package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void onSetHealth(float health, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            DungeonBossSpawnerBlockEntity dungeonSpawner = ArenasLdMod.DUNGEON_BOSS_MANAGER.getSpawnerForPlayer(player);
            if (health <= 0.0F && dungeonSpawner != null) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    dungeonSpawner.handlePlayerDown(player);
                    ci.cancel();
                    return;
                }
            }
            if (health <= 0.0F && ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    player.setGameMode(GameType.SPECTATOR);
                    float newHealth = player.getMaxHealth() * 0.5f;
                    player.setHealth(newHealth);
                    ci.cancel();
                }
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void onDie(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            DungeonBossSpawnerBlockEntity dungeonSpawner = ArenasLdMod.DUNGEON_BOSS_MANAGER.getSpawnerForPlayer(player);
            if (dungeonSpawner != null) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    dungeonSpawner.handlePlayerDown(player);
                    ci.cancel();
                    return;
                }
            }
            if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    player.setGameMode(GameType.SPECTATOR);
                    player.setHealth(player.getMaxHealth() * 0.5f);
                    ci.cancel();
                }
            }
        }
    }
}
