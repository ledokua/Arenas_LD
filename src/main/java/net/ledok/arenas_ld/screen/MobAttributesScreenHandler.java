package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.AttributeProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public class MobAttributesScreenHandler extends AbstractContainerMenu {
    public final AttributeProvider attributeProvider;
    public final BlockEntity blockEntity;
    public final List<AttributeData> attributes;

    public MobAttributesScreenHandler(int syncId, Inventory inventory, MobAttributesData data) {
        super(ModScreenHandlers.MOB_ATTRIBUTES_SCREEN_HANDLER.get(), syncId);
        this.blockEntity = inventory.player.level().getBlockEntity(data.pos());
        if (this.blockEntity instanceof AttributeProvider) {
            this.attributeProvider = (AttributeProvider) this.blockEntity;
            this.attributes = new ArrayList<>(this.attributeProvider.getAttributes());
        } else {
            throw new IllegalStateException("Block entity at " + data.pos() + " does not implement AttributeProvider");
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
