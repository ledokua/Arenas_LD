package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.DataResult;
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

class DungeonRunTest {

    private static final UUID UUID_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID UUID_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID UUID_C = UUID.fromString("cccccccc-0000-0000-0000-000000000003");

    private DungeonRun freshRun() {
        return new DungeonRun(
            DifficultyTier.HARD,
            TierConfig.HARD_DEFAULT,
            false,
            new BlockPos(100, 64, 200),
            Level.OVERWORLD,
            12345L,
            "Owner"
        );
    }

    @Test
    void initialStateIsCorrect() {
        DungeonRun run = freshRun();
        assertEquals(DungeonPhase.STARTING, run.phase());
        assertEquals(DungeonOutcome.IN_PROGRESS, run.outcome());
        assertEquals(DifficultyTier.HARD, run.tier());
        assertEquals(TierConfig.HARD_DEFAULT, run.resolvedTierConfig());
        assertFalse(run.hardcoreEnabled());
        assertEquals(new BlockPos(100, 64, 200), run.dbsPos());
        assertEquals(Level.OVERWORLD, run.dbsDimension());
        assertEquals(0, run.currentRoomIndex());
        assertEquals(12000, run.dungeonTimerTicks());
        assertEquals(0, run.closeTimerTicks());
        assertEquals(12345L, run.startTick());
        assertTrue(run.participants().isEmpty());
        assertTrue(run.returnPoints().isEmpty());
        assertTrue(run.downedPlayers().isEmpty());
        assertFalse(run.isFinished());
    }

    @Test
    void mapGettersReturnUnmodifiableViews() {
        DungeonRun run = freshRun();
        assertThrows(UnsupportedOperationException.class,
            () -> run.participants().put(UUID_A, null));
        assertThrows(UnsupportedOperationException.class,
            () -> run.returnPoints().clear());
        assertThrows(UnsupportedOperationException.class,
            () -> run.downedPlayers().put(UUID_A, null));
    }

    @Test
    void activeParticipantUuidsFiltersByStatus() {
        DungeonRun run = freshRun();
        run.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 0L));
        run.addParticipant(new RunParticipant(UUID_B, "Bob", ParticipantStatus.DOWNED, 0L));
        run.addParticipant(new RunParticipant(UUID_C, "Cara", ParticipantStatus.DISCONNECTED, 0L));

        Set<UUID> active = run.activeParticipantUuids();
        assertEquals(Set.of(UUID_A), active);
    }

    @Test
    void lootEligibleUuidsExcludesRemoved() {
        DungeonRun run = freshRun();
        run.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 0L));
        run.addParticipant(new RunParticipant(UUID_B, "Bob", ParticipantStatus.DOWNED, 0L));
        run.addParticipant(new RunParticipant(UUID_C, "Cara", ParticipantStatus.REMOVED, 0L));

        Set<UUID> eligible = run.lootEligibleUuids();
        assertEquals(Set.of(UUID_A, UUID_B), eligible);
    }

    @Test
    void updateParticipantReplacesByUuid() {
        DungeonRun run = freshRun();
        run.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 0L));
        run.updateParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.DOWNED, 100L));

        RunParticipant updated = run.participants().get(UUID_A);
        assertEquals(ParticipantStatus.DOWNED, updated.status());
        assertEquals(100L, updated.lastSeenTick());
        assertEquals(1, run.participants().size());
    }

    @Test
    void isFinishedTrueOnlyWhenPhaseDone() {
        DungeonRun run = freshRun();
        assertFalse(run.isFinished());
        run.setPhase(DungeonPhase.RUNNING);
        assertFalse(run.isFinished());
        run.setPhase(DungeonPhase.CLOSING);
        assertFalse(run.isFinished());
        run.setPhase(DungeonPhase.DONE);
        assertTrue(run.isFinished());
    }

    @Test
    void codecRoundTripsPopulatedRun() {
        DungeonRun original = freshRun();
        original.setPhase(DungeonPhase.RUNNING);
        original.setCurrentRoomIndex(2);
        original.setDungeonTimerTicks(8400);

        original.addParticipant(new RunParticipant(UUID_A, "Alice", ParticipantStatus.ACTIVE, 100L));
        original.addParticipant(new RunParticipant(UUID_B, "Bob", ParticipantStatus.DOWNED, 150L));
        original.addParticipant(new RunParticipant(UUID_C, "Cara", ParticipantStatus.DISCONNECTED, 200L));

        original.setReturnPoint(UUID_A, new PlayerReturnPoint(
            Level.OVERWORLD, new net.minecraft.world.phys.Vec3(0, 64, 0), 0f, 0f, GameType.SURVIVAL));
        original.setReturnPoint(UUID_B, new PlayerReturnPoint(
            Level.OVERWORLD, new net.minecraft.world.phys.Vec3(10, 64, 10), 90f, 0f, GameType.SURVIVAL));
        original.setReturnPoint(UUID_C, new PlayerReturnPoint(
            Level.OVERWORLD, new net.minecraft.world.phys.Vec3(20, 64, 20), 180f, -10f, GameType.SURVIVAL));

        original.setDowned(new DownedPlayer(UUID_B, 25));

        Tag encoded = DungeonRun.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DataResult<DungeonRun> decodedResult = DungeonRun.CODEC.parse(NbtOps.INSTANCE, encoded);
        DungeonRun decoded = decodedResult.getOrThrow();

        assertEquals(DungeonPhase.RUNNING, decoded.phase());
        assertEquals(DungeonOutcome.IN_PROGRESS, decoded.outcome());
        assertEquals(DifficultyTier.HARD, decoded.tier());
        assertEquals(TierConfig.HARD_DEFAULT, decoded.resolvedTierConfig());
        assertEquals(false, decoded.hardcoreEnabled());
        assertEquals(new BlockPos(100, 64, 200), decoded.dbsPos());
        assertEquals(Level.OVERWORLD, decoded.dbsDimension());
        assertEquals(2, decoded.currentRoomIndex());
        assertEquals(8400, decoded.dungeonTimerTicks());
        assertEquals(0, decoded.closeTimerTicks());
        assertEquals(12345L, decoded.startTick());

        assertEquals(3, decoded.participants().size());
        assertEquals(ParticipantStatus.DOWNED, decoded.participants().get(UUID_B).status());
        assertEquals(3, decoded.returnPoints().size());
        assertEquals(1, decoded.downedPlayers().size());
        assertEquals(25, decoded.downedPlayers().get(UUID_B).ticksRemaining());
    }
}
