package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DungeonOutcomeTest {

    @Test
    void hasExactlyFiveValuesInExpectedOrder() {
        DungeonOutcome[] values = DungeonOutcome.values();
        assertEquals(5, values.length);
        assertEquals(DungeonOutcome.IN_PROGRESS, values[0]);
        assertEquals(DungeonOutcome.WIN, values[1]);
        assertEquals(DungeonOutcome.LOSS_TIMEOUT, values[2]);
        assertEquals(DungeonOutcome.LOSS_ABANDONED, values[3]);
        assertEquals(DungeonOutcome.LOSS_FORCED, values[4]);
    }

    @Test
    void codecRoundTripsEveryValue() {
        for (DungeonOutcome outcome : DungeonOutcome.values()) {
            Tag encoded = DungeonOutcome.CODEC.encodeStart(NbtOps.INSTANCE, outcome).getOrThrow();
            DataResult<DungeonOutcome> decoded = DungeonOutcome.CODEC.parse(NbtOps.INSTANCE, encoded);
            assertEquals(outcome, decoded.getOrThrow());
        }
    }

    @Test
    void codecDecodesUnknownStringAsInProgress() {
        DungeonOutcome decoded = DungeonOutcome.CODEC
            .parse(NbtOps.INSTANCE, StringTag.valueOf("CORRUPTED_OUTCOME"))
            .getOrThrow();
        assertEquals(DungeonOutcome.IN_PROGRESS, decoded);
    }

    @Test
    void isLossTrueForAllLossVariants() {
        assertTrue(DungeonOutcome.LOSS_TIMEOUT.isLoss());
        assertTrue(DungeonOutcome.LOSS_ABANDONED.isLoss());
        assertTrue(DungeonOutcome.LOSS_FORCED.isLoss());
    }

    @Test
    void isLossFalseForInProgressAndWin() {
        assertFalse(DungeonOutcome.IN_PROGRESS.isLoss());
        assertFalse(DungeonOutcome.WIN.isLoss());
    }
}
