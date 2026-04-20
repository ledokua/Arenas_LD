package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.entity.DungeonBossSpawnerBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyArg(
            method = "actuallyHurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setHealth(F)V"),
            index = 0
    )
    private float arenasLd$applyDungeonTierDamageMultiplier(float newHealth) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return newHealth;
        }

        DungeonBossSpawnerBlockEntity dungeonSpawner = ArenasLdMod.DUNGEON_BOSS_MANAGER.getSpawnerForPlayer(player);
        if (dungeonSpawner == null) {
            return newHealth;
        }

        DamageSource source = ((LivingEntity) (Object) this).getLastDamageSource();
        if (!arenasLd$matchesDungeonDamageFilter(source)) {
            return newHealth;
        }

        double damageMultiplier = dungeonSpawner.getEffectiveDamageMultiplier();
        if (damageMultiplier == 1.0) {
            return newHealth;
        }

        float oldHealth = player.getHealth();
        float appliedDamage = oldHealth - newHealth;
        if (appliedDamage <= 0.0F) {
            return newHealth;
        }

        float scaledDamage = (float) (appliedDamage * damageMultiplier);
        return Math.max(0.0F, oldHealth - scaledDamage);
    }

    private boolean arenasLd$matchesDungeonDamageFilter(DamageSource source) {
        if (source == null) {
            return true;
        }
        String filter = ArenasLdMod.CONFIG.dungeon_damage_source_filter;
        if (filter == null) {
            return true;
        }
        return switch (filter.toUpperCase(java.util.Locale.ROOT)) {
            case "LIVING" -> source.getEntity() instanceof LivingEntity;
            case "MOB" -> source.getEntity() instanceof Mob;
            default -> true;
        };
    }


    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void onSetHealth(float health, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            DungeonBossSpawnerBlockEntity dungeonSpawner = ArenasLdMod.DUNGEON_BOSS_MANAGER.getSpawnerForPlayer(player);
            if (health <= 0.0F && dungeonSpawner != null) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    if (dungeonSpawner.isHardcoreEnabled()) {
                        dungeonSpawner.handlePlayerHardcoreDeath(player);
                    } else {
                        dungeonSpawner.handlePlayerDown(player);
                    }
                    ci.cancel();
                    return;
                }
            }
            if (health <= 0.0F && ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    var arenaInfo = ArenasLdMod.MOB_ARENA_MANAGER.getArenaInfo(player);
                    if (arenaInfo != null) {
                        var world = player.server.getLevel(arenaInfo.dimension());
                        if (world != null && world.getBlockEntity(arenaInfo.pos()) instanceof net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity spawner) {
                            if (spawner.isHardcoreEnabled()) {
                                spawner.handlePlayerHardcoreDeath(player);
                                ci.cancel();
                                return;
                            }
                        }
                    }
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
                    if (dungeonSpawner.isHardcoreEnabled()) {
                        dungeonSpawner.handlePlayerHardcoreDeath(player);
                    } else {
                        dungeonSpawner.handlePlayerDown(player);
                    }
                    ci.cancel();
                    return;
                }
            }
            if (ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    var arenaInfo = ArenasLdMod.MOB_ARENA_MANAGER.getArenaInfo(player);
                    if (arenaInfo != null) {
                        var world = player.server.getLevel(arenaInfo.dimension());
                        if (world != null && world.getBlockEntity(arenaInfo.pos()) instanceof net.ledok.arenas_ld.block.entity.MobArenaSpawnerBlockEntity spawner) {
                            if (spawner.isHardcoreEnabled()) {
                                spawner.handlePlayerHardcoreDeath(player);
                                ci.cancel();
                                return;
                            }
                        }
                    }
                    player.setGameMode(GameType.SPECTATOR);
                    player.setHealth(player.getMaxHealth() * 0.5f);
                    ci.cancel();
                }
            }
        }
    }
}
