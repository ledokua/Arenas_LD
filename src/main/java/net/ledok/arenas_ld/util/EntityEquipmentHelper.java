package net.ledok.arenas_ld.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class EntityEquipmentHelper {
    private EntityEquipmentHelper() {
    }

    public static void applyEquipment(LivingEntity entity, EquipmentSlot slot, String itemId) {
        applyEquipment(entity, slot, itemId, false);
    }

    public static void applyEquipment(LivingEntity entity, EquipmentSlot slot, String itemId, boolean dropChance) {
        if (itemId == null || itemId.isEmpty()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null) {
            return;
        }
        entity.setItemSlot(slot, new ItemStack(item));
        if (entity instanceof Mob mob) {
            mob.setDropChance(slot, dropChance ? 1.0F : 0.0F);
        }
    }
}
