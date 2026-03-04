package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.util.EquipmentData;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClientEquipmentScreen extends Screen {

    private final MobArenaMobData mobData;
    private final Screen parent;
    private EditBox headField;
    private EditBox chestField;
    private EditBox legsField;
    private EditBox feetField;
    private EditBox mainHandField;
    private EditBox offHandField;
    private Checkbox dropChanceCheckbox;
    private boolean firstLoad = true;

    public ClientEquipmentScreen(MobArenaMobData mobData, Screen parent) {
        super(Component.translatable("gui.arenas_ld.mob_equipment"));
        this.mobData = mobData;
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int fieldWidth = 150;
        int fieldHeight = 20;
        int yOffset = 24;
        int x = (this.width - fieldWidth) / 2;
        int y = 40;

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
        EquipmentData data = mobData.equipment;
        headField.setValue(data.head);
        chestField.setValue(data.chest);
        legsField.setValue(data.legs);
        feetField.setValue(data.feet);
        mainHandField.setValue(data.mainHand);
        offHandField.setValue(data.offHand);
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
        mobData.equipment = data;
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
        
        if (firstLoad) {
             EquipmentData data = mobData.equipment;
             if (data.dropChance != dropChanceCheckbox.selected()) {
                 dropChanceCheckbox.onPress();
             }
             firstLoad = false;
        }
    }
}
