package net.ledok.arenas_ld.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One player's totals in one run. Primitive counters only (the hot path adds to them, nothing else). */
public final class PlayerCounters {
    public final RunStats run;
    public final UUID player;
    public String name;
    /** HP their hits (projectiles, pets, summons, damage over time included) took off the run's mobs. */
    public float dealt;
    /** Of {@link #dealt}, what went into the run's bosses. */
    public float bossDealt;
    /** Damage past what the mob had left (staff). */
    public float overkill;
    /** HP they lost: from enemies, from the environment (falls, lava, no attacker). */
    public float taken;
    public float takenEnemies;
    public float takenEnvironment;
    /** Damage that reached their armor, before any protection (staff). */
    public float takenRaw;
    /** Damage stopped: shield, armor, Resistance, protection enchantments, absorption hearts. */
    public float blockedShield;
    public float blockedArmor;
    public float blockedResistance;
    public float blockedEnchant;
    public float blockedAbsorption;
    /** HP restored on themselves, and on other players (Spell Engine heals credit the caster). */
    public float healedSelf;
    public float healedOthers;
    /** Healing past full HP (staff). */
    public float overheal;
    /** Killing blows; of them, on bosses; and assists (10%+ of a mob's HP, not the killing blow; staff). */
    public int kills;
    public int bossKills;
    public int assists;
    /** Ticks they were alive in the run (not downed, not out, online). */
    public int aliveTicks;
    /** What the run paid them, "3x minecraft:diamond" (staff). */
    public final List<String> loot = new ArrayList<>();
    /** Seconds in which they landed damage on a boss; the last such second (game time / 20). */
    public int bossActiveSeconds;
    long lastBossSecond = Long.MIN_VALUE;
    /** Stats when the fight began (their first boss hit; their first hit until then). */
    RunRecord.Stats statsStart;
    boolean statsStartOnBoss;

    /** Whether their stats at the start are taken (game tests). */
    public boolean hasStartStats() {
        return statsStart != null;
    }
    /** Best damage minute so far, and the minute going on now (run minute, its damage, stats at its first hit). */
    RunRecord.Stats statsBestMinute;
    float bestMinuteDealt;
    long minute = Long.MIN_VALUE;
    float minuteDealt;
    RunRecord.Stats minuteStats;
    /** Skill-tree nodes unlocked at run start. */
    java.util.List<String> skills;
    /** Boss hits per weapon pair (main hand → offhand → aggregate). */
    final java.util.Map<net.minecraft.world.item.Item, java.util.Map<net.minecraft.world.item.Item, FightStats.WeaponAgg>> weapons =
        new java.util.HashMap<>();
    /** Timeline snapshots, and what the last one saw (not saved: a new one comes 30 s after a restart). */
    final List<RunRecord.Stats> timeline = new ArrayList<>();
    long lastMomentTick = Long.MIN_VALUE;
    net.minecraft.world.item.Item lastMain, lastOff;
    double lastSpeed, lastDamage;
    /** A duel fighter's side, and how often they went down (a duel's lethal hits; 4.42.0). */
    public String side = "";
    public int pvpDeaths;
    /** Left the run with /exit before it was over ("forfeited"). */
    public boolean forfeited;
    /** Rental build in this run: the loadout id, the class and the level band (null/""/0: their own gear). */
    public String loadoutId;
    public String rentalClass = "";
    public int rentalBand;
    /** The spells picked for the rental book (empty: the kit's own) and that book's pool (4.40.0). */
    public java.util.List<String> rentalSpells = java.util.List.of();
    public String rentalPool = "";
    /** Whether the picked spells are in the lent book now, and how many seconds that has failed (not saved). */
    public boolean rentalSpellsApplied;
    public int rentalSpellsTries;
    /** Failed re-begins of the rental after a restart, and the server tick of the next try (not saved). */
    public int rentalRejoinTries;
    public int rentalRetryTick;
    /** Level and gear when they joined the run (null until taken). */
    public RunRecord.Gear gear;
    /** Their biggest hits, biggest first (at most {@link RunStats#TOP_HITS}). */
    final List<RunRecord.Hit> topHits = new ArrayList<>();
    /**
     * Damage per source. Keys are what the hot path has at hand without allocating (a spell or damage type holder,
     * an item, an entity type) and turn into labels when saved; after a restart the saved labels are keys too.
     */
    final java.util.Map<Object, float[]> bySource = new java.util.HashMap<>();
    /** Effective healing per source ("spell:…", "effect:…", "other:<class>"), on themselves, and given to other players. */
    final java.util.Map<String, float[]> healBySource = new java.util.HashMap<>();
    final java.util.Map<String, float[]> healOthersBySource = new java.util.HashMap<>();
    /** Their biggest heals, biggest first (at most {@link RunStats#TOP_HEALS}). */
    final List<RunRecord.Heal> topHeals = new ArrayList<>();

