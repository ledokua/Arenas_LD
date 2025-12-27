package net.ledok.arenas_ld.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.screen.CreateLobbyData;
import net.ledok.arenas_ld.screen.CreateLobbyScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CreateLobbyScreen extends AbstractContainerScreen<CreateLobbyScreenHandler> {

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "textures/gui/lobby.png");

    public CreateLobbyScreen(CreateLobbyScreenHandler handler, Inventory inventory, Component title) {
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
        for (CreateLobbyData.DungeonStatus dungeon : this.menu.dungeons) {
            Component buttonText;
            boolean enabled = false;

            switch (dungeon.status()) {
                case AVAILABLE:
                    buttonText = Component.literal(dungeon.name());
                    enabled = true;
                    break;
                case LOCKED:
                    buttonText = Component.literal(dungeon.name() + " (Locked)");
                    break;
                case COOLDOWN:
                    buttonText = Component.literal(dungeon.name() + " (Cooldown: " + dungeon.cooldown() / 20 + "s)");
                    break;
                default:
                    buttonText = Component.literal(dungeon.name() + " (Unknown)");
                    break;
            }

            Button button = Button.builder(buttonText, b -> {
                ClientPlayNetworking.send(new ModPackets.CreateLobbyRequestPayload(dungeon.name()));
            }).bounds(x + 20, yOffset, 200, 20).build();
            button.active = enabled;
            
            this.addRenderableWidget(button);
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
