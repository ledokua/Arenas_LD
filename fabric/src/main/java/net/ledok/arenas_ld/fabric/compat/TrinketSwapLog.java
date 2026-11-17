package net.ledok.arenas_ld.fabric.compat;

import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.event.TrinketEquipCallback;
import dev.emi.trinkets.api.event.TrinketUnequipCallback;
import net.ledok.arenas_ld.stats.RunEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Trinket slots (relics, accessories) into the run event log as {@code swap} events (4.37.0): taking one off is
 * "item → nothing", putting one on "nothing → item"; a swap within half a second merges into one. Server side only.
 */
public final class TrinketSwapLog {
    private TrinketSwapLog() {
    }

    public static void register() {
        TrinketUnequipCallback.EVENT.register((stack, slot, entity) -> log(entity, slot, stack, ItemStack.EMPTY));
        TrinketEquipCallback.EVENT.register((stack, slot, entity) -> log(entity, slot, ItemStack.EMPTY, stack));
    }

    private static void log(LivingEntity entity, SlotReference slot, ItemStack before, ItemStack after) {
        if (entity.level().isClientSide()) {
            return;
        }
        RunEvents.swap(entity, "trinket:" + slot.getId(), before, after);
    }
}
