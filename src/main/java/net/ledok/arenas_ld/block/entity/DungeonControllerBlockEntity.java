package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.screen.DungeonControllerData;
import net.ledok.arenas_ld.screen.DungeonControllerScreenHandler;
import net.ledok.arenas_ld.util.DungeonLeaderboardEntry;
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

public class DungeonControllerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<DungeonControllerData> {
    public BlockPos dungeonSpawnerPos = BlockPos.ZERO;
    public ResourceKey<Level> dungeonSpawnerDimension = Level.OVERWORLD;
    public Set<UUID> partyMembers = new HashSet<>();
    public boolean isLocked = false;
    public int remainingDungeonTimeSeconds = 0;
    public int dungeonCooldownSeconds = 0;
    public List<DungeonLeaderboardEntry> leaderboard = new ArrayList<>();

    public DungeonControllerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.DUNGEON_CONTROLLER_BLOCK_ENTITY, pos, state);
    }

    public void reset() {
        partyMembers.clear();
        isLocked = false;
        remainingDungeonTimeSeconds = 0;
        dungeonCooldownSeconds = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putLong("DungeonSpawnerPos", dungeonSpawnerPos.asLong());
        nbt.putString("DungeonSpawnerDimension", dungeonSpawnerDimension.location().toString());
        nbt.putBoolean("IsLocked", isLocked);
        nbt.putInt("RemainingDungeonTimeSeconds", remainingDungeonTimeSeconds);
        nbt.putInt("DungeonCooldownSeconds", dungeonCooldownSeconds);

        ListTag membersList = new ListTag();
        for (UUID uuid : partyMembers) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("uuid", uuid);
            membersList.add(tag);
        }
        nbt.put("PartyMembers", membersList);

        ListTag leaderboardList = new ListTag();
        for (DungeonLeaderboardEntry entry : leaderboard) {
            leaderboardList.add(entry.toNbt());
        }
        nbt.put("Leaderboard", leaderboardList);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        dungeonSpawnerPos = BlockPos.of(nbt.getLong("DungeonSpawnerPos"));
        dungeonSpawnerDimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString("DungeonSpawnerDimension")));
        isLocked = nbt.getBoolean("IsLocked");
        remainingDungeonTimeSeconds = nbt.getInt("RemainingDungeonTimeSeconds");
        dungeonCooldownSeconds = nbt.getInt("DungeonCooldownSeconds");

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
                leaderboard.add(DungeonLeaderboardEntry.fromNbt((CompoundTag) t));
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
        return Component.translatable("container.arenas_ld.dungeon_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new DungeonControllerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public DungeonControllerData getScreenOpeningData(ServerPlayer player) {
        return new DungeonControllerData(this.worldPosition);
    }
}