    PlayerCounters(RunStats run, UUID player, String name) {
        this.run = run;
        this.player = player;
        this.name = name;
    }

    public float healed() {
        return healedSelf + healedOthers;
    }

    public float blocked() {
        return blockedShield + blockedArmor + blockedResistance + blockedEnchant + blockedAbsorption;
    }

    /** The totals as saved (run file, the run's save). */
    // ── run event log (4.37.0, RunEvents) ──
    final List<List<Object>> events = new ArrayList<>();
    final java.util.Map<String, Integer> eventCounts = new java.util.TreeMap<>();
    int eventsDropped;
    /** The last swap event per slot (merged with a swap of the same slot right after it). */
    final java.util.Map<String, List<Object>> lastSwap = new java.util.HashMap<>();
    /** The last landed hit's attacker / damage type: the cause a "downed" event names. */
    String lastCause = "";
    // the open hit (ServerPlayer.hurt HEAD → RETURN)
    int hitDepth;
    long hitGameTime;
    float hitAmount, hitHpBefore, hitRaw, hitAfterArmor, hitAfterMagic, hitAbsorbed, hitToHealth;
    /** What the hit really took off health ({@code setHealth} in {@code Player.actuallyHurt}), -1 when not seen. */
    float hitApplied = -1;
    String hitAttacker = "", hitDamageType = "";
    boolean hitIframes, hitLanded, hitShield, hitDodged;
    final List<String> hitProcs = new ArrayList<>();
    final List<List<Object>> hitAfter = new ArrayList<>();

    /** The reward breakdown on a win ("Loot: base 3 · Hardcore ×2 → 6"), for the run details' Rewards tab. */
    final List<net.minecraft.network.chat.Component> rewards = new ArrayList<>();

    public RunRecord.Player toRecord(int deaths, String state) {
        RunRecord.Player p = new RunRecord.Player();
        p.uuid = player.toString();
        p.name = name;
        p.state = state;
        p.side = side;
        p.deaths = deaths;
        for (net.minecraft.network.chat.Component line : rewards) {
            p.rewards.add(net.minecraft.network.chat.Component.Serializer.toJson(line,
                net.minecraft.core.RegistryAccess.EMPTY));
        }
        p.dealt = dealt;
        p.bossDealt = bossDealt;
        p.overkill = overkill;
        p.taken = taken;
        p.takenEnemies = takenEnemies;
        p.takenEnvironment = takenEnvironment;
        p.takenRaw = takenRaw;
        p.blockedShield = blockedShield;
        p.blockedArmor = blockedArmor;
        p.blockedResistance = blockedResistance;
        p.blockedEnchant = blockedEnchant;
        p.blockedAbsorption = blockedAbsorption;
        p.healedSelf = healedSelf;
        p.healedOthers = healedOthers;
        p.overheal = overheal;
        p.kills = kills;
        p.bossKills = bossKills;
        p.assists = assists;
        p.aliveSeconds = aliveTicks / 20;
        p.loot = new ArrayList<>(loot);
        p.gear = gear;
        p.forfeited = forfeited;
        p.weapons = FightStats.save(weapons);
        p.timeline = new ArrayList<>(timeline);
        p.events = new ArrayList<>(events);
        p.eventCounts = new java.util.TreeMap<>(eventCounts);
        p.eventsDropped = eventsDropped;
        p.loadoutId = loadoutId == null ? "" : loadoutId;
        p.rentalClass = rentalClass;
        p.rentalBand = rentalBand;
        p.rentalSpells = new java.util.ArrayList<>(rentalSpells);
        p.rentalPool = rentalPool;
        p.bossActiveSeconds = bossActiveSeconds;
        p.statsStart = statsStart;
        boolean nowBest = minuteStats != null && minuteDealt > bestMinuteDealt;
        p.statsBestMinute = nowBest ? minuteStats : statsBestMinute;
        p.bestMinuteDealt = Math.round((nowBest ? minuteDealt : bestMinuteDealt) * 10) / 10f;
        p.skills = skills == null ? new ArrayList<>() : new ArrayList<>(skills);
        p.topHits = new ArrayList<>(topHits);
        java.util.List<java.util.Map.Entry<Object, float[]>> sources = new ArrayList<>(bySource.entrySet());
        sources.sort((a, b) -> Float.compare(b.getValue()[0], a.getValue()[0]));
        for (java.util.Map.Entry<Object, float[]> source : sources) {
            p.damageBySource.merge(RunStats.sourceLabel(source.getKey()), source.getValue()[0], Float::sum);
        }
        p.healBySource = sorted(healBySource);
        p.healOthersBySource = sorted(healOthersBySource);
        p.topHeals = new ArrayList<>(topHeals);
        return p;
    }

