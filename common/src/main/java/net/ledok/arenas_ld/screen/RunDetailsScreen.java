package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.client.hud.LootRevealHud;
import net.ledok.arenas_ld.packet.RunDetailsClosedPayload;
import net.ledok.arenas_ld.packet.RunDetailsPayload;
import net.ledok.arenas_ld.packet.RunHudPayload;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.screen.run.RunWidgets;
import net.ledok.vectorlib.client.canvas.ItemStackNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.presentation.CanvasScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * The run details screen (UI rework step 5): a run as it goes (refreshed about once a second while open), or as it
 * ended (the end-of-run screen, the staff history).
 * <ul>
 *   <li>text is 1:1; the shell fills the window up to a maximum and drops columns below 560 GUI px (D1);</li>
 *   <li>a title bar (activity, tier, Hardcore, result, time, boss HP), the run's modifiers, and tabs:
 *       <b>Party</b> (head, class icon, level, state, HP bar, dealt, taken, healed, deaths; the rest in the row's
 *       tooltip), <b>Damage</b> (+ blocked, kills, share, boss phases), <b>Healing</b> (on others / on self, share)
 *       and <b>Rewards</b> (what each player got and how it was counted, D5: instead of the gray chat line);</li>
 *   <li>colors come from the player's Eden hub theme, or parchment ({@link RunTheme}, D3).</li>
 * </ul>
 * Staff also get each player's staff line and the damage nobody was credited for (Damage tab).
 */
public class RunDetailsScreen extends CanvasScreen {
    private static final int MAX_SHELL_W = 720;
    private static final int MAX_SHELL_H = 420;
    private static final int WIDE_MIN = 560;
    private static final float ROW_H = 26;

    enum Tab { PARTY, DAMAGE, HEALING, REWARDS, TIMELINE }

    /** An end-of-run screen waiting for the loot popup and any open screen to go. */
    private static @Nullable RunDetailsPayload pendingEnd;
    /** The tab picked last (kept between opens in a session; the end screen opens on Rewards when there are any). */
    private static Tab lastTab = Tab.PARTY;

    private RunDetailsPayload payload;
    private Tab tab;
    private RunTheme.Look look = RunTheme.current();
    private RunTheme.Palette pal = look.palette();
    private @Nullable Object builtThemeKey;
    private boolean wide = true;
    private @Nullable ScrollPanel scroll;
    private float savedScroll;
    private boolean restoreScrollNextTick;

    public RunDetailsScreen(RunDetailsPayload payload) {
        super(Component.translatable("gui.arenas_ld.run_details.title"), VectorCanvas.create(560, 340), Placement.Screen.center());
        fillWindow();
        this.payload = payload;
        this.tab = payload.over() && ownRewards(payload) ? Tab.REWARDS : lastTab;
        if (tab == Tab.REWARDS && !hasRewards(payload)) {
            tab = Tab.PARTY; // a duel has no rewards
        }
        if (tab == Tab.TIMELINE) {
            net.ledok.arenas_ld.screen.run.TimelineView.open(payload, this::timelineChanged);
        }
    }

    /** Shows the payload in the open screen, opens one for an opening payload, or queues the end screen. */
    public static void onPayload(RunDetailsPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (payload.end()) {
            pendingEnd = payload;
        } else if (mc.screen instanceof RunDetailsScreen screen) {
            screen.update(payload);
        } else if (payload.opening()) {
            mc.setScreen(new RunDetailsScreen(payload));
        }
    }

