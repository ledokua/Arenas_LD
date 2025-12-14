package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

public record AttributeData(String id, double value) {
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putDouble("Value", value);
        return tag;
    }

    public static AttributeData fromNbt(CompoundTag tag) {
        return new AttributeData(tag.getString("Id"), tag.getDouble("Value"));
    }
}
