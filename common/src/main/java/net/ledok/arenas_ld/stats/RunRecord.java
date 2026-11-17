package net.ledok.arenas_ld.stats;

import java.util.ArrayList;
import java.util.List;

/**
 * A run's stats as saved: the run file written when it ends (Gson, gzip), and the copy a running run keeps in its
 * own save so a restart doesn't zero it. Plain fields so the files stay readable for balance work.
 */
public final class RunRecord {
    /** 2 (4.37.0): players have {@code events} / {@code eventCounts}; the timeline is every 30 s only. */
    public static final int VERSION = 2;

    public int version = VERSION;
    /** "<epoch ms>-<activity path>", also the file name. */
    public String runId = "";
    public String activityId = "";
    public String activityName = "";
    /** "dungeon" or "raid". */
    public String type = "";
    public String difficulty = "";
    /** The difficulty's level (from its loot table, "…/level_40" → 40); -1 when it has none. */
    public int runLevel = -1;
    public boolean hardcore;
    public List<String> modifiers = new ArrayList<>();
    /** The controller's rental mode for this run: "optional" or "required" (everyone rented). 4.39.0+; "" in older files. */
    public String rentalMode = "";
    /** The completion's base loot rolls (the controller's own or the global). 4.39.0+; -1 in older files. */
    public double baseLootRolls = -1;
    public String dimension = "";
    public int x, y, z;
    public long startEpochMs;
    public int durationSeconds;
    /** "win", "fail", "abandoned", "forced"; empty while running. */
    public String result = "";
    public float unattributed;
    /** The difficulty's time limit in seconds (0 = none). 4.25.0+; -1 in older files. */
    public int timeLimitSeconds = -1;
    /** The run's bosses: max HP when they spawned (or the highest seen), HP left at the end (0 = killed). */
    public List<Boss> bosses = new ArrayList<>();
    /** Sums over {@link #bosses}. */
    public float bossMaxHealth, bossHealthLeft;
    public List<Phase> phases = new ArrayList<>();
    public List<Player> players = new ArrayList<>();

    public static final class Boss {
        public String uuid = "";
        /** Entity type id, and the name it had (a custom name when the boss has one). */
        public String id = "";
        public String name = "";
        public float maxHealth;
        public float healthLeft;
        public boolean killed;
    }

    /** A boss reached {@code percent}% HP (0 = killed) {@code seconds} into the run. */
    public static final class Phase {
        public String boss = "";
        public int percent;
        public int seconds;
    }

    public static final class Player {
        public String uuid = "";
        public String name = "";
        /** "alive", "downed", "out", "offline", "forfeited" (left with /exit mid-run), "won", "lost" when the run ended. */
        public String state = "";
        public int deaths;
        public float dealt, bossDealt, overkill;
        public float taken, takenEnemies, takenEnvironment, takenRaw;
        public float blockedShield, blockedArmor, blockedResistance, blockedEnchant, blockedAbsorption;
        public float healedSelf, healedOthers, overheal;
        public int kills, bossKills, assists;
        public int aliveSeconds;
        public List<String> loot = new ArrayList<>();
        /** The reward breakdown this player got on a win (4.36.0+), one text component (JSON) per line. */
        public List<String> rewards = new ArrayList<>();
        /**
         * Boss hits per weapon pair (4.27.0+), most damage first: hits, damage, stat ranges at the moment of the hits,
         * and the effects active while hitting.
         */
        public List<WeaponUse> weapons = new ArrayList<>();
        /** Stats every 30 s and on a weapon or attack speed/damage change (4.27.0+). */
        public List<Stats> timeline = new ArrayList<>();
        /**
         * The run event log (version 2+, {@code RunEvents}): {@code [tick since the run started, type, fields…]} in
         * order, at most 5,000; {@code eventCounts} counts every event by type (over the cap, refreshes and ignored hits
         * too), {@code eventsDropped} how many were left out over the cap.
         */
        public List<List<Object>> events = new ArrayList<>();
        public java.util.Map<String, Integer> eventCounts = new java.util.TreeMap<>();
        public int eventsDropped;
        /** Rental build (4.26.0+): the loadout id ("" = their own gear), its class and level band (5…25). */
        /** Left with /exit before the run was over (4.27.0+). */
        public boolean forfeited;
        public String loadoutId = "";
        public String rentalClass = "";
        public int rentalBand;
        /** The spells picked for the rental spell book (empty: the kit's own) and its pool. 4.40.0+. */
        public List<String> rentalSpells = new ArrayList<>();
        public String rentalPool = "";
        /** Level and gear when they joined the run (null: not taken, e.g. a file from before 4.24.0). */
        public Gear gear;
        /** Their three biggest single hits, biggest first. */
        public List<Hit> topHits = new ArrayList<>();
        /** Seconds in which they landed damage on a boss ("endless attack DPS" = bossDealt / bossActiveSeconds). */
        public int bossActiveSeconds;
        /** Their stats when the fight began: their first hit on a boss (their first hit at all until then). */
        public Stats statsStart;
        /** Their stats in their best damage minute (taken at that minute's first hit), and what that minute dealt. */
        public Stats statsBestMinute;
        public float bestMinuteDealt;
        /** Skill-tree nodes unlocked at run start, "<category>/<node>" (Pufferfish skills). */
        public List<String> skills = new ArrayList<>();
        /** Damage dealt (HP taken) per source: "spell:…", "item:…" (the weapon; "item:minecraft:air" = fists), "entity:…" (a pet, a projectile without a weapon), "damage:…" (no attacker: poison, fire, …). */
        public java.util.Map<String, Float> damageBySource = new java.util.LinkedHashMap<>();
        /**
         * Effective healing (overheal and natural food regeneration left out) per source (4.26.3+): "spell:<spell>"
         * (Spell Engine heals), "effect:<effect>" (regeneration and other effect ticks), "other:<class>" (the first
         * mod class that called heal: potions, lifesteal passives, relics). Self = on themselves, others = given.
         */
        public java.util.Map<String, Float> healBySource = new java.util.LinkedHashMap<>();
        public java.util.Map<String, Float> healOthersBySource = new java.util.LinkedHashMap<>();
        /** Their five biggest single heals (on themselves or, for a caster, on others), biggest first (4.26.3+). */
        public List<Heal> topHeals = new ArrayList<>();
    }

