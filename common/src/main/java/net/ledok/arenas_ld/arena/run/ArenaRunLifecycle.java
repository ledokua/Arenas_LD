package net.ledok.arenas_ld.arena.run;

import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaSpawnerBlockEntity;
import net.ledok.arenas_ld.compat.PuffishSkillsCompat;
import net.ledok.arenas_ld.dungeon.run.DownedPlayer;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.util.EconomyCompat;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.ledok.arenas_ld.util.MobArenaRewardData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.core.registries.Registries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Controller-driven lifecycle for a wave-survival arena run. Mirrors the structure of
 * {@link net.ledok.arenas_ld.raid.run.RaidRunLifecycle} (STARTING/RUNNING/CLOSING/DONE, downed /
 * disconnect-grace / return-point plumbing) but the RUNNING phase drives an escalating wave loop
 * with archetypes and objectives instead of a single boss.
 */
public final class ArenaRunLifecycle {

    public static final String BUSY_REASON = "arenas_ld:arena";
    private static final int DOWNED_RESPAWN_TICKS_FALLBACK = 60;
    private static final int DEATH_TIME_PENALTY_FALLBACK = 200;
    private static final double ELITE_SCALE = 1.6;

    private ArenaRunLifecycle() {}

    // ── Entry points ──────────────────────────────────────────────────────────

    public static void beginRun(ServerLevel world, ArenaControllerBlockEntity controller,
                                ArenaRun run, List<ServerPlayer> players) {
        ArenaSpawnerBlockEntity spawner = spawnerFor(world, run);
        long now = world.getGameTime();
        ServerLevel entranceWorld = spawner != null ? world.getServer().getLevel(spawner.getEntranceDimension()) : world;
        BlockPos entrance = spawner != null ? spawner.getAbsoluteEntrancePos() : run.spawnerPos();

        for (ServerPlayer player : players) {
            run.setReturnPoint(player.getUUID(), new PlayerReturnPoint(
                player.level().dimension(), player.position(), player.getYRot(), player.getXRot(),
                player.gameMode.getGameModeForPlayer()));
            run.addParticipant(new RunParticipant(player.getUUID(), player.getGameProfile().getName(),
                ParticipantStatus.ACTIVE, now));
            net.ledok.arenas_ld.util.PlayerStatsStore.get(world.getServer())
                .recordRunStart(player.getUUID(), net.ledok.arenas_ld.util.PlayerStatsStore.Mode.ARENA);
            player.setGameMode(GameType.SURVIVAL);
            player.setHealth(player.getMaxHealth());
            if (entranceWorld != null) {
                player.teleportTo(entranceWorld, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
            }
        }

        run.setCurrentWave(0);
        run.clearAliveMobs();
        // Freeze the per-player HP multiplier at run start — players leaving mid-run don't weaken it.
        run.setPartyHealthMultiplier(controller.resolvePartyHealthMultiplier(players.size()));
        run.setPrepareTicksRemaining(spawner != null ? spawner.getPrepareTime() * 20 : 200);
        run.setPhase(ArenaPhase.RUNNING);
        ArenasLdMod.LOGGER.info("Arena run started at {} with {} players", run.spawnerPos(), players.size());
    }

    public static void tick(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        switch (run.phase()) {
            case STARTING -> run.setPhase(ArenaPhase.RUNNING);
            case RUNNING -> tickRunning(world, controller, run);
            case CLOSING -> tickClosing(world, controller, run);
            case DONE -> { /* already removed from controller */ }
        }
    }

    @Nullable
    private static ArenaSpawnerBlockEntity spawnerFor(ServerLevel world, ArenaRun run) {
        BlockEntity be = world.getBlockEntity(run.spawnerPos());
        return be instanceof ArenaSpawnerBlockEntity s ? s : null;
    }

    // ── RUNNING ──────────────────────────────────────────────────────────────

    private static void tickRunning(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        ArenaSpawnerBlockEntity spawner = spawnerFor(world, run);
        if (spawner == null) {
            handleLoss(world, controller, run, ArenaOutcome.FORCED, "Arena spawner missing.");
            return;
        }

        tickDisconnectedPlayers(world, controller, run);
        pruneDeadMobs(world, run);
        updateWaveBossBar(world, run, spawner);

        // Abandoned: nobody from the party is online any more.
        boolean anyOnline = run.participants().keySet().stream()
            .anyMatch(uuid -> world.getServer().getPlayerList().getPlayer(uuid) != null);
        if (!anyOnline) {
            handleLoss(world, controller, run, ArenaOutcome.ABANDONED, "All players left.");
            return;
        }

        // Wipe: at least one participant, but none are ACTIVE (all downed / removed).
        if (!run.participants().isEmpty() && run.activeParticipantUuids().isEmpty()) {
            handleLoss(world, controller, run, ArenaOutcome.WIPED, "Party wiped.");
            return;
        }

        if (run.prepareTicksRemaining() > 0) {
            run.setPrepareTicksRemaining(run.prepareTicksRemaining() - 1);
            if (run.prepareTicksRemaining() == 0) startWave(world, controller, run, spawner);
            return;
        }
        if (run.betweenWaveTicksRemaining() > 0) {
            run.setBetweenWaveTicksRemaining(run.betweenWaveTicksRemaining() - 1);
            if (run.betweenWaveTicksRemaining() == 0) startWave(world, controller, run, spawner);
            return;
        }

        // Objective: KILL_MARKED clears the wave the instant the marked mob dies.
        boolean cleared;
        if (run.currentObjective() == ObjectiveType.KILL_MARKED && run.markedMob() != null) {
            Entity marked = world.getEntity(run.markedMob());
            cleared = marked == null || !marked.isAlive();
        } else {
            cleared = run.aliveMobs().isEmpty();
        }

        if (cleared && run.currentWave() > 0) {
            completeWave(world, controller, run, spawner);
            return;
        }

        // Objective: DEFEND_ZONE fails the moment no standing party member is inside the zone.
        if (run.currentWave() > 0 && run.currentObjective() == ObjectiveType.DEFEND_ZONE
            && run.objectiveProgress() == 0) {
            double radius = defendZoneRadius(spawner);
            Vec3 center = Vec3.atCenterOf(run.spawnerPos());
            boolean anyInside = false;
            for (UUID uuid : run.activeParticipantUuids()) {
                ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
                if (p != null && p.level() == world && p.position().distanceTo(center) <= radius) {
                    anyInside = true;
                    break;
                }
            }
            if (!anyInside) failObjective(world, run);

            // Once a second, mark the zone edge with a particle ring so players can see it.
            if (world.getGameTime() % 20 == 0) {
                for (int i = 0; i < 24; i++) {
                    double angle = (Math.PI * 2 * i) / 24;
                    world.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        center.x + Math.cos(angle) * radius, center.y + 0.3, center.z + Math.sin(angle) * radius,
                        1, 0.0, 0.05, 0.0, 0.0);
                }
            }
        }

        if (run.waveTicksRemaining() > 0) {
            run.setWaveTicksRemaining(run.waveTicksRemaining() - 1);
            if (run.waveTicksRemaining() == 0) {
                handleLoss(world, controller, run, ArenaOutcome.TIMEOUT, "Time ran out on wave " + run.currentWave());
            }
        }
    }

