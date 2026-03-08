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

public class MobArenaSpawnerScreen extends AbstractContainerScreen<MobArenaSpawnerScreenHandler> {

    private EditBox triggerRadiusField;
    private EditBox battleRadiusField;
    private EditBox spawnDistanceField;
    private EditBox waveTimerField;
    private EditBox additionalTimeField;
    private EditBox timeBetweenWavesField;
    private EditBox attributeScaleField;
    private EditBox prepareTimeField;
    private EditBox groupIdField;
    private EditBox bossWaveAdditionalTimeField;
    private EditBox exitPositionField;
    private EditBox exitDimensionField;
    private EditBox arenaEntrancePositionField;
    private EditBox arenaEntranceDimensionField;

    public MobArenaSpawnerScreen(MobArenaSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 470;
        this.imageHeight = 280;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int fieldWidth = 150;
        int fieldHeight = 20;
        int yOffset = 24;
        int columnPadding = 10;

        int col1X = this.leftPos + 5;
        int col2X = col1X + fieldWidth + columnPadding;

        int y;

        // Column 1
        y = 20;
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.trigger_radius"), (button) -> {}, this.font));
        triggerRadiusField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        triggerRadiusField.setMaxLength(4);
        this.addRenderableWidget(triggerRadiusField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.battle_radius"), (button) -> {}, this.font));
        battleRadiusField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        battleRadiusField.setMaxLength(4);
        this.addRenderableWidget(battleRadiusField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.spawn_distance"), (button) -> {}, this.font));
        spawnDistanceField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        spawnDistanceField.setMaxLength(4);
        this.addRenderableWidget(spawnDistanceField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.wave_timer"), (button) -> {}, this.font));
        waveTimerField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        waveTimerField.setMaxLength(8);
        this.addRenderableWidget(waveTimerField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.additional_time"), (button) -> {}, this.font));
        additionalTimeField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        additionalTimeField.setMaxLength(8);
        this.addRenderableWidget(additionalTimeField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.time_between_waves"), (button) -> {}, this.font));
        timeBetweenWavesField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        timeBetweenWavesField.setMaxLength(8);
        this.addRenderableWidget(timeBetweenWavesField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.attribute_scale"), (button) -> {}, this.font));
        attributeScaleField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        attributeScaleField.setMaxLength(8);
        this.addRenderableWidget(attributeScaleField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.prepare_time"), (button) -> {}, this.font));
        prepareTimeField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        prepareTimeField.setMaxLength(8);
        this.addRenderableWidget(prepareTimeField);
        y += (int)(yOffset * 1.7);
        
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.group_id"), (button) -> {}, this.font));
        groupIdField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        groupIdField.setMaxLength(32);
        this.addRenderableWidget(groupIdField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.boss_wave_additional_time"), (button) -> {}, this.font));
        bossWaveAdditionalTimeField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        bossWaveAdditionalTimeField.setMaxLength(8);
        this.addRenderableWidget(bossWaveAdditionalTimeField);

        // Column 2
        y = 20;
        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.arena_entrance_position"), (button) -> {}, this.font));
        arenaEntrancePositionField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        arenaEntrancePositionField.setMaxLength(32);
        this.addRenderableWidget(arenaEntrancePositionField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.arena_entrance_dimension"), (button) -> {}, this.font));
        arenaEntranceDimensionField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        arenaEntranceDimensionField.setMaxLength(128);
        this.addRenderableWidget(arenaEntranceDimensionField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.exit_position"), (button) -> {}, this.font));
        exitPositionField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        exitPositionField.setMaxLength(32);
        this.addRenderableWidget(exitPositionField);
        y += (int)(yOffset * 1.7);

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.exit_dimension"), (button) -> {}, this.font));
        exitDimensionField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        exitDimensionField.setMaxLength(128);
        this.addRenderableWidget(exitDimensionField);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.mobs"), button -> {
            this.minecraft.setScreen(new MobArenaMobsScreen(menu.blockEntity.getBlockPos(), menu.blockEntity.mobs, this));
        }).bounds(this.width / 2 - 105, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.rewards"), button -> {
            this.minecraft.setScreen(new MobArenaRewardsScreen(menu.blockEntity.getBlockPos(), menu.blockEntity.rewards, this));
        }).bounds(this.width / 2 - 105, this.height - 54, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.save"), button -> onSave())
                .bounds(this.width / 2 + 5, this.height - 30, 100, 20)
                .build());

        loadBlockEntityData();
    }

    private void loadBlockEntityData() {
        if (menu.blockEntity != null) {
            triggerRadiusField.setValue(String.valueOf(menu.blockEntity.triggerRadius));
            battleRadiusField.setValue(String.valueOf(menu.blockEntity.battleRadius));
            spawnDistanceField.setValue(String.valueOf(menu.blockEntity.spawnDistance));
            waveTimerField.setValue(String.valueOf(menu.blockEntity.waveTimer));
            additionalTimeField.setValue(String.valueOf(menu.blockEntity.additionalTime));
            timeBetweenWavesField.setValue(String.valueOf(menu.blockEntity.timeBetweenWaves));
            attributeScaleField.setValue(String.valueOf(menu.blockEntity.attributeScale));
            prepareTimeField.setValue(String.valueOf(menu.blockEntity.prepareTime));
            groupIdField.setValue(menu.blockEntity.groupId);
            bossWaveAdditionalTimeField.setValue(String.valueOf(menu.blockEntity.bossWaveAdditionalTime));
            exitPositionField.setValue(String.format("%d %d %d", menu.blockEntity.exitPosition.getX(), menu.blockEntity.exitPosition.getY(), menu.blockEntity.exitPosition.getZ()));
            exitDimensionField.setValue(menu.blockEntity.exitDimension.location().toString());
            arenaEntrancePositionField.setValue(String.format("%d %d %d", menu.blockEntity.arenaEntrancePosition.getX(), menu.blockEntity.arenaEntrancePosition.getY(), menu.blockEntity.arenaEntrancePosition.getZ()));
            arenaEntranceDimensionField.setValue(menu.blockEntity.arenaEntranceDimension.location().toString());
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
        return ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    }

    private void onSave() {
        try {
            ClientPlayNetworking.send(new ModPackets.UpdateMobArenaSpawnerPayload(
                    menu.blockEntity.getBlockPos(),
                    Integer.parseInt(triggerRadiusField.getValue()),
                    Integer.parseInt(battleRadiusField.getValue()),
                    Integer.parseInt(spawnDistanceField.getValue()),
                    Integer.parseInt(waveTimerField.getValue()),
                    Integer.parseInt(additionalTimeField.getValue()),
                    Integer.parseInt(timeBetweenWavesField.getValue()),
                    Double.parseDouble(attributeScaleField.getValue()),
                    Integer.parseInt(prepareTimeField.getValue()),
                    parseCoords(exitPositionField.getValue()),
                    parseDimension(exitDimensionField.getValue()),
                    parseCoords(arenaEntrancePositionField.getValue()),
                    parseDimension(arenaEntranceDimensionField.getValue()),
                    groupIdField.getValue(),
                    Integer.parseInt(bossWaveAdditionalTimeField.getValue())
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
