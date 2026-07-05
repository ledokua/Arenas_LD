package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
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
    /** Entrance doors: PhaseBlocks players cross to enter this room. A block shared with another
     *  room's exit door defines a graph edge (that room → this room) for branching dungeons. */
    private final List<BlockPos> entranceOffsets = new ArrayList<>();
    /** Single respawn point for this room, relative to the controller. Players downed while this room is active respawn here. */
    @Nullable private BlockPos respawnOffset = null;
    private final Set<UUID> aliveMobs = new HashSet<>();
    /** Subset of {@link #aliveMobs} spawned by a linked boss spawner. When this room has any and
     *  they've all died, any remaining adds are discarded instead of requiring them to be killed too. */
    private final Set<UUID> bossMobs = new HashSet<>();
    private static final int NEXT_WAVE_DELAY_TICKS = 60; // 3 s between waves
    /** Sorted distinct wave numbers of linked spawners, computed at activation. Empty = legacy single-wave. */
    private final List<Integer> waveNumbers = new ArrayList<>();
    private int currentWaveIndex = 0;
    private int nextWaveDelayTicks = -1; // -1 = no countdown running
    /** True while the current wave has a boss spawn; when all of the wave's bosses die,
     *  its remaining adds are discarded instead of requiring them to be killed too. */
    private boolean bossInCurrentWave = false;
    /** Set for one poll when a between-wave countdown begins, so the lifecycle can telegraph
     *  the next wave's spawn positions to clients. Transient — not persisted. */
    private boolean waveCountdownJustStarted = false;
    private boolean activated = false;
    private boolean cleared = false;
    private String roomName = "";
    /** Reward granted to each eligible player when this room is cleared. EMPTY = nothing. */
    private RoomRewardConfig roomReward = RoomRewardConfig.EMPTY;

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

    public List<BlockPos> getEntrancePositions() {
        List<BlockPos> absolute = new ArrayList<>(entranceOffsets.size());
        for (BlockPos entranceOffset : entranceOffsets) {
            absolute.add(worldPosition.offset(entranceOffset));
        }
        return Collections.unmodifiableList(absolute);
    }

    public List<BlockPos> getEntranceOffsets() {
        return Collections.unmodifiableList(entranceOffsets);
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

    /** 1-based number of the wave currently in progress, for display. Always 1 for legacy/single-wave rooms. */
    public int getWaveDisplay() {
        return waveNumbers.isEmpty() ? 1 : Math.min(currentWaveIndex + 1, waveNumbers.size());
    }

    /** Total number of waves this room activated with. 1 for legacy/single-wave rooms. */
    public int getTotalWaves() {
        return Math.max(1, waveNumbers.size());
    }

    private boolean isOnFinalWave() {
        return currentWaveIndex >= waveNumbers.size() - 1;
    }

    /** Designer-facing name for this room, used for the ARMED hint and the DBS rooms list. May be blank. */
    public String getRoomName() {
        return roomName;
    }

    // ---- Admin / Linker operations ----

    /** setChanged() only marks the chunk dirty for saving; linker/admin edits also need to reach
     *  tracking clients immediately so SelectionOverlayRenderer reflects them without a chunk re-track. */
    private void markDirtyAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setRoomName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
        }
        if (!this.roomName.equals(trimmed)) {
            this.roomName = trimmed;
            markDirtyAndSync();
        }
    }

    public RoomRewardConfig getRoomReward() {
        return roomReward;
    }

    public void setRoomReward(RoomRewardConfig reward) {
        RoomRewardConfig sanitized = reward == null ? RoomRewardConfig.EMPTY : reward;
        if (!this.roomReward.equals(sanitized)) {
            this.roomReward = sanitized;
            markDirtyAndSync();
        }
    }

    /** Returns true if the spawner was added (false if already present). */
    public boolean addSpawner(BlockPos absolutePos) {
        BlockPos spawnerOffset = absolutePos.subtract(worldPosition);
        if (spawnerOffsets.contains(spawnerOffset)) {
            return false;
        }
        spawnerOffsets.add(spawnerOffset);
        markDirtyAndSync();
        return true;
    }

    /** Returns true if the spawner was removed (false if not present). */
    public boolean removeSpawner(BlockPos absolutePos) {
        BlockPos spawnerOffset = absolutePos.subtract(worldPosition);
        boolean removed = spawnerOffsets.remove(spawnerOffset);
        if (removed) {
            markDirtyAndSync();
        }
        return removed;
    }

    public void clearSpawners() {
        if (!spawnerOffsets.isEmpty()) {
            spawnerOffsets.clear();
            markDirtyAndSync();
        }
    }

    /** Returns true if the door was added (false if already present). */
    public boolean addDoor(BlockPos absolutePos) {
        BlockPos doorOffset = absolutePos.subtract(worldPosition);
        if (doorOffsets.contains(doorOffset)) {
            return false;
        }
        doorOffsets.add(doorOffset);
        markDirtyAndSync();
        return true;
    }

    /** Returns true if the door was removed (false if not present). */
    public boolean removeDoor(BlockPos absolutePos) {
        BlockPos doorOffset = absolutePos.subtract(worldPosition);
        boolean removed = doorOffsets.remove(doorOffset);
        if (removed) {
            markDirtyAndSync();
        }
        return removed;
    }

    public void clearDoors() {
        if (!doorOffsets.isEmpty()) {
            doorOffsets.clear();
            markDirtyAndSync();
        }
    }

    /** Returns true if the entrance door was added (false if already present). */
    public boolean addEntranceDoor(BlockPos absolutePos) {
        BlockPos entranceOffset = absolutePos.subtract(worldPosition);
        if (entranceOffsets.contains(entranceOffset)) {
            return false;
        }
        entranceOffsets.add(entranceOffset);
        markDirtyAndSync();
        return true;
    }

    /** Returns true if the entrance door was removed (false if not present). */
    public boolean removeEntranceDoor(BlockPos absolutePos) {
        BlockPos entranceOffset = absolutePos.subtract(worldPosition);
        boolean removed = entranceOffsets.remove(entranceOffset);
        if (removed) {
            markDirtyAndSync();
        }
        return removed;
    }

    public void clearEntranceDoors() {
        if (!entranceOffsets.isEmpty()) {
            entranceOffsets.clear();
            markDirtyAndSync();
        }
    }

    public void setRespawnPos(@Nullable BlockPos absolutePos) {
        BlockPos newRespawnOffset = absolutePos == null ? null : absolutePos.subtract(worldPosition);
        if (!Objects.equals(respawnOffset, newRespawnOffset)) {
            this.respawnOffset = newRespawnOffset;
            markDirtyAndSync();
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
        bossMobs.clear();
        waveNumbers.clear();
        currentWaveIndex = 0;
        nextWaveDelayTicks = -1;
        bossInCurrentWave = false;
        setChanged();
    }

    void trackSpawnedMob(UUID uuid) {
        aliveMobs.add(uuid);
        setChanged();
    }

    /** Like {@link #trackSpawnedMob(UUID)}, but also marks the mob as a boss for {@link #refreshAliveMobs}. */
    void trackBossMob(UUID uuid) {
        aliveMobs.add(uuid);
        bossMobs.add(uuid);
        setChanged();
    }

    void untrackSpawnedMob(UUID uuid) {
        aliveMobs.remove(uuid);
        bossMobs.remove(uuid);
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
        return activate(world, tier, 1.0);
    }

    /**
     * Like {@link #activate(ServerLevel, TierConfig)}, but the tier's health multiplier is further
     * multiplied by {@code partyHealthMultiplier} (per-player HP scaling from the controller).
     */
    public int activate(ServerLevel world, TierConfig tier, double partyHealthMultiplier) {
        if (activated) return 0;
        if (spawnerOffsets.isEmpty()) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {} has no linked spawners; activation deferred",
                worldPosition
            );
            return 0;
        }

        List<Integer> waves = collectWaveNumbers(world);
        if (waves.isEmpty()) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {} has no linked spawners; activation deferred",
                worldPosition
            );
            return 0;
        }

        double healthMultiplier = tier.healthMultiplier() * partyHealthMultiplier;
        // Walk forward past waves whose spawners are all broken so a bad first wave can't soft-lock activation.
        for (int index = 0; index < waves.size(); index++) {
            int spawned = spawnWave(world, healthMultiplier, waves.get(index), true);
            if (spawned > 0) {
                waveNumbers.clear();
                waveNumbers.addAll(waves);
                currentWaveIndex = index;
                nextWaveDelayTicks = -1;
                markActivated();
                return spawned;
            }
        }
        ArenasLdMod.LOGGER.warn(
            "RoomController at {} linked {} spawners but spawned 0 mobs; activation deferred",
            worldPosition,
            spawnerOffsets.size()
        );
        return 0;
    }

    /**
     * Like {@link #activate(ServerLevel, TierConfig, double)}, but for lock-in rooms: a room that
     * yields 0 mobs (no spawners, all broken) is marked activated + cleared instead of deferring,
     * because under lock-in a deferred room is a cage with no key.
     */
    public int activateOrAutoClear(ServerLevel world, TierConfig tier, double partyHealthMultiplier) {
        int spawned = activate(world, tier, partyHealthMultiplier);
        if (spawned == 0 && !activated) {
            markActivated();
            markCleared();
            ArenasLdMod.LOGGER.warn(
                "RoomController at {} auto-cleared under lock-in (0 mobs spawned)", worldPosition);
        }
        return spawned;
    }

    /** Sorted distinct wave numbers across all linked spawners; empty if no linked position is a spawner. */
    private List<Integer> collectWaveNumbers(ServerLevel world) {
        Set<Integer> waves = new java.util.TreeSet<>();
        for (BlockPos absolutePos : getSpawnerPositions()) {
            BlockEntity be = world.getBlockEntity(absolutePos);
            if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity mobSpawner) {
                waves.add(Math.max(1, mobSpawner.getEntityDefinition().wave()));
            } else if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity bossSpawner) {
                waves.add(Math.max(1, bossSpawner.getEntityDefinition().wave()));
            }
        }
        return new ArrayList<>(waves);
    }

    /**
     * Spawn all mobs of linked spawners configured for {@code waveNumber} and track them.
     * {@code logInvalid} limits the "not a spawner" warning to activation so it isn't
     * re-logged for every later wave.
     *
     * @return the count of mobs that were spawned and tracked
     */
    private int spawnWave(ServerLevel world, double healthMultiplier, int waveNumber, boolean logInvalid) {
        bossInCurrentWave = false;
        int spawned = 0;
        for (BlockPos absolutePos : getSpawnerPositions()) {
            BlockEntity be = world.getBlockEntity(absolutePos);
            if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity newMobSpawner) {
                if (Math.max(1, newMobSpawner.getEntityDefinition().wave()) != waveNumber) continue;
                for (LivingEntity entity : newMobSpawner.spawnScaled(world, healthMultiplier)) {
                    trackSpawnedMob(entity.getUUID());
                    spawned++;
                }
            } else if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity newBossSpawner) {
                if (Math.max(1, newBossSpawner.getEntityDefinition().wave()) != waveNumber) continue;
                LivingEntity entity = newBossSpawner.spawnSingleScaled(world, healthMultiplier);
                if (entity != null) {
                    trackBossMob(entity.getUUID());
                    bossInCurrentWave = true;
                    spawned++;
                }
            } else if (logInvalid) {
                ArenasLdMod.LOGGER.warn(
                    "RoomController at {}: linked position {} is not a spawner (got {})",
                    worldPosition, absolutePos, be == null ? "null" : be.getClass().getSimpleName());
            }
        }
        return spawned;
    }

    /**
     * Advance the wave state machine: once the current wave is dead (and it isn't the last),
     * count down {@link #NEXT_WAVE_DELAY_TICKS}, then spawn the next non-empty wave. If every
     * remaining wave spawns 0 mobs (broken spawners), the room is marked cleared instead of
     * soft-locking. Called once per tick by the run lifecycle while the room is active.
     *
     * @return UUIDs of newly spawned mobs, for run registration; empty when nothing spawned
     */
    public List<UUID> tickWaveProgression(ServerLevel world, TierConfig tier, double partyHealthMultiplier) {
        if (!activated || cleared || !aliveMobs.isEmpty() || isOnFinalWave()) {
            nextWaveDelayTicks = -1;
            return List.of();
        }
        if (nextWaveDelayTicks < 0) {
            nextWaveDelayTicks = NEXT_WAVE_DELAY_TICKS;
            waveCountdownJustStarted = true;
            setChanged();
            return List.of();
        }
        if (--nextWaveDelayTicks > 0) {
            return List.of();
        }
        nextWaveDelayTicks = -1;
        double healthMultiplier = tier.healthMultiplier() * partyHealthMultiplier;
        while (!isOnFinalWave()) {
            currentWaveIndex++;
            if (spawnWave(world, healthMultiplier, waveNumbers.get(currentWaveIndex), false) > 0) {
                setChanged();
                return List.copyOf(aliveMobs); // was empty before spawn → exactly the new mobs
            }
        }
        markCleared();
        return List.of();
    }

    /** True exactly once after a between-wave countdown starts; reading it resets the flag. */
    public boolean pollWaveCountdownStarted() {
        boolean started = waveCountdownJustStarted;
        waveCountdownJustStarted = false;
        return started;
    }

    /**
     * Configured spawn positions of the given wave, for client-side telegraphing. Approximation:
     * every spawn offset of matching mob spawners (or the spawner itself when none are set) plus
     * the boss spawn position of matching boss spawners.
     */
    public List<BlockPos> getWaveSpawnPositions(ServerLevel world, int waveNumber) {
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos absolutePos : getSpawnerPositions()) {
            BlockEntity be = world.getBlockEntity(absolutePos);
            if (be instanceof MobSpawnerBlockEntity mobSpawner) {
                if (Math.max(1, mobSpawner.getEntityDefinition().wave()) != waveNumber) continue;
                List<BlockPos> offsets = mobSpawner.getEntityDefinition().spawnOffsets();
                if (offsets.isEmpty()) {
                    positions.add(absolutePos.above());
                } else {
                    for (BlockPos offset : offsets) {
                        positions.add(absolutePos.offset(offset));
                    }
                }
            } else if (be instanceof DungeonBossSpawnerBlockEntity bossSpawner) {
                if (Math.max(1, bossSpawner.getEntityDefinition().wave()) != waveNumber) continue;
                positions.add(BlockPos.containing(net.ledok.arenas_ld.util.EntityEquipmentHelper
                    .resolveBossSpawnPos(absolutePos, bossSpawner.getEntityDefinition().spawnOffsets())));
            }
        }
        return positions;
    }

    /** Spawn positions of the first wave that will actually fire on activation. */
    public List<BlockPos> getFirstWaveSpawnPositions(ServerLevel world) {
        List<Integer> waves = collectWaveNumbers(world);
        return waves.isEmpty() ? List.of() : getWaveSpawnPositions(world, waves.get(0));
    }

    /** Spawn positions of the next configured wave, or empty when on the final wave. */
    public List<BlockPos> getUpcomingWaveSpawnPositions(ServerLevel world) {
        if (waveNumbers.isEmpty() || isOnFinalWave()) return List.of();
        return getWaveSpawnPositions(world, waveNumbers.get(currentWaveIndex + 1));
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
        closeAllDoors(world);
    }

    /**
     * Open the room's door by setting the phase block's SOLID property to false.
     * No-op if no door is set, the door position isn't loaded, or the block at that position
     * isn't a PhaseBlock. Legacy (linear) path: opens fully invisible.
     */
    public void openDoor(ServerLevel world) {
        for (BlockPos doorOffset : doorOffsets) {
            setDoorState(world, worldPosition.offset(doorOffset), false, false);
        }
    }

    /**
     * Close all of the room's doors by setting their phase blocks' SOLID property to true.
     * Same no-op conditions as {@link #openDoor(ServerLevel)}, per door.
     */
    public void closeDoor(ServerLevel world) {
        for (BlockPos doorOffset : doorOffsets) {
            setDoorState(world, worldPosition.offset(doorOffset), true, false);
        }
    }

    /** Open this room's entrance doors ARMED (orange): passable, but this room isn't cleared yet. */
    public void openEntranceDoorsArmed(ServerLevel world) {
        for (BlockPos entranceOffset : entranceOffsets) {
            setDoorState(world, worldPosition.offset(entranceOffset), false, true);
        }
    }

    /**
     * Open every door of this room when it clears: entrances turn fully invisible (both sides
     * beaten), exits open ARMED (orange) because the rooms beyond aren't cleared yet.
     */
    public void openAllDoors(ServerLevel world) {
        for (BlockPos doorOffset : doorOffsets) {
            setDoorState(world, worldPosition.offset(doorOffset), false, true);
        }
        for (BlockPos entranceOffset : entranceOffsets) {
            setDoorState(world, worldPosition.offset(entranceOffset), false, false);
        }
    }

    /** Close every door of this room — exits and entrances. Called on lock-in and reset. */
    public void closeAllDoors(ServerLevel world) {
        closeDoor(world);
        for (BlockPos entranceOffset : entranceOffsets) {
            setDoorState(world, worldPosition.offset(entranceOffset), true, false);
        }
    }

    private void setDoorState(ServerLevel world, BlockPos doorAbsolute, boolean solid, boolean armed) {
        if (!world.isLoaded(doorAbsolute)) return;
        BlockState state = world.getBlockState(doorAbsolute);
        if (!(state.getBlock() instanceof PhaseBlock)) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {}: door position {} is not a PhaseBlock (got {})",
                worldPosition, doorAbsolute, state.getBlock());
            return;
        }
        if (state.getValue(PhaseBlock.SOLID) != solid || state.getValue(PhaseBlock.ARMED) != armed) {
            world.setBlock(doorAbsolute,
                state.setValue(PhaseBlock.SOLID, solid).setValue(PhaseBlock.ARMED, armed), 3);
            if (world.getBlockEntity(doorAbsolute) instanceof net.ledok.arenas_ld.block.entity.PhaseBlockEntity phaseBlock) {
                phaseBlock.propagateState(solid, armed, new java.util.ArrayList<>());
            }
        }
    }

    /**
     * Drop any aliveMobs UUIDs whose entities are dead, removed, in another dimension, or unloaded.
     * Updates {@link #cleared} to true if this leaves {@code aliveMobs} empty (and the room was activated).
     *
     * <p>If this room has a linked boss spawner and every boss mob it tracked is now gone, any
     * remaining adds are discarded on the spot — a boss fight ends the instant the boss dies, the
     * adds don't need to be hunted down individually.
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
                bossMobs.remove(uuid);
                ArenasLdMod.DUNGEON_MANAGER.unregisterMob(uuid);
                changed = true;
            }
        }

        // All bosses of the current wave died → discard the wave's remaining adds on the spot.
        if (bossInCurrentWave && bossMobs.isEmpty() && !aliveMobs.isEmpty()) {
            for (UUID uuid : new ArrayList<>(aliveMobs)) {
                Entity entity = world.getEntity(uuid);
                if (entity != null && entity.isAlive()) {
                    entity.discard();
                }
                ArenasLdMod.DUNGEON_MANAGER.unregisterMob(uuid);
            }
            aliveMobs.clear();
            bossInCurrentWave = false;
            changed = true;
        }

        if (activated && !cleared && aliveMobs.isEmpty() && isOnFinalWave()) {
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
        Optional<BlockPos> respawnOffset,
        Set<UUID> aliveMobs,
        Set<UUID> bossMobs,
        boolean activated,
        boolean cleared,
        String roomName,
        List<Integer> waveNumbers,
        int currentWaveIndex,
        int nextWaveDelayTicks,
        boolean bossInCurrentWave,
        RoomRewardConfig roomReward,
        List<BlockPos> entranceOffsets
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("spawnerOffsets").forGetter(State::spawnerOffsets),
            BlockPos.CODEC.listOf().optionalFieldOf("doorOffsets", List.of()).forGetter(State::doorOffsets),
            BlockPos.CODEC.optionalFieldOf("respawnOffset").forGetter(State::respawnOffset),
            UUIDUtil.CODEC.listOf()
                .xmap((List<UUID> list) -> (Set<UUID>) new HashSet<>(list),
                      (Set<UUID> set) -> new ArrayList<>(set))
                .fieldOf("aliveMobs").forGetter(State::aliveMobs),
            UUIDUtil.CODEC.listOf()
                .xmap((List<UUID> list) -> (Set<UUID>) new HashSet<>(list),
                      (Set<UUID> set) -> new ArrayList<>(set))
                .optionalFieldOf("bossMobs", Set.of()).forGetter(State::bossMobs),
            Codec.BOOL.fieldOf("activated").forGetter(State::activated),
            Codec.BOOL.fieldOf("cleared").forGetter(State::cleared),
            Codec.STRING.optionalFieldOf("roomName", "").forGetter(State::roomName),
            Codec.INT.listOf().optionalFieldOf("waveNumbers", List.of()).forGetter(State::waveNumbers),
            Codec.INT.optionalFieldOf("currentWaveIndex", 0).forGetter(State::currentWaveIndex),
            Codec.INT.optionalFieldOf("nextWaveDelayTicks", -1).forGetter(State::nextWaveDelayTicks),
            Codec.BOOL.optionalFieldOf("bossInCurrentWave", false).forGetter(State::bossInCurrentWave),
            RoomRewardConfig.CODEC.optionalFieldOf("roomReward", RoomRewardConfig.EMPTY).forGetter(State::roomReward),
            BlockPos.CODEC.listOf().optionalFieldOf("entranceOffsets", List.of()).forGetter(State::entranceOffsets)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        State state = new State(spawnerOffsets, doorOffsets,
            Optional.ofNullable(respawnOffset), aliveMobs, bossMobs, activated, cleared, roomName,
            waveNumbers, currentWaveIndex, nextWaveDelayTicks, bossInCurrentWave, roomReward, entranceOffsets);
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
                    respawnOffset = state.respawnOffset().orElse(null);
                    aliveMobs.clear();
                    aliveMobs.addAll(state.aliveMobs());
                    bossMobs.clear();
                    bossMobs.addAll(state.bossMobs());
                    activated = state.activated();
                    cleared = state.cleared();
                    roomName = state.roomName();
                    waveNumbers.clear();
                    waveNumbers.addAll(state.waveNumbers());
                    currentWaveIndex = state.currentWaveIndex();
                    nextWaveDelayTicks = state.nextWaveDelayTicks();
                    bossInCurrentWave = state.bossInCurrentWave();
                    roomReward = state.roomReward();
                    entranceOffsets.clear();
                    entranceOffsets.addAll(state.entranceOffsets());
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
        List<RoomControllerData.SpawnerEntry> entries = new ArrayList<>();
        for (BlockPos absolutePos : getSpawnerPositions()) {
            BlockEntity be = level != null ? level.getBlockEntity(absolutePos) : null;
            if (be instanceof MobSpawnerBlockEntity mobSpawner) {
                entries.add(new RoomControllerData.SpawnerEntry(
                    absolutePos, Math.max(1, mobSpawner.getEntityDefinition().wave()), false, false));
            } else if (be instanceof DungeonBossSpawnerBlockEntity bossSpawner) {
                entries.add(new RoomControllerData.SpawnerEntry(
                    absolutePos, Math.max(1, bossSpawner.getEntityDefinition().wave()), true, false));
            } else {
                // Linked position without a spawner BE (broken link or unloaded chunk).
                entries.add(new RoomControllerData.SpawnerEntry(absolutePos, 1, false, true));
            }
        }
        return new RoomControllerData(worldPosition, entries, getDoorPositions(), getEntrancePositions(),
            roomName, Optional.ofNullable(getRespawnPos()), roomReward, enumerateLootTables());
    }

    /** Sorted loot table ids for the reward screen's autocomplete; empty when called client-side. */
    private List<String> enumerateLootTables() {
        if (!(level instanceof ServerLevel serverLevel) || serverLevel.getServer() == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try {
            serverLevel.getServer().reloadableRegistries().lookup()
                .lookup(net.minecraft.core.registries.Registries.LOOT_TABLE)
                .ifPresent(getter -> {
                    if (getter instanceof net.minecraft.core.HolderLookup.RegistryLookup<?> reg) {
                        reg.listElementIds().forEach(key -> ids.add(key.location().toString()));
                    }
                });
        } catch (Exception ignored) {
        }
        ids.sort(null);
        return ids;
    }
}
