package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.UpdateMobSpawnerEntityDefPayload;
import net.ledok.arenas_ld.screen.EquipmentScreen;
import net.ledok.arenas_ld.screen.EquipmentScreenData;
import net.ledok.arenas_ld.screen.EquipmentScreenHandler;
import net.ledok.arenas_ld.screen.MobAttributesData;
import net.ledok.arenas_ld.screen.MobAttributesScreen;
import net.ledok.arenas_ld.screen.MobAttributesScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class MobSpawnerScreen extends AbstractContainerScreen<MobSpawnerScreenHandler> {
    private static final int WIDTH = 250;
    private static final int HEIGHT = 220;

    private EditBox mobIdField;

    public MobSpawnerScreen(MobSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = this.imageHeight + 1000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        mobIdField = new EditBox(font, x + 70, y + 34, 165, 16, Component.empty());
        mobIdField.setValue(menu.getMobId());
        mobIdField.setResponder(value -> ClientPlayNetworking.send(new UpdateMobSpawnerEntityDefPayload(menu.getBlockPos(), value)));
        addRenderableWidget(mobIdField);

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.attributes"), b -> {
            if (minecraft != null && minecraft.player != null) {
                minecraft.setScreen(new MobAttributesScreen(
                    new MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new MobAttributesData(menu.getBlockPos())),
                    minecraft.player.getInventory(),
                    Component.translatable("gui.arenas_ld.mob_attributes")
                ));
            }
        }).bounds(x + 10, y + 72, 120, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.equipment"), b -> {
            if (minecraft != null && minecraft.player != null) {
                minecraft.setScreen(new EquipmentScreen(
                    new EquipmentScreenHandler(menu.containerId, minecraft.player.getInventory(), new EquipmentScreenData(menu.getBlockPos())),
                    minecraft.player.getInventory(),
                    Component.translatable("gui.arenas_ld.mob_equipment")
                ));
            }
        }).bounds(x + 10, y + 98, 120, 20).build());

        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
            .bounds(x + WIDTH - 22, y + 6, 16, 14).build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF121620);
        guiGraphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 20, 0xFF1A2130);
        guiGraphics.fill(x + 8, y + 28, x + WIDTH - 8, y + 58, 0xFF0E1320);
        guiGraphics.fill(x + 8, y + 68, x + WIDTH - 8, y + 128, 0xFF0E1320);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(font, title, x + 8, y + 8, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.mob_id"), x + 12, y + 37, 0xC0C8E0, false);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
