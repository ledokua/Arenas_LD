package net.ledok.arenas_ld.raid.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.DownedPlayer;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Mutable runtime state of an active raid. Mirrors {@link net.ledok.arenas_ld.dungeon.run.DungeonRun}.
 *
 * <p>Owned by the raid controller. Data + accessors + package-private mutators only —
 * lifecycle transitions live in {@code RaidRunLifecycle} (added in a later PR).
 */
public final class RaidRun {

    private RaidPhase phase;
    private RaidOutcome outcome;
    private final UUID lobbyId;
    private final String ownerName;
    private final DifficultyTier tier;
    private final RaidTierConfig resolvedTierConfig;
    private final boolean hardcoreEnabled;
    private final BlockPos spawnerPos;
    private final ResourceKey<Level> spawnerDimension;
    private int timerTicks;
    private int closeTimerTicks;
    private int initialCloseTimerTicks;
    private int regenerationTickTimer;
    private final long startTick;
    @Nullable private BossRef bossRef;
    private final Map<UUID, RunParticipant> participants;
    private final Map<UUID, PlayerReturnPoint> returnPoints;
    private final Map<UUID, DownedPlayer> downedPlayers;
    private final Map<UUID, Long> disconnectedAt;
    @Nullable private transient ServerBossEvent raidTimerBossBar;
    @Nullable private transient ServerBossEvent closeTimerBossBar;

    public RaidRun(
        UUID lobbyId,
        String ownerName,
        DifficultyTier tier,
        RaidTierConfig resolvedTierConfig,
        boolean hardcoreEnabled,
        BlockPos spawnerPos,
        ResourceKey<Level> spawnerDimension,
        long startTick
    ) {
        this.phase = RaidPhase.STARTING;
        this.outcome = RaidOutcome.IN_PROGRESS;
        this.lobbyId = lobbyId;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.tier = tier;
        this.resolvedTierConfig = resolvedTierConfig;
        this.hardcoreEnabled = hardcoreEnabled;
        this.spawnerPos = spawnerPos;
        this.spawnerDimension = spawnerDimension;
        this.timerTicks = resolvedTierConfig.raidTimeSeconds() * 20;
        this.closeTimerTicks = 0;
        this.initialCloseTimerTicks = 0;
        this.regenerationTickTimer = 0;
        this.startTick = startTick;
        this.bossRef = null;
        this.participants = new LinkedHashMap<>();
        this.returnPoints = new HashMap<>();
        this.downedPlayers = new HashMap<>();
        this.disconnectedAt = new HashMap<>();
        this.raidTimerBossBar = null;
        this.closeTimerBossBar = null;
    }

    RaidRun(
        RaidPhase phase,
        RaidOutcome outcome,
        UUID lobbyId,
        DifficultyTier tier,
        RaidTierConfig resolvedTierConfig,
        boolean hardcoreEnabled,
        BlockPos spawnerPos,
        ResourceKey<Level> spawnerDimension,
        RaidRunTimers timers,
        long startTick,
        Optional<BossRef> bossRef,
        Map<UUID, RunParticipant> participants,
        Map<UUID, PlayerReturnPoint> returnPoints,
        Map<UUID, DownedPlayer> downedPlayers,
        Map<UUID, Long> disconnectedAt
    ) {
        this.phase = phase;
        this.outcome = outcome;
        this.lobbyId = lobbyId;
        this.ownerName = "";
        this.tier = tier;
        this.resolvedTierConfig = resolvedTierConfig;
        this.hardcoreEnabled = hardcoreEnabled;
        this.spawnerPos = spawnerPos;
        this.spawnerDimension = spawnerDimension;
        this.timerTicks = timers.timerTicks();
        this.closeTimerTicks = timers.closeTimerTicks();
        this.initialCloseTimerTicks = timers.initialCloseTimerTicks();
        this.regenerationTickTimer = timers.regenerationTickTimer();
        this.startTick = startTick;
        this.bossRef = bossRef.orElse(null);
        this.participants = new LinkedHashMap<>(participants);
        this.returnPoints = new HashMap<>(returnPoints);
        this.downedPlayers = new HashMap<>(downedPlayers);
        this.disconnectedAt = new HashMap<>(disconnectedAt);
        this.raidTimerBossBar = null;
        this.closeTimerBossBar = null;
    }

