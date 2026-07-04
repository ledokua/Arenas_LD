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
    List<BlockPos> spawnOffsets,
    int wave
) {
    // spawnCount 0 means "unconfigured": spawnScaled still spawns one mob (Math.max(1, count)),
    // but the configurator's +1-per-placed-position arithmetic starts from zero, so placing
    // N spawn positions yields exactly N mobs instead of N+1.
    public static final EntityDefinition DEFAULT =
        new EntityDefinition("minecraft:husk", List.of(), new EquipmentData(), 0, List.of(), 1);

    public static final Codec<EntityDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("mobId").forGetter(EntityDefinition::mobId),
        AttributeData.CODEC.listOf().fieldOf("attributes").forGetter(EntityDefinition::attributes),
        EquipmentData.CODEC.fieldOf("equipment").forGetter(EntityDefinition::equipment),
        Codec.INT.optionalFieldOf("spawnCount", 0).forGetter(EntityDefinition::spawnCount),
        BlockPos.CODEC.listOf().optionalFieldOf("spawnOffsets", List.of()).forGetter(EntityDefinition::spawnOffsets),
        Codec.INT.optionalFieldOf("wave", 1).forGetter(EntityDefinition::wave)
    ).apply(i, EntityDefinition::new));

    /** Convenience constructor for legacy code; defaults spawnCount=1, no offsets, wave 1. */
    public EntityDefinition(String mobId, List<AttributeData> attributes, EquipmentData equipment) {
        this(mobId, attributes, equipment, 1, List.of(), 1);
    }

    public EntityDefinition withMobId(String newId) {
        return new EntityDefinition(newId, attributes, equipment, spawnCount, spawnOffsets, wave);
    }

    public EntityDefinition withAttributes(List<AttributeData> newAttrs) {
        return new EntityDefinition(mobId, newAttrs, equipment, spawnCount, spawnOffsets, wave);
    }

    public EntityDefinition withEquipment(EquipmentData newEquip) {
        return new EntityDefinition(mobId, attributes, newEquip, spawnCount, spawnOffsets, wave);
    }

    public EntityDefinition withSpawnCount(int newCount) {
        return new EntityDefinition(mobId, attributes, equipment, newCount, spawnOffsets, wave);
    }

    public EntityDefinition withSpawnOffsets(List<BlockPos> newOffsets) {
        return new EntityDefinition(mobId, attributes, equipment, spawnCount, newOffsets, wave);
    }

    public EntityDefinition withWave(int newWave) {
        return new EntityDefinition(mobId, attributes, equipment, spawnCount, spawnOffsets, Math.max(1, newWave));
    }
}