    /** Client tick: the end screen opens once the loot popup is gone and nothing else is open. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (pendingEnd != null && mc.screen == null && mc.player != null && !LootRevealHud.isActive()) {
            RunDetailsPayload payload = pendingEnd;
            pendingEnd = null;
            mc.setScreen(new RunDetailsScreen(payload));
        }
    }

    public static void clear() {
        pendingEnd = null;
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        build();
    }

    @Override
    public void tick() {
        super.tick();
        if (tab == Tab.TIMELINE) {
            net.ledok.arenas_ld.screen.run.TimelineView.tick(!payload.over());
        }
        if (!Objects.equals(builtThemeKey, RunTheme.key())) {
            build(); // the player picked another hub theme
        }
        if (restoreScrollNextTick) {
            restoreScrollNextTick = false;
            restoreScroll();
        }
    }

    @Override
    public void removed() {
        net.ledok.arenas_ld.screen.run.TimelineView.clear();
        ArenasNetwork.sendToServer(RunDetailsClosedPayload.INSTANCE);
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB && input.focusNext(hasShiftDown())) {
            ArenasUi.revealFocus(input);
            return true;
        }
        if (scroll != null && (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP
            || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN)) {
            float page = Math.max(12, scroll.bounds().height() - 12);
            scroll.scrollTo(scroll.scrollY() + (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP ? -page : page));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** New Timeline events (or another player or filter picked): rebuilt while that tab shows. */
    private void timelineChanged() {
        if (tab == Tab.TIMELINE && Minecraft.getInstance().screen == this) {
            build();
        }
    }

    private void update(RunDetailsPayload p) {
        this.payload = p;
        if (p.opening()) {
            net.ledok.arenas_ld.screen.run.TimelineView.clear();
            if (tab == Tab.TIMELINE) {
                net.ledok.arenas_ld.screen.run.TimelineView.open(p, this::timelineChanged);
            }
            savedScroll = 0;
            scroll = null;
        }
        build();
    }

    // ── build ────────────────────────────────────────────────────────────────

    private void build() {
        if (scroll != null) {
            savedScroll = scroll.scrollY();
        }
        var focused = input.focusedNode();
        String focusedId = focused == null ? null : focused.id();
        look = RunTheme.current();
        pal = look.palette();
        builtThemeKey = RunTheme.key();
        canvas.theme(look.theme());
        canvas.clear();
        scroll = null;

        float cw = canvas.width();
        float ch = canvas.height();
        int shellW = (int) Math.max(1, Math.min(MAX_SHELL_W, cw - 12));
        int shellH = (int) Math.max(1, Math.min(MAX_SHELL_H, ch - 12));
        wide = shellW - 16 >= WIDE_MIN;

        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(shellW), Sizing.fixed(shellH));
        shell.background(look.panel());
        shell.padding(Insets.of(8));
        shell.item(titleBar());
        Flex mods = modifiersLine();
        if (mods != null) {
            shell.item(mods);
        }
        shell.item(tabs(shellW - 16));
        Flex body = Flex.column();
        body.sizing(Sizing.fill(), Sizing.expand());
        body.backgroundFill(pal.panel());
        shell.item(body);
        Flex content = Flex.column().gap(3).padding(Insets.of(6, 10, 8, 8));
        content.sizing(Sizing.fill(), Sizing.content());
        switch (tab) {
            case PARTY -> party(content);
            case DAMAGE -> damage(content);
            case HEALING -> healing(content);
            case REWARDS -> rewards(content);
            case TIMELINE -> net.ledok.arenas_ld.screen.run.TimelineView.build(content, payload, pal, shellW - 42);
        }
        scroll = new ScrollPanel(100, 100, content);
        scroll.sizing(Sizing.fill(), Sizing.expand());
        scroll.barWidth(6);
        scroll.wheelStep(18);
        body.item(scroll);

