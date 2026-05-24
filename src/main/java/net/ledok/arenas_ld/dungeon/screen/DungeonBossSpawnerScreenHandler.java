package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class DungeonBossSpawnerScreenHandler extends AbstractContainerMenu {
    private final DungeonBossSpawnerData data;

    public DungeonBossSpawnerScreenHandler(int syncId, Inventory playerInventory, DungeonBossSpawnerData data) {
        super(ModScreenHandlers.DUNGEON_BOSS_SPAWNER_SCREEN_HANDLER, syncId);
        this.data = data;
    }

    public DungeonBossSpawnerScreenHandler(int syncId, Inventory playerInventory, net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity blockEntity) {
        this(syncId, playerInventory, new DungeonBossSpawnerData(
            blockEntity.getBlockPos(),
            blockEntity.getEntityDefinition().mobId(),
            blockEntity.getRooms()
        ));
    }

    public BlockPos getBlockPos() { return data.blockPos(); }
    public String getMobId() { return data.mobId(); }
    public List<BlockPos> getRooms() { return data.rooms(); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        BlockPos pos = data.blockPos();
        return player.level().getBlockEntity(pos) instanceof net.ledok.arenas_ld.dungeon.blockentity.DungeonBossSpawnerBlockEntity
            && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
