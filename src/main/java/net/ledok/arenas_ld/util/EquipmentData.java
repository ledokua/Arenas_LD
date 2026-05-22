package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public class EquipmentData {
    public static final Codec<EquipmentData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("head", "").forGetter(data -> data.head),
        Codec.STRING.optionalFieldOf("chest", "").forGetter(data -> data.chest),
        Codec.STRING.optionalFieldOf("legs", "").forGetter(data -> data.legs),
        Codec.STRING.optionalFieldOf("feet", "").forGetter(data -> data.feet),
        Codec.STRING.optionalFieldOf("mainHand", "").forGetter(data -> data.mainHand),
        Codec.STRING.optionalFieldOf("offHand", "").forGetter(data -> data.offHand),
        Codec.BOOL.optionalFieldOf("dropChance", false).forGetter(data -> data.dropChance)
    ).apply(instance, EquipmentData::new));

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EquipmentData that)) return false;
        return dropChance == that.dropChance
            && Objects.equals(head, that.head)
            && Objects.equals(chest, that.chest)
            && Objects.equals(legs, that.legs)
            && Objects.equals(feet, that.feet)
            && Objects.equals(mainHand, that.mainHand)
            && Objects.equals(offHand, that.offHand);
    }

    @Override
    public int hashCode() {
        return Objects.hash(head, chest, legs, feet, mainHand, offHand, dropChance);
    }
}