    /** Back from a save (a restart mid-run). */
    void load(RunRecord.Player p) {
        if (p.events != null) {
            for (List<Object> event : p.events) {
                List<Object> copy = new ArrayList<>(event.size());
                for (Object field : event) {
                    // Gson reads every number as a double: whole ones back to longs (ticks, amplifiers).
                    copy.add(field instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15 ? (Object) d.longValue() : field);
                }
                events.add(copy);
            }
        }
        if (p.eventCounts != null) {
            eventCounts.putAll(p.eventCounts);
        }
        eventsDropped = p.eventsDropped;
        side = p.side == null ? "" : p.side;
        if (p.rewards != null) {
            for (String json : p.rewards) {
                try {
                    var line = net.minecraft.network.chat.Component.Serializer.fromJson(json, net.minecraft.core.RegistryAccess.EMPTY);
                    if (line != null) {
                        rewards.add(line);
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        dealt = p.dealt;
        bossDealt = p.bossDealt;
        overkill = p.overkill;
        taken = p.taken;
        takenEnemies = p.takenEnemies;
        takenEnvironment = p.takenEnvironment;
        takenRaw = p.takenRaw;
        blockedShield = p.blockedShield;
        blockedArmor = p.blockedArmor;
        blockedResistance = p.blockedResistance;
        blockedEnchant = p.blockedEnchant;
        blockedAbsorption = p.blockedAbsorption;
        healedSelf = p.healedSelf;
        healedOthers = p.healedOthers;
        overheal = p.overheal;
        kills = p.kills;
        bossKills = p.bossKills;
        assists = p.assists;
        aliveTicks = p.aliveSeconds * 20;
        if (p.loot != null) {
            loot.addAll(p.loot);
        }
        gear = p.gear;
        forfeited = p.forfeited;
        FightStats.load(weapons, p.weapons);
        if (p.timeline != null) {
            timeline.addAll(p.timeline);
        }
        loadoutId = p.loadoutId == null || p.loadoutId.isEmpty() ? null : p.loadoutId;
        rentalClass = p.rentalClass == null ? "" : p.rentalClass;
        rentalBand = p.rentalBand;
        rentalSpells = p.rentalSpells == null ? java.util.List.of() : java.util.List.copyOf(p.rentalSpells);
        rentalPool = p.rentalPool == null ? "" : p.rentalPool;
        bossActiveSeconds = p.bossActiveSeconds;
        statsStart = p.statsStart;
        statsStartOnBoss = p.statsStart != null; // don't retake it after a restart
        statsBestMinute = p.statsBestMinute;
        bestMinuteDealt = p.bestMinuteDealt;
        skills = p.skills;
        if (p.topHits != null) {
            topHits.addAll(p.topHits);
        }
        if (p.damageBySource != null) {
            p.damageBySource.forEach((label, amount) -> bySource.put(label, new float[]{amount}));
        }
        if (p.healBySource != null) {
            p.healBySource.forEach((label, amount) -> healBySource.put(label, new float[]{amount}));
        }
        if (p.topHeals != null) {
            topHeals.addAll(p.topHeals);
        }
        if (p.healOthersBySource != null) {
            p.healOthersBySource.forEach((label, amount) -> healOthersBySource.put(label, new float[]{amount}));
        }
    }

    static void addHeal(java.util.Map<String, float[]> map, String label, float amount) {
        if (amount != 0) {
            map.computeIfAbsent(label, k -> new float[1])[0] += amount;
        }
    }

    private static java.util.Map<String, Float> sorted(java.util.Map<String, float[]> map) {
        java.util.Map<String, Float> out = new java.util.LinkedHashMap<>();
        map.entrySet().stream()
            .filter(e -> e.getValue()[0] > 0.05f)
            .sorted((a, b) -> Float.compare(b.getValue()[0], a.getValue()[0]))
            .forEach(e -> out.put(e.getKey(), Math.round(e.getValue()[0] * 10) / 10f));
        return out;
    }
}
