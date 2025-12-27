package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.EquipmentProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class EquipmentScreen extends AbstractContainerScreen<EquipmentScreenHandler> {

    private EditBox headField;
    private EditBox chestField;
    private EditBox legsField;
    private EditBox feetField;
    private EditBox mainHandField;
    private EditBox offHandField;
    private Checkbox dropChanceCheckbox;

    public EquipmentScreen(EquipmentScreenHandler handler, Inventory inventory, Component title) {
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

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.literal("Head"), (button) -> {}, this.font));
        headField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        headField.setMaxLength(128);
        this.addRenderableWidget(headField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.literal("Chest"), (button) -> {}, this.font));
        chestField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        chestField.setMaxLength(128);
        this.addRenderableWidget(chestField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col1X, y - 15, fieldWidth, fieldHeight, Component.literal("Legs"), (button) -> {}, this.font));
        legsField = new EditBox(this.font, col1X, y, fieldWidth, fieldHeight, Component.literal(""));
        legsField.setMaxLength(128);
        this.addRenderableWidget(legsField);
        y += yOffset * 1.7;

        int col2X = (this.width / 2) + 5;
        y = 20;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.literal("Feet"), (button) -> {}, this.font));
        feetField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        feetField.setMaxLength(128);
        this.addRenderableWidget(feetField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.literal("Main Hand"), (button) -> {}, this.font));
        mainHandField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        mainHandField.setMaxLength(128);
        this.addRenderableWidget(mainHandField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(col2X, y - 15, fieldWidth, fieldHeight, Component.literal("Off Hand"), (button) -> {}, this.font));
        offHandField = new EditBox(this.font, col2X, y, fieldWidth, fieldHeight, Component.literal(""));
        offHandField.setMaxLength(128);
        this.addRenderableWidget(offHandField);

        boolean initialDropChance = false;
        if (menu.blockEntity instanceof EquipmentProvider provider) {
            initialDropChance = provider.getEquipment().dropChance;
        }

        dropChanceCheckbox = Checkbox.builder(Component.literal("Guaranteed Drop"), this.font)
                .pos(this.width / 2 - 75, this.height - 70)
                .selected(initialDropChance)
                .build();
        this.addRenderableWidget(dropChanceCheckbox);

        this.addRenderableWidget(Button.builder(Component.literal("Save"), button -> onSave())
                .bounds(this.width / 2 - 50, this.height - 50, 100, 20)
                .build());

        loadBlockEntityData();
    }

    private void loadBlockEntityData() {
        if (menu.blockEntity instanceof EquipmentProvider provider) {
            EquipmentData data = provider.getEquipment();
            headField.setValue(data.head);
            chestField.setValue(data.chest);
            legsField.setValue(data.legs);
            feetField.setValue(data.feet);
            mainHandField.setValue(data.mainHand);
            offHandField.setValue(data.offHand);
        }
    }

    private void onSave() {
        EquipmentData data = new EquipmentData();
        data.head = headField.getValue();
        data.chest = chestField.getValue();
        data.legs = legsField.getValue();
        data.feet = feetField.getValue();
        data.mainHand = mainHandField.getValue();
        data.offHand = offHandField.getValue();
        data.dropChance = dropChanceCheckbox.selected();

        ClientPlayNetworking.send(new ModPackets.UpdateEquipmentPayload(
                menu.blockEntity.getBlockPos(),
                data
        ));
        this.onClose();
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
