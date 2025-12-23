package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;

public class EquipmentData {
    public String head = "";
    public String chest = "";
    public String legs = "";
    public String feet = "";
    public String mainHand = "";
    public String offHand = "";
    public boolean dropChance = false;

    public EquipmentData() {}

    public EquipmentData(String head, String chest, String legs, String feet, String mainHand, String offHand, boolean dropChance) {
        this.head = head;
        this.chest = chest;
        this.legs = legs;
        this.feet = feet;
        this.mainHand = mainHand;
        this.offHand = offHand;
        this.dropChance = dropChance;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Head", head);
        tag.putString("Chest", chest);
        tag.putString("Legs", legs);
        tag.putString("Feet", feet);
        tag.putString("MainHand", mainHand);
        tag.putString("OffHand", offHand);
        tag.putBoolean("DropChance", dropChance);
        return tag;
    }

    public static EquipmentData fromNbt(CompoundTag tag) {
        EquipmentData data = new EquipmentData();
        if (tag.contains("Head")) data.head = tag.getString("Head");
        if (tag.contains("Chest")) data.chest = tag.getString("Chest");
        if (tag.contains("Legs")) data.legs = tag.getString("Legs");
        if (tag.contains("Feet")) data.feet = tag.getString("Feet");
        if (tag.contains("MainHand")) data.mainHand = tag.getString("MainHand");
        if (tag.contains("OffHand")) data.offHand = tag.getString("OffHand");
        if (tag.contains("DropChance")) data.dropChance = tag.getBoolean("DropChance");
        return data;
    }
}
