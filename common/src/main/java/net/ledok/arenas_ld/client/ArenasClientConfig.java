package net.ledok.arenas_ld.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.platform.ArenasPlatform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The client's own settings ({@code config/arenas_ld-client.json}), read once at startup. */
public final class ArenasClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = ArenasPlatform.INSTANCE.configDir().resolve(ArenasLdMod.MOD_ID + "-client.json");
    private static ArenasClientConfig instance;

    // Where the "you're in a lobby" badge sits: "top_left", "top_right" (under the effect icons) or "hidden".
    public String lobby_badge = "top_left";
    // Ally HP sidebar in runs and parties: "left", "right" or "hidden".
    public String ally_sidebar = "left";
    // Moves the ally sidebar up (negative) or down from the middle of the screen, in GUI pixels.
    public int ally_sidebar_offset_y = 0;
    // Run timer / boss HP HUD: "new" (the wide dark panel, 4.34.0) or "classic" (the small centered timer before it).
    public String run_hud_style = "new";
    // End-of-run loot reveal: "new" (on a dark backing panel) or "classic" (the items alone, as before 4.34.0).
    public String loot_reveal_style = "new";

    public static ArenasClientConfig get() {
        if (instance == null) {
            instance = read();
        }
        return instance;
    }

    private static ArenasClientConfig read() {
        ArenasClientConfig config = null;
        if (Files.exists(PATH)) {
            try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
                config = GSON.fromJson(reader, ArenasClientConfig.class);
            } catch (IOException | JsonParseException e) {
                ArenasLdMod.LOGGER.error("Failed to read {}; using the defaults", PATH, e);
                return new ArenasClientConfig();
            }
        }
        if (config == null) {
            config = new ArenasClientConfig();
        }
        if (config.lobby_badge == null) {
            config.lobby_badge = "top_left";
        }
        if (config.ally_sidebar == null) {
            config.ally_sidebar = "left";
        }
        if (config.run_hud_style == null) {
            config.run_hud_style = "new";
        }
        if (config.loot_reveal_style == null) {
            config.loot_reveal_style = "new";
        }
        // Writes back so new keys appear in the file.
        config.save();
        return config;
    }

    /** Writes the settings to the file (a setting changed in game). */
    public void save() {
        ArenasClientConfig config = this;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            ArenasLdMod.LOGGER.error("Failed to write {}", PATH, e);
        }
    }

    public boolean classicRunHud() {
        return "classic".equals(run_hud_style);
    }

    public boolean classicLootReveal() {
        return "classic".equals(loot_reveal_style);
    }
}
