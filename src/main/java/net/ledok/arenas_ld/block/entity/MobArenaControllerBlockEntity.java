package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.screen.MobArenaControllerData;
import net.ledok.arenas_ld.screen.MobArenaControllerScreenHandler;
import net.ledok.arenas_ld.util.LeaderboardEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MobArenaControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<MobArenaControllerData> {
    public record ControllerKey(BlockPos pos, ResourceKey<Level> dimension) {}
    private static final Set<ControllerKey> CONTROLLERS = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private BlockPos arenaSpawnerPos = BlockPos.ZERO;
    private ResourceKey<Level> arenaSpawnerDimension = Level.OVERWORLD;
    private final Set<UUID> partyMembers = new HashSet<>();
    private boolean isLocked = false;
    private int currentWave = 0;
    private boolean hardcoreEnabled = false;
    private long rewardCurrencyPerWave = 0L;
    private List<LeaderboardEntry> leaderboard = new ArrayList<>();

    public MobArenaControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_ARENA_CONTROLLER_BLOCK_ENTITY, pos, state);
    }

    public static Set<ControllerKey> getControllers() {
        return CONTROLLERS;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide) {
            CONTROLLERS.add(new ControllerKey(worldPosition, level.dimension()));
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            CONTROLLERS.remove(new ControllerKey(worldPosition, level.dimension()));
        }
        super.setRemoved();
    }

    public void reset() {
        partyMembers.clear();
        isLocked = false;
        currentWave = 0;
        markDirtyAndSync();
    }

    public BlockPos getArenaSpawnerPos() {
        return arenaSpawnerPos;
    }

    public ResourceKey<Level> getArenaSpawnerDimension() {
        return arenaSpawnerDimension;
    }

    public boolean hasLinkedSpawner() {
        return !arenaSpawnerPos.equals(BlockPos.ZERO);
    }

    public void setLinkedSpawner(BlockPos spawnerPos, ResourceKey<Level> spawnerDimension) {
        this.arenaSpawnerPos = spawnerPos;
        this.arenaSpawnerDimension = spawnerDimension;
        markDirtyAndSync();
    }

    public void clearLinkedSpawner() {
        this.arenaSpawnerPos = BlockPos.ZERO;
        this.arenaSpawnerDimension = Level.OVERWORLD;
        markDirtyAndSync();
    }

    public Set<UUID> getPartyMembers() {
        return Collections.unmodifiableSet(partyMembers);
    }

    public boolean isPartyMember(UUID playerId) {
        return partyMembers.contains(playerId);
    }

    public boolean addPartyMember(UUID playerId) {
        boolean changed = partyMembers.add(playerId);
        if (changed) {
            markDirtyAndSync();
        }
        return changed;
    }

    public boolean removePartyMember(UUID playerId) {
        boolean changed = partyMembers.remove(playerId);
        if (changed) {
            markDirtyAndSync();
        }
        return changed;
    }

    public void setPartyMembers(Set<UUID> newMembers) {
        partyMembers.clear();
        partyMembers.addAll(newMembers);
        markDirtyAndSync();
    }

    public void clearPartyMembers() {
        if (!partyMembers.isEmpty()) {
            partyMembers.clear();
            markDirtyAndSync();
        }
    }

    public boolean isLocked() {
        return isLocked;
    }

    public boolean isArenaActive() {
        return isLocked || currentWave > 0;
    }

    public void setLocked(boolean locked) {
        if (this.isLocked != locked) {
            this.isLocked = locked;
            markDirtyAndSync();
        }
    }

    public int getCurrentWave() {
        return currentWave;
    }

    public void setCurrentWave(int currentWave) {
        if (this.currentWave != currentWave) {
            this.currentWave = currentWave;
            markDirtyAndSync();
        }
    }

    public boolean isHardcoreEnabled() {
        return hardcoreEnabled;
    }

    public void setHardcoreEnabled(boolean hardcoreEnabled) {
        if (this.hardcoreEnabled != hardcoreEnabled) {
            this.hardcoreEnabled = hardcoreEnabled;
            markDirtyAndSync();
        }
    }

    public long getRewardCurrencyPerWave() {
        return Math.max(0L, rewardCurrencyPerWave);
    }

    public void setRewardCurrencyPerWave(long amount) {
        long clamped = Math.max(0L, amount);
        if (this.rewardCurrencyPerWave != clamped) {
            this.rewardCurrencyPerWave = clamped;
            markDirtyAndSync();
        }
    }

    public List<LeaderboardEntry> getLeaderboard() {
        return Collections.unmodifiableList(leaderboard);
    }

    public void setLeaderboard(List<LeaderboardEntry> leaderboard) {
        this.leaderboard = new ArrayList<>(leaderboard);
        markDirtyAndSync();
    }

    public void markDirtyAndSync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putLong("ArenaSpawnerPos", arenaSpawnerPos.asLong());
        nbt.putString("ArenaSpawnerDimension", arenaSpawnerDimension.location().toString());
        nbt.putBoolean("IsLocked", isLocked);
        nbt.putInt("CurrentWave", currentWave);
        nbt.putBoolean("HardcoreEnabled", hardcoreEnabled);
        nbt.putLong("RewardCurrencyPerWave", rewardCurrencyPerWave);

        ListTag membersList = new ListTag();
        for (UUID uuid : partyMembers) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            membersList.add(tag);
        }
        nbt.put("PartyMembers", membersList);

        ListTag leaderboardList = new ListTag();
        for (LeaderboardEntry entry : leaderboard) {
            leaderboardList.add(entry.toNbt());
        }
        nbt.put("Leaderboard", leaderboardList);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        arenaSpawnerPos = BlockPos.of(nbt.getLong("ArenaSpawnerPos"));
        arenaSpawnerDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("ArenaSpawnerDimension")));
        isLocked = nbt.getBoolean("IsLocked");
        currentWave = nbt.getInt("CurrentWave");
        hardcoreEnabled = nbt.getBoolean("HardcoreEnabled");
        rewardCurrencyPerWave = nbt.contains("RewardCurrencyPerWave") ? Math.max(0L, nbt.getLong("RewardCurrencyPerWave")) : 0L;

        partyMembers.clear();
        if (nbt.contains("PartyMembers")) {
            ListTag membersList = nbt.getList("PartyMembers", Tag.TAG_COMPOUND);
            for (Tag t : membersList) {
                partyMembers.add(((CompoundTag) t).getUUID("uuid"));
            }
        }

        leaderboard.clear();
        if (nbt.contains("Leaderboard")) {
            ListTag leaderboardList = nbt.getList("Leaderboard", Tag.TAG_COMPOUND);
            for (Tag t : leaderboardList) {
                leaderboard.add(LeaderboardEntry.fromNbt((CompoundTag) t));
            }
        }
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

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arenas_ld.mob_arena_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new MobArenaControllerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public MobArenaControllerData getScreenOpeningData(ServerPlayer player) {
        return new MobArenaControllerData(this.worldPosition);
    }
}
