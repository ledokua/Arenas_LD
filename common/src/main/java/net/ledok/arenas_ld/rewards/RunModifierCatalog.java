package net.ledok.arenas_ld.rewards;

import net.ledok.arenas_ld.config.ArenasLdConfig;
import net.ledok.arenas_ld.config.ArenasLdConfig.Effects;
import net.ledok.arenas_ld.config.ArenasLdConfig.ModifierEntry;
import net.ledok.arenas_ld.config.ArenasLdConfig.RunModifierDef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The run modifiers: the built-in lobby ones (hardcore, LeDok's first seven and the two to-loot ones) plus the config's {@code run_modifiers},
 * whose entries add new ones or replace a built-in by id ({@code "enabled": false} hides one).
 */
public final class RunModifierCatalog {
    private RunModifierCatalog() {
    }

    /** The built-in hardcore modifier; old saves' hardcore flag means this one. */
    public static final String HARDCORE = "hardcore_run";

    /** The built-in lobby modifiers, in their lobby order. */
    static Map<String, RunModifierDef> builtIns() {
        Map<String, RunModifierDef> out = new LinkedHashMap<>();
        // Hardcore (the lobby's old hardcore switch): one life each, 2× items and currency, +25% XP.
        out.put(HARDCORE, def("minecraft:wither_skeleton_skull", List.of(),
            List.of(entry("loot", "more", 2), entry("currency", "more", 2), entry("xp", "increased", 0.25)),
            e -> e.one_life = true));
        out.put("frozen_ground", def("minecraft:packed_ice", List.of(),
            List.of(entry("xp", "increased", 0.15), entry("currency", "increased", 0.25)),
            e -> e.frozen_ground = true));
        out.put("glass_cannon", def("minecraft:glass", List.of(),
            List.of(entry("loot", "flat", 2)),
            e -> { e.damage_taken = 4; e.damage_dealt = 4; }));
        out.put("tanky_mobs", def("minecraft:shield", List.of(),
            List.of(entry("loot", "flat", 1)),
            e -> e.mob_health = 2));
        out.put("bloody_mobs", def("minecraft:redstone", List.of(),
            List.of(entry("loot", "flat", 1)),
            e -> e.damage_taken = 2));
        // Arena wave rows carry no XP to convert into, so items-to-XP is for dungeons and raids.
        out.put("items_to_xp", def("minecraft:experience_bottle", List.of("dungeon", "raid"),
            List.of(entry("loot", "convert", 0.10)), e -> { }, "xp_to_loot", "currency_to_loot"));
        out.put("currency_to_xp", def("minecraft:gold_nugget", List.of(),
            List.of(entry("currency", "convert", 0.25)), e -> { }, "currency_to_loot", "xp_to_loot"));
        out.put("speed_run", def("minecraft:sugar", List.of(),
            List.of(entry("xp", "increased", 0.10)),
            e -> { e.mob_speed = 1.5; e.player_speed = 1.5; }));
        // The way back: what the run would give as XP or currency becomes loot rolls (none of it is paid).
        out.put("xp_to_loot", def("minecraft:chest", List.of(),
            List.of(entry("xp", "more", 0), entry("loot", "flat", 2)), e -> { }, "items_to_xp", "currency_to_xp"));
        out.put("currency_to_loot", def("minecraft:emerald", List.of(),
            List.of(entry("currency", "more", 0), entry("loot", "flat", 1)), e -> { }, "currency_to_xp", "items_to_xp"));
        out.forEach((id, def) -> {
            def.name = "run_modifier.arenas_ld." + id;
            def.description = "run_modifier.arenas_ld." + id + ".desc";
        });
        return out;
    }

    private static RunModifierDef def(String icon, List<String> types, List<ModifierEntry> modifiers,
                                      java.util.function.Consumer<Effects> effects, String... conflicts) {
        RunModifierDef def = new RunModifierDef();
        def.conflicts = new ArrayList<>(List.of(conflicts));
        def.icon = icon;
        def.selectable = true;
        def.types = new ArrayList<>(types);
        def.modifiers = new ArrayList<>(modifiers);
        def.effects = new Effects();
        effects.accept(def.effects);
        return def;
    }

    private static ModifierEntry entry(String stat, String kind, double value) {
        ModifierEntry entry = new ModifierEntry();
        entry.stat = stat;
        entry.kind = kind;
        entry.value = value;
        return entry;
    }

    /**
     * Every enabled modifier by id: the built-ins, then the config's (replacing a built-in of the same id).
     * A replacement that leaves its name, description or icon empty, or its effects or conflicts out, keeps the built-in's.
     */
    public static Map<String, RunModifierDef> all() {
        Map<String, RunModifierDef> out = builtIns();
        ArenasLdConfig.getInstance().run_modifiers.forEach((id, def) -> {
            RunModifierDef builtIn = out.get(id);
            out.put(id, def == null || builtIn == null ? def : withBuiltInText(def, builtIn));
        });
        out.values().removeIf(def -> def == null || !def.enabled);
        return out;
    }

    /** A copy of the config's {@code def} with its empty texts taken from {@code builtIn} (the config stays as written). */
    private static RunModifierDef withBuiltInText(RunModifierDef def, RunModifierDef builtIn) {
        RunModifierDef out = new RunModifierDef();
        out.enabled = def.enabled;
        out.name = blank(def.name) ? builtIn.name : def.name;
        out.description = blank(def.description) ? builtIn.description : def.description;
        out.icon = blank(def.icon) ? builtIn.icon : def.icon;
        out.selectable = def.selectable;
        out.types = def.types;
        out.group = def.group;
        out.modifiers = def.modifiers;
        out.effects = def.effects == null ? builtIn.effects : def.effects;
        out.conflicts = def.conflicts == null ? builtIn.conflicts : def.conflicts;
        return out;
    }

    private static boolean blank(@Nullable String text) {
        return text == null || text.isBlank();
    }

    public static @Nullable RunModifierDef get(String id) {
        return all().get(id);
    }

    /** The modifiers a lobby of {@code type} ("dungeon", "raid", "arena") can switch on, in order. */
    public static Map<String, RunModifierDef> selectable(String type) {
        Map<String, RunModifierDef> out = new LinkedHashMap<>();
        all().forEach((id, def) -> {
            if (def.selectable && allows(def, type)) {
                out.put(id, def);
            }
        });
        return out;
    }

    public static boolean allows(RunModifierDef def, String type) {
        return def.types == null || def.types.isEmpty()
            || def.types.stream().anyMatch(t -> t.equalsIgnoreCase(type));
    }

    /** The other modifiers of {@code id}'s exclusive group (switched off when it goes on). */
    public static List<String> sameGroup(String id) {
        RunModifierDef def = get(id);
        List<String> out = new ArrayList<>();
        if (def == null || def.group == null || def.group.isBlank()) {
            return out;
        }
        all().forEach((other, d) -> {
            if (!other.equals(id) && def.group.equals(d.group)) {
                out.add(other);
            }
        });
        return out;
    }

    /**
     * What the picked modifiers do together. Factors add their bonuses (LeDok 2026-11-17):
     * {@code 1 + Σ (value − 1)}, never below 0; so ×4 and ×2 make ×5, not ×8. The run's tier multiplies on top
     * elsewhere ({@code LivingEntityHooks}). Frozen ground and one life: if any has it.
     */
    public static Effects combined(Collection<String> ids) {
        Effects out = new Effects();
        for (String id : ids) {
            RunModifierDef def = get(id);
            if (def == null || def.effects == null) {
                continue;
            }
            Effects e = def.effects;
            out.damage_taken += e.damage_taken - 1;
            out.damage_dealt += e.damage_dealt - 1;
            out.mob_health += e.mob_health - 1;
            out.mob_speed += e.mob_speed - 1;
            out.player_speed += e.player_speed - 1;
            out.frozen_ground |= e.frozen_ground;
            out.one_life |= e.one_life;
        }
        out.damage_taken = Math.max(0, out.damage_taken);
        out.damage_dealt = Math.max(0, out.damage_dealt);
        out.mob_health = Math.max(0, out.mob_health);
        out.mob_speed = Math.max(0, out.mob_speed);
        out.player_speed = Math.max(0, out.player_speed);
        return out;
    }

    /**
     * Writes each built-in missing from the config's {@code run_modifiers} into it with all its values (name and
     * description as translation keys), so the file shows every modifier to edit. Entries already there stay.
     */
    public static void addMissingBuiltIns(Map<String, RunModifierDef> configured) {
        builtIns().forEach(configured::putIfAbsent);
    }

    /** The ids {@code id} can't be picked with: its own conflicts and every modifier listing it. */
    public static Set<String> conflicts(String id) {
        Map<String, RunModifierDef> all = all();
        Set<String> out = new LinkedHashSet<>();
        RunModifierDef def = all.get(id);
        if (def != null && def.conflicts != null) {
            out.addAll(def.conflicts);
        }
        all.forEach((other, d) -> {
            if (d.conflicts != null && d.conflicts.contains(id)) {
                out.add(other);
            }
        });
        out.remove(id);
        return out;
    }

    /** The first of {@code picked} that conflicts with {@code id}, or null. */
    public static @Nullable String conflictWith(String id, Collection<String> picked) {
        Set<String> conflicts = conflicts(id);
        for (String other : picked) {
            if (!other.equals(id) && conflicts.contains(other)) {
                return other;
            }
        }
        return null;
    }

    /** The modifier's name for players (its translation key or literal text, the id when it has none). */
    public static net.minecraft.network.chat.Component displayName(String id) {
        RunModifierDef def = get(id);
        return RewardMath.sourceName(def == null || def.name == null || def.name.isBlank() ? id : def.name);
    }

    /**
     * Whether switching {@code id} on is refused because a picked modifier conflicts with it (the ones of its
     * exclusive group don't count: they go off); tells {@code player} which one.
     */
    public static boolean refusePick(net.minecraft.server.level.ServerPlayer player, String id, Collection<String> picked) {
        Set<String> staying = new LinkedHashSet<>(picked);
        sameGroup(id).forEach(staying::remove);
        String other = conflictWith(id, staying);
        if (other == null) {
            return false;
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.arenas_ld.run_modifier.conflict",
            displayName(id), displayName(other)).withStyle(net.minecraft.ChatFormatting.RED));
        return true;
    }

    /** "Hardcore run conflicts with …" for a pick that has two conflicting modifiers (the config changed), or null. */
    public static net.minecraft.network.chat.@Nullable Component conflictRefusal(Collection<String> picked) {
        for (String id : picked) {
            String other = conflictWith(id, picked);
            if (other != null) {
                return net.minecraft.network.chat.Component.translatable("message.arenas_ld.run_modifier.conflict_start",
                    displayName(id), displayName(other)).withStyle(net.minecraft.ChatFormatting.RED);
            }
        }
        return null;
    }

    /** Whether the picked modifiers make the run hardcore (one life each). */
    public static boolean oneLife(Collection<String> ids) {
        return combined(ids).one_life;
    }

    /**
     * {@code ids} as an old save means them: its hardcore flag ({@code hardcore}) set and no one-life modifier
     * picked adds {@link #HARDCORE}.
     */
    public static Set<String> withLegacyHardcore(Collection<String> ids, boolean hardcore) {
        Set<String> out = new LinkedHashSet<>(ids);
        if (hardcore && !oneLife(ids)) {
            out.add(HARDCORE);
        }
        return out;
    }

    /** "dungeon" / "raid" / "arena" for an activity type name. */
    public static String typeKey(Enum<?> type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
