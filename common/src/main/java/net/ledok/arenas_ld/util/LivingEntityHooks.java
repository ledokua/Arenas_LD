package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.run.ArenaRun;
import net.ledok.arenas_ld.arena.run.ArenaRunLifecycle;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;

import java.util.Locale;

/**
 * Loader-neutral implementations of the LivingEntity behaviour hooks. On Fabric
 * these are driven by LivingEntityMixin; on NeoForge by the corresponding
 * NeoForge events (LivingIncomingDamageEvent / LivingDeathEvent / LivingDropsEvent).
 */
public final class LivingEntityHooks {
    private LivingEntityHooks() {}

    /**
     * Scales damage a player takes by the dungeon tier's damage multiplier (subject
     * to the configured damage-source filter) or, failing that, the raid spawner's
     * effective damage multiplier. Returns the (possibly unchanged) amount.
     */
    public static float modifyPlayerHurtAmount(ServerPlayer player, DamageSource source, float amount) {
        DungeonRun v4Run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(player.getUUID());
        if (v4Run != null) {
            if (!matchesDungeonDamageFilter(source)) {
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

    private static boolean matchesDungeonDamageFilter(DamageSource source) {
        if (source == null) {
            return true;
        }
        String filter = ArenasLdMod.CONFIG.dungeon_damage_source_filter;
        if (filter == null) {
            return true;
        }
        return switch (filter.toUpperCase(Locale.ROOT)) {
            case "LIVING" -> source.getEntity() instanceof LivingEntity;
            case "MOB" -> source.getEntity() instanceof Mob;
            default -> true;
        };
    }

    /**
     * Routes a lethal hit on a player inside a dungeon/raid/arena run to the
     * corresponding down/hardcore-death handling (which restores health itself).
     * Returns true if the death was intercepted and vanilla death must be cancelled.
     */
    public static boolean handlePlayerLethal(ServerPlayer player) {
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            return false;
        }
        DungeonRun v4Run = ArenasLdMod.DUNGEON_MANAGER.getRunForPlayer(player.getUUID());
        if (v4Run != null) {
            handleDungeonPlayerDown(player, v4Run);
            return true;
        }
        RaidBossSpawnerBlockEntity raidSpawner = ArenasLdMod.RAID_BOSS_MANAGER.getSpawnerForPlayer(player);
        if (raidSpawner != null) {
            if (raidSpawner.isHardcoreEnabled()) {
                raidSpawner.handlePlayerHardcoreDeath(player);
            } else {
                raidSpawner.handlePlayerDown(player);
            }
            return true;
        }
        return handleArenaDeath(player);
    }

    /**
     * Whether natural loot-table drops (bones, arrows, rotten flesh, …) should be
     * suppressed for this mob (arena "Enable drops" toggle off). Configured
     * equipment never drops regardless (its drop chance is forced to 0 at spawn).
     */
    public static boolean suppressNaturalLoot(LivingEntity entity) {
        return entity.getTags().contains(EntityEquipmentHelper.NO_NATURAL_LOOT_TAG);
    }

    /**
     * Route a lethal hit on a reworked-arena participant to the arena lifecycle
     * (down, or hardcore removal). Returns true if the player was in an active
     * arena run and was handled.
     */
    private static boolean handleArenaDeath(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) return false;
        var server = sl.getServer();
        for (ArenaControllerBlockEntity.ControllerKey key : ArenaControllerBlockEntity.getControllers()) {
            ServerLevel controllerLevel = server.getLevel(key.dimension());
            if (controllerLevel == null) continue;
            if (!(controllerLevel.getBlockEntity(key.pos()) instanceof ArenaControllerBlockEntity controller)) continue;
            for (ArenaRun run : controller.getActiveRuns().values()) {
                RunParticipant p = run.participants().get(player.getUUID());
                if (p == null || p.status() == RunParticipant.ParticipantStatus.REMOVED) continue;
                ServerLevel runLevel = server.getLevel(run.spawnerDimension());
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

    private static void handleDungeonPlayerDown(ServerPlayer player, DungeonRun run) {
        if (!(player.level() instanceof ServerLevel sl)) {
            return;
        }
        // The controller may live in a different dimension than the player's run; scan all controllers.
        net.ledok.arenas_ld.dungeon.blockentity.DungeonControllerBlockEntity controller =
            ArenasLdMod.DUNGEON_MANAGER.findControllerForRun(sl.getServer(), run);
        if (controller == null) {
            return;
        }
        net.ledok.arenas_ld.dungeon.run.DungeonRunLifecycle.handlePlayerDown(sl, controller, run, player);
    }
}
