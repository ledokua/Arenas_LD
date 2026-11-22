package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
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
    public int maxWave = Integer.MAX_VALUE;
    public boolean isBoss = false;
    /** Extra mob data as SNBT, applied before Arenas' own settings (see {@code EntityDefinition.nbt}). */
    public String nbt = "";
    /** Split parts' strength in percent of the size-scaled parent (slimes and magma cubes). */
    public int splitStrength = 100;

    public static final Codec<MobArenaMobData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.optionalFieldOf("mobId", "minecraft:zombie").forGetter(d -> d.mobId),
        AttributeData.CODEC.listOf().optionalFieldOf("attributes", List.of()).forGetter(d -> d.attributes),
        EquipmentData.CODEC.optionalFieldOf("equipment", new EquipmentData()).forGetter(d -> d.equipment),
        Codec.INT.optionalFieldOf("weight", 10).forGetter(d -> d.weight),
        Codec.INT.optionalFieldOf("minWave", 1).forGetter(d -> d.minWave),
        Codec.INT.optionalFieldOf("maxWave", 100).forGetter(d -> d.maxWave),
        Codec.BOOL.optionalFieldOf("isBoss", false).forGetter(d -> d.isBoss),
        Codec.STRING.optionalFieldOf("nbt", "").forGetter(d -> d.nbt),
        Codec.INT.optionalFieldOf("splitStrength", 100).forGetter(d -> d.splitStrength)
    ).apply(instance, (mobId, attributes, equipment, weight, minWave, maxWave, isBoss, nbt, split) -> {
        MobArenaMobData data = new MobArenaMobData(mobId, attributes, equipment, weight, minWave, maxWave, isBoss);
        data.nbt = nbt;
        data.splitStrength = split;
        return data;
    }));

    public MobArenaMobData() {}

    public MobArenaMobData(String mobId, List<AttributeData> attributes, EquipmentData equipment, int weight, int minWave, int maxWave, boolean isBoss) {
        this.mobId = mobId;
        this.attributes = new ArrayList<>(attributes);
        this.equipment = equipment;
        this.weight = weight;
        this.minWave = minWave;
        this.maxWave = maxWave;
        this.isBoss = isBoss;
    }

    public CompoundTag toNbt(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("MobId", mobId);

        ListTag attributeList = new ListTag();
        for (AttributeData attr : attributes) {
            attributeList.add(attr.toNbt());
        }
        tag.put("Attributes", attributeList);

        tag.put("Equipment", equipment.toNbt(registries));
        tag.putInt("Weight", weight);
        tag.putInt("MinWave", minWave);
        tag.putInt("MaxWave", maxWave);
        tag.putBoolean("IsBoss", isBoss);
        if (!nbt.isEmpty()) {
            tag.putString("Nbt", nbt);
        }
        if (splitStrength != 100) {
            tag.putInt("SplitStrength", splitStrength);
        }
        return tag;
    }

    public static MobArenaMobData fromNbt(HolderLookup.Provider registries, CompoundTag tag) {
        MobArenaMobData data = new MobArenaMobData();
        data.mobId = tag.getString("MobId");

        ListTag attributeList = tag.getList("Attributes", CompoundTag.TAG_COMPOUND);
        for (Tag t : attributeList) {
            data.attributes.add(AttributeData.fromNbt((CompoundTag) t));
        }

        if (tag.contains("Equipment")) {
            data.equipment = EquipmentData.fromNbt(registries, tag.getCompound("Equipment"));
        }

        data.weight = tag.getInt("Weight");
        data.minWave = tag.getInt("MinWave");
        data.maxWave = tag.getInt("MaxWave");
        data.isBoss = tag.getBoolean("IsBoss");
        data.nbt = tag.getString("Nbt");
        data.splitStrength = tag.contains("SplitStrength") ? tag.getInt("SplitStrength") : 100;
        return data;
    }

    /** A copy with everything, the extra data included. */
    public MobArenaMobData copy() {
        MobArenaMobData data = new MobArenaMobData(mobId, attributes, equipment, weight, minWave, maxWave, isBoss);
        data.nbt = nbt;
        data.splitStrength = splitStrength;
        return data;
    }
}
