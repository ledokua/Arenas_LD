package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.block.PhaseBlock;
import net.ledok.arenas_ld.dungeon.room.RoomObjectiveConfig;
import net.ledok.arenas_ld.dungeon.room.RoomRewardConfig;
import net.ledok.arenas_ld.dungeon.run.RoomGraph;
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
import net.ledok.arenas_ld.platform.ExtendedMenuProvider;

public class RoomControllerBlockEntity extends BlockEntity implements ExtendedMenuProvider<RoomControllerData> {

    // ---- Fields ----

    private final List<BlockPos> spawnerOffsets = new ArrayList<>();
    /** Doors: PhaseBlock groups by canonical anchor, direction-free. A door linked by this room
     *  and another room connects them in the {@code RoomGraph}; a door linked only here is a
     *  boundary door (the start room's spawn-side entrance, or a dead end). Legacy saves'
     *  separate entrance list is merged into this on load. */
    private final List<BlockPos> doorOffsets = new ArrayList<>();
    /** Respawn points for this room, relative to the controller. Rooms can be entered from any
     *  door, so several may be placed — entry and downed-player respawns pick the closest one. */
    private final List<BlockPos> respawnOffsets = new ArrayList<>();
    private final Set<UUID> aliveMobs = new HashSet<>();
    /** Subset of {@link #aliveMobs} spawned by a linked boss spawner. When this room has any and
     *  they've all died, any remaining adds are discarded instead of requiring them to be killed too. */
    private final Set<UUID> bossMobs = new HashSet<>();
    private static final int NEXT_WAVE_DELAY_TICKS = 60; // 3 s between waves
    /** Sorted distinct wave numbers of linked spawners, computed at activation. Empty = legacy single-wave. */
    private final List<Integer> waveNumbers = new ArrayList<>();
    private int currentWaveIndex = 0;
    private int nextWaveDelayTicks = -1; // -1 = no countdown running
    /** How many mobs the current wave spawned, for the low-wave glow threshold. 0 = no wave yet. */
    private int currentWaveSize = 0;
    /** Below this fraction of {@link #currentWaveSize} alive, the stragglers get the glow. */
    private static final double GLOW_FRACTION = 0.15;
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
    /** How this room clears. DEFAULT (kill everything) matches pre-objective behavior. */
    private RoomObjectiveConfig objective = RoomObjectiveConfig.DEFAULT;
    /** SURVIVE countdown in ticks, set at activation. -1 = no timer running. */
    private int surviveTicksRemaining = -1;
    /** PROTECT target spawned at activation; null when none is alive/tracked. */
    @Nullable private UUID protectTargetUuid = null;
    /** Set for one poll when the protect target is found dead, so the lifecycle can fail the run.
     *  Transient — not persisted (the persisted UUID re-detects death after a restart). */
    private boolean protectTargetDied = false;

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

    public List<BlockPos> getRespawnPointOffsets() {
        return Collections.unmodifiableList(respawnOffsets);
    }

    public List<BlockPos> getRespawnPositions() {
        List<BlockPos> absolute = new ArrayList<>(respawnOffsets.size());
        for (BlockPos offset : respawnOffsets) {
            absolute.add(worldPosition.offset(offset));
        }
        return Collections.unmodifiableList(absolute);
    }

