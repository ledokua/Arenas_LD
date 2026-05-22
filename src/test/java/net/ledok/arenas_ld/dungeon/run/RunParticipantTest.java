package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunParticipantTest {

    private static final UUID TEST_UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @Test
    void participantStatusHasFourValuesInOrder() {
        ParticipantStatus[] values = ParticipantStatus.values();
        assertEquals(4, values.length);
        assertEquals(ParticipantStatus.ACTIVE, values[0]);
        assertEquals(ParticipantStatus.DOWNED, values[1]);
        assertEquals(ParticipantStatus.DISCONNECTED, values[2]);
        assertEquals(ParticipantStatus.REMOVED, values[3]);
    }

    @Test
    void participantStatusCodecRoundTrips() {
        for (ParticipantStatus status : ParticipantStatus.values()) {
            Tag encoded = ParticipantStatus.CODEC.encodeStart(NbtOps.INSTANCE, status).getOrThrow();
            DataResult<ParticipantStatus> decoded = ParticipantStatus.CODEC.parse(NbtOps.INSTANCE, encoded);
            assertEquals(status, decoded.getOrThrow());
        }
    }

    @Test
    void participantStatusCodecDecodesUnknownAsRemoved() {
        ParticipantStatus decoded = ParticipantStatus.CODEC
            .parse(NbtOps.INSTANCE, StringTag.valueOf("GARBAGE"))
            .getOrThrow();
        assertEquals(ParticipantStatus.REMOVED, decoded);
    }

    @Test
    void withStatusReplacesStatusAndTick() {
        RunParticipant original = new RunParticipant(TEST_UUID, "Steve", ParticipantStatus.ACTIVE, 100L);
        RunParticipant updated = original.withStatus(ParticipantStatus.DOWNED, 200L);
        assertEquals(TEST_UUID, updated.playerUuid());
        assertEquals("Steve", updated.playerName());
        assertEquals(ParticipantStatus.DOWNED, updated.status());
        assertEquals(200L, updated.lastSeenTick());
        assertEquals(ParticipantStatus.ACTIVE, original.status());
        assertEquals(100L, original.lastSeenTick());
    }

    @Test
    void isEligibleForLootTrueForNonRemoved() {
        assertTrue(new RunParticipant(TEST_UUID, "s", ParticipantStatus.ACTIVE, 0L).isEligibleForLoot());
        assertTrue(new RunParticipant(TEST_UUID, "s", ParticipantStatus.DOWNED, 0L).isEligibleForLoot());
        assertTrue(new RunParticipant(TEST_UUID, "s", ParticipantStatus.DISCONNECTED, 0L).isEligibleForLoot());
    }

    @Test
    void isEligibleForLootFalseForRemoved() {
        assertFalse(new RunParticipant(TEST_UUID, "s", ParticipantStatus.REMOVED, 0L).isEligibleForLoot());
    }

    @Test
    void runParticipantCodecRoundTrips() {
        RunParticipant original = new RunParticipant(TEST_UUID, "Steve", ParticipantStatus.DOWNED, 12345L);
        Tag encoded = RunParticipant.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DataResult<RunParticipant> decoded = RunParticipant.CODEC.parse(NbtOps.INSTANCE, encoded);
        assertEquals(original, decoded.getOrThrow());
    }
}
