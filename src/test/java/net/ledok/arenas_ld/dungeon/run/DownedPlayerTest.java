package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownedPlayerTest {

    private static final UUID TEST_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Test
    void tickDecrementsRemaining() {
        DownedPlayer dp = new DownedPlayer(TEST_UUID, 40);
        DownedPlayer ticked = dp.tick();
        assertEquals(39, ticked.ticksRemaining());
        assertEquals(TEST_UUID, ticked.playerUuid());
    }

    @Test
    void tickReturnsNewInstance() {
        DownedPlayer dp = new DownedPlayer(TEST_UUID, 40);
        DownedPlayer ticked = dp.tick();
        // Original unchanged (records are immutable; sanity check).
        assertEquals(40, dp.ticksRemaining());
        // New instance is a different object reference.
        assertFalse(dp == ticked);
    }

    @Test
    void tickClampsAtZero() {
        DownedPlayer dp = new DownedPlayer(TEST_UUID, 0);
        DownedPlayer ticked = dp.tick();
        assertEquals(0, ticked.ticksRemaining());
    }

    @Test
    void isReadyToRespawnTrueAtZero() {
        assertTrue(new DownedPlayer(TEST_UUID, 0).isReadyToRespawn());
    }

    @Test
    void isReadyToRespawnTrueBelowZero() {
        // Defensive: even if some path produces a negative value, the predicate stays correct.
        assertTrue(new DownedPlayer(TEST_UUID, -1).isReadyToRespawn());
    }

    @Test
    void isReadyToRespawnFalseAbovZero() {
        assertFalse(new DownedPlayer(TEST_UUID, 1).isReadyToRespawn());
        assertFalse(new DownedPlayer(TEST_UUID, 40).isReadyToRespawn());
    }

    @Test
    void codecRoundTripsPopulatedInstance() {
        DownedPlayer original = new DownedPlayer(TEST_UUID, 25);
        Tag encoded = DownedPlayer.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DataResult<DownedPlayer> decoded = DownedPlayer.CODEC.parse(NbtOps.INSTANCE, encoded);
        assertEquals(original, decoded.getOrThrow());
    }
}
