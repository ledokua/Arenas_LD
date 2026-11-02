package net.ledok.arenas_ld.util;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The run-owned tag and how leftovers are sorted (handling them needs a world: see the GameTests). */
class RunLeftoversTest {

    @Test
    void runIdIsShortAndStable() {
        String id = RunLeftovers.runId("minecraft:overworld", 10, 64, -20, 12345L);
        assertEquals(8, id.length());
        assertTrue(id.matches("[0-9a-f]{8}"), id);
        assertEquals(id, RunLeftovers.runId("minecraft:overworld", 10, 64, -20, 12345L));
    }

    @Test
    void runIdTellsInstancesAndRunsApart() {
        String id = RunLeftovers.runId("minecraft:overworld", 10, 64, -20, 12345L);
        assertNotEquals(id, RunLeftovers.runId("minecraft:overworld", 90, 64, -20, 12345L), "another raid copy");
        assertNotEquals(id, RunLeftovers.runId("minecraft:the_nether", 10, 64, -20, 12345L), "another dimension");
        assertNotEquals(id, RunLeftovers.runId("minecraft:overworld", 10, 64, -20, 12346L), "a later run there");
    }

    @Test
    void tagRoundTrips() {
        UUID owner = UUID.randomUUID();
        String tag = RunLeftovers.tag("0a1b2c3d", owner);
        assertTrue(tag.startsWith(RunLeftovers.TAG_PREFIX));
        RunLeftovers.OwnedTag parsed = RunLeftovers.parseTag(tag);
        assertEquals(new RunLeftovers.OwnedTag("0a1b2c3d", owner), parsed);
    }

    @Test
    void otherOrDamagedTagsDontParse() {
        assertNull(RunLeftovers.parseTag("arenas_ld_run_mob"));
        assertNull(RunLeftovers.parseTag("arenas_ld_minion"));
        assertNull(RunLeftovers.parseTag(RunLeftovers.TAG_PREFIX));
        assertNull(RunLeftovers.parseTag(RunLeftovers.TAG_PREFIX + "0a1b2c3d"));
        assertNull(RunLeftovers.parseTag(RunLeftovers.TAG_PREFIX + ":" + UUID.randomUUID()));
        assertNull(RunLeftovers.parseTag(RunLeftovers.TAG_PREFIX + "0a1b2c3d:not-a-uuid"));
    }

    @Test
    void summonsAndProjectilesGoPetsAndItemsGoBack() {
        // A Spell Engine summon is living, and still goes.
        assertEquals(RunLeftovers.Kind.DISCARD, RunLeftovers.classify(true, false, true, false));
        // Arrows, snowballs, fangs, clouds: not living.
        assertEquals(RunLeftovers.Kind.DISCARD, RunLeftovers.classify(false, false, false, false));
        // Wolves, dragons, horses: never deleted.
        assertEquals(RunLeftovers.Kind.PET, RunLeftovers.classify(false, false, true, false));
        assertEquals(RunLeftovers.Kind.ITEM, RunLeftovers.classify(false, true, false, false));
        // A thrown trident is given back rather than lost.
        assertEquals(RunLeftovers.Kind.ITEM, RunLeftovers.classify(false, false, false, true));
    }
}
