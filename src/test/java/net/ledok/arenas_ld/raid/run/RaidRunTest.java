package net.ledok.arenas_ld.raid.run;

import com.mojang.serialization.DataResult;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.DownedPlayer;
import net.ledok.arenas_ld.dungeon.run.ParticipantStatus;
import net.ledok.arenas_ld.dungeon.run.PlayerReturnPoint;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaidRunTest {

    private static final UUID LOBBY_ID = UUID.fromString("11111111-0000-0000-0000-000000000001");
    private static final UUID UUID_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID UUID_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID UUID_C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID BOSS_UUID = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    private RaidRun freshRun() {
        return new RaidRun(
            LOBBY_ID,
            "Owner",
            DifficultyTier.HARD,
            RaidTierConfig.HARD_DEFAULT,
            false,
            new BlockPos(100, 64, 200),
            Level.OVERWORLD,
            12345L
        );
    }

    @Test
    void initialStateIsCorrect() {
        RaidRun run = freshRun();
        assertEquals(RaidPhase.STARTING, run.phase());
        assertEquals(RaidOutcome.IN_PROGRESS, run.outcome());
        assertEquals(LOBBY_ID, run.lobbyId());
        assertEquals("Owner", run.ownerName());
        assertEquals(DifficultyTier.HARD, run.tier());
        assertEquals(RaidTierConfig.HARD_DEFAULT, run.resolvedTierConfig());
        assertFalse(run.hardcoreEnabled());
        assertEquals(new BlockPos(100, 64, 200), run.spawnerPos());
        assertEquals(Level.OVERWORLD, run.spawnerDimension());
        assertEquals(RaidTierConfig.HARD_DEFAULT.raidTimeSeconds() * 20, run.timerTicks());
        assertEquals(0, run.closeTimerTicks());
        assertEquals(0, run.regenerationTickTimer());
        assertEquals(0, run.boundsTickCounter());
        assertEquals(12345L, run.startTick());
        assertNull(run.bossUuid());
        assertNull(run.bossDimension());
        assertTrue(run.participants().isEmpty());
        assertTrue(run.returnPoints().isEmpty());
        assertTrue(run.downedPlayers().isEmpty());
        assertTrue(run.disconnectedAt().isEmpty());
        assertFalse(run.isFinished());
    }

    @Test
    void mapGettersReturnUnmodifiableViews() {
        RaidRun run = freshRun();
        assertThrows(UnsupportedOperationException.class,
            () -> run.participants().put(UUID_A, null));
        assertThrows(UnsupportedOperationException.class,
            () -> run.returnPoints().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> run.downedPlayers().put(UUID_A, null));
        assertThrows(UnsupportedOperationException.class,
            () -> run.disconnectedAt().clear());
    }

    @Test
    void activeParticipantUuidsFiltersByStatus() {
        RaidRun run = freshRun();
        run.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 0L));
        run.addParticipant(new RunParticipant(UUID_B, "Bob", ParticipantStatus.DOWNED, 0L));
        run.addParticipant(new RunParticipant(UUID_C, "Cara", ParticipantStatus.DISCONNECTED, 0L));

        Set<UUID> active = run.activeParticipantUuids();
        assertEquals(Set.of(UUID_A), active);
    }

    @Test
    void isFinishedTrueOnlyWhenPhaseDone() {
        RaidRun run = freshRun();
        assertFalse(run.isFinished());
        run.setPhase(RaidPhase.RUNNING);
        assertFalse(run.isFinished());
        run.setPhase(RaidPhase.CLOSING);
        assertFalse(run.isFinished());
        run.setPhase(RaidPhase.DONE);
        assertTrue(run.isFinished());
    }

    @Test
    void codecRoundTripsPopulatedRun() {
        RaidRun original = freshRun();
        original.setPhase(RaidPhase.RUNNING);
        original.setTimerTicks(8400);
        original.setBossRef(BOSS_UUID, Level.NETHER);

        original.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 100L));
        original.addParticipant(new RunParticipant(UUID_B, "Bob", ParticipantStatus.DOWNED, 150L));
        original.addParticipant(new RunParticipant(UUID_C, "Cara", ParticipantStatus.DISCONNECTED, 200L));

        original.setReturnPoint(UUID_A, new PlayerReturnPoint(
            Level.OVERWORLD, new net.minecraft.world.phys.Vec3(0, 64, 0), 0f, 0f, GameType.SURVIVAL));
        original.setReturnPoint(UUID_B, new PlayerReturnPoint(
            Level.OVERWORLD, new net.minecraft.world.phys.Vec3(10, 64, 10), 90f, 0f, GameType.ADVENTURE));

        original.setDowned(new DownedPlayer(UUID_B, 25));
        original.markDisconnected(UUID_C, 999L);

        Tag encoded = RaidRun.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DataResult<RaidRun> decodedResult = RaidRun.CODEC.parse(NbtOps.INSTANCE, encoded);
        RaidRun decoded = decodedResult.getOrThrow();

        assertEquals(RaidPhase.RUNNING, decoded.phase());
        assertEquals(RaidOutcome.IN_PROGRESS, decoded.outcome());
        assertEquals(LOBBY_ID, decoded.lobbyId());
        assertEquals(DifficultyTier.HARD, decoded.tier());
        assertEquals(RaidTierConfig.HARD_DEFAULT, decoded.resolvedTierConfig());
        assertFalse(decoded.hardcoreEnabled());
        assertEquals(new BlockPos(100, 64, 200), decoded.spawnerPos());
        assertEquals(Level.OVERWORLD, decoded.spawnerDimension());
        assertEquals(8400, decoded.timerTicks());
        assertEquals(0, decoded.closeTimerTicks());
        assertEquals(12345L, decoded.startTick());
        assertEquals(BOSS_UUID, decoded.bossUuid());
        assertEquals(Level.NETHER, decoded.bossDimension());

        assertEquals(3, decoded.participants().size());
        assertEquals(ParticipantStatus.DOWNED, decoded.participants().get(UUID_B).status());
        assertEquals(2, decoded.returnPoints().size());
        assertEquals(GameType.ADVENTURE, decoded.returnPoints().get(UUID_B).previousGameMode());
        assertEquals(1, decoded.downedPlayers().size());
        assertEquals(25, decoded.downedPlayers().get(UUID_B).ticksRemaining());
        assertEquals(1, decoded.disconnectedAt().size());
        assertEquals(999L, decoded.disconnectedAt().get(UUID_C));
    }

    @Test
    void codecRoundTripsRunWithoutBoss() {
        RaidRun original = freshRun();
        Tag encoded = RaidRun.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        RaidRun decoded = RaidRun.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertNull(decoded.bossUuid());
        assertNull(decoded.bossDimension());
    }
}
