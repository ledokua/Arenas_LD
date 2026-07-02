package net.ledok.arenas_ld.arena.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.arena.run.ObjectiveType;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.ledok.arenas_ld.util.MobArenaRewardData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Passive arena anchor. Holds only arena geometry and wave/mob/reward/cadence configuration; all
 * run state lives in {@code ArenaRun} and is driven by the controller via {@code ArenaRunLifecycle}.
 * Mirrors {@link net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity}.
 */
public class ArenaSpawnerBlockEntity extends BlockEntity
    implements net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory<net.ledok.arenas_ld.arena.screen.ArenaSpawnerMenuData> {

    // --- Geometry / identity ---
    private String groupId = "";
    private BlockPos entranceOffset = BlockPos.ZERO;
    private ResourceKey<Level> entranceDimension = Level.OVERWORLD;
    private final List<BlockPos> respawnPointOffsets = new ArrayList<>();

    // --- Combat geometry ---
    private int battleRadius = 64;
    private int spawnDistance = 8;
    private double attributeScale = 0.1;
    private int entityHighlightTime = 0;

    // --- Wave timing (seconds) ---
    private int waveTimer = 120;
    private int additionalTime = 5;
    private int timeBetweenWaves = 10;
    private int prepareTime = 10;
    private int bossWaveAdditionalTime = 60;

    // --- Archetype / objective cadence (every N waves; 0 = never) ---
    private int bossEveryNWaves = 5;
    private int eliteEveryNWaves = 3;
    private int objectiveEveryNWaves = 7;
    private final List<ObjectiveType> enabledObjectives = new ArrayList<>(List.of(
        ObjectiveType.DEFEND_ZONE, ObjectiveType.SURVIVE_UNTOUCHED, ObjectiveType.KILL_MARKED));

    // --- Content ---
    private List<MobArenaMobData> mobs = new ArrayList<>();
    private List<MobArenaRewardData> rewards = new ArrayList<>();

    public ArenaSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.ARENA_SPAWNER_BLOCK_ENTITY, pos, state);
    }

    private void markDirtyAndSync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ── Geometry ──────────────────────────────────────────────────────────────
    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId == null ? "" : groupId; markDirtyAndSync(); }

    public BlockPos getEntranceOffset() { return entranceOffset; }
    public ResourceKey<Level> getEntranceDimension() { return entranceDimension; }
    public BlockPos getAbsoluteEntrancePos() { return worldPosition.offset(entranceOffset); }

    public void setEntrancePosition(BlockPos absolutePos, ResourceKey<Level> dim) {
        this.entranceOffset = absolutePos.subtract(worldPosition);
        this.entranceDimension = dim == null ? Level.OVERWORLD : dim;
        markDirtyAndSync();
    }

    public List<BlockPos> getRespawnPointOffsets() { return Collections.unmodifiableList(respawnPointOffsets); }

    public void addRespawnPointOffset(BlockPos relativePos) {
        if (relativePos != null && !respawnPointOffsets.contains(relativePos)) {
            respawnPointOffsets.add(relativePos);
            markDirtyAndSync();
        }
    }

    public boolean removeRespawnPointOffset(BlockPos relativePos) {
        if (relativePos != null && respawnPointOffsets.remove(relativePos)) {
            markDirtyAndSync();
            return true;
        }
        return false;
    }

    // ── Combat geometry / timing / cadence ──────────────────────────────────────
    public int getBattleRadius() { return battleRadius; }
    public void setBattleRadius(int v) { this.battleRadius = v; markDirtyAndSync(); }
    public int getSpawnDistance() { return spawnDistance; }
    public void setSpawnDistance(int v) { this.spawnDistance = v; markDirtyAndSync(); }
    public double getAttributeScale() { return attributeScale; }
    public void setAttributeScale(double v) { this.attributeScale = v; markDirtyAndSync(); }
    public int getEntityHighlightTime() { return entityHighlightTime; }
    public void setEntityHighlightTime(int v) { this.entityHighlightTime = v; markDirtyAndSync(); }

    public int getWaveTimer() { return waveTimer; }
    public void setWaveTimer(int v) { this.waveTimer = v; markDirtyAndSync(); }
    public int getAdditionalTime() { return additionalTime; }
    public void setAdditionalTime(int v) { this.additionalTime = v; markDirtyAndSync(); }
    public int getTimeBetweenWaves() { return timeBetweenWaves; }
    public void setTimeBetweenWaves(int v) { this.timeBetweenWaves = v; markDirtyAndSync(); }
    public int getPrepareTime() { return prepareTime; }
    public void setPrepareTime(int v) { this.prepareTime = v; markDirtyAndSync(); }
    public int getBossWaveAdditionalTime() { return bossWaveAdditionalTime; }
    public void setBossWaveAdditionalTime(int v) { this.bossWaveAdditionalTime = v; markDirtyAndSync(); }

    public int getBossEveryNWaves() { return bossEveryNWaves; }
    public void setBossEveryNWaves(int v) { this.bossEveryNWaves = Math.max(0, v); markDirtyAndSync(); }
    public int getEliteEveryNWaves() { return eliteEveryNWaves; }
    public void setEliteEveryNWaves(int v) { this.eliteEveryNWaves = Math.max(0, v); markDirtyAndSync(); }
    public int getObjectiveEveryNWaves() { return objectiveEveryNWaves; }
    public void setObjectiveEveryNWaves(int v) { this.objectiveEveryNWaves = Math.max(0, v); markDirtyAndSync(); }

    public List<ObjectiveType> getEnabledObjectives() { return Collections.unmodifiableList(enabledObjectives); }
    public void setEnabledObjectives(List<ObjectiveType> objectives) {
        enabledObjectives.clear();
        if (objectives != null) {
            for (ObjectiveType o : objectives) {
                if (o != ObjectiveType.NONE && !enabledObjectives.contains(o)) enabledObjectives.add(o);
            }
        }
        markDirtyAndSync();
    }

    // ── Content ──────────────────────────────────────────────────────────────
    public List<MobArenaMobData> getMobs() { return Collections.unmodifiableList(mobs); }
    public void setMobs(List<MobArenaMobData> mobs) { this.mobs = new ArrayList<>(mobs); markDirtyAndSync(); }
    public List<MobArenaRewardData> getRewards() { return Collections.unmodifiableList(rewards); }
    public void setRewards(List<MobArenaRewardData> rewards) { this.rewards = new ArrayList<>(rewards); markDirtyAndSync(); }

    // ── Persistence ──────────────────────────────────────────────────────────
    private record WaveTiming(int waveTimer, int additionalTime, int timeBetweenWaves,
                              int prepareTime, int bossWaveAdditionalTime) {
        static final WaveTiming DEFAULT = new WaveTiming(120, 5, 10, 10, 60);
        static final Codec<WaveTiming> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("waveTimer", 120).forGetter(WaveTiming::waveTimer),
            Codec.INT.optionalFieldOf("additionalTime", 5).forGetter(WaveTiming::additionalTime),
            Codec.INT.optionalFieldOf("timeBetweenWaves", 10).forGetter(WaveTiming::timeBetweenWaves),
            Codec.INT.optionalFieldOf("prepareTime", 10).forGetter(WaveTiming::prepareTime),
            Codec.INT.optionalFieldOf("bossWaveAdditionalTime", 60).forGetter(WaveTiming::bossWaveAdditionalTime)
        ).apply(i, WaveTiming::new));
    }

    private record Cadence(int bossEveryN, int eliteEveryN, int objectiveEveryN, List<ObjectiveType> objectives) {
        static final Cadence DEFAULT = new Cadence(5, 3, 7,
            List.of(ObjectiveType.DEFEND_ZONE, ObjectiveType.SURVIVE_UNTOUCHED, ObjectiveType.KILL_MARKED));
        static final Codec<Cadence> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("bossEveryN", 5).forGetter(Cadence::bossEveryN),
            Codec.INT.optionalFieldOf("eliteEveryN", 3).forGetter(Cadence::eliteEveryN),
            Codec.INT.optionalFieldOf("objectiveEveryN", 7).forGetter(Cadence::objectiveEveryN),
            ObjectiveType.CODEC.listOf().optionalFieldOf("objectives", DEFAULT.objectives()).forGetter(Cadence::objectives)
        ).apply(i, Cadence::new));
    }

    private record State(
        String groupId,
        BlockPos entranceOffset,
        ResourceKey<Level> entranceDim,
        List<BlockPos> respawnPointOffsets,
        int battleRadius,
        int spawnDistance,
        double attributeScale,
        int entityHighlightTime,
        WaveTiming timing,
        Cadence cadence,
        List<MobArenaMobData> mobs,
        List<MobArenaRewardData> rewards
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("groupId", "").forGetter(State::groupId),
            BlockPos.CODEC.optionalFieldOf("entranceOffset", BlockPos.ZERO).forGetter(State::entranceOffset),
            ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("entranceDim", Level.OVERWORLD).forGetter(State::entranceDim),
            BlockPos.CODEC.listOf().optionalFieldOf("respawnPointOffsets", List.of()).forGetter(State::respawnPointOffsets),
            Codec.INT.optionalFieldOf("battleRadius", 64).forGetter(State::battleRadius),
            Codec.INT.optionalFieldOf("spawnDistance", 8).forGetter(State::spawnDistance),
            Codec.DOUBLE.optionalFieldOf("attributeScale", 0.1).forGetter(State::attributeScale),
            Codec.INT.optionalFieldOf("entityHighlightTime", 0).forGetter(State::entityHighlightTime),
            WaveTiming.CODEC.optionalFieldOf("timing", WaveTiming.DEFAULT).forGetter(State::timing),
            Cadence.CODEC.optionalFieldOf("cadence", Cadence.DEFAULT).forGetter(State::cadence),
            MobArenaMobData.CODEC.listOf().optionalFieldOf("mobs", List.of()).forGetter(State::mobs),
            MobArenaRewardData.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(State::rewards)
        ).apply(i, State::new));
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        State state = new State(groupId, entranceOffset, entranceDimension, new ArrayList<>(respawnPointOffsets),
            battleRadius, spawnDistance, attributeScale, entityHighlightTime,
            new WaveTiming(waveTimer, additionalTime, timeBetweenWaves, prepareTime, bossWaveAdditionalTime),
            new Cadence(bossEveryNWaves, eliteEveryNWaves, objectiveEveryNWaves, new ArrayList<>(enabledObjectives)),
            new ArrayList<>(mobs), new ArrayList<>(rewards));
        State.CODEC.encodeStart(registryLookup.createSerializationContext(NbtOps.INSTANCE), state)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save ArenaSpawner at {}: {}", worldPosition, err))
            .ifPresent(tag -> nbt.put("State", tag));
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        if (nbt.contains("State")) {
            State.CODEC.parse(registryLookup.createSerializationContext(NbtOps.INSTANCE), nbt.get("State"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load ArenaSpawner at {}: {}", worldPosition, err))
                .ifPresent(this::applyState);
        }
    }

    private void applyState(State state) {
        groupId = state.groupId();
        entranceOffset = state.entranceOffset();
        entranceDimension = state.entranceDim();
        respawnPointOffsets.clear();
        respawnPointOffsets.addAll(state.respawnPointOffsets());
        battleRadius = state.battleRadius();
        spawnDistance = state.spawnDistance();
        attributeScale = state.attributeScale();
        entityHighlightTime = state.entityHighlightTime();
        waveTimer = state.timing().waveTimer();
        additionalTime = state.timing().additionalTime();
        timeBetweenWaves = state.timing().timeBetweenWaves();
        prepareTime = state.timing().prepareTime();
        bossWaveAdditionalTime = state.timing().bossWaveAdditionalTime();
        bossEveryNWaves = state.cadence().bossEveryN();
        eliteEveryNWaves = state.cadence().eliteEveryN();
        objectiveEveryNWaves = state.cadence().objectiveEveryN();
        enabledObjectives.clear();
        enabledObjectives.addAll(state.cadence().objectives());
        mobs = new ArrayList<>(state.mobs());
        rewards = new ArrayList<>(state.rewards());
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

    // ── Screen factory (config editor) ──────────────────────────────────────────
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return net.minecraft.network.chat.Component.translatable("block.arenas_ld.arena_spawner");
    }

    @Nullable
    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int syncId, net.minecraft.world.entity.player.Inventory inventory, net.minecraft.world.entity.player.Player player) {
        return new net.ledok.arenas_ld.arena.screen.ArenaSpawnerScreenHandler(syncId, inventory, this);
    }

    @Override
    public net.ledok.arenas_ld.arena.screen.ArenaSpawnerMenuData getScreenOpeningData(net.minecraft.server.level.ServerPlayer player) {
        return new net.ledok.arenas_ld.arena.screen.ArenaSpawnerMenuData(worldPosition);
    }
}
