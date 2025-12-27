package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MobSpawnerScreen extends AbstractContainerScreen<MobSpawnerScreenHandler> {

    private EditBox mobIdField;
    private EditBox respawnTimeField;
    private EditBox triggerRadiusField;
    private EditBox mobCountField;
    private EditBox mobSpreadField;
    private EditBox groupIdField;

    public MobSpawnerScreen(MobSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 150;
        int fieldHeight = 20;
        int yOffset = 24;

        int col1X = (this.width / 2) - fieldWidth - 10;
        int y = 20;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.literal("Mob ID"), (button) -> {}, this.font));
        mobIdField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        mobIdField.setMaxLength(128);
        this.addRenderableWidget(mobIdField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.literal("Mob Count"), (button) -> {}, this.font));
        mobCountField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        mobCountField.setMaxLength(4);
        this.addRenderableWidget(mobCountField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.literal("Group ID"), (button) -> {}, this.font));
        groupIdField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        groupIdField.setMaxLength(128);
        this.addRenderableWidget(groupIdField);
        y += yOffset * 1.7;

        int col2X = (this.width / 2) + 5;
        y = 20;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.literal("Respawn Time (ticks)"), (button) -> {}, this.font));
        respawnTimeField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        respawnTimeField.setMaxLength(8);
        this.addRenderableWidget(respawnTimeField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.literal("Trigger Radius"), (button) -> {}, this.font));
        triggerRadiusField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        triggerRadiusField.setMaxLength(4);
        this.addRenderableWidget(triggerRadiusField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.literal("Mob Spread"), (button) -> {}, this.font));
        mobSpreadField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        mobSpreadField.setMaxLength(4);
        this.addRenderableWidget(mobSpreadField);

        this.addRenderableWidget(Button.builder(Component.literal("Attributes"), button -> {
            this.minecraft.setScreen(new MobAttributesScreen(new MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new MobAttributesData(menu.blockEntity.getBlockPos())), minecraft.player.getInventory(), Component.literal("Mob Attributes")));
        }).bounds(this.width / 2 - 150, this.height - 70, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Equipment"), button -> {
            this.minecraft.setScreen(new EquipmentScreen(new EquipmentScreenHandler(menu.containerId, minecraft.player.getInventory(), new EquipmentScreenData(menu.blockEntity.getBlockPos())), minecraft.player.getInventory(), Component.literal("Mob Equipment")));
        }).bounds(this.width / 2 + 50, this.height - 70, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> onSave())
                .bounds(this.width / 2 - 50, this.height - 50, 100, 20)
                .build());

        loadBlockEntityData();
    }

    private void loadBlockEntityData() {
        if (menu.blockEntity != null) {
            mobIdField.setValue(menu.blockEntity.mobId);
            respawnTimeField.setValue(String.valueOf(menu.blockEntity.respawnTime));
            triggerRadiusField.setValue(String.valueOf(menu.blockEntity.triggerRadius));
            mobCountField.setValue(String.valueOf(menu.blockEntity.mobCount));
            mobSpreadField.setValue(String.valueOf(menu.blockEntity.mobSpread));
            groupIdField.setValue(menu.blockEntity.groupId);
        }
    }

    private void onSave() {
        try {
            ClientPlayNetworking.send(new ModPackets.UpdateMobSpawnerPayload(
                    menu.blockEntity.getBlockPos(),
                    mobIdField.getValue(),
                    Integer.parseInt(respawnTimeField.getValue()),
                    Integer.parseInt(triggerRadiusField.getValue()),
                    Integer.parseInt(mobCountField.getValue()),
                    Integer.parseInt(mobSpreadField.getValue()),
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
