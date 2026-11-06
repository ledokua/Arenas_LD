package net.ledok.arenas_ld.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespawnDelaysTest {
    @Test
    void perDeathExtraGrowsAndCaps() {
        assertEquals(0, RespawnDelays.perDeathExtra(0, 100, 1200));
        assertEquals(100, RespawnDelays.perDeathExtra(1, 100, 1200));
        assertEquals(500, RespawnDelays.perDeathExtra(5, 100, 1200));
        assertEquals(1200, RespawnDelays.perDeathExtra(40, 100, 1200));
        assertEquals(0, RespawnDelays.perDeathExtra(3, -5, 1200));
        assertEquals(0, RespawnDelays.perDeathExtra(3, 100, 0));
    }

    @Test
    void togetherRespawnWaitsForTheNextClockTick() {
        // Clock every 600 ticks from the raid's start.
        assertEquals(0, RespawnDelays.ticksToTogetherRespawn(1200, 0, 600), "on a tick with no own wait: now");
        assertEquals(500, RespawnDelays.ticksToTogetherRespawn(700, 0, 600), "the next tick");
        assertEquals(500, RespawnDelays.ticksToTogetherRespawn(700, 300, 600), "own wait over before the next tick");
        assertEquals(1100, RespawnDelays.ticksToTogetherRespawn(700, 600, 600), "own wait pushes to the tick after");
        assertEquals(600, RespawnDelays.ticksToTogetherRespawn(600, 600, 600), "own wait ending on a tick");
    }
}
