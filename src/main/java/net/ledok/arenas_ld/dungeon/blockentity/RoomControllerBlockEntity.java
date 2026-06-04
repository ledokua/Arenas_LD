package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerData;
import net.ledok.arenas_ld.dungeon.screen.RoomControllerScreenHandler;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.Iterator;

public class RoomControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<RoomControllerData> {

    // ---- Fields ----

    private final List<BlockPos> spawnerOffsets = new ArrayList<>();
    private final List<BlockPos> doorOffsets = new ArrayList<>();
    /** Single respawn point for this room, relative to the controller. Players downed while this room is active respawn here. */
    @Nullable private BlockPos respawnOffset = null;
    private final Set<UUID> aliveMobs = new HashSet<>();
    private boolean activated = false;
    private boolean cleared = false;

    // ---- Construction ----

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.ROOM_CONTROLLER_BLOCK_ENTITY, pos, state);
    }

    // ---- Getters ----

    public List<BlockPos> getSpawnerPositions() {
        List<BlockPos> absoluteSpawners = new ArrayList<>(spawnerOffsets.size());
        for (BlockPos spawnerOffset : spawnerOffsets) {
            absoluteSpawners.add(worldPosition.offset(spawnerOffset));
        }
        return Collections.unmodifiableList(absoluteSpawners);
    }

    public List<BlockPos> getSpawnerOffsets() {
        return Collections.unmodifiableList(spawnerOffsets);
    }

    public List<BlockPos> getDoorPositions() {
        List<BlockPos> absolute = new ArrayList<>(doorOffsets.size());
        for (BlockPos doorOffset : doorOffsets) {
            absolute.add(worldPosition.offset(doorOffset));
        }
        return Collections.unmodifiableList(absolute);
    }

    public List<BlockPos> getDoorOffsets() {
        return Collections.unmodifiableList(doorOffsets);
    }

    /** Absolute respawn position for this room, or {@code null} if none is configured. */
    @Nullable
    public BlockPos getRespawnPos() {
        return respawnOffset == null ? null : worldPosition.offset(respawnOffset);
    }

    @Nullable
    public BlockPos getRespawnOffset() {
        return respawnOffset;
    }

    public Set<UUID> getAliveMobs() {
        return Collections.unmodifiableSet(aliveMobs);
    }

    public boolean isActivated() {
        return activated;
    }

    public boolean isCleared() {
        return cleared;
    }

    // ---- Admin / Linker operations ----

    /** Returns true if the spawner was added (false if already present). */
    public boolean addSpawner(BlockPos absolutePos) {
        BlockPos spawnerOffset = absolutePos.subtract(worldPosition);
        if (spawnerOffsets.contains(spawnerOffset)) {
            return false;
        }
        spawnerOffsets.add(spawnerOffset);
        setChanged();
        return true;
    }

    /** Returns true if the spawner was removed (false if not present). */
    public boolean removeSpawner(BlockPos absolutePos) {
        BlockPos spawnerOffset = absolutePos.subtract(worldPosition);
        boolean removed = spawnerOffsets.remove(spawnerOffset);
        if (removed) {
            setChanged();
        }
        return removed;
    }

    public void clearSpawners() {
        if (!spawnerOffsets.isEmpty()) {
            spawnerOffsets.clear();
            setChanged();
        }
    }

    /** Returns true if the door was added (false if already present). */
    public boolean addDoor(BlockPos absolutePos) {
        BlockPos doorOffset = absolutePos.subtract(worldPosition);
        if (doorOffsets.contains(doorOffset)) {
            return false;
        }
        doorOffsets.add(doorOffset);
        setChanged();
        return true;
    }

    /** Returns true if the door was removed (false if not present). */
    public boolean removeDoor(BlockPos absolutePos) {
        BlockPos doorOffset = absolutePos.subtract(worldPosition);
        boolean removed = doorOffsets.remove(doorOffset);
        if (removed) {
            setChanged();
        }
        return removed;
    }

    public void clearDoors() {
        if (!doorOffsets.isEmpty()) {
            doorOffsets.clear();
            setChanged();
        }
    }

    public void setRespawnPos(@Nullable BlockPos absolutePos) {
        BlockPos newRespawnOffset = absolutePos == null ? null : absolutePos.subtract(worldPosition);
        if (!Objects.equals(respawnOffset, newRespawnOffset)) {
            this.respawnOffset = newRespawnOffset;
            setChanged();
        }
    }

    // ---- Runtime operations (called by PC-3 methods + Phase E controller) ----
    // Package-private so only same-package code can flip activated/cleared flags.

    void markActivated() {
        activated = true;
        setChanged();
    }

    void markCleared() {
        cleared = true;
        setChanged();
    }

    void clearRuntimeState() {
        activated = false;
        cleared = false;
        aliveMobs.clear();
        setChanged();
    }

    void trackSpawnedMob(UUID uuid) {
        aliveMobs.add(uuid);
        setChanged();
    }

    void untrackSpawnedMob(UUID uuid) {
        aliveMobs.remove(uuid);
        setChanged();
    }

    /**
     * Spawn this room's mobs, applying the tier's health multiplier.
     *
     * <p>For each {@code BlockPos} in {@link #spawnerOffsets}:
     * <ul>
     *   <li>If the block entity at that position is a legacy {@code MobSpawnerBlockEntity},
     *       call {@code spawnSingleScaled(world, tier.healthMultiplier())} on it.</li>
     *   <li>If it's a legacy {@code DungeonBossSpawnerBlockEntity}, same call on that type.</li>
     *   <li>Otherwise, log a warning and skip that position.</li>
     * </ul>
     *
     * <p>Sets {@code activated=true} regardless of how many mobs actually spawned.
     *
     * <p>If {@code activated} is already true, this is a no-op and returns 0 (defensive against
     * double-activation in case of a controller bug).
     *
     * @return the count of mobs that were spawned and tracked
     */
    public int activate(ServerLevel world, TierConfig tier) {
        if (activated) return 0;
        if (spawnerOffsets.isEmpty()) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {} has no linked spawners; activation deferred",
                worldPosition
            );
            return 0;
        }

        int spawned = 0;
        for (BlockPos absolutePos : getSpawnerPositions()) {
            BlockEntity be = world.getBlockEntity(absolutePos);
            if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity newMobSpawner) {
                for (LivingEntity entity : newMobSpawner.spawnScaled(world, tier.healthMultiplier())) {
                    trackSpawnedMob(entity.getUUID());
                    spawned++;
                }
            } else if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity newBossSpawner) {
                LivingEntity entity = newBossSpawner.spawnSingleScaled(world, tier.healthMultiplier());
                if (entity != null) {
                    trackSpawnedMob(entity.getUUID());
                    spawned++;
                }
            } else {
                ArenasLdMod.LOGGER.warn(
                    "RoomController at {}: linked position {} is not a spawner (got {})",
                    worldPosition, absolutePos, be == null ? "null" : be.getClass().getSimpleName());
            }
        }
        if (spawned <= 0) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {} linked {} spawners but spawned 0 mobs; activation deferred",
                worldPosition,
                spawnerOffsets.size()
            );
            return 0;
        }
        markActivated();
        return spawned;
    }

    /**
     * Despawn any alive mobs this room spawned, clear runtime state, close the door.
     * Idempotent: safe to call on a not-yet-activated or already-reset room.
     */
    public void reset(ServerLevel world) {
        for (UUID uuid : new ArrayList<>(aliveMobs)) {
            Entity entity = world.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
            ArenasLdMod.DUNGEON_MANAGER.unregisterMob(uuid);
        }
        clearRuntimeState();
        closeDoor(world);
    }

    /**
     * Open the room's door by setting the phase block's SOLID property to false.
     * No-op if no door is set, the door position isn't loaded, or the block at that position
     * isn't a PhaseBlock.
     */
    public void openDoor(ServerLevel world) {
        for (BlockPos doorOffset : doorOffsets) {
            setDoorSolid(world, worldPosition.offset(doorOffset), false);
        }
    }

    /**
     * Close all of the room's doors by setting their phase blocks' SOLID property to true.
     * Same no-op conditions as {@link #openDoor(ServerLevel)}, per door.
     */
    public void closeDoor(ServerLevel world) {
        for (BlockPos doorOffset : doorOffsets) {
            setDoorSolid(world, worldPosition.offset(doorOffset), true);
        }
    }

    private void setDoorSolid(ServerLevel world, BlockPos doorAbsolute, boolean solid) {
        if (!world.isLoaded(doorAbsolute)) return;
        BlockState state = world.getBlockState(doorAbsolute);
        if (!(state.getBlock() instanceof PhaseBlock)) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {}: door position {} is not a PhaseBlock (got {})",
                worldPosition, doorAbsolute, state.getBlock());
            return;
        }
        if (state.getValue(PhaseBlock.SOLID) != solid) {
            world.setBlock(doorAbsolute, state.setValue(PhaseBlock.SOLID, solid), 3);
            if (world.getBlockEntity(doorAbsolute) instanceof net.ledok.arenas_ld.block.entity.PhaseBlockEntity phaseBlock) {
                phaseBlock.propagateState(solid, new java.util.ArrayList<>());
            }
        }
    }

    /**
     * Drop any aliveMobs UUIDs whose entities are dead, removed, in another dimension, or unloaded.
     * Updates {@link #cleared} to true if this leaves {@code aliveMobs} empty (and the room was activated).
     *
     * <p>This is intended to be called once per tick by the controller (Phase E) during a run.
     * It does NOT open the door — that's the controller's job after observing {@code isCleared()}.
     */
    public void refreshAliveMobs(ServerLevel world) {
        Iterator<UUID> it = aliveMobs.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            UUID uuid = it.next();
            Entity entity = world.getEntity(uuid);
            if (entity == null || !entity.isAlive() || entity.isRemoved() || entity.level() != world) {
                it.remove();
                ArenasLdMod.DUNGEON_MANAGER.unregisterMob(uuid);
                changed = true;
            }
        }
        if (activated && !cleared && aliveMobs.isEmpty()) {
            markCleared();
            return;
        }
        if (changed) setChanged();
    }

    // ---- NBT ----

    // Internal serialization record; never exposed outside the class.
    private record State(
        List<BlockPos> spawnerOffsets,
        List<BlockPos> doorOffsets,
        Optional<BlockPos> legacyDoorOffset,
        Optional<BlockPos> respawnOffset,
        Set<UUID> aliveMobs,
        boolean activated,
        boolean cleared
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("spawnerOffsets").forGetter(State::spawnerOffsets),
            BlockPos.CODEC.listOf().optionalFieldOf("doorOffsets", List.of()).forGetter(State::doorOffsets),
            BlockPos.CODEC.optionalFieldOf("doorOffset").forGetter(State::legacyDoorOffset),
            BlockPos.CODEC.optionalFieldOf("respawnOffset").forGetter(State::respawnOffset),
            UUIDUtil.CODEC.listOf()
                .xmap((List<UUID> list) -> (Set<UUID>) new HashSet<>(list),
                      (Set<UUID> set) -> new ArrayList<>(set))
                .fieldOf("aliveMobs").forGetter(State::aliveMobs),
            Codec.BOOL.fieldOf("activated").forGetter(State::activated),
            Codec.BOOL.fieldOf("cleared").forGetter(State::cleared)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        State state = new State(spawnerOffsets, doorOffsets, Optional.empty(),
            Optional.ofNullable(respawnOffset), aliveMobs, activated, cleared);
        State.CODEC.encodeStart(NbtOps.INSTANCE, state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                "Failed to save RoomController at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error(
                    "Failed to load RoomController at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    spawnerOffsets.clear();
                    spawnerOffsets.addAll(state.spawnerOffsets());
                    doorOffsets.clear();
                    doorOffsets.addAll(state.doorOffsets());
                    // Legacy migration: a single pre-list door offset folds into the list.
                    state.legacyDoorOffset().ifPresent(legacy -> {
                        if (!doorOffsets.contains(legacy)) {
                            doorOffsets.add(legacy);
                        }
                    });
                    respawnOffset = state.respawnOffset().orElse(null);
                    aliveMobs.clear();
                    aliveMobs.addAll(state.aliveMobs());
                    activated = state.activated();
                    cleared = state.cleared();
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
        return Component.translatable("block.arenas_ld.room_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new RoomControllerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public RoomControllerData getScreenOpeningData(ServerPlayer player) {
        return new RoomControllerData(worldPosition, getSpawnerPositions(), getDoorPositions());
    }
}
