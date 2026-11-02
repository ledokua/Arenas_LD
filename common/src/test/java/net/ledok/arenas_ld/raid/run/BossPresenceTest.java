package net.ledok.arenas_ld.raid.run;

import org.junit.jupiter.api.Test;

import static net.ledok.arenas_ld.raid.run.BossPresence.Verdict.LOSE;
import static net.ledok.arenas_ld.raid.run.BossPresence.Verdict.WAIT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BossPresenceTest {

    @Test
    void waitsWhileTheBossChunkHasNoEntitiesLoaded() {
        assertEquals(WAIT, BossPresence.decide(0, -1));
        assertEquals(WAIT, BossPresence.decide(BossPresence.HARD_CAP_TICKS - 1, -1));
    }

    @Test
    void losesAfterTheHardCapWhateverTheChunk() {
        assertEquals(LOSE, BossPresence.decide(BossPresence.HARD_CAP_TICKS, -1));
        assertEquals(LOSE, BossPresence.decide(BossPresence.HARD_CAP_TICKS, 0));
    }

    @Test
    void losesOnlyAfterTheGraceOnceItsChunkIsLoaded() {
        assertEquals(WAIT, BossPresence.decide(5, 0));
        assertEquals(WAIT, BossPresence.decide(200, BossPresence.LOADED_GRACE_TICKS - 1));
        assertEquals(LOSE, BossPresence.decide(200, BossPresence.LOADED_GRACE_TICKS));
    }
}