        root.layoutIn(cw, ch);
        restoreScroll();
        restoreScrollNextTick = true;
        if (focusedId != null) {
            canvas.focus(canvas.find(focusedId));
        }
    }

    private void restoreScroll() {
        if (scroll != null && savedScroll > 0) {
            scroll.scrollTo(savedScroll);
        }
    }

    private Flex titleBar() {
        RunDetailsPayload p = payload;
        Flex header = Flex.column().padding(Insets.of(2, 5, 4, 6));
        header.sizing(Sizing.fill(), Sizing.content());
        header.backgroundFill(pal.panel2(), pal.hairline(), 1);
        Flex bar = header.item(Flex.row().gap(6).alignItems(Align.CENTER));
        bar.sizing(Sizing.fill(), Sizing.fixed(22));
        MutableComponent title = p.activity().copy();
        if (!p.tier().getString().isEmpty()) {
            title.append(" — ").append(p.tier()); // a duel has none
        }
        bar.item(RunWidgets.grow(title, pal.ink()));
        if (p.hardcore()) {
            bar.item(RunWidgets.pill(Component.translatable("hud.arenas_ld.hardcore"), pal.danger()));
        }
        if (!p.result().getString().isEmpty()) {
            bar.item(RunWidgets.pill(p.result(), resultColor()));
        } else if (p.over()) {
            bar.item(RunWidgets.pill(Component.translatable("gui.arenas_ld.run_details.over"), pal.inkDim()));
        }
        MutableComponent meta = Component.empty();
        if (!p.phase().getString().isEmpty() && wide) {
            meta.append(p.phase()).append("  ·  ");
        }
        if (p.totalTicks() > 0 && !p.over()) {
            meta.append(Component.translatable("gui.arenas_ld.run_details.time_left", clock(p.remainingTicks() / 20)));
        } else {
            meta.append(Component.translatable(p.over() ? "gui.arenas_ld.run_details.duration"
                : "gui.arenas_ld.run_details.elapsed", clock(p.elapsedSeconds())));
        }
        if (p.bossHp() != RunHudPayload.NO_BOSS_HP) {
            meta.append("  ·  ").append(Component.translatable("gui.arenas_ld.run_details.boss_hp", p.bossHp()));
        }
        Button close = ArenasUi.closeButton(this::onClose);
        close.style(RunWidgets.flatStyle(pal));
        close.id("run-close");
        bar.item(close);
        Flex timing = header.item(Flex.row());
        timing.sizing(Sizing.fill(), Sizing.content());
        timing.item(RunWidgets.grow(meta, pal.inkMid()));
        return header;
    }

    private int resultColor() {
        String result = payload.result().getString();
        return payload.rows().stream().anyMatch(r -> r.state() == RunDetailsPayload.State.WON) ? pal.good()
            : result.isEmpty() ? pal.inkDim() : pal.danger();
    }

    private @Nullable Flex modifiersLine() {
        if (payload.modifiers().isEmpty()) {
            return null;
        }
        MutableComponent line = Component.translatable("gui.arenas_ld.run_details.modifiers");
        for (int i = 0; i < payload.modifiers().size(); i++) {
            line.append(i == 0 ? Component.literal(" ") : Component.literal(", ")).append(payload.modifiers().get(i));
        }
        Flex row = Flex.row().padding(Insets.of(4, 2, 0, 2));
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(RunWidgets.grow(line, pal.accent()));
        return row;
    }

    private Flex tabs(float availableWidth) {
        Flex tabs = Flex.column().gap(2).padding(Insets.of(4, 0, 0, 0));
        tabs.sizing(Sizing.fill(), Sizing.content());
        Flex row = null;
        float used = 0;
        for (Tab target : Tab.values()) {
            if (target == Tab.REWARDS && !hasRewards(payload)) {
                continue;
            }
            RunWidgets.Tab button = tabButton(target, target.name().toLowerCase(java.util.Locale.ROOT));
            float width = button.bounds().width();
            if (row == null || used + 2 + width > availableWidth) {
                row = tabs.item(Flex.row().gap(2));
                row.sizing(Sizing.fill(), Sizing.content());
                used = 0;
            }
            row.item(button);
            used += (used == 0 ? 0 : 2) + width;
        }
        return tabs;
    }

    private RunWidgets.Tab tabButton(Tab target, String key) {
        RunWidgets.Tab button = new RunWidgets.Tab(pal, Component.translatable("gui.arenas_ld.run_details.tab." + key),
            () -> tab == target, () -> {
                tab = target;
                lastTab = target;
                savedScroll = 0;
                scroll = null;
                if (target == Tab.TIMELINE) {
                    net.ledok.arenas_ld.screen.run.TimelineView.open(payload, this::timelineChanged);
                }
                build();
            });
        button.id("run-tab-" + key);
        return button;
    }

    // ── Party ────────────────────────────────────────────────────────────────

    private void party(Flex col) {
        if (!payload.highlights().isEmpty()) {
            col.item(wrapped(join(payload.highlights()), pal.inkDim()));
        }
        float hpW = payload.over() ? 0 : wide ? 110 : 76; // a finished run has no HP to show
        col.item(header(new String[]{"hp", "dealt", "taken", "healed", "deaths"},
            wide ? new float[]{hpW, 64, 56, 56, 44} : new float[]{hpW, 56, 0, 0, 40}));
        for (RunDetailsPayload.Row r : payload.rows()) {
            Flex row = playerRow(r);
            if (hpW > 0) {
                row.item(hpCell(r, hpW));
            }
            row.item(cell(number(r.dealt()), 64, pal.ink(), wide ? 64 : 56));
            if (wide) {
                row.item(cell(number(r.taken()), 56, pal.inkMid(), 56));
                row.item(cell(number(r.healed()), 56, pal.inkMid(), 56));
            }
            row.item(cell(Integer.toString(r.deaths()), 44, r.deaths() > 0 ? pal.warn() : pal.inkMid(), wide ? 44 : 40));
            col.item(row);
        }
    }

    private Flex hpCell(RunDetailsPayload.Row r, float width) {
        Flex box = Flex.row().gap(4).alignItems(Align.CENTER);
        box.sizing(Sizing.fixed(width), Sizing.content());
        if (r.hp() < 0 || r.maxHp() <= 0) {
            String text = r.respawnSeconds() >= 0 ? Component.translatable("gui.arenas_ld.run_details.respawn_in",
                r.respawnSeconds()).getString() : "—";
            box.item(RunWidgets.text(Component.literal(text), r.respawnSeconds() >= 0 ? pal.warn() : pal.inkDim()));
            return box;
        }
        float frac = Math.max(0, Math.min(1, r.hp() / r.maxHp()));
        float barW = width - 30;
        box.item(bar(barW, frac, frac < 0.35f ? pal.danger() : pal.barFill()));
        box.item(RunWidgets.text(Component.literal(Integer.toString(Math.round(r.hp()))), frac < 0.35f ? pal.danger() : pal.inkMid()));
        return box;
    }

    // ── Damage ───────────────────────────────────────────────────────────────

    private void damage(Flex col) {
        float top = 0;
        for (RunDetailsPayload.Row r : payload.rows()) {
            top = Math.max(top, r.dealt());
        }
        float total = 0;
        for (RunDetailsPayload.Row r : payload.rows()) {
            total += r.dealt();
        }
        col.item(header(new String[]{"share", "dealt", "taken", "blocked", "kills", "deaths"},
            wide ? new float[]{96, 64, 56, 56, 40, 44} : new float[]{0, 56, 0, 0, 36, 40}));
        for (RunDetailsPayload.Row r : payload.rows()) {
            Flex row = playerRow(r);
            if (wide) {
                row.item(shareCell(r.dealt(), top, total, 96));
            }
            row.item(cell(number(r.dealt()), 64, pal.ink(), wide ? 64 : 56));
            if (wide) {
                row.item(cell(number(r.taken()), 56, pal.inkMid(), 56));
                row.item(cell(number(r.blocked()), 56, pal.inkMid(), 56));
            }
            row.item(cell(Integer.toString(r.kills()), 40, pal.inkMid(), wide ? 40 : 36));
            row.item(cell(Integer.toString(r.deaths()), 44, r.deaths() > 0 ? pal.warn() : pal.inkMid(), wide ? 44 : 40));
            col.item(row);
            if (!r.staffLine().getString().isEmpty()) {
                col.item(wrapped(r.staffLine(), pal.inkDim()));
            }
        }
        if (!payload.phases().isEmpty()) {
            col.item(RunWidgets.spacer(4));
            col.item(RunWidgets.section(pal, Component.translatable("gui.arenas_ld.run_details.phases"), null, null));
            for (Component phase : payload.phases()) {
                col.item(wrapped(phase, pal.info()));
            }
        }
        if (payload.unattributed() >= 0) {
            col.item(RunWidgets.spacer(4));
            col.item(wrapped(Component.translatable("gui.arenas_ld.run_details.unattributed", number(payload.unattributed())), pal.info()));
        }
    }

    // ── Healing ──────────────────────────────────────────────────────────────

    private void healing(Flex col) {
        float top = 0;
        float total = 0;
        for (RunDetailsPayload.Row r : payload.rows()) {
            top = Math.max(top, r.healed());
            total += r.healed();
        }
        col.item(header(new String[]{"share", "healed", "healed_others", "healed_self"},
            wide ? new float[]{96, 64, 72, 64} : new float[]{0, 56, 64, 0}));
        for (RunDetailsPayload.Row r : payload.rows()) {
            Flex row = playerRow(r);
            if (wide) {
                row.item(shareCell(r.healed(), top, total, 96));
            }
            row.item(cell(number(r.healed()), 64, pal.ink(), wide ? 64 : 56));
            row.item(cell(number(r.healedOthers()), 72, r.healedOthers() > 0 ? pal.good() : pal.inkMid(), wide ? 72 : 64));
            if (wide) {
                row.item(cell(number(Math.max(0, r.healed() - r.healedOthers())), 64, pal.inkMid(), 64));
            }
            col.item(row);
        }
    }

    // ── Rewards ──────────────────────────────────────────────────────────────

    private void rewards(Flex col) {
        if (!payload.rewards().getString().isEmpty()) {
            col.item(RunWidgets.section(pal, Component.translatable("gui.arenas_ld.run_details.rewards"), null, null));
            col.item(wrapped(payload.rewards(), pal.good()));
            col.item(RunWidgets.spacer(4));
        }
        List<RunDetailsPayload.Row> rows = new ArrayList<>(payload.rows());
        UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        rows.sort((a, b) -> Boolean.compare(!a.uuid().equals(self), !b.uuid().equals(self))); // own first
        boolean any = false;
        for (RunDetailsPayload.Row r : rows) {
            if (r.rewards().isEmpty()) {
                continue;
            }
            any = true;
            boolean own = r.uuid().equals(self);
            Flex card = Flex.column().gap(3).padding(Insets.of(5, 6, 5, 6));
            card.sizing(Sizing.fill(), Sizing.content());
            card.backgroundFill(pal.row(), own ? pal.accent() : pal.hairline(), own ? 2 : 1);
            Flex head = Flex.row().gap(5).alignItems(Align.CENTER);
            head.sizing(Sizing.fill(), Sizing.content());
            head.item(RunWidgets.head(r.uuid(), 16));
            head.item(RunWidgets.text(Component.literal(r.name()), pal.ink()));
            card.item(head);
            // What they got, right under the name (4.36.1: on the far right it was easy to miss).
            Component summary = r.rewards().get(0);
            card.item(wrapped(summary.getString().isEmpty()
                ? Component.translatable("gui.arenas_ld.run_details.got.nothing") : summary,
                summary.getString().isEmpty() ? pal.inkDim() : pal.good()));
            if (r.rewards().size() > 1) {
                card.item(RunWidgets.text(Component.translatable("gui.arenas_ld.run_details.counted"), pal.inkDim()));
                for (int i = 1; i < r.rewards().size(); i++) {
                    card.item(wrapped(r.rewards().get(i), pal.inkMid()));
                }
            }
            col.item(card);
        }
        if (!any) {
            col.item(wrapped(Component.translatable(payload.over()
                ? "gui.arenas_ld.run_details.no_rewards" : "gui.arenas_ld.run_details.rewards_after_win"), pal.inkDim()));
        }
    }

    // ── rows and cells ───────────────────────────────────────────────────────

    /** A column header row over the player column; widths of 0 are columns left out at this width. */
    private Flex header(String[] keys, float[] widths) {
        Flex row = Flex.row().alignItems(Align.CENTER).padding(Insets.of(0, 6, 0, 6));
        row.sizing(Sizing.fill(), Sizing.content());
        Flex player = Flex.row();
        player.sizing(Sizing.expand(), Sizing.content());
        player.item(RunWidgets.text(col("player"), pal.inkDim()));
        row.item(player);
        for (int i = 0; i < keys.length; i++) {
            if (widths[i] > 0) {
                boolean bar = keys[i].equals("hp") || keys[i].equals("share"); // bars start at the left
                Flex c = cell(col(keys[i]).getString(), widths[i], pal.inkDim(), widths[i]);
                if (bar) {
                    c.justify(Justify.START);
                }
                row.item(c);
            }
        }
        return row;
    }

    private static Component col(String key) {
        return Component.translatable("gui.arenas_ld.run_details.col." + key);
    }

    /** A player's row: head, class icon, name, state word and level on the left (expands); the caller adds cells. */
    private Flex playerRow(RunDetailsPayload.Row r) {
        UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        boolean own = r.uuid().equals(self);
        Flex row = Flex.row().gap(0).alignItems(Align.CENTER).padding(Insets.of(0, 6, 0, 5));
        row.sizing(Sizing.fill(), Sizing.fixed(ROW_H));
        row.backgroundFill(pal.row(), own ? pal.accent() : pal.hairline(), own ? 2 : 1);
        row.tooltip(rowTooltip(r).toArray(Component[]::new));

        Flex who = Flex.row().gap(4).alignItems(Align.CENTER);
        who.sizing(Sizing.expand(), Sizing.content());
        who.item(RunWidgets.head(r.uuid(), 16));
        Flex icon = classIcon(r);
        if (icon != null) {
            who.item(icon);
        }
        MutableComponent name = Component.literal(r.name()).withStyle(Style.EMPTY.withBold(own));
        MutableComponent line = name.copy().append(Component.literal("  "))
            .append(stateWord(r).copy().withColor(stateColor(r) & 0xFFFFFF));
        if (r.level() >= 0) {
            line.append(Component.literal("  ")).append(Component.translatable("gui.arenas_ld.run_details.level", r.level())
                .withColor(pal.inkDim() & 0xFFFFFF));
        }
        who.item(RunWidgets.grow(line, pal.ink()));
        row.item(who);
        return row;
    }

    private List<Component> rowTooltip(RunDetailsPayload.Row r) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(r.name()));
        Component cls = className(r);
        if (cls != null || r.level() >= 0) {
            MutableComponent l = Component.empty();
            if (cls != null) {
                l.append(cls);
            }
            if (r.level() >= 0) {
                l.append(cls != null ? "  " : "").append(Component.translatable("gui.arenas_ld.run_details.level", r.level()));
            }
            lines.add(l);
        }
        lines.add(stateWord(r));
        if (r.respawnSeconds() >= 0) {
            lines.add(Component.translatable("gui.arenas_ld.run_details.respawn_in", r.respawnSeconds()));
        }
        lines.add(stat("dealt", number(r.dealt())));
        lines.add(stat("taken", number(r.taken())));
        lines.add(stat("blocked", number(r.blocked())));
        lines.add(stat("healed", number(r.healed())));
        lines.add(stat("kills", Integer.toString(r.kills())));
        lines.add(stat("deaths", Integer.toString(r.deaths())));
        return lines;
    }

    private static Component stat(String key, String value) {
        return col(key).copy().append(": " + value);
    }

    private Component stateWord(RunDetailsPayload.Row r) {
        if (payload.over() && r.state() == RunDetailsPayload.State.ALIVE) {
            return Component.literal("—");
        }
        return Component.translatable("gui.arenas_ld.run_details.state." + r.state().name().toLowerCase(Locale.ROOT));
    }

    private int stateColor(RunDetailsPayload.Row r) {
        return switch (r.state()) {
            case ALIVE -> r.hp() >= 0 && r.maxHp() > 0 && r.hp() / r.maxHp() < 0.35f ? pal.warn() : pal.good();
            case DOWNED, FORFEITED -> pal.warn();
            case OUT, LOST -> pal.danger();
            case OFFLINE -> pal.inkDim();
            case WON -> pal.accent();
        };
    }

    /**
     * The class icon: the player's own spell book stack (4.36.2: its components pick the class look, one item for every
     * class), else the book item by id; nothing for a skill-tree class or without a class (the name is in the tooltip).
     */
    private @Nullable Flex classIcon(RunDetailsPayload.Row r) {
        ItemStack stack = classItem(r);
        if (stack.isEmpty()) {
            return null;
        }
        Flex box = Flex.row();
        box.sizing(Sizing.fixed(14), Sizing.fixed(14));
        box.item(ItemStackNode.of(stack, 0, 0, 14).decorations(false).stackTooltip(false));
        Component name = className(r);
        if (name != null) {
            box.tooltip(name);
        }
        return box;
    }

    private static ItemStack classItem(RunDetailsPayload.Row r) {
        if (!r.classIcon().isEmpty()) {
            return r.classIcon();
        }
        String playerClass = r.playerClass();
        if (playerClass == null || playerClass.isEmpty() || playerClass.startsWith("skills:")) {
            return ItemStack.EMPTY; // a skill-tree class has no item of its own
        }
        ResourceLocation id = ResourceLocation.tryParse(playerClass);
        Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null || item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** "Arcane Spell Book" (the book's own name), "Rogue" (a skill-tree branch), or null. */
    private static @Nullable Component className(RunDetailsPayload.Row r) {
        String playerClass = r.playerClass();
        if (playerClass != null && playerClass.startsWith("skills:")) {
            String branch = playerClass.substring("skills:".length());
            branch = branch.substring(branch.lastIndexOf(':') + 1).replace('_', ' ');
            return Component.literal(branch.isEmpty() ? branch : Character.toUpperCase(branch.charAt(0)) + branch.substring(1));
        }
        ItemStack stack = classItem(r);
        return stack.isEmpty() ? null : stack.getHoverName();
    }

    private Flex cell(String text, float labelWidth, int color, float width) {
        Flex box = Flex.row().justify(Justify.END).padding(Insets.of(0, 4, 0, 0));
        box.sizing(Sizing.fixed(width), Sizing.content());
        Component c = Component.literal(text);
        box.item(RunWidgets.label(Math.min(width - 4, RunWidgets.width(c) + 2), c, color));
        return box;
    }

    private Flex shareCell(float value, float top, float total, float width) {
        Flex box = Flex.row().gap(4).alignItems(Align.CENTER).padding(Insets.of(0, 0, 0, 6));
        box.sizing(Sizing.fixed(width), Sizing.content());
        box.item(bar(width - 40, top <= 0 ? 0 : value / top, pal.accent()));
        int percent = total <= 0 ? 0 : Math.round(value * 100 / total);
        box.item(RunWidgets.text(Component.literal(percent + "%"), pal.inkMid()));
        return box;
    }

    private Flex bar(float width, float frac, int fill) {
        Flex track = Flex.row();
        track.sizing(Sizing.fixed(width), Sizing.fixed(5));
        track.backgroundFill(pal.barTrack());
        if (frac > 0) {
            Flex filled = Flex.row();
            filled.sizing(Sizing.fixed(Math.max(1, width * Math.min(1, frac))), Sizing.fixed(5));
            filled.backgroundFill(fill);
            track.item(filled);
        }
        return track;
    }

    private Flex wrapped(Component text, int color) {
        Flex row = Flex.row();
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(RunWidgets.grow(text, color));
        return row;
    }

    /** A finished run that paid the viewer: its details open on Rewards. */
    /** A rewards line or anyone's reward breakdown (a duel has neither; 4.42.0). */
    private static boolean hasRewards(RunDetailsPayload p) {
        return !p.rewards().getString().isEmpty() || p.rows().stream().anyMatch(r -> !r.rewards().isEmpty());
    }

    private static boolean ownRewards(RunDetailsPayload p) {
        UUID self = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getUUID();
        return p.rows().stream().anyMatch(r -> r.uuid().equals(self) && !r.rewards().isEmpty());
    }

    private static Component join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            out.append(i == 0 ? Component.empty() : Component.literal("  ·  ")).append(parts.get(i));
        }
        return out;
    }

    private static String number(float value) {
        if (value >= 10_000) {
            return String.format(Locale.ROOT, "%.1fk", value / 1000);
        }
        if (value >= 100 || value == Math.floor(value)) {
            return String.format(Locale.ROOT, "%,.0f", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String clock(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
