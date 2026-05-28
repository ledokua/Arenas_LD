package net.ledok.arenas_ld.raid.blockentity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.compat.PuffishSkillsCompat;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.dungeon.run.DownedPlayer;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.raid.run.RaidDifficulty;
import net.ledok.arenas_ld.raid.run.RaidRun;
import net.ledok.arenas_ld.raid.run.RaidRunLifecycle;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.ledok.arenas_ld.raid.screen.RaidBossSpawnerData;
import net.ledok.arenas_ld.raid.screen.RaidBossSpawnerScreenHandler;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class RaidBossSpawnerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<RaidBossSpawnerData>, AttributeProvider, EquipmentProvider {
    private static final int DOWNED_RESPAWN_TICKS = 60;
    private static final int REGEN_INTERVAL_TICKS = 100;
    private static final int DEATH_TIME_PENALTY_TICKS = 10 * 20;

    // --- Config ---
    public int respawnTime = 6000;
    public String lootTableId = "minecraft:chests/simple_dungeon";
    public String perPlayerLootTableId = "";
    public int battleRadius = 64;
    public int regeneration = 0;
    public int skillExperiencePerWin = 100;
    private String groupId = "";
    public double hpScalePerPlayer = 0.10;
    public int battleTimeLimitTicks = 0;
    public BlockPos entrancePosition = BlockPos.ZERO; // relative to spawner
    public ResourceKey<Level> entranceDimension = Level.OVERWORLD;
    public BlockPos exitPosition = BlockPos.ZERO; // absolute
    public ResourceKey<Level> exitDimension = Level.OVERWORLD;
    private final List<BlockPos> respawnPointOffsets = new ArrayList<>();
    private final Map<RaidDifficulty, RaidTierConfig> tierConfigs = new EnumMap<>(RaidDifficulty.class);
    /** Bundled entity config (mobId + attributes + equipment). Mirrors dungeon spawners. */
    private EntityDefinition entityDefinition = new EntityDefinition(
        "minecraft:zombie",
        new ArrayList<>(List.of(
            new AttributeData("minecraft:generic.max_health", 300.0),
            new AttributeData("minecraft:generic.attack_damage", 15.0)
        )),
        new EquipmentData()
    );

    // --- Runtime: spawner-local fields not yet migrated to RaidRun ---
    private final ServerBossEvent raidTimerBossBar = (ServerBossEvent) new ServerBossEvent(
            Component.translatable("gui.arenas_ld.raid_timer"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
    );
    protected int respawnCooldown = 0;
    private AABB cachedBattleBounds = null;
    private int cachedBattleBoundsRadius = Integer.MIN_VALUE;

    public RaidBossSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.RAID_BOSS_SPAWNER_BLOCK_ENTITY, pos, state);
        initializeTierConfigs();
    }

    private void markDirty() {
        super.setChanged();
    }

    private void markDirtyAndSync() {
        markDirty();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Resolve the live raid run anchored at this spawner, or null when there is none. */
    @Nullable
    private RaidRun findRun() {
        if (!(level instanceof ServerLevel sl)) return null;
        return RaidRunLifecycle.findRun(sl.getServer(), worldPosition);
    }

    private void initializeTierConfigs() {
        for (RaidDifficulty tier : RaidDifficulty.values()) {
            tierConfigs.putIfAbsent(tier, RaidTierConfig.defaultFor(tier));
        }
    }

    public Map<RaidDifficulty, RaidTierConfig> getTierConfigs() {
        return tierConfigs;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId == null ? "" : groupId.trim();
        markDirtyAndSync();
    }

    public void setTierConfig(RaidDifficulty tier, RaidTierConfig config) {
        if (tier == null || config == null) {
            return;
        }
        tierConfigs.put(tier, config);
        markDirtyAndSync();
    }

    public void addRespawnPointOffset(BlockPos relativePos) {
        if (relativePos == null) {
            return;
        }
        if (!respawnPointOffsets.contains(relativePos)) {
            respawnPointOffsets.add(relativePos);
            markDirtyAndSync();
        }
    }

    public boolean removeRespawnPointOffset(BlockPos relativePos) {
        if (relativePos == null) {
            return false;
        }
        if (respawnPointOffsets.remove(relativePos)) {
            markDirtyAndSync();
            return true;
        }
        return false;
    }

    public List<BlockPos> getRespawnPointOffsets() {
        return Collections.unmodifiableList(respawnPointOffsets);
    }

    public double computeExpectedHp(RaidDifficulty tier, int playerCount) {
        RaidDifficulty safeTier = tier != null ? tier : RaidDifficulty.NORMAL;
        RaidTierConfig config = tierConfigs.getOrDefault(safeTier, RaidTierConfig.defaultFor(safeTier));
        return computeExpectedHp(safeTier, playerCount, config, hpScalePerPlayer);
    }

    public double computeExpectedHp(RaidDifficulty tier, int playerCount, RaidTierConfig config, double hpScale) {
        RaidDifficulty safeTier = tier != null ? tier : RaidDifficulty.NORMAL;
        RaidTierConfig safeConfig = config != null ? config : tierConfigs.getOrDefault(safeTier, RaidTierConfig.defaultFor(safeTier));
        int safePlayerCount = Math.max(1, playerCount);
        double baseHp = getConfiguredBaseMaxHealth();
        double healthMult = safeConfig.healthMultiplier();
        double perPlayerMult = Math.pow(1.0 + hpScale, Math.max(0, safePlayerCount - 1));
        return baseHp * healthMult * perPlayerMult;
    }

    private double getConfiguredBaseMaxHealth() {
        for (AttributeData attr : entityDefinition.attributes()) {
            if ("minecraft:generic.max_health".equals(attr.id())) {
                return attr.value();
            }
        }
        return 20.0;
    }

    public EntityDefinition getEntityDefinition() {
        return entityDefinition;
    }

    public void setEntityDefinition(EntityDefinition def) {
        this.entityDefinition = def;
        markDirtyAndSync();
    }

    public String getMobId() {
        return entityDefinition.mobId();
    }

    public void setMobId(String id) {
        this.entityDefinition = entityDefinition.withMobId(id);
        markDirtyAndSync();
    }

    @Override
    public List<AttributeData> getAttributes() {
        return entityDefinition.attributes();
    }

    @Override
    public void setAttributes(List<AttributeData> attributes) {
        this.entityDefinition = entityDefinition.withAttributes(new ArrayList<>(attributes));
        markDirtyAndSync();
    }

    @Override
    public EquipmentData getEquipment() {
        return entityDefinition.equipment();
    }

    @Override
    public void setEquipment(EquipmentData equipment) {
        this.entityDefinition = entityDefinition.withEquipment(equipment);
        markDirtyAndSync();
    }

    public static void tick(Level world, BlockPos pos, BlockState state, RaidBossSpawnerBlockEntity be) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) return;

        RaidRun run = be.findRun();
        if (run != null) {
            be.handleActiveBattle(serverLevel, run);
        } else {
            be.handleIdleState();
        }
    }

    @Override
    public void setRemoved() {
        ArenasLdMod.RAID_BOSS_MANAGER.unregisterSpawner(this);
        super.setRemoved();
    }

    protected void handleIdleState() {
        if (respawnCooldown > 0) {
            respawnCooldown--;
        }
    }

    protected void handleActiveBattle(ServerLevel world, RaidRun run) {
        // Idempotent re-registration so server restarts with persisted runs
        // re-link with the boss manager on first tick.
        ArenasLdMod.RAID_BOSS_MANAGER.registerSpawner(this);

        UUID bossUuid = run.bossUuid();
        ResourceKey<Level> bossDim = run.bossDimension();
        if (bossUuid == null || bossDim == null) {
            handleBattleLoss(world, "Boss reference was null.");
            return;
        }

        ServerLevel bossWorld = world.getServer().getLevel(bossDim);
        if (bossWorld == null) {
            handleBattleLoss(world, "Boss world was null.");
            return;
        }

        Entity bossEntity = bossWorld.getEntity(bossUuid);
        if (bossEntity == null) {
            handleBattleLoss(world, "Boss entity disappeared.");
            return;
        }

        if (!bossEntity.isAlive()) {
            handleBattleWin(world, bossEntity);
            return;
        }

        if (battleTimeLimitTicks > 0) {
            int remaining = run.timerTicks() - 1;
            RaidRunLifecycle.setTimerTicks(run, Math.max(0, remaining));
            updateRaidTimerBossBar(world, run);
            if (remaining <= 0) {
                bossEntity.discard();
                handleBattleLoss(world, "Time ran out.");
                return;
            }
        }

        Set<UUID> participantIds = run.participants().keySet();
        Map<UUID, DownedPlayer> downed = run.downedPlayers();
        boolean hardcore = run.hardcoreEnabled();

        int nextBounds = run.boundsTickCounter() + 1;
        if (nextBounds >= 20) {
            RaidRunLifecycle.setBoundsTickCounter(run, 0);
            enforceBattleBounds(world);
            for (UUID uuid : participantIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.isSpectator() || downed.containsKey(uuid)) {
                    continue;
                }
                if (player.getHealth() <= 1.0F) {
                    if (hardcore) {
                        handlePlayerHardcoreDeath(player);
                    } else {
                        handlePlayerDown(player);
                    }
                }
            }
        } else {
            RaidRunLifecycle.setBoundsTickCounter(run, nextBounds);
        }

        tickDownedPlayers(world, run);

        if (hardcore) {
            boolean anyFighting = participantIds.stream().anyMatch(id -> {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(id);
                return player != null && player.isAlive() && !player.isSpectator();
            });
            if (!anyFighting && downed.isEmpty()) {
                bossEntity.discard();
                handleBattleLoss(world, "All players eliminated.");
                return;
            }
        }

        if (regeneration > 0 && bossEntity instanceof LivingEntity livingBoss) {
            int nextRegen = run.regenerationTickTimer() + 1;
            if (nextRegen >= REGEN_INTERVAL_TICKS) {
                livingBoss.heal((float) regeneration);
                RaidRunLifecycle.setRegenerationTickTimer(run, 0);
            } else {
                RaidRunLifecycle.setRegenerationTickTimer(run, nextRegen);
            }
        }
    }

    public void startBattle(
            ServerLevel world,
            List<ServerPlayer> players,
            RaidDifficulty difficulty,
            boolean hardcoreEnabled,
            int battleTimeLimitTicks
    ) {
        if (players == null || players.isEmpty()) {
            return;
        }

        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(entityDefinition.mobId());
        Component mobDisplayName = entityTypeOpt.map(EntityType::getDescription).orElse(Component.literal(entityDefinition.mobId()));
        Component announcement = Component.translatable(
                "message.arenas_ld.raid_start",
                players.get(0).getDisplayName(),
                mobDisplayName
        ).withStyle(net.minecraft.ChatFormatting.GOLD);
        world.getServer().getPlayerList().broadcastSystemMessage(announcement, false);

        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.error("Invalid mob ID in spawner at {}: {}", this.worldPosition, entityDefinition.mobId());
            return;
        }

        Entity boss = entityTypeOpt.get().create(world);
        if (boss == null) {
            ArenasLdMod.LOGGER.error("Failed to create entity from ID: {}", entityDefinition.mobId());
            return;
        }

        RaidDifficulty diff = difficulty != null ? difficulty : RaidDifficulty.NORMAL;
        this.battleTimeLimitTicks = Math.max(0, battleTimeLimitTicks);

        RaidRun run = findRun();
        long now = world.getGameTime();
        // Spawner's battleTimeLimitTicks (UI-configurable) overrides the tier-config
        // value the run was constructed with. 0 means "no time limit".
        if (run != null && this.battleTimeLimitTicks > 0) {
            RaidRunLifecycle.setTimerTicks(run, this.battleTimeLimitTicks);
        }
        for (ServerPlayer p : players) {
            if (run != null) {
                RaidRunLifecycle.addParticipant(run, new RunParticipant(
                    p.getUUID(), p.getGameProfile().getName(), ParticipantStatus.ACTIVE, now));
                RaidRunLifecycle.setReturnPoint(run, p.getUUID(), PlayerReturnPoint.capture(p));
            }
            raidTimerBossBar.addPlayer(p);
        }
        if (this.battleTimeLimitTicks > 0) {
            raidTimerBossBar.setVisible(true);
            raidTimerBossBar.setProgress(1.0f);
            raidTimerBossBar.setName(Component.translatable("gui.arenas_ld.raid_timer_remaining", formatTime(this.battleTimeLimitTicks / 20)));
        } else {
            raidTimerBossBar.setVisible(false);
        }

        if (boss instanceof LivingEntity livingBoss) {
            RaidTierConfig tierCfg = tierConfigs.getOrDefault(diff, RaidTierConfig.defaultFor(diff));
            double healthMult = tierCfg.healthMultiplier();
            double damageMult = tierCfg.damageMultiplier();
            double perPlayerMult = Math.pow(1.0 + hpScalePerPlayer, Math.max(0, players.size() - 1));

            for (AttributeData attr : entityDefinition.attributes()) {
                ResourceLocation attrLocation = ResourceLocation.tryParse(attr.id());
                if (attrLocation == null) continue;
                var attributeRegistry = world.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
                ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, attrLocation);
                attributeRegistry.getHolder(key).ifPresent(holder -> {
                    AttributeInstance instance = livingBoss.getAttribute(holder);
                    if (instance == null) return;
                    double value = attr.value();
                    if ("minecraft:generic.max_health".equals(attr.id())) {
                        value = value * healthMult * perPlayerMult;
                    } else if ("minecraft:generic.attack_damage".equals(attr.id())) {
                        value = value * damageMult;
                    }
                    instance.setBaseValue(value);
                });
            }

            EquipmentData equip = entityDefinition.equipment();
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.HEAD, equip.head, equip.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.CHEST, equip.chest, equip.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.LEGS, equip.legs, equip.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.FEET, equip.feet, equip.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.MAINHAND, equip.mainHand, equip.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.OFFHAND, equip.offHand, equip.dropChance);

            livingBoss.heal(livingBoss.getMaxHealth());
            String teamName = this.groupId == null || this.groupId.isBlank() ? "arenas_ld" : this.groupId;
            Scoreboard scoreboard = world.getScoreboard();
            PlayerTeam team = scoreboard.getPlayerTeam(teamName);
            if (team == null) {
                team = scoreboard.addPlayerTeam(teamName);
                team.setAllowFriendlyFire(false);
            }
            scoreboard.addPlayerToTeam(livingBoss.getScoreboardName(), team);
        }

        boss.moveTo(worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, 0, 0);
        world.addFreshEntity(boss);

        ServerLevel entranceWorld = world.getServer().getLevel(entranceDimension);
        BlockPos absoluteEntrance = worldPosition.offset(entrancePosition);
        for (ServerPlayer player : players) {
            player.setGameMode(GameType.ADVENTURE);
            player.setHealth(player.getMaxHealth());
            if (entranceWorld != null) {
                player.teleportTo(
                        entranceWorld,
                        absoluteEntrance.getX() + 0.5,
                        absoluteEntrance.getY(),
                        absoluteEntrance.getZ() + 0.5,
                        player.getYRot(),
                        player.getXRot()
                );
            }
        }

        this.respawnCooldown = 0;
        if (run != null) {
            RaidRunLifecycle.setBossRef(run, boss.getUUID(), world.dimension());
            RaidRunLifecycle.setBoundsTickCounter(run, 0);
            RaidRunLifecycle.setRegenerationTickTimer(run, 0);
        }
        ArenasLdMod.RAID_BOSS_MANAGER.registerSpawner(this);
        markDirtyAndSync();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        ArenasLdMod.LOGGER.info(
                "Raid battle started at {} with boss {}, tier={}, players={}",
                this.worldPosition, entityDefinition.mobId(), diff, players.size()
        );
    }

    protected void handleBattleWin(ServerLevel world, Entity defeatedBoss) {
        ArenasLdMod.LOGGER.info("Raid battle won at spawner {}", worldPosition);

        // Capture the run before notifyController removes it from activeRuns.
        RaidRun run = findRun();
        Set<UUID> participantIds = run != null ? run.participants().keySet() : Set.of();

        if (this.skillExperiencePerWin > 0) {
            for (UUID uuid : participantIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && FabricLoader.getInstance().isModLoaded("puffish_skills")) {
                    PuffishSkillsCompat.addExperience(player, this.skillExperiencePerWin);
                }
            }
        }

        RaidDifficulty diff = run != null ? RaidDifficulty.from(run.tier()) : RaidDifficulty.NORMAL;
        RaidTierConfig tierCfg = tierConfigs.getOrDefault(diff, RaidTierConfig.defaultFor(diff));
        String resolvedLootTableId = lootTableId;
        String resolvedPerPlayerLootTableId = tierCfg.perPlayerLootTable().isEmpty() ? perPlayerLootTableId : tierCfg.perPlayerLootTable();

        ResourceLocation lootTableIdentifier = ResourceLocation.tryParse(resolvedLootTableId);
        if (lootTableIdentifier != null) {
            LootTable lootTable = world.getServer().reloadableRegistries().getLootTable(
                    ResourceKey.create(Registries.LOOT_TABLE, lootTableIdentifier)
            );

            LootParams.Builder builder = new LootParams.Builder(world)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                    .withParameter(LootContextParams.THIS_ENTITY, defeatedBoss);

            LootParams lootParams = builder.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.GIFT);
            lootTable.getRandomItems(lootParams).forEach(stack -> {
                double x = worldPosition.getX() + 0.5 + (world.random.nextDouble() * 8.0) - 4.0;
                double y = worldPosition.getY() + 3.5;
                double z = worldPosition.getZ() + 0.5 + (world.random.nextDouble() * 8.0) - 4.0;
                ItemEntity itemEntity = new ItemEntity(world, x, y, z, stack);
                itemEntity.setDeltaMovement(world.random.nextDouble() * 0.2 - 0.1, 0.4, world.random.nextDouble() * 0.2 - 0.1);
                world.addFreshEntity(itemEntity);
            });
        }

        if (!resolvedPerPlayerLootTableId.isEmpty()) {
            for (UUID uuid : participantIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null) {
                    continue;
                }
                ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
                bundle.set(DataComponentRegistry.LOOT_BUNDLE_DATA, new LootBundleDataComponent(resolvedPerPlayerLootTableId));
                if (!player.getInventory().add(bundle)) {
                    player.drop(bundle, false);
                }
            }
        }

        notifyController(world, true, run);
        resetAfterBattle(world, run);
    }

    protected void handleBattleLoss(ServerLevel world, String reason) {
        ArenasLdMod.LOGGER.info("Raid battle lost at spawner {}: {}", worldPosition, reason);

        // Capture the run before notifyController removes it from activeRuns.
        RaidRun run = findRun();

        UUID bossUuid = run != null ? run.bossUuid() : null;
        ResourceKey<Level> bossDim = run != null ? run.bossDimension() : null;
        if (bossUuid != null && bossDim != null) {
            ServerLevel bossWorld = world.getServer().getLevel(bossDim);
            if (bossWorld != null) {
                Entity bossEntity = bossWorld.getEntity(bossUuid);
                if (bossEntity != null && bossEntity.isAlive()) {
                    bossEntity.discard();
                    ArenasLdMod.LOGGER.info("Despawned boss after battle loss at {}.", worldPosition);
                }
            }
        }

        notifyController(world, false, run);
        resetAfterBattle(world, run);
    }

    private void resetAfterBattle(ServerLevel world, @Nullable RaidRun run) {
        ServerLevel exitWorld = world.getServer().getLevel(exitDimension);
        BlockPos absoluteExit = exitPosition;
        if (run != null) {
            for (UUID uuid : run.participants().keySet()) {
                PlayerReturnPoint rp = run.returnPoints().get(uuid);
                GameType restoreMode = rp != null ? rp.previousGameMode() : GameType.SURVIVAL;
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null) {
                    ArenasLdMod.RAID_BOSS_MANAGER.addPendingRestore(uuid, exitPosition, exitDimension, restoreMode);
                    continue;
                }
                player.setGameMode(restoreMode);
                player.setHealth(player.getMaxHealth());
                if (exitWorld != null) {
                    player.teleportTo(
                            exitWorld,
                            absoluteExit.getX() + 0.5,
                            absoluteExit.getY(),
                            absoluteExit.getZ() + 0.5,
                            player.getYRot(),
                            player.getXRot()
                    );
                }
            }
        }

        // 1-tick safety buffer so controller state can transition instance status first.
        this.respawnCooldown = 1;
        this.raidTimerBossBar.removeAllPlayers();
        this.raidTimerBossBar.setVisible(false);
        ArenasLdMod.RAID_BOSS_MANAGER.unregisterSpawner(this);
        markDirtyAndSync();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean isHardcoreEnabled() {
        RaidRun run = findRun();
        return run != null && run.hardcoreEnabled();
    }

    public double getEffectiveDamageMultiplier() {
        RaidRun run = findRun();
        RaidDifficulty diff = run != null ? RaidDifficulty.from(run.tier()) : RaidDifficulty.NORMAL;
        RaidTierConfig tierCfg = tierConfigs.getOrDefault(diff, RaidTierConfig.defaultFor(diff));
        return tierCfg.damageMultiplier();
    }

    public boolean isTracked(UUID playerId) {
        if (playerId == null) return false;
        RaidRun run = findRun();
        return run != null && run.participants().containsKey(playerId);
    }

    public boolean isRaidRunning() {
        return findRun() != null;
    }

    /**
     * Resolve the death-time-penalty by querying the linked controller, falling back to the
     * legacy constant if none is loaded. Controller is the source of truth.
     */
    private int resolveDeathTimePenaltyTicks() {
        if (!(level instanceof ServerLevel sl) || sl.getServer() == null) return DEATH_TIME_PENALTY_TICKS;
        RaidControllerBlockEntity ctrl = RaidRunLifecycle.findOwningController(sl.getServer(), worldPosition);
        return ctrl != null ? ctrl.getDeathTimePenaltyTicks() : DEATH_TIME_PENALTY_TICKS;
    }

    public void handlePlayerDown(ServerPlayer player) {
        if (player == null) return;
        RaidRun run = findRun();
        if (run == null || !run.participants().containsKey(player.getUUID())) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return;
        if (run.downedPlayers().containsKey(player.getUUID())) return;

        player.setHealth(1.0F);
        player.setGameMode(GameType.SPECTATOR);
        RaidRunLifecycle.setTimerTicks(run, Math.max(0, run.timerTicks() - resolveDeathTimePenaltyTicks()));
        RaidRunLifecycle.setDowned(run, new DownedPlayer(player.getUUID(), DOWNED_RESPAWN_TICKS));
        markDirtyAndSync();
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        if (player == null) return;
        RaidRun run = findRun();
        if (run == null || !run.participants().containsKey(player.getUUID())) return;

        if (run.hardcoreEnabled()) {
            RaidRunLifecycle.clearDisconnected(run, player.getUUID());
            handlePlayerHardcoreDeath(player);
            return;
        }

        RaidRunLifecycle.markDisconnected(run, player.getUUID(),
            level instanceof ServerLevel sl ? sl.getGameTime() : 0L);
        if (!run.downedPlayers().containsKey(player.getUUID())) {
            if (battleTimeLimitTicks > 0) {
                RaidRunLifecycle.setTimerTicks(run, Math.max(0, run.timerTicks() - resolveDeathTimePenaltyTicks()));
            }
            RaidRunLifecycle.setDowned(run, new DownedPlayer(player.getUUID(), DOWNED_RESPAWN_TICKS));
        }
        markDirtyAndSync();
    }

    public void handlePlayerReconnect(ServerPlayer player) {
        if (player == null) return;
        RaidRun run = findRun();
        if (run == null) {
            ArenasLdMod.RAID_BOSS_MANAGER.handlePostBattleReconnect(player);
            return;
        }
        if (!run.participants().containsKey(player.getUUID())) return;

        RaidRunLifecycle.clearDisconnected(run, player.getUUID());

        DownedPlayer downed = run.downedPlayers().get(player.getUUID());
        if (downed == null) {
            downed = new DownedPlayer(player.getUUID(), DOWNED_RESPAWN_TICKS);
            RaidRunLifecycle.setDowned(run, downed);
        }
        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(player.getMaxHealth());
        if (downed.isReadyToRespawn()) {
            respawnAtEntrance(player.serverLevel(), player);
            RaidRunLifecycle.clearDowned(run, player.getUUID());
        }
    }

    public void handlePlayerHardcoreDeath(ServerPlayer player) {
        if (player == null) return;
        RaidRun run = findRun();
        if (run == null || !run.participants().containsKey(player.getUUID())) return;

        player.setGameMode(GameType.SPECTATOR);
        RaidRunLifecycle.clearDowned(run, player.getUUID());
        RaidRunLifecycle.removeParticipant(run, player.getUUID());
        markDirtyAndSync();
    }

    private void tickDownedPlayers(ServerLevel world, @Nullable RaidRun run) {
        if (run == null || run.downedPlayers().isEmpty()) return;

        // Snapshot the keys so we can mutate via the lifecycle while iterating.
        for (UUID uuid : new ArrayList<>(run.downedPlayers().keySet())) {
            DownedPlayer downed = run.downedPlayers().get(uuid);
            if (downed == null) continue;
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                RaidRunLifecycle.setDowned(run, downed.tick());
                continue;
            }
            player.setDeltaMovement(0, 0, 0);
            DownedPlayer ticked = downed.tick();
            if (ticked.isReadyToRespawn()) {
                respawnAtEntrance(world, player);
                RaidRunLifecycle.clearDowned(run, uuid);
            } else {
                RaidRunLifecycle.setDowned(run, ticked);
            }
        }
    }

    private void respawnAtEntrance(ServerLevel world, ServerPlayer player) {
        if (player == null || findRun() == null) {
            return;
        }

        BlockPos target = worldPosition.offset(entrancePosition);
        if (!respawnPointOffsets.isEmpty()) {
            Vec3 playerPos = player.position();
            target = respawnPointOffsets.stream()
                    .map(worldPosition::offset)
                    .min(Comparator.comparingDouble(p ->
                            playerPos.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5)))
                    .orElse(target);
        }

        ServerLevel entranceWorld = world.getServer().getLevel(entranceDimension);
        player.setGameMode(GameType.ADVENTURE);
        player.setHealth(player.getMaxHealth());
        if (entranceWorld != null) {
            player.teleportTo(
                    entranceWorld,
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5,
                    player.getYRot(),
                    player.getXRot()
            );
        }
    }

    private void updateRaidTimerBossBar(ServerLevel world, RaidRun run) {
        if (battleTimeLimitTicks <= 0) {
            raidTimerBossBar.setVisible(false);
            return;
        }
        if (!raidTimerBossBar.isVisible()) {
            raidTimerBossBar.setVisible(true);
        }
        int remainingTicks = Math.max(0, run.timerTicks());
        float progress = (float) remainingTicks / (float) battleTimeLimitTicks;
        raidTimerBossBar.setProgress(Mth.clamp(progress, 0.0f, 1.0f));
        raidTimerBossBar.setName(Component.translatable("gui.arenas_ld.raid_timer_remaining", formatTime(remainingTicks / 20)));

        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && !raidTimerBossBar.getPlayers().contains(player)) {
                raidTimerBossBar.addPlayer(player);
            }
        }
    }

    private void enforceBattleBounds(ServerLevel world) {
        AABB battleBox = getCachedBattleBounds();
        RaidRun run = findRun();
        if (run == null) return;
        Map<UUID, DownedPlayer> downed = run.downedPlayers();
        for (UUID uuid : run.participants().keySet()) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null || player.isSpectator() || downed.containsKey(uuid)) {
                continue;
            }
            if (player.level() != world || !battleBox.contains(player.position())) {
                player.teleportTo(
                        world,
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 1,
                        worldPosition.getZ() + 0.5,
                        player.getYRot(),
                        player.getXRot()
                );
                player.sendSystemMessage(Component.translatable("message.arenas_ld.raid_out_of_bounds"));
            }
        }
    }

    private AABB getCachedBattleBounds() {
        if (cachedBattleBounds == null || cachedBattleBoundsRadius != battleRadius) {
            cachedBattleBounds = new AABB(worldPosition).inflate(battleRadius);
            cachedBattleBoundsRadius = battleRadius;
        }
        return cachedBattleBounds;
    }

    private void notifyController(ServerLevel world, boolean wasWin, @Nullable RaidRun run) {
        RaidControllerBlockEntity controller = RaidRunLifecycle.findOwningController(world.getServer(), worldPosition);
        if (controller == null) return;

        List<String> names = run != null
            ? run.participants().values().stream().map(RunParticipant::playerName).toList()
            : List.of();
        int elapsed = run != null ? Math.max(0, (int) ((world.getGameTime() - run.startTick()) / 20L)) : 0;
        RaidDifficulty diff = run != null ? RaidDifficulty.from(run.tier()) : RaidDifficulty.NORMAL;
        controller.onRaidEnded(this.worldPosition, wasWin, diff, new ArrayList<>(names), elapsed);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        EntityDefinition.CODEC.encodeStart(NbtOps.INSTANCE, entityDefinition)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save EntityDefinition at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("EntityDefinition", tag));
        nbt.putInt("RespawnTime", respawnTime);
        nbt.putString("LootTableId", lootTableId);
        nbt.putString("PerPlayerLootTableId", perPlayerLootTableId);
        nbt.putInt("BattleRadius", battleRadius);
        nbt.putInt("Regeneration", regeneration);
        nbt.putInt("SkillExperiencePerWin", skillExperiencePerWin);
        nbt.putString("GroupId", groupId);
        nbt.putDouble("HpScalePerPlayer", hpScalePerPlayer);
        nbt.putInt("BattleTimeLimitTicks", battleTimeLimitTicks);
        nbt.putLong("EntrancePosition", entrancePosition.asLong());
        nbt.putString("EntranceDimension", entranceDimension.location().toString());
        nbt.putLong("ExitPosition", exitPosition.asLong());
        nbt.putString("ExitDimension", exitDimension.location().toString());
        nbt.putLongArray("RespawnPointOffsets", respawnPointOffsets.stream().mapToLong(BlockPos::asLong).toArray());

        nbt.putInt("RespawnCooldown", respawnCooldown);

        CompoundTag tierConfigsTag = new CompoundTag();
        for (RaidDifficulty tier : RaidDifficulty.values()) {
            tierConfigsTag.put(tier.name(), tierConfigs.getOrDefault(tier, RaidTierConfig.defaultFor(tier)).toNbt());
        }
        nbt.put("RaidTierConfigs", tierConfigsTag);

    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        respawnTime = nbt.getInt("RespawnTime");
        lootTableId = nbt.getString("LootTableId");
        perPlayerLootTableId = nbt.getString("PerPlayerLootTableId");
        battleRadius = nbt.getInt("BattleRadius");
        regeneration = nbt.getInt("Regeneration");
        skillExperiencePerWin = nbt.getInt("SkillExperiencePerWin");
        groupId = nbt.getString("GroupId");
        hpScalePerPlayer = nbt.contains("HpScalePerPlayer") ? nbt.getDouble("HpScalePerPlayer") : 0.10;
        battleTimeLimitTicks = nbt.contains("BattleTimeLimitTicks") ? nbt.getInt("BattleTimeLimitTicks") : 0;
        entrancePosition = nbt.contains("EntrancePosition", Tag.TAG_LONG) ? BlockPos.of(nbt.getLong("EntrancePosition")) : BlockPos.ZERO;
        if (nbt.contains("EntranceDimension")) {
            entranceDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("EntranceDimension")));
        } else {
            entranceDimension = Level.OVERWORLD;
        }
        exitPosition = nbt.contains("ExitPosition", Tag.TAG_LONG) ? BlockPos.of(nbt.getLong("ExitPosition")) : BlockPos.ZERO;
        if (nbt.contains("ExitDimension")) {
            exitDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("ExitDimension")));
        } else {
            exitDimension = Level.OVERWORLD;
        }
        respawnPointOffsets.clear();
        if (nbt.contains("RespawnPointOffsets", Tag.TAG_LONG_ARRAY)) {
            for (long posLong : nbt.getLongArray("RespawnPointOffsets")) {
                respawnPointOffsets.add(BlockPos.of(posLong));
            }
        }

        respawnCooldown = nbt.getInt("RespawnCooldown");

        tierConfigs.clear();
        initializeTierConfigs();
        if (nbt.contains("RaidTierConfigs", Tag.TAG_COMPOUND)) {
            CompoundTag tierConfigsTag = nbt.getCompound("RaidTierConfigs");
            for (RaidDifficulty tier : RaidDifficulty.values()) {
                if (tierConfigsTag.contains(tier.name(), Tag.TAG_COMPOUND)) {
                    tierConfigs.put(tier, RaidTierConfig.fromNbt(tierConfigsTag.getCompound(tier.name())));
                }
            }
        }

        if (nbt.contains("EntityDefinition", Tag.TAG_COMPOUND)) {
            EntityDefinition.CODEC.parse(NbtOps.INSTANCE, nbt.getCompound("EntityDefinition"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load EntityDefinition at {}: {}", worldPosition, err))
                .ifPresent(def -> this.entityDefinition = def);
        } else {
            // Legacy migration: assemble EntityDefinition from old top-level fields.
            String legacyMobId = nbt.contains("MobId") ? nbt.getString("MobId") : "minecraft:zombie";
            List<AttributeData> legacyAttrs = new ArrayList<>();
            ListTag attributeList = nbt.getList("Attributes", Tag.TAG_COMPOUND);
            for (Tag tag : attributeList) {
                legacyAttrs.add(AttributeData.fromNbt((CompoundTag) tag));
            }
            if (legacyAttrs.isEmpty()) {
                legacyAttrs.add(new AttributeData("minecraft:generic.max_health", 300.0));
                legacyAttrs.add(new AttributeData("minecraft:generic.attack_damage", 15.0));
            }
            EquipmentData legacyEquip = nbt.contains("Equipment")
                ? EquipmentData.fromNbt(nbt.getCompound("Equipment"))
                : new EquipmentData();
            this.entityDefinition = new EntityDefinition(legacyMobId, legacyAttrs, legacyEquip);
        }
        // Active-run registration happens lazily on first tick via findRun();
        // we can't reliably consult the controller's activeRuns during loadAdditional
        // because controllers in other dimensions may not have loaded yet.
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        return saveWithoutMetadata(registryLookup);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arenas_ld.boss_spawner_config");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new RaidBossSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public RaidBossSpawnerData getScreenOpeningData(ServerPlayer player) {
        return new RaidBossSpawnerData(this.worldPosition);
    }

    private static String formatTime(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        int minutes = clamped / 60;
        int seconds = clamped % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

}
