package net.ledok.arenas_ld.block.entity;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.ledok.arenas_ld.registry.BlockEntitiesRegistry;
import net.ledok.arenas_ld.screen.MobArenaSpawnerData;
import net.ledok.arenas_ld.screen.MobArenaSpawnerScreenHandler;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MobArenaSpawnerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<MobArenaSpawnerData> {

    public int triggerRadius = 16;
    public int battleRadius = 64;
    public int spawnDistance = 8;
    public int waveTimer = 120;
    public int additionalTime = 5;
    public int timeBetweenWaves = 10;
    public double attributeScale = 0.1;
    
    public List<MobArenaMobData> mobs = new ArrayList<>();

    public MobArenaSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntitiesRegistry.MOB_ARENA_SPAWNER_BLOCK_ENTITY, pos, state);
    }

    public static void tick(Level world, BlockPos pos, BlockState state, MobArenaSpawnerBlockEntity be) {
        // Tick logic will be added later
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.saveAdditional(nbt, registryLookup);
        nbt.putInt("TriggerRadius", triggerRadius);
        nbt.putInt("BattleRadius", battleRadius);
        nbt.putInt("SpawnDistance", spawnDistance);
        nbt.putInt("WaveTimer", waveTimer);
        nbt.putInt("AdditionalTime", additionalTime);
        nbt.putInt("TimeBetweenWaves", timeBetweenWaves);
        nbt.putDouble("AttributeScale", attributeScale);
        
        ListTag mobsList = new ListTag();
        for (MobArenaMobData mob : mobs) {
            mobsList.add(mob.toNbt());
        }
        nbt.put("Mobs", mobsList);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registryLookup) {
        super.loadAdditional(nbt, registryLookup);
        triggerRadius = nbt.getInt("TriggerRadius");
        battleRadius = nbt.getInt("BattleRadius");
        spawnDistance = nbt.getInt("SpawnDistance");
        waveTimer = nbt.getInt("WaveTimer");
        additionalTime = nbt.getInt("AdditionalTime");
        timeBetweenWaves = nbt.getInt("TimeBetweenWaves");
        attributeScale = nbt.getDouble("AttributeScale");
        
        mobs.clear();
        if (nbt.contains("Mobs")) {
            ListTag mobsList = nbt.getList("Mobs", Tag.TAG_COMPOUND);
            for (Tag t : mobsList) {
                mobs.add(MobArenaMobData.fromNbt((CompoundTag) t));
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
    public Component getDisplayName() {return Component.translatable("container.arenas_ld.mob_arena_spawner_config");}

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new MobArenaSpawnerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public MobArenaSpawnerData getScreenOpeningData(ServerPlayer player) {return new MobArenaSpawnerData(this.worldPosition);}
}
