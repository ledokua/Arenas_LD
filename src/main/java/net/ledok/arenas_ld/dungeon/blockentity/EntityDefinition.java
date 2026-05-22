package net.ledok.arenas_ld.dungeon.blockentity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;

import java.util.List;

/**
 * Immutable description of an entity that a spawner will produce. Pure data; spawning logic
 * lives on the spawner block entities.
 *
 * @param mobId      the entity type's registry id (e.g. "minecraft:husk"). Spawner falls back
 *                   to logging a warning and producing null if this is invalid.
 * @param attributes per-instance attribute overrides. Tier scaling (applied at spawn time) is
 *                   layered on top of these — see {@code MobSpawnerBlockEntity.spawnSingleScaled}.
 * @param equipment  per-slot item ids with drop chance. Empty strings = no item in that slot.
 */
public record EntityDefinition(
    String mobId,
    List<AttributeData> attributes,
    EquipmentData equipment
) {
    public static final EntityDefinition DEFAULT =
        new EntityDefinition("minecraft:husk", List.of(), new EquipmentData());

    public static final Codec<EntityDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("mobId").forGetter(EntityDefinition::mobId),
        AttributeData.CODEC.listOf().fieldOf("attributes").forGetter(EntityDefinition::attributes),
        EquipmentData.CODEC.fieldOf("equipment").forGetter(EntityDefinition::equipment)
    ).apply(i, EntityDefinition::new));

    public EntityDefinition withMobId(String newId) {
        return new EntityDefinition(newId, attributes, equipment);
    }

    public EntityDefinition withAttributes(List<AttributeData> newAttrs) {
        return new EntityDefinition(mobId, newAttrs, equipment);
    }

    public EntityDefinition withEquipment(EquipmentData newEquip) {
        return new EntityDefinition(mobId, attributes, newEquip);
    }
}
