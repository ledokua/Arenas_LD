package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.packet.AcceptInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.AcceptJoinRequestPayload;
import net.ledok.arenas_ld.dungeon.packet.CreateLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineJoinRequestPayload;
import net.ledok.arenas_ld.dungeon.packet.InvitePlayerPayload;
import net.ledok.arenas_ld.dungeon.packet.JoinLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.RequestJoinPayload;
import net.ledok.arenas_ld.dungeon.packet.KickFromLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.LeaveLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyHardcorePayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyTierPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyVisibilityPayload;
import net.ledok.arenas_ld.dungeon.packet.StartRunPayload;
import net.ledok.arenas_ld.dungeon.packet.ToggleReadyPayload;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.screen.ArenasParchment;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.vectorlib.client.canvas.CanvasNode;
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
import net.ledok.arenas_ld.screen.FitCanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import net.ledok.arenas_ld.platform.ArenasNetwork;

public class DungeonControllerScreen extends FitCanvasHandledScreen<DungeonControllerScreenHandler> {
    private static final int INVITE_DROPDOWN_MAX_HEIGHT = 154;
    // Design (forced-integer-GUI-scale) shell size; keeps the shell a fixed size and the font crisp
    // regardless of window size, matching the shop/AH screens in Economy_LD.
    private static final int DESIGN_W = 560;
    private static final int DESIGN_H = 480;

