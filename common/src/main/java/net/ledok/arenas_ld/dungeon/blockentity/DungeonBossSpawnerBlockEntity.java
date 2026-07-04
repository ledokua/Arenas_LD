package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerData;
import net.ledok.arenas_ld.dungeon.screen.DungeonBossSpawnerScreenHandler;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.ledok.arenas_ld.util.EntityEquipmentHelper;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class DungeonBossSpawnerBlockEntity extends BlockEntity implements AttributeProvider, EquipmentProvider, ExtendedScreenHandlerFactory<DungeonBossSpawnerData> {

    private EntityDefinition entityDefinition = EntityDefinition.DEFAULT.withMobId("minecraft:zombie");
    private BlockPos entranceOffset = BlockPos.ZERO;
    private ResourceKey<Level> entranceDimension = Level.OVERWORLD;
    private final List<BlockPos> roomOffsets = new ArrayList<>();

    public DungeonBossSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_BOSS_SPAWNER_BLOCK_ENTITY, pos, state);
    }

    /** Mark dirty for saving and push a block update so clients (overlay, screens) refresh. */
    private void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public EntityDefinition getEntityDefinition() { return entityDefinition; }

    public void setEntityDefinition(EntityDefinition def) {
        this.entityDefinition = def;
        markDirtyAndSync();
    }

    @Override
    public List<AttributeData> getAttributes() {
        return entityDefinition.attributes();
    }

    @Override
    public void setAttributes(List<AttributeData> attrs) {
        setEntityDefinition(entityDefinition.withAttributes(attrs));
    }

    @Override
    public EquipmentData getEquipment() {
        return entityDefinition.equipment();
    }

    @Override
    public void setEquipment(EquipmentData eq) {
        setEntityDefinition(entityDefinition.withEquipment(eq));
    }

    public BlockPos getEntranceOffset() { return entranceOffset; }

    public ResourceKey<Level> getEntranceDimension() { return entranceDimension; }

    public BlockPos getAbsoluteEntrancePos() {
        return worldPosition.offset(entranceOffset);
    }

    public void setEntrancePosition(BlockPos absolutePos, ResourceKey<Level> dim) {
        this.entranceOffset = absolutePos.subtract(worldPosition);
        this.entranceDimension = dim;
        markDirtyAndSync();
    }

    public List<BlockPos> getRooms() {
        List<BlockPos> absoluteRooms = new ArrayList<>(roomOffsets.size());
        for (BlockPos roomOffset : roomOffsets) {
            absoluteRooms.add(worldPosition.offset(roomOffset));
        }
        return Collections.unmodifiableList(absoluteRooms);
    }

    public List<BlockPos> getRoomOffsets() {
        return Collections.unmodifiableList(roomOffsets);
    }

    /** Room name per position in {@link #getRooms()} (blank if unset or the controller isn't loaded). */
    public List<String> getRoomNames() {
        List<String> names = new ArrayList<>(roomOffsets.size());
        for (BlockPos absolutePos : getRooms()) {
            String name = "";
            if (getLevel() != null && getLevel().getBlockEntity(absolutePos) instanceof RoomControllerBlockEntity room) {
                name = room.getRoomName();
            }
            names.add(name);
        }
        return names;
    }

    public boolean addRoom(BlockPos absolutePos) {
        BlockPos roomOffset = absolutePos.subtract(worldPosition);
        if (roomOffsets.contains(roomOffset)) return false;
        roomOffsets.add(roomOffset);
        markDirtyAndSync();
        return true;
    }

    public boolean removeRoom(BlockPos absolutePos) {
        BlockPos roomOffset = absolutePos.subtract(worldPosition);
        boolean removed = roomOffsets.remove(roomOffset);
        if (removed) markDirtyAndSync();
        return removed;
    }

    public boolean moveRoom(int from, int to) {
        if (from < 0 || from >= roomOffsets.size() || to < 0 || to >= roomOffsets.size()) return false;
        if (from == to) return false;
        BlockPos moved = roomOffsets.remove(from);
        roomOffsets.add(to, moved);
        markDirtyAndSync();
        return true;
    }

    public void clearRooms() {
        if (!roomOffsets.isEmpty()) {
            roomOffsets.clear();
            markDirtyAndSync();
        }
    }

    @Nullable
    public LivingEntity spawnSingleScaled(ServerLevel world, double healthMultiplier) {
        Optional<EntityType<?>> entityTypeOpt = EntityType.byString(this.entityDefinition.mobId());
        if (entityTypeOpt.isEmpty()) {
            ArenasLdMod.LOGGER.warn("DungeonBossSpawner (v4.0): invalid mob ID {} at {}", entityDefinition.mobId(), worldPosition);
            return null;
        }

        Entity mob = entityTypeOpt.get().create(world);
        if (!(mob instanceof LivingEntity living)) {
            ArenasLdMod.LOGGER.warn("DungeonBossSpawner (v4.0): not a LivingEntity: {}", entityDefinition.mobId());
            return null;
        }

        EntityEquipmentHelper.applyScaledAttributes(
            living, entityDefinition.attributes(), world.registryAccess(), healthMultiplier, 1.0, 1.0);
        EntityEquipmentHelper.applyAllEquipment(living, entityDefinition.equipment());

        living.heal(living.getMaxHealth());
        // Owned by the run: never let vanilla despawn the boss.
        if (living instanceof net.minecraft.world.entity.Mob persistentMob) {
            persistentMob.setPersistenceRequired();
        }

        Vec3 spawnPos = EntityEquipmentHelper.resolveBossSpawnPos(worldPosition, entityDefinition.spawnOffsets());
        living.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, world.random.nextFloat() * 360.0F, 0.0F);

        if (!world.addFreshEntity(living)) {
            ArenasLdMod.LOGGER.warn(
                "DungeonBossSpawner (v4.0): failed to add entity {} to world at {}",
                entityDefinition.mobId(),
                worldPosition
            );
            return null;
        }
        MobSpawnerBlockEntity.addToArenasTeam(world, living);
        return living;
    }

    private record State(
        EntityDefinition entity,
        BlockPos entranceOffset,
        ResourceKey<Level> entranceDim,
        List<BlockPos> roomOffsets
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityDefinition.CODEC.fieldOf("entity").forGetter(State::entity),
            BlockPos.CODEC.fieldOf("entranceOffset").forGetter(State::entranceOffset),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("entranceDim").forGetter(State::entranceDim),
            BlockPos.CODEC.listOf().fieldOf("roomOffsets").forGetter(State::roomOffsets)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        State.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), new State(entityDefinition, entranceOffset, entranceDimension, roomOffsets))
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                "Failed to save DungeonBossSpawner at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            State.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                    "Failed to load DungeonBossSpawner at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    this.entityDefinition = state.entity();
                    this.entranceOffset = state.entranceOffset();
                    this.entranceDimension = state.entranceDim();
                    this.roomOffsets.clear();
                    this.roomOffsets.addAll(state.roomOffsets());
                });
        }
    }

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
        return Component.translatable("gui.arenas_ld.dungeon_boss_spawner.title");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new DungeonBossSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public DungeonBossSpawnerData getScreenOpeningData(ServerPlayer player) {
        return new DungeonBossSpawnerData(worldPosition, entityDefinition.mobId(), entityDefinition.wave(), getRooms(), getRoomNames());
    }
}
