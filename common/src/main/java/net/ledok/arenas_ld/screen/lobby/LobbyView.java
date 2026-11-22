package net.ledok.arenas_ld.screen.lobby;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.rewards.RunModifierOption;
import net.ledok.arenas_ld.screen.InstancePills;
import net.ledok.arenas_ld.screen.RelativeTime;
import net.ledok.arenas_ld.screen.RentalPick;
import net.ledok.arenas_ld.screen.RunTheme;
import net.ledok.arenas_ld.screen.run.RunWidgets;
import net.ledok.vectorlib.client.canvas.CanvasGroup;
import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.ledok.vectorlib.client.canvas.ItemStackNode;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.canvas.widget.UiSounds;
import net.ledok.vectorlib.client.canvas.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The lobby screen of every dungeon, raid and arena controller (UI rework steps 1–2, LeDok's D1/D2/D3/D6):
 * <ul>
 *   <li>text is always 1:1; the shell fills the window up to a maximum, and below 560 GUI px of width it reflows to
 *       one column (Party / Settings sub-tabs) instead of shrinking;</li>
 *   <li>a fixed title bar, a tab row, the content (lists scroll, nothing is capped), and a fixed action bar with the
 *       status line, Leave, Ready and Start;</li>
 *   <li>"My lobby" is party (member cards, invite) | settings (difficulty, visibility, run modifiers). The owner can
 *       change the settings any time; everyone else sees them as summaries;</li>
 *   <li>colors and widgets come from the player's Eden hub theme ({@link RunTheme}).</li>
 * </ul>
 * One instance per open screen; the screen calls {@link #build} on open/resize, {@link #dataChanged} on a snapshot,
 * {@link #tick} every client tick and {@link #clickedAt} after a mouse click.
 */
public final class LobbyView {
    private static final int MAX_SHELL_W = 720;
    private static final int MAX_SHELL_H = 440;
    private static final int TWO_COLUMNS_MIN = 560;
    private static final int PARTY_COLUMN_W = 272;

    /** 4.40.3: no public lobby list (LeDok: the Eden hub lists the open lobbies). */
    enum Tab { MY_LOBBY, LEADERBOARD, INVITES }

    enum Sub { PARTY, SETTINGS }

    private final LobbyHost host;
    /** Where it builds: its own screen's canvas, or a group a hub placed (hosted, 4.35.0). */
    private final CanvasGroup target;
    private final boolean hosted;
    private float hostWidth = 560;
    private float hostHeight = 300;
    private float builtWidth = 560;

    private Tab tab = Tab.MY_LOBBY;
    private boolean leaderboardAsked;
    /** Set by {@link #openOnLeaderboard}: the own lobby's snapshot doesn't switch the tab until the player does. */
    private boolean stayOnTab;
    private Sub sub = Sub.PARTY;
    private DifficultyTier leaderboardTier = DifficultyTier.NORMAL;
    private @Nullable String footerError;
    private boolean hadOwnLobby;
    private boolean opened;

    // per build
    private RunTheme.Look look = RunTheme.current();
    private RunTheme.Palette p = look.palette();
    private @Nullable Object builtThemeKey;
    private boolean wide = true;
    private final Map<String, ScrollPanel> scrolls = new HashMap<>();
    private final Map<String, Float> savedScroll = new HashMap<>();
    private boolean restoreScrollNextTick;
    private final List<Countdown> countdowns = new ArrayList<>();
    private final InstancePills instancePills = new InstancePills();
    private long lastSecond = -1;

    private record Countdown(Label label, long expiresAtTick) {
    }

    public LobbyView(LobbyHost host, VectorCanvas canvas) {
        this.host = host;
        this.target = canvas;
        this.hosted = false;
    }

    /** A view inside a hub's canvas ({@code ArenasClientApi.HostedLobby}): no title bar, the hub's theme and panel. */
    public LobbyView(LobbyHost host, CanvasGroup target) {
        this.host = host;
        this.target = target;
        this.hosted = true;
    }

    /** The hosted view's size (canvas units). */
    public void hostSize(float width, float height) {
        this.hostWidth = Math.max(200, width);
        this.hostHeight = Math.max(160, height);
    }

    // ── lifecycle ────────────────────────────────────────────────────────────

    /** Opens on "My lobby" when the player is in one. */
    public void open() {
        if (!opened) {
            opened = true;
            hadOwnLobby = host.ownLobby().isPresent();
            if (hadOwnLobby) {
                tab = Tab.MY_LOBBY;
            }
        }
    }

    /**
     * Opens on the Leaderboard tab (4.43.2, {@code ArenasApi.openLeaderboard}), once: it stays there until the player
     * picks a tab, even when the first snapshot shows them in a lobby.
     */
    public void openOnLeaderboard() {
        if (!leaderboardAsked) {
            leaderboardAsked = true;
            tab = Tab.LEADERBOARD;
            stayOnTab = true;
        }
    }

    /** A new snapshot: follow the own lobby (joined → My lobby, lost → the list), then rebuild. */
    public void dataChanged() {
        boolean has = host.ownLobby().isPresent();
        if (has && !hadOwnLobby && !stayOnTab) {
            tab = Tab.MY_LOBBY;
            sub = Sub.PARTY;
        }
        if (!has && hadOwnLobby) {
            InvitePicker.close(); // the lobby is gone: nothing left to invite to
        }
        hadOwnLobby = has;
        build();
    }

    /** Every client tick: the theme, countdowns, cooldown pills, a pending scroll restore. */
    public void tick() {
        if (!Objects.equals(RunTheme.key(), builtThemeKey)) {
            build();
            return;
        }
        if (restoreScrollNextTick) {
            restoreScrollNextTick = false;
            restoreScroll();
        }
        long second = host.serverTick() / 20L;
        if (second != lastSecond) {
            lastSecond = second;
            instancePills.refresh(host.serverTick() - host.snapshotTick());
            long now = host.serverTick();
            for (Countdown countdown : countdowns) {
                long left = Math.max(0L, countdown.expiresAtTick() - now) / 20L;
                countdown.label().text(Component.literal(clock(left)));
                countdown.label().color(left <= 15 ? p.warn() : p.inkMid());
            }
        }
    }

    /** After a click (canvas units): a click outside the invite field and its list closes the list. */
    public void clickedAt(float x, float y) {
        // 4.40.3: the invite field's list is gone (the picker is a modal); kept for the screens that call it.
    }

    /** The whole screen again, keeping the tab and scroll positions. */
    public void build() {
        captureScroll();
        look = RunTheme.current();
        p = look.palette();
        builtThemeKey = RunTheme.key();
        if (!hosted && target instanceof VectorCanvas own) {
            own.theme(look.theme());
        }
        target.clear();
        scrolls.clear();
        countdowns.clear();
        instancePills.clear();

        float cw = hosted ? hostWidth : ((VectorCanvas) target).width();
        float ch = hosted ? hostHeight : ((VectorCanvas) target).height();
        int shellW = hosted ? (int) cw : (int) Math.max(200, Math.min(MAX_SHELL_W, cw - 12));
        int shellH = hosted ? (int) ch : (int) Math.max(160, Math.min(MAX_SHELL_H, ch - 12));
        wide = shellW - (hosted ? 0 : 16) >= TWO_COLUMNS_MIN;
        builtWidth = shellW;

        Flex root = target.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(shellW), Sizing.fixed(shellH));
        if (!hosted) {
            // Hosted: the hub's panel is behind it already, and the hub's title row names the activity.
            shell.background(look.panel());
            shell.padding(Insets.of(8));
            shell.item(titleBar());
        }
        shell.item(tabRow());
        Flex body = Flex.column();
        body.sizing(Sizing.fill(), Sizing.expand());
        body.backgroundFill(p.panel());
        shell.item(body);
        switch (tab) {
            case MY_LOBBY -> myLobby(body);
            case LEADERBOARD -> body.item(scroll("leaderboard", leaderboardContent()));
            case INVITES -> body.item(scroll("invites", invitesContent()));
        }
        shell.item(actionBar());

        root.layoutIn(cw, ch);
        restoreScroll();
        restoreScrollNextTick = true;
    }

    // ── frame ────────────────────────────────────────────────────────────────

    private Flex titleBar() {
        Flex bar = Flex.row().gap(6).padding(Insets.of(0, 4, 0, 6)).alignItems(Align.CENTER);
        bar.sizing(Sizing.fill(), Sizing.fixed(24));
        bar.backgroundFill(p.panel2(), p.hairline(), 1);
        bar.item(RunWidgets.grow(host.title(), p.ink()));
        bar.item(statusPill());
        Button close = net.ledok.arenas_ld.screen.ArenasUi.closeButton(host::close);
        close.style(RunWidgets.flatStyle(p));
        bar.item(close);
        List<Component> meta = host.meta();
        if (meta.isEmpty()) {
            return bar;
        }

        Flex header = Flex.column();
        header.sizing(Sizing.fill(), Sizing.content());
        header.item(bar);
        MutableComponent line = Component.empty();
        for (Component value : meta) {
            if (!line.getSiblings().isEmpty()) line.append(" · ");
            line.append(value);
        }
        Flex detail = header.item(Flex.row().padding(Insets.of(2, 4, 0, 6)));
        detail.sizing(Sizing.fill(), Sizing.content());
        detail.item(RunWidgets.grow(line, p.inkDim()));
        return header;
    }

    private Flex statusPill() {
        if (host.ownLobby().isPresent()) {
            return RunWidgets.pill(tr("gui.arenas_ld.dungeon_controller.ui.status.in_lobby"), p.info());
        }
        if (!host.invites().isEmpty()) {
            return RunWidgets.pill(Component.translatable("gui.arenas_ld.dungeon_controller.ui.status.invites",
                host.invites().size()), p.accent());
        }
        return RunWidgets.pill(tr("gui.arenas_ld.dungeon_controller.ui.status.available"), p.good());
    }

    private Flex tabRow() {
        Flex header = Flex.column().gap(2).padding(Insets.of(4, 0, 0, 0));
        header.sizing(Sizing.fill(), Sizing.content());
        Optional<Lobby> own = host.ownLobby();
        List<RunWidgets.Tab> tabs = List.of(
            tabButton(Tab.MY_LOBBY, tr("gui.arenas_ld.lobby.tab.my_lobby").copy()
                .append(own.map(l -> count(l.members().size())).orElse(Component.empty()))),
            tabButton(Tab.LEADERBOARD, tr("gui.arenas_ld.lobby.tab.leaderboard")),
            tabButton(Tab.INVITES, tr("gui.arenas_ld.lobby.tab.invites").copy().append(count(host.invites().size())))
        );
        float available = builtWidth - (hosted ? 0 : 16);
        Flex row = null;
        float used = 0;
        for (RunWidgets.Tab button : tabs) {
            float width = button.bounds().width();
            if (row == null || used + 2 + width > available) {
                row = header.item(Flex.row().gap(2).alignItems(Align.END));
                row.sizing(Sizing.fill(), Sizing.content());
                used = 0;
            }
            row.item(button);
            used += (used == 0 ? 0 : 2) + width;
        }
        List<LobbyHost.Instance> instances = host.instances();
        if (instances != null) {
            Flex status = header.item(Flex.row());
            status.sizing(Sizing.fill(), Sizing.content());
            status.item(instancesStrip(instances));
        }
        return header;
    }

    private static Component count(int n) {
        return n > 0 ? Component.literal(" " + n) : Component.empty();
    }

    private RunWidgets.Tab tabButton(Tab target, Component label) {
        return new RunWidgets.Tab(p, label, () -> tab == target, () -> {
            tab = target;
            stayOnTab = false;
            footerError = null;
            savedScroll.clear();
            build();
        });
    }

    /** The instances: a pill each when the strip is wide, else one summary pill with the list on hover. */
    private CanvasNode instancesStrip(List<LobbyHost.Instance> instances) {
        Flex strip = Flex.row().gap(3).alignItems(Align.CENTER);
        strip.sizing(Sizing.content(), Sizing.content());
        if (instances.isEmpty()) {
            strip.item(RunWidgets.text(tr("gui.arenas_ld.raid_controller.ui.instance.no_instances"), p.inkDim()));
            return strip;
        }
        if (wide && instances.size() <= 4) {
            strip.item(RunWidgets.text(tr("gui.arenas_ld.raid_controller.ui.instance.title"), p.inkDim()));
            for (LobbyHost.Instance instance : instances) {
                strip.item(instancePills.pill(instance.status(), instance.cooldownTicks()));
            }
            return strip;
        }
        long free = instances.stream().filter(i -> i.status() == net.ledok.arenas_ld.util.InstanceStatus.FREE).count();
        Flex summary = RunWidgets.pill(Component.translatable("gui.arenas_ld.lobby.instances_free", free, instances.size()),
            free > 0 ? p.good() : p.warn());
        summary.tooltip(() -> {
            List<Component> lines = new ArrayList<>();
            lines.add(tr("gui.arenas_ld.raid_controller.ui.instance.title"));
            long since = host.serverTick() - host.snapshotTick();
            for (int i = 0; i < instances.size(); i++) {
                LobbyHost.Instance instance = instances.get(i);
                long left = instance.cooldownTicks() - Math.max(0, since);
                lines.add(Component.literal("#" + (i + 1) + " ")
                    .append(InstancePills.statusText(instance.status(), left)));
            }
            return lines;
        });
        strip.item(summary);
        return strip;
    }

    private Flex actionBar() {
        Flex bar = Flex.row().gap(5).padding(Insets.of(0, 6, 0, 6)).alignItems(Align.CENTER);
        bar.sizing(Sizing.fill(), Sizing.fixed(30));
        bar.backgroundFill(p.panel2(), p.hairline(), 1);
        Optional<Lobby> own = host.ownLobby();
        Component status;
        int statusColor;
        if (footerError != null) {
            status = Component.literal(footerError);
            statusColor = p.danger();
        } else if (own.isPresent()) {
            status = statusLine(own.get());
            statusColor = p.inkMid();
        } else {
            status = tr("gui.arenas_ld.lobby.status.none");
            statusColor = p.inkDim();
        }
        Flex footer = bar;
        if (builtWidth < 365) {
            footer = Flex.column();
            footer.sizing(Sizing.fill(), Sizing.content());
            Flex message = footer.item(Flex.row().padding(Insets.of(3, 6, 0, 6)));
            message.sizing(Sizing.fill(), Sizing.content());
            message.item(RunWidgets.grow(status, statusColor));
            footer.item(bar);
        } else {
            bar.item(RunWidgets.grow(status, statusColor));
        }

        if (own.isEmpty()) {
            if (tab != Tab.MY_LOBBY) {
                bar.item(RunWidgets.button(tr("gui.arenas_ld.lobby.tab.my_lobby"), () -> {
                    tab = Tab.MY_LOBBY;
                    build();
                }));
            }
            Button create = RunWidgets.button(tr("gui.arenas_ld.dungeon_controller.button.create"), this::create);
            create.style(RunWidgets.primaryStyle(p));
            create.size(RunWidgets.width(tr("gui.arenas_ld.dungeon_controller.button.create")) + 18, 20);
            bar.item(create);
            return footer;
        }
        Lobby lobby = own.get();
        UUID self = self();
        boolean owner = lobby.ownerUuid().equals(self);
        boolean ready = lobby.readyMembers().contains(self);

        Button leave = RunWidgets.button(tr("gui.arenas_ld.lobby.leave"), this::leave);
        leave.style(RunWidgets.flatStyle(p));
        bar.item(leave);
        bar.item(RunWidgets.button(tr(ready ? "gui.arenas_ld.dungeon_controller.button.unready"
            : "gui.arenas_ld.dungeon_controller.button.ready"), () -> {
            footerError = null;
            host.toggleReady();
        }));
        Component startLabel = tr(host.kind() == LobbyHost.Kind.RAID
            ? "gui.arenas_ld.raid_controller.button.start" : "gui.arenas_ld.dungeon_controller.button.start");
        Button start = new Button(RunWidgets.width(startLabel) + 20, 20, startLabel, this::start);
        start.style(RunWidgets.primaryStyle(p));
        boolean canStart = canStart(lobby);
        start.enabled(canStart);
        if (!canStart) {
            start.tooltip(owner ? statusLine(lobby) : tr(errorKey("only_owner")));
        }
        bar.item(start);
        return footer;
    }

    private Component statusLine(Lobby lobby) {
        int queue = host.queuePosition();
        if (queue >= 1) {
            return Component.translatable("gui.arenas_ld.lobby.status.queued", queue);
        }
        List<String> waiting = new ArrayList<>();
        for (UUID member : ordered(lobby)) {
            if (!lobby.readyMembers().contains(member)) {
                waiting.add(name(lobby, member));
            }
        }
        if (!waiting.isEmpty()) {
            return Component.translatable("gui.arenas_ld.lobby.status.waiting", String.join(", ", waiting));
        }
        if (!allOnline(lobby)) {
            return tr(errorKey("all_online_required"));
        }
        return tr(lobby.ownerUuid().equals(self()) ? "gui.arenas_ld.lobby.status.all_ready"
            : "gui.arenas_ld.lobby.status.owner_starts");
    }

    private boolean canStart(Lobby lobby) {
        return lobby.ownerUuid().equals(self()) && lobby.allReady() && allOnline(lobby)
            && (!host.startNeedsNoQueue() || host.queuePosition() < 1);
    }

    private void start() {
        footerError = null;
        Lobby lobby = host.ownLobby().orElse(null);
        if (lobby == null) {
            footerError = tr("gui.arenas_ld.dungeon_controller.no_own_lobby").getString();
        } else if (!lobby.ownerUuid().equals(self())) {
            footerError = tr(errorKey("only_owner")).getString();
        } else if (!lobby.allReady()) {
            footerError = tr(errorKey("all_ready_required")).getString();
        } else if (!allOnline(lobby)) {
            footerError = tr(errorKey("all_online_required")).getString();
        } else {
            host.start();
            return;
        }
        build();
    }

    private String errorKey(String what) {
        return (host.kind() == LobbyHost.Kind.RAID ? "gui.arenas_ld.raid_controller.error." : "gui.arenas_ld.dungeon_controller.error.") + what;
    }

    private void create() {
        footerError = null;
        host.create();
    }

    // ── scrolling ────────────────────────────────────────────────────────────

    private ScrollPanel scroll(String role, Flex content) {
        ScrollPanel panel = new ScrollPanel(100, 100, content);
        panel.sizing(Sizing.fill(), Sizing.expand());
        panel.barWidth(6);
        panel.wheelStep(18);
        scrolls.put(role, panel);
        return panel;
    }

    private Flex column() {
        Flex col = Flex.column().gap(4).padding(Insets.of(6, 10, 8, 8));
        col.sizing(Sizing.fill(), Sizing.content());
        return col;
    }

    private void captureScroll() {
        for (Map.Entry<String, ScrollPanel> entry : scrolls.entrySet()) {
            savedScroll.put(entry.getKey(), entry.getValue().scrollY());
        }
    }

    private void restoreScroll() {
        for (Map.Entry<String, ScrollPanel> entry : scrolls.entrySet()) {
            Float y = savedScroll.get(entry.getKey());
            if (y != null && y > 0) {
                entry.getValue().scrollTo(y);
            }
        }
    }

    // ── My lobby ─────────────────────────────────────────────────────────────

    private void myLobby(Flex body) {
        Optional<Lobby> own = host.ownLobby();
        if (own.isEmpty()) {
            Flex col = column();
            col.item(RunWidgets.text(tr("gui.arenas_ld.dungeon_controller.no_own_lobby"), p.inkDim()));
            body.item(scroll("my_empty", col));
            return;
        }
        Lobby lobby = own.get();
        boolean owner = lobby.ownerUuid().equals(self());
        if (host.queuePosition() >= 1) {
            body.item(queueBanner(owner));
        }
        if (wide) {
            Flex columns = Flex.row().gap(0);
            columns.sizing(Sizing.fill(), Sizing.expand());
            ScrollPanel party = scroll("party", partyContent(lobby, owner));
            party.sizing(Sizing.fixed(PARTY_COLUMN_W), Sizing.fill());
            columns.item(party);
            Flex divider = Flex.column();
            divider.sizing(Sizing.fixed(1), Sizing.fill());
            divider.backgroundFill(p.hairline());
            columns.item(divider);
            ScrollPanel settings = scroll("settings", settingsContent(lobby, owner));
            settings.sizing(Sizing.expand(), Sizing.fill());
            columns.item(settings);
            body.item(columns);
        } else {
            Flex subs = Flex.row().gap(3).padding(Insets.of(4, 8, 0, 8)).alignItems(Align.CENTER);
            subs.sizing(Sizing.fill(), Sizing.content());
            subs.item(subTab(Sub.PARTY, Component.translatable("gui.arenas_ld.lobby.sub.party",
                lobby.members().size(), host.maxPartySize())));
            subs.item(subTab(Sub.SETTINGS, tr("gui.arenas_ld.lobby.sub.settings")));
            subs.spacer();
            if (host.hasTiers()) {
                subs.item(RunWidgets.pill(tierName(lobby.selectedTier()), tierColor(lobby.selectedTier())));
            }
            if (lobby.hardcoreEnabled()) {
                subs.item(RunWidgets.pill(tr("gui.arenas_ld.lobby.hardcore_short"), p.danger()));
            }
            body.item(subs);
            body.item(sub == Sub.PARTY ? scroll("party", partyContent(lobby, owner))
                : scroll("settings", settingsContent(lobby, owner)));
        }
    }

    private RunWidgets.Tab subTab(Sub target, Component label) {
        return new RunWidgets.Tab(p, label, () -> sub == target, () -> {
            sub = target;
            build();
        });
    }

    private Flex queueBanner(boolean owner) {
        Flex banner = Flex.row().gap(6).padding(Insets.of(3, 8, 3, 8)).alignItems(Align.CENTER);
        banner.sizing(Sizing.fill(), Sizing.fixed(24));
        int w = p.warn();
        banner.backgroundFill((w & 0x00FFFFFF) | 0x22000000, (w & 0x00FFFFFF) | 0x66000000, 1);
        banner.item(RunWidgets.pill(Component.translatable("gui.arenas_ld.dungeon_controller.ui.queue.in_line",
            host.queuePosition()), w));
        if (host.etaSeconds() > 0) {
            banner.item(RunWidgets.text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.queue.eta",
                clock(host.etaSeconds())), p.inkMid()));
        }
        banner.spacer();
        if (owner && host.queueStartButton()) {
            banner.item(RunWidgets.button(tr("gui.arenas_ld.raid_controller.button.start"), this::start));
        }
        if (owner && host.kind() != LobbyHost.Kind.DUNGEON) {
            banner.item(RunWidgets.button(tr("gui.arenas_ld.raid_controller.button.leave_queue"), this::leave));
        }
        return banner;
    }

    /**
     * Leaves the lobby. In a hub (hosted, 4.43.0) the view is done then: its menu closes from the client (closed()
     * turns true) and the hub goes back to its lobby list, instead of showing "You're not in a lobby" here.
     */
    private void leave() {
        footerError = null;
        host.leave();
        if (hosted) {
            host.close();
        }
    }

    private Flex partyContent(Lobby lobby, boolean owner) {
        Flex col = column();
        col.item(RunWidgets.section(p, tr("gui.arenas_ld.lobby.party"),
            Component.translatable("gui.arenas_ld.lobby.party_count", lobby.members().size(), host.maxPartySize(),
                lobby.readyMembers().size()), null));
        for (UUID member : ordered(lobby)) {
            col.item(memberCard(lobby, member, owner));
        }
        if (owner && !lobby.isFull(host.maxPartySize())) {
            col.item(emptySlot());
        }
        // The party column's content width: the column less its padding (18) and the scroll bar (6).
        Flex rental = RentalPick.build(host.controllerPos(), p, (wide ? PARTY_COLUMN_W : builtWidth) - 24);
        col.item(rental);
        return col;
    }

    private Flex memberCard(Lobby lobby, UUID member, boolean viewerOwns) {
        boolean online = isOnline(member);
        boolean ready = lobby.readyMembers().contains(member);
        boolean isOwner = member.equals(lobby.ownerUuid());
        String name = name(lobby, member);

        Flex card = Flex.row().gap(5).padding(Insets.of(0, 5, 0, 5)).alignItems(Align.CENTER);
        card.sizing(Sizing.fill(), Sizing.fixed(26));
        card.backgroundFill(member.equals(self()) ? p.rowAlt() : p.row(), (p.hairline() & 0x00FFFFFF) | 0x66000000, 1);
        card.item(RunWidgets.head(member, 16));

        Flex text = Flex.column().gap(1);
        text.sizing(Sizing.expand(), Sizing.content());
        MutableComponent first = Component.literal(name);
        if (isOwner) {
            first.append(Component.literal(" ★"));
        }
        text.item(RunWidgets.grow(first, online ? p.ink() : p.inkDim()));
        List<String> parts = new ArrayList<>();
        if (isOwner) {
            parts.add(tr("gui.arenas_ld.lobby.owner").getString());
        }
        String rental = RentalPick.pickOf(host.controllerPos(), name);
        if (rental != null) {
            parts.add(Component.translatable("gui.arenas_ld.rental.badge", rental).getString());
        }
        if (!online) {
            parts.add(tr("gui.arenas_ld.lobby.offline").getString());
        }
        if (!parts.isEmpty()) {
            text.item(RunWidgets.grow(Component.literal(String.join(" · ", parts)),
                !online ? p.danger() : rental != null ? p.warn() : p.inkDim()));
        }
        card.item(text);

        Flex square = Flex.row().justify(Justify.CENTER).alignItems(Align.CENTER);
        square.sizing(Sizing.fixed(12), Sizing.fixed(12));
        square.backgroundFill(ready ? p.good() : p.barTrack());
        square.item(RunWidgets.text(Component.literal(ready ? "✓" : "…"), ready ? 0xFFFFFFFF : p.inkMid()));
        square.tooltip(tr(ready ? "gui.arenas_ld.dungeon_controller.ui.member.ready" : "gui.arenas_ld.dungeon_controller.ui.member.not_ready"));
        card.item(square);

        if (viewerOwns && !isOwner) {
            Button kick = new Button(16, 16, Component.literal("✕"), () -> {
                footerError = null;
                host.kick(member);
            });
            kick.style(RunWidgets.dangerStyle(p));
            kick.tooltip(tr("gui.arenas_ld.lobby.kick"));
            card.item(kick);
        }
        return card;
    }

    private Widget emptySlot() {
        EmptySlot[] slot = new EmptySlot[1];
        slot[0] = new EmptySlot(p, tr("gui.arenas_ld.lobby.invite_slot"), () -> {
            footerError = null;
            if (slot[0].canvas() != null) {
                InvitePicker.show(slot[0].canvas(), host); // 4.40.3: a picker like the Eden hub's Friends tab
            }
        });
        EmptySlot result = slot[0];
        result.sizing(Sizing.fill(), Sizing.fixed(22));
        return result;
    }

    /** The dashed "+ Invite a player" slot. */
    private static final class EmptySlot extends Widget {
        private final RunTheme.Palette p;
        private final Component label;
        private final Runnable action;

        EmptySlot(RunTheme.Palette p, Component label, Runnable action) {
            super(100, 22);
            this.p = p;
            this.label = label;
            this.action = action;
        }

        @Override
        protected void rebuild() {
            int line = hovered || focused ? p.accent() : p.hairline();
            for (float x = 0; x < width; x += 6) {
                add(Shapes.rect(x, 0, Math.min(3, width - x), 1).fill(line));
                add(Shapes.rect(x, height - 1, Math.min(3, width - x), 1).fill(line));
            }
            add(Shapes.rect(0, 0, 1, height).fill(line));
            add(Shapes.rect(width - 1, 0, 1, height).fill(line));
            add(TextNode.of(label).at(width / 2, textY(height)).align(TextNode.Align.CENTER)
                .color(hovered || focused ? p.accent() : p.inkDim()).shadow(false));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (button != 0) return false;
            UiSounds.click();
            action.run();
            return true;
        }

        @Override
        public boolean focusable() { return true; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (key == 257 || key == 335 || key == 32) { UiSounds.click(); action.run(); return true; }
            return false;
        }
    }

    private Flex settingsContent(Lobby lobby, boolean owner) {
        Flex col = column();
        if (host.hasTiers()) {
            col.item(RunWidgets.section(p, tr("gui.arenas_ld.lobby.difficulty"), null, null));
            if (owner) {
                List<RunWidgets.Segment> segments = new ArrayList<>();
                for (DifficultyTier tier : DifficultyTier.values()) {
                    boolean enabled = host.tierEnabled(tier);
                    Component lock = enabled ? host.tierLock(tier) : null;
                    RunWidgets.Segment segment = new RunWidgets.Segment(p, tierName(tier),
                        lock != null || !enabled ? p.inkDim() : tierColor(tier),
                        () -> host.ownLobby().map(Lobby::selectedTier).orElse(null) == tier,
                        () -> host.setTier(tier));
                    segment.enabled(enabled && lock == null);
                    Component note = lock != null ? lock : enabled ? host.tierMet(tier) : tr("gui.arenas_ld.lobby.tier_off");
                    if (note != null) {
                        segment.tooltip(note);
                    }
                    segments.add(segment);
                }
                col.item(RunWidgets.segmented(segments.toArray(RunWidgets.Segment[]::new)));
            } else {
                col.item(RunWidgets.pill(tierName(lobby.selectedTier()), tierColor(lobby.selectedTier())));
            }
            Component met = host.tierMet(lobby.selectedTier());
            if (met != null && !met.getString().isEmpty()) {
                Label metLabel = RunWidgets.label(100, met, p.good());
                metLabel.sizing(Sizing.fill(), Sizing.content());
                col.item(metLabel);
            }
            col.item(RunWidgets.spacer(2));
        }

        col.item(RunWidgets.section(p, tr("gui.arenas_ld.lobby.visibility"), null, null));
        if (owner) {
            List<RunWidgets.Segment> segments = new ArrayList<>();
            for (LobbyVisibility visibility : LobbyVisibility.values()) {
                segments.add(new RunWidgets.Segment(p, visibilityName(visibility), visibilityColor(visibility),
                    () -> host.ownLobby().map(Lobby::visibility).orElse(null) == visibility,
                    () -> host.setVisibility(visibility)));
            }
            col.item(RunWidgets.segmented(segments.toArray(RunWidgets.Segment[]::new)));
        } else {
            col.item(RunWidgets.pill(visibilityName(lobby.visibility()), visibilityColor(lobby.visibility())));
        }
        col.item(RunWidgets.spacer(2));

        // 4.40.1: the controller's run settings, here because the hub-hosted view has no title bar for them.
        List<Component> settings = host.settingsLines();
        if (!settings.isEmpty()) {
            for (Component setting : settings) {
                Label line = RunWidgets.label(100, setting, p.accent());
                line.sizing(Sizing.fill(), Sizing.content());
                col.item(line);
            }
            col.item(RunWidgets.spacer(2));
        }

        List<RunModifierOption> options = host.modifierOptions();
        if (!options.isEmpty()) {
            Set<String> active = lobby.modifiers();
            long on = options.stream().filter(o -> active.contains(o.id())).count();
            col.item(RunWidgets.section(p, tr("gui.arenas_ld.run_modifiers.title"),
                Component.literal("· " + on + " / " + options.size()), null));
            boolean any = false;
            for (RunModifierOption option : options) {
                boolean isOn = active.contains(option.id());
                if (!owner && !isOn) {
                    continue;
                }
                any = true;
                ModifierRow row = new ModifierRow(p, host.controllerPos(), option, isOn, conflict(option, active, options), owner);
                row.sizing(Sizing.fill(), Sizing.fixed(20));
                col.item(row);
            }
            if (!any) {
                col.item(RunWidgets.text(tr("gui.arenas_ld.lobby.no_modifiers"), p.inkDim()));
            }
            if (!host.runModifiersLine().getString().isEmpty()) {
                Label picked = RunWidgets.label(100, host.runModifiersLine(), p.accent());
                picked.sizing(Sizing.fill(), Sizing.content());
                col.item(picked);
            }
        }
        return col;
    }

    private static @Nullable Component conflict(RunModifierOption option, Set<String> active, List<RunModifierOption> options) {
        for (String other : option.conflicts()) {
            if (!other.equals(option.id()) && active.contains(other)) {
                return options.stream().filter(o -> o.id().equals(other)).findFirst()
                    .map(RunModifierOption::name).orElse(Component.literal(other));
            }
        }
        return null;
    }

    /**
     * One run modifier, 20 tall: icon, name, reward effect on the right; on = accent bar and fill, conflict = red.
     * The description, rewards and conflict are in the tooltip. The owner clicks it to switch it.
     */
    private static final class ModifierRow extends Widget {
        private static final int CONFLICT_BG = 0xFFD42A1E;
        private final RunTheme.Palette p;
        private final net.minecraft.core.BlockPos controller;
        private final RunModifierOption option;
        private final boolean on;
        private final @Nullable Component conflict;
        private final boolean owner;
        private final ItemStack icon;

        ModifierRow(RunTheme.Palette p, net.minecraft.core.BlockPos controller, RunModifierOption option, boolean on,
                    @Nullable Component conflict, boolean owner) {
            super(200, 20);
            this.p = p;
            this.controller = controller;
            this.option = option;
            this.on = on;
            this.conflict = conflict;
            this.owner = owner;
            ResourceLocation id = ResourceLocation.tryParse(option.icon() == null ? "" : option.icon());
            this.icon = id == null || !BuiltInRegistries.ITEM.containsKey(id)
                ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
            List<Component> lines = new ArrayList<>();
            lines.add(option.name());
            if (!option.description().getString().isEmpty()) {
                lines.add(option.description().copy().withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            lines.add(option.rewards().copy().withStyle(net.minecraft.ChatFormatting.GREEN));
            if (conflict != null) {
                lines.add(Component.translatable("gui.arenas_ld.run_modifiers.conflict", conflict)
                    .withStyle(net.minecraft.ChatFormatting.RED));
            }
            if (owner) {
                lines.add(Component.translatable(on ? "gui.arenas_ld.lobby.modifier.click_off" : "gui.arenas_ld.lobby.modifier.click_on")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            tooltip(lines.toArray(Component[]::new));
        }

        @Override
        protected void rebuild() {
            boolean hover = owner && (hovered || focused);
            int fill = conflict != null ? CONFLICT_BG : on ? p.rowAlt() : hover ? p.row() : (p.row() & 0x00FFFFFF) | 0x88000000;
            add(Shapes.rect(0, 0, width, height).fill(fill).stroke(on ? p.accent() : (p.hairline() & 0x00FFFFFF) | 0x55000000, 1));
            if (on) {
                add(Shapes.rect(0, 0, 3, height).fill(p.accent()));
            }
            if (!icon.isEmpty()) {
                add(ItemStackNode.of(icon, 6, 2, 16).decorations(false));
            }
            var font = Minecraft.getInstance().font;
            // The reward is what players compare: it gets the room the name doesn't need (the name keeps ~70 px);
            // anything cut ends in "…" and the tooltip has it all.
            String reward = option.rewards().getString();
            String fullName = option.name().getString();
            int room = (int) width - 28 - 12;
            int nameNeed = Math.min(font.width(fullName), 70);
            int rewardW = Math.min(font.width(reward), Math.max(40, room - nameNeed));
            String shownReward = fit(font, reward, rewardW);
            String name = fit(font, fullName, Math.max(10, room - font.width(shownReward)));
            int ink = conflict != null ? 0xFFFFFFFF : on ? p.ink() : p.inkMid();
            add(TextNode.of(Component.literal(name)).at(26, textY(height)).color(ink).shadow(false));
            add(TextNode.of(Component.literal(shownReward)).at(width - 6, textY(height)).align(TextNode.Align.RIGHT)
                .color(conflict != null ? 0xFFFFFFFF : p.good()).shadow(false));
        }

        private static String fit(net.minecraft.client.gui.Font font, String text, int width) {
            if (font.width(text) <= width) {
                return text;
            }
            return font.plainSubstrByWidth(text, Math.max(0, width - font.width("…"))) + "…";
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!owner || button != 0) return false;
            UiSounds.click();
            ArenasNetwork.sendToServer(new net.ledok.arenas_ld.packet.SetLobbyModifierPayload(controller, option.id(), !on));
            return true;
        }

        @Override
        public boolean focusable() { return owner; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (!owner) return false;
            if (key == 257 || key == 335 || key == 32) { onMouseDown(0, 0, 0); return true; }
            return false;
        }
    }

    // ── Leaderboard ──────────────────────────────────────────────────────────

    private Flex leaderboardContent() {
        Flex col = column();
        List<LeaderboardEntry> entries = new ArrayList<>();
        boolean byWave = !host.hasTiers();
        if (byWave) {
            entries.addAll(host.leaderboard(null));
            entries.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds).reversed());
            col.item(RunWidgets.section(p, tr("gui.arenas_ld.arena.leaderboard.banner_title"), null, null));
            col.item(wrapped(tr("gui.arenas_ld.arena.leaderboard.banner_desc"), p.inkMid()));
        } else {
            List<RunWidgets.Segment> segments = new ArrayList<>();
            for (DifficultyTier tier : DifficultyTier.values()) {
                segments.add(new RunWidgets.Segment(p, tierName(tier), tierColor(tier), () -> leaderboardTier == tier, () -> {
                    leaderboardTier = tier;
                    savedScroll.clear();
                    build();
                }));
            }
            col.item(RunWidgets.segmented(segments.toArray(RunWidgets.Segment[]::new)));
            entries.addAll(host.leaderboard(leaderboardTier));
            entries.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds));
            col.item(wrapped(Component.translatable("gui.arenas_ld.dungeon_controller.ui.leaderboard.banner_desc",
                titleCase(leaderboardTier.name())), p.inkMid()));
        }
        col.item(RunWidgets.section(p, tr("gui.arenas_ld.dungeon_controller.ui.section.top_runs"),
            Component.literal("· " + entries.size()), null));
        if (entries.isEmpty()) {
            col.item(RunWidgets.text(tr("gui.arenas_ld.dungeon_controller.ui.leaderboard.no_runs"), p.inkDim()));
        }
        int accent = byWave ? p.accent() : tierColor(leaderboardTier);
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            boolean top = i == 0;
            Flex row = Flex.row().gap(6).padding(Insets.of(4, 8, 4, 8)).alignItems(Align.CENTER);
            row.sizing(Sizing.fill(), Sizing.fixed(20));
            row.backgroundFill(top ? (accent & 0x00FFFFFF) | 0x22000000 : (i % 2 == 0 ? p.row() : p.rowAlt()));
            row.item(fixed(Component.literal(top ? "★1" : "#" + (i + 1)), 30, top ? accent : p.inkMid(), TextNode.Align.LEFT));
            row.item(RunWidgets.grow(Component.literal(entry.playerName()), p.ink()));
            row.item(fixed(Component.literal(RelativeTime.ago(entry.recordedAtEpochMillis())), 44, p.inkDim(), TextNode.Align.RIGHT));
            Component score = byWave ? Component.translatable("gui.arenas_ld.arena.wave_n", entry.timeSeconds())
                : Component.literal(clock(entry.timeSeconds()));
            row.item(fixed(score, 56, top ? accent : p.inkMid(), TextNode.Align.RIGHT));
            col.item(row);
        }
        return col;
    }

    // ── Invites ──────────────────────────────────────────────────────────────

    private Flex invitesContent() {
        Flex col = column();
        List<PendingInvite> invites = host.invites();
        col.item(RunWidgets.section(p, tr("gui.arenas_ld.dungeon_controller.invites"), Component.literal("· " + invites.size()), null));
        if (invites.isEmpty()) {
            col.item(RunWidgets.text(tr("gui.arenas_ld.dungeon_controller.no_invites"), p.inkDim()));
        }
        for (PendingInvite invite : invites) {
            col.item(inviteRow(invite));
        }
        Optional<Lobby> own = host.ownLobby();
        if (own.isPresent() && own.get().ownerUuid().equals(self())) {
            UUID ownId = own.get().lobbyId();
            List<PendingJoinRequest> incoming = host.joinRequests().stream().filter(r -> r.lobbyId().equals(ownId)).toList();
            col.item(RunWidgets.spacer(6));
            col.item(RunWidgets.section(p, tr("gui.arenas_ld.dungeon_controller.join_requests"),
                Component.literal("· " + incoming.size()), null));
            if (incoming.isEmpty()) {
                col.item(RunWidgets.text(tr("gui.arenas_ld.dungeon_controller.no_join_requests"), p.inkDim()));
            }
            for (PendingJoinRequest request : incoming) {
                col.item(requestRow(request));
            }
        }
        return col;
    }

    private Flex inviteRow(PendingInvite invite) {
        Optional<Lobby> lobby = lobbyOf(invite.lobbyId());
        String ownerName = lobby.map(Lobby::ownerName).filter(n -> !n.isEmpty())
            .orElseGet(() -> !invite.ownerName().isEmpty() ? invite.ownerName() : shortId(invite.lobbyId()));
        String inviter = lobby.map(l -> l.memberNames().get(invite.inviterUuid())).filter(n -> n != null && !n.isEmpty())
            .orElse(ownerName);
        DifficultyTier tier = lobby.map(Lobby::selectedTier).orElse(invite.tier());

        Flex row = Flex.row().gap(6).padding(Insets.of(4, 6, 4, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(28));
        row.backgroundFill(p.row(), (p.hairline() & 0x00FFFFFF) | 0x66000000, 1);
        row.item(RunWidgets.head(invite.inviterUuid(), 16));
        Flex text = Flex.column().gap(1);
        text.sizing(Sizing.expand(), Sizing.content());
        text.item(RunWidgets.grow(Component.translatable("gui.arenas_ld.lobby.invite_from", inviter), p.ink()));
        text.item(RunWidgets.grow(Component.translatable("gui.arenas_ld.lobby.invite_owner", ownerName), p.inkDim()));
        row.item(text);
        if (host.hasTiers()) {
            row.item(RunWidgets.pill(tierName(tier), tierColor(tier), 64));
        }
        Label countdown = fixed(Component.literal(clock(remaining(invite.expiresAtTick()))), 36, p.inkMid(), TextNode.Align.CENTER);
        countdowns.add(new Countdown(countdown, invite.expiresAtTick()));
        row.item(countdown);
        Component lock = host.hasTiers() ? host.tierLock(tier) : null;
        Button accept = RunWidgets.button(lock != null ? tr("gui.arenas_ld.lobby.locked")
            : tr("gui.arenas_ld.dungeon_controller.button.accept"), 56, () -> {
            footerError = null;
            host.acceptInvite(invite.lobbyId());
        });
        accept.enabled(lock == null);
        if (lock != null) {
            accept.tooltip(lock);
        }
        row.item(accept);
        row.item(RunWidgets.button(tr("gui.arenas_ld.dungeon_controller.button.decline"), 56, () -> {
            footerError = null;
            host.declineInvite(invite.lobbyId());
        }));
        return row;
    }

    private Flex requestRow(PendingJoinRequest request) {
        String name = !request.requesterName().isEmpty() ? request.requesterName() : shortId(request.requesterUuid());
        Flex row = Flex.row().gap(6).padding(Insets.of(4, 6, 4, 6)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(26));
        row.backgroundFill(p.row(), (p.hairline() & 0x00FFFFFF) | 0x66000000, 1);
        row.item(RunWidgets.head(request.requesterUuid(), 16));
        row.item(RunWidgets.grow(Component.translatable("gui.arenas_ld.lobby.wants_to_join", name), p.ink()));
        if (host.hasTiers()) {
            row.item(RunWidgets.pill(tierName(request.tier()), tierColor(request.tier()), 64));
        }
        Label countdown = fixed(Component.literal(clock(remaining(request.expiresAtTick()))), 36, p.inkMid(), TextNode.Align.CENTER);
        countdowns.add(new Countdown(countdown, request.expiresAtTick()));
        row.item(countdown);
        UUID requester = request.requesterUuid();
        row.item(RunWidgets.button(tr("gui.arenas_ld.dungeon_controller.button.accept"), 56, () -> {
            footerError = null;
            host.acceptRequest(requester);
        }));
        row.item(RunWidgets.button(tr("gui.arenas_ld.dungeon_controller.button.decline"), 56, () -> {
            footerError = null;
            host.declineRequest(requester);
        }));
        return row;
    }

    private Optional<Lobby> lobbyOf(UUID lobbyId) {
        for (Lobby lobby : host.visibleLobbies()) {
            if (lobby.lobbyId().equals(lobbyId)) {
                return Optional.of(lobby);
            }
        }
        return host.ownLobby().filter(l -> l.lobbyId().equals(lobbyId));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Label fixed(Component text, float width, int color, TextNode.Align align) {
        Label label = RunWidgets.label(width, text, color);
        label.align(align);
        label.sizing(Sizing.fixed(width), Sizing.content());
        return label;
    }

    private TextNode wrapped(Component text, int color) {
        return RunWidgets.text(text, color).maxWidth(Math.max(80, (int) builtWidth - 80));
    }

    private long remaining(long expiresAtTick) {
        return Math.max(0L, expiresAtTick - host.serverTick()) / 20L;
    }

    private static Component tr(String key) {
        return Component.translatable(key);
    }

    private static UUID self() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.getUUID() : new UUID(0, 0);
    }

    private static boolean isOnline(UUID uuid) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.getPlayerInfo(uuid) != null;
    }

    private static boolean allOnline(Lobby lobby) {
        for (UUID member : lobby.members()) {
            if (!isOnline(member)) {
                return false;
            }
        }
        return true;
    }

    private static List<UUID> ordered(Lobby lobby) {
        return lobby.members().stream()
            .sorted(Comparator.comparing((UUID m) -> m.equals(lobby.ownerUuid()) ? 0 : 1)
                .thenComparing(m -> name(lobby, m).toLowerCase(Locale.ROOT)))
            .toList();
    }

    private static String name(Lobby lobby, UUID member) {
        return lobby.memberNames().getOrDefault(member, shortId(member));
    }

    private static String shortId(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private static String clock(long totalSeconds) {
        long s = Math.max(0, totalSeconds);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    private static String titleCase(String value) {
        return value.isEmpty() ? value : value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static Component tierName(DifficultyTier tier) {
        return Component.translatable("gui.arenas_ld.lobby.tier." + tier.name().toLowerCase(Locale.ROOT));
    }

    private int tierColor(DifficultyTier tier) {
        return switch (tier) {
            case EASY -> p.good();
            case NORMAL -> p.info();
            case HARD -> p.warn();
            case NIGHTMARE -> p.danger();
        };
    }

    private static Component visibilityName(LobbyVisibility visibility) {
        return Component.translatable("gui.arenas_ld.dungeon_controller.visibility." + visibility.name().toLowerCase(Locale.ROOT));
    }

    private int visibilityColor(LobbyVisibility visibility) {
        return switch (visibility) {
            case PUBLIC -> p.good();
            case FRIENDS -> p.info();
            case PRIVATE -> p.warn();
        };
    }

}
