package net.ledok.arenas_ld.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class DungeonManager {
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("arenas_ld_dungeons.json").toFile();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<DungeonEntry> dungeons = new ArrayList<>();

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                Type listType = new TypeToken<ArrayList<DungeonEntry>>() {}.getType();
                List<DungeonEntry> loaded = GSON.fromJson(reader, listType);
                if (loaded != null) {
                    dungeons.clear();
                    dungeons.addAll(loaded);
                    dungeons.sort(Comparator.comparingInt(DungeonEntry::priority));
                }
            } catch (IOException e) {
                ArenasLdMod.LOGGER.error("Failed to load dungeon config", e);
            }
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(dungeons, writer);
        } catch (IOException e) {
            ArenasLdMod.LOGGER.error("Failed to save dungeon config", e);
        }
    }

    public static void addDungeon(String name, BlockPos pos, int priority, boolean isRaid) {
        dungeons.add(new DungeonEntry(name, pos, priority, isRaid));
        dungeons.sort(Comparator.comparingInt(DungeonEntry::priority));
        save();
    }

    public static boolean removeDungeon(String name) {
        boolean removed = dungeons.removeIf(d -> d.name().equals(name));
        if (removed) save();
        return removed;
    }
    
    public static Optional<DungeonEntry> getDungeon(String name) {
        return dungeons.stream().filter(d -> d.name().equals(name)).findFirst();
    }

    public static List<DungeonEntry> getDungeons() {
        return new ArrayList<>(dungeons);
    }

    public record DungeonEntry(String name, BlockPos pos, int priority, boolean isRaid) {
        public static final StreamCodec<FriendlyByteBuf, DungeonEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, DungeonEntry::name,
                BlockPos.STREAM_CODEC, DungeonEntry::pos,
                ByteBufCodecs.INT, DungeonEntry::priority,
                ByteBufCodecs.BOOL, DungeonEntry::isRaid,
                DungeonEntry::new
        );
    }
}
