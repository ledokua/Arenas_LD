package net.ledok.arenas_ld.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

public class MobArenaMobData {
    public String mobId = "minecraft:zombie";
    public List<AttributeData> attributes = new ArrayList<>();
    public EquipmentData equipment = new EquipmentData();
    public int weight = 10;
    public int minWave = 1;
    public int maxWave = 100;

    public MobArenaMobData() {}

    public MobArenaMobData(String mobId, List<AttributeData> attributes, EquipmentData equipment, int weight, int minWave, int maxWave) {
        this.mobId = mobId;
        this.attributes = attributes;
        this.equipment = equipment;
        this.weight = weight;
        this.minWave = minWave;
        this.maxWave = maxWave;
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("MobId", mobId);
        
        ListTag attributeList = new ListTag();
        for (AttributeData attr : attributes) {
            attributeList.add(attr.toNbt());
        }
        tag.put("Attributes", attributeList);
        
        tag.put("Equipment", equipment.toNbt());
        tag.putInt("Weight", weight);
        tag.putInt("MinWave", minWave);
        tag.putInt("MaxWave", maxWave);
        return tag;
    }

    public static MobArenaMobData fromNbt(CompoundTag tag) {
        MobArenaMobData data = new MobArenaMobData();
        data.mobId = tag.getString("MobId");
        
        ListTag attributeList = tag.getList("Attributes", CompoundTag.TAG_COMPOUND);
        for (Tag t : attributeList) {
            data.attributes.add(AttributeData.fromNbt((CompoundTag) t));
        }
        
        if (tag.contains("Equipment")) {
            data.equipment = EquipmentData.fromNbt(tag.getCompound("Equipment"));
        }
        
        data.weight = tag.getInt("Weight");
        data.minWave = tag.getInt("MinWave");
        data.maxWave = tag.getInt("MaxWave");
        return data;
    }
}
