package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.DungeonLeaderboardEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DungeonControllerScreen extends AbstractContainerScreen<DungeonControllerScreenHandler> {
    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "textures/test/arena_controller_background.png");
    private static final int LEADERBOARD_HEIGHT = 55;
    private Button startButton;
    private Button joinButton;
    private Button leaveButton;
    private Checkbox hardcoreCheckbox;
    private boolean suppressHardcoreSync = false;
    private double playerListScrollAmount = 0;
    private List<Component> playerList = new ArrayList<>();
    private List<DungeonLeaderboardEntry> leaderboard = new ArrayList<>();
    private int remainingDungeonTimeSeconds = 0;
    private int dungeonCooldownSeconds = 0;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private double scrollAmount = 0;

    public DungeonControllerScreen(DungeonControllerScreenHandler handler, Inventory inventory, Component title) {
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
            ClientPlayNetworking.send(new ModPackets.DungeonControllerActionPayload(menu.getPos(), 1));
            updateInfo();
        }).bounds(x + 10, y + 20, 100, 20).build());

        leaveButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.leave_party"), button -> {
            ClientPlayNetworking.send(new ModPackets.DungeonControllerActionPayload(menu.getPos(), 2));
            updateInfo();
        }).bounds(x + 10, y + 50, 100, 20).build());

        startButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.start_dungeon"), button -> {
            ClientPlayNetworking.send(new ModPackets.DungeonControllerActionPayload(menu.getPos(), 0));
            updateInfo();
        }).bounds(x + 10, y + 80, 100, 20).build());

        hardcoreCheckbox = addRenderableWidget(Checkbox.builder(Component.translatable("gui.arenas_ld.hardcore"), this.font)
                .pos(x + 130, y + 150)
                .selected(false)
                .onValueChange((checkbox, selected) -> {
                    if (!suppressHardcoreSync) {
                        ClientPlayNetworking.send(new ModPackets.UpdateDungeonControllerSettingsPayload(menu.getPos(), selected));
                    }
                })
                .build());
        hardcoreCheckbox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.arenas_ld.hardcore_desc")));

        scheduler.scheduleAtFixedRate(this::updateInfo, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void removed() {
        super.removed();
        scheduler.shutdown();
    }

    private void updateInfo() {
        if (minecraft == null || minecraft.level == null) return;
        ClientPlayNetworking.send(new ModPackets.RequestDungeonControllerInfoPayload(menu.getPos()));
    }

    public void applyServerInfo(ModPackets.DungeonControllerInfoPayload payload) {
        if (!payload.pos().equals(menu.getPos())) return;
        List<Component> newPlayerList = new ArrayList<>();
        for (String name : payload.players()) {
            newPlayerList.add(Component.literal(name));
        }
        this.playerList = newPlayerList;
        this.remainingDungeonTimeSeconds = payload.remainingDungeonTimeSeconds();
        this.dungeonCooldownSeconds = payload.dungeonCooldownSeconds();
        if (hardcoreCheckbox != null && payload.hardcoreEnabled() != hardcoreCheckbox.selected()) {
            suppressHardcoreSync = true;
            hardcoreCheckbox.onPress();
            suppressHardcoreSync = false;
        }
        this.leaderboard = payload.leaderboard();
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

        // Remaining Dungeon Time / Cooldown
        if (dungeonCooldownSeconds > 0) {
            Component timeText = Component.translatable("gui.arenas_ld.dungeon_cooldown", formatTime(dungeonCooldownSeconds));
            float timeScale = 0.75f;
            int timeTextWidth = Math.round(this.font.width(timeText) * timeScale);
            int timeTextHeight = Math.round(this.font.lineHeight * timeScale);
            int timeX = x + 130 + (100 / 2) - (timeTextWidth / 2);
            int timeY = y + 20 + (20 / 2) - (timeTextHeight / 2);
            drawScaledString(guiGraphics, timeText, timeX, timeY, 0xFFFFFF, timeScale);
        } else if (remainingDungeonTimeSeconds > 0) {
            Component timeText = Component.translatable("gui.arenas_ld.dungeon_time_remaining", formatTime(remainingDungeonTimeSeconds));
            float timeScale = 0.75f;
            int timeTextWidth = Math.round(this.font.width(timeText) * timeScale);
            int timeTextHeight = Math.round(this.font.lineHeight * timeScale);
            int timeX = x + 130 + (100 / 2) - (timeTextWidth / 2);
            int timeY = y + 20 + (20 / 2) - (timeTextHeight / 2);
            drawScaledString(guiGraphics, timeText, timeX, timeY, 0xFFFFFF, timeScale);
        }

        // Player List Title
        Component playerListTitle = Component.translatable("gui.arenas_ld.players");
        int playerListTitleWidth = this.font.width(playerListTitle);
        guiGraphics.drawString(this.font, playerListTitle, x + 130 + (100 / 2) - (playerListTitleWidth / 2), y + 50 + (15 / 2) - (this.font.lineHeight / 2), 0xFFFFFF);

        // Player List
        int playerListX = x + 135;
        int playerListY = y + 70;
        int playerListWidth = 90;
        int playerListHeight = 80;
        guiGraphics.enableScissor(playerListX, playerListY, playerListX + playerListWidth, playerListY + playerListHeight);
        int playerEntryStep = 10;
        int playerTotalHeight = playerList.size() * playerEntryStep;
        int playerMaxScroll = Math.max(0, playerTotalHeight - playerListHeight);
        playerListScrollAmount = Mth.clamp(playerListScrollAmount, 0, playerMaxScroll);
        for (int i = 0; i < playerList.size(); i++) {
            int entryY = (int) (playerListY + i * playerEntryStep - playerListScrollAmount);
            guiGraphics.drawString(this.font, (i + 1) + ". " + playerList.get(i).getString(), playerListX, entryY, 0xFFFFFF);
        }
        guiGraphics.disableScissor();

        if (playerList.size() > 4) {
            int scrollbarX = playerListX + playerListWidth + 5;
            int scrollbarHeight = playerListHeight;
            int scrollbarHandleHeight = playerTotalHeight > 0
                    ? Math.max(6, (int) ((float) scrollbarHeight * scrollbarHeight / playerTotalHeight))
                    : scrollbarHeight;
            scrollbarHandleHeight = Math.min(scrollbarHandleHeight, scrollbarHeight);
            int scrollbarHandleY = playerListY + (playerMaxScroll == 0
                    ? 0
                    : (int) (playerListScrollAmount / playerMaxScroll * (scrollbarHeight - scrollbarHandleHeight)));
            guiGraphics.fill(scrollbarX, playerListY, scrollbarX + 6, playerListY + scrollbarHeight, 0x80000000);
            guiGraphics.fill(scrollbarX, scrollbarHandleY, scrollbarX + 6, scrollbarHandleY + scrollbarHandleHeight, 0x80FFFFFF);
        }

        // Leaderboard
        int leaderboardX = x + 15;
        int leaderboardY = y + 120;
        int leaderboardWidth = 90;
        int leaderboardHeight = LEADERBOARD_HEIGHT;

        Component leaderboardTitle = Component.translatable("gui.arenas_ld.fastest_clears");
        int leaderboardTitleWidth = this.font.width(leaderboardTitle);
        guiGraphics.drawString(this.font, leaderboardTitle, leaderboardX + (leaderboardWidth / 2) - (leaderboardTitleWidth / 2), y + 105, 0xFFFFFF);

        guiGraphics.enableScissor(leaderboardX, leaderboardY, leaderboardX + leaderboardWidth, leaderboardY + leaderboardHeight);

        float entryScale = 0.75f;
        int entryStep = Math.max(1, Math.round(10 * entryScale));
        int totalHeight = leaderboard.size() * entryStep;
        int maxScroll = Math.max(0, totalHeight - leaderboardHeight);
        scrollAmount = Mth.clamp(scrollAmount, 0, maxScroll);
        for (int i = 0; i < leaderboard.size(); i++) {
            DungeonLeaderboardEntry entry = leaderboard.get(i);
            Component entryText = Component.literal((i + 1) + ". " + entry.playerName + " - " + formatTime(entry.timeSeconds));
            int entryY = (int) (leaderboardY + i * entryStep - scrollAmount);
            drawScaledString(guiGraphics, entryText, leaderboardX, entryY, 0xFFFFFF, entryScale);
        }

        guiGraphics.disableScissor();

        if (leaderboard.size() > 4) {
            int scrollbarX = leaderboardX + leaderboardWidth + 5;
            int scrollbarHeight = leaderboardHeight;
            int scrollbarHandleHeight = totalHeight > 0
                    ? Math.max(6, (int) ((float) scrollbarHeight * scrollbarHeight / totalHeight))
                    : scrollbarHeight;
            scrollbarHandleHeight = Math.min(scrollbarHandleHeight, scrollbarHeight);
            int scrollbarHandleY = leaderboardY + (maxScroll == 0
                    ? 0
                    : (int) (scrollAmount / maxScroll * (scrollbarHeight - scrollbarHandleHeight)));
            guiGraphics.fill(scrollbarX, leaderboardY, scrollbarX + 6, leaderboardY + scrollbarHeight, 0x80000000);
            guiGraphics.fill(scrollbarX, scrollbarHandleY, scrollbarX + 6, scrollbarHandleY + scrollbarHandleHeight, 0x80FFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int playerListX = x + 135;
        int playerListY = y + 70;
        int playerListWidth = 90;
        int playerListHeight = 55;
        if (mouseX >= playerListX && mouseX <= playerListX + playerListWidth && mouseY >= playerListY && mouseY <= playerListY + playerListHeight) {
            int playerEntryStep = 10;
            int playerMaxScroll = Math.max(0, playerList.size() * playerEntryStep - playerListHeight);
            if (playerMaxScroll > 0) {
                playerListScrollAmount = Mth.clamp(playerListScrollAmount - verticalAmount * 10, 0, playerMaxScroll);
            }
            return true;
        }
        float entryScale = 0.75f;
        int entryStep = Math.max(1, Math.round(10 * entryScale));
        int maxScroll = Math.max(0, leaderboard.size() * entryStep - LEADERBOARD_HEIGHT);
        if (maxScroll > 0) {
            scrollAmount = Mth.clamp(scrollAmount - verticalAmount * 10, 0, maxScroll);
        }
        return true;
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleWidth = this.font.width(this.title);
        guiGraphics.drawString(this.font, this.title, 60 + (120 / 2) - (titleWidth / 2), 5 + (10 / 2) - (this.font.lineHeight / 2), 0x000000, false);
    }

    // Checkbox handles its own input via onValueChange.

    private static String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void drawScaledString(GuiGraphics guiGraphics, Component text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(this.font, text, 0, 0, color);
        guiGraphics.pose().popPose();
    }
}
