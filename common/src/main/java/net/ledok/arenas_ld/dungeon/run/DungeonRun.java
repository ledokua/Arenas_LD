package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mutable runtime state of an active (or just-ended) dungeon run.
 *
 * <p>Owned by the dungeon controller (Phase E). This class is the centerpiece of the v4.0
 * architecture: it holds every piece of state that used to be spread across the legacy
 * {@code DungeonBossSpawnerBlockEntity} (trackedPlayers, downedPlayers, isBattleActive,
 * isDungeonActive, internalDungeonCloseTimer, dungeonTimeTicksRemaining, etc).
 *
 * <p>This class is intentionally <b>data + accessors + mutators only</b>. Lifecycle methods
 * (tick, handleWin, handleLoss, transitions) live in Phase E sibling code and call the
 * package-private mutators here. This prevents controllers in other packages from mutating
 * run state directly.
 *
 * <p>Persisted via {@link #CODEC} so runs survive server restarts.
 *
 * <p>Not thread-safe. Block entities tick on the server thread only.
 */
public final class DungeonRun {

    // ---- Fields ----

    private DungeonPhase phase;
    private DungeonOutcome outcome;
    private final DifficultyTier tier;
    private final String ownerName;
    private final TierConfig resolvedTierConfig;
    private final boolean hardcoreEnabled;
    private final BlockPos dbsPos;
    private final ResourceKey<Level> dbsDimension;
    private int currentRoomIndex;
    private int dungeonTimerTicks;
    private int closeTimerTicks;
    private int initialCloseTimerTicks;
    private final long startTick;
    private final Map<UUID, RunParticipant> participants;
    private final Map<UUID, PlayerReturnPoint> returnPoints;
    private final Map<UUID, DownedPlayer> downedPlayers;
    private final Map<UUID, Long> disconnectedAt;
    /** Mob HP multiplier from the starting party size — frozen at run start, like the tier config. */
    private double partyHealthMultiplier = 1.0;
    // Branching-mode progression (legacy linear runs leave these untouched):
    /** Room the party is locked into (PENDING/ACTIVE), or null while EXPLORING. */
    @Nullable private BlockPos currentRoomPos = null;
    private final Set<BlockPos> clearedRooms = new java.util.LinkedHashSet<>();
    /** Grace countdown before the current room activates; -1 = not pending. */
    private int pendingGraceTicks = -1;
    /** Entrance-door block → room lookup for crossing detection; rebuilt lazily, never persisted. */
    @Nullable private transient Map<BlockPos, BlockPos> entranceDetectionCache = null;
    @Nullable private transient ServerBossEvent dungeonTimeBossBar;
    @Nullable private transient ServerBossEvent closeTimerBossBar;

    // ---- Constructors ----

    /**
     * Construct a fresh run, ready to begin its STARTING phase.
     * Maps start empty; phase=STARTING; outcome=IN_PROGRESS; currentRoomIndex=0;
     * dungeonTimerTicks = resolvedTierConfig.dungeonTimeSeconds() * 20; closeTimerTicks=0.
     *
     * @param tier               the chosen difficulty tier
     * @param resolvedTierConfig snapshotted at run start — admin edits during a run don't affect this run
     * @param hardcoreEnabled    whether hardcore mode is active for this run
     * @param dbsPos             position of the DungeonBossSpawner anchoring this run
     * @param dbsDimension       dimension of the DungeonBossSpawner
     * @param startTick          server tick at which the run was created
     */
    public DungeonRun(
        DifficultyTier tier,
        TierConfig resolvedTierConfig,
        boolean hardcoreEnabled,
        BlockPos dbsPos,
        ResourceKey<Level> dbsDimension,
        long startTick,
        String ownerName
    ) {
        this.phase = DungeonPhase.STARTING;
        this.outcome = DungeonOutcome.IN_PROGRESS;
        this.tier = tier;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.resolvedTierConfig = resolvedTierConfig;
        this.hardcoreEnabled = hardcoreEnabled;
        this.dbsPos = dbsPos;
        this.dbsDimension = dbsDimension;
        this.currentRoomIndex = 0;
        this.dungeonTimerTicks = resolvedTierConfig.dungeonTimeSeconds() * 20;
        this.closeTimerTicks = 0;
        this.initialCloseTimerTicks = 0;
        this.startTick = startTick;
        this.participants = new LinkedHashMap<>();
        this.returnPoints = new HashMap<>();
        this.downedPlayers = new HashMap<>();
        this.disconnectedAt = new HashMap<>();
        this.dungeonTimeBossBar = null;
        this.closeTimerBossBar = null;
    }

    /**
     * Constructor used by the codec when loading from NBT. Takes every field explicitly.
     * Package-private — outside callers use the public constructor + mutators.
     */
    DungeonRun(
        DungeonPhase phase,
        DungeonOutcome outcome,
        DifficultyTier tier,
        TierConfig resolvedTierConfig,
        boolean hardcoreEnabled,
        BlockPos dbsPos,
        ResourceKey<Level> dbsDimension,
        int currentRoomIndex,
        int dungeonTimerTicks,
        int closeTimerTicks,
        int initialCloseTimerTicks,
        long startTick,
        Map<UUID, RunParticipant> participants,
        Map<UUID, PlayerReturnPoint> returnPoints,
        Map<UUID, DownedPlayer> downedPlayers,
        Map<UUID, Long> disconnectedAt
    ) {
        this.phase = phase;
        this.outcome = outcome;
        this.tier = tier;
        this.ownerName = "";
        this.resolvedTierConfig = resolvedTierConfig;
        this.hardcoreEnabled = hardcoreEnabled;
        this.dbsPos = dbsPos;
        this.dbsDimension = dbsDimension;
        this.currentRoomIndex = currentRoomIndex;
        this.dungeonTimerTicks = dungeonTimerTicks;
        this.closeTimerTicks = closeTimerTicks;
        this.initialCloseTimerTicks = initialCloseTimerTicks;
        this.startTick = startTick;
        this.participants = new LinkedHashMap<>(participants);
        this.returnPoints = new HashMap<>(returnPoints);
        this.downedPlayers = new HashMap<>(downedPlayers);
        this.disconnectedAt = new HashMap<>(disconnectedAt);
        this.dungeonTimeBossBar = null;
        this.closeTimerBossBar = null;
    }

    // ---- Public getters ----

    public DungeonPhase phase() { return phase; }
    public DungeonOutcome outcome() { return outcome; }
    public DifficultyTier tier() { return tier; }
    public double partyHealthMultiplier() { return partyHealthMultiplier; }
    public void setPartyHealthMultiplier(double multiplier) { this.partyHealthMultiplier = Math.max(1.0, multiplier); }
    public String ownerName() { return ownerName; }
    public TierConfig resolvedTierConfig() { return resolvedTierConfig; }
    public boolean hardcoreEnabled() { return hardcoreEnabled; }
    public BlockPos dbsPos() { return dbsPos; }
    public ResourceKey<Level> dbsDimension() { return dbsDimension; }
    /** Legacy linear progression cursor; branching runs use {@link #currentRoomPos()} instead. */
    public int currentRoomIndex() { return currentRoomIndex; }
    @Nullable public BlockPos currentRoomPos() { return currentRoomPos; }
    public Set<BlockPos> clearedRooms() { return Collections.unmodifiableSet(clearedRooms); }
    public boolean isRoomCleared(BlockPos roomPos) { return clearedRooms.contains(roomPos); }
    public int pendingGraceTicks() { return pendingGraceTicks; }
    @Nullable public Map<BlockPos, BlockPos> entranceDetectionCache() { return entranceDetectionCache; }
    public int dungeonTimerTicks() { return dungeonTimerTicks; }
    public int closeTimerTicks() { return closeTimerTicks; }
    public int initialCloseTimerTicks() { return initialCloseTimerTicks; }
    public long startTick() { return startTick; }
    @Nullable public ServerBossEvent getDungeonTimeBossBar() { return dungeonTimeBossBar; }
    @Nullable public ServerBossEvent getCloseTimerBossBar() { return closeTimerBossBar; }

    public Map<UUID, RunParticipant> participants() {
        return Collections.unmodifiableMap(participants);
    }
    public Map<UUID, PlayerReturnPoint> returnPoints() {
        return Collections.unmodifiableMap(returnPoints);
    }
    public Map<UUID, DownedPlayer> downedPlayers() {
        return Collections.unmodifiableMap(downedPlayers);
    }
    public Map<UUID, Long> disconnectedAt() {
        return Collections.unmodifiableMap(disconnectedAt);
    }

    // ---- Package-private mutators (called by lifecycle code in Phase E) ----

    void setPhase(DungeonPhase phase) { this.phase = phase; }
    void setOutcome(DungeonOutcome outcome) { this.outcome = outcome; }
    void setCurrentRoomIndex(int index) { this.currentRoomIndex = index; }
    void setCurrentRoomPos(@Nullable BlockPos pos) { this.currentRoomPos = pos; }
    void addClearedRoom(BlockPos pos) { this.clearedRooms.add(pos); }
    void setPendingGraceTicks(int ticks) { this.pendingGraceTicks = ticks; }
    void setEntranceDetectionCache(@Nullable Map<BlockPos, BlockPos> cache) { this.entranceDetectionCache = cache; }
    void invalidateEntranceDetectionCache() { this.entranceDetectionCache = null; }
    void setDungeonTimerTicks(int ticks) { this.dungeonTimerTicks = ticks; }
    void setCloseTimerTicks(int ticks) { this.closeTimerTicks = ticks; }
    void setInitialCloseTimerTicks(int ticks) { this.initialCloseTimerTicks = ticks; }
    void setDungeonTimeBossBar(@Nullable ServerBossEvent bar) { this.dungeonTimeBossBar = bar; }
    void setCloseTimerBossBar(@Nullable ServerBossEvent bar) { this.closeTimerBossBar = bar; }

    void addParticipant(RunParticipant p) { participants.put(p.playerUuid(), p); }
    void updateParticipant(RunParticipant p) { participants.put(p.playerUuid(), p); }
    void removeParticipant(UUID uuid) { participants.remove(uuid); }

    void setReturnPoint(UUID uuid, PlayerReturnPoint rp) { returnPoints.put(uuid, rp); }
    void removeReturnPoint(UUID uuid) { returnPoints.remove(uuid); }

    void setDowned(DownedPlayer dp) { downedPlayers.put(dp.playerUuid(), dp); }
    void clearDowned(UUID uuid) { downedPlayers.remove(uuid); }
    void markDisconnected(UUID uuid, long tick) { disconnectedAt.put(uuid, tick); }
    void clearDisconnected(UUID uuid) { disconnectedAt.remove(uuid); }

    // ---- Convenience predicates ----

    /** True if the player is currently within their disconnect grace window. */
    public boolean isDisconnected(UUID uuid) { return disconnectedAt.containsKey(uuid); }

    /** True if the player has an active downed (awaiting-respawn) entry. */
    public boolean isDowned(UUID uuid) { return downedPlayers.containsKey(uuid); }

    /** UUIDs of participants whose status is exactly ACTIVE. */
    public Set<UUID> activeParticipantUuids() {
        return participants.values().stream()
            .filter(p -> p.status() == ParticipantStatus.ACTIVE)
            .map(RunParticipant::playerUuid)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** UUIDs of participants eligible for loot at win — any status except REMOVED. */
    public Set<UUID> lootEligibleUuids() {
        return participants.values().stream()
            .filter(RunParticipant::isEligibleForLoot)
            .map(RunParticipant::playerUuid)
            .collect(Collectors.toUnmodifiableSet());
    }

    /** True only when phase == DONE. */
    public boolean isFinished() {
        return phase == DungeonPhase.DONE;
    }

    // ---- Serialization ----

    /** Branching-mode progression snapshot; EMPTY for legacy runs and pre-upgrade saves. */
    public record RoomProgress(java.util.Optional<BlockPos> currentRoomPos, java.util.List<BlockPos> clearedRooms, int pendingGraceTicks) {
        public static final RoomProgress EMPTY = new RoomProgress(java.util.Optional.empty(), java.util.List.of(), -1);
        public static final Codec<RoomProgress> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.optionalFieldOf("currentRoomPos").forGetter(RoomProgress::currentRoomPos),
            BlockPos.CODEC.listOf().optionalFieldOf("clearedRooms", java.util.List.of()).forGetter(RoomProgress::clearedRooms),
            Codec.INT.optionalFieldOf("pendingGraceTicks", -1).forGetter(RoomProgress::pendingGraceTicks)
        ).apply(i, RoomProgress::new));
    }

    private RoomProgress snapshotRoomProgress() {
        return new RoomProgress(java.util.Optional.ofNullable(currentRoomPos),
            java.util.List.copyOf(clearedRooms), pendingGraceTicks);
    }

    private void applyRoomProgress(RoomProgress progress) {
        this.currentRoomPos = progress.currentRoomPos().orElse(null);
        this.clearedRooms.clear();
        this.clearedRooms.addAll(progress.clearedRooms());
        this.pendingGraceTicks = progress.pendingGraceTicks();
    }

    /** The original 16 fields — kept in their own group because DFU caps group() at 16 entries. */
    private static final com.mojang.serialization.MapCodec<DungeonRun> BASE_CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            DungeonPhase.CODEC.fieldOf("phase").forGetter(DungeonRun::phase),
            DungeonOutcome.CODEC.fieldOf("outcome").forGetter(DungeonRun::outcome),
            DifficultyTier.CODEC.fieldOf("tier").forGetter(DungeonRun::tier),
            TierConfig.CODEC.fieldOf("tierConfig").forGetter(DungeonRun::resolvedTierConfig),
            Codec.BOOL.fieldOf("hardcore").forGetter(DungeonRun::hardcoreEnabled),
            BlockPos.CODEC.fieldOf("dbsPos").forGetter(DungeonRun::dbsPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dbsDim").forGetter(DungeonRun::dbsDimension),
            Codec.INT.fieldOf("currentRoomIndex").forGetter(DungeonRun::currentRoomIndex),
            Codec.INT.fieldOf("dungeonTimerTicks").forGetter(DungeonRun::dungeonTimerTicks),
            Codec.INT.fieldOf("closeTimerTicks").forGetter(DungeonRun::closeTimerTicks),
            Codec.INT.optionalFieldOf("initialCloseTimerTicks", 0).forGetter(DungeonRun::initialCloseTimerTicks),
            Codec.LONG.fieldOf("startTick").forGetter(DungeonRun::startTick),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, RunParticipant.CODEC)
                .fieldOf("participants").forGetter(DungeonRun::participants),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerReturnPoint.CODEC)
                .fieldOf("returnPoints").forGetter(DungeonRun::returnPoints),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, DownedPlayer.CODEC)
                .fieldOf("downedPlayers").forGetter(DungeonRun::downedPlayers),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                .optionalFieldOf("disconnectedAt", Map.of()).forGetter(DungeonRun::disconnectedAt)
        ).apply(instance, DungeonRun::new)
    );

    public static final Codec<DungeonRun> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            BASE_CODEC.forGetter(run -> run),
            Codec.DOUBLE.optionalFieldOf("partyHealthMultiplier", 1.0).forGetter(DungeonRun::partyHealthMultiplier),
            RoomProgress.CODEC.optionalFieldOf("roomProgress", RoomProgress.EMPTY).forGetter(DungeonRun::snapshotRoomProgress)
        ).apply(instance, (run, partyHealthMultiplier, roomProgress) -> {
            run.setPartyHealthMultiplier(partyHealthMultiplier);
            run.applyRoomProgress(roomProgress);
            return run;
        })
    );
}
