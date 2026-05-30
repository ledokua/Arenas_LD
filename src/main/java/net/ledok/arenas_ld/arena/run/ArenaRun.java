package net.ledok.arenas_ld.arena.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.dungeon.run.DownedPlayer;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mutable runtime state of an active arena run. Mirrors {@link net.ledok.arenas_ld.raid.run.RaidRun}
 * but models wave-survival rather than a single boss: it tracks the current wave, its archetype /
 * objective, the per-phase timers, and the set of mobs alive in the wave.
 *
 * <p>Owned by the arena controller. Data + accessors + package-private mutators only — lifecycle
 * transitions live in {@code ArenaRunLifecycle}.
 */
public final class ArenaRun {

    private ArenaPhase phase;
    private ArenaOutcome outcome;
    private final UUID lobbyId;
    private final String ownerName;
    private final boolean hardcoreEnabled;
    private final BlockPos spawnerPos;
    private final ResourceKey<Level> spawnerDimension;
    /** Configured ceiling; {@code -1} means endless until wipe / wave-timer expiry. */
    private final int maxWave;
    private final long startTick;

    // Wave-loop state.
    private int currentWave;
    private WaveArchetype currentArchetype;
    private ObjectiveType currentObjective;
    private int objectiveProgress;
    @Nullable private UUID markedMob;
    private int waveTicksRemaining;
    private int prepareTicksRemaining;
    private int betweenWaveTicksRemaining;

    // CLOSING grace.
    private int closeTimerTicks;
    private int initialCloseTimerTicks;

    private final Set<UUID> aliveMobs;
    private final Map<UUID, RunParticipant> participants;
    private final Map<UUID, PlayerReturnPoint> returnPoints;
    private final Map<UUID, DownedPlayer> downedPlayers;
    private final Map<UUID, Long> disconnectedAt;

    @Nullable private transient ServerBossEvent waveBossBar;
    @Nullable private transient ServerBossEvent closeTimerBossBar;

    public ArenaRun(
        UUID lobbyId,
        String ownerName,
        boolean hardcoreEnabled,
        BlockPos spawnerPos,
        ResourceKey<Level> spawnerDimension,
        int maxWave,
        long startTick
    ) {
        this.phase = ArenaPhase.STARTING;
        this.outcome = ArenaOutcome.IN_PROGRESS;
        this.lobbyId = lobbyId;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.hardcoreEnabled = hardcoreEnabled;
        this.spawnerPos = spawnerPos;
        this.spawnerDimension = spawnerDimension;
        this.maxWave = maxWave;
        this.startTick = startTick;
        this.currentWave = 0;
        this.currentArchetype = WaveArchetype.HORDE;
        this.currentObjective = ObjectiveType.NONE;
        this.objectiveProgress = 0;
        this.markedMob = null;
        this.waveTicksRemaining = 0;
        this.prepareTicksRemaining = 0;
        this.betweenWaveTicksRemaining = 0;
        this.closeTimerTicks = 0;
        this.initialCloseTimerTicks = 0;
        this.aliveMobs = new LinkedHashSet<>();
        this.participants = new LinkedHashMap<>();
        this.returnPoints = new HashMap<>();
        this.downedPlayers = new HashMap<>();
        this.disconnectedAt = new HashMap<>();
    }

    ArenaRun(
        ArenaPhase phase,
        ArenaOutcome outcome,
        UUID lobbyId,
        boolean hardcoreEnabled,
        BlockPos spawnerPos,
        ResourceKey<Level> spawnerDimension,
        int maxWave,
        long startTick,
        WaveState waveState,
        ArenaRunTimers timers,
        Set<UUID> aliveMobs,
        Map<UUID, RunParticipant> participants,
        Map<UUID, PlayerReturnPoint> returnPoints,
        Map<UUID, DownedPlayer> downedPlayers,
        Map<UUID, Long> disconnectedAt
    ) {
        this.phase = phase;
        this.outcome = outcome;
        this.lobbyId = lobbyId;
        this.ownerName = "";
        this.hardcoreEnabled = hardcoreEnabled;
        this.spawnerPos = spawnerPos;
        this.spawnerDimension = spawnerDimension;
        this.maxWave = maxWave;
        this.startTick = startTick;
        this.currentWave = waveState.currentWave();
        this.currentArchetype = waveState.archetype();
        this.currentObjective = waveState.objective();
        this.objectiveProgress = waveState.objectiveProgress();
        this.markedMob = waveState.markedMob().orElse(null);
        this.waveTicksRemaining = waveState.waveTicksRemaining();
        this.prepareTicksRemaining = waveState.prepareTicksRemaining();
        this.betweenWaveTicksRemaining = waveState.betweenWaveTicksRemaining();
        this.closeTimerTicks = timers.closeTimerTicks();
        this.initialCloseTimerTicks = timers.initialCloseTimerTicks();
        this.aliveMobs = new LinkedHashSet<>(aliveMobs);
        this.participants = new LinkedHashMap<>(participants);
        this.returnPoints = new HashMap<>(returnPoints);
        this.downedPlayers = new HashMap<>(downedPlayers);
        this.disconnectedAt = new HashMap<>(disconnectedAt);
    }

