package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.DataResult;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class TierConfigTest {

    @Test
    void defaultForReturnsExpectedConstants() {
        assertSame(TierConfig.EASY_DEFAULT, TierConfig.defaultFor(DifficultyTier.EASY));
        assertSame(TierConfig.NORMAL_DEFAULT, TierConfig.defaultFor(DifficultyTier.NORMAL));
        assertSame(TierConfig.HARD_DEFAULT, TierConfig.defaultFor(DifficultyTier.HARD));
        assertSame(TierConfig.NIGHTMARE_DEFAULT, TierConfig.defaultFor(DifficultyTier.NIGHTMARE));
    }

    @Test
    void defaultConstantsHaveExpectedValues() {
        assertEquals(0.75, TierConfig.EASY_DEFAULT.healthMultiplier());
        assertEquals(0.75, TierConfig.EASY_DEFAULT.damageMultiplier());
        assertEquals(false, TierConfig.EASY_DEFAULT.hardcoreDefault());

        assertEquals(1.0, TierConfig.NORMAL_DEFAULT.healthMultiplier());
        assertEquals(1.0, TierConfig.NORMAL_DEFAULT.damageMultiplier());
        assertEquals("", TierConfig.NORMAL_DEFAULT.perPlayerLootTable());
        assertEquals(600, TierConfig.NORMAL_DEFAULT.dungeonTimeSeconds());
        assertEquals(false, TierConfig.NORMAL_DEFAULT.hardcoreDefault());

        assertEquals(1.5, TierConfig.HARD_DEFAULT.healthMultiplier());
        assertEquals(2.5, TierConfig.NIGHTMARE_DEFAULT.healthMultiplier());
        assertEquals(true, TierConfig.NIGHTMARE_DEFAULT.hardcoreDefault());
    }

    @Test
    void codecRoundTripsPopulatedInstance() {
        TierConfig original = new TierConfig(2.7, 3.1, "arenas_ld:dungeon/test_table", 450, true, true);
        Tag encoded = TierConfig.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        DataResult<TierConfig> decoded = TierConfig.CODEC.parse(NbtOps.INSTANCE, encoded);
        assertEquals(original, decoded.getOrThrow());
    }

    @Test
    void codecRoundTripsEmptyLootTable() {
        TierConfig original = new TierConfig(1.0, 1.0, "", 600, false, true);
        Tag encoded = TierConfig.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        TierConfig decoded = TierConfig.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow();
        assertEquals("", decoded.perPlayerLootTable());
    }
}
