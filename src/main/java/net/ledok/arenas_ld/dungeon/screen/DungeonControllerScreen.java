package net.ledok.arenas_ld.dungeon.screen;

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
import net.ledok.arenas_ld.dungeon.packet.AcceptInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.CreateLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.InvitePlayerPayload;
import net.ledok.arenas_ld.dungeon.packet.JoinLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.KickFromLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.LeaveLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyHardcorePayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyTierPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyVisibilityPayload;
import net.ledok.arenas_ld.dungeon.packet.StartRunPayload;
import net.ledok.arenas_ld.dungeon.packet.ToggleReadyPayload;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
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

public class DungeonControllerScreen extends BaseOwoHandledScreen<FlowLayout, DungeonControllerScreenHandler> {
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

    private enum Tab {
        LOBBIES,
        MY_LOBBY,
        INVITES
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
    private Optional<Lobby> ownLobby;

    private Tab currentTab = Tab.LOBBIES;
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
    private ButtonComponent invitesTabButton;
    private TextBoxComponent inviteField;
    private String inviteInput = "";
    private double savedScrollProgress = 0.0D;
    private boolean restoreScrollNextTick = false;
    private final Map<UUID, LabelComponent> inviteCountdownLabels = new HashMap<>();
    private final Map<UUID, MemberRowRefs> memberRowRefs = new HashMap<>();
    private LabelComponent summaryOwnerLabel;
    private LabelComponent summaryMembersLabel;
    private LabelComponent summaryTierValue;
    private LabelComponent summaryVisibilityValue;
    private LabelComponent summaryHardcoreValue;
    private LabelComponent tierSummaryLabel;
    private LabelComponent visibilitySummaryLabel;
    private LabelComponent hardcoreSummaryLabel;
    private ButtonComponent hardcoreToggleButton;
    private ButtonComponent readyToggleButton;
    private ButtonComponent leaveLobbyButton;
    private ButtonComponent startRunButton;

