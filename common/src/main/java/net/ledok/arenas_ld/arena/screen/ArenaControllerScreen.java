package net.ledok.arenas_ld.arena.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity.ArenaInstanceState;
import net.ledok.arenas_ld.arena.packet.ArenaLobbyActionPayload;
import net.ledok.arenas_ld.arena.packet.ArenaLobbyTargetPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetHardcorePayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetVisibilityPayload;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.screen.ArenasParchment;
import net.ledok.arenas_ld.screen.ForcedGuiScale;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.ledok.vectorlib.client.canvas.Rect;
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
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.ledok.vectorlib.client.presentation.CanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import static net.ledok.arenas_ld.screen.ArenasParchment.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasParchment.ACCENT_DARK;
import static net.ledok.arenas_ld.screen.ArenasParchment.DANGER;
import static net.ledok.arenas_ld.screen.ArenasParchment.GOOD;
import static net.ledok.arenas_ld.screen.ArenasParchment.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasParchment.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasParchment.INFO;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK_MID;
import static net.ledok.arenas_ld.screen.ArenasParchment.PANEL;
import static net.ledok.arenas_ld.screen.ArenasParchment.PANEL_2;
import static net.ledok.arenas_ld.screen.ArenasParchment.ROW_BG;
import static net.ledok.arenas_ld.screen.ArenasParchment.ROW_BG_ALT;
import static net.ledok.arenas_ld.screen.ArenasParchment.WARN;

/**
 * Player lobby view for the arena controller, styled to match the dungeon and raid controller
 * screens: tabbed (Lobbies / My Lobby / Leaderboard / Invites), instances strip, header status
 * badge, footer actions, segmented controls and invite dropdown.
 *
 * <p>Unlike the raid/dungeon screens (which apply server snapshots), the arena reads live state
 * directly from the client-synced {@link ArenaControllerBlockEntity} each frame and rebuilds when
 * the lobby state signature changes. There are no difficulty tiers; the leaderboard is keyed on the
 * deepest wave reached. Ignores the inventory key so "E" does not close the screen.
 */
public class ArenaControllerScreen extends CanvasHandledScreen<ArenaControllerScreenHandler> {
    private static final int INVITE_DROPDOWN_MAX_HEIGHT = 154;
    // Design (forced-integer-GUI-scale) shell size; keeps the shell a fixed size and the font crisp
    // regardless of window size, matching the shop/AH screens in Economy_LD.
    private static final int DESIGN_W = 560;
    private static final int DESIGN_H = 480;
    private final ForcedGuiScale guiScale = new ForcedGuiScale(DESIGN_W, DESIGN_H);

