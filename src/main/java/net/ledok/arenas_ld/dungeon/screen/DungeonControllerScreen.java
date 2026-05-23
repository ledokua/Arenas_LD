package net.ledok.arenas_ld.dungeon.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.packet.AcceptInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.CreateLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.DeclineInvitePayload;
import net.ledok.arenas_ld.dungeon.packet.JoinLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.LeaveLobbyPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyHardcorePayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyTierPayload;
import net.ledok.arenas_ld.dungeon.packet.SetLobbyVisibilityPayload;
import net.ledok.arenas_ld.dungeon.packet.StartRunPayload;
import net.ledok.arenas_ld.dungeon.packet.ToggleReadyPayload;
import net.ledok.arenas_ld.dungeon.run.DifficultyTier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DungeonControllerScreen extends AbstractContainerScreen<DungeonControllerScreenHandler> {
    private static final int WIDTH = 340;
    private static final int HEIGHT = 230;

    private enum Tab {
        LOBBIES,
        MY_LOBBY
    }

    private final List<Lobby> visibleLobbies;
    private final List<PendingInvite> myInvites;
    private Optional<Lobby> ownLobby;
    private Tab currentTab = Tab.LOBBIES;

    public DungeonControllerScreen(DungeonControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.imageWidth = WIDTH;
        this.imageHeight = HEIGHT;
        this.inventoryLabelY = this.imageHeight + 1000;
        this.visibleLobbies = new ArrayList<>(handler.getVisibleLobbies());
        this.myInvites = new ArrayList<>(handler.getMyInvites());
        this.ownLobby = handler.getOwnLobby();
    }

    @Override
    protected void init() {
        super.init();
        rebuildWidgets();
    }

    @Override
    protected void rebuildWidgets() {
        clearWidgets();
        int x = leftPos;
        int y = topPos;

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.tab.lobbies"), b -> {
            currentTab = Tab.LOBBIES;
            rebuildWidgets();
        }).bounds(x + 8, y + 24, 90, 18).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.tab.my_lobby"), b -> {
            currentTab = Tab.MY_LOBBY;
            rebuildWidgets();
        }).bounds(x + 102, y + 24, 90, 18).build());

        if (currentTab == Tab.LOBBIES) {
            buildLobbiesTab(x, y);
        } else {
            buildMyLobbyTab(x, y);
        }
    }

    private void buildLobbiesTab(int x, int y) {
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.button.create"), b -> {
            ClientPlayNetworking.send(new CreateLobbyPayload(menu.getBlockPos()));
        }).bounds(x + 8, y + HEIGHT - 24, 120, 16).build());

        int rowY = y + 50;
        int shown = 0;
        for (Lobby lobby : visibleLobbies) {
            if (shown >= 5) break;
            if (lobby.visibility() != LobbyVisibility.PUBLIC) continue;
            final UUID lobbyId = lobby.lobbyId();
            addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.button.join"), b -> {
                ClientPlayNetworking.send(new JoinLobbyPayload(menu.getBlockPos(), lobbyId));
            }).bounds(x + WIDTH - 62, rowY - 2, 52, 14).build());
            rowY += 18;
            shown++;
        }

        int inviteY = y + 156;
        for (PendingInvite invite : myInvites) {
            addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.button.accept"), b -> {
                ClientPlayNetworking.send(new AcceptInvitePayload(menu.getBlockPos(), invite.lobbyId()));
            }).bounds(x + WIDTH - 116, inviteY - 2, 50, 14).build());
            addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.button.decline"), b -> {
                ClientPlayNetworking.send(new DeclineInvitePayload(menu.getBlockPos(), invite.lobbyId()));
            }).bounds(x + WIDTH - 62, inviteY - 2, 52, 14).build());
            inviteY += 18;
        }
    }

    private void buildMyLobbyTab(int x, int y) {
        if (ownLobby.isEmpty()) {
            return;
        }
        Lobby lobby = ownLobby.get();
        boolean isOwner = minecraft != null && minecraft.player != null && lobby.ownerUuid().equals(minecraft.player.getUUID());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.button.ready"), b -> {
            ClientPlayNetworking.send(new ToggleReadyPayload(menu.getBlockPos()));
        }).bounds(x + 8, y + HEIGHT - 24, 90, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.button.leave"), b -> {
            ClientPlayNetworking.send(new LeaveLobbyPayload(menu.getBlockPos()));
        }).bounds(x + 102, y + HEIGHT - 24, 90, 16).build());

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.button.start"), b -> {
            ClientPlayNetworking.send(new StartRunPayload(menu.getBlockPos()));
        }).bounds(x + WIDTH - 72, y + HEIGHT - 24, 64, 16).build()).active = isOwner && lobby.allReady();

        addRenderableWidget(Button.builder(Component.literal("N"), b ->
            ClientPlayNetworking.send(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.NORMAL)))
            .bounds(x + 70, y + 116, 18, 14).build()).active = isOwner;
        addRenderableWidget(Button.builder(Component.literal("H"), b ->
            ClientPlayNetworking.send(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.HARD)))
            .bounds(x + 90, y + 116, 18, 14).build()).active = isOwner;
        addRenderableWidget(Button.builder(Component.literal("X"), b ->
            ClientPlayNetworking.send(new SetLobbyTierPayload(menu.getBlockPos(), DifficultyTier.HELL)))
            .bounds(x + 110, y + 116, 18, 14).build()).active = isOwner;

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.visibility.public"), b ->
            ClientPlayNetworking.send(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.PUBLIC)))
            .bounds(x + 8, y + 136, 64, 14).build()).active = isOwner;
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.visibility.friends"), b ->
            ClientPlayNetworking.send(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.FRIENDS)))
            .bounds(x + 76, y + 136, 64, 14).build()).active = isOwner;
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.visibility.private"), b ->
            ClientPlayNetworking.send(new SetLobbyVisibilityPayload(menu.getBlockPos(), LobbyVisibility.PRIVATE)))
            .bounds(x + 144, y + 136, 64, 14).build()).active = isOwner;

        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.hardcore.on"), b ->
            ClientPlayNetworking.send(new SetLobbyHardcorePayload(menu.getBlockPos(), true)))
            .bounds(x + 8, y + 154, 64, 14).build()).active = isOwner;
        addRenderableWidget(Button.builder(Component.translatable("gui.arenas_ld.dungeon_controller.hardcore.off"), b ->
            ClientPlayNetworking.send(new SetLobbyHardcorePayload(menu.getBlockPos(), false)))
            .bounds(x + 76, y + 154, 64, 14).build()).active = isOwner;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        guiGraphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xFF121620);
        guiGraphics.fill(x + 1, y + 1, x + WIDTH - 1, y + 20, 0xFF1A2130);
        guiGraphics.fill(x + 6, y + 46, x + WIDTH - 6, y + HEIGHT - 30, 0xFF0E1320);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int x = leftPos;
        int y = topPos;
        guiGraphics.drawString(font, this.title, x + 8, y + 8, 0xFFFFFF, false);

        if (currentTab == Tab.LOBBIES) {
            drawLobbiesTab(guiGraphics, x, y);
        } else {
            drawMyLobbyTab(guiGraphics, x, y);
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void drawLobbiesTab(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.public_lobbies"), x + 8, y + 50, 0xC0C8E0, false);
        int rowY = y + 64;
        int shown = 0;
        for (Lobby lobby : visibleLobbies) {
            if (shown >= 5) break;
            if (lobby.visibility() != LobbyVisibility.PUBLIC) continue;
            guiGraphics.drawString(font,
                Component.translatable("gui.arenas_ld.dungeon_controller.lobby_summary",
                    lobby.ownerName(),
                    lobby.members().size(),
                    menu.getMaxPartySize(),
                    lobby.selectedTier().name()),
                x + 10,
                rowY,
                0xE0E0E0,
                false);
            rowY += 18;
            shown++;
        }
        if (shown == 0) {
            guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.no_lobbies"), x + 10, y + 64, 0x808AA6, false);
        }

        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.invites"), x + 8, y + 146, 0xC0C8E0, false);
        int inviteY = y + 160;
        for (PendingInvite invite : myInvites) {
            guiGraphics.drawString(font, invite.lobbyId().toString().substring(0, 8), x + 10, inviteY, 0xE0E0E0, false);
            inviteY += 18;
        }
        if (myInvites.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.no_invites"), x + 10, y + 160, 0x808AA6, false);
        }
    }

    private void drawMyLobbyTab(GuiGraphics guiGraphics, int x, int y) {
        if (ownLobby.isEmpty()) {
            guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.no_own_lobby"), x + 8, y + 54, 0x808AA6, false);
            return;
        }
        Lobby lobby = ownLobby.get();
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.owner", lobby.ownerName()), x + 8, y + 50, 0xC0C8E0, false);
        guiGraphics.drawString(font,
            Component.translatable("gui.arenas_ld.dungeon_controller.members", lobby.members().size(), menu.getMaxPartySize()),
            x + 8,
            y + 64,
            0xC0C8E0,
            false);

        int memberY = y + 78;
        int memberRow = 0;
        int maxMembers = menu.getMaxPartySize();
        for (UUID member : lobby.members()) {
            if (memberRow >= maxMembers) break;
            String name = lobby.memberNames().getOrDefault(member, member.toString().substring(0, 8));
            Component statusComponent = lobby.readyMembers().contains(member)
                ? Component.translatable("gui.arenas_ld.dungeon_controller.status.ready").withStyle(ChatFormatting.GREEN)
                : Component.translatable("gui.arenas_ld.dungeon_controller.status.pending").withStyle(ChatFormatting.GRAY);
            Component memberLine = Component.translatable("gui.arenas_ld.dungeon_controller.member_line", name, statusComponent);
            guiGraphics.drawString(font, memberLine, x + 10, memberY, 0xE0E0E0, false);
            memberY += 12;
            memberRow++;
        }

        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.tier", lobby.selectedTier().name()), x + 8, y + 118, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.visibility", lobby.visibility().name()), x + 8, y + 138, 0xC0C8E0, false);
        guiGraphics.drawString(font, Component.translatable("gui.arenas_ld.dungeon_controller.hardcore", lobby.hardcoreEnabled()), x + 8, y + 156, 0xC0C8E0, false);
    }
}
