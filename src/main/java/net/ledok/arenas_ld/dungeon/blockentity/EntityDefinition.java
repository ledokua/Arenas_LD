package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;
import net.minecraft.core.BlockPos;

import java.util.List;

public record EntityDefinition(
    String mobId,
    List<AttributeData> attributes,
    EquipmentData equipment,
    int spawnCount,
    List<BlockPos> spawnOffsets
) {
    public static final EntityDefinition DEFAULT =
        new EntityDefinition("minecraft:husk", List.of(), new EquipmentData(), 1, List.of());

    public static final Codec<EntityDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("mobId").forGetter(EntityDefinition::mobId),
        AttributeData.CODEC.listOf().fieldOf("attributes").forGetter(EntityDefinition::attributes),
        EquipmentData.CODEC.fieldOf("equipment").forGetter(EntityDefinition::equipment),
        Codec.INT.optionalFieldOf("spawnCount", 1).forGetter(EntityDefinition::spawnCount),
        BlockPos.CODEC.listOf().optionalFieldOf("spawnOffsets", List.of()).forGetter(EntityDefinition::spawnOffsets)
    ).apply(i, EntityDefinition::new));

    /** Convenience constructor for legacy code; defaults spawnCount=1 and no offsets. */
    public EntityDefinition(String mobId, List<AttributeData> attributes, EquipmentData equipment) {
        this(mobId, attributes, equipment, 1, List.of());
    }

    public EntityDefinition withMobId(String newId) {
        return new EntityDefinition(newId, attributes, equipment, spawnCount, spawnOffsets);
    }

    public EntityDefinition withAttributes(List<AttributeData> newAttrs) {
        return new EntityDefinition(mobId, newAttrs, equipment, spawnCount, spawnOffsets);
    }

    public EntityDefinition withEquipment(EquipmentData newEquip) {
        return new EntityDefinition(mobId, attributes, newEquip, spawnCount, spawnOffsets);
    }

    public EntityDefinition withSpawnCount(int newCount) {
        return new EntityDefinition(mobId, attributes, equipment, newCount, spawnOffsets);
    }

    public EntityDefinition withSpawnOffsets(List<BlockPos> newOffsets) {
        return new EntityDefinition(mobId, attributes, equipment, spawnCount, newOffsets);
    }
}
