package net.ledok.arenas_ld.arena.run;

import net.ledok.arenas_ld.dungeon.run.DownedPlayer;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaRunTest {

    private static final UUID LOBBY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID UUID_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID UUID_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID UUID_C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID MARKED = UUID.fromString("dddddddd-0000-0000-0000-000000000004");
    private static final UUID MOB_1 = UUID.fromString("eeeeeeee-0000-0000-0000-000000000005");
    private static final UUID MOB_2 = UUID.fromString("ffffffff-0000-0000-0000-000000000006");

    private ArenaRun freshRun() {
        return new ArenaRun(
            LOBBY_ID,
            "Owner",
            false,
            new BlockPos(100, 64, 200),
            Level.OVERWORLD,
            -1,
            12345L
        );
    }

    @Test
    void initialStateIsCorrect() {
        ArenaRun run = freshRun();
        assertEquals(ArenaPhase.STARTING, run.phase());
        assertEquals(ArenaOutcome.IN_PROGRESS, run.outcome());
        assertEquals(LOBBY_ID, run.lobbyId());
        assertEquals("Owner", run.ownerName());
        assertFalse(run.hardcoreEnabled());
        assertEquals(new BlockPos(100, 64, 200), run.spawnerPos());
        assertEquals(Level.OVERWORLD, run.spawnerDimension());
        assertEquals(-1, run.maxWave());
        assertEquals(0, run.currentWave());
        assertEquals(WaveArchetype.HORDE, run.currentArchetype());
        assertEquals(ObjectiveType.NONE, run.currentObjective());
        assertEquals(12345L, run.startTick());
        assertTrue(run.aliveMobs().isEmpty());
        assertTrue(run.participants().isEmpty());
        assertTrue(run.returnPoints().isEmpty());
        assertTrue(run.downedPlayers().isEmpty());
        assertTrue(run.disconnectedAt().isEmpty());
        assertFalse(run.isFinished());
    }

    @Test
    void mapGettersReturnUnmodifiableViews() {
        ArenaRun run = freshRun();
        assertThrows(UnsupportedOperationException.class, () -> run.participants().put(UUID_A, null));
        assertThrows(UnsupportedOperationException.class, () -> run.returnPoints().clear());
        assertThrows(UnsupportedOperationException.class, () -> run.downedPlayers().put(UUID_A, null));
        assertThrows(UnsupportedOperationException.class, () -> run.disconnectedAt().clear());
        assertThrows(UnsupportedOperationException.class, () -> run.aliveMobs().clear());
    }

    @Test
    void activeParticipantUuidsFiltersByStatus() {
        ArenaRun run = freshRun();
        run.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 0L));
        run.addParticipant(new RunParticipant(UUID_B, "Bob", ParticipantStatus.DOWNED, 0L));
        run.addParticipant(new RunParticipant(UUID_C, "Cara", ParticipantStatus.DISCONNECTED, 0L));

        assertEquals(Set.of(UUID_A), run.activeParticipantUuids());
    }

    @Test
    void isFinishedTrueOnlyWhenPhaseDone() {
        ArenaRun run = freshRun();
        assertFalse(run.isFinished());
        run.setPhase(ArenaPhase.RUNNING);
        assertFalse(run.isFinished());
        run.setPhase(ArenaPhase.CLOSING);
        assertFalse(run.isFinished());
        run.setPhase(ArenaPhase.DONE);
        assertTrue(run.isFinished());
    }

    @Test
    void codecRoundTripsPopulatedRun() {
        ArenaRun original = freshRun();
        original.setPhase(ArenaPhase.RUNNING);
        original.setCurrentWave(7);
        original.setCurrentArchetype(WaveArchetype.OBJECTIVE);
        original.setCurrentObjective(ObjectiveType.KILL_MARKED);
        original.setObjectiveProgress(2);
        original.setMarkedMob(MARKED);
        original.setWaveTicksRemaining(420);
        original.addAliveMob(MOB_1);
        original.addAliveMob(MOB_2);

        original.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 100L));
        original.addParticipant(new RunParticipant(UUID_B, "Bob", ParticipantStatus.DOWNED, 150L));

        original.setReturnPoint(UUID_A, new PlayerReturnPoint(
            Level.OVERWORLD, new net.minecraft.world.phys.Vec3(0, 64, 0), 0f, 0f, GameType.SURVIVAL));
        original.setDowned(new DownedPlayer(UUID_B, 25));
        original.markDisconnected(UUID_C, 999L);

        Tag encoded = ArenaRun.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        ArenaRun decoded = ArenaRun.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();

        assertEquals(ArenaPhase.RUNNING, decoded.phase());
        assertEquals(LOBBY_ID, decoded.lobbyId());
        assertEquals(-1, decoded.maxWave());
        assertEquals(7, decoded.currentWave());
        assertEquals(WaveArchetype.OBJECTIVE, decoded.currentArchetype());
        assertEquals(ObjectiveType.KILL_MARKED, decoded.currentObjective());
        assertEquals(2, decoded.objectiveProgress());
        assertEquals(MARKED, decoded.markedMob());
        assertEquals(420, decoded.waveTicksRemaining());
        assertEquals(Set.of(MOB_1, MOB_2), decoded.aliveMobs());

        assertEquals(2, decoded.participants().size());
        assertEquals(ParticipantStatus.DOWNED, decoded.participants().get(UUID_B).status());
        assertEquals(1, decoded.returnPoints().size());
        assertEquals(1, decoded.downedPlayers().size());
        assertEquals(25, decoded.downedPlayers().get(UUID_B).ticksRemaining());
        assertEquals(999L, decoded.disconnectedAt().get(UUID_C));
    }

    @Test
    void codecRoundTripsFreshRun() {
        ArenaRun original = freshRun();
        Tag encoded = ArenaRun.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        ArenaRun decoded = ArenaRun.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals(0, decoded.currentWave());
        assertEquals(WaveArchetype.HORDE, decoded.currentArchetype());
        assertTrue(decoded.aliveMobs().isEmpty());
    }
}
