package net.ledok.arenas_ld.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.networking.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MobArenaControllerScreen extends AbstractContainerScreen<MobArenaControllerScreenHandler> {
    private Button startButton;
    private Button joinButton;
    private Button leaveButton;
    private List<Component> playerList = new ArrayList<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MobArenaControllerScreen(MobArenaControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        startButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.start_arena"), button -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 0));
        }).bounds(x + 10, y + 20, 100, 20).build());

        joinButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.join_party"), button -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 1));
        }).bounds(x + 10, y + 50, 100, 20).build());

        leaveButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.leave_party"), button -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 2));
        }).bounds(x + 10, y + 80, 100, 20).build());

        scheduler.scheduleAtFixedRate(this::updatePlayerList, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void removed() {
        super.removed();
        scheduler.shutdown();
    }

    private void updatePlayerList() {
        if (minecraft != null && minecraft.level != null) {
            minecraft.execute(() -> {
                if (minecraft.level.getBlockEntity(menu.getPos()) instanceof net.ledok.arenas_ld.block.entity.MobArenaControllerBlockEntity controller) {
                    List<Component> newPlayerList = new ArrayList<>();
                    for (UUID uuid : controller.partyMembers) {
                        Player player = minecraft.level.getPlayerByUUID(uuid);
                        if (player != null) {
                            newPlayerList.add(player.getDisplayName());
                        }
                    }
                    this.playerList = newPlayerList;
                }
            });
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (width - imageWidth) / 2 + 120;
        int y = (height - imageHeight) / 2 + 20;

        for (int i = 0; i < playerList.size(); i++) {
            guiGraphics.drawString(this.font, playerList.get(i), x, y + i * 10, 0xFFFFFF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
    }
}
