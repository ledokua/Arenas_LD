package net.ledok.arenas_ld.stats;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;

/**
 * The run's stats JSON in its block entity's save. An NBT string can't pass 65,535 bytes of (modified) UTF-8, and a
 * long raid's stats did (4.27.0: the chunk then failed to save). Short JSON stays one string, as before; longer JSON
 * is written as a list of parts of at most {@link #PART_CHARS} chars (each at most 3 bytes, so under the limit).
 * Both forms read back.
 */
public final class RunStatsSave {
    static final int PART_CHARS = 16_000;

    public static final Codec<String> CODEC = Codec.either(Codec.STRING, Codec.STRING.listOf()).xmap(
        either -> either.map(s -> s, parts -> String.join("", parts)),
        json -> json.length() <= PART_CHARS ? Either.left(json) : Either.right(split(json)));

    private RunStatsSave() {
    }

    static List<String> split(String json) {
        List<String> parts = new ArrayList<>(json.length() / PART_CHARS + 1);
        for (int i = 0; i < json.length(); i += PART_CHARS) {
            parts.add(json.substring(i, Math.min(json.length(), i + PART_CHARS)));
        }
        return parts;
    }
}
