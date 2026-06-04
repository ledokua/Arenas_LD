package net.ledok.arenas_ld.raid.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.blockentity.EntityDefinition;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.raid.run.RaidRun;
import net.ledok.arenas_ld.raid.run.RaidRunLifecycle;
import net.ledok.arenas_ld.raid.screen.RaidBossSpawnerData;
import net.ledok.arenas_ld.raid.screen.RaidBossSpawnerScreenHandler;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class RaidBossSpawnerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<RaidBossSpawnerData>, AttributeProvider, EquipmentProvider {
    // --- Config (arena geometry + mob definition only) ---
    private String groupId = "";
    private BlockPos entranceOffset = BlockPos.ZERO;
    private ResourceKey<Level> entranceDimension = Level.OVERWORLD;
    private final List<BlockPos> respawnPointOffsets = new ArrayList<>();
    /** Bundled entity config (mobId + attributes + equipment). Mirrors dungeon spawners. */
    private EntityDefinition entityDefinition = new EntityDefinition(
        "minecraft:zombie",
        new ArrayList<>(List.of(
            new AttributeData("minecraft:generic.max_health", 300.0),
            new AttributeData("minecraft:generic.attack_damage", 15.0)
        )),
        new EquipmentData()
    );

    public RaidBossSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.RAID_BOSS_SPAWNER_BLOCK_ENTITY, pos, state);
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

    public BlockPos getEntranceOffset() { return entranceOffset; }

    public ResourceKey<Level> getEntranceDimension() { return entranceDimension; }

    public BlockPos getAbsoluteEntrancePos() { return worldPosition.offset(entranceOffset); }

    public void setEntrancePosition(BlockPos absolutePos, ResourceKey<Level> dim) {
        this.entranceOffset = absolutePos.subtract(worldPosition);
        this.entranceDimension = dim == null ? Level.OVERWORLD : dim;
        markDirtyAndSync();
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId == null ? "" : groupId.trim();
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

    @Override
    public void setRemoved() {
        ArenasLdMod.RAID_BOSS_MANAGER.unregisterSpawner(this);
        super.setRemoved();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Run-logic delegation shims.
    //  All run logic now lives in RaidRunLifecycle, driven by the controller's
    //  tick loop. This block entity is a passive arena/config holder; the methods
    //  below exist only so external callers (LivingEntityMixin, RaidBossManager,
    //  PhaseBlockEntity) keep working by forwarding to the live run.
    // ─────────────────────────────────────────────────────────────────────────

    @Nullable
    private RaidControllerBlockEntity owningController() {
        if (!(level instanceof ServerLevel sl) || sl.getServer() == null) return null;
        return RaidRunLifecycle.findOwningController(sl.getServer(), worldPosition);
    }

    public boolean isHardcoreEnabled() {
        RaidRun run = findRun();
        return run != null && run.hardcoreEnabled();
    }

    public double getEffectiveDamageMultiplier() {
        RaidRun run = findRun();
        return run != null ? run.resolvedTierConfig().damageMultiplier() : 1.0;
    }

    public boolean isTracked(UUID playerId) {
        if (playerId == null) return false;
        RaidRun run = findRun();
        return run != null && run.participants().containsKey(playerId);
    }

    public boolean isRaidRunning() {
        return findRun() != null;
    }

    public void handlePlayerDown(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) return;
        RaidRun run = findRun();
        if (run == null) return;
        RaidRunLifecycle.handlePlayerDown(sl, owningController(), run, player);
    }

    public void handlePlayerHardcoreDeath(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) return;
        RaidRun run = findRun();
        if (run == null) return;
        RaidRunLifecycle.handlePlayerHardcoreDeath(sl, run, player);
    }

    public void handlePlayerDisconnect(ServerPlayer player) {
        if (!(level instanceof ServerLevel sl)) return;
        RaidRun run = findRun();
        if (run == null) return;
        RaidRunLifecycle.handlePlayerDisconnect(sl, owningController(), run, player);
    }

    public void handlePlayerReconnect(ServerPlayer player) {
        if (player == null) return;
        RaidRun run = findRun();
        if (run == null) {
            ArenasLdMod.RAID_BOSS_MANAGER.handlePostBattleReconnect(player);
            return;
        }
        if (level instanceof ServerLevel sl) {
            RaidRunLifecycle.handlePlayerReconnect(sl, owningController(), run, player);
        }
    }

    private record State(
        EntityDefinition entity,
        BlockPos entranceOffset,
        ResourceKey<Level> entranceDim,
        List<BlockPos> respawnPointOffsets,
        String groupId
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityDefinition.CODEC.fieldOf("entity").forGetter(State::entity),
            BlockPos.CODEC.fieldOf("entranceOffset").forGetter(State::entranceOffset),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("entranceDim").forGetter(State::entranceDim),
            BlockPos.CODEC.listOf().fieldOf("respawnPointOffsets").forGetter(State::respawnPointOffsets),
            Codec.STRING.fieldOf("groupId").forGetter(State::groupId)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        State.CODEC.encodeStart(registryLookup.createSerializationContext(NbtOps.INSTANCE), new State(
                entityDefinition, entranceOffset, entranceDimension,
                new ArrayList<>(respawnPointOffsets), groupId))
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save RaidBossSpawner at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if (nbt.contains("State")) {
            State.CODEC.parse(registryLookup.createSerializationContext(NbtOps.INSTANCE), nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load RaidBossSpawner at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    this.entityDefinition = state.entity();
                    this.entranceOffset = state.entranceOffset();
                    this.entranceDimension = state.entranceDim();
                    this.respawnPointOffsets.clear();
                    this.respawnPointOffsets.addAll(state.respawnPointOffsets());
                    this.groupId = state.groupId();
                });
        } else {
            // Legacy migration from the pre-State per-field format.
            groupId = nbt.getString("GroupId");
            entranceOffset = nbt.contains("EntrancePosition", Tag.TAG_LONG)
                ? BlockPos.of(nbt.getLong("EntrancePosition")) : BlockPos.ZERO;
            if (nbt.contains("EntranceDimension")) {
                entranceDimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(nbt.getString("EntranceDimension")));
            } else {
                entranceDimension = Level.OVERWORLD;
            }
            respawnPointOffsets.clear();
            if (nbt.contains("RespawnPointOffsets", Tag.TAG_LONG_ARRAY)) {
                for (long posLong : nbt.getLongArray("RespawnPointOffsets")) {
                    respawnPointOffsets.add(BlockPos.of(posLong));
                }
            }
            if (nbt.contains("EntityDefinition", Tag.TAG_COMPOUND)) {
                EntityDefinition.CODEC.parse(registryLookup.createSerializationContext(NbtOps.INSTANCE), nbt.getCompound("EntityDefinition"))
                    .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load EntityDefinition at {}: {}", worldPosition, err))
                    .ifPresent(def -> this.entityDefinition = def);
            } else {
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
                    ? EquipmentData.fromNbt(registryLookup, nbt.getCompound("Equipment"))
                    : new EquipmentData();
                this.entityDefinition = new EntityDefinition(legacyMobId, legacyAttrs, legacyEquip);
            }
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
}
