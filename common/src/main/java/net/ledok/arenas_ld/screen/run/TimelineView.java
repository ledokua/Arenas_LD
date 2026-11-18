package net.ledok.arenas_ld.screen.run;

import net.ledok.arenas_ld.packet.RunDetailsPayload;
import net.ledok.arenas_ld.packet.RunTimelinePayload;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.screen.RunTheme;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Widget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The run details' Timeline tab (4.37.0; 4.41.0 merged party view): every player's run event log ({@code RunEvents}),
 * paged from the server one player at a time ({@link RunTimelinePayload}, unchanged) and merged here in time order,
 * newest at the bottom. In a party run each line names its player. Two rows of on/off toggles filter it: event kinds
 * and players. "Show earlier" goes back in steps of {@link #STEP} rows. While the run goes it asks for new events every
 * two seconds. Client only; one at a time.
 */
public final class TimelineView {
    private static final int STEP = 300;
    private static final float NAME_MAX = 84;

    enum Filter {
        HITS(Set.of("hit_taken", "hit_cancelled", "dodge")),
        CASTS(Set.of("cast_start", "cast_end", "cast_cancel", "channel_start", "channel_end")),
        PROCS(Set.of("passive")), EFFECTS(Set.of("effect_add", "effect_remove")), SWAPS(Set.of("swap")),
        DOWNS(Set.of("downed", "revived", "death"));

        final Set<String> types;

        Filter(Set<String> types) {
            this.types = types;
        }

        /** The kind an event type belongs to; null for one no toggle names (always shown). */
        static @Nullable Filter of(String type) {
            for (Filter f : values()) {
                if (f.types.contains(type)) return f;
            }
            return null;
        }
    }

    /** One player's log as fetched so far. */
    private static final class Stream {
        final UUID uuid;
        final String name;
        final List<List<String>> events = new ArrayList<>();
        int total;
        int dropped;
        boolean waiting;
        boolean timedOut;
        int waitingTicks;

        Stream(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private record Line(Stream who, List<String> event, long tick) {
    }

    private static final Map<UUID, Stream> STREAMS = new LinkedHashMap<>();
    private static final EnumSet<Filter> KINDS = EnumSet.allOf(Filter.class);
    private static final Set<UUID> HIDDEN = new HashSet<>();
    private static int ticks;
    private static int shown = STEP;
    private static @Nullable Runnable refresh;

    private TimelineView() {
    }

    /** The tab opened (or the screen, with it already picked): everyone's log, filters kept for the same party. */
    public static void open(RunDetailsPayload payload, Runnable onChange) {
        refresh = onChange;
        List<UUID> party = payload.rows().stream().map(RunDetailsPayload.Row::uuid).toList();
        if (!STREAMS.keySet().equals(new java.util.LinkedHashSet<>(party))) {
            STREAMS.clear();
            HIDDEN.clear();
            KINDS.addAll(EnumSet.allOf(Filter.class));
            shown = STEP;
            for (RunDetailsPayload.Row row : payload.rows()) {
                STREAMS.put(row.uuid(), new Stream(row.uuid(), row.name()));
            }
        }
        for (Stream s : STREAMS.values()) {
            if (s.events.isEmpty()) {
                s.waiting = false;
                request(s, 0);
            }
        }
    }

    public static void clear() {
        STREAMS.clear();
        HIDDEN.clear();
        KINDS.addAll(EnumSet.allOf(Filter.class));
        ticks = 0;
        shown = STEP;
        refresh = null;
    }

    private static void request(Stream s, int from) {
        if (s.waiting) {
            return;
        }
        s.waiting = true;
        s.timedOut = false;
        s.waitingTicks = 0;
        ArenasNetwork.sendToServer(new RunTimelinePayload.Request(s.uuid, from));
    }

    public static void onPayload(RunTimelinePayload payload) {
        Stream s = STREAMS.get(payload.player());
        if (s == null) return;
        if (payload.from() != s.events.size()) {
            // The server clamps the offset when the viewer's current log is shorter: start that player over.
            if (payload.from() == payload.total() && payload.total() < s.events.size()) {
                s.events.clear();
                s.total = s.dropped = 0;
                s.waiting = false;
                request(s, 0);
                changed();
            }
            return;
        }
        boolean recovered = s.timedOut;
        s.waiting = false;
        s.timedOut = false;
        s.events.addAll(payload.events());
        s.total = payload.total();
        s.dropped = payload.dropped();
        if (s.events.size() < s.total && !payload.events().isEmpty()) {
            request(s, s.events.size()); // the next page
        }
        if (!payload.events().isEmpty() || payload.from() == 0 || recovered) {
            changed();
        }
    }

    private static void changed() {
        if (refresh != null) refresh.run();
    }

    /** Poll an active run every two seconds. A missing reply exposes Retry after five seconds. */
    public static void tick(boolean live) {
        boolean timedOutNow = false;
        for (Stream s : STREAMS.values()) {
            if (s.waiting && ++s.waitingTicks > 100) {
                s.waiting = false;
                s.timedOut = true;
                timedOutNow = true;
            }
        }
        if (timedOutNow) changed();
        if (live && ++ticks % 40 == 0) {
            for (Stream s : STREAMS.values()) {
                if (!s.timedOut) request(s, s.events.size());
            }
        }
    }

    // ── filters ──────────────────────────────────────────────────────────────

    /** All on: this one alone. Otherwise flips it; switching the last one off turns all back on. */
    private static void toggleKind(Filter f) {
        if (KINDS.size() == Filter.values().length) {
            KINDS.clear();
            KINDS.add(f);
        } else if (!KINDS.remove(f)) {
            KINDS.add(f);
        } else if (KINDS.isEmpty()) {
            KINDS.addAll(EnumSet.allOf(Filter.class));
        }
        shown = STEP;
        changed();
    }

    private static void togglePlayer(UUID uuid) {
        if (HIDDEN.isEmpty()) {
            for (UUID other : STREAMS.keySet()) {
                if (!other.equals(uuid)) HIDDEN.add(other);
            }
        } else if (!HIDDEN.remove(uuid)) {
            HIDDEN.add(uuid);
            if (HIDDEN.size() >= STREAMS.size()) HIDDEN.clear();
        }
        shown = STEP;
        changed();
    }

    // ── build ────────────────────────────────────────────────────────────────

    public static void build(Flex col, RunDetailsPayload payload, RunTheme.Palette pal, float availableWidth) {
        boolean party = STREAMS.size() > 1;
        List<Widget> kinds = new ArrayList<>();
        kinds.add(toggle(pal, Component.translatable("gui.arenas_ld.timeline.filter.all"), pal.info(),
            () -> KINDS.size() == Filter.values().length, () -> {
                KINDS.addAll(EnumSet.allOf(Filter.class));
                shown = STEP;
                changed();
            }, "timeline-filter-all", availableWidth));
        for (Filter f : Filter.values()) {
            String key = f.name().toLowerCase(Locale.ROOT);
            kinds.add(toggle(pal, Component.translatable("gui.arenas_ld.timeline.filter." + key), pal.info(),
                () -> KINDS.size() < Filter.values().length && KINDS.contains(f), () -> toggleKind(f),
                "timeline-filter-" + key, availableWidth));
        }
        Flex filters = Flex.column().gap(4);
        filters.sizing(Sizing.fill(), Sizing.content());
        wrap(filters, kinds, availableWidth);
        if (party) {
            List<Widget> people = new ArrayList<>();
            people.add(toggle(pal, Component.translatable("gui.arenas_ld.timeline.players.all"), pal.accent(),
                HIDDEN::isEmpty, () -> {
                    HIDDEN.clear();
                    shown = STEP;
                    changed();
                }, "timeline-players-all", availableWidth));
            for (Stream s : STREAMS.values()) {
                people.add(toggle(pal, Component.literal(s.name), pal.accent(),
                    () -> !HIDDEN.isEmpty() && !HIDDEN.contains(s.uuid), () -> togglePlayer(s.uuid),
                    "timeline-player-" + s.uuid, availableWidth));
            }
            wrap(filters, people, availableWidth);
        }
        col.item(filters);

        List<Line> all = new ArrayList<>();
        int total = 0;
        int dropped = 0;
        boolean loading = false;
        boolean timedOut = false;
        for (Stream s : STREAMS.values()) {
            timedOut |= s.timedOut;
            if (HIDDEN.contains(s.uuid)) continue;
            total += s.total;
            dropped += s.dropped;
            loading |= s.waiting && (s.events.size() < s.total || s.events.isEmpty());
            for (List<String> event : s.events) {
                if (event.size() < 2) continue;
                Filter kind = Filter.of(event.get(1));
                if (kind == null || KINDS.contains(kind)) {
                    all.add(new Line(s, event, tick(event.get(0))));
                }
            }
        }
        // Each log is in time order already; a stable sort keeps same-tick events in player order.
        if (party) all.sort(Comparator.comparingLong(Line::tick));

        boolean filtered = KINDS.size() < Filter.values().length;
        MutableComponent info = filtered
            ? Component.translatable("gui.arenas_ld.timeline.count_of", all.size(), total)
            : Component.translatable("gui.arenas_ld.timeline.count", total);
        if (dropped > 0) {
            info.append(" · ").append(Component.translatable("gui.arenas_ld.timeline.dropped", dropped));
        }
        if (loading) {
            info.append(" · ").append(Component.translatable("gui.arenas_ld.timeline.loading"));
        }
        Flex counter = line(info, pal.inkDim());
        counter.padding(Insets.of(2, 0, 0, 0));
        col.item(counter);
        if (timedOut) {
            col.item(line(Component.translatable("gui.arenas_ld.timeline.timeout"), pal.warn()));
            Button retry = RunWidgets.button(Component.translatable("gui.arenas_ld.timeline.retry"), () -> {
                for (Stream s : STREAMS.values()) {
                    if (s.timedOut) request(s, s.events.size());
                }
                changed();
            });
            retry.style(RunWidgets.flatStyle(pal));
            col.item(retry);
        }
        if (all.isEmpty()) {
            if (!loading && !timedOut) {
                col.item(line(Component.translatable(total == 0 ? "gui.arenas_ld.timeline.none"
                    : "gui.arenas_ld.timeline.none_matching"), pal.inkMid()));
            }
            return;
        }
        int start = Math.max(0, all.size() - shown);
        if (start > 0) {
            Component label = Component.translatable("gui.arenas_ld.timeline.earlier", start);
            Button earlier = new Button(Math.min(availableWidth, Math.max(160, RunWidgets.width(label) + 16)), 16,
                label, () -> {
                    shown += STEP;
                    changed();
                });
            earlier.style(RunWidgets.flatStyle(pal));
            col.item(earlier);
        }
        float nameW = 0;
        if (party) {
            for (Stream s : STREAMS.values()) {
                nameW = Math.max(nameW, RunWidgets.width(Component.literal(s.name)));
            }
            nameW = Math.min(Math.min(NAME_MAX, availableWidth / 5), nameW + 2);
        }
        for (int i = start; i < all.size(); i++) {
            col.item(row(all.get(i), party ? nameW : 0, pal));
        }
    }

    private static RunWidgets.Toggle toggle(RunTheme.Palette pal, Component label, int color,
                                           java.util.function.BooleanSupplier on, Runnable action, String id,
                                           float availableWidth) {
        RunWidgets.Toggle t = new RunWidgets.Toggle(pal, label, color, on, action);
        if (t.bounds().width() > availableWidth) t.tooltip(label);
        t.id(id);
        return t;
    }

    /** The chips left to right, a new row when the next one wouldn't fit; each row as tall as a chip. */
    private static void wrap(Flex col, List<? extends Widget> chips, float availableWidth) {
        Flex row = null;
        float used = 0;
        for (Widget chip : chips) {
            float width = Math.min(chip.bounds().width(), Math.max(1, availableWidth));
            if (row == null || used + 4 + width > availableWidth) {
                row = col.item(Flex.row().gap(4).alignItems(Align.CENTER));
                row.sizing(Sizing.fill(), Sizing.fixed(RunWidgets.Toggle.HEIGHT));
                used = 0;
            }
            chip.sizing(Sizing.fixed(width), Sizing.fixed(RunWidgets.Toggle.HEIGHT));
            row.item(chip);
            used += (used == 0 ? 0 : 4) + width;
        }
    }

    private static Flex line(Component text, int color) {
        Flex row = Flex.row();
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(RunWidgets.grow(text, color));
        return row;
    }

    private static Flex row(Line line, float nameW, RunTheme.Palette pal) {
        List<String> e = line.event();
        Flex row = Flex.row().gap(6).alignItems(Align.CENTER).padding(Insets.of(1, 4, 1, 4));
        row.sizing(Sizing.fill(), Sizing.content());
        String type = e.get(1);
        boolean saved = "hit_taken".equals(type) && "saved".equals(field(e, 11));
        if (saved || "downed".equals(type) || "death".equals(type)) {
            row.backgroundFill((pal.danger() & 0x00FFFFFF) | 0x22000000);
        }
        Flex time = Flex.row();
        time.sizing(Sizing.fixed(46), Sizing.content());
        time.item(RunWidgets.text(Component.literal(clock(e.get(0))), pal.inkDim()));
        row.item(time);
        if (nameW > 0) {
            var name = RunWidgets.label(nameW, Component.literal(line.who().name), pal.accent());
            name.sizing(Sizing.fixed(nameW), Sizing.content());
            row.item(name);
        }
        row.item(RunWidgets.grow(describe(e), color(type, e, pal)));
        return row;
    }

    private static long tick(String ticks) {
        try {
            return Long.parseLong(ticks);
        } catch (NumberFormatException ex) {
            return Long.MAX_VALUE;
        }
    }

    private static int color(String type, List<String> e, RunTheme.Palette pal) {
        return switch (type) {
            case "hit_taken" -> "lethal".equals(field(e, 11)) || "saved".equals(field(e, 11)) ? pal.danger() : pal.ink();
            case "hit_cancelled", "dodge" -> pal.good();
            case "passive" -> pal.accent();
            case "cast_start", "cast_end", "cast_cancel", "channel_start", "channel_end" -> pal.info();
            case "downed", "death" -> pal.danger();
            case "revived" -> pal.good();
            case "swap" -> pal.inkDim();
            default -> pal.inkMid();
        };
    }

    /** "2:11.35" from a tick count. */
    static String clock(String ticks) {
        long t;
        try {
            t = Long.parseLong(ticks);
        } catch (NumberFormatException ex) {
            return ticks;
        }
        long hundredths = t * 5; // 1 tick = 0.05 s
        long seconds = hundredths / 100;
        return String.format(Locale.ROOT, "%d:%02d.%02d", seconds / 60, seconds % 60, hundredths % 100);
    }

    private static String field(List<String> e, int i) {
        return i < e.size() ? e.get(i) : "";
    }

    /** One event as a line of text (field positions: {@code RunEvents}). */
    static Component describe(List<String> e) {
        String type = e.get(1);
        String k = "gui.arenas_ld.timeline.event." + type;
        return switch (type) {
            case "hit_taken" -> {
                // Raw → what came off health (4.37.1: field 12; older events: after Resistance/enchantments).
                MutableComponent c = Component.translatable(k, entity(field(e, 2), field(e, 3)), field(e, 4),
                    field(e, 12).isEmpty() ? field(e, 6) : field(e, 12), field(e, 8), field(e, 9));
                if (!"0".equals(field(e, 7)) && !field(e, 7).isEmpty()) {
                    c.append(" ").append(Component.translatable("gui.arenas_ld.timeline.absorbed", field(e, 7)));
                }
                if (!"0".equals(field(e, 13)) && !field(e, 13).isEmpty()) {
                    c.append(" ").append(Component.translatable("gui.arenas_ld.timeline.reduced", field(e, 13)));
                }
                procs(c, field(e, 10));
                if ("saved".equals(field(e, 11))) {
                    c.append(" — ").append(Component.translatable("gui.arenas_ld.timeline.saved"));
                } else if ("lethal".equals(field(e, 11))) {
                    c.append(" — ").append(Component.translatable("gui.arenas_ld.timeline.lethal"));
                }
                yield c;
            }
            case "hit_cancelled" -> {
                MutableComponent c = Component.translatable(k, Component.translatable("gui.arenas_ld.timeline.reason."
                    + field(e, 5)), entity(field(e, 2), field(e, 3)), field(e, 4), field(e, 6), field(e, 7));
                procs(c, field(e, 8));
                yield c;
            }
            case "effect_add" -> Component.translatable(k, effect(field(e, 2)), level(field(e, 3)),
                "-1".equals(field(e, 4)) ? "∞" : seconds(field(e, 4)), source(field(e, 5)));
            case "effect_remove" -> Component.translatable(k, effect(field(e, 2)), level(field(e, 3)));
            case "downed" -> Component.translatable(k, field(e, 2).isEmpty() ? Component.literal("?") : cause(field(e, 2)));
            case "revived" -> Component.translatable(k);
            case "death" -> Component.translatable(k, entity(field(e, 3), field(e, 2)));
            case "swap" -> Component.translatable(k, slot(field(e, 2)), item(field(e, 3)), item(field(e, 4)));
            case "cast_start" -> Component.translatable(k, spell(field(e, 2)), from(field(e, 4)), seconds(field(e, 6)));
            case "cast_end" -> {
                MutableComponent c = Component.translatable(k, spell(field(e, 2)), from(field(e, 4)));
                if (!"0".equals(field(e, 6))) {
                    c.append(" ").append(Component.translatable("gui.arenas_ld.timeline.cast_time", seconds(field(e, 6))));
                }
                if (!"0".equals(field(e, 8)) && !field(e, 8).isEmpty()) {
                    c.append(" · ").append(Component.translatable("gui.arenas_ld.timeline.cooldown", seconds(field(e, 8))));
                }
                yield c;
            }
            case "cast_cancel" -> Component.translatable(k, spell(field(e, 2)), seconds(field(e, 4)),
                Component.translatable("gui.arenas_ld.timeline.reason." + field(e, 5).toLowerCase(Locale.ROOT)));
            case "channel_start" -> Component.translatable(k, spell(field(e, 2)), from(field(e, 3)), seconds(field(e, 5)));
            case "channel_end" -> Component.translatable(k, spell(field(e, 2)), seconds(field(e, 3)), field(e, 4),
                Component.translatable("gui.arenas_ld.timeline.reason." + field(e, 5).toLowerCase(Locale.ROOT)));
            case "passive" -> Component.translatable(k, spell(field(e, 2)), field(e, 3).toLowerCase(Locale.ROOT)
                .replace('_', ' '), from(field(e, 5)));
            case "dodge" -> Component.translatable(k, entity(field(e, 2), field(e, 3)), field(e, 4));
            default -> Component.literal(type + " " + String.join(" ", e.subList(2, e.size())));
        };
    }

    private static void procs(MutableComponent c, String procs) {
        if (procs.isEmpty()) {
            return;
        }
        MutableComponent names = Component.empty();
        String[] ids = procs.split(",");
        for (int i = 0; i < ids.length; i++) {
            names.append(i == 0 ? Component.empty() : Component.literal(", ")).append(spell(ids[i]));
        }
        c.append(" · ").append(Component.translatable("gui.arenas_ld.timeline.procs", names));
    }

    private static Component entity(String id, String damageType) {
        if (id.isEmpty()) {
            return Component.literal(damageType.isEmpty() ? "?" : damageType.substring(damageType.indexOf(':') + 1).replace('_', ' '));
        }
        boolean boss = id.startsWith("boss:");
        String plain = boss ? id.substring(5) : id;
        Component name = EntityType.byString(plain).map(EntityType::getDescription)
            .orElse(Component.literal(plain));
        return boss ? Component.translatable("gui.arenas_ld.timeline.boss", name) : name;
    }

    private static Component cause(String cause) {
        int at = cause.indexOf(" / ");
        return at < 0 ? entity("", cause) : entity(cause.substring(0, at), cause.substring(at + 3));
    }

    private static Component effect(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        var effect = rl == null ? null : BuiltInRegistries.MOB_EFFECT.get(rl);
        return effect == null ? Component.literal(id) : effect.getDisplayName();
    }

    private static Component item(String id) {
        if (id.isEmpty()) {
            return Component.literal("—");
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? Component.literal(id) : BuiltInRegistries.ITEM.get(rl).getDescription();
    }

    /** Spell Engine's own name key, else the id's path in words. */
    static Component spell(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return Component.literal(id);
        }
        return Component.translatableWithFallback("spell." + rl.getNamespace() + "." + rl.getPath() + ".name",
            rl.getPath().replace('_', ' '));
    }

    private static Component slot(String slot) {
        if (slot.startsWith("trinket:")) {
            return Component.literal(slot.substring("trinket:".length()));
        }
        return Component.translatableWithFallback("gui.arenas_ld.timeline.slot." + slot, slot);
    }

    /** " from off hand" when the source container isn't the main hand. */
    private static Component from(String container) {
        if (container.isEmpty() || "main_hand".equals(container)) {
            return Component.empty();
        }
        return Component.translatable("gui.arenas_ld.timeline.from", container.replace('_', ' '));
    }

    private static Component source(String source) {
        if (source.isEmpty()) {
            return Component.empty();
        }
        if ("self".equals(source) || "player".equals(source)) {
            return Component.translatable("gui.arenas_ld.timeline.by_" + source);
        }
        return Component.translatable("gui.arenas_ld.timeline.by", entity(source, ""));
    }

    private static String level(String amplifier) {
        try {
            int n = Integer.parseInt(amplifier) + 1;
            return n <= 1 ? "" : " " + n;
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private static String seconds(String ticks) {
        try {
            double s = Double.parseDouble(ticks) / 20.0;
            return s == Math.rint(s) ? Long.toString((long) s) : String.format(Locale.ROOT, "%.1f", s);
        } catch (NumberFormatException ex) {
            return ticks;
        }
    }
}
