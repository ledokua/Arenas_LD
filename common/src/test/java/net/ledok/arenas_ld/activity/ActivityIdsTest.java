package net.ledok.arenas_ld.activity;

import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.ledok.arenas_ld.api.ArenasApi;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The pure parts of the activity id rules (parsing admin input, slugs, uniqueness) and old-save loading. */
class ActivityIdsTest {

    @Test
    void bareIdGetsTheModNamespace() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("arenas_ld", "crypt"), ActivityRegistry.parseId("crypt"));
    }

    @Test
    void namespacedIdIsKeptAndInputIsTrimmedAndLowercased() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("myserver", "crypt"), ActivityRegistry.parseId("  MyServer:Crypt "));
    }

    @Test
    void invalidIdsAreRefused() {
        assertNull(ActivityRegistry.parseId(""));
        assertNull(ActivityRegistry.parseId("   "));
        assertNull(ActivityRegistry.parseId("crypt of doom"));
        assertNull(ActivityRegistry.parseId(":crypt"));
        assertNull(ActivityRegistry.parseId("arenas_ld:"));
        assertNull(ActivityRegistry.parseId("склеп"));
    }

    @Test
    void slugKeepsAsciiWordsOnly() {
        assertEquals("the_old_crypt", ActivityRegistry.slug("  The Old Crypt! "));
        assertEquals("", ActivityRegistry.slug("Старий склеп"));
        assertEquals("", ActivityRegistry.slug(null));
        assertEquals(32, ActivityRegistry.slug("a".repeat(50)).length());
    }

    @Test
    void uniqueIdCountsUpPastTakenOnes() {
        Set<String> taken = Set.of("arenas_ld:dungeon_crypt", "arenas_ld:dungeon_crypt_2");
        assertEquals(ResourceLocation.fromNamespaceAndPath("arenas_ld", "dungeon_crypt_3"),
            ActivityRegistry.uniqueId("dungeon_crypt", id -> taken.contains(id.toString())));
        assertEquals(ResourceLocation.fromNamespaceAndPath("arenas_ld", "raid_1_64_-20"),
            ActivityRegistry.uniqueId("raid_1_64_-20", id -> false));
    }

    /** 4.5.0 saved a requiredLevel in every tier config; those keys are ignored on load. */
    @Test
    void tierConfigsFrom450StillLoad() {
        CompoundTag oldDungeonTier = (CompoundTag) TierConfig.CODEC.encodeStart(NbtOps.INSTANCE, TierConfig.HARD_DEFAULT).getOrThrow();
        oldDungeonTier.putInt("requiredLevel", 25);
        assertEquals(TierConfig.HARD_DEFAULT, TierConfig.CODEC.parse(NbtOps.INSTANCE, oldDungeonTier).getOrThrow());

        CompoundTag oldRaidTier = RaidTierConfig.NORMAL_DEFAULT.toNbt();
        oldRaidTier.putInt("requiredLevel", 10);
        assertEquals(RaidTierConfig.NORMAL_DEFAULT, RaidTierConfig.fromNbt(oldRaidTier));
    }

    /** 4.5.0's arenas_ld_activities.dat also held levels and card art; they are ignored on load. */
    @Test
    void activityIndexEntriesFrom450StillLoad() {
        CompoundTag hard = new CompoundTag();
        hard.putString("id", "hard");
        hard.putInt("requiredLevel", 25);
        ListTag difficulties = new ListTag();
        difficulties.add(hard);
        CompoundTag entry = new CompoundTag();
        entry.putString("id", "arenas_ld:crypt");
        entry.putString("type", "DUNGEON");
        entry.putString("dimension", "minecraft:overworld");
        entry.put("pos", new IntArrayTag(new int[] {1, 64, -3}));
        entry.putString("name", "Crypt");
        entry.put("difficulties", difficulties);
        entry.putInt("requiredLevel", 25);
        entry.putString("cardArt", "eden_ld:dungeon_crypt");

        ActivityIndex.Entry parsed = ActivityIndex.Entry.CODEC.parse(NbtOps.INSTANCE, entry).getOrThrow();
        assertEquals(List.of(new ArenasApi.Difficulty("hard")), parsed.difficulties());
        assertEquals(new BlockPos(1, 64, -3), parsed.pos());
        assertEquals("nightmare", ActivityHost.difficultyId(DifficultyTier.NIGHTMARE));
    }
}
