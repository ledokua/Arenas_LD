package net.ledok.arenas_ld.util;

import net.ledok.arenas_ld.activity.InstanceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RunPartyTeamsTest {
    private static final ResourceKey<Level> A = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("test", "instance_a"));
    private static final ResourceKey<Level> B = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath("test", "instance_b"));

    @Test
    void sameInstanceSameTeam() {
        BlockPos pos = new BlockPos(100, 64, -200);
        assertEquals(RunPartyTeams.teamName("ald_a_", A, pos), RunPartyTeams.teamName("ald_a_", A, pos.immutable()));
    }

    @Test
    void copiedDimensionsGetTheirOwnTeams() {
        BlockPos pos = new BlockPos(100, 64, -200);
        assertNotEquals(RunPartyTeams.teamName("ald_a_", A, pos), RunPartyTeams.teamName("ald_a_", B, pos));
    }

    @Test
    void positionsWithTheSameHashCodeGetTheirOwnTeams() {
        BlockPos first = new BlockPos(0, 0, 0);
        BlockPos second = new BlockPos(31, -1, 0); // Vec3i.hashCode: (y + z * 31) * 31 + x
        assertEquals(first.hashCode(), second.hashCode(), "the old names collided here");
        assertEquals(RunPartyTeams.legacyTeamName("ald_r_", first), RunPartyTeams.legacyTeamName("ald_r_", second));
        assertNotEquals(RunPartyTeams.teamName("ald_r_", A, first), RunPartyTeams.teamName("ald_r_", A, second));
    }

    @Test
    void instanceKeysTellDimensionsApart() {
        BlockPos pos = new BlockPos(5, 70, 5);
        assertEquals(new InstanceKey(A, pos), new InstanceKey(A, pos.immutable()));
        assertNotEquals(new InstanceKey(A, pos), new InstanceKey(B, pos));
    }
}
