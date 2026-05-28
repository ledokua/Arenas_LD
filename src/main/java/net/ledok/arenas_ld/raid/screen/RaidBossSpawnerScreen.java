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
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.raid.run.RaidDifficulty;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class RaidBossSpawnerScreen extends BaseOwoHandledScreen<FlowLayout, RaidBossSpawnerScreenHandler> {
    // --- Palette (matches RaidControllerScreen / RaidControllerAdminScreen) ---
    private static final int BG          = 0xFF070E14;
    private static final int PANEL       = 0xFF121922;
    private static final int PANEL_2     = 0xFF0C1218;
    private static final int HAIRLINE    = 0xFF283442;
    private static final int HAIRLINE_HI = 0xFF3A4A5C;
    private static final int ROW_BG      = 0xFF19222D;
    private static final int INK         = 0xFFE8EEF5;
    private static final int INK_MID     = 0xFF9AA8B8;
    private static final int INK_DIM     = 0xFF5F6E80;
    private static final int GOOD        = 0xFF86D36C;
    private static final int WARN        = 0xFFF5B042;
    private static final int DANGER      = 0xFFE8624A;
    private static final int INFO        = 0xFF6DA3E8;
    private static final int ACCENT      = 0xFFA98BE8;
    private static final int ACCENT_DARK = 0xFF6C4FB5;

    private enum Tab {
        GENERAL,
        TIERS
    }

    private Tab currentTab = Tab.GENERAL;
    private RaidDifficulty selectedTier = RaidDifficulty.NORMAL;

    // Drafted general-tab values
    private String mobIdValue                  = "";
    private String respawnTimeValue            = "";
    private String lootTableIdValue            = "";
    private String perPlayerLootTableIdValue   = "";
    private String battleRadiusValue           = "";
    private String regenerationValue           = "";
    private String skillExperienceValue        = "";
    private String hpScalePerPlayerValue       = "";
    private String battleTimeLimitSecsValue    = "";

    // Drafted tier-tab values (scratch fields)
    private String tierHealthMultValue          = "";
    private String tierDamageMultValue          = "";
    private String tierLootTableValue           = "";
    private String tierPerPlayerLootTableValue  = "";

    private final Map<RaidDifficulty, RaidTierConfig> tierConfigDraft = new EnumMap<>(RaidDifficulty.class);

    // Layout refs (rebuilt each tab switch)
    private FlowLayout contentArea;
    private FlowLayout footerActions;
    private LabelComponent footerLabel;
    private ButtonComponent generalTabButton;
    private ButtonComponent tiersTabButton;
    private String footerError;

    public RaidBossSpawnerScreen(RaidBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
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
        loadFromBlockEntity();

        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth  = Math.max(440, Math.min(560, this.width - 24));
        int shellHeight = Math.max(320, this.height - 24);
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

        rebuildUi();
    }

    // ── Header ───────────────────────────────────────────────────────────────
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
        LabelComponent titleLabel = Components.label(Component.translatable("gui.arenas_ld.boss_spawner"));
        titleLabel.color(Color.ofArgb(INK));
        titleLine.child(titleLabel);
        info.child(titleLine);

        FlowLayout meta = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        meta.gap(10);
        meta.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        meta.child(smallMeta("POS · X " + menu.blockEntity.getBlockPos().getX()
            + " · Y " + menu.blockEntity.getBlockPos().getY()
            + " · Z " + menu.blockEntity.getBlockPos().getZ(), INK_DIM));
        FlowLayout live = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        live.gap(4);
        live.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        live.child(text(Component.literal("●"), GOOD));
        live.child(smallMeta("LIVE", GOOD));
        meta.child(live);
        info.child(meta);
        header.child(info);

        header.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        ButtonComponent close = smallButton(Component.literal("×"), b -> onClose());
        close.sizing(Sizing.fixed(22), Sizing.fixed(18));
        header.child(close);
        return header;
    }

    // ── Tabs ─────────────────────────────────────────────────────────────────
    private FlowLayout buildTabs() {
        FlowLayout tabs = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(30));
        tabs.surface(Surface.flat(PANEL_2));
        tabs.padding(Insets.of(4));
        tabs.gap(0);
        tabs.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        generalTabButton = tabButton("gui.arenas_ld.tab_general", Tab.GENERAL);
        tiersTabButton   = tabButton("gui.arenas_ld.tab_tiers",   Tab.TIERS);

        tabs.child(tabCell(generalTabButton));
        tabs.child(tabCell(tiersTabButton));
        return tabs;
    }

    private FlowLayout tabCell(ButtonComponent button) {
        FlowLayout cell = Containers.verticalFlow(Sizing.fill(50), Sizing.content());
        cell.surface(Surface.BLANK);
        cell.padding(Insets.of(0, 0, 2, 2));
        button.horizontalSizing(Sizing.fill(100));
        cell.child(button);
        return cell;
    }

    private ButtonComponent tabButton(String key, Tab tab) {
        ButtonComponent button = Components.button(Component.translatable(key), b -> {
            captureCurrentTierEdits();
            currentTab = tab;
            footerError = null;
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

    // ── Footer ───────────────────────────────────────────────────────────────
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

    // ── Rebuild ──────────────────────────────────────────────────────────────
    private void rebuildUi() {
        contentArea.clearChildren();
        footerActions.clearChildren();

        generalTabButton.active(currentTab != Tab.GENERAL);
        tiersTabButton.active(currentTab != Tab.TIERS);

        switch (currentTab) {
            case GENERAL -> buildGeneralTab();
            case TIERS   -> buildTiersTab();
        }

        footerLabel.text(footerError == null
            ? Component.empty()
            : Component.literal(footerError).withStyle(ChatFormatting.RED));
    }

    // ── General tab ──────────────────────────────────────────────────────────
    private void buildGeneralTab() {
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.tab_general")));
        contentArea.child(spacer(2));

        contentArea.child(twoColumnRow(
            textField(tr("gui.arenas_ld.mob_id"), mobIdValue, v -> mobIdValue = v),
            intField(tr("gui.arenas_ld.respawn_time"), "T", respawnTimeValue, v -> respawnTimeValue = v)
        ));
        contentArea.child(spacer(6));
        contentArea.child(twoColumnRow(
            textField(tr("gui.arenas_ld.loot_table_id"), lootTableIdValue, v -> lootTableIdValue = v),
            intField(tr("gui.arenas_ld.battle_radius"), "B", battleRadiusValue, v -> battleRadiusValue = v)
        ));
        contentArea.child(spacer(6));
        contentArea.child(twoColumnRow(
            textField(tr("gui.arenas_ld.per_player_loot_table_id"), perPlayerLootTableIdValue, v -> perPlayerLootTableIdValue = v),
            intField(tr("gui.arenas_ld.regeneration"), "R", regenerationValue, v -> regenerationValue = v)
        ));
        contentArea.child(spacer(6));
        contentArea.child(twoColumnRow(
            decimalField(tr("gui.arenas_ld.hp_scale_per_player"), "×", hpScalePerPlayerValue, v -> hpScalePerPlayerValue = v),
            intField(tr("gui.arenas_ld.skill_xp"), "XP", skillExperienceValue, v -> skillExperienceValue = v)
        ));
        contentArea.child(spacer(6));
        contentArea.child(twoColumnRow(
            intField(tr("gui.arenas_ld.battle_time_limit"), "S", battleTimeLimitSecsValue, v -> battleTimeLimitSecsValue = v),
            Containers.verticalFlow(Sizing.fill(100), Sizing.content())
        ));

        contentArea.child(spacer(8));
        int linkedRespawns = menu.blockEntity.getRespawnPointOffsets().size();
        contentArea.child(text(
            Component.translatable("gui.arenas_ld.respawn_points_linked_readonly", linkedRespawns),
            INK_MID));

        contentArea.child(spacer(8));
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.gap(6);
        actions.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        ButtonComponent attributes = smallButton(
            Component.translatable("gui.arenas_ld.attributes"),
            b -> openAttributesScreen());
        attributes.horizontalSizing(Sizing.fixed(110));
        actions.child(attributes);

        ButtonComponent equipment = smallButton(
            Component.translatable("gui.arenas_ld.equipment"),
            b -> openEquipmentScreen());
        equipment.horizontalSizing(Sizing.fixed(110));
        actions.child(equipment);

        actions.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        contentArea.child(actions);

        ButtonComponent save = smallButton(
            Component.translatable("gui.arenas_ld.save"),
            b -> onSaveGeneral());
        save.horizontalSizing(Sizing.fixed(110));
        footerActions.child(save);
    }

    // ── Tiers tab ────────────────────────────────────────────────────────────
    private void buildTiersTab() {
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.tab_tiers")));
        contentArea.child(spacer(4));
        contentArea.child(tierPickerRow());
        contentArea.child(spacer(6));
        contentArea.child(tierBanner(selectedTier));
        contentArea.child(spacer(6));

        loadTierFieldsForSelectedTier();

        contentArea.child(twoColumnRow(
            decimalField(tr("gui.arenas_ld.tier_health_mult"), "×", tierHealthMultValue, v -> tierHealthMultValue = v),
            decimalField(tr("gui.arenas_ld.tier_damage_mult"), "×", tierDamageMultValue, v -> tierDamageMultValue = v)
        ));
        contentArea.child(spacer(6));
        contentArea.child(twoColumnRow(
            textField(tr("gui.arenas_ld.tier_loot_table"), tierLootTableValue, v -> tierLootTableValue = v),
            textField(tr("gui.arenas_ld.tier_per_player_loot_table"), tierPerPlayerLootTableValue, v -> tierPerPlayerLootTableValue = v)
        ));

        // HP preview, mirrored from old screen's renderLabels
        contentArea.child(spacer(10));
        contentArea.child(hpPreviewPanel());

        ButtonComponent save = smallButton(
            Component.translatable("gui.arenas_ld.save"),
            b -> onSaveTierConfigs());
        save.horizontalSizing(Sizing.fixed(110));
        footerActions.child(save);
    }

    private FlowLayout tierPickerRow() {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        for (RaidDifficulty tier : RaidDifficulty.values()) {
            row.child(tierPillButton(tier));
        }
        return row;
    }

    private ButtonComponent tierPillButton(RaidDifficulty tier) {
        ButtonComponent button = Components.button(Component.translatable(tier.translationKey()), b -> {
            captureCurrentTierEdits();
            selectedTier = tier;
            rebuildUi();
        });
        int color = tierColor(tier);
        button.sizing(Sizing.fixed(86), Sizing.fixed(20));
        button.active(tier != selectedTier);
        button.renderer((context, rendered, delta) -> {
            boolean active = !rendered.active(); // selected tier
            int fill = active
                ? ((color & 0x00FFFFFF) | 0x33000000)
                : (rendered.isHoveredOrFocused() ? ROW_BG : PANEL_2);
            int border = active ? color : HAIRLINE;
            context.fill(rendered.getX(), rendered.getY(),
                rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return button;
    }

    private FlowLayout tierBanner(RaidDifficulty tier) {
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
        inner.child(badge(Component.literal(tier.name()), color));
        inner.child(text(Component.translatable(tier.translationKey()), INK));
        banner.child(inner);
        return banner;
    }

    private FlowLayout hpPreviewPanel() {
        captureCurrentTierEdits();
        RaidTierConfig cfg = tierConfigDraft.getOrDefault(selectedTier, RaidTierConfig.defaultFor(selectedTier));
        double hpScale = clamp(parseDoubleOrDefault(hpScalePerPlayerValue, menu.blockEntity.hpScalePerPlayer), -0.99, 100.0);

        FlowLayout panel = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        panel.surface(Surface.flat(ROW_BG).and(Surface.outline(HAIRLINE)));
        panel.padding(Insets.of(8));
        panel.gap(3);
        panel.child(text(Component.translatable("gui.arenas_ld.hp_preview"), INK_DIM));

        long hp1 = Math.round(menu.blockEntity.computeExpectedHp(selectedTier, 1, cfg, hpScale));
        long hp2 = Math.round(menu.blockEntity.computeExpectedHp(selectedTier, 2, cfg, hpScale));
        long hp4 = Math.round(menu.blockEntity.computeExpectedHp(selectedTier, 4, cfg, hpScale));
        panel.child(text(Component.translatable("gui.arenas_ld.hp_preview_players", 1, hp1), INK));
        panel.child(text(Component.translatable("gui.arenas_ld.hp_preview_players", 2, hp2), INK));
        panel.child(text(Component.translatable("gui.arenas_ld.hp_preview_players", 4, hp4), INK));
        return panel;
    }

    // ── Field builders ───────────────────────────────────────────────────────
    private FlowLayout textField(String caption, String initial, Consumer<String> onChange) {
        return fieldWithUnit(caption, null, initial, onChange);
    }

    private FlowLayout intField(String caption, String unit, String initial, Consumer<String> onChange) {
        return fieldWithUnit(caption, unit, initial, onChange);
    }

    private FlowLayout decimalField(String caption, String unit, String initial, Consumer<String> onChange) {
        return fieldWithUnit(caption, unit, initial, onChange);
    }

    private FlowLayout fieldWithUnit(String caption, String unit, String initial, Consumer<String> onChange) {
        FlowLayout col = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        col.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        head.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        head.child(text(Component.literal(caption), INK_DIM));
        col.child(head);

        FlowLayout fieldWrap = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        fieldWrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        fieldWrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        fieldWrap.child(accent);

        TextBoxComponent field = Components.textBox(Sizing.expand(), initial == null ? "" : initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(onChange::accept);
        fieldWrap.child(field);

        if (unit != null && !unit.isEmpty()) {
            FlowLayout unitCell = Containers.horizontalFlow(Sizing.content(), Sizing.content());
            unitCell.padding(Insets.of(0, 0, 6, 6));
            unitCell.child(text(Component.literal(unit), INK_DIM));
            fieldWrap.child(unitCell);
        }
        col.child(fieldWrap);
        return col;
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

    // ── Helpers ──────────────────────────────────────────────────────────────
    private FlowLayout sectionHeader(Component title) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        row.gap(8);
        LabelComponent titleLabel = Components.label(title);
        titleLabel.color(Color.ofArgb(INK_DIM));
        row.child(titleLabel);
        return row;
    }

    private FlowLayout spacer(int px) {
        FlowLayout spacer = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        spacer.surface(Surface.BLANK);
        return spacer;
    }

    private LabelComponent text(Component component, int color) {
        LabelComponent label = Components.label(component);
        label.color(Color.ofArgb(color));
        return label;
    }

    private LabelComponent smallMeta(String text, int color) {
        LabelComponent label = Components.label(Component.literal(text));
        label.color(Color.ofArgb(color));
        return label;
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

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private int tierColor(RaidDifficulty tier) {
        return switch (tier) {
            case EASY      -> GOOD;
            case NORMAL    -> INFO;
            case HARD      -> WARN;
            case LEGENDARY -> DANGER;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ── Data loading / save ──────────────────────────────────────────────────
    private void loadFromBlockEntity() {
        if (menu.blockEntity == null) return;
        mobIdValue                 = menu.blockEntity.getMobId();
        respawnTimeValue           = Integer.toString(menu.blockEntity.respawnTime);
        lootTableIdValue           = menu.blockEntity.lootTableId;
        perPlayerLootTableIdValue  = menu.blockEntity.perPlayerLootTableId;
        battleRadiusValue          = Integer.toString(menu.blockEntity.battleRadius);
        regenerationValue          = Integer.toString(menu.blockEntity.regeneration);
        skillExperienceValue       = Integer.toString(menu.blockEntity.skillExperiencePerWin);
        hpScalePerPlayerValue      = formatDecimal(menu.blockEntity.hpScalePerPlayer);
        battleTimeLimitSecsValue   = Integer.toString(Math.max(0, menu.blockEntity.battleTimeLimitTicks / 20));

        tierConfigDraft.clear();
        for (RaidDifficulty tier : RaidDifficulty.values()) {
            RaidTierConfig cfg = menu.blockEntity.getTierConfigs().getOrDefault(tier, RaidTierConfig.defaultFor(tier));
            tierConfigDraft.put(tier, cfg);
        }
    }

    private void loadTierFieldsForSelectedTier() {
        RaidTierConfig cfg = tierConfigDraft.getOrDefault(selectedTier, RaidTierConfig.defaultFor(selectedTier));
        tierHealthMultValue         = Double.toString(cfg.healthMultiplier());
        tierDamageMultValue         = Double.toString(cfg.damageMultiplier());
        // Mirrors old behaviour at line 235 — UI scratch field, never persisted (no per-tier loot table in record).
        tierLootTableValue          = "";
        tierPerPlayerLootTableValue = cfg.perPlayerLootTable();
    }

    private void captureCurrentTierEdits() {
        // Only meaningful if the user has been editing the TIERS tab.
        if (currentTab != Tab.TIERS) return;
        RaidTierConfig existing = tierConfigDraft.getOrDefault(selectedTier, RaidTierConfig.defaultFor(selectedTier));
        double health = parseDoubleOrDefault(tierHealthMultValue, existing.healthMultiplier());
        double damage = parseDoubleOrDefault(tierDamageMultValue, existing.damageMultiplier());
        String perPlayer = tierPerPlayerLootTableValue == null ? "" : tierPerPlayerLootTableValue.trim();
        tierConfigDraft.put(selectedTier, new RaidTierConfig(
            health, damage, perPlayer,
            existing.raidTimeSeconds(), existing.enabled(), existing.rewardCurrency()));
    }

    private void onSaveGeneral() {
        try {
            ClientPlayNetworking.send(new ModPackets.UpdateBossSpawnerPayload(
                menu.blockEntity.getBlockPos(),
                mobIdValue,
                Integer.parseInt(respawnTimeValue),
                lootTableIdValue,
                perPlayerLootTableIdValue,
                0,                                              // legacy param
                Integer.parseInt(battleRadiusValue),
                Integer.parseInt(regenerationValue),
                1,                                              // legacy param
                Integer.parseInt(skillExperienceValue),
                Math.max(0, Integer.parseInt(battleTimeLimitSecsValue) * 20),
                Double.parseDouble(hpScalePerPlayerValue),
                ""                                              // legacy param
            ));
            this.onClose();
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format in one of the fields.");
            footerError = "Invalid number format";
            rebuildUi();
        }
    }

    private void onSaveTierConfigs() {
        captureCurrentTierEdits();
        double hpScale = parseDoubleOrDefault(hpScalePerPlayerValue, menu.blockEntity.hpScalePerPlayer);
        CompoundTag tierConfigsTag = new CompoundTag();
        for (RaidDifficulty tier : RaidDifficulty.values()) {
            RaidTierConfig cfg = tierConfigDraft.getOrDefault(tier, RaidTierConfig.defaultFor(tier));
            tierConfigsTag.put(tier.name(), cfg.toNbt());
        }
        ClientPlayNetworking.send(new ModPackets.UpdateBossSpawnerTierConfigsPayload(
            menu.blockEntity.getBlockPos(),
            hpScale,
            tierConfigsTag
        ));
        this.onClose();
    }

    private void openAttributesScreen() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        this.minecraft.setScreen(new net.ledok.arenas_ld.screen.MobAttributesScreen(
            new net.ledok.arenas_ld.screen.MobAttributesScreenHandler(
                menu.containerId,
                minecraft.player.getInventory(),
                new net.ledok.arenas_ld.screen.MobAttributesData(menu.blockEntity.getBlockPos())),
            minecraft.player.getInventory(),
            Component.translatable("gui.arenas_ld.boss_attributes")));
    }

    private void openEquipmentScreen() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        this.minecraft.setScreen(new net.ledok.arenas_ld.screen.EquipmentScreen(
            new net.ledok.arenas_ld.screen.EquipmentScreenHandler(
                menu.containerId,
                minecraft.player.getInventory(),
                new net.ledok.arenas_ld.screen.EquipmentScreenData(menu.blockEntity.getBlockPos())),
            minecraft.player.getInventory(),
            Component.translatable("gui.arenas_ld.boss_equipment")));
    }

    private static double parseDoubleOrDefault(String value, double def) {
        if (value == null || value.isBlank()) return def;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static String formatDecimal(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
