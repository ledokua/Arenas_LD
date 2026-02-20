package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.compat.PuffishSkillsCompat;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.registry.BlockRegistry;
import net.ledok.arenas_ld.registry.ItemRegistry;
import net.ledok.arenas_ld.screen.BossSpawnerData;
import net.ledok.arenas_ld.screen.BossSpawnerScreenHandler;
import net.ledok.arenas_ld.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class BossSpawnerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BossSpawnerData>, AttributeProvider, EquipmentProvider, LinkableSpawner {

    // --- Configuration Fields ---
    public String mobId = "minecraft:zombie";
    public int respawnTime = 6000;
    public int portalActiveTime = 600;
    public String lootTableId = "minecraft:chests/simple_dungeon";
    public String perPlayerLootTableId = "";
    public BlockPos exitPortalCoords = BlockPos.ZERO;
    public BlockPos enterPortalSpawnCoords = BlockPos.ZERO;
    public BlockPos enterPortalDestCoords = BlockPos.ZERO;
    public int triggerRadius = 16;
    public int battleRadius = 64;
    public int regeneration = 0;
    public int minPlayers = 2;
    public int skillExperiencePerWin = 100;
    public String groupId = "";
    public String instanceId = "";
    private final List<AttributeData> attributes = new ArrayList<>();
    private EquipmentData equipment = new EquipmentData();
    private final List<BlockPos> linkedSpawnerOffsets = new ArrayList<>();

    // --- State Machine Fields ---
    protected boolean isBattleActive = false;
    protected int respawnCooldown = 0;
    protected UUID activeBossUuid = null;
    protected ResourceKey<Level> bossDimension = null;
    protected int regenerationTickTimer = 0;
    protected int enterPortalRemovalTimer = -1;
    protected boolean firstTick = true;

    public BossSpawnerBlockEntity(BlockPos pos, BlockState state) {
        this(BlockEntitiesRegistry.BOSS_SPAWNER_BLOCK_ENTITY, pos, state);
    }

    protected BossSpawnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
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

    @Override
    public void addLinkedSpawner(BlockPos pos) {
        BlockPos offset = pos.subtract(this.worldPosition);
        if (!linkedSpawnerOffsets.contains(offset)) {
            linkedSpawnerOffsets.add(offset);
            setChanged();
        }
    }

    @Override
    public void clearLinkedSpawners() {
        linkedSpawnerOffsets.clear();
        setChanged();
    }

    @Override
    public List<BlockPos> getLinkedSpawners() {
        List<BlockPos> absolutes = new ArrayList<>();
        for (BlockPos offset : linkedSpawnerOffsets) {
            absolutes.add(this.worldPosition.offset(offset));
        }
        return absolutes;
    }

    @Override
    public void forceReset() {
        if (this.level instanceof ServerLevel serverLevel) {
            resetSpawner(serverLevel);
        }
    }

    public static void tick(Level world, BlockPos pos, BlockState state, BossSpawnerBlockEntity be) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) return;
        
        if (be.firstTick) {
            // Auto-Configuration Logic
            if (be.instanceId.isEmpty()) {
                be.instanceId = UUID.randomUUID().toString();
                be.setChanged();
                
                // Propagate to linked spawners
                for (BlockPos linkedPos : be.getLinkedSpawners()) {
                    if (world.isLoaded(linkedPos)) {
                        BlockEntity linkedBe = world.getBlockEntity(linkedPos);
                        if (linkedBe instanceof MobSpawnerBlockEntity mobSpawner) {
                            mobSpawner.instanceId = be.instanceId;
                            mobSpawner.setChanged();
                            // Re-register with new instance ID
                            ArenasLdMod.PHASE_BLOCK_MANAGER.unregisterSpawner(mobSpawner);
                            ArenasLdMod.PHASE_BLOCK_MANAGER.registerSpawner(mobSpawner);
                        }
                    }
                }
                
                // Claim orphan Phase Blocks
                if (!be.groupId.isEmpty()) {
                    ArenasLdMod.PHASE_BLOCK_MANAGER.claimOrphans(serverLevel, be.groupId, be.instanceId);
                }
            }

            // Re-link spawners on first tick
            for (BlockPos linkedPos : be.getLinkedSpawners()) {
                if (world.isLoaded(linkedPos)) {
                    BlockEntity linkedBe = world.getBlockEntity(linkedPos);
                    // Just ensuring the chunk is loaded and we can access it if needed
                }
            }
            be.firstTick = false;
        }

        if (be.isBattleActive) {
            be.handleActiveBattle(serverLevel);
        } else {
            be.handleIdleState(serverLevel, pos);
        }
    }

    protected void handleIdleState(ServerLevel world, BlockPos pos) {
        if (respawnCooldown > 0) {
            respawnCooldown--;
            if (respawnCooldown == 0) {
                spawnEnterPortal(world);
                
                // Trigger linked spawners
                for (BlockPos linkedPos : getLinkedSpawners()) {
                    if (world.isLoaded(linkedPos)) {
                        BlockEntity be = world.getBlockEntity(linkedPos);
                        if (be instanceof LinkableSpawner linkedSpawner) {
                            linkedSpawner.forceReset();
                        }
                    }
                }
            }
            return;
        }

        AABB triggerBox = new AABB(pos).inflate(triggerRadius);
        List<ServerPlayer> playersInTriggerZone = world.getEntitiesOfClass(ServerPlayer.class, triggerBox, p -> !p.isSpectator());

        if (playersInTriggerZone.size() >= this.minPlayers) {
            startBattle(world, pos, playersInTriggerZone.get(0));
        }
    }

    protected void handleActiveBattle(ServerLevel world) {
        if (this.enterPortalRemovalTimer > 0) {
            this.enterPortalRemovalTimer--;
            if (this.enterPortalRemovalTimer == 0) {
                removeEnterPortal(world);
                ArenasLdMod.LOGGER.info("Enter portal at {} has timed out and was removed.", enterPortalSpawnCoords);
            }
        }

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
            bossEntity.discard();
            handleBattleLoss(world, "All players left the battle area.");
            return;
        }
        if (regeneration > 0 && bossEntity instanceof LivingEntity livingBoss) {
            regenerationTickTimer++;
            if (regenerationTickTimer >= 100) {
                livingBoss.heal((float) regeneration);
                regenerationTickTimer = 0;
            }
        }
    }

    protected void startBattle(ServerLevel world, BlockPos spawnPos, ServerPlayer triggeringPlayer) {
        this.enterPortalRemovalTimer = 600;

        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(this.mobId);

        Component mobDisplayName = entityTypeOpt.map(EntityType::getDescription).orElse(Component.literal(this.mobId));

        Component announcement = Component.translatable("message.arenas_ld.raid_start", triggeringPlayer.getDisplayName(), mobDisplayName)
                .withStyle(net.minecraft.ChatFormatting.GOLD);
        Objects.requireNonNull(world.getServer()).getPlayerList().broadcastSystemMessage(announcement, false);

        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.error("Invalid mob ID in spawner at {}: {}", this.worldPosition, this.mobId);
            this.respawnCooldown = this.respawnTime;
            return;
        }
        Entity boss = entityTypeOpt.get().create(world);
        if (boss instanceof LivingEntity livingBoss) {
            for (AttributeData attr : attributes) {
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
        ArenasLdMod.LOGGER.info("Battle started at spawner {} with boss {}", this.worldPosition, this.mobId);
    }

    protected void applyEquipment(LivingEntity entity, EquipmentSlot slot, String itemId) {
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

    protected void handleBattleWin(ServerLevel world, Entity defeatedBoss) {
        ArenasLdMod.LOGGER.info("Battle won at spawner {}", worldPosition);

        if (this.skillExperiencePerWin > 0) {
            AABB battleBox = new AABB(worldPosition).inflate(battleRadius);
            List<ServerPlayer> playersInBattle = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
            for (ServerPlayer player : playersInBattle) {
                if (FabricLoader.getInstance().isModLoaded("puffish_skills")) {
                    PuffishSkillsCompat.addExperience(player, this.skillExperiencePerWin);
                }
            }
        }

        ResourceLocation lootTableIdentifier = ResourceLocation.tryParse(this.lootTableId);
        if (lootTableIdentifier != null) {
            LootTable lootTable = Objects.requireNonNull(world.getServer()).reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTableIdentifier));

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

        if (this.perPlayerLootTableId != null && !this.perPlayerLootTableId.isEmpty()) {
            AABB battleBox = new AABB(worldPosition).inflate(battleRadius);
            List<ServerPlayer> playersInBattle = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
            for (ServerPlayer player : playersInBattle) {
                ItemStack bundle = new ItemStack(ItemRegistry.LOOT_BUNDLE);
                bundle.set(LootBundleDataComponent.LOOT_BUNDLE_DATA, new LootBundleDataComponent(this.perPlayerLootTableId));
                if (!player.getInventory().add(bundle)) {
                    player.drop(bundle, false);
                }
            }
        }

        BlockPos portalPos = worldPosition.above(2);
        world.setBlock(portalPos, BlockRegistry.EXIT_PORTAL_BLOCK.defaultBlockState(), 3);
        if (world.getBlockEntity(portalPos) instanceof ExitPortalBlockEntity portal) {
            portal.setDetails(this.portalActiveTime, this.exitPortalCoords);
            ArenasLdMod.LOGGER.info("Spawned exit portal at {} for {} ticks.", portalPos, this.portalActiveTime);
        }
        resetSpawner(world);
        removeEnterPortal(world);
    }

    protected void handleBattleLoss(ServerLevel world, String reason) {
        ArenasLdMod.LOGGER.info("Battle lost at spawner {}: {}", worldPosition, reason);

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

        removeEnterPortal(world);
        this.respawnCooldown = 1200; // 60 seconds
        this.isBattleActive = false;
        this.activeBossUuid = null;
        this.bossDimension = null;
        this.regenerationTickTimer = 0;
        this.enterPortalRemovalTimer = -1;
        this.setChanged();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        ArenasLdMod.LOGGER.info("Spawner at {} on short cooldown (60s) after battle loss.", worldPosition);
    }

    protected void resetSpawner(ServerLevel world) {
        this.isBattleActive = false;
        this.activeBossUuid = null;
        this.bossDimension = null;
        this.respawnCooldown = this.respawnTime;
        this.regenerationTickTimer = 0;
        this.enterPortalRemovalTimer = -1;
        this.setChanged();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        if (this.respawnCooldown <= 0) {
            spawnEnterPortal(world);
        }
    }

    protected void spawnEnterPortal(ServerLevel world) {
        if (enterPortalSpawnCoords == null || enterPortalDestCoords == null || enterPortalSpawnCoords.equals(BlockPos.ZERO)) {
            return;
        }
        world.setBlock(enterPortalSpawnCoords, BlockRegistry.ENTER_PORTAL_BLOCK.defaultBlockState(), 3);
        if (world.getBlockEntity(enterPortalSpawnCoords) instanceof EnterPortalBlockEntity be) {
            be.setDestination(enterPortalDestCoords);
        }
    }

    protected void removeEnterPortal(ServerLevel world) {
        if (enterPortalSpawnCoords != null && world.getBlockState(enterPortalSpawnCoords).is(BlockRegistry.ENTER_PORTAL_BLOCK)) {
            world.setBlock(enterPortalSpawnCoords, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putString("MobId", mobId);
        nbt.putInt("RespawnTime", respawnTime);
        nbt.putInt("PortalActiveTime", portalActiveTime);
        nbt.putString("LootTableId", lootTableId);
        nbt.putString("PerPlayerLootTableId", perPlayerLootTableId);
        nbt.putLong("ExitPortalCoords", exitPortalCoords.asLong());
        if (enterPortalSpawnCoords != null) nbt.putLong("EnterPortalSpawn", enterPortalSpawnCoords.asLong());
        if (enterPortalDestCoords != null) nbt.putLong("EnterPortalDest", enterPortalDestCoords.asLong());
        nbt.putInt("TriggerRadius", triggerRadius);
        nbt.putInt("BattleRadius", battleRadius);
        nbt.putInt("Regeneration", regeneration);
        nbt.putInt("MinPlayers", minPlayers);
        nbt.putInt("SkillExperiencePerWin", skillExperiencePerWin);
        nbt.putBoolean("IsBattleActive", isBattleActive);
        nbt.putInt("RespawnCooldown", respawnCooldown);
        nbt.putString("GroupId", groupId);
        nbt.putString("InstanceId", instanceId);
        if (activeBossUuid != null) nbt.putUUID("ActiveBossUuid", activeBossUuid);
        if (bossDimension != null) nbt.putString("BossDimension", bossDimension.location().toString());

        ListTag attributeList = new ListTag();
        for (AttributeData attr : attributes) {
            attributeList.add(attr.toNbt());
        }
        nbt.put("Attributes", attributeList);
        nbt.put("Equipment", equipment.toNbt());
        
        nbt.putLongArray("LinkedSpawnerOffsets", linkedSpawnerOffsets.stream().mapToLong(BlockPos::asLong).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        mobId = nbt.getString("MobId");
        respawnTime = nbt.getInt("RespawnTime");
        portalActiveTime = nbt.getInt("PortalActiveTime");
        lootTableId = nbt.getString("LootTableId");
        perPlayerLootTableId = nbt.getString("PerPlayerLootTableId");
        exitPortalCoords = BlockPos.of(nbt.getLong("ExitPortalCoords"));
        if (nbt.contains("EnterPortalSpawn"))
            enterPortalSpawnCoords = BlockPos.of(nbt.getLong("EnterPortalSpawn"));
        if (nbt.contains("EnterPortalDest"))
            enterPortalDestCoords = BlockPos.of(nbt.getLong("EnterPortalDest"));
        triggerRadius = nbt.getInt("TriggerRadius");
        battleRadius = nbt.getInt("BattleRadius");
        regeneration = nbt.getInt("Regeneration");
        minPlayers = nbt.contains("MinPlayers") ? nbt.getInt("MinPlayers") : 1;
        skillExperiencePerWin = nbt.getInt("SkillExperiencePerWin");
        isBattleActive = nbt.getBoolean("IsBattleActive");
        respawnCooldown = nbt.getInt("RespawnCooldown");
        groupId = nbt.getString("GroupId");
        if (nbt.contains("InstanceId")) {
            instanceId = nbt.getString("InstanceId");
        }
        if (nbt.hasUUID("ActiveBossUuid")) activeBossUuid = nbt.getUUID("ActiveBossUuid");
        if (nbt.contains("BossDimension")) {
            bossDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("BossDimension")));
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
        
        linkedSpawnerOffsets.clear();
        if (nbt.contains("LinkedSpawnerOffsets")) {
            long[] array = nbt.getLongArray("LinkedSpawnerOffsets");
            for (long l : array) {
                linkedSpawnerOffsets.add(BlockPos.of(l));
            }
        } else if (nbt.contains("LinkedSpawners")) {
            List<BlockPos> absolutes = new ArrayList<>();
            if (nbt.contains("LinkedSpawners", Tag.TAG_LONG_ARRAY)) {
                long[] array = nbt.getLongArray("LinkedSpawners");
                for (long l : array) {
                    absolutes.add(BlockPos.of(l));
                }
            } else if (nbt.contains("LinkedSpawners", Tag.TAG_LIST)) {
                ListTag linkedList = nbt.getList("LinkedSpawners", CompoundTag.TAG_COMPOUND);
                for (Tag tag : linkedList) {
                    NbtUtils.readBlockPos((CompoundTag) tag, "").ifPresent(absolutes::add);
                }
            }
            for (BlockPos abs : absolutes) {
                linkedSpawnerOffsets.add(abs.subtract(this.worldPosition));
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
}
