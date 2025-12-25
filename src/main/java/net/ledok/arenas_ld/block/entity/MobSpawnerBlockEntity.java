package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;
//import net.ledok.arenas_ld.compat.PuffishSkillsCompat;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.screen.MobSpawnerData;
import net.ledok.arenas_ld.screen.MobSpawnerScreenHandler;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
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

public class MobSpawnerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<MobSpawnerData>, AttributeProvider, EquipmentProvider {

    // --- Configuration Fields ---
    public String mobId = "minecraft:zombie";
    public int respawnTime = 1200;
    public String lootTableId = "";
    public int triggerRadius = 16;
    public int battleRadius = 64;
    public int regeneration = 0;
    public int skillExperiencePerWin = 0;
    public int mobCount = 1;
    public int mobSpread = 5;
    public String groupId = "";
    private final List<AttributeData> attributes = new ArrayList<>();
    private EquipmentData equipment = new EquipmentData();

    // --- State Machine Fields ---
    private boolean isBattleActive = false;
    private int respawnCooldown = 0;
    private final List<UUID> activeMobUuids = new ArrayList<>();
    private final Map<UUID, Float> playerDamageDealt = new HashMap<>();
    private int regenerationTickTimer = 0;
    private boolean firstTick = true;


    public MobSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_SPAWNER_BLOCK_ENTITY, pos, state);
        // Default attributes
        if (attributes.isEmpty()) {
            attributes.add(new AttributeData("minecraft:generic.max_health", 20.0));
            attributes.add(new AttributeData("minecraft:generic.attack_damage", 3.0));
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

    public static void tick(Level world, BlockPos pos, BlockState state, MobSpawnerBlockEntity be) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverLevel)) return;

        if (be.firstTick) {
            ArenasLdMod.PHASE_BLOCK_MANAGER.registerSpawner(be);
            if (be.respawnCooldown > 0) {
                ArenasLdMod.PHASE_BLOCK_MANAGER.onSpawnerBattleWon(be.groupId, be.worldPosition);
            }
            be.firstTick = false;
        }

        if (be.isBattleActive) {
            be.handleActiveBattle(serverLevel);
        } else {
            be.handleIdleState(serverLevel, pos);
        }
    }

    @Override
    public void setRemoved() {
        if (this.level != null && !this.level.isClientSide()) {
            ArenasLdMod.PHASE_BLOCK_MANAGER.unregisterSpawner(this);
        }
        super.setRemoved();
    }

    private void handleIdleState(ServerLevel world, BlockPos pos) {
        if (respawnCooldown > 0) {
            respawnCooldown--;
            if (respawnCooldown == 0) {
                ArenasLdMod.PHASE_BLOCK_MANAGER.onSpawnerReset(this.groupId, this.worldPosition);
            }
            return;
        }

        AABB triggerBox = new AABB(pos).inflate(triggerRadius);
        List<ServerPlayer> playersInTriggerZone = world.getEntitiesOfClass(ServerPlayer.class, triggerBox, p -> !p.isSpectator());

        if (!playersInTriggerZone.isEmpty()) {
            startBattle(world, pos);
        }
    }

    private void handleActiveBattle(ServerLevel world) {
        activeMobUuids.removeIf(uuid -> world.getEntity(uuid) == null || !world.getEntity(uuid).isAlive());

        if (activeMobUuids.isEmpty()) {
            handleBattleWin(world);
            return;
        }

        AABB battleBox = new AABB(worldPosition).inflate(battleRadius);
        List<ServerPlayer> playersInBattle = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
        if (playersInBattle.isEmpty()) {
            handleBattleLoss(world, "All players left the battle area.");
            return;
        }

        for (UUID mobUuid : new ArrayList<>(activeMobUuids)) {
            Entity mob = world.getEntity(mobUuid);
            if (mob != null && !battleBox.intersects(mob.getBoundingBox())) {
                handleBattleLoss(world, "A mob left the battle zone.");
                return;
            }
        }

        if (regeneration > 0) {
            regenerationTickTimer++;
            if (regenerationTickTimer >= 100) { // Every 5 seconds
                for (UUID mobUuid : activeMobUuids) {
                    Entity mob = world.getEntity(mobUuid);
                    if (mob instanceof LivingEntity livingMob) {
                        livingMob.heal((float) regeneration);
                    }
                }
                regenerationTickTimer = 0;
            }
        }
    }


    private void startBattle(ServerLevel world, BlockPos spawnCenter) {
        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(mobId);
        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.error("Invalid mob ID in arena spawner at {}: {}", this.worldPosition, this.mobId);
            return;
        }

        ArenasLdMod.PHASE_BLOCK_MANAGER.onSpawnerReset(this.groupId, this.worldPosition);

        isBattleActive = true;
        playerDamageDealt.clear();

        for (int i = 0; i < mobCount; i++) {
            Entity mob = entityTypeOpt.get().create(world);
            if (mob instanceof LivingEntity livingMob) {
                for (AttributeData attr : attributes) {
                    ResourceLocation attrLocation = ResourceLocation.tryParse(attr.id());
                    if (attrLocation != null) {
                        var attributeRegistry = world.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
                        ResourceKey<Attribute> key = ResourceKey.create(Registries.ATTRIBUTE, attrLocation);
                        attributeRegistry.getHolder(key).ifPresent(holder -> {
                            AttributeInstance instance = livingMob.getAttribute(holder);
                            if (instance != null) {
                                instance.setBaseValue(attr.value());
                            }
                        });
                    }
                }
                
                // Apply Equipment
                applyEquipment(livingMob, EquipmentSlot.HEAD, equipment.head);
                applyEquipment(livingMob, EquipmentSlot.CHEST, equipment.chest);
                applyEquipment(livingMob, EquipmentSlot.LEGS, equipment.legs);
                applyEquipment(livingMob, EquipmentSlot.FEET, equipment.feet);
                applyEquipment(livingMob, EquipmentSlot.MAINHAND, equipment.mainHand);
                applyEquipment(livingMob, EquipmentSlot.OFFHAND, equipment.offHand);

                livingMob.heal(livingMob.getMaxHealth());

                if (!this.groupId.isEmpty()) {
                    Scoreboard scoreboard = world.getScoreboard();
                    PlayerTeam team = scoreboard.getPlayerTeam(this.groupId);
                    if (team == null) {
                        team = scoreboard.addPlayerTeam(this.groupId);
                        team.setAllowFriendlyFire(false);
                    }
                    scoreboard.addPlayerToTeam(livingMob.getScoreboardName(), team);
                }

                // Attempt to find a safe spawn location
                boolean spawned = false;
                for (int attempt = 0; attempt < 10; attempt++) {
                    double x = spawnCenter.getX() + 0.5 + (world.random.nextDouble() - 0.5) * mobSpread * 2;
                    double z = spawnCenter.getZ() + 0.5 + (world.random.nextDouble() - 0.5) * mobSpread * 2;
                    
                    // Search for ground starting from spawner level up to +5
                    for (int yOffset = 0; yOffset <= 5; yOffset++) {
                        int targetY = spawnCenter.getY() + yOffset;
                        BlockPos pos = new BlockPos((int)x, targetY, (int)z);
                        
                        // Check if feet are in air and block below is solid
                        if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty() &&
                            !world.getBlockState(pos.below()).getCollisionShape(world, pos.below()).isEmpty()) {
                            
                            livingMob.moveTo(x, targetY, z, world.random.nextFloat() * 360.0F, 0.0F);
                            if (world.noCollision(livingMob) && !world.containsAnyLiquid(livingMob.getBoundingBox())) {
                                world.addFreshEntity(livingMob);
                                activeMobUuids.add(livingMob.getUUID());
                                spawned = true;
                                break;
                            }
                        }
                    }
                    if (spawned) break;
                }
                
                if (!spawned) {
                    // Fallback: spawn at center + 1
                    double x = spawnCenter.getX() + 0.5;
                    double y = spawnCenter.getY() + 1;
                    double z = spawnCenter.getZ() + 0.5;
                    livingMob.moveTo(x, y, z, world.random.nextFloat() * 360.0F, 0.0F);
                    world.addFreshEntity(livingMob);
                    activeMobUuids.add(livingMob.getUUID());
                }
            }
        }
        ArenasLdMod.LOGGER.info("Mob Spawner started at {} with {} of {}", this.worldPosition, this.mobCount, this.mobId);
        setChanged();
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

    private void handleBattleWin(ServerLevel world) {
        ArenasLdMod.LOGGER.info("Mob Spawner won at {}", worldPosition);
        ArenasLdMod.PHASE_BLOCK_MANAGER.onSpawnerBattleWon(this.groupId, this.worldPosition);

        if (lootTableId != null && !lootTableId.isEmpty()) {
            ResourceLocation lootTableIdentifier = ResourceLocation.tryParse(lootTableId);
            if (lootTableIdentifier != null) {
                LootTable lootTable = Objects.requireNonNull(world.getServer()).reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, lootTableIdentifier));
                ServerPlayer contextPlayer = null;
                if (!playerDamageDealt.isEmpty()) {
                    UUID playerUuid = playerDamageDealt.keySet().iterator().next();
                    contextPlayer = world.getServer().getPlayerList().getPlayer(playerUuid);
                }

                if (contextPlayer == null) {
                    AABB battleBox = new AABB(worldPosition).inflate(battleRadius);
                    List<ServerPlayer> playersInBattle = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
                    if (!playersInBattle.isEmpty()) {
                        contextPlayer = playersInBattle.get(0);
                    }
                }

                if (contextPlayer != null) {
                    LootParams.Builder builder = new LootParams.Builder(world)
                            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                            .withParameter(LootContextParams.THIS_ENTITY, contextPlayer);

                    LootParams lootParams = builder.create(net.minecraft.world.level.storage.loot.parameters.LootContextParamSets.GIFT);
                    lootTable.getRandomItems(lootParams).forEach(stack ->
                            world.addFreshEntity(new ItemEntity(world, worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, stack)));

                } else {
                    ArenasLdMod.LOGGER.warn("Mob Spawner at {} won, but no player could be found to generate loot.", worldPosition);
                }
            }
        }

        /*if (this.skillExperiencePerWin > 0) {
            AABB battleBox = new AABB(worldPosition).inflate(battleRadius);
            List<ServerPlayer> playersInBattle = world.getEntitiesOfClass(ServerPlayer.class, battleBox, p -> !p.isSpectator());
            for (ServerPlayer player : playersInBattle) {
                if (FabricLoader.getInstance().isModLoaded("puffish_skills")) {
                    PuffishSkillsCompat.addExperience(player, this.skillExperiencePerWin);
                }
            }
        }*/

        resetSpawner(world, true);
    }

    private void handleBattleLoss(ServerLevel world, String reason) {
        ArenasLdMod.LOGGER.info("Mob Spawner lost at {}: {}", worldPosition, reason);
        for (UUID mobUuid : activeMobUuids) {
            Entity mob = world.getEntity(mobUuid);
            if (mob != null && mob.isAlive()) {
                mob.discard();
            }
        }
        resetSpawner(world, false);
    }

    private void resetSpawner(ServerLevel world, boolean wasWin) {
        isBattleActive = false;
        activeMobUuids.clear();
        playerDamageDealt.clear();
        regenerationTickTimer = 0;
        respawnCooldown = wasWin ? respawnTime : 0;
        setChanged();
        world.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void onMobDamaged(DamageSource source, float amount) {
        if (isBattleActive && source.getEntity() instanceof Player) {
            UUID playerUuid = source.getEntity().getUUID();
            playerDamageDealt.merge(playerUuid, amount, Float::sum);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putString("MobId", mobId);
        nbt.putInt("RespawnTime", respawnTime);
        nbt.putString("LootTableId", lootTableId);
        nbt.putInt("TriggerRadius", triggerRadius);
        nbt.putInt("BattleRadius", battleRadius);
        nbt.putInt("Regeneration", regeneration);
        nbt.putInt("SkillExperiencePerWin", skillExperiencePerWin);
        nbt.putInt("MobCount", mobCount);
        nbt.putInt("MobSpread", mobSpread);
        nbt.putString("GroupId", groupId);
        nbt.putBoolean("IsBattleActive", isBattleActive);
        nbt.putInt("RespawnCooldown", respawnCooldown);

        ListTag mobUuids = new ListTag();
        for (UUID uuid : activeMobUuids) {
            CompoundTag uuidNbt = new CompoundTag();
            uuidNbt.putUUID("uuid", uuid);
            mobUuids.add(uuidNbt);
        }
        nbt.put("ActiveMobs", mobUuids);

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
        triggerRadius = nbt.getInt("TriggerRadius");
        battleRadius = nbt.getInt("BattleRadius");
        regeneration = nbt.getInt("Regeneration");
        skillExperiencePerWin = nbt.getInt("SkillExperiencePerWin");
        mobCount = nbt.contains("MobCount") ? nbt.getInt("MobCount") : 1;
        mobSpread = nbt.contains("MobSpread") ? nbt.getInt("MobSpread") : 5;
        isBattleActive = nbt.getBoolean("IsBattleActive");
        respawnCooldown = nbt.getInt("RespawnCooldown");
        groupId = nbt.getString("GroupId");

        activeMobUuids.clear();
        ListTag mobUuids = nbt.getList("ActiveMobs", CompoundTag.TAG_COMPOUND);
        for (Tag tag : mobUuids) {
            CompoundTag uuidNbt = (CompoundTag) tag;
            activeMobUuids.add(uuidNbt.getUUID("uuid"));
        }

        attributes.clear();
        ListTag attributeList = nbt.getList("Attributes", CompoundTag.TAG_COMPOUND);
        for (Tag tag : attributeList) {
            attributes.add(AttributeData.fromNbt((CompoundTag) tag));
        }
        if (attributes.isEmpty()) {
             attributes.add(new AttributeData("minecraft:generic.max_health", 20.0));
            attributes.add(new AttributeData("minecraft:generic.attack_damage", 3.0));
        }
        if (nbt.contains("Equipment")) {
            equipment = EquipmentData.fromNbt(nbt.getCompound("Equipment"));
        }
    }

    public String getGroupId() {
        return groupId;
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
        return Component.literal("Mob Spawner Configuration");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new MobSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public MobSpawnerData getScreenOpeningData(ServerPlayer player) {
        return new MobSpawnerData(this.worldPosition);
    }
}
