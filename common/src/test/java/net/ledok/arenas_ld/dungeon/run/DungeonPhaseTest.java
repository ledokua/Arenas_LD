package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DungeonPhaseTest {

    @Test
    void hasExactlyFourValuesInExpectedOrder() {
        DungeonPhase[] values = DungeonPhase.values();
        assertEquals(4, values.length);
        assertEquals(DungeonPhase.STARTING, values[0]);
        assertEquals(DungeonPhase.RUNNING, values[1]);
        assertEquals(DungeonPhase.CLOSING, values[2]);
        assertEquals(DungeonPhase.DONE, values[3]);
    }

    @Test
    void codecRoundTripsEveryValue() {
        for (DungeonPhase phase : DungeonPhase.values()) {
            Tag encoded = DungeonPhase.CODEC.encodeStart(NbtOps.INSTANCE, phase).getOrThrow();
            DataResult<DungeonPhase> decoded = DungeonPhase.CODEC.parse(NbtOps.INSTANCE, encoded);
            assertEquals(phase, decoded.getOrThrow());
        }
    }

    @Test
    void codecDecodesUnknownStringAsDone() {
        DungeonPhase decoded = DungeonPhase.CODEC
            .parse(NbtOps.INSTANCE, StringTag.valueOf("CORRUPTED_PHASE"))
            .getOrThrow();
        assertEquals(DungeonPhase.DONE, decoded);
    }
}
