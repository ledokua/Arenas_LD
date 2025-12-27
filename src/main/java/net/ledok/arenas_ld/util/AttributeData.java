package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public record AttributeData(String id, double value) {
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("id", id);
        nbt.putDouble("value", value);
        return nbt;
    }

    public static AttributeData fromNbt(CompoundTag nbt) {
        return new AttributeData(nbt.getString("id"), nbt.getDouble("value"));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeDouble(value);
    }

    public static AttributeData read(FriendlyByteBuf buf) {
        return new AttributeData(buf.readUtf(), buf.readDouble());
    }
}