    /** Close (×) button look: quiet panel that lights up to the row color on hover. */
    private static final WidgetStyle CLOSE_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL, HAIRLINE_HI, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE_HI, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE_HI, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL, HAIRLINE_HI, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, INK, INK_MID, INK_DIM, HAIRLINE_HI, 0x557A4A1E,
            18, 16, 4, false);

    /** Kick button look: translucent red fill that deepens on hover, DANGER border. */
    private static final WidgetStyle DANGER_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x1F000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x44000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x44000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x1F000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            DANGER, DANGER, INK_DIM, INK_DIM, DANGER, 0x557A4A1E,
            18, 16, 4, false);

    /** Chevron (▲/▼) button: bare glyph, no fill or border — the owo screen custom-rendered just the glyph. */
    private static final WidgetStyle CHEVRON_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(0x00000000, 0, 0, 0),
            new WidgetStyle.Skin.Flat(0x00000000, 0, 0, 0),
            new WidgetStyle.Skin.Flat(0x00000000, 0, 0, 0),
            new WidgetStyle.Skin.Flat(0x00000000, 0, 0, 0),
            new WidgetStyle.Skin.Flat(0x00000000, 0, 0, 0),
            new WidgetStyle.Skin.Flat(0x00000000, 0, 0, 0),
            new WidgetStyle.Skin.Flat(0x00000000, 0, 0, 0),
            INK_MID, INK_MID, INK_MID, INK_DIM, 0x00000000, 0x557A4A1E,
            20, 20, 4, false);

    private enum Tab {
        LOBBIES,
        MY_LOBBY,
        LEADERBOARD,
        INVITES
    }

    private enum InviteStatus {
        ONLINE,
        IN_LOBBY,
        IN_RUN,
        BUSY
    }

    private record InviteCandidate(UUID uuid, String name, InviteStatus status) {
        boolean selectable() {
            return status == InviteStatus.ONLINE;
        }
    }

    private record MemberRowRefs(Flex statusSquare, TextNode statusGlyph, Label connectionLabel,
                                 Badge readyBadge, Button kickButton) {}

    private record Badge(Flex box, TextNode label) {}

    private final List<Lobby> visibleLobbies;
    private final List<PendingInvite> myInvites;
    private final List<PendingJoinRequest> myJoinRequests;
    private Optional<Lobby> ownLobby;
    private java.util.Set<UUID> busyPlayers;
    private Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards = Map.of();
    private DifficultyTier leaderboardTier = DifficultyTier.NORMAL;

    private Tab currentTab = Tab.LOBBIES;
    private int lastQueuePosition = 0;
    private String footerError;
    private long snapshotServerTick;
    private long snapshotEpochMs;
    private long lastCountdownSecond = -1L;

    private Flex contentArea;
    private ScrollPanel contentScroll;
    private Flex footerActions;
    private Label footerLabel;
    private TabButton lobbiesTabButton;
    private TabButton myLobbyTabButton;
    private TabButton leaderboardTabButton;
    private TabButton invitesTabButton;
    private IdSuggestionDropdown.Field inviteField;
    private String inviteInput = "";
    private boolean inviteDropdownOpen = false;
    private Flex inviteFieldRow;
    private Flex inviteDropdownPanel;
    private Button inviteChevron;
    private double savedScrollProgress = 0.0D;
    private Double pendingScrollProgress = null;
    private final Map<UUID, Label> inviteCountdownLabels = new HashMap<>();
    private final Map<UUID, Label> joinRequestCountdownLabels = new HashMap<>();
    private final Map<UUID, MemberRowRefs> memberRowRefs = new HashMap<>();
    private TextNode summaryOwnerLabel;
    private TextNode summaryMembersLabel;
    private TextNode membersSummaryLabel;
    private TextNode summaryTierValue;
    private TextNode summaryVisibilityValue;
    private TextNode summaryHardcoreValue;
    private Button readyToggleButton;
    private Button leaveLobbyButton;
    private Button startRunButton;

    public DungeonControllerScreen(DungeonControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(DESIGN_W, DESIGN_H), Placement.Screen.center());
        this.visibleLobbies = new ArrayList<>(handler.getVisibleLobbies());
        this.myInvites = new ArrayList<>(handler.getMyInvites());
        this.myJoinRequests = new ArrayList<>(handler.getMyJoinRequests());
        this.ownLobby = handler.getOwnLobby();
        this.busyPlayers = handler.getBusyPlayers();
        this.topLeaderboards = handler.getTopLeaderboards();
        this.snapshotServerTick = handler.getServerGameTick();
        this.snapshotEpochMs = System.currentTimeMillis();
        canvas.theme(ArenasParchment.THEME);
        fillWindow();
        // Lobby rows are fixed-badge columns ~540 px wide: below this the whole panel
        // scales down instead of the badges sliding over the owner column.
        minLogicalWidth(DESIGN_W + 8);
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

    private void buildAll() {
        canvas.clear();
        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);

        Flex shell = root.item(Flex.column());
        ArenasParchment.sizeShell(shell, canvas.width(), canvas.height(), DESIGN_W, DESIGN_H);
        shell.background(ArenasParchment.panel());
        shell.padding(Insets.of(8)); // keep content off the parchment border art

        shell.item(buildHeader());
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

        syncFromMenu(false);
        rebuildUi();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (pendingScrollProgress != null && contentScroll != null) {
            applyScrollProgress(pendingScrollProgress);
            pendingScrollProgress = null;
        }
        if ((currentTab == Tab.LOBBIES || currentTab == Tab.INVITES) && (!myInvites.isEmpty() || !myJoinRequests.isEmpty())) {
            long now = approximateServerTick() / 20L;
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

    private boolean pointInside(CanvasNode node, float canvasX, float canvasY) {
        if (node == null) {
            return false;
        }
        float[] local = node.rootToLocal(canvasX, canvasY);
        return node.hitTest(local[0], local[1]);
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
        titleLine.item(ArenasParchment.text(Component.translatable("container.arenas_ld.dungeon_controller"), INK));
        titleLine.item(statusBadge());
        info.item(titleLine);

        Flex meta = Flex.row().gap(10).alignItems(Align.CENTER);
        meta.sizing(Sizing.content(), Sizing.content());
        meta.item(smallMeta("POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()));
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

    private Flex tabCell(TabButton button) {
        Flex cell = Flex.column().padding(Insets.of(0, 2, 0, 2));
        cell.sizing(Sizing.fill(0.25f), Sizing.content());
        button.sizing(Sizing.fill(), Sizing.fixed(20));
        cell.item(button);
        return cell;
    }

    private Flex buildFooter() {
        Flex footer = Flex.row().gap(6).padding(Insets.of(6));
        footer.sizing(Sizing.fill(), Sizing.fixed(34));
        footer.backgroundFill(PANEL_2);

        footerLabel = Label.of(100, Component.empty());
        footerLabel.color(DANGER);
        footerLabel.sizing(Sizing.expand(), Sizing.content());
        footer.item(footerLabel);

        footerActions = Flex.row().gap(4);
        footerActions.sizing(Sizing.content(), Sizing.content());
        footer.item(footerActions);
        return footer;
    }

    private TabButton tabButton(String key, Tab tab) {
        return new TabButton(Component.translatable(key), () -> {
            currentTab = tab;
            footerError = null;
            savedScrollProgress = 0.0D;
            rebuildUi();
        });
    }

    /** The owo tab renderer: PANEL fill + 2px accent bottom bar when selected, hairline outline otherwise. */
    private static final class TabButton extends Widget {
        private Component label;
        private final Runnable action;

        TabButton(Component label, Runnable action) {
            super(120, 20);
            this.label = label;
            this.action = action;
        }

        TabButton label(Component label) {
            this.label = label;
            refresh();
            return this;
        }

        @Override
        protected void rebuild() {
            boolean selected = !enabled;
            int fill = selected ? PANEL : (hovered || focused ? ROW_BG : PANEL_2);
            add(Shapes.rect(0, 0, width, height).fill(fill));
            if (selected) {
                add(Shapes.rect(0, height - 2, width, 2).fill(ACCENT));
            } else {
                add(Shapes.rect(0, 0, width, height).stroke(HAIRLINE, 1));
            }
            add(TextNode.of(label).at(width / 2, textY(height)).align(TextNode.Align.CENTER)
                .color(enabled ? INK : INK_MID).shadow(false));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!enabled || button != 0) return false;
            UiSounds.click();
            action.run();
            return true;
        }

        @Override
        public boolean focusable() { return enabled; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (!enabled) return false;
            if (key == 257 || key == 335 || key == 32) { UiSounds.click(); action.run(); return true; }
            return false;
        }
    }

    private void rebuildUi() {
        if (contentArea == null) {
            return;
        }
        captureScrollProgress();
        contentArea.clear();
        footerActions.clear();
        inviteCountdownLabels.clear();
        joinRequestCountdownLabels.clear();
        memberRowRefs.clear();
        summaryOwnerLabel = null;
        summaryMembersLabel = null;
        membersSummaryLabel = null;
        summaryTierValue = null;
        summaryVisibilityValue = null;
        summaryHardcoreValue = null;
        readyToggleButton = null;
        leaveLobbyButton = null;
        startRunButton = null;
        inviteFieldRow = null;
        inviteDropdownPanel = null;
        inviteChevron = null;
        inviteDropdownOpen = false;

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
        if (contentScroll != null && savedScrollProgress > 0.0D) {
            applyScrollProgress(savedScrollProgress);
            pendingScrollProgress = savedScrollProgress;
        } else {
            pendingScrollProgress = null;
        }
    }

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

        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        boolean isOwner = self != null && ownLobby.isPresent() && ownLobby.get().ownerUuid().equals(self);
        if (isOwner) {
            UUID ownedLobbyId = ownLobby.get().lobbyId();
            List<PendingJoinRequest> incoming = myJoinRequests.stream()
                .filter(r -> r.lobbyId().equals(ownedLobbyId))
                .toList();
            contentArea.item(ArenasParchment.spacer(8));
            contentArea.item(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.join_requests"), incoming.size()));
            if (incoming.isEmpty()) {
                contentArea.item(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_join_requests")));
            } else {
                contentArea.item(joinRequestHeaderRow());
                for (PendingJoinRequest req : incoming) {
                    contentArea.item(joinRequestRow(req));
                }
            }
        }
    }

    private Flex joinRequestHeaderRow() {
        Flex row = Flex.row().gap(6).padding(Insets.of(2, 6, 2, 6));
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.requester"), Sizing.expand()));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.tier"), Sizing.fixed(86)));
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

        row.item(fixedBadge(Component.literal(req.tier().name()), 86, tierColor(req.tier())).box());

        long remainingSeconds = Math.max(0L, req.expiresAtTick() - approximateServerTick()) / 20L;
        Label countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, TextNode.Align.CENTER);
        joinRequestCountdownLabels.put(req.requesterUuid(), countdown);
        row.item(countdown);

        UUID requesterUuid = req.requesterUuid();
        row.item(smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), 54, () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new AcceptJoinRequestPayload(menu.getBlockPos(), requesterUuid));
        }));

        row.item(smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), 58, () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new DeclineJoinRequestPayload(menu.getBlockPos(), requesterUuid));
        }));

        return row;
    }

    private void buildLeaderboardContent() {
        contentArea.item(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.col.tier")));
        contentArea.item(leaderboardTierControls());

        contentArea.item(ArenasParchment.spacer(4));
        contentArea.item(leaderboardBanner(leaderboardTier));

        List<LeaderboardEntry> entries = new ArrayList<>(topLeaderboards.getOrDefault(leaderboardTier, List.of()));
        entries.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds));

        contentArea.item(ArenasParchment.spacer(4));
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
                list.item(leaderboardRow(i + 1, entries.get(i), leaderboardTier));
            }
        }
        contentArea.item(list);
    }

    private Flex leaderboardTierControls() {
        SegmentButton easy = leaderboardTierButton(DifficultyTier.EASY, "EASY");
        SegmentButton normal = leaderboardTierButton(DifficultyTier.NORMAL, "NORMAL");
        SegmentButton hard = leaderboardTierButton(DifficultyTier.HARD, "HARD");
        SegmentButton nightmare = leaderboardTierButton(DifficultyTier.NIGHTMARE, "NIGHTMARE");
        return segmentedControl(easy, normal, hard, nightmare);
    }

    private SegmentButton leaderboardTierButton(DifficultyTier tier, String label) {
        return segmentButton(label, tierColor(tier), true,
            () -> leaderboardTier == tier,
            () -> {
                leaderboardTier = tier;
                rebuildUi();
            });
    }

    private Flex leaderboardBanner(DifficultyTier tier) {
        int color = tierColor(tier);
        Flex banner = Flex.row().alignItems(Align.CENTER);
        banner.sizing(Sizing.fill(), Sizing.fixed(34));
        banner.backgroundFill((color & 0x00FFFFFF) | 0x1F000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(3), Sizing.fill());
        accent.backgroundFill(color);
        banner.item(accent);
        Flex inner = Flex.row().gap(8).padding(Insets.of(0, 10, 0, 10)).alignItems(Align.CENTER);
        inner.sizing(Sizing.expand(), Sizing.content());
        inner.item(badge(Component.translatable("gui.arenas_ld.dungeon_controller.ui.leaderboard.banner_badge", tier.name()), color));
        inner.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.leaderboard.banner_desc", titleCase(tier.name())), INK));
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
        Label timeHeader = headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.time"), Sizing.fixed(64));
        timeHeader.align(TextNode.Align.RIGHT);
        row.item(timeHeader);
        return row;
    }

    private Flex leaderboardRow(int rank, LeaderboardEntry entry, DifficultyTier tier) {
        boolean top = rank == 1;
        int color = tierColor(tier);
        Flex row = Flex.row().gap(8).padding(Insets.of(7, 8, 7, 8)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(top ? ((color & 0x00FFFFFF) | 0x1A000000) : ROW_BG);

        Label rankLabel = ArenasParchment.label(50, Component.literal(top ? "★1" : ("#" + rank)), top ? color : INK_MID);
        rankLabel.sizing(Sizing.fixed(50), Sizing.content());
        row.item(rankLabel);

        Label nameLabel = ArenasParchment.label(100, Component.literal(entry.playerName()), INK);
        nameLabel.sizing(Sizing.expand(), Sizing.content());
        row.item(nameLabel);

        Label whenLabel = fixedText(Component.literal(
            net.ledok.arenas_ld.screen.RelativeTime.ago(entry.recordedAtEpochMillis())), 44, INK_MID, TextNode.Align.RIGHT);
        row.item(whenLabel);

        Label timeLabel = fixedText(Component.literal(formatClock(entry.timeSeconds())), 64, top ? color : INK_MID, TextNode.Align.RIGHT);
        row.item(timeLabel);
        return row;
    }

    private static String formatClock(int totalSeconds) {
        int mins = Math.max(0, totalSeconds) / 60;
        int secs = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private void buildLobbiesContent() {
        Button create = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.create"), () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new CreateLobbyPayload(menu.getBlockPos()));
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

    private Flex inviteRow(PendingInvite invite) {
        Flex row = rowPanel(false);
        row.gap(6);

        Optional<Lobby> inviteLobby = resolveLobbyForInvite(invite);
        String ownerName = inviteLobby.map(Lobby::ownerName)
            .filter(n -> !n.isEmpty())
            .orElseGet(() -> !invite.ownerName().isEmpty() ? invite.ownerName() : shortUuid(invite.lobbyId()));
        String inviterName = inviteLobby
            .map(l -> l.memberNames().get(invite.inviterUuid()))
            .filter(n -> !n.isEmpty())
            .orElse(ownerName);
        DifficultyTier inviteTier = inviteLobby.map(Lobby::selectedTier).orElse(invite.tier());

        long remainingTicks = Math.max(0L, invite.expiresAtTick() - approximateServerTick());
        long remainingSeconds = remainingTicks / 20L;
        Flex inviteInfo = Flex.column();
        inviteInfo.sizing(Sizing.expand(), Sizing.content());
        Flex fromLine = Flex.row().gap(3);
        fromLine.sizing(Sizing.content(), Sizing.content());
        fromLine.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.from"), INK));
        fromLine.item(text(Component.literal(inviterName), ACCENT));
        inviteInfo.item(fromLine);
        inviteInfo.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.owner", ownerName.toUpperCase()), INK_DIM));
        row.item(inviteInfo);
        row.item(fixedText(Component.literal("-"), 40, INK_DIM, TextNode.Align.CENTER));

        row.item(fixedBadge(Component.literal(inviteTier.name()), 86, tierColor(inviteTier)).box());
        Label countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, TextNode.Align.CENTER);
        inviteCountdownLabels.put(invite.lobbyId(), countdown);
        row.item(countdown);

        row.item(smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), 54, () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new AcceptInvitePayload(menu.getBlockPos(), invite.lobbyId()));
        }));

        row.item(smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), 58, () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new DeclineInvitePayload(menu.getBlockPos(), invite.lobbyId()));
        }));

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
        ownerInfo.item(text(Component.literal(shortUuid(lobby.lobbyId()).toUpperCase()), INK_DIM));
        row.item(ownerInfo);
        row.item(fixedBadge(Component.literal(lobby.members().size() + "/" + menu.getMaxPartySize()), 44, INK_MID).box());
        row.item(fixedBadge(Component.literal(lobby.selectedTier().name()), 70, tierColor(lobby.selectedTier())).box());

        Flex visCell = Flex.row().gap(4).padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        visCell.sizing(Sizing.fixed(78), Sizing.content());
        int visColor = visibilityColor(lobby.visibility());
        visCell.backgroundFill((visColor & 0x00FFFFFF) | 0x22000000, (visColor & 0x00FFFFFF) | 0x55000000, 1);
        visCell.item(text(Component.literal("●"), visColor));
        visCell.item(text(Component.literal(lobby.visibility().name()), visColor));
        row.item(visCell);

        row.item(fixedBadge(Component.literal(statusLabel(lobby.status())), 82, statusColor(lobby.status())).box());

        UUID lobbyId = lobby.lobbyId();
        boolean isMine = ownLobby.isPresent() && ownLobby.get().lobbyId().equals(lobbyId);
        boolean isFull = lobby.isFull(menu.getMaxPartySize());
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
                ArenasNetwork.sendToServer(new JoinLobbyPayload(menu.getBlockPos(), lobbyId));
            });
            actionButton.enabled(!isMine && ownLobby.isEmpty());
        } else {
            UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
            boolean alreadyRequested = self != null && myJoinRequests.stream()
                .anyMatch(r -> r.lobbyId().equals(lobbyId) && r.requesterUuid().equals(self));
            if (alreadyRequested) {
                actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.pending"), () -> {});
                actionButton.enabled(false);
            } else {
                actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.request"), () -> {
                    footerError = null;
                    ArenasNetwork.sendToServer(new RequestJoinPayload(menu.getBlockPos(), lobbyId));
                });
                actionButton.enabled(!isMine && ownLobby.isEmpty());
            }
        }
        actionButton.sizing(Sizing.fill(), Sizing.fixed(18));
        actionCell.item(actionButton);
        row.item(actionCell);
        return row;
    }

    private void buildMyLobbyContent() {
        if (ownLobby.isEmpty()) {
            contentArea.item(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_own_lobby")));
            return;
        }

        Lobby lobby = ownLobby.get();
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : UUID.randomUUID();
        boolean isOwner = lobby.ownerUuid().equals(self);

        int queuePosition = menu.getQueuePosition();
        if (queuePosition >= 1) {
            Flex queueBanner = Flex.row().gap(8).padding(Insets.of(4, 8, 4, 8)).alignItems(Align.CENTER);
            queueBanner.sizing(Sizing.fill(), Sizing.fixed(28));
            queueBanner.backgroundFill((WARN & 0x00FFFFFF) | 0x1A000000, (WARN & 0x00FFFFFF) | 0x55000000, 1);
            Flex queueAccent = Flex.column();
            queueAccent.sizing(Sizing.fixed(3), Sizing.fill());
            queueAccent.backgroundFill(WARN);
            queueBanner.item(queueAccent);
            queueBanner.item(badge(Component.translatable("gui.arenas_ld.dungeon_controller.ui.queue.in_line", queuePosition), WARN));
            int etaSeconds = menu.getEstimatedWaitSeconds();
            if (etaSeconds > 0) {
                queueBanner.item(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.queue.eta", formatClock(etaSeconds)), INK_MID));
            }
            contentArea.item(queueBanner);
            contentArea.item(ArenasParchment.spacer(4));
        }

        summaryOwnerLabel = text(Component.literal(lobby.ownerName()), ACCENT);
        summaryMembersLabel = text(Component.literal(lobby.members().size() + " / " + menu.getMaxPartySize()), INK);
        summaryTierValue = text(Component.literal(titleCase(lobby.selectedTier().name())), tierColor(lobby.selectedTier()));
        summaryVisibilityValue = text(Component.literal(titleCase(lobby.visibility().name())), visibilityColor(lobby.visibility()));
        summaryHardcoreValue = text(Component.translatable(lobby.hardcoreEnabled() ? "gui.arenas_ld.dungeon_controller.ui.hardcore.on" : "gui.arenas_ld.dungeon_controller.ui.hardcore.off"), lobby.hardcoreEnabled() ? DANGER : INK_MID);

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
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.owner"), summaryOwnerLabel, Sizing.fill(0.24f)));
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.tier"), summaryTierValue, Sizing.fill(0.18f)));
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.members"), summaryMembersLabel, Sizing.fill(0.18f)));
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility"), summaryVisibilityValue, Sizing.fill(0.18f)));
        summary.item(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.hardcore"), summaryHardcoreValue, Sizing.fill(0.18f)));
        contentArea.item(summary);
        contentArea.item(membersSectionHeader(lobby.readyMembers().size(), lobby.members().size(), menu.getMaxPartySize()));

        Flex memberList = Flex.column();
        memberList.sizing(Sizing.fill(), Sizing.content());
        memberList.item(memberHeaderRow(isOwner));
        memberList.item(rowDivider(HAIRLINE_HI));
        List<UUID> orderedMembers = lobby.members().stream()
            .sorted(Comparator.comparing((UUID m) -> m.equals(lobby.ownerUuid()) ? 0 : 1)
                .thenComparing(m -> lobby.memberNames().getOrDefault(m, shortUuid(m)).toLowerCase(java.util.Locale.ROOT)))
            .limit(5)
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

        contentArea.item(ArenasParchment.spacer(6));
        contentArea.item(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.ui.section.owner_controls"), null));

        contentArea.item(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.col.tier")));
        contentArea.item(ownerTierControls(isOwner));

        contentArea.item(ArenasParchment.spacer(4));
        contentArea.item(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility")));
        contentArea.item(ownerVisibilityControls(isOwner));

        contentArea.item(ArenasParchment.spacer(4));
        contentArea.item(hardcoreControl(isOwner));

        contentArea.item(ArenasParchment.spacer(4));
        contentArea.item(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.section.invite_player")));
        contentArea.item(ownerInviteControls(isOwner));

        boolean ready = lobby.readyMembers().contains(self);
        boolean allReady = lobby.allReady();
        boolean allOnline = allOnline(lobby);
        boolean canStart = isOwner && allReady && allOnline;

        readyToggleButton = smallButton(Component.translatable(ready
            ? "gui.arenas_ld.dungeon_controller.button.unready"
            : "gui.arenas_ld.dungeon_controller.button.ready"), () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new ToggleReadyPayload(menu.getBlockPos()));
        });
        footerActions.item(readyToggleButton);

        leaveLobbyButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.leave"), () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new LeaveLobbyPayload(menu.getBlockPos()));
        });
        footerActions.item(leaveLobbyButton);

        Button start = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.start"), () -> {
            footerError = null;
            Lobby currentLobby = ownLobby.orElse(null);
            UUID currentSelf = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : UUID.randomUUID();
            if (currentLobby == null) {
                footerError = Component.translatable("gui.arenas_ld.dungeon_controller.no_own_lobby").getString();
                return;
            }
            boolean currentOwner = currentLobby.ownerUuid().equals(currentSelf);
            boolean currentAllReady = currentLobby.allReady();
            boolean currentAllOnline = allOnline(currentLobby);
            if (!currentOwner) {
                footerError = Component.translatable("gui.arenas_ld.dungeon_controller.error.only_owner").getString();
                return;
            }
            if (!currentAllReady) {
                footerError = Component.translatable("gui.arenas_ld.dungeon_controller.error.all_ready_required").getString();
                return;
            }
            if (!currentAllOnline) {
                footerError = Component.translatable("gui.arenas_ld.dungeon_controller.error.all_online_required").getString();
                return;
            }
            ArenasNetwork.sendToServer(new StartRunPayload(menu.getBlockPos()));
        });
        start.enabled(canStart);
        startRunButton = start;
        footerActions.item(startRunButton);
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
        TextNode statusGlyph = text(Component.literal(online ? "✓" : "!"), online ? GOOD : WARN);
        applyStatusSquare(statusSquare, online);
        statusSquare.item(statusGlyph);
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
        connectionLabel.sizing(Sizing.fixed(84), Sizing.content());
        row.item(connectionLabel);

        Badge readyBadge = fixedBadge(Component.translatable(ready ? "gui.arenas_ld.dungeon_controller.ui.member.ready" : "gui.arenas_ld.dungeon_controller.ui.member.not_ready"), 84, ready ? GOOD : INK_DIM);
        row.item(readyBadge.box());

        Button kickButton = null;
        if (isOwner && !isLobbyOwner) {
            kickButton = dangerButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.kick"), 54, () -> {
                footerError = null;
                ArenasNetwork.sendToServer(new KickFromLobbyPayload(menu.getBlockPos(), member));
            });
            row.item(kickButton);
        } else {
            Flex kickSpace = Flex.row();
            kickSpace.sizing(Sizing.fixed(54), Sizing.content());
            row.item(kickSpace);
        }
        memberRowRefs.put(member, new MemberRowRefs(statusSquare, statusGlyph, connectionLabel, readyBadge, kickButton));
        return row;
    }

    private Flex ownerTierControls(boolean isOwner) {
        SegmentButton easy = segmentButton("EASY", tierColor(DifficultyTier.EASY), isOwner && isTierEnabled(DifficultyTier.EASY),
            () -> ownLobby.map(Lobby::selectedTier).orElse(null) == DifficultyTier.EASY,
            () -> ArenasNetwork.sendToServer(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.EASY)));
        SegmentButton normal = segmentButton("NORMAL", tierColor(DifficultyTier.NORMAL), isOwner && isTierEnabled(DifficultyTier.NORMAL),
            () -> ownLobby.map(Lobby::selectedTier).orElse(null) == DifficultyTier.NORMAL,
            () -> ArenasNetwork.sendToServer(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.NORMAL)));
        SegmentButton hard = segmentButton("HARD", tierColor(DifficultyTier.HARD), isOwner && isTierEnabled(DifficultyTier.HARD),
            () -> ownLobby.map(Lobby::selectedTier).orElse(null) == DifficultyTier.HARD,
            () -> ArenasNetwork.sendToServer(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.HARD)));
        SegmentButton nightmare = segmentButton("NIGHTMARE", tierColor(DifficultyTier.NIGHTMARE), isOwner && isTierEnabled(DifficultyTier.NIGHTMARE),
            () -> ownLobby.map(Lobby::selectedTier).orElse(null) == DifficultyTier.NIGHTMARE,
            () -> ArenasNetwork.sendToServer(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.NIGHTMARE)));
        return segmentedControl(easy, normal, hard, nightmare);
    }

    private boolean isTierEnabled(DifficultyTier tier) {
        net.ledok.arenas_ld.dungeon.run.TierConfig config = menu.getTiers().get(tier);
        return config == null || config.enabled();
    }

    private Flex ownerVisibilityControls(boolean isOwner) {
        SegmentButton pub = segmentButton("PUBLIC", visibilityColor(LobbyVisibility.PUBLIC), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.PUBLIC,
            () -> ArenasNetwork.sendToServer(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.PUBLIC)));
        SegmentButton fr = segmentButton("FRIENDS", visibilityColor(LobbyVisibility.FRIENDS), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.FRIENDS,
            () -> ArenasNetwork.sendToServer(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.FRIENDS)));
        SegmentButton pr = segmentButton("PRIVATE", visibilityColor(LobbyVisibility.PRIVATE), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.PRIVATE,
            () -> ArenasNetwork.sendToServer(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.PRIVATE)));
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

        ToggleSwitch toggle = new ToggleSwitch(DANGER,
            () -> ownLobby.map(Lobby::hardcoreEnabled).orElse(false),
            () -> {
                footerError = null;
                boolean currentHardcore = ownLobby.map(Lobby::hardcoreEnabled).orElse(false);
                ArenasNetwork.sendToServer(new SetLobbyHardcorePayload(menu.getBlockPos(), !currentHardcore));
            });
        toggle.enabled(isOwner);
        toggle.bind(() -> ownLobby.map(Lobby::hardcoreEnabled).orElse(false));
        row.item(toggle);
        return row;
    }

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

        // The old owo text box kept vanilla's default 32-char cap; preserved here.
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

        Button invite = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.invite"), 58, this::sendInvite);
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
            footerError = Component.translatable("gui.arenas_ld.dungeon_controller.error.invitee_not_found").getString();
            return;
        }
        ArenasNetwork.sendToServer(new InvitePlayerPayload(menu.getBlockPos(), invitee));
        inviteInput = "";
        if (inviteField != null) {
            inviteField.text("");
        }
        closeInviteDropdown();
    }

    private void openInviteDropdown() {
        if (!inviteDropdownOpen) {
            inviteDropdownOpen = true;
            if (inviteChevron != null) {
                inviteChevron.label(Component.literal("▲"));
            }
            refreshInviteDropdown();
            if (contentScroll != null) {
                applyScrollProgress(1.0D);
                pendingScrollProgress = 1.0D;
            }
        }
    }

    private void closeInviteDropdown() {
        if (inviteDropdownOpen) {
            inviteDropdownOpen = false;
            if (inviteChevron != null) {
                inviteChevron.label(Component.literal("▼"));
            }
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
        String statusText = switch (candidate.status()) {
            case ONLINE -> tr("gui.arenas_ld.dungeon_controller.ui.member.online");
            case IN_LOBBY -> tr("gui.arenas_ld.dungeon_controller.ui.candidate.in_lobby");
            case IN_RUN -> tr("gui.arenas_ld.dungeon_controller.ui.candidate.in_run");
            case BUSY -> tr("gui.arenas_ld.dungeon_controller.ui.candidate.busy");
        };
        InviteCandidateRow row = new InviteCandidateRow("@" + candidate.name(), statusText,
            candidate.selectable() ? GOOD : WARN, candidate.selectable(), () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new InvitePlayerPayload(menu.getBlockPos(), candidate.uuid()));
            inviteInput = "";
            if (inviteField != null) {
                inviteField.text("");
            }
            closeInviteDropdown();
        });
        row.sizing(Sizing.fill(), Sizing.fixed(22));
        return row;
    }

    /** The owo candidate-row renderer: hover fill + accent bar, status square, name, right-aligned status. */
    private static final class InviteCandidateRow extends Widget {
        private final String name;
        private final String statusText;
        private final int statusColor;
        private final boolean selectable;
        private final Runnable action;

        InviteCandidateRow(String name, String statusText, int statusColor, boolean selectable, Runnable action) {
            super(100, 22);
            this.name = name;
            this.statusText = statusText;
            this.statusColor = statusColor;
            this.selectable = selectable;
            this.action = action;
        }

        @Override
        protected void rebuild() {
            int alpha = selectable ? 0xFF000000 : 0x73000000;
            boolean hover = selectable && (hovered || focused);
            add(Shapes.rect(0, 0, width, height).fill(hover ? ROW_BG : PANEL_2));
            if (hover) {
                add(Shapes.rect(0, 0, 2, height).fill(ACCENT));
            }
            add(Shapes.rect(8, height / 2 - 4, 8, 8).fill((statusColor & 0x00FFFFFF) | alpha));
            add(TextNode.of(name).at(24, textY(height)).color((INK & 0x00FFFFFF) | alpha).shadow(false));
            add(TextNode.of(statusText).at(width - 10, textY(height)).align(TextNode.Align.RIGHT)
                .color((statusColor & 0x00FFFFFF) | alpha).shadow(false));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!selectable || button != 0) return false;
            UiSounds.click();
            action.run();
            return true;
        }

        @Override
        public boolean focusable() { return selectable; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (!selectable) return false;
            if (key == 257 || key == 335 || key == 32) { UiSounds.click(); action.run(); return true; }
            return false;
        }
    }

    private List<InviteCandidate> inviteCandidates(String filter) {
        List<InviteCandidate> result = new ArrayList<>();
        if (minecraft == null || minecraft.getConnection() == null) {
            return result;
        }
        UUID self = minecraft.player != null ? minecraft.player.getUUID() : null;
        java.util.Set<UUID> myMembers = ownLobby.map(lobby -> new java.util.HashSet<>(lobby.members())).orElseGet(java.util.HashSet::new);
        String needle = filter == null ? "" : filter.trim().toLowerCase(java.util.Locale.ROOT);
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
            if (!needle.isEmpty() && !name.toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                continue;
            }
            result.add(new InviteCandidate(id, name, inviteStatusFor(id)));
        }
        result.sort(Comparator.comparing((InviteCandidate c) -> c.selectable() ? 0 : 1)
            .thenComparing(c -> c.name().toLowerCase(java.util.Locale.ROOT)));
        return result;
    }

    private InviteStatus inviteStatusFor(UUID uuid) {
        if (isBusy(uuid)) {
            return InviteStatus.BUSY;
        }
        for (Lobby lobby : visibleLobbies) {
            if (lobby.members().contains(uuid)) {
                return lobby.status() == LobbyStatus.IN_RUN ? InviteStatus.IN_RUN : InviteStatus.IN_LOBBY;
            }
        }
        return InviteStatus.ONLINE;
    }

    private boolean isBusy(UUID uuid) {
        return busyPlayers != null && busyPlayers.contains(uuid);
    }

    private Flex inviteHeaderRow() {
        Flex row = Flex.row().gap(6).padding(Insets.of(2, 6, 2, 6));
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.from"), Sizing.expand()));
        row.item(headerCell("", Sizing.fixed(40)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.tier"), Sizing.fixed(86)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.expires"), Sizing.fixed(62)));
        row.item(headerCell("", Sizing.fixed(54)));
        row.item(headerCell("", Sizing.fixed(58)));
        return row;
    }

    private Flex lobbyHeaderRow() {
        Flex row = Flex.row().gap(8).padding(Insets.of(2, 6, 2, 6));
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.owner"), Sizing.expand()));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.size"), Sizing.fixed(44)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.tier"), Sizing.fixed(70)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility"), Sizing.fixed(78)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.status"), Sizing.fixed(82)));
        row.item(headerCell("", Sizing.fixed(84)));
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

    /** Content-width accent button, 18px tall — the owo smallButton. */
    private Button smallButton(Component text, Runnable action) {
        return ArenasParchment.button(text, action);
    }

    /** Fixed-width accent button, 18px tall. */
    private Button smallButton(Component text, float width, Runnable action) {
        return ArenasParchment.button(text, width, 18, action);
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

    private Flex sectionHeaderWithAction(Component title, Integer count, Button action) {
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
        membersSummaryLabel = text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.members_summary", readyCount, count, max), ACCENT);
        row.item(membersSummaryLabel);
        return row;
    }

    private Flex rowDivider(int color) {
        Flex divider = Flex.row();
        divider.sizing(Sizing.fill(), Sizing.fixed(1));
        divider.backgroundFill(color);
        return divider;
    }

    private void applyStatusSquare(Flex square, boolean online) {
        int color = online ? GOOD : WARN;
        square.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x66000000, 1);
    }

    private Button dangerButton(Component text, float width, Runnable action) {
        Button button = new Button(width, 18, text.copy().withStyle(ChatFormatting.RED), action);
        button.style(DANGER_STYLE);
        return button;
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private TextNode controlCaption(String caption) {
        return ArenasParchment.text(Component.literal(caption), INK_DIM);
    }

    private SegmentButton segmentButton(String label, int accentColor, boolean active, java.util.function.BooleanSupplier selected, Runnable action) {
        SegmentButton button = new SegmentButton(label, accentColor, selected, action);
        button.enabled(active);
        button.bind(selected::getAsBoolean); // repaint the moment the server flips the selection
        return button;
    }

    /** The owo segmented-control renderer: translucent accent fill + accent border when selected. */
    private static final class SegmentButton extends Widget {
        private final String label;
        private final int accentColor;
        private final java.util.function.BooleanSupplier selected;
        private final Runnable action;

        SegmentButton(String label, int accentColor, java.util.function.BooleanSupplier selected, Runnable action) {
            super(100, 26);
            this.label = label;
            this.accentColor = accentColor;
            this.selected = selected;
            this.action = action;
        }

        @Override
        protected void rebuild() {
            boolean sel = selected.getAsBoolean();
            boolean hover = hovered || focused;
            int fill = sel ? ((accentColor & 0x00FFFFFF) | 0x26000000) : (enabled && hover ? ROW_BG : PANEL_2);
            int border = sel ? accentColor : HAIRLINE;
            add(Shapes.rect(0, 0, width, height).fill(fill).stroke(border, 1));
            int textColor = sel ? accentColor : (enabled ? INK : INK_DIM);
            add(TextNode.of(label).at(width / 2, textY(height)).align(TextNode.Align.CENTER).color(textColor).shadow(false));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!enabled || button != 0) return false;
            UiSounds.click();
            action.run();
            return true;
        }

        @Override
        public boolean focusable() { return enabled; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (!enabled) return false;
            if (key == 257 || key == 335 || key == 32) { UiSounds.click(); action.run(); return true; }
            return false;
        }
    }

    private Flex segmentedControl(SegmentButton... buttons) {
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

    /** The 38×18 track+knob switch the owo screen custom-rendered onto a button. */
    private static final class ToggleSwitch extends Widget {
        private final int onColor;
        private final java.util.function.BooleanSupplier on;
        private final Runnable onToggle;

        ToggleSwitch(int onColor, java.util.function.BooleanSupplier on, Runnable onToggle) {
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
        public boolean focusable() { return enabled; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (!enabled) return false;
            if (key == 257 || key == 335 || key == 32) { UiSounds.click(); onToggle.run(); return true; }
            return false;
        }
    }

    private Flex infoColumn(String caption, TextNode value, Sizing width) {
        Flex col = Flex.column().gap(3);
        col.sizing(width, Sizing.content());
        col.item(ArenasParchment.text(Component.literal(caption), INK_DIM));
        col.item(value);
        return col;
    }

    private static String titleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.charAt(0) + value.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private Label headerCell(String text, Sizing sizing) {
        Label label = ArenasParchment.label(100, Component.literal(text), INK_DIM);
        label.sizing(sizing, Sizing.content());
        return label;
    }

    private Label fixedText(Component text, float width, int color, TextNode.Align alignment) {
        Label label = ArenasParchment.label(width, text, color);
        label.align(alignment);
        label.sizing(Sizing.fixed(width), Sizing.content());
        return label;
    }

    private Flex badge(Component text, int color) {
        Flex tag = Flex.row().padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.sizing(Sizing.content(), Sizing.content());
        tag.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        tag.item(this.text(text, color));
        return tag;
    }

    private Badge fixedBadge(Component text, float width, int color) {
        Flex tag = Flex.row().padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.sizing(Sizing.fixed(width), Sizing.content());
        tag.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        TextNode label = this.text(text, color);
        tag.item(label);
        return new Badge(tag, label);
    }

    private Flex rowPanel(boolean accentLeft) {
        Flex row = Flex.row().padding(Insets.of(6)).alignItems(Align.CENTER);
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

    private int tierColor(DifficultyTier tier) {
        return switch (tier) {
            case EASY -> GOOD;
            case NORMAL -> INFO;
            case HARD -> WARN;
            case NIGHTMARE -> DANGER;
        };
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

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(DungeonControllerData data) {
        Optional<Lobby> previousOwnLobby = this.ownLobby;
        int previousQueuePosition = this.lastQueuePosition;
        menu.applyData(data);
        syncFromMenu(true);
        this.lastQueuePosition = menu.getQueuePosition();

        // Queue position changed → the banner appears/disappears, so do a full rebuild
        // rather than the in-place member refresh (which wouldn't add/remove the banner).
        if (this.lastQueuePosition != previousQueuePosition) {
            rebuildUi();
            return;
        }

        if (currentTab == Tab.MY_LOBBY && this.ownLobby.isEmpty()) {
            currentTab = Tab.LOBBIES;
            rebuildUi();
            return;
        } else if (currentTab == Tab.LOBBIES && previousOwnLobby.isEmpty() && this.ownLobby.isPresent()) {
            currentTab = Tab.MY_LOBBY;
            rebuildUi();
            return;
        }

        if (currentTab == Tab.MY_LOBBY
            && previousOwnLobby.isPresent()
            && this.ownLobby.isPresent()
            && sameLobbyStructure(previousOwnLobby.get(), this.ownLobby.get())) {
            refreshTabButtons();
            refreshMyLobbyInPlace(this.ownLobby.get());
            return;
        }

        rebuildUi();
    }

    private void syncFromMenu(boolean preserveTab) {
        this.visibleLobbies.clear();
        this.visibleLobbies.addAll(menu.getVisibleLobbies());
        this.myInvites.clear();
        this.myInvites.addAll(menu.getMyInvites());
        this.myJoinRequests.clear();
        this.myJoinRequests.addAll(menu.getMyJoinRequests());
        this.ownLobby = menu.getOwnLobby();
        this.busyPlayers = menu.getBusyPlayers();
        this.topLeaderboards = menu.getTopLeaderboards();
        this.snapshotServerTick = menu.getServerGameTick();
        this.snapshotEpochMs = System.currentTimeMillis();

        if (!preserveTab && this.ownLobby.isPresent()) {
            this.currentTab = Tab.MY_LOBBY;
        }
    }

    private UUID resolveInviteeUuid(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
            // fallthrough
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

    private long approximateServerTick() {
        long elapsedMs = Math.max(0L, System.currentTimeMillis() - snapshotEpochMs);
        return snapshotServerTick + (elapsedMs / 50L);
    }

    private void refreshTabButtons() {
        lobbiesTabButton.enabled(currentTab != Tab.LOBBIES);
        myLobbyTabButton.enabled(currentTab != Tab.MY_LOBBY);
        lobbiesTabButton.label(Component.translatable("gui.arenas_ld.dungeon_controller.tab.lobbies").append(" " + visibleLobbies.size()));
        myLobbyTabButton.label(Component.translatable("gui.arenas_ld.dungeon_controller.tab.my_lobby").append(ownLobby.isPresent() ? " " + ownLobby.get().members().size() : ""));
    }

    private void refreshMyLobbyInPlace(Lobby lobby) {
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : UUID.randomUUID();
        boolean isOwner = lobby.ownerUuid().equals(self);
        boolean ready = lobby.readyMembers().contains(self);
        boolean allReady = lobby.allReady();
        boolean allOnline = allOnline(lobby);
        boolean canStart = isOwner && allReady && allOnline;

        if (summaryOwnerLabel != null) {
            summaryOwnerLabel.text(Component.literal(lobby.ownerName()));
        }
        if (summaryMembersLabel != null) {
            summaryMembersLabel.text(Component.literal(lobby.members().size() + " / " + menu.getMaxPartySize()));
        }
        if (membersSummaryLabel != null) {
            membersSummaryLabel.text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.members_summary", lobby.readyMembers().size(), lobby.members().size(), menu.getMaxPartySize()));
        }
        if (summaryTierValue != null) {
            summaryTierValue.text(Component.literal(titleCase(lobby.selectedTier().name())));
            summaryTierValue.color(tierColor(lobby.selectedTier()));
        }
        if (summaryVisibilityValue != null) {
            summaryVisibilityValue.text(Component.literal(titleCase(lobby.visibility().name())));
            summaryVisibilityValue.color(visibilityColor(lobby.visibility()));
        }
        if (summaryHardcoreValue != null) {
            summaryHardcoreValue.text(Component.translatable(lobby.hardcoreEnabled() ? "gui.arenas_ld.dungeon_controller.ui.hardcore.on" : "gui.arenas_ld.dungeon_controller.ui.hardcore.off"));
            summaryHardcoreValue.color(lobby.hardcoreEnabled() ? DANGER : INK_MID);
        }
        if (readyToggleButton != null) {
            readyToggleButton.label(Component.translatable(ready
                ? "gui.arenas_ld.dungeon_controller.button.unready"
                : "gui.arenas_ld.dungeon_controller.button.ready"));
        }
        if (leaveLobbyButton != null) {
            leaveLobbyButton.enabled(true);
        }
        if (startRunButton != null) {
            startRunButton.enabled(canStart);
        }

        for (UUID member : lobby.members()) {
            MemberRowRefs refs = memberRowRefs.get(member);
            if (refs == null) continue;
            boolean online = isOnline(member);
            boolean memberReady = lobby.readyMembers().contains(member);
            applyStatusSquare(refs.statusSquare(), online);
            refs.statusGlyph().text(Component.literal(online ? "✓" : "!")).color(online ? GOOD : WARN);
            refs.connectionLabel().text(Component.literal(online ? "● " : "○ ").append(Component.translatable(online ? "gui.arenas_ld.dungeon_controller.ui.member.online" : "gui.arenas_ld.dungeon_controller.ui.member.offline")));
            refs.connectionLabel().color(online ? GOOD : DANGER);
            updateBadge(refs.readyBadge(), Component.translatable(memberReady ? "gui.arenas_ld.dungeon_controller.ui.member.ready" : "gui.arenas_ld.dungeon_controller.ui.member.not_ready"), memberReady ? GOOD : INK_DIM);
            if (refs.kickButton() != null) {
                refs.kickButton().enabled(isOwner && !member.equals(lobby.ownerUuid()));
            }
        }

        if (inviteDropdownOpen) {
            refreshInviteDropdown();
        }

        footerLabel.text(footerError == null ? Component.empty() : Component.literal(footerError).withStyle(ChatFormatting.RED));
    }

    private void refreshInviteCountdowns() {
        for (PendingInvite invite : myInvites) {
            Label label = inviteCountdownLabels.get(invite.lobbyId());
            if (label == null) continue;
            long remainingTicks = Math.max(0L, invite.expiresAtTick() - approximateServerTick());
            long remainingSeconds = remainingTicks / 20L;
            label.text(Component.literal(formatRemaining(remainingSeconds)));
            label.color(remainingSeconds <= 15 ? WARN : INK_MID);
        }
    }

    private void refreshJoinRequestCountdowns() {
        for (PendingJoinRequest req : myJoinRequests) {
            Label label = joinRequestCountdownLabels.get(req.requesterUuid());
            if (label == null) continue;
            long remainingTicks = Math.max(0L, req.expiresAtTick() - approximateServerTick());
            long remainingSeconds = remainingTicks / 20L;
            label.text(Component.literal(formatRemaining(remainingSeconds)));
            label.color(remainingSeconds <= 15 ? WARN : INK_MID);
        }
    }

    // ── scroll preservation (the owo reflection into ScrollContainer replaced with public API) ──

    private float maxContentScroll() {
        if (contentArea == null || contentScroll == null) {
            return 0;
        }
        return Math.max(0, contentArea.height() - contentScroll.height());
    }

    private void captureScrollProgress() {
        if (contentScroll == null) {
            savedScrollProgress = 0.0D;
            return;
        }
        float maxScroll = maxContentScroll();
        savedScrollProgress = maxScroll <= 0 ? 0.0D
            : Math.max(0.0D, Math.min(1.0D, contentScroll.scrollY() / (double) maxScroll));
    }

    private void applyScrollProgress(double progress) {
        if (contentScroll == null) {
            return;
        }
        contentScroll.scrollTo((float) (progress * maxContentScroll()));
    }

    private void updateBadge(Badge badge, Component text, int color) {
        badge.box().backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        badge.label().text(text).color(color);
    }

    private boolean sameLobbyStructure(Lobby previous, Lobby current) {
        // Settings are part of "structure": the segment buttons and the hardcore switch bake
        // their selected state into their built nodes, so a tier/visibility/hardcore change
        // must take the full-rebuild path — the in-place refresh only touches labels.
        return previous.lobbyId().equals(current.lobbyId())
            && previous.ownerUuid().equals(current.ownerUuid())
            && previous.members().equals(current.members())
            && previous.selectedTier() == current.selectedTier()
            && previous.visibility() == current.visibility()
            && previous.hardcoreEnabled() == current.hardcoreEnabled();
    }

    private Optional<Lobby> resolveLobbyForInvite(PendingInvite invite) {
        for (Lobby lobby : visibleLobbies) {
            if (lobby.lobbyId().equals(invite.lobbyId())) {
                return Optional.of(lobby);
            }
        }
        if (ownLobby.isPresent() && ownLobby.get().lobbyId().equals(invite.lobbyId())) {
            return ownLobby;
        }
        return Optional.empty();
    }

    private static String shortUuid(UUID uuid) {
        return uuid.toString().substring(0, 8);
    }

    private static String formatRemaining(long seconds) {
        long mins = seconds / 60L;
        long sec = seconds % 60L;
        return mins + ":" + String.format("%02d", sec);
    }
}
