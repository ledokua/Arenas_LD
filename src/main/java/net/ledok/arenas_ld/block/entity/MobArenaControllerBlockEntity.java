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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MobArenaControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<MobArenaControllerData> {
    public BlockPos arenaSpawnerPos = BlockPos.ZERO;
    public ResourceKey<Level> arenaSpawnerDimension = Level.OVERWORLD;
    public Set<UUID> partyMembers = new HashSet<>();
    public boolean isLocked = false;
    public int currentWave = 0;
    public List<LeaderboardEntry> leaderboard = new ArrayList<>();

    public MobArenaControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_ARENA_CONTROLLER_BLOCK_ENTITY, pos, state);
    }

    public void reset() {
        partyMembers.clear();
        isLocked = false;
        currentWave = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putLong("ArenaSpawnerPos", arenaSpawnerPos.asLong());
        nbt.putString("ArenaSpawnerDimension", arenaSpawnerDimension.location().toString());
        nbt.putBoolean("IsLocked", isLocked);
        nbt.putInt("CurrentWave", currentWave);

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
