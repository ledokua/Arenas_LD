package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardEntryTest {

    @Test
    void codecRoundTripsPopulatedInstance() {
        LeaderboardEntry original = new LeaderboardEntry("Steve", 287, 1734567890123L);
        Tag encoded = LeaderboardEntry.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DataResult<LeaderboardEntry> decoded = LeaderboardEntry.CODEC.parse(NbtOps.INSTANCE, encoded);
        assertEquals(original, decoded.getOrThrow());
    }

    @Test
    void codecRoundTripsZeroTimeAndEpoch() {
        // Zero is a valid value (just-now, no-time-elapsed); make sure it round-trips.
        LeaderboardEntry original = new LeaderboardEntry("", 0, 0L);
        LeaderboardEntry decoded = LeaderboardEntry.CODEC.parse(NbtOps.INSTANCE,
            LeaderboardEntry.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow()
        ).getOrThrow();
        assertEquals(original, decoded);
    }

    @Test
    void recordsAreImmutableAndEqual() {
        LeaderboardEntry a = new LeaderboardEntry("Steve", 287, 1000L);
        LeaderboardEntry b = new LeaderboardEntry("Steve", 287, 1000L);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
