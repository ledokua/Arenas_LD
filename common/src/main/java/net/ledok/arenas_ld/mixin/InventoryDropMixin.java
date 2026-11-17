package net.ledok.arenas_ld.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.ledok.arenas_ld.util.RunPlayerSafety;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A full inventory while Arenas closes a player's screens for a run: the leftover goes to the inbox, not the ground. */
@Mixin(Inventory.class)
public abstract class InventoryDropMixin {
    @WrapOperation(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity arenasLd$keepForInbox(Player player, ItemStack stack, boolean randomly, Operation<ItemEntity> original) {
        if (RunPlayerSafety.catchDrop(player, stack)) {
            return null;
        }
        return original.call(player, stack, randomly);
    }
}
