package net.ledok.arenas_ld.activity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.api.ArenasApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Server-global index of every activity: its id, where its controller is, and a copy of what the
 * API reports. Persisted so the API lists every activity even before (or without) its controller's
 * chunk being loaded, and so ids stay unique across controllers that are not loaded.
 *
 * <p>The controllers are the source of truth; {@link ActivityRegistry} copies their state in here.
 */
public final class ActivityIndex extends SavedData {
    private static final String DATA_ID = "arenas_ld_activities";

    private static final Codec<ArenasApi.ActivityType> TYPE_CODEC = Codec.STRING.xmap(
        name -> {
            try {
                return ArenasApi.ActivityType.valueOf(name);
            } catch (IllegalArgumentException e) {
                return ArenasApi.ActivityType.DUNGEON;
            }
        },
        Enum::name);

    /** 4.5.0 files also stored a requiredLevel here; unknown keys are ignored. */
    private static final Codec<ArenasApi.Difficulty> DIFFICULTY_CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.fieldOf("id").forGetter(ArenasApi.Difficulty::id)
    ).apply(i, ArenasApi.Difficulty::new));

    /**
     * One activity as last reported by its controller. 4.5.0's requiredLevel/cardArt keys are ignored.
     *
     * @param generatedId the id was made up by Arenas_LD ({@link ActivityRegistry#attach}), not set
     *                    by staff; saves from before 4.9.0 don't say, see {@link ActivityIndex#looksGenerated}
     */
    public record Entry(
        ResourceLocation id,
        ArenasApi.ActivityType type,
        ResourceKey<Level> dimension,
        BlockPos pos,
        String name,
        List<ArenasApi.Difficulty> difficulties,
        boolean generatedId
    ) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Entry::id),
            TYPE_CODEC.fieldOf("type").forGetter(Entry::type),
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Entry::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
            Codec.STRING.optionalFieldOf("name", "").forGetter(Entry::name),
            DIFFICULTY_CODEC.listOf().optionalFieldOf("difficulties", List.of()).forGetter(Entry::difficulties),
            Codec.BOOL.optionalFieldOf("generatedId").forGetter(e -> Optional.of(e.generatedId()))
        ).apply(i, (id, type, dimension, pos, name, difficulties, generatedId) -> new Entry(id, type, dimension, pos,
            name, difficulties, generatedId.orElseGet(() -> looksGenerated(id, type, pos)))));

        public boolean isAt(ResourceKey<Level> dimension, BlockPos pos) {
            return this.dimension.equals(dimension) && this.pos.equals(pos);
        }

        /** "minecraft:overworld 10 64 -3", for chat and logs. */
        public String location() {
            return dimension.location() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
        }
    }

    /**
     * For entries saved before 4.9.0, which didn't record whether their id was generated: the
     * position form ({@code arenas_ld:dungeon_10_64_-3}, maybe {@code _2}…) for the entry's own
     * position is taken as generated. The name form ({@code arenas_ld:dungeon_old_crypt}) can't be
     * told from one staff typed, so it isn't.
     */
    static boolean looksGenerated(ResourceLocation id, ArenasApi.ActivityType type, BlockPos pos) {
        if (!ArenasLdMod.MOD_ID.equals(id.getNamespace())) {
            return false;
        }
        String base = type.name().toLowerCase(Locale.ROOT) + "_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
        String path = id.getPath();
        if (path.equals(base)) {
            return true;
        }
        String suffix = path.startsWith(base + "_") ? path.substring(base.length() + 1) : "";
        return !suffix.isEmpty() && suffix.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static final Factory<ActivityIndex> FACTORY = new Factory<>(ActivityIndex::new, ActivityIndex::load, null);

    private final Map<ResourceLocation, Entry> entries = new LinkedHashMap<>();

    public static ActivityIndex get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_ID);
    }

    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public @Nullable Entry get(ResourceLocation id) {
        return entries.get(id);
    }

    /** Stores the entry; returns whether anything changed. */
    boolean put(Entry entry) {
        Entry previous = entries.put(entry.id(), entry);
        if (entry.equals(previous)) {
            return false;
        }
        setDirty();
        return true;
    }

    boolean remove(ResourceLocation id) {
        if (entries.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    /** Removes every entry at the location except {@code keep}; returns whether any was removed. */
    boolean removeAt(ResourceKey<Level> dimension, BlockPos pos, @Nullable ResourceLocation keep) {
        boolean removed = entries.values().removeIf(e -> e.isAt(dimension, pos) && !e.id().equals(keep));
        if (removed) {
            setDirty();
        }
        return removed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        Entry.CODEC.listOf().encodeStart(NbtOps.INSTANCE, new ArrayList<>(entries.values()))
            .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to save the activity index: {}", err))
            .ifPresent(list -> tag.put("activities", list));
        return tag;
    }

    private static ActivityIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        ActivityIndex index = new ActivityIndex();
        if (tag.contains("activities")) {
            Entry.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("activities"))
                .resultOrPartial(err -> ArenasLdMod.LOGGER.error("Failed to load the activity index: {}", err))
                .ifPresent(list -> list.forEach(e -> index.entries.put(e.id(), e)));
        }
        return index;
    }
}
