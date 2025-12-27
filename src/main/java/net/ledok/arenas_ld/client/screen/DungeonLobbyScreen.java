package net.ledok.arenas_ld.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.screen.DungeonLobbyScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class DungeonLobbyScreen extends AbstractContainerScreen<DungeonLobbyScreenHandler> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "textures/gui/lobby.png");
    private int countdown = -1;

    public DungeonLobbyScreen(DungeonLobbyScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 729;
        this.imageHeight = 342;
    }

    @Override
    protected void init() {
        super.init();
        
        boolean isOwner = this.minecraft.player.getUUID().equals(this.menu.ownerId);

        if (isOwner) {
            this.addRenderableWidget(Button.builder(Component.literal("Start"), button -> {
                ClientPlayNetworking.send(new ModPackets.StartLobbyRequestPayload());
            }).bounds(this.width / 2 - 105, this.height / 2 + 100, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Disband"), button -> {
                ClientPlayNetworking.send(new ModPackets.DisbandLobbyRequestPayload());
            }).bounds(this.width / 2 + 5, this.height / 2 + 100, 100, 20).build());
        } else {
            this.addRenderableWidget(Button.builder(Component.literal("Leave Lobby"), button -> {
                ClientPlayNetworking.send(new ModPackets.LeaveLobbyRequestPayload());
            }).bounds(this.width / 2 - 50, this.height / 2 + 100, 100, 20).build());
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

        int x = (this.width / 2) - 100;
        int y = (this.height / 2) - 80;
        guiGraphics.drawString(this.font, "Players in Lobby:", x, y, 0xFFFFFF);
        y += 12;

        for (String playerName : this.menu.playerNames) {
            guiGraphics.drawString(this.font, playerName, x, y, 0xFFFFFF);
            y += 10;
        }

        if (countdown > 0) {
            guiGraphics.drawCenteredString(this.font, "Starting in " + (countdown / 20) + "...", this.width / 2, this.height / 2 - 100, 0xFFFFFF);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    public void setCountdown(int ticks) {
        this.countdown = ticks;
    }
}