    private static void pruneDeadMobs(ServerLevel world, ArenaRun run) {
        for (UUID uuid : new ArrayList<>(run.aliveMobs())) {
            Entity entity = world.getEntity(uuid);
            if (entity == null || !entity.isAlive()) run.removeAliveMob(uuid);
        }
    }

    private static void startWave(ServerLevel world, ArenaControllerBlockEntity controller,
                                  ArenaRun run, ArenaSpawnerBlockEntity spawner) {
        int wave = run.currentWave() + 1;
        run.setCurrentWave(wave);
        run.setMarkedMob(null);
        run.setObjectiveProgress(0);

        WaveArchetype archetype = chooseArchetype(wave, spawner);
        run.setCurrentArchetype(archetype);
        run.setCurrentObjective(archetype == WaveArchetype.OBJECTIVE ? pickObjective(world, spawner) : ObjectiveType.NONE);
        announceObjective(world, run, spawner);

        // Clear dropped items from the previous wave.
        for (ItemEntity item : world.getEntitiesOfClass(ItemEntity.class, new AABB(run.spawnerPos()).inflate(spawner.getBattleRadius()))) {
            item.discard();
        }

        boolean bossSpawned = spawnWaveMobs(world, controller, run, spawner, archetype);

        int waveSeconds = spawner.getWaveTimer() + (wave - 1) * spawner.getAdditionalTime();
        if (bossSpawned) waveSeconds += spawner.getBossWaveAdditionalTime();
        run.setWaveTicksRemaining(waveSeconds * 20);

        Component subtitle = switch (archetype) {
            case BOSS -> Component.translatable("subtitle.arenas_ld.wave.boss").withStyle(net.minecraft.ChatFormatting.DARK_RED);
            case ELITE -> Component.translatable("subtitle.arenas_ld.wave.elite").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE);
            case OBJECTIVE -> Component.translatable("subtitle.arenas_ld.wave.objective").withStyle(net.minecraft.ChatFormatting.GOLD);
            case HORDE -> null;
        };
        net.minecraft.sounds.SoundEvent waveSound = archetype == WaveArchetype.BOSS
            ? net.minecraft.sounds.SoundEvents.WITHER_SPAWN
            : net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP;
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            Component.translatable("title.arenas_ld.wave", wave), subtitle,
            waveSound, archetype == WaveArchetype.BOSS ? 0.7f : 0.8f, archetype == WaveArchetype.BOSS ? 1.0f : 0.7f);

        controller.markDirtyAndSync();
    }

    private static WaveArchetype chooseArchetype(int wave, ArenaSpawnerBlockEntity spawner) {
        boolean hasBoss = spawner.getMobs().stream().anyMatch(m -> m.isBoss && wave >= m.minWave && wave <= m.maxWave);
        int bossN = spawner.getBossEveryNWaves();
        int objN = spawner.getObjectiveEveryNWaves();
        int eliteN = spawner.getEliteEveryNWaves();
        if (bossN > 0 && wave % bossN == 0 && hasBoss) return WaveArchetype.BOSS;
        if (objN > 0 && wave % objN == 0 && !spawner.getEnabledObjectives().isEmpty()) return WaveArchetype.OBJECTIVE;
        if (eliteN > 0 && wave % eliteN == 0) return WaveArchetype.ELITE;
        return WaveArchetype.HORDE;
    }

    private static ObjectiveType pickObjective(ServerLevel world, ArenaSpawnerBlockEntity spawner) {
        List<ObjectiveType> enabled = spawner.getEnabledObjectives();
        if (enabled.isEmpty()) return ObjectiveType.NONE;
        return enabled.get(world.random.nextInt(enabled.size()));
    }

    /** DEFEND_ZONE keep-out radius: half the mob spawn ring, so kiting to the edge fails it. */
    private static double defendZoneRadius(ArenaSpawnerBlockEntity spawner) {
        return Math.max(3, spawner.getSpawnDistance() / 2.0);
    }

    private static void announceObjective(ServerLevel world, ArenaRun run, ArenaSpawnerBlockEntity spawner) {
        Component message = switch (run.currentObjective()) {
            case DEFEND_ZONE -> Component.translatable("message.arenas_ld.arena.objective.defend_zone",
                (int) defendZoneRadius(spawner));
            case SURVIVE_UNTOUCHED -> Component.translatable("message.arenas_ld.arena.objective.survive_untouched");
            case KILL_MARKED -> Component.translatable("message.arenas_ld.arena.objective.kill_marked");
            case NONE -> null;
        };
        if (message != null) {
            broadcast(world, run, message.copy().withStyle(net.minecraft.ChatFormatting.GOLD));
        }
    }

    /** Marks the current wave's objective failed (idempotent) and tells the party the bonus is gone. */
    private static void failObjective(ServerLevel world, ArenaRun run) {
        if (run.objectiveProgress() != 0) return;
        run.setObjectiveProgress(1);
        broadcast(world, run, Component.translatable("message.arenas_ld.arena.objective_failed")
            .withStyle(net.minecraft.ChatFormatting.RED));
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            null, null, net.minecraft.sounds.SoundEvents.VILLAGER_NO, 0.8f, 0.8f);
    }

    /** @return true if a boss was spawned this wave. */
    private static boolean spawnWaveMobs(ServerLevel world, ArenaControllerBlockEntity controller,
                                         ArenaRun run, ArenaSpawnerBlockEntity spawner, WaveArchetype archetype) {
        int wave = run.currentWave();
        List<MobArenaMobData> bosses = new ArrayList<>();
        List<MobArenaMobData> regulars = new ArrayList<>();
        for (MobArenaMobData mob : spawner.getMobs()) {
            if (wave < mob.minWave || wave > mob.maxWave) continue;
            (mob.isBoss ? bosses : regulars).add(mob);
        }

        int partySize = Math.max(1, run.activeParticipantUuids().size());
        // Party-scaled base HP, further grown per wave — a bigger party's base compounds into a
        // much bigger number at high waves than a solo player's.
        double partyHp = run.partyHealthMultiplier() * controller.resolveWaveHealthMultiplier(wave);
        double waveScale = Math.pow(1.0 + spawner.getAttributeScale(), wave - 1);

        boolean bossSpawned = false;
        if (archetype == WaveArchetype.BOSS && !bosses.isEmpty()) {
            MobArenaMobData bossData = bosses.get(world.random.nextInt(bosses.size()));
            spawnMob(world, run, spawner, bossData, true, partyHp);
            bossSpawned = true;
        }

        if (!regulars.isEmpty() && archetype != WaveArchetype.BOSS) {
            int base = 5 + wave / 2 + 2 * (partySize - 1);
            int count = switch (archetype) {
                case HORDE -> (int) Math.round(base * 1.5);
                case ELITE -> Math.max(1, base / 3);
                default -> base; // OBJECTIVE
            };
            double scale = waveScale * partyHp * (archetype == WaveArchetype.ELITE ? ELITE_SCALE : 1.0);
            UUID firstSpawned = null;
            for (int i = 0; i < count; i++) {
                MobArenaMobData mobData = selectRandomMob(world, regulars);
                if (mobData == null) continue;
                UUID spawned = spawnMob(world, run, spawner, mobData, false, scale);
                if (firstSpawned == null) firstSpawned = spawned;
            }
            // KILL_MARKED: flag the first spawned regular as the target.
            if (run.currentObjective() == ObjectiveType.KILL_MARKED && firstSpawned != null) {
                run.setMarkedMob(firstSpawned);
                Entity marked = world.getEntity(firstSpawned);
                if (marked != null) marked.setGlowingTag(true);
            }
        }
        return bossSpawned;
    }

    @Nullable
    private static MobArenaMobData selectRandomMob(ServerLevel world, List<MobArenaMobData> valid) {
        int totalWeight = 0;
        for (MobArenaMobData mob : valid) totalWeight += Math.max(0, mob.weight);
        if (valid.isEmpty() || totalWeight <= 0) return valid.isEmpty() ? null : valid.get(0);
        int roll = world.random.nextInt(totalWeight);
        for (MobArenaMobData mob : valid) {
            roll -= Math.max(0, mob.weight);
            if (roll < 0) return mob;
        }
        return valid.get(0);
    }

    @Nullable
    private static UUID spawnMob(ServerLevel world, ArenaRun run, ArenaSpawnerBlockEntity spawner,
                                 MobArenaMobData mobData, boolean isBoss, double scaleFactor) {
        var typeOpt = EntityType.byString(mobData.mobId);
        if (typeOpt.isEmpty()) return null;
        Entity entity = typeOpt.get().create(world);
        if (!(entity instanceof LivingEntity living)) return null;

        applyScaledAttributes(world, living, mobData, scaleFactor);
        EntityEquipmentHelper.applyAllEquipment(living, mobData.equipment);
        living.heal(living.getMaxHealth());
        // Owned by the run: never let vanilla despawn arena mobs.
        if (living instanceof net.minecraft.world.entity.Mob mob) {
            mob.setPersistenceRequired();
        }
        assignTeam(world, spawner, living);

        BlockPos origin = run.spawnerPos();
        int radius = spawner.getBattleRadius();
        int spawnDistance = spawner.getSpawnDistance();
        boolean placed = false;
        for (int attempt = 0; attempt < 10 && !placed; attempt++) {
            double x, z;
            if (isBoss) {
                x = origin.getX() + 0.5;
                z = origin.getZ() + 0.5;
            } else {
                double min = spawnDistance;
                double max = Math.max(spawnDistance + 1, radius - 2);
                double r = min + world.random.nextDouble() * (max - min);
                double angle = world.random.nextDouble() * 2 * Math.PI;
                x = origin.getX() + 0.5 + r * Math.cos(angle);
                z = origin.getZ() + 0.5 + r * Math.sin(angle);
            }
            for (int yOff = 0; yOff <= 5; yOff++) {
                BlockPos pos = new BlockPos((int) x, origin.getY() + yOff, (int) z);
                if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty()
                    && !world.getBlockState(pos.below()).getCollisionShape(world, pos.below()).isEmpty()) {
                    living.moveTo(x, origin.getY() + yOff, z, world.random.nextFloat() * 360.0F, 0.0F);
                    if (world.noCollision(living) && !world.containsAnyLiquid(living.getBoundingBox())) {
                        world.addFreshEntity(living);
                        run.addAliveMob(living.getUUID());
                        placed = true;
                        break;
                    }
                }
            }
        }
        if (!placed) {
            living.moveTo(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, world.random.nextFloat() * 360.0F, 0.0F);
            world.addFreshEntity(living);
            run.addAliveMob(living.getUUID());
        }
        return living.getUUID();
    }

    private static void applyScaledAttributes(ServerLevel world, LivingEntity living, MobArenaMobData mobData, double scaleFactor) {
        var attributeRegistry = world.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
        for (var attr : mobData.attributes) {
            ResourceLocation loc = ResourceLocation.tryParse(attr.id());
            if (loc == null) continue;
            ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, loc);
            attributeRegistry.getHolder(key).ifPresent(holder -> {
                AttributeInstance instance = living.getAttribute(holder);
                if (instance == null) return;
                double value = Math.min(attr.value() * scaleFactor, attr.maxValue());
                instance.setBaseValue(value);
            });
        }
    }

    private static void assignTeam(ServerLevel world, ArenaSpawnerBlockEntity spawner, LivingEntity living) {
        String teamName = spawner.getGroupId() == null || spawner.getGroupId().isBlank() ? "arenas_ld" : spawner.getGroupId();
        Scoreboard scoreboard = world.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            team.setAllowFriendlyFire(false);
        }
        scoreboard.addPlayerToTeam(living.getScoreboardName(), team);
    }

    private static void completeWave(ServerLevel world, ArenaControllerBlockEntity controller,
                                     ArenaRun run, ArenaSpawnerBlockEntity spawner) {
        // KILL_MARKED clears the wave by killing the target — discard the leftover mobs.
        if (run.currentObjective() == ObjectiveType.KILL_MARKED) {
            for (UUID uuid : new ArrayList<>(run.aliveMobs())) {
                Entity e = world.getEntity(uuid);
                if (e != null) e.discard();
                run.removeAliveMob(uuid);
            }
        }

        broadcast(world, run, Component.translatable("message.arenas_ld.arena.wave_cleared", run.currentWave()));
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            null, null, net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6f, 1.4f);
        // A failed objective forfeits the wave bonus (loot + heal/repair); downed players still revive.
        if (run.objectiveProgress() == 0) {
            distributeWaveLoot(world, controller, run, spawner);
            applyWaveCompletionBonus(world, run);
        }
        reviveDowned(world, run, spawner);

        // Win when the configured ceiling is reached.
        if (run.maxWave() > 0 && run.currentWave() >= run.maxWave()) {
            handleWin(world, controller, run);
            return;
        }
        run.setBetweenWaveTicksRemaining(spawner.getTimeBetweenWaves() * 20);
        controller.markDirtyAndSync();
    }

    private static void distributeWaveLoot(ServerLevel world, ArenaControllerBlockEntity controller,
                                           ArenaRun run, ArenaSpawnerBlockEntity spawner) {
        int wave = run.currentWave();
        List<MobArenaRewardData> valid = new ArrayList<>();
        for (MobArenaRewardData reward : spawner.getRewards()) {
            if (wave >= reward.minWave && wave <= reward.maxWave
                && reward.waveFrequency > 0 && (wave - reward.minWave) % reward.waveFrequency == 0) {
                valid.add(reward);
            }
        }
        if (valid.isEmpty()) return;

        List<ServerPlayer> players = onlineParticipants(world, run);
        if (players.isEmpty()) return;
        int multiplier = run.hardcoreEnabled() ? 2 : 1;
        boolean viaInbox = controller.isLootViaInbox();

        for (MobArenaRewardData reward : valid) {
            for (int i = 0; i < reward.rolls * multiplier; i++) {
                if (reward.perPlayer) {
                    for (ServerPlayer player : players) giveLootBundle(player, reward.lootTableId, viaInbox);
                } else {
                    ServerPlayer player = players.get(world.random.nextInt(players.size()));
                    rollLootTableToWorld(world, run, reward.lootTableId, player);
                }
            }
        }
    }

    private static void giveLootBundle(ServerPlayer player, String lootTableId, boolean viaInbox) {
        ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
        bundle.set(DataComponentRegistry.LOOT_BUNDLE_DATA, new LootBundleDataComponent(lootTableId));
        if (viaInbox) {
            EconomyCompat.deliverItem(player.getUUID(), bundle, bundle.getCount(), "ARENA_LOOT");
        } else if (!player.getInventory().add(bundle)) {
            player.drop(bundle, false);
        }
    }

    private static void rollLootTableToWorld(ServerLevel world, ArenaRun run, String lootTableId, ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse(lootTableId);
        if (id == null) return;
        LootTable table = Objects.requireNonNull(world.getServer()).reloadableRegistries()
            .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, id));
        LootParams params = new LootParams.Builder(world)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(run.spawnerPos()))
            .withParameter(LootContextParams.THIS_ENTITY, player)
            .create(LootContextParamSets.GIFT);
        table.getRandomItems(params).forEach(stack -> {
            double x = run.spawnerPos().getX() + 0.5 + (world.random.nextDouble() * 8.0) - 4.0;
            double y = run.spawnerPos().getY() + 3.5;
            double z = run.spawnerPos().getZ() + 0.5 + (world.random.nextDouble() * 8.0) - 4.0;
            ItemEntity item = new ItemEntity(world, x, y, z, stack);
            item.setDeltaMovement(world.random.nextDouble() * 0.2 - 0.1, 0.4, world.random.nextDouble() * 0.2 - 0.1);
            world.addFreshEntity(item);
        });
    }

    private static void applyWaveCompletionBonus(ServerLevel world, ArenaRun run) {
        float pct = Math.min(1.0f, run.currentWave() / 100.0f);
        for (ServerPlayer player : onlineParticipants(world, run)) {
            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) continue;
            player.heal(player.getMaxHealth() * pct);
            for (ItemStack stack : player.getArmorSlots()) repair(stack, pct);
            repair(player.getMainHandItem(), pct);
            repair(player.getOffhandItem(), pct);
        }
    }

    private static void repair(ItemStack stack, float pct) {
        if (stack.isDamageableItem()) {
            int amount = (int) (stack.getMaxDamage() * pct);
            stack.setDamageValue(Math.max(0, stack.getDamageValue() - amount));
        }
    }

    /** Revive every downed party member between waves at the nearest respawn point. */
    private static void reviveDowned(ServerLevel world, ArenaRun run, ArenaSpawnerBlockEntity spawner) {
        for (UUID uuid : new ArrayList<>(run.downedPlayers().keySet())) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            run.clearDowned(uuid);
            RunParticipant participant = run.participants().get(uuid);
            if (participant != null && participant.status() != ParticipantStatus.REMOVED) {
                run.updateParticipant(participant.withStatus(ParticipantStatus.ACTIVE, world.getGameTime()));
            }
            if (player != null) {
                player.setGameMode(GameType.SURVIVAL);
                player.setHealth(player.getMaxHealth());
                teleportToRespawn(world, run, spawner, player);
            }
        }
    }

    private static void teleportToRespawn(ServerLevel world, ArenaRun run, ArenaSpawnerBlockEntity spawner, ServerPlayer player) {
        BlockPos target = spawner.getAbsoluteEntrancePos();
        List<BlockPos> offsets = spawner.getRespawnPointOffsets();
        if (!offsets.isEmpty()) {
            Vec3 pos = player.position();
            target = offsets.stream().map(run.spawnerPos()::offset)
                .min(Comparator.comparingDouble(p -> pos.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)))
                .orElse(target);
        }
        ServerLevel entranceWorld = world.getServer().getLevel(spawner.getEntranceDimension());
        if (entranceWorld != null) {
            player.teleportTo(entranceWorld, target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        }
    }

    // ── Win / loss / closing ───────────────────────────────────────────────────

    private static void handleWin(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        run.setOutcome(ArenaOutcome.COMPLETED);
        for (UUID uuid : run.lootEligibleUuids()) {
            net.ledok.arenas_ld.util.PlayerStatsStore.get(world.getServer())
                .recordWin(uuid, net.ledok.arenas_ld.util.PlayerStatsStore.Mode.ARENA);
        }
        broadcast(world, run, Component.translatable("message.arenas_ld.arena.completed", run.currentWave()));
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            Component.translatable("title.arenas_ld.victory").withStyle(net.minecraft.ChatFormatting.GREEN), null,
            net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        enterClosing(world, controller, run);
    }

    private static void handleLoss(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run,
                                   ArenaOutcome outcome, String reason) {
        run.setOutcome(outcome);
        ArenasLdMod.LOGGER.info("Arena run ended at {} ({}): {}", run.spawnerPos(), outcome, reason);
        broadcast(world, run, Component.translatable("message.arenas_ld.arena.run_over", run.currentWave()));
        net.ledok.arenas_ld.util.RunFeedback.toAll(world, run.participants().keySet(),
            Component.translatable("title.arenas_ld.defeat").withStyle(net.minecraft.ChatFormatting.RED), null,
            net.minecraft.sounds.SoundEvents.ANVIL_LAND, 0.6f, 0.7f);
        enterClosing(world, controller, run);
    }

    private static void enterClosing(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        payoutSummary(world, controller, run);
        for (UUID uuid : new ArrayList<>(run.aliveMobs())) {
            Entity e = world.getEntity(uuid);
            if (e != null) e.discard();
        }
        run.clearAliveMobs();
        clearWaveBossBar(run);
        run.setPhase(ArenaPhase.CLOSING);
        int closeTicks = Math.max(0, controller.getCloseTimerSeconds() * 20);
        run.setInitialCloseTimerTicks(closeTicks);
        run.setCloseTimerTicks(closeTicks);
        if (closeTicks <= 0) {
            finalizeRun(world, controller, run);
            return;
        }
        sendExitPrompt(world, run);
    }

    /** Offers each remaining participant a clickable "[Exit Now]" to skip the close timer. */
    private static void sendExitPrompt(ServerLevel world, ArenaRun run) {
        net.minecraft.network.chat.MutableComponent button = net.ledok.arenas_ld.util.LobbyChatActions.button(
            Component.translatable("message.arenas_ld.run.exit_button"),
            0x55FF55,
            "/exit",
            Component.translatable("message.arenas_ld.run.exit_hover"));
        Component line = Component.translatable("message.arenas_ld.run.exit_prompt").append(button);
        for (Map.Entry<UUID, RunParticipant> entry : run.participants().entrySet()) {
            if (entry.getValue().status() == ParticipantStatus.REMOVED) continue;
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                player.sendSystemMessage(line);
            }
        }
    }

    /**
     * Pulls a single player out of a run that has already ended (CLOSING phase) before the
     * shared close timer elapses. Mirrors {@code DungeonRunLifecycle#exitEarly}.
     *
     * @return true if the player was in a finished arena run and was removed; false otherwise.
     */
    public static boolean exitEarly(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
        UUID uuid = player.getUUID();
        for (ArenaControllerBlockEntity.ControllerKey key : ArenaControllerBlockEntity.getControllers()) {
            ServerLevel world = server.getLevel(key.dimension());
            if (world == null) continue;
            if (!(world.getBlockEntity(key.pos()) instanceof ArenaControllerBlockEntity controller)) continue;
            for (ArenaRun run : controller.getActiveRuns().values()) {
                if (run.phase() != ArenaPhase.CLOSING) continue;
                RunParticipant participant = run.participants().get(uuid);
                if (participant == null || participant.status() == ParticipantStatus.REMOVED) continue;

                restoreToReturnPoint(world, run, uuid);
                run.removeReturnPoint(uuid);
                run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, world.getGameTime()));
                run.clearDowned(uuid);
                run.clearDisconnected(uuid);
                net.ledok.arenas_ld.util.BusyStateCompat.clearBusy(uuid, BUSY_REASON);

                boolean anyRemaining = run.participants().values().stream()
                    .anyMatch(p -> p.status() != ParticipantStatus.REMOVED);
                if (!anyRemaining) {
                    finalizeRun(world, controller, run);
                }
                return true;
            }
        }
        return false;
    }

    private static void tickClosing(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        run.setCloseTimerTicks(run.closeTimerTicks() - 1);
        updateCloseTimerBossBar(world, run);
        if (run.closeTimerTicks() <= 0) finalizeRun(world, controller, run);
    }

    /** Non-linear end-of-run summary: currency + skill XP scaled by deepest wave reached. */
    private static void payoutSummary(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        int waves = run.currentWave();
        if (waves <= 0) return;
        int multiplier = run.hardcoreEnabled() ? 2 : 1;
        long currency = controller.computeCurrencyReward(waves) * multiplier;
        int xp = controller.computeXpReward(waves) * multiplier;
        boolean skillsLoaded = FabricLoader.getInstance().isModLoaded("puffish_skills");

        for (UUID uuid : run.lootEligibleUuids()) {
            if (currency > 0) EconomyCompat.deliverCurrency(uuid, currency, "ARENA_REWARD");
            if (xp > 0 && skillsLoaded) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) PuffishSkillsCompat.addExperience(player, xp);
            }
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.arena.summary", waves, currency, xp));
            }
        }
    }

    private static void finalizeRun(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        for (UUID uuid : run.lootEligibleUuids()) {
            net.ledok.arenas_ld.util.PlayerStatsStore.get(world.getServer())
                .recordArenaWave(uuid, run.currentWave());
        }
        List<String> names = new ArrayList<>();
        for (UUID uuid : run.participants().keySet()) {
            RunParticipant p = run.participants().get(uuid);
            if (p != null) names.add(p.playerName());
            restoreToReturnPoint(world, run, uuid);
        }
        clearWaveBossBar(run);
        clearCloseTimerBossBar(run);
        run.setPhase(ArenaPhase.DONE);
        controller.onArenaEnded(run.spawnerPos(), run.currentWave(), names);
    }

    // ── Per-player handlers (invoked from the mixin via the manager in a later phase) ──

    public static void handlePlayerDown(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run, ServerPlayer player) {
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return;
        if (run.downedPlayers().containsKey(player.getUUID())) return;

        // SURVIVE_UNTOUCHED objective is failed the moment anyone goes down.
        if (run.currentObjective() == ObjectiveType.SURVIVE_UNTOUCHED) failObjective(world, run);

        run.updateParticipant(participant.withStatus(ParticipantStatus.DOWNED, world.getGameTime()));
        run.setDowned(new DownedPlayer(player.getUUID(), resolveRespawnTicks(controller)));
        run.setWaveTicksRemaining(Math.max(0, run.waveTicksRemaining() - resolveDeathPenaltyTicks(controller)));
        player.setHealth(player.getMaxHealth());
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.translatable("message.arenas_ld.arena.you_are_downed"));
    }

    public static void handlePlayerHardcoreDeath(ServerLevel world, ArenaRun run, ServerPlayer player) {
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) return;
        run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, world.getGameTime()));
        run.clearDowned(player.getUUID());
        run.clearDisconnected(player.getUUID());
        restoreToReturnPoint(world, run, player.getUUID());
        run.removeReturnPoint(player.getUUID());
        net.ledok.arenas_ld.util.BusyStateCompat.clearBusy(player.getUUID(), BUSY_REASON);
        player.sendSystemMessage(Component.translatable("message.arenas_ld.arena.hardcore_death")
            .withStyle(net.minecraft.ChatFormatting.RED));
    }

    public static void handlePlayerDisconnect(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run, ServerPlayer player) {
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null || participant.status() == ParticipantStatus.REMOVED) return;
        if (run.hardcoreEnabled()) {
            run.clearDisconnected(player.getUUID());
            handlePlayerHardcoreDeath(world, run, player);
            return;
        }
        run.markDisconnected(player.getUUID(), world.getGameTime());
        if (!run.downedPlayers().containsKey(player.getUUID())) {
            run.updateParticipant(participant.withStatus(ParticipantStatus.DOWNED, world.getGameTime()));
            run.setDowned(new DownedPlayer(player.getUUID(), resolveRespawnTicks(controller)));
        }
    }

    public static void handlePlayerReconnect(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run, ServerPlayer player) {
        if (player == null || run == null) return;
        RunParticipant participant = run.participants().get(player.getUUID());
        if (participant == null) return;
        run.clearDisconnected(player.getUUID());
        if (participant.status() == ParticipantStatus.REMOVED) {
            run.clearDowned(player.getUUID());
            restoreToReturnPoint(world, run, player.getUUID());
            return;
        }
        // Still part of the run — they wait out the wave as a spectator and are revived next wave.
        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(player.getMaxHealth());
    }

    private static void tickDisconnectedPlayers(ServerLevel world, ArenaControllerBlockEntity controller, ArenaRun run) {
        if (run.disconnectedAt().isEmpty()) return;
        long now = world.getGameTime();
        int grace = controller.getDisconnectGraceTicks();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(run.disconnectedAt()).entrySet()) {
            if (now - entry.getValue() <= grace) continue;
            UUID uuid = entry.getKey();
            RunParticipant participant = run.participants().get(uuid);
            if (participant != null) run.updateParticipant(participant.withStatus(ParticipantStatus.REMOVED, now));
            run.clearDisconnected(uuid);
            run.clearDowned(uuid);
            net.ledok.arenas_ld.util.BusyStateCompat.clearBusy(uuid, BUSY_REASON);
        }
    }

    private static void restoreToReturnPoint(ServerLevel world, ArenaRun run, UUID uuid) {
        PlayerReturnPoint rp = run.returnPoints().get(uuid);
        if (rp == null) return;
        GameType restoreMode = rp.previousGameMode();
        ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
        if (player == null) {
            // Offline at run end / forfeit — persist the eject so they're sent home on next login.
            net.ledok.arenas_ld.util.PendingRestoreStore.get(world.getServer()).put(uuid, rp);
            return;
        }
        player.setGameMode(restoreMode);
        player.setHealth(player.getMaxHealth());
        ServerLevel returnWorld = world.getServer().getLevel(rp.dimension());
        if (returnWorld != null) {
            player.teleportTo(returnWorld, rp.pos().x(), rp.pos().y(), rp.pos().z(), rp.yaw(), rp.pitch());
        }
    }

    // ── Boss bars ──────────────────────────────────────────────────────────────

    private static ServerBossEvent ensureWaveBossBar(ArenaRun run) {
        ServerBossEvent bar = run.getWaveBossBar();
        if (bar == null) {
            bar = (ServerBossEvent) new ServerBossEvent(Component.literal("Arena"),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS)
                .setDarkenScreen(false).setPlayBossMusic(false).setCreateWorldFog(false);
            run.setWaveBossBar(bar);
        }
        return bar;
    }

    private static void updateWaveBossBar(ServerLevel world, ArenaRun run, ArenaSpawnerBlockEntity spawner) {
        ServerBossEvent bar = ensureWaveBossBar(run);
        bar.setVisible(true);
        syncBarViewers(world, run, bar);
        if (run.prepareTicksRemaining() > 0) {
            int total = Math.max(1, spawner.getPrepareTime() * 20);
            bar.setName(Component.translatable("bossbar.arenas_ld.prepare_time", run.prepareTicksRemaining() / 20));
            bar.setProgress(Math.min(1f, (float) run.prepareTicksRemaining() / total));
        } else if (run.betweenWaveTicksRemaining() > 0) {
            int total = Math.max(1, spawner.getTimeBetweenWaves() * 20);
            bar.setName(Component.translatable("bossbar.arenas_ld.next_wave_in", run.currentWave() + 1, run.betweenWaveTicksRemaining() / 20));
            bar.setProgress(Math.min(1f, (float) run.betweenWaveTicksRemaining() / total));
        } else {
            int total = Math.max(1, (spawner.getWaveTimer() + Math.max(0, run.currentWave() - 1) * spawner.getAdditionalTime()) * 20);
            bar.setName(Component.translatable("bossbar.arenas_ld.wave_info", run.currentWave(), run.waveTicksRemaining() / 20));
            bar.setProgress(Math.min(1f, (float) run.waveTicksRemaining() / total));
        }
    }

    private static void clearWaveBossBar(ArenaRun run) {
        ServerBossEvent bar = run.getWaveBossBar();
        if (bar != null) {
            bar.removeAllPlayers();
            bar.setVisible(false);
            run.setWaveBossBar(null);
        }
    }

    private static void updateCloseTimerBossBar(ServerLevel world, ArenaRun run) {
        ServerBossEvent bar = run.getCloseTimerBossBar();
        if (bar == null) {
            bar = new ServerBossEvent(Component.translatable("boss_bar.arenas_ld.closing"),
                BossEvent.BossBarColor.BLUE, BossEvent.BossBarOverlay.PROGRESS);
            run.setCloseTimerBossBar(bar);
        }
        bar.setVisible(true);
        syncBarViewers(world, run, bar);
        int total = Math.max(1, run.initialCloseTimerTicks());
        bar.setProgress(Math.max(0f, Math.min(1f, (float) run.closeTimerTicks() / total)));
    }

    private static void clearCloseTimerBossBar(ArenaRun run) {
        ServerBossEvent bar = run.getCloseTimerBossBar();
        if (bar != null) {
            bar.removeAllPlayers();
            bar.setVisible(false);
            run.setCloseTimerBossBar(null);
        }
    }

    private static void syncBarViewers(ServerLevel world, ArenaRun run, ServerBossEvent bar) {
        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) bar.addPlayer(player);
        }
        for (ServerPlayer viewer : new ArrayList<>(bar.getPlayers())) {
            if (!run.participants().containsKey(viewer.getUUID())) bar.removePlayer(viewer);
        }
    }

    // ── Misc helpers ────────────────────────────────────────────────────────────

    private static List<ServerPlayer> onlineParticipants(ServerLevel world, ArenaRun run) {
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer p = world.getServer().getPlayerList().getPlayer(uuid);
            if (p != null) players.add(p);
        }
        return players;
    }

    private static void broadcast(ServerLevel world, ArenaRun run, Component message) {
        for (ServerPlayer player : onlineParticipants(world, run)) player.sendSystemMessage(message);
    }

    private static int resolveRespawnTicks(ArenaControllerBlockEntity controller) {
        return controller != null ? controller.getRespawnTimeTicks() : DOWNED_RESPAWN_TICKS_FALLBACK;
    }

    private static int resolveDeathPenaltyTicks(ArenaControllerBlockEntity controller) {
        return controller != null ? controller.getDeathTimePenaltyTicks() : DEATH_TIME_PENALTY_FALLBACK;
    }
}
