package net.ledok.arenas_ld.raid.run;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RaidTierConfigTest {

    @Test
    void codecRoundTripsRewardCommands() {
        RaidTierConfig original = new RaidTierConfig(1.5, 2.0, "arenas_ld:raid/test", 900, true, 50L, 200, 5, 0.2,
            List.of("give @dungeonplayer diamond", "say won"));
        Tag encoded = RaidTierConfig.CODEC.encodeStart(NbtOps.INSTANCE, original).getOrThrow();
        assertEquals(original, RaidTierConfig.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void oldDataWithoutCommandsLoadsWithNone() {
        RaidTierConfig old = new RaidTierConfig(1.0, 1.0, "", 600, true, 0L, 100, 0, 0.10);
        CompoundTag tag = old.toNbt();
        tag.remove("rewardCommands");
        RaidTierConfig decoded = RaidTierConfig.fromNbt(tag);
        assertEquals(List.of(), decoded.rewardCommands());
        assertEquals(old, decoded);
    }

    @Test
    void commandsAreAnImmutableCopy() {
        List<String> commands = new ArrayList<>(List.of("say a"));
        RaidTierConfig config = RaidTierConfig.NORMAL_DEFAULT.withRewardCommands(commands);
        commands.add("say b");
        assertEquals(List.of("say a"), config.rewardCommands());
        assertThrows(UnsupportedOperationException.class, () -> config.rewardCommands().add("say c"));
        assertEquals(List.of(), RaidTierConfig.NORMAL_DEFAULT.withRewardCommands(null).rewardCommands());
    }
}