    /** Flat tab look: PANEL_2 idle, ROW_BG on hover, PANEL + ACCENT outline when selected (= disabled). */
    private static final WidgetStyle TAB_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, INK, INK_MID, INK_DIM, HAIRLINE, 0x557A4A1E,
            18, 16, 4, false);

    /** Translucent red kick-button look (fill brightens on hover, DANGER outline). */
    private static final WidgetStyle DANGER_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(0x1F8B2F23, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat(0x448B2F23, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat(0x448B2F23, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat(0x1F8B2F23, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, INK, INK_DIM, INK_DIM, DANGER, 0x557A4A1E,
            18, 16, 4, false);

    /** Header "×" close button: PANEL fill, ROW_BG on hover, HAIRLINE_HI outline. */
    private static final WidgetStyle CLOSE_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL, HAIRLINE_HI, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE_HI, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE_HI, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL, HAIRLINE_HI, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, INK, INK_DIM, INK_DIM, ACCENT, 0x557A4A1E,
            18, 16, 4, false);

    /** Invite-field ▼/▲ chevron: bare INK_MID glyph over the field's PANEL_2 background. */
    private static final WidgetStyle CHEVRON_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL_2, 0, 0, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, 0, 0, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, 0, 0, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, 0, 0, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK_MID, INK_MID, INK_MID, INK_MID, ACCENT, 0x557A4A1E,
            18, 16, 4, false);

    private enum Tab { LOBBIES, MY_LOBBY, LEADERBOARD, INVITES }

    private enum InviteStatus { ONLINE, IN_LOBBY, IN_RUN }

    private record InviteCandidate(UUID uuid, String name, InviteStatus status) {
        boolean selectable() {
            return status == InviteStatus.ONLINE;
        }
    }

    private final BlockPos pos;

    // Snapshot of controller state, refreshed from the block entity on each rebuild.
    private List<Lobby> visibleLobbies = new ArrayList<>();
    private List<PendingInvite> myInvites = new ArrayList<>();
    private List<PendingJoinRequest> myJoinRequests = new ArrayList<>();
    private Optional<Lobby> ownLobby = Optional.empty();
    private List<ArenaInstanceState> instances = new ArrayList<>();
    private List<LeaderboardEntry> leaderboard = new ArrayList<>();
    private int maxPartySize = 10;
    private int maxWave = -1;
    private int queuePos = 0;
    private boolean hadOwnLobby = false;

    private Tab currentTab = Tab.LOBBIES;
    private String footerError;
    private long lastCountdownSecond = -1L;
    private int lastSignature = Integer.MIN_VALUE;

    private Flex contentArea;
    private ScrollPanel contentScroll;
    private Flex footerActions;
    private Label footerLabel;
    private Flex instancesStrip;
    private Button lobbiesTabButton;
    private Button myLobbyTabButton;
    private Button leaderboardTabButton;
    private Button invitesTabButton;

    private IdSuggestionDropdown.Field inviteField;
    private String inviteInput = "";
    private boolean inviteDropdownOpen = false;
    private Flex inviteFieldRow;
    private Flex inviteDropdownPanel;
    private Button inviteChevron;
    private float savedScrollY = 0f;
    private boolean restoreScrollNextTick = false;
    private boolean scrollToBottomNextTick = false;
    private final Map<UUID, Label> inviteCountdownLabels = new HashMap<>();
    private final Map<UUID, Label> joinRequestCountdownLabels = new HashMap<>();

    public ArenaControllerScreen(ArenaControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(DESIGN_W, DESIGN_H), Placement.Screen.center());
        this.pos = handler.getBlockPos();
        canvas.theme(ArenasParchment.THEME);
        fillWindow();
    }

    @Override
    protected void init() {
        if (this.minecraft != null) {
            this.guiScale.apply(this.minecraft);
            this.width = this.minecraft.getWindow().getGuiScaledWidth();
            this.height = this.minecraft.getWindow().getGuiScaledHeight();
        }
        super.init();
    }

    @Override
    public void removed() {
        if (this.minecraft != null) {
            this.guiScale.restore(this.minecraft);
        }
        super.removed();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // The old owo screen swallowed the inventory key entirely (close via × or Esc only).
        if (ArenasUi.swallowsInventoryKey(input, keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    @Override
    protected void onCanvasFrame(float partialTick) {
        int sig = computeSignature();
        if (sig != lastSignature && contentArea != null) {
            rebuildUi();
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (contentScroll != null) {
            if (restoreScrollNextTick) {
                contentScroll.scrollTo(savedScrollY);
                restoreScrollNextTick = false;
            }
            if (scrollToBottomNextTick) {
                contentScroll.scrollTo(1_000_000f);
                scrollToBottomNextTick = false;
            }
        }
        if ((currentTab == Tab.LOBBIES || currentTab == Tab.INVITES) && (!myInvites.isEmpty() || !myJoinRequests.isEmpty())) {
            long now = currentServerTick() / 20L;
            if (now != lastCountdownSecond) {
                lastCountdownSecond = now;
                refreshInviteCountdowns();
                refreshJoinRequestCountdowns();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if (inviteDropdownOpen) {
            float[] c = toCanvas(mouseX, mouseY);
            if (!pointInside(inviteFieldRow, c[0], c[1]) && !pointInside(inviteDropdownPanel, c[0], c[1])) {
                closeInviteDropdown();
            }
        }
        return handled;
    }

    private boolean pointInside(@Nullable CanvasNode node, float canvasX, float canvasY) {
        if (node == null) {
            return false;
        }
        float[] local = node.rootToLocal(canvasX, canvasY);
        Rect bounds = node.bounds();
        return bounds != null && bounds.contains(local[0], local[1]);
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    @Nullable
    private ArenaControllerBlockEntity controller() {
        return this.menu.controller;
    }

    private UUID self() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
    }

    private int computeSignature() {
        ArenaControllerBlockEntity c = controller();
        if (c == null) {
            return 0;
        }
        UUID me = self();
        int sig = 7;
        sig = sig * 31 + c.getLobbies().size();
        sig = sig * 31 + c.getQueuedLobbyIds().size();
        sig = sig * 31 + c.getInstances().size();
        sig = sig * 31 + c.getLeaderboard().size();
        sig = sig * 31 + c.getMaxWave();
        for (ArenaInstanceState inst : c.getInstances()) {
            sig = sig * 31 + inst.status().ordinal();
            sig = sig * 31 + (inst.cooldownTicksRemaining() / 20);
        }
        int myInviteCount = 0;
        for (PendingInvite inv : c.getPendingInvites()) {
            if (inv.invitedUuid().equals(me)) myInviteCount++;
        }
        sig = sig * 31 + myInviteCount;
        sig = sig * 31 + c.getPendingJoinRequests().size();
        Lobby own = c.getLobbyByMember(me);
        if (own != null) {
            sig = sig * 31 + own.members().size();
            sig = sig * 31 + own.readyMembers().size();
            sig = sig * 31 + own.memberNames().hashCode();
            sig = sig * 31 + own.status().ordinal();
            sig = sig * 31 + own.visibility().ordinal();
            sig = sig * 31 + (own.hardcoreEnabled() ? 1 : 0);
            sig = sig * 31 + own.ownerUuid().hashCode();
        }
        return sig;
    }

    private void snapshot() {
        visibleLobbies = new ArrayList<>();
        myInvites = new ArrayList<>();
        myJoinRequests = new ArrayList<>();
        instances = new ArrayList<>();
        leaderboard = new ArrayList<>();
        ownLobby = Optional.empty();
        queuePos = 0;

        ArenaControllerBlockEntity c = controller();
        if (c == null) {
            return;
        }
        UUID me = self();
        maxPartySize = c.getMaxPartySize();
        maxWave = c.getMaxWave();
        instances = new ArrayList<>(c.getInstances());
        leaderboard = new ArrayList<>(c.getLeaderboard());

        Lobby own = c.getLobbyByMember(me);
        ownLobby = Optional.ofNullable(own);
        UUID ownId = own != null ? own.lobbyId() : null;

        for (Lobby lobby : c.getLobbies()) {
            if (ownId != null && lobby.lobbyId().equals(ownId)) continue;
            if (lobby.status() == LobbyStatus.DISBANDED) continue;
            if (lobby.visibility() == LobbyVisibility.PRIVATE) continue;
            visibleLobbies.add(lobby);
        }

        for (PendingInvite inv : c.getPendingInvites()) {
            if (inv.invitedUuid().equals(me)) myInvites.add(inv);
        }
        if (own != null && own.isOwner(me)) {
            for (PendingJoinRequest req : c.getPendingJoinRequests()) {
                if (req.lobbyId().equals(ownId)) myJoinRequests.add(req);
            }
        }
        if (ownId != null) {
            int idx = c.getQueuedLobbyIds().indexOf(ownId);
            queuePos = idx >= 0 ? idx + 1 : 0;
        }

        // Auto-switch to the most relevant tab when lobby membership changes.
        boolean hasOwn = ownLobby.isPresent();
        if (hasOwn && !hadOwnLobby && currentTab == Tab.LOBBIES) {
            currentTab = Tab.MY_LOBBY;
        } else if (!hasOwn && currentTab == Tab.MY_LOBBY) {
            currentTab = Tab.LOBBIES;
        }
        hadOwnLobby = hasOwn;
    }

    // ── Build ────────────────────────────────────────────────────────────────

    private void buildAll() {
        canvas.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);

        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(DESIGN_W), Sizing.fixed(DESIGN_H));
        shell.background(ArenasParchment.panel());
        shell.padding(Insets.of(8)); // keep content off the parchment border art

        shell.item(buildHeader());

        instancesStrip = Flex.column().padding(Insets.of(6, 8, 6, 8));
        instancesStrip.sizing(Sizing.fill(), Sizing.content());
        instancesStrip.backgroundFill(PANEL_2);
        shell.item(instancesStrip);

        shell.item(buildTabs());

        contentArea = Flex.column().gap(4).padding(Insets.of(10));
        contentArea.sizing(Sizing.fill(), Sizing.content());
        contentArea.backgroundFill(PANEL);
        contentScroll = new ScrollPanel(100, 100, contentArea);
        contentScroll.sizing(Sizing.fill(), Sizing.expand());
        contentScroll.barWidth(8);
        contentScroll.wheelStep(18);
        shell.item(contentScroll);

        shell.item(buildFooter());

        root.layoutIn(canvas.width(), canvas.height());

        rebuildUi();
    }

    private void rebuildUi() {
        lastSignature = computeSignature();
        snapshot();

        captureScrollY();
        if (contentArea == null) {
            return;
        }
        contentArea.clear();
        footerActions.clear();
        inviteCountdownLabels.clear();
        joinRequestCountdownLabels.clear();
        inviteFieldRow = null;
        inviteDropdownPanel = null;
        inviteChevron = null;
        inviteDropdownOpen = false;

        if (instancesStrip != null) {
            instancesStrip.clear();
            buildInstancesStrip();
        }

        if (controller() == null) {
            contentArea.item(dimLabel(Component.translatable("gui.arenas_ld.arena.not_loaded")));
            footerLabel.text(Component.empty());
            return;
        }

        lobbiesTabButton.enabled(currentTab != Tab.LOBBIES);
        myLobbyTabButton.enabled(currentTab != Tab.MY_LOBBY);
        leaderboardTabButton.enabled(currentTab != Tab.LEADERBOARD);
        invitesTabButton.enabled(currentTab != Tab.INVITES);
        lobbiesTabButton.label(Component.translatable("gui.arenas_ld.dungeon_controller.tab.lobbies").append(" " + visibleLobbies.size()));
        myLobbyTabButton.label(Component.translatable("gui.arenas_ld.dungeon_controller.tab.my_lobby").append(ownLobby.isPresent() ? " " + ownLobby.get().members().size() : ""));
        leaderboardTabButton.label(Component.translatable("gui.arenas_ld.dungeon_controller.tab.leaderboard"));
        invitesTabButton.label(Component.translatable("gui.arenas_ld.dungeon_controller.tab.invites").append(" " + myInvites.size()));

        switch (currentTab) {
            case LOBBIES -> buildLobbiesContent();
            case MY_LOBBY -> buildMyLobbyContent();
            case LEADERBOARD -> buildLeaderboardContent();
            case INVITES -> buildInvitesContent();
        }

        footerLabel.text(footerError == null ? Component.empty() : Component.literal(footerError).withStyle(ChatFormatting.RED));
        if (contentScroll != null && savedScrollY > 0f) {
            contentScroll.scrollTo(savedScrollY);
            restoreScrollNextTick = true;
        } else {
            restoreScrollNextTick = false;
        }
    }

    private Flex buildHeader() {
        Flex header = Flex.row().gap(10).padding(Insets.of(8, 10, 8, 10)).alignItems(Align.CENTER);
        header.sizing(Sizing.fill(), Sizing.fixed(46));
        header.backgroundFill(PANEL_2);

        Flex mark = Flex.column();
        mark.sizing(Sizing.fixed(20), Sizing.fixed(20));
        mark.backgroundFill(ACCENT, ACCENT_DARK, 1);
        header.item(mark);

        Flex info = Flex.column().gap(3);
        info.sizing(Sizing.content(), Sizing.content());

        Flex titleLine = Flex.row().gap(8).alignItems(Align.CENTER);
        titleLine.sizing(Sizing.content(), Sizing.content());
        titleLine.item(text(Component.translatable("container.arenas_ld.arena_controller"), INK));
        titleLine.item(statusBadge());
        info.item(titleLine);

        Flex meta = Flex.row().gap(10).alignItems(Align.CENTER);
        meta.sizing(Sizing.content(), Sizing.content());
        String waveMeta = maxWave < 0
            ? Component.translatable("gui.arenas_ld.arena.endless").getString()
            : String.valueOf(maxWave);
        meta.item(smallMeta(Component.translatable("gui.arenas_ld.arena.max_wave_meta", waveMeta).getString()));
        Flex live = Flex.row().gap(4).alignItems(Align.CENTER);
        live.sizing(Sizing.content(), Sizing.content());
        live.item(text(Component.literal("●"), GOOD));
        live.item(smallMeta("LIVE", GOOD));
        meta.item(live);
        info.item(meta);
        header.item(info);

        header.spacer();

        Button close = new Button(22, 18, Component.literal("×"), this::onClose);
        close.style(CLOSE_STYLE);
        header.item(close);
        return header;
    }

    private Flex statusBadge() {
        Component label;
        int color;
        if (ownLobby.isPresent()) {
            label = Component.translatable("gui.arenas_ld.dungeon_controller.ui.status.in_lobby");
            color = INFO;
        } else if (!myInvites.isEmpty()) {
            label = Component.translatable("gui.arenas_ld.dungeon_controller.ui.status.invites", myInvites.size());
            color = ACCENT;
        } else {
            label = Component.translatable("gui.arenas_ld.dungeon_controller.ui.status.available");
            color = GOOD;
        }
        return badge(label, color);
    }

    private Flex buildTabs() {
        Flex tabs = Flex.row().gap(0).padding(Insets.of(4)).alignItems(Align.CENTER);
        tabs.sizing(Sizing.fill(), Sizing.fixed(30));
        tabs.backgroundFill(PANEL_2);

        lobbiesTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.lobbies", Tab.LOBBIES);
        myLobbyTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.my_lobby", Tab.MY_LOBBY);
        leaderboardTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.leaderboard", Tab.LEADERBOARD);
        invitesTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.invites", Tab.INVITES);

        tabs.item(tabCell(lobbiesTabButton));
        tabs.item(tabCell(myLobbyTabButton));
        tabs.item(tabCell(leaderboardTabButton));
        tabs.item(tabCell(invitesTabButton));
        return tabs;
    }

    private Flex tabCell(Button button) {
        Flex cell = Flex.column().padding(Insets.of(0, 2, 0, 2));
        cell.sizing(Sizing.fill(0.25f), Sizing.content());
        button.sizing(Sizing.fill(), Sizing.fixed(20));
        cell.item(button);
        return cell;
    }

    private Button tabButton(String key, Tab tab) {
        Button button = new Button(120, 20, Component.translatable(key), () -> {
            currentTab = tab;
            footerError = null;
            savedScrollY = 0f;
            rebuildUi();
        });
        button.style(TAB_STYLE);
        return button;
    }

    private Flex buildFooter() {
        Flex footer = Flex.row().gap(6).padding(Insets.of(6));
        footer.sizing(Sizing.fill(), Sizing.fixed(34));
        footer.backgroundFill(PANEL_2);

        footerLabel = ArenasParchment.label(100, Component.empty(), DANGER);
        footerLabel.sizing(Sizing.expand(), Sizing.content());
        footer.item(footerLabel);

        footerActions = Flex.row().gap(4);
        footerActions.sizing(Sizing.content(), Sizing.content());
        footer.item(footerActions);
        return footer;
    }

    // ── Instances strip ────────────────────────────────────────────────────────

    private void buildInstancesStrip() {
        Flex titleRow = Flex.row().gap(8).alignItems(Align.CENTER);
        titleRow.sizing(Sizing.fill(), Sizing.content());
        titleRow.item(text(Component.translatable("gui.arenas_ld.arena.instances"), INK_DIM));
        instancesStrip.item(titleRow);

        if (instances.isEmpty()) {
            instancesStrip.item(text(Component.translatable("gui.arenas_ld.arena.no_instances"), INK_DIM));
            return;
        }

        Flex pillRow = Flex.row().gap(4).alignItems(Align.CENTER);
        pillRow.sizing(Sizing.fill(), Sizing.content());
        for (ArenaInstanceState inst : instances) {
            String pillText = inst.status().name()
                + (inst.cooldownTicksRemaining() > 0 ? " " + (inst.cooldownTicksRemaining() / 20) + "s" : "");
            pillRow.item(badge(Component.literal(pillText), instanceStatusColor(inst.status())));
        }
        instancesStrip.item(pillRow);
    }

    private int instanceStatusColor(InstanceStatus status) {
        return switch (status) {
            case FREE -> GOOD;
            case RUNNING -> WARN;
            case COOLDOWN -> DANGER;
        };
    }

    // ── Lobbies tab ────────────────────────────────────────────────────────────

    private void buildLobbiesContent() {
        Button create = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.create"), () -> {
            footerError = null;
            send(new ArenaLobbyActionPayload(pos, "create"));
        });
        create.enabled(ownLobby.isEmpty());
        contentArea.item(sectionHeaderWithAction(Component.translatable("gui.arenas_ld.dungeon_controller.public_lobbies"), visibleLobbies.size(), create));
        if (!visibleLobbies.isEmpty()) {
            contentArea.item(lobbyHeaderRow());
        }

        List<Lobby> sorted = visibleLobbies.stream().sorted(Comparator.comparing(Lobby::ownerName)).limit(6).toList();
        if (sorted.isEmpty()) {
            contentArea.item(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_lobbies")));
        } else {
            for (Lobby lobby : sorted) {
                contentArea.item(lobbyRow(lobby));
            }
        }
    }

    private Flex lobbyHeaderRow() {
        Flex row = Flex.row().gap(8).padding(Insets.of(2, 6, 2, 6));
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.owner"), Sizing.expand()));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.size"), Sizing.fixed(44)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility"), Sizing.fixed(78)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.status"), Sizing.fixed(82)));
        row.item(headerCell("", Sizing.fixed(84)));
        return row;
    }

    private Flex lobbyRow(Lobby lobby) {
        Flex row = rowPanel(false);
        row.gap(8);

        Flex ownerInfo = Flex.column();
        ownerInfo.sizing(Sizing.expand(), Sizing.content());
        Flex nameLine = Flex.row().gap(5).alignItems(Align.CENTER);
        nameLine.sizing(Sizing.expand(), Sizing.content());
        nameLine.item(text(Component.literal(lobby.ownerName()), INK));
        if (lobby.hardcoreEnabled()) {
            nameLine.item(badge(Component.literal("H"), DANGER));
        }
        ownerInfo.item(nameLine);
        ownerInfo.item(text(Component.literal(shortUuid(lobby.lobbyId()).toUpperCase(Locale.ROOT)), INK_DIM));
        row.item(ownerInfo);
        row.item(fixedBadge(Component.literal(lobby.members().size() + "/" + maxPartySize), 44, INK_MID));

        Flex visCell = Flex.row().gap(4).padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        visCell.sizing(Sizing.fixed(78), Sizing.content());
        int visColor = visibilityColor(lobby.visibility());
        visCell.backgroundFill((visColor & 0x00FFFFFF) | 0x22000000, (visColor & 0x00FFFFFF) | 0x55000000, 1);
        visCell.item(text(Component.literal("●"), visColor));
        visCell.item(text(Component.literal(lobby.visibility().name()), visColor));
        row.item(visCell);

        row.item(fixedBadge(Component.literal(statusLabel(lobby.status())), 82, statusColor(lobby.status())));

        UUID lobbyId = lobby.lobbyId();
        boolean isFull = lobby.isFull(maxPartySize);
        boolean inRun = lobby.status() == LobbyStatus.IN_RUN;

        Flex actionCell = Flex.row().justify(Justify.END).alignItems(Align.CENTER);
        actionCell.sizing(Sizing.fixed(84), Sizing.content());
        Button actionButton;
        if (inRun) {
            actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.running"), () -> {});
            actionButton.enabled(false);
        } else if (isFull) {
            actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.full"), () -> {});
            actionButton.enabled(false);
        } else if (lobby.visibility() == LobbyVisibility.PUBLIC) {
            actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.join"), () -> {
                footerError = null;
                send(new ArenaLobbyTargetPayload(pos, "join", lobbyId));
            });
            actionButton.enabled(ownLobby.isEmpty());
        } else {
            UUID me = self();
            boolean alreadyRequested = myJoinRequests.stream()
                .anyMatch(r -> r.lobbyId().equals(lobbyId) && r.requesterUuid().equals(me));
            if (alreadyRequested) {
                actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.pending"), () -> {});
                actionButton.enabled(false);
            } else {
                actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.request"), () -> {
                    footerError = null;
                    send(new ArenaLobbyTargetPayload(pos, "request", lobbyId));
                });
                actionButton.enabled(ownLobby.isEmpty());
            }
        }
        actionButton.sizing(Sizing.fill(), Sizing.fixed(18));
        actionCell.item(actionButton);
        row.item(actionCell);
        return row;
    }

    // ── My Lobby tab ────────────────────────────────────────────────────────────

    private void buildMyLobbyContent() {
        if (ownLobby.isEmpty()) {
            contentArea.item(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_own_lobby")));
            return;
        }

        Lobby lobby = ownLobby.get();
        UUID self = self();
        boolean isOwner = lobby.ownerUuid().equals(self);

        if (queuePos >= 1) {
            Flex queueBanner = Flex.row().gap(8).padding(Insets.of(4, 8, 4, 8)).alignItems(Align.CENTER);
            queueBanner.sizing(Sizing.fill(), Sizing.fixed(28));
            queueBanner.backgroundFill((WARN & 0x00FFFFFF) | 0x1A000000, (WARN & 0x00FFFFFF) | 0x55000000, 1);
            Flex queueAccent = Flex.column();
            queueAccent.sizing(Sizing.fixed(3), Sizing.fill());
            queueAccent.backgroundFill(WARN);
            queueBanner.item(queueAccent);
            queueBanner.item(badge(Component.translatable("gui.arenas_ld.raid_controller.ui.queue.in_line", queuePos), WARN));
            queueBanner.spacer();
            if (isOwner) {
                queueBanner.item(smallButton(Component.translatable("gui.arenas_ld.raid_controller.button.leave_queue"), () -> {
                    footerError = null;
                    send(new ArenaLobbyActionPayload(pos, "leave"));
                }));
            }
            contentArea.item(queueBanner);
            contentArea.item(spacer(4));
        }

        Flex summary = Flex.row().alignItems(Align.CENTER);
        summary.sizing(Sizing.fill(), Sizing.fixed(40));
        summary.backgroundFill(ROW_BG, HAIRLINE, 1);
        Flex summaryAccent = Flex.column();
        summaryAccent.sizing(Sizing.fixed(3), Sizing.fill());
        summaryAccent.backgroundFill(ACCENT);
        summary.item(summaryAccent);
        Flex summaryPad = Flex.column();
        summaryPad.sizing(Sizing.fixed(9), Sizing.fill());
        summary.item(summaryPad);
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.owner"),
            text(Component.literal(lobby.ownerName()), ACCENT), Sizing.fill(0.34f)));
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.members"),
            text(Component.literal(lobby.members().size() + " / " + maxPartySize), INK), Sizing.fill(0.22f)));
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility"),
            text(Component.literal(titleCase(lobby.visibility().name())), visibilityColor(lobby.visibility())), Sizing.fill(0.22f)));
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.hardcore"),
            text(Component.translatable(lobby.hardcoreEnabled() ? "gui.arenas_ld.dungeon_controller.ui.hardcore.on" : "gui.arenas_ld.dungeon_controller.ui.hardcore.off"), lobby.hardcoreEnabled() ? DANGER : INK_MID), Sizing.fill(0.22f)));
        contentArea.item(summary);

        contentArea.item(membersSectionHeader(lobby.readyMembers().size(), lobby.members().size(), maxPartySize));
        Flex memberList = Flex.column();
        memberList.sizing(Sizing.fill(), Sizing.content());
        memberList.item(memberHeaderRow(isOwner));
        memberList.item(rowDivider(HAIRLINE_HI));
        List<UUID> orderedMembers = lobby.members().stream()
            .sorted(Comparator.comparing((UUID m) -> m.equals(lobby.ownerUuid()) ? 0 : 1)
                .thenComparing(m -> lobby.memberNames().getOrDefault(m, shortUuid(m)).toLowerCase(Locale.ROOT)))
            .limit(maxPartySize)
            .toList();
        boolean firstMemberRow = true;
        for (UUID member : orderedMembers) {
            if (!firstMemberRow) {
                memberList.item(rowDivider(HAIRLINE));
            }
            memberList.item(memberRow(lobby, member, isOwner));
            firstMemberRow = false;
        }
        contentArea.item(memberList);

        if (isOwner) {
            contentArea.item(spacer(6));
            contentArea.item(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.ui.section.owner_controls"), null));

            contentArea.item(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility")));
            contentArea.item(ownerVisibilityControls(true));

            contentArea.item(spacer(4));
            contentArea.item(hardcoreControl(true));

            contentArea.item(spacer(4));
            contentArea.item(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.section.invite_player")));
            contentArea.item(ownerInviteControls(true));
        }

        boolean ready = lobby.readyMembers().contains(self);
        boolean canStart = isOwner && lobby.allReady() && allOnline(lobby) && queuePos < 1;

        footerActions.item(smallButton(Component.translatable(ready
            ? "gui.arenas_ld.dungeon_controller.button.unready"
            : "gui.arenas_ld.dungeon_controller.button.ready"), () -> {
            footerError = null;
            send(new ArenaLobbyActionPayload(pos, "ready"));
        }));
        footerActions.item(smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.leave"), () -> {
            footerError = null;
            send(new ArenaLobbyActionPayload(pos, "leave"));
        }));
        Button start = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.start"), () -> {
            footerError = null;
            Lobby current = ownLobby.orElse(null);
            if (current == null) {
                footerError = tr("gui.arenas_ld.dungeon_controller.no_own_lobby");
                rebuildUi();
                return;
            }
            if (!current.ownerUuid().equals(self())) {
                footerError = tr("gui.arenas_ld.dungeon_controller.error.only_owner");
                rebuildUi();
                return;
            }
            if (!current.allReady()) {
                footerError = tr("gui.arenas_ld.dungeon_controller.error.all_ready_required");
                rebuildUi();
                return;
            }
            if (!allOnline(current)) {
                footerError = tr("gui.arenas_ld.dungeon_controller.error.all_online_required");
                rebuildUi();
                return;
            }
            send(new ArenaLobbyActionPayload(pos, "start"));
        });
        start.enabled(canStart);
        footerActions.item(start);
    }

    private Flex memberRow(Lobby lobby, UUID member, boolean isOwner) {
        boolean online = isOnline(member);
        boolean ready = lobby.readyMembers().contains(member);
        boolean isLobbyOwner = member.equals(lobby.ownerUuid());

        Flex row = Flex.row().gap(8).padding(Insets.of(7, 8, 7, 8)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(ROW_BG);

        Flex statusSquare = Flex.row().justify(Justify.CENTER).alignItems(Align.CENTER);
        statusSquare.sizing(Sizing.fixed(18), Sizing.fixed(18));
        int squareColor = online ? GOOD : WARN;
        statusSquare.backgroundFill((squareColor & 0x00FFFFFF) | 0x22000000, (squareColor & 0x00FFFFFF) | 0x66000000, 1);
        statusSquare.item(text(Component.literal(online ? "✓" : "!"), online ? GOOD : WARN));
        row.item(statusSquare);

        String name = lobby.memberNames().getOrDefault(member, shortUuid(member));
        Flex nameCell = Flex.row().gap(6).alignItems(Align.CENTER);
        nameCell.sizing(Sizing.expand(), Sizing.content());
        nameCell.item(text(Component.literal(name), INK));
        if (isLobbyOwner) {
            nameCell.item(badge(Component.literal("★ ").append(Component.translatable("gui.arenas_ld.dungeon_controller.ui.owner_badge")), ACCENT));
        }
        row.item(nameCell);

        Label connectionLabel = ArenasParchment.label(84,
            Component.literal(online ? "● " : "○ ").append(Component.translatable(online ? "gui.arenas_ld.dungeon_controller.ui.member.online" : "gui.arenas_ld.dungeon_controller.ui.member.offline")),
            online ? GOOD : DANGER);
        row.item(connectionLabel);

        row.item(fixedBadge(Component.translatable(ready ? "gui.arenas_ld.dungeon_controller.ui.member.ready" : "gui.arenas_ld.dungeon_controller.ui.member.not_ready"), 84, ready ? GOOD : INK_DIM));

        if (isOwner && !isLobbyOwner) {
            Button kick = dangerButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.kick"), 54, () -> {
                footerError = null;
                send(new ArenaLobbyTargetPayload(pos, "kick", member));
            });
            row.item(kick);
        } else {
            Flex kickSpace = Flex.row();
            kickSpace.sizing(Sizing.fixed(54), Sizing.content());
            row.item(kickSpace);
        }
        return row;
    }

    private Flex memberHeaderRow(boolean isOwner) {
        Flex row = Flex.row().gap(8).padding(Insets.of(5, 8, 5, 8)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(PANEL_2);
        Flex statusSpacer = Flex.row();
        statusSpacer.sizing(Sizing.fixed(18), Sizing.content());
        row.item(statusSpacer);
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.name"), Sizing.expand()));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.connection"), Sizing.fixed(84)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.ready"), Sizing.fixed(84)));
        row.item(headerCell(isOwner ? tr("gui.arenas_ld.dungeon_controller.ui.col.action") : "", Sizing.fixed(54)));
        return row;
    }

    private Flex ownerVisibilityControls(boolean isOwner) {
        LobbyVisibility current = ownLobby.map(Lobby::visibility).orElse(null);
        Button pub = segmentButton("PUBLIC", visibilityColor(LobbyVisibility.PUBLIC), isOwner,
            current == LobbyVisibility.PUBLIC,
            () -> send(new ArenaSetVisibilityPayload(pos, LobbyVisibility.PUBLIC.name())));
        Button fr = segmentButton("FRIENDS", visibilityColor(LobbyVisibility.FRIENDS), isOwner,
            current == LobbyVisibility.FRIENDS,
            () -> send(new ArenaSetVisibilityPayload(pos, LobbyVisibility.FRIENDS.name())));
        Button pr = segmentButton("PRIVATE", visibilityColor(LobbyVisibility.PRIVATE), isOwner,
            current == LobbyVisibility.PRIVATE,
            () -> send(new ArenaSetVisibilityPayload(pos, LobbyVisibility.PRIVATE.name())));
        return segmentedControl(pub, fr, pr);
    }

    private Flex hardcoreControl(boolean isOwner) {
        Flex row = Flex.row().gap(8).padding(Insets.of(8, 10, 8, 10)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(ROW_BG, HAIRLINE, 1);

        Flex textCol = Flex.column().gap(2);
        textCol.sizing(Sizing.expand(), Sizing.content());
        textCol.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.hardcore.title"), INK_DIM));
        textCol.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.hardcore.desc"), INK));
        row.item(textCol);

        row.item(toggleSwitch(isOwner,
            () -> ownLobby.map(Lobby::hardcoreEnabled).orElse(false),
            () -> {
                footerError = null;
                boolean current = ownLobby.map(Lobby::hardcoreEnabled).orElse(false);
                send(new ArenaSetHardcorePayload(pos, !current));
            }));
        return row;
    }

    // ── Invite dropdown ──────────────────────────────────────────────────────────

    private Flex ownerInviteControls(boolean isOwner) {
        Flex section = Flex.column().gap(0);
        section.sizing(Sizing.fill(), Sizing.content());

        Flex row = Flex.row().gap(6).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        inviteFieldRow = row;

        Flex fieldWrap = Flex.row().alignItems(Align.CENTER);
        fieldWrap.sizing(Sizing.expand(), Sizing.content());
        fieldWrap.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex fieldAccent = Flex.column();
        fieldAccent.sizing(Sizing.fixed(2), Sizing.fixed(20));
        fieldAccent.backgroundFill(ACCENT);
        fieldWrap.item(fieldAccent);

        inviteField = IdSuggestionDropdown.textBox(100, inviteInput, 32);
        inviteField.sizing(Sizing.expand(), Sizing.fixed(18));
        inviteField.enabled(isOwner);
        inviteField.changeListeners.add(value -> {
            inviteInput = value;
            if (inviteDropdownOpen) {
                refreshInviteDropdown();
            }
        });
        inviteField.mouseDownHooks.add(() -> {
            if (isOwner) {
                openInviteDropdown();
            }
        });
        fieldWrap.item(inviteField);

        inviteChevron = new Button(18, 20, Component.literal("▼"), () -> {
            if (isOwner) {
                toggleInviteDropdown();
            }
        });
        inviteChevron.style(CHEVRON_STYLE);
        inviteChevron.enabled(isOwner);
        fieldWrap.item(inviteChevron);
        row.item(fieldWrap);

        Button invite = ArenasParchment.button(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.invite"), 58, 18, this::sendInvite);
        invite.enabled(isOwner);
        row.item(invite);
        section.item(row);

        inviteDropdownPanel = Flex.column();
        inviteDropdownPanel.sizing(Sizing.fill(), Sizing.content());
        section.item(inviteDropdownPanel);
        refreshInviteDropdown();
        return section;
    }

    private void sendInvite() {
        footerError = null;
        UUID invitee = resolveInviteeUuid(inviteInput);
        if (invitee == null) {
            footerError = tr("gui.arenas_ld.dungeon_controller.error.invitee_not_found");
            return;
        }
        send(new ArenaLobbyTargetPayload(pos, "invite", invitee));
        inviteInput = "";
        if (inviteField != null) {
            inviteField.text("");
        }
        closeInviteDropdown();
    }

    private UUID resolveInviteeUuid(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
            // treat as a player name
        }
        if (minecraft == null || minecraft.getConnection() == null) {
            return null;
        }
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (info.getProfile().getName().equalsIgnoreCase(trimmed)) {
                return info.getProfile().getId();
            }
        }
        return null;
    }

    private void openInviteDropdown() {
        if (!inviteDropdownOpen) {
            inviteDropdownOpen = true;
            refreshInviteDropdown();
            if (contentScroll != null) {
                contentScroll.scrollTo(1_000_000f);
                scrollToBottomNextTick = true;
            }
        }
    }

    private void closeInviteDropdown() {
        if (inviteDropdownOpen) {
            inviteDropdownOpen = false;
            refreshInviteDropdown();
        }
    }

    private void toggleInviteDropdown() {
        if (inviteDropdownOpen) {
            closeInviteDropdown();
        } else {
            openInviteDropdown();
        }
    }

    private void refreshInviteDropdown() {
        if (inviteChevron != null) {
            inviteChevron.label(Component.literal(inviteDropdownOpen ? "▲" : "▼"));
        }
        if (inviteDropdownPanel == null) {
            return;
        }
        inviteDropdownPanel.clear();
        if (!inviteDropdownOpen) {
            inviteDropdownPanel.background(null);
            return;
        }
        inviteDropdownPanel.backgroundFill(PANEL_2, HAIRLINE, 1);

        List<InviteCandidate> candidates = inviteCandidates(inviteInput);
        long available = candidates.stream().filter(InviteCandidate::selectable).count();

        Flex header = Flex.row().padding(Insets.of(4, 8, 4, 8)).alignItems(Align.CENTER);
        header.sizing(Sizing.fill(), Sizing.content());
        header.backgroundFill(ROW_BG);
        Label headerLabel = ArenasParchment.label(10, Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite.header"), INK_DIM)
            .secondary(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite.available", available))
            .secondaryColor(ACCENT);
        headerLabel.sizing(Sizing.fill(), Sizing.content());
        header.item(headerLabel);
        inviteDropdownPanel.item(header);
        inviteDropdownPanel.item(rowDivider(HAIRLINE_HI));

        if (candidates.isEmpty()) {
            Flex empty = Flex.row().padding(Insets.of(8)).alignItems(Align.CENTER);
            empty.sizing(Sizing.fill(), Sizing.content());
            empty.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite.empty"), INK_DIM));
            inviteDropdownPanel.item(empty);
            return;
        }

        Flex list = Flex.column();
        list.sizing(Sizing.fill(), Sizing.content());
        boolean first = true;
        for (InviteCandidate candidate : candidates) {
            if (!first) {
                list.item(rowDivider(HAIRLINE));
            }
            list.item(inviteCandidateRow(candidate));
            first = false;
        }
        int rowsHeight = candidates.size() * 22 + Math.max(0, candidates.size() - 1);
        int visibleHeight = Math.min(rowsHeight, INVITE_DROPDOWN_MAX_HEIGHT);
        ScrollPanel scroll = new ScrollPanel(100, visibleHeight, list);
        scroll.sizing(Sizing.fill(), Sizing.fixed(visibleHeight));
        inviteDropdownPanel.item(scroll);
    }

    private InviteCandidateRow inviteCandidateRow(InviteCandidate candidate) {
        boolean selectable = candidate.selectable();
        int statusColor = selectable ? GOOD : WARN;
        String statusText = switch (candidate.status()) {
            case ONLINE -> tr("gui.arenas_ld.dungeon_controller.ui.member.online");
            case IN_LOBBY -> tr("gui.arenas_ld.dungeon_controller.ui.candidate.in_lobby");
            case IN_RUN -> tr("gui.arenas_ld.dungeon_controller.ui.candidate.in_run");
        };
        String nameText = "@" + candidate.name();
        InviteCandidateRow row = new InviteCandidateRow(nameText, statusText, statusColor, selectable, () -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "invite", candidate.uuid()));
            inviteInput = "";
            if (inviteField != null) {
                inviteField.text("");
            }
            closeInviteDropdown();
        });
        row.sizing(Sizing.fill(), Sizing.fixed(22));
        return row;
    }

    /**
     * One dropdown suggestion row: hover fill + accent bar, status square, "@name" and the
     * right-aligned status text — the ported owo button renderer, drawn as canvas shapes.
     */
    private static final class InviteCandidateRow extends Widget {
        private final String nameText;
        private final String statusText;
        private final int statusColor;
        private final boolean selectable;
        private final Runnable onPick;

        InviteCandidateRow(String nameText, String statusText, int statusColor, boolean selectable, Runnable onPick) {
            super(100, 22);
            this.nameText = nameText;
            this.statusText = statusText;
            this.statusColor = statusColor;
            this.selectable = selectable;
            this.onPick = onPick;
        }

        @Override
        protected void rebuild() {
            boolean hover = selectable && (hovered || focused);
            add(Shapes.rect(0, 0, width, height).fill(hover ? ROW_BG : PANEL_2));
            if (hover) {
                add(Shapes.rect(0, 0, 2, height).fill(ACCENT));
            }
            int alpha = selectable ? 0xFF000000 : 0x73000000;
            add(Shapes.rect(8, height / 2 - 4, 8, 8).fill((statusColor & 0x00FFFFFF) | alpha));
            add(TextNode.of(nameText).color((INK & 0x00FFFFFF) | alpha).shadow(false).at(24, textY(height)));
            float statusWidth = Minecraft.getInstance().font.width(statusText);
            add(TextNode.of(statusText).color((statusColor & 0x00FFFFFF) | alpha).shadow(false)
                .at(width - statusWidth - 10, textY(height)));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!selectable || button != 0) {
                return false;
            }
            UiSounds.click();
            onPick.run();
            return true;
        }

        @Override
        public Cursor cursor() {
            return selectable ? Cursor.HAND : Cursor.DEFAULT;
        }
    }

    private List<InviteCandidate> inviteCandidates(String filter) {
        List<InviteCandidate> result = new ArrayList<>();
        if (minecraft == null || minecraft.getConnection() == null) {
            return result;
        }
        UUID self = self();
        Set<UUID> myMembers = ownLobby.map(lobby -> new HashSet<>(lobby.members())).orElseGet(HashSet::new);
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        if (needle.startsWith("@")) {
            needle = needle.substring(1);
        }
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            UUID id = info.getProfile().getId();
            String name = info.getProfile().getName();
            if (id == null || name == null) {
                continue;
            }
            if (id.equals(self) || myMembers.contains(id)) {
                continue;
            }
            if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(new InviteCandidate(id, name, inviteStatusFor(id)));
        }
        result.sort(Comparator.comparing((InviteCandidate c) -> c.selectable() ? 0 : 1)
            .thenComparing(c -> c.name().toLowerCase(Locale.ROOT)));
        return result;
    }

    private InviteStatus inviteStatusFor(UUID uuid) {
        ArenaControllerBlockEntity c = controller();
        if (c != null) {
            for (Lobby lobby : c.getLobbies()) {
                if (lobby.members().contains(uuid)) {
                    return lobby.status() == LobbyStatus.IN_RUN ? InviteStatus.IN_RUN : InviteStatus.IN_LOBBY;
                }
            }
        }
        return InviteStatus.ONLINE;
    }

    // ── Leaderboard tab ──────────────────────────────────────────────────────────

    private void buildLeaderboardContent() {
        contentArea.item(leaderboardBanner());

        List<LeaderboardEntry> entries = new ArrayList<>(leaderboard);
        entries.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds).reversed());

        contentArea.item(spacer(4));
        contentArea.item(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.ui.section.top_runs"), entries.size()));

        Flex list = Flex.column();
        list.sizing(Sizing.fill(), Sizing.content());
        list.item(leaderboardHeaderRow());
        list.item(rowDivider(HAIRLINE_HI));
        if (entries.isEmpty()) {
            Flex empty = Flex.row().padding(Insets.of(10)).alignItems(Align.CENTER);
            empty.sizing(Sizing.fill(), Sizing.content());
            empty.backgroundFill(ROW_BG);
            empty.item(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.ui.leaderboard.no_runs")));
            list.item(empty);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    list.item(rowDivider(HAIRLINE));
                }
                list.item(leaderboardRow(i + 1, entries.get(i)));
            }
        }
        contentArea.item(list);
    }

    private Flex leaderboardBanner() {
        Flex banner = Flex.row().alignItems(Align.CENTER);
        banner.sizing(Sizing.fill(), Sizing.fixed(34));
        banner.backgroundFill((ACCENT & 0x00FFFFFF) | 0x1F000000, (ACCENT & 0x00FFFFFF) | 0x55000000, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(3), Sizing.fill());
        accent.backgroundFill(ACCENT);
        banner.item(accent);
        Flex inner = Flex.row().gap(8).padding(Insets.of(0, 10, 0, 10)).alignItems(Align.CENTER);
        inner.sizing(Sizing.expand(), Sizing.content());
        inner.item(badge(Component.translatable("gui.arenas_ld.arena.leaderboard.banner_title"), ACCENT));
        inner.item(text(Component.translatable("gui.arenas_ld.arena.leaderboard.banner_desc"), INK));
        banner.item(inner);
        return banner;
    }

    private Flex leaderboardHeaderRow() {
        Flex row = Flex.row().gap(8).padding(Insets.of(5, 8, 5, 8)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(PANEL_2);
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.rank"), Sizing.fixed(50)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.player"), Sizing.expand()));
        Label whenHeader = headerCell(tr("gui.arenas_ld.ui.col.when"), Sizing.fixed(44));
        whenHeader.align(TextNode.Align.RIGHT);
        row.item(whenHeader);
        Label waveHeader = headerCell(tr("gui.arenas_ld.arena.col.wave"), Sizing.fixed(64));
        waveHeader.align(TextNode.Align.RIGHT);
        row.item(waveHeader);
        return row;
    }

    private Flex leaderboardRow(int rank, LeaderboardEntry entry) {
        boolean top = rank == 1;
        Flex row = Flex.row().gap(8).padding(Insets.of(7, 8, 7, 8)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(top ? ((ACCENT & 0x00FFFFFF) | 0x1A000000) : ROW_BG);

        Label rankLabel = ArenasParchment.label(50, Component.literal(top ? "★1" : ("#" + rank)), top ? ACCENT : INK_MID);
        row.item(rankLabel);

        Label nameLabel = ArenasParchment.label(100, Component.literal(entry.playerName()), INK);
        nameLabel.sizing(Sizing.expand(), Sizing.content());
        row.item(nameLabel);

        Label whenLabel = ArenasParchment.label(44, Component.literal(
            net.ledok.arenas_ld.screen.RelativeTime.ago(entry.recordedAtEpochMillis())), INK_MID);
        whenLabel.align(TextNode.Align.RIGHT);
        row.item(whenLabel);

        Label waveLabel = ArenasParchment.label(64, Component.translatable("gui.arenas_ld.arena.wave_n", entry.timeSeconds()), top ? ACCENT : INK_MID);
        waveLabel.align(TextNode.Align.RIGHT);
        row.item(waveLabel);
        return row;
    }

    // ── Invites tab ──────────────────────────────────────────────────────────────

    private void buildInvitesContent() {
        contentArea.item(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.invites"), myInvites.size()));
        if (myInvites.isEmpty()) {
            contentArea.item(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_invites")));
        } else {
            contentArea.item(inviteHeaderRow());
            for (PendingInvite invite : myInvites) {
                contentArea.item(inviteRow(invite));
            }
        }

        UUID self = self();
        boolean isOwner = ownLobby.isPresent() && ownLobby.get().ownerUuid().equals(self);
        if (isOwner) {
            contentArea.item(spacer(8));
            contentArea.item(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.join_requests"), myJoinRequests.size()));
            if (myJoinRequests.isEmpty()) {
                contentArea.item(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_join_requests")));
            } else {
                contentArea.item(joinRequestHeaderRow());
                for (PendingJoinRequest req : myJoinRequests) {
                    contentArea.item(joinRequestRow(req));
                }
            }
        }
    }

    private Flex inviteHeaderRow() {
        Flex row = Flex.row().gap(6).padding(Insets.of(2, 6, 2, 6));
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.from"), Sizing.expand()));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.expires"), Sizing.fixed(62)));
        row.item(headerCell("", Sizing.fixed(54)));
        row.item(headerCell("", Sizing.fixed(58)));
        return row;
    }

    private Flex inviteRow(PendingInvite invite) {
        Flex row = rowPanel(false);
        row.gap(6);

        Optional<Lobby> inviteLobby = resolveLobbyForInvite(invite);
        String ownerName = inviteLobby.map(Lobby::ownerName)
            .filter(n -> !n.isEmpty())
            .orElseGet(() -> !invite.ownerName().isEmpty() ? invite.ownerName() : shortUuid(invite.lobbyId()));
        String inviterName = inviteLobby
            .map(l -> l.memberNames().get(invite.inviterUuid()))
            .filter(n -> n != null && !n.isEmpty())
            .orElse(ownerName);

        long remainingSeconds = Math.max(0L, invite.expiresAtTick() - currentServerTick()) / 20L;
        Flex inviteInfo = Flex.column();
        inviteInfo.sizing(Sizing.expand(), Sizing.content());
        Flex fromLine = Flex.row().gap(3);
        fromLine.sizing(Sizing.content(), Sizing.content());
        fromLine.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.from"), INK));
        fromLine.item(text(Component.literal(inviterName), ACCENT));
        inviteInfo.item(fromLine);
        inviteInfo.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.owner", ownerName.toUpperCase(Locale.ROOT)), INK_DIM));
        row.item(inviteInfo);

        Label countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, TextNode.Align.CENTER);
        inviteCountdownLabels.put(invite.lobbyId(), countdown);
        row.item(countdown);

        Button accept = ArenasParchment.button(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), 54, 18, () -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "accept_invite", invite.lobbyId()));
        });
        row.item(accept);

        Button decline = ArenasParchment.button(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), 58, 18, () -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "decline_invite", invite.lobbyId()));
        });
        row.item(decline);
        return row;
    }

    private Flex joinRequestHeaderRow() {
        Flex row = Flex.row().gap(6).padding(Insets.of(2, 6, 2, 6));
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.requester"), Sizing.expand()));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.expires"), Sizing.fixed(62)));
        row.item(headerCell("", Sizing.fixed(54)));
        row.item(headerCell("", Sizing.fixed(58)));
        return row;
    }

    private Flex joinRequestRow(PendingJoinRequest req) {
        Flex row = rowPanel(false);
        row.gap(6);

        String requesterName = !req.requesterName().isEmpty() ? req.requesterName() : shortUuid(req.requesterUuid());
        Flex info = Flex.column();
        info.sizing(Sizing.expand(), Sizing.content());
        Flex fromLine = Flex.row().gap(3);
        fromLine.sizing(Sizing.content(), Sizing.content());
        fromLine.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.from"), INK));
        fromLine.item(text(Component.literal(requesterName), ACCENT));
        info.item(fromLine);
        info.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.join_request_row.wants_to_join"), INK_DIM));
        row.item(info);

        long remainingSeconds = Math.max(0L, req.expiresAtTick() - currentServerTick()) / 20L;
        Label countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, TextNode.Align.CENTER);
        joinRequestCountdownLabels.put(req.requesterUuid(), countdown);
        row.item(countdown);

        UUID requesterUuid = req.requesterUuid();
        Button accept = ArenasParchment.button(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), 54, 18, () -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "accept_request", requesterUuid));
        });
        row.item(accept);

        Button decline = ArenasParchment.button(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), 58, 18, () -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "decline_request", requesterUuid));
        });
        row.item(decline);
        return row;
    }

    // ── Countdowns ──────────────────────────────────────────────────────────────

    private void refreshInviteCountdowns() {
        for (PendingInvite invite : myInvites) {
            Label label = inviteCountdownLabels.get(invite.lobbyId());
            if (label == null) continue;
            long remainingSeconds = Math.max(0L, invite.expiresAtTick() - currentServerTick()) / 20L;
            label.text(Component.literal(formatRemaining(remainingSeconds)));
            label.color(remainingSeconds <= 15 ? WARN : INK_MID);
        }
    }

    private void refreshJoinRequestCountdowns() {
        for (PendingJoinRequest req : myJoinRequests) {
            Label label = joinRequestCountdownLabels.get(req.requesterUuid());
            if (label == null) continue;
            long remainingSeconds = Math.max(0L, req.expiresAtTick() - currentServerTick()) / 20L;
            label.text(Component.literal(formatRemaining(remainingSeconds)));
            label.color(remainingSeconds <= 15 ? WARN : INK_MID);
        }
    }

    // ── Widgets & helpers ─────────────────────────────────────────────────────────

    private void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    /** Content-width accent button, 18 px tall — the old owo smallButton renderer lives in ArenasParchment.WIDGETS. */
    private Button smallButton(Component text, Runnable action) {
        return ArenasParchment.button(text, action);
    }

    private Button dangerButton(Component text, float width, Runnable action) {
        Button button = new Button(width, 18, text.copy().withStyle(ChatFormatting.RED), action);
        button.style(DANGER_STYLE);
        return button;
    }

    private Button segmentButton(String label, int accentColor, boolean active, boolean selected, Runnable action) {
        Button button = new Button(100, 26, Component.literal(label), action);
        button.style(segmentStyle(accentColor, selected));
        button.enabled(active);
        return button;
    }

    /** Segmented-control skin: tinted fill + accent outline when selected, PANEL_2/ROW_BG otherwise. */
    private WidgetStyle segmentStyle(int accentColor, boolean selected) {
        WidgetStyle.Skin idle = selected
            ? new WidgetStyle.Skin.Flat((accentColor & 0x00FFFFFF) | 0x26000000, accentColor, 1, 0)
            : new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0);
        WidgetStyle.Skin hover = selected
            ? new WidgetStyle.Skin.Flat((accentColor & 0x00FFFFFF) | 0x26000000, accentColor, 1, 0)
            : new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0);
        return new WidgetStyle(
            idle, hover, hover,
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            selected ? accentColor : INK, selected ? accentColor : INK, INK_DIM, INK_DIM, accentColor, 0x557A4A1E,
            26, 16, 4, false);
    }

    private Flex segmentedControl(Button... buttons) {
        Flex row = Flex.row().alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        int n = buttons.length;
        int base = 100 / n;
        for (int i = 0; i < n; i++) {
            int pct = (i == n - 1) ? (100 - base * (n - 1)) : base;
            Flex cell = Flex.column().padding(Insets.of(0, i == n - 1 ? 0 : 3, 0, i == 0 ? 0 : 3));
            cell.sizing(Sizing.fill(pct / 100f), Sizing.content());
            buttons[i].sizing(Sizing.fill(), Sizing.fixed(26));
            cell.item(buttons[i]);
            row.item(cell);
        }
        return row;
    }

    private ToggleSwitch toggleSwitch(boolean active, BooleanSupplier on, Runnable action) {
        ToggleSwitch toggle = new ToggleSwitch(DANGER, on, action);
        toggle.enabled(active);
        return toggle;
    }

    /** The custom-drawn track+knob switch the owo screen rendered via a button renderer lambda. */
    private static final class ToggleSwitch extends Widget {
        private final int onColor;
        private final BooleanSupplier on;
        private final Runnable onToggle;

        ToggleSwitch(int onColor, BooleanSupplier on, Runnable onToggle) {
            super(38, 18);
            this.onColor = onColor;
            this.on = on;
            this.onToggle = onToggle;
        }

        @Override
        protected void rebuild() {
            boolean isOn = on.getAsBoolean();
            int track = isOn ? ((onColor & 0x00FFFFFF) | 0x55000000) : PANEL_2;
            int border = isOn ? onColor : HAIRLINE;
            add(Shapes.rect(0, 0, width, height).fill(track).stroke(border, 1));
            float knobW = (int) (width / 2) - 3;
            float knobX = isOn ? (width - knobW - 2) : 2;
            int knobColor = isOn ? onColor : INK;
            add(Shapes.rect(knobX, 2, knobW, height - 4).fill(knobColor));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!enabled || button != 0) return false;
            UiSounds.click();
            onToggle.run();
            return true;
        }

        @Override
        public Cursor cursor() {
            return enabled ? Cursor.HAND : Cursor.DEFAULT;
        }
    }

    private TextNode dimLabel(Component text) {
        return ArenasParchment.text(text, INK_DIM);
    }

    private TextNode text(Component text, int color) {
        return ArenasParchment.text(text, color);
    }

    private Flex sectionHeader(Component title, Integer count) {
        return sectionHeaderWithAction(title, count, null);
    }

    private Flex sectionHeaderWithAction(Component title, Integer count, @Nullable Button action) {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(text(title, INK_DIM));
        if (count != null) {
            row.item(text(Component.literal("· " + count), ACCENT));
        }
        row.spacer();
        if (action != null) {
            row.item(action);
        }
        return row;
    }

    private Flex membersSectionHeader(int readyCount, int count, int max) {
        Flex row = Flex.row().gap(6).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.section.members"), INK_DIM));
        row.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.members_summary", readyCount, count, max), ACCENT));
        return row;
    }

    private Flex rowDivider(int color) {
        Flex divider = Flex.row();
        divider.sizing(Sizing.fill(), Sizing.fixed(1));
        divider.backgroundFill(color);
        return divider;
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private TextNode controlCaption(String caption) {
        return ArenasParchment.text(Component.literal(caption), INK_DIM);
    }

    private Flex infoColumn(String caption, TextNode value, Sizing width) {
        Flex col = Flex.column().gap(3);
        col.sizing(width, Sizing.content());
        col.item(ArenasParchment.text(Component.literal(caption), INK_DIM));
        col.item(value);
        return col;
    }

    private Label headerCell(String text, Sizing sizing) {
        Label label = ArenasParchment.label(10, Component.literal(text), INK_DIM);
        label.sizing(sizing, Sizing.content());
        return label;
    }

    private Label fixedText(Component text, float width, int color, TextNode.Align alignment) {
        Label label = ArenasParchment.label(width, text, color);
        label.align(alignment);
        return label;
    }

    private Flex badge(Component text, int color) {
        Flex tag = Flex.row().padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.sizing(Sizing.content(), Sizing.content());
        tag.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        tag.item(this.text(text, color));
        return tag;
    }

    private Flex fixedBadge(Component text, float width, int color) {
        Flex tag = Flex.row().padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.sizing(Sizing.fixed(width), Sizing.content());
        tag.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        Label label = ArenasParchment.label(100, text, color);
        label.align(TextNode.Align.CENTER);
        label.sizing(Sizing.expand(), Sizing.content());
        tag.item(label);
        return tag;
    }

    private Flex rowPanel(boolean accentLeft) {
        Flex row = Flex.row().padding(Insets.of(6));
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(accentLeft ? ROW_BG_ALT : ROW_BG, accentLeft ? ACCENT_DARK : HAIRLINE, 1);
        return row;
    }

    private TextNode smallMeta(String text) {
        return smallMeta(text, INK_DIM);
    }

    private TextNode smallMeta(String text, int color) {
        return ArenasParchment.text(Component.literal(text), color);
    }

    private int visibilityColor(LobbyVisibility visibility) {
        return switch (visibility) {
            case PUBLIC -> GOOD;
            case FRIENDS -> INFO;
            case PRIVATE -> WARN;
        };
    }

    private int statusColor(LobbyStatus status) {
        return switch (status) {
            case READY -> GOOD;
            case IN_RUN -> WARN;
            case DISBANDED -> DANGER;
            default -> INFO;
        };
    }

    private String statusLabel(LobbyStatus status) {
        return switch (status) {
            case IN_RUN -> "IN RUN";
            default -> status.name();
        };
    }

    private Flex spacer(int px) {
        return ArenasParchment.spacer(px);
    }

    private Optional<Lobby> resolveLobbyForInvite(PendingInvite invite) {
        ArenaControllerBlockEntity c = controller();
        if (c != null) {
            for (Lobby lobby : c.getLobbies()) {
                if (lobby.lobbyId().equals(invite.lobbyId())) {
                    return Optional.of(lobby);
                }
            }
        }
        return Optional.empty();
    }

    private boolean isOnline(UUID uuid) {
        if (minecraft == null || minecraft.getConnection() == null) {
            return false;
        }
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            if (uuid.equals(info.getProfile().getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean allOnline(Lobby lobby) {
        for (UUID uuid : lobby.members()) {
            if (!isOnline(uuid)) {
                return false;
            }
        }
        return true;
    }

    private long currentServerTick() {
        return minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() : 0L;
    }

    private void captureScrollY() {
        savedScrollY = contentScroll == null ? 0f : contentScroll.scrollY();
    }

    private static String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private static String titleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String formatRemaining(long seconds) {
        long mins = seconds / 60L;
        long sec = seconds % 60L;
        return mins + ":" + String.format("%02d", sec);
    }
}
