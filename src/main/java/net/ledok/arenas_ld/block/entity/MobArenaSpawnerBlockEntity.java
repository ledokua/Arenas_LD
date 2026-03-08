package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.registry.DataComponentRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.screen.MobArenaSpawnerData;
import net.ledok.arenas_ld.screen.MobArenaSpawnerScreenHandler;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.LootBundleDataComponent;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.ledok.arenas_ld.util.MobArenaRewardData;
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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
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

public class MobArenaSpawnerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<MobArenaSpawnerData> {

    // --- Configuration Fields ---
    public int triggerRadius = 16;
    public int battleRadius = 64;
    public int spawnDistance = 8;
    public int waveTimer = 120;
    public int additionalTime = 5;
    public int timeBetweenWaves = 10;
    public double attributeScale = 0.1;
    public int prepareTime = 10;
    public String groupId = "";
    public int bossWaveAdditionalTime = 60;
    
    public BlockPos exitPosition = BlockPos.ZERO;
    public ResourceKey<Level> exitDimension = Level.OVERWORLD;
    public BlockPos arenaEntrancePosition = BlockPos.ZERO;
    public ResourceKey<Level> arenaEntranceDimension = Level.OVERWORLD;

    public List<MobArenaMobData> mobs = new ArrayList<>();
    public List<MobArenaRewardData> rewards = new ArrayList<>();

    // --- State Machine Fields ---
    private boolean isArenaActive = false;
    private int currentWave = 0;
    private int waveTicksRemaining = 0;
    private int timeBetweenWavesTicks = 0;
    private int prepareTicksRemaining = 0;
    private final Set<UUID> aliveMobs = new HashSet<>();
    private final ServerBossEvent bossBar = (ServerBossEvent) new ServerBossEvent(
            Component.literal("Mob Arena"),
            BossEvent.BossBarColor.RED,
            BossEvent.BossBarOverlay.PROGRESS
    ).setDarkenScreen(false).setPlayBossMusic(false).setCreateWorldFog(false);
    private boolean isChunkLoaded = false;
    private Set<UUID> participatingPlayers = new HashSet<>();
    private BlockPos controllerPos;

