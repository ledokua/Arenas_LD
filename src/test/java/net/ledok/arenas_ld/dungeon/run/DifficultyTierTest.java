package net.ledok.arenas_ld.dungeon.run;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifficultyTierTest {

    @Test
    void hasExactlyThreeValuesInExpectedOrder() {
        DifficultyTier[] values = DifficultyTier.values();
        assertEquals(3, values.length);
        assertEquals(DifficultyTier.NORMAL, values[0]);
        assertEquals(DifficultyTier.HARD, values[1]);
        assertEquals(DifficultyTier.HELL, values[2]);
    }

    @Test
    void codecRoundTripsEveryValue() {
        for (DifficultyTier tier : DifficultyTier.values()) {
            Tag encoded = DifficultyTier.CODEC.encodeStart(NbtOps.INSTANCE, tier).getOrThrow();
            DataResult<DifficultyTier> decoded = DifficultyTier.CODEC.parse(NbtOps.INSTANCE, encoded);
            assertEquals(tier, decoded.getOrThrow());
        }
    }

    @Test
    void codecDecodesUnknownStringAsNormal() {
        DataResult<DifficultyTier> decoded =
            DifficultyTier.CODEC.parse(NbtOps.INSTANCE, StringTag.valueOf("GARBAGE_VALUE"));
        assertTrue(decoded.result().isPresent());
        assertEquals(DifficultyTier.NORMAL, decoded.getOrThrow());
    }

    @Test
    void streamCodecRoundTripsEveryValue() {
        for (DifficultyTier tier : DifficultyTier.values()) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            DifficultyTier.STREAM_CODEC.encode(buf, tier);
            DifficultyTier decoded = DifficultyTier.STREAM_CODEC.decode(buf);
            assertEquals(tier, decoded);
            buf.release();
        }
    }
}
