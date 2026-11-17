package net.ledok.arenas_ld.stats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The run event log (4.37.0, LeDok 2026-11-17): what each run player did and what happened to them, as events with the
 * exact tick since the run started, instead of sampling them every second. Hooks only: every method starts from the
 * player's run mark ({@link RunStatsHolder}), so players outside runs cost one null check.
 *
 * <p>One event = one compact list {@code [tick, type, fields…]} (saved as a JSON array in the run record). Types and
 * their fields (numbers rounded to 0.1):
 * <ul>
 *   <li>{@code hit_taken}: attacker (entity type id, "boss:" in front for a run boss, "" without), damage type, raw,
 *       after armor, after Resistance/enchantments, absorbed, HP before, HP after, procs (passives that fired during
 *       the hit, comma-separated), flag, applied, other reductions (4.37.1). {@code applied} is what really came off
 *       health; {@code other} = after Resistance/enchantments − absorbed − applied: what other mods took off on the
 *       way (Eden's damage reduction and the like). Flag: "lethal" = it took them down; "saved" = what came off
 *       health was at least their HP, yet they lived (something stopped it at the end: a floor, a totem, an on-hit
 *       heal; {@code procs} names the passives that fired in that hit); "" otherwise.</li>
 *   <li>{@code hit_cancelled}: attacker, damage type, amount, reason (dodge / shield / cancelled), HP before, HP after,
 *       procs. Hits ignored by the half-second invulnerability are only counted ({@code hit_ignored}).</li>
 *   <li>{@code effect_add}: effect, amplifier, duration ticks, source ("self", "player", an entity type id, "");
 *       a refresh at the same or a lower amplifier is only counted ({@code effect_refresh})</li>
 *   <li>{@code effect_remove}: effect, amplifier</li>
 *   <li>{@code downed}: cause (the last hit's attacker / damage type); {@code revived}; {@code death}: damage type,
 *       attacker</li>
 *   <li>{@code swap}: slot (mainhand, offhand, head…, "trinket:&lt;group&gt;/&lt;slot&gt;"), item before, item after
 *       (swaps of one slot within half a second merge into one)</li>
 *   <li>from Spell Engine Expansion ({@link #spell}): {@code cast_start}, {@code cast_end}, {@code cast_cancel},
 *       {@code channel_start}, {@code channel_end}, {@code passive}, {@code dodge}</li>
 * </ul>
 * At most {@link #CAP} events per player per run; after that only {@code eventCounts} grows.
 */
public final class RunEvents {
    public static final int CAP = 5000;
    /** Swaps of the same slot this close together are one swap (scrolling the hotbar, a trinket re-equipped). */
    static final int SWAP_MERGE_TICKS = 10;
    /** Events that a hit causes: written after the hit's own row. */
    private static final Set<String> AFTER_HIT = Set.of("downed", "death", "effect_add", "effect_remove", "revived");

    private RunEvents() {
    }

    private static @Nullable PlayerCounters counters(Entity player) {
        return player instanceof Player && ((RunStatsHolder) player).arenasLd$runStats() instanceof PlayerCounters c
            && !c.run.ended() ? c : null;
    }

    /** Appends an event; the event list (to change later), or null when capped. */
    static @Nullable List<Object> add(PlayerCounters c, long gameTime, String type, Object... fields) {
        if (c.run.ended()) {
            return null;
        }
        List<Object> event = new ArrayList<>(fields.length + 2);
        event.add(Math.max(0, gameTime - c.run.startTick()));
        event.add(type);
        for (Object field : fields) {
            event.add(field instanceof Float f ? round(f) : field instanceof Double d ? round(d.floatValue()) : field);
        }
        if (c.hitDepth > 0 && AFTER_HIT.contains(type)) {
            c.hitAfter.add(event); // written (and counted) when the hit closes
            return event;
        }
        return store(c, event);
    }

    private static @Nullable List<Object> store(PlayerCounters c, List<Object> event) {
        c.eventCounts.merge((String) event.get(1), 1, Integer::sum);
        if (c.events.size() >= CAP) {
            c.eventsDropped++;
            return null;
        }
        c.events.add(event);
        return event;
    }

    private static void count(PlayerCounters c, String type) {
        c.eventCounts.merge(type, 1, Integer::sum);
    }

    static float round(float value) {
        return Math.round(value * 10) / 10f;
    }

    // ── hits ─────────────────────────────────────────────────────────────────

    /** {@code ServerPlayer.hurt} began: opens the hit (nested hurts, e.g. thorns back, stay inside it). */
    public static void hurtStart(ServerPlayer player, DamageSource source, float amount) {
        PlayerCounters c = counters(player);
        if (c == null || c.hitDepth++ > 0) {
            return;
        }
        c.hitGameTime = player.level().getGameTime();
        c.hitAmount = amount;
        c.hitHpBefore = player.getHealth();
        c.hitAttacker = attacker(source);
        c.hitDamageType = damageType(source);
        c.hitIframes = player.invulnerableTime > 10;
        c.hitLanded = false;
        c.hitApplied = -1;
        c.hitShield = false;
        c.hitDodged = false;
        c.hitProcs.clear();
        c.hitAfter.clear();
    }

    /** {@code ServerPlayer.hurt} returned: writes the hit, then what it caused. */
    public static void hurtEnd(ServerPlayer player) {
        PlayerCounters c = ((RunStatsHolder) player).arenasLd$runStats() instanceof PlayerCounters pc ? pc : null;
        if (c == null || c.hitDepth <= 0 || --c.hitDepth > 0) {
            return;
        }
        String procs = String.join(",", c.hitProcs);
        float hpAfter = player.getHealth();
        if (c.hitLanded) {
            boolean down = c.hitAfter.stream().anyMatch(e -> "downed".equals(e.get(1)) || "death".equals(e.get(1)));
            // What really came off health (after every mod's reductions), not the vanilla steps' estimate.
            float applied = c.hitApplied >= 0 ? c.hitApplied : Math.max(0, c.hitHpBefore - hpAfter);
            float other = Math.max(0, c.hitAfterMagic - c.hitAbsorbed - applied);
            boolean lethal = applied >= c.hitHpBefore && c.hitHpBefore > 0;
            String flag = down || hpAfter <= 0 ? "lethal" : lethal ? "saved" : "";
            c.lastCause = c.hitAttacker.isEmpty() ? c.hitDamageType : c.hitAttacker + " / " + c.hitDamageType;
            add(c, c.hitGameTime, "hit_taken", c.hitAttacker, c.hitDamageType, c.hitRaw, c.hitAfterArmor,
                c.hitAfterMagic, c.hitAbsorbed, c.hitHpBefore, hpAfter, procs, flag, applied, other);
        } else if (c.hitAmount > 0) {
            String reason = c.hitDodged ? "dodge" : c.hitShield ? "shield" : c.hitIframes ? null : "cancelled";
            if (reason == null) {
                count(c, "hit_ignored");
            } else {
                add(c, c.hitGameTime, "hit_cancelled", c.hitAttacker, c.hitDamageType, c.hitAmount, reason,
                    c.hitHpBefore, hpAfter, procs);
            }
        }
        for (List<Object> after : c.hitAfter) {
            store(c, after);
        }
        c.hitAfter.clear();
        c.hitProcs.clear();
    }

    /** The hit reached the player's health or absorption ({@code RunStats.onHurt}): its numbers. */
    static void landed(LivingEntity victim, PlayerCounters c, DamageSource source, float raw, float armor,
                       float resistance, float enchant, float absorbed, float lost, float toHealth) {
        float afterArmor = Math.max(0, raw - armor);
        float afterMagic = Math.max(0, afterArmor - resistance - enchant);
        if (c.hitDepth > 0) {
            c.hitLanded = true;
            c.hitRaw = raw;
            c.hitAfterArmor = afterArmor;
            c.hitAfterMagic = afterMagic;
            c.hitAbsorbed = absorbed;
            c.hitToHealth = toHealth;
            return;
        }
        // Damage that skipped ServerPlayer.hurt (a mod calling actuallyHurt directly): written at once.
        float hpAfter = victim.getHealth();
        add(c, victim.level().getGameTime(), "hit_taken", attacker(source), damageType(source), raw, afterArmor,
            afterMagic, absorbed, hpAfter + lost, hpAfter, "", hpAfter <= 0 ? "lethal" : "", lost,
            Math.max(0, afterMagic - absorbed - lost));
    }

    /** {@code Player.actuallyHurt} is setting health: the damage really applied (other mods' reductions included). */
    public static void applied(Player player, float damage) {
        if (((RunStatsHolder) player).arenasLd$runStats() instanceof PlayerCounters c && c.hitDepth > 0) {
            c.hitApplied = Math.max(0, damage);
        }
    }

    static void shield(PlayerCounters c) {
        if (c.hitDepth > 0) {
            c.hitShield = true;
        }
    }

    private static String attacker(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity == null) {
            return "";
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return entity instanceof RunStatsHolder holder && holder.arenasLd$boss() ? "boss:" + id : id;
    }

    private static String damageType(DamageSource source) {
        return source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("");
    }

    // ── effects ──────────────────────────────────────────────────────────────

    /** {@code addEffect} on a run player (before vanilla applies it). */
    static void effectAdded(LivingEntity player, PlayerCounters c, MobEffectInstance effect, @Nullable Entity source) {
        MobEffectInstance old = player.getEffect(effect.getEffect());
        if (old != null && old.getAmplifier() >= effect.getAmplifier()) {
            count(c, "effect_refresh");
            return;
        }
        String from = source == null ? "" : source == player ? "self" : source instanceof Player ? "player"
            : BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).toString();
        add(c, player.level().getGameTime(), "effect_add", effectId(effect), effect.getAmplifier(),
            effect.isInfiniteDuration() ? -1 : effect.getDuration(), from);
    }

    /** {@code onEffectRemoved}: it ran out or was taken away. */
    public static void effectRemoved(LivingEntity player, MobEffectInstance effect) {
        PlayerCounters c = counters(player);
        if (c != null) {
            add(c, player.level().getGameTime(), "effect_remove", effectId(effect), effect.getAmplifier());
        }
    }

    private static String effectId(MobEffectInstance effect) {
        return effect.getEffect().unwrapKey().map(k -> k.location().toString()).orElse("");
    }

    // ── downed, revived, death ───────────────────────────────────────────────

    public static void downed(ServerPlayer player) {
        PlayerCounters c = counters(player);
        if (c != null) {
            add(c, player.level().getGameTime(), "downed", c.lastCause);
        }
    }

    public static void revived(ServerPlayer player) {
        PlayerCounters c = counters(player);
        if (c != null) {
            add(c, player.level().getGameTime(), "revived");
        }
    }

    public static void died(LivingEntity player, DamageSource source) {
        PlayerCounters c = counters(player);
        if (c != null) {
            add(c, player.level().getGameTime(), "death", damageType(source), attacker(source));
        }
    }

    // ── swaps ────────────────────────────────────────────────────────────────

    /** An equipment slot changed item (vanilla's equipment check, or a trinket slot). Same item: nothing. */
    public static void swap(LivingEntity player, String slot, ItemStack before, ItemStack after) {
        PlayerCounters c = counters(player);
        if (c == null) {
            return;
        }
        String was = itemId(before);
        String now = itemId(after);
        if (was.equals(now)) {
            return;
        }
        long gameTime = player.level().getGameTime();
        long tick = Math.max(0, gameTime - c.run.startTick());
        List<Object> last = c.lastSwap.get(slot);
        if (last != null && tick - ((Number) last.get(0)).longValue() <= SWAP_MERGE_TICKS) {
            last.set(4, now); // the same swap, still going: keep where it started, take where it ended
            return;
        }
        List<Object> event = add(c, gameTime, "swap", slot, was, now);
        if (event != null) {
            c.lastSwap.put(slot, event);
        }
    }

    private static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ── Spell Engine Expansion ───────────────────────────────────────────────

    /**
     * An event from Spell Engine Expansion's {@code SpellActivityEvents} (cast_start, cast_end, cast_cancel,
     * channel_start, channel_end, passive, dodge), with its fields in the order the class doc lists. A passive during an
     * open hit is also written onto that hit's row; a dodge marks it dodged.
     */
    public static void spell(ServerPlayer player, long gameTime, String type, Object... fields) {
        PlayerCounters c = counters(player);
        if (c == null) {
            return;
        }
        if (c.hitDepth > 0) {
            if ("passive".equals(type) && fields.length > 0) {
                c.hitProcs.add(String.valueOf(fields[0]));
            } else if ("dodge".equals(type)) {
                c.hitDodged = true;
            }
        }
        add(c, gameTime, type, fields);
    }
}
