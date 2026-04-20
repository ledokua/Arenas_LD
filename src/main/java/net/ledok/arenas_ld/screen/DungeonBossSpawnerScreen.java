package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.DifficultyTier;
import net.ledok.arenas_ld.util.TierConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.EnumMap;
import java.util.Map;

public class DungeonBossSpawnerScreen extends AbstractContainerScreen<DungeonBossSpawnerScreenHandler> {

    private EditBox mobIdField;
    private EditBox respawnTimeField;
    private EditBox dungeonCloseTimerField;
    private EditBox dungeonTimeField;
    private EditBox lootTableIdField;
    private EditBox perPlayerLootTableIdField;
    private EditBox exitPositionCoordsField;
    private EditBox exitPositionDimensionField;
    private EditBox triggerRadiusField;
    private EditBox battleRadiusField;
    private EditBox regenerationField;
    private EditBox skillExperienceField;
    private EditBox groupIdField;
    private Button selectedTierButton;
    private EditBox tierHealthMultiplierField;
    private EditBox tierDamageMultiplierField;
    private EditBox tierLootTableField;
    private EditBox tierPerPlayerLootTableField;
    private EditBox tierHardcoreOverrideField;
    private DifficultyTier selectedTier = DifficultyTier.NORMAL;
    private final Map<DifficultyTier, TierConfig> tierConfigs = new EnumMap<>(DifficultyTier.class);

