package net.ledok.arenas_ld.dungeon.blockentity;

import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.EquipmentData;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class EntityDefinitionTest {

    @BeforeAll
    static void bootstrap() {
        // EquipmentData.CODEC references ItemStack codecs, which require the registries to be loaded.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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

    @Test
    void waveDefaultsToOneWhenAbsentFromNbt() {
        // Pre-wave saves have no "wave" field; they must load as wave 1.
        Tag encoded = EntityDefinition.CODEC.encodeStart(NbtOps.INSTANCE,
            new EntityDefinition("minecraft:zombie", List.of(), new EquipmentData())).getOrThrow();
        ((net.minecraft.nbt.CompoundTag) encoded).remove("wave");
        EntityDefinition decoded = EntityDefinition.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(1, decoded.wave());
    }

    @Test
    void withWaveClampsToOne() {
        assertEquals(1, EntityDefinition.DEFAULT.withWave(0).wave());
        assertEquals(1, EntityDefinition.DEFAULT.withWave(-5).wave());
        assertEquals(3, EntityDefinition.DEFAULT.withWave(3).wave());
    }
}
