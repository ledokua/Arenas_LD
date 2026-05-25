package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.DbsClearRoomsPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsMoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.DbsRemoveRoomPayload;
import net.ledok.arenas_ld.dungeon.packet.UpdateDbsEntityDefPayload;
import net.ledok.arenas_ld.screen.EquipmentScreen;
import net.ledok.arenas_ld.screen.EquipmentScreenData;
import net.ledok.arenas_ld.screen.EquipmentScreenHandler;
import net.ledok.arenas_ld.screen.MobAttributesData;
import net.ledok.arenas_ld.screen.MobAttributesScreen;
import net.ledok.arenas_ld.screen.MobAttributesScreenHandler;
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

public class DungeonBossSpawnerScreen extends AbstractContainerScreen<DungeonBossSpawnerScreenHandler> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 260;
    private static final int MAX_ROOMS_VISIBLE = 6;

    private EditBox mobIdField;
    private final List<net.minecraft.core.BlockPos> rooms;
    private int roomOffset;

    public DungeonBossSpawnerScreen(DungeonBossSpawnerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = this.imageHeight + 1000;
        this.rooms = new ArrayList<>(handler.getRooms());
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

        mobIdField = new EditBox(font, x + 68, y + 26, 200, 16, Component.empty());
        mobIdField.setValue(menu.getMobId());
        mobIdField.setMaxLength(128);
        addRenderableWidget(mobIdField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b ->
            ClientPlayNetworking.send(new UpdateDbsEntityDefPayload(menu.getBlockPos(), mobIdField.getValue()))
        ).bounds(x + 272, y + 25, 56, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.attributes"), b -> {
            if (minecraft != null && minecraft.player != null) {
                minecraft.setScreen(new MobAttributesScreen(
                    new MobAttributesScreenHandler(menu.containerId, minecraft.player.getInventory(), new MobAttributesData(menu.getBlockPos())),
                    minecraft.player.getInventory(),
                    Component.translatable("gui.arenas_ld.boss_attributes")
                ));
            }
        }).bounds(x + 10, y + 50, 100, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.equipment"), b -> {
            if (minecraft != null && minecraft.player != null) {
                minecraft.setScreen(new EquipmentScreen(
                    new EquipmentScreenHandler(menu.containerId, minecraft.player.getInventory(), new EquipmentScreenData(menu.getBlockPos())),
                    minecraft.player.getInventory(),
                    Component.translatable("gui.arenas_ld.boss_equipment")
                ));
            }
        }).bounds(x + 116, y + 50, 100, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.room_controller.button.clear_all"), b -> {
            if (minecraft == null) return;
            minecraft.setScreen(new ConfirmScreen(ok -> {
                minecraft.setScreen(this);
                if (ok) {
                    ClientPlayNetworking.send(new DbsClearRoomsPayload(menu.getBlockPos()));
                }
            }, Component.translatable("gui.arenas_ld.room_controller.confirm.clear_title"),
                Component.translatable("gui.arenas_ld.dbs.clear_rooms_confirm")));
        }).bounds(x + WIDTH - 84, y + 230, 74, 16).build());

        int visible = Math.min(MAX_ROOMS_VISIBLE, Math.max(0, rooms.size() - roomOffset));
        for (int i = 0; i < visible; i++) {
            int idx = roomOffset + i;
            int rowY = y + 138 + i * 14;
            net.minecraft.core.BlockPos room = rooms.get(idx);

            addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                ClientPlayNetworking.send(new DbsRemoveRoomPayload(menu.getBlockPos(), room));
            }).bounds(x + WIDTH - 24, rowY, 14, 12).build());

            addRenderableWidget(Button.builder(Component.literal("v"), b -> {
                ClientPlayNetworking.send(new DbsMoveRoomPayload(menu.getBlockPos(), idx, idx + 1));
            }).bounds(x + WIDTH - 42, rowY, 14, 12).build()).active = idx < rooms.size() - 1;

            addRenderableWidget(Button.builder(Component.literal("^"), b -> {
                ClientPlayNetworking.send(new DbsMoveRoomPayload(menu.getBlockPos(), idx, idx - 1));
            }).bounds(x + WIDTH - 60, rowY, 14, 12).build()).active = idx > 0;
        }

        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            roomOffset = Math.max(0, roomOffset - 1);
            rebuildWidgets();
        }).bounds(x + 10, y + 230, 14, 16).build()).active = roomOffset > 0;

        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            roomOffset = Math.min(Math.max(0, rooms.size() - MAX_ROOMS_VISIBLE), roomOffset + 1);
            rebuildWidgets();
        }).bounds(x + 28, y + 230, 14, 16).build()).active = roomOffset < Math.max(0, rooms.size() - MAX_ROOMS_VISIBLE);

        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose()).bounds(x + WIDTH - 22, y + 6, 16, 14).build());
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF121620);
        guiGraphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 20, 0xFF1A2130);
        guiGraphics.fill(x + 8, y + 22, x + WIDTH - 8, y + 70, 0xFF0E1320);
        guiGraphics.fill(x + 8, y + 74, x + WIDTH - 8, y + 122, 0xFF0E1320);
        guiGraphics.fill(x + 8, y + 126, x + WIDTH - 8, y + 248, 0xFF0E1320);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(font, title, x + 8, y + 8, 0xFFFFFF, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.mob_id"), x + 12, y + 29, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dbs.entrance_hint"), x + 12, y + 84, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dbs.rooms"), x + 12, y + 130, 0xC0C8E0, false);

        int visible = Math.min(MAX_ROOMS_VISIBLE, Math.max(0, rooms.size() - roomOffset));
        for (int i = 0; i < visible; i++) {
            int idx = roomOffset + i;
            int rowY = y + 140 + i * 14;
            var room = rooms.get(idx);
            guiGraphics.drawString(font, (idx + 1) + ". " + room.toShortString(), x + 12, rowY, 0xE0E0E0, false);
        }
        if (rooms.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.room_controller.spawners_empty"), x + 12, y + 140, 0x808AA6, false);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    public boolean matchesSpawner(BlockPos blockPos) {
        return menu.getBlockPos().equals(blockPos);
    }

    public void applyData(DungeonBossSpawnerData data) {
        menu.applyData(data);
        syncFromMenu();
        rebuildWidgets();
    }

    private void syncFromMenu() {
        rooms.clear();
        rooms.addAll(menu.getRooms());
        roomOffset = Math.min(roomOffset, Math.max(0, rooms.size() - MAX_ROOMS_VISIBLE));
        if (mobIdField != null) {
            mobIdField.setValue(menu.getMobId());
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
