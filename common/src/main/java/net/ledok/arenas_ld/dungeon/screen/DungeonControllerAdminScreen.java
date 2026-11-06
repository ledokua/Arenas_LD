package net.ledok.arenas_ld.dungeon.screen;

import net.ledok.arenas_ld.dungeon.packet.MoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.RemoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.SetCloseTimerSecondsPayload;
import net.ledok.arenas_ld.dungeon.packet.SetCooldownTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetInviteExpiryTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetRespawnTimeTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetDeathTimePenaltyPayload;
import net.ledok.arenas_ld.dungeon.packet.SetMaxPartySizePayload;
import net.ledok.arenas_ld.dungeon.packet.SetTierConfigPayload;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.ledok.arenas_ld.screen.AdminDrafts;
import net.ledok.arenas_ld.screen.AdminEditState;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
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
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.UiSounds;
import net.ledok.vectorlib.client.canvas.widget.Widget;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.ledok.arenas_ld.screen.FitCanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
import static net.ledok.arenas_ld.screen.ArenasUi.GOOD;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasUi.INFO;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_MID;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;
import static net.ledok.arenas_ld.screen.ArenasUi.ROW_BG;
import static net.ledok.arenas_ld.screen.ArenasUi.WARN;
import net.ledok.arenas_ld.platform.ArenasNetwork;

public class DungeonControllerAdminScreen extends FitCanvasHandledScreen<DungeonControllerAdminScreenHandler> {

