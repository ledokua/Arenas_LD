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
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.raid.packet.RaidSetMaxPartySizePayload;
import net.ledok.arenas_ld.raid.packet.RaidSetRespawnTimeTicksPayload;
import net.ledok.arenas_ld.raid.packet.RaidMoveInstancePayload;
import net.ledok.arenas_ld.raid.packet.RaidRemoveInstancePayload;
import net.ledok.arenas_ld.raid.packet.RaidSetCloseTimerSecondsPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetCooldownTicksPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetInviteExpiryTicksPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetDeathTimePenaltyPayload;
import net.ledok.arenas_ld.raid.packet.RaidSetLootViaInboxPayload;
import net.ledok.arenas_ld.screen.IdSuggestionDropdown;
import net.ledok.arenas_ld.raid.packet.RaidSetTierConfigPayload;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.ledok.arenas_ld.util.InstanceStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class RaidControllerAdminScreen extends BaseOwoHandledScreen<FlowLayout, RaidControllerAdminScreenHandler> {
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
    private static final int DROPDOWN_MAX_CANDIDATES = 50;
    private static final int DROPDOWN_MAX_HEIGHT = 140;

    private enum Tab {
        INSTANCES,
        GENERAL,
        EASY,
        NORMAL,
        HARD,
        NIGHTMARE
    }

    private final List<RaidControllerAdminData.InstanceEntry> instances = new ArrayList<>();
    private final Set<BlockPos> pendingRemovals = new HashSet<>();
    private final Map<BlockPos, RaidControllerAdminData.InstanceRun> runningInstances = new HashMap<>();
    private final Map<BlockPos, FlowLayout> statusBadges = new HashMap<>();
    private final Map<DifficultyTier, RaidTierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);

    private Tab currentTab = Tab.INSTANCES;
    private String footerError;
    private long lastCooldownSecond = -1L;
    private long cooldownSnapshotEpochMs = System.currentTimeMillis();

    private FlowLayout contentArea;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;

    private ButtonComponent instancesTabButton;
    private ButtonComponent generalTabButton;
    private ButtonComponent easyTabButton;
    private ButtonComponent normalTabButton;
    private ButtonComponent hardTabButton;
    private ButtonComponent nightmareTabButton;

    private List<String> knownLootTableIds = List.of();
    private boolean lootDropdownOpen = false;
    private FlowLayout lootDropdownPanel;
    private TextBoxComponent currentLootField;
    private Consumer<String> currentLootOnChange;

    private String nameInput;
    private String cooldownInput;
    private String closeTimerInput;
    private String maxPartyInput;
    private String inviteExpiryInput;
    private String respawnTimeInput;
    private String deathPenaltyInput;
    private boolean lootViaInboxInput;

    private final Map<DifficultyTier, String> healthInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> damageInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> lootInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> timeInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> rewardInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> xpInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> regenInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> hpScaleInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, Boolean> enabledInputs = new EnumMap<>(DifficultyTier.class);

    public RaidControllerAdminScreen(RaidControllerAdminScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.inventoryLabelY = 9999;
        this.titleLabelY = 9999;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth = Math.max(500, Math.min(620, this.width - 24));
        int shellHeight = Math.max(300, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));

        shell.child(buildHeader());
        shell.child(buildTabs());

        contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        contentArea.surface(Surface.flat(PANEL));
        contentArea.padding(Insets.of(10));
        contentArea.gap(4);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), contentArea);
        scroll.surface(Surface.flat(PANEL));
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        scroll.scrollbarThiccness(8);
        scroll.fixedScrollbarLength(28);
        scroll.scrollStep(18);
        shell.child(scroll);

        shell.child(buildFooter());
        rootComponent.child(shell);

        syncFromMenu();
        rebuildUi();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (currentTab == Tab.INSTANCES) {
            long second = System.currentTimeMillis() / 1000L;
            if (second != lastCooldownSecond) {
                lastCooldownSecond = second;
                refreshCooldownsInPlace();
            }
        }
    }

    private void refreshCooldownsInPlace() {
        for (RaidControllerAdminData.InstanceEntry entry : instances) {
            BlockPos pos = entry.spawnerPos();
            FlowLayout badge = statusBadges.get(pos);
            if (badge == null) continue;
            if (pendingRemovals.contains(pos) || entry.status() == InstanceStatus.RUNNING) {
                continue;
            }
            InstanceStatusInfo info = instanceStatusInfo(entry);
            updateBadge(badge, info.label(), info.color());
        }
    }

    private void updateBadge(FlowLayout badge, String text, int color) {
        badge.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        if (!badge.children().isEmpty() && badge.children().get(0) instanceof LabelComponent label) {
            label.text(Component.literal(text));
            label.color(Color.ofArgb(color));
        }
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(42));
        header.surface(Surface.flat(PANEL_2));
        header.padding(Insets.of(6));
        header.gap(8);

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(18), Sizing.fixed(18));
        mark.surface(Surface.flat(WARN));
        header.child(mark);

        FlowLayout info = Containers.verticalFlow(Sizing.content(), Sizing.content());
        info.gap(3);

        FlowLayout titleLine = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        titleLine.gap(8);
        titleLine.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent titleLabel = Components.label(Component.translatable("gui.arenas_ld.raid_controller_admin.title"));
        titleLabel.color(Color.ofArgb(INK));
        titleLine.child(titleLabel);
        titleLine.child(badge(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.op"), WARN));
        info.child(titleLine);

        FlowLayout meta = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        meta.gap(10);
        meta.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        meta.child(smallMeta("POS · X " + menu.getBlockPos().getX() + " · Y " + menu.getBlockPos().getY() + " · Z " + menu.getBlockPos().getZ()));
        FlowLayout live = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        live.gap(4);
        live.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent liveDot = Components.label(Component.literal("●"));
        liveDot.color(Color.ofArgb(GOOD));
        live.child(liveDot);
        live.child(smallMeta("LIVE", GOOD));
        meta.child(live);
        info.child(meta);
        header.child(info);

        header.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        ButtonComponent close = smallButton(Component.literal("X"), b -> onClose());
        close.sizing(Sizing.fixed(20), Sizing.fixed(16));
        header.child(close);
        return header;
    }

    private FlowLayout buildTabs() {
        FlowLayout tabs = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        tabs.surface(Surface.flat(PANEL_2));
        tabs.padding(Insets.of(4));
        tabs.gap(4);

        instancesTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.instances", Tab.INSTANCES, 76);
        generalTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.general", Tab.GENERAL, 72);
        easyTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.easy", Tab.EASY, 56);
        normalTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.normal", Tab.NORMAL, 64);
        hardTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.hard", Tab.HARD, 56);
        nightmareTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.nightmare", Tab.NIGHTMARE, 80);

        tabs.child(instancesTabButton);
        tabs.child(generalTabButton);
        tabs.child(easyTabButton);
        tabs.child(normalTabButton);
        tabs.child(hardTabButton);
        tabs.child(nightmareTabButton);
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

    private ButtonComponent tabButton(String key, Tab tab, int width) {
        ButtonComponent button = Components.button(Component.translatable(key), b -> {
            currentTab = tab;
            footerError = null;
            rebuildUi();
        });
        button.sizing(Sizing.fixed(width), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            boolean active = !rendered.active();
            int fill = active ? PANEL : (rendered.isHoveredOrFocused() ? ROW_BG : PANEL_2);
            int border = active ? ACCENT : HAIRLINE;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return button;
    }

    private void rebuildUi() {
        lootDropdownOpen = false;
        lootDropdownPanel = null;
        currentLootField = null;
        currentLootOnChange = null;

        contentArea.clearChildren();
        footerActions.clearChildren();

        instancesTabButton.active(currentTab != Tab.INSTANCES);
        generalTabButton.active(currentTab != Tab.GENERAL);
        easyTabButton.active(currentTab != Tab.EASY);
        normalTabButton.active(currentTab != Tab.NORMAL);
        hardTabButton.active(currentTab != Tab.HARD);
        nightmareTabButton.active(currentTab != Tab.NIGHTMARE);
        instancesTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.instances").append(" [" + instances.size() + "]"));

        switch (currentTab) {
            case INSTANCES -> buildInstancesTab();
            case GENERAL -> buildGeneralTab();
            case EASY -> buildTierTab(DifficultyTier.EASY);
            case NORMAL -> buildTierTab(DifficultyTier.NORMAL);
            case HARD -> buildTierTab(DifficultyTier.HARD);
            case NIGHTMARE -> buildTierTab(DifficultyTier.NIGHTMARE);
        }

        footerLabel.text(footerError == null ? Component.empty() : Component.literal(footerError));
    }

    private void buildInstancesTab() {
        statusBadges.clear();
        int running = 0;
        int idleCooldown = 0;
        for (RaidControllerAdminData.InstanceEntry entry : instances) {
            if (pendingRemovals.contains(entry.spawnerPos())) {
                continue;
            }
            if (entry.status() == InstanceStatus.RUNNING) {
                running++;
            } else {
                idleCooldown++;
            }
        }

        contentArea.child(instancesSummaryBar(running, instances.size(), idleCooldown));

        if (instances.isEmpty()) {
            FlowLayout empty = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            empty.surface(Surface.flat(ROW_BG));
            empty.padding(Insets.of(10));
            empty.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
            empty.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller_admin.use_linker_hint")));
            contentArea.child(empty);
            return;
        }

        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.child(instanceHeaderRow());
        list.child(rowDivider(HAIRLINE_HI));
        for (int i = 0; i < instances.size(); i++) {
            if (i > 0) {
                list.child(rowDivider(HAIRLINE));
            }
            list.child(instanceRow(i, instances.get(i)));
        }
        contentArea.child(list);
    }

    private FlowLayout instancesSummaryBar(int running, int total, int idleCooldown) {
        FlowLayout bar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        bar.surface(Surface.flat(ROW_BG).and(Surface.outline(HAIRLINE)));
        bar.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(3), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        bar.child(accent);

        FlowLayout inner = Containers.horizontalFlow(Sizing.expand(), Sizing.fill(100));
        inner.padding(Insets.of(0, 0, 12, 10));
        inner.gap(0);
        inner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent activeValue = Components.label(Component.literal(running + " / " + total));
        activeValue.color(Color.ofArgb(running > 0 ? GOOD : INK));
        inner.child(summaryColumn(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.active_runs"), activeValue, Sizing.fixed(120)));
        inner.child(summaryColumn(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.idle_cooldown"), labelLiteral(Integer.toString(idleCooldown), INK), Sizing.fixed(140)));

        FlowLayout hint = Containers.horizontalFlow(Sizing.expand(), Sizing.content());
        hint.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        hint.gap(3);
        hint.child(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.hint_pre"), INK_DIM));
        hint.child(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.hint_link"), ACCENT));
        hint.child(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.summary.hint_post"), INK_DIM));
        inner.child(hint);
        bar.child(inner);
        return bar;
    }

    private FlowLayout summaryColumn(String caption, LabelComponent value, Sizing width) {
        FlowLayout col = Containers.verticalFlow(width, Sizing.content());
        col.gap(3);
        col.child(labelLiteral(caption, INK_DIM));
        col.child(value);
        return col;
    }

    private FlowLayout instanceRow(int index, RaidControllerAdminData.InstanceEntry entry) {
        BlockPos pos = entry.spawnerPos();
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(ROW_BG));
        row.padding(Insets.of(7, 7, 8, 8));
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent rank = labelLiteral(Integer.toString(index + 1), INK_MID);
        rank.horizontalSizing(Sizing.fixed(22));
        row.child(rank);

        FlowLayout posCol = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        posCol.gap(1);
        posCol.child(labelLiteral("X " + pos.getX() + " · Y " + pos.getY() + " · Z " + pos.getZ(), INK));
        posCol.child(labelLiteral(entry.dimension(), INK_DIM));
        row.child(posCol);

        InstanceStatusInfo status = instanceStatusInfo(entry);
        FlowLayout statusBadge = fixedBadge(Component.literal(status.label()), 120, status.color());
        statusBadges.put(pos, statusBadge);
        row.child(statusBadge);

        FlowLayout tierParty = Containers.verticalFlow(Sizing.fixed(120), Sizing.content());
        tierParty.gap(1);
        RaidControllerAdminData.InstanceRun run = runningInstances.get(pos);
        if (run != null) {
            tierParty.child(labelLiteral(titleCase(run.tier().name()), tierColor(run.tier())));
            String party = run.party() == null || run.party().isEmpty()
                ? tr("gui.arenas_ld.dungeon_controller_admin.ui.party_fallback")
                : Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.party_suffix", run.party()).getString();
            tierParty.child(labelLiteral(party, INK_MID));
        } else {
            tierParty.child(labelLiteral("—", INK_DIM));
        }
        row.child(tierParty);

        ButtonComponent up = smallButton(Component.literal("↑"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidMoveInstancePayload(menu.getBlockPos(), index, Math.max(0, index - 1)));
        });
        up.sizing(Sizing.fixed(22), Sizing.fixed(18));
        up.active(index > 0);
        row.child(up);

        ButtonComponent down = smallButton(Component.literal("↓"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RaidMoveInstancePayload(menu.getBlockPos(), index, Math.min(instances.size() - 1, index + 1)));
        });
        down.sizing(Sizing.fixed(22), Sizing.fixed(18));
        down.active(index < instances.size() - 1);
        row.child(down);

        boolean pending = pendingRemovals.contains(pos);
        String dimension = entry.dimension();
        ButtonComponent action;
        if (pending) {
            action = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.action.cancel"), b -> {
                footerError = null;
                ClientPlayNetworking.send(new RaidRemoveInstancePayload(menu.getBlockPos(), pos, dimension));
            });
        } else {
            action = dangerButton(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.action.remove"), b -> {
                footerError = null;
                ClientPlayNetworking.send(new RaidRemoveInstancePayload(menu.getBlockPos(), pos, dimension));
            });
        }
        action.horizontalSizing(Sizing.fixed(64));
        row.child(action);
        return row;
    }

    private record InstanceStatusInfo(String label, int color) {}

    private InstanceStatusInfo instanceStatusInfo(RaidControllerAdminData.InstanceEntry entry) {
        BlockPos pos = entry.spawnerPos();
        if (pendingRemovals.contains(pos)) {
            return new InstanceStatusInfo(tr("gui.arenas_ld.dungeon_controller_admin.ui.status.pending_removal"), WARN);
        }
        if (entry.status() == InstanceStatus.RUNNING) {
            return new InstanceStatusInfo(tr("gui.arenas_ld.dungeon_controller_admin.ui.status.running"), GOOD);
        }
        if (entry.status() == InstanceStatus.COOLDOWN) {
            int liveTicks = liveCooldownTicks(entry.cooldownTicksRemaining());
            if (liveTicks > 0) {
                return new InstanceStatusInfo(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.status.cooldown", formatTicks(liveTicks)).getString(), DANGER);
            }
        }
        return new InstanceStatusInfo(tr("gui.arenas_ld.dungeon_controller_admin.ui.status.idle"), GOOD);
    }

    // Counts down locally between snapshots so the cooldown ticks live without a server push.
    private int liveCooldownTicks(int snapshotTicks) {
        if (snapshotTicks <= 0) {
            return 0;
        }
        int elapsedTicks = (int) ((System.currentTimeMillis() - cooldownSnapshotEpochMs) / 50L);
        return Math.max(0, snapshotTicks - elapsedTicks);
    }

    private void buildGeneralTab() {
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.general.section"), null));
        contentArea.child(spacer(2));
        contentArea.child(nameFieldRow());
        contentArea.child(spacer(8));
        contentArea.child(twoColumnRow(
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.cooldown"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.cooldown_hint"), "S", 5, 0, 86400, cooldownInput, v -> cooldownInput = v),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.close_timer"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.close_timer_hint"), "S", 5, 0, 86400, closeTimerInput, v -> closeTimerInput = v)
        ));
        contentArea.child(spacer(8));
        contentArea.child(twoColumnRow(
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.max_party"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.max_party_hint"), "P", 1, 1, 64, maxPartyInput, v -> maxPartyInput = v),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.invite_expiry"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.invite_expiry_hint"), "S", 5, 1, 86400, inviteExpiryInput, v -> inviteExpiryInput = v)
        ));
        contentArea.child(spacer(8));
        contentArea.child(twoColumnRow(
            stepperField(tr("gui.arenas_ld.raid_controller_admin.ui.general.respawn_time"), tr("gui.arenas_ld.raid_controller_admin.ui.general.respawn_time_hint"), "T", 20, 0, 1_200_000, respawnTimeInput, v -> respawnTimeInput = v),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.death_penalty"), tr("gui.arenas_ld.dungeon_controller_admin.ui.general.death_penalty_hint"), "S", 1, 0, 600, deathPenaltyInput, v -> deathPenaltyInput = v)
        ));
        contentArea.child(spacer(10));

        contentArea.child(togglePanel(
            tr("gui.arenas_ld.dungeon_controller_admin.ui.general.loot_via_inbox"),
            tr("gui.arenas_ld.dungeon_controller_admin.ui.general.loot_via_inbox_desc"),
            lootViaInboxInput ? tr("gui.arenas_ld.dungeon_controller_admin.ui.general.loot_via_inbox_on") : tr("gui.arenas_ld.dungeon_controller_admin.ui.general.loot_via_inbox_off"),
            lootViaInboxInput ? INFO : INK_DIM,
            INFO,
            () -> lootViaInboxInput,
            () -> {
                lootViaInboxInput = !lootViaInboxInput;
                ClientPlayNetworking.send(new RaidSetLootViaInboxPayload(menu.getBlockPos(), lootViaInboxInput));
                rebuildUi();
            }
        ));
        contentArea.child(spacer(10));

        FlowLayout applyRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        applyRow.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        ButtonComponent apply = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.general.apply"), b -> applyGeneral());
        apply.horizontalSizing(Sizing.fixed(120));
        applyRow.child(apply);
        contentArea.child(applyRow);
    }

    private FlowLayout twoColumnRow(FlowLayout left, FlowLayout right) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        FlowLayout c0 = Containers.verticalFlow(Sizing.fill(50), Sizing.content());
        c0.padding(Insets.of(0, 0, 0, 5));
        c0.child(left);
        FlowLayout c1 = Containers.verticalFlow(Sizing.fill(50), Sizing.content());
        c1.padding(Insets.of(0, 0, 5, 0));
        c1.child(right);
        row.child(c0);
        row.child(c1);
        return row;
    }

    private FlowLayout nameFieldRow() {
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        head.child(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.general.name"), INK_DIM));
        head.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        LabelComponent hintLabel = Components.label(
            Component.literal(tr("gui.arenas_ld.raid_controller_admin.ui.general.name_hint")).withStyle(net.minecraft.ChatFormatting.ITALIC));
        hintLabel.color(Color.ofArgb(INK_DIM));
        head.child(hintLabel);
        col.child(head);

        FlowLayout fieldWrap = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldWrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldWrap.child(accent);
        TextBoxComponent field = Components.textBox(Sizing.expand(), nameInput == null ? "" : nameInput);
        field.verticalSizing(Sizing.fixed(18));
        field.setMaxLength(48);
        field.onChanged().subscribe(v -> nameInput = v);
        fieldWrap.child(field);
        col.child(fieldWrap);
        return col;
    }

    private FlowLayout stepperField(String caption, String hint, String unit, int step, int min, int max,
                                    String initial, java.util.function.Consumer<String> onChange) {
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        head.child(labelLiteral(caption, INK_DIM));
        head.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        LabelComponent hintLabel = Components.label(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.ITALIC));
        hintLabel.color(Color.ofArgb(INK_DIM));
        head.child(hintLabel);
        col.child(head);

        FlowLayout stepper = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        stepper.gap(6);
        stepper.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout fieldWrap = Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(22));
        fieldWrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldWrap.child(accent);
        TextBoxComponent field = Components.textBox(Sizing.expand(), initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(onChange::accept);
        fieldWrap.child(field);
        FlowLayout unitCell = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        unitCell.padding(Insets.of(0, 0, 6, 6));
        unitCell.child(labelLiteral(unit, INK_DIM));
        fieldWrap.child(unitCell);

        stepper.child(stepButton("-", () -> stepValue(field, -step, min, max, onChange)));
        stepper.child(fieldWrap);
        stepper.child(stepButton("+", () -> stepValue(field, step, min, max, onChange)));
        col.child(stepper);
        return col;
    }

    private ButtonComponent stepButton(String glyph, Runnable action) {
        ButtonComponent button = Components.button(Component.literal(glyph), b -> action.run());
        button.sizing(Sizing.fixed(34), Sizing.fixed(22));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ROW_BG : PANEL_2;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), HAIRLINE);
        });
        return button;
    }

    private void stepValue(TextBoxComponent field, int delta, int min, int max, java.util.function.Consumer<String> onChange) {
        int current;
        try {
            current = Integer.parseInt(field.getValue().trim());
        } catch (Exception ignored) {
            current = min;
        }
        int next = Math.max(min, Math.min(max, current + delta));
        String value = Integer.toString(next);
        field.text(value);
        onChange.accept(value);
    }

    private void buildTierTab(DifficultyTier tier) {
        RaidTierConfig config = tierConfigs.getOrDefault(tier, RaidTierConfig.defaultFor(tier));
        String tierName = titleCase(tier.name());
        boolean enabled = enabledInputs.getOrDefault(tier, config.enabled());

        contentArea.child(tierBanner(tier, tierName));
        contentArea.child(spacer(4));

        contentArea.child(togglePanel(
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

        contentArea.child(spacer(6));
        contentArea.child(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.parameters"), INK_DIM));
        contentArea.child(spacer(2));

        contentArea.child(twoColumnRow(
            stepperFieldDouble(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.health"), tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.health_hint"), "×", 0.25, 0.0, 100.0,
                healthInputs.getOrDefault(tier, trimDouble(config.healthMultiplier())), v -> healthInputs.put(tier, v)),
            stepperFieldDouble(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.damage"), tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.damage_hint"), "×", 0.25, 0.0, 100.0,
                damageInputs.getOrDefault(tier, trimDouble(config.damageMultiplier())), v -> damageInputs.put(tier, v))
        ));

        contentArea.child(spacer(6));
        contentArea.child(twoColumnRow(
            lootField(lootInputs.getOrDefault(tier, config.perPlayerLootTable()), v -> lootInputs.put(tier, v)),
            stepperField(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.time"), tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.time_hint"), "S", 30, 1, 86400,
                timeInputs.getOrDefault(tier, Integer.toString(config.raidTimeSeconds())), v -> timeInputs.put(tier, v))
        ));

        contentArea.child(spacer(6));
        contentArea.child(twoColumnRow(
            stepperField(tr("gui.arenas_ld.raid_controller_admin.ui.tier.xp"), tr("gui.arenas_ld.raid_controller_admin.ui.tier.xp_hint"), "XP", 10, 0, 1_000_000,
                xpInputs.getOrDefault(tier, Integer.toString(config.skillExperiencePerWin())), v -> xpInputs.put(tier, v)),
            stepperField(tr("gui.arenas_ld.raid_controller_admin.ui.tier.regen"), tr("gui.arenas_ld.raid_controller_admin.ui.tier.regen_hint"), "HP", 1, 0, 1_000_000,
                regenInputs.getOrDefault(tier, Integer.toString(config.regeneration())), v -> regenInputs.put(tier, v))
        ));

        contentArea.child(spacer(6));
        contentArea.child(twoColumnRow(
            stepperFieldDouble(tr("gui.arenas_ld.raid_controller_admin.ui.tier.hp_scale_per_player"), tr("gui.arenas_ld.raid_controller_admin.ui.tier.hp_scale_per_player_hint"), "×", 0.05, -0.99, 10.0,
                hpScaleInputs.getOrDefault(tier, trimDouble(config.hpScalePerPlayer())), v -> hpScaleInputs.put(tier, v)),
            rewardCurrencyField(tier, config)
        ));

        contentArea.child(spacer(8));
        FlowLayout applyRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        applyRow.alignment(HorizontalAlignment.RIGHT, VerticalAlignment.CENTER);
        ButtonComponent apply = smallButton(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.apply", tier.name()), b -> applyTier(tier));
        apply.horizontalSizing(Sizing.fixed(150));
        applyRow.child(apply);
        contentArea.child(applyRow);
    }

    private FlowLayout tierBanner(DifficultyTier tier, String tierName) {
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
        inner.child(badge(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.banner_badge", tier.name()), color));
        inner.child(labelLiteral(Component.translatable("gui.arenas_ld.dungeon_controller_admin.ui.tier.banner_desc", tierName).getString(), INK));
        banner.child(inner);
        return banner;
    }

    private FlowLayout togglePanel(String caption, String description, String stateLabel, int stateColor, int onColor,
                                   java.util.function.BooleanSupplier on, Runnable onToggle) {
        FlowLayout panel = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        panel.surface(Surface.flat(ROW_BG).and(Surface.outline(HAIRLINE)));
        panel.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(onColor));
        panel.child(accent);

        FlowLayout inner = Containers.horizontalFlow(Sizing.expand(), Sizing.fill(100));
        inner.padding(Insets.of(6, 6, 10, 10));
        inner.gap(8);
        inner.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout text = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        text.gap(2);
        text.child(labelLiteral(caption, INK_DIM));
        text.child(labelLiteral(description, INK));
        inner.child(text);

        if (stateLabel != null) {
            inner.child(labelLiteral(stateLabel, stateColor));
        }
        inner.child(toggleSwitch(onColor, on, onToggle));
        panel.child(inner);
        return panel;
    }

    private ButtonComponent toggleSwitch(int onColor, java.util.function.BooleanSupplier on, Runnable onToggle) {
        ButtonComponent button = Components.button(Component.empty(), b -> onToggle.run());
        button.sizing(Sizing.fixed(38), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            boolean isOn = on.getAsBoolean();
            int x1 = rendered.getX();
            int y1 = rendered.getY();
            int w = rendered.getWidth();
            int h = rendered.getHeight();
            int track = isOn ? ((onColor & 0x00FFFFFF) | 0x55000000) : PANEL_2;
            int border = isOn ? onColor : HAIRLINE;
            context.fill(x1, y1, x1 + w, y1 + h, track);
            context.drawRectOutline(x1, y1, w, h, border);
            int knobW = w / 2 - 3;
            int knobX = isOn ? (x1 + w - knobW - 2) : (x1 + 2);
            int knobColor = isOn ? onColor : INK;
            context.fill(knobX, y1 + 2, knobX + knobW, y1 + h - 2, knobColor);
        });
        return button;
    }

    private FlowLayout rewardCurrencyField(DifficultyTier tier, RaidTierConfig config) {
        String initial = rewardInputs.getOrDefault(tier, Long.toString(config.rewardCurrency()));
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        head.child(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.reward"), INK_DIM));
        head.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        LabelComponent hintLabel = Components.label(Component.literal(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.reward_hint")).withStyle(net.minecraft.ChatFormatting.ITALIC));
        hintLabel.color(Color.ofArgb(INK_DIM));
        head.child(hintLabel);
        col.child(head);

        FlowLayout stepper = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        stepper.gap(6);
        stepper.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout fieldWrap = Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(22));
        fieldWrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldWrap.child(accent);
        TextBoxComponent field = Components.textBox(Sizing.expand(), initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(v -> rewardInputs.put(tier, v));
        fieldWrap.child(field);
        FlowLayout unitCell = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        unitCell.padding(Insets.of(0, 0, 6, 6));
        unitCell.child(labelLiteral("LC", INK_DIM));
        fieldWrap.child(unitCell);

        stepper.child(stepButton("-", () -> stepLong(field, -10L, 0L, 1_000_000_000L, v -> rewardInputs.put(tier, v))));
        stepper.child(fieldWrap);
        stepper.child(stepButton("+", () -> stepLong(field, 10L, 0L, 1_000_000_000L, v -> rewardInputs.put(tier, v))));
        col.child(stepper);
        return col;
    }

    private void stepLong(TextBoxComponent field, long delta, long min, long max, java.util.function.Consumer<String> onChange) {
        long current;
        try {
            current = Long.parseLong(field.getValue().trim());
        } catch (Exception ignored) {
            current = min;
        }
        long next = Math.max(min, Math.min(max, current + delta));
        String value = Long.toString(next);
        field.text(value);
        onChange.accept(value);
    }

    private FlowLayout lootField(String initial, Consumer<String> onChange) {
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);
        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        head.child(labelLiteral(tr("gui.arenas_ld.dungeon_controller_admin.ui.tier.loot"), INK_DIM));
        col.child(head);

        FlowLayout fieldRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldRow.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldRow.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        fieldRow.gap(4);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldRow.child(accent);
        FlowLayout idCell = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        idCell.padding(Insets.of(0, 0, 4, 0));
        idCell.child(labelLiteral("id:", INK_DIM));
        fieldRow.child(idCell);

        TextBoxComponent field = Components.textBox(Sizing.expand(), initial);
        field.verticalSizing(Sizing.fixed(18));
        field.setMaxLength(256);
        IdSuggestionDropdown.attachFullValueTooltip(field);
        field.onChanged().subscribe(v -> {
            onChange.accept(v);
            if (lootDropdownOpen) refreshLootDropdown();
        });
        field.mouseDown().subscribe((mx, my, btn) -> {
            currentLootField = field;
            currentLootOnChange = onChange;
            openLootDropdown();
            return false;
        });
        currentLootField = field;
        currentLootOnChange = onChange;
        fieldRow.child(field);

        ButtonComponent chevron = Components.button(Component.empty(), b -> {
            currentLootField = field;
            currentLootOnChange = onChange;
            toggleLootDropdown();
        });
        chevron.sizing(Sizing.fixed(18), Sizing.fixed(20));
        chevron.renderer((context, rendered, delta) -> {
            String glyph = lootDropdownOpen ? "▲" : "▼";
            int tx = rendered.getX() + (rendered.getWidth() - this.font.width(glyph)) / 2;
            int ty = rendered.getY() + (rendered.getHeight() - this.font.lineHeight) / 2 + 1;
            context.drawString(this.font, glyph, tx, ty, INK_MID, false);
        });
        fieldRow.child(chevron);
        col.child(fieldRow);

        FlowLayout dropdown = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        dropdown.surface(Surface.BLANK);
        lootDropdownPanel = dropdown;
        col.child(dropdown);

        return col;
    }

    private void openLootDropdown() {
        if (!lootDropdownOpen) {
            lootDropdownOpen = true;
            refreshLootDropdown();
        }
    }

    private void closeLootDropdown() {
        lootDropdownOpen = false;
        if (lootDropdownPanel != null) {
            lootDropdownPanel.clearChildren();
            lootDropdownPanel.surface(Surface.BLANK);
        }
    }

    private void toggleLootDropdown() {
        if (lootDropdownOpen) closeLootDropdown();
        else openLootDropdown();
    }

    private void refreshLootDropdown() {
        if (lootDropdownPanel == null) return;
        String current = currentLootField != null ? currentLootField.getValue() : "";
        List<String> candidates = lootCandidates(current);
        lootDropdownPanel.clearChildren();
        if (!lootDropdownOpen || candidates.isEmpty()) {
            lootDropdownPanel.surface(Surface.BLANK);
            return;
        }
        lootDropdownPanel.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));

        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        boolean first = true;
        for (String id : candidates) {
            if (!first) list.child(rowDivider(HAIRLINE));
            Consumer<String> captured = currentLootOnChange;
            list.child(registryRow(id, () -> {
                if (currentLootField != null) currentLootField.text(id);
                if (captured != null) captured.accept(id);
                closeLootDropdown();
            }));
            first = false;
        }

        int rowsHeight = candidates.size() * 20 + Math.max(0, candidates.size() - 1);
        int visibleHeight = Math.min(rowsHeight, DROPDOWN_MAX_HEIGHT);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(visibleHeight), list);
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        lootDropdownPanel.child(scroll);
    }

    private List<String> lootCandidates(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        return knownLootTableIds.stream()
            .filter(id -> needle.isEmpty() || id.toLowerCase(Locale.ROOT).contains(needle))
            .limit(DROPDOWN_MAX_CANDIDATES)
            .collect(Collectors.toList());
    }

    private ButtonComponent registryRow(String id, Runnable onClick) {
        ButtonComponent row = Components.button(Component.empty(), b -> onClick.run());
        row.sizing(Sizing.fill(100), Sizing.fixed(20));
        row.renderer((context, rendered, delta) -> {
            int x1 = rendered.getX(); int y1 = rendered.getY();
            boolean hover = rendered.isHoveredOrFocused();
            context.fill(x1, y1, x1 + rendered.getWidth(), y1 + rendered.getHeight(), hover ? ROW_BG : PANEL_2);
            if (hover) context.fill(x1, y1, x1 + 2, y1 + rendered.getHeight(), ACCENT);
            context.drawString(this.font, id, x1 + 8, y1 + (rendered.getHeight() - this.font.lineHeight) / 2 + 1, INK, false);
        });
        return row;
    }

    private FlowLayout stepperFieldDouble(String caption, String hint, String unit, double step, double min, double max,
                                          String initial, java.util.function.Consumer<String> onChange) {
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        head.child(labelLiteral(caption, INK_DIM));
        head.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        LabelComponent hintLabel = Components.label(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.ITALIC));
        hintLabel.color(Color.ofArgb(INK_DIM));
        head.child(hintLabel);
        col.child(head);

        FlowLayout stepper = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        stepper.gap(6);
        stepper.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout fieldWrap = Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(22));
        fieldWrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldWrap.child(accent);
        TextBoxComponent field = Components.textBox(Sizing.expand(), initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(onChange::accept);
        fieldWrap.child(field);
        FlowLayout unitCell = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        unitCell.padding(Insets.of(0, 0, 6, 6));
        unitCell.child(labelLiteral(unit, INK_DIM));
        fieldWrap.child(unitCell);

        stepper.child(stepButton("-", () -> stepValueDouble(field, -step, min, max, onChange)));
        stepper.child(fieldWrap);
        stepper.child(stepButton("+", () -> stepValueDouble(field, step, min, max, onChange)));
        col.child(stepper);
        return col;
    }

    private void stepValueDouble(TextBoxComponent field, double delta, double min, double max, java.util.function.Consumer<String> onChange) {
        double current;
        try {
            current = Double.parseDouble(field.getValue().trim());
        } catch (Exception ignored) {
            current = min;
        }
        double next = Math.max(min, Math.min(max, current + delta));
        String value = trimDouble(next);
        field.text(value);
        onChange.accept(value);
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

    private ButtonComponent dangerButton(Component text, java.util.function.Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text.copy().withStyle(net.minecraft.ChatFormatting.RED), action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ((DANGER & 0x00FFFFFF) | 0x44000000) : ((DANGER & 0x00FFFFFF) | 0x1F000000);
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), DANGER);
        });
        return button;
    }

    private FlowLayout rowDivider(int color) {
        FlowLayout divider = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
        divider.surface(Surface.flat(color));
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

    private LabelComponent dimLabel(Component text) {
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(INK_DIM));
        return label;
    }

    private FlowLayout sectionHeader(Component title, Integer count) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.gap(8);
        LabelComponent titleLabel = Components.label(title);
        titleLabel.color(Color.ofArgb(INK_DIM));
        row.child(titleLabel);
        if (count != null) {
            LabelComponent countLabel = Components.label(Component.literal("· " + count));
            countLabel.color(Color.ofArgb(ACCENT));
            row.child(countLabel);
        }
        return row;
    }

    private FlowLayout instanceHeaderRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(PANEL_2));
        row.padding(Insets.of(5, 5, 8, 8));
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.child(headerCell("#", Sizing.fixed(22)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller_admin.ui.col.position"), Sizing.expand()));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller_admin.ui.col.status"), Sizing.fixed(120)));
        row.child(headerCell(tr("gui.arenas_ld.dungeon_controller_admin.ui.col.tier_party"), Sizing.fixed(120)));
        row.child(headerCell("", Sizing.fixed(22)));
        row.child(headerCell("", Sizing.fixed(22)));
        row.child(headerCell("", Sizing.fixed(64)));
        return row;
    }

    private FlowLayout badge(Component text, int color) {
        FlowLayout tag = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        tag.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        tag.padding(Insets.of(4, 2, 4, 4));
        tag.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(color));
        tag.child(label);
        return tag;
    }

    private LabelComponent headerCell(String text, Sizing sizing) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(INK_DIM));
        label.horizontalSizing(sizing);
        return label;
    }

    private FlowLayout fixedBadge(Component text, int width, int color) {
        FlowLayout tag = Containers.horizontalFlow(Sizing.fixed(width), Sizing.content());
        tag.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        tag.padding(Insets.of(4, 2, 4, 4));
        tag.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(color));
        label.horizontalSizing(Sizing.expand());
        label.horizontalTextAlignment(HorizontalAlignment.CENTER);
        tag.child(label);
        return tag;
    }

    private LabelComponent labelLiteral(String value, int color) {
        LabelComponent label = Components.label(Component.literal(value));
        label.color(Color.ofArgb(color));
        return label;
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

    private LabelComponent smallMeta(String text, int color) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(color));
        return label;
    }

    private LabelComponent smallMeta(String text) {
        return smallMeta(text, INK_DIM);
    }

    private FlowLayout spacer(int px) {
        FlowLayout spacer = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        spacer.surface(Surface.BLANK);
        return spacer;
    }

    private void applyGeneral() {
        Integer cooldown = parseInt(cooldownInput);
        Integer close = parseInt(closeTimerInput);
        Integer maxParty = parseInt(maxPartyInput);
        Integer invite = parseInt(inviteExpiryInput);
        Integer respawn = parseInt(respawnTimeInput);
        Integer death = parseInt(deathPenaltyInput);
        if (cooldown == null || close == null || maxParty == null || invite == null || respawn == null || death == null) {
            footerError = Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value").getString();
            rebuildUi();
            return;
        }

        footerError = null;
        ClientPlayNetworking.send(new net.ledok.arenas_ld.raid.packet.RaidSetNamePayload(menu.getBlockPos(), nameInput == null ? "" : nameInput));
        ClientPlayNetworking.send(new RaidSetCooldownTicksPayload(menu.getBlockPos(), cooldown * 20));
        ClientPlayNetworking.send(new RaidSetCloseTimerSecondsPayload(menu.getBlockPos(), close));
        ClientPlayNetworking.send(new RaidSetMaxPartySizePayload(menu.getBlockPos(), maxParty));
        ClientPlayNetworking.send(new RaidSetInviteExpiryTicksPayload(menu.getBlockPos(), invite * 20));
        ClientPlayNetworking.send(new RaidSetRespawnTimeTicksPayload(menu.getBlockPos(), respawn));
        ClientPlayNetworking.send(new RaidSetDeathTimePenaltyPayload(menu.getBlockPos(), death * 20));
    }

    private void applyTier(DifficultyTier tier) {
        RaidTierConfig current = tierConfigs.getOrDefault(tier, RaidTierConfig.defaultFor(tier));
        Double health = parseDouble(healthInputs.getOrDefault(tier, Double.toString(current.healthMultiplier())));
        Double damage = parseDouble(damageInputs.getOrDefault(tier, Double.toString(current.damageMultiplier())));
        Integer time = parseInt(timeInputs.getOrDefault(tier, Integer.toString(current.raidTimeSeconds())));
        String loot = lootInputs.getOrDefault(tier, current.perPlayerLootTable());
        Long reward = parseLong(rewardInputs.getOrDefault(tier, Long.toString(current.rewardCurrency())));
        Integer xp = parseInt(xpInputs.getOrDefault(tier, Integer.toString(current.skillExperiencePerWin())));
        Integer regen = parseInt(regenInputs.getOrDefault(tier, Integer.toString(current.regeneration())));
        Double hpScale = parseDouble(hpScaleInputs.getOrDefault(tier, Double.toString(current.hpScalePerPlayer())));

        if (health == null || damage == null || time == null || reward == null
            || xp == null || regen == null || hpScale == null) {
            footerError = Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value").getString();
            rebuildUi();
            return;
        }

        boolean enabled = enabledInputs.getOrDefault(tier, current.enabled());
        RaidTierConfig updated = new RaidTierConfig(
            health, damage, loot, time, enabled, Math.max(0L, reward),
            Math.max(0, xp), Math.max(0, regen), hpScale);
        tierConfigs.put(tier, updated);
        footerError = null;
        ClientPlayNetworking.send(new RaidSetTierConfigPayload(menu.getBlockPos(), tier, updated));
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

    private void syncFromMenu() {
        knownLootTableIds = new ArrayList<>(menu.getKnownLootTableIds());

        instances.clear();
        instances.addAll(menu.getInstances());
        cooldownSnapshotEpochMs = System.currentTimeMillis();
        pendingRemovals.clear();
        pendingRemovals.addAll(menu.getPendingRemovals());
        runningInstances.clear();
        runningInstances.putAll(menu.getRunningInstances());
        tierConfigs.clear();
        tierConfigs.putAll(menu.getTierConfigs());

        nameInput = menu.getRaidName();
        cooldownInput = Integer.toString(menu.getCooldownTicks() / 20);
        closeTimerInput = Integer.toString(menu.getCloseTimerSeconds());
        maxPartyInput = Integer.toString(menu.getMaxPartySize());
        inviteExpiryInput = Integer.toString(menu.getInviteExpiryTicks() / 20);
        respawnTimeInput = Integer.toString(menu.getRespawnTimeTicks());
        deathPenaltyInput = Integer.toString(menu.getDeathTimePenaltyTicks() / 20);
        lootViaInboxInput = menu.isLootViaInbox();

        for (Map.Entry<DifficultyTier, RaidTierConfig> entry : tierConfigs.entrySet()) {
            syncTierInputs(entry.getKey(), entry.getValue());
        }
    }

    private void syncTierInputs(DifficultyTier tier, RaidTierConfig config) {
        healthInputs.put(tier, trimDouble(config.healthMultiplier()));
        damageInputs.put(tier, trimDouble(config.damageMultiplier()));
        lootInputs.put(tier, config.perPlayerLootTable());
        timeInputs.put(tier, Integer.toString(config.raidTimeSeconds()));
        rewardInputs.put(tier, Long.toString(config.rewardCurrency()));
        xpInputs.put(tier, Integer.toString(config.skillExperiencePerWin()));
        regenInputs.put(tier, Integer.toString(config.regeneration()));
        hpScaleInputs.put(tier, trimDouble(config.hpScalePerPlayer()));
        enabledInputs.put(tier, config.enabled());
    }

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(RaidControllerAdminData data) {
        menu.applyData(data);
        syncFromMenu();
        rebuildUi();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
