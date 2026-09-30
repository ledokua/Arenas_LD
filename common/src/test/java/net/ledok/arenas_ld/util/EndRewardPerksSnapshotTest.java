package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import net.ledok.arenas_ld.arena.run.ArenaRun;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.DungeonRun;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.raid.run.RaidRun;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline paths only: the online path reads LuckPerms through the loader service, absent in unit tests. */
class EndRewardPerksSnapshotTest {

    private static final UUID PRIME = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID PLAIN = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final EndRewardPerks PRIME_PERKS = new EndRewardPerks(2, 1.1, 1.25);

    @Test
    void offlinePlayerGetsTheirSnapshot() {
        Map<UUID, EndRewardPerks> snapshots = new HashMap<>(Map.of(PRIME, PRIME_PERKS));
        assertEquals(PRIME_PERKS, EndRewardPerks.resolve(snapshots, PRIME, false, EndRewardPerks.Mode.RAID));
    }

    @Test
    void offlinePlayerWithoutSnapshotGetsDefaults() {
        assertEquals(EndRewardPerks.DEFAULT,
            EndRewardPerks.resolve(new HashMap<>(), PLAIN, false, EndRewardPerks.Mode.DUNGEON));
    }

    @Test
    void snapshotCodecRoundTrips() {
        Map<UUID, EndRewardPerks> original = Map.of(PRIME, PRIME_PERKS, PLAIN, EndRewardPerks.DEFAULT);
        Tag tag = EndRewardPerks.SNAPSHOT_CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        assertEquals(original, EndRewardPerks.SNAPSHOT_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow());
    }

    @Test
    void dungeonRunPersistsSnapshots() {
        DungeonRun run = new DungeonRun(DifficultyTier.HARD, TierConfig.HARD_DEFAULT, false,
            new BlockPos(1, 64, 2), Level.OVERWORLD, 100L, "Owner");
        assertPersists(DungeonRun.CODEC, run, run.perkSnapshots(), DungeonRun::perkSnapshots);
    }

    @Test
    void raidRunPersistsSnapshots() {
        RaidRun run = new RaidRun(UUID.randomUUID(), "Owner", DifficultyTier.HARD, RaidTierConfig.HARD_DEFAULT,
            false, new BlockPos(1, 64, 2), Level.OVERWORLD, 100L);
        assertPersists(RaidRun.CODEC, run, run.perkSnapshots(), RaidRun::perkSnapshots);
    }

    @Test
    void arenaRunPersistsSnapshots() {
        ArenaRun run = new ArenaRun(UUID.randomUUID(), "Owner", false,
            new BlockPos(1, 64, 2), Level.OVERWORLD, -1, 100L);
        assertPersists(ArenaRun.CODEC, run, run.perkSnapshots(), ArenaRun::perkSnapshots);
    }

    /** Snapshots survive a save/load, and a save from before snapshots existed still loads (empty map). */
    private static <R> void assertPersists(Codec<R> codec, R run, Map<UUID, EndRewardPerks> live,
                                           Function<R, Map<UUID, EndRewardPerks>> snapshots) {
        live.put(PRIME, PRIME_PERKS);

        Tag saved = codec.encodeStart(NbtOps.INSTANCE, run).getOrThrow();
        assertEquals(Map.of(PRIME, PRIME_PERKS), snapshots.apply(codec.parse(NbtOps.INSTANCE, saved).getOrThrow()));

        CompoundTag legacy = ((CompoundTag) saved).copy();
        assertTrue(legacy.contains("perkSnapshots"), "snapshots saved at the top level of the run's tag");
        legacy.remove("perkSnapshots");
        assertTrue(snapshots.apply(codec.parse(NbtOps.INSTANCE, legacy).getOrThrow()).isEmpty());
    }
}
