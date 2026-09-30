package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class MobSpawnerScreenHandler extends AbstractContainerMenu {
    private final BlockPos blockPos;
    private String mobId;
    private int spawnCount;
    private int wave;
    private List<BlockPos> spawnOffsets;
    private String name;

    public MobSpawnerScreenHandler(int syncId, Inventory playerInventory, MobSpawnerData data) {
        super(ModScreenHandlers.MOB_SPAWNER_SCREEN_HANDLER, syncId);
        this.blockPos = data.blockPos();
        this.mobId = data.mobId();
        this.spawnCount = data.spawnCount();
        this.wave = data.wave();
        this.spawnOffsets = data.spawnOffsets();
        this.name = data.name();
    }

    public MobSpawnerScreenHandler(int syncId, Inventory playerInventory, net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity blockEntity) {
        this(syncId, playerInventory, new MobSpawnerData(
            blockEntity.getBlockPos(),
            blockEntity.getEntityDefinition().mobId(),
            blockEntity.getEntityDefinition().spawnCount(),
            blockEntity.getEntityDefinition().wave(),
            blockEntity.getEntityDefinition().spawnOffsets(),
            blockEntity.getSpawnerName()
        ));
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public String getMobId() {
        return mobId;
    }

    public int getSpawnCount() {
        return spawnCount;
    }

    public int getWave() {
        return wave;
    }

    public List<BlockPos> getSpawnOffsets() {
        return spawnOffsets;
    }

    public String getName() {
        return name;
    }

    public void applyData(MobSpawnerData data) {
        this.mobId = data.mobId();
        this.spawnCount = data.spawnCount();
        this.wave = data.wave();
        this.spawnOffsets = data.spawnOffsets();
        this.name = data.name();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // No distance clause: the Dungeon Tool's gear button opens this screen from anywhere in
        // the room. The screen is op-gated and every mutation re-checks permission server-side;
        // only the block ceasing to exist closes it.
        return player.level().getBlockEntity(blockPos) instanceof net.ledok.arenas_ld.dungeon.blockentity.MobSpawnerBlockEntity;
    }
}
