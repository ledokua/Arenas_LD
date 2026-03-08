package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.networking.ModPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MobArenaControllerScreen extends AbstractContainerScreen<MobArenaControllerScreenHandler> {
    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "textures/test/arena_controller_background.png");
    private Button startButton;
    private Button joinButton;
    private Button leaveButton;
    private List<Component> playerList = new ArrayList<>();
    private int currentWave = 0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public MobArenaControllerScreen(MobArenaControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 240;
        this.imageHeight = 180;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        joinButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.join_party"), button -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 1));
        }).bounds(x + 10, y + 20, 100, 20).build());

        leaveButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.leave_party"), button -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 2));
        }).bounds(x + 10, y + 50, 100, 20).build());

        startButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.start_arena"), button -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 0));
        }).bounds(x + 10, y + 80, 100, 20).build());

        scheduler.scheduleAtFixedRate(this::updateInfo, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void removed() {
        super.removed();
        scheduler.shutdown();
    }

    private void updateInfo() {
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
                    this.currentWave = controller.currentWave;
                }
            });
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // Wave Counter
        if (currentWave > 0) {
            Component waveText = Component.translatable("gui.arenas_ld.current_wave", currentWave);
            int waveTextWidth = this.font.width(waveText);
            guiGraphics.drawString(this.font, waveText, x + 130 + (100 / 2) - (waveTextWidth / 2), y + 20 + (20 / 2) - (this.font.lineHeight / 2), 0xFFFFFF);
        }

        // Player List Title
        Component playerListTitle = Component.translatable("gui.arenas_ld.players");
        int playerListTitleWidth = this.font.width(playerListTitle);
        guiGraphics.drawString(this.font, playerListTitle, x + 130 + (100 / 2) - (playerListTitleWidth / 2), y + 50 + (15 / 2) - (this.font.lineHeight / 2), 0xFFFFFF);
        
        // Player List
        int playerListX = x + 135;
        int playerListY = y + 70;
        for (int i = 0; i < playerList.size(); i++) {
            guiGraphics.drawString(this.font, (i + 1) + ". " + playerList.get(i).getString(), playerListX, playerListY + i * 10, 0xFFFFFF);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleWidth = this.font.width(this.title);
        guiGraphics.drawString(this.font, this.title, 60 + (120 / 2) - (titleWidth / 2), 4 + (10 / 2) - (this.font.lineHeight / 2), 0x000000, false);
    }
}
