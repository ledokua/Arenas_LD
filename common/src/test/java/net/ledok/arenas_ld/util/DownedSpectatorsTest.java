package net.ledok.arenas_ld.util;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sneak cycle order and the tether decision (the camera itself needs a server: see the GameTests). */
class DownedSpectatorsTest {

    private static final List<String> PARTY = List.of("a", "b", "c", "d");

    @Test
    void sneakGoesToTheNextLivingMateAndWraps() {
        assertEquals("b", DownedSpectators.nextInCycle(PARTY, "a", m -> true));
        assertEquals("a", DownedSpectators.nextInCycle(PARTY, "d", m -> true), "wraps to the front");
    }

    @Test
    void downedAndGoneMatesAreSkipped() {
        Set<String> living = Set.of("a", "d");
        assertEquals("d", DownedSpectators.nextInCycle(PARTY, "a", living::contains));
        assertEquals("a", DownedSpectators.nextInCycle(PARTY, "d", living::contains));
    }

    @Test
    void theOnlyLivingMateStays() {
        assertEquals("b", DownedSpectators.nextInCycle(PARTY, "b", "b"::equals));
    }

    @Test
    void anIneligibleCurrentStillSetsTheOrder() {
        // "b" is no longer eligible: the one after it is picked, not the front of the list.
        assertEquals("d", DownedSpectators.nextInCycle(PARTY, "b", m -> m.equals("a") || m.equals("d")));
    }

    @Test
    void nobodyLeftMeansNoCamera() {
        assertNull(DownedSpectators.nextInCycle(PARTY, "b", m -> false));
        assertNull(DownedSpectators.nextInCycle(List.<String>of(), "b", m -> true));
    }

    @Test
    void noCurrentStartsAtTheFront() {
        assertEquals("a", DownedSpectators.nextInCycle(PARTY, null, m -> true));
        assertEquals("c", DownedSpectators.nextInCycle(PARTY, "x", m -> m.equals("c")), "unknown current");
    }

    @Test
    void tetherHoldsWithinTheRadius() {
        assertFalse(DownedSpectators.outsideTether(true, 15.9 * 15.9, 16));
        assertFalse(DownedSpectators.outsideTether(true, 16 * 16, 16), "on the edge is still inside");
        assertTrue(DownedSpectators.outsideTether(true, 16.1 * 16.1, 16));
        assertTrue(DownedSpectators.outsideTether(true, 30 * 30, 16));
    }

    @Test
    void anotherDimensionIsAlwaysOutside() {
        assertTrue(DownedSpectators.outsideTether(false, 0, 16));
        assertTrue(DownedSpectators.outsideTether(false, 0, 1000));
    }

    @Test
    void zeroOrLessPinsToTheSpot() {
        assertFalse(DownedSpectators.outsideTether(true, 0.5 * 0.5, 0));
        assertTrue(DownedSpectators.outsideTether(true, 1.5 * 1.5, 0));
        assertTrue(DownedSpectators.outsideTether(true, 1.5 * 1.5, -4));
    }
}