    public ArenaPhase phase() { return phase; }
    public ArenaOutcome outcome() { return outcome; }
    public UUID lobbyId() { return lobbyId; }
    public String ownerName() { return ownerName; }
    public boolean hardcoreEnabled() { return hardcoreEnabled; }
    public BlockPos spawnerPos() { return spawnerPos; }
    public ResourceKey<Level> spawnerDimension() { return spawnerDimension; }
    public int maxWave() { return maxWave; }
    public long startTick() { return startTick; }
    public int currentWave() { return currentWave; }
    public WaveArchetype currentArchetype() { return currentArchetype; }
    public ObjectiveType currentObjective() { return currentObjective; }
    public int objectiveProgress() { return objectiveProgress; }
    @Nullable public UUID markedMob() { return markedMob; }
    public int waveTicksRemaining() { return waveTicksRemaining; }
    public int prepareTicksRemaining() { return prepareTicksRemaining; }
    public int betweenWaveTicksRemaining() { return betweenWaveTicksRemaining; }
    public int closeTimerTicks() { return closeTimerTicks; }
    public int initialCloseTimerTicks() { return initialCloseTimerTicks; }
    @Nullable public ServerBossEvent getWaveBossBar() { return waveBossBar; }
    @Nullable public ServerBossEvent getCloseTimerBossBar() { return closeTimerBossBar; }

    public Set<UUID> aliveMobs() { return Collections.unmodifiableSet(aliveMobs); }
    public Map<UUID, RunParticipant> participants() { return Collections.unmodifiableMap(participants); }
    public Map<UUID, PlayerReturnPoint> returnPoints() { return Collections.unmodifiableMap(returnPoints); }
    public Map<UUID, DownedPlayer> downedPlayers() { return Collections.unmodifiableMap(downedPlayers); }
    public Map<UUID, Long> disconnectedAt() { return Collections.unmodifiableMap(disconnectedAt); }

    void setPhase(ArenaPhase phase) { this.phase = phase; }
    void setOutcome(ArenaOutcome outcome) { this.outcome = outcome; }
    void setCurrentWave(int wave) { this.currentWave = wave; }
    void setCurrentArchetype(WaveArchetype archetype) { this.currentArchetype = archetype; }
    void setCurrentObjective(ObjectiveType objective) { this.currentObjective = objective; }
    void setObjectiveProgress(int progress) { this.objectiveProgress = progress; }
    void setMarkedMob(@Nullable UUID mob) { this.markedMob = mob; }
    void setWaveTicksRemaining(int ticks) { this.waveTicksRemaining = ticks; }
    void setPrepareTicksRemaining(int ticks) { this.prepareTicksRemaining = ticks; }
    void setBetweenWaveTicksRemaining(int ticks) { this.betweenWaveTicksRemaining = ticks; }
    void setCloseTimerTicks(int ticks) { this.closeTimerTicks = ticks; }
    void setInitialCloseTimerTicks(int ticks) { this.initialCloseTimerTicks = ticks; }
    void setWaveBossBar(@Nullable ServerBossEvent bar) { this.waveBossBar = bar; }
    void setCloseTimerBossBar(@Nullable ServerBossEvent bar) { this.closeTimerBossBar = bar; }

    void addAliveMob(UUID uuid) { aliveMobs.add(uuid); }
    void removeAliveMob(UUID uuid) { aliveMobs.remove(uuid); }
    void clearAliveMobs() { aliveMobs.clear(); }

    void addParticipant(RunParticipant p) { participants.put(p.playerUuid(), p); }
    void updateParticipant(RunParticipant p) { participants.put(p.playerUuid(), p); }
    void removeParticipant(UUID uuid) { participants.remove(uuid); }

    void setReturnPoint(UUID uuid, PlayerReturnPoint rp) { returnPoints.put(uuid, rp); }
    void removeReturnPoint(UUID uuid) { returnPoints.remove(uuid); }

    void setDowned(DownedPlayer dp) { downedPlayers.put(dp.playerUuid(), dp); }
    void clearDowned(UUID uuid) { downedPlayers.remove(uuid); }
    void markDisconnected(UUID uuid, long tick) { disconnectedAt.put(uuid, tick); }
    void clearDisconnected(UUID uuid) { disconnectedAt.remove(uuid); }