    public RaidPhase phase() { return phase; }
    public RaidOutcome outcome() { return outcome; }
    public UUID lobbyId() { return lobbyId; }
    public String ownerName() { return ownerName; }
    public DifficultyTier tier() { return tier; }
    public RaidTierConfig resolvedTierConfig() { return resolvedTierConfig; }
    public boolean hardcoreEnabled() { return hardcoreEnabled; }
    public BlockPos spawnerPos() { return spawnerPos; }
    public ResourceKey<Level> spawnerDimension() { return spawnerDimension; }
    public int timerTicks() { return timerTicks; }
    public int closeTimerTicks() { return closeTimerTicks; }
    public int initialCloseTimerTicks() { return initialCloseTimerTicks; }
    public int regenerationTickTimer() { return regenerationTickTimer; }
    public long startTick() { return startTick; }
    @Nullable public UUID bossUuid() { return bossRef == null ? null : bossRef.bossUuid(); }
    @Nullable public ResourceKey<Level> bossDimension() { return bossRef == null ? null : bossRef.bossDimension(); }
    @Nullable public ServerBossEvent getRaidTimerBossBar() { return raidTimerBossBar; }
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

    void setPhase(RaidPhase phase) { this.phase = phase; }
    void setOutcome(RaidOutcome outcome) { this.outcome = outcome; }
    void setTimerTicks(int ticks) { this.timerTicks = ticks; }
    void setCloseTimerTicks(int ticks) { this.closeTimerTicks = ticks; }
    void setInitialCloseTimerTicks(int ticks) { this.initialCloseTimerTicks = ticks; }
    void setRegenerationTickTimer(int ticks) { this.regenerationTickTimer = ticks; }
    void setBossRef(@Nullable UUID bossUuid, @Nullable ResourceKey<Level> bossDimension) {
        this.bossRef = (bossUuid == null || bossDimension == null) ? null : new BossRef(bossUuid, bossDimension);
    }
    void setRaidTimerBossBar(@Nullable ServerBossEvent bar) { this.raidTimerBossBar = bar; }
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
        return phase == RaidPhase.DONE;
    }

    private RaidRunTimers timersSnapshot() {
        return new RaidRunTimers(timerTicks, closeTimerTicks, initialCloseTimerTicks,
            regenerationTickTimer);
    }

    private Optional<BossRef> bossRefSnapshot() {
        return Optional.ofNullable(bossRef);
    }

    public record BossRef(UUID bossUuid, ResourceKey<Level> bossDimension) {
        public static final Codec<BossRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("uuid").forGetter(BossRef::bossUuid),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dim").forGetter(BossRef::bossDimension)
        ).apply(i, BossRef::new));
    }

    public record RaidRunTimers(
        int timerTicks,
        int closeTimerTicks,
        int initialCloseTimerTicks,
        int regenerationTickTimer
    ) {
        public static final RaidRunTimers ZERO = new RaidRunTimers(0, 0, 0, 0);

        public static final Codec<RaidRunTimers> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("timerTicks").forGetter(RaidRunTimers::timerTicks),
            Codec.INT.fieldOf("closeTimerTicks").forGetter(RaidRunTimers::closeTimerTicks),
            Codec.INT.optionalFieldOf("initialCloseTimerTicks", 0).forGetter(RaidRunTimers::initialCloseTimerTicks),
            Codec.INT.optionalFieldOf("regenerationTickTimer", 0).forGetter(RaidRunTimers::regenerationTickTimer)
        ).apply(i, RaidRunTimers::new));
    }

    public static final Codec<RaidRun> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            RaidPhase.CODEC.fieldOf("phase").forGetter(RaidRun::phase),
            RaidOutcome.CODEC.fieldOf("outcome").forGetter(RaidRun::outcome),
            UUIDUtil.CODEC.fieldOf("lobbyId").forGetter(RaidRun::lobbyId),
            DifficultyTier.CODEC.fieldOf("tier").forGetter(RaidRun::tier),
            RaidTierConfig.CODEC.fieldOf("tierConfig").forGetter(RaidRun::resolvedTierConfig),
            Codec.BOOL.fieldOf("hardcore").forGetter(RaidRun::hardcoreEnabled),
            BlockPos.CODEC.fieldOf("spawnerPos").forGetter(RaidRun::spawnerPos),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("spawnerDim").forGetter(RaidRun::spawnerDimension),
            RaidRunTimers.CODEC.optionalFieldOf("timers", RaidRunTimers.ZERO).forGetter(RaidRun::timersSnapshot),
            Codec.LONG.fieldOf("startTick").forGetter(RaidRun::startTick),
            BossRef.CODEC.optionalFieldOf("bossRef").forGetter(RaidRun::bossRefSnapshot),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, RunParticipant.CODEC)
                .fieldOf("participants").forGetter(RaidRun::participants),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, PlayerReturnPoint.CODEC)
                .fieldOf("returnPoints").forGetter(RaidRun::returnPoints),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, DownedPlayer.CODEC)
                .fieldOf("downedPlayers").forGetter(RaidRun::downedPlayers),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
                .optionalFieldOf("disconnectedAt", Map.of()).forGetter(RaidRun::disconnectedAt)
        ).apply(instance, RaidRun::new)
    );
}
