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
    void hasExactlyFourValuesInExpectedOrder() {
        DifficultyTier[] values = DifficultyTier.values();
        assertEquals(4, values.length);
        assertEquals(DifficultyTier.EASY, values[0]);
        assertEquals(DifficultyTier.NORMAL, values[1]);
        assertEquals(DifficultyTier.HARD, values[2]);
        assertEquals(DifficultyTier.NIGHTMARE, values[3]);
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
