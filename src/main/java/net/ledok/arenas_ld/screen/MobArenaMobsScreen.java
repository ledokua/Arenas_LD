package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.MobArenaMobData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

public class MobArenaMobsScreen extends Screen {
    private final BlockPos blockPos;
    private final List<MobArenaMobData> mobs;
    private MobList list;
    private final Screen parent;
    private Component tooltip;

    public MobArenaMobsScreen(BlockPos blockPos, List<MobArenaMobData> mobs, Screen parent) {
        super(Component.translatable("gui.arenas_ld.mobs"));
        this.blockPos = blockPos;
        this.mobs = new ArrayList<>(mobs); // Copy list to avoid modifying original until save
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.list = new MobList(this.minecraft, this.width, this.height, 32, 25);
        for (MobArenaMobData mob : mobs) {
            this.list.addEntry(new MobEntry(mob));
        }
        this.addRenderableWidget(this.list);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.add"), button -> {
            MobArenaMobData newMob = new MobArenaMobData();
            mobs.add(newMob);
            list.addEntry(new MobEntry(newMob));
        }).bounds(this.width / 2 - 155, this.height - 28, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.save"), button -> {
            ClientPlayNetworking.send(new ModPackets.UpdateMobArenaMobsPayload(blockPos, mobs));
            this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 + 5, this.height - 28, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.tooltip = null;
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        this.list.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 16777215);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        if (this.tooltip != null) {
            guiGraphics.renderTooltip(this.font, this.tooltip, mouseX, mouseY);
        }
    }

    class MobList extends ContainerObjectSelectionList<MobEntry> {
        public MobList(Minecraft minecraft, int width, int height, int y0, int itemHeight) {
            super(minecraft, width, height, y0, itemHeight);
        }

        @Override
        public int getRowWidth() {
            return 400;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 190;
        }
        
        @Override
        public int addEntry(MobEntry entry) {
            return super.addEntry(entry);
        }
        
        public boolean removeEntry(MobEntry entry) {
            return super.removeEntry(entry);
        }
    }

    class MobEntry extends ContainerObjectSelectionList.Entry<MobEntry> {
        private final MobArenaMobData mobData;
        private final EditBox mobIdField;
        private final EditBox weightField;
        private final EditBox minWaveField;
        private final EditBox maxWaveField;
        private final Button attributesButton;
        private final Button equipmentButton;
        private final Button removeButton;

        public MobEntry(MobArenaMobData mobData) {
            this.mobData = mobData;

            this.mobIdField = new EditBox(font, 0, 0, 100, 20, Component.literal("Mob ID"));
            this.mobIdField.setMaxLength(255);
            this.mobIdField.setValue(mobData.mobId);
            this.mobIdField.setResponder(s -> mobData.mobId = s);

            this.weightField = new EditBox(font, 0, 0, 30, 20, Component.literal("Weight"));
            this.weightField.setValue(String.valueOf(mobData.weight));
            this.weightField.setResponder(s -> {
                try {
                    mobData.weight = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });

            this.minWaveField = new EditBox(font, 0, 0, 30, 20, Component.literal("Min Wave"));
            this.minWaveField.setValue(String.valueOf(mobData.minWave));
            this.minWaveField.setResponder(s -> {
                try {
                    mobData.minWave = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });

            this.maxWaveField = new EditBox(font, 0, 0, 30, 20, Component.literal("Max Wave"));
            this.maxWaveField.setValue(String.valueOf(mobData.maxWave));
            this.maxWaveField.setResponder(s -> {
                try {
                    mobData.maxWave = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });

            this.attributesButton = Button.builder(Component.translatable("gui.arenas_ld.attributes"), button -> {
                minecraft.setScreen(new ClientMobAttributesScreen(mobData, MobArenaMobsScreen.this));
            }).bounds(0, 0, 60, 20).build();

            this.equipmentButton = Button.builder(Component.translatable("gui.arenas_ld.equipment"), button -> {
                minecraft.setScreen(new ClientEquipmentScreen(mobData, MobArenaMobsScreen.this));
            }).bounds(0, 0, 60, 20).build();

            this.removeButton = Button.builder(Component.translatable("gui.arenas_ld.remove"), button -> {
                mobs.remove(mobData);
                list.removeEntry(this);
            }).bounds(0, 0, 20, 20).build();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean inBounds = mouseY >= 32 && mouseY <= MobArenaMobsScreen.this.height - 32;

            this.mobIdField.setX(left);
            this.mobIdField.setY(top);
            this.mobIdField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.mobIdField.isMouseOver(mouseX, mouseY)) {
                MobArenaMobsScreen.this.tooltip = Component.literal("Mob ID");
            }

            this.attributesButton.setX(left + 105);
            this.attributesButton.setY(top);
            this.attributesButton.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.attributesButton.isMouseOver(mouseX, mouseY)) {
                MobArenaMobsScreen.this.tooltip = this.attributesButton.getMessage();
            }

            this.equipmentButton.setX(left + 170);
            this.equipmentButton.setY(top);
            this.equipmentButton.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.equipmentButton.isMouseOver(mouseX, mouseY)) {
                MobArenaMobsScreen.this.tooltip = this.equipmentButton.getMessage();
            }

            this.weightField.setX(left + 235);
            this.weightField.setY(top);
            this.weightField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.weightField.isMouseOver(mouseX, mouseY)) {
                MobArenaMobsScreen.this.tooltip = Component.literal("Weight");
            }

            this.minWaveField.setX(left + 270);
            this.minWaveField.setY(top);
            this.minWaveField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.minWaveField.isMouseOver(mouseX, mouseY)) {
                MobArenaMobsScreen.this.tooltip = Component.literal("Min Wave");
            }

            this.maxWaveField.setX(left + 305);
            this.maxWaveField.setY(top);
            this.maxWaveField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.maxWaveField.isMouseOver(mouseX, mouseY)) {
                MobArenaMobsScreen.this.tooltip = Component.literal("Max Wave");
            }

            this.removeButton.setX(left + 340);
            this.removeButton.setY(top);
            this.removeButton.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.removeButton.isMouseOver(mouseX, mouseY)) {
                MobArenaMobsScreen.this.tooltip = this.removeButton.getMessage();
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(mobIdField, weightField, minWaveField, maxWaveField, attributesButton, equipmentButton, removeButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(mobIdField, weightField, minWaveField, maxWaveField, attributesButton, equipmentButton, removeButton);
        }
    }
}
