package net.ledok.arenas_ld.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.DifficultyTier;
import net.ledok.arenas_ld.util.DungeonLeaderboardEntry;
import net.ledok.arenas_ld.util.LobbyVisibility;
import net.ledok.arenas_ld.util.LobbyStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class DungeonControllerScreen extends AbstractContainerScreen<DungeonControllerScreenHandler> {
    private enum UiState {
        BROWSING,
        MEMBER,
        OWNER,
        QUEUED,
        IN_DUNGEON
    }

    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, "textures/test/background.png");
    private static final int LEADERBOARD_HEIGHT = 55;
    private static final int INSTANCE_WINDOW_SIZE = 3;
    private static final int ADMIN_RESPAWN_STEP_TICKS = 30 * 20;
    private static final int ADMIN_RESPAWN_MAX_TICKS = 60 * 60 * 20;
    private Button startButton;
    private Button joinButton;
    private Button leaveButton;
    private Button tierEasyButton;
    private Button tierNormalButton;
    private Button tierHardButton;
    private Button tierNightmareButton;
    private Button prevLobbyButton;
    private Button nextLobbyButton;
    private Button acceptInviteButton;
    private Button declineInviteButton;
    private Button visibilityButton;
    private Button createLobbyButton;
    private Button disbandLobbyButton;
    private Button kickMemberButton;
    private Button sendInviteButton;
    private Button adminSettingsButton;
    private Button adminCooldownMinusButton;
    private Button adminCooldownPlusButton;
    private Button adminCooldownApplyButton;
    private Checkbox hardcoreCheckbox;
    private EditBox inviteNameBox;
    private boolean suppressHardcoreSync = false;
    private double playerListScrollAmount = 0;
    private List<Component> playerList = new ArrayList<>();
    private Component rightPanelTitle = Component.translatable("gui.arenas_ld.players");
    private int pendingInviteCount = 0;
    private List<ModPackets.DungeonControllerInfoPayload.LobbyView> lobbyViews = new ArrayList<>();
    private List<ModPackets.DungeonControllerInfoPayload.LobbyView> visibleLobbyViews = new ArrayList<>();
    private List<ModPackets.DungeonControllerInfoPayload.LobbyView> invitedLobbyViews = new ArrayList<>();
    private List<ModPackets.DungeonControllerInfoPayload.InstanceView> instanceViews = new ArrayList<>();
    private int instanceViewOffset = 0;
    private int selectedLobbyIndex = 0;
    private int selectedMemberIndex = 0;
    private List<DungeonLeaderboardEntry> leaderboard = new ArrayList<>();
    private int remainingDungeonTimeSeconds = 0;
    private int dungeonCooldownSeconds = 0;
    private DifficultyTier selectedTier = DifficultyTier.NORMAL;
    private boolean inLobby = false;
    private boolean lobbyOwner = false;
    private LobbyStatus lobbyStatus = LobbyStatus.OPEN;
    private LobbyVisibility currentLobbyVisibility = LobbyVisibility.OPEN;
    private int queuePosition = 0;
    private int queueEstimateSeconds = 0;
    private boolean controllerLocked = false;
    private boolean canManageAdmin = false;
    private boolean adminSettingsOpen = false;
    private int controllerRespawnTimeTicks = 6000;
    private int adminDraftRespawnTimeTicks = 6000;
    private int refreshTickCounter = 0;
    private double scrollAmount = 0;

    public DungeonControllerScreen(DungeonControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = 256;
        this.imageHeight = 256;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        joinButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.join_party"), button -> {
            if (!inLobby && !visibleLobbyViews.isEmpty() && selectedLobbyIndex >= 0 && selectedLobbyIndex < visibleLobbyViews.size()) {
                String lobbyId = visibleLobbyViews.get(selectedLobbyIndex).id();
                ClientPlayNetworking.send(new ModPackets.JoinDungeonLobbyPayload(menu.getPos(), lobbyId));
            } else {
                ClientPlayNetworking.send(new ModPackets.DungeonControllerActionPayload(menu.getPos(), 1));
            }
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
                        syncControllerSettings();
                    }
                })
                .build());
        hardcoreCheckbox.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.arenas_ld.hardcore_desc")));

        tierEasyButton = addRenderableWidget(Button.builder(Component.translatable(DifficultyTier.EASY.translationKey()), button -> {
            selectedTier = DifficultyTier.EASY;
            updateTierButtonVisuals();
            syncControllerSettings();
            updateInfo();
        }).bounds(x + 130, y + 120, 24, 20).build());
        tierNormalButton = addRenderableWidget(Button.builder(Component.translatable(DifficultyTier.NORMAL.translationKey()), button -> {
            selectedTier = DifficultyTier.NORMAL;
            updateTierButtonVisuals();
            syncControllerSettings();
            updateInfo();
        }).bounds(x + 156, y + 120, 24, 20).build());
        tierHardButton = addRenderableWidget(Button.builder(Component.translatable(DifficultyTier.HARD.translationKey()), button -> {
            selectedTier = DifficultyTier.HARD;
            updateTierButtonVisuals();
            syncControllerSettings();
            updateInfo();
        }).bounds(x + 182, y + 120, 24, 20).build());
        tierNightmareButton = addRenderableWidget(Button.builder(Component.translatable(DifficultyTier.NIGHTMARE.translationKey()), button -> {
            selectedTier = DifficultyTier.NIGHTMARE;
            updateTierButtonVisuals();
            syncControllerSettings();
            updateInfo();
        }).bounds(x + 208, y + 120, 24, 20).build());
        prevLobbyButton = addRenderableWidget(Button.builder(Component.literal("<"), button -> {
            if (!visibleLobbyViews.isEmpty()) {
                selectedLobbyIndex = (selectedLobbyIndex - 1 + visibleLobbyViews.size()) % visibleLobbyViews.size();
                rebuildRightPanelFromLobbies();
            }
        }).bounds(x + 130, y + 20, 20, 20).build());
        nextLobbyButton = addRenderableWidget(Button.builder(Component.literal(">"), button -> {
            if (!visibleLobbyViews.isEmpty()) {
                selectedLobbyIndex = (selectedLobbyIndex + 1) % visibleLobbyViews.size();
                rebuildRightPanelFromLobbies();
            }
        }).bounds(x + 210, y + 20, 20, 20).build());
        acceptInviteButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.accept"), button -> {
            ModPackets.DungeonControllerInfoPayload.LobbyView inviteLobby = getSelectedInvitedLobby();
            if (inviteLobby != null) {
                ClientPlayNetworking.send(new ModPackets.RespondDungeonLobbyInvitePayload(inviteLobby.id(), true));
                updateInfo();
            }
        }).bounds(x + 130, y + 44, 48, 16).build());
        declineInviteButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.decline"), button -> {
            ModPackets.DungeonControllerInfoPayload.LobbyView inviteLobby = getSelectedInvitedLobby();
            if (inviteLobby != null) {
                ClientPlayNetworking.send(new ModPackets.RespondDungeonLobbyInvitePayload(inviteLobby.id(), false));
                updateInfo();
            }
        }).bounds(x + 182, y + 44, 48, 16).build());
        visibilityButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
            currentLobbyVisibility = currentLobbyVisibility == LobbyVisibility.OPEN ? LobbyVisibility.INVITE_ONLY : LobbyVisibility.OPEN;
            ClientPlayNetworking.send(new ModPackets.UpdateDungeonLobbyVisibilityPayload(menu.getPos(), currentLobbyVisibility.name()));
            updateVisibilityButtonLabel();
            updateInfo();
        }).bounds(x + 130, y + 95, 100, 20).build());
        createLobbyButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.create_lobby"), button -> {
            ClientPlayNetworking.send(new ModPackets.CreateDungeonLobbyPayload(menu.getPos()));
            updateInfo();
        }).bounds(x + 10, y + 110, 100, 20).build());
        disbandLobbyButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.disband_lobby"), button -> {
            ClientPlayNetworking.send(new ModPackets.DisbandDungeonLobbyPayload(menu.getPos()));
            updateInfo();
        }).bounds(x + 10, y + 110, 100, 20).build());
        kickMemberButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.kick_member"), button -> {
            if (!inLobby || !lobbyOwner || selectedMemberIndex <= 0 || selectedMemberIndex >= playerList.size()) {
                return;
            }
            String selectedName = playerList.get(selectedMemberIndex).getString();
            if (selectedName.startsWith("> ")) {
                selectedName = selectedName.substring(2);
            }
            ClientPlayNetworking.send(new ModPackets.KickDungeonLobbyPlayerPayload(menu.getPos(), selectedName));
            updateInfo();
        }).bounds(x + 10, y + 140, 100, 20).build());
        inviteNameBox = new EditBox(this.font, x + 130, y + 135, 70, 18, Component.translatable("gui.arenas_ld.invite_player"));
        inviteNameBox.setMaxLength(32);
        addRenderableWidget(inviteNameBox);
        sendInviteButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.send_invite"), button -> {
            if (!inLobby || !lobbyOwner) {
                return;
            }
            String name = inviteNameBox.getValue().trim();
            if (name.isEmpty()) {
                return;
            }
            ClientPlayNetworking.send(new ModPackets.InviteDungeonLobbyPlayerPayload(menu.getPos(), name));
            inviteNameBox.setValue("");
            updateInfo();
        }).bounds(x + 204, y + 135, 26, 18).build());
        adminSettingsButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.controller_admin_settings"), button -> {
            adminSettingsOpen = !adminSettingsOpen;
            if (!adminSettingsOpen) {
                adminDraftRespawnTimeTicks = controllerRespawnTimeTicks;
            }
            updateActionButtons();
        }).bounds(x + 130, y + 170, 100, 20).build());
        adminCooldownMinusButton = addRenderableWidget(Button.builder(Component.literal("-"), button -> {
            adjustAdminRespawnDraft(-ADMIN_RESPAWN_STEP_TICKS);
            updateActionButtons();
        }).bounds(x + 130, y + 195, 20, 20).build());
        adminCooldownApplyButton = addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.apply"), button -> {
            ClientPlayNetworking.send(new ModPackets.UpdateDungeonControllerAdminSettingsPayload(menu.getPos(), adminDraftRespawnTimeTicks));
            updateInfo();
        }).bounds(x + 154, y + 195, 52, 20).build());
        adminCooldownPlusButton = addRenderableWidget(Button.builder(Component.literal("+"), button -> {
            adjustAdminRespawnDraft(ADMIN_RESPAWN_STEP_TICKS);
            updateActionButtons();
        }).bounds(x + 210, y + 195, 20, 20).build());
        updateTierButtonVisuals();
        updateVisibilityButtonLabel();
        updateActionButtons();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refreshTickCounter++;
        if (refreshTickCounter >= 20) {
            refreshTickCounter = 0;
            updateInfo();
        }
    }

    private void updateInfo() {
        if (minecraft == null || minecraft.level == null) return;
        ClientPlayNetworking.send(new ModPackets.RequestDungeonControllerInfoPayload(menu.getPos()));
    }

    public void applyServerInfo(ModPackets.DungeonControllerInfoPayload payload) {
        if (!payload.pos().equals(menu.getPos())) return;
        this.remainingDungeonTimeSeconds = payload.remainingDungeonTimeSeconds();
        this.dungeonCooldownSeconds = payload.dungeonCooldownSeconds();
        this.inLobby = payload.inLobby();
        this.lobbyOwner = payload.isLobbyOwner();
        this.controllerLocked = payload.controllerLocked();
        this.instanceViews = new ArrayList<>(payload.instances());
        instanceViewOffset = Mth.clamp(instanceViewOffset, 0, Math.max(0, instanceViews.size() - INSTANCE_WINDOW_SIZE));
        this.lobbyStatus = parseLobbyStatus(payload.lobbyStatus());
        this.currentLobbyVisibility = parseLobbyVisibility(payload.currentLobbyVisibility());
        this.queuePosition = payload.queuePosition();
        this.queueEstimateSeconds = estimateQueueWaitSeconds(payload.queuePosition(), payload.dungeonCooldownSeconds());
        int previousServerRespawnTicks = this.controllerRespawnTimeTicks;
        this.canManageAdmin = payload.canManageAdmin();
        this.controllerRespawnTimeTicks = Math.max(0, payload.controllerRespawnTimeTicks());
        if (adminDraftRespawnTimeTicks == previousServerRespawnTicks) {
            this.adminDraftRespawnTimeTicks = this.controllerRespawnTimeTicks;
        }
        if (!canManageAdmin) {
            adminSettingsOpen = false;
        }
        if (hardcoreCheckbox != null && payload.hardcoreEnabled() != hardcoreCheckbox.selected()) {
            suppressHardcoreSync = true;
            hardcoreCheckbox.onPress();
            suppressHardcoreSync = false;
        }
        List<Component> newRightPanel = new ArrayList<>();
        pendingInviteCount = 0;
        this.lobbyViews = new ArrayList<>(payload.lobbies());
        if (inLobby) {
            UiState state = getUiState();
            if (state == UiState.OWNER) {
                rightPanelTitle = Component.literal("Your Lobby");
            } else if (state == UiState.QUEUED) {
                rightPanelTitle = Component.literal("Your Lobby (Queued)");
            } else if (state == UiState.IN_DUNGEON) {
                rightPanelTitle = Component.literal("Run Team");
            } else {
                rightPanelTitle = Component.literal("Lobby Members");
            }
            newRightPanel = buildLobbyMemberPanel(payload.players());
            selectedLobbyIndex = 0;
            selectedMemberIndex = Mth.clamp(selectedMemberIndex, 0, Math.max(0, newRightPanel.size() - 1));
            if (!newRightPanel.isEmpty()) {
                Component selected = newRightPanel.get(selectedMemberIndex);
                newRightPanel.set(selectedMemberIndex, Component.literal("> ").append(selected));
            }
        } else {
            if (selectedLobbyIndex >= lobbyViews.size()) {
                selectedLobbyIndex = Math.max(0, lobbyViews.size() - 1);
            }
            newRightPanel = buildRightPanelFromLobbies();
        }
        this.playerList = newRightPanel;
        selectedTier = DifficultyTier.fromNameOrDefault(payload.selectedTier(), DifficultyTier.NORMAL);
        updateTierButtonVisuals();
        updateActionButtons();
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
        UiState uiState = getUiState();

        int instanceBaseY = y + 38;
        List<Component> instanceLines = buildInstanceSummaryLines();
        for (int i = 0; i < instanceLines.size(); i++) {
            drawScaledString(guiGraphics, instanceLines.get(i), x + 132, instanceBaseY + (i * 8), 0x99E0FF, 0.75f);
        }

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

        int subtitleBaseY = y + 50 + (instanceLines.size() * 8);
        List<Component> subtitleLines = buildStateSubtitle(uiState);
        for (int i = 0; i < subtitleLines.size(); i++) {
            drawScaledString(guiGraphics, subtitleLines.get(i), x + 132, subtitleBaseY + (i * 8), 0xFFD95A, 0.75f);
        }
        int inviteBannerConsumedHeight = renderInviteBanners(guiGraphics, x, y, uiState);

        // Right panel title
        Component playerListTitle = rightPanelTitle;
        int playerListTitleWidth = this.font.width(playerListTitle);
        int titleY = y + 50 + (instanceLines.size() * 8) + (subtitleLines.isEmpty() ? 0 : (subtitleLines.size() * 8 + 4)) + inviteBannerConsumedHeight;
        guiGraphics.drawString(this.font, playerListTitle, x + 130 + (100 / 2) - (playerListTitleWidth / 2), titleY + (15 / 2) - (this.font.lineHeight / 2), 0xFFFFFF);

        // Player List
        int playerListX = x + 135;
        int playerListY = getPlayerListY(y, uiState);
        int playerListWidth = 90;
        int playerListHeight = getPlayerListHeight(uiState);
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

        if (canManageAdmin) {
            drawScaledString(
                    guiGraphics,
                    Component.literal("Admin  CD " + formatTime(Math.max(0, controllerRespawnTimeTicks) / 20)
                            + "  Draft " + formatTime(Math.max(0, adminDraftRespawnTimeTicks) / 20)),
                    x + 130,
                    y + 236,
                    0x99E0FF,
                    0.75f
            );
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        UiState uiState = getUiState();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        if (isMouseOverInstancePanel(mouseX, mouseY, x, y)) {
            scrollInstanceWindow(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        int playerListX = x + 135;
        int playerListY = getPlayerListY(y, uiState);
        int playerListWidth = 90;
        int playerListHeight = getPlayerListHeight(uiState);
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        UiState uiState = getUiState();
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        if (handleInviteBannerClick(mouseX, mouseY, x, y, uiState)) {
            return true;
        }
        if (inLobby) {
            int playerListX = x + 135;
            int playerListY = getPlayerListY(y, uiState);
            int playerListWidth = 90;
            int playerListHeight = getPlayerListHeight(uiState);
            if (mouseX >= playerListX && mouseX <= playerListX + playerListWidth && mouseY >= playerListY && mouseY <= playerListY + playerListHeight) {
                int clicked = (int) ((mouseY - playerListY + playerListScrollAmount) / 10.0);
                if (clicked >= 0 && clicked < playerList.size()) {
                    selectedMemberIndex = clicked;
                    applyMemberSelectionMarker();
                    updateActionButtons();
                    return true;
                }
            }
        } else {
            int playerListX = x + 135;
            int playerListY = getPlayerListY(y, uiState);
            int playerListWidth = 90;
            int playerListHeight = getPlayerListHeight(uiState);
            if (mouseX >= playerListX && mouseX <= playerListX + playerListWidth && mouseY >= playerListY && mouseY <= playerListY + playerListHeight) {
                int clicked = (int) ((mouseY - playerListY + playerListScrollAmount) / 10.0);
                if (clicked >= 0 && clicked < visibleLobbyViews.size()) {
                    selectedLobbyIndex = clicked;
                    rebuildRightPanelFromLobbies();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleWidth = this.font.width(this.title);
        int titleX = (this.imageWidth / 2) - (titleWidth / 2);
        guiGraphics.drawString(this.font, this.title, titleX, 10, 0x000000, false);
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

    private void syncControllerSettings() {
        ClientPlayNetworking.send(new ModPackets.UpdateDungeonControllerSettingsPayload(menu.getPos(), hardcoreCheckbox.selected(), selectedTier.name()));
    }

    private void updateTierButtonVisuals() {
        if (tierEasyButton != null) {
            tierEasyButton.active = selectedTier != DifficultyTier.EASY;
        }
        if (tierNormalButton != null) {
            tierNormalButton.active = selectedTier != DifficultyTier.NORMAL;
        }
        if (tierHardButton != null) {
            tierHardButton.active = selectedTier != DifficultyTier.HARD;
        }
        if (tierNightmareButton != null) {
            tierNightmareButton.active = selectedTier != DifficultyTier.NIGHTMARE;
        }
    }

    private void updateActionButtons() {
        if (joinButton == null || leaveButton == null || startButton == null || tierEasyButton == null || tierNormalButton == null || tierHardButton == null || tierNightmareButton == null || hardcoreCheckbox == null || prevLobbyButton == null || nextLobbyButton == null || acceptInviteButton == null || declineInviteButton == null || visibilityButton == null || createLobbyButton == null || disbandLobbyButton == null || kickMemberButton == null || inviteNameBox == null || sendInviteButton == null || adminSettingsButton == null || adminCooldownMinusButton == null || adminCooldownPlusButton == null || adminCooldownApplyButton == null) {
            return;
        }
        UiState uiState = getUiState();
        boolean inDungeonState = controllerLocked || lobbyStatus == LobbyStatus.IN_DUNGEON || remainingDungeonTimeSeconds > 0;
        boolean queuedState = lobbyStatus == LobbyStatus.QUEUED;
        boolean adminView = false;
        boolean freeInstanceAvailable = hasAnyFreeInstance();

        joinButton.visible = !adminView && uiState == UiState.BROWSING;
        joinButton.active = joinButton.visible && canJoinSelectedLobby();
        joinButton.setMessage(Component.translatable(canJoinSelectedLobby() ? "gui.arenas_ld.join_selected" : "gui.arenas_ld.invite_only"));
        prevLobbyButton.visible = !adminView && uiState == UiState.BROWSING;
        nextLobbyButton.visible = !adminView && uiState == UiState.BROWSING;
        prevLobbyButton.active = prevLobbyButton.visible && visibleLobbyViews.size() > 1;
        nextLobbyButton.active = nextLobbyButton.visible && visibleLobbyViews.size() > 1;
        acceptInviteButton.visible = false;
        declineInviteButton.visible = false;
        acceptInviteButton.active = false;
        declineInviteButton.active = false;
        visibilityButton.visible = !adminView && uiState == UiState.OWNER;
        visibilityButton.active = visibilityButton.visible;
        createLobbyButton.visible = !adminView && uiState == UiState.BROWSING;
        createLobbyButton.active = createLobbyButton.visible;
        disbandLobbyButton.visible = !adminView && uiState == UiState.OWNER;
        disbandLobbyButton.active = disbandLobbyButton.visible;
        kickMemberButton.visible = !adminView && uiState == UiState.OWNER;
        kickMemberButton.active = kickMemberButton.visible && selectedMemberIndex > 0 && selectedMemberIndex < playerList.size();
        inviteNameBox.visible = !adminView && uiState == UiState.OWNER && currentLobbyVisibility == LobbyVisibility.INVITE_ONLY;
        inviteNameBox.setEditable(inviteNameBox.visible);
        sendInviteButton.visible = inviteNameBox.visible;
        sendInviteButton.active = inviteNameBox.visible && !inviteNameBox.getValue().trim().isEmpty();
        leaveButton.visible = !adminView && (uiState == UiState.MEMBER || uiState == UiState.OWNER || uiState == UiState.QUEUED);
        leaveButton.active = leaveButton.visible;
        leaveButton.setMessage(Component.translatable(queuedState ? "gui.arenas_ld.leave_queue" : "gui.arenas_ld.leave_party"));

        startButton.visible = !adminView && (uiState == UiState.OWNER || uiState == UiState.QUEUED || uiState == UiState.IN_DUNGEON);
        boolean canStart = uiState == UiState.OWNER;
        startButton.active = canStart;
        if (queuedState) {
            if (queuePosition > 0) {
                startButton.setMessage(Component.translatable("gui.arenas_ld.queued_position", queuePosition));
            } else {
                startButton.setMessage(Component.translatable("gui.arenas_ld.queued"));
            }
        } else if (inDungeonState) {
            startButton.setMessage(Component.translatable("gui.arenas_ld.in_dungeon"));
        } else if (uiState == UiState.OWNER && !freeInstanceAvailable) {
            startButton.setMessage(Component.literal("Join Queue"));
        } else {
            startButton.setMessage(Component.translatable("gui.arenas_ld.start_dungeon"));
        }

        boolean canEditSettings = !adminView && (uiState == UiState.OWNER || uiState == UiState.QUEUED);
        tierEasyButton.visible = canEditSettings;
        tierNormalButton.visible = canEditSettings;
        tierHardButton.visible = canEditSettings;
        tierNightmareButton.visible = canEditSettings;
        hardcoreCheckbox.visible = canEditSettings;
        if (canEditSettings) {
            updateTierButtonVisuals();
        }
        hardcoreCheckbox.active = canEditSettings;

        adminSettingsButton.visible = false;
        adminSettingsButton.active = false;
        boolean showAdminSettings = canManageAdmin;
        adminCooldownMinusButton.visible = showAdminSettings;
        adminCooldownPlusButton.visible = showAdminSettings;
        adminCooldownApplyButton.visible = showAdminSettings;
        adminCooldownMinusButton.active = showAdminSettings && adminDraftRespawnTimeTicks > 0;
        adminCooldownPlusButton.active = showAdminSettings && adminDraftRespawnTimeTicks < ADMIN_RESPAWN_MAX_TICKS;
        adminCooldownApplyButton.active = showAdminSettings && adminDraftRespawnTimeTicks != controllerRespawnTimeTicks;
        updateVisibilityButtonLabel();
    }

    private void adjustAdminRespawnDraft(int deltaTicks) {
        int updated = adminDraftRespawnTimeTicks + deltaTicks;
        adminDraftRespawnTimeTicks = Mth.clamp(updated, 0, ADMIN_RESPAWN_MAX_TICKS);
    }

    private boolean isAdminView() {
        return canManageAdmin && adminSettingsOpen;
    }

    private int renderInviteBanners(GuiGraphics guiGraphics, int x, int y, UiState uiState) {
        int bannerCount = getInviteBannerCount(uiState);
        if (bannerCount == 0) {
            return 0;
        }
        int rowHeight = 12;
        int baseY = y + 70;
        String acceptLabel = "[Accept]";
        String declineLabel = "[Decline]";
        int acceptX = x + 178;
        int declineX = x + 214;
        for (int i = 0; i < bannerCount; i++) {
            ModPackets.DungeonControllerInfoPayload.LobbyView lobby = invitedLobbyViews.get(i);
            int rowY = baseY + (i * rowHeight);
            String tier = lobby.tier() != null ? lobby.tier().toLowerCase() : "normal";
            String owner = trimForBanner(lobby.ownerName(), 10);
            guiGraphics.drawString(this.font, "Invite: " + owner + " (" + tier + ")", x + 132, rowY, 0xFFD95A);
            guiGraphics.drawString(this.font, acceptLabel, acceptX, rowY, 0x55FF55);
            guiGraphics.drawString(this.font, declineLabel, declineX, rowY, 0xFF5555);
        }
        int hidden = invitedLobbyViews.size() - bannerCount;
        if (hidden > 0) {
            guiGraphics.drawString(this.font, "+" + hidden + " more invites", x + 132, baseY + (bannerCount * rowHeight), 0xAAAAAA);
            return (bannerCount * rowHeight) + rowHeight + 4;
        }
        return (bannerCount * rowHeight) + 4;
    }

    private boolean handleInviteBannerClick(double mouseX, double mouseY, int x, int y, UiState uiState) {
        int bannerCount = getInviteBannerCount(uiState);
        if (bannerCount == 0) {
            return false;
        }
        int rowHeight = 12;
        int baseY = y + 70;
        String acceptLabel = "[Accept]";
        String declineLabel = "[Decline]";
        int acceptX = x + 178;
        int declineX = x + 214;
        int acceptWidth = this.font.width(acceptLabel);
        int declineWidth = this.font.width(declineLabel);
        for (int i = 0; i < bannerCount; i++) {
            int rowY = baseY + (i * rowHeight);
            if (mouseY < rowY || mouseY > rowY + this.font.lineHeight) {
                continue;
            }
            ModPackets.DungeonControllerInfoPayload.LobbyView lobby = invitedLobbyViews.get(i);
            if (mouseX >= acceptX && mouseX <= acceptX + acceptWidth) {
                respondToInvite(lobby, true);
                return true;
            }
            if (mouseX >= declineX && mouseX <= declineX + declineWidth) {
                respondToInvite(lobby, false);
                return true;
            }
        }
        return false;
    }

    private void respondToInvite(ModPackets.DungeonControllerInfoPayload.LobbyView lobby, boolean accept) {
        if (lobby == null) {
            return;
        }
        ClientPlayNetworking.send(new ModPackets.RespondDungeonLobbyInvitePayload(lobby.id(), accept));
        updateInfo();
    }

    private int getInviteBannerCount(UiState uiState) {
        if (uiState != UiState.BROWSING || inLobby || invitedLobbyViews.isEmpty()) {
            return 0;
        }
        return Math.min(3, invitedLobbyViews.size());
    }

    private int getPlayerListY(int baseY, UiState uiState) {
        int bannersHeight = getInviteBannerCount(uiState) * 12;
        if (getInviteBannerCount(uiState) > 0) {
            bannersHeight += 4;
        }
        return baseY + 70 + bannersHeight;
    }

    private int getPlayerListHeight(UiState uiState) {
        int consumed = getInviteBannerCount(uiState) * 12;
        if (getInviteBannerCount(uiState) > 0) {
            consumed += 4;
        }
        return Math.max(32, 80 - consumed);
    }

    private String trimForBanner(String text, int maxLen) {
        if (text == null || text.isEmpty()) {
            return "Unknown";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(1, maxLen - 1)) + "~";
    }

    private LobbyStatus parseLobbyStatus(String statusName) {
        try {
            return LobbyStatus.valueOf(statusName);
        } catch (IllegalArgumentException ignored) {
            return LobbyStatus.OPEN;
        }
    }

    private LobbyVisibility parseLobbyVisibility(String visibilityName) {
        try {
            return LobbyVisibility.valueOf(visibilityName);
        } catch (IllegalArgumentException ignored) {
            return LobbyVisibility.OPEN;
        }
    }

    private void rebuildRightPanelFromLobbies() {
        this.playerList = buildRightPanelFromLobbies();
        updateActionButtons();
    }

    private List<Component> buildRightPanelFromLobbies() {
        rightPanelTitle = Component.literal("Open Lobbies");
        List<Component> newRightPanel = new ArrayList<>();
        visibleLobbyViews = new ArrayList<>();
        invitedLobbyViews = new ArrayList<>();
        pendingInviteCount = 0;
        for (ModPackets.DungeonControllerInfoPayload.LobbyView lobby : lobbyViews) {
            if ("IN_DUNGEON".equalsIgnoreCase(lobby.status())) {
                continue;
            }
            visibleLobbyViews.add(lobby);
            if (lobby.invited()) {
                pendingInviteCount++;
                invitedLobbyViews.add(lobby);
            }
            String line = formatLobbyRow(lobby);
            newRightPanel.add(Component.literal(line));
        }
        if (newRightPanel.isEmpty()) {
            newRightPanel.add(Component.literal("No open lobbies"));
            return newRightPanel;
        }
        selectedLobbyIndex = Mth.clamp(selectedLobbyIndex, 0, newRightPanel.size() - 1);
        Component selected = newRightPanel.get(selectedLobbyIndex);
        newRightPanel.set(selectedLobbyIndex, Component.literal("> ").append(selected));
        return newRightPanel;
    }

    private ModPackets.DungeonControllerInfoPayload.LobbyView getSelectedInvitedLobby() {
        if (!visibleLobbyViews.isEmpty() && selectedLobbyIndex >= 0 && selectedLobbyIndex < visibleLobbyViews.size()) {
            ModPackets.DungeonControllerInfoPayload.LobbyView selected = visibleLobbyViews.get(selectedLobbyIndex);
            if (selected.invited()) {
                return selected;
            }
        }
        for (ModPackets.DungeonControllerInfoPayload.LobbyView lobby : visibleLobbyViews) {
            if (lobby.invited()) {
                return lobby;
            }
        }
        return null;
    }

    private boolean canJoinSelectedLobby() {
        if (visibleLobbyViews.isEmpty() || selectedLobbyIndex < 0 || selectedLobbyIndex >= visibleLobbyViews.size()) {
            return false;
        }
        ModPackets.DungeonControllerInfoPayload.LobbyView selected = visibleLobbyViews.get(selectedLobbyIndex);
        if ("INVITE_ONLY".equalsIgnoreCase(selected.visibility()) && !selected.invited()) {
            return false;
        }
        if ("QUEUED".equalsIgnoreCase(selected.status()) || "IN_DUNGEON".equalsIgnoreCase(selected.status())) {
            return false;
        }
        return selected.size() < selected.maxSize();
    }

    private void updateVisibilityButtonLabel() {
        if (visibilityButton != null) {
            String key = currentLobbyVisibility == LobbyVisibility.OPEN ? "gui.arenas_ld.visibility_open" : "gui.arenas_ld.visibility_invite_only";
            visibilityButton.setMessage(Component.translatable(key));
        }
    }

    private void applyMemberSelectionMarker() {
        if (!inLobby || playerList.isEmpty()) {
            return;
        }
        List<Component> clean = new ArrayList<>();
        for (Component c : playerList) {
            String s = c.getString();
            clean.add(Component.literal(s.startsWith("> ") ? s.substring(2) : s));
        }
        selectedMemberIndex = Mth.clamp(selectedMemberIndex, 0, clean.size() - 1);
        Component selected = clean.get(selectedMemberIndex);
        clean.set(selectedMemberIndex, Component.literal("> ").append(selected));
        playerList = clean;
    }

    private UiState getUiState() {
        boolean inDungeonState = controllerLocked || lobbyStatus == LobbyStatus.IN_DUNGEON || remainingDungeonTimeSeconds > 0;
        if (!inLobby) {
            return UiState.BROWSING;
        }
        if (inDungeonState) {
            return UiState.IN_DUNGEON;
        }
        if (lobbyStatus == LobbyStatus.QUEUED) {
            return UiState.QUEUED;
        }
        return lobbyOwner ? UiState.OWNER : UiState.MEMBER;
    }

    private List<Component> buildStateSubtitle(UiState state) {
        List<Component> lines = new ArrayList<>();
        switch (state) {
            case BROWSING -> {
                if (pendingInviteCount > 0) {
                    lines.add(Component.literal("Pending invites: " + pendingInviteCount));
                }
                long queuedCount = visibleLobbyViews.stream().filter(l -> "QUEUED".equalsIgnoreCase(l.status())).count();
                if (queuedCount > 0) {
                    lines.add(Component.literal("Queued lobbies: " + queuedCount));
                }
            }
            case MEMBER -> {
                lines.add(Component.literal("Waiting for lobby owner"));
                lines.add(Component.literal("Tier: " + selectedTier.name().toLowerCase() + (hardcoreCheckbox != null && hardcoreCheckbox.selected() ? " | hardcore" : "")));
            }
            case OWNER -> {
                lines.add(Component.literal("Owner controls active"));
                lines.add(Component.literal("Tier: " + selectedTier.name().toLowerCase() + (hardcoreCheckbox != null && hardcoreCheckbox.selected() ? " | hardcore" : "")));
                lines.add(Component.literal("Visibility: " + (currentLobbyVisibility == LobbyVisibility.OPEN ? "open" : "invite")));
                if (!hasAnyFreeInstance()) {
                    lines.add(Component.literal("All instances busy - start will queue"));
                }
            }
            case QUEUED -> {
                lines.add(Component.literal("Queued for next free instance"));
                if (queuePosition > 0) {
                    lines.add(Component.literal("Queue position: #" + queuePosition));
                }
                if (queueEstimateSeconds > 0) {
                    lines.add(Component.literal("Estimated wait: " + formatTime(queueEstimateSeconds)));
                } else if (dungeonCooldownSeconds > 0) {
                    lines.add(Component.literal("Next slot in: " + formatTime(dungeonCooldownSeconds)));
                }
            }
            case IN_DUNGEON -> {
                lines.add(Component.literal("Dungeon run active"));
                if (remainingDungeonTimeSeconds > 0) {
                    lines.add(Component.literal("Run time left: " + formatTime(remainingDungeonTimeSeconds)));
                }
            }
        }
        return lines;
    }

    private List<Component> buildLobbyMemberPanel(List<String> names) {
        List<Component> rows = new ArrayList<>();
        if (names == null || names.isEmpty()) {
            rows.add(Component.literal("No members"));
            return rows;
        }
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (i == 0) {
                rows.add(Component.literal("★ " + name + " (owner)"));
            } else {
                rows.add(Component.literal("  " + name));
            }
        }
        return rows;
    }

    private int estimateQueueWaitSeconds(int position, int nextCooldownSeconds) {
        if (position <= 0 || nextCooldownSeconds <= 0) {
            return 0;
        }
        return Math.max(0, position * nextCooldownSeconds);
    }

    private List<Component> buildInstanceSummaryLines() {
        List<Component> lines = new ArrayList<>();
        if (instanceViews.isEmpty()) {
            lines.add(Component.literal("Instances: none"));
            return lines;
        }
        int start = Math.min(instanceViewOffset, Math.max(0, instanceViews.size() - 1));
        int endExclusive = Math.min(instanceViews.size(), start + INSTANCE_WINDOW_SIZE);
        lines.add(Component.literal("Instances " + (start + 1) + "-" + endExclusive + "/" + instanceViews.size()));
        for (int i = start; i < endExclusive; i++) {
            ModPackets.DungeonControllerInfoPayload.InstanceView instance = instanceViews.get(i);
            lines.add(Component.literal("#" + (i + 1) + " " + shortInstanceStatus(instance)));
        }
        if (instanceViews.size() > INSTANCE_WINDOW_SIZE) {
            lines.add(Component.literal("Scroll for more"));
        }
        return lines;
    }

    private String shortInstanceStatus(ModPackets.DungeonControllerInfoPayload.InstanceView instance) {
        if ("FREE".equalsIgnoreCase(instance.status())) {
            return "FREE";
        }
        if ("RUNNING".equalsIgnoreCase(instance.status())) {
            return "RUN";
        }
        if ("COOLDOWN".equalsIgnoreCase(instance.status())) {
            return "CD " + formatTime(Math.max(0, instance.cooldownSeconds()));
        }
        return instance.status();
    }

    private boolean isMouseOverInstancePanel(double mouseX, double mouseY, int x, int y) {
        int panelX = x + 130;
        int panelY = y + 36;
        int panelWidth = 100;
        int panelHeight = 32;
        return mouseX >= panelX && mouseX <= panelX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight;
    }

    private void scrollInstanceWindow(int delta) {
        int maxOffset = Math.max(0, instanceViews.size() - INSTANCE_WINDOW_SIZE);
        instanceViewOffset = Mth.clamp(instanceViewOffset + delta, 0, maxOffset);
    }

    private String formatLobbyRow(ModPackets.DungeonControllerInfoPayload.LobbyView lobby) {
        String inviteMarker = lobby.invited() ? "*" : "";
        String owner = trimForBanner(lobby.ownerName(), 8);
        String visibility = "INVITE_ONLY".equalsIgnoreCase(lobby.visibility()) ? "L" : "O";
        String status;
        if ("QUEUED".equalsIgnoreCase(lobby.status())) {
            status = lobby.queuePosition() > 0 ? "Q#" + lobby.queuePosition() : "Q";
        } else if ("IN_DUNGEON".equalsIgnoreCase(lobby.status())) {
            status = "RUN";
        } else {
            status = "RDY";
        }
        String action = rowActionLabel(lobby);
        String row = String.format(
                "%s%s %d/%d %s %s %s %s",
                owner,
                inviteMarker,
                lobby.size(),
                lobby.maxSize(),
                shortTier(lobby.tier()),
                visibility,
                status,
                action
        );
        return clampLobbyRow(row, 30);
    }

    private boolean hasAnyFreeInstance() {
        for (ModPackets.DungeonControllerInfoPayload.InstanceView instance : instanceViews) {
            if ("FREE".equalsIgnoreCase(instance.status())) {
                return true;
            }
        }
        return false;
    }

    private String rowActionLabel(ModPackets.DungeonControllerInfoPayload.LobbyView lobby) {
        if ("IN_DUNGEON".equalsIgnoreCase(lobby.status()) || "QUEUED".equalsIgnoreCase(lobby.status())) {
            return "[BUSY]";
        }
        if (lobby.size() >= lobby.maxSize()) {
            return "[FULL]";
        }
        if ("INVITE_ONLY".equalsIgnoreCase(lobby.visibility()) && !lobby.invited()) {
            return "[LOCK]";
        }
        return "[JOIN]";
    }

    private String shortTier(String tier) {
        if (tier == null || tier.isEmpty()) {
            return "N";
        }
        return switch (tier.toUpperCase()) {
            case "EASY" -> "E";
            case "NORMAL" -> "N";
            case "HARD" -> "H";
            case "NIGHTMARE" -> "NM";
            default -> trimForBanner(tier, 2).toUpperCase();
        };
    }

    private String clampLobbyRow(String row, int maxChars) {
        if (row.length() <= maxChars) {
            return row;
        }
        if (maxChars <= 1) {
            return "~";
        }
        return row.substring(0, maxChars - 1) + "~";
    }
}
