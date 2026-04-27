package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.block.entity.MobArenaControllerBlockEntity;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.LeaderboardEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MobArenaControllerScreen extends AbstractContainerScreen<MobArenaControllerScreenHandler> {
    private static final int W = 256;
    private static final int H = 240;
    private static final int P = 8;
    private static final int IW = W - (P * 2);

    private static final int PARTY_Y = 20;
    private static final int PARTY_H = 126;
    private static final int LEADERBOARD_Y = 152;
    private static final int LEADERBOARD_H = 80;

    private static final int BTN_H = 20;
    private static final int BTN_W = (IW - 8) / 3;

    private static final int C_BG = 0xFF111622;
    private static final int C_TITLEBAR = 0xFF141826;
    private static final int C_PANEL = 0xFF0e1220;
    private static final int C_BDR = 0xFF1f2845;
    private static final int C_BDR_HL = 0xFF3a4a8a;
    private static final int C_TEXT = 0xFFFFFFFF;
    private static final int C_MUTED = 0xFF6a7aaa;
    private static final int C_LABEL = 0xFF4a5580;
    private static final int C_GREEN = 0xFF44ee44;
    private static final int C_RED = 0xFFff5555;
    private static final int C_YELLOW = 0xFFffcc00;

    private static final int ROW_H = 11;

    private Button joinButton;
    private Button leaveButton;
    private Button startButton;
    private Checkbox hardcoreCheckbox;

    private boolean suppressHardcoreSync = false;
    private int refreshTickCounter = 0;
    private double partyScroll = 0;
    private double leaderboardScroll = 0;

    private int currentWave = 0;
    private List<String> partyNames = new ArrayList<>();
    private List<LeaderboardEntry> leaderboard = new ArrayList<>();

    public MobArenaControllerScreen(MobArenaControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        int actionsY = y + PARTY_Y + PARTY_H - BTN_H - 8;
        joinButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.join_party"), b -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 1));
            updateInfo();
        }).bounds(x + P, actionsY, BTN_W, BTN_H).build());

        leaveButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.leave_party"), b -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 2));
            updateInfo();
        }).bounds(x + P + BTN_W + 4, actionsY, BTN_W, BTN_H).build());

        startButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.start_arena"), b -> {
            ClientPlayNetworking.send(new ModPackets.MobArenaControllerActionPayload(menu.getPos(), 0));
            updateInfo();
        }).bounds(x + P + (BTN_W + 4) * 2, actionsY, BTN_W, BTN_H).build());

        hardcoreCheckbox = addRenderableWidget(Checkbox.builder(
                        Component.translatable("gui.arenas_ld.hardcore"), this.font)
                .pos(x + P, y + PARTY_Y + PARTY_H - 36)
                .selected(false)
                .onValueChange((checkbox, selected) -> {
                    if (!suppressHardcoreSync) {
                        ClientPlayNetworking.send(new ModPackets.UpdateMobArenaControllerSettingsPayload(menu.getPos(), selected));
                        updateInfo();
                    }
                })
                .build());

        updateInfo();
        updateWidgetVisibility();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshTickCounter++;
        if (refreshTickCounter >= 20) {
            refreshTickCounter = 0;
            updateInfo();
        }
        updateWidgetVisibility();
    }

    private void updateInfo() {
        if (minecraft == null || minecraft.level == null) {
            return;
        }
        ClientPlayNetworking.send(new ModPackets.RequestMobArenaControllerInfoPayload(menu.getPos()));
    }

    public void applyServerInfo(ModPackets.MobArenaControllerInfoPayload payload) {
        if (!payload.pos().equals(menu.getPos())) {
            return;
        }
        this.currentWave = payload.currentWave();

        this.partyNames = new ArrayList<>(payload.players());
        this.partyNames.sort(String.CASE_INSENSITIVE_ORDER);

        this.leaderboard = new ArrayList<>(payload.leaderboard());

        if (hardcoreCheckbox != null && payload.hardcoreEnabled() != hardcoreCheckbox.selected()) {
            suppressHardcoreSync = true;
            hardcoreCheckbox.onPress();
            suppressHardcoreSync = false;
        }
    }

    private MobArenaControllerBlockEntity getController() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        if (minecraft.level.getBlockEntity(menu.getPos()) instanceof MobArenaControllerBlockEntity controller) {
            return controller;
        }
        return null;
    }

    private boolean isArenaActive() {
        MobArenaControllerBlockEntity controller = getController();
        return controller != null && controller.isArenaActive();
    }

    private boolean isInParty() {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        MobArenaControllerBlockEntity controller = getController();
        return controller != null && controller.isPartyMember(minecraft.player.getUUID());
    }

    private boolean isPartyLeader() {
        if (minecraft == null || minecraft.player == null) {
            return false;
        }
        MobArenaControllerBlockEntity controller = getController();
        if (controller == null) {
            return false;
        }
        Set<UUID> members = controller.getPartyMembers();
        if (members.isEmpty()) {
            return false;
        }
        UUID leader = members.stream().min(Comparator.comparing(UUID::toString)).orElse(null);
        return leader != null && leader.equals(minecraft.player.getUUID());
    }

    private void updateWidgetVisibility() {
        boolean active = isArenaActive();
        boolean inParty = isInParty();
        boolean leader = isPartyLeader();

        if (joinButton != null) {
            joinButton.visible = !active;
            joinButton.active = !active && !inParty;
        }
        if (leaveButton != null) {
            leaveButton.visible = !active;
            leaveButton.active = !active && inParty;
        }
        if (startButton != null) {
            startButton.visible = !active && inParty && leader;
            startButton.active = !active && inParty && leader;
        }
        if (hardcoreCheckbox != null) {
            hardcoreCheckbox.visible = !active;
            hardcoreCheckbox.active = !active && inParty;
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        boolean active = isArenaActive();

        g.fill(x, y, x + W, y + H, C_BG);

        g.fill(x, y, x + W, y + 16, C_TITLEBAR);
        drawBorder(g, x, y, W, 16, C_BDR_HL);
        Component titleText = active
                ? Component.translatable("container.arenas_ld.mob_arena_controller").append(" - ").append(Component.translatable("gui.arenas_ld.current_wave", currentWave))
                : Component.translatable("container.arenas_ld.mob_arena_controller");
        g.drawString(font, titleText, x + P, y + 4, C_TEXT, false);

        drawPanel(g, x + P, y + PARTY_Y, IW, PARTY_H);
        drawPanel(g, x + P, y + LEADERBOARD_Y, IW, LEADERBOARD_H);

        if (active) {
            renderActiveState(g, x, y);
        } else {
            renderIdleState(g, x, y);
        }
        renderLeaderboard(g, x, y);
    }

    private void renderIdleState(GuiGraphics g, int x, int y) {
        smallLabel(g, Component.translatable("gui.arenas_ld.players"), x + P + 4, y + PARTY_Y + 4);

        int listX = x + P + 4;
        int listY = y + PARTY_Y + 16;
        int listW = IW - 8;
        int listH = 62;
        drawBorder(g, listX, listY, listW, listH, C_BDR);

        renderPartyMembers(g, listX + 4, listY + 4, listH - 8, false);
        small(g, Component.literal(partyNames.size() + " players"), x + P + 4, y + PARTY_Y + PARTY_H - 48, C_MUTED);
    }

    private void renderActiveState(GuiGraphics g, int x, int y) {
        smallLabel(g, Component.translatable("gui.arenas_ld.players"), x + P + 4, y + PARTY_Y + 4);
        Component waveText = Component.translatable("gui.arenas_ld.current_wave", currentWave);
        g.drawString(font, waveText, x + W - P - font.width(waveText), y + PARTY_Y + 4, C_YELLOW, false);

        int listX = x + P + 4;
        int listY = y + PARTY_Y + 16;
        int listW = IW - 8;
        int listH = PARTY_H - 24;
        drawBorder(g, listX, listY, listW, listH, C_BDR);

        renderPartyMembers(g, listX + 4, listY + 4, listH - 8, true);
    }

    private void renderPartyMembers(GuiGraphics g, int x, int y, int h, boolean activeState) {
        int totalHeight = partyNames.size() * ROW_H;
        int maxScroll = Math.max(0, totalHeight - h);
        partyScroll = Mth.clamp(partyScroll, 0, maxScroll);

        g.enableScissor(x, y, x + (IW - 16), y + h);
        for (int i = 0; i < partyNames.size(); i++) {
            String name = partyNames.get(i);
            int drawY = (int) (y + i * ROW_H - partyScroll);
            if (drawY + ROW_H < y || drawY > y + h) {
                continue;
            }
            boolean me = minecraft != null && minecraft.player != null
                    && name.equalsIgnoreCase(minecraft.player.getGameProfile().getName());
            boolean leader = isNameLeader(name);

            int color = me ? C_GREEN : C_TEXT;
            String prefix = leader ? "\u2605 " : "  ";
            String suffix = "";
            if (activeState && isDowned(name)) {
                suffix = " [downed]";
                color = C_RED;
            }
            g.drawString(font, prefix + name + suffix, x, drawY, color, false);
        }
        g.disableScissor();
    }

    private boolean isNameLeader(String name) {
        if (minecraft == null || minecraft.level == null) {
            return false;
        }
        MobArenaControllerBlockEntity controller = getController();
        if (controller == null) {
            return false;
        }
        UUID leader = controller.getPartyMembers().stream().min(Comparator.comparing(UUID::toString)).orElse(null);
        if (leader == null || minecraft.getConnection() == null) {
            return false;
        }
        var info = minecraft.getConnection().getPlayerInfo(leader);
        return info != null && info.getProfile().getName().equalsIgnoreCase(name);
    }

    private boolean isDowned(String name) {
        if (minecraft == null || minecraft.getConnection() == null) {
            return false;
        }
        for (var info : minecraft.getConnection().getOnlinePlayers()) {
            if (info.getProfile().getName().equalsIgnoreCase(name)) {
                return info.getGameMode() == net.minecraft.world.level.GameType.SPECTATOR;
            }
        }
        return false;
    }

    private void renderLeaderboard(GuiGraphics g, int x, int y) {
        smallLabel(g, Component.translatable("gui.arenas_ld.highest_wave"), x + P + 4, y + LEADERBOARD_Y + 4);

        int listX = x + P + 4;
        int listY = y + LEADERBOARD_Y + 14;
        int listW = IW - 8;
        int listH = LEADERBOARD_H - 18;
        drawBorder(g, listX, listY, listW, listH, C_BDR);

        int totalHeight = leaderboard.size() * ROW_H;
        int maxScroll = Math.max(0, totalHeight - (listH - 4));
        leaderboardScroll = Mth.clamp(leaderboardScroll, 0, maxScroll);

        g.enableScissor(listX + 2, listY + 2, listX + listW - 2, listY + listH - 2);
        for (int i = 0; i < leaderboard.size(); i++) {
            LeaderboardEntry entry = leaderboard.get(i);
            int drawY = (int) (listY + 3 + i * ROW_H - leaderboardScroll);
            if (drawY + ROW_H < listY || drawY > listY + listH) {
                continue;
            }
            int color = i == 0 ? C_YELLOW : C_TEXT;
            g.drawString(font, (i + 1) + ". " + entry.playerName + " - Wave " + entry.wave, listX + 4, drawY, color, false);
        }
        g.disableScissor();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = leftPos;
        int y = topPos;

        int partyListX = x + P + 8;
        int partyListY = y + PARTY_Y + 20;
        int partyListW = IW - 16;
        int partyListH = isArenaActive() ? PARTY_H - 32 : 54;
        if (mouseX >= partyListX && mouseX <= partyListX + partyListW
                && mouseY >= partyListY && mouseY <= partyListY + partyListH) {
            int totalHeight = partyNames.size() * ROW_H;
            int maxScroll = Math.max(0, totalHeight - partyListH);
            partyScroll = Mth.clamp(partyScroll - verticalAmount * 10, 0, maxScroll);
            return true;
        }

        int lbX = x + P + 8;
        int lbY = y + LEADERBOARD_Y + 16;
        int lbW = IW - 16;
        int lbH = LEADERBOARD_H - 22;
        if (mouseX >= lbX && mouseX <= lbX + lbW && mouseY >= lbY && mouseY <= lbY + lbH) {
            int totalHeight = leaderboard.size() * ROW_H;
            int maxScroll = Math.max(0, totalHeight - lbH);
            leaderboardScroll = Mth.clamp(leaderboardScroll - verticalAmount * 10, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Drawn in renderBg.
    }

    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, C_PANEL);
        drawBorder(g, x, y, w, h, C_BDR);
    }

    private static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private void smallLabel(GuiGraphics g, Component text, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.75f, 0.75f, 1f);
        g.drawString(font, text, 0, 0, C_LABEL, false);
        g.pose().popPose();
    }

    private void small(GuiGraphics g, Component text, int x, int y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.8f, 0.8f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }
}
