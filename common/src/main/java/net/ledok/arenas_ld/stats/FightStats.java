package net.ledok.arenas_ld.stats;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Stats over the whole fight (LeDok, TODO 10), not only the start snapshot:
 * <ul>
 *   <li><b>Per weapon pair</b> (main hand + offhand item), at every boss hit: hits, damage, and min / mean / max of
 *       attack speed, attack damage, crit chance and damage (melee and spell), and the spell power of the hit's school;
 *       the effects active while hitting (highest amplifier). No strings on the way, a few attribute reads per hit.</li>
 *   <li><b>A timeline</b> ({@link #moment}): every 30 s, and on a weapon change or a move of more than 10% in attack
 *       speed or damage (at most every 5 s), a stats snapshot with the held items.</li>
 * </ul>
 */
final class FightStats {
    static final String[] STAT_NAMES = {"attack_speed", "attack_damage", "crit_chance", "crit_damage",
        "spell_crit_chance", "spell_crit_damage", "school_power"};
    static final int SCHOOL = 6;
    private static final String[] STAT_IDS = {"minecraft:generic.attack_speed", "minecraft:generic.attack_damage",
        "critical_strike:chance", "critical_strike:damage", "spell_power:critical_chance", "spell_power:critical_damage"};
    static final int TIMELINE_TICKS = 600;
    static final int CHANGE_MIN_TICKS = 100;
    /** Timeline entries kept per player (2 h of 30 s entries); past it the oldest after the first go. */
    static final int MAX_TIMELINE = 240;
    static final double CHANGE_FRACTION = 0.10;

    @SuppressWarnings("unchecked")
    private static Holder<Attribute>[] attributes;
    /** Damage type → the attribute of the same id (a spell school's power), or empty. */
    private static final Map<Holder<DamageType>, Optional<Holder<Attribute>>> SCHOOL_POWER = new IdentityHashMap<>();

    /** One weapon pair's boss hits. */
    static final class WeaponAgg {
        int hits;
        float damage;
        final double[] min = new double[STAT_NAMES.length];
        final double[] sum = new double[STAT_NAMES.length];
        final double[] max = new double[STAT_NAMES.length];
        final int[] count = new int[STAT_NAMES.length];
        final Map<Holder<MobEffect>, Integer> effects = new HashMap<>();

        void add(int stat, double value) {
            if (count[stat] == 0) {
                min[stat] = value;
                max[stat] = value;
            } else {
                min[stat] = Math.min(min[stat], value);
                max[stat] = Math.max(max[stat], value);
            }
            sum[stat] += value;
            count[stat]++;
        }
    }

    private FightStats() {
    }

    @SuppressWarnings("unchecked")
    private static Holder<Attribute>[] attributes() {
        if (attributes == null) {
            Holder<Attribute>[] found = new Holder[STAT_IDS.length];
            for (int i = 0; i < STAT_IDS.length; i++) {
                found[i] = BuiltInRegistries.ATTRIBUTE.getHolder(ResourceLocation.parse(STAT_IDS[i])).orElse(null);
            }
            attributes = found;
        }
        return attributes;
    }

    /** A boss hit by {@code player} (directly or through their pet, projectile or spell). */
    static void bossHit(PlayerCounters counters, ServerPlayer player, DamageSource source, float lost) {
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        WeaponAgg agg = counters.weapons.computeIfAbsent(main, k -> new HashMap<>()).computeIfAbsent(off, k -> new WeaponAgg());
        agg.hits++;
        agg.damage += lost;
        Holder<Attribute>[] holders = attributes();
        for (int i = 0; i < holders.length; i++) {
            if (holders[i] != null) {
                AttributeInstance instance = player.getAttribute(holders[i]);
                if (instance != null) {
                    agg.add(i, instance.getValue());
                }
            }
        }
        Optional<Holder<Attribute>> school = SCHOOL_POWER.computeIfAbsent(source.typeHolder(), type -> type.unwrapKey()
            .flatMap(key -> BuiltInRegistries.ATTRIBUTE.getHolder(key.location())).map(h -> (Holder<Attribute>) h));
        if (school.isPresent()) {
            AttributeInstance instance = player.getAttribute(school.get());
            if (instance != null) {
                agg.add(SCHOOL, instance.getValue());
            }
        }
        for (MobEffectInstance effect : player.getActiveEffects()) {
            agg.effects.merge(effect.getEffect(), effect.getAmplifier(), Math::max);
        }
    }

    /** The run's once-a-second tick for a living player: a timeline entry when 30 s passed or their build changed. */
    static void moment(PlayerCounters counters, ServerPlayer player, int seconds) {
        long now = player.server.getTickCount();
        // Every 30 s only (4.37.0): swaps, effects and the rest are in the run event log now.
        if (counters.lastMomentTick != Long.MIN_VALUE && now - counters.lastMomentTick < TIMELINE_TICKS) {
            return;
        }
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        double speed = player.getAttributeValue(Attributes.ATTACK_SPEED);
        double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        String reason = null;
        if (counters.lastMomentTick == Long.MIN_VALUE || now - counters.lastMomentTick >= TIMELINE_TICKS) {
            reason = "30s";
        }
        if (reason == null) {
            return;
        }
        RunRecord.Stats stats = RunGear.stats(player, seconds);
        stats.reason = reason;
        stats.mainHand = BuiltInRegistries.ITEM.getKey(main).toString();
        stats.offHand = BuiltInRegistries.ITEM.getKey(off).toString();
        if (counters.timeline.size() >= MAX_TIMELINE) {
            counters.timeline.remove(1);
        }
        counters.timeline.add(stats);
        counters.lastMomentTick = now;
        counters.lastMain = main;
        counters.lastOff = off;
        counters.lastSpeed = speed;
        counters.lastDamage = damage;
    }

    private static boolean moved(double now, double before) {
        return Math.abs(now - before) > Math.abs(before) * CHANGE_FRACTION;
    }

    /** The weapon pairs as saved, most damage first. */
    static java.util.List<RunRecord.WeaponUse> save(Map<Item, Map<Item, WeaponAgg>> weapons) {
        java.util.List<RunRecord.WeaponUse> out = new java.util.ArrayList<>();
        weapons.forEach((main, byOff) -> byOff.forEach((off, agg) -> {
            RunRecord.WeaponUse use = new RunRecord.WeaponUse();
            use.mainHand = BuiltInRegistries.ITEM.getKey(main).toString();
            use.offHand = BuiltInRegistries.ITEM.getKey(off).toString();
            use.hits = agg.hits;
            use.damage = Math.round(agg.damage * 10) / 10f;
            for (int i = 0; i < STAT_NAMES.length; i++) {
                if (agg.count[i] > 0) {
                    use.stats.put(STAT_NAMES[i], new double[]{round(agg.min[i]), round(agg.sum[i] / agg.count[i]), round(agg.max[i]),
                        agg.count[i]});
                }
            }
            agg.effects.forEach((effect, amplifier) ->
                effect.unwrapKey().ifPresent(key -> use.effects.put(key.location().toString(), amplifier)));
            out.add(use);
        }));
        out.sort((a, b) -> Float.compare(b.damage, a.damage));
        return out;
    }

    /** Back from a save (a restart mid-run). */
    static void load(Map<Item, Map<Item, WeaponAgg>> weapons, @Nullable java.util.List<RunRecord.WeaponUse> saved) {
        if (saved == null) {
            return;
        }
        for (RunRecord.WeaponUse use : saved) {
            Item main = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(use.mainHand));
            Item off = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(use.offHand));
            WeaponAgg agg = weapons.computeIfAbsent(main, k -> new HashMap<>()).computeIfAbsent(off, k -> new WeaponAgg());
            agg.hits += use.hits;
            agg.damage += use.damage;
            for (int i = 0; i < STAT_NAMES.length; i++) {
                double[] range = use.stats == null ? null : use.stats.get(STAT_NAMES[i]);
                if (range != null && range.length >= 4 && range[3] > 0) {
                    int n = (int) range[3];
                    agg.min[i] = agg.count[i] == 0 ? range[0] : Math.min(agg.min[i], range[0]);
                    agg.max[i] = agg.count[i] == 0 ? range[2] : Math.max(agg.max[i], range[2]);
                    agg.sum[i] += range[1] * n;
                    agg.count[i] += n;
                }
            }
            if (use.effects != null) {
                use.effects.forEach((id, amplifier) -> BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.tryParse(id))
                    .ifPresent(holder -> agg.effects.merge(holder, amplifier, Math::max)));
            }
        }
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
