package net.ledok.arenas_ld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class ArenasLdConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve(ArenasLdMod.MOD_ID + ".json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ArenasLdConfig instance;

    // Config fields with default values
    public String puffish_skills_tree_id = "puffish_skills:combat";
    public String dungeon_damage_source_filter = "ALL";
    // Items that may not be used while a player is in a raid/dungeon/arena run. Entries are item ids
    // (e.g. "minecraft:ender_pearl") or item tags prefixed with '#' (e.g. "#minecraft:beds").
    public java.util.List<String> run_item_blacklist = new java.util.ArrayList<>(java.util.List.of(
        "minecraft:ender_pearl",
        "minecraft:chorus_fruit"
    ));
    // Potion/mob effects stripped from players during a run (e.g. "minecraft:speed",
    // "galosphere:astral"). Removed every tick, so they can't be applied mid-run either.
    public java.util.List<String> run_effect_blacklist = new java.util.ArrayList<>();

    public static ArenasLdConfig getInstance() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    public static ArenasLdConfig load() {
        try (FileReader reader = new FileReader(CONFIG_PATH.toFile())) {
            instance = GSON.fromJson(reader, ArenasLdConfig.class);
            if (instance == null) {
                instance = new ArenasLdConfig();
            }
        } catch (IOException e) {
            instance = new ArenasLdConfig();
        }
        save();
        return instance;
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            ArenasLdMod.LOGGER.error("Failed to save Arenas LD config", e);
        }
    }

    public static void reload() {
        load();
    }
}
