package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MobArenaSpawnerScreen extends AbstractContainerScreen<MobArenaSpawnerScreenHandler> {

    private EditBox triggerRadiusField;
    private EditBox battleRadiusField;
    private EditBox spawnDistanceField;
    private EditBox waveTimerField;
    private EditBox additionalTimeField;
    private EditBox timeBetweenWavesField;
    private EditBox attributeScaleField;

    public MobArenaSpawnerScreen(MobArenaSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 200;
        this.imageHeight = 250;
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int fieldWidth = 150;
        int fieldHeight = 20;
        int yOffset = 24;
        int x = (this.width - fieldWidth) / 2;
        int y = 20;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.trigger_radius"), (button) -> {}, this.font));
        triggerRadiusField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        triggerRadiusField.setMaxLength(4);
        this.addRenderableWidget(triggerRadiusField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.battle_radius"), (button) -> {}, this.font));
        battleRadiusField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        battleRadiusField.setMaxLength(4);
        this.addRenderableWidget(battleRadiusField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.spawn_distance"), (button) -> {}, this.font));
        spawnDistanceField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        spawnDistanceField.setMaxLength(4);
        this.addRenderableWidget(spawnDistanceField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.wave_timer"), (button) -> {}, this.font));
        waveTimerField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        waveTimerField.setMaxLength(8);
        this.addRenderableWidget(waveTimerField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.additional_time"), (button) -> {}, this.font));
        additionalTimeField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        additionalTimeField.setMaxLength(8);
        this.addRenderableWidget(additionalTimeField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.time_between_waves"), (button) -> {}, this.font));
        timeBetweenWavesField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        timeBetweenWavesField.setMaxLength(8);
        this.addRenderableWidget(timeBetweenWavesField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.attribute_scale"), (button) -> {}, this.font));
        attributeScaleField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        attributeScaleField.setMaxLength(8);
        this.addRenderableWidget(attributeScaleField);
        y += yOffset * 1.7;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.mobs"), button -> {
            this.minecraft.setScreen(new MobArenaMobsScreen(menu.blockEntity.getBlockPos(), menu.blockEntity.mobs, this));
        }).bounds(this.width / 2 - 105, this.height - 30, 100, 20).build());

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
        }
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
                    Double.parseDouble(attributeScaleField.getValue())
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