    public DungeonControllerScreen(DungeonControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
        this.visibleLobbies = new ArrayList<>(handler.getVisibleLobbies());
        this.myInvites = new ArrayList<>(handler.getMyInvites());
        this.ownLobby = handler.getOwnLobby();
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
        if ((currentTab == Tab.LOBBIES || currentTab == Tab.INVITES) && !myInvites.isEmpty()) {
            long now = approximateServerTick() / 20L;
            if (now != lastCountdownSecond) {
                lastCountdownSecond = now;
                refreshInviteCountdowns();
            }
        }
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
        LabelComponent titleLabel = Components.label(Component.translatable("container.arenas_ld.dungeon_controller"));
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
        close.renderer((context, button, delta) -> {
            int fill = button.isHoveredOrFocused() ? ROW_BG : PANEL;
            context.fill(button.getX(), button.getY(), button.getX() + button.getWidth(), button.getY() + button.getHeight(), fill);
            context.drawRectOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), HAIRLINE_HI);
        });
        header.child(close);
        return header;
    }

    private FlowLayout statusBadge() {
        Component label;
        int color;
        if (ownLobby.isPresent()) {
            label = Component.literal("IN LOBBY");
            color = INFO;
        } else if (!myInvites.isEmpty()) {
            label = Component.literal("INVITES " + myInvites.size());
            color = ACCENT;
        } else {
            label = Component.literal("AVAILABLE");
            color = GOOD;
        }
        return badge(label, color);
    }

    private FlowLayout buildTabs() {
        FlowLayout tabs = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        tabs.surface(Surface.flat(PANEL_2));
        tabs.padding(Insets.of(4));
        tabs.gap(4);

        lobbiesTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.lobbies", Tab.LOBBIES);
        myLobbyTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.my_lobby", Tab.MY_LOBBY);
        invitesTabButton = tabButton("gui.arenas_ld.dungeon_controller.tab.invites", Tab.INVITES);

        tabs.child(lobbiesTabButton);
        tabs.child(myLobbyTabButton);
        tabs.child(invitesTabButton);
        return tabs;
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
        memberRowRefs.clear();
        summaryOwnerLabel = null;
        summaryMembersLabel = null;
        summaryTierValue = null;
        summaryVisibilityValue = null;
        summaryHardcoreValue = null;
        tierSummaryLabel = null;
        visibilitySummaryLabel = null;
        hardcoreSummaryLabel = null;
        hardcoreToggleButton = null;
        readyToggleButton = null;
        leaveLobbyButton = null;
        startRunButton = null;

        lobbiesTabButton.active(currentTab != Tab.LOBBIES);
        myLobbyTabButton.active(currentTab != Tab.MY_LOBBY);
        invitesTabButton.active(currentTab != Tab.INVITES);
        lobbiesTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.lobbies").append(" " + visibleLobbies.size()));
        myLobbyTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.my_lobby").append(ownLobby.isPresent() ? " " + ownLobby.get().members().size() : ""));
        invitesTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller.tab.invites").append(" " + myInvites.size()));

        if (currentTab == Tab.LOBBIES) {
            buildLobbiesContent();
        } else if (currentTab == Tab.INVITES) {
            buildInvitesContent();
        } else {
            buildMyLobbyContent();
        }

        footerLabel.text(footerError == null ? Component.empty() : Component.literal(footerError).withStyle(ChatFormatting.RED));
        if (contentScroll != null && savedScrollProgress > 0.0D) {
            contentScroll.scrollTo(savedScrollProgress);
            restoreScrollNextTick = true;
        } else {
            restoreScrollNextTick = false;
        }
    }

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
    }

    private void buildLobbiesContent() {
        ButtonComponent create = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.create"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new CreateLobbyPayload(menu.getBlockPos()));
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
        String inviterName = inviteLobby
            .map(l -> l.memberNames().getOrDefault(invite.inviterUuid(), shortUuid(invite.inviterUuid())))
            .orElse(shortUuid(invite.inviterUuid()));
        String ownerName = inviteLobby.map(Lobby::ownerName).orElseGet(() -> shortUuid(invite.lobbyId()));

        long remainingTicks = Math.max(0L, invite.expiresAtTick() - approximateServerTick());
        long remainingSeconds = remainingTicks / 20L;
        FlowLayout inviteInfo = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        FlowLayout fromLine = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        fromLine.gap(3);
        fromLine.child(text(Component.literal("From"), INK));
        fromLine.child(text(Component.literal(inviterName), ACCENT));
        inviteInfo.child(fromLine);
        inviteInfo.child(text(Component.literal("LOBBY OWNER: " + ownerName.toUpperCase()), INK_DIM));
        row.child(inviteInfo);
        row.child(fixedText(Component.literal("-"), 40, INK_DIM, HorizontalAlignment.CENTER));

        Component badgeText;
        int badgeColor;
        if (inviteLobby.isPresent()) {
            DifficultyTier inviteTier = inviteLobby.get().selectedTier();
            badgeText = Component.literal(inviteTier.name());
            badgeColor = tierColor(inviteTier);
        } else {
            badgeText = Component.literal(shortUuid(invite.lobbyId()).toUpperCase());
            badgeColor = INFO;
        }
        row.child(fixedBadge(badgeText, 86, badgeColor));
        LabelComponent countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, HorizontalAlignment.CENTER);
        inviteCountdownLabels.put(invite.lobbyId(), countdown);
        row.child(countdown);

        ButtonComponent accept = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new AcceptInvitePayload(menu.getBlockPos(), invite.lobbyId()));
        });
        accept.horizontalSizing(Sizing.fixed(54));
        row.child(accept);

        ButtonComponent decline = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new DeclineInvitePayload(menu.getBlockPos(), invite.lobbyId()));
        });
        decline.horizontalSizing(Sizing.fixed(58));
        row.child(decline);

        return row;
    }

    private FlowLayout lobbyRow(Lobby lobby) {
        FlowLayout row = rowPanel(false);
        row.gap(8);

        FlowLayout ownerInfo = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        ownerInfo.child(text(Component.literal(lobby.ownerName()), INK));
        String idLine = shortUuid(lobby.lobbyId()).toUpperCase() + (lobby.hardcoreEnabled() ? " · HC" : "");
        ownerInfo.child(text(Component.literal(idLine), INK_DIM));
        row.child(ownerInfo);
        row.child(fixedBadge(Component.literal(lobby.members().size() + "/" + menu.getMaxPartySize()), 44, INK_MID));
        row.child(fixedBadge(Component.literal(lobby.selectedTier().name()), 70, tierColor(lobby.selectedTier())));

        FlowLayout visCell = Containers.horizontalFlow(Sizing.fixed(78), Sizing.content());
        int visColor = visibilityColor(lobby.visibility());
        visCell.surface(Surface.flat((visColor & 0x00FFFFFF) | 0x22000000).and(Surface.outline((visColor & 0x00FFFFFF) | 0x55000000)));
        visCell.padding(Insets.of(2, 4, 2, 4));
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
            actionButton = smallButton(Component.literal("RUNNING"), b -> {});
            actionButton.active(false);
        } else if (isFull) {
            actionButton = smallButton(Component.literal("FULL"), b -> {});
            actionButton.active(false);
        } else if (lobby.visibility() == LobbyVisibility.PUBLIC) {
            actionButton = smallButton(Component.literal("JOIN"), b -> {
                footerError = null;
                ClientPlayNetworking.send(new JoinLobbyPayload(menu.getBlockPos(), lobbyId));
            });
            actionButton.active(!isMine && ownLobby.isEmpty());
        } else {
            actionButton = smallButton(Component.literal("REQUEST"), b ->
                footerError = Component.translatable("gui.arenas_ld.dungeon_controller.error.invite_only").getString()
            );
            actionButton.active(!isMine);
        }
        actionButton.horizontalSizing(Sizing.fill(100));
        actionCell.child(actionButton);
        row.child(actionCell);
        return row;
    }

    private void buildMyLobbyContent() {
        if (ownLobby.isEmpty()) {
            contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_own_lobby")));
            return;
        }

        Lobby lobby = ownLobby.get();
        UUID self = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : UUID.randomUUID();
        boolean isOwner = lobby.ownerUuid().equals(self);

        summaryOwnerLabel = text(Component.literal(lobby.ownerName()), ACCENT);
        summaryMembersLabel = text(Component.literal(lobby.members().size() + " / " + menu.getMaxPartySize()), INK);
        summaryTierValue = text(Component.literal(titleCase(lobby.selectedTier().name())), tierColor(lobby.selectedTier()));
        summaryVisibilityValue = text(Component.literal(titleCase(lobby.visibility().name())), visibilityColor(lobby.visibility()));
        summaryHardcoreValue = text(Component.literal(lobby.hardcoreEnabled() ? "On" : "Off"), lobby.hardcoreEnabled() ? DANGER : INK_MID);

        FlowLayout summary = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        summary.surface(Surface.flat(ROW_BG).and(Surface.outline(HAIRLINE)));
        summary.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout summaryAccent = Containers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
        summaryAccent.surface(Surface.flat(ACCENT));
        summary.child(summaryAccent);
        FlowLayout summaryPad = Containers.verticalFlow(Sizing.fixed(9), Sizing.fill(100));
        summaryPad.surface(Surface.BLANK);
        summary.child(summaryPad);
        summary.child(infoColumn("OWNER", summaryOwnerLabel, Sizing.fill(24)));
        summary.child(infoColumn("TIER", summaryTierValue, Sizing.fill(18)));
        summary.child(infoColumn("MEMBERS", summaryMembersLabel, Sizing.fill(18)));
        summary.child(infoColumn("VISIBILITY", summaryVisibilityValue, Sizing.fill(18)));
        summary.child(infoColumn("HARDCORE", summaryHardcoreValue, Sizing.fill(18)));
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

        contentArea.child(spacer(4));
        contentArea.child(sectionHeader(Component.literal("OWNER CONTROLS"), null));
        contentArea.child(labeledControlRow(tierSummaryLabel = text(Component.translatable("gui.arenas_ld.dungeon_controller.tier", lobby.selectedTier().name()), INK_MID), ownerTierControls(isOwner)));
        contentArea.child(labeledControlRow(visibilitySummaryLabel = text(Component.translatable("gui.arenas_ld.dungeon_controller.visibility", lobby.visibility().name()), INK_MID), ownerVisibilityControls(isOwner)));

        ButtonComponent hc = smallButton(Component.translatable(lobby.hardcoreEnabled()
            ? "gui.arenas_ld.dungeon_controller.hardcore.off"
            : "gui.arenas_ld.dungeon_controller.hardcore.on"), b -> {
            footerError = null;
            boolean currentHardcore = ownLobby.map(Lobby::hardcoreEnabled).orElse(false);
            ClientPlayNetworking.send(new SetLobbyHardcorePayload(menu.getBlockPos(), !currentHardcore));
        });
        hc.active(isOwner);
        hardcoreToggleButton = hc;
        hardcoreSummaryLabel = text(Component.translatable(
            "gui.arenas_ld.dungeon_controller.hardcore",
            Component.translatable(lobby.hardcoreEnabled()
                ? "gui.arenas_ld.dungeon_controller.hardcore.on"
                : "gui.arenas_ld.dungeon_controller.hardcore.off")
        ), INK_MID);
        contentArea.child(labeledControlRow(hardcoreSummaryLabel, hc));

        contentArea.child(labeledControlRow(text(Component.translatable("gui.arenas_ld.dungeon_controller.button.invite"), INK_MID), ownerInviteControls(isOwner)));

        boolean ready = lobby.readyMembers().contains(self);
        boolean allReady = lobby.allReady();
        boolean allOnline = allOnline(lobby);
        boolean canStart = isOwner && allReady && allOnline;

        readyToggleButton = smallButton(Component.translatable(ready
            ? "gui.arenas_ld.dungeon_controller.button.unready"
            : "gui.arenas_ld.dungeon_controller.button.ready"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new ToggleReadyPayload(menu.getBlockPos()));
        });
        footerActions.child(readyToggleButton);

        leaveLobbyButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.leave"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new LeaveLobbyPayload(menu.getBlockPos()));
        });
        footerActions.child(leaveLobbyButton);

        ButtonComponent start = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.start"), b -> {
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
            ClientPlayNetworking.send(new StartRunPayload(menu.getBlockPos()));
        });
        start.active(canStart);
        startRunButton = start;
        footerActions.child(startRunButton);
    }

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
            nameCell.child(badge(Component.literal("★ OWNER"), ACCENT));
        }
        row.child(nameCell);

        LabelComponent connectionLabel = text(Component.literal(online ? "● ONLINE" : "○ OFFLINE"), online ? GOOD : DANGER);
        connectionLabel.horizontalSizing(Sizing.fixed(84));
        row.child(connectionLabel);

        FlowLayout readyBadge = fixedBadge(Component.literal(ready ? "READY" : "NOT READY"), 84, ready ? GOOD : INK_DIM);
        row.child(readyBadge);

        ButtonComponent kickButton = null;
        if (isOwner && !isLobbyOwner) {
            kickButton = dangerButton(Component.literal("KICK"), b -> {
                footerError = null;
                ClientPlayNetworking.send(new KickFromLobbyPayload(menu.getBlockPos(), member));
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

    private FlowLayout ownerTierControls(boolean isOwner) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);

        ButtonComponent normal = smallButton(Component.literal("Normal"), b ->
            ClientPlayNetworking.send(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.NORMAL)));
        ButtonComponent hard = smallButton(Component.literal("Hard"), b ->
            ClientPlayNetworking.send(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.HARD)));
        ButtonComponent hell = smallButton(Component.literal("Hell"), b ->
            ClientPlayNetworking.send(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.HELL)));

        normal.active(isOwner);
        hard.active(isOwner);
        hell.active(isOwner);
        row.child(normal);
        row.child(hard);
        row.child(hell);
        return row;
    }

    private FlowLayout ownerVisibilityControls(boolean isOwner) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);

        ButtonComponent pub = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.visibility.public"), b ->
            ClientPlayNetworking.send(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.PUBLIC)));
        ButtonComponent fr = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.visibility.friends"), b ->
            ClientPlayNetworking.send(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.FRIENDS)));
        ButtonComponent pr = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.visibility.private"), b ->
            ClientPlayNetworking.send(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.PRIVATE)));

        pub.active(isOwner);
        fr.active(isOwner);
        pr.active(isOwner);
        row.child(pub);
        row.child(fr);
        row.child(pr);
        return row;
    }

    private FlowLayout ownerInviteControls(boolean isOwner) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);

        inviteField = Components.textBox(Sizing.fixed(170), inviteInput);
        inviteField.verticalSizing(Sizing.fixed(18));
        inviteField.onChanged().subscribe(value -> inviteInput = value);
        row.child(inviteField);

        ButtonComponent invite = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.invite"), b -> {
            footerError = null;
            UUID invitee = resolveInviteeUuid(inviteInput);
            if (invitee == null) {
                footerError = Component.translatable("gui.arenas_ld.dungeon_controller.error.invitee_not_found").getString();
                return;
            }
            ClientPlayNetworking.send(new InvitePlayerPayload(menu.getBlockPos(), invitee));
            inviteInput = "";
            if (inviteField != null) {
                inviteField.text("");
            }
        });
        invite.active(isOwner);
        row.child(invite);

        return row;
    }

    private FlowLayout inviteHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.child(headerCell("FROM", Sizing.expand()));
        row.child(headerCell("", Sizing.fixed(40)));
        row.child(headerCell("TIER", Sizing.fixed(86)));
        row.child(headerCell("EXPIRES", Sizing.fixed(62)));
        row.child(headerCell("", Sizing.fixed(54)));
        row.child(headerCell("", Sizing.fixed(58)));
        return row;
    }

    private FlowLayout lobbyHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(8);
        row.child(headerCell("OWNER", Sizing.expand()));
        row.child(headerCell("SIZE", Sizing.fixed(44)));
        row.child(headerCell("TIER", Sizing.fixed(70)));
        row.child(headerCell("VISIBILITY", Sizing.fixed(78)));
        row.child(headerCell("STATUS", Sizing.fixed(82)));
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
        row.child(headerCell("NAME", Sizing.expand()));
        row.child(headerCell("CONNECTION", Sizing.fixed(84)));
        row.child(headerCell("READY", Sizing.fixed(84)));
        row.child(headerCell(isOwner ? "ACTION" : "", Sizing.fixed(54)));
        return row;
    }

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
        row.child(text(Component.literal("MEMBERS"), INK_DIM));
        row.child(text(Component.literal("· " + readyCount + " READY · " + count + " / " + max), ACCENT));
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

    private FlowLayout labeledControlRow(LabelComponent labelComponent, io.wispforest.owo.ui.core.Component controls) {
        FlowLayout row = rowPanel(true);
        row.gap(8);
        labelComponent.horizontalSizing(Sizing.fixed(170));
        row.child(labelComponent);
        row.child(controls);
        return row;
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
        tag.padding(Insets.of(2, 4, 2, 4));
        tag.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        tag.child(this.text(text, color));
        return tag;
    }

    private FlowLayout fixedBadge(Component text, int width, int color) {
        FlowLayout tag = Containers.horizontalFlow(Sizing.fixed(width), Sizing.content());
        tag.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        tag.padding(Insets.of(2, 4, 2, 4));
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
            case NORMAL -> INFO;
            case HARD -> WARN;
            case HELL -> DANGER;
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

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(DungeonControllerData data) {
        Optional<Lobby> previousOwnLobby = this.ownLobby;
        menu.applyData(data);
        syncFromMenu(true);

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
        this.ownLobby = menu.getOwnLobby();
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
        boolean canStart = isOwner && allReady && allOnline;

        if (summaryOwnerLabel != null) {
            summaryOwnerLabel.text(Component.literal(lobby.ownerName()));
        }
        if (summaryMembersLabel != null) {
            summaryMembersLabel.text(Component.literal(lobby.members().size() + " / " + menu.getMaxPartySize()));
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
            summaryHardcoreValue.text(Component.literal(lobby.hardcoreEnabled() ? "On" : "Off"));
            summaryHardcoreValue.color(Color.ofArgb(lobby.hardcoreEnabled() ? DANGER : INK_MID));
        }
        if (tierSummaryLabel != null) {
            tierSummaryLabel.text(Component.translatable("gui.arenas_ld.dungeon_controller.tier", lobby.selectedTier().name()));
        }
        if (visibilitySummaryLabel != null) {
            visibilitySummaryLabel.text(Component.translatable("gui.arenas_ld.dungeon_controller.visibility", lobby.visibility().name()));
        }
        if (hardcoreSummaryLabel != null) {
            hardcoreSummaryLabel.text(Component.translatable(
                "gui.arenas_ld.dungeon_controller.hardcore",
                Component.translatable(lobby.hardcoreEnabled()
                    ? "gui.arenas_ld.dungeon_controller.hardcore.on"
                    : "gui.arenas_ld.dungeon_controller.hardcore.off")
            ));
        }
        if (hardcoreToggleButton != null) {
            hardcoreToggleButton.setMessage(Component.translatable(lobby.hardcoreEnabled()
                ? "gui.arenas_ld.dungeon_controller.hardcore.off"
                : "gui.arenas_ld.dungeon_controller.hardcore.on"));
            hardcoreToggleButton.active(isOwner);
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
            boolean ready = lobby.readyMembers().contains(member);
            applyStatusSquare(refs.statusSquare, online);
            refs.statusGlyph.text(Component.literal(online ? "✓" : "!"));
            refs.statusGlyph.color(Color.ofArgb(online ? GOOD : WARN));
            refs.connectionLabel.text(Component.literal(online ? "● ONLINE" : "○ OFFLINE"));
            refs.connectionLabel.color(Color.ofArgb(online ? GOOD : DANGER));
            updateBadge(refs.readyBadge, Component.literal(ready ? "READY" : "NOT READY"), ready ? GOOD : INK_DIM);
            if (refs.kickButton != null) {
                refs.kickButton.active(isOwner && !member.equals(lobby.ownerUuid()));
            }
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
