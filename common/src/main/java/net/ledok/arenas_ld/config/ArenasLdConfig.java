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
    // Blocks dungeon/raid players may break (Adventure mode), as block ids or "#tags". Only in difficulties
    // with "allowBlockBreaking" on; a controller can add to this list or replace it. No drops, nothing restored.
    public java.util.List<String> run_breakable_blocks = new java.util.ArrayList<>(java.util.List.of(
        "bosses_of_mass_destruction:void_blossom",
        "bosses_of_mass_destruction:vine_wall"
    ));
    // Seconds between "start" and the teleport of a dungeon, raid or arena run, to load the arena first (a countdown
    // title for the party; the run starts earlier once everything is loaded). 0: start at once, as before 4.29.0.
    public int run_prepare_seconds = 10;
    // During that wait: chunks loaded around the entrance (a square of this radius, besides the run area itself).
    // Match it to the run dimension's view distance. Max 16.
    public int run_prepare_radius_chunks = 8;
    // Blocks run players (raid, dungeon, arena) can't right-click, as block ids or "#tags", e.g. the respawn anchor
    // (it would set their spawn or explode inside the run).
    public java.util.List<String> run_blocked_use_blocks = new java.util.ArrayList<>(java.util.List.of(
        "minecraft:respawn_anchor"
    ));
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
    // After a dungeon or raid respawn the player can't be hurt for this long (20 = 1 s; 0 = off). It ends early on
    // their first swing or the first damage they deal. Void and /kill still hurt.
    public int respawn_invulnerable_ticks = 20;
    // Raids: downed players come back together at the next mark, every respawn interval counted from the raid's
    // start. The interval is each raid controller's ("Respawn interval" in its admin screen); raid_respawn_wave_ticks
    // is only the fallback for a controller set to 0.
    // false = each player on their own timer (the controller's interval plus the per-death extra).
    public boolean raid_respawn_together = true;
    // With raid_respawn_together: whether earlier deaths (respawn_per_death_*) delay a downed raid player past the
    // shared mark. false (default): everyone downed comes back together at the next mark.
    public boolean raid_per_death_extra = false;
    public int raid_respawn_wave_ticks = 1200;
    // Reward modifiers (see the README's "Reward modifiers"). How often a dungeon or raid completion rolls the
    // tier's per-player loot table before modifiers (LuckPerms +50% makes 4.5 on average, hardcore doubles it).
    public double completion_loot_rolls = 3;
    // Rental builds: a skill-tree class (the falleneden:classes branch with most unlocked nodes) → the preset class its
    // kits use, where the names differ ("earth": "terra"). Unlisted classes use their own name. Those presets show
    // first in the rental preview, marked "Recommended".
    public java.util.Map<String, String> rental_class_presets = new java.util.LinkedHashMap<>();
    // Rental spell books: the highest spell tier each kit level folder may pick ("0": 1, "15": 2, …; the highest folder
    // at or below the kit's level counts). Empty = the book's whole pool.
    public java.util.Map<String, Integer> rental_spell_max_tier = new java.util.LinkedHashMap<>();
    // Arena per-player wave rows roll this many times their own rolls (communal rows don't). Live keeps 3, what the
    // old LuckPerms arena_loot_rolls 3 gave every player.
    public double arena_player_row_rolls = 1;
    // Live per-player combat stats of dungeon and raid runs (the run details screen); false turns all counting off.
    public RunStatsConfig run_stats = new RunStatsConfig();
    // Named run modifiers: id -> name, description and the modifiers it brings.
    public java.util.Map<String, RunModifierDef> run_modifiers = new java.util.LinkedHashMap<>();
    // Which run modifiers every run of an activity gets: "arenas_ld:crypt" (all difficulties) or
    // "arenas_ld:crypt#hard" (one difficulty) -> run modifier ids.
    public java.util.Map<String, java.util.List<String>> activity_modifiers = new java.util.LinkedHashMap<>();
    // Server-wide reward events: a run modifier id active from start to end (ISO times, e.g. 2026-11-07T00:00:00+02:00).
    public java.util.List<RewardEvent> reward_events = new java.util.ArrayList<>();

    /**
     * A named run modifier. The built-in ones (hardcore_run, frozen_ground, glass_cannon, …) are written into the
     * file on load when missing; an entry with the same id replaces one, and "enabled": false hides it.
     */
    public static class RunModifierDef {
        public boolean enabled = true;
        // Shown to players: a translation key or literal text.
        public String name = "";
        public String description = "";
        // An item id for the lobby toggle, e.g. "minecraft:ice".
        public String icon = "";
        // Whether lobby owners can switch it on in the lobby.
        public boolean selectable = false;
        // Activity types it may be picked for: "dungeon", "raid", "arena"; empty = all.
        public java.util.List<String> types = new java.util.ArrayList<>();
        // Picking one modifier switches off the others of the same group (empty = no group).
        public String group = "";
        // Modifier ids it can't be picked with (either side listing the other is enough). Left out: the built-in's.
        public java.util.List<String> conflicts = null;
        public java.util.List<ModifierEntry> modifiers = new java.util.ArrayList<>();
        // Left out: none, or the built-in's when the entry replaces one (so tuning hardcore_run's rewards keeps its one life).
        public Effects effects = null;
    }

    /** What a run modifier does in the run (factors add their bonuses between modifiers: ×4 and ×2 make ×5). */
    public static class Effects {
        // Damage run players take / deal (to anything but other players).
        public double damage_taken = 1;
        public double damage_dealt = 1;
        // Run mobs' max health and movement speed, on top of the run's own scaling.
        public double mob_health = 1;
        public double mob_speed = 1;
        // Run players' movement speed.
        public double player_speed = 1;
        // Run players slide as on ice wherever they walk.
        public boolean frozen_ground = false;
        // Hardcore: one life per player, the hardcore death and eject rules (the built-in hardcore_run).
        public boolean one_life = false;
    }

    /** One stat change of a run modifier: stat loot|xp|currency, kind increased|more, value (0.15 = +15%, 2 = ×2). */
    public static class ModifierEntry {
        public String stat = "loot";
        public String kind = "increased";
        public double value = 0;
    }

    public static class RunStatsConfig {
        public boolean enabled = true;
    }

    /** A run modifier that applies to every payout between start and end. */
    public static class RewardEvent {
        public String modifier = "";
        public String start = "";
        public String end = "";
    }

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
        // Every built-in run modifier is written out with its values, so editing the numbers is enough.
        net.ledok.arenas_ld.rewards.RunModifierCatalog.addMissingBuiltIns(loaded.run_modifiers);
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
        if (run_breakable_blocks == null) {
            run_breakable_blocks = new ArrayList<>();
        }
        if (run_blocked_use_blocks == null) {
            run_blocked_use_blocks = new ArrayList<>();
        }
        if (dungeon_damage_source_filter == null) {
            dungeon_damage_source_filter = "ALL";
        }
        if (run_stats == null) {
            run_stats = new RunStatsConfig();
        }
        if (run_modifiers == null) {
            run_modifiers = new java.util.LinkedHashMap<>();
        }
        if (activity_modifiers == null) {
            activity_modifiers = new java.util.LinkedHashMap<>();
        }
        if (reward_events == null) {
            reward_events = new ArrayList<>();
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
