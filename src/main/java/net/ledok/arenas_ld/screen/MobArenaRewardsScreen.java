package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.MobArenaRewardData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class MobArenaRewardsScreen extends Screen {
    private final BlockPos blockPos;
    private final List<MobArenaRewardData> rewards;
    private RewardList list;
    private final Screen parent;
    private Component tooltip;

    public MobArenaRewardsScreen(BlockPos blockPos, List<MobArenaRewardData> rewards, Screen parent) {
        super(Component.translatable("gui.arenas_ld.rewards"));
        this.blockPos = blockPos;
        this.rewards = new ArrayList<>(rewards);
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.list = new RewardList(this.minecraft, this.width, 32, this.height - 32, 25);
        for (MobArenaRewardData reward : rewards) {
            this.list.addEntry(new RewardEntry(reward));
        }
        this.addRenderableWidget(this.list);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.add"), button -> {
            MobArenaRewardData newReward = new MobArenaRewardData();
            rewards.add(newReward);
            list.addEntry(new RewardEntry(newReward));
        }).bounds(this.width / 2 - 155, this.height - 28, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.save"), button -> {
            ClientPlayNetworking.send(new ModPackets.UpdateMobArenaRewardsPayload(blockPos, rewards));
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
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

    class RewardList extends ContainerObjectSelectionList<RewardEntry> {
        public RewardList(Minecraft minecraft, int width, int y0, int y1, int itemHeight) {
            super(minecraft, width, y1 - y0, y0, itemHeight);
        }

        public int addEntry(RewardEntry entry) {
            return super.addEntry(entry);
        }

        public boolean removeEntry(RewardEntry entry) {
            return super.removeEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return 400;
        }

        @Override
        protected int getScrollbarPosition() {
            return this.width / 2 + 190;
        }
    }

    class RewardEntry extends ContainerObjectSelectionList.Entry<RewardEntry> {
        private final MobArenaRewardData rewardData;
        private final Checkbox perPlayerCheckbox;
        private final EditBox lootTableIdField;
        private final EditBox weightField;
        private final EditBox rollsField;
        private final EditBox minWaveField;
        private final EditBox maxWaveField;
        private final EditBox waveFrequencyField;
        private final Button removeButton;

        public RewardEntry(MobArenaRewardData rewardData) {
            this.rewardData = rewardData;

            this.perPlayerCheckbox = Checkbox.builder(Component.empty(), font).pos(0, 0).selected(rewardData.perPlayer).onValueChange((checkbox, selected) -> rewardData.perPlayer = selected).build();
            
            this.lootTableIdField = new EditBox(font, 0, 0, 100, 20, Component.literal("Loot Table ID"));
            this.lootTableIdField.setMaxLength(255);
            this.lootTableIdField.setValue(rewardData.lootTableId);
            this.lootTableIdField.setResponder(s -> rewardData.lootTableId = s);

            this.weightField = new EditBox(font, 0, 0, 30, 20, Component.literal("Weight"));
            this.weightField.setValue(String.valueOf(rewardData.weight));
            this.weightField.setResponder(s -> {
                try {
                    rewardData.weight = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });
            
            this.rollsField = new EditBox(font, 0, 0, 30, 20, Component.literal("Rolls"));
            this.rollsField.setValue(String.valueOf(rewardData.rolls));
            this.rollsField.setResponder(s -> {
                try {
                    rewardData.rolls = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });

            this.minWaveField = new EditBox(font, 0, 0, 30, 20, Component.literal("Min Wave"));
            this.minWaveField.setValue(String.valueOf(rewardData.minWave));
            this.minWaveField.setResponder(s -> {
                try {
                    rewardData.minWave = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });

            this.maxWaveField = new EditBox(font, 0, 0, 30, 20, Component.literal("Max Wave"));
            this.maxWaveField.setValue(String.valueOf(rewardData.maxWave));
            this.maxWaveField.setResponder(s -> {
                try {
                    rewardData.maxWave = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });
            
            this.waveFrequencyField = new EditBox(font, 0, 0, 30, 20, Component.literal("Wave Freq"));
            this.waveFrequencyField.setValue(String.valueOf(rewardData.waveFrequency));
            this.waveFrequencyField.setResponder(s -> {
                try {
                    rewardData.waveFrequency = Integer.parseInt(s);
                } catch (NumberFormatException ignored) {}
            });

            this.removeButton = Button.builder(Component.translatable("gui.arenas_ld.remove"), button -> {
                rewards.remove(this.rewardData);
                list.removeEntry(this);
            }).bounds(0, 0, 20, 20).build();
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean inBounds = mouseY >= 32 && mouseY <= MobArenaRewardsScreen.this.height - 32;

            this.perPlayerCheckbox.setX(left);
            this.perPlayerCheckbox.setY(top);
            this.perPlayerCheckbox.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.perPlayerCheckbox.isMouseOver(mouseX, mouseY)) {
                MobArenaRewardsScreen.this.tooltip = Component.literal("Per Player");
            }
            
            this.lootTableIdField.setX(left + 25);
            this.lootTableIdField.setY(top);
            this.lootTableIdField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.lootTableIdField.isMouseOver(mouseX, mouseY)) {
                MobArenaRewardsScreen.this.tooltip = Component.literal("Loot Table ID");
            }

            this.weightField.setX(left + 130);
            this.weightField.setY(top);
            this.weightField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.weightField.isMouseOver(mouseX, mouseY)) {
                MobArenaRewardsScreen.this.tooltip = Component.literal("Weight");
            }
            
            this.rollsField.setX(left + 165);
            this.rollsField.setY(top);
            this.rollsField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.rollsField.isMouseOver(mouseX, mouseY)) {
                MobArenaRewardsScreen.this.tooltip = Component.literal("Rolls");
            }

            this.minWaveField.setX(left + 200);
            this.minWaveField.setY(top);
            this.minWaveField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.minWaveField.isMouseOver(mouseX, mouseY)) {
                MobArenaRewardsScreen.this.tooltip = Component.literal("Min Wave");
            }

            this.maxWaveField.setX(left + 235);
            this.maxWaveField.setY(top);
            this.maxWaveField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.maxWaveField.isMouseOver(mouseX, mouseY)) {
                MobArenaRewardsScreen.this.tooltip = Component.literal("Max Wave");
            }
            
            this.waveFrequencyField.setX(left + 270);
            this.waveFrequencyField.setY(top);
            this.waveFrequencyField.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.waveFrequencyField.isMouseOver(mouseX, mouseY)) {
                MobArenaRewardsScreen.this.tooltip = Component.literal("Wave Frequency");
            }

            this.removeButton.setX(left + 305);
            this.removeButton.setY(top);
            this.removeButton.render(guiGraphics, mouseX, mouseY, partialTick);
            if (inBounds && this.removeButton.isMouseOver(mouseX, mouseY)) {
                MobArenaRewardsScreen.this.tooltip = this.removeButton.getMessage();
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(perPlayerCheckbox, lootTableIdField, weightField, rollsField, minWaveField, maxWaveField, waveFrequencyField, removeButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(perPlayerCheckbox, lootTableIdField, weightField, rollsField, minWaveField, maxWaveField, waveFrequencyField, removeButton);
        }
    }
}
