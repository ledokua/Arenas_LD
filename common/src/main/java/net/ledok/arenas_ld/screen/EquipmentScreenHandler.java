package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Menu for the equipment editor. The six equipment slots are <em>ghost</em> slots: clicking one with
 * an item on the cursor stamps a copy as the template (the cursor item is never consumed); clicking
 * with an empty cursor clears it. The player's inventory is shown only so the player has items to
 * stamp from — those slots behave normally.
 */
public class EquipmentScreenHandler extends AbstractContainerMenu {
    public final EquipmentProvider equipmentProvider;
    public final BlockEntity blockEntity;
    public final SimpleContainer equipmentContainer = new SimpleContainer(EquipmentData.SLOT_COUNT);

    public EquipmentScreenHandler(int syncId, Inventory playerInventory, EquipmentScreenData data) {
        this(syncId, playerInventory, playerInventory.player.level().getBlockEntity(data.pos()));
    }

    public EquipmentScreenHandler(int syncId, Inventory playerInventory, BlockEntity blockEntity) {
        super(ModScreenHandlers.EQUIPMENT_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        if (blockEntity instanceof EquipmentProvider provider) {
            this.equipmentProvider = provider;
        } else {
            throw new IllegalStateException("BlockEntity is not an EquipmentProvider");
        }

        // Seed the ghost slots from the current templates (both sides read their synced BE).
        EquipmentData equip = equipmentProvider.getEquipment();
        for (int i = 0; i < EquipmentData.SLOT_COUNT; i++) {
            equipmentContainer.setItem(i, equip.items[i] == null ? ItemStack.EMPTY : equip.items[i].copy());
        }

        // Slots 0..5: ghost equipment slots. owo positions them via slotAsComponent.
        for (int i = 0; i < EquipmentData.SLOT_COUNT; i++) {
            addSlot(new Slot(equipmentContainer, i, 0, 0) {
                @Override public boolean mayPickup(Player player) { return false; }
                @Override public boolean mayPlace(ItemStack stack) { return false; }
            });
        }
        // Player inventory (27) + hotbar (9), positioned by owo.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 0, 0));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 0, 0));
        }
    }

    private boolean isGhostSlot(int slotId) {
        return slotId >= 0 && slotId < EquipmentData.SLOT_COUNT;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (isGhostSlot(slotId)) {
            ItemStack carried = getCarried();
            if (!carried.isEmpty()) {
                equipmentContainer.setItem(slotId, carried.copyWithCount(1));
            } else {
                equipmentContainer.setItem(slotId, ItemStack.EMPTY);
            }
            return; // never consume the cursor or move real items
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Snapshot of the current ghost-slot items, indexed by {@link EquipmentData#SLOTS}. */
    public ItemStack[] currentItems() {
        ItemStack[] items = new ItemStack[EquipmentData.SLOT_COUNT];
        for (int i = 0; i < EquipmentData.SLOT_COUNT; i++) {
            items[i] = equipmentContainer.getItem(i);
        }
        return items;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
