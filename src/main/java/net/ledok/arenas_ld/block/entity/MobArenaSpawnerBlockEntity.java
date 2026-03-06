package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.registry.BlockRegistry;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
    
    public BlockPos exitPortalDestination = BlockPos.ZERO;
    public ResourceKey<Level> exitPortalDestinationDimension = Level.OVERWORLD;
    public BlockPos enterPortalSpawnCoords = BlockPos.ZERO;
    public ResourceKey<Level> enterPortalSpawnDimension = Level.OVERWORLD;
    public BlockPos enterPortalDestCoords = BlockPos.ZERO;
    public ResourceKey<Level> enterPortalDestDimension = Level.OVERWORLD;

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

    public MobArenaSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_ARENA_SPAWNER_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, MobArenaSpawnerBlockEntity be) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) return;

        if (be.isArenaActive) {
            be.handleActiveArena(serverLevel);
        } else {
            be.handleIdleState(serverLevel);
        }
    }

    private void handleIdleState(ServerLevel world) {
        spawnEnterPortal(world);
        spawnExitPortal(world);
        AABB triggerBox = new AABB(this.worldPosition).inflate(triggerRadius);
        List<ServerPlayer> players = world.getEntitiesOfClass(ServerPlayer.class, triggerBox, p -> !p.isSpectator());

        if (!players.isEmpty()) {
            startArena(world);
        }
    }

    private void handleActiveArena(ServerLevel world) {
        AABB battleBox = new AABB(this.worldPosition).inflate(battleRadius);
        List<ServerPlayer> players = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
        
        if (players.isEmpty()) {
            failArena(world);
            return;
        }

        for (ServerPlayer player : players) {
            bossBar.addPlayer(player);
        }
        
        Set<ServerPlayer> playersToRemove = new HashSet<>(bossBar.getPlayers());
        playersToRemove.removeAll(players);
        for (ServerPlayer player : playersToRemove) {
            bossBar.removePlayer(player);
        }

        if (prepareTicksRemaining > 0) {
            prepareTicksRemaining--;
            bossBar.setName(Component.translatable("bossbar.arenas_ld.prepare_time", prepareTicksRemaining / 20));
            bossBar.setProgress((float) prepareTicksRemaining / (prepareTime * 20));
            if (prepareTicksRemaining == 0) {
                removeEnterPortal(world);
                removeExitPortal(world);
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

    private void startArena(ServerLevel world) {
        isArenaActive = true;
        currentWave = 0;
        aliveMobs.clear();
        bossBar.setVisible(true);
        prepareTicksRemaining = prepareTime * 20;
        setChanged();
    }

    private void startWave(ServerLevel world) {
        currentWave++;
        int waveTimeSeconds = waveTimer + (currentWave - 1) * additionalTime;
        waveTicksRemaining = waveTimeSeconds * 20;
        removeExitPortal(world);
        
        // Clear items on the ground
        List<ItemEntity> items = world.getEntitiesOfClass(ItemEntity.class, new AABB(worldPosition).inflate(battleRadius));
        for (ItemEntity item : items) {
            item.discard();
        }
        
        spawnMobs(world);
        setChanged();
    }

    private void completeWave(ServerLevel world) {
        distributeRewards(world);
        timeBetweenWavesTicks = timeBetweenWaves * 20;
        spawnExitPortal(world);
        setChanged();
    }

    private void failArena(ServerLevel world) {
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
        spawnEnterPortal(world);
        spawnExitPortal(world);
        setChanged();
    }

    private void spawnMobs(ServerLevel world) {
        if (mobs.isEmpty()) return;

        int mobCount = 5 + (currentWave / 2); 
        
        for (int i = 0; i < mobCount; i++) {
            MobArenaMobData mobData = selectRandomMob();
            if (mobData != null) {
                spawnMob(world, mobData);
            }
        }
    }

    private MobArenaMobData selectRandomMob() {
        List<MobArenaMobData> validMobs = new ArrayList<>();
        int totalWeight = 0;
        for (MobArenaMobData mob : mobs) {
            if (currentWave >= mob.minWave && currentWave <= mob.maxWave) {
                validMobs.add(mob);
                totalWeight += mob.weight;
            }
        }

        if (validMobs.isEmpty()) return null;

        int randomWeight = level.random.nextInt(totalWeight);
        for (MobArenaMobData mob : validMobs) {
            randomWeight -= mob.weight;
            if (randomWeight < 0) {
                return mob;
            }
        }
        return validMobs.get(0);
    }

    private void spawnMob(ServerLevel world, MobArenaMobData mobData) {
        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(mobData.mobId);
        if (entityTypeOpt.isEmpty()) return;

        Entity entity = entityTypeOpt.get().create(world);
        if (entity instanceof LivingEntity livingEntity) {
            double scaleFactor = Math.pow(1.0 + attributeScale, currentWave - 1);
            
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
            
            double minRange = spawnDistance;
            double maxRange = Math.max(spawnDistance + 1, battleRadius - 2);
            
            double r = minRange + world.random.nextDouble() * (maxRange - minRange);
            double angle = world.random.nextDouble() * 2 * Math.PI;
            double x = this.worldPosition.getX() + 0.5 + r * Math.cos(angle);
            double z = this.worldPosition.getZ() + 0.5 + r * Math.sin(angle);
            double y = this.worldPosition.getY() + 1;
            
            livingEntity.moveTo(x, y, z, world.random.nextFloat() * 360F, 0);
            world.addFreshEntity(livingEntity);
            aliveMobs.add(livingEntity.getUUID());
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
    
    private void spawnEnterPortal(ServerLevel world) {
        if (enterPortalSpawnCoords != null && !enterPortalSpawnCoords.equals(BlockPos.ZERO)) {
            ServerLevel spawnWorld = world.getServer().getLevel(enterPortalSpawnDimension);
            if (spawnWorld != null) {
                BlockPos absolutePos = this.worldPosition.offset(enterPortalSpawnCoords);
                spawnWorld.setBlock(absolutePos, BlockRegistry.ENTER_PORTAL_BLOCK.defaultBlockState(), 3);
                if (spawnWorld.getBlockEntity(absolutePos) instanceof EnterPortalBlockEntity be) {
                    be.setDestination(this.worldPosition.offset(enterPortalDestCoords), enterPortalDestDimension);
                }
            }
        }
    }

    private void removeEnterPortal(ServerLevel world) {
        if (enterPortalSpawnCoords != null && !enterPortalSpawnCoords.equals(BlockPos.ZERO)) {
            ServerLevel spawnWorld = world.getServer().getLevel(enterPortalSpawnDimension);
            if (spawnWorld != null) {
                BlockPos absolutePos = this.worldPosition.offset(enterPortalSpawnCoords);
                if (spawnWorld.getBlockState(absolutePos).is(BlockRegistry.ENTER_PORTAL_BLOCK)) {
                    spawnWorld.setBlock(absolutePos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private void spawnExitPortal(ServerLevel world) {
        BlockPos portalPos = this.worldPosition.above(2);
        world.setBlock(portalPos, BlockRegistry.EXIT_PORTAL_BLOCK.defaultBlockState(), 3);
        if (world.getBlockEntity(portalPos) instanceof ExitPortalBlockEntity portal) {
            BlockPos absoluteDest;
            if (this.exitPortalDestinationDimension.equals(world.dimension())) {
                absoluteDest = this.worldPosition.offset(this.exitPortalDestination);
            } else {
                absoluteDest = this.exitPortalDestination;
            }
            portal.setDetails(Integer.MAX_VALUE, absoluteDest, this.exitPortalDestinationDimension);
        }
    }

    private void removeExitPortal(ServerLevel world) {
        BlockPos portalPos = this.worldPosition.above(2);
        if (world.getBlockState(portalPos).is(BlockRegistry.EXIT_PORTAL_BLOCK)) {
            world.setBlock(portalPos, Blocks.AIR.defaultBlockState(), 3);
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

        AABB battleBox = new AABB(this.worldPosition).inflate(battleRadius);
        List<ServerPlayer> players = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());

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
        
        nbt.putLong("ExitPortalDestination", exitPortalDestination.asLong());
        nbt.putString("ExitPortalDestinationDimension", exitPortalDestinationDimension.location().toString());
        nbt.putLong("EnterPortalSpawnCoords", enterPortalSpawnCoords.asLong());
        nbt.putString("EnterPortalSpawnDimension", enterPortalSpawnDimension.location().toString());
        nbt.putLong("EnterPortalDestCoords", enterPortalDestCoords.asLong());
        nbt.putString("EnterPortalDestDimension", enterPortalDestDimension.location().toString());
        
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
        
        exitPortalDestination = BlockPos.of(nbt.getLong("ExitPortalDestination"));
        exitPortalDestinationDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("ExitPortalDestinationDimension")));
        enterPortalSpawnCoords = BlockPos.of(nbt.getLong("EnterPortalSpawnCoords"));
        enterPortalSpawnDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("EnterPortalSpawnDimension")));
        enterPortalDestCoords = BlockPos.of(nbt.getLong("EnterPortalDestCoords"));
        enterPortalDestDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("EnterPortalDestDimension")));
        
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
    }
    
    @Override
    public void setRemoved() {
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
