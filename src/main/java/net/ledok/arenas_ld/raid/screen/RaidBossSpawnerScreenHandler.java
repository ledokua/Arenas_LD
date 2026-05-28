package net.ledok.arenas_ld.raid.screen;

import net.ledok.arenas_ld.raid.blockentity.RaidBossSpawnerBlockEntity;
import net.ledok.arenas_ld.screen.ModScreenHandlers;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

public class RaidBossSpawnerScreenHandler extends AbstractContainerMenu {
    public final RaidBossSpawnerBlockEntity blockEntity;
    public final Player player;

    // MOJANG MAPPINGS: PlayerInventory is now Inventory.
    public RaidBossSpawnerScreenHandler(int syncId, Inventory playerInventory, RaidBossSpawnerData data) {
        this(syncId, playerInventory, (RaidBossSpawnerBlockEntity) playerInventory.player.level().getBlockEntity(data.blockPos()));
    }

    public RaidBossSpawnerScreenHandler(int syncId, Inventory playerInventory, RaidBossSpawnerBlockEntity blockEntity) {
        super(ModScreenHandlers.RAID_BOSS_SPAWNER_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.player = playerInventory.player;
    }

    // MOJANG MAPPINGS: quickMove is now quickMoveStack, PlayerEntity is Player.
    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    // MOJANG MAPPINGS: canUse is now stillValid.
    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