    public Set<UUID> activeParticipantUuids() {
        return participants.values().stream()
            .filter(p -> p.status() == ParticipantStatus.ACTIVE)
            .map(RunParticipant::playerUuid)
            .collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> lootEligibleUuids() {
        return participants.values().stream()
            .filter(RunParticipant::isEligibleForLoot)
            .map(RunParticipant::playerUuid)
            .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isFinished() {
        return phase == ArenaPhase.DONE;
    }

    private WaveState waveStateSnapshot() {
        return new WaveState(currentWave, currentArchetype, currentObjective, objectiveProgress,
            Optional.ofNullable(markedMob), waveTicksRemaining, prepareTicksRemaining, betweenWaveTicksRemaining);
    }

    private ArenaRunTimers timersSnapshot() {
        return new ArenaRunTimers(closeTimerTicks, initialCloseTimerTicks);
    }

    public record WaveState(
        int currentWave,
        WaveArchetype archetype,
        ObjectiveType objective,
        int objectiveProgress,
        Optional<UUID> markedMob,
        int waveTicksRemaining,
        int prepareTicksRemaining,
        int betweenWaveTicksRemaining
    ) {
        public static final WaveState ZERO =
            new WaveState(0, WaveArchetype.HORDE, ObjectiveType.NONE, 0, Optional.empty(), 0, 0, 0);

        public static final Codec<WaveState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("currentWave").forGetter(WaveState::currentWave),
            WaveArchetype.CODEC.optionalFieldOf("archetype", WaveArchetype.HORDE).forGetter(WaveState::archetype),
            ObjectiveType.CODEC.optionalFieldOf("objective", ObjectiveType.NONE).forGetter(WaveState::objective),
            Codec.INT.optionalFieldOf("objectiveProgress", 0).forGetter(WaveState::objectiveProgress),
            UUIDUtil.CODEC.optionalFieldOf("markedMob").forGetter(WaveState::markedMob),
            Codec.INT.optionalFieldOf("waveTicksRemaining", 0).forGetter(WaveState::waveTicksRemaining),
            Codec.INT.optionalFieldOf("prepareTicksRemaining", 0).forGetter(WaveState::prepareTicksRemaining),
            Codec.INT.optionalFieldOf("betweenWaveTicksRemaining", 0).forGetter(WaveState::betweenWaveTicksRemaining)
        ).apply(i, WaveState::new));
    }

    public record ArenaRunTimers(
        int closeTimerTicks,
        int initialCloseTimerTicks
    ) {
        public static final ArenaRunTimers ZERO = new ArenaRunTimers(0, 0);

        public static final Codec<ArenaRunTimers> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("closeTimerTicks", 0).forGetter(ArenaRunTimers::closeTimerTicks),
            Codec.INT.optionalFieldOf("initialCloseTimerTicks", 0).forGetter(ArenaRunTimers::initialCloseTimerTicks)
        ).apply(i, ArenaRunTimers::new));
    }

    public static final Codec<ArenaRun> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ArenaPhase.CODEC.fieldOf("phase").forGetter(ArenaRun::phase),
            ArenaOutcome.CODEC.fieldOf("outcome").forGetter(ArenaRun::outcome),
            UUIDUtil.CODEC.fieldOf("lobbyId").forGetter(ArenaRun::lobbyId),
            Codec.BOOL.fieldOf("hardcore").forGetter(ArenaRun::hardcoreEnabled),
            BlockPos.CODEC.fieldOf("spawnerPos").forGetter(ArenaRun::spawnerPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("spawnerDim").forGetter(ArenaRun::spawnerDimension),
            Codec.INT.optionalFieldOf("maxWave", -1).forGetter(ArenaRun::maxWave),
            Codec.LONG.fieldOf("startTick").forGetter(ArenaRun::startTick),
            WaveState.CODEC.optionalFieldOf("waveState", WaveState.ZERO).forGetter(ArenaRun::waveStateSnapshot),
            ArenaRunTimers.CODEC.optionalFieldOf("timers", ArenaRunTimers.ZERO).forGetter(ArenaRun::timersSnapshot),
            UUIDUtil.CODEC.listOf().xmap(
                (java.util.List<UUID> list) -> (Set<UUID>) new LinkedHashSet<>(list),
                (Set<UUID> set) -> new java.util.ArrayList<>(set))
                .optionalFieldOf("aliveMobs", new LinkedHashSet<>()).forGetter(ArenaRun::aliveMobs),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, RunParticipant.CODEC)
                .fieldOf("participants").forGetter(ArenaRun::participants),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerReturnPoint.CODEC)
                .fieldOf("returnPoints").forGetter(ArenaRun::returnPoints),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, DownedPlayer.CODEC)
                .fieldOf("downedPlayers").forGetter(ArenaRun::downedPlayers),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                .optionalFieldOf("disconnectedAt", Map.of()).forGetter(ArenaRun::disconnectedAt)
        ).apply(instance, ArenaRun::new)
    );
}
