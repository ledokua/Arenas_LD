package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What run listeners receive, from a stub controller (no world needed). */
class RunEventsTest {

    private record StubHost(ArenasApi.ActivityType activityType, ResourceLocation activityId) implements ActivityHost {
        @Override public boolean loadedWithoutId() { return false; }
        @Override public void storeActivityId(ResourceLocation id) { }
        @Override public String activityName() { return ""; }
        @Override public void setActivityName(String name) { }
        @Override public void clearCopiedState(ActivityHost original) { }
        @Override public List<DifficultyTier> enabledTiers() { return List.of(); }
    }

    @Test
    void listenersGetTheRunResult() {
        List<ArenasApi.RunResult> seen = new ArrayList<>();
        RunEvents.addListener(seen::add);
        RunEvents.addListener(result -> { throw new IllegalStateException("a broken listener is skipped"); });
        UUID player = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
        ResourceLocation crypt = ResourceLocation.fromNamespaceAndPath("arenas_ld", "crypt");
        ResourceLocation pit = ResourceLocation.fromNamespaceAndPath("arenas_ld", "pit");

        RunEvents.fire(new StubHost(ArenasApi.ActivityType.DUNGEON, crypt), DifficultyTier.HARD, true, 100, 1300, 0, List.of(player));
        RunEvents.fire(new StubHost(ArenasApi.ActivityType.ARENA, pit), null, false, 50, 40, 7, List.of(player));

        assertEquals(new ArenasApi.RunResult(crypt, ArenasApi.ActivityType.DUNGEON, "hard", ArenasApi.Outcome.WIN, 1200, 0,
            List.of(player)), seen.get(0));
        ArenasApi.RunResult arena = seen.get(1);
        assertNull(arena.difficulty());
        assertEquals(ArenasApi.Outcome.LOSS, arena.outcome());
        assertEquals(0, arena.durationTicks(), "a clock running backwards never reports a negative duration");
        assertEquals(7, arena.wave());
    }
}
