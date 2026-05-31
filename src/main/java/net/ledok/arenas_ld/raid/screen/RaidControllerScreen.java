package net.ledok.arenas_ld.raid.screen;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.raid.packet.RaidAcceptInvitePayload;
import net.ledok.arenas_ld.raid.packet.RaidAcceptJoinRequestPayload;
import net.ledok.arenas_ld.raid.packet.RaidCreateLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidDeclineInvitePayload;
import net.ledok.arenas_ld.raid.packet.RaidDeclineJoinRequestPayload;
import net.ledok.arenas_ld.raid.packet.RaidInvitePlayerPayload;
import net.ledok.arenas_ld.raid.packet.RaidJoinLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidKickFromLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidLeaveLobbyPayload;
import net.ledok.arenas_ld.raid.packet.RaidRequestJoinPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLobbyHardcorePayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLobbyTierPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLobbyVisibilityPayload;
import net.ledok.arenas_ld.raid.packet.RaidStartRunPayload;
import net.ledok.arenas_ld.raid.packet.RaidToggleReadyPayload;
import net.ledok.arenas_ld.raid.screen.RaidControllerData.RaidInstanceState;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class RaidControllerScreen extends BaseOwoHandledScreen<FlowLayout, RaidControllerScreenHandler> {
    private static final int BG = 0xFF070E14;
    private static final int PANEL = 0xFF121922;
    private static final int PANEL_2 = 0xFF0C1218;
    private static final int HAIRLINE = 0xFF283442;
    private static final int HAIRLINE_HI = 0xFF3A4A5C;
    private static final int ROW_BG = 0xFF19222D;
    private static final int ROW_BG_ALT = 0xFF16202A;
    private static final int INK = 0xFFE8EEF5;
    private static final int INK_MID = 0xFF9AA8B8;
    private static final int INK_DIM = 0xFF5F6E80;
    private static final int GOOD = 0xFF86D36C;
    private static final int WARN = 0xFFF5B042;
    private static final int DANGER = 0xFFE8624A;
    private static final int INFO = 0xFF6DA3E8;
    private static final int ACCENT = 0xFFA98BE8;
    private static final int ACCENT_DARK = 0xFF6C4FB5;
    private static final int INVITE_DROPDOWN_MAX_HEIGHT = 154;

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

    private static final class MemberRowRefs {
        private final FlowLayout statusSquare;
        private final LabelComponent statusGlyph;
        private final LabelComponent connectionLabel;
        private final FlowLayout readyBadge;
        private final ButtonComponent kickButton;

        private MemberRowRefs(FlowLayout statusSquare, LabelComponent statusGlyph, LabelComponent connectionLabel, FlowLayout readyBadge, ButtonComponent kickButton) {
            this.statusSquare = statusSquare;
            this.statusGlyph = statusGlyph;
            this.connectionLabel = connectionLabel;
            this.readyBadge = readyBadge;
            this.kickButton = kickButton;
        }
    }

    private final List<Lobby> visibleLobbies;
    private final List<PendingInvite> myInvites;
    private final List<PendingJoinRequest> myJoinRequests;
    private Optional<Lobby> ownLobby;
    private java.util.Set<UUID> busyPlayers;
    private Map<DifficultyTier, List<LeaderboardEntry>> topLeaderboards = Map.of();
    private DifficultyTier leaderboardTier = DifficultyTier.NORMAL;
    private List<RaidInstanceState> instances = new ArrayList<>();

    // Admin draft state
    private Tab currentTab = Tab.LOBBIES;
    private int lastQueuePosition = 0;
    private String footerError;
    private long snapshotServerTick;
    private long snapshotEpochMs;
    private long lastCountdownSecond = -1L;

    private FlowLayout contentArea;
    private ScrollContainer<FlowLayout> contentScroll;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;
    private ButtonComponent lobbiesTabButton;
    private ButtonComponent myLobbyTabButton;
    private ButtonComponent leaderboardTabButton;
    private ButtonComponent invitesTabButton;
    private TextBoxComponent inviteField;
    private String inviteInput = "";
    private boolean inviteDropdownOpen = false;
    private FlowLayout inviteFieldRow;
    private FlowLayout inviteDropdownPanel;
    private ButtonComponent inviteChevron;
    private double savedScrollProgress = 0.0D;
    private boolean restoreScrollNextTick = false;
    private final Map<UUID, LabelComponent> inviteCountdownLabels = new HashMap<>();
    private final Map<UUID, LabelComponent> joinRequestCountdownLabels = new HashMap<>();
    private final Map<UUID, MemberRowRefs> memberRowRefs = new HashMap<>();
    private LabelComponent summaryOwnerLabel;
    private LabelComponent summaryMembersLabel;
    private LabelComponent membersSummaryLabel;
    private LabelComponent summaryTierValue;
    private LabelComponent summaryVisibilityValue;
    private LabelComponent summaryHardcoreValue;
    private ButtonComponent readyToggleButton;
    private ButtonComponent leaveLobbyButton;
    private ButtonComponent startRunButton;

    // Instances strip panel (rebuilt in rebuildUi)
    private FlowLayout instancesStrip;

    public RaidControllerScreen(RaidControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
        this.visibleLobbies = new ArrayList<>(handler.getVisibleLobbies());
        this.myInvites = new ArrayList<>(handler.getMyInvites());
        this.myJoinRequests = new ArrayList<>(handler.getMyJoinRequests());
        this.ownLobby = handler.getOwnLobby();
        this.busyPlayers = handler.getBusyPlayers();
        this.topLeaderboards = handler.getTopLeaderboards();
        this.instances = new ArrayList<>(handler.getInstances());
        this.snapshotServerTick = handler.getServerGameTick();
        this.snapshotEpochMs = System.currentTimeMillis();
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth = Math.max(420, Math.min(560, this.width - 24));
        int shellHeight = Math.max(300, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));

        shell.child(buildHeader());

        instancesStrip = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        instancesStrip.surface(Surface.flat(PANEL_2));
        instancesStrip.padding(Insets.of(6, 6, 8, 8));
        shell.child(instancesStrip);

        shell.child(buildTabs());

        contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        contentArea.surface(Surface.flat(PANEL));
        contentArea.padding(Insets.of(10));
        contentArea.gap(4);
        contentScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), contentArea);
        contentScroll.surface(Surface.flat(PANEL));
        contentScroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        contentScroll.scrollbarThiccness(8);
        contentScroll.fixedScrollbarLength(28);
        contentScroll.scrollStep(18);
        shell.child(contentScroll);

        shell.child(buildFooter());
        rootComponent.child(shell);

        syncFromMenu(false);
        rebuildUi();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (restoreScrollNextTick && contentScroll != null) {
            contentScroll.scrollTo(savedScrollProgress);
            restoreScrollNextTick = false;
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
        if (inviteDropdownOpen
            && !pointInside(inviteFieldRow, mouseX, mouseY)
            && !pointInside(inviteDropdownPanel, mouseX, mouseY)) {
            closeInviteDropdown();
        }
        return handled;
    }

    private boolean pointInside(io.wispforest.owo.ui.core.Component component, double mouseX, double mouseY) {
        if (component == null) {
            return false;
        }
        return mouseX >= component.x() && mouseX < component.x() + component.width()
            && mouseY >= component.y() && mouseY < component.y() + component.height();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(46));
        header.surface(Surface.flat(PANEL_2));
        header.padding(Insets.of(8, 8, 10, 10));
        header.gap(10);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(20), Sizing.fixed(20));
        mark.surface(Surface.flat(ACCENT).and(Surface.outline(ACCENT_DARK)));
        header.child(mark);

        FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
        info.gap(3);

        FlowLayout titleLine = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        titleLine.gap(8);
        titleLine.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent titleLabel = Components.label(Component.translatable("container.arenas_ld.raid_controller"));
        titleLabel.color(Color.ofArgb(INK));
        titleLine.child(titleLabel);
        titleLine.child(statusBadge());
        info.child(titleLine);

        FlowLayout meta = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        meta.gap(10);
        meta.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        meta.child(smallMeta("POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()));
        FlowLayout live = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        live.gap(4);
        live.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        live.child(text(Component.literal("●"), GOOD));
        live.child(smallMeta("LIVE", GOOD));
        meta.child(live);
        info.child(meta);
        header.child(info);

        header.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        ButtonComponent close = Components.button(Component.literal("×"), b -> onClose());
        close.sizing(Sizing.fixed(22), Sizing.fixed(18));
        close.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ROW_BG : PANEL;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), HAIRLINE_HI);
        });
        header.child(close);
        return header;
    }

    private FlowLayout statusBadge() {
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

    private FlowLayout buildTabs() {
        FlowLayout tabs = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        tabs.surface(Surface.flat(PANEL_2));
        tabs.padding(Insets.of(4));
        tabs.gap(0);
        tabs.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        lobbiesTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.lobbies", Tab.LOBBIES);
        myLobbyTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.my_lobby", Tab.MY_LOBBY);
        leaderboardTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.leaderboard", Tab.LEADERBOARD);
        invitesTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.invites", Tab.INVITES);

        tabs.child(tabCell(lobbiesTabButton));
        tabs.child(tabCell(myLobbyTabButton));
        tabs.child(tabCell(leaderboardTabButton));
        tabs.child(tabCell(invitesTabButton));
        return tabs;
    }

    private FlowLayout tabCell(ButtonComponent button) {
        FlowLayout cell = Containers.verticalFlow(Sizing.fill(25), Sizing.content());
        cell.surface(Surface.BLANK);
        cell.padding(Insets.of(0, 0, 2, 2));
        button.horizontalSizing(Sizing.fill(100));
        cell.child(button);
        return cell;
    }

    private FlowLayout buildFooter() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(34));
        footer.surface(Surface.flat(PANEL_2));
        footer.padding(Insets.of(6));
        footer.gap(6);

        footerLabel = Components.label(Component.empty());
        footerLabel.color(Color.ofArgb(DANGER));
        footerLabel.horizontalSizing(Sizing.expand());
        footer.child(footerLabel);

        footerActions = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        footerActions.gap(4);
        footer.child(footerActions);
        return footer;
    }

    private ButtonComponent tabButton(String key, Tab tab) {
        ButtonComponent button = Components.button(Component.translatable(key), b -> {
            currentTab = tab;
            footerError = null;
            savedScrollProgress = 0.0D;
            rebuildUi();
        });
        button.sizing(Sizing.fixed(120), Sizing.fixed(20));
        button.renderer((context, rendered, delta) -> {
            boolean active = !rendered.active();
            int fill = active ? PANEL : (rendered.isHoveredOrFocused() ? ROW_BG : PANEL_2);
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            if (active) {
                context.fill(rendered.getX(), rendered.getY() + rendered.getHeight() - 2,
                    rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), ACCENT);
            } else {
                context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), HAIRLINE);
            }
        });
        return button;
    }

    private void rebuildUi() {
        captureScrollProgress();
        contentArea.clearChildren();
        footerActions.clearChildren();
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

        // Rebuild instances strip
        if (instancesStrip != null) {
            instancesStrip.clearChildren();
            buildInstancesStrip();
        }

        lobbiesTabButton.active(currentTab != Tab.LOBBIES);
        myLobbyTabButton.active(currentTab != Tab.MY_LOBBY);
        leaderboardTabButton.active(currentTab != Tab.LEADERBOARD);
        invitesTabButton.active(currentTab != Tab.INVITES);
        lobbiesTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.lobbies").append(" " + visibleLobbies.size()));
        myLobbyTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.my_lobby").append(ownLobby.isPresent() ? " " + ownLobby.get().members().size() : ""));
        leaderboardTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.leaderboard"));
        invitesTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.invites").append(" " + myInvites.size()));

        switch (currentTab) {
            case LOBBIES -> buildLobbiesContent();
            case MY_LOBBY -> buildMyLobbyContent();
            case LEADERBOARD -> buildLeaderboardContent();
            case INVITES -> buildInvitesContent();
        }

        footerLabel.text(footerError == null ? Component.empty() : Component.literal(footerError).withStyle(ChatFormatting.RED));
        if (contentScroll != null && savedScrollProgress > 0.0D) {
            contentScroll.scrollTo(savedScrollProgress);
            restoreScrollNextTick = true;
        } else {
            restoreScrollNextTick = false;
        }
    }

    // ── Instances strip ──────────────────────────────────────────────────────

    private void buildInstancesStrip() {
        FlowLayout titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.gap(8);
        titleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        titleRow.child(text(Component.translatable("gui.arenas_ld.raid_controller.ui.instance.title"), INK_DIM));
        instancesStrip.child(titleRow);

        if (instances.isEmpty()) {
            instancesStrip.child(text(Component.translatable("gui.arenas_ld.raid_controller.ui.instance.no_instances"), INK_DIM));
            return;
        }

        FlowLayout pillRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        pillRow.gap(4);
        pillRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        for (RaidInstanceState inst : instances) {
            String pillText = inst.status().name()
                + (inst.cooldownTicks() > 0 ? " " + (inst.cooldownTicks() / 20) + "s" : "");
            int pillColor = instanceStatusColor(inst.status());
            pillRow.child(badge(Component.literal(pillText), pillColor));
        }
        instancesStrip.child(pillRow);
    }

    private int instanceStatusColor(InstanceStatus status) {
        return switch (status) {
            case FREE -> GOOD;
            case RUNNING -> WARN;
            case COOLDOWN -> DANGER;
        };
    }

    // ── Invites tab ──────────────────────────────────────────────────────────

    private void buildInvitesContent() {
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.invites"), myInvites.size()));
        if (myInvites.isEmpty()) {
            contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_invites")));
        } else {
            contentArea.child(inviteHeaderRow());
            for (PendingInvite invite : myInvites) {
                contentArea.child(inviteRow(invite));
            }
        }

        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        boolean isOwner = self != null && ownLobby.isPresent() && ownLobby.get().ownerUuid().equals(self);
        if (isOwner) {
            UUID ownedLobbyId = ownLobby.get().lobbyId();
            List<PendingJoinRequest> incoming = myJoinRequests.stream()
                .filter(r -> r.lobbyId().equals(ownedLobbyId))
                .toList();
            contentArea.child(spacer(8));
            contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.join_requests"), incoming.size()));
            if (incoming.isEmpty()) {
                contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_join_requests")));
            } else {
                contentArea.child(joinRequestHeaderRow());
                for (PendingJoinRequest req : incoming) {
                    contentArea.child(joinRequestRow(req));
                }
            }
        }
    }

    private FlowLayout joinRequestHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.requester"), Sizing.expand()));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.tier"), Sizing.fixed(86)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.expires"), Sizing.fixed(62)));
        row.child(headerCell("", Sizing.fixed(54)));
        row.child(headerCell("", Sizing.fixed(58)));
        return row;
    }

    private FlowLayout joinRequestRow(PendingJoinRequest req) {
        FlowLayout row = rowPanel(false);
        row.gap(6);

        String requesterName = !req.requesterName().isEmpty() ? req.requesterName() : shortUuid(req.requesterUuid());

        FlowLayout info = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        FlowLayout fromLine = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        fromLine.gap(3);
        fromLine.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.from"), INK));
        fromLine.child(text(Component.literal(requesterName), ACCENT));
        info.child(fromLine);
        info.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.join_request_row.wants_to_join"), INK_DIM));
        row.child(info);

        row.child(fixedBadge(Component.literal(req.tier().name()), 86, tierColor(req.tier())));

        long remainingSeconds = Math.max(0L, req.expiresAtTick() - approximateServerTick()) / 20L;
        LabelComponent countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, HorizontalAlignment.CENTER);
        joinRequestCountdownLabels.put(req.requesterUuid(), countdown);
        row.child(countdown);

        UUID requesterUuid = req.requesterUuid();
        ButtonComponent accept = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidAcceptJoinRequestPayload(menu.getBlockPos(), requesterUuid));
        });
        accept.horizontalSizing(Sizing.fixed(54));
        row.child(accept);

        ButtonComponent decline = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidDeclineJoinRequestPayload(menu.getBlockPos(), requesterUuid));
        });
        decline.horizontalSizing(Sizing.fixed(58));
        row.child(decline);

        return row;
    }

    // ── Leaderboard tab ──────────────────────────────────────────────────────

    private void buildLeaderboardContent() {
        contentArea.child(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.col.tier")));
        contentArea.child(leaderboardTierControls());

        contentArea.child(spacer(4));
        contentArea.child(leaderboardBanner(leaderboardTier));

        List<LeaderboardEntry> entries = new ArrayList<>(topLeaderboards.getOrDefault(leaderboardTier, List.of()));
        entries.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds));

        contentArea.child(spacer(4));
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.ui.section.top_runs"), entries.size()));

        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.child(leaderboardHeaderRow());
        list.child(rowDivider(HAIRLINE_HI));
        if (entries.isEmpty()) {
            FlowLayout empty = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            empty.surface(Surface.flat(ROW_BG));
            empty.padding(Insets.of(10));
            empty.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            empty.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.ui.leaderboard.no_runs")));
            list.child(empty);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                if (i > 0) {
                    list.child(rowDivider(HAIRLINE));
                }
                list.child(leaderboardRow(i + 1, entries.get(i), leaderboardTier));
            }
        }
        contentArea.child(list);
    }

    private FlowLayout leaderboardTierControls() {
        ButtonComponent easy = leaderboardTierButton(DifficultyTier.EASY, "EASY");
        ButtonComponent normal = leaderboardTierButton(DifficultyTier.NORMAL, "NORMAL");
        ButtonComponent hard = leaderboardTierButton(DifficultyTier.HARD, "HARD");
        ButtonComponent nightmare = leaderboardTierButton(DifficultyTier.NIGHTMARE, "NIGHTMARE");
        return segmentedControl(easy, normal, hard, nightmare);
    }

    private ButtonComponent leaderboardTierButton(DifficultyTier tier, String label) {
        return segmentButton(label, tierColor(tier), true,
            () -> leaderboardTier == tier,
            b -> {
                leaderboardTier = tier;
                rebuildUi();
            });
    }

    private FlowLayout leaderboardBanner(DifficultyTier tier) {
        int color = tierColor(tier);
        FlowLayout banner = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(34));
        banner.surface(Surface.flat((color & 0x00FFFFFF) | 0x1F000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        banner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
        accent.surface(Surface.flat(color));
        banner.child(accent);
        FlowLayout inner = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        inner.padding(Insets.of(0, 0, 10, 10));
        inner.gap(8);
        inner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        inner.child(badge(Component.translatable("gui.arenas_ld.dungeon_controller.ui.leaderboard.banner_badge", tier.name()), color));
        inner.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.leaderboard.banner_desc", titleCase(tier.name())), INK));
        banner.child(inner);
        return banner;
    }

    private FlowLayout leaderboardHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(PANEL_2));
        row.padding(Insets.of(5, 5, 8, 8));
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.rank"), Sizing.fixed(50)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.player"), Sizing.expand()));
        LabelComponent timeHeader = headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.time"), Sizing.fixed(64));
        timeHeader.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        row.child(timeHeader);
        return row;
    }

    private FlowLayout leaderboardRow(int rank, LeaderboardEntry entry, DifficultyTier tier) {
        boolean top = rank == 1;
        int color = tierColor(tier);
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(top ? ((color & 0x00FFFFFF) | 0x1A000000) : ROW_BG));
        row.padding(Insets.of(7, 7, 8, 8));
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent rankLabel = text(Component.literal(top ? "★1" : ("#" + rank)), top ? color : INK_MID);
        rankLabel.horizontalSizing(Sizing.fixed(50));
        row.child(rankLabel);

        LabelComponent nameLabel = text(Component.literal(entry.playerName()), INK);
        nameLabel.horizontalSizing(Sizing.expand());
        row.child(nameLabel);

        LabelComponent timeLabel = text(Component.literal(formatClock(entry.timeSeconds())), top ? color : INK_MID);
        timeLabel.horizontalSizing(Sizing.fixed(64));
        timeLabel.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        row.child(timeLabel);
        return row;
    }

    private static String formatClock(int totalSeconds) {
        int mins = Math.max(0, totalSeconds) / 60;
        int secs = Math.max(0, totalSeconds) % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    // ── Lobbies tab ──────────────────────────────────────────────────────────

    private void buildLobbiesContent() {
        ButtonComponent create = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.create"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidCreateLobbyPayload(menu.getBlockPos()));
        });
        create.active(ownLobby.isEmpty());
        contentArea.child(sectionHeaderWithAction(Component.translatable("gui.arenas_ld.dungeon_controller.public_lobbies"), visibleLobbies.size(), create));
        if (!visibleLobbies.isEmpty()) {
            contentArea.child(lobbyHeaderRow());
        }

        List<Lobby> sorted = visibleLobbies.stream().sorted(Comparator.comparing(Lobby::ownerName)).limit(6).toList();
        if (sorted.isEmpty()) {
            contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_lobbies")));
        } else {
            for (Lobby lobby : sorted) {
                contentArea.child(lobbyRow(lobby));
            }
        }
    }

    private FlowLayout inviteRow(PendingInvite invite) {
        FlowLayout row = rowPanel(false);
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
        FlowLayout inviteInfo = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        FlowLayout fromLine = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        fromLine.gap(3);
        fromLine.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.from"), INK));
        fromLine.child(text(Component.literal(inviterName), ACCENT));
        inviteInfo.child(fromLine);
        inviteInfo.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.owner", ownerName.toUpperCase()), INK_DIM));
        row.child(inviteInfo);
        row.child(fixedText(Component.literal("-"), 40, INK_DIM, HorizontalAlignment.CENTER));

        row.child(fixedBadge(Component.literal(inviteTier.name()), 86, tierColor(inviteTier)));
        LabelComponent countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, HorizontalAlignment.CENTER);
        inviteCountdownLabels.put(invite.lobbyId(), countdown);
        row.child(countdown);

        ButtonComponent accept = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidAcceptInvitePayload(menu.getBlockPos(), invite.lobbyId()));
        });
        accept.horizontalSizing(Sizing.fixed(54));
        row.child(accept);

        ButtonComponent decline = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidDeclineInvitePayload(menu.getBlockPos(), invite.lobbyId()));
        });
        decline.horizontalSizing(Sizing.fixed(58));
        row.child(decline);

        return row;
    }

    private FlowLayout lobbyRow(Lobby lobby) {
        FlowLayout row = rowPanel(false);
        row.gap(8);

        FlowLayout ownerInfo = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        FlowLayout nameLine = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        nameLine.gap(5);
        nameLine.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        nameLine.child(text(Component.literal(lobby.ownerName()), INK));
        if (lobby.hardcoreEnabled()) {
            nameLine.child(badge(Component.literal("H"), DANGER));
        }
        ownerInfo.child(nameLine);
        ownerInfo.child(text(Component.literal(shortUuid(lobby.lobbyId()).toUpperCase()), INK_DIM));
        row.child(ownerInfo);
        row.child(fixedBadge(Component.literal(lobby.members().size() + "/" + menu.getMaxPartySize()), 44, INK_MID));
        row.child(fixedBadge(Component.literal(lobby.selectedTier().name()), 70, tierColor(lobby.selectedTier())));

        FlowLayout visCell = Containers.horizontalFlow(Sizing.fixed(78), Sizing.content());
        int visColor = visibilityColor(lobby.visibility());
        visCell.surface(Surface.flat((visColor & 0x00FFFFFF) | 0x22000000).and(Surface.outline((visColor & 0x00FFFFFF) | 0x55000000)));
        visCell.padding(Insets.of(4, 2, 4, 4));
        visCell.gap(4);
        visCell.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        visCell.child(text(Component.literal("●"), visColor));
        visCell.child(text(Component.literal(lobby.visibility().name()), visColor));
        row.child(visCell);

        row.child(fixedBadge(Component.literal(statusLabel(lobby.status())), 82, statusColor(lobby.status())));

        UUID lobbyId = lobby.lobbyId();
        boolean isMine = ownLobby.isPresent() && ownLobby.get().lobbyId().equals(lobbyId);
        boolean isFull = lobby.isFull(menu.getMaxPartySize());
        boolean inRun = lobby.status() == LobbyStatus.IN_RUN;

        FlowLayout actionCell = Containers.horizontalFlow(Sizing.fixed(84), Sizing.content());
        actionCell.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        ButtonComponent actionButton;
        if (inRun) {
            actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.running"), b -> {});
            actionButton.active(false);
        } else if (isFull) {
            actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.full"), b -> {});
            actionButton.active(false);
        } else if (lobby.visibility() == LobbyVisibility.PUBLIC) {
            actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.join"), b -> {
                footerError = null;
                ClientPlayNetworking.send(new RaidJoinLobbyPayload(menu.getBlockPos(), lobbyId));
            });
            actionButton.active(!isMine && ownLobby.isEmpty());
        } else {
            UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
            boolean alreadyRequested = self != null && myJoinRequests.stream()
                .anyMatch(r -> r.lobbyId().equals(lobbyId) && r.requesterUuid().equals(self));
            if (alreadyRequested) {
                actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.pending"), b -> {});
                actionButton.active(false);
            } else {
                actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.request"), b -> {
                    footerError = null;
                    ClientPlayNetworking.send(new RaidRequestJoinPayload(menu.getBlockPos(), lobbyId));
                });
                actionButton.active(!isMine && ownLobby.isEmpty());
            }
        }
        actionButton.horizontalSizing(Sizing.fill(100));
        actionCell.child(actionButton);
        row.child(actionCell);
        return row;
    }

    // ── MY LOBBY tab ─────────────────────────────────────────────────────────

    private void buildMyLobbyContent() {
        if (ownLobby.isEmpty()) {
            contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_own_lobby")));
            return;
        }

        Lobby lobby = ownLobby.get();
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : UUID.randomUUID();
        boolean isOwner = lobby.ownerUuid().equals(self);

        // Queue position banner
        int queuePos = menu.getQueuePosition();
        if (queuePos >= 1) {
            FlowLayout queueBanner = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(28));
            queueBanner.surface(Surface.flat((WARN & 0x00FFFFFF) | 0x1A000000).and(Surface.outline((WARN & 0x00FFFFFF) | 0x55000000)));
            queueBanner.padding(Insets.of(4, 4, 8, 8));
            queueBanner.gap(8);
            queueBanner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

            FlowLayout queueAccent = Containers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
            queueAccent.surface(Surface.flat(WARN));
            queueBanner.child(queueAccent);

            String inLineText = Component.translatable("gui.arenas_ld.raid_controller.ui.queue.in_line", queuePos).getString();
            queueBanner.child(badge(Component.literal(inLineText), WARN));

            int etaSeconds = menu.getEstimatedWaitSeconds();
            if (etaSeconds > 0) {
                queueBanner.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.queue.eta", formatClock(etaSeconds)), INK_MID));
            }

            queueBanner.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

            if (isOwner) {
                ButtonComponent startOrQueue = smallButton(Component.translatable("gui.arenas_ld.raid_controller.button.start"), b -> {
                    footerError = null;
                    Lobby currentLobby = ownLobby.orElse(null);
                    UUID currentSelf2 = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : UUID.randomUUID();
                    if (currentLobby == null) return;
                    if (!currentLobby.ownerUuid().equals(currentSelf2)) {
                        footerError = Component.translatable("gui.arenas_ld.raid_controller.error.only_owner").getString();
                        rebuildUi();
                        return;
                    }
                    if (!currentLobby.allReady()) {
                        footerError = Component.translatable("gui.arenas_ld.raid_controller.error.all_ready_required").getString();
                        rebuildUi();
                        return;
                    }
                    if (!allOnline(currentLobby)) {
                        footerError = Component.translatable("gui.arenas_ld.raid_controller.error.all_online_required").getString();
                        rebuildUi();
                        return;
                    }
                    ClientPlayNetworking.send(new RaidStartRunPayload(menu.getBlockPos()));
                });
                queueBanner.child(startOrQueue);

                ButtonComponent leaveQueue = smallButton(Component.translatable("gui.arenas_ld.raid_controller.button.leave_queue"), b -> {
                    footerError = null;
                    ClientPlayNetworking.send(new RaidLeaveLobbyPayload(menu.getBlockPos()));
                });
                queueBanner.child(leaveQueue);
            }

            contentArea.child(queueBanner);
            contentArea.child(spacer(4));
        }

        summaryOwnerLabel = text(Component.literal(lobby.ownerName()), ACCENT);
        summaryMembersLabel = text(Component.literal(lobby.members().size() + " / " + menu.getMaxPartySize()), INK);
        summaryTierValue = text(Component.literal(titleCase(lobby.selectedTier().name())), tierColor(lobby.selectedTier()));
        summaryVisibilityValue = text(Component.literal(titleCase(lobby.visibility().name())), visibilityColor(lobby.visibility()));
        summaryHardcoreValue = text(Component.translatable(lobby.hardcoreEnabled() ? "gui.arenas_ld.dungeon_controller.ui.hardcore.on" : "gui.arenas_ld.dungeon_controller.ui.hardcore.off"), lobby.hardcoreEnabled() ? DANGER : INK_MID);

        FlowLayout summary = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        summary.surface(Surface.flat(ROW_BG).and(Surface.outline(HAIRLINE)));
        summary.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout summaryAccent = Containers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
        summaryAccent.surface(Surface.flat(ACCENT));
        summary.child(summaryAccent);
        FlowLayout summaryPad = Containers.verticalFlow(Sizing.fixed(9), Sizing.fill(100));
        summaryPad.surface(Surface.BLANK);
        summary.child(summaryPad);
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.owner"), summaryOwnerLabel, Sizing.fill(24)));
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.tier"), summaryTierValue, Sizing.fill(18)));
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.members"), summaryMembersLabel, Sizing.fill(18)));
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility"), summaryVisibilityValue, Sizing.fill(18)));
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.hardcore"), summaryHardcoreValue, Sizing.fill(18)));
        contentArea.child(summary);
        contentArea.child(membersSectionHeader(lobby.readyMembers().size(), lobby.members().size(), menu.getMaxPartySize()));

        FlowLayout memberList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        memberList.child(memberHeaderRow(isOwner));
        memberList.child(rowDivider(HAIRLINE_HI));
        List<UUID> orderedMembers = lobby.members().stream()
            .sorted(Comparator.comparing((UUID m) -> m.equals(lobby.ownerUuid()) ? 0 : 1)
                .thenComparing(m -> lobby.memberNames().getOrDefault(m, shortUuid(m)).toLowerCase(java.util.Locale.ROOT)))
            .limit(5)
            .toList();
        boolean firstMemberRow = true;
        for (UUID member : orderedMembers) {
            if (!firstMemberRow) {
                memberList.child(rowDivider(HAIRLINE));
            }
            memberList.child(memberRow(lobby, member, isOwner));
            firstMemberRow = false;
        }
        contentArea.child(memberList);

        contentArea.child(spacer(6));
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.ui.section.owner_controls"), null));

        contentArea.child(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.col.tier")));
        contentArea.child(ownerTierControls(isOwner));

        contentArea.child(spacer(4));
        contentArea.child(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility")));
        contentArea.child(ownerVisibilityControls(isOwner));

        contentArea.child(spacer(4));
        contentArea.child(hardcoreControl(isOwner));

        contentArea.child(spacer(4));
        contentArea.child(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.section.invite_player")));
        contentArea.child(ownerInviteControls(isOwner));

        boolean ready = lobby.readyMembers().contains(self);
        boolean allReady = lobby.allReady();
        boolean allOnline = allOnline(lobby);
        boolean canStart = isOwner && allReady && allOnline && queuePos < 1;

        readyToggleButton = smallButton(Component.translatable(ready
            ? "gui.arenas_ld.dungeon_controller.button.unready"
            : "gui.arenas_ld.dungeon_controller.button.ready"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidToggleReadyPayload(menu.getBlockPos()));
        });
        footerActions.child(readyToggleButton);

        leaveLobbyButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.leave"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidLeaveLobbyPayload(menu.getBlockPos()));
        });
        footerActions.child(leaveLobbyButton);

        ButtonComponent start = smallButton(Component.translatable("gui.arenas_ld.raid_controller.button.start"), b -> {
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
                footerError = Component.translatable("gui.arenas_ld.raid_controller.error.only_owner").getString();
                return;
            }
            if (!currentAllReady) {
                footerError = Component.translatable("gui.arenas_ld.raid_controller.error.all_ready_required").getString();
                return;
            }
            if (!currentAllOnline) {
                footerError = Component.translatable("gui.arenas_ld.raid_controller.error.all_online_required").getString();
                return;
            }
            ClientPlayNetworking.send(new RaidStartRunPayload(menu.getBlockPos()));
        });
        start.active(canStart);
        startRunButton = start;
        footerActions.child(startRunButton);
    }

    // ── Member row ───────────────────────────────────────────────────────────

    private FlowLayout memberRow(Lobby lobby, UUID member, boolean isOwner) {
        boolean online = isOnline(member);
        boolean ready = lobby.readyMembers().contains(member);
        boolean isLobbyOwner = member.equals(lobby.ownerUuid());

        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(ROW_BG));
        row.padding(Insets.of(7, 7, 8, 8));
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout statusSquare = Containers.horizontalFlow(Sizing.fixed(18), Sizing.fixed(18));
        statusSquare.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        LabelComponent statusGlyph = text(Component.literal(online ? "✓" : "!"), online ? GOOD : WARN);
        applyStatusSquare(statusSquare, online);
        statusSquare.child(statusGlyph);
        row.child(statusSquare);

        String name = lobby.memberNames().getOrDefault(member, shortUuid(member));
        FlowLayout nameCell = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        nameCell.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        nameCell.gap(6);
        nameCell.child(text(Component.literal(name), INK));
        if (isLobbyOwner) {
            nameCell.child(badge(Component.literal("★ ").append(Component.translatable("gui.arenas_ld.dungeon_controller.ui.owner_badge")), ACCENT));
        }
        row.child(nameCell);

        LabelComponent connectionLabel = text(Component.literal(online ? "● " : "○ ").append(Component.translatable(online ? "gui.arenas_ld.dungeon_controller.ui.member.online" : "gui.arenas_ld.dungeon_controller.ui.member.offline")), online ? GOOD : DANGER);
        connectionLabel.horizontalSizing(Sizing.fixed(84));
        row.child(connectionLabel);

        FlowLayout readyBadge = fixedBadge(Component.translatable(ready ? "gui.arenas_ld.dungeon_controller.ui.member.ready" : "gui.arenas_ld.dungeon_controller.ui.member.not_ready"), 84, ready ? GOOD : INK_DIM);
        row.child(readyBadge);

        ButtonComponent kickButton = null;
        if (isOwner && !isLobbyOwner) {
            kickButton = dangerButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.kick"), b -> {
                footerError = null;
                ClientPlayNetworking.send(new RaidKickFromLobbyPayload(menu.getBlockPos(), member));
            });
            kickButton.horizontalSizing(Sizing.fixed(54));
            row.child(kickButton);
        } else {
            FlowLayout kickSpace = Containers.horizontalFlow(Sizing.fixed(54), Sizing.content());
            kickSpace.surface(Surface.BLANK);
            row.child(kickSpace);
        }
        memberRowRefs.put(member, new MemberRowRefs(statusSquare, statusGlyph, connectionLabel, readyBadge, kickButton));
        return row;
    }

    // ── Owner controls ───────────────────────────────────────────────────────

    private FlowLayout ownerTierControls(boolean isOwner) {
        ButtonComponent easy = segmentButton("EASY", tierColor(DifficultyTier.EASY), isOwner,
            () -> ownLobby.map(Lobby::selectedTier).orElse(null) == DifficultyTier.EASY,
            b -> ClientPlayNetworking.send(new RaidSetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.EASY)));
        ButtonComponent normal = segmentButton("NORMAL", tierColor(DifficultyTier.NORMAL), isOwner,
            () -> ownLobby.map(Lobby::selectedTier).orElse(null) == DifficultyTier.NORMAL,
            b -> ClientPlayNetworking.send(new RaidSetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.NORMAL)));
        ButtonComponent hard = segmentButton("HARD", tierColor(DifficultyTier.HARD), isOwner,
            () -> ownLobby.map(Lobby::selectedTier).orElse(null) == DifficultyTier.HARD,
            b -> ClientPlayNetworking.send(new RaidSetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.HARD)));
        ButtonComponent nightmare = segmentButton("NIGHTMARE", tierColor(DifficultyTier.NIGHTMARE), isOwner,
            () -> ownLobby.map(Lobby::selectedTier).orElse(null) == DifficultyTier.NIGHTMARE,
            b -> ClientPlayNetworking.send(new RaidSetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.NIGHTMARE)));
        return segmentedControl(easy, normal, hard, nightmare);
    }

    private FlowLayout ownerVisibilityControls(boolean isOwner) {
        ButtonComponent pub = segmentButton("PUBLIC", visibilityColor(LobbyVisibility.PUBLIC), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.PUBLIC,
            b -> ClientPlayNetworking.send(new RaidSetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.PUBLIC)));
        ButtonComponent fr = segmentButton("FRIENDS", visibilityColor(LobbyVisibility.FRIENDS), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.FRIENDS,
            b -> ClientPlayNetworking.send(new RaidSetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.FRIENDS)));
        ButtonComponent pr = segmentButton("PRIVATE", visibilityColor(LobbyVisibility.PRIVATE), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.PRIVATE,
            b -> ClientPlayNetworking.send(new RaidSetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.PRIVATE)));
        return segmentedControl(pub, fr, pr);
    }

    private FlowLayout hardcoreControl(boolean isOwner) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(ROW_BG).and(Surface.outline(HAIRLINE)));
        row.padding(Insets.of(8, 8, 10, 10));
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout textCol = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        textCol.gap(2);
        textCol.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.hardcore.title"), INK_DIM));
        textCol.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.hardcore.desc"), INK));
        row.child(textCol);

        ButtonComponent toggle = toggleSwitch(isOwner,
            () -> ownLobby.map(Lobby::hardcoreEnabled).orElse(false),
            b -> {
                footerError = null;
                boolean currentHardcore = ownLobby.map(Lobby::hardcoreEnabled).orElse(false);
                ClientPlayNetworking.send(new RaidSetLobbyHardcorePayload(menu.getBlockPos(), !currentHardcore));
            });
        row.child(toggle);
        return row;
    }

    private FlowLayout ownerInviteControls(boolean isOwner) {
        FlowLayout section = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        section.gap(0);

        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        inviteFieldRow = row;

        FlowLayout fieldWrap = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        fieldWrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout fieldAccent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fixed(20));
        fieldAccent.surface(Surface.flat(ACCENT));
        fieldWrap.child(fieldAccent);

        inviteField = Components.textBox(Sizing.expand(), inviteInput);
        inviteField.verticalSizing(Sizing.fixed(18));
        inviteField.active = isOwner;
        inviteField.onChanged().subscribe(value -> {
            inviteInput = value;
            if (inviteDropdownOpen) {
                refreshInviteDropdown();
            }
        });
        inviteField.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (isOwner) {
                openInviteDropdown();
            }
            return false;
        });
        fieldWrap.child(inviteField);

        inviteChevron = Components.button(Component.empty(), b -> {
            if (isOwner) {
                toggleInviteDropdown();
            }
        });
        inviteChevron.sizing(Sizing.fixed(18), Sizing.fixed(20));
        inviteChevron.active(isOwner);
        inviteChevron.renderer((context, rendered, delta) -> {
            String glyph = inviteDropdownOpen ? "▲" : "▼";
            int tx = rendered.getX() + (rendered.getWidth() - this.font.width(glyph)) / 2;
            int ty = rendered.getY() + (rendered.getHeight() - this.font.lineHeight) / 2 + 1;
            context.drawString(this.font, glyph, tx, ty, INK_MID, false);
        });
        fieldWrap.child(inviteChevron);
        row.child(fieldWrap);

        ButtonComponent invite = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.invite"), b -> sendInvite());
        invite.active(isOwner);
        invite.horizontalSizing(Sizing.fixed(58));
        row.child(invite);

        section.child(row);

        inviteDropdownPanel = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        inviteDropdownPanel.surface(Surface.BLANK);
        section.child(inviteDropdownPanel);
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
        ClientPlayNetworking.send(new RaidInvitePlayerPayload(menu.getBlockPos(), invitee));
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
            // fallthrough — treat as a player name
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
                contentScroll.scrollTo(1.0D);
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
        if (inviteDropdownPanel == null) {
            return;
        }
        inviteDropdownPanel.clearChildren();
        if (!inviteDropdownOpen) {
            inviteDropdownPanel.surface(Surface.BLANK);
            return;
        }
        inviteDropdownPanel.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));

        List<InviteCandidate> candidates = inviteCandidates(inviteInput);
        long available = candidates.stream().filter(InviteCandidate::selectable).count();

        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        header.surface(Surface.flat(ROW_BG));
        header.padding(Insets.of(4, 4, 8, 8));
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        header.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite.header"), INK_DIM));
        header.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        header.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite.available", available), ACCENT));
        inviteDropdownPanel.child(header);
        inviteDropdownPanel.child(rowDivider(HAIRLINE_HI));

        if (candidates.isEmpty()) {
            FlowLayout empty = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            empty.padding(Insets.of(8));
            empty.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            empty.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite.empty"), INK_DIM));
            inviteDropdownPanel.child(empty);
            return;
        }

        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        boolean first = true;
        for (InviteCandidate candidate : candidates) {
            if (!first) {
                list.child(rowDivider(HAIRLINE));
            }
            list.child(inviteCandidateRow(candidate));
            first = false;
        }

        int rowsHeight = candidates.size() * 22 + Math.max(0, candidates.size() - 1);
        int visibleHeight = Math.min(rowsHeight, INVITE_DROPDOWN_MAX_HEIGHT);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(visibleHeight), list);
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        inviteDropdownPanel.child(scroll);
    }

    private ButtonComponent inviteCandidateRow(InviteCandidate candidate) {
        boolean selectable = candidate.selectable();
        ButtonComponent row = Components.button(Component.empty(), b -> {
            if (!selectable) {
                return;
            }
            footerError = null;
            ClientPlayNetworking.send(new RaidInvitePlayerPayload(menu.getBlockPos(), candidate.uuid()));
            inviteInput = "";
            if (inviteField != null) {
                inviteField.text("");
            }
            closeInviteDropdown();
        });
        row.sizing(Sizing.fill(100), Sizing.fixed(22));
        row.active(selectable);
        int statusColor = selectable ? GOOD : WARN;
        int alpha = selectable ? 0xFF000000 : 0x73000000;
        String statusText = switch (candidate.status()) {
            case ONLINE -> tr("gui.arenas_ld.dungeon_controller.ui.member.online");
            case IN_LOBBY -> tr("gui.arenas_ld.dungeon_controller.ui.candidate.in_lobby");
            case IN_RUN -> tr("gui.arenas_ld.dungeon_controller.ui.candidate.in_run");
            case BUSY -> tr("gui.arenas_ld.dungeon_controller.ui.candidate.busy");
        };
        String nameText = "@" + candidate.name();
        row.renderer((context, rendered, delta) -> {
            int x1 = rendered.getX();
            int y1 = rendered.getY();
            int w = rendered.getWidth();
            int h = rendered.getHeight();
            boolean hover = selectable && rendered.isHoveredOrFocused();
            context.fill(x1, y1, x1 + w, y1 + h, hover ? ROW_BG : PANEL_2);
            if (hover) {
                context.fill(x1, y1, x1 + 2, y1 + h, ACCENT);
            }
            int squareColor = (statusColor & 0x00FFFFFF) | alpha;
            context.fill(x1 + 8, y1 + h / 2 - 4, x1 + 16, y1 + h / 2 + 4, squareColor);
            int nameColor = (INK & 0x00FFFFFF) | alpha;
            context.drawString(this.font, nameText, x1 + 24, y1 + (h - this.font.lineHeight) / 2 + 1, nameColor, false);
            int statusTextColor = (statusColor & 0x00FFFFFF) | alpha;
            int statusWidth = this.font.width(statusText);
            context.drawString(this.font, statusText, x1 + w - statusWidth - 10, y1 + (h - this.font.lineHeight) / 2 + 1, statusTextColor, false);
        });
        return row;
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

    // ── Header rows ──────────────────────────────────────────────────────────

    private FlowLayout inviteHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.from"), Sizing.expand()));
        row.child(headerCell("", Sizing.fixed(40)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.tier"), Sizing.fixed(86)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.expires"), Sizing.fixed(62)));
        row.child(headerCell("", Sizing.fixed(54)));
        row.child(headerCell("", Sizing.fixed(58)));
        return row;
    }

    private FlowLayout lobbyHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(8);
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.owner"), Sizing.expand()));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.size"), Sizing.fixed(44)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.tier"), Sizing.fixed(70)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility"), Sizing.fixed(78)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.status"), Sizing.fixed(82)));
        row.child(headerCell("", Sizing.fixed(84)));
        return row;
    }

    private FlowLayout memberHeaderRow(boolean isOwner) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(PANEL_2));
        row.padding(Insets.of(5, 5, 8, 8));
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout statusSpacer = Containers.horizontalFlow(Sizing.fixed(18), Sizing.content());
        statusSpacer.surface(Surface.BLANK);
        row.child(statusSpacer);
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.name"), Sizing.expand()));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.connection"), Sizing.fixed(84)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.ready"), Sizing.fixed(84)));
        row.child(headerCell(isOwner ? tr("gui.arenas_ld.dungeon_controller.ui.col.action") : "", Sizing.fixed(54)));
        return row;
    }

    // ── UI widget helpers ────────────────────────────────────────────────────

    private ButtonComponent smallButton(Component text, java.util.function.Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text, action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.active() ? (rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK) : PANEL;
            int border = rendered.active() ? ACCENT : HAIRLINE;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return button;
    }

    private LabelComponent dimLabel(Component text) {
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(INK_DIM));
        return label;
    }

    private LabelComponent text(Component text, int color) {
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(color));
        return label;
    }

    private FlowLayout sectionHeader(Component title, Integer count) {
        return sectionHeaderWithAction(title, count, null);
    }

    private FlowLayout sectionHeaderWithAction(Component title, Integer count, ButtonComponent action) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.gap(8);
        LabelComponent titleLabel = text(title, INK_DIM);
        row.child(titleLabel);
        if (count != null) {
            row.child(text(Component.literal("· " + count), ACCENT));
        }
        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        if (action != null) {
            row.child(action);
        }
        return row;
    }

    private FlowLayout membersSectionHeader(int readyCount, int count, int max) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.gap(6);
        row.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.section.members"), INK_DIM));
        membersSummaryLabel = text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.members_summary", readyCount, count, max), ACCENT);
        row.child(membersSummaryLabel);
        return row;
    }

    private FlowLayout rowDivider(int color) {
        FlowLayout divider = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface(Surface.flat(color));
        return divider;
    }

    private void applyStatusSquare(FlowLayout square, boolean online) {
        int color = online ? GOOD : WARN;
        square.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x66000000)));
    }

    private ButtonComponent dangerButton(Component text, java.util.function.Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text.copy().withStyle(ChatFormatting.RED), action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ((DANGER & 0x00FFFFFF) | 0x44000000) : ((DANGER & 0x00FFFFFF) | 0x1F000000);
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), DANGER);
        });
        return button;
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private LabelComponent controlCaption(String caption) {
        LabelComponent label = Components.label(Component.literal(caption));
        label.color(Color.ofArgb(INK_DIM));
        return label;
    }

    private ButtonComponent segmentButton(String label, int accentColor, boolean active, java.util.function.BooleanSupplier selected, java.util.function.Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(Component.empty(), action);
        button.sizing(Sizing.fill(100), Sizing.fixed(26));
        button.active(active);
        button.renderer((context, rendered, delta) -> {
            boolean sel = selected.getAsBoolean();
            boolean isActive = rendered.active();
            boolean hover = rendered.isHoveredOrFocused();
            int x1 = rendered.getX();
            int y1 = rendered.getY();
            int w = rendered.getWidth();
            int h = rendered.getHeight();
            int fill = sel ? ((accentColor & 0x00FFFFFF) | 0x26000000) : (isActive && hover ? ROW_BG : PANEL_2);
            int border = sel ? accentColor : HAIRLINE;
            context.fill(x1, y1, x1 + w, y1 + h, fill);
            context.drawRectOutline(x1, y1, w, h, border);
            int textColor = sel ? accentColor : (isActive ? INK : INK_DIM);
            int tx = x1 + (w - this.font.width(label)) / 2;
            int ty = y1 + (h - this.font.lineHeight) / 2 + 1;
            context.drawString(this.font, label, tx, ty, textColor, false);
        });
        return button;
    }

    private FlowLayout segmentedControl(ButtonComponent... buttons) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        int n = buttons.length;
        int base = 100 / n;
        for (int i = 0; i < n; i++) {
            int pct = (i == n - 1) ? (100 - base * (n - 1)) : base;
            FlowLayout cell = Containers.verticalFlow(Sizing.fill(pct), Sizing.content());
            cell.surface(Surface.BLANK);
            cell.padding(Insets.of(0, 0, i == 0 ? 0 : 3, i == n - 1 ? 0 : 3));
            cell.child(buttons[i]);
            row.child(cell);
        }
        return row;
    }

    private ButtonComponent toggleSwitch(boolean active, java.util.function.BooleanSupplier on, java.util.function.Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(Component.empty(), action);
        button.sizing(Sizing.fixed(38), Sizing.fixed(18));
        button.active(active);
        button.renderer((context, rendered, delta) -> {
            boolean isOn = on.getAsBoolean();
            int x1 = rendered.getX();
            int y1 = rendered.getY();
            int w = rendered.getWidth();
            int h = rendered.getHeight();
            int track = isOn ? ((DANGER & 0x00FFFFFF) | 0x55000000) : PANEL_2;
            int border = isOn ? DANGER : HAIRLINE;
            context.fill(x1, y1, x1 + w, y1 + h, track);
            context.drawRectOutline(x1, y1, w, h, border);
            int knobW = w / 2 - 3;
            int knobX = isOn ? (x1 + w - knobW - 2) : (x1 + 2);
            int knobColor = isOn ? DANGER : INK;
            context.fill(knobX, y1 + 2, knobX + knobW, y1 + h - 2, knobColor);
        });
        return button;
    }

    private FlowLayout infoColumn(String caption, LabelComponent value, Sizing width) {
        FlowLayout col = Containers.verticalFlow(width, Sizing.content());
        col.gap(3);
        LabelComponent captionLabel = Components.label(Component.literal(caption));
        captionLabel.color(Color.ofArgb(INK_DIM));
        col.child(captionLabel);
        col.child(value);
        return col;
    }

    private static String titleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.charAt(0) + value.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private LabelComponent headerCell(String text, Sizing sizing) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(INK_DIM));
        label.horizontalSizing(sizing);
        return label;
    }

    private LabelComponent fixedText(Component text, int width, int color, HorizontalAlignment alignment) {
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(color));
        label.horizontalSizing(Sizing.fixed(width));
        label.horizontalTextAlignment(alignment);
        return label;
    }

    private FlowLayout badge(Component text, int color) {
        FlowLayout tag = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        tag.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        tag.padding(Insets.of(4, 2, 4, 4));
        tag.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        tag.child(this.text(text, color));
        return tag;
    }

    private FlowLayout fixedBadge(Component text, int width, int color) {
        FlowLayout tag = Containers.horizontalFlow(Sizing.fixed(width), Sizing.content());
        tag.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        tag.padding(Insets.of(4, 2, 4, 4));
        tag.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        LabelComponent label = this.text(text, color);
        label.horizontalSizing(Sizing.expand());
        label.horizontalTextAlignment(HorizontalAlignment.CENTER);
        tag.child(label);
        return tag;
    }

    private FlowLayout rowPanel(boolean accentLeft) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(accentLeft ? ROW_BG_ALT : ROW_BG).and(Surface.outline(accentLeft ? ACCENT_DARK : HAIRLINE)));
        row.padding(Insets.of(6));
        return row;
    }

    private LabelComponent smallMeta(String text) {
        return smallMeta(text, INK_DIM);
    }

    private LabelComponent smallMeta(String text, int color) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(color));
        return label;
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

    private FlowLayout spacer(int px) {
        FlowLayout spacer = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        spacer.surface(Surface.BLANK);
        return spacer;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    /**
     * Called when a full snapshot arrives from the server.
     */
    public void applyData(RaidControllerData data) {
        Optional<Lobby> previousOwnLobby = this.ownLobby;
        int previousQueuePosition = this.lastQueuePosition;
        menu.applyData(data);
        syncFromMenu(true);
        this.lastQueuePosition = menu.getQueuePosition();

        // Queue position changed → the banner appears/disappears, so do a full rebuild rather than
        // the in-place member refresh (which wouldn't add/remove the banner).
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

    /**
     * Legacy compatibility method — just triggers a full UI rebuild with current data.
     */
    public void applyServerInfo(ModPackets.RaidControllerInfoPayload payload) {
        rebuildUi();
    }

    // ── Internal sync ────────────────────────────────────────────────────────

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
        this.instances.clear();
        this.instances.addAll(menu.getInstances());
        this.snapshotServerTick = menu.getServerGameTick();
        this.snapshotEpochMs = System.currentTimeMillis();

        if (!preserveTab && this.ownLobby.isPresent()) {
            this.currentTab = Tab.MY_LOBBY;
        }
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
        lobbiesTabButton.active(currentTab != Tab.LOBBIES);
        myLobbyTabButton.active(currentTab != Tab.MY_LOBBY);
        lobbiesTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.lobbies").append(" " + visibleLobbies.size()));
        myLobbyTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.my_lobby").append(ownLobby.isPresent() ? " " + ownLobby.get().members().size() : ""));
    }

    private void refreshMyLobbyInPlace(Lobby lobby) {
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : UUID.randomUUID();
        boolean isOwner = lobby.ownerUuid().equals(self);
        boolean ready = lobby.readyMembers().contains(self);
        boolean allReady = lobby.allReady();
        boolean allOnline = allOnline(lobby);
        int queuePos = menu.getQueuePosition();
        boolean canStart = isOwner && allReady && allOnline && queuePos < 1;

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
            summaryTierValue.color(Color.ofArgb(tierColor(lobby.selectedTier())));
        }
        if (summaryVisibilityValue != null) {
            summaryVisibilityValue.text(Component.literal(titleCase(lobby.visibility().name())));
            summaryVisibilityValue.color(Color.ofArgb(visibilityColor(lobby.visibility())));
        }
        if (summaryHardcoreValue != null) {
            summaryHardcoreValue.text(Component.translatable(lobby.hardcoreEnabled() ? "gui.arenas_ld.dungeon_controller.ui.hardcore.on" : "gui.arenas_ld.dungeon_controller.ui.hardcore.off"));
            summaryHardcoreValue.color(Color.ofArgb(lobby.hardcoreEnabled() ? DANGER : INK_MID));
        }
        if (readyToggleButton != null) {
            readyToggleButton.setMessage(Component.translatable(ready
                ? "gui.arenas_ld.dungeon_controller.button.unready"
                : "gui.arenas_ld.dungeon_controller.button.ready"));
        }
        if (leaveLobbyButton != null) {
            leaveLobbyButton.active(true);
        }
        if (startRunButton != null) {
            startRunButton.active(canStart);
        }

        for (UUID member : lobby.members()) {
            MemberRowRefs refs = memberRowRefs.get(member);
            if (refs == null) continue;
            boolean online = isOnline(member);
            boolean memberReady = lobby.readyMembers().contains(member);
            applyStatusSquare(refs.statusSquare, online);
            refs.statusGlyph.text(Component.literal(online ? "✓" : "!"));
            refs.statusGlyph.color(Color.ofArgb(online ? GOOD : WARN));
            refs.connectionLabel.text(Component.literal(online ? "● " : "○ ").append(Component.translatable(online ? "gui.arenas_ld.dungeon_controller.ui.member.online" : "gui.arenas_ld.dungeon_controller.ui.member.offline")));
            refs.connectionLabel.color(Color.ofArgb(online ? GOOD : DANGER));
            updateBadge(refs.readyBadge, Component.translatable(memberReady ? "gui.arenas_ld.dungeon_controller.ui.member.ready" : "gui.arenas_ld.dungeon_controller.ui.member.not_ready"), memberReady ? GOOD : INK_DIM);
            if (refs.kickButton != null) {
                refs.kickButton.active(isOwner && !member.equals(lobby.ownerUuid()));
            }
        }

        if (inviteDropdownOpen) {
            refreshInviteDropdown();
        }

        footerLabel.text(footerError == null ? Component.empty() : Component.literal(footerError).withStyle(ChatFormatting.RED));
    }

    private void refreshInviteCountdowns() {
        for (PendingInvite invite : myInvites) {
            LabelComponent label = inviteCountdownLabels.get(invite.lobbyId());
            if (label == null) continue;
            long remainingTicks = Math.max(0L, invite.expiresAtTick() - approximateServerTick());
            long remainingSeconds = remainingTicks / 20L;
            label.text(Component.literal(formatRemaining(remainingSeconds)));
            label.color(Color.ofArgb(remainingSeconds <= 15 ? WARN : INK_MID));
        }
    }

    private void refreshJoinRequestCountdowns() {
        for (PendingJoinRequest req : myJoinRequests) {
            LabelComponent label = joinRequestCountdownLabels.get(req.requesterUuid());
            if (label == null) continue;
            long remainingTicks = Math.max(0L, req.expiresAtTick() - approximateServerTick());
            long remainingSeconds = remainingTicks / 20L;
            label.text(Component.literal(formatRemaining(remainingSeconds)));
            label.color(Color.ofArgb(remainingSeconds <= 15 ? WARN : INK_MID));
        }
    }

    private void captureScrollProgress() {
        if (contentScroll == null) return;
        try {
            Field maxScrollField = ScrollContainer.class.getDeclaredField("maxScroll");
            Field scrollOffsetField = ScrollContainer.class.getDeclaredField("scrollOffset");
            maxScrollField.setAccessible(true);
            scrollOffsetField.setAccessible(true);

            int maxScroll = maxScrollField.getInt(contentScroll);
            double scrollOffset = scrollOffsetField.getDouble(contentScroll);
            savedScrollProgress = maxScroll <= 0 ? 0.0D : Math.max(0.0D, Math.min(1.0D, scrollOffset / (double) maxScroll));
        } catch (ReflectiveOperationException ignored) {
            savedScrollProgress = 0.0D;
        }
    }

    private void updateBadge(FlowLayout badge, Component text, int color) {
        badge.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        if (!badge.children().isEmpty() && badge.children().get(0) instanceof LabelComponent label) {
            label.text(text);
            label.color(Color.ofArgb(color));
        }
    }

    private boolean sameLobbyStructure(Lobby previous, Lobby current) {
        return previous.lobbyId().equals(current.lobbyId())
            && previous.ownerUuid().equals(current.ownerUuid())
            && previous.members().equals(current.members());
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