    public DungeonBossSpawnerScreen(DungeonBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 470; // Adjusted for three columns (3*150 + 2*10 padding)
        this.imageHeight = 280; // Adjusted height to fit content
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets(); // Clear existing widgets to re-add them

        int fieldWidth = 150;
        int fieldHeight = 20;
        int yOffset = 24;
        int columnPadding = 10;

        // Calculate column X positions relative to the screen's left edge
        int col1X = this.leftPos + 5; // Small margin from the left edge of the screen
        int col2X = col1X + fieldWidth + columnPadding;
        int col3X = col2X + fieldWidth + columnPadding;

        int currentY;

        // --- Column 1: General Settings ---
        currentY = 20;
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.mob_id"), (button) -> {}, this.font));
        mobIdField = new EditBox(this.font, col1X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        mobIdField.setMaxLength(128);
        this.addRenderableWidget(mobIdField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.loot_table_id"), (button) -> {}, this.font));
        lootTableIdField = new EditBox(this.font, col1X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        lootTableIdField.setMaxLength(128);
        this.addRenderableWidget(lootTableIdField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.per_player_loot_table_id"), (button) -> {}, this.font));
        perPlayerLootTableIdField = new EditBox(this.font, col1X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        perPlayerLootTableIdField.setMaxLength(128);
        this.addRenderableWidget(perPlayerLootTableIdField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.group_id"), (button) -> {}, this.font));
        groupIdField = new EditBox(this.font, col1X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        groupIdField.setMaxLength(128);
        this.addRenderableWidget(groupIdField);


        // --- Column 2: Numerical Settings ---
        currentY = 20;
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.respawn_time"), (button) -> {}, this.font));
        respawnTimeField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        respawnTimeField.setMaxLength(8);
        this.addRenderableWidget(respawnTimeField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.dungeon_close_timer"), (button) -> {}, this.font));
        dungeonCloseTimerField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        dungeonCloseTimerField.setMaxLength(8);
        this.addRenderableWidget(dungeonCloseTimerField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.dungeon_time"), (button) -> {}, this.font));
        dungeonTimeField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        dungeonTimeField.setMaxLength(8);
        this.addRenderableWidget(dungeonTimeField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.trigger_radius"), (button) -> {}, this.font));
        triggerRadiusField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        triggerRadiusField.setMaxLength(4);
        this.addRenderableWidget(triggerRadiusField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.battle_radius"), (button) -> {}, this.font));
        battleRadiusField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        battleRadiusField.setMaxLength(4);
        this.addRenderableWidget(battleRadiusField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.regeneration"), (button) -> {}, this.font));
        regenerationField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        regenerationField.setMaxLength(4);
        this.addRenderableWidget(regenerationField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.skill_xp"), (button) -> {}, this.font));
        skillExperienceField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        skillExperienceField.setMaxLength(8);
        this.addRenderableWidget(skillExperienceField);


        // --- Column 3: Exit Settings ---
        currentY = 20;
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.exit_position"), (button) -> {}, this.font));
        exitPositionCoordsField = new EditBox(this.font, col3X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        exitPositionCoordsField.setMaxLength(32);
        this.addRenderableWidget(exitPositionCoordsField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.exit_position_dim"), (button) -> {}, this.font));
        exitPositionDimensionField = new EditBox(this.font, col3X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        exitPositionDimensionField.setMaxLength(128);
        this.addRenderableWidget(exitPositionDimensionField);


        // --- Action Buttons ---
        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.attributes"), button -> {
            this.minecraft.setScreen(new MobAttributesScreen(new MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new MobAttributesData(menu.blockEntity.getBlockPos())), minecraft.player.getInventory(), Component.translatable("gui.arenas_ld.boss_attributes")));
        }).bounds(this.leftPos + this.imageWidth / 2 - 150, this.height - 70, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.equipment"), button -> {
            this.minecraft.setScreen(new EquipmentScreen(new EquipmentScreenHandler(menu.containerId, minecraft.player.getInventory(), new EquipmentScreenData(menu.blockEntity.getBlockPos())), minecraft.player.getInventory(), Component.translatable("gui.arenas_ld.boss_equipment")));
        }).bounds(this.leftPos + this.imageWidth / 2 + 50, this.height - 70, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.save"), button -> onSave())
                .bounds(this.leftPos + this.imageWidth / 2 - 50, this.height - 50, 100, 20)
                .build());

        int tierY = this.topPos + 165;
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, tierY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_selected"), (button) -> {}, this.font));
        selectedTierButton = this.addRenderableWidget(Button.builder(Component.empty(), button -> {
            saveCurrentTierConfigFromFields();
            DifficultyTier[] tiers = DifficultyTier.values();
            selectedTier = tiers[(selectedTier.ordinal() + 1) % tiers.length];
            refreshTierControls();
        }).bounds(col3X, tierY, fieldWidth, fieldHeight).build());

        tierY += (int) (yOffset * 1.7);
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, tierY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_health_mult"), (button) -> {}, this.font));
        tierHealthMultiplierField = new EditBox(this.font, col3X, tierY, fieldWidth, fieldHeight, Component.literal(""));
        this.addRenderableWidget(tierHealthMultiplierField);

        tierY += (int) (yOffset * 1.7);
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, tierY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_damage_mult"), (button) -> {}, this.font));
        tierDamageMultiplierField = new EditBox(this.font, col3X, tierY, fieldWidth, fieldHeight, Component.literal(""));
        this.addRenderableWidget(tierDamageMultiplierField);

        tierY += (int) (yOffset * 1.7);
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, tierY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_loot_table"), (button) -> {}, this.font));
        tierLootTableField = new EditBox(this.font, col3X, tierY, fieldWidth, fieldHeight, Component.literal(""));
        this.addRenderableWidget(tierLootTableField);

        tierY += (int) (yOffset * 1.7);
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, tierY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_per_player_loot_table"), (button) -> {}, this.font));
        tierPerPlayerLootTableField = new EditBox(this.font, col3X, tierY, fieldWidth, fieldHeight, Component.literal(""));
        this.addRenderableWidget(tierPerPlayerLootTableField);

        tierY += (int) (yOffset * 1.7);
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, tierY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.tier_hardcore_override"), (button) -> {}, this.font));
        tierHardcoreOverrideField = new EditBox(this.font, col3X, tierY, fieldWidth, fieldHeight, Component.literal(""));
        tierHardcoreOverrideField.setMaxLength(16);
        this.addRenderableWidget(tierHardcoreOverrideField);

        loadBlockEntityData();
    }

    private void loadBlockEntityData() {
        if (menu.blockEntity != null) {
            mobIdField.setValue(menu.blockEntity.getMobId());
            respawnTimeField.setValue(String.valueOf(menu.blockEntity.getRespawnTime()));
            dungeonCloseTimerField.setValue(String.valueOf(menu.blockEntity.getDungeonCloseTimer()));
            dungeonTimeField.setValue(String.valueOf(menu.blockEntity.getDungeonTime()));
            lootTableIdField.setValue(menu.blockEntity.getLootTableId());
            perPlayerLootTableIdField.setValue(menu.blockEntity.getPerPlayerLootTableId());
            exitPositionCoordsField.setValue(String.format("%d %d %d", menu.blockEntity.getExitPositionCoords().getX(), menu.blockEntity.getExitPositionCoords().getY(), menu.blockEntity.getExitPositionCoords().getZ()));
            exitPositionDimensionField.setValue(menu.blockEntity.getExitPositionDimension().location().toString());
            triggerRadiusField.setValue(String.valueOf(menu.blockEntity.getTriggerRadius()));
            battleRadiusField.setValue(String.valueOf(menu.blockEntity.getBattleRadius()));
            regenerationField.setValue(String.valueOf(menu.blockEntity.getRegeneration()));
            skillExperienceField.setValue(String.valueOf(menu.blockEntity.getSkillExperiencePerWin()));
            groupIdField.setValue(menu.blockEntity.getGroupId());
            tierConfigs.clear();
            for (DifficultyTier tier : DifficultyTier.values()) {
                TierConfig source = menu.blockEntity.getTierConfigs().get(tier);
                TierConfig copy = new TierConfig();
                if (source != null) {
                    copy.healthMultiplierOverride = source.healthMultiplierOverride;
                    copy.damageMultiplierOverride = source.damageMultiplierOverride;
                    copy.lootTableIdOverride = source.lootTableIdOverride;
                    copy.perPlayerLootTableIdOverride = source.perPlayerLootTableIdOverride;
                    copy.hardcoreOverride = source.hardcoreOverride;
                }
                tierConfigs.put(tier, copy);
            }
            selectedTier = menu.blockEntity.getActiveTier();
            refreshTierControls();
        }
    }

    private BlockPos parseCoords(String text) {
        try {
            String[] parts = text.split(" ");
            if (parts.length == 3) {
                return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
        } catch (NumberFormatException ignored) {
        }
        return BlockPos.ZERO;
    }

    private ResourceLocation parseDimension(String text) {
        try {
            return ResourceLocation.parse(text);
        } catch (Exception ignored) {
        }
        return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld"); // Default to overworld
    }

    private void onSave() {
        try {
            saveCurrentTierConfigFromFields();
            ClientPlayNetworking.send(new ModPackets.UpdateDungeonBossSpawnerPayload(
                    menu.blockEntity.getBlockPos(),
                    mobIdField.getValue(),
                    Integer.parseInt(respawnTimeField.getValue()),
                    Integer.parseInt(dungeonCloseTimerField.getValue()),
                    Integer.parseInt(dungeonTimeField.getValue()),
                    lootTableIdField.getValue(),
                    perPlayerLootTableIdField.getValue(),
                    parseCoords(exitPositionCoordsField.getValue()),
                    parseDimension(exitPositionDimensionField.getValue()),
                    Integer.parseInt(triggerRadiusField.getValue()),
                    Integer.parseInt(battleRadiusField.getValue()),
                    Integer.parseInt(regenerationField.getValue()),
                    Integer.parseInt(skillExperienceField.getValue()),
                    groupIdField.getValue()
            ));
            CompoundTag tierConfigsTag = new CompoundTag();
            for (DifficultyTier tier : DifficultyTier.values()) {
                TierConfig config = tierConfigs.getOrDefault(tier, new TierConfig());
                tierConfigsTag.put(tier.name(), config.toNbt());
            }
            ClientPlayNetworking.send(new ModPackets.UpdateDungeonTierConfigsPayload(menu.blockEntity.getBlockPos(), tierConfigsTag));
            this.onClose();
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format in one of the fields.");
        }
    }

    private void saveCurrentTierConfigFromFields() {
        TierConfig config = tierConfigs.computeIfAbsent(selectedTier, unused -> new TierConfig());
        config.healthMultiplierOverride = parseNullableDouble(tierHealthMultiplierField.getValue());
        config.damageMultiplierOverride = parseNullableDouble(tierDamageMultiplierField.getValue());
        config.lootTableIdOverride = tierLootTableField.getValue().trim();
        config.perPlayerLootTableIdOverride = tierPerPlayerLootTableField.getValue().trim();
        config.hardcoreOverride = parseNullableBoolean(tierHardcoreOverrideField.getValue());
    }

    private void refreshTierControls() {
        if (selectedTierButton != null) {
            selectedTierButton.setMessage(Component.translatable(selectedTier.translationKey()));
        }
        TierConfig config = tierConfigs.getOrDefault(selectedTier, new TierConfig());
        tierHealthMultiplierField.setValue(config.healthMultiplierOverride != null ? String.valueOf(config.healthMultiplierOverride) : "");
        tierDamageMultiplierField.setValue(config.damageMultiplierOverride != null ? String.valueOf(config.damageMultiplierOverride) : "");
        tierLootTableField.setValue(config.lootTableIdOverride != null ? config.lootTableIdOverride : "");
        tierPerPlayerLootTableField.setValue(config.perPlayerLootTableIdOverride != null ? config.perPlayerLootTableIdOverride : "");
        tierHardcoreOverrideField.setValue(config.hardcoreOverride != null ? config.hardcoreOverride.toString() : "");
    }

    private Double parseNullableDouble(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(trimmed);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Boolean parseNullableBoolean(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return Boolean.parseBoolean(trimmed);
        }
        return null;
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.renderTooltip(context, mouseX, mouseY);
    }
}
