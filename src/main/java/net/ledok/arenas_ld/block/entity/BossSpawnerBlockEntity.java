package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.compat.PuffishSkillsCompat;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.screen.BossSpawnerData;
import net.ledok.arenas_ld.screen.BossSpawnerScreenHandler;
import net.ledok.arenas_ld.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

public class BossSpawnerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BossSpawnerData>, AttributeProvider, EquipmentProvider {
    private static final int DOWNED_RESPAWN_TICKS = 60;
    private static final int REGEN_INTERVAL_TICKS = 100;
    private static final int DEATH_TIME_PENALTY_TICKS = 10 * 20;

    // --- Config ---
    public String mobId = "minecraft:zombie";
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
    private final List<AttributeData> attributes = new ArrayList<>();
    private EquipmentData equipment = new EquipmentData();

    // --- Runtime from controller ---
    private BlockPos controllerPos = null;
    private ResourceKey<Level> controllerDimension = null;
    private RaidDifficulty activeDifficulty = RaidDifficulty.NORMAL;
    private int trackedPlayerCount = 0;
    private long battleStartTime = -1;
    private boolean hardcoreEnabled = false;
    private final List<UUID> trackedPlayerIds = new ArrayList<>();
    private final List<String> trackedPlayerNames = new ArrayList<>();
    private final Map<UUID, DownedPlayer> downedPlayers = new HashMap<>();
    private final Set<UUID> disconnectedPlayers = new HashSet<>();
    private final ServerBossEvent raidTimerBossBar = (ServerBossEvent) new ServerBossEvent(
            Component.translatable("gui.arenas_ld.raid_timer"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS
    );

    // --- Runtime state ---
    protected boolean isBattleActive = false;
    protected int respawnCooldown = 0;
    protected UUID activeBossUuid = null;
    protected ResourceKey<Level> bossDimension = null;
    protected int regenerationTickTimer = 0;
    protected int boundsTickCounter = 0;
    private final Map<UUID, GameType> playerGameModesBeforeRaid = new HashMap<>();
    private AABB cachedBattleBounds = null;
    private int cachedBattleBoundsRadius = Integer.MIN_VALUE;

    public BossSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.BOSS_SPAWNER_BLOCK_ENTITY, pos, state);
        initializeTierConfigs();
        if (attributes.isEmpty()) {
            attributes.add(new AttributeData("minecraft:generic.max_health", 300.0));
            attributes.add(new AttributeData("minecraft:generic.attack_damage", 15.0));
        }
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
        double healthMult = safeConfig.healthMultOverride() > 0 ? safeConfig.healthMultOverride() : safeTier.healthMult;
        double perPlayerMult = Math.pow(1.0 + hpScale, Math.max(0, safePlayerCount - 1));
        return baseHp * healthMult * perPlayerMult;
    }

    private double getConfiguredBaseMaxHealth() {
        for (AttributeData attr : attributes) {
            if ("minecraft:generic.max_health".equals(attr.id())) {
                return attr.value();
            }
        }
        return 20.0;
    }

    @Override
    public List<AttributeData> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(List<AttributeData> attributes) {
        this.attributes.clear();
        this.attributes.addAll(attributes);
        markDirtyAndSync();
    }

    @Override
    public EquipmentData getEquipment() {
        return equipment;
    }

    @Override
    public void setEquipment(EquipmentData equipment) {
        this.equipment = equipment;
        markDirtyAndSync();
    }

    public static void tick(Level world, BlockPos pos, BlockState state, BossSpawnerBlockEntity be) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) return;

        if (be.isBattleActive) {
            be.handleActiveBattle(serverLevel);
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

    protected void handleActiveBattle(ServerLevel world) {
        if (activeBossUuid == null) {
            handleBattleLoss(world, "Boss UUID was null.");
            return;
        }

        ServerLevel bossWorld = world.getServer().getLevel(bossDimension);
        if (bossWorld == null) {
            handleBattleLoss(world, "Boss world was null.");
            return;
        }

        Entity bossEntity = bossWorld.getEntity(activeBossUuid);
        if (bossEntity == null) {
            handleBattleLoss(world, "Boss entity disappeared.");
            return;
        }

        if (!bossEntity.isAlive()) {
            handleBattleWin(world, bossEntity);
            return;
        }

        if (battleTimeLimitTicks > 0 && battleStartTime >= 0) {
            long elapsedTicks = world.getGameTime() - battleStartTime;
            updateRaidTimerBossBar(world, elapsedTicks);
            if (elapsedTicks >= battleTimeLimitTicks) {
                bossEntity.discard();
                handleBattleLoss(world, "Time ran out.");
                return;
            }
        }

        if (++boundsTickCounter >= 20) {
            boundsTickCounter = 0;
            enforceBattleBounds(world);
            for (UUID uuid : trackedPlayerIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || player.isSpectator() || downedPlayers.containsKey(uuid)) {
                    continue;
                }
                if (player.getHealth() <= 1.0F) {
                    if (hardcoreEnabled) {
                        handlePlayerHardcoreDeath(player);
                    } else {
                        handlePlayerDown(player);
                    }
                }
            }
        }

        tickDownedPlayers(world);

        if (hardcoreEnabled) {
            boolean anyFighting = trackedPlayerIds.stream().anyMatch(id -> {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(id);
                return player != null && player.isAlive() && !player.isSpectator();
            });
            if (!anyFighting && downedPlayers.isEmpty()) {
                bossEntity.discard();
                handleBattleLoss(world, "All players eliminated.");
                return;
            }
        }

        if (regeneration > 0 && bossEntity instanceof LivingEntity livingBoss) {
            regenerationTickTimer++;
            if (regenerationTickTimer >= REGEN_INTERVAL_TICKS) {
                livingBoss.heal((float) regeneration);
                regenerationTickTimer = 0;
            }
        }
    }

    public void startBattle(
            ServerLevel world,
            List<ServerPlayer> players,
            RaidDifficulty difficulty,
            BlockPos controllerPos,
            ResourceKey<Level> controllerDimension,
            boolean hardcoreEnabled,
            int battleTimeLimitTicks
    ) {
        if (players == null || players.isEmpty()) {
            return;
        }

        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(this.mobId);
        Component mobDisplayName = entityTypeOpt.map(EntityType::getDescription).orElse(Component.literal(this.mobId));
        Component announcement = Component.translatable(
                "message.arenas_ld.raid_start",
                players.get(0).getDisplayName(),
                mobDisplayName
        ).withStyle(net.minecraft.ChatFormatting.GOLD);
        world.getServer().getPlayerList().broadcastSystemMessage(announcement, false);

        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.error("Invalid mob ID in spawner at {}: {}", this.worldPosition, this.mobId);
            return;
        }

        Entity boss = entityTypeOpt.get().create(world);
        if (boss == null) {
            ArenasLdMod.LOGGER.error("Failed to create entity from ID: {}", this.mobId);
            return;
        }

        this.controllerPos = controllerPos;
        this.controllerDimension = controllerDimension;
        this.activeDifficulty = difficulty != null ? difficulty : RaidDifficulty.NORMAL;
        this.trackedPlayerCount = players.size();
        this.battleStartTime = world.getGameTime();
        this.hardcoreEnabled = hardcoreEnabled;
        this.battleTimeLimitTicks = Math.max(0, battleTimeLimitTicks);
        this.trackedPlayerIds.clear();
        this.trackedPlayerNames.clear();
        this.downedPlayers.clear();
        this.disconnectedPlayers.clear();
        this.playerGameModesBeforeRaid.clear();
        for (ServerPlayer p : players) {
            trackedPlayerIds.add(p.getUUID());
            trackedPlayerNames.add(p.getGameProfile().getName());
            playerGameModesBeforeRaid.put(p.getUUID(), p.gameMode.getGameModeForPlayer());
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
            RaidTierConfig tierCfg = tierConfigs.getOrDefault(activeDifficulty, RaidTierConfig.defaultFor(activeDifficulty));
            double healthMult = tierCfg.healthMultOverride() > 0 ? tierCfg.healthMultOverride() : activeDifficulty.healthMult;
            double damageMult = tierCfg.damageMultOverride() > 0 ? tierCfg.damageMultOverride() : activeDifficulty.damageMult;
            double perPlayerMult = Math.pow(1.0 + hpScalePerPlayer, Math.max(0, players.size() - 1));

            for (AttributeData attr : attributes) {
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

            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.HEAD, equipment.head, equipment.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.CHEST, equipment.chest, equipment.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.LEGS, equipment.legs, equipment.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.FEET, equipment.feet, equipment.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.MAINHAND, equipment.mainHand, equipment.dropChance);
            EntityEquipmentHelper.applyEquipment(livingBoss, EquipmentSlot.OFFHAND, equipment.offHand, equipment.dropChance);

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

        this.isBattleActive = true;
        this.activeBossUuid = boss.getUUID();
        this.bossDimension = world.dimension();
        this.respawnCooldown = 0;
        this.boundsTickCounter = 0;
        ArenasLdMod.RAID_BOSS_MANAGER.registerSpawner(this);
        markDirtyAndSync();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        ArenasLdMod.LOGGER.info(
                "Raid battle started at {} with boss {}, tier={}, players={}",
                this.worldPosition, this.mobId, this.activeDifficulty, this.trackedPlayerCount
        );
    }

    protected void handleBattleWin(ServerLevel world, Entity defeatedBoss) {
        ArenasLdMod.LOGGER.info("Raid battle won at spawner {}", worldPosition);

        if (this.skillExperiencePerWin > 0) {
            for (UUID uuid : trackedPlayerIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && FabricLoader.getInstance().isModLoaded("puffish_skills")) {
                    PuffishSkillsCompat.addExperience(player, this.skillExperiencePerWin);
                }
            }
        }

        RaidTierConfig tierCfg = tierConfigs.getOrDefault(activeDifficulty, RaidTierConfig.defaultFor(activeDifficulty));
        String resolvedLootTableId = tierCfg.lootTableId().isEmpty() ? lootTableId : tierCfg.lootTableId();
        String resolvedPerPlayerLootTableId = tierCfg.perPlayerLootTableId().isEmpty() ? perPlayerLootTableId : tierCfg.perPlayerLootTableId();

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
            for (UUID uuid : trackedPlayerIds) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player == null || !isTracked(uuid)) {
                    continue;
                }
                ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
                bundle.set(DataComponentRegistry.LOOT_BUNDLE_DATA, new LootBundleDataComponent(resolvedPerPlayerLootTableId));
                if (!player.getInventory().add(bundle)) {
                    player.drop(bundle, false);
                }
            }
        }

        notifyController(world, true);
        resetAfterBattle(world);
    }

    protected void handleBattleLoss(ServerLevel world, String reason) {
        ArenasLdMod.LOGGER.info("Raid battle lost at spawner {}: {}", worldPosition, reason);

        if (activeBossUuid != null && bossDimension != null) {
            ServerLevel bossWorld = world.getServer().getLevel(bossDimension);
            if (bossWorld != null) {
                Entity bossEntity = bossWorld.getEntity(activeBossUuid);
                if (bossEntity != null && bossEntity.isAlive()) {
                    bossEntity.discard();
                    ArenasLdMod.LOGGER.info("Despawned boss after battle loss at {}.", worldPosition);
                }
            }
        }

        notifyController(world, false);
        resetAfterBattle(world);
    }

    private void resetAfterBattle(ServerLevel world) {
        ServerLevel exitWorld = world.getServer().getLevel(exitDimension);
        BlockPos absoluteExit = exitPosition;
        for (UUID uuid : trackedPlayerIds) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                GameType restoreMode = playerGameModesBeforeRaid.getOrDefault(uuid, GameType.SURVIVAL);
                ArenasLdMod.RAID_BOSS_MANAGER.addPendingRestore(uuid, exitPosition, exitDimension, restoreMode);
                continue;
            }
            GameType restoreMode = playerGameModesBeforeRaid.getOrDefault(uuid, GameType.SURVIVAL);
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

        this.isBattleActive = false;
        this.activeBossUuid = null;
        this.bossDimension = null;
        this.regenerationTickTimer = 0;
        this.boundsTickCounter = 0;
        // 1-tick safety buffer so controller state can transition instance status first.
        this.respawnCooldown = 1;
        this.controllerPos = null;
        this.controllerDimension = null;
        this.activeDifficulty = RaidDifficulty.NORMAL;
        this.trackedPlayerCount = 0;
        this.battleStartTime = -1;
        this.hardcoreEnabled = false;
        this.disconnectedPlayers.clear();
        this.trackedPlayerIds.clear();
        this.trackedPlayerNames.clear();
        this.downedPlayers.clear();
        this.playerGameModesBeforeRaid.clear();
        this.raidTimerBossBar.removeAllPlayers();
        this.raidTimerBossBar.setVisible(false);
        ArenasLdMod.RAID_BOSS_MANAGER.unregisterSpawner(this);
        markDirtyAndSync();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean isHardcoreEnabled() {
        return hardcoreEnabled;
    }

    public void setHardcoreEnabled(boolean hardcoreEnabled) {
        this.hardcoreEnabled = hardcoreEnabled;
        markDirtyAndSync();
    }

    public double getEffectiveDamageMultiplier() {
        RaidTierConfig tierCfg = tierConfigs.getOrDefault(activeDifficulty, RaidTierConfig.defaultFor(activeDifficulty));
        if (tierCfg.damageMultOverride() > 0) {
            return tierCfg.damageMultOverride();
        }
        return activeDifficulty.damageMult;
    }

    public boolean isTracked(UUID playerId) {
        return playerId != null && trackedPlayerIds.contains(playerId);
    }

    public boolean isRaidRunning() {
        return isBattleActive;
    }

    public void handlePlayerDown(ServerPlayer player) {
        if (!isBattleActive || player == null) return;
        if (!isTracked(player.getUUID())) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return;
        if (downedPlayers.containsKey(player.getUUID())) return;

        player.setHealth(1.0F);
        player.setGameMode(GameType.SPECTATOR);
        battleStartTime -= DEATH_TIME_PENALTY_TICKS;
        downedPlayers.put(player.getUUID(), new DownedPlayer(DOWNED_RESPAWN_TICKS));
        markDirtyAndSync();
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        if (player == null || !isBattleActive || !isTracked(player.getUUID())) {
            return;
        }

        if (hardcoreEnabled) {
            disconnectedPlayers.remove(player.getUUID());
            handlePlayerHardcoreDeath(player);
            return;
        }

        disconnectedPlayers.add(player.getUUID());
        if (!downedPlayers.containsKey(player.getUUID())) {
            if (battleTimeLimitTicks > 0) {
                battleStartTime -= DEATH_TIME_PENALTY_TICKS;
            }
            downedPlayers.put(player.getUUID(), new DownedPlayer(DOWNED_RESPAWN_TICKS));
        }
        markDirtyAndSync();
    }

    public void handlePlayerReconnect(ServerPlayer player) {
        if (player == null || !isTracked(player.getUUID())) {
            return;
        }

        disconnectedPlayers.remove(player.getUUID());

        if (!isBattleActive) {
            ArenasLdMod.RAID_BOSS_MANAGER.handlePostBattleReconnect(player);
            return;
        }

        DownedPlayer downed = downedPlayers.computeIfAbsent(player.getUUID(), id -> new DownedPlayer(DOWNED_RESPAWN_TICKS));
        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(player.getMaxHealth());
        if (downed.ticksRemaining <= 0) {
            respawnAtEntrance(player.serverLevel(), player);
            downedPlayers.remove(player.getUUID());
        }
    }

    public void handlePlayerHardcoreDeath(ServerPlayer player) {
        if (!isBattleActive || player == null) return;
        if (!isTracked(player.getUUID())) return;

        player.setGameMode(GameType.SPECTATOR);
        downedPlayers.remove(player.getUUID());
        trackedPlayerIds.remove(player.getUUID());
        trackedPlayerNames.remove(player.getGameProfile().getName());
        markDirtyAndSync();
    }

    private void tickDownedPlayers(ServerLevel world) {
        if (downedPlayers.isEmpty()) return;

        Iterator<Map.Entry<UUID, DownedPlayer>> iterator = downedPlayers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, DownedPlayer> entry = iterator.next();
            UUID uuid = entry.getKey();
            DownedPlayer downed = entry.getValue();
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null) {
                downed.ticksRemaining = Math.max(0, downed.ticksRemaining - 1);
                continue;
            }
            player.setDeltaMovement(0, 0, 0);
            downed.ticksRemaining--;
            if (downed.ticksRemaining <= 0) {
                respawnAtEntrance(world, player);
                iterator.remove();
            }
        }
    }

    private void respawnAtEntrance(ServerLevel world, ServerPlayer player) {
        if (!isBattleActive || player == null) {
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

    private void updateRaidTimerBossBar(ServerLevel world, long elapsedTicks) {
        if (battleTimeLimitTicks <= 0) {
            raidTimerBossBar.setVisible(false);
            return;
        }
        if (!raidTimerBossBar.isVisible()) {
            raidTimerBossBar.setVisible(true);
        }
        long remainingTicks = Math.max(0L, (long) battleTimeLimitTicks - elapsedTicks);
        float progress = 1.0f - ((float) elapsedTicks / (float) battleTimeLimitTicks);
        raidTimerBossBar.setProgress(Mth.clamp(progress, 0.0f, 1.0f));
        raidTimerBossBar.setName(Component.translatable("gui.arenas_ld.raid_timer_remaining", formatTime((int) (remainingTicks / 20L))));

        for (UUID uuid : trackedPlayerIds) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && !raidTimerBossBar.getPlayers().contains(player)) {
                raidTimerBossBar.addPlayer(player);
            }
        }
    }

    private void enforceBattleBounds(ServerLevel world) {
        AABB battleBox = getCachedBattleBounds();
        for (UUID uuid : trackedPlayerIds) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player == null || player.isSpectator() || downedPlayers.containsKey(uuid)) {
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

    private void notifyController(ServerLevel world, boolean wasWin) {
        if (controllerPos == null || controllerDimension == null) return;
        ServerLevel controllerWorld = world.getServer().getLevel(controllerDimension);
        if (controllerWorld == null) return;
        BlockEntity be = controllerWorld.getBlockEntity(controllerPos);
        if (!(be instanceof RaidRunCallback callback)) {
            return;
        }

        int elapsed = battleStartTime > 0 ? Math.max(0, (int) ((world.getGameTime() - battleStartTime) / 20L)) : 0;
        callback.onRaidEnded(this.worldPosition, wasWin, activeDifficulty, new ArrayList<>(trackedPlayerNames), elapsed);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putString("MobId", mobId);
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

        nbt.putBoolean("IsBattleActive", isBattleActive);
        nbt.putInt("RespawnCooldown", respawnCooldown);
        if (activeBossUuid != null) nbt.putUUID("ActiveBossUuid", activeBossUuid);
        if (bossDimension != null) nbt.putString("BossDimension", bossDimension.location().toString());
        if (controllerPos != null) nbt.putLong("ControllerPos", controllerPos.asLong());
        if (controllerDimension != null) nbt.putString("ControllerDimension", controllerDimension.location().toString());
        nbt.putString("ActiveDifficulty", activeDifficulty.name());
        nbt.putInt("TrackedPlayerCount", trackedPlayerCount);
        nbt.putLong("BattleStartTime", battleStartTime);
        nbt.putBoolean("HardcoreEnabled", hardcoreEnabled);

        ListTag trackedIdList = new ListTag();
        for (UUID id : trackedPlayerIds) {
            CompoundTag t = new CompoundTag();
            t.putUUID("uuid", id);
            trackedIdList.add(t);
        }
        nbt.put("TrackedPlayerIds", trackedIdList);

        ListTag trackedNameList = new ListTag();
        for (String name : trackedPlayerNames) {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            trackedNameList.add(t);
        }
        nbt.put("TrackedPlayerNames", trackedNameList);

        CompoundTag tierConfigsTag = new CompoundTag();
        for (RaidDifficulty tier : RaidDifficulty.values()) {
            tierConfigsTag.put(tier.name(), tierConfigs.getOrDefault(tier, RaidTierConfig.defaultFor(tier)).toNbt());
        }
        nbt.put("RaidTierConfigs", tierConfigsTag);

        ListTag attributeList = new ListTag();
        for (AttributeData attr : attributes) {
            attributeList.add(attr.toNbt());
        }
        nbt.put("Attributes", attributeList);
        nbt.put("Equipment", equipment.toNbt());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        mobId = nbt.getString("MobId");
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

        isBattleActive = nbt.getBoolean("IsBattleActive");
        respawnCooldown = nbt.getInt("RespawnCooldown");
        if (nbt.hasUUID("ActiveBossUuid")) activeBossUuid = nbt.getUUID("ActiveBossUuid");
        if (nbt.contains("BossDimension")) {
            bossDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("BossDimension")));
        }
        controllerPos = nbt.contains("ControllerPos") ? BlockPos.of(nbt.getLong("ControllerPos")) : null;
        if (nbt.contains("ControllerDimension")) {
            controllerDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("ControllerDimension")));
        }
        activeDifficulty = RaidDifficulty.fromNameOrDefault(nbt.getString("ActiveDifficulty"), RaidDifficulty.NORMAL);
        trackedPlayerCount = nbt.getInt("TrackedPlayerCount");
        battleStartTime = nbt.getLong("BattleStartTime");
        hardcoreEnabled = nbt.getBoolean("HardcoreEnabled");

        trackedPlayerIds.clear();
        if (nbt.contains("TrackedPlayerIds", Tag.TAG_LIST)) {
            ListTag trackedIdList = nbt.getList("TrackedPlayerIds", Tag.TAG_COMPOUND);
            for (Tag t : trackedIdList) {
                trackedPlayerIds.add(((CompoundTag) t).getUUID("uuid"));
            }
        }
        trackedPlayerNames.clear();
        if (nbt.contains("TrackedPlayerNames", Tag.TAG_LIST)) {
            ListTag trackedNameList = nbt.getList("TrackedPlayerNames", Tag.TAG_COMPOUND);
            for (Tag t : trackedNameList) {
                trackedPlayerNames.add(((CompoundTag) t).getString("name"));
            }
        }

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

        attributes.clear();
        ListTag attributeList = nbt.getList("Attributes", Tag.TAG_COMPOUND);
        for (Tag tag : attributeList) {
            attributes.add(AttributeData.fromNbt((CompoundTag) tag));
        }
        if (attributes.isEmpty()) {
            attributes.add(new AttributeData("minecraft:generic.max_health", 300.0));
            attributes.add(new AttributeData("minecraft:generic.attack_damage", 15.0));
        }
        if (nbt.contains("Equipment")) {
            equipment = EquipmentData.fromNbt(nbt.getCompound("Equipment"));
        }
        if (isBattleActive) {
            ArenasLdMod.RAID_BOSS_MANAGER.registerSpawner(this);
        }
        disconnectedPlayers.clear();
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
        return new BossSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BossSpawnerData getScreenOpeningData(ServerPlayer player) {
        return new BossSpawnerData(this.worldPosition);
    }

    private static class DownedPlayer {
        private int ticksRemaining;

        private DownedPlayer(int ticksRemaining) {
            this.ticksRemaining = ticksRemaining;
        }
    }

    private static String formatTime(int totalSeconds) {
        int clamped = Math.max(0, totalSeconds);
        int minutes = clamped / 60;
        int seconds = clamped % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
