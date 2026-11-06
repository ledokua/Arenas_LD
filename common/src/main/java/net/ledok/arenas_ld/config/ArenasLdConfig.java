package net.ledok.arenas_ld.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.ledok.arenas_ld.ArenasLdMod;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import net.ledok.arenas_ld.platform.ArenasPlatform;

/**
 * The mod's JSON config. Always read it through {@link #getInstance()} — {@link #reload()} replaces
 * the instance, so a reference kept elsewhere goes stale after {@code /arenasld reload}.
 */
public class ArenasLdConfig {
    private static final Path CONFIG_PATH = ArenasPlatform.INSTANCE.configDir().resolve(ArenasLdMod.MOD_ID + ".json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile ArenasLdConfig instance;

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
    // Verbose "[debug]" logging of run lifecycle events: teleports (with full player state),
    // reconnect/disconnect decisions, and chunk force-loading. For diagnosing stuck players.
    public boolean debug_logging = false;
    // How far (blocks) a downed run player may fly from where they went down while they have no
    // living teammate to watch; farther away, or in another dimension, they are put back on the spot.
    // 0 or less pins them to it.
    public int downed_spectator_radius = 16;
    // Respawn delays (ticks; 20 = 1 s). A dungeon respawns after its controller's respawn time, but at
    // least this long (live controllers keep their own, often 40 = 2 s); a protect room adds 50% on top.
    public int dungeon_respawn_min_ticks = 200;
    // Each earlier death of the same player in the same dungeon or raid run adds this much, up to the cap.
    public int respawn_per_death_ticks = 100;
    public int respawn_per_death_max_ticks = 1200;
    // Raids: downed players come back together, every raid_respawn_wave_ticks counted from the raid's start,
    // once their own wait (the per-death extra) is over. 0 or less = the controller's respawn time.
    // false = each player on their own timer (the controller's respawn time plus the per-death extra).
    public boolean raid_respawn_together = true;
    public int raid_respawn_wave_ticks = 600;

    public static ArenasLdConfig getInstance() {
        ArenasLdConfig current = instance;
        if (current == null) {
            synchronized (ArenasLdConfig.class) {
                if (instance == null) {
                    read();
                }
                current = instance;
            }
        }
        return current;
    }

    /** Loads (or reloads) the config and returns the instance now in effect. */
    public static ArenasLdConfig load() {
        read();
        return instance;
    }

    /**
     * Re-reads the config file. Returns false when the file could not be read or parsed; the
     * config then in effect stays as it was (defaults on first load), and the file is left
     * untouched so the admin's edits are not overwritten.
     */
    public static synchronized boolean reload() {
        return read();
    }

    private static synchronized boolean read() {
        ArenasLdConfig loaded;
        if (Files.notExists(CONFIG_PATH)) {
            loaded = new ArenasLdConfig();
        } else {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, ArenasLdConfig.class);
            } catch (IOException | JsonParseException e) {
                ArenasLdMod.LOGGER.error("Failed to read Arenas LD config {} — keeping the {} settings; "
                        + "fix the file and run /arenasld reload", CONFIG_PATH,
                    instance == null ? "default" : "previous", e);
                if (instance == null) {
                    instance = new ArenasLdConfig();
                }
                return false;
            }
            if (loaded == null) { // empty file
                loaded = new ArenasLdConfig();
            }
        }
        loaded.sanitize();
        instance = loaded;
        // Writes back so keys added by a newer mod version appear in the file.
        save();
        return true;
    }

    /** Explicit JSON nulls would otherwise reach callers that iterate these lists. */
    private void sanitize() {
        if (run_item_blacklist == null) {
            run_item_blacklist = new ArrayList<>();
        }
        if (run_effect_blacklist == null) {
            run_effect_blacklist = new ArrayList<>();
        }
        if (dungeon_damage_source_filter == null) {
            dungeon_damage_source_filter = "ALL";
        }
        if (puffish_skills_tree_id == null) {
            puffish_skills_tree_id = "puffish_skills:combat";
        }
    }

    /** Writes the current config through a temp file, so a crash mid-write cannot truncate it. */
    public static synchronized void save() {
        if (instance == null) {
            return;
        }
        Path tmp = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(instance, writer);
            }
            try {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ArenasLdMod.LOGGER.error("Failed to save Arenas LD config", e);
        }
    }
}
