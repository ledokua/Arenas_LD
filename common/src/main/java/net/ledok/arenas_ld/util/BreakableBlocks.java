package net.ledok.arenas_ld.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * A dungeon or raid controller's own breakable blocks list ({@code breakable_blocks}): {@code mode} "add" puts
 * {@code blocks} on top of the global {@code run_breakable_blocks}, "replace" uses only {@code blocks} (empty =
 * nothing breakable). Entries are block ids or {@code #tags}. See {@link RunBlockBreaking}.
 */
public record BreakableBlocks(String mode, List<String> blocks) {
    public static final String ADD = "add";
    public static final String REPLACE = "replace";
    public static final BreakableBlocks DEFAULT = new BreakableBlocks(ADD, List.of());

    public static final Codec<BreakableBlocks> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("mode", ADD).forGetter(BreakableBlocks::mode),
        Codec.STRING.listOf().optionalFieldOf("blocks", List.of()).forGetter(BreakableBlocks::blocks)
    ).apply(i, BreakableBlocks::new));

    private static final String NBT_KEY = "BreakableBlocks";

    public BreakableBlocks {
        mode = REPLACE.equals(mode) ? REPLACE : ADD;
        List<String> clean = new ArrayList<>();
        if (blocks != null) {
            for (String entry : blocks) {
                if (entry != null && !entry.isBlank() && !clean.contains(entry.trim())) {
                    clean.add(entry.trim());
                }
            }
        }
        blocks = List.copyOf(clean);
    }

    public boolean replaces() {
        return REPLACE.equals(mode);
    }

    /** The entries a run of this controller allows, given the global list. */
    public List<String> effective(List<String> global) {
        if (replaces()) {
            return blocks;
        }
        List<String> all = new ArrayList<>(global == null ? List.of() : global);
        for (String entry : blocks) {
            if (!all.contains(entry)) {
                all.add(entry);
            }
        }
        return all;
    }

    /** Writes this list to a controller's NBT (nothing for the default). */
    public void save(CompoundTag nbt) {
        if (equals(DEFAULT)) {
            return;
        }
        CODEC.encodeStart(NbtOps.INSTANCE, this).ifSuccess(tag -> nbt.put(NBT_KEY, tag));
    }

    public static BreakableBlocks load(CompoundTag nbt) {
        Tag tag = nbt.get(NBT_KEY);
        if (tag == null) {
            return DEFAULT;
        }
        return CODEC.parse(NbtOps.INSTANCE, tag)
            .resultOrPartial(err -> ArenasLdMod.LOGGER.warn("[Arenas] Bad breakable blocks list: {}", err))
            .orElse(DEFAULT);
    }

    /** A dungeon or raid controller that has its own list. */
    public interface Holder {
        BreakableBlocks getBreakableBlocks();

        void setBreakableBlocks(BreakableBlocks blocks);
    }
}
