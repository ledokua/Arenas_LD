package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.EquipmentData;
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
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 150;
        int fieldHeight = 20;
        int yOffset = 24;
        int x = (this.width - fieldWidth) / 2;
        int y = 40; // Moved down to make space for title

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.head_item_id"), (button) -> {}, this.font));
        headField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        headField.setMaxLength(128);
        this.addRenderableWidget(headField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.chest_item_id"), (button) -> {}, this.font));
        chestField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        chestField.setMaxLength(128);
        this.addRenderableWidget(chestField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.legs_item_id"), (button) -> {}, this.font));
        legsField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        legsField.setMaxLength(128);
        this.addRenderableWidget(legsField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.feet_item_id"), (button) -> {}, this.font));
        feetField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        feetField.setMaxLength(128);
        this.addRenderableWidget(feetField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.main_hand_item_id"), (button) -> {}, this.font));
        mainHandField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        mainHandField.setMaxLength(128);
        this.addRenderableWidget(mainHandField);
        y += yOffset * 1.7;

        addRenderableWidget(new net.minecraft.client.gui.components.PlainTextButton(x, y - 15, fieldWidth, fieldHeight, Component.translatable("gui.arenas_ld.off_hand_item_id"), (button) -> {}, this.font));
        offHandField = new EditBox(this.font, x, y, fieldWidth, fieldHeight, Component.literal(""));
        offHandField.setMaxLength(128);
        this.addRenderableWidget(offHandField);
        y += yOffset;

        dropChanceCheckbox = Checkbox.builder(Component.translatable("gui.arenas_ld.enable_drops"), this.font).pos(x, y).build();
        this.addRenderableWidget(dropChanceCheckbox);
        y += yOffset;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.save"), button -> onSave())
                .bounds(this.width / 2 - 50, this.height - 30, 100, 20)
                .build());

        loadData();
    }

    private void loadData() {
        if (menu.equipmentProvider != null) {
            EquipmentData data = menu.equipmentProvider.getEquipment();
            headField.setValue(data.head);
            chestField.setValue(data.chest);
            legsField.setValue(data.legs);
            feetField.setValue(data.feet);
            mainHandField.setValue(data.mainHand);
            offHandField.setValue(data.offHand);
            if (data.dropChance) {
                // dropChanceCheckbox.onPress(); // Handled in render
            }
        }
    }

    private void onSave() {
        EquipmentData data = new EquipmentData(
                headField.getValue(),
                chestField.getValue(),
                legsField.getValue(),
                feetField.getValue(),
                mainHandField.getValue(),
                offHandField.getValue(),
                dropChanceCheckbox.selected()
        );
        
        ClientPlayNetworking.send(new ModPackets.UpdateEquipmentPayload(
                menu.blockEntity.getBlockPos(),
                data
        ));
        this.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
        // No background texture
    }

    @Override
    protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
        // Disable default labels to prevent overlap and wrong positioning
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        this.renderTooltip(context, mouseX, mouseY);
        
        if (menu.equipmentProvider != null && firstLoad) {
             EquipmentData data = menu.equipmentProvider.getEquipment();
             if (data.dropChance != dropChanceCheckbox.selected()) {
                 dropChanceCheckbox.onPress();
             }
             firstLoad = false;
        }
    }
    
    private boolean firstLoad = true;
}