    /** Tab strip look: flat panel buttons; the selected tab (disabled) gets the PANEL fill + accent border. */
    private static final WidgetStyle TAB_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFA0A0A0, INK_DIM, ACCENT, 0x80A98BE8,
            18, 16, 4, true);

    /** -/+ stepper button look: quiet panel fill, hairline border, row highlight on hover. */
    private static final WidgetStyle STEP_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ROW_BG, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            0xFFFFFFFF, 0xFFFFFFFF, INK_DIM, INK_DIM, HAIRLINE, 0x80A98BE8,
            22, 16, 4, true);

    /** Remove button look: translucent red fill that deepens on hover, DANGER border. */
    private static final WidgetStyle DANGER_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x1F000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x44000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x44000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat((DANGER & 0x00FFFFFF) | 0x1F000000, DANGER, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            DANGER, DANGER, INK_DIM, INK_DIM, DANGER, 0x80A98BE8,
            18, 16, 4, true);

    private enum Tab {
        INSTANCES,
        GENERAL,
        EASY,
        NORMAL,
        HARD,
        NIGHTMARE
    }

    private final List<BlockPos> instances = new ArrayList<>();
    private final Set<BlockPos> activeRuns = new HashSet<>();
    private final Map<BlockPos, Integer> cooldowns = new HashMap<>();
    private final Set<BlockPos> pendingRemovals = new HashSet<>();
    private final Map<BlockPos, DungeonControllerAdminData.InstanceRun> runningInstances = new HashMap<>();
    private final Map<BlockPos, StatusBadge> statusBadges = new HashMap<>();
    private final Map<DifficultyTier, TierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);

    private Tab currentTab = Tab.INSTANCES;
    private String footerError;
    /** Which other tabs another admin's save just changed; cleared on a tab switch. */
    private String footerNotice;
    /**
     * Every settings field with the server value it came from: a snapshot (another admin's save,
     * or a save in another tab) only refreshes the fields nobody is editing here.
     */
    private final AdminDrafts drafts = new AdminDrafts();
    /** What the tab labels show (see {@link AdminEditState#markers}), so they're only relabeled on a change. */
    private String tabMarkers;
    private boolean synced;
    private long lastCooldownSecond = -1L;
    private long cooldownSnapshotEpochMs = System.currentTimeMillis();

    private Flex contentArea;
    private Flex footerActions;
    private Label footerLabel;

    private Button instancesTabButton;
    private Button generalTabButton;
    private Button easyTabButton;
    private Button normalTabButton;
    private Button hardTabButton;
    private Button nightmareTabButton;

    private String nameInput;
    private String activityIdInput;
    private String cooldownInput;
    private String closeTimerInput;
    private String maxPartyInput;
    private String inviteExpiryInput;
    private String respawnTimeInput;
    private String deathPenaltyInput;
    /** Percent of base HP added per extra player (0 = disabled); stored on the controller as a 0–100 double. */
    private String hpScaleInput;

    private List<String> knownLootTableIds = List.of();

    private final Map<DifficultyTier, String> healthInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> damageInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> xpInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> lootInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> timeInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> rewardInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, Boolean> enabledInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, List<String>> commandInputs = new EnumMap<>(DifficultyTier.class);
    /** The "Commands on win" add field per tier, kept across rebuilds. */
    private final Map<DifficultyTier, String> commandDrafts = new EnumMap<>(DifficultyTier.class);

    public DungeonControllerAdminScreen(DungeonControllerAdminScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(600, 340), Placement.Screen.center());
        canvas.theme(ArenasUi.THEME);
        fillWindow();
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

        float shellWidth = Math.max(500, Math.min(620, canvas.width() - 24));
        float shellHeight = Math.max(300, canvas.height() - 24);
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.backgroundFill(PANEL, HAIRLINE_HI, 1);

        shell.item(buildHeader());
        shell.item(buildTabs());

        contentArea = Flex.column().gap(4).padding(Insets.of(10));
        contentArea.sizing(Sizing.fill(), Sizing.content());
        contentArea.backgroundFill(PANEL);
        ScrollPanel scroll = new ScrollPanel(100, 100, contentArea);
        scroll.sizing(Sizing.fill(), Sizing.expand());
        scroll.wheelStep(18);
        shell.item(scroll);

        shell.item(buildFooter());

        root.layoutIn(canvas.width(), canvas.height());

        syncStateFromMenu();
        if (!synced) {
            drafts.merge(serverTexts());
            loadInputs();
            synced = true;
        }
        rebuildUi();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (synced && minecraft != null && minecraft.player != null && minecraft.player.tickCount % 10 == 0) {
            updateTabLabels(); // the unsaved-edit markers follow typing
        }
        if (currentTab == Tab.INSTANCES) {
            long second = System.currentTimeMillis() / 1000L;
            if (second != lastCooldownSecond) {
                lastCooldownSecond = second;
                refreshCooldownsInPlace();
            }
        }
    }

    private void refreshCooldownsInPlace() {
        for (Map.Entry<BlockPos, StatusBadge> entry : statusBadges.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pendingRemovals.contains(pos) || activeRuns.contains(pos) || !cooldowns.containsKey(pos)) {
                continue;
            }
            InstanceStatusInfo info = instanceStatusInfo(pos);
            updateBadge(entry.getValue(), info.label(), info.color());
        }
    }

    private record StatusBadge(Flex box, TextNode label) {}

    private void updateBadge(StatusBadge badge, String text, int color) {
        badge.box().backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        badge.label().text(Component.literal(text)).color(color);
    }

    private Flex buildHeader() {
        Flex header = Flex.row().gap(8).padding(Insets.of(6));
        header.sizing(Sizing.fill(), Sizing.fixed(42));
        header.backgroundFill(PANEL_2);

        Flex mark = Flex.column();
        mark.sizing(Sizing.fixed(18), Sizing.fixed(18));
        mark.backgroundFill(WARN);
        header.item(mark);

        Flex info = Flex.column().gap(3);
        info.sizing(Sizing.content(), Sizing.content());

        Flex titleLine = Flex.row().gap(8).alignItems(Align.CENTER);
        titleLine.sizing(Sizing.content(), Sizing.content());
        titleLine.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dungeon_controller_admin.title"), INK));
        titleLine.item(badge(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.op"), WARN));
        info.item(titleLine);

        Flex meta = Flex.row().gap(10).alignItems(Align.CENTER);
        meta.sizing(Sizing.content(), Sizing.content());
        meta.item(smallMeta("POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()));
        Flex live = Flex.row().gap(4).alignItems(Align.CENTER);
        live.sizing(Sizing.content(), Sizing.content());
        live.item(ArenasUi.text(Component.literal("●"), GOOD));
        live.item(smallMeta("LIVE", GOOD));
        meta.item(live);
        info.item(meta);
        header.item(info);

        header.spacer();

        header.item(ArenasUi.button(Component.literal("X"), 20, 16, this::onClose));
        return header;
    }

    private Flex buildTabs() {
        Flex tabs = Flex.row().gap(4).padding(Insets.of(4));
        tabs.sizing(Sizing.fill(), Sizing.fixed(30));
        tabs.backgroundFill(PANEL_2);

        tabMarkers = null; // new buttons: label them all
        instancesTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.instances", Tab.INSTANCES, 76);
        generalTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.general", Tab.GENERAL, 72);
        easyTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.easy", Tab.EASY, 56);
        normalTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.normal", Tab.NORMAL, 64);
        hardTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.hard", Tab.HARD, 56);
        nightmareTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.nightmare", Tab.NIGHTMARE, 80);

        tabs.item(instancesTabButton);
        tabs.item(generalTabButton);
        tabs.item(easyTabButton);
        tabs.item(normalTabButton);
        tabs.item(hardTabButton);
        tabs.item(nightmareTabButton);
        return tabs;
    }

    private Flex buildFooter() {
        Flex footer = Flex.row().gap(6).padding(Insets.of(6)).alignItems(Align.CENTER);
        footer.sizing(Sizing.fill(), Sizing.fixed(34));
        footer.backgroundFill(PANEL_2);

        footerLabel = Label.of(100, Component.empty());
        footerLabel.color(DANGER).tooltipWhenCut(true);
        footerLabel.sizing(Sizing.expand(), Sizing.content());
        footer.item(footerLabel);

        footerActions = Flex.row().gap(4);
        footerActions.sizing(Sizing.content(), Sizing.content());
        footer.item(footerActions);
        return footer;
    }

    private Button tabButton(String key, Tab tab, float width) {
        Button button = new Button(width, 18, Component.translatable(key), () -> {
            currentTab = tab;
            footerError = null;
            footerNotice = null;
            rebuildUi();
        });
        button.style(TAB_STYLE);
        return button;
    }

    private void rebuildUi() {
        contentArea.clear();
        footerActions.clear();

        instancesTabButton.enabled(currentTab != Tab.INSTANCES);
        generalTabButton.enabled(currentTab != Tab.GENERAL);
        easyTabButton.enabled(currentTab != Tab.EASY);
        normalTabButton.enabled(currentTab != Tab.NORMAL);
        hardTabButton.enabled(currentTab != Tab.HARD);
        nightmareTabButton.enabled(currentTab != Tab.NIGHTMARE);
        updateTabLabels();

        switch (currentTab) {
            case INSTANCES -> buildInstancesTab();
            case GENERAL -> buildGeneralTab();
            case EASY -> buildTierTab(DifficultyTier.EASY);
            case NORMAL -> buildTierTab(DifficultyTier.NORMAL);
            case HARD -> buildTierTab(DifficultyTier.HARD);
            case NIGHTMARE -> buildTierTab(DifficultyTier.NIGHTMARE);
        }

        refreshFooter();
    }

    private void updateTabLabels() {
        drafts.setAll(inputTexts());
        String markers = AdminEditState.markers(drafts, "general.", "EASY.", "NORMAL.", "HARD.", "NIGHTMARE.") + instances.size();
        if (markers.equals(tabMarkers)) {
            return;
        }
        tabMarkers = markers;
        instancesTabButton.label(Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.instances").append(" [" + instances.size() + "]"));
        generalTabButton.label(AdminEditState.tabLabel("gui.arenas_ld.dungeon_controller_admin.tab.general", drafts, prefix(Tab.GENERAL)));
        easyTabButton.label(AdminEditState.tabLabel("gui.arenas_ld.dungeon_controller_admin.tab.easy", drafts, prefix(Tab.EASY)));
        normalTabButton.label(AdminEditState.tabLabel("gui.arenas_ld.dungeon_controller_admin.tab.normal", drafts, prefix(Tab.NORMAL)));
        hardTabButton.label(AdminEditState.tabLabel("gui.arenas_ld.dungeon_controller_admin.tab.hard", drafts, prefix(Tab.HARD)));
        nightmareTabButton.label(AdminEditState.tabLabel("gui.arenas_ld.dungeon_controller_admin.tab.nightmare", drafts, prefix(Tab.NIGHTMARE)));
    }

    /** The draft key prefix of a settings tab; null for the instances tab. */
    private static String prefix(Tab tab) {
        return switch (tab) {
            case INSTANCES -> null;
            case GENERAL -> "general.";
            case EASY -> "EASY.";
            case NORMAL -> "NORMAL.";
            case HARD -> "HARD.";
            case NIGHTMARE -> "NIGHTMARE.";
        };
    }

    /**
     * The footer: an input error first, then this tab's conflicts (with a button that takes the
     * other admin's values), then which other tabs another admin just saved.
     */
    private void refreshFooter() {
        footerActions.clear();
        String tabPrefix = prefix(currentTab);
        Map<String, String> conflicts = tabPrefix == null ? Map.of() : drafts.conflicts(tabPrefix);
        if (footerError != null) {
            footerLabel.text(Component.literal(footerError)).color(DANGER);
        } else if (!conflicts.isEmpty()) {
            footerLabel.text(AdminEditState.conflictText(menu.getLastEditor(), conflicts, DungeonControllerAdminScreen::caption)).color(WARN);
            footerActions.item(ArenasUi.button(Component.translatable("gui.arenas_ld.admin.take_theirs"), 96, 18, () -> {
                drafts.takeServer(conflicts.keySet());
                loadInputs();
                rebuildUi();
            }));
        } else if (footerNotice != null) {
            footerLabel.text(Component.literal(footerNotice)).color(INFO);
        } else {
            footerLabel.text(Component.empty());
        }
    }

    /** A field's caption, for the conflict warning. */
    private static Component caption(String key) {
        String field = key.substring(key.indexOf('.') + 1);
        String section = key.startsWith("general.") ? "general" : "tier";
        return switch (field) {
            case "id" -> Component.translatable("gui.arenas_ld.admin.activity_id");
            case "commands" -> Component.translatable("gui.arenas_ld.admin.reward_commands");
            case "enabled" -> Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.availability");
            case "loot" -> Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.loot");
            default -> Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui." + section + "." + field);
        };
    }

    private void buildInstancesTab() {
        statusBadges.clear();
        int running = 0;
        int idleCooldown = 0;
        for (BlockPos pos : instances) {
            if (pendingRemovals.contains(pos)) {
                continue;
            }
            if (activeRuns.contains(pos)) {
                running++;
            } else {
                idleCooldown++;
            }
        }
        contentArea.item(instancesSummaryBar(running, instances.size(), idleCooldown));

        if (instances.isEmpty()) {
            Flex empty = Flex.row().padding(Insets.of(10)).alignItems(Align.CENTER);
            empty.sizing(Sizing.fill(), Sizing.content());
            empty.backgroundFill(ROW_BG);
            empty.item(ArenasUi.text(Component.translatable("gui.arenas_ld.dungeon_controller_admin.use_linker_hint"), INK_DIM));
            contentArea.item(empty);
            return;
        }

        Flex list = Flex.column();
        list.sizing(Sizing.fill(), Sizing.content());
        list.item(instanceHeaderRow());
        list.item(rowDivider(HAIRLINE_HI));
        for (int i = 0; i < instances.size(); i++) {
            if (i > 0) {
                list.item(rowDivider(HAIRLINE));
            }
            list.item(instanceRow(i, instances.get(i)));
        }
        contentArea.item(list);
    }

    private Flex instancesSummaryBar(int running, int total, int idleCooldown) {
        Flex bar = Flex.row().alignItems(Align.CENTER);
        bar.sizing(Sizing.fill(), Sizing.fixed(40));
        bar.backgroundFill(ROW_BG, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(3), Sizing.fill());
        accent.backgroundFill(ACCENT);
        bar.item(accent);

        Flex inner = Flex.row().gap(0).padding(Insets.of(0, 10, 0, 12)).alignItems(Align.CENTER);
        inner.sizing(Sizing.expand(), Sizing.fill());

        TextNode activeValue = ArenasUi.text(Component.literal(running + " / " + total), running > 0 ? GOOD : INK);
        inner.item(summaryColumn(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.active_runs"), activeValue, Sizing.fixed(120)));
        inner.item(summaryColumn(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.idle_cooldown"),
            ArenasUi.text(Component.literal(Integer.toString(idleCooldown)), INK), Sizing.fixed(140)));

        Flex hint = Flex.row().gap(3).justify(Justify.END).alignItems(Align.CENTER);
        hint.sizing(Sizing.expand(), Sizing.content());
        hint.item(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.hint_pre"), INK_DIM));
        hint.item(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.hint_link"), ACCENT));
        hint.item(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.hint_post"), INK_DIM));
        inner.item(hint);
        bar.item(inner);
        return bar;
    }

    private Flex summaryColumn(String caption, TextNode value, Sizing width) {
        Flex col = Flex.column().gap(3);
        col.sizing(width, Sizing.content());
        col.item(labelLiteral(caption, INK_DIM));
        col.item(value);
        return col;
    }

    private Flex instanceRow(int index, BlockPos pos) {
        Flex row = Flex.row().gap(6).padding(Insets.of(7, 8, 7, 8)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(ROW_BG);

        Label rank = ArenasUi.label(28, Component.literal(Integer.toString(index + 1)), INK_MID);
        rank.sizing(Sizing.fixed(28), Sizing.content());
        row.item(rank);

        Label position = ArenasUi.label(100, Component.literal("X " + pos.getX() + " · Z " + pos.getZ()), INK);
        position.sizing(Sizing.expand(), Sizing.content());
        row.item(position);

        InstanceStatusInfo status = instanceStatusInfo(pos);
        StatusBadge statusBadge = fixedBadge(Component.literal(status.label()), 120, status.color());
        statusBadges.put(pos, statusBadge);
        row.item(statusBadge.box());

        Flex tierParty = Flex.column().gap(1);
        tierParty.sizing(Sizing.fixed(140), Sizing.content());
        DungeonControllerAdminData.InstanceRun run = runningInstances.get(pos);
        if (run != null) {
            tierParty.item(labelLiteral(titleCase(run.tier().name()), tierColor(run.tier())));
            String party = run.party() == null || run.party().isEmpty()
                ? tr("gui.arenas_ld.dungeon_controller_admin.ui.party_fallback")
                : Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.party_suffix", run.party()).getString();
            tierParty.item(labelLiteral(party, INK_MID));
        } else {
            tierParty.item(labelLiteral("—", INK_DIM));
        }
        row.item(tierParty);

        Button up = ArenasUi.button(Component.literal("↑"), 22, 18, () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new MoveDungeonInstancePayload(menu.getBlockPos(), pos, -1));
        });
        up.enabled(index > 0);
        row.item(up);

        Button down = ArenasUi.button(Component.literal("↓"), 22, 18, () -> {
            footerError = null;
            ArenasNetwork.sendToServer(new MoveDungeonInstancePayload(menu.getBlockPos(), pos, 1));
        });
        down.enabled(index < instances.size() - 1);
        row.item(down);

        boolean pending = pendingRemovals.contains(pos);
        Button action;
        if (pending) {
            action = ArenasUi.button(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.action.cancel"), 64, 18, () -> {
                footerError = null;
                ArenasNetwork.sendToServer(new RemoveDungeonInstancePayload(menu.getBlockPos(), pos));
            });
        } else {
            action = dangerButton(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.action.remove"), 64, () -> {
                footerError = null;
                ArenasNetwork.sendToServer(new RemoveDungeonInstancePayload(menu.getBlockPos(), pos));
            });
        }
        row.item(action);
        return row;
    }

    private record InstanceStatusInfo(String label, int color) {}

    private InstanceStatusInfo instanceStatusInfo(BlockPos pos) {
        if (pendingRemovals.contains(pos)) {
            return new InstanceStatusInfo(tr("gui.arenas_ld.dungeon_controller_admin.ui.status.pending_removal"), WARN);
        }
        if (activeRuns.contains(pos)) {
            return new InstanceStatusInfo(tr("gui.arenas_ld.dungeon_controller_admin.ui.status.running"), GOOD);
        }
        int cooldown = liveCooldownTicks(pos);
        if (cooldown > 0) {
            return new InstanceStatusInfo(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.status.cooldown", formatTicks(cooldown)).getString(), WARN);
        }
        return new InstanceStatusInfo(tr("gui.arenas_ld.dungeon_controller_admin.ui.status.idle"), INK_DIM);
    }

    // Counts down locally between snapshots so the cooldown ticks live without a server push.
    private int liveCooldownTicks(BlockPos pos) {
        Integer ticks = cooldowns.get(pos);
        if (ticks == null) {
            return 0;
        }
        int elapsedTicks = (int) ((System.currentTimeMillis() - cooldownSnapshotEpochMs) / 50L);
        return Math.max(0, ticks - elapsedTicks);
    }

    private void buildGeneralTab() {
        contentArea.item(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.general.section"), null));
        contentArea.item(ArenasUi.spacer(2));
        contentArea.item(nameFieldRow());
        contentArea.item(ArenasUi.spacer(8));
        contentArea.item(textFieldRow(tr("gui.arenas_ld.admin.activity_id"), tr("gui.arenas_ld.admin.activity_id_hint"),
            activityIdInput, 128, v -> activityIdInput = v));
        contentArea.item(ArenasUi.spacer(8));
        contentArea.item(twoColumnRow(
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.cooldown"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.cooldown_hint"), "S", 5, 0, 86400, cooldownInput, v -> cooldownInput = v),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.close_timer"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.close_timer_hint"), "S", 5, 0, 86400, closeTimerInput, v -> closeTimerInput = v)
        ));
        contentArea.item(ArenasUi.spacer(8));
        contentArea.item(twoColumnRow(
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.max_party"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.max_party_hint"), "P", 1, 1, 64, maxPartyInput, v -> maxPartyInput = v),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.invite_expiry"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.invite_expiry_hint"), "S", 5, 1, 86400, inviteExpiryInput, v -> inviteExpiryInput = v)
        ));
        contentArea.item(ArenasUi.spacer(8));
        contentArea.item(twoColumnRow(
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.respawn_time"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.respawn_time_hint"), "T", 10, 0, 12000, respawnTimeInput, v -> respawnTimeInput = v),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.death_penalty"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.death_penalty_hint"), "S", 1, 0, 600, deathPenaltyInput, v -> deathPenaltyInput = v)
        ));
        contentArea.item(ArenasUi.spacer(8));
        contentArea.item(stepperField(
            tr("gui.arenas_ld.dungeon_controller_admin.ui.general.hp_scale"),
            tr("gui.arenas_ld.dungeon_controller_admin.ui.general.hp_scale_hint"),
            "%", 5, 0, 1000, hpScaleInput, v -> hpScaleInput = v));
        contentArea.item(ArenasUi.spacer(10));

        Flex applyRow = Flex.row().justify(Justify.END).alignItems(Align.CENTER);
        applyRow.sizing(Sizing.fill(), Sizing.content());
        applyRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.general.apply"), 120, 18, this::applyGeneral));
        contentArea.item(applyRow);
    }

    private Flex twoColumnRow(Flex left, Flex right) {
        Flex row = Flex.row();
        row.sizing(Sizing.fill(), Sizing.content());
        Flex c0 = Flex.column().padding(Insets.of(0, 5, 0, 0));
        c0.sizing(Sizing.fill(0.5f), Sizing.content());
        c0.item(left);
        Flex c1 = Flex.column().padding(Insets.of(0, 0, 0, 5));
        c1.sizing(Sizing.fill(0.5f), Sizing.content());
        c1.item(right);
        row.item(c0);
        row.item(c1);
        return row;
    }

    private Flex nameFieldRow() {
        Flex col = Flex.column().gap(4);
        col.sizing(Sizing.fill(), Sizing.content());

        col.item(fieldHead(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.name"),
            tr("gui.arenas_ld.dungeon_controller_admin.ui.general.name_hint")));

        Flex fieldWrap = Flex.row().alignItems(Align.CENTER);
        fieldWrap.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldWrap.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldWrap.item(accent);
        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(100, nameInput, 48);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(v -> nameInput = v);
        fieldWrap.item(field);
        col.item(fieldWrap);
        return col;
    }

    /** Caption + accent-barred single-line text field (the activity id). */
    private Flex textFieldRow(String caption, String hint, String initial, int maxLength, Consumer<String> onChange) {
        Flex col = Flex.column().gap(4);
        col.sizing(Sizing.fill(), Sizing.content());
        col.item(fieldHead(caption, hint));
        Flex fieldWrap = Flex.row().alignItems(Align.CENTER);
        fieldWrap.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldWrap.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldWrap.item(accent);
        TextField field = ArenasUi.textField(100, initial == null ? "" : initial, maxLength);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.onChange(onChange);
        fieldWrap.item(field);
        col.item(fieldWrap);
        return col;
    }

    private Flex stepperField(String caption, String hint, String unit, int step, int min, int max,
                              String initial, Consumer<String> onChange) {
        Flex col = Flex.column().gap(4);
        col.sizing(Sizing.fill(), Sizing.content());

        col.item(fieldHead(caption, hint));

        Flex stepper = Flex.row().gap(6).alignItems(Align.CENTER);
        stepper.sizing(Sizing.fill(), Sizing.content());

        TextField field = ArenasUi.textField(100, initial, 32);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.onChange(onChange);

        stepper.item(stepButton("-", () -> stepValue(field, -step, min, max, onChange)));
        stepper.item(fieldWrap(field, unit));
        stepper.item(stepButton("+", () -> stepValue(field, step, min, max, onChange)));
        col.item(stepper);
        return col;
    }

    /** Caption at the left, italic hint at the right — the head row shared by every field. */
    private Label fieldHead(String caption, String hint) {
        Label head = ArenasUi.label(10, Component.literal(caption), INK_DIM)
            .secondary(Component.literal(hint).withStyle(ChatFormatting.ITALIC))
            .secondaryColor(INK_DIM)
            .cutSecondaryFirst(true)
            .tooltipWhenCut(true);
        head.sizing(Sizing.fill(), Sizing.content());
        return head;
    }

    /** Accent bar + text field + unit cell inside the outlined 22px wrapper. */
    private Flex fieldWrap(TextField field, String unit) {
        Flex fieldWrap = Flex.row().alignItems(Align.CENTER);
        fieldWrap.sizing(Sizing.expand(), Sizing.fixed(22));
        fieldWrap.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldWrap.item(accent);
        fieldWrap.item(field);
        Flex unitCell = Flex.row().padding(Insets.of(0, 6, 0, 6));
        unitCell.sizing(Sizing.content(), Sizing.content());
        unitCell.item(labelLiteral(unit, INK_DIM));
        fieldWrap.item(unitCell);
        return fieldWrap;
    }

    private Button stepButton(String glyph, Runnable action) {
        Button button = new Button(34, 22, Component.literal(glyph), action);
        button.style(STEP_STYLE);
        return button;
    }

    private void stepValue(TextField field, int delta, int min, int max, Consumer<String> onChange) {
        int current;
        try {
            current = Integer.parseInt(field.text().trim());
        } catch (Exception ignored) {
            current = min;
        }
        int next = Math.max(min, Math.min(max, current + delta));
        String value = Integer.toString(next);
        field.text(value);
        onChange.accept(value);
    }

    private void buildTierTab(DifficultyTier tier) {
        TierConfig config = tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier));
        String tierName = titleCase(tier.name());
        boolean enabled = enabledInputs.getOrDefault(tier, config.enabled());

        contentArea.item(tierBanner(tier, tierName));
        contentArea.item(ArenasUi.spacer(4));

        contentArea.item(togglePanel(
            tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.availability"),
            Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.availability_desc", tierName).getString(),
            enabled ? tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.enabled") : tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.disabled"),
            enabled ? GOOD : INK_DIM,
            GOOD,
            () -> enabledInputs.getOrDefault(tier, config.enabled()),
            () -> {
                enabledInputs.put(tier, !enabledInputs.getOrDefault(tier, config.enabled()));
                rebuildUi();
            }
        ));

        contentArea.item(ArenasUi.spacer(6));
        contentArea.item(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.parameters"), INK_DIM));
        contentArea.item(ArenasUi.spacer(2));

        contentArea.item(twoColumnRow(
            stepperFieldDouble(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.health"), tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.health_hint"), "×", 0.25, 0.0, 100.0,
                healthInputs.getOrDefault(tier, trimDouble(config.healthMultiplier())), v -> healthInputs.put(tier, v)),
            stepperFieldDouble(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.damage"), tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.damage_hint"), "×", 0.25, 0.0, 100.0,
                damageInputs.getOrDefault(tier, trimDouble(config.damageMultiplier())), v -> damageInputs.put(tier, v))
        ));

        contentArea.item(ArenasUi.spacer(6));
        contentArea.item(twoColumnRow(
            lootField(lootInputs.getOrDefault(tier, config.perPlayerLootTable()), v -> lootInputs.put(tier, v)),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.time"), tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.time_hint"), "S", 30, 1, 86400,
                timeInputs.getOrDefault(tier, Integer.toString(config.dungeonTimeSeconds())), v -> timeInputs.put(tier, v))
        ));

        contentArea.item(ArenasUi.spacer(6));
        contentArea.item(twoColumnRow(
            rewardCurrencyField(tier, config),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.xp"), tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.xp_hint"), "XP", 10, 0, 1_000_000,
                xpInputs.getOrDefault(tier, Integer.toString(config.skillExperiencePerWin())), v -> xpInputs.put(tier, v))
        ));

        contentArea.item(ArenasUi.spacer(6));
        contentArea.item(net.ledok.arenas_ld.screen.RewardCommandsEditor.build(
            commandInputs.computeIfAbsent(tier, t -> new ArrayList<>(config.rewardCommands())),
            commandDrafts.getOrDefault(tier, ""), v -> commandDrafts.put(tier, v), this::rebuildUi));

        contentArea.item(ArenasUi.spacer(8));
        Flex applyRow = Flex.row().justify(Justify.END).alignItems(Align.CENTER);
        applyRow.sizing(Sizing.fill(), Sizing.content());
        applyRow.item(ArenasUi.button(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.apply", tier.name()), 150, 18, () -> applyTier(tier)));
        contentArea.item(applyRow);
    }

    private Flex tierBanner(DifficultyTier tier, String tierName) {
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
        inner.item(badge(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.banner_badge", tier.name()), color));
        inner.item(labelLiteral(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.banner_desc", tierName).getString(), INK));
        banner.item(inner);
        return banner;
    }

    private Flex togglePanel(String caption, String description, String stateLabel, int stateColor, int onColor,
                             java.util.function.BooleanSupplier on, Runnable onToggle) {
        Flex panel = Flex.row().alignItems(Align.CENTER);
        panel.sizing(Sizing.fill(), Sizing.fixed(40));
        panel.backgroundFill(ROW_BG, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(onColor);
        panel.item(accent);

        Flex inner = Flex.row().gap(8).padding(Insets.of(6, 10, 6, 10)).alignItems(Align.CENTER);
        inner.sizing(Sizing.expand(), Sizing.fill());

        Flex text = Flex.column().gap(2);
        text.sizing(Sizing.expand(), Sizing.content());
        text.item(labelLiteral(caption, INK_DIM));
        text.item(labelLiteral(description, INK));
        inner.item(text);

        if (stateLabel != null) {
            inner.item(labelLiteral(stateLabel, stateColor));
        }
        inner.item(new ToggleSwitch(onColor, on, onToggle));
        panel.item(inner);
        return panel;
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

    private Flex rewardCurrencyField(DifficultyTier tier, TierConfig config) {
        String initial = rewardInputs.getOrDefault(tier, Long.toString(config.rewardCurrency()));
        Flex col = Flex.column().gap(4);
        col.sizing(Sizing.fill(), Sizing.content());

        col.item(fieldHead(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.reward"), tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.reward_hint")));

        Flex stepper = Flex.row().gap(6).alignItems(Align.CENTER);
        stepper.sizing(Sizing.fill(), Sizing.content());

        TextField field = ArenasUi.textField(100, initial, 32);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.onChange(v -> rewardInputs.put(tier, v));

        stepper.item(stepButton("-", () -> stepLong(field, -10L, 0L, 1_000_000_000L, v -> rewardInputs.put(tier, v))));
        stepper.item(fieldWrap(field, "LC"));
        stepper.item(stepButton("+", () -> stepLong(field, 10L, 0L, 1_000_000_000L, v -> rewardInputs.put(tier, v))));
        col.item(stepper);
        return col;
    }

    private void stepLong(TextField field, long delta, long min, long max, Consumer<String> onChange) {
        long current;
        try {
            current = Long.parseLong(field.text().trim());
        } catch (Exception ignored) {
            current = min;
        }
        long next = Math.max(min, Math.min(max, current + delta));
        String value = Long.toString(next);
        field.text(value);
        onChange.accept(value);
    }

    private Flex lootField(String initial, Consumer<String> onChange) {
        Flex col = Flex.column().gap(4);
        col.sizing(Sizing.fill(), Sizing.content());
        Flex head = Flex.row().alignItems(Align.CENTER);
        head.sizing(Sizing.fill(), Sizing.content());
        head.item(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.loot"), INK_DIM));
        col.item(head);

        Flex fieldRow = Flex.row().gap(4).alignItems(Align.CENTER);
        fieldRow.sizing(Sizing.fill(), Sizing.fixed(22));
        fieldRow.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = Flex.column();
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);
        fieldRow.item(accent);
        Flex idCell = Flex.row().padding(Insets.of(0, 0, 0, 4));
        idCell.sizing(Sizing.content(), Sizing.content());
        idCell.item(labelLiteral("id:", INK_DIM));
        fieldRow.item(idCell);

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(100, initial, 256);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(onChange);
        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(() -> knownLootTableIds, field);
        fieldRow.item(field);
        fieldRow.item(dropdown.chevron());
        col.item(fieldRow);

        col.item(dropdown.panel());
        return col;
    }

    private Flex stepperFieldDouble(String caption, String hint, String unit, double step, double min, double max,
                                    String initial, Consumer<String> onChange) {
        Flex col = Flex.column().gap(4);
        col.sizing(Sizing.fill(), Sizing.content());

        col.item(fieldHead(caption, hint));

        Flex stepper = Flex.row().gap(6).alignItems(Align.CENTER);
        stepper.sizing(Sizing.fill(), Sizing.content());

        TextField field = ArenasUi.textField(100, initial, 32);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.onChange(onChange);

        stepper.item(stepButton("-", () -> stepValueDouble(field, -step, min, max, onChange)));
        stepper.item(fieldWrap(field, unit));
        stepper.item(stepButton("+", () -> stepValueDouble(field, step, min, max, onChange)));
        col.item(stepper);
        return col;
    }

    private void stepValueDouble(TextField field, double delta, double min, double max, Consumer<String> onChange) {
        double current;
        try {
            current = Double.parseDouble(field.text().trim());
        } catch (Exception ignored) {
            current = min;
        }
        double next = Math.max(min, Math.min(max, current + delta));
        String value = trimDouble(next);
        field.text(value);
        onChange.accept(value);
    }

    private Button dangerButton(Component text, float width, Runnable action) {
        Button button = new Button(width, 18, text.copy().withStyle(ChatFormatting.RED), action);
        button.style(DANGER_STYLE);
        return button;
    }

    private Flex rowDivider(int color) {
        Flex divider = Flex.row();
        divider.sizing(Sizing.fill(), Sizing.fixed(1));
        divider.backgroundFill(color);
        return divider;
    }

    private int tierColor(DifficultyTier tier) {
        return switch (tier) {
            case EASY -> GOOD;
            case NORMAL -> INFO;
            case HARD -> WARN;
            case NIGHTMARE -> DANGER;
        };
    }

    private static String titleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.charAt(0) + value.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private Flex sectionHeader(Component title, Integer count) {
        Flex row = Flex.row().gap(8).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(ArenasUi.text(title, INK_DIM));
        if (count != null) {
            row.item(ArenasUi.text(Component.literal("· " + count), ACCENT));
        }
        return row;
    }

    private Flex instanceHeaderRow() {
        Flex row = Flex.row().gap(6).padding(Insets.of(5, 8, 5, 8)).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.backgroundFill(PANEL_2);
        row.item(headerCell("#", Sizing.fixed(28)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller_admin.ui.col.position"), Sizing.expand()));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller_admin.ui.col.status"), Sizing.fixed(120)));
        row.item(headerCell(tr("gui.arenas_ld.dungeon_controller_admin.ui.col.tier_party"), Sizing.fixed(140)));
        row.item(headerCell("", Sizing.fixed(22)));
        row.item(headerCell("", Sizing.fixed(22)));
        row.item(headerCell("", Sizing.fixed(64)));
        return row;
    }

    private Flex badge(Component text, int color) {
        Flex tag = Flex.row().padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.sizing(Sizing.content(), Sizing.content());
        tag.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        tag.item(ArenasUi.text(text, color));
        return tag;
    }

    private Label headerCell(String text, Sizing sizing) {
        Label label = ArenasUi.label(100, Component.literal(text), INK_DIM);
        label.sizing(sizing, Sizing.content());
        return label;
    }

    private StatusBadge fixedBadge(Component text, float width, int color) {
        Flex tag = Flex.row().padding(Insets.of(4, 4, 2, 4)).justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.sizing(Sizing.fixed(width), Sizing.content());
        tag.backgroundFill((color & 0x00FFFFFF) | 0x22000000, (color & 0x00FFFFFF) | 0x55000000, 1);
        TextNode label = ArenasUi.text(text, color);
        tag.item(label);
        return new StatusBadge(tag, label);
    }

    private TextNode labelLiteral(String value, int color) {
        return ArenasUi.text(Component.literal(value), color);
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private String trimDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private TextNode smallMeta(String text, int color) {
        return ArenasUi.text(Component.literal(text), color);
    }

    private TextNode smallMeta(String text) {
        return smallMeta(text, INK_DIM);
    }

    private void applyGeneral() {
        Integer cooldown = parseInt(cooldownInput);
        Integer close = parseInt(closeTimerInput);
        Integer maxParty = parseInt(maxPartyInput);
        Integer invite = parseInt(inviteExpiryInput);
        Integer respawn = parseInt(respawnTimeInput);
        Integer death = parseInt(deathPenaltyInput);
        Integer hpScalePct = parseInt(hpScaleInput);
        if (cooldown == null || close == null || maxParty == null || invite == null || respawn == null || death == null || hpScalePct == null) {
            footerError = Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value").getString();
            rebuildUi();
            return;
        }

        footerError = null;
        // Only what this admin changed: a field another admin saved meanwhile is left alone.
        drafts.setAll(inputTexts());
        BlockPos pos = menu.getBlockPos();
        List<String> sent = new ArrayList<>();
        if (drafts.dirty("general.name") || drafts.dirty("general.id")) {
            ArenasNetwork.sendToServer(new net.ledok.arenas_ld.packet.SetActivityInfoPayload(pos,
                activityIdInput == null ? "" : activityIdInput, nameInput == null ? "" : nameInput));
            sent.add("general.name");
            sent.add("general.id");
        }
        sendIfDirty(sent, "general.cooldown", () -> new SetCooldownTicksPayload(pos, cooldown * 20));
        sendIfDirty(sent, "general.close_timer", () -> new SetCloseTimerSecondsPayload(pos, close));
        sendIfDirty(sent, "general.max_party", () -> new SetMaxPartySizePayload(pos, maxParty));
        sendIfDirty(sent, "general.invite_expiry", () -> new SetInviteExpiryTicksPayload(pos, invite * 20));
        sendIfDirty(sent, "general.respawn_time", () -> new SetRespawnTimeTicksPayload(pos, respawn));
        sendIfDirty(sent, "general.death_penalty", () -> new SetDeathTimePenaltyPayload(pos, death * 20));
        sendIfDirty(sent, "general.hp_scale",
            () -> new net.ledok.arenas_ld.dungeon.packet.SetHpScalePerPlayerPayload(pos, hpScalePct / 100.0));
        // One snapshot per packet (the activity info packet covers two fields).
        int packets = sent.size() - (sent.contains("general.id") ? 1 : 0);
        drafts.saved(sent, packets);
        rebuildUi();
    }

    private void sendIfDirty(List<String> sent, String key,
                             java.util.function.Supplier<net.minecraft.network.protocol.common.custom.CustomPacketPayload> payload) {
        if (drafts.dirty(key)) {
            ArenasNetwork.sendToServer(payload.get());
            sent.add(key);
        }
    }

    private void applyTier(DifficultyTier tier) {
        TierConfig current = tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier));
        Double health = parseDouble(healthInputs.getOrDefault(tier, Double.toString(current.healthMultiplier())));
        Double damage = parseDouble(damageInputs.getOrDefault(tier, Double.toString(current.damageMultiplier())));
        Integer time = parseInt(timeInputs.getOrDefault(tier, Integer.toString(current.dungeonTimeSeconds())));
        String loot = lootInputs.getOrDefault(tier, current.perPlayerLootTable());
        Long reward = parseLong(rewardInputs.getOrDefault(tier, Long.toString(current.rewardCurrency())));
        Integer xp = parseInt(xpInputs.getOrDefault(tier, Integer.toString(current.skillExperiencePerWin())));

        if (health == null || damage == null || time == null || reward == null || xp == null) {
            footerError = Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value").getString();
            rebuildUi();
            return;
        }

        boolean enabled = enabledInputs.getOrDefault(tier, current.enabled());
        TierConfig updated = new TierConfig(health, damage, loot, time, enabled, Math.max(0L, reward), Math.max(0, xp),
            commandInputs.getOrDefault(tier, current.rewardCommands()));
        footerError = null;
        ArenasNetwork.sendToServer(new SetTierConfigPayload(menu.getBlockPos(), tier, updated));
        drafts.setAll(inputTexts());
        drafts.saved(drafts.values().keySet().stream().filter(k -> k.startsWith(tier.name() + ".")).toList(), 1);
        rebuildUi();
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        int rem = seconds % 60;
        return String.format("%d:%02d", minutes, rem);
    }

    /** Instances and their runs and cooldowns: what changes while nobody edits. */
    private void syncStateFromMenu() {
        knownLootTableIds = new ArrayList<>(menu.getKnownLootTableIds());
        tierConfigs.clear();
        tierConfigs.putAll(menu.getTierConfigs());

        instances.clear();
        instances.addAll(menu.getInstances());
        activeRuns.clear();
        activeRuns.addAll(menu.getActiveRunInstances());
        cooldowns.clear();
        cooldowns.putAll(menu.getInstanceCooldownTimers());
        cooldownSnapshotEpochMs = System.currentTimeMillis();
        pendingRemovals.clear();
        pendingRemovals.addAll(menu.getPendingRemovals());
        runningInstances.clear();
        runningInstances.putAll(menu.getRunningInstances());
    }

    /** Every settings field as the server has it, in the text the inputs show. */
    private Map<String, String> serverTexts() {
        Map<String, String> texts = new HashMap<>();
        texts.put("general.name", menu.getDungeonName());
        texts.put("general.id", menu.getActivityId());
        texts.put("general.cooldown", Integer.toString(menu.getCooldownTicks() / 20));
        texts.put("general.close_timer", Integer.toString(menu.getCloseTimerSeconds()));
        texts.put("general.max_party", Integer.toString(menu.getMaxPartySize()));
        texts.put("general.invite_expiry", Integer.toString(menu.getInviteExpiryTicks() / 20));
        texts.put("general.respawn_time", Integer.toString(menu.getRespawnTimeTicks()));
        texts.put("general.death_penalty", Integer.toString(menu.getDeathTimePenaltyTicks() / 20));
        texts.put("general.hp_scale", Integer.toString((int) Math.round(menu.getHpScalePerPlayer() * 100.0)));
        for (DifficultyTier tier : DifficultyTier.values()) {
            TierConfig config = menu.getTierConfigs().getOrDefault(tier, TierConfig.defaultFor(tier));
            String p = tier.name() + ".";
            texts.put(p + "health", trimDouble(config.healthMultiplier()));
            texts.put(p + "damage", trimDouble(config.damageMultiplier()));
            texts.put(p + "loot", config.perPlayerLootTable());
            texts.put(p + "time", Integer.toString(config.dungeonTimeSeconds()));
            texts.put(p + "reward", Long.toString(config.rewardCurrency()));
            texts.put(p + "xp", Integer.toString(config.skillExperiencePerWin()));
            texts.put(p + "enabled", Boolean.toString(config.enabled()));
            texts.put(p + "commands", AdminDrafts.joinLines(config.rewardCommands()));
        }
        return texts;
    }

    /** What the inputs hold now, keyed like {@link #serverTexts}. */
    private Map<String, String> inputTexts() {
        Map<String, String> texts = new HashMap<>();
        texts.put("general.name", nameInput == null ? "" : nameInput);
        texts.put("general.id", activityIdInput == null ? "" : activityIdInput);
        texts.put("general.cooldown", cooldownInput);
        texts.put("general.close_timer", closeTimerInput);
        texts.put("general.max_party", maxPartyInput);
        texts.put("general.invite_expiry", inviteExpiryInput);
        texts.put("general.respawn_time", respawnTimeInput);
        texts.put("general.death_penalty", deathPenaltyInput);
        texts.put("general.hp_scale", hpScaleInput);
        for (DifficultyTier tier : DifficultyTier.values()) {
            String p = tier.name() + ".";
            texts.put(p + "health", healthInputs.get(tier));
            texts.put(p + "damage", damageInputs.get(tier));
            texts.put(p + "loot", lootInputs.get(tier));
            texts.put(p + "time", timeInputs.get(tier));
            texts.put(p + "reward", rewardInputs.get(tier));
            texts.put(p + "xp", xpInputs.get(tier));
            texts.put(p + "enabled", String.valueOf(enabledInputs.get(tier)));
            texts.put(p + "commands", AdminDrafts.joinLines(commandInputs.getOrDefault(tier, List.of())));
        }
        texts.values().removeIf(java.util.Objects::isNull);
        return texts;
    }

    /** Puts the drafts back into the inputs (the commands lists in place: an open editor holds them). */
    private void loadInputs() {
        nameInput = drafts.get("general.name");
        activityIdInput = drafts.get("general.id");
        cooldownInput = drafts.get("general.cooldown");
        closeTimerInput = drafts.get("general.close_timer");
        maxPartyInput = drafts.get("general.max_party");
        inviteExpiryInput = drafts.get("general.invite_expiry");
        respawnTimeInput = drafts.get("general.respawn_time");
        deathPenaltyInput = drafts.get("general.death_penalty");
        hpScaleInput = drafts.get("general.hp_scale");
        for (DifficultyTier tier : DifficultyTier.values()) {
            String p = tier.name() + ".";
            healthInputs.put(tier, drafts.get(p + "health"));
            damageInputs.put(tier, drafts.get(p + "damage"));
            lootInputs.put(tier, drafts.get(p + "loot"));
            timeInputs.put(tier, drafts.get(p + "time"));
            rewardInputs.put(tier, drafts.get(p + "reward"));
            xpInputs.put(tier, drafts.get(p + "xp"));
            enabledInputs.put(tier, Boolean.parseBoolean(drafts.get(p + "enabled")));
            List<String> commands = commandInputs.computeIfAbsent(tier, t -> new ArrayList<>());
            commands.clear();
            commands.addAll(AdminDrafts.splitLines(drafts.get(p + "commands")));
        }
    }

    /** Whether a snapshot is for this screen's controller: same position and dimension. */
    public boolean matchesController(BlockPos blockPos, ResourceKey<Level> dimension) {
        return menu.getBlockPos().equals(blockPos) && menu.getDimension().equals(dimension);
    }

    public void applyData(DungeonControllerAdminData data) {
        // Run starts, run ends and cooldowns send snapshots too. Settings merge field by field:
        // what nobody edits here follows the server, unsaved edits stay (see AdminDrafts).
        menu.applyData(data);
        syncStateFromMenu();
        if (!synced) {
            return; // buildAll takes everything at once
        }
        drafts.setAll(inputTexts());
        List<String> changed = drafts.merge(serverTexts());
        loadInputs();
        String notice = AdminEditState.notice(menu.getLastEditor(), changed, tabNames());
        if (notice != null) {
            footerNotice = notice;
        }
        String tabPrefix = prefix(currentTab);
        if (currentTab == Tab.INSTANCES || (tabPrefix != null && AdminDrafts.any(changed, tabPrefix))) {
            rebuildUi();
        } else {
            updateTabLabels(); // leaves the open tab's fields (and their focus) alone
            refreshFooter();
        }
    }

    /** Draft key prefix → tab name, for the "saved by" notice. */
    private static Map<String, Component> tabNames() {
        Map<String, Component> names = new java.util.LinkedHashMap<>();
        for (Tab tab : Tab.values()) {
            String p = prefix(tab);
            if (p != null) {
                names.put(p, Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab." + tab.name().toLowerCase(java.util.Locale.ROOT)));
            }
        }
        return names;
    }
}
