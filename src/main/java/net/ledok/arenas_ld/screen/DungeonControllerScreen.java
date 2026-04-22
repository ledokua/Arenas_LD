package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.DifficultyTier;
import net.ledok.arenas_ld.util.DungeonLeaderboardEntry;
import net.ledok.arenas_ld.util.LobbyStatus;
import net.ledok.arenas_ld.util.LobbyVisibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class DungeonControllerScreen extends AbstractContainerScreen<DungeonControllerScreenHandler> {
    private static final int BUTTON_SHIFT_X = 2;

    // ── Screen dimensions ────────────────────────────────────────────────────
    private static final int W  = 270;
    private static final int H  = 310;
    private static final int P  = 8;   // horizontal padding
    private static final int IW = W - P * 2; // inner width = 248

    // ── Fixed layout zones (Y offsets from screen top) ───────────────────────
    // [0  – 14 ] Title bar
    // [14 – 52 ] Instances panel
    // [56 – 224] State content zone  (168 px)
    // [228– 284] Leaderboard zone    ( 56 px: 14 tabs + 42 entries)
    // [286– 310] Admin bar (ops only)
    private static final int Y_INST     = 16;
    private static final int Y_CONTENT  = 62;
    private static final int Y_ACTIONS  = Y_CONTENT + 148; // 204  action buttons
    private static final int Y_LB       = 228;
    private static final int Y_LB_ENTRY = Y_LB + 16;      // 244
    private static final int Y_ADMIN    = 286;

    // Within OWNER content zone:
    private static final int YO_DIFF    = Y_CONTENT + 2;   // 58  "Difficulty" label
    private static final int YO_TIERS   = Y_CONTENT + 12;  // 68  tier buttons
    private static final int YO_VIS     = Y_CONTENT + 32;  // 88  visibility buttons
    private static final int YO_HC      = Y_CONTENT + 52;  // 108 hardcore checkbox
    private static final int YO_MEMBERS = Y_CONTENT + 70;  // 126 "Members" label
    private static final int YO_LIST    = Y_CONTENT + 80;  // 136 member list
    private static final int YO_INVITE  = Y_CONTENT + 130; // 186 invite box

    // Within BROWSING content zone:
    private static final int YB_BANNER  = Y_CONTENT + 2;   // 58  invite banner
    private static final int YB_LBABEL  = Y_CONTENT + 28;  // 84  "Open lobbies" label
    private static final int YB_LIST    = Y_CONTENT + 38;  // 94  lobby list
    private static final int YB_LIST_H  = 104;             //     list height (≈5 visible rows)

    // Button geometry helpers
    private static final int BTN_H    = 20;
    private static final int BTN_HALF = (IW - 8) / 2;  // 118 for two equal buttons

    // Tier button width: 4 buttons with 3-px gaps
    private static final int TIER_W = (IW - 11) / 4;   // 57

    // ── Colors ───────────────────────────────────────────────────────────────
    // Backgrounds
    private static final int C_BG       = 0xFF111622;
    private static final int C_PANEL    = 0xFF0e1220;
    private static final int C_TITLEBAR = 0xFF141826;
    private static final int C_ROW_SEL  = 0xFF1a1e38;
    private static final int C_ROW_NORM = 0xFF0e1220;

    // Borders
    private static final int C_BDR      = 0xFF1f2845;
    private static final int C_BDR_HL   = 0xFF3a4a8a;

    // Text
    private static final int C_TEXT     = 0xFFFFFFFF;
    private static final int C_MUTED    = 0xFF6a7aaa;
    private static final int C_LABEL    = 0xFF4a5580;
    private static final int C_GREEN    = 0xFF44ee44;
    private static final int C_AMBER    = 0xFFff9900;
    private static final int C_RED      = 0xFFff5555;
    private static final int C_YELLOW   = 0xFFffcc00;
    private static final int C_CYAN     = 0xFF44ccff;
    private static final int C_BLUE     = 0xFF4a88ff;
    private static final int C_PURPLE   = 0xFFcc88ff;
    private static final int C_NOTIFY   = 0xFF44aa44;

    // Instance pill colors indexed by ordinal: [FREE=0, RUNNING=1, COOLDOWN=2]
    private static final int[] PILL_BG  = { 0xFF0d2a0d, 0xFF2a1800, 0xFF2a0a0a };
    private static final int[] PILL_TXT = { 0xFF44ee44, 0xFFff9900, 0xFFff5555 };
    private static final int[] PILL_BDR = { 0xFF1a5a1a, 0xFF5a3a00, 0xFF5a1a1a };

    // Tier colors indexed by DifficultyTier.ordinal(): [EASY=0, NORMAL=1, HARD=2, NIGHTMARE=3]
    private static final int[] TIER_BG  = { 0xFF0a2a0a, 0xFF0a0a2a, 0xFF2a1800, 0xFF2a0a18 };
    private static final int[] TIER_TXT = { 0xFF44ee44, 0xFF4a88ff, 0xFFff9900, 0xFFff55aa };
    private static final int[] TIER_BDR = { 0xFF1a5a1a, 0xFF1a3a7a, 0xFF5a3800, 0xFF5a1a3a };

    // ── Server state ─────────────────────────────────────────────────────────
    private int remainingSecs, cooldownSecs;
    private boolean hardcoreEnabled;
    private DifficultyTier selectedTier = DifficultyTier.NORMAL;
    private boolean inLobby, isOwner, controllerLocked;
    private LobbyStatus lobbyStatus = LobbyStatus.OPEN;
    private LobbyVisibility lobbyVis = LobbyVisibility.OPEN;
    private int queuePos, queueEstSecs;
    private boolean canAdmin;
    private int serverRespawnTicks = 6000;
    private int draftRespawnTicks  = 6000;
    private List<String>                                               memberNames    = new ArrayList<>();
    private List<DungeonLeaderboardEntry>                              leaderboard    = new ArrayList<>();
    private List<ModPackets.DungeonControllerInfoPayload.InstanceView> instanceViews  = new ArrayList<>();
    private List<ModPackets.DungeonControllerInfoPayload.LobbyView>    allLobbies     = new ArrayList<>();
    private List<ModPackets.DungeonControllerInfoPayload.LobbyView>    visLobbies     = new ArrayList<>();
    private List<ModPackets.DungeonControllerInfoPayload.LobbyView>    invitedLobbies = new ArrayList<>();

    // ── Client-only state ─────────────────────────────────────────────────────
    private DifficultyTier lbTab        = DifficultyTier.NORMAL;
    private int    selLobby             = 0;
    private int    selMember            = 0;
    private double lobbyScroll          = 0;
    private double memberScroll         = 0;
    private double lbScroll             = 0;
    private boolean suppressHcSync      = false;
    private int refreshTick             = 0;

    private static final int ADMIN_STEP = 30 * 20;
    private static final int ADMIN_MAX  = 60 * 60 * 20;
    private static final int ROW_H      = 19; // height of each lobby / member row
    private static final int INST_PANEL_H = 42;
    private static final int INST_PILL_GAP_X = 3;
    private static final int INST_PILL_GAP_Y = 3;

    // ── Widget references ─────────────────────────────────────────────────────
    // BROWSING
    private Button createLobbyBtn, joinLobbyBtn;
    // MEMBER
    private Button leaveLobbyBtn;
    // OWNER
    private final Button[] tierBtns = new Button[4];
    private Button visOpenBtn, visInviteBtn;
    private Checkbox hcBox;
    private Button kickBtn;
    private EditBox inviteBox;
    private Button sendInviteBtn;
    private Button startBtn, disbandBtn;
    // QUEUED
    private Button leaveQueueBtn;
    // Leaderboard tabs
    private final Button[] lbTabBtns = new Button[4];
    // Admin
    private Button adminMinusBtn, adminApplyBtn, adminPlusBtn;

    // ── Constructor ───────────────────────────────────────────────────────────
    public DungeonControllerScreen(DungeonControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth  = W;
        this.imageHeight = H;
    }

    // ── Init ──────────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;

        // ── BROWSING ──
        createLobbyBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.create_lobby"),
                b -> { ClientPlayNetworking.send(new ModPackets.CreateDungeonLobbyPayload(menu.getPos())); updateInfo(); }
        ).bounds(x + P + BUTTON_SHIFT_X, y + Y_ACTIONS - 5, BTN_HALF, BTN_H).build());

        joinLobbyBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.join_selected"),
                b -> {
                    if (!visLobbies.isEmpty() && selLobby < visLobbies.size()) {
                        ClientPlayNetworking.send(new ModPackets.JoinDungeonLobbyPayload(menu.getPos(), visLobbies.get(selLobby).id()));
                        updateInfo();
                    }
                }
        ).bounds(x + P + BTN_HALF + 4 + BUTTON_SHIFT_X, y + Y_ACTIONS - 5, BTN_HALF, BTN_H).build());

        // ── MEMBER ──
        leaveLobbyBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.leave_party"),
                b -> { ClientPlayNetworking.send(new ModPackets.DungeonControllerActionPayload(menu.getPos(), 2)); updateInfo(); }
        ).bounds(x + P + BUTTON_SHIFT_X, y + Y_ACTIONS, IW, BTN_H).build());

        // ── OWNER: tier buttons ──
        DifficultyTier[] tiers = DifficultyTier.values();
        for (int i = 0; i < 4; i++) {
            final DifficultyTier t = tiers[i];
            tierBtns[i] = addRenderableWidget(Button.builder(
                    Component.translatable(t.translationKey()),
                    b -> { selectedTier = t; syncSettings(); updateInfo(); }
            ).bounds(x + P + i * (TIER_W + 3) + BUTTON_SHIFT_X, y + YO_TIERS, TIER_W, 16).build());
        }

        // ── OWNER: visibility buttons ──
        int visW = ((IW - 3) / 2) - 2;
        visOpenBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.visibility_open"),
                b -> { ClientPlayNetworking.send(new ModPackets.UpdateDungeonLobbyVisibilityPayload(menu.getPos(), "OPEN")); updateInfo(); }
        ).bounds(x + P + BUTTON_SHIFT_X, y + YO_VIS, visW, 16).build());
        visInviteBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.visibility_invite_only"),
                b -> { ClientPlayNetworking.send(new ModPackets.UpdateDungeonLobbyVisibilityPayload(menu.getPos(), "INVITE_ONLY")); updateInfo(); }
        ).bounds(x + P + ((IW - 3) / 2) + 3 + BUTTON_SHIFT_X, y + YO_VIS, visW, 16).build());

        // ── OWNER: hardcore checkbox ──
        hcBox = addRenderableWidget(Checkbox.builder(
                Component.translatable("gui.arenas_ld.hardcore"),
                font
        ).pos(x + P + 2, y + YO_HC).selected(false).onValueChange((cb, sel) -> { if (!suppressHcSync) syncSettings(); }).build());
        hcBox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("gui.arenas_ld.hardcore_desc")));

        // ── OWNER: kick button ──
        kickBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.kick_member"),
                b -> {
                    if (selMember > 0 && selMember < memberNames.size()) {
                        ClientPlayNetworking.send(new ModPackets.KickDungeonLobbyPlayerPayload(menu.getPos(), memberNames.get(selMember)));
                        updateInfo();
                    }
                }
        ).bounds(x + W - P - 46 + BUTTON_SHIFT_X, y + YO_LIST + (selMember * ROW_H) + 2, 44, 14).build());

        // ── OWNER: invite ──
        inviteBox = new EditBox(font, x + P, y + YO_INVITE, 186, 16, Component.translatable("gui.arenas_ld.invite_player"));
        inviteBox.setMaxLength(32);
        addRenderableWidget(inviteBox);

        sendInviteBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.send_invite"),
                b -> {
                    String name = inviteBox.getValue().trim();
                    if (!name.isEmpty()) {
                        ClientPlayNetworking.send(new ModPackets.InviteDungeonLobbyPlayerPayload(menu.getPos(), name));
                        inviteBox.setValue("");
                        updateInfo();
                    }
                }
        ).bounds(x + P + 190 + BUTTON_SHIFT_X, y + YO_INVITE, IW - 190, 16).build());

        // ── OWNER: start / disband ──
        startBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.start_dungeon"),
                b -> { ClientPlayNetworking.send(new ModPackets.DungeonControllerActionPayload(menu.getPos(), 0)); updateInfo(); }
        ).bounds(x + P + BUTTON_SHIFT_X, y + Y_ACTIONS, 148, BTN_H - 5).build());

        disbandBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.disband_lobby"),
                b -> { ClientPlayNetworking.send(new ModPackets.DisbandDungeonLobbyPayload(menu.getPos())); updateInfo(); }
        ).bounds(x + P + 152 + BUTTON_SHIFT_X, y + Y_ACTIONS, IW - 156, BTN_H - 5).build());

        // ── QUEUED ──
        leaveQueueBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.leave_queue"),
                b -> { ClientPlayNetworking.send(new ModPackets.DungeonControllerActionPayload(menu.getPos(), 2)); updateInfo(); }
        ).bounds(x + P + BUTTON_SHIFT_X, y + Y_ACTIONS, IW, BTN_H).build());

        // ── Leaderboard tier tabs ──
        for (int i = 0; i < 4; i++) {
            final DifficultyTier t = DifficultyTier.values()[i];
            lbTabBtns[i] = addRenderableWidget(Button.builder(
                    Component.translatable(t.translationKey()),
                    b -> { lbTab = t; lbScroll = 0; updateInfo(); }
            ).bounds(x + P + i * (TIER_W + 3) + BUTTON_SHIFT_X, y + Y_LB, TIER_W, 14).build());
        }

        // ── Admin bar ──
        adminMinusBtn = addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            draftRespawnTicks = Mth.clamp(draftRespawnTicks - ADMIN_STEP, 0, ADMIN_MAX);
        }).bounds(x + P + BUTTON_SHIFT_X, y + Y_ADMIN + 1, 16, 12).build());

        adminApplyBtn = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            ClientPlayNetworking.send(new ModPackets.UpdateDungeonControllerAdminSettingsPayload(menu.getPos(), draftRespawnTicks));
            updateInfo();
        }).bounds(x + P + 20 + BUTTON_SHIFT_X, y + Y_ADMIN + 1, 40, 12).build());

        adminPlusBtn = addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            draftRespawnTicks = Mth.clamp(draftRespawnTicks + ADMIN_STEP, 0, ADMIN_MAX);
        }).bounds(x + P + 64 + BUTTON_SHIFT_X, y + Y_ADMIN + 1, 16, 12).build());

        updateButtons();
        updateInfo();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────
    @Override
    protected void containerTick() {
        super.containerTick();
        if (++refreshTick >= 20) {
            refreshTick = 0;
            updateInfo();
        }
    }

    private void updateInfo() {
        if (minecraft == null || minecraft.level == null) return;
        ClientPlayNetworking.send(new ModPackets.RequestDungeonControllerInfoPayload(menu.getPos(), lbTab.name()));
    }

    // ── Apply server payload ──────────────────────────────────────────────────
    public void applyServerInfo(ModPackets.DungeonControllerInfoPayload p) {
        if (!p.pos().equals(menu.getPos())) return;

        remainingSecs     = p.remainingDungeonTimeSeconds();
        cooldownSecs      = p.dungeonCooldownSeconds();
        hardcoreEnabled   = p.hardcoreEnabled();
        selectedTier      = DifficultyTier.fromNameOrDefault(p.selectedTier(), DifficultyTier.NORMAL);
        inLobby           = p.inLobby();
        isOwner           = p.isLobbyOwner();
        controllerLocked  = p.controllerLocked();
        lobbyStatus       = parseLobbyStatus(p.lobbyStatus());
        lobbyVis          = parseLobbyVis(p.currentLobbyVisibility());
        queuePos          = p.queuePosition();
        queueEstSecs      = queuePos > 0 && cooldownSecs > 0 ? queuePos * cooldownSecs : 0;
        canAdmin          = p.canManageAdmin();
        int prevRespawn   = serverRespawnTicks;
        serverRespawnTicks = Math.max(0, p.controllerRespawnTimeTicks());
        if (draftRespawnTicks == prevRespawn) draftRespawnTicks = serverRespawnTicks;

        memberNames  = new ArrayList<>(p.players());
        leaderboard  = new ArrayList<>(p.leaderboard());
        instanceViews= new ArrayList<>(p.instances());

        // Partition lobbies
        allLobbies     = new ArrayList<>(p.lobbies());
        visLobbies     = new ArrayList<>();
        invitedLobbies = new ArrayList<>();
        for (var lv : allLobbies) {
            if ("IN_DUNGEON".equalsIgnoreCase(lv.status())) continue;
            visLobbies.add(lv);
            if (lv.invited()) invitedLobbies.add(lv);
        }
        selLobby = Mth.clamp(selLobby, 0, Math.max(0, visLobbies.size() - 1));
        selMember = Mth.clamp(selMember, 0, Math.max(0, memberNames.size() - 1));

        // Sync hardcore checkbox
        if (hcBox != null && hcBox.selected() != hardcoreEnabled) {
            suppressHcSync = true;
            hcBox.onPress();
            suppressHcSync = false;
        }

        updateButtons();
    }

    // ── Button visibility ─────────────────────────────────────────────────────
    private void updateButtons() {
        if (createLobbyBtn == null) return;
        UiState s = uiState();

        // BROWSING
        show(createLobbyBtn, s == UiState.BROWSING);
        show(joinLobbyBtn,   s == UiState.BROWSING);
        boolean canJoin = s == UiState.BROWSING && canJoinSelected();
        joinLobbyBtn.active = canJoin;
        if (visLobbies.isEmpty()) {
            joinLobbyBtn.setMessage(Component.translatable("gui.arenas_ld.join_selected"));
        } else if (canJoin) {
            joinLobbyBtn.setMessage(Component.translatable("gui.arenas_ld.join_selected"));
        } else {
            joinLobbyBtn.setMessage(Component.translatable("gui.arenas_ld.invite_only"));
        }

        // MEMBER
        show(leaveLobbyBtn, s == UiState.MEMBER);

        // OWNER
        boolean ownerActive = s == UiState.OWNER;
        for (int i = 0; i < 4; i++) {
            show(tierBtns[i], ownerActive);
            tierBtns[i].active = ownerActive && DifficultyTier.values()[i] != selectedTier;
        }
        show(visOpenBtn,   ownerActive);
        show(visInviteBtn, ownerActive);
        visOpenBtn.active   = ownerActive && lobbyVis != LobbyVisibility.OPEN;
        visInviteBtn.active = ownerActive && lobbyVis != LobbyVisibility.INVITE_ONLY;
        show(hcBox, ownerActive);
        hcBox.active = ownerActive;
        show(kickBtn, ownerActive && selMember > 0);
        kickBtn.active = ownerActive && selMember > 0 && selMember < memberNames.size();
        // Reposition kick button next to selected member
        if (kickBtn.visible) kickBtn.setY(topPos + YO_LIST + selMember * ROW_H + 2);
        boolean inviteOnly = ownerActive && lobbyVis == LobbyVisibility.INVITE_ONLY;
        show(inviteBox,    inviteOnly);
        inviteBox.setEditable(inviteOnly);
        show(sendInviteBtn, inviteOnly);
        sendInviteBtn.active = inviteOnly && !inviteBox.getValue().trim().isEmpty();
        show(startBtn,   ownerActive);
        show(disbandBtn, ownerActive);
        if (ownerActive) {
            boolean freeExists = instanceViews.stream().anyMatch(iv -> "FREE".equalsIgnoreCase(iv.status()));
            startBtn.setMessage(Component.translatable(
                    freeExists ? "gui.arenas_ld.start_dungeon" : "gui.arenas_ld.join_queue"));
        }

        // QUEUED
        show(leaveQueueBtn, s == UiState.QUEUED);

        // Leaderboard tabs — always visible
        for (int i = 0; i < 4; i++) {
            lbTabBtns[i].visible = true;
            lbTabBtns[i].active  = DifficultyTier.values()[i] != lbTab;
        }

        // Admin
        show(adminMinusBtn, canAdmin);
        show(adminApplyBtn, canAdmin);
        show(adminPlusBtn,  canAdmin);
        if (adminApplyBtn.visible) adminApplyBtn.active = draftRespawnTicks != serverRespawnTicks;
        if (adminMinusBtn.visible) adminMinusBtn.active = draftRespawnTicks > 0;
        if (adminPlusBtn.visible)  adminPlusBtn.active  = draftRespawnTicks < ADMIN_MAX;
    }

    private static void show(net.minecraft.client.gui.components.AbstractWidget w, boolean v) {
        w.visible = v;
        w.active  = v;
    }

    // ── Background ────────────────────────────────────────────────────────────
    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        int x = leftPos, y = topPos;

        // Full screen background
        g.fill(x, y, x + W, y + H, C_BG);

        // Outer border
        drawBorder(g, x, y, W, H, C_BDR);

        // Title bar
        g.fill(x + 1, y + 1, x + W - 1, y + 14, C_TITLEBAR);
        drawBorder(g, x + 1, y + 1, W - 2, 13, C_BDR);

        // Instance panel
        drawPanel(g, x + P, y + Y_INST, IW, INST_PANEL_H);

        // State content panel
        drawPanel(g, x + P, y + Y_CONTENT - 2, IW, 170);

        // Leaderboard panel
        drawPanel(g, x + P, y + Y_LB - 2, IW, 58);

        // Admin bar (subtle divider only)
        if (canAdmin) {
            g.fill(x + P, y + Y_ADMIN - 2, x + W - P, y + Y_ADMIN - 1, C_BDR);
        }
    }

    // ── Main render ───────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        super.render(g, mx, my, dt);
        renderTooltip(g, mx, my);

        int x = leftPos, y = topPos;
        UiState s = uiState();

        renderInstances(g, x, y);

        switch (s) {
            case BROWSING -> renderBrowsing(g, x, y, mx, my);
            case MEMBER   -> renderMember(g, x, y, mx, my);
            case OWNER    -> renderOwner(g, x, y, mx, my);
            case QUEUED   -> renderQueued(g, x, y);
        }

        renderLeaderboard(g, x, y);
        if (canAdmin) renderAdminBar(g, x, y);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        // Title centered in title bar
        int tw = font.width(this.title);
        g.drawString(font, this.title, (imageWidth - tw) / 2, 4, C_TEXT, false);
    }

    // ── Section renderers ─────────────────────────────────────────────────────
    private void renderInstances(GuiGraphics g, int x, int y) {
        smallLabel(g, Component.translatable("gui.arenas_ld.instances"), x + P + 3, y + Y_INST + 4);
        int panelX = x + P;
        int panelY = y + Y_INST;
        int panelInnerLeft = panelX + 3;
        int panelInnerRight = panelX + IW - 3;
        int px = panelInnerLeft;
        int py = panelY + 14;
        if (instanceViews.isEmpty()) {
            g.drawString(font, Component.translatable("gui.arenas_ld.no_instances_registered"), px, py, C_MUTED, false);
            return;
        }
        int shown = 0;
        int maxRows = 2;
        int row = 0;
        int rowBottom = py + 12;
        for (int i = 0; i < instanceViews.size(); i++) {
            var iv = instanceViews.get(i);
            int idx = pillIndex(iv.status());
            String label = pillLabel(iv, i + 1);
            int pillW = font.width(label) + 8;
            if (px + pillW > panelInnerRight) {
                row++;
                if (row >= maxRows) {
                    int hidden = instanceViews.size() - shown;
                    if (hidden > 0) {
                        Component more = Component.translatable("gui.arenas_ld.more_count", hidden);
                        g.drawString(font, more, panelInnerRight - font.width(more), y + Y_INST + 4, C_MUTED, false);
                    }
                    break;
                }
                px = panelInnerLeft;
                py += 12 + INST_PILL_GAP_Y;
                rowBottom = py + 12;
            }
            px = drawPill(g, label, px, py, PILL_BG[idx], PILL_TXT[idx], PILL_BDR[idx]);
            px += INST_PILL_GAP_X;
            shown++;
        }
    }

    private void renderBrowsing(GuiGraphics g, int x, int y, int mx, int my) {
        int contentY = y + Y_CONTENT + 4;

        // Invite banner (first invite, if any) — only occupies space when present
        if (!invitedLobbies.isEmpty()) {
            var inv = invitedLobbies.get(0);
            int bx = x + P + 3, by = contentY;
            g.fill(bx, by, bx + IW - 6, by + 20, 0xFF1a1800);
            drawBorder(g, bx, by, IW - 6, 20, 0xFF4a4000);
            String ownerTrunc = truncate(inv.ownerName(), 12);
            Component invitedText = Component.translatable("gui.arenas_ld.owner_invited_you", ownerTrunc);
            g.drawString(font, invitedText, bx + 4, by + 6, C_AMBER, false);
            drawTierPill(g, inv.tier(), bx + 4 + font.width(invitedText) + 4, by + 4);
            if (invitedLobbies.size() > 1) {
                small(g, Component.translatable("gui.arenas_ld.more_count", invitedLobbies.size() - 1), bx + 4, by + 14, C_MUTED);
            }
            int axBtn = bx + IW - 6 - 80;
            g.fill(axBtn, by + 4, axBtn + 36, by + 16, 0xFF0a2a0a);
            drawBorder(g, axBtn, by + 4, 36, 12, 0xFF1a5a1a);
            g.drawString(font, Component.translatable("gui.arenas_ld.accept"), axBtn + 4, by + 6, C_GREEN, false);
            g.fill(axBtn + 40, by + 4, axBtn + 76, by + 16, 0xFF2a0a0a);
            drawBorder(g, axBtn + 40, by + 4, 36, 12, 0xFF5a1a1a);
            g.drawString(font, Component.translatable("gui.arenas_ld.decline"), axBtn + 44, by + 6, C_RED, false);
            contentY += 24; // banner height + gap
        }

        // "OPEN LOBBIES" label
        smallLabel(g, Component.translatable("gui.arenas_ld.open_lobbies"), x + P + 3, contentY);
        contentY += 10;

        // Lobby list — fills remaining content zone down to action buttons
        int listX = x + P + 3, listY = contentY;
        int listW = IW - 6;
        int listH = Math.max(20, (y + Y_ACTIONS - 8) - listY);

        g.enableScissor(listX, listY, listX + listW, listY + listH);
        int maxScroll = Math.max(0, visLobbies.size() * ROW_H - listH);
        lobbyScroll = Mth.clamp(lobbyScroll, 0, maxScroll);

        if (visLobbies.isEmpty()) {
            g.drawString(font, Component.translatable("gui.arenas_ld.no_open_lobbies"), listX + 4, listY + 4, C_MUTED, false);
        }
        for (int i = 0; i < visLobbies.size(); i++) {
            var lv = visLobbies.get(i);
            int ry = (int)(listY + i * ROW_H - lobbyScroll);
            if (ry + ROW_H < listY || ry > listY + listH) continue;

            boolean sel = (i == selLobby);
            g.fill(listX, ry, listX + listW, ry + ROW_H - 1, sel ? C_ROW_SEL : C_ROW_NORM);
            drawBorder(g, listX, ry, listW, ROW_H - 1, sel ? C_BDR_HL : C_BDR);

            // Owner name
            g.drawString(font, truncate(lv.ownerName(), 10), listX + 4, ry + 5, C_TEXT, false);

            // Size
            String sizeStr = lv.size() + "/" + lv.maxSize();
            int sizeX = listX + 90;
            g.drawString(font, sizeStr, sizeX, ry + 5, C_MUTED, false);

            // Tier pill
            int tierPx = drawTierPill(g, lv.tier(), listX + 115, ry + 3);

            // Action tag
            String actionKey = lobbyActionLabelKey(lv);
            Component action = Component.translatable(actionKey);
            int actionColor = "gui.arenas_ld.action_join".equals(actionKey) ? C_BLUE : C_MUTED;
            Component actionBracket = Component.literal("[").append(action).append(Component.literal("]"));
            g.drawString(font, actionBracket, listX + listW - font.width(actionBracket) - 4, ry + 5, actionColor, false);
        }
        g.disableScissor();

        // Scroll indicator
        if (maxScroll > 0) {
            int sbH = listH;
            int knobH = Math.max(8, sbH * listH / Math.max(1, visLobbies.size() * ROW_H));
            int knobY = listY + (int)(lobbyScroll / maxScroll * (sbH - knobH));
            g.fill(listX + listW + 2, listY, listX + listW + 5, listY + sbH, 0x33FFFFFF);
            g.fill(listX + listW + 2, knobY, listX + listW + 5, knobY + knobH, 0x88FFFFFF);
        }
    }

    private void renderMember(GuiGraphics g, int x, int y, int mx, int my) {
        // Find our lobby
        // Use server-pushed member list — first is owner
        int cy = y + Y_CONTENT + 4;

        // Lobby info row
        g.drawString(font, Component.translatable("gui.arenas_ld.lobby"), x + P + 3, cy + 3, C_MUTED, false);
        // Tier badge from selectedTier
        drawTierPill(g, selectedTier.name(), x + W - P - 3 - 30, cy + 1);
        cy += 16;

        // Status
        g.drawString(font, Component.translatable("gui.arenas_ld.waiting_for_owner"), x + P + 3, cy, C_GREEN, false);
        cy += 16;

        // Members section
        smallLabel(g, Component.translatable("gui.arenas_ld.members"), x + P + 3, cy);
        cy += 12;

        renderMemberList(g, x, y, cy, false);
    }

    private void renderOwner(GuiGraphics g, int x, int y, int mx, int my) {
        // "Difficulty" label above tier buttons
        smallLabel(g, Component.translatable("gui.arenas_ld.difficulty"), x + P + 3, y + YO_DIFF);

        // Hardcore suffix next to checkbox label is handled by the Checkbox widget itself
        // "1 life, 2× rewards" — draw muted text next to the checkbox
        int hcTextX = x + P + 3 + 14 + font.width(Component.translatable("gui.arenas_ld.hardcore")) + 4;
        g.drawString(font, Component.translatable("gui.arenas_ld.hardcore_suffix"), hcTextX + 2, y + YO_HC + 5, C_MUTED, false);

        // Members label
        smallLabel(g, Component.translatable("gui.arenas_ld.members"), x + P + 3, y + YO_MEMBERS);

        renderMemberList(g, x, y, y + YO_LIST, true);
    }

    private void renderMemberList(GuiGraphics g, int x, int y, int listStartY, boolean ownerMode) {
        if (memberNames.isEmpty()) {
            g.drawString(font, Component.translatable("gui.arenas_ld.no_members"), x + P + 3, listStartY + 3, C_MUTED, false);
            return;
        }
        for (int i = 0; i < memberNames.size(); i++) {
            int ry = listStartY + i * ROW_H;
            boolean sel = ownerMode && i == selMember;
            if (sel) g.fill(x + P, ry, x + W - P, ry + ROW_H - 1, C_ROW_SEL);

            // Star for owner (index 0)
            if (i == 0) g.drawString(font, "\u2605", x + P + 3, ry + 4, C_YELLOW, false);
            // Name — highlight current player
            int nameColor = C_TEXT;
            g.drawString(font, memberNames.get(i), x + P + 14, ry + 4, nameColor, false);
            // "owner" tag
            if (i == 0) {
                Component ownerTag = Component.translatable("gui.arenas_ld.owner");
                small(g, ownerTag, x + W - P - 3 - font.width(ownerTag), ry + 6, C_MUTED);
            }
        }
        // Player count
        small(g, Component.translatable("gui.arenas_ld.players_count", memberNames.size(), 4),
              x + P + 3, listStartY + memberNames.size() * ROW_H + 2, C_MUTED);
    }

    private void renderQueued(GuiGraphics g, int x, int y) {
        int cy = y + Y_CONTENT + 4;
        int lx = x + P + 3;

        // "Queue  [#2 in line]"
        Component queueLabel = Component.translatable("gui.arenas_ld.queue");
        Component queuePosLabel = Component.translatable("gui.arenas_ld.queue_in_line", queuePos);
        g.drawString(font, queueLabel, lx, cy + 3, C_TEXT, false);
        int badgeX = lx + font.width(queueLabel) + 6;
        g.fill(badgeX, cy, badgeX + font.width(queuePosLabel) + 8, cy + 14, 0xFF1a0a2a);
        drawBorder(g, badgeX, cy, font.width(queuePosLabel) + 8, 14, 0xFF4a2a7a);
        g.drawString(font, queuePosLabel, badgeX + 4, cy + 3, C_PURPLE, false);
        cy += 18;

        // Est. wait
        Component estWaitLabel = Component.translatable("gui.arenas_ld.est_wait");
        g.drawString(font, estWaitLabel, lx, cy, C_MUTED, false);
        g.drawString(font, formatTime(queueEstSecs), lx + font.width(estWaitLabel), cy, C_AMBER, false);
        cy += 13;

        // Next slot
        if (cooldownSecs > 0) {
            Component nextSlotLabel = Component.translatable("gui.arenas_ld.next_slot");
            g.drawString(font, nextSlotLabel, lx, cy, C_MUTED, false);
            g.drawString(font, formatTime(cooldownSecs), lx + font.width(nextSlotLabel), cy, C_TEXT, false);
            cy += 13;
        }

        // Notification
        cy += 4;
        g.drawString(font, Component.translatable("gui.arenas_ld.slot_notify"), lx, cy, C_NOTIFY, false);
        cy += 16;

        // Your party
        smallLabel(g, Component.translatable("gui.arenas_ld.your_party"), lx, cy);
        cy += 12;
        renderMemberList(g, x, y, cy, false);
    }

    private void renderLeaderboard(GuiGraphics g, int x, int y) {
        int lx = x + P + 3;
        int ey = y + Y_LB_ENTRY + 2;
        int rowH = 10;
        int viewTop = ey;
        int viewBottom = y + Y_LB + 56 - 3;
        int viewH = Math.max(1, viewBottom - viewTop);

        List<DungeonLeaderboardEntry> entries = leaderboard; // server sends for current tier
        if (entries.isEmpty()) {
            Component noRecords = Component.translatable("gui.arenas_ld.no_records_yet");
            small(g, noRecords, lx + (IW - 6 - font.width(noRecords)) / 2, ey + 4, C_MUTED);
            return;
        }
        int totalH = entries.size() * rowH;
        int maxScroll = Math.max(0, totalH - viewH);
        lbScroll = Mth.clamp(lbScroll, 0, maxScroll);

        g.enableScissor(lx, viewTop, lx + IW - 6, viewBottom);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = (int) (ey + i * rowH - lbScroll);
            if (rowY + rowH < viewTop || rowY > viewBottom) continue;
            var e = entries.get(i);
            int rank_color = i == 0 ? C_YELLOW : (i == 1 ? 0xFFcccccc : (i == 2 ? 0xFFcc8833 : C_MUTED));
            small(g, (i + 1) + ".", lx, rowY, rank_color);
            small(g, truncate(e.playerName, 14), lx + 14, rowY, C_TEXT);
            String t = formatTime(e.timeSeconds);
            small(g, t, lx + IW - 6 - font.width(t), rowY, C_CYAN);
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int sbX = lx + IW - 6;
            int sbH = viewH;
            int knobH = Math.max(8, sbH * viewH / Math.max(1, totalH));
            int knobY = viewTop + (int) (lbScroll / maxScroll * (sbH - knobH));
            g.fill(sbX, viewTop, sbX + 2, viewTop + sbH, 0x33FFFFFF);
            g.fill(sbX, knobY, sbX + 2, knobY + knobH, 0x88FFFFFF);
        }
    }

    private void renderAdminBar(GuiGraphics g, int x, int y) {
        int lx = x + P + 88; // after the three buttons
        small(g, Component.translatable("gui.arenas_ld.admin_cd", formatTime(serverRespawnTicks / 20)), lx, y + Y_ADMIN + 3, C_LABEL);
        small(g, Component.translatable("gui.arenas_ld.admin_draft", formatTime(draftRespawnTicks / 20)), lx + 50, y + Y_ADMIN + 3, C_MUTED);
    }

    // ── Input handling ─────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int x = leftPos, y = topPos;
        UiState s = uiState();

        // Lobby list click (BROWSING)
        if (s == UiState.BROWSING) {
            int listX = x + P + 3, listY = y + YB_LIST;
            int listW = IW - 6, listH = YB_LIST_H;
            if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
                int clicked = (int)((my - listY + lobbyScroll) / ROW_H);
                if (clicked >= 0 && clicked < visLobbies.size()) {
                    selLobby = clicked;
                    updateButtons();
                    return true;
                }
            }

            // Invite banner accept/decline
            if (!invitedLobbies.isEmpty()) {
                int bx = x + P + 3, by = y + Y_CONTENT + 4;
                int axBtn = bx + IW - 6 - 80;
                if (my >= by + 4 && my <= by + 16) {
                    if (mx >= axBtn && mx < axBtn + 36) {
                        respondInvite(invitedLobbies.get(0), true);
                        return true;
                    }
                    if (mx >= axBtn + 40 && mx < axBtn + 76) {
                        respondInvite(invitedLobbies.get(0), false);
                        return true;
                    }
                }
            }
        }

        // Member list click (OWNER/MEMBER)
        if (s == UiState.OWNER || s == UiState.MEMBER) {
            int listStartY = (s == UiState.OWNER) ? y + YO_LIST : y + Y_CONTENT + 56;
            int listX = x + P;
            if (mx >= listX && mx < listX + IW && my >= listStartY) {
                int clicked = (int)((my - listStartY) / ROW_H);
                if (clicked >= 0 && clicked < memberNames.size()) {
                    selMember = clicked;
                    updateButtons();
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int x = leftPos, y = topPos;
        UiState s = uiState();
        if (s == UiState.BROWSING) {
            int bannerOffset = invitedLobbies.isEmpty() ? 0 : 24;
            int dynListY = y + Y_CONTENT + 4 + bannerOffset + 10;
            int dynListH = Math.max(20, (y + Y_ACTIONS - 8) - dynListY);
            int maxScroll = Math.max(0, visLobbies.size() * ROW_H - dynListH);
            if (mx >= x + P + 3 && mx < x + P + IW - 3 && my >= dynListY && my < dynListY + dynListH && maxScroll > 0) {
                lobbyScroll = Mth.clamp(lobbyScroll - dy * ROW_H, 0, maxScroll);
                return true;
            }
        }

        int lbX = x + P + 3;
        int lbY = y + Y_LB_ENTRY + 2;
        int lbW = IW - 6;
        int lbH = Math.max(1, (y + Y_LB + 56 - 3) - lbY);
        if (mx >= lbX && mx < lbX + lbW && my >= lbY && my < lbY + lbH) {
            int rowH = 10;
            int maxScroll = Math.max(0, leaderboard.size() * rowH - lbH);
            if (maxScroll > 0) {
                lbScroll = Mth.clamp(lbScroll - dy * rowH, 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    private void respondInvite(ModPackets.DungeonControllerInfoPayload.LobbyView lv, boolean accept) {
        ClientPlayNetworking.send(new ModPackets.RespondDungeonLobbyInvitePayload(lv.id(), accept));
        updateInfo();
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────
    /** Fill rect then draw 1-px border. */
    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF0e1220);
        drawBorder(g, x, y, w, h, 0xFF1f2845);
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x,         y,         x + w,     y + 1,     color); // top
        g.fill(x,         y + h - 1, x + w,     y + h,     color); // bottom
        g.fill(x,         y,         x + 1,     y + h,     color); // left
        g.fill(x + w - 1, y,         x + w,     y + h,     color); // right
    }

    /** Draws a colored pill. Returns new x after the pill. */
    private int drawPill(GuiGraphics g, String text, int x, int y, int bg, int textColor, int border) {
        int pw = font.width(text) + 6;
        int ph = 9;
        g.fill(x, y, x + pw, y + ph, bg);
        drawBorder(g, x, y, pw, ph, border);
        g.pose().pushPose();
        g.pose().translate(x + 3, y + 1, 0);
        g.pose().scale(0.9f, 0.9f, 1f);
        g.drawString(font, text, 0, 0, textColor, false);
        g.pose().popPose();
        return x + pw;
    }

    /** Draw tier pill, returns X after pill. */
    private int drawTierPill(GuiGraphics g, String tierName, int x, int y) {
        DifficultyTier t = DifficultyTier.fromNameOrDefault(tierName, DifficultyTier.NORMAL);
        int i = t.ordinal();
        String label = t.name().charAt(0) + t.name().substring(1).toLowerCase();
        int pw = font.width(label) + 6;
        g.fill(x, y, x + pw, y + 10, TIER_BG[i]);
        drawBorder(g, x, y, pw, 10, TIER_BDR[i]);
        g.drawString(font, label, x + 3, y + 2, TIER_TXT[i], false);
        return x + pw + 3;
    }

    private void smallLabel(GuiGraphics g, Component text, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, C_LABEL, false);
        g.pose().popPose();
    }

    private void smallLabel(GuiGraphics g, String text, int x, int y) {
        smallLabel(g, Component.literal(text), x, y);
    }

    private void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void small(GuiGraphics g, String text, int x, int y, int color) {
        small(g, Component.literal(text), x, y, color);
    }

    // ── UI state helpers ──────────────────────────────────────────────────────
    private enum UiState { BROWSING, MEMBER, OWNER, QUEUED }

    private UiState uiState() {
        if (!inLobby) return UiState.BROWSING;
        if (lobbyStatus == LobbyStatus.QUEUED) return UiState.QUEUED;
        return isOwner ? UiState.OWNER : UiState.MEMBER;
    }

    private boolean canJoinSelected() {
        if (visLobbies.isEmpty() || selLobby >= visLobbies.size()) return false;
        var lv = visLobbies.get(selLobby);
        if ("INVITE_ONLY".equalsIgnoreCase(lv.visibility()) && !lv.invited()) return false;
        if ("QUEUED".equalsIgnoreCase(lv.status()) || "IN_DUNGEON".equalsIgnoreCase(lv.status())) return false;
        return lv.size() < lv.maxSize();
    }

    private void syncSettings() {
        ClientPlayNetworking.send(new ModPackets.UpdateDungeonControllerSettingsPayload(
                menu.getPos(), hcBox != null && hcBox.selected(), selectedTier.name()));
    }

    // ── Formatting helpers ────────────────────────────────────────────────────
    private static String formatTime(int secs) {
        return String.format("%02d:%02d", secs / 60, secs % 60);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.isEmpty()) return "Unknown";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "~";
    }

    private static int pillIndex(String status) {
        if ("FREE".equalsIgnoreCase(status))     return 0;
        if ("RUNNING".equalsIgnoreCase(status))  return 1;
        return 2; // COOLDOWN
    }

    private static String pillLabel(ModPackets.DungeonControllerInfoPayload.InstanceView iv, int num) {
        return switch (iv.status().toUpperCase()) {
            case "FREE"     -> Component.translatable("gui.arenas_ld.instance_free", num).getString();
            case "RUNNING"  -> Component.translatable("gui.arenas_ld.instance_running", num).getString();
            default         -> Component.translatable("gui.arenas_ld.instance_cd", num, formatTime(iv.cooldownSeconds())).getString();
        };
    }

    private static String lobbyActionLabelKey(ModPackets.DungeonControllerInfoPayload.LobbyView lv) {
        if ("QUEUED".equalsIgnoreCase(lv.status()) || "IN_DUNGEON".equalsIgnoreCase(lv.status())) return "gui.arenas_ld.action_busy";
        if (lv.size() >= lv.maxSize()) return "gui.arenas_ld.action_full";
        if ("INVITE_ONLY".equalsIgnoreCase(lv.visibility()) && !lv.invited()) return "gui.arenas_ld.action_locked";
        return "gui.arenas_ld.action_join";
    }

    private static LobbyStatus parseLobbyStatus(String s) {
        try { return LobbyStatus.valueOf(s); } catch (Exception e) { return LobbyStatus.OPEN; }
    }

    private static LobbyVisibility parseLobbyVis(String s) {
        try { return LobbyVisibility.valueOf(s); } catch (Exception e) { return LobbyVisibility.OPEN; }
    }
}
