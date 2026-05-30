package net.ledok.arenas_ld.mixin;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.run.ArenaRun;
import net.ledok.arenas_ld.arena.run.ArenaRunLifecycle;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique
    private DamageSource arenasLd$currentDamageSource;

    @Inject(method = "hurt", at = @At("HEAD"))
    private void arenasLd$captureDamageSource(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        this.arenasLd$currentDamageSource = source;
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void arenasLd$clearDamageSource(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        this.arenasLd$currentDamageSource = null;
    }

    @ModifyArg(
            method = "hurt",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"),
            index = 1
    )
    private float arenasLd$applyDungeonTierDamageMultiplier(float amount) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return amount;
        }

        DungeonRun v4Run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(player.getUUID());
        if (v4Run != null) {
            if (!arenasLd$matchesDungeonDamageFilter(this.arenasLd$currentDamageSource)) {
                return amount;
            }
            double multiplier = v4Run.resolvedTierConfig().damageMultiplier();
            if (multiplier == 1.0) {
                return amount;
            }
            return (float) (amount * multiplier);
        }

        RaidBossSpawnerBlockEntity raidSpawner = ArenasLdMod.RAID_BOSS_MANAGER.getSpawnerForPlayer(player);
        if (raidSpawner == null) {
            return amount;
        }
        double raidDamageMultiplier = raidSpawner.getEffectiveDamageMultiplier();
        if (raidDamageMultiplier == 1.0) {
            return amount;
        }
        return (float) (amount * raidDamageMultiplier);
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
            if (health <= 0.0F) {
                DungeonRun v4Run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(player.getUUID());
                if (v4Run != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    arenasLd$handleV4PlayerDown(player, v4Run);
                    ci.cancel();
                    return;
                }
            }
            RaidBossSpawnerBlockEntity raidSpawner = ArenasLdMod.RAID_BOSS_MANAGER.getSpawnerForPlayer(player);
            if (health <= 0.0F && raidSpawner != null) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    if (raidSpawner.isHardcoreEnabled()) {
                        raidSpawner.handlePlayerHardcoreDeath(player);
                    } else {
                        raidSpawner.handlePlayerDown(player);
                    }
                    ci.cancel();
                    return;
                }
            }
            if (health <= 0.0F && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                && arenasLd$handleArenaDeath(player)) {
                ci.cancel();
                return;
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
            DungeonRun v4Run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(player.getUUID());
            if (v4Run != null && player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                arenasLd$handleV4PlayerDown(player, v4Run);
                ci.cancel();
                return;
            }
            RaidBossSpawnerBlockEntity raidSpawner = ArenasLdMod.RAID_BOSS_MANAGER.getSpawnerForPlayer(player);
            if (raidSpawner != null) {
                if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
                    if (raidSpawner.isHardcoreEnabled()) {
                        raidSpawner.handlePlayerHardcoreDeath(player);
                    } else {
                        raidSpawner.handlePlayerDown(player);
                    }
                    ci.cancel();
                    return;
                }
            }
            if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR
                && arenasLd$handleArenaDeath(player)) {
                ci.cancel();
                return;
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

    /**
     * Suppress natural loot-table drops (bones, arrows, rotten flesh, …) for arena mobs whose
     * "Enable drops" toggle is off. Configured equipment never drops regardless (its drop chance
     * is forced to 0 at spawn), so this only gates the mob's own loot.
     */
    @Inject(method = "dropFromLootTable", at = @At("HEAD"), cancellable = true)
    private void arenasLd$suppressNaturalLoot(DamageSource damageSource, boolean hitByPlayer, CallbackInfo ci) {
        if (((LivingEntity) (Object) this).getTags().contains(EntityEquipmentHelper.NO_NATURAL_LOOT_TAG)) {
            ci.cancel();
        }
    }

    /**
     * Route a lethal hit on a reworked-arena participant to the arena lifecycle (down, or hardcore
     * removal). Returns true if the player was in an active arena run and was handled.
     */
    @Unique
    private boolean arenasLd$handleArenaDeath(ServerPlayer player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) return false;
        var server = sl.getServer();
        for (ArenaControllerBlockEntity.ControllerKey key : ArenaControllerBlockEntity.getControllers()) {
            net.minecraft.server.level.ServerLevel controllerLevel = server.getLevel(key.dimension());
            if (controllerLevel == null) continue;
            if (!(controllerLevel.getBlockEntity(key.pos()) instanceof ArenaControllerBlockEntity controller)) continue;
            for (ArenaRun run : controller.getActiveRuns().values()) {
                RunParticipant p = run.participants().get(player.getUUID());
                if (p == null || p.status() == RunParticipant.ParticipantStatus.REMOVED) continue;
                net.minecraft.server.level.ServerLevel runLevel = server.getLevel(run.spawnerDimension());
                if (runLevel == null) runLevel = sl;
                if (run.hardcoreEnabled()) {
                    ArenaRunLifecycle.handlePlayerHardcoreDeath(runLevel, run, player);
                } else {
                    ArenaRunLifecycle.handlePlayerDown(runLevel, controller, run, player);
                }
                return true;
            }
        }
        return false;
    }

    @Unique
    private void arenasLd$handleV4PlayerDown(ServerPlayer player, DungeonRun run) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel sl)) {
            return;
        }
        net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller = null;
        for (net.minecraft.core.BlockPos pos : ArenasLdMod.DUNGEON_MANAGER.getControllersIn(sl.dimension())) {
            if (sl.getBlockEntity(pos) instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity c) {
                if (c.getActiveRuns().containsValue(run)) {
                    controller = c;
                    break;
                }
            }
        }
        if (controller == null) {
            return;
        }
        net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.handlePlayerDown(sl, controller, run, player);
    }
}
