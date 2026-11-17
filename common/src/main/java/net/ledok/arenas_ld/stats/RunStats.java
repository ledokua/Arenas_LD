package net.ledok.arenas_ld.stats;

import com.google.gson.Gson;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.dungeon.run.RunParticipant;
import net.ledok.arenas_ld.packet.RunHudPayload;
import net.ledok.arenas_ld.util.DownedSpectators;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One dungeon or raid run's combat stats (the RunStats design). The run marks its own entities
 * ({@link RunStatsHolder}); the hooks on {@code LivingEntity} only count entities that carry the mark, so everything
 * outside runs costs one null check per hit. Totals only: per player {@link PlayerCounters}, plus the HP lost by run
 * mobs that nothing traces to a player ({@link #unattributed}) and when bosses crossed 75/50/25% HP.
 *
 * <p>Credit, as the design says: a player's hit, projectile, pet or summon goes to that player; damage with no
 * attacker (poison, wither, bleed) to whoever last put a harmful effect on the mob; anything else (fire, falls,
 * lava) to the last player who hit it ({@code getKillCredit}). Spell Engine heals go to the caster.
 *
 * <p>The totals go into the run's save ({@link #save}) and come back after a restart; the run's mobs carry a tag
 * with the run's key, so they get their mark back when they load ({@link #onLoaded}).
 */
public final class RunStats {
    public static final String MOB_TAG_PREFIX = "arenas_ld_stats:";
    public static final String BOSS_TAG = "arenas_ld_stats_boss";
    private static final Gson GSON = new Gson();
    private static final int[] PHASE_MARKS = {75, 50, 25};
    /** Run mobs read from disk, waiting for their run's tick to mark them again (or to be dropped after 5 min). */
    private static final List<Pending> PENDING = new ArrayList<>();

    /** The run whose mobs are being spawned now (the run's tick sets it), for {@code markRunMob}. */
    private static @Nullable RunStats current;
    /** Set while hunger regenerates a player (not counted as healing, LeDok). */
    static boolean naturalRegen;
    /** What is healing right now when the caller says so (an effect tick: "effect:<id>"); null = find it from the stack. */
    static String healContext;
    static String lastHealLabel;
    /** The top-heal entry the last heal made (null: it made none), relabeled or moved by the Spell Engine heal event. */
    static @Nullable RunRecord.Heal lastTopHeal;
    /** How many of a player's biggest heals are kept. */
    public static final int TOP_HEALS = 5;
    /** The last counted heal, for the Spell Engine heal event that follows it (it names the caster). */
    static @Nullable PlayerCounters lastHealed;
    static float lastHealAmount;

    public final DownedSpectators.RunView view;
    private final Supplier<RunDetailsSource> source;
    private final Map<UUID, PlayerCounters> players = new LinkedHashMap<>();
    final List<RunRecord.Phase> phases = new ArrayList<>();
    /** The run's bosses by UUID, in spawn order. */
    final Map<UUID, RunRecord.Boss> bosses = new LinkedHashMap<>();
    /** {@link RunDetailsSource#startTick}, cached for the hot path ({@link #source()} builds a new record). */
    private long startTick = Long.MIN_VALUE;
    public float unattributed;
    /** The last run HUD sent (phase line, timer, boss HP), for the details screen. */
    public @Nullable RunHudPayload lastHud;
    private @Nullable String key;
    private boolean ended;
    /**
     * The controller's rental mode when the run started ({@code RunSettings.OPTIONAL}/{@code REQUIRED}); null until
     * the run's first tick reads it. Saved with the run, so changing the controller mid-run doesn't change the run.
     */
    private @Nullable String rentalMode;
    /** The server tick of the run's last tick; a run that stops ticking (taken away by its controller) is ended. */
    long lastTick = -1;

    /** Who hit a run mob how much (for assists); made on its first credited hit, dropped when it dies. */
    public static final class Hits {
        private PlayerCounters[] who = new PlayerCounters[4];
        private float[] amount = new float[4];
        private int size;

        void add(PlayerCounters counters, float value) {
            for (int i = 0; i < size; i++) {
                if (who[i] == counters) {
                    amount[i] += value;
                    return;
                }
            }
            if (size == who.length) {
                who = Arrays.copyOf(who, size * 2);
                amount = Arrays.copyOf(amount, size * 2);
            }
            who[size] = counters;
            amount[size++] = value;
        }
    }

    private record Pending(LivingEntity entity, String key, long sinceMs) {
    }

    public RunStats(DownedSpectators.RunView view, Supplier<RunDetailsSource> source) {
        this.view = view;
        this.source = source;
        RunDetails.track(this);
    }

    public static void naturalRegen(boolean on) {
        naturalRegen = on;
    }

    public static boolean enabled() {
        return ArenasLdConfig.getInstance().run_stats.enabled;
    }

    public RunDetailsSource source() {
        return source.get();
    }

    public Map<UUID, PlayerCounters> players() {
        return players;
    }

    public boolean ended() {
        return ended;
    }

    public PlayerCounters counters(UUID player, String name) {
        return players.computeIfAbsent(player, uuid -> new PlayerCounters(this, uuid, name));
    }

    /** The run's key in its mobs' tags: its instance and start tick (the same after a restart). */
    String key() {
        if (key == null) {
            RunDetailsSource s = source();
            key = s.instance().dimension().location() + "@" + s.instance().pos().toShortString().replace(" ", "")
                + "@" + s.startTick();
        }
        return key;
    }

    // ── run side ─────────────────────────────────────────────────────────────

    /** Runs {@code body} with this run as the one new run mobs belong to (null: none). */
    public static void during(@Nullable RunStats stats, Runnable body) {
        RunStats previous = current;
        current = stats != null && enabled() && !stats.ended ? stats : null;
        try {
            body.run();
        } finally {
            current = previous;
        }
    }

    /** {@code markRunMob}: a mob the running run spawned is counted for it. */
    public static void onRunMob(LivingEntity mob) {
        if (current != null && !(mob instanceof Player)) {
            ((RunStatsHolder) mob).arenasLd$setRunStats(current);
            mob.addTag(MOB_TAG_PREFIX + current.key());
        }
    }

    /** One of the run's bosses (a dungeon boss, a room's boss wave, the raid boss). */
    public static void markBoss(LivingEntity mob) {
        ((RunStatsHolder) mob).arenasLd$setBoss(true);
        mob.addTag(BOSS_TAG);
        if (((RunStatsHolder) mob).arenasLd$runStats() instanceof RunStats stats) {
            stats.boss(mob); // its max HP as it spawns, scaling included
        }
    }

    /** The run's record of a boss (made on first sight); its max HP is the highest seen. */
    private RunRecord.Boss boss(LivingEntity mob) {
        RunRecord.Boss boss = bosses.get(mob.getUUID());
        if (boss == null) {
            boss = new RunRecord.Boss();
            boss.uuid = mob.getUUID().toString();
            boss.id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString();
            boss.name = mob.getName().getString();
            boss.healthLeft = mob.getHealth();
            bosses.put(mob.getUUID(), boss);
        }
        boss.maxHealth = Math.max(boss.maxHealth, mob.getMaxHealth());
        return boss;
    }

    /** The run's rental mode (read from its controller the first time; kept for the rest of the run). */
    public String rentalMode(MinecraftServer server) {
        if (rentalMode == null) {
            var id = source().activityId();
            rentalMode = net.ledok.arenas_ld.util.RunSettings.of(id == null ? null
                : net.ledok.arenas_ld.activity.ActivityRegistry.resolve(server, id)).rentalMode();
        }
        return rentalMode;
    }

    /**
     * Whether everyone in this run had to rent a build (full rewards, no rental cut). Read at the run's first tick
     * ({@link #rentalMode}); false before it.
     */
    public boolean rentalRequired() {
        return net.ledok.arenas_ld.util.RunSettings.REQUIRED.equals(rentalMode);
    }

    /** The run's stable id for a session elsewhere (rental builds): instance and start tick, the same after a restart. */
    public String sessionKey() {
        return key();
    }

    long startTick() {
        if (startTick == Long.MIN_VALUE) {
            startTick = source().startTick();
        }
        return startTick;
    }

    /**
     * A credited hit's fight data: seconds on the boss, the stats when the fight began, and the best damage minute.
     * Captures stats only at a player's first (boss) hit and at each new minute's first hit.
     */
    private void fightData(PlayerCounters credit, LivingEntity victim, boolean onBoss, float lost) {
        long now = victim.level().getGameTime();
        if (onBoss) {
            long second = now / 20;
            if (second != credit.lastBossSecond) {
                credit.lastBossSecond = second;
                credit.bossActiveSeconds++;
            }
        }
        long minute = (now - startTick()) / 1200;
        boolean newMinute = minute != credit.minute;
        boolean needStart = credit.statsStart == null || (onBoss && !credit.statsStartOnBoss);
        if (newMinute || needStart) {
            MinecraftServer server = victim.getServer();
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(credit.player);
            int seconds = (int) Math.max(0, (now - startTick()) / 20);
            if (newMinute) {
                if (credit.minuteStats != null && credit.minuteDealt > credit.bestMinuteDealt) {
                    credit.bestMinuteDealt = credit.minuteDealt;
                    credit.statsBestMinute = credit.minuteStats;
                }
                credit.minute = minute;
                credit.minuteDealt = 0;
                credit.minuteStats = player == null ? null : RunGear.stats(player, seconds);
            }
            if (needStart && player != null) {
                credit.statsStart = newMinute && credit.minuteStats != null ? credit.minuteStats : RunGear.stats(player, seconds);
                credit.statsStartOnBoss = onBoss;
            }
        }
        credit.minuteDealt += lost;
    }

    /** A minion counts for the run of the mob that summoned it. */
    public static void copyMob(LivingEntity summoner, LivingEntity minion) {
        if (((RunStatsHolder) summoner).arenasLd$runStats() instanceof RunStats stats) {
            ((RunStatsHolder) minion).arenasLd$setRunStats(stats);
            minion.addTag(MOB_TAG_PREFIX + stats.key());
        }
    }

    /** A summon or pet a run player owns ({@code RunLeftovers} tagged it): its hits are the owner's. */
    public static void onOwnedEntity(Entity entity, @Nullable ServerPlayer owner) {
        if (entity instanceof LivingEntity living && !(entity instanceof Player) && owner != null
            && ((RunStatsHolder) owner).arenasLd$runStats() instanceof PlayerCounters counters) {
            ((RunStatsHolder) living).arenasLd$setRunStats(counters);
        }
    }

    /** {@code Entity.load}: a run mob read from disk waits for its run to mark it again. */
    public static void onLoaded(Entity entity) {
        if (entity instanceof LivingEntity living && !(entity instanceof Player) && !entity.level().isClientSide()) {
            for (String tag : entity.getTags()) {
                if (tag.startsWith(MOB_TAG_PREFIX)) {
                    PENDING.add(new Pending(living, tag.substring(MOB_TAG_PREFIX.length()), System.currentTimeMillis()));
                    return;
                }
            }
        }
    }

    /** {@code /exit} (or the exit button) before the run is over: the player forfeited it. */
    public static void markForfeit(UUID player) {
        RunStats stats = RunDetails.runOf(player);
        if (stats != null) {
            stats.players.computeIfAbsent(player, uuid -> new PlayerCounters(stats, uuid, "")).forfeited = true;
        }
    }

    /** Loot the run paid a player (run file, staff). */
    public static void onLoot(UUID player, List<ItemStack> stacks) {
        RunStats stats = RunDetails.runOfAny(player);
        if (stats == null || stacks.isEmpty()) {
            return;
        }
        PlayerCounters counters = stats.players.get(player);
        if (counters == null) {
            return;
        }
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                counters.loot.add(stack.getCount() + "x " + BuiltInRegistries.ITEM.getKey(stack.getItem()));
            }
        }
    }

    /**
     * A player's reward breakdown on a win: kept for the run details' Rewards tab (UI rework D5) instead of chat.
     * False when the player's run has no stats (stats off): then the caller says it in chat as before.
     */
    public static boolean recordRewards(UUID player, net.minecraft.network.chat.Component summary,
                                        List<net.minecraft.network.chat.Component> lines) {
        RunStats stats = RunDetails.runOfAny(player);
        if (stats == null || stats.ended) {
            return false;
        }
        RunParticipant participant = stats.view.participants().get().get(player);
        PlayerCounters counters = stats.counters(player, participant != null ? participant.playerName() : "?");
        counters.rewards.clear();
        counters.rewards.add(summary); // the first line is what they got; the rest is how it was counted
        counters.rewards.addAll(lines);
        return true;
    }

    /**
     * The run's tick (once a second): its online participants carry its counters (removed ones are let go), the
     * living ones gain time alive, its reloaded mobs get their mark back, and the run is finished when it is over.
     */
    public void tick(MinecraftServer server) {
        lastTick = server.getTickCount();
        rentalMode(server);
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        if (view.over().getAsBoolean() || !enabled()) {
            end(server);
            return;
        }
        for (Map.Entry<UUID, RunParticipant> entry : view.participants().get().entrySet()) {
            RunParticipant participant = entry.getValue();
            PlayerCounters counters = counters(entry.getKey(), participant.playerName());
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                continue;
            }
            RunStatsHolder holder = (RunStatsHolder) player;
            if (participant.status() == RunParticipant.ParticipantStatus.REMOVED) {
                if (holder.arenasLd$runStats() == counters) {
                    holder.arenasLd$setRunStats(null);
                }
                continue;
            }
            if (holder.arenasLd$runStats() != counters) {
                holder.arenasLd$setRunStats(counters);
            }
            if (counters.gear == null) {
                counters.gear = RunGear.capture(player); // once: their level, gear and skills as they start
                counters.skills = RunGear.skills(player);
            }
            net.ledok.arenas_ld.util.RunRentals.onRunPlayer(this, counters, player);
            if (!view.downed().test(entry.getKey())) {
                counters.aliveTicks += 20;
                FightStats.moment(counters, player, (int) Math.max(0, (server.overworld().getGameTime() - startTick()) / 20));
            }
        }
        claimPending();
    }

    private void claimPending() {
        if (PENDING.isEmpty()) {
            return;
        }
        String own = key();
        long now = System.currentTimeMillis();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pending = it.next();
            if (pending.entity().isRemoved() || now - pending.sinceMs() > 300_000) {
                it.remove();
            } else if (pending.key().equals(own)) {
                RunStatsHolder holder = (RunStatsHolder) pending.entity();
                holder.arenasLd$setRunStats(this);
                holder.arenasLd$setBoss(pending.entity().getTags().contains(BOSS_TAG));
                it.remove();
            }
        }
    }

    /** The run is over: its players stop counting (its mobs on their next hit), and its run file is written. */
    public void end(MinecraftServer server) {
        if (ended) {
            return;
        }
        RunRecord record = snapshot(server);
        ended = true;
        RunDetails.untrack(this);
        for (UUID uuid : players.keySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null && ((RunStatsHolder) player).arenasLd$runStats() instanceof PlayerCounters counters
                && counters.run == this) {
                ((RunStatsHolder) player).arenasLd$setRunStats(null);
            }
        }
        if (enabled()) {
            RunRecords.finish(server, this, record);
        }
    }

    // ── saving ───────────────────────────────────────────────────────────────

    /** The run as a record now (the details screen, the run's save, the run file). */
    public RunRecord snapshot(MinecraftServer server) {
        RunDetailsSource s = source();
        RunRecord record = new RunRecord();
        record.activityId = s.activityId() == null ? "" : s.activityId().toString();
        record.activityName = s.activityName().getString();
        record.type = s.type();
        record.difficulty = s.tier().name().toLowerCase(java.util.Locale.ROOT);
        record.runLevel = s.runLevel();
        record.hardcore = s.hardcore();
        record.modifiers = new ArrayList<>(s.modifiers());
        record.rentalMode = rentalMode(server);
        record.baseLootRolls = s.lootRolls();
        record.dimension = s.instance().dimension().location().toString();
        record.x = s.instance().pos().getX();
        record.y = s.instance().pos().getY();
        record.z = s.instance().pos().getZ();
        long now = server.overworld().getGameTime();
        record.durationSeconds = (int) Math.max(0, (now - s.startTick()) / 20);
        record.startEpochMs = System.currentTimeMillis() - record.durationSeconds * 1000L;
        record.result = s.result();
        record.unattributed = unattributed;
        record.timeLimitSeconds = s.timeLimitSeconds();
        bossesInto(server, record);
        record.phases = new ArrayList<>(phases);
        for (Map.Entry<UUID, RunParticipant> entry : view.participants().get().entrySet()) {
            RunParticipant participant = entry.getValue();
            PlayerCounters counters = counters(entry.getKey(), participant.playerName());
            record.players.add(counters.toRecord(participant.deaths(), RunDetails.state(server, this, entry.getKey(), participant)));
        }
        return record;
    }

    /** The bosses as they are now (a loaded, living boss updates its HP left; a regenerating one too), and the sums. */
    private void bossesInto(MinecraftServer server, RunRecord record) {
        for (Map.Entry<UUID, RunRecord.Boss> entry : bosses.entrySet()) {
            RunRecord.Boss boss = entry.getValue();
            if (!boss.killed) {
                for (net.minecraft.server.level.ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(entry.getKey()) instanceof LivingEntity living && living.isAlive()) {
                        boss.maxHealth = Math.max(boss.maxHealth, living.getMaxHealth());
                        boss.healthLeft = living.getHealth();
                        break;
                    }
                }
            }
            record.bosses.add(boss);
            record.bossMaxHealth += boss.maxHealth;
            record.bossHealthLeft += boss.healthLeft;
        }
    }

    /** For the run's own save (written when its chunk saves, not per hit): the totals as JSON. */
    public String saveTotals() {
        RunRecord record = new RunRecord();
        record.unattributed = unattributed;
        record.phases = new ArrayList<>(phases);
        record.bosses = new ArrayList<>(bosses.values());
        record.rentalMode = rentalMode == null ? "" : rentalMode;
        for (PlayerCounters counters : players.values()) {
            record.players.add(counters.toRecord(0, ""));
        }
        return GSON.toJson(record);
    }

    /** Back from the run's save after a restart. */
    public void load(String json) {
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            RunRecord record = GSON.fromJson(json, RunRecord.class);
            unattributed = record.unattributed;
            phases.addAll(record.phases);
            if (record.rentalMode != null && !record.rentalMode.isEmpty()) {
                rentalMode = record.rentalMode;
            }
            if (record.bosses != null) {
                for (RunRecord.Boss boss : record.bosses) {
                    try {
                        bosses.put(UUID.fromString(boss.uuid), boss);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
            for (RunRecord.Player p : record.players) {
                counters(UUID.fromString(p.uuid), p.name).load(p);
            }
        } catch (RuntimeException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] Run stats: couldn't read a run's saved totals; they start over", e);
        }
    }

    // ── hooks (LivingEntity mixin) ───────────────────────────────────────────

    /**
     * {@code actuallyHurt} ran on a marked entity: {@code raw} reached its armor, armor stopped {@code armor},
     * Resistance {@code resistance}, enchantments {@code enchant}, absorption {@code absorbed}; {@code toHealth}
     * was left for its health and it lost {@code lost} (the rest is overkill).
     */
    public static void onHurt(LivingEntity victim, Object mark, DamageSource source, float raw, float lost, float toHealth,
                              float armor, float resistance, float enchant, float absorbed) {
        if (mark instanceof RunStats stats) {
            if (stats.ended) {
                ((RunStatsHolder) victim).arenasLd$setRunStats(null);
                return;
            }
            if (lost <= 0) {
                return;
            }
            RunStatsHolder holder = (RunStatsHolder) victim;
            PlayerCounters credit = credit(source, victim, stats);
            if (credit != null) {
                credit.dealt += lost;
                credit.overkill += Math.max(0, toHealth - lost);
                if (holder.arenasLd$boss()) {
                    credit.bossDealt += lost;
                }
                Hits hits = holder.arenasLd$hits();
                if (hits == null) {
                    hits = new Hits();
                    holder.arenasLd$setHits(hits);
                }
                hits.add(credit, lost);
                Object key = sourceKey(source);
                float[] total = credit.bySource.get(key);
                if (total == null) {
                    credit.bySource.put(key, new float[]{lost});
                } else {
                    total[0] += lost;
                }
                if (credit.topHits.size() < TOP_HITS || toHealth > credit.topHits.get(credit.topHits.size() - 1).amount) {
                    stats.topHit(credit, victim, holder.arenasLd$boss(), source, key, toHealth);
                }
                stats.fightData(credit, victim, holder.arenasLd$boss(), lost);
                if (holder.arenasLd$boss() && victim.getServer() != null
                    && victim.getServer().getPlayerList().getPlayer(credit.player) instanceof ServerPlayer attacker) {
                    FightStats.bossHit(credit, attacker, source, lost);
                }
            } else {
                stats.unattributed += lost;
            }
            if (holder.arenasLd$boss()) {
                stats.bossPhase(victim, victim.getHealth() + lost, victim.getHealth());
                stats.boss(victim).healthLeft = victim.getHealth();
            }
        } else if (mark instanceof PlayerCounters counters && victim instanceof Player) {
            if (counters.run.ended) {
                ((RunStatsHolder) victim).arenasLd$setRunStats(null);
                return;
            }
            counters.taken += lost;
            if (source.getEntity() != null) {
                counters.takenEnemies += lost;
            } else {
                counters.takenEnvironment += lost;
            }
            counters.takenRaw += raw;
            counters.blockedArmor += armor;
            counters.blockedResistance += resistance;
            counters.blockedEnchant += enchant;
            counters.blockedAbsorption += absorbed;
            RunEvents.landed(victim, counters, source, raw, armor, resistance, enchant, absorbed, lost, toHealth);
        }
    }

    /** How many of a player's biggest hits are kept. */
    public static final int TOP_HITS = 3;

    /** A hit that makes a player's top 3 (rare: the only place a hit allocates). */
    private void topHit(PlayerCounters credit, LivingEntity victim, boolean boss, DamageSource source, Object key, float amount) {
        RunRecord.Hit hit = new RunRecord.Hit();
        hit.amount = Math.round(amount * 10) / 10f;
        hit.target = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()).toString();
        hit.boss = boss;
        hit.seconds = (int) Math.max(0, (victim.level().getGameTime() - source().startTick()) / 20);
        hit.source = sourceLabel(key);
        hit.damageType = source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
        int at = 0;
        while (at < credit.topHits.size() && credit.topHits.get(at).amount >= hit.amount) {
            at++;
        }
        credit.topHits.add(at, hit);
        while (credit.topHits.size() > TOP_HITS) {
            credit.topHits.remove(credit.topHits.size() - 1);
        }
    }

    /**
     * What dealt a hit, without allocating: the running Spell Engine spell (its holder), else the weapon of the
     * direct attacker (the player's main hand, an arrow's bow), else the direct attacker's entity type (a pet, a
     * projectile with no weapon), else the damage type (poison, fire and other hits with no attacker).
     */
    static Object sourceKey(DamageSource source) {
        Object spell = SpellContext.current();
        if (spell != null) {
            return spell;
        }
        Entity direct = source.getDirectEntity();
        if (direct != null) {
            ItemStack weapon = direct instanceof Player || direct instanceof net.minecraft.world.entity.projectile.Projectile
                ? direct.getWeaponItem() : null;
            if (weapon != null && (!weapon.isEmpty() || direct instanceof Player)) {
                return weapon.getItem();
            }
            return direct.getType();
        }
        return source.typeHolder();
    }

    /** A {@link #sourceKey} as saved: "spell:wizards:fireball", "item:minecraft:diamond_sword", "entity:…", "damage:…". */
    static String sourceLabel(Object key) {
        if (key instanceof String label) {
            return label; // from a save
        }
        if (key instanceof net.minecraft.world.item.Item item) {
            return "item:" + BuiltInRegistries.ITEM.getKey(item);
        }
        if (key instanceof net.minecraft.world.entity.EntityType<?> type) {
            return "entity:" + BuiltInRegistries.ENTITY_TYPE.getKey(type);
        }
        if (key instanceof net.minecraft.core.Holder<?> holder) {
            var id = holder.unwrapKey().orElse(null);
            if (id == null) {
                return "unknown";
            }
            return (id.registry().equals(net.minecraft.core.registries.Registries.DAMAGE_TYPE.location()) ? "damage:" : "spell:")
                + id.location();
        }
        return "unknown";
    }

    /** A run player's shield took a hit. */
    public static void onShieldBlocked(PlayerCounters counters, float amount) {
        if (!counters.run.ended) {
            counters.blockedShield += amount;
            RunEvents.shield(counters);
        }
    }

    /** A run mob died: the killing blow's player gets the kill, others with 10%+ of its HP an assist. */
    public static void onMobDied(LivingEntity mob, RunStats stats, DamageSource source) {
        RunStatsHolder holder = (RunStatsHolder) mob;
        if (stats.ended) {
            holder.arenasLd$setRunStats(null);
            return;
        }
        PlayerCounters killer = credit(source, mob, stats);
        if (killer != null) {
            killer.kills++;
            if (holder.arenasLd$boss()) {
                killer.bossKills++;
            }
        }
        Hits hits = holder.arenasLd$hits();
        if (hits != null) {
            float threshold = mob.getMaxHealth() * 0.1f;
            for (int i = 0; i < hits.size; i++) {
                if (hits.who[i] != killer && hits.amount[i] >= threshold) {
                    hits.who[i].assists++;
                }
            }
        }
        if (holder.arenasLd$boss()) {
            stats.phaseMark(mob, 0);
            RunRecord.Boss boss = stats.boss(mob);
            boss.killed = true;
            boss.healthLeft = 0;
        }
        holder.arenasLd$setHits(null);
        holder.arenasLd$setApplier(null);
    }

    /** {@code heal} on a marked player asked for {@code amount} and restored {@code gained}. */
    public static void onHealed(LivingEntity target, Object mark, float amount, float gained) {
        if (naturalRegen || !(mark instanceof PlayerCounters counters) || !(target instanceof Player) || counters.run.ended) {
            return;
        }
        counters.healedSelf += gained;
        counters.overheal += Math.max(0, amount - gained);
        lastHealed = counters;
        lastHealAmount = gained;
        lastHealLabel = healContext != null ? healContext : callerLabel();
        PlayerCounters.addHeal(counters.healBySource, lastHealLabel, gained);
        lastTopHeal = null;
        if (gained > 0 && (counters.topHeals.size() < TOP_HEALS
            || gained > counters.topHeals.get(counters.topHeals.size() - 1).amount)) {
            RunRecord.Heal heal = new RunRecord.Heal();
            heal.amount = Math.round(gained * 10) / 10f;
            heal.source = lastHealLabel;
            heal.seconds = (int) Math.max(0, (target.level().getGameTime() - counters.run.startTick()) / 20);
            insertTopHeal(counters, heal);
            lastTopHeal = heal;
        }
    }

    private static void insertTopHeal(PlayerCounters counters, RunRecord.Heal heal) {
        int at = 0;
        while (at < counters.topHeals.size() && counters.topHeals.get(at).amount >= heal.amount) {
            at++;
        }
        counters.topHeals.add(at, heal);
        while (counters.topHeals.size() > TOP_HEALS) {
            counters.topHeals.remove(counters.topHeals.size() - 1);
        }
    }

    public static void healContext(@Nullable String label) {
        healContext = label;
    }

    /**
     * An effect starts healing {@code entity}: for a run player the heal context becomes "effect:<id>". Returns the
     * context to put back ({@link #exitHealContext}); anything else costs one field check.
     */
    public static @Nullable String enterHealContext(LivingEntity entity, net.minecraft.core.Holder<?> effect) {
        String previous = healContext;
        if (entity instanceof Player && ((RunStatsHolder) entity).arenasLd$runStats() instanceof PlayerCounters) {
            healContext = effect.unwrapKey().map(k -> "effect:" + k.location()).orElse("effect:unknown");
        }
        return previous;
    }

    public static void exitHealContext(@Nullable String previous) {
        healContext = previous;
    }

    private static final java.util.Set<String> SKIP_CALLERS = java.util.Set.of("net.minecraft.", "java.", "jdk.", "sun.",
        "net.ledok.arenas_ld.", "org.spongepowered.", "com.llamalad7.", "net.fabricmc.", "com.mojang.");

    /** "other:<SimpleClass>" of the first mod class (not vanilla, not Arenas, not a mixin library) that called heal. */
    static String callerLabel() {
        String found = StackWalker.getInstance().walk(frames -> frames.limit(24)
            .map(StackWalker.StackFrame::getClassName)
            .filter(c -> SKIP_CALLERS.stream().noneMatch(c::startsWith))
            .findFirst().orElse(null));
        if (found == null) {
            return "other:minecraft";
        }
        String simple = found.substring(found.lastIndexOf('.') + 1);
        int lambda = simple.indexOf("$$");
        return "other:" + (lambda > 0 ? simple.substring(0, lambda) : simple);
    }

    /** Spell Engine's heal event, right after the heal: the caster gets the credit for it. */
    static void onSpellHeal(@Nullable LivingEntity caster, LivingEntity target, @Nullable String spellLabel) {
        PlayerCounters healed = lastHealed;
        RunRecord.Heal top = lastTopHeal;
        lastHealed = null;
        lastTopHeal = null;
        if (healed == null || ((RunStatsHolder) target).arenasLd$runStats() != healed) {
            return;
        }
        float amount = lastHealAmount;
        String label = spellLabel != null ? spellLabel : lastHealLabel;
        if (!label.equals(lastHealLabel)) {
            // the heal was first put under its caller; it is this spell
            PlayerCounters.addHeal(healed.healBySource, lastHealLabel, -amount);
            PlayerCounters.addHeal(healed.healBySource, label, amount);
            if (top != null) {
                top.source = label;
            }
            lastHealLabel = label;
        }
        if (caster == null || caster == target
            || !(((RunStatsHolder) caster).arenasLd$runStats() instanceof PlayerCounters healer)
            || healer.run != healed.run) {
            return;
        }
        // Healing another player is the caster's: in their healBySource (self and others) and healOthersBySource.
        healed.healedSelf -= amount;
        healer.healedOthers += amount;
        PlayerCounters.addHeal(healed.healBySource, label, -amount);
        PlayerCounters.addHeal(healer.healBySource, label, amount);
        PlayerCounters.addHeal(healer.healOthersBySource, label, amount);
        if (top != null && healed.topHeals.remove(top)) {
            top.target = healed.name;
            if (healer.topHeals.size() < TOP_HEALS || top.amount > healer.topHeals.get(healer.topHeals.size() - 1).amount) {
                insertTopHeal(healer, top);
            }
        }
    }

    /** {@code addEffect} on a marked mob: a harmful effect from a run player makes its damage over time theirs. */
    public static void onEffectAdded(LivingEntity mob, Object mark, MobEffectInstance effect, @Nullable Entity source) {
        if (mark instanceof PlayerCounters counters && mob instanceof net.minecraft.world.entity.player.Player
            && !counters.run.ended) {
            RunEvents.effectAdded(mob, counters, effect, source);
            return;
        }
        if (mark instanceof RunStats stats && source != null
            && effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
            PlayerCounters applier = countersOf(source, stats);
            if (applier != null) {
                ((RunStatsHolder) mob).arenasLd$setApplier(applier);
            }
        }
    }

    /** A boss went from {@code before} to {@code after} HP: note each 75/50/25% mark it crossed. */
    private void bossPhase(LivingEntity boss, float before, float after) {
        float max = boss.getMaxHealth();
        if (max <= 0) {
            return;
        }
        for (int mark : PHASE_MARKS) {
            float line = max * mark / 100f;
            if (before > line && after <= line) {
                phaseMark(boss, mark);
            }
        }
    }

    private void phaseMark(LivingEntity boss, int percent) {
        RunRecord.Phase phase = new RunRecord.Phase();
        phase.boss = boss.getName().getString();
        phase.percent = percent;
        phase.seconds = (int) Math.max(0, (boss.level().getGameTime() - source().startTick()) / 20);
        phases.add(phase);
    }

    private static @Nullable PlayerCounters credit(DamageSource source, LivingEntity victim, RunStats stats) {
        Entity attacker = source.getEntity(); // a projectile's or spell cloud's owner already
        if (attacker != null) {
            return countersOf(attacker, stats);
        }
        PlayerCounters applier = ((RunStatsHolder) victim).arenasLd$applier();
        if (applier != null && applier.run == stats) {
            return applier;
        }
        return countersOf(victim.getKillCredit(), stats);
    }

    /** The run counters behind an attacker: a run player, or a pet/summon of one. */
    private static @Nullable PlayerCounters countersOf(@Nullable Entity entity, RunStats stats) {
        if (entity instanceof RunStatsHolder holder && holder.arenasLd$runStats() instanceof PlayerCounters counters) {
            return counters.run == stats ? counters : null;
        }
        if (entity instanceof OwnableEntity pet && pet.getOwner() instanceof RunStatsHolder owner
            && owner.arenasLd$runStats() instanceof PlayerCounters counters) {
            return counters.run == stats ? counters : null;
        }
        return null;
    }
}
