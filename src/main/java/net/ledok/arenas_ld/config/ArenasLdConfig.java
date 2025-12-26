package net.ledok.arenas_ld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.ledok.arenas_ld.ArenasLdMod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;

public class ArenasLdConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public String puffish_skills_tree_id = "puffish_skills:combat";

    public static ArenasLdConfig load() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(ArenasLdMod.MOD_ID);
        File configFile = configDir.resolve("arenas_ld.json").toFile();

        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                return GSON.fromJson(reader, ArenasLdConfig.class);
            } catch (IOException e) {
                ArenasLdMod.LOGGER.error("Failed to load config", e);
            }
        }

        ArenasLdConfig config = new ArenasLdConfig();
        config.save();
        return config;
    }

    public void save() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(ArenasLdMod.MOD_ID);
        File configFile = configDir.resolve("arenas_ld.json").toFile();

        if (!configFile.getParentFile().exists()) {
            configFile.getParentFile().mkdirs();
        }

        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            ArenasLdMod.LOGGER.error("Failed to save config", e);
        }
    }

    public void reloadFromFile() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(ArenasLdMod.MOD_ID);
        File configFile = configDir.resolve("arenas_ld.json").toFile();

        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                ArenasLdConfig loaded = GSON.fromJson(reader, ArenasLdConfig.class);
                this.puffish_skills_tree_id = loaded.puffish_skills_tree_id;
            } catch (IOException e) {
                ArenasLdMod.LOGGER.error("Failed to reload config", e);
            }
        } else {
            save(); // Create if missing
        }
    }
}
