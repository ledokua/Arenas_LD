package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.AttributeData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class MobAttributesScreen extends AbstractContainerScreen<MobAttributesScreenHandler> {

    private final List<AttributeField> attributeFields = new ArrayList<>();

    public MobAttributesScreen(MobAttributesScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageHeight = 220;
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    public void rebuildWidgets() {
        clearWidgets();
        attributeFields.clear();

        int y = 20;
        for (AttributeData attribute : menu.attributes) {
            addAttributeFields(y, attribute);
            y += 24;
        }

        addRenderableWidget(Button.builder(Component.literal("Add"), button -> {
            menu.attributes.add(new AttributeData("minecraft:generic.max_health", 20.0));
            rebuildWidgets();
        }).bounds(this.width / 2 - 100, this.height - 50, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Save"), button -> onSave())
                .bounds(this.width / 2 + 20, this.height - 50, 80, 20)
                .build());
    }

    private void addAttributeFields(int y, AttributeData attribute) {
        int x = this.width / 2 - 155;
        EditBox idField = new EditBox(this.font, x, y, 200, 20, Component.literal("Attribute ID"));
        idField.setMaxLength(128);
        idField.setValue(attribute.id());
        addRenderableWidget(idField);

        x += 205;
        EditBox valueField = new EditBox(this.font, x, y, 60, 20, Component.literal("Value"));
        valueField.setValue(String.valueOf(attribute.value()));
        addRenderableWidget(valueField);

        x += 65;
        Button removeButton = Button.builder(Component.literal("X"), button -> {
            menu.attributes.remove(attribute);
            rebuildWidgets();
        }).bounds(x, y, 20, 20).build();
        addRenderableWidget(removeButton);

        attributeFields.add(new AttributeField(idField, valueField, attribute));
    }

    private void onSave() {
        List<AttributeData> updatedAttributes = new ArrayList<>();
        for (AttributeField field : attributeFields) {
            try {
                String id = field.idField.getValue();
                double value = Double.parseDouble(field.valueField.getValue());
                updatedAttributes.add(new AttributeData(id, value));
            } catch (NumberFormatException e) {
                // Handle error
            }
        }

        ClientPlayNetworking.send(new ModPackets.UpdateAttributesPayload(
                menu.blockEntity.getBlockPos(),
                updatedAttributes
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

    private record AttributeField(EditBox idField, EditBox valueField, AttributeData originalAttribute) {}
}
