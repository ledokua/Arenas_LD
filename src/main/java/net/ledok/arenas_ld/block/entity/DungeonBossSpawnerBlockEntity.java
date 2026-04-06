package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.busylib.BusyState;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.compat.PuffishSkillsCompat;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.screen.BossSpawnerData;
import net.ledok.arenas_ld.screen.DungeonBossSpawnerScreenHandler;
import net.ledok.arenas_ld.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class DungeonBossSpawnerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BossSpawnerData>, AttributeProvider, EquipmentProvider, LinkableSpawner {

    // --- Configuration Fields ---
    public String mobId = "minecraft:zombie";
    public int respawnTime = 6000;
    public int dungeonCloseTimer = 600; // Renamed from portalActiveTime
    public int dungeonTime = 600; // Seconds
    public String lootTableId = "minecraft:chests/simple_dungeon";
    public String perPlayerLootTableId = "";
    public BlockPos exitPositionCoords = BlockPos.ZERO; // Renamed from exitPortalCoords
    public ResourceKey<Level> exitPositionDimension = Level.OVERWORLD;
    public BlockPos entrancePosition = BlockPos.ZERO;
    public ResourceKey<Level> entranceDimension = Level.OVERWORLD;
    public int triggerRadius = 16;
    public int battleRadius = 64;
    public int regeneration = 0;
    public int skillExperiencePerWin = 100;
    public String groupId = "";
    public List<DungeonLeaderboardEntry> leaderboard = new ArrayList<>();
    private static final String BUSY_REASON = "arenas_ld";

    private final List<AttributeData> attributes = new ArrayList<>();
    private EquipmentData equipment = new EquipmentData();
    private final List<BlockPos> linkedSpawners = new ArrayList<>();

    // --- State Machine Fields ---
    private boolean isBattleActive = false;
    private boolean isDungeonActive = false;
    private int respawnCooldown = 0;
    private UUID activeBossUuid = null;
    private ResourceKey<Level> bossDimension = null;
    private int regenerationTickTimer = 0;
    private int internalDungeonCloseTimer = -1; // Renamed from exitPortalTimer
    private final Set<UUID> trackedPlayers = new HashSet<>(); // Track players who entered
    private boolean firstTick = true;
    private long lastTickTime = -1;
    private boolean isChunkLoaded = false;
    private BlockPos controllerPos;
    private ResourceKey<Level> controllerDimension;
    private long dungeonStartTick = -1;
    private int dungeonTimeTicksRemaining = 0;
    private int lastPublishedDungeonSeconds = -1;
    private final Map<UUID, DownedPlayer> downedPlayers = new HashMap<>();
    private boolean hardcoreEnabled = false;
    
    private final ServerBossEvent dungeonCloseBossBar = (ServerBossEvent) new ServerBossEvent(
            Component.translatable("bossbar.arenas_ld.dungeon_closing"), 
            BossEvent.BossBarColor.RED, 
            BossEvent.BossBarOverlay.PROGRESS
    ).setDarkenScreen(false).setPlayBossMusic(false).setCreateWorldFog(false);

    private final ServerBossEvent dungeonTimeBossBar = (ServerBossEvent) new ServerBossEvent(
            Component.translatable("bossbar.arenas_ld.dungeon_time"),
            BossEvent.BossBarColor.BLUE,
            BossEvent.BossBarOverlay.PROGRESS
    ).setDarkenScreen(false).setPlayBossMusic(false).setCreateWorldFog(false);

    public DungeonBossSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_BOSS_SPAWNER_BLOCK_ENTITY, pos, state);
        if (attributes.isEmpty()) {
            attributes.add(new AttributeData("minecraft:generic.max_health", 300.0));
            attributes.add(new AttributeData("minecraft:generic.attack_damage", 15.0));
        }
    }

    @Override
    public List<AttributeData> getAttributes() {
        return attributes;
    }

    @Override
    public void setAttributes(List<AttributeData> attributes) {
        this.attributes.clear();
        this.attributes.addAll(attributes);
        setChanged();
    }

    @Override
    public EquipmentData getEquipment() {
        return equipment;
    }

    @Override
    public void setEquipment(EquipmentData equipment) {
        this.equipment = equipment;
        setChanged();
    }
    
    public void trackPlayer(UUID playerUuid) {
        addTrackedPlayer(playerUuid);
    }
    
    public boolean isTracked(UUID playerUuid) {
        return this.trackedPlayers.contains(playerUuid);
    }

    private void addTrackedPlayer(UUID playerUuid) {
        if (this.trackedPlayers.add(playerUuid)) {
            BusyState.setBusy(playerUuid, BUSY_REASON);
            this.setChanged();
        }
    }

    private void removeTrackedPlayer(UUID playerUuid) {
        if (this.trackedPlayers.remove(playerUuid)) {
            BusyState.clearBusy(playerUuid, BUSY_REASON);
            this.setChanged();
        }
    }

    private void clearTrackedPlayers() {
        for (UUID playerId : this.trackedPlayers) {
            BusyState.clearBusy(playerId, BUSY_REASON);
        }
        clearTrackedPlayers();
        this.setChanged();
    }

    public boolean isDungeonRunning() {
        return isDungeonActive || isBattleActive || internalDungeonCloseTimer > 0;
    }

    public int getRespawnCooldownSeconds() {
        return (respawnCooldown + 19) / 20;
    }
    
    public void handlePlayerDisconnect(ServerPlayer player, Component reason) {
        handlePlayerExit(player, reason, true);
    }

    private void handlePlayerExit(ServerPlayer player, Component reason, boolean removeFromDowned) {
        if (exitPositionCoords != null && !exitPositionCoords.equals(BlockPos.ZERO)) {
            ServerLevel exitWorld = Objects.requireNonNull(player.getServer()).getLevel(exitPositionDimension);
            if (exitWorld != null) {
                BlockPos absoluteExitPos = this.worldPosition.offset(exitPositionCoords);
                teleportPlayerToExit(player, exitWorld, absoluteExitPos);
            }
        }
        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_left", reason).withStyle(net.minecraft.ChatFormatting.RED));
        removeTrackedPlayer(player.getUUID());
        if (removeFromDowned) {
            this.downedPlayers.remove(player.getUUID());
        }
        this.dungeonCloseBossBar.removePlayer(player);
        this.dungeonTimeBossBar.removePlayer(player);
        this.setChanged();

        if (trackedPlayers.isEmpty() && this.level instanceof ServerLevel serverLevel && isDungeonRunning()) {
            handleBattleLoss(serverLevel, "All players left the dungeon.");
        }
    }

    @Override
    public void addLinkedSpawner(BlockPos pos) {
        if (!linkedSpawners.contains(pos)) {
            linkedSpawners.add(pos);
            setChanged();
        }
    }

    @Override
    public void clearLinkedSpawners() {
        linkedSpawners.clear();
        setChanged();
    }

    @Override
    public List<BlockPos> getLinkedSpawners() {
        return linkedSpawners;
    }

    @Override
    public void forceReset() {
        if (this.level instanceof ServerLevel serverLevel) {
            resetSpawner(serverLevel, false);
        }
    }

    public static void tick(Level world, BlockPos pos, BlockState state, DungeonBossSpawnerBlockEntity be) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) return;
        
        long currentTime = world.getGameTime();
        if (be.lastTickTime != -1) {
            long timeDiff = currentTime - be.lastTickTime;
            if (timeDiff > 1) {
                // Chunk was unloaded or server lagged, catch up cooldown
                if (be.respawnCooldown > 0) {
                    be.respawnCooldown = Math.max(0, be.respawnCooldown - (int) timeDiff);
                    if (be.respawnCooldown == 0) {
                        // Trigger linked spawners
                        for (BlockPos relativePos : be.linkedSpawners) {
                            BlockPos absolutePos = pos.offset(relativePos);
                            BlockEntity linkedBe = world.getBlockEntity(absolutePos);
                            if (linkedBe instanceof LinkableSpawner linkedSpawner) {
                                linkedSpawner.forceReset();
                            }
                        }
                    }
                }
                if (be.isDungeonActive && be.dungeonTimeTicksRemaining > 0) {
                    be.dungeonTimeTicksRemaining = Math.max(0, be.dungeonTimeTicksRemaining - (int) timeDiff);
                    be.lastPublishedDungeonSeconds = -1;
                }
            }
        }
        be.lastTickTime = currentTime;
        
        be.updateChunkLoading(serverLevel);
        
        if (be.firstTick) {
            // Re-link spawners on first tick
            for (BlockPos relativePos : be.linkedSpawners) {
                BlockPos absolutePos = pos.offset(relativePos);
                if (world.isLoaded(absolutePos)) {
                    BlockEntity linkedBe = world.getBlockEntity(absolutePos);
                    // Just ensuring the chunk is loaded and we can access it if needed
                }
            }
            
            be.firstTick = false;
        }

        if ((be.isDungeonActive || be.isBattleActive) && be.trackedPlayers.isEmpty()) {
            be.handleBattleLoss(serverLevel, "No tracked players.");
            return;
        }

        if (be.isDungeonActive) {
            be.updateDungeonTimer(serverLevel);
        }

        if (be.isBattleActive) {
            be.handleActiveBattle(serverLevel);
        } else if (be.internalDungeonCloseTimer > 0) {
            // Battle won, waiting for exit timer
            be.internalDungeonCloseTimer--;
            
            // Update Boss Bar
            be.dungeonCloseBossBar.setProgress((float) be.internalDungeonCloseTimer / (float) be.dungeonCloseTimer);
            be.updateBossBarPlayers(serverLevel);
            
            if (be.internalDungeonCloseTimer == 0) {
                be.teleportTrackedPlayersToExit(serverLevel);
                be.resetSpawner(serverLevel, true);
            }
        } else if (be.isDungeonActive) {
            be.handleDungeonActive(serverLevel, pos);
        } else {
            be.handleIdleState(serverLevel, pos);
        }

        if (be.respawnCooldown > 0 && !be.isDungeonActive && !be.isBattleActive && be.internalDungeonCloseTimer <= 0) {
            int cooldownSeconds = (be.respawnCooldown + 19) / 20;
            be.updateControllerCooldown(serverLevel, cooldownSeconds);
            be.updateControllerRemainingTime(serverLevel, 0);
        } else if (be.respawnCooldown == 0) {
            be.updateControllerCooldown(serverLevel, 0);
        }
        
        // Clean up tracked players
        if (!be.trackedPlayers.isEmpty()) {
            List<UUID> trackedSnapshot = new ArrayList<>(be.trackedPlayers);
            for (UUID uuid : trackedSnapshot) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(uuid);
                if (player == null) {
                    be.removeTrackedPlayer(uuid);
                    continue;
                }
                BusyState.setBusy(uuid, BUSY_REASON);
                if (player.level() != world) {
                    be.handlePlayerDisconnect(player, Component.translatable("message.arenas_ld.dungeon_left.reason.dimension"));
                    continue;
                }
                if (player.isDeadOrDying()) {
                    be.dungeonCloseBossBar.removePlayer(player);
                    be.dungeonTimeBossBar.removePlayer(player);
                    be.removeTrackedPlayer(uuid);
                }
            }
        }

        be.tickDownedPlayers(serverLevel);
    }
    
    private void updateChunkLoading(ServerLevel world) {
        boolean shouldBeLoaded = this.respawnCooldown > 0 || this.isBattleActive || this.isDungeonActive || this.internalDungeonCloseTimer > 0;
        if (shouldBeLoaded != isChunkLoaded) {
            ChunkPos chunkPos = new ChunkPos(this.worldPosition);
            world.setChunkForced(chunkPos.x, chunkPos.z, shouldBeLoaded);
            if (controllerPos != null && controllerDimension != null) {
                ServerLevel controllerWorld = world.getServer().getLevel(controllerDimension);
                if (controllerWorld != null) {
                    ChunkPos controllerChunkPos = new ChunkPos(controllerPos);
                    controllerWorld.setChunkForced(controllerChunkPos.x, controllerChunkPos.z, shouldBeLoaded);
                }
            }
            isChunkLoaded = shouldBeLoaded;
        }
    }
    
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && isChunkLoaded) {
            ChunkPos chunkPos = new ChunkPos(this.worldPosition);
            serverLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
            if (controllerPos != null && controllerDimension != null) {
                ServerLevel controllerWorld = serverLevel.getServer().getLevel(controllerDimension);
                if (controllerWorld != null) {
                    ChunkPos controllerChunkPos = new ChunkPos(controllerPos);
                    controllerWorld.setChunkForced(controllerChunkPos.x, controllerChunkPos.z, false);
                }
            }
            isChunkLoaded = false;
        }
        super.setRemoved();
    }
    
    private void updateBossBarPlayers(ServerLevel world) {
        for (UUID uuid : trackedPlayers) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                if (dungeonCloseBossBar.isVisible()) {
                    if (!dungeonCloseBossBar.getPlayers().contains(player)) {
                        dungeonCloseBossBar.addPlayer(player);
                    }
                } else if (dungeonCloseBossBar.getPlayers().contains(player)) {
                    dungeonCloseBossBar.removePlayer(player);
                }
                if (!dungeonTimeBossBar.getPlayers().contains(player)) {
                    dungeonTimeBossBar.addPlayer(player);
                }
            }
        }
    }

    private void updateDungeonTimer(ServerLevel world) {
        if (dungeonTimeTicksRemaining <= 0) {
            handleBattleLoss(world, "Dungeon time expired.");
            return;
        }
        if (!dungeonTimeBossBar.isVisible()) {
            dungeonTimeBossBar.setVisible(true);
        }
        dungeonTimeTicksRemaining--;
        int remainingSeconds = (dungeonTimeTicksRemaining + 19) / 20;
        if (remainingSeconds != lastPublishedDungeonSeconds) {
            lastPublishedDungeonSeconds = remainingSeconds;
            updateControllerRemainingTime(world, remainingSeconds);
            dungeonTimeBossBar.setName(Component.translatable("bossbar.arenas_ld.dungeon_time", remainingSeconds));
        }
        if (dungeonTime > 0) {
            dungeonTimeBossBar.setProgress((float) dungeonTimeTicksRemaining / (dungeonTime * 20));
        }
        updateBossBarPlayers(world);
    }

    public void handlePlayerDown(ServerPlayer player) {
        if (!isDungeonRunning()) return;
        if (!isTracked(player.getUUID())) return;
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) return;
        if (downedPlayers.containsKey(player.getUUID())) return;

        player.setHealth(1.0F);
        player.setGameMode(GameType.SPECTATOR);
        player.setHealth(player.getMaxHealth() * 0.5f);

        int penaltyTicks = 10 * 20;
        dungeonTimeTicksRemaining = Math.max(0, dungeonTimeTicksRemaining - penaltyTicks);
        lastPublishedDungeonSeconds = -1;
        updateDungeonTimer(player.serverLevel());

        downedPlayers.put(player.getUUID(), new DownedPlayer(40, player.position(), player.level().dimension()));
        setChanged();
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
                iterator.remove();
                continue;
            }

            ServerLevel downedWorld = world.getServer().getLevel(downed.dimension);
            if (downedWorld == null || player.level() != downedWorld) {
                handlePlayerExit(player, Component.translatable("message.arenas_ld.dungeon_left.reason.dimension"), false);
                iterator.remove();
                continue;
            }

            if (player.level() == downedWorld) {
                player.setDeltaMovement(0, 0, 0);
            }

            downed.ticksRemaining--;
            if (downed.ticksRemaining <= 0) {
                respawnDownedPlayer(world, player);
                iterator.remove();
            }
        }
    }

    private void respawnDownedPlayer(ServerLevel world, ServerPlayer player) {
        player.setGameMode(GameType.SURVIVAL);
        player.setHealth(player.getMaxHealth() * 0.5f);

        if (entrancePosition != null && !entrancePosition.equals(BlockPos.ZERO)) {
            ServerLevel destLevel = world.getServer().getLevel(entranceDimension);
            if (destLevel != null) {
                BlockPos absoluteEntrance = this.worldPosition.offset(entrancePosition);
                player.teleportTo(destLevel, absoluteEntrance.getX() + 0.5, absoluteEntrance.getY(), absoluteEntrance.getZ() + 0.5, player.getYRot(), player.getXRot());
            }
        }
    }

    private void handleIdleState(ServerLevel world, BlockPos pos) {
        if (respawnCooldown > 0) {
            respawnCooldown--;
            if (respawnCooldown == 0) {
                // Trigger linked spawners
                for (BlockPos relativePos : linkedSpawners) {
                    BlockPos absolutePos = pos.offset(relativePos);
                    BlockEntity be = world.getBlockEntity(absolutePos);
                    if (be instanceof LinkableSpawner linkedSpawner) {
                        linkedSpawner.forceReset();
                    }
                }
            }
            return;
        }
    }

    public boolean startDungeon(Set<UUID> players, BlockPos controllerPos, ResourceKey<Level> controllerDimension) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (isDungeonActive || isBattleActive || internalDungeonCloseTimer > 0 || respawnCooldown > 0) return false;

        this.controllerPos = controllerPos;
        this.controllerDimension = controllerDimension;
        clearTrackedPlayers();
        this.downedPlayers.clear();
        this.dungeonCloseBossBar.removeAllPlayers();
        this.dungeonCloseBossBar.setVisible(false);
        ArenasLdMod.DUNGEON_BOSS_MANAGER.registerSpawner(this);

        List<ServerPlayer> validPlayers = new ArrayList<>();
        for (UUID playerId : players) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                validPlayers.add(player);
                trackPlayer(playerId);
                if (entrancePosition != null && !entrancePosition.equals(BlockPos.ZERO)) {
                    ServerLevel destLevel = serverLevel.getServer().getLevel(entranceDimension);
                    if (destLevel != null) {
                        BlockPos absoluteEntrance = this.worldPosition.offset(entrancePosition);
                        player.teleportTo(destLevel, absoluteEntrance.getX() + 0.5, absoluteEntrance.getY(), absoluteEntrance.getZ() + 0.5, player.getYRot(), player.getXRot());
                    }
                }
            }
        }

        if (validPlayers.isEmpty()) return false;
        this.isDungeonActive = true;
        this.dungeonStartTick = serverLevel.getGameTime();
        this.dungeonTimeTicksRemaining = Math.max(0, dungeonTime) * 20;
        this.lastPublishedDungeonSeconds = -1;
        this.dungeonTimeBossBar.setVisible(true);
        int initialSeconds = (this.dungeonTimeTicksRemaining + 19) / 20;
        this.dungeonTimeBossBar.setName(Component.translatable("bossbar.arenas_ld.dungeon_time", initialSeconds));
        if (dungeonTime > 0) {
            this.dungeonTimeBossBar.setProgress((float) this.dungeonTimeTicksRemaining / (dungeonTime * 20));
        }
        updateBossBarPlayers(serverLevel);
        updateControllerRemainingTime(serverLevel, (this.dungeonTimeTicksRemaining + 19) / 20);

        for (BlockPos relativePos : linkedSpawners) {
            BlockPos absolutePos = this.worldPosition.offset(relativePos);
            BlockEntity linkedBe = serverLevel.getBlockEntity(absolutePos);
            if (linkedBe instanceof LinkableSpawner linkedSpawner) {
                linkedSpawner.forceReset();
            }
        }

        setChanged();
        updateControllerCooldown(serverLevel, 0);
        return true;
    }

    public void setHardcoreEnabled(boolean hardcoreEnabled) {
        this.hardcoreEnabled = hardcoreEnabled;
        setChanged();
    }

    public boolean isHardcoreEnabled() {
        return hardcoreEnabled;
    }

    public void handlePlayerHardcoreDeath(ServerPlayer player) {
        if (!isDungeonRunning() || !isTracked(player.getUUID())) return;
        revivePlayerForExit(player);
        handlePlayerExit(player, Component.translatable("message.arenas_ld.dungeon_left.reason.died"), true);
    }

    private void handleDungeonActive(ServerLevel world, BlockPos pos) {
        if (!areLinkedSpawnersCleared(world, pos)) {
            return;
        }

        AABB triggerBox = new AABB(pos).inflate(triggerRadius);
        List<ServerPlayer> playersInTriggerZone = world.getEntitiesOfClass(ServerPlayer.class, triggerBox, p -> !p.isSpectator());
        if (!playersInTriggerZone.isEmpty()) {
            startBattle(world, pos, playersInTriggerZone.get(0));
        }
    }

    private boolean areLinkedSpawnersCleared(ServerLevel world, BlockPos pos) {
        if (linkedSpawners.isEmpty()) {
            return true;
        }
        for (BlockPos relativePos : linkedSpawners) {
            BlockPos absolutePos = pos.offset(relativePos);
            if (!world.isLoaded(absolutePos)) {
                return false;
            }
            BlockEntity be = world.getBlockEntity(absolutePos);
            if (be instanceof MobSpawnerBlockEntity mobSpawner) {
                if (mobSpawner.isBattleActive() || mobSpawner.getRespawnCooldown() <= 0) {
                    return false;
                }
            } else if (be instanceof BossSpawnerBlockEntity bossSpawner) {
                if (bossSpawner.isBattleActive || bossSpawner.respawnCooldown <= 0) {
                    return false;
                }
            } else if (be instanceof DungeonBossSpawnerBlockEntity dungeonSpawner) {
                if (dungeonSpawner.isBattleActive || dungeonSpawner.respawnCooldown <= 0) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    private void updateControllerRemainingTime(ServerLevel world, int remainingSeconds) {
        if (controllerPos != null && controllerDimension != null) {
            ServerLevel controllerWorld = world.getServer().getLevel(controllerDimension);
            if (controllerWorld != null && controllerWorld.getBlockEntity(controllerPos) instanceof DungeonControllerBlockEntity controller) {
                if (controller.remainingDungeonTimeSeconds != remainingSeconds) {
                    controller.remainingDungeonTimeSeconds = remainingSeconds;
                    controller.setChanged();
                    controllerWorld.sendBlockUpdated(controllerPos, controller.getBlockState(), controller.getBlockState(), 3);
                }
            }
        }
    }

    private void updateControllerCooldown(ServerLevel world, int cooldownSeconds) {
        if (controllerPos != null && controllerDimension != null) {
            ServerLevel controllerWorld = world.getServer().getLevel(controllerDimension);
            if (controllerWorld != null && controllerWorld.getBlockEntity(controllerPos) instanceof DungeonControllerBlockEntity controller) {
                if (controller.dungeonCooldownSeconds != cooldownSeconds) {
                    controller.dungeonCooldownSeconds = cooldownSeconds;
                    controller.setChanged();
                    controllerWorld.sendBlockUpdated(controllerPos, controller.getBlockState(), controller.getBlockState(), 3);
                }
            }
        }
    }

    private void syncLeaderboardToController(ServerLevel world) {
        if (controllerPos != null && controllerDimension != null) {
            ServerLevel controllerWorld = world.getServer().getLevel(controllerDimension);
            if (controllerWorld != null && controllerWorld.getBlockEntity(controllerPos) instanceof DungeonControllerBlockEntity controller) {
                controller.leaderboard = this.leaderboard;
                controller.setChanged();
                controllerWorld.sendBlockUpdated(controllerPos, controller.getBlockState(), controller.getBlockState(), 3);
            }
        }
    }

    private void updateLeaderboardForPlayers(ServerLevel world, Collection<UUID> players, int timeSeconds) {
        for (UUID playerId : players) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            String playerName = player.getGameProfile().getName();
            Optional<DungeonLeaderboardEntry> existingEntry = leaderboard.stream()
                    .filter(entry -> entry.playerName.equals(playerName))
                    .findFirst();

            if (existingEntry.isPresent()) {
                if (timeSeconds < existingEntry.get().timeSeconds) {
                    leaderboard.remove(existingEntry.get());
                    leaderboard.add(new DungeonLeaderboardEntry(playerName, timeSeconds));
                }
            } else {
                leaderboard.add(new DungeonLeaderboardEntry(playerName, timeSeconds));
            }
        }

        leaderboard = leaderboard.stream()
                .sorted(Comparator.comparingInt(e -> e.timeSeconds))
                .limit(20)
                .collect(Collectors.toList());

        setChanged();
        syncLeaderboardToController(world);
    }

    private void resetController(ServerLevel world) {
        if (controllerPos != null && controllerDimension != null) {
            ServerLevel controllerWorld = world.getServer().getLevel(controllerDimension);
            if (controllerWorld != null && controllerWorld.getBlockEntity(controllerPos) instanceof DungeonControllerBlockEntity controller) {
                controller.reset();
            }
        }
    }

    private void handleActiveBattle(ServerLevel world) {
        if (activeBossUuid == null) {
            handleBattleLoss(world, "Boss UUID was null.");
            return;
        }

        ServerLevel bossWorld = Objects.requireNonNull(world.getServer()).getLevel(bossDimension);
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
        AABB battleBox = new AABB(worldPosition).inflate(battleRadius);
        List<ServerPlayer> playersInBattle = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
        if (playersInBattle.isEmpty()) {
            if (downedPlayers.isEmpty()) {
                bossEntity.discard();
                handleBattleLoss(world, "All players left the battle area.");
                return;
            }
        }
        if (regeneration > 0 && bossEntity instanceof LivingEntity livingBoss) {
            regenerationTickTimer++;
            if (regenerationTickTimer >= 100) {
                livingBoss.heal((float) regeneration);
                regenerationTickTimer = 0;
            }
        }
    }

    private void startBattle(ServerLevel world, BlockPos spawnPos, ServerPlayer triggeringPlayer) {
        
        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(this.mobId);

        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.error("Invalid mob ID in spawner at {}: {}", this.worldPosition, this.mobId);
            this.respawnCooldown = this.respawnTime;
            return;
        }
        Entity boss = entityTypeOpt.get().create(world);
        if (boss instanceof LivingEntity livingBoss) {
            for (var attr : attributes) {
                ResourceLocation attrLocation = ResourceLocation.tryParse(attr.id());
                if (attrLocation != null) {
                    var attributeRegistry = world.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
                    ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, attrLocation);
                    attributeRegistry.getHolder(key).ifPresent(holder -> {
                        AttributeInstance instance = livingBoss.getAttribute(holder);
                        if (instance != null) {
                            instance.setBaseValue(attr.value());
                        }
                    });
                }
            }
            
            // Apply Equipment
            applyEquipment(livingBoss, EquipmentSlot.HEAD, equipment.head);
            applyEquipment(livingBoss, EquipmentSlot.CHEST, equipment.chest);
            applyEquipment(livingBoss, EquipmentSlot.LEGS, equipment.legs);
            applyEquipment(livingBoss, EquipmentSlot.FEET, equipment.feet);
            applyEquipment(livingBoss, EquipmentSlot.MAINHAND, equipment.mainHand);
            applyEquipment(livingBoss, EquipmentSlot.OFFHAND, equipment.offHand);

            livingBoss.heal(livingBoss.getMaxHealth());
        }
        if (boss == null) {
            ArenasLdMod.LOGGER.error("Failed to create entity from ID: {}", this.mobId);
            return;
        }
        boss.moveTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1, spawnPos.getZ() + 0.5, 0, 0);
        world.addFreshEntity(boss);
        this.isBattleActive = true;
        this.activeBossUuid = boss.getUUID();
        this.bossDimension = world.dimension();
        this.setChanged();
        ArenasLdMod.LOGGER.info("Dungeon Battle started at spawner {} with boss {}", this.worldPosition, this.mobId);
    }

    private void applyEquipment(LivingEntity entity, EquipmentSlot slot, String itemId) {
        if (itemId != null && !itemId.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null) {
                    entity.setItemSlot(slot, new ItemStack(item));
                    if (entity instanceof Mob mob) {
                        if (equipment.dropChance) {
                            mob.setDropChance(slot, 1.0F);
                        } else {
                            mob.setDropChance(slot, 0.0F);
                        }
                    }
                }
            }
        }
    }

    private void handleBattleWin(ServerLevel world, Entity defeatedBoss) {
        ArenasLdMod.LOGGER.info("Dungeon Battle won at spawner {}", worldPosition);
        int durationSeconds = 0;
        if (dungeonStartTick >= 0) {
            durationSeconds = (int) Math.max(0, (world.getGameTime() - dungeonStartTick) / 20);
        }
        updateLeaderboardForPlayers(world, trackedPlayers, durationSeconds);
        String totalTime = formatTime(durationSeconds);
        for (UUID uuid : trackedPlayers) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null) {
                player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_cleared", totalTime).withStyle(net.minecraft.ChatFormatting.GREEN));
            }
        }

        if (this.skillExperiencePerWin > 0) {
            AABB battleBox = new AABB(worldPosition).inflate(battleRadius);
            List<ServerPlayer> playersInBattle = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
            for (ServerPlayer player : playersInBattle) {
                if (FabricLoader.getInstance().isModLoaded("puffish_skills")) {
                    PuffishSkillsCompat.addExperience(player, this.skillExperiencePerWin);
                }
            }
        }

        int rewardMultiplier = hardcoreEnabled ? 2 : 1;

        ResourceLocation lootTableIdentifier = ResourceLocation.tryParse(this.lootTableId);
        if (lootTableIdentifier != null) {
            LootTable lootTable = Objects.requireNonNull(world.getServer()).reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTableIdentifier));

            LootParams.Builder builder = new LootParams.Builder(world)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                    .withParameter(LootContextParams.THIS_ENTITY, defeatedBoss);

            LootParams lootParams = builder.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.GIFT);
            for (int i = 0; i < rewardMultiplier; i++) {
                lootTable.getRandomItems(lootParams).forEach(stack -> {
                    double x = worldPosition.getX() + 0.5 + (world.random.nextDouble() * 8.0) - 4.0;
                    double y = worldPosition.getY() + 3.5;
                    double z = worldPosition.getZ() + 0.5 + (world.random.nextDouble() * 8.0) - 4.0;

                    ItemEntity itemEntity = new ItemEntity(world, x, y, z, stack);
                    itemEntity.setDeltaMovement(world.random.nextDouble() * 0.2 - 0.1, 0.4, world.random.nextDouble() * 0.2 - 0.1);
                    world.addFreshEntity(itemEntity);
                });
            }
        }

        if (this.perPlayerLootTableId != null && !this.perPlayerLootTableId.isEmpty()) {
            AABB battleBox = new AABB(worldPosition).inflate(battleRadius);
            List<ServerPlayer> playersInBattle = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
            for (ServerPlayer player : playersInBattle) {
                for (int i = 0; i < rewardMultiplier; i++) {
                    ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
                    bundle.set(DataComponentRegistry.LOOT_BUNDLE_DATA, new LootBundleDataComponent(this.perPlayerLootTableId));
                    if (!player.getInventory().add(bundle)) {
                        player.drop(bundle, false);
                    }
                }
            }
        }

        // Start dungeon close timer
        this.internalDungeonCloseTimer = this.dungeonCloseTimer;
        this.isBattleActive = false; // Battle technically over, but waiting for timer
        this.isDungeonActive = false;
        this.activeBossUuid = null;
        this.bossDimension = null;
        this.dungeonStartTick = -1;
        this.dungeonTimeTicksRemaining = 0;
        this.lastPublishedDungeonSeconds = -1;
        this.downedPlayers.clear();
        this.dungeonTimeBossBar.setVisible(false);
        updateControllerRemainingTime(world, 0);
        
        // Initialize Boss Bar
        this.dungeonCloseBossBar.setProgress(1.0F);
        this.dungeonCloseBossBar.setVisible(true);
        updateBossBarPlayers(world);

        this.setChanged();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    
    private void teleportTrackedPlayersToExit(ServerLevel world) {
        if (exitPositionCoords == null || exitPositionCoords.equals(BlockPos.ZERO)) return;
        
        for (UUID uuid : trackedPlayers) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
            if (player != null && player.level() == world && !player.isDeadOrDying()) {
                ServerLevel exitWorld = Objects.requireNonNull(player.getServer()).getLevel(exitPositionDimension);
                if (exitWorld != null) {
                    BlockPos absoluteExitPos = this.worldPosition.offset(exitPositionCoords);
                    revivePlayerForExit(player);
                    teleportPlayerToExit(player, exitWorld, absoluteExitPos);
                    player.setPortalCooldown();
                }
            }
        }
        clearTrackedPlayers();
    }

    private void handleBattleLoss(ServerLevel world, String reason) {
        ArenasLdMod.LOGGER.info("Dungeon Battle lost at spawner {}: {}", worldPosition, reason);
        this.isDungeonActive = false;
        this.dungeonStartTick = -1;
        this.dungeonTimeTicksRemaining = 0;
        this.lastPublishedDungeonSeconds = -1;
        this.downedPlayers.clear();
        this.dungeonTimeBossBar.setVisible(false);

        if (activeBossUuid != null && bossDimension != null) {
            ServerLevel bossWorld = Objects.requireNonNull(world.getServer()).getLevel(bossDimension);
            if (bossWorld != null) {
                Entity bossEntity = bossWorld.getEntity(activeBossUuid);
                if (bossEntity != null && bossEntity.isAlive()) {
                    bossEntity.discard();
                    ArenasLdMod.LOGGER.info("Despawned boss after battle loss at {}.", worldPosition);
                }
            }
        }
        
        // Teleport tracked players to exit and send message
        if (exitPositionCoords != null && !exitPositionCoords.equals(BlockPos.ZERO)) {
            for (UUID uuid : trackedPlayers) {
                ServerPlayer player = world.getServer().getPlayerList().getPlayer(uuid);
                if (player != null && player.level() == world && !player.isDeadOrDying()) {
                    ServerLevel exitWorld = Objects.requireNonNull(player.getServer()).getLevel(exitPositionDimension);
                    if (exitWorld != null) {
                        BlockPos absoluteExitPos = this.worldPosition.offset(exitPositionCoords);
                        revivePlayerForExit(player);
                        teleportPlayerToExit(player, exitWorld, absoluteExitPos);
                        player.setPortalCooldown();
                        player.sendSystemMessage(Component.translatable("message.arenas_ld.dungeon_failed").withStyle(net.minecraft.ChatFormatting.RED));
                    }
                }
            }
        }
        clearTrackedPlayers();

        // No cooldown on loss, reset immediately
        resetSpawner(world, false);
        
        ArenasLdMod.LOGGER.info("Spawner at {} reset after battle loss.", worldPosition);
    }

    private void resetSpawner(ServerLevel world, boolean wasWin) {
        this.isBattleActive = false;
        this.isDungeonActive = false;
        this.activeBossUuid = null;
        this.bossDimension = null;
        this.respawnCooldown = wasWin ? this.respawnTime : 0;
        this.regenerationTickTimer = 0;
        this.internalDungeonCloseTimer = -1;
        this.dungeonStartTick = -1;
        this.dungeonTimeTicksRemaining = 0;
        this.lastPublishedDungeonSeconds = -1;
        this.downedPlayers.clear();
        clearTrackedPlayers();
        this.dungeonCloseBossBar.removeAllPlayers();
        this.dungeonCloseBossBar.setVisible(false);
        this.dungeonTimeBossBar.removeAllPlayers();
        this.dungeonTimeBossBar.setVisible(false);
        updateControllerRemainingTime(world, 0);
        resetController(world);
        this.setChanged();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        updateChunkLoading(world);
        ArenasLdMod.DUNGEON_BOSS_MANAGER.unregisterSpawner(this);
    }

    private void revivePlayerForExit(ServerPlayer player) {
        downedPlayers.remove(player.getUUID());
        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            player.setGameMode(GameType.SURVIVAL);
        }
        player.setHealth(player.getMaxHealth() * 0.5f);
    }

    private void teleportPlayerToExit(ServerPlayer player, ServerLevel exitWorld, BlockPos absoluteExitPos) {
        ChunkPos chunkPos = new ChunkPos(absoluteExitPos);
        exitWorld.setChunkForced(chunkPos.x, chunkPos.z, true);
        player.teleportTo(exitWorld, absoluteExitPos.getX() + 0.5, absoluteExitPos.getY(), absoluteExitPos.getZ() + 0.5, player.getYRot(), player.getXRot());
        exitWorld.setChunkForced(chunkPos.x, chunkPos.z, false);
    }

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putString("MobId", mobId);
        nbt.putInt("RespawnTime", respawnTime);
        nbt.putInt("DungeonCloseTimer", dungeonCloseTimer);
        nbt.putInt("DungeonTime", dungeonTime);
        nbt.putString("LootTableId", lootTableId);
        nbt.putString("PerPlayerLootTableId", perPlayerLootTableId);
        nbt.putLong("ExitPositionCoords", exitPositionCoords.asLong()); // Save relative
        nbt.putString("ExitPositionDimension", exitPositionDimension.location().toString());
        nbt.putLong("EntrancePosition", entrancePosition.asLong());
        nbt.putString("EntranceDimension", entranceDimension.location().toString());
        nbt.putInt("TriggerRadius", triggerRadius);
        nbt.putInt("BattleRadius", battleRadius);
        nbt.putInt("Regeneration", regeneration);
        nbt.putInt("SkillExperiencePerWin", skillExperiencePerWin);
        nbt.putBoolean("IsBattleActive", isBattleActive);
        nbt.putBoolean("IsDungeonActive", isDungeonActive);
        nbt.putInt("RespawnCooldown", respawnCooldown);
        nbt.putString("GroupId", groupId);
        if (activeBossUuid != null) nbt.putUUID("ActiveBossUuid", activeBossUuid);
        if (bossDimension != null) nbt.putString("BossDimension", bossDimension.location().toString());
        nbt.putInt("InternalDungeonCloseTimer", internalDungeonCloseTimer);
        nbt.putLong("DungeonStartTick", dungeonStartTick);
        nbt.putInt("DungeonTimeTicksRemaining", dungeonTimeTicksRemaining);
        nbt.putLong("LastTickTime", lastTickTime);
        nbt.putBoolean("HardcoreEnabled", hardcoreEnabled);
        if (controllerPos != null && controllerDimension != null) {
            nbt.putLong("ControllerPos", controllerPos.asLong());
            nbt.putString("ControllerDimension", controllerDimension.location().toString());
        }

        ListTag attributeList = new ListTag();
        for (AttributeData attr : attributes) {
            attributeList.add(attr.toNbt());
        }
        nbt.put("Attributes", attributeList);
        nbt.put("Equipment", equipment.toNbt());
        
        ListTag trackedList = new ListTag();
        for (UUID uuid : trackedPlayers) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            trackedList.add(tag);
        }
        nbt.put("TrackedPlayers", trackedList);

        ListTag leaderboardList = new ListTag();
        for (DungeonLeaderboardEntry entry : leaderboard) {
            leaderboardList.add(entry.toNbt());
        }
        nbt.put("Leaderboard", leaderboardList);
        
        nbt.putLongArray("LinkedSpawners", linkedSpawners.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        mobId = nbt.getString("MobId");
        respawnTime = nbt.getInt("RespawnTime");
        dungeonCloseTimer = nbt.getInt("DungeonCloseTimer");
        if (nbt.contains("DungeonTime")) {
            dungeonTime = nbt.getInt("DungeonTime");
        }
        lootTableId = nbt.getString("LootTableId");
        perPlayerLootTableId = nbt.getString("PerPlayerLootTableId");
        exitPositionCoords = BlockPos.of(nbt.getLong("ExitPositionCoords")); // Load relative
        if (nbt.contains("ExitPositionDimension")) {
            exitPositionDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("ExitPositionDimension")));
        }
        if (nbt.contains("EntrancePosition")) {
            entrancePosition = BlockPos.of(nbt.getLong("EntrancePosition"));
        }
        if (nbt.contains("EntranceDimension")) {
            entranceDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("EntranceDimension")));
        }
        triggerRadius = nbt.getInt("TriggerRadius");
        battleRadius = nbt.getInt("BattleRadius");
        regeneration = nbt.getInt("Regeneration");
        skillExperiencePerWin = nbt.getInt("SkillExperiencePerWin");
        isBattleActive = nbt.getBoolean("IsBattleActive");
        isDungeonActive = nbt.getBoolean("IsDungeonActive");
        respawnCooldown = nbt.getInt("RespawnCooldown");
        groupId = nbt.getString("GroupId");
        if (nbt.hasUUID("ActiveBossUuid")) activeBossUuid = nbt.getUUID("ActiveBossUuid");
        if (nbt.contains("BossDimension")) {
            bossDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("BossDimension")));
        }
        if (nbt.contains("InternalDungeonCloseTimer")) {
            internalDungeonCloseTimer = nbt.getInt("InternalDungeonCloseTimer");
        }
        if (nbt.contains("DungeonStartTick")) {
            dungeonStartTick = nbt.getLong("DungeonStartTick");
        }
        if (nbt.contains("DungeonTimeTicksRemaining")) {
            dungeonTimeTicksRemaining = nbt.getInt("DungeonTimeTicksRemaining");
        }
        if (nbt.contains("LastTickTime")) {
            lastTickTime = nbt.getLong("LastTickTime");
        }
        hardcoreEnabled = nbt.getBoolean("HardcoreEnabled");
        if (nbt.contains("ControllerPos") && nbt.contains("ControllerDimension")) {
            controllerPos = BlockPos.of(nbt.getLong("ControllerPos"));
            controllerDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("ControllerDimension")));
        }

        attributes.clear();
        ListTag attributeList = nbt.getList("Attributes", CompoundTag.TAG_COMPOUND);
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
        if (nbt.contains("TrackedPlayers")) {
            clearTrackedPlayers();
            ListTag trackedList = nbt.getList("TrackedPlayers", CompoundTag.TAG_COMPOUND);
            for (Tag tag : trackedList) {
                trackedPlayers.add(((CompoundTag) tag).getUUID("uuid"));
            }
        }

        leaderboard.clear();
        if (nbt.contains("Leaderboard")) {
            ListTag leaderboardList = nbt.getList("Leaderboard", Tag.TAG_COMPOUND);
            for (Tag tag : leaderboardList) {
                leaderboard.add(DungeonLeaderboardEntry.fromNbt((CompoundTag) tag));
            }
        }
        
        linkedSpawners.clear();
        if (nbt.contains("LinkedSpawners", Tag.TAG_LONG_ARRAY)) {
            long[] linkedSpawnersArray = nbt.getLongArray("LinkedSpawners");
            for (long posLong : linkedSpawnersArray) {
                linkedSpawners.add(BlockPos.of(posLong));
            }
        }
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

    public void setExitPositionCoords(BlockPos exitPositionCoords, ResourceKey<Level> exitPositionDimension) {
        this.exitPositionCoords = exitPositionCoords;
        this.exitPositionDimension = exitPositionDimension;
        setChanged();
    }

    public void setEntrancePosition(BlockPos entrancePosition, ResourceKey<Level> entranceDimension) {
        this.entrancePosition = entrancePosition;
        this.entranceDimension = entranceDimension;
        setChanged();
    }

    public void debugEndDungeon() {
        if (this.level instanceof ServerLevel serverLevel) {
            if (isDungeonActive || isBattleActive || internalDungeonCloseTimer > 0) {
                handleBattleLoss(serverLevel, "Debug end");
            } else {
                resetSpawner(serverLevel, false);
            }
        }
    }

    private static class DownedPlayer {
        private int ticksRemaining;
        private final Vec3 pos;
        private final ResourceKey<Level> dimension;

        private DownedPlayer(int ticksRemaining, Vec3 pos, ResourceKey<Level> dimension) {
            this.ticksRemaining = ticksRemaining;
            this.pos = pos;
            this.dimension = dimension;
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arenas_ld.dungeon_boss_spawner_config");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new DungeonBossSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BossSpawnerData getScreenOpeningData(ServerPlayer player) {
        return new BossSpawnerData(this.worldPosition);
    }
}