    public MobArenaSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_ARENA_SPAWNER_BLOCK_ENTITY, pos, state);
    }

    public void removeParticipatingPlayer(UUID playerUUID) {
        participatingPlayers.remove(playerUUID);
        setChanged();
    }

    public int getParticipatingPlayerCount() {
        return participatingPlayers.size();
    }
    
    public void endArena() {
        if (level instanceof ServerLevel serverLevel) {
            endArena(serverLevel);
        }
    }

    public static void tick(Level world, BlockPos pos, BlockState state, MobArenaSpawnerBlockEntity be) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) return;

        be.updateChunkLoading(serverLevel);

        if (be.isArenaActive) {
            be.handleActiveArena(serverLevel);
        }
    }
    
    private void updateChunkLoading(ServerLevel world) {
        boolean shouldBeLoaded = this.isArenaActive;
        if (shouldBeLoaded != isChunkLoaded) {
            ChunkPos chunkPos = new ChunkPos(this.worldPosition);
            world.setChunkForced(chunkPos.x, chunkPos.z, shouldBeLoaded);
            isChunkLoaded = shouldBeLoaded;
        }
    }

    private void handleActiveArena(ServerLevel world) {
        // Check if any participating players are still in the arena
        boolean anyPlayerPresent = false;
        int spectatorCount = 0;
        for (UUID playerId : participatingPlayers) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && ArenasLdMod.MOB_ARENA_MANAGER.isInArena(player)) {
                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    spectatorCount++;
                } else {
                    anyPlayerPresent = true;
                }
            }
        }
        
        if (!anyPlayerPresent && spectatorCount > 0) {
            failArena(world);
            return;
        }

        // Update boss bar players
        for (UUID playerId : participatingPlayers) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                bossBar.addPlayer(player);
            }
        }
        
        Set<ServerPlayer> playersToRemove = new HashSet<>(bossBar.getPlayers());
        playersToRemove.removeIf(p -> participatingPlayers.contains(p.getUUID()));
        for (ServerPlayer player : playersToRemove) {
            bossBar.removePlayer(player);
        }

        if (prepareTicksRemaining > 0) {
            prepareTicksRemaining--;
            bossBar.setName(Component.translatable("bossbar.arenas_ld.prepare_time", prepareTicksRemaining / 20));
            bossBar.setProgress((float) prepareTicksRemaining / (prepareTime * 20));
            if (prepareTicksRemaining == 0) {
                startWave(world);
            }
            return;
        }

        aliveMobs.removeIf(uuid -> {
            Entity entity = world.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });

        if (timeBetweenWavesTicks > 0) {
            timeBetweenWavesTicks--;
            bossBar.setName(Component.translatable("bossbar.arenas_ld.next_wave_in", currentWave + 1, timeBetweenWavesTicks / 20));
            bossBar.setProgress((float) timeBetweenWavesTicks / (timeBetweenWaves * 20));
            if (timeBetweenWavesTicks == 0) {
                startWave(world);
            }
            return;
        }

        if (aliveMobs.isEmpty() && currentWave > 0) {
            completeWave(world);
            return;
        }

        if (waveTicksRemaining > 0) {
            waveTicksRemaining--;
            bossBar.setName(Component.translatable("bossbar.arenas_ld.wave_info", currentWave, waveTicksRemaining / 20));
            
            int totalWaveTime = (waveTimer + (currentWave - 1) * additionalTime) * 20;
            bossBar.setProgress((float) waveTicksRemaining / totalWaveTime);
            
            if (waveTicksRemaining == 0) {
                failArena(world);
            }
        }
    }

    public void startArena(Set<UUID> players, BlockPos controllerPos) {
        if (level instanceof ServerLevel serverLevel) {
            this.participatingPlayers = new HashSet<>(players);
            this.controllerPos = controllerPos;
            
            // Teleport players to arena
            for (UUID playerId : participatingPlayers) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    ArenasLdMod.MOB_ARENA_MANAGER.addPlayer(player, this.worldPosition, this.level.dimension());
                    BlockPos absoluteEnterDest = this.worldPosition.offset(this.arenaEntrancePosition);
                    ServerLevel destLevel = serverLevel.getServer().getLevel(arenaEntranceDimension);
                    if (destLevel != null) {
                        player.teleportTo(destLevel, absoluteEnterDest.getX() + 0.5, absoluteEnterDest.getY(), absoluteEnterDest.getZ() + 0.5, player.getYRot(), player.getXRot());
                    }
                }
            }

            isArenaActive = true;
            currentWave = 0;
            aliveMobs.clear();
            bossBar.setVisible(true);
            prepareTicksRemaining = prepareTime * 20;
            setChanged();
        }
    }

    private void startWave(ServerLevel world) {
        currentWave++;
        if (controllerPos != null && world.getBlockEntity(controllerPos) instanceof MobArenaControllerBlockEntity controller) {
            controller.currentWave = currentWave;
            controller.setChanged();
            world.sendBlockUpdated(controllerPos, controller.getBlockState(), controller.getBlockState(), 3);
        }
        
        // Clear items on the ground
        List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, new AABB(worldPosition).inflate(battleRadius));
        for (ItemEntity item : items) {
            item.discard();
        }
        
        boolean bossSpawned = spawnMobs(world);
        int waveTimeSeconds = waveTimer + (currentWave - 1) * additionalTime;
        if (bossSpawned) {
            waveTimeSeconds += bossWaveAdditionalTime;
        }
        
        waveTicksRemaining = waveTimeSeconds * 20;
        setChanged();
    }

    private void completeWave(ServerLevel world) {
        distributeRewards(world);
        timeBetweenWavesTicks = timeBetweenWaves * 20;
        reviveSpectators(world);
        setChanged();
    }

    private void failArena(ServerLevel world) {
        endArena(world);
    }

    private void endArena(ServerLevel world) {
        isArenaActive = false;
        currentWave = 0;
        waveTicksRemaining = 0;
        timeBetweenWavesTicks = 0;
        prepareTicksRemaining = 0;
        
        for (UUID uuid : aliveMobs) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
        aliveMobs.clear();
        
        bossBar.removeAllPlayers();
        bossBar.setVisible(false);
        
        for (UUID playerId : participatingPlayers) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                ArenasLdMod.MOB_ARENA_MANAGER.removePlayer(player);
                player.setGameMode(GameType.SURVIVAL);
                BlockPos absoluteExitDest = this.worldPosition.offset(this.exitPosition);
                ServerLevel destLevel = world.getServer().getLevel(exitDimension);
                if (destLevel != null) {
                    player.teleportTo(destLevel, absoluteExitDest.getX() + 0.5, absoluteExitDest.getY(), absoluteExitDest.getZ() + 0.5, player.getYRot(), player.getXRot());
                }
            }
        }
        
        if (controllerPos != null && world.getBlockEntity(controllerPos) instanceof MobArenaControllerBlockEntity controller) {
            controller.reset();
        }
        
        setChanged();
    }

    private void reviveSpectators(ServerLevel world) {
        for (UUID playerId : participatingPlayers) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player != null && player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.SURVIVAL);
                BlockPos absoluteEnterDest = this.worldPosition.offset(this.arenaEntrancePosition);
                ServerLevel destLevel = world.getServer().getLevel(arenaEntranceDimension);
                if (destLevel != null) {
                    player.teleportTo(destLevel, absoluteEnterDest.getX() + 0.5, absoluteEnterDest.getY(), absoluteEnterDest.getZ() + 0.5, player.getYRot(), player.getXRot());
                }
            }
        }
    }

    private boolean spawnMobs(ServerLevel world) {
        if (mobs.isEmpty()) return false;

        boolean bossSpawned = false;
        List<MobArenaMobData> validBosses = new ArrayList<>();
        List<MobArenaMobData> validRegulars = new ArrayList<>();
        
        for (MobArenaMobData mob : mobs) {
            if (currentWave >= mob.minWave && currentWave <= mob.maxWave) {
                if (mob.isBoss) {
                    validBosses.add(mob);
                } else {
                    validRegulars.add(mob);
                }
            }
        }

        // Spawn Boss (Limit 1)
        if (!validBosses.isEmpty()) {
            MobArenaMobData bossData = validBosses.get(world.random.nextInt(validBosses.size()));
            spawnMob(world, bossData, true);
            bossSpawned = true;
        }

        // Spawn Regulars
        if (!validRegulars.isEmpty()) {
            int mobCount = 5 + (currentWave / 2); 
            for (int i = 0; i < mobCount; i++) {
                MobArenaMobData mobData = selectRandomMob(validRegulars);
                if (mobData != null) {
                    spawnMob(world, mobData, false);
                }
            }
        }
        
        return bossSpawned;
    }

    private MobArenaMobData selectRandomMob(List<MobArenaMobData> validMobs) {
        int totalWeight = 0;
        for (MobArenaMobData mob : validMobs) {
            totalWeight += mob.weight;
        }

        if (validMobs.isEmpty() || totalWeight <= 0) return null;

        int randomWeight = level.random.nextInt(totalWeight);
        for (MobArenaMobData mob : validMobs) {
            randomWeight -= mob.weight;
            if (randomWeight < 0) {
                return mob;
            }
        }
        return validMobs.get(0);
    }

    private void spawnMob(ServerLevel world, MobArenaMobData mobData, boolean isBoss) {
        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(mobData.mobId);
        if (entityTypeOpt.isEmpty()) return;

        Entity entity = entityTypeOpt.get().create(world);
        if (entity instanceof LivingEntity livingEntity) {
            double scaleFactor = isBoss ? 1.0 : Math.pow(1.0 + attributeScale, currentWave - 1);
            
            for (AttributeData attr : mobData.attributes) {
                ResourceLocation attrLocation = ResourceLocation.tryParse(attr.id());
                if (attrLocation != null) {
                    var attributeRegistry = world.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
                    ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, attrLocation);
                    attributeRegistry.getHolder(key).ifPresent(holder -> {
                        AttributeInstance instance = livingEntity.getAttribute(holder);
                        if (instance != null) {
                            double value = attr.value() * scaleFactor;
                            if (value > attr.maxValue()) {
                                value = attr.maxValue();
                            }
                            instance.setBaseValue(value);
                        }
                    });
                }
            }
            
            applyEquipment(livingEntity, EquipmentSlot.HEAD, mobData.equipment.head);
            applyEquipment(livingEntity, EquipmentSlot.CHEST, mobData.equipment.chest);
            applyEquipment(livingEntity, EquipmentSlot.LEGS, mobData.equipment.legs);
            applyEquipment(livingEntity, EquipmentSlot.FEET, mobData.equipment.feet);
            applyEquipment(livingEntity, EquipmentSlot.MAINHAND, mobData.equipment.mainHand);
            applyEquipment(livingEntity, EquipmentSlot.OFFHAND, mobData.equipment.offHand, mobData.equipment.dropChance);

            livingEntity.heal(livingEntity.getMaxHealth());
            
            if (!this.groupId.isEmpty()) {
                Scoreboard scoreboard = world.getScoreboard();
                PlayerTeam team = scoreboard.getPlayerTeam(this.groupId);
                if (team == null) {
                    team = scoreboard.addPlayerTeam(this.groupId);
                    team.setAllowFriendlyFire(false);
                }
                scoreboard.addPlayerToTeam(livingEntity.getScoreboardName(), team);
            }
            
            boolean spawned = false;
            for (int attempt = 0; attempt < 10; attempt++) {
                double x, z;
                if (isBoss) {
                    x = this.worldPosition.getX() + 0.5;
                    z = this.worldPosition.getZ() + 0.5;
                } else {
                    double minRange = spawnDistance;
                    double maxRange = Math.max(spawnDistance + 1, battleRadius - 2);
                    double r = minRange + world.random.nextDouble() * (maxRange - minRange);
                    double angle = world.random.nextDouble() * 2 * Math.PI;
                    x = this.worldPosition.getX() + 0.5 + r * Math.cos(angle);
                    z = this.worldPosition.getZ() + 0.5 + r * Math.sin(angle);
                }

                for (int yOffset = 0; yOffset <= 5; yOffset++) {
                    int targetY = this.worldPosition.getY() + yOffset;
                    BlockPos pos = new BlockPos((int)x, targetY, (int)z);
                    
                    if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty() &&
                        !world.getBlockState(pos.below()).getCollisionShape(world, pos.below()).isEmpty()) {
                        
                        livingEntity.moveTo(x, targetY, z, world.random.nextFloat() * 360.0F, 0.0F);
                        if (world.noCollision(livingEntity) && !world.containsAnyLiquid(livingEntity.getBoundingBox())) {
                            world.addFreshEntity(livingEntity);
                            aliveMobs.add(livingEntity.getUUID());
                            spawned = true;
                            break;
                        }
                    }
                }
                if (spawned) break;
            }
            
            if (!spawned) {
                double x = this.worldPosition.getX() + 0.5;
                double y = this.worldPosition.getY() + 1;
                double z = this.worldPosition.getZ() + 0.5;
                livingEntity.moveTo(x, y, z, world.random.nextFloat() * 360.0F, 0.0F);
                world.addFreshEntity(livingEntity);
                aliveMobs.add(livingEntity.getUUID());
            }
        }
    }
    
    private void applyEquipment(LivingEntity entity, EquipmentSlot slot, String itemId) {
        applyEquipment(entity, slot, itemId, false);
    }

    private void applyEquipment(LivingEntity entity, EquipmentSlot slot, String itemId, boolean dropChance) {
        if (itemId != null && !itemId.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id != null) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != null) {
                    entity.setItemSlot(slot, new ItemStack(item));
                    if (entity instanceof Mob mob) {
                        mob.setDropChance(slot, dropChance ? 1.0F : 0.0F);
                    }
                }
            }
        }
    }

    private void distributeRewards(ServerLevel world) {
        List<MobArenaRewardData> validRewards = new ArrayList<>();
        for (MobArenaRewardData reward : rewards) {
            if (currentWave >= reward.minWave && currentWave <= reward.maxWave && (currentWave - reward.minWave) % reward.waveFrequency == 0) {
                validRewards.add(reward);
            }
        }

        if (validRewards.isEmpty()) return;

        // Distribute rewards to participating players
        List<ServerPlayer> players = new ArrayList<>();
        for (UUID playerId : participatingPlayers) {
            ServerPlayer player = world.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                players.add(player);
            }
        }

        for (MobArenaRewardData reward : validRewards) {
            for (int i = 0; i < reward.rolls; i++) {
                if (reward.perPlayer) {
                    for (ServerPlayer player : players) {
                        ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
                        bundle.set(DataComponentRegistry.LOOT_BUNDLE_DATA, new LootBundleDataComponent(reward.lootTableId));
                        if (!player.getInventory().add(bundle)) {
                            player.drop(bundle, false);
                        }
                    }
                } else {
                    if (players.isEmpty()) continue;
                    ServerPlayer randomPlayer = players.get(world.random.nextInt(players.size()));
                    ResourceLocation lootTableIdentifier = ResourceLocation.tryParse(reward.lootTableId);
                    if (lootTableIdentifier != null) {
                        LootTable lootTable = Objects.requireNonNull(world.getServer()).reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTableIdentifier));
                        LootParams.Builder builder = new LootParams.Builder(world)
                                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                                .withParameter(LootContextParams.THIS_ENTITY, randomPlayer);
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
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putInt("TriggerRadius", triggerRadius);
        nbt.putInt("BattleRadius", battleRadius);
        nbt.putInt("SpawnDistance", spawnDistance);
        nbt.putInt("WaveTimer", waveTimer);
        nbt.putInt("AdditionalTime", additionalTime);
        nbt.putInt("TimeBetweenWaves", timeBetweenWaves);
        nbt.putDouble("AttributeScale", attributeScale);
        nbt.putInt("PrepareTime", prepareTime);
        nbt.putString("GroupId", groupId);
        nbt.putInt("BossWaveAdditionalTime", bossWaveAdditionalTime);
        
        nbt.putLong("ExitPosition", exitPosition.asLong());
        nbt.putString("ExitDimension", exitDimension.location().toString());
        nbt.putLong("ArenaEntrancePosition", arenaEntrancePosition.asLong());
        nbt.putString("ArenaEntranceDimension", arenaEntranceDimension.location().toString());
        
        ListTag mobsList = new ListTag();
        for (MobArenaMobData mob : mobs) {
            mobsList.add(mob.toNbt());
        }
        nbt.put("Mobs", mobsList);
        
        ListTag rewardsList = new ListTag();
        for (MobArenaRewardData reward : rewards) {
            rewardsList.add(reward.toNbt());
        }
        nbt.put("Rewards", rewardsList);
        
        nbt.putBoolean("IsArenaActive", isArenaActive);
        nbt.putInt("CurrentWave", currentWave);
        nbt.putInt("WaveTicksRemaining", waveTicksRemaining);
        nbt.putInt("TimeBetweenWavesTicks", timeBetweenWavesTicks);
        nbt.putInt("PrepareTicksRemaining", prepareTicksRemaining);
        
        ListTag aliveMobsList = new ListTag();
        for (UUID uuid : aliveMobs) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            aliveMobsList.add(tag);
        }
        nbt.put("AliveMobs", aliveMobsList);

        ListTag participatingPlayersList = new ListTag();
        for (UUID uuid : participatingPlayers) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            participatingPlayersList.add(tag);
        }
        nbt.put("ParticipatingPlayers", participatingPlayersList);

        if (controllerPos != null) {
            nbt.putLong("ControllerPos", controllerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        triggerRadius = nbt.getInt("TriggerRadius");
        battleRadius = nbt.getInt("BattleRadius");
        spawnDistance = nbt.getInt("SpawnDistance");
        waveTimer = nbt.getInt("WaveTimer");
        additionalTime = nbt.getInt("AdditionalTime");
        timeBetweenWaves = nbt.getInt("TimeBetweenWaves");
        attributeScale = nbt.getDouble("AttributeScale");
        prepareTime = nbt.getInt("PrepareTime");
        groupId = nbt.getString("GroupId");
        bossWaveAdditionalTime = nbt.getInt("BossWaveAdditionalTime");
        
        exitPosition = BlockPos.of(nbt.getLong("ExitPosition"));
        exitDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("ExitDimension")));
        arenaEntrancePosition = BlockPos.of(nbt.getLong("ArenaEntrancePosition"));
        arenaEntranceDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("ArenaEntranceDimension")));
        
        mobs.clear();
        if (nbt.contains("Mobs")) {
            ListTag mobsList = nbt.getList("Mobs", Tag.TAG_COMPOUND);
            for (Tag t : mobsList) {
                mobs.add(MobArenaMobData.fromNbt((CompoundTag) t));
            }
        }
        
        rewards.clear();
        if (nbt.contains("Rewards")) {
            ListTag rewardsList = nbt.getList("Rewards", Tag.TAG_COMPOUND);
            for (Tag t : rewardsList) {
                rewards.add(MobArenaRewardData.fromNbt((CompoundTag) t));
            }
        }
        
        isArenaActive = nbt.getBoolean("IsArenaActive");
        currentWave = nbt.getInt("CurrentWave");
        waveTicksRemaining = nbt.getInt("WaveTicksRemaining");
        timeBetweenWavesTicks = nbt.getInt("TimeBetweenWavesTicks");
        prepareTicksRemaining = nbt.getInt("PrepareTicksRemaining");
        
        aliveMobs.clear();
        if (nbt.contains("AliveMobs")) {
            ListTag aliveMobsList = nbt.getList("AliveMobs", Tag.TAG_COMPOUND);
            for (Tag t : aliveMobsList) {
                aliveMobs.add(((CompoundTag) t).getUUID("uuid"));
            }
        }

        participatingPlayers.clear();
        if (nbt.contains("ParticipatingPlayers")) {
            ListTag participatingPlayersList = nbt.getList("ParticipatingPlayers", Tag.TAG_COMPOUND);
            for (Tag t : participatingPlayersList) {
                participatingPlayers.add(((CompoundTag) t).getUUID("uuid"));
            }
        }

        if (nbt.contains("ControllerPos")) {
            controllerPos = BlockPos.of(nbt.getLong("ControllerPos"));
        }
    }
    
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && isChunkLoaded) {
            ChunkPos chunkPos = new ChunkPos(this.worldPosition);
            serverLevel.setChunkForced(chunkPos.x, chunkPos.z, false);
            isChunkLoaded = false;
        }
        bossBar.removeAllPlayers();
        super.setRemoved();
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
    public Component getDisplayName() {return Component.translatable("container.arenas_ld.mob_arena_spawner_config");}

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new MobArenaSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public MobArenaSpawnerData getScreenOpeningData(ServerPlayer player) {return new MobArenaSpawnerData(this.worldPosition);}
}
