package net.ledok.arenas_ld.rewards;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.compat.LuckPermsCompat;
import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.rewards.RewardModifier.Stat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Where reward modifiers come from, collected for one payout:
 * <ul>
 *   <li><b>LuckPerms</b> (per player): {@code arenas_ld.loot_bonus}, {@code xp_bonus}, {@code currency_bonus}
 *       (increased, 0.5 = +50%), each overridable per mode ({@code arenas_ld.dungeon.loot_bonus} …), shown as
 *       {@code arenas_ld.bonus_name}. Until those are set, the old keys are read and converted
 *       ({@code raid/dungeon_loot_rolls} N → N / base rolls − 1, {@code currency_multiplier}/{@code xp_multiplier} M → M − 1;
 *       the old {@code arena_loot_rolls} is not read, see {@code arena_player_row_rolls}).</li>
 *   <li><b>Run modifiers</b> the lobby picked, hardcore ({@code hardcore_run}) among them.</li>
 *   <li><b>Run modifiers</b> the config attaches to an activity ({@code activity_modifiers}), and
 *       <b>reward events</b> active now ({@code reward_events}).</li>
 *   <li>Other mods: {@link #registerSource}.</li>
 * </ul>
 */
public final class RewardModifiers {
    public static final String HARDCORE_NAME = "message.arenas_ld.reward.source.hardcore";
    public static final String RANK_NAME = "message.arenas_ld.reward.source.rank";

    /** Which run type a payout is for (LuckPerms per-mode keys, old-key conversion). */
    public enum Mode { RAID, DUNGEON, ARENA }

    /** Run-wide modifiers another mod adds (an event, a buff): asked at every payout. */
    @FunctionalInterface
    public interface Source {
        List<RewardModifier> modifiers(ResourceLocation activity, @Nullable String difficulty, Mode mode);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    private static boolean warnedOldKeys;
    /** An event time that didn't parse: the event never applies. */
    private static final Instant INVALID = Instant.MIN;

    private RewardModifiers() {
    }

    public static void registerSource(Source source) {
        SOURCES.add(source);
    }

    // ── per player: LuckPerms ────────────────────────────────────────────────

    /** {@code player}'s LuckPerms modifiers for {@code mode}; empty offline or without LuckPerms. */
    public static List<RewardModifier> luckPerms(UUID player, Mode mode) {
        String name = LuckPermsCompat.metaString(player, "arenas_ld.bonus_name").orElse(RANK_NAME);
        String prefix = "arenas_ld." + mode.name().toLowerCase(Locale.ROOT) + ".";
        List<RewardModifier> out = new ArrayList<>();
        bonus(player, prefix, "loot_bonus").ifPresentOrElse(
            v -> out.add(RewardModifier.increased(Stat.LOOT, v, "luckperms", name)),
            () -> oldLootRolls(player, mode).ifPresent(v -> out.add(RewardModifier.increased(Stat.LOOT, v, "luckperms", name))));
        bonus(player, prefix, "xp_bonus").ifPresentOrElse(
            v -> out.add(RewardModifier.increased(Stat.XP, v, "luckperms", name)),
            () -> oldMultiplier(player, "arenas_ld.xp_multiplier").ifPresent(v -> out.add(RewardModifier.increased(Stat.XP, v, "luckperms", name))));
        bonus(player, prefix, "currency_bonus").ifPresentOrElse(
            v -> out.add(RewardModifier.increased(Stat.CURRENCY, v, "luckperms", name)),
            () -> oldMultiplier(player, "arenas_ld.currency_multiplier").ifPresent(v -> out.add(RewardModifier.increased(Stat.CURRENCY, v, "luckperms", name))));
        out.removeIf(RewardModifier::isNeutral);
        return out;
    }

    private static OptionalDouble bonus(UUID player, String modePrefix, String key) {
        OptionalDouble perMode = LuckPermsCompat.metaDouble(player, modePrefix + key);
        return perMode.isPresent() ? perMode : LuckPermsCompat.metaDouble(player, "arenas_ld." + key);
    }

    /**
     * The old integer roll key as an increased bonus over the base rolls. The old arena key is not read:
     * its ×3 for everyone is now the config's {@code arena_player_row_rolls}.
     */
    private static OptionalDouble oldLootRolls(UUID player, Mode mode) {
        String key = switch (mode) {
            case RAID -> "arenas_ld.raid_loot_rolls";
            case DUNGEON -> "arenas_ld.dungeon_loot_rolls";
            case ARENA -> null;
        };
        if (key == null) {
            return OptionalDouble.empty();
        }
        var rolls = LuckPermsCompat.metaInt(player, key);
        if (rolls.isEmpty()) {
            return OptionalDouble.empty();
        }
        warnOldKeys();
        double base = Math.max(0.01, ArenasLdConfig.getInstance().completion_loot_rolls);
        return OptionalDouble.of(Math.clamp(rolls.getAsInt(), 1, 10) / base - 1);
    }

    private static OptionalDouble oldMultiplier(UUID player, String key) {
        OptionalDouble multiplier = LuckPermsCompat.metaDouble(player, key);
        if (multiplier.isEmpty()) {
            return multiplier;
        }
        warnOldKeys();
        return OptionalDouble.of(Math.clamp(multiplier.getAsDouble(), 0.0, 10.0) - 1);
    }

    private static void warnOldKeys() {
        if (!warnedOldKeys) {
            warnedOldKeys = true;
            ArenasLdMod.LOGGER.info("[Arenas] LuckPerms still uses the old reward keys (*_loot_rolls, currency_multiplier, "
                + "xp_multiplier); they are read as +% bonuses. Switch to arenas_ld.loot_bonus / xp_bonus / currency_bonus.");
        }
    }

    // ── per run: hardcore, run modifiers, events, other mods ─────────────────

    /** Every run-wide modifier for a payout of {@code activity} (and difficulty) now. */
    public static List<RewardModifier> forRun(@Nullable ResourceLocation activity, @Nullable String difficulty,
                                              boolean hardcore, Mode mode) {
        return forRun(activity, difficulty, hardcore, mode, List.of());
    }

    /** As above, plus the run modifiers the lobby picked ({@code picked}). */
    public static List<RewardModifier> forRun(@Nullable ResourceLocation activity, @Nullable String difficulty,
                                              boolean hardcore, Mode mode, java.util.Collection<String> picked) {
        List<RewardModifier> out = new ArrayList<>();
        for (String id : activeRunModifierIds(activity, difficulty)) {
            out.addAll(definition(id));
        }
        // Hardcore is the hardcore_run modifier; a run saved before that (only its flag set) gets it here.
        for (String id : RunModifierCatalog.withLegacyHardcore(picked, hardcore)) {
            out.addAll(definition(id));
        }
        if (activity != null) {
            for (Source source : SOURCES) {
                try {
                    out.addAll(source.modifiers(activity, difficulty, mode));
                } catch (Throwable t) {
                    ArenasLdMod.LOGGER.error("Reward modifier source {} failed", source, t);
                }
            }
        }
        out.removeIf(RewardModifier::isNeutral);
        return out;
    }

    /** The run modifier ids attached to the activity (all difficulties, then this one) and the events running now. */
    public static List<String> activeRunModifierIds(@Nullable ResourceLocation activity, @Nullable String difficulty) {
        ArenasLdConfig config = ArenasLdConfig.getInstance();
        List<String> ids = new ArrayList<>();
        if (activity != null) {
            ids.addAll(config.activity_modifiers.getOrDefault(activity.toString(), List.of()));
            if (difficulty != null) {
                ids.addAll(config.activity_modifiers.getOrDefault(activity + "#" + difficulty, List.of()));
            }
        }
        Instant now = Instant.now();
        for (ArenasLdConfig.RewardEvent event : config.reward_events) {
            if (event != null && event.modifier != null && activeAt(event, now)) {
                ids.add(event.modifier);
            }
        }
        return ids;
    }

    static boolean activeAt(ArenasLdConfig.RewardEvent event, Instant now) {
        Instant start = parse(event.start);
        Instant end = parse(event.end);
        if (start == INVALID || end == INVALID) {
            return false;
        }
        return (start == null || !now.isBefore(start)) && (end == null || now.isBefore(end));
    }

    private static @Nullable Instant parse(@Nullable String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(time.trim()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(time.trim());
            } catch (DateTimeParseException e2) {
                ArenasLdMod.LOGGER.warn("[Arenas] Reward event time '{}' isn't an ISO time; the event is ignored", time);
                return INVALID;
            }
        }
    }

    /** A configured run modifier's stat changes, named after it; empty (with a warning) when unknown. */
    public static List<RewardModifier> definition(String id) {
        ArenasLdConfig.RunModifierDef def = RunModifierCatalog.get(id);
        if (def == null) {
            ArenasLdMod.LOGGER.warn("[Arenas] Unknown run modifier '{}' (not in run_modifiers)", id);
            return List.of();
        }
        String name = def.name == null || def.name.isBlank() ? id : def.name;
        List<RewardModifier> out = new ArrayList<>();
        if (def.modifiers != null) {
            for (ArenasLdConfig.ModifierEntry entry : def.modifiers) {
                try {
                    out.add(new RewardModifier(Stat.valueOf(entry.stat.toUpperCase(Locale.ROOT)),
                        RewardModifier.Kind.valueOf(entry.kind.toUpperCase(Locale.ROOT)), entry.value, id, name));
                } catch (RuntimeException e) {
                    ArenasLdMod.LOGGER.warn("[Arenas] Run modifier '{}' has a bad entry ({} {} {})", id,
                        entry.stat, entry.kind, entry.value);
                }
            }
        }
        return out;
    }

    /**
     * The lobby header: the activity's run modifiers and events, then what the viewer's own lobby picked
     * as one total ("Picked: +3 loot · +35% XP"); empty when there is nothing.
     */
    public static Component lobbyLine(@Nullable ResourceLocation activity, java.util.Collection<String> picked) {
        MutableComponent line = lobbyLine(activity).copy();
        List<RewardModifier> chosen = new ArrayList<>();
        for (String id : picked) {
            chosen.addAll(definition(id));
        }
        Component total = RunModifierOption.rewards(total(chosen));
        if (!total.getString().isEmpty()) {
            if (!line.getString().isEmpty()) {
                line.append(" · ");
            }
            line.append(Component.translatable("message.arenas_ld.reward.picked", total));
        }
        return line;
    }

    /** Modifiers summed per stat and kind: flats and increased add, more factors multiply, conversions stay. */
    public static List<RewardModifier> total(List<RewardModifier> modifiers) {
        List<RewardModifier> out = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            double flat = 0;
            double increased = 0;
            double more = 1;
            boolean convert = false;
            double convertValue = 0;
            for (RewardModifier m : modifiers) {
                if (m.stat() != stat) {
                    continue;
                }
                switch (m.kind()) {
                    case FLAT -> flat += m.value();
                    case INCREASED -> increased += m.value();
                    case MORE -> more *= m.value();
                    case CONVERT -> {
                        convert = true;
                        convertValue += m.value();
                    }
                }
            }
            out.add(new RewardModifier(stat, RewardModifier.Kind.FLAT, flat, "total", ""));
            out.add(new RewardModifier(stat, RewardModifier.Kind.INCREASED, increased, "total", ""));
            out.add(new RewardModifier(stat, RewardModifier.Kind.MORE, more, "total", ""));
            if (convert) {
                out.add(new RewardModifier(stat, RewardModifier.Kind.CONVERT, convertValue, "total", ""));
            }
        }
        out.removeIf(RewardModifier::isNeutral);
        return out;
    }

    /** "Treasure Hunter (+15% loot) · Weekend (+25% XP)" for the lobby; empty when nothing is active. */
    public static Component lobbyLine(@Nullable ResourceLocation activity) {
        MutableComponent line = Component.empty();
        boolean first = true;
        for (String id : activeRunModifierIds(activity, null)) {
            List<RewardModifier> mods = definition(id);
            if (mods.isEmpty()) {
                continue;
            }
            if (!first) {
                line.append(" · ");
            }
            first = false;
            line.append(RewardMath.sourceName(mods.get(0).name())).append(" (");
            for (int i = 0; i < mods.size(); i++) {
                if (i > 0) {
                    line.append(", ");
                }
                line.append(RewardMath.amount(mods.get(i))).append(" ")
                    .append(Component.translatable("message.arenas_ld.reward.stat_short."
                        + mods.get(i).stat().name().toLowerCase(Locale.ROOT)));
            }
            line.append(")");
        }
        return first ? Component.empty() : line;
    }
}
