package net.ledok.arenas_ld.arena.screen;

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
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

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
public class ArenaControllerScreen extends BaseOwoHandledScreen<FlowLayout, ArenaControllerScreenHandler> {
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

    private FlowLayout contentArea;
    private ScrollContainer<FlowLayout> contentScroll;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;
    private FlowLayout instancesStrip;
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

    public ArenaControllerScreen(ArenaControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.pos = handler.getBlockPos();
        this.titleLabelY = 9999;
        this.inventoryLabelY = 9999;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.flat(BG));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth = Math.max(440, Math.min(560, this.width - 24));
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
        root.child(shell);

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
            long now = currentServerTick() / 20L;
            if (now != lastCountdownSecond) {
                lastCountdownSecond = now;
                refreshInviteCountdowns();
                refreshJoinRequestCountdowns();
            }
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int sig = computeSignature();
        if (sig != lastSignature) {
            rebuildUi();
        }
        super.render(ctx, mouseX, mouseY, delta);
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

    private void rebuildUi() {
        lastSignature = computeSignature();
        snapshot();

        captureScrollProgress();
        if (contentArea == null) {
            return;
        }
        contentArea.clearChildren();
        footerActions.clearChildren();
        inviteCountdownLabels.clear();
        joinRequestCountdownLabels.clear();
        inviteFieldRow = null;
        inviteDropdownPanel = null;
        inviteChevron = null;
        inviteDropdownOpen = false;

        if (instancesStrip != null) {
            instancesStrip.clearChildren();
            buildInstancesStrip();
        }

        if (controller() == null) {
            contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.arena.not_loaded")));
            footerLabel.text(Component.empty());
            return;
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
        titleLine.child(text(Component.translatable("container.arenas_ld.arena_controller"), INK));
        titleLine.child(statusBadge());
        info.child(titleLine);

        FlowLayout meta = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        meta.gap(10);
        meta.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        String waveMeta = maxWave < 0
            ? Component.translatable("gui.arenas_ld.arena.endless").getString()
            : String.valueOf(maxWave);
        meta.child(smallMeta(Component.translatable("gui.arenas_ld.arena.max_wave_meta", waveMeta).getString()));
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

    // ── Instances strip ────────────────────────────────────────────────────────

    private void buildInstancesStrip() {
        FlowLayout titleRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        titleRow.gap(8);
        titleRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        titleRow.child(text(Component.translatable("gui.arenas_ld.arena.instances"), INK_DIM));
        instancesStrip.child(titleRow);

        if (instances.isEmpty()) {
            instancesStrip.child(text(Component.translatable("gui.arenas_ld.arena.no_instances"), INK_DIM));
            return;
        }

        FlowLayout pillRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        pillRow.gap(4);
        pillRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        for (ArenaInstanceState inst : instances) {
            String pillText = inst.status().name()
                + (inst.cooldownTicksRemaining() > 0 ? " " + (inst.cooldownTicksRemaining() / 20) + "s" : "");
            pillRow.child(badge(Component.literal(pillText), instanceStatusColor(inst.status())));
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

    // ── Lobbies tab ────────────────────────────────────────────────────────────

    private void buildLobbiesContent() {
        ButtonComponent create = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.create"), b -> {
            footerError = null;
            send(new ArenaLobbyActionPayload(pos, "create"));
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

    private FlowLayout lobbyHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(8);
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.owner"), Sizing.expand()));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.size"), Sizing.fixed(44)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility"), Sizing.fixed(78)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.status"), Sizing.fixed(82)));
        row.child(headerCell("", Sizing.fixed(84)));
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
        ownerInfo.child(text(Component.literal(shortUuid(lobby.lobbyId()).toUpperCase(Locale.ROOT)), INK_DIM));
        row.child(ownerInfo);
        row.child(fixedBadge(Component.literal(lobby.members().size() + "/" + maxPartySize), 44, INK_MID));

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
        boolean isFull = lobby.isFull(maxPartySize);
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
                send(new ArenaLobbyTargetPayload(pos, "join", lobbyId));
            });
            actionButton.active(ownLobby.isEmpty());
        } else {
            UUID me = self();
            boolean alreadyRequested = myJoinRequests.stream()
                .anyMatch(r -> r.lobbyId().equals(lobbyId) && r.requesterUuid().equals(me));
            if (alreadyRequested) {
                actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.pending"), b -> {});
                actionButton.active(false);
            } else {
                actionButton = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.request"), b -> {
                    footerError = null;
                    send(new ArenaLobbyTargetPayload(pos, "request", lobbyId));
                });
                actionButton.active(ownLobby.isEmpty());
            }
        }
        actionButton.horizontalSizing(Sizing.fill(100));
        actionCell.child(actionButton);
        row.child(actionCell);
        return row;
    }

    // ── My Lobby tab ────────────────────────────────────────────────────────────

    private void buildMyLobbyContent() {
        if (ownLobby.isEmpty()) {
            contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_own_lobby")));
            return;
        }

        Lobby lobby = ownLobby.get();
        UUID self = self();
        boolean isOwner = lobby.ownerUuid().equals(self);

        if (queuePos >= 1) {
            FlowLayout queueBanner = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(28));
            queueBanner.surface(Surface.flat((WARN & 0x00FFFFFF) | 0x1A000000).and(Surface.outline((WARN & 0x00FFFFFF) | 0x55000000)));
            queueBanner.padding(Insets.of(4, 4, 8, 8));
            queueBanner.gap(8);
            queueBanner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            FlowLayout queueAccent = Containers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
            queueAccent.surface(Surface.flat(WARN));
            queueBanner.child(queueAccent);
            queueBanner.child(badge(Component.translatable("gui.arenas_ld.raid_controller.ui.queue.in_line", queuePos), WARN));
            queueBanner.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
            if (isOwner) {
                queueBanner.child(smallButton(Component.translatable("gui.arenas_ld.raid_controller.button.leave_queue"), b -> {
                    footerError = null;
                    send(new ArenaLobbyActionPayload(pos, "leave"));
                }));
            }
            contentArea.child(queueBanner);
            contentArea.child(spacer(4));
        }

        FlowLayout summary = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        summary.surface(Surface.flat(ROW_BG).and(Surface.outline(HAIRLINE)));
        summary.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout summaryAccent = Containers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
        summaryAccent.surface(Surface.flat(ACCENT));
        summary.child(summaryAccent);
        FlowLayout summaryPad = Containers.verticalFlow(Sizing.fixed(9), Sizing.fill(100));
        summaryPad.surface(Surface.BLANK);
        summary.child(summaryPad);
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.owner"),
            text(Component.literal(lobby.ownerName()), ACCENT), Sizing.fill(34)));
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.members"),
            text(Component.literal(lobby.members().size() + " / " + maxPartySize), INK), Sizing.fill(22)));
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility"),
            text(Component.literal(titleCase(lobby.visibility().name())), visibilityColor(lobby.visibility())), Sizing.fill(22)));
        summary.child(infoColumn(tr("gui.arenas_ld.dungeon_controller.ui.col.hardcore"),
            text(Component.translatable(lobby.hardcoreEnabled() ? "gui.arenas_ld.dungeon_controller.ui.hardcore.on" : "gui.arenas_ld.dungeon_controller.ui.hardcore.off"), lobby.hardcoreEnabled() ? DANGER : INK_MID), Sizing.fill(22)));
        contentArea.child(summary);

        contentArea.child(membersSectionHeader(lobby.readyMembers().size(), lobby.members().size(), maxPartySize));
        FlowLayout memberList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        memberList.child(memberHeaderRow(isOwner));
        memberList.child(rowDivider(HAIRLINE_HI));
        List<UUID> orderedMembers = lobby.members().stream()
            .sorted(Comparator.comparing((UUID m) -> m.equals(lobby.ownerUuid()) ? 0 : 1)
                .thenComparing(m -> lobby.memberNames().getOrDefault(m, shortUuid(m)).toLowerCase(Locale.ROOT)))
            .limit(maxPartySize)
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

        if (isOwner) {
            contentArea.child(spacer(6));
            contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.ui.section.owner_controls"), null));

            contentArea.child(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.col.visibility")));
            contentArea.child(ownerVisibilityControls(true));

            contentArea.child(spacer(4));
            contentArea.child(hardcoreControl(true));

            contentArea.child(spacer(4));
            contentArea.child(controlCaption(tr("gui.arenas_ld.dungeon_controller.ui.section.invite_player")));
            contentArea.child(ownerInviteControls(true));
        }

        boolean ready = lobby.readyMembers().contains(self);
        boolean canStart = isOwner && lobby.allReady() && allOnline(lobby) && queuePos < 1;

        footerActions.child(smallButton(Component.translatable(ready
            ? "gui.arenas_ld.dungeon_controller.button.unready"
            : "gui.arenas_ld.dungeon_controller.button.ready"), b -> {
            footerError = null;
            send(new ArenaLobbyActionPayload(pos, "ready"));
        }));
        footerActions.child(smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.leave"), b -> {
            footerError = null;
            send(new ArenaLobbyActionPayload(pos, "leave"));
        }));
        ButtonComponent start = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.start"), b -> {
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
        start.active(canStart);
        footerActions.child(start);
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
        int squareColor = online ? GOOD : WARN;
        statusSquare.surface(Surface.flat((squareColor & 0x00FFFFFF) | 0x22000000).and(Surface.outline((squareColor & 0x00FFFFFF) | 0x66000000)));
        statusSquare.child(text(Component.literal(online ? "✓" : "!"), online ? GOOD : WARN));
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

        row.child(fixedBadge(Component.translatable(ready ? "gui.arenas_ld.dungeon_controller.ui.member.ready" : "gui.arenas_ld.dungeon_controller.ui.member.not_ready"), 84, ready ? GOOD : INK_DIM));

        if (isOwner && !isLobbyOwner) {
            ButtonComponent kick = dangerButton(Component.translatable("gui.arenas_ld.dungeon_controller.ui.action.kick"), b -> {
                footerError = null;
                send(new ArenaLobbyTargetPayload(pos, "kick", member));
            });
            kick.horizontalSizing(Sizing.fixed(54));
            row.child(kick);
        } else {
            FlowLayout kickSpace = Containers.horizontalFlow(Sizing.fixed(54), Sizing.content());
            kickSpace.surface(Surface.BLANK);
            row.child(kickSpace);
        }
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

    private FlowLayout ownerVisibilityControls(boolean isOwner) {
        ButtonComponent pub = segmentButton("PUBLIC", visibilityColor(LobbyVisibility.PUBLIC), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.PUBLIC,
            b -> send(new ArenaSetVisibilityPayload(pos, LobbyVisibility.PUBLIC.name())));
        ButtonComponent fr = segmentButton("FRIENDS", visibilityColor(LobbyVisibility.FRIENDS), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.FRIENDS,
            b -> send(new ArenaSetVisibilityPayload(pos, LobbyVisibility.FRIENDS.name())));
        ButtonComponent pr = segmentButton("PRIVATE", visibilityColor(LobbyVisibility.PRIVATE), isOwner,
            () -> ownLobby.map(Lobby::visibility).orElse(null) == LobbyVisibility.PRIVATE,
            b -> send(new ArenaSetVisibilityPayload(pos, LobbyVisibility.PRIVATE.name())));
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

        row.child(toggleSwitch(isOwner,
            () -> ownLobby.map(Lobby::hardcoreEnabled).orElse(false),
            b -> {
                footerError = null;
                boolean current = ownLobby.map(Lobby::hardcoreEnabled).orElse(false);
                send(new ArenaSetHardcorePayload(pos, !current));
            }));
        return row;
    }

    // ── Invite dropdown ──────────────────────────────────────────────────────────

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
            send(new ArenaLobbyTargetPayload(pos, "invite", candidate.uuid()));
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
        UUID self = self();
        java.util.Set<UUID> myMembers = ownLobby.map(lobby -> new java.util.HashSet<>(lobby.members())).orElseGet(java.util.HashSet::new);
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
        contentArea.child(leaderboardBanner());

        List<LeaderboardEntry> entries = new ArrayList<>(leaderboard);
        entries.sort(Comparator.comparingInt(LeaderboardEntry::timeSeconds).reversed());

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
                list.child(leaderboardRow(i + 1, entries.get(i)));
            }
        }
        contentArea.child(list);
    }

    private FlowLayout leaderboardBanner() {
        FlowLayout banner = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(34));
        banner.surface(Surface.flat((ACCENT & 0x00FFFFFF) | 0x1F000000).and(Surface.outline((ACCENT & 0x00FFFFFF) | 0x55000000)));
        banner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        banner.child(accent);
        FlowLayout inner = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        inner.padding(Insets.of(0, 0, 10, 10));
        inner.gap(8);
        inner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        inner.child(badge(Component.translatable("gui.arenas_ld.arena.leaderboard.banner_title"), ACCENT));
        inner.child(text(Component.translatable("gui.arenas_ld.arena.leaderboard.banner_desc"), INK));
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
        LabelComponent waveHeader = headerCell(tr("gui.arenas_ld.arena.col.wave"), Sizing.fixed(64));
        waveHeader.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        row.child(waveHeader);
        return row;
    }

    private FlowLayout leaderboardRow(int rank, LeaderboardEntry entry) {
        boolean top = rank == 1;
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(top ? ((ACCENT & 0x00FFFFFF) | 0x1A000000) : ROW_BG));
        row.padding(Insets.of(7, 7, 8, 8));
        row.gap(8);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent rankLabel = text(Component.literal(top ? "★1" : ("#" + rank)), top ? ACCENT : INK_MID);
        rankLabel.horizontalSizing(Sizing.fixed(50));
        row.child(rankLabel);

        LabelComponent nameLabel = text(Component.literal(entry.playerName()), INK);
        nameLabel.horizontalSizing(Sizing.expand());
        row.child(nameLabel);

        LabelComponent waveLabel = text(Component.translatable("gui.arenas_ld.arena.wave_n", entry.timeSeconds()), top ? ACCENT : INK_MID);
        waveLabel.horizontalSizing(Sizing.fixed(64));
        waveLabel.horizontalTextAlignment(HorizontalAlignment.RIGHT);
        row.child(waveLabel);
        return row;
    }

    // ── Invites tab ──────────────────────────────────────────────────────────────

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

        UUID self = self();
        boolean isOwner = ownLobby.isPresent() && ownLobby.get().ownerUuid().equals(self);
        if (isOwner) {
            contentArea.child(spacer(8));
            contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller.join_requests"), myJoinRequests.size()));
            if (myJoinRequests.isEmpty()) {
                contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller.no_join_requests")));
            } else {
                contentArea.child(joinRequestHeaderRow());
                for (PendingJoinRequest req : myJoinRequests) {
                    contentArea.child(joinRequestRow(req));
                }
            }
        }
    }

    private FlowLayout inviteHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.from"), Sizing.expand()));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.expires"), Sizing.fixed(62)));
        row.child(headerCell("", Sizing.fixed(54)));
        row.child(headerCell("", Sizing.fixed(58)));
        return row;
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
            .filter(n -> n != null && !n.isEmpty())
            .orElse(ownerName);

        long remainingSeconds = Math.max(0L, invite.expiresAtTick() - currentServerTick()) / 20L;
        FlowLayout inviteInfo = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        FlowLayout fromLine = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        fromLine.gap(3);
        fromLine.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.from"), INK));
        fromLine.child(text(Component.literal(inviterName), ACCENT));
        inviteInfo.child(fromLine);
        inviteInfo.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.invite_row.owner", ownerName.toUpperCase(Locale.ROOT)), INK_DIM));
        row.child(inviteInfo);

        LabelComponent countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, HorizontalAlignment.CENTER);
        inviteCountdownLabels.put(invite.lobbyId(), countdown);
        row.child(countdown);

        ButtonComponent accept = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), b -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "accept_invite", invite.lobbyId()));
        });
        accept.horizontalSizing(Sizing.fixed(54));
        row.child(accept);

        ButtonComponent decline = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), b -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "decline_invite", invite.lobbyId()));
        });
        decline.horizontalSizing(Sizing.fixed(58));
        row.child(decline);
        return row;
    }

    private FlowLayout joinRequestHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller.ui.col.requester"), Sizing.expand()));
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

        long remainingSeconds = Math.max(0L, req.expiresAtTick() - currentServerTick()) / 20L;
        LabelComponent countdown = fixedText(Component.literal(formatRemaining(remainingSeconds)), 62, remainingSeconds <= 15 ? WARN : INK_MID, HorizontalAlignment.CENTER);
        joinRequestCountdownLabels.put(req.requesterUuid(), countdown);
        row.child(countdown);

        UUID requesterUuid = req.requesterUuid();
        ButtonComponent accept = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), b -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "accept_request", requesterUuid));
        });
        accept.horizontalSizing(Sizing.fixed(54));
        row.child(accept);

        ButtonComponent decline = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), b -> {
            footerError = null;
            send(new ArenaLobbyTargetPayload(pos, "decline_request", requesterUuid));
        });
        decline.horizontalSizing(Sizing.fixed(58));
        row.child(decline);
        return row;
    }

    // ── Countdowns ──────────────────────────────────────────────────────────────

    private void refreshInviteCountdowns() {
        for (PendingInvite invite : myInvites) {
            LabelComponent label = inviteCountdownLabels.get(invite.lobbyId());
            if (label == null) continue;
            long remainingSeconds = Math.max(0L, invite.expiresAtTick() - currentServerTick()) / 20L;
            label.text(Component.literal(formatRemaining(remainingSeconds)));
            label.color(Color.ofArgb(remainingSeconds <= 15 ? WARN : INK_MID));
        }
    }

    private void refreshJoinRequestCountdowns() {
        for (PendingJoinRequest req : myJoinRequests) {
            LabelComponent label = joinRequestCountdownLabels.get(req.requesterUuid());
            if (label == null) continue;
            long remainingSeconds = Math.max(0L, req.expiresAtTick() - currentServerTick()) / 20L;
            label.text(Component.literal(formatRemaining(remainingSeconds)));
            label.color(Color.ofArgb(remainingSeconds <= 15 ? WARN : INK_MID));
        }
    }

    // ── Widgets & helpers ─────────────────────────────────────────────────────────

    private void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    private ButtonComponent smallButton(Component text, Consumer<ButtonComponent> action) {
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

    private ButtonComponent dangerButton(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text.copy().withStyle(ChatFormatting.RED), action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ((DANGER & 0x00FFFFFF) | 0x44000000) : ((DANGER & 0x00FFFFFF) | 0x1F000000);
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), DANGER);
        });
        return button;
    }

    private ButtonComponent segmentButton(String label, int accentColor, boolean active, BooleanSupplier selected, Consumer<ButtonComponent> action) {
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

    private ButtonComponent toggleSwitch(boolean active, BooleanSupplier on, Consumer<ButtonComponent> action) {
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
        row.child(text(title, INK_DIM));
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
        row.child(text(Component.translatable("gui.arenas_ld.dungeon_controller.ui.members_summary", readyCount, count, max), ACCENT));
        return row;
    }

    private FlowLayout rowDivider(int color) {
        FlowLayout divider = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface(Surface.flat(color));
        return divider;
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private LabelComponent controlCaption(String caption) {
        LabelComponent label = Components.label(Component.literal(caption));
        label.color(Color.ofArgb(INK_DIM));
        return label;
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

    private void captureScrollProgress() {
        if (contentScroll == null) {
            return;
        }
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