    /** A player's level and gear at run start. */
    public static final class Gear {
        /** The {@code loot_stage:loot_stage} attribute; -1 without Loot Stage. */
        public int playerLevel = -1;
        /** Average and highest Dungeon Difficulty power level ({@code dd.rsf}) of the slots that have one; -1: none. */
        public float powerAvg = -1;
        public int powerMax = -1;
        public List<Slot> slots = new ArrayList<>();
        /**
         * Their class: the equipped Spell Engine spell book's item id ("wizards:arcane_spell_book"), else
         * "skills:<branch>" (the {@code falleneden:classes} skill-tree branch with most unlocked nodes), else "".
         */
        public String playerClass = "";
        /**
         * The spell book itself (4.36.2), as SNBT with its components: one item (spell_engine:spell_book) takes its
         * class look and name from them, so the run details draw this stack. "" without a book.
         */
        public String playerClassStack = "";
        /** The spell book's spell pool id ("" without a book or when it names none). */
        public String spellPool = "";
    }

    /** One equipment slot: "head", "chest", "legs", "feet", "mainhand", "offhand". */
    public static final class Slot {
        public String slot = "";
        public String item = "";
        /** {@code dd.rsf}; -1 when the item has none (crafted gear). */
        public int power = -1;
        /** {@code limit_break_level} (Limit Breaker); -1 when none. */
        public int limitBreak = -1;
    }

    /** One weapon pair's boss hits. */
    public static final class WeaponUse {
        public String mainHand = "";
        public String offHand = "";
        public int hits;
        public float damage;
        /**
         * Per stat ("attack_speed", "attack_damage", "crit_chance", "crit_damage", "spell_crit_chance",
         * "spell_crit_damage", "school_power" = the spell power of the hit's school): [min, mean, max, hits counted].
         */
        public java.util.Map<String, double[]> stats = new java.util.LinkedHashMap<>();
        /** Effects active while hitting: id → highest amplifier (0 = level I). */
        public java.util.Map<String, Integer> effects = new java.util.TreeMap<>();
    }

    /** A player's combat stats at one moment. */
    public static final class Stats {
        /** Seconds into the run. */
        public int seconds;
        /** Timeline entries (4.27.0+): why it was taken ("30s", "change") and the items in hand. */
        public String reason;
        public String mainHand;
        public String offHand;
        /**
         * Attribute values: attack damage and speed, max health, armor and toughness, {@code eden_ld:damage_reduction},
         * {@code spell_engine:evasion_chance}, {@code mrpg_lib:rage_modifier}, and every {@code spell_power:*} and
         * {@code critical_strike:*} above 0.
         */
        public java.util.Map<String, Double> attributes = new java.util.TreeMap<>();
        /** Active effects, "id amplifier" (amplifier 0 = level I). */
        public List<String> effects = new ArrayList<>();
    }

    /** One heal: HP restored (overheal left out), its source, seconds into the run, and who got it when not the healer. */
    public static final class Heal {
        public float amount;
        public String source = "";
        public int seconds;
        /** The healed player's name for a heal on someone else; "" for a heal on themselves. */
        public String target = "";
    }

    /** One hit as it reached the target (after armor and the rest, before the HP cap). */
    public static final class Hit {
        public float amount;
        /** The target's entity type id. */
        public String target = "";
        public boolean boss;
        public int seconds;
        /** What dealt it, as in {@link Player#damageBySource}. */
        public String source = "";
        public String damageType = "";
    }
}
