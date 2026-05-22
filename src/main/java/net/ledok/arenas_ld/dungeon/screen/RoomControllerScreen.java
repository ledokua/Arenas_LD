package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.RoomClearDoorPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomClearSpawnersPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomRemoveSpawnerPayload;
import net.ledok.arenas_ld.dungeon.packet.RoomResetPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoomControllerScreen extends AbstractContainerScreen<RoomControllerScreenHandler> {
    private static final int WIDTH = 250;
    private static final int HEIGHT = 200;
    private static final int MAX_ROWS = 5;

    private final List<net.minecraft.core.BlockPos> spawnerPositions;
    private Optional<net.minecraft.core.BlockPos> doorPos;
    private int scrollOffset;

    public RoomControllerScreen(RoomControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.spawnerPositions = new ArrayList<>(handler.getSpawnerPositions());
        this.doorPos = handler.getDoorPos();
        this.inventoryLabelY = this.imageHeight + 1000;
    }

    @Override
    protected void init() {
        super.init();
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
            .bounds(x + WIDTH - 22, y + 6, 16, 14).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.clear_all"), b -> {
            minecraft.setScreen(new ConfirmScreen(
                ok -> {
                    minecraft.setScreen(this);
                    if (ok) {
                        ClientPlayNetworking.send(new RoomClearSpawnersPayload(menu.getBlockPos()));
                        spawnerPositions.clear();
                        scrollOffset = 0;
                        rebuildWidgets();
                    }
                },
                Component.translatable("gui.arenas_ld.room_controller.confirm.clear_title"),
                Component.translatable("gui.arenas_ld.room_controller.confirm.clear_message")
            ));
        }).bounds(x + WIDTH - 80, y + 117, 72, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.clear_door"), b -> {
            ClientPlayNetworking.send(new RoomClearDoorPayload(menu.getBlockPos()));
            doorPos = Optional.empty();
            rebuildWidgets();
        }).bounds(x + WIDTH - 80, y + 142, 72, 16).build());

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
        }).bounds(x + WIDTH - 80, y + 174, 72, 16).build());

        int visible = Math.min(MAX_ROWS, Math.max(0, spawnerPositions.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            int index = scrollOffset + i;
            net.minecraft.core.BlockPos pos = spawnerPositions.get(index);
            int rowY = y + 32 + i * 16;
            addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.remove"), b -> {
                ClientPlayNetworking.send(new RoomRemoveSpawnerPayload(menu.getBlockPos(), pos));
                spawnerPositions.remove(pos);
                rebuildWidgets();
            }).bounds(x + WIDTH - 62, rowY, 54, 14).build());
        }

        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            scrollOffset = Math.max(0, scrollOffset - 1);
            rebuildWidgets();
        }).bounds(x + 8, y + 117, 16, 16).build()).active = scrollOffset > 0;

        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            scrollOffset = Math.min(Math.max(0, spawnerPositions.size() - MAX_ROWS), scrollOffset + 1);
            rebuildWidgets();
        }).bounds(x + 26, y + 117, 16, 16).build()).active = scrollOffset < Math.max(0, spawnerPositions.size() - MAX_ROWS);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF121620);
        guiGraphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 20, 0xFF1A2130);
        guiGraphics.fill(x + 6, y + 26, x + WIDTH - 6, y + 136, 0xFF0E1320);
        guiGraphics.fill(x + 6, y + 138, x + WIDTH - 6, y + 162, 0xFF0E1320);
        guiGraphics.fill(x + 6, y + 166, x + WIDTH - 6, y + 194, 0xFF0E1320);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(font, this.title, x + 8, y + 8, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.spawners_label"), x + 8, y + 22, 0xC0C8E0, false);

        int visible = Math.min(MAX_ROWS, Math.max(0, spawnerPositions.size() - scrollOffset));
        for (int i = 0; i < visible; i++) {
            net.minecraft.core.BlockPos pos = spawnerPositions.get(scrollOffset + i);
            int rowY = y + 35 + i * 16;
            guiGraphics.drawString(font, "- " + pos.toShortString(), x + 10, rowY, 0xE0E0E0, false);
        }

        if (spawnerPositions.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.spawners_empty"), x + 10, y + 35, 0x808AA6, false);
        }

        Component doorValue = doorPos
            .<Component>map(p -> Component.literal(p.getX() + ", " + p.getY() + ", " + p.getZ()))
            .orElse(Component.translatable("gui.arenas_ld.room_controller.door_none"));
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.door_label", doorValue), x + 8, y + 144, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.door_hint"), x + 8, y + 154, 0x808AA6, false);

        if (!spawnerPositions.isEmpty()) {
            int totalPages = Math.max(1, (int) Math.ceil(spawnerPositions.size() / (double) MAX_ROWS));
            int page = (scrollOffset / MAX_ROWS) + 1;
            guiGraphics.drawString(font, page + "/" + totalPages, x + 48, y + 121, 0x9FA8C8, false);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
