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
    int wave,
    /** Extra mob data as SNBT (e.g. {@code {Size:3}} for a size-4 slime), applied before Arenas' own settings. */
    String nbt,
    /** Split parts' strength in percent of the size-scaled parent (slimes and magma cubes); 100 = proportional. */
    int splitStrength
) {
    // spawnCount 0 means "unconfigured": spawnScaled still spawns one mob (Math.max(1, count)),
    // but the configurator's +1-per-placed-position arithmetic starts from zero, so placing
    // N spawn positions yields exactly N mobs instead of N+1.
    public static final EntityDefinition DEFAULT =
        new EntityDefinition("minecraft:husk", List.of(), new EquipmentData(), 0, List.of(), 1, "", 100);

    public static final Codec<EntityDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("mobId").forGetter(EntityDefinition::mobId),
        AttributeData.CODEC.listOf().fieldOf("attributes").forGetter(EntityDefinition::attributes),
        EquipmentData.CODEC.fieldOf("equipment").forGetter(EntityDefinition::equipment),
        Codec.INT.optionalFieldOf("spawnCount", 0).forGetter(EntityDefinition::spawnCount),
        BlockPos.CODEC.listOf().optionalFieldOf("spawnOffsets", List.of()).forGetter(EntityDefinition::spawnOffsets),
        Codec.INT.optionalFieldOf("wave", 1).forGetter(EntityDefinition::wave),
        Codec.STRING.optionalFieldOf("nbt", "").forGetter(EntityDefinition::nbt),
        Codec.INT.optionalFieldOf("splitStrength", 100).forGetter(EntityDefinition::splitStrength)
    ).apply(i, EntityDefinition::new));

    /** Convenience constructor for legacy code; defaults spawnCount=1, no offsets, wave 1. */
    public EntityDefinition(String mobId, List<AttributeData> attributes, EquipmentData equipment) {
        this(mobId, attributes, equipment, 1, List.of(), 1, "", 100);
    }

    public EntityDefinition withMobId(String newId) {
        return new EntityDefinition(newId, attributes, equipment, spawnCount, spawnOffsets, wave, nbt, splitStrength);
    }

    public EntityDefinition withAttributes(List<AttributeData> newAttrs) {
        return new EntityDefinition(mobId, newAttrs, equipment, spawnCount, spawnOffsets, wave, nbt, splitStrength);
    }

    public EntityDefinition withEquipment(EquipmentData newEquip) {
        return new EntityDefinition(mobId, attributes, newEquip, spawnCount, spawnOffsets, wave, nbt, splitStrength);
    }

    public EntityDefinition withSpawnCount(int newCount) {
        return new EntityDefinition(mobId, attributes, equipment, newCount, spawnOffsets, wave, nbt, splitStrength);
    }

    public EntityDefinition withSpawnOffsets(List<BlockPos> newOffsets) {
        return new EntityDefinition(mobId, attributes, equipment, spawnCount, newOffsets, wave, nbt, splitStrength);
    }

    public EntityDefinition withWave(int newWave) {
        return new EntityDefinition(mobId, attributes, equipment, spawnCount, spawnOffsets, Math.max(1, newWave), nbt, splitStrength);
    }

    /** The extra mob data (SNBT) and the split parts' strength (percent, 1–1000). */
    public EntityDefinition withSummonData(String newNbt, int newSplitStrength) {
        return new EntityDefinition(mobId, attributes, equipment, spawnCount, spawnOffsets, wave,
            newNbt == null ? "" : newNbt, Math.max(1, Math.min(1000, newSplitStrength)));
    }
}
