package net.ledok.arenas_ld.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardCommandsTest {

    @Test
    void cleanTrimsAndDropsLeadingSlashes() {
        assertEquals("give @s diamond", RewardCommands.clean("  /give @s diamond  "));
        assertEquals("say hi", RewardCommands.clean("// say hi"));
        assertEquals("", RewardCommands.clean("   "));
        assertEquals("", RewardCommands.clean("/"));
        assertEquals("", RewardCommands.clean(null));
    }

    @Test
    void cleanCutsAtMaxLength() {
        String longCommand = "say " + "x".repeat(400);
        assertEquals(RewardCommands.MAX_LENGTH, RewardCommands.clean(longCommand).length());
    }

    @Test
    void sanitizeDropsBlanksAndKeepsAtMostEight() {
        List<String> raw = new ArrayList<>(Arrays.asList("", " ", null, "/say 1"));
        for (int i = 2; i <= 12; i++) {
            raw.add("say " + i);
        }
        List<String> clean = RewardCommands.sanitize(raw);
        assertEquals(RewardCommands.MAX_COMMANDS, clean.size());
        assertEquals("say 1", clean.get(0));
        assertEquals("say 8", clean.get(7));
        assertEquals(List.of(), RewardCommands.sanitize(null));
    }

    @Test
    void placeholdersMarkPerPlayerCommands() {
        assertTrue(RewardCommands.isPerPlayer("give @dungeonplayer diamond"));
        assertTrue(RewardCommands.isPerPlayer("give @s diamond"));
        assertTrue(RewardCommands.isPerPlayer("effect give @s[gamemode=survival] speed"));
        assertTrue(RewardCommands.isPerPlayer("tag @s"));
        assertFalse(RewardCommands.isPerPlayer("say @a won"));
        assertFalse(RewardCommands.isPerPlayer("say @sa"));
        assertFalse(RewardCommands.isPerPlayer("say everyone"));
    }

    @Test
    void resolveSubstitutesTheName() {
        assertEquals("give Alice diamond 2", RewardCommands.resolve("give @dungeonplayer diamond 2", "Alice"));
        assertEquals("tp Alice Alice", RewardCommands.resolve("tp @s @dungeonplayer", "Alice"));
        // Selector arguments stay usable: the player is picked by name.
        assertEquals("effect give @a[name=Alice,gamemode=survival] speed",
            RewardCommands.resolve("effect give @s[gamemode=survival] speed", "Alice"));
        assertEquals("tag @a[name=Alice] add x", RewardCommands.resolve("tag @s[ ] add x", "Alice"));
        assertEquals("say @sa @a", RewardCommands.resolve("say @sa @a", "Alice"));
        // A name is inserted literally, never read as a regex replacement.
        assertEquals("tag A$1 add x", RewardCommands.resolve("tag @s add x", "A$1"));
    }

    @Test
    void onlyPlainNamesAreSafeToSubstitute() {
        assertTrue(RewardCommands.isSafePlayerName("Steve_123"));
        assertTrue(RewardCommands.isSafePlayerName(".BedrockSteve")); // Floodgate prefix
        assertFalse(RewardCommands.isSafePlayerName("@a"));
        assertFalse(RewardCommands.isSafePlayerName("!x"));
        assertFalse(RewardCommands.isSafePlayerName("a,b"));
        assertFalse(RewardCommands.isSafePlayerName("x]"));
        assertFalse(RewardCommands.isSafePlayerName("two words"));
        assertFalse(RewardCommands.isSafePlayerName(""));
        assertFalse(RewardCommands.isSafePlayerName(null));
        assertFalse(RewardCommands.isSafePlayerName("a".repeat(17)));
    }
}
