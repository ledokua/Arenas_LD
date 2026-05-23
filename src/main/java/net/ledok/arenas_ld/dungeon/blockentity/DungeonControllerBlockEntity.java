package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonControllerBlockEntity extends BlockEntity {
    private static final int DEFAULT_COOLDOWN_TICKS = 5 * 60 * 20;
    private static final int DEFAULT_CLOSE_TIMER_SECONDS = 30;
    private static final int DEFAULT_MAX_PARTY_SIZE = 4;

    private final List<BlockPos> instances = new ArrayList<>();
    private final Map<DifficultyTier, TierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);
    private int cooldownTicks = DEFAULT_COOLDOWN_TICKS;
    private int closeTimerSeconds = DEFAULT_CLOSE_TIMER_SECONDS;
    private int maxPartySize = DEFAULT_MAX_PARTY_SIZE;
    private final Map<BlockPos, Integer> instanceCooldownTimers = new HashMap<>();
    private final Set<BlockPos> pendingInstanceRemovals = new HashSet<>();
    private final Map<DifficultyTier, List<LeaderboardEntry>> leaderboards = new EnumMap<>(DifficultyTier.class);

    public DungeonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_CONTROLLER_V2_BLOCK_ENTITY, pos, state);
        initializeDefaults();
    }

    private void initializeDefaults() {
        tierConfigs.putIfAbsent(DifficultyTier.NORMAL, TierConfig.NORMAL_DEFAULT);
        tierConfigs.putIfAbsent(DifficultyTier.HARD, TierConfig.HARD_DEFAULT);
        tierConfigs.putIfAbsent(DifficultyTier.HELL, TierConfig.HELL_DEFAULT);
        for (DifficultyTier tier : DifficultyTier.values()) {
            leaderboards.putIfAbsent(tier, new ArrayList<>());
        }
    }

    public List<BlockPos> getInstances() {
        return Collections.unmodifiableList(instances);
    }

    public Map<DifficultyTier, TierConfig> getTierConfigs() {
        return Collections.unmodifiableMap(tierConfigs);
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getCloseTimerSeconds() {
        return closeTimerSeconds;
    }

    public int getMaxPartySize() {
        return maxPartySize;
    }

    public Map<BlockPos, Integer> getInstanceCooldownTimers() {
        return Collections.unmodifiableMap(instanceCooldownTimers);
    }

    public Set<BlockPos> getPendingInstanceRemovals() {
        return Collections.unmodifiableSet(pendingInstanceRemovals);
    }

    public Map<DifficultyTier, List<LeaderboardEntry>> getLeaderboards() {
        Map<DifficultyTier, List<LeaderboardEntry>> copy = new EnumMap<>(DifficultyTier.class);
        for (Map.Entry<DifficultyTier, List<LeaderboardEntry>> entry : leaderboards.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public void setTierConfig(DifficultyTier tier, TierConfig config) {
        tierConfigs.put(tier, config);
        setChanged();
    }

    public void setCooldownTicks(int cooldownTicks) {
        if (cooldownTicks < 0) return;
        this.cooldownTicks = cooldownTicks;
        setChanged();
    }

    public void setCloseTimerSeconds(int closeTimerSeconds) {
        if (closeTimerSeconds <= 0) return;
        this.closeTimerSeconds = closeTimerSeconds;
        setChanged();
    }

    public void setMaxPartySize(int maxPartySize) {
        if (maxPartySize < 1 || maxPartySize > 16) return;
        this.maxPartySize = maxPartySize;
        setChanged();
    }

    public boolean addInstance(BlockPos pos) {
        if (instances.contains(pos)) return false;
        instances.add(pos);
        setChanged();
        return true;
    }

    public boolean removeInstance(BlockPos pos) {
        if (!instances.contains(pos)) return false;
        if (isInstanceInActiveRun(pos)) {
            pendingInstanceRemovals.add(pos);
            setChanged();
            return true;
        }
        boolean removed = instances.remove(pos);
        if (removed) setChanged();
        return removed;
    }

    public boolean moveInstance(int from, int to) {
        if (from < 0 || from >= instances.size() || to < 0 || to >= instances.size()) return false;
        if (from == to) return false;
        BlockPos moved = instances.remove(from);
        instances.add(to, moved);
        setChanged();
        return true;
    }

    // Active run tracking arrives in PE-6.
    private boolean isInstanceInActiveRun(BlockPos pos) {
        return false;
    }

    void startInstanceCooldown(BlockPos pos) {
        instanceCooldownTimers.put(pos, cooldownTicks);
        setChanged();
    }

    void decrementInstanceCooldown(BlockPos pos) {
        Integer current = instanceCooldownTimers.get(pos);
        if (current == null) return;
        if (current <= 1) {
            instanceCooldownTimers.remove(pos);
        } else {
            instanceCooldownTimers.put(pos, current - 1);
        }
        setChanged();
    }

    void clearInstanceCooldown(BlockPos pos) {
        instanceCooldownTimers.remove(pos);
        setChanged();
    }

    void addLeaderboardEntry(DifficultyTier tier, LeaderboardEntry entry) {
        leaderboards.computeIfAbsent(tier, unused -> new ArrayList<>()).add(entry);
        setChanged();
    }

    void clearPendingRemoval(BlockPos pos) {
        pendingInstanceRemovals.remove(pos);
        setChanged();
    }

    void executePendingRemoval(BlockPos pos) {
        pendingInstanceRemovals.remove(pos);
        instances.remove(pos);
        setChanged();
    }

    private record InstanceCooldown(BlockPos pos, int ticks) {
        static final Codec<InstanceCooldown> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(InstanceCooldown::pos),
            Codec.INT.fieldOf("ticks").forGetter(InstanceCooldown::ticks)
        ).apply(i, InstanceCooldown::new));
    }

    private record State(
        List<BlockPos> instances,
        Map<DifficultyTier, TierConfig> tierConfigs,
        int cooldownTicks,
        int closeTimerSeconds,
        int maxPartySize,
        List<InstanceCooldown> instanceCooldowns,
        List<BlockPos> pendingInstanceRemovals,
        Map<DifficultyTier, List<LeaderboardEntry>> leaderboards
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("instances").forGetter(State::instances),
            Codec.unboundedMap(DifficultyTier.CODEC, TierConfig.CODEC).fieldOf("tierConfigs").forGetter(State::tierConfigs),
            Codec.INT.fieldOf("cooldownTicks").forGetter(State::cooldownTicks),
            Codec.INT.fieldOf("closeTimerSeconds").forGetter(State::closeTimerSeconds),
            Codec.INT.fieldOf("maxPartySize").forGetter(State::maxPartySize),
            InstanceCooldown.CODEC.listOf().fieldOf("instanceCooldowns").forGetter(State::instanceCooldowns),
            BlockPos.CODEC.listOf().fieldOf("pendingInstanceRemovals").forGetter(State::pendingInstanceRemovals),
            Codec.unboundedMap(DifficultyTier.CODEC, LeaderboardEntry.CODEC.listOf()).fieldOf("leaderboards").forGetter(State::leaderboards)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        List<InstanceCooldown> cooldowns = instanceCooldownTimers.entrySet().stream()
            .map(e -> new InstanceCooldown(e.getKey(), e.getValue()))
            .toList();
        State state = new State(
            instances,
            tierConfigs,
            cooldownTicks,
            closeTimerSeconds,
            maxPartySize,
            cooldowns,
            new ArrayList<>(pendingInstanceRemovals),
            leaderboards
        );
        State.CODEC.encodeStart(NbtOps.INSTANCE, state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save DungeonController at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        if (nbt.contains("State")) {
            State.CODEC.parse(NbtOps.INSTANCE, nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load DungeonController at {}: {}", worldPosition, err))
                .ifPresent(state -> {
                    instances.clear();
                    instances.addAll(state.instances());
                    tierConfigs.clear();
                    tierConfigs.putAll(state.tierConfigs());
                    cooldownTicks = state.cooldownTicks();
                    closeTimerSeconds = state.closeTimerSeconds();
                    maxPartySize = state.maxPartySize();
                    instanceCooldownTimers.clear();
                    for (InstanceCooldown c : state.instanceCooldowns()) {
                        instanceCooldownTimers.put(c.pos(), c.ticks());
                    }
                    pendingInstanceRemovals.clear();
                    pendingInstanceRemovals.addAll(state.pendingInstanceRemovals());
                    leaderboards.clear();
                    for (Map.Entry<DifficultyTier, List<LeaderboardEntry>> entry : state.leaderboards().entrySet()) {
                        leaderboards.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                    }
                    initializeDefaults();
                });
        } else {
            initializeDefaults();
        }
    }
}
