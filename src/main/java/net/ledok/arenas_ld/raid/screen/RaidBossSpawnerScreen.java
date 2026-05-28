package net.ledok.arenas_ld.raid.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.raid.run.RaidDifficulty;
import net.ledok.arenas_ld.raid.run.RaidTierConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class RaidBossSpawnerScreen extends AbstractContainerScreen<RaidBossSpawnerScreenHandler> {
    private enum Tab {
        GENERAL,
        TIERS
    }

    private Tab activeTab = Tab.GENERAL;
    private RaidDifficulty selectedTier = RaidDifficulty.NORMAL;

    private EditBox mobIdField;
    private EditBox respawnTimeField;
    private EditBox lootTableIdField;
    private EditBox perPlayerLootTableIdField;
    private EditBox battleRadiusField;
    private EditBox regenerationField;
    private EditBox skillExperienceField;
    private EditBox hpScalePerPlayerField;
    private EditBox battleTimeLimitSecsField;

    private EditBox tierHealthMultField;
    private EditBox tierDamageMultField;
    private EditBox tierLootTableField;
    private EditBox tierPerPlayerLootTableField;

    private Button tabGeneralBtn;
    private Button tabTiersBtn;
    private final Button[] tierButtons = new Button[4];
    private Button saveButton;

    private final List<AbstractWidget> generalWidgets = new ArrayList<>();
    private final List<AbstractWidget> tierWidgets = new ArrayList<>();
    private final Map<RaidDifficulty, RaidTierConfig> tierConfigDraft = new EnumMap<>(RaidDifficulty.class);

    public RaidBossSpawnerScreen(RaidBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 330;
        this.imageHeight = 290;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();
        this.generalWidgets.clear();
        this.tierWidgets.clear();

        int fieldWidth = 150;
        int fieldHeight = 20;
        int yOffset = 36;
        int columnPadding = 10;
        int col1X = this.leftPos + 10;
        int col2X = col1X + fieldWidth + columnPadding;

        tabGeneralBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.tab_general"),
                b -> {
                    captureCurrentTierEdits();
                    activeTab = Tab.GENERAL;
                    updateTabVisibility();
                }).bounds(this.leftPos + 10, this.topPos + 8, 80, 16).build());
        tabTiersBtn = addRenderableWidget(Button.builder(
                Component.translatable("gui.arenas_ld.tab_tiers"),
                b -> {
                    captureCurrentTierEdits();
                    activeTab = Tab.TIERS;
                    updateTabVisibility();
                }).bounds(this.leftPos + 94, this.topPos + 8, 80, 16).build());

        int y = this.topPos + 50;
        addGeneralWidget(new PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.mob_id"), b -> {}, this.font));
        mobIdField = addGeneralWidget(new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal("")));
        mobIdField.setMaxLength(128);
        y += yOffset;

        addGeneralWidget(new PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.loot_table_id"), b -> {}, this.font));
        lootTableIdField = addGeneralWidget(new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal("")));
        lootTableIdField.setMaxLength(128);
        y += yOffset;

        addGeneralWidget(new PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.per_player_loot_table_id"), b -> {}, this.font));
        perPlayerLootTableIdField = addGeneralWidget(new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal("")));
        perPlayerLootTableIdField.setMaxLength(128);
        y += yOffset;

        addGeneralWidget(new PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.hp_scale_per_player"), b -> {}, this.font));
        hpScalePerPlayerField = addGeneralWidget(new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal("")));
        hpScalePerPlayerField.setMaxLength(12);
        y += yOffset;

        addGeneralWidget(new PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.battle_time_limit"), b -> {}, this.font));
        battleTimeLimitSecsField = addGeneralWidget(new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal("")));
        battleTimeLimitSecsField.setMaxLength(8);

        y = this.topPos + 50;
        addGeneralWidget(new PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.respawn_time"), b -> {}, this.font));
        respawnTimeField = addGeneralWidget(new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal("")));
        respawnTimeField.setMaxLength(8);
        y += yOffset;

        addGeneralWidget(new PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.battle_radius"), b -> {}, this.font));
        battleRadiusField = addGeneralWidget(new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal("")));
        battleRadiusField.setMaxLength(4);
        y += yOffset;

        addGeneralWidget(new PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.regeneration"), b -> {}, this.font));
        regenerationField = addGeneralWidget(new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal("")));
        regenerationField.setMaxLength(4);
        y += yOffset;

        addGeneralWidget(new PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.skill_xp"), b -> {}, this.font));
        skillExperienceField = addGeneralWidget(new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal("")));
        skillExperienceField.setMaxLength(8);

        int tierBtnY = this.topPos + 50;
        int tierBtnW = 74;
        int tierBtnGap = 4;
        RaidDifficulty[] difficulties = RaidDifficulty.values();
        for (int i = 0; i < difficulties.length; i++) {
            final RaidDifficulty tier = difficulties[i];
            tierButtons[i] = addTierWidget(Button.builder(
                    Component.translatable(tier.translationKey()),
                    b -> {
                        captureCurrentTierEdits();
                        selectedTier = tier;
                        loadTierFieldsForSelectedTier();
                        updateTierButtonState();
                    }
            ).bounds(this.leftPos + 10 + i * (tierBtnW + tierBtnGap), tierBtnY, tierBtnW, 16).build());
        }

        int tierCol1X = this.leftPos + 10;
        int tierCol2X = tierCol1X + fieldWidth + columnPadding;
        int tierY = this.topPos + 86;

        addTierWidget(new PlainTextButton(tierCol1X, tierY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_health_mult"), b -> {}, this.font));
        tierHealthMultField = addTierWidget(new EditBox(this.font, tierCol1X, tierY, fieldWidth, fieldHeight, Component.literal("")));
        tierHealthMultField.setMaxLength(12);
        tierY += yOffset;

        addTierWidget(new PlainTextButton(tierCol1X, tierY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_damage_mult"), b -> {}, this.font));
        tierDamageMultField = addTierWidget(new EditBox(this.font, tierCol1X, tierY, fieldWidth, fieldHeight, Component.literal("")));
        tierDamageMultField.setMaxLength(12);

        int tierY2 = this.topPos + 86;
        addTierWidget(new PlainTextButton(tierCol2X, tierY2 - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_loot_table"), b -> {}, this.font));
        tierLootTableField = addTierWidget(new EditBox(this.font, tierCol2X, tierY2, fieldWidth, fieldHeight, Component.literal("")));
        tierLootTableField.setMaxLength(128);
        tierY2 += yOffset;

        addTierWidget(new PlainTextButton(tierCol2X, tierY2 - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_per_player_loot_table"), b -> {}, this.font));
        tierPerPlayerLootTableField = addTierWidget(new EditBox(this.font, tierCol2X, tierY2, fieldWidth, fieldHeight, Component.literal("")));
        tierPerPlayerLootTableField.setMaxLength(128);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.attributes"), button -> {
            this.minecraft.setScreen(new net.ledok.arenas_ld.screen.MobAttributesScreen(
                    new net.ledok.arenas_ld.screen.MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new net.ledok.arenas_ld.screen.MobAttributesData(menu.blockEntity.getBlockPos())),
                    minecraft.player.getInventory(),
                    Component.translatable("gui.arenas_ld.boss_attributes")));
        }).bounds(this.leftPos + 15, this.topPos + this.imageHeight - 48, 95, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.equipment"), button -> {
            this.minecraft.setScreen(new net.ledok.arenas_ld.screen.EquipmentScreen(
                    new net.ledok.arenas_ld.screen.EquipmentScreenHandler(menu.containerId, minecraft.player.getInventory(), new net.ledok.arenas_ld.screen.EquipmentScreenData(menu.blockEntity.getBlockPos())),
                    minecraft.player.getInventory(),
                    Component.translatable("gui.arenas_ld.boss_equipment")));
        }).bounds(this.leftPos + 120, this.topPos + this.imageHeight - 48, 95, 20).build());

        saveButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.save"), button -> onSave())
                .bounds(this.leftPos + 225, this.topPos + this.imageHeight - 48, 90, 20)
                .build());

        loadBlockEntityData();
        loadTierFieldsForSelectedTier();
        updateTierButtonState();
        updateTabVisibility();
    }

    private <T extends AbstractWidget> T addGeneralWidget(T widget) {
        this.addRenderableWidget(widget);
        this.generalWidgets.add(widget);
        return widget;
    }

    private <T extends AbstractWidget> T addTierWidget(T widget) {
        this.addRenderableWidget(widget);
        this.tierWidgets.add(widget);
        return widget;
    }

    private void loadBlockEntityData() {
        if (menu.blockEntity == null) return;
        mobIdField.setValue(menu.blockEntity.mobId);
        respawnTimeField.setValue(String.valueOf(menu.blockEntity.respawnTime));
        lootTableIdField.setValue(menu.blockEntity.lootTableId);
        perPlayerLootTableIdField.setValue(menu.blockEntity.perPlayerLootTableId);
        battleRadiusField.setValue(String.valueOf(menu.blockEntity.battleRadius));
        regenerationField.setValue(String.valueOf(menu.blockEntity.regeneration));
        skillExperienceField.setValue(String.valueOf(menu.blockEntity.skillExperiencePerWin));
        hpScalePerPlayerField.setValue(String.valueOf(menu.blockEntity.hpScalePerPlayer));
        battleTimeLimitSecsField.setValue(String.valueOf(Math.max(0, menu.blockEntity.battleTimeLimitTicks / 20)));

        tierConfigDraft.clear();
        for (RaidDifficulty tier : RaidDifficulty.values()) {
            RaidTierConfig cfg = menu.blockEntity.getTierConfigs().getOrDefault(tier, RaidTierConfig.defaultFor(tier));
            tierConfigDraft.put(tier, cfg);
        }
    }

    private void loadTierFieldsForSelectedTier() {
        RaidTierConfig cfg = tierConfigDraft.getOrDefault(selectedTier, RaidTierConfig.defaultFor(selectedTier));
        tierHealthMultField.setValue(Double.toString(cfg.healthMultiplier()));
        tierDamageMultField.setValue(Double.toString(cfg.damageMultiplier()));
        tierLootTableField.setValue("");
        tierPerPlayerLootTableField.setValue(cfg.perPlayerLootTable());
        tierHealthMultField.setHint(Component.translatable("gui.arenas_ld.default_hint", formatDecimal(selectedTier.healthMult)));
        tierDamageMultField.setHint(Component.translatable("gui.arenas_ld.default_hint", formatDecimal(selectedTier.damageMult)));
    }

    private void captureCurrentTierEdits() {
        RaidTierConfig existing = tierConfigDraft.getOrDefault(selectedTier, RaidTierConfig.defaultFor(selectedTier));
        double health = parseDoubleOrDefault(tierHealthMultField.getValue(), existing.healthMultiplier());
        double damage = parseDoubleOrDefault(tierDamageMultField.getValue(), existing.damageMultiplier());
        String perPlayer = tierPerPlayerLootTableField.getValue().trim();
        tierConfigDraft.put(selectedTier, new RaidTierConfig(
            health, damage, perPlayer, existing.raidTimeSeconds(), existing.enabled(), existing.rewardCurrency()));
    }

    private void updateTierButtonState() {
        for (int i = 0; i < tierButtons.length; i++) {
            if (tierButtons[i] != null) {
                tierButtons[i].active = RaidDifficulty.values()[i] != selectedTier;
            }
        }
    }

    private void updateTabVisibility() {
        boolean general = activeTab == Tab.GENERAL;
        for (AbstractWidget widget : generalWidgets) {
            widget.visible = general;
            widget.active = general;
        }
        for (AbstractWidget widget : tierWidgets) {
            widget.visible = !general;
            widget.active = !general;
        }
        tabGeneralBtn.active = !general;
        tabTiersBtn.active = general;
    }

    private void onSave() {
        if (activeTab == Tab.GENERAL) {
            onSaveGeneral();
        } else {
            onSaveTierConfigs();
        }
    }

    private void onSaveGeneral() {
        try {
            double hpScale = Double.parseDouble(hpScalePerPlayerField.getValue());
            int battleTimeLimitTicks = Math.max(0, Integer.parseInt(battleTimeLimitSecsField.getValue()) * 20);
            ClientPlayNetworking.send(new ModPackets.UpdateBossSpawnerPayload(
                    menu.blockEntity.getBlockPos(),
                    mobIdField.getValue(),
                    Integer.parseInt(respawnTimeField.getValue()),
                    lootTableIdField.getValue(),
                    perPlayerLootTableIdField.getValue(),
                    0,
                    Integer.parseInt(battleRadiusField.getValue()),
                    Integer.parseInt(regenerationField.getValue()),
                    1,
                    Integer.parseInt(skillExperienceField.getValue()),
                    battleTimeLimitTicks,
                    hpScale,
                    ""
            ));
            this.onClose();
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format in one of the fields.");
        }
    }

    private void onSaveTierConfigs() {
        captureCurrentTierEdits();
        double hpScale = parseDoubleOrDefault(hpScalePerPlayerField.getValue(), menu.blockEntity.hpScalePerPlayer);
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

    private static double parseDoubleOrDefault(String value, double def) {
        if (value == null || value.isBlank()) return def;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return def;
        }
    }

    private static String formatDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
        if (menu.blockEntity == null) {
            return;
        }
        if (activeTab == Tab.GENERAL) {
            int linkedRespawns = menu.blockEntity.getRespawnPointOffsets().size();
            context.drawString(this.font,
                    Component.translatable("gui.arenas_ld.respawn_points_linked_readonly", linkedRespawns),
                    10,
                    this.imageHeight - 62,
                    0x808080,
                    false);
            return;
        }
        if (activeTab != Tab.TIERS) {
            return;
        }
        captureCurrentTierEdits();
        RaidTierConfig cfg = tierConfigDraft.getOrDefault(selectedTier, RaidTierConfig.defaultFor(selectedTier));
        double hpScale = parseDoubleOrDefault(hpScalePerPlayerField.getValue(), menu.blockEntity.hpScalePerPlayer);
        hpScale = Mth.clamp(hpScale, -0.99, 100.0);

        int x = 10;
        int y = this.imageHeight - 74;
        context.drawString(this.font, Component.translatable("gui.arenas_ld.hp_preview"), x, y, 0xC0C0C0, false);
        y += 12;
        long hp1 = Math.round(menu.blockEntity.computeExpectedHp(selectedTier, 1, cfg, hpScale));
        long hp2 = Math.round(menu.blockEntity.computeExpectedHp(selectedTier, 2, cfg, hpScale));
        long hp4 = Math.round(menu.blockEntity.computeExpectedHp(selectedTier, 4, cfg, hpScale));
        context.drawString(this.font, Component.translatable("gui.arenas_ld.hp_preview_players", 1, hp1), x, y, 0xE0E0E0, false);
        y += 10;
        context.drawString(this.font, Component.translatable("gui.arenas_ld.hp_preview_players", 2, hp2), x, y, 0xE0E0E0, false);
        y += 10;
        context.drawString(this.font, Component.translatable("gui.arenas_ld.hp_preview_players", 4, hp4), x, y, 0xE0E0E0, false);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.renderTooltip(context, mouseX, mouseY);
    }
}
