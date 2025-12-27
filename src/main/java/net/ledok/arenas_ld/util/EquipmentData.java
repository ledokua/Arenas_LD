package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

public class EquipmentData {
    public String head = "";
    public String chest = "";
    public String legs = "";
    public String feet = "";
    public String mainHand = "";
    public String offHand = "";
    public boolean dropChance = false;

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("head", head);
        nbt.putString("chest", chest);
        nbt.putString("legs", legs);
        nbt.putString("feet", feet);
        nbt.putString("mainHand", mainHand);
        nbt.putString("offHand", offHand);
        nbt.putBoolean("dropChance", dropChance);
        return nbt;
    }

    public static EquipmentData fromNbt(CompoundTag nbt) {
        EquipmentData data = new EquipmentData();
        data.head = nbt.getString("head");
        data.chest = nbt.getString("chest");
        data.legs = nbt.getString("legs");
        data.feet = nbt.getString("feet");
        data.mainHand = nbt.getString("mainHand");
        data.offHand = nbt.getString("offHand");
        data.dropChance = nbt.getBoolean("dropChance");
        return data;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(head);
        buf.writeUtf(chest);
        buf.writeUtf(legs);
        buf.writeUtf(feet);
        buf.writeUtf(mainHand);
        buf.writeUtf(offHand);
        buf.writeBoolean(dropChance);
    }

    public static EquipmentData read(FriendlyByteBuf buf) {
        EquipmentData data = new EquipmentData();
        data.head = buf.readUtf();
        data.chest = buf.readUtf();
        data.legs = buf.readUtf();
        data.feet = buf.readUtf();
        data.mainHand = buf.readUtf();
        data.offHand = buf.readUtf();
        data.dropChance = buf.readBoolean();
        return data;
    }
}
