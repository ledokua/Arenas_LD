package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
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

public class RoomControllerBlockEntity extends BlockEntity {

    // ---- Fields ----

    private final List<BlockPos> spawnerPositions = new ArrayList<>();
    @Nullable private BlockPos doorPos = null;
    private final Set<UUID> aliveMobs = new HashSet<>();
    private boolean activated = false;
    private boolean cleared = false;

    // ---- Construction ----

    public RoomControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.ROOM_CONTROLLER_BLOCK_ENTITY, pos, state);
    }

    // ---- Getters ----

    public List<BlockPos> getSpawnerPositions() {
        return Collections.unmodifiableList(spawnerPositions);
    }

    @Nullable
    public BlockPos getDoorPos() {
        return doorPos;
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
    public boolean addSpawner(BlockPos pos) {
        if (spawnerPositions.contains(pos)) {
            return false;
        }
        spawnerPositions.add(pos);
        setChanged();
        return true;
    }

    /** Returns true if the spawner was removed (false if not present). */
    public boolean removeSpawner(BlockPos pos) {
        boolean removed = spawnerPositions.remove(pos);
        if (removed) {
            setChanged();
        }
        return removed;
    }

    public void clearSpawners() {
        if (!spawnerPositions.isEmpty()) {
            spawnerPositions.clear();
            setChanged();
        }
    }

    public void setDoorPos(@Nullable BlockPos pos) {
        if (!Objects.equals(doorPos, pos)) {
            this.doorPos = pos;
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

    // ---- NBT ----

    // Internal serialization record; never exposed outside the class.
    private record State(
        List<BlockPos> spawners,
        Optional<BlockPos> door,
        Set<UUID> aliveMobs,
        boolean activated,
        boolean cleared
    ) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.listOf().fieldOf("spawners").forGetter(State::spawners),
            BlockPos.CODEC.optionalFieldOf("door").forGetter(State::door),
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
        State state = new State(spawnerPositions, Optional.ofNullable(doorPos), aliveMobs, activated, cleared);
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
                    spawnerPositions.clear();
                    spawnerPositions.addAll(state.spawners());
                    doorPos = state.door().orElse(null);
                    aliveMobs.clear();
                    aliveMobs.addAll(state.aliveMobs());
                    activated = state.activated();
                    cleared = state.cleared();
                });
        }
    }
}