    /** The respawn position closest to {@code near} (squared block distance), or null if none set. */
    @Nullable
    public BlockPos closestRespawnPos(BlockPos near) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos offset : respawnOffsets) {
            BlockPos absolute = worldPosition.offset(offset);
            double dist = absolute.distSqr(near);
            if (dist < bestDist) {
                bestDist = dist;
                best = absolute;
            }
        }
        return best;
    }

    public Set<UUID> getAliveMobs() {
        return Collections.unmodifiableSet(aliveMobs);
    }

    /** Live boss-flagged mobs of the current wave (empty when the wave has none). */
    public Set<UUID> getBossMobs() {
        return Collections.unmodifiableSet(bossMobs);
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

    /** SURVIVE countdown in ticks, or -1 when no timer is running. */
    public int getSurviveTicksRemaining() {
        return surviveTicksRemaining;
    }

    /** True while a SURVIVE timer runs: waves loop and mob deaths don't clear the room. */
    private boolean isSurviveLooping() {
        return objective.type() == RoomObjectiveConfig.Type.SURVIVE && surviveTicksRemaining > 0;
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

    public RoomObjectiveConfig getObjective() {
        return objective;
    }

    public void setObjective(RoomObjectiveConfig newObjective) {
        RoomObjectiveConfig sanitized = newObjective == null ? RoomObjectiveConfig.DEFAULT : newObjective;
        if (!this.objective.equals(sanitized)) {
            this.objective = sanitized;
            markDirtyAndSync();
        }
    }

    /** Absolute PROTECT target spawn position, or {@code null} if none is configured. */
    @Nullable
    public BlockPos getProtectPos() {
        return objective.protectOffset().map(worldPosition::offset).orElse(null);
    }

    /** Sets (or clears, with {@code null}) the PROTECT target spawn position from an absolute position. */
    public void setProtectPos(@Nullable BlockPos absolutePos) {
        setObjective(objective.withProtectOffset(
            Optional.ofNullable(absolutePos).map(pos -> pos.subtract(worldPosition))));
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

    /** Toggles the spawner link, like doors: returns true when now linked, false when unlinked. */
    public boolean toggleSpawner(BlockPos absolutePos) {
        if (removeSpawner(absolutePos)) {
            return false;
        }
        addSpawner(absolutePos);
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

    /** Toggles the exit door containing this phase block; returns true when it is now linked. */
    public boolean toggleDoor(BlockPos absolutePos) {
        return toggleDoorAnchor(doorOffsets, absolutePos);
    }

    /**
     * Doors are stored by their group's canonical anchor, not the block the builder clicked:
     * the clicked phase block is flood-filled to its whole connected door and
     * {@link RoomGraph#canonicalAnchor} picks the representative. Clicking any block of the door —
     * from this room or the one on the other side — therefore resolves to the same stored position.
     * Clicking a block of an already-linked door unlinks the whole door (entries from older saves
     * that stored the clicked block instead of the anchor are matched through the group too).
     */
    private boolean toggleDoorAnchor(List<BlockPos> offsets, BlockPos absolutePos) {
        Set<BlockPos> group = level != null
            ? RoomGraph.expandDoorGroup(level, absolutePos)
            : Set.of(absolutePos);
        boolean wasLinked = offsets.removeIf(offset -> group.contains(worldPosition.offset(offset)));
        if (!wasLinked) {
            offsets.add(RoomGraph.canonicalAnchor(group).subtract(worldPosition));
        }
        markDirtyAndSync();
        return !wasLinked;
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

    /** Returns true if the respawn point was added (false if already present). */
    public boolean addRespawnPointOffset(BlockPos offset) {
        if (respawnOffsets.contains(offset)) {
            return false;
        }
        respawnOffsets.add(offset);
        markDirtyAndSync();
        return true;
    }

    /** Returns true if the respawn point was removed (false if not present). */
    public boolean removeRespawnPointOffset(BlockPos offset) {
        boolean removed = respawnOffsets.remove(offset);
        if (removed) {
            markDirtyAndSync();
        }
        return removed;
    }

    public void clearRespawnPoints() {
        if (!respawnOffsets.isEmpty()) {
            respawnOffsets.clear();
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
        currentWaveSize = 0;
        bossInCurrentWave = false;
        surviveTicksRemaining = -1;
        protectTargetUuid = null;
        protectTargetDied = false;
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
            int spawned = spawnWave(world, healthMultiplier, waves.get(index), true).size();
            if (spawned > 0) {
                waveNumbers.clear();
                waveNumbers.addAll(waves);
                currentWaveIndex = index;
                nextWaveDelayTicks = -1;
                if (objective.type() == RoomObjectiveConfig.Type.SURVIVE) {
                    surviveTicksRemaining = Math.max(1, objective.surviveSeconds()) * 20;
                }
                if (objective.type() == RoomObjectiveConfig.Type.PROTECT) {
                    spawnProtectTarget(world);
                }
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
     * @return the UUIDs of the mobs that were spawned and tracked
     */
    private List<UUID> spawnWave(ServerLevel world, double healthMultiplier, int waveNumber, boolean logInvalid) {
        bossInCurrentWave = false;
        List<UUID> spawned = new ArrayList<>();
        for (BlockPos absolutePos : getSpawnerPositions()) {
            BlockEntity be = world.getBlockEntity(absolutePos);
            if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity newMobSpawner) {
                if (Math.max(1, newMobSpawner.getEntityDefinition().wave()) != waveNumber) continue;
                for (LivingEntity entity : newMobSpawner.spawnScaled(world, healthMultiplier)) {
                    trackSpawnedMob(entity.getUUID());
                    spawned.add(entity.getUUID());
                }
            } else if (be instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity newBossSpawner) {
                if (Math.max(1, newBossSpawner.getEntityDefinition().wave()) != waveNumber) continue;
                LivingEntity entity = newBossSpawner.spawnSingleScaled(world, healthMultiplier);
                if (entity != null) {
                    trackBossMob(entity.getUUID());
                    bossInCurrentWave = true;
                    spawned.add(entity.getUUID());
                }
            } else if (logInvalid) {
                ArenasLdMod.LOGGER.warn(
                    "RoomController at {}: linked position {} is not a spawner (got {})",
                    worldPosition, absolutePos, be == null ? "null" : be.getClass().getSimpleName());
            }
        }
        if (!spawned.isEmpty()) {
            currentWaveSize = spawned.size();
        }
        return spawned;
    }

    /**
     * When {@link #GLOW_FRACTION} or less of the current wave is left alive (10 mobs → the last
     * one), the stragglers get a permanent glow so the party can find them. Per wave — the next
     * wave's spawn resets the threshold. SURVIVE rooms never reach this (the timer clears them,
     * leftovers don't need hunting down).
     */
    private void tickLowWaveGlow(ServerLevel world) {
        if (aliveMobs.isEmpty() || currentWaveSize <= 0) {
            return;
        }
        int threshold = Math.max(1, (int) Math.floor(currentWaveSize * GLOW_FRACTION));
        if (aliveMobs.size() > threshold) {
            return;
        }
        for (UUID uuid : aliveMobs) {
            if (world.getEntity(uuid) instanceof LivingEntity living
                && !living.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING)) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.GLOWING,
                    net.minecraft.world.effect.MobEffectInstance.INFINITE_DURATION, 0, false, false));
            }
        }
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
        if (!activated || cleared) {
            nextWaveDelayTicks = -1;
            return List.of();
        }
        // SURVIVE runs on its own schedule: waves force-spawn at a fixed interval no matter what's
        // still alive (no AFKing behind a leftover mob), and the timer — not mob deaths — ends the room.
        if (surviveTicksRemaining > 0 && objective.type() == RoomObjectiveConfig.Type.SURVIVE) {
            nextWaveDelayTicks = -1;
            surviveTicksRemaining--;
            if (surviveTicksRemaining == 0) {
                discardAliveMobs(world);
                markCleared();
                return List.of();
            }
            return tickSurviveSchedule(world, tier, partyHealthMultiplier);
        }
        tickLowWaveGlow(world);
        if (!aliveMobs.isEmpty() || isOnFinalWave()) {
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
            if (!spawnWave(world, healthMultiplier, waveNumbers.get(currentWaveIndex), false).isEmpty()) {
                setChanged();
                return List.copyOf(aliveMobs); // was empty before spawn → exactly the new mobs
            }
        }
        markCleared();
        discardProtectTarget(world);
        return List.of();
    }

    /**
     * SURVIVE wave schedule, derived from the survive timer so no extra state needs persisting:
     * every {@code surviveWaveIntervalSeconds} of elapsed survive time the next wave (looping past
     * the last back to the first) spawns on top of whatever is still alive, telegraphed
     * {@link #NEXT_WAVE_DELAY_TICKS} in advance. Waves that spawn 0 (broken spawners) are skipped;
     * the timer keeps running either way — it alone clears the room.
     *
     * @return UUIDs of newly spawned mobs, for run registration
     */
    private List<UUID> tickSurviveSchedule(ServerLevel world, TierConfig tier, double partyHealthMultiplier) {
        if (waveNumbers.isEmpty()) {
            return List.of();
        }
        int intervalTicks = Math.max(1, objective.surviveWaveIntervalSeconds()) * 20;
        int elapsedTicks = Math.max(1, objective.surviveSeconds()) * 20 - surviveTicksRemaining;
        if (elapsedTicks <= 0) {
            return List.of();
        }
        if ((elapsedTicks + NEXT_WAVE_DELAY_TICKS) % intervalTicks == 0) {
            waveCountdownJustStarted = true;
        }
        if (elapsedTicks % intervalTicks != 0) {
            return List.of();
        }
        double healthMultiplier = tier.healthMultiplier() * partyHealthMultiplier;
        for (int attempts = 0; attempts < waveNumbers.size(); attempts++) {
            currentWaveIndex = (currentWaveIndex + 1) % waveNumbers.size();
            List<UUID> spawned = spawnWave(world, healthMultiplier, waveNumbers.get(currentWaveIndex), false);
            if (!spawned.isEmpty()) {
                setChanged();
                return spawned;
            }
        }
        return List.of();
    }

    /**
     * Spawns the PROTECT objective's target mob at the configured position. On a missing position
     * or invalid mob id the room falls back to plain kill-everything behavior (no target tracked),
     * so a config mistake can never brick a run — it just logs.
     */
    private void spawnProtectTarget(ServerLevel world) {
        BlockPos spawnPos = getProtectPos();
        if (spawnPos == null) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {}: PROTECT objective has no position set; falling back to kill-all", worldPosition);
            return;
        }
        Optional<net.minecraft.world.entity.EntityType<?>> type =
            net.minecraft.world.entity.EntityType.byString(objective.protectMobId());
        if (type.isEmpty()) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {}: invalid PROTECT mob id {}; falling back to kill-all",
                worldPosition, objective.protectMobId());
            return;
        }
        Entity created = type.get().create(world);
        if (!(created instanceof LivingEntity living)) {
            ArenasLdMod.LOGGER.warn(
                "RoomController at {}: PROTECT mob {} is not a LivingEntity; falling back to kill-all",
                worldPosition, objective.protectMobId());
            return;
        }
        // Deliberately NOT on the arenas_dungeon mob team (wave mobs must be able to hurt it);
        // the run lifecycle adds it to the party's no-friendly-fire team so players can't.
        net.ledok.arenas_ld.util.EntityEquipmentHelper.markRunMob(living);
        if (objective.protectStationary() && living instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
        }
        // Configured attributes are absolute — the target isn't scaled by tier or party size.
        net.ledok.arenas_ld.util.EntityEquipmentHelper.applyScaledAttributes(
            living, objective.protectAttributes(), world.registryAccess(), 1.0, 1.0, 1.0);
        living.heal(living.getMaxHealth());
        living.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
        if (world.addFreshEntity(living)) {
            protectTargetUuid = living.getUUID();
            protectTargetDied = false;
            setChanged();
        }
    }

    /** Removes the protect target from the world without failing the objective (room cleared / reset). */
    private void discardProtectTarget(ServerLevel world) {
        if (protectTargetUuid == null) {
            return;
        }
        Entity target = world.getEntity(protectTargetUuid);
        if (target != null && target.isAlive()) {
            target.discard();
        }
        protectTargetUuid = null;
        setChanged();
    }

    /** UUID of the alive protect target, or {@code null}. The lifecycle uses it for team assignment. */
    @Nullable
    public UUID getProtectTargetUuid() {
        return protectTargetUuid;
    }

    /** True exactly once after the protect target is found dead; reading it resets the flag. */
    public boolean pollProtectTargetDied() {
        boolean died = protectTargetDied;
        protectTargetDied = false;
        return died;
    }

    /** Protect target health as 0–100, or -1 when no target is alive/tracked. */
    public int getProtectTargetHealthPercent(ServerLevel world) {
        if (protectTargetUuid == null) {
            return -1;
        }
        return world.getEntity(protectTargetUuid) instanceof LivingEntity living && living.getMaxHealth() > 0.0F
            ? Math.clamp(Math.round(living.getHealth() * 100.0F / living.getMaxHealth()), 0, 100)
            : -1;
    }

    /** Discards every tracked mob and unregisters it from the run. */
    private void discardAliveMobs(ServerLevel world) {
        for (UUID uuid : new ArrayList<>(aliveMobs)) {
            Entity entity = world.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                entity.discard();
            }
            ArenasLdMod.DUNGEON_MANAGER.unregisterMob(uuid);
        }
        aliveMobs.clear();
        bossMobs.clear();
        bossInCurrentWave = false;
        setChanged();
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

    /** Spawn positions of the next configured wave, or empty when on the final wave (unless a survive loop wraps back to the first). */
    public List<BlockPos> getUpcomingWaveSpawnPositions(ServerLevel world) {
        if (waveNumbers.isEmpty()) return List.of();
        if (isOnFinalWave()) {
            return isSurviveLooping() ? getWaveSpawnPositions(world, waveNumbers.get(0)) : List.of();
        }
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
        discardProtectTarget(world);
        clearRuntimeState();
        closeAllDoors(world);
    }

    /**
     * Open all of the room's doors fully (invisible). Legacy (linear) path: doors there are
     * plain "opens on clear" exits with no graph semantics.
     */
    public void openDoor(ServerLevel world) {
        for (BlockPos doorOffset : doorOffsets) {
            setDoorState(world, worldPosition.offset(doorOffset), false, false);
        }
    }

    /** Close every door of this room. Called on lock-in and reset. */
    public void closeAllDoors(ServerLevel world) {
        for (BlockPos doorOffset : doorOffsets) {
            setDoorState(world, worldPosition.offset(doorOffset), true, false);
        }
    }

    /**
     * Set one door's state by any block of its group (branching lifecycle decides open/armed
     * per door from the graph: partner cleared → invisible, partner pending → armed orange).
     */
    public void setDoorGroupState(ServerLevel world, BlockPos doorAbsolute, boolean solid, boolean armed) {
        setDoorState(world, doorAbsolute, solid, armed);
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
        // PROTECT: notice the target dying before anything else this tick.
        LivingEntity protectTarget = null;
        if (protectTargetUuid != null && activated && !cleared) {
            Entity targetEntity = world.getEntity(protectTargetUuid);
            if (targetEntity instanceof LivingEntity livingTarget && livingTarget.isAlive() && !livingTarget.isRemoved()) {
                protectTarget = livingTarget;
            } else {
                protectTargetUuid = null;
                protectTargetDied = true;
                setChanged();
            }
        }

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
            } else if (protectTarget != null
                    && entity instanceof net.minecraft.world.entity.Mob mob
                    && (mob.getTarget() == null || !mob.getTarget().isAlive())) {
                // Idle wave mobs beeline the protect target; player aggro (revenge) overrides until it drops.
                mob.setTarget(protectTarget);
            }
        }

        // All bosses of the current wave died → discard the wave's remaining adds on the spot.
        if (bossInCurrentWave && bossMobs.isEmpty()) {
            if (!aliveMobs.isEmpty()) {
                discardAliveMobs(world);
            }
            bossInCurrentWave = false;
            changed = true;
            // KILL_BOSS: the boss dying ends the room outright, waves left or not.
            if (objective.type() == RoomObjectiveConfig.Type.KILL_BOSS && activated && !cleared) {
                markCleared();
                return;
            }
        }

        // A running SURVIVE timer owns the clear — dead waves loop instead (tickWaveProgression).
        if (activated && !cleared && aliveMobs.isEmpty() && isOnFinalWave() && !isSurviveLooping()) {
            markCleared();
            discardProtectTarget(world);
            return;
        }
        if (changed) setChanged();
    }

    // ---- NBT ----

    // Internal serialization record; never exposed outside the class.
    private record State(
        List<BlockPos> spawnerOffsets,
        List<BlockPos> doorOffsets,
        RespawnPoints respawnPoints,
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
        List<BlockPos> entranceOffsets,
        RoomObjectiveConfig objective,
        ObjectiveRuntime objectiveRuntime
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("spawnerOffsets").forGetter(State::spawnerOffsets),
            BlockPos.CODEC.listOf().optionalFieldOf("doorOffsets", List.of()).forGetter(State::doorOffsets),
            RespawnPoints.MAP_CODEC.forGetter(State::respawnPoints),
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
            BlockPos.CODEC.listOf().optionalFieldOf("entranceOffsets", List.of()).forGetter(State::entranceOffsets),
            RoomObjectiveConfig.CODEC.optionalFieldOf("objective", RoomObjectiveConfig.DEFAULT).forGetter(State::objective),
            ObjectiveRuntime.CODEC.optionalFieldOf("objectiveRuntime", ObjectiveRuntime.EMPTY).forGetter(State::objectiveRuntime)
        ).apply(i, State::new));
    }

    /** Respawn points, nested as a flattened MapCodec because the State codec is at the 16-field
     *  cap: reads the pre-rework single "respawnOffset" alongside the list for old saves. */
    private record RespawnPoints(Optional<BlockPos> legacySingle, List<BlockPos> offsets) {
        static final com.mojang.serialization.MapCodec<RespawnPoints> MAP_CODEC =
            RecordCodecBuilder.mapCodec(i -> i.group(
                BlockPos.CODEC.optionalFieldOf("respawnOffset").forGetter(RespawnPoints::legacySingle),
                BlockPos.CODEC.listOf().optionalFieldOf("respawnOffsets", List.of()).forGetter(RespawnPoints::offsets)
            ).apply(i, RespawnPoints::new));
    }

    /** Objective and wave runtime state, nested because the State codec is at the 16-field cap. */
    private record ObjectiveRuntime(int surviveTicksRemaining, Optional<UUID> protectTargetUuid,
                                    int currentWaveSize) {
        static final ObjectiveRuntime EMPTY = new ObjectiveRuntime(-1, Optional.empty(), 0);
        static final Codec<ObjectiveRuntime> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("surviveTicksRemaining", -1).forGetter(ObjectiveRuntime::surviveTicksRemaining),
            UUIDUtil.CODEC.optionalFieldOf("protectTargetUuid").forGetter(ObjectiveRuntime::protectTargetUuid),
            Codec.INT.optionalFieldOf("currentWaveSize", 0).forGetter(ObjectiveRuntime::currentWaveSize)
        ).apply(i, ObjectiveRuntime::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        State state = new State(spawnerOffsets, doorOffsets,
            new RespawnPoints(Optional.empty(), respawnOffsets), aliveMobs, bossMobs, activated, cleared, roomName,
            waveNumbers, currentWaveIndex, nextWaveDelayTicks, bossInCurrentWave, roomReward,
            List.of() /* legacy entranceOffsets — merged into doorOffsets since the undirected-door rework */,
            objective, new ObjectiveRuntime(surviveTicksRemaining, Optional.ofNullable(protectTargetUuid),
                currentWaveSize));
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
                    // Pre-rework saves kept entrance doors in a second list; doors are
                    // direction-free now, so fold them in (positions, not groups — group-level
                    // dedupe happens wherever doors are consumed).
                    for (BlockPos legacyEntrance : state.entranceOffsets()) {
                        if (!doorOffsets.contains(legacyEntrance)) {
                            doorOffsets.add(legacyEntrance);
                        }
                    }
                    respawnOffsets.clear();
                    respawnOffsets.addAll(state.respawnPoints().offsets());
                    state.respawnPoints().legacySingle().ifPresent(legacy -> {
                        if (!respawnOffsets.contains(legacy)) {
                            respawnOffsets.add(legacy);
                        }
                    });
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
                    objective = state.objective();
                    surviveTicksRemaining = state.objectiveRuntime().surviveTicksRemaining();
                    protectTargetUuid = state.objectiveRuntime().protectTargetUuid().orElse(null);
                    currentWaveSize = state.objectiveRuntime().currentWaveSize();
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
        return new RoomControllerData(worldPosition, entries, getDoorPositions(),
            roomName, getRespawnPositions(), roomReward, objective, enumerateLootTables());
    }

    @Override
    public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, RoomControllerData> openingDataCodec() {
        return RoomControllerData.STREAM_CODEC;
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
