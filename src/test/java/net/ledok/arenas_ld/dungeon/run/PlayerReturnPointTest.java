package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerReturnPointTest {

    @Test
    void codecRoundTripsPopulatedInstance() {
        PlayerReturnPoint original = new PlayerReturnPoint(
            Level.OVERWORLD,
            new Vec3(123.5, 64.0, -47.875),
            180.0f,
            -10.5f
        );
        Tag encoded = PlayerReturnPoint.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DataResult<PlayerReturnPoint> decoded = PlayerReturnPoint.CODEC.parse(NbtOps.INSTANCE, encoded);
        assertEquals(original, decoded.getOrThrow());
    }

    @Test
    void codecRoundTripsNonOverworldDimension() {
        ResourceKey<Level> custom = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath("arenas_ld", "test_dim")
        );
        PlayerReturnPoint original = new PlayerReturnPoint(
            custom,
            new Vec3(0.0, 100.0, 0.0),
            0.0f,
            0.0f
        );
        PlayerReturnPoint decoded = PlayerReturnPoint.CODEC.parse(NbtOps.INSTANCE,
            PlayerReturnPoint.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow()
        ).getOrThrow();
        assertEquals(custom, decoded.dimension());
        assertEquals(new Vec3(0.0, 100.0, 0.0), decoded.pos());
    }

    @Test
    void preservesYawAndPitchPrecision() {
        PlayerReturnPoint original = new PlayerReturnPoint(
            Level.OVERWORLD,
            Vec3.ZERO,
            47.123f,
            -89.456f
        );
        PlayerReturnPoint decoded = PlayerReturnPoint.CODEC.parse(NbtOps.INSTANCE,
            PlayerReturnPoint.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow()
        ).getOrThrow();
        assertEquals(47.123f, decoded.yaw());
        assertEquals(-89.456f, decoded.pitch());
    }
}
