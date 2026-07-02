package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.RoomClearDoorPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearSpawnersPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomRemoveSpawnerPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomResetPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomSetNamePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class RoomControllerScreen extends AbstractContainerScreen<RoomControllerScreenHandler> {
    private static final int WIDTH = 250;
    private static final int HEIGHT = 186;
    private static final int MAX_ROWS = 5;

    private final List<net.minecraft.core.BlockPos> spawnerPositions;
    private List<net.minecraft.core.BlockPos> doorPositions;
    private int scrollOffset;
    private String roomNameInput;
    private EditBox roomNameField;

    public RoomControllerScreen(RoomControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.spawnerPositions = new ArrayList<>(handler.getSpawnerPositions());
        this.doorPositions = handler.getDoorPositions();
        this.roomNameInput = handler.getRoomName();
        this.inventoryLabelY = this.imageHeight + 1000;
    }

    @Override
    protected void init() {
        super.init();
        syncFromMenu();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int x = leftPos;
        int y = topPos;

        if (scrollOffset > Math.max(0, spawnerPositions.size() - MAX_ROWS)) {
            scrollOffset = Math.max(0, spawnerPositions.size() - MAX_ROWS);
        }

        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
            .bounds(x + WIDTH - 22, y + 3, 16, 12).build());

        roomNameField = new EditBox(font, x + 10, y + 22, 186, 12, Component.translatable("gui.arenas_ld.room_controller.name_label"));
        roomNameField.setMaxLength(48);
        roomNameField.setValue(roomNameInput == null ? "" : roomNameInput);
        roomNameField.setResponder(v -> roomNameInput = v);
        addRenderableWidget(roomNameField);

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.set_name"), b -> {
            ClientPlayNetworking.send(new RoomSetNamePayload(menu.getBlockPos(), roomNameInput == null ? "" : roomNameInput));
        }).bounds(x + 200, y + 21, 44, 14).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.clear_all"), b -> {
            minecraft.setScreen(new ConfirmScreen(
                ok -> {
                    minecraft.setScreen(this);
                    if (ok) {
                        ClientPlayNetworking.send(new RoomClearSpawnersPayload(menu.getBlockPos()));
                    }
                },
                Component.translatable("gui.arenas_ld.room_controller.confirm.clear_title"),
                Component.translatable("gui.arenas_ld.room_controller.confirm.clear_message")
            ));
        }).bounds(x + WIDTH - 80, y + 123, 72, 13).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.clear_door"), b -> {
            ClientPlayNetworking.send(new RoomClearDoorPayload(menu.getBlockPos()));
        }).bounds(x + WIDTH - 80, y + 143, 72, 14).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.reset"), b -> {
            minecraft.setScreen(new ConfirmScreen(
                ok -> {
                    minecraft.setScreen(this);
                    if (ok) {
                        ClientPlayNetworking.send(new RoomResetPayload(menu.getBlockPos()));
                    }
                },
                Component.translatable("gui.arenas_ld.room_controller.confirm.reset_title"),
                Component.translatable("gui.arenas_ld.room_controller.confirm.reset_message")
            ));
        }).bounds(x + WIDTH - 80, y + 165, 72, 14).build());

        int visible = Math.min(MAX_ROWS, Math.max(0, spawnerPositions.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            int index = scrollOffset + i;
            net.minecraft.core.BlockPos pos = spawnerPositions.get(index);
            int rowY = y + 52 + i * 13;
            addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.remove"), b -> {
                ClientPlayNetworking.send(new RoomRemoveSpawnerPayload(menu.getBlockPos(), pos));
            }).bounds(x + WIDTH - 62, rowY, 54, 12).build());
        }

        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            scrollOffset = Math.max(0, scrollOffset - 1);
            rebuildWidgets();
        }).bounds(x + 8, y + 123, 16, 13).build()).active = scrollOffset > 0;

        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            scrollOffset = Math.min(Math.max(0, spawnerPositions.size() - MAX_ROWS), scrollOffset + 1);
            rebuildWidgets();
        }).bounds(x + 26, y + 123, 16, 13).build()).active = scrollOffset < Math.max(0, spawnerPositions.size() - MAX_ROWS);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF121620);
        guiGraphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 16, 0xFF1A2130);
        guiGraphics.fill(x + 6, y + 19, x + WIDTH - 6, y + 37, 0xFF0E1320);
        guiGraphics.fill(x + 6, y + 48, x + WIDTH - 6, y + 121, 0xFF0E1320);
        guiGraphics.fill(x + 6, y + 139, x + WIDTH - 6, y + 161, 0xFF0E1320);
        guiGraphics.fill(x + 6, y + 164, x + WIDTH - 6, y + 180, 0xFF0E1320);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(font, this.title, x + 8, y + 6, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.spawners_label"), x + 8, y + 44, 0xC0C8E0, false);

        int visible = Math.min(MAX_ROWS, Math.max(0, spawnerPositions.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            net.minecraft.core.BlockPos pos = spawnerPositions.get(scrollOffset + i);
            int rowY = y + 55 + i * 13;
            guiGraphics.drawString(font, "- " + pos.toShortString(), x + 10, rowY, 0xE0E0E0, false);
        }

        if (spawnerPositions.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.spawners_empty"), x + 10, y + 55, 0x808AA6, false);
        }

        Component doorValue = doorPositions.isEmpty()
            ? Component.translatable("gui.arenas_ld.room_controller.door_none")
            : (doorPositions.size() == 1
                ? Component.literal(doorPositions.get(0).toShortString())
                : Component.translatable("gui.arenas_ld.room_controller.door_count", doorPositions.size()));
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.door_label", doorValue), x + 8, y + 142, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.door_hint"), x + 8, y + 151, 0x808AA6, false);

        if (!spawnerPositions.isEmpty()) {
            int totalPages = Math.max(1, (int) Math.ceil(spawnerPositions.size() / (double) MAX_ROWS));
            int page = (scrollOffset / MAX_ROWS) + 1;
            guiGraphics.drawString(font, page + "/" + totalPages, x + 48, y + 127, 0x9FA8C8, false);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    public boolean matchesController(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(RoomControllerData data) {
        menu.applyData(data);
        syncFromMenu();
        rebuildWidgets();
    }

    private void syncFromMenu() {
        spawnerPositions.clear();
        spawnerPositions.addAll(menu.getSpawnerPositions());
        doorPositions = menu.getDoorPositions();
        roomNameInput = menu.getRoomName();
        scrollOffset = Math.min(scrollOffset, Math.max(0, spawnerPositions.size() - MAX_ROWS));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)
            && (roomNameField == null || !roomNameField.isFocused())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
