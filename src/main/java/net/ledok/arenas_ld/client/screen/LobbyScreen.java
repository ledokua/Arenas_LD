package net.ledok.arenas_ld.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.screen.LobbyScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class LobbyScreen extends AbstractContainerScreen<LobbyScreenHandler> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "textures/gui/lobby.png");

    public LobbyScreen(LobbyScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 729;
        this.imageHeight = 342;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int buttonWidth = 120;
        int buttonHeight = 20;
        int spacing = 10;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.create_lobby"), button -> {
            ClientPlayNetworking.send(new ModPackets.OpenCreateLobbyScreenPayload());
        }).bounds(centerX - buttonWidth - spacing / 2, centerY - buttonHeight / 2, buttonWidth, buttonHeight).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.join_lobby"), button -> {
            ClientPlayNetworking.send(new ModPackets.RequestLobbyListPayload());
        }).bounds(centerX + spacing / 2, centerY - buttonHeight / 2, buttonWidth, buttonHeight).build());
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Do not render labels (title and inventory name)
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        // Use the blit overload that accepts texture width and height to support non-256x256 textures
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
