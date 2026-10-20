package net.ledok.arenas_ld.arena.run;

import com.mojang.serialization.JsonOps;
import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.dungeon.run.RunParticipant.ParticipantStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ArenaRunLifecycle#runStatus}: the staff status line of one arena run. */
class ArenaRunStatusTest {

    private static final UUID LOBBY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID UUID_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID UUID_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final BlockPos SPAWNER = new BlockPos(100, 64, 200);

    private static ArenaRun run(int maxWave) {
        ArenaRun run = new ArenaRun(LOBBY_ID, "Owner", false, SPAWNER, Level.OVERWORLD, maxWave, 1000L);
        run.setPhase(ArenaPhase.RUNNING);
        run.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 0L));
        run.addParticipant(new RunParticipant(UUID_B, "Bob", ParticipantStatus.REMOVED, 0L));
        return run;
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    @Test
    void preparePhaseCountsThePrepareTimer() {
        ArenaRun run = run(10);
        run.setPrepareTicksRemaining(140);
        ArenasApi.RunStatus status = ArenaRunLifecycle.runStatus(run, 1300L);
        assertEquals("status.arenas_ld.arena.prepare", key(status.phase()));
        assertEquals(140, status.ticksLeft());
        assertEquals(300, status.ticksElapsed());
        assertFalse(status.closing());
        assertNull(status.difficulty());
        assertEquals(new ArenasApi.InstanceRef(Level.OVERWORLD, SPAWNER), status.instance());
        assertEquals(List.of(UUID_A), status.players(), "removed players are left out");
    }

    @Test
    void waveShowsTheCeilingAndItsTimer() {
        ArenaRun run = run(10);
        run.setCurrentWave(4);
        run.setWaveTicksRemaining(600);
        ArenasApi.RunStatus status = ArenaRunLifecycle.runStatus(run, 1000L);
        assertEquals("status.arenas_ld.arena.wave_of", key(status.phase()));
        assertEquals(600, status.ticksLeft());
    }

    @Test
    void endlessWaveWithoutTimerReportsNoTimer() {
        ArenaRun run = run(-1);
        run.setCurrentWave(3);
        ArenasApi.RunStatus status = ArenaRunLifecycle.runStatus(run, 1000L);
        assertEquals("status.arenas_ld.arena.wave", key(status.phase()));
        assertEquals(-1, status.ticksLeft());
    }

    @Test
    void betweenWavesNamesTheNextWave() {
        ArenaRun run = run(10);
        run.setCurrentWave(2);
        run.setBetweenWaveTicksRemaining(100);
        ArenasApi.RunStatus status = ArenaRunLifecycle.runStatus(run, 1000L);
        assertEquals("status.arenas_ld.arena.next_wave", key(status.phase()));
        assertEquals(100, status.ticksLeft());
    }

    @Test
    void closingTellsHowTheRunEnded() {
        ArenaRun run = run(10);
        run.setCurrentWave(5);
        run.setPhase(ArenaPhase.CLOSING);
        run.setCloseTimerTicks(400);

        run.setOutcome(ArenaOutcome.STAFF_ENDED);
        ArenasApi.RunStatus forced = ArenaRunLifecycle.runStatus(run, 900L);
        assertEquals("status.arenas_ld.arena.closing_forced", key(forced.phase()));
        assertEquals(400, forced.ticksLeft());
        assertTrue(forced.closing());
        assertEquals(0, forced.ticksElapsed(), "elapsed never goes negative");

        run.setOutcome(ArenaOutcome.COMPLETED);
        assertEquals("status.arenas_ld.arena.closing_won", key(ArenaRunLifecycle.runStatus(run, 1000L).phase()));
        // A missing spawner is a plain loss, not a staff end.
        run.setOutcome(ArenaOutcome.FORCED);
        assertEquals("status.arenas_ld.arena.closing_lost", key(ArenaRunLifecycle.runStatus(run, 1000L).phase()));
    }

    @Test
    void staffEndedOutcomeSurvivesTheSaveAndCountsAsALoss() {
        assertTrue(ArenaOutcome.STAFF_ENDED.isLoss());
        var encoded = ArenaOutcome.CODEC.encodeStart(JsonOps.INSTANCE, ArenaOutcome.STAFF_ENDED).getOrThrow();
        assertEquals(ArenaOutcome.STAFF_ENDED, ArenaOutcome.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }
}
