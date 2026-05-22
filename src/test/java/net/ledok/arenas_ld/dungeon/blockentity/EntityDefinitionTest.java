package net.ledok.arenas_ld.dungeon.blockentity;

import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class EntityDefinitionTest {

    @Test
    void defaultHasExpectedValues() {
        assertEquals("minecraft:husk", EntityDefinition.DEFAULT.mobId());
        assertEquals(List.of(), EntityDefinition.DEFAULT.attributes());
    }

    @Test
    void codecRoundTripsPopulatedInstance() {
        EntityDefinition original = new EntityDefinition(
            "minecraft:zombie",
            List.of(new AttributeData("minecraft:generic.max_health", 50.0)),
            new EquipmentData()
        );
        Tag encoded = EntityDefinition.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        EntityDefinition decoded = EntityDefinition.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(original, decoded);
    }

    @Test
    void withMobIdProducesNewInstance() {
        EntityDefinition a = EntityDefinition.DEFAULT;
        EntityDefinition b = a.withMobId("minecraft:skeleton");
        assertNotSame(a, b);
        assertEquals("minecraft:husk", a.mobId());
        assertEquals("minecraft:skeleton", b.mobId());
    }
}
