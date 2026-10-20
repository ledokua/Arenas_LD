package net.ledok.arenas_ld.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The order safe spots are tried in around a controller (the block checks need a world). */
class SafeSpotTest {

    @Test
    void sidesComeBeforeDiagonalsClockwiseFromNorth() {
        List<int[]> ring = SafeSpot.ring(1);
        int[][] expected = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}, {1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        assertEquals(expected.length, ring.size());
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(expected[i], ring.get(i), "cell " + i);
        }
    }

    @Test
    void outerRingsGoClosestFirst() {
        List<int[]> ring = SafeSpot.ring(3);
        assertEquals(24, ring.size(), "a square ring 3 out");
        assertArrayEquals(new int[] {0, -3}, ring.get(0), "straight north first");
        assertArrayEquals(new int[] {-3, -3}, ring.get(ring.size() - 1), "a far corner last");
        for (int i = 1; i < ring.size(); i++) {
            int[] previous = ring.get(i - 1);
            int[] cell = ring.get(i);
            assertTrue(previous[0] * previous[0] + previous[1] * previous[1] <= cell[0] * cell[0] + cell[1] * cell[1],
                "sorted by distance at " + i);
        }
    }
}
