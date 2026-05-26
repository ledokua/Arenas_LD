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
import net.ledok.arenas_ld.dungeon.packet.MoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.RemoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.SetCloseTimerSecondsPayload;
import net.ledok.arenas_ld.dungeon.packet.SetCooldownTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetInviteExpiryTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetMaxPartySizePayload;
import net.ledok.arenas_ld.dungeon.packet.SetTierConfigPayload;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.ledok.arenas_ld.dungeon.run.LeaderboardEntry;
import net.ledok.arenas_ld.dungeon.run.TierConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonControllerAdminScreen extends BaseOwoHandledScreen<FlowLayout, DungeonControllerAdminScreenHandler> {
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
        INSTANCES,
        GENERAL,
        NORMAL,
        HARD,
        HELL
    }

    private final List<BlockPos> instances = new ArrayList<>();
    private final Set<BlockPos> activeRuns = new HashSet<>();
    private final Map<BlockPos, Integer> cooldowns = new HashMap<>();
    private final Set<BlockPos> pendingRemovals = new HashSet<>();
    private final Map<DifficultyTier, TierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, List<LeaderboardEntry>> leaderboards = new EnumMap<>(DifficultyTier.class);

    private Tab currentTab = Tab.INSTANCES;
    private String footerError;
    private long lastCooldownSecond = -1L;

    private FlowLayout contentArea;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;

    private ButtonComponent instancesTabButton;
    private ButtonComponent generalTabButton;
    private ButtonComponent normalTabButton;
    private ButtonComponent hardTabButton;
    private ButtonComponent hellTabButton;

    private String cooldownInput;
    private String closeTimerInput;
    private String maxPartyInput;
    private String inviteExpiryInput;

    private final Map<DifficultyTier, String> healthInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> damageInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> lootInputs = new EnumMap<>(DifficultyTier.class);
    private final Map<DifficultyTier, String> timeInputs = new EnumMap<>(DifficultyTier.class);

    public DungeonControllerAdminScreen(DungeonControllerAdminScreenHandler handler, Inventory inventory, Component title) {
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
        int shellHeight = Math.max(280, Math.min(360, this.height - 24));
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
                rebuildUi();
            }
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
        LabelComponent titleLabel = Components.label(Component.translatable("gui.arenas_ld.dungeon_controller_admin.title"));
        titleLabel.color(Color.ofArgb(INK));
        FlowLayout meta = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        meta.gap(8);
        meta.child(smallMeta("POS  " + menu.getBlockPos().getX() + "  " + menu.getBlockPos().getY() + "  " + menu.getBlockPos().getZ()));
        meta.child(smallMeta("OP", WARN));
        info.child(titleLabel);
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
        normalTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.normal", Tab.NORMAL, 64);
        hardTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.hard", Tab.HARD, 56);
        hellTabButton = tabButton("gui.arenas_ld.dungeon_controller_admin.tab.hell", Tab.HELL, 56);

        tabs.child(instancesTabButton);
        tabs.child(generalTabButton);
        tabs.child(normalTabButton);
        tabs.child(hardTabButton);
        tabs.child(hellTabButton);
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
        contentArea.clearChildren();
        footerActions.clearChildren();

        instancesTabButton.active(currentTab != Tab.INSTANCES);
        generalTabButton.active(currentTab != Tab.GENERAL);
        normalTabButton.active(currentTab != Tab.NORMAL);
        hardTabButton.active(currentTab != Tab.HARD);
        hellTabButton.active(currentTab != Tab.HELL);
        instancesTabButton.setMessage(Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.instances").append(" [" + instances.size() + "]"));

        switch (currentTab) {
            case INSTANCES -> buildInstancesTab();
            case GENERAL -> buildGeneralTab();
            case NORMAL -> buildTierTab(DifficultyTier.NORMAL);
            case HARD -> buildTierTab(DifficultyTier.HARD);
            case HELL -> buildTierTab(DifficultyTier.HELL);
        }

        footerLabel.text(footerError == null ? Component.empty() : Component.literal(footerError));
    }

    private void buildInstancesTab() {
        FlowLayout summary = rowPanel(true);
        summary.gap(8);
        summary.child(fixedBadge(Component.literal(activeRuns.size() + " RUNNING"), 108, GOOD));
        summary.child(fixedBadge(Component.literal(cooldowns.size() + " COOLDOWN"), 116, WARN));
        summary.child(fixedBadge(Component.literal(pendingRemovals.size() + " PENDING"), 108, DANGER));
        summary.child(fixedBadge(Component.literal(Math.max(0, instances.size() - activeRuns.size() - cooldowns.size()) + " IDLE"), 82, INK_MID));
        summary.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        summary.child(label("gui.arenas_ld.dungeon_controller_admin.active_runs_count", INK_MID,
            activeRuns.size(), instances.size()));
        contentArea.child(summary);
        if (!instances.isEmpty()) {
            contentArea.child(instanceHeaderRow());
        }

        for (int i = 0; i < instances.size() && i < 7; i++) {
            contentArea.child(instanceRow(i, instances.get(i)));
        }

        if (instances.isEmpty()) {
            contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller_admin.use_linker_hint")));
        }
    }

    private FlowLayout instanceRow(int index, BlockPos pos) {
        FlowLayout row = rowPanel(index % 2 == 0);
        row.gap(6);

        FlowLayout info = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        LabelComponent name = Components.label(Component.literal((index + 1) + ". " + pos.toShortString()));
        name.color(Color.ofArgb(INK));
        LabelComponent hint = Components.label(Component.literal("INSTANCE"));
        hint.color(Color.ofArgb(INK_DIM));
        info.child(name);
        info.child(hint);
        row.child(info);

        row.child(fixedBadge(instanceStatus(pos), 138, statusColor(pos)));

        ButtonComponent up = smallButton(Component.literal("^"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new MoveDungeonInstancePayload(menu.getBlockPos(), index, Math.max(0, index - 1)));
        });
        up.active(index > 0);
        row.child(up);

        ButtonComponent down = smallButton(Component.literal("v"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new MoveDungeonInstancePayload(menu.getBlockPos(), index, Math.min(instances.size() - 1, index + 1)));
        });
        down.active(index < instances.size() - 1);
        row.child(down);

        row.child(smallButton(Component.literal("X"), b -> {
            footerError = null;
            ClientPlayNetworking.send(new RemoveDungeonInstancePayload(menu.getBlockPos(), pos));
        }));

        return row;
    }

    private void buildGeneralTab() {
        contentArea.child(sectionHeader(Component.literal("GENERAL CONFIGURATION"), null));
        contentArea.child(numberFieldRow(
            "gui.arenas_ld.dungeon_controller_admin.label.cooldown",
            cooldownInput,
            value -> cooldownInput = value
        ));
        contentArea.child(numberFieldRow(
            "gui.arenas_ld.dungeon_controller_admin.label.close_timer",
            closeTimerInput,
            value -> closeTimerInput = value
        ));
        contentArea.child(numberFieldRow(
            "gui.arenas_ld.dungeon_controller_admin.label.max_party",
            maxPartyInput,
            value -> maxPartyInput = value
        ));
        contentArea.child(numberFieldRow(
            "gui.arenas_ld.dungeon_controller_admin.label.invite_expiry",
            inviteExpiryInput,
            value -> inviteExpiryInput = value
        ));

        footerActions.child(smallButton(Component.translatable("gui.arenas_ld.apply"), b -> applyGeneral()));
    }

    private FlowLayout numberFieldRow(String labelKey, String initial, java.util.function.Consumer<String> consumer) {
        FlowLayout row = rowPanel(false);
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent label = Components.label(Component.translatable(labelKey));
        label.color(Color.ofArgb(INK_MID));
        label.horizontalSizing(Sizing.fixed(180));
        row.child(label);

        TextBoxComponent field = Components.textBox(Sizing.fixed(80), initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(consumer::accept);
        row.child(field);
        return row;
    }

    private void buildTierTab(DifficultyTier tier) {
        TierConfig config = tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier));
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller_admin.tier_header", tier.name()), null));
        FlowLayout summary = rowPanel(true);
        summary.gap(8);
        summary.child(fixedBadge(Component.literal("HP x" + trimDouble(config.healthMultiplier())), 96, GOOD));
        summary.child(fixedBadge(Component.literal("DMG x" + trimDouble(config.damageMultiplier())), 104, DANGER));
        summary.child(fixedBadge(Component.literal(config.dungeonTimeSeconds() + "s"), 72, INFO));
        summary.child(fixedBadge(Component.literal(config.hardcoreDefault() ? "HC DEFAULT" : "SAFE DEFAULT"), 118, config.hardcoreDefault() ? WARN : INK_MID));
        contentArea.child(summary);

        contentArea.child(numberFieldRow(
            "gui.arenas_ld.dungeon_controller_admin.tier.health",
            healthInputs.getOrDefault(tier, Double.toString(config.healthMultiplier())),
            value -> healthInputs.put(tier, value)
        ));
        contentArea.child(numberFieldRow(
            "gui.arenas_ld.dungeon_controller_admin.tier.damage",
            damageInputs.getOrDefault(tier, Double.toString(config.damageMultiplier())),
            value -> damageInputs.put(tier, value)
        ));
        contentArea.child(textFieldRow(
            "gui.arenas_ld.dungeon_controller_admin.tier.loot",
            lootInputs.getOrDefault(tier, config.perPlayerLootTable()),
            value -> lootInputs.put(tier, value)
        ));
        contentArea.child(numberFieldRow(
            "gui.arenas_ld.dungeon_controller_admin.tier.time",
            timeInputs.getOrDefault(tier, Integer.toString(config.dungeonTimeSeconds())),
            value -> timeInputs.put(tier, value)
        ));

        ButtonComponent hardcore = smallButton(Component.translatable(config.hardcoreDefault()
            ? "gui.arenas_ld.dungeon_controller_admin.hardcore.on"
            : "gui.arenas_ld.dungeon_controller_admin.hardcore.off"), b -> {
            TierConfig current = tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier));
            tierConfigs.put(tier, new TierConfig(
                current.healthMultiplier(),
                current.damageMultiplier(),
                current.perPlayerLootTable(),
                current.dungeonTimeSeconds(),
                !current.hardcoreDefault()
            ));
            syncTierInputs(tier, tierConfigs.get(tier));
            rebuildUi();
        });
        contentArea.child(lineWithLabel("gui.arenas_ld.dungeon_controller_admin.tier.hardcore", hardcore));

        contentArea.child(spacer(4));
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.dungeon_controller_admin.leaderboard"), topEntriesCount(tier)));

        List<LeaderboardEntry> topEntries = leaderboards.getOrDefault(tier, List.of()).stream()
            .sorted(Comparator.comparingInt(LeaderboardEntry::timeSeconds))
            .limit(4)
            .toList();

        if (topEntries.isEmpty()) {
            contentArea.child(dimLabel(Component.translatable("gui.arenas_ld.dungeon_controller_admin.leaderboard.empty")));
        } else {
            for (int i = 0; i < topEntries.size(); i++) {
                LeaderboardEntry entry = topEntries.get(i);
                contentArea.child(leaderboardRow(i, entry));
            }
        }

        footerActions.child(smallButton(Component.translatable("gui.arenas_ld.dungeon_controller_admin.button.apply_all"), b -> applyTier(tier)));
    }

    private FlowLayout textFieldRow(String labelKey, String initial, java.util.function.Consumer<String> consumer) {
        FlowLayout row = rowPanel(false);
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent label = Components.label(Component.translatable(labelKey));
        label.color(Color.ofArgb(INK_MID));
        label.horizontalSizing(Sizing.fixed(180));
        row.child(label);

        TextBoxComponent field = Components.textBox(Sizing.expand(), initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(consumer::accept);
        row.child(field);
        return row;
    }

    private FlowLayout lineWithLabel(String labelKey, ButtonComponent component) {
        FlowLayout row = rowPanel(false);
        row.gap(6);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        LabelComponent label = Components.label(Component.translatable(labelKey));
        label.color(Color.ofArgb(INK_MID));
        label.horizontalSizing(Sizing.fixed(180));
        row.child(label);
        row.child(component);
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

    private LabelComponent label(String key, int color, Object... args) {
        LabelComponent label = Components.label(Component.translatable(key, args));
        label.color(Color.ofArgb(color));
        return label;
    }

    private LabelComponent label(String key, int color) {
        return label(key, color, new Object[0]);
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
        row.gap(6);
        row.child(headerCell("INSTANCE", Sizing.expand()));
        row.child(headerCell("STATUS", Sizing.fixed(138)));
        row.child(headerCell("", Sizing.fixed(20)));
        row.child(headerCell("", Sizing.fixed(20)));
        row.child(headerCell("", Sizing.fixed(20)));
        return row;
    }

    private FlowLayout leaderboardRow(int index, LeaderboardEntry entry) {
        FlowLayout row = rowPanel(index % 2 == 0);
        row.gap(8);
        row.child(fixedText(Component.literal("#" + (index + 1)), 30, ACCENT, HorizontalAlignment.LEFT));

        FlowLayout name = Containers.verticalFlow(Sizing.expand(), Sizing.content());
        name.child(labelLiteral(entry.playerName(), INK));
        name.child(labelLiteral("RUNNER", INK_DIM));
        row.child(name);

        row.child(fixedBadge(Component.literal(entry.timeSeconds() + "s"), 70, INFO));
        return row;
    }

    private FlowLayout badge(Component text, int color) {
        FlowLayout tag = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        tag.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        tag.padding(Insets.of(2, 4, 2, 4));
        tag.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        LabelComponent label = Components.label(text);
        label.color(Color.ofArgb(color));
        tag.child(label);
        return tag;
    }

    private FlowLayout rowPanel(boolean alternate) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.surface(Surface.flat(alternate ? ROW_BG_ALT : ROW_BG).and(Surface.outline(HAIRLINE)));
        row.padding(Insets.of(6));
        return row;
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

    private FlowLayout fixedBadge(Component text, int width, int color) {
        FlowLayout tag = Containers.horizontalFlow(Sizing.fixed(width), Sizing.content());
        tag.surface(Surface.flat((color & 0x00FFFFFF) | 0x22000000).and(Surface.outline((color & 0x00FFFFFF) | 0x55000000)));
        tag.padding(Insets.of(2, 4, 2, 4));
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

    private String trimDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Integer.toString((int) Math.rint(value));
        }
        return String.format("%.2f", value);
    }

    private LabelComponent smallMeta(String text, int color) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(color));
        return label;
    }

    private LabelComponent smallMeta(String text) {
        return smallMeta(text, INK_DIM);
    }

    private int topEntriesCount(DifficultyTier tier) {
        return (int) leaderboards.getOrDefault(tier, List.of()).stream().limit(4).count();
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
        if (cooldown == null || close == null || maxParty == null || invite == null) {
            footerError = Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value").getString();
            rebuildUi();
            return;
        }

        footerError = null;
        ClientPlayNetworking.send(new SetCooldownTicksPayload(menu.getBlockPos(), cooldown * 20));
        ClientPlayNetworking.send(new SetCloseTimerSecondsPayload(menu.getBlockPos(), close));
        ClientPlayNetworking.send(new SetMaxPartySizePayload(menu.getBlockPos(), maxParty));
        ClientPlayNetworking.send(new SetInviteExpiryTicksPayload(menu.getBlockPos(), invite * 20));
    }

    private void applyTier(DifficultyTier tier) {
        TierConfig current = tierConfigs.getOrDefault(tier, TierConfig.defaultFor(tier));
        Double health = parseDouble(healthInputs.getOrDefault(tier, Double.toString(current.healthMultiplier())));
        Double damage = parseDouble(damageInputs.getOrDefault(tier, Double.toString(current.damageMultiplier())));
        Integer time = parseInt(timeInputs.getOrDefault(tier, Integer.toString(current.dungeonTimeSeconds())));
        String loot = lootInputs.getOrDefault(tier, current.perPlayerLootTable());

        if (health == null || damage == null || time == null) {
            footerError = Component.translatable("message.arenas_ld.dungeon_controller_admin.invalid_value").getString();
            rebuildUi();
            return;
        }

        TierConfig updated = new TierConfig(health, damage, loot, time, current.hardcoreDefault());
        tierConfigs.put(tier, updated);
        footerError = null;
        ClientPlayNetworking.send(new SetTierConfigPayload(menu.getBlockPos(), tier, updated));
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
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Component instanceStatus(BlockPos pos) {
        if (pendingRemovals.contains(pos)) {
            return Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.pending_removal");
        }
        if (activeRuns.contains(pos)) {
            return Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.running");
        }
        if (cooldowns.containsKey(pos)) {
            return Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.cooldown", formatTicks(cooldowns.get(pos)));
        }
        return Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.idle");
    }

    private int statusColor(BlockPos pos) {
        if (pendingRemovals.contains(pos)) {
            return 0xFFE17070;
        }
        if (activeRuns.contains(pos)) {
            return 0xFF66DD66;
        }
        if (cooldowns.containsKey(pos)) {
            return 0xFFE8CC66;
        }
        return 0xFFA0A0A0;
    }

    private String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        int rem = seconds % 60;
        return String.format("%d:%02d", minutes, rem);
    }

    private void syncFromMenu() {
        instances.clear();
        instances.addAll(menu.getInstances());
        activeRuns.clear();
        activeRuns.addAll(menu.getActiveRunInstances());
        cooldowns.clear();
        cooldowns.putAll(menu.getInstanceCooldownTimers());
        pendingRemovals.clear();
        pendingRemovals.addAll(menu.getPendingRemovals());
        tierConfigs.clear();
        tierConfigs.putAll(menu.getTierConfigs());
        leaderboards.clear();
        leaderboards.putAll(menu.getTopLeaderboards());

        cooldownInput = Integer.toString(menu.getCooldownTicks() / 20);
        closeTimerInput = Integer.toString(menu.getCloseTimerSeconds());
        maxPartyInput = Integer.toString(menu.getMaxPartySize());
        inviteExpiryInput = Integer.toString(menu.getInviteExpiryTicks() / 20);

        for (Map.Entry<DifficultyTier, TierConfig> entry : tierConfigs.entrySet()) {
            syncTierInputs(entry.getKey(), entry.getValue());
        }
    }

    private void syncTierInputs(DifficultyTier tier, TierConfig config) {
        healthInputs.put(tier, Double.toString(config.healthMultiplier()));
        damageInputs.put(tier, Double.toString(config.damageMultiplier()));
        lootInputs.put(tier, config.perPlayerLootTable());
        timeInputs.put(tier, Integer.toString(config.dungeonTimeSeconds()));
    }

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(DungeonControllerAdminData data) {
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
