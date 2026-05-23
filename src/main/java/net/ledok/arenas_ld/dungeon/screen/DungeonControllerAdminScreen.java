package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.packet.MoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.RemoveDungeonInstancePayload;
import net.ledok.arenas_ld.dungeon.packet.SetCloseTimerSecondsPayload;
import net.ledok.arenas_ld.dungeon.packet.SetCooldownTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetInviteExpiryTicksPayload;
import net.ledok.arenas_ld.dungeon.packet.SetMaxPartySizePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DungeonControllerAdminScreen extends AbstractContainerScreen<DungeonControllerAdminScreenHandler> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 230;

    private enum Tab { INSTANCES, GENERAL }

    private final List<net.minecraft.core.BlockPos> instances;
    private final Set<net.minecraft.core.BlockPos> activeRuns;
    private final Map<net.minecraft.core.BlockPos, Integer> cooldowns;
    private final Set<net.minecraft.core.BlockPos> pendingRemovals;
    private Tab currentTab = Tab.INSTANCES;

    private EditBox cooldownField;
    private EditBox closeTimerField;
    private EditBox maxPartyField;
    private EditBox inviteExpiryField;

    public DungeonControllerAdminScreen(DungeonControllerAdminScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = this.imageHeight + 1000;
        this.instances = new ArrayList<>(handler.getInstances());
        this.activeRuns = handler.getActiveRunInstances();
        this.cooldowns = handler.getInstanceCooldownTimers();
        this.pendingRemovals = handler.getPendingRemovals();
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

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.instances"), b -> {
            currentTab = Tab.INSTANCES;
            rebuildWidgets();
        }).bounds(x + 8, y + 24, 74, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller_admin.tab.general"), b -> {
            currentTab = Tab.GENERAL;
            rebuildWidgets();
        }).bounds(x + 86, y + 24, 74, 18).build());

        addRenderableWidget(Button.builder(Component.literal("X"), b -> onClose())
            .bounds(x + WIDTH - 22, y + 6, 16, 14).build());

        if (currentTab == Tab.INSTANCES) {
            buildInstancesTab(x, y);
        } else {
            buildGeneralTab(x, y);
        }
    }

    private void buildInstancesTab(int x, int y) {
        int rowY = y + 72;
        for (int i = 0; i < instances.size() && i < 7; i++) {
            final int index = i;
            final net.minecraft.core.BlockPos pos = instances.get(i);
            addRenderableWidget(Button.builder(Component.literal("^"), b -> {
                int target = Math.max(0, index - 1);
                if (target != index) {
                    ClientPlayNetworking.send(new MoveDungeonInstancePayload(menu.getBlockPos(), index, target));
                    net.minecraft.core.BlockPos moved = instances.remove(index);
                    instances.add(target, moved);
                    rebuildWidgets();
                }
            }).bounds(x + WIDTH - 56, rowY - 2, 14, 14).build()).active = index > 0;
            addRenderableWidget(Button.builder(Component.literal("v"), b -> {
                int target = Math.min(instances.size() - 1, index + 1);
                if (target != index) {
                    ClientPlayNetworking.send(new MoveDungeonInstancePayload(menu.getBlockPos(), index, target));
                    net.minecraft.core.BlockPos moved = instances.remove(index);
                    instances.add(target, moved);
                    rebuildWidgets();
                }
            }).bounds(x + WIDTH - 40, rowY - 2, 14, 14).build()).active = index < instances.size() - 1;
            addRenderableWidget(Button.builder(Component.literal("X"), b -> {
                ClientPlayNetworking.send(new RemoveDungeonInstancePayload(menu.getBlockPos(), pos));
                instances.remove(index);
                rebuildWidgets();
            }).bounds(x + WIDTH - 24, rowY - 2, 14, 14).build());
            rowY += 18;
        }
    }

    private void buildGeneralTab(int x, int y) {
        cooldownField = new EditBox(font, x + 120, y + 58, 60, 16, Component.empty());
        cooldownField.setValue(Integer.toString(menu.getCooldownTicks() / 20));
        addRenderableWidget(cooldownField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            Integer value = parseField(cooldownField);
            if (value != null) {
                ClientPlayNetworking.send(new SetCooldownTicksPayload(menu.getBlockPos(), value * 20));
            }
        }).bounds(x + 186, y + 57, 54, 18).build());

        closeTimerField = new EditBox(font, x + 120, y + 84, 60, 16, Component.empty());
        closeTimerField.setValue(Integer.toString(menu.getCloseTimerSeconds()));
        addRenderableWidget(closeTimerField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            Integer value = parseField(closeTimerField);
            if (value != null) {
                ClientPlayNetworking.send(new SetCloseTimerSecondsPayload(menu.getBlockPos(), value));
            }
        }).bounds(x + 186, y + 83, 54, 18).build());

        maxPartyField = new EditBox(font, x + 120, y + 110, 60, 16, Component.empty());
        maxPartyField.setValue(Integer.toString(menu.getMaxPartySize()));
        addRenderableWidget(maxPartyField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            Integer value = parseField(maxPartyField);
            if (value != null) {
                ClientPlayNetworking.send(new SetMaxPartySizePayload(menu.getBlockPos(), value));
            }
        }).bounds(x + 186, y + 109, 54, 18).build());

        inviteExpiryField = new EditBox(font, x + 120, y + 136, 60, 16, Component.empty());
        inviteExpiryField.setValue(Integer.toString(menu.getInviteExpiryTicks() / 20));
        addRenderableWidget(inviteExpiryField);
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), b -> {
            Integer value = parseField(inviteExpiryField);
            if (value != null) {
                ClientPlayNetworking.send(new SetInviteExpiryTicksPayload(menu.getBlockPos(), value * 20));
            }
        }).bounds(x + 186, y + 135, 54, 18).build());
    }

    private Integer parseField(EditBox field) {
        try {
            return Integer.parseInt(field.getValue());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF121620);
        guiGraphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 20, 0xFF1A2130);
        guiGraphics.fill(x + 6, y + 46, x + WIDTH - 6, y + HEIGHT - 8, 0xFF0E1320);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(font, title, x + 8, y + 8, 0xFFFFFF, false);

        if (currentTab == Tab.INSTANCES) {
            renderInstancesText(guiGraphics, x, y);
        } else {
            renderGeneralText(guiGraphics, x, y);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderInstancesText(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.drawString(font,
            Component.translatable("gui.arenas_ld.dungeon_controller_admin.active_runs_count", activeRuns.size(), instances.size()),
            x + 8,
            y + 50,
            0xC0C8E0,
            false);

        int rowY = y + 72;
        for (int i = 0; i < instances.size() && i < 7; i++) {
            net.minecraft.core.BlockPos pos = instances.get(i);
            Component status;
            int color;
            if (pendingRemovals.contains(pos)) {
                status = Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.pending_removal");
                color = ChatFormatting.RED.getColor();
            } else if (activeRuns.contains(pos)) {
                status = Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.running");
                color = 0x66DD66;
            } else if (cooldowns.containsKey(pos)) {
                status = Component.translatable(
                    "gui.arenas_ld.dungeon_controller_admin.instance_status.cooldown",
                    formatTicks(cooldowns.get(pos))
                );
                color = 0xE8CC66;
            } else {
                status = Component.translatable("gui.arenas_ld.dungeon_controller_admin.instance_status.idle");
                color = 0xA0A0A0;
            }
            guiGraphics.drawString(font, (i + 1) + ". " + pos.toShortString(), x + 10, rowY, 0xE0E0E0, false);
            guiGraphics.drawString(font, status, x + 150, rowY, color, false);
            rowY += 18;
        }

        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.use_linker_hint"), x + 10, y + HEIGHT - 18, 0x808AA6, false);
    }

    private void renderGeneralText(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.label.cooldown"), x + 10, y + 62, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.label.close_timer"), x + 10, y + 88, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.label.max_party"), x + 10, y + 114, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller_admin.label.invite_expiry"), x + 10, y + 140, 0xC0C8E0, false);
    }

    private String formatTicks(int ticks) {
        int seconds = Math.max(0, ticks / 20);
        int minutes = seconds / 60;
        int rem = seconds % 60;
        return String.format("%d:%02d", minutes, rem);
    }
}
