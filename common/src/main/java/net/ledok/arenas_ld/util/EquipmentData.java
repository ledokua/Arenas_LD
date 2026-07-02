package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * A mob's configured equipment: a template {@link ItemStack} per slot (full components preserved)
 * plus a 0–100 spawn chance per slot, and a flag governing the mob's natural loot-table drops.
 *
 * <p>Slot order is fixed ({@link #SLOTS}) so the items/chances arrays line up with the editor and
 * the network/persistence codecs.
 */
public class EquipmentData {
    /** Fixed slot order shared by the editor, codecs and {@link EntityEquipmentHelper}. */
    public static final EquipmentSlot[] SLOTS = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
        EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    public static final int SLOT_COUNT = SLOTS.length;

    public static final Codec<EquipmentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", java.util.Collections.emptyList())
            .forGetter(d -> java.util.List.of(d.items)),
        Codec.INT.listOf().optionalFieldOf("chances", java.util.Collections.emptyList())
            .forGetter(d -> {
                java.util.List<Integer> list = new java.util.ArrayList<>(SLOT_COUNT);
                for (int c : d.chances) list.add(c);
                return list;
            }),
        Codec.BOOL.optionalFieldOf("dropChance", false).forGetter(d -> d.dropChance)
    ).apply(instance, EquipmentData::fromLists));

    public final ItemStack[] items = new ItemStack[SLOT_COUNT];
    public final int[] chances = new int[SLOT_COUNT];
    public boolean dropChance = false;

    public EquipmentData() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            items[i] = ItemStack.EMPTY;
            chances[i] = 100;
        }
    }

    public EquipmentData(ItemStack[] items, int[] chances, boolean dropChance) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            this.items[i] = i < items.length && items[i] != null ? items[i] : ItemStack.EMPTY;
            this.chances[i] = clampChance(i < chances.length ? chances[i] : 100);
        }
        this.dropChance = dropChance;
    }

    private static EquipmentData fromLists(java.util.List<ItemStack> items, java.util.List<Integer> chances, boolean dropChance) {
        EquipmentData data = new EquipmentData();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i < items.size() && items.get(i) != null) data.items[i] = items.get(i);
            if (i < chances.size() && chances.get(i) != null) data.chances[i] = clampChance(chances.get(i));
        }
        data.dropChance = dropChance;
        return data;
    }

    private static int clampChance(int c) {
        return Math.max(0, Math.min(100, c));
    }

    public CompoundTag toNbt(HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return (CompoundTag) CODEC.encodeStart(ops, this).result().orElseGet(CompoundTag::new);
    }

    public static EquipmentData fromNbt(HolderLookup.Provider registries, CompoundTag tag) {
        if (tag == null) return new EquipmentData();
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        return CODEC.parse(ops, tag).result().orElseGet(EquipmentData::new);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentData that)) return false;
        if (dropChance != that.dropChance) return false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (chances[i] != that.chances[i]) return false;
            if (!ItemStack.matches(items[i], that.items[i])) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(dropChance);
        for (int i = 0; i < SLOT_COUNT; i++) {
            result = 31 * result + chances[i];
            result = 31 * result + (items[i].isEmpty() ? 0 : items[i].getItem().hashCode());
        }
        return result;
    }
}
