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
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The run details' Timeline tab (4.37.0): one player's run event log ({@code RunEvents}), paged from the server
 * ({@link RunTimelinePayload}), newest at the bottom. A player picker, type filters, and "show earlier" in steps of
 * {@link #STEP} rows. While the run goes it asks for new events every two seconds. Client only; one at a time.
 */
public final class TimelineView {
    private static final int STEP = 300;

    enum Filter {
        ALL(null), HITS(Set.of("hit_taken", "hit_cancelled", "dodge")),
        CASTS(Set.of("cast_start", "cast_end", "cast_cancel", "channel_start", "channel_end")),
        PROCS(Set.of("passive")), EFFECTS(Set.of("effect_add", "effect_remove")), SWAPS(Set.of("swap")),
        DOWNS(Set.of("downed", "revived", "death"));

        final @Nullable Set<String> types;

        Filter(@Nullable Set<String> types) {
            this.types = types;
        }
    }

    private static @Nullable UUID player;
    private static final List<List<String>> EVENTS = new ArrayList<>();
    private static int total;
    private static int dropped;
    private static boolean waiting;
    private static int waitingTicks;
    private static int ticks;
    private static Filter filter = Filter.ALL;
    private static int shown = STEP;
    private static @Nullable Runnable refresh;

    private TimelineView() {
    }

    /** The tab opened (or the screen, with it already picked): the viewer's own row first, else the first. */
    public static void open(RunDetailsPayload payload, Runnable onChange) {
        refresh = onChange;
        UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        UUID pick = null;
        for (RunDetailsPayload.Row row : payload.rows()) {
            if (row.uuid().equals(player)) {
                pick = player; // keep the one picked before (same run, reopened tab)
                break;
            }
            if (row.uuid().equals(self) || pick == null) {
                pick = row.uuid();
            }
        }
        if (pick != null) {
            select(pick);
        }
    }

    public static void clear() {
        player = null;
        EVENTS.clear();
        total = dropped = 0;
        waiting = false;
        refresh = null;
    }

    private static void select(UUID uuid) {
        player = uuid;
        EVENTS.clear();
        total = dropped = 0;
        shown = STEP;
        waiting = false;
        request(0);
    }

    private static void request(int from) {
        if (player == null || waiting) {
            return;
        }
        waiting = true;
        waitingTicks = 0;
        ArenasNetwork.sendToServer(new RunTimelinePayload.Request(player, from));
    }

    public static void onPayload(RunTimelinePayload payload) {
        if (!payload.player().equals(player)) {
            return;
        }
        waiting = false;
        if (payload.from() == EVENTS.size()) {
            EVENTS.addAll(payload.events());
        }
        total = payload.total();
        dropped = payload.dropped();
        if (EVENTS.size() < total && !payload.events().isEmpty()) {
            request(EVENTS.size()); // the next page
        }
        if (refresh != null && (!payload.events().isEmpty() || payload.from() == 0)) {
            refresh.run();
        }
    }

    /** Client tick while the tab shows: new events of a going run every 2 s; a lost answer retried after 5 s. */
    public static void tick(boolean live) {
        if (waiting && ++waitingTicks > 100) {
            waiting = false;
        }
        if (live && ++ticks % 40 == 0) {
            request(EVENTS.size());
        }
    }

    // ── build ────────────────────────────────────────────────────────────────

    public static void build(Flex col, RunDetailsPayload payload, RunTheme.Palette pal) {
        if (payload.rows().size() > 1) {
            List<RunWidgets.Segment> people = new ArrayList<>();
            for (RunDetailsPayload.Row row : payload.rows()) {
                UUID uuid = row.uuid();
                people.add(new RunWidgets.Segment(pal, Component.literal(row.name()), pal.accent(),
                    () -> uuid.equals(player), () -> {
                    select(uuid);
                    if (refresh != null) {
                        refresh.run();
                    }
                }));
            }
            col.item(RunWidgets.segmented(people.toArray(RunWidgets.Segment[]::new)));
        }
        List<RunWidgets.Segment> chips = new ArrayList<>();
        for (Filter f : Filter.values()) {
            chips.add(new RunWidgets.Segment(pal, Component.translatable("gui.arenas_ld.timeline.filter."
                + f.name().toLowerCase(Locale.ROOT)), pal.info(), () -> filter == f, () -> {
                filter = f;
                shown = STEP;
                if (refresh != null) {
                    refresh.run();
                }
            }));
        }
        col.item(RunWidgets.segmented(chips.toArray(RunWidgets.Segment[]::new)));

        List<List<String>> matching = new ArrayList<>();
        for (List<String> event : EVENTS) {
            if (event.size() >= 2 && (filter.types == null || filter.types.contains(event.get(1)))) {
                matching.add(event);
            }
        }
        MutableComponent info = Component.translatable("gui.arenas_ld.timeline.count", total);
        if (dropped > 0) {
            info.append(" · ").append(Component.translatable("gui.arenas_ld.timeline.dropped", dropped));
        }
        if (EVENTS.size() < total || waiting && EVENTS.isEmpty()) {
            info.append(" · ").append(Component.translatable("gui.arenas_ld.timeline.loading"));
        }
        col.item(line(info, pal.inkDim()));
        if (matching.isEmpty()) {
            col.item(line(Component.translatable(total == 0 && !waiting ? "gui.arenas_ld.timeline.none"
                : "gui.arenas_ld.timeline.none_matching"), pal.inkDim()));
            return;
        }
        int start = Math.max(0, matching.size() - shown);
        if (start > 0) {
            Button earlier = new Button(160, 16, Component.translatable("gui.arenas_ld.timeline.earlier", start), () -> {
                shown += STEP;
                if (refresh != null) {
                    refresh.run();
                }
            });
            earlier.style(RunWidgets.flatStyle(pal));
            col.item(earlier);
        }
        for (int i = start; i < matching.size(); i++) {
            col.item(row(matching.get(i), pal));
        }
    }

    private static Flex line(Component text, int color) {
        Flex row = Flex.row();
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(RunWidgets.grow(text, color));
        return row;
    }

    private static Flex row(List<String> e, RunTheme.Palette pal) {
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
        row.item(RunWidgets.grow(describe(e), color(type, e, pal)));
        return row;
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
