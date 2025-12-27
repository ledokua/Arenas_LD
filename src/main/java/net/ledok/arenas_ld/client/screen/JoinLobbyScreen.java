package net.ledok.arenas_ld.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.screen.JoinLobbyData;
import net.ledok.arenas_ld.screen.JoinLobbyScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class JoinLobbyScreen extends AbstractContainerScreen<JoinLobbyScreenHandler> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "textures/gui/lobby.png");

    public JoinLobbyScreen(JoinLobbyScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 729;
        this.imageHeight = 342;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        
        int yOffset = y + 30;
        for (JoinLobbyData.LobbyInfo lobby : this.menu.lobbies) {
            Component buttonText = Component.literal(lobby.dungeonName() + " (" + lobby.playerCount() + "/" + lobby.maxPlayers() + ") - Owner: " + lobby.ownerName());
            this.addRenderableWidget(Button.builder(buttonText, button -> {
                ClientPlayNetworking.send(new ModPackets.JoinLobbyRequestPayload(lobby.lobbyId()));
            }).bounds(x + 20, yOffset, 300, 20).build());
            yOffset += 24;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Do not render labels
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
