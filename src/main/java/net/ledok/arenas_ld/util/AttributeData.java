package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

public record AttributeData(String id, double value, double maxValue) {
    
    public AttributeData(String id, double value) {
        this(id, value, Double.MAX_VALUE);
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putDouble("Value", value);
        tag.putDouble("MaxValue", maxValue);
        return tag;
    }

    public static AttributeData fromNbt(CompoundTag tag) {
        double max = tag.contains("MaxValue") ? tag.getDouble("MaxValue") : Double.MAX_VALUE;
        return new AttributeData(tag.getString("Id"), tag.getDouble("Value"), max);
    }
}
