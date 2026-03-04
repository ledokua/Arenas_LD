package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ClientMobAttributesScreen extends Screen {

    private final MobArenaMobData mobData;
    private final Screen parent;
    private final List<AttributeField> attributeFields = new ArrayList<>();
    private final List<AttributeData> workingAttributes;

    public ClientMobAttributesScreen(MobArenaMobData mobData, Screen parent) {
        super(Component.translatable("gui.arenas_ld.mob_attributes"));
        this.mobData = mobData;
        this.parent = parent;
        this.workingAttributes = new ArrayList<>(mobData.attributes);
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    public void rebuildWidgets() {
        clearWidgets();
        attributeFields.clear();

        int y = 40;
        for (AttributeData attribute : workingAttributes) {
            addAttributeFields(y, attribute);
            y += 24;
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.add"), button -> {
            workingAttributes.add(new AttributeData("minecraft:generic.max_health", 20.0));
            rebuildWidgets();
        }).bounds(this.width / 2 - 100, this.height - 30, 80, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.save"), button -> onSave())
                .bounds(this.width / 2 + 20, this.height - 30, 80, 20)
                .build());
    }

    private void addAttributeFields(int y, AttributeData attribute) {
        int x = this.width / 2 - 155;
        EditBox idField = new EditBox(this.font, x, y, 200, 20, Component.translatable("gui.arenas_ld.attribute_id"));
        idField.setMaxLength(128);
        idField.setValue(attribute.id());
        addRenderableWidget(idField);

        x += 205;
        EditBox valueField = new EditBox(this.font, x, y, 60, 20, Component.translatable("gui.arenas_ld.value"));
        valueField.setValue(String.valueOf(attribute.value()));
        addRenderableWidget(valueField);

        x += 65;
        Button removeButton = Button.builder(Component.translatable("gui.arenas_ld.remove"), button -> {
            workingAttributes.remove(attribute);
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
        mobData.attributes = updatedAttributes;
        this.minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
        super.render(context, mouseX, mouseY, delta);
    }

    private record AttributeField(EditBox idField, EditBox valueField, AttributeData originalAttribute) {}
}
