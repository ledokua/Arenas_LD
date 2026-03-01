package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class BossSpawnerScreen extends AbstractContainerScreen<BossSpawnerScreenHandler> {

    private EditBox mobIdField;
    private EditBox respawnTimeField;
    private EditBox portalActiveTimeField;
    private EditBox lootTableIdField;
    private EditBox perPlayerLootTableIdField;
    private EditBox exitPortalCoordsField;
    private EditBox exitDimensionField;
    private EditBox triggerRadiusField;
    private EditBox battleRadiusField;
    private EditBox regenerationField;
    private EditBox enterPortalSpawnCoordsField;
    private EditBox enterPortalSpawnDimensionField;
    private EditBox enterPortalDestCoordsField;
    private EditBox enterPortalDestDimensionField;
    private EditBox minPlayersField;
    private EditBox skillExperienceField;
    private EditBox groupIdField;

    public BossSpawnerScreen(BossSpawnerScreenHandler handler, Inventory inventory, Component title) {
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

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.portal_active_time"), (button) -> {}, this.font));
        portalActiveTimeField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        portalActiveTimeField.setMaxLength(8);
        this.addRenderableWidget(portalActiveTimeField);
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

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.min_players"), (button) -> {}, this.font));
        minPlayersField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        minPlayersField.setMaxLength(3);
        this.addRenderableWidget(minPlayersField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.skill_xp"), (button) -> {}, this.font));
        skillExperienceField = new EditBox(this.font, col2X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        skillExperienceField.setMaxLength(8);
        this.addRenderableWidget(skillExperienceField);


        // --- Column 3: Portal Settings ---
        currentY = 20;
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.enter_portal_spawn"), (button) -> {}, this.font));
        enterPortalSpawnCoordsField = new EditBox(this.font, col3X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        enterPortalSpawnCoordsField.setMaxLength(32);
        this.addRenderableWidget(enterPortalSpawnCoordsField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.enter_portal_spawn_dim"), (button) -> {}, this.font));
        enterPortalSpawnDimensionField = new EditBox(this.font, col3X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        enterPortalSpawnDimensionField.setMaxLength(128);
        this.addRenderableWidget(enterPortalSpawnDimensionField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.enter_portal_dest"), (button) -> {}, this.font));
        enterPortalDestCoordsField = new EditBox(this.font, col3X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        enterPortalDestCoordsField.setMaxLength(32);
        this.addRenderableWidget(enterPortalDestCoordsField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.enter_portal_dest_dim"), (button) -> {}, this.font));
        enterPortalDestDimensionField = new EditBox(this.font, col3X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        enterPortalDestDimensionField.setMaxLength(128);
        this.addRenderableWidget(enterPortalDestDimensionField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.exit_portal_coords"), (button) -> {}, this.font));
        exitPortalCoordsField = new EditBox(this.font, col3X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        exitPortalCoordsField.setMaxLength(32);
        this.addRenderableWidget(exitPortalCoordsField);
        currentY += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col3X, currentY - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.exit_portal_dim"), (button) -> {}, this.font));
        exitDimensionField = new EditBox(this.font, col3X, currentY, fieldWidth, fieldHeight, Component.literal(""));
        exitDimensionField.setMaxLength(128);
        this.addRenderableWidget(exitDimensionField);


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

        loadBlockEntityData();
    }

    private void loadBlockEntityData() {
        if (menu.blockEntity != null) {
            mobIdField.setValue(menu.blockEntity.mobId);
            respawnTimeField.setValue(String.valueOf(menu.blockEntity.respawnTime));
            portalActiveTimeField.setValue(String.valueOf(menu.blockEntity.portalActiveTime));
            lootTableIdField.setValue(menu.blockEntity.lootTableId);
            perPlayerLootTableIdField.setValue(menu.blockEntity.perPlayerLootTableId);
            exitPortalCoordsField.setValue(String.format("%d %d %d", menu.blockEntity.exitPortalCoords.getX(), menu.blockEntity.exitPortalCoords.getY(), menu.blockEntity.exitPortalCoords.getZ()));
            exitDimensionField.setValue(menu.blockEntity.exitDimension.location().toString());
            triggerRadiusField.setValue(String.valueOf(menu.blockEntity.triggerRadius));
            battleRadiusField.setValue(String.valueOf(menu.blockEntity.battleRadius));
            regenerationField.setValue(String.valueOf(menu.blockEntity.regeneration));
            enterPortalSpawnCoordsField.setValue(String.format("%d %d %d", menu.blockEntity.enterPortalSpawnCoords.getX(), menu.blockEntity.enterPortalSpawnCoords.getY(), menu.blockEntity.enterPortalSpawnCoords.getZ()));
            enterPortalSpawnDimensionField.setValue(menu.blockEntity.enterPortalSpawnDimension.location().toString());
            enterPortalDestCoordsField.setValue(String.format("%d %d %d", menu.blockEntity.enterPortalDestCoords.getX(), menu.blockEntity.enterPortalDestCoords.getY(), menu.blockEntity.enterPortalDestCoords.getZ()));
            enterPortalDestDimensionField.setValue(menu.blockEntity.enterPortalDestDimension.location().toString());
            minPlayersField.setValue(String.valueOf(menu.blockEntity.minPlayers));
            skillExperienceField.setValue(String.valueOf(menu.blockEntity.skillExperiencePerWin));
            groupIdField.setValue(menu.blockEntity.groupId);
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
            ClientPlayNetworking.send(new ModPackets.UpdateBossSpawnerPayload(
                    menu.blockEntity.getBlockPos(),
                    mobIdField.getValue(),
                    Integer.parseInt(respawnTimeField.getValue()),
                    Integer.parseInt(portalActiveTimeField.getValue()),
                    lootTableIdField.getValue(),
                    perPlayerLootTableIdField.getValue(),
                    parseCoords(exitPortalCoordsField.getValue()),
                    parseDimension(exitDimensionField.getValue()),
                    parseCoords(enterPortalSpawnCoordsField.getValue()),
                    parseDimension(enterPortalSpawnDimensionField.getValue()),
                    parseCoords(enterPortalDestCoordsField.getValue()),
                    parseDimension(enterPortalDestDimensionField.getValue()),
                    Integer.parseInt(triggerRadiusField.getValue()),
                    Integer.parseInt(battleRadiusField.getValue()),
                    Integer.parseInt(regenerationField.getValue()),
                    Integer.parseInt(minPlayersField.getValue()),
                    Integer.parseInt(skillExperienceField.getValue()),
                    groupIdField.getValue()
            ));
            this.onClose();
        } catch (NumberFormatException e) {
            System.err.println("Invalid number format in one of the fields.");
        }
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
