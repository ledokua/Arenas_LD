package net.ledok.arenas_ld.arena.screen;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.arena.blockentity.ArenaControllerBlockEntity;
import net.ledok.arenas_ld.arena.packet.ArenaLobbyActionPayload;
import net.ledok.arenas_ld.arena.packet.ArenaLobbyTargetPayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetHardcorePayload;
import net.ledok.arenas_ld.arena.packet.ArenaSetVisibilityPayload;
import net.ledok.arenas_ld.dungeon.lobby.Lobby;
import net.ledok.arenas_ld.dungeon.lobby.LobbyStatus;
import net.ledok.arenas_ld.dungeon.lobby.LobbyVisibility;
import net.ledok.arenas_ld.dungeon.lobby.PendingInvite;
import net.ledok.arenas_ld.dungeon.lobby.PendingJoinRequest;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Player lobby view for the arena controller. Reads live state from the client-synced
 * {@link ArenaControllerBlockEntity} and sends the controller action packets. Functional owo UI in
 * the shared dark style; rebuilds when the synced lobby state changes. Ignores the inventory key.
 */
public class ArenaControllerScreen extends BaseOwoHandledScreen<FlowLayout, ArenaControllerScreenHandler> {
    private static final int BG = 0xFF070E14, PANEL = 0xFF121922, PANEL_2 = 0xFF0C1218;
    private static final int HAIRLINE = 0xFF283442, HAIRLINE_HI = 0xFF3A4A5C, ROW_BG = 0xFF19222D;
    private static final int INK = 0xFFE8EEF5, INK_MID = 0xFF9AA8B8, INK_DIM = 0xFF5F6E80;
    private static final int GOOD = 0xFF86D36C, DANGER = 0xFFE8624A, ACCENT = 0xFFA98BE8, ACCENT_DARK = 0xFF6C4FB5;

    private final BlockPos pos;
    private FlowLayout contentArea;
    private int lastSignature = Integer.MIN_VALUE;

    public ArenaControllerScreen(ArenaControllerScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.pos = handler.getBlockPos();
        this.titleLabelY = 9999;
        this.inventoryLabelY = 9999;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.flat(BG));
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int w = Math.max(460, Math.min(600, this.width - 24));
        int h = Math.max(280, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(w), Sizing.fixed(h));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));
        shell.child(header());

        contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        contentArea.padding(Insets.of(10));
        contentArea.gap(6);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), contentArea);
        scroll.surface(Surface.flat(PANEL));
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        shell.child(scroll);
        root.child(shell);

        rebuildUi();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        int sig = computeSignature();
        if (sig != lastSignature) rebuildUi();
        super.render(ctx, mouseX, mouseY, delta);
    }

    @org.jetbrains.annotations.Nullable
    private ArenaControllerBlockEntity controller() {
        return this.menu.controller;
    }

    private UUID self() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : new UUID(0, 0);
    }

    private int computeSignature() {
        ArenaControllerBlockEntity c = controller();
        if (c == null) return 0;
        int sig = c.getLobbies().size() * 31 + c.getQueuedLobbyIds().size();
        Lobby own = c.getLobbyByMember(self());
        if (own != null) {
            sig = sig * 31 + own.members().size();
            sig = sig * 31 + own.readyMembers().size();
            sig = sig * 31 + own.status().ordinal();
            sig = sig * 31 + own.visibility().ordinal();
            sig = sig * 31 + (own.hardcoreEnabled() ? 1 : 0);
        }
        return sig;
    }

    private void rebuildUi() {
        if (contentArea == null) return;
        lastSignature = computeSignature();
        contentArea.clearChildren();
        ArenaControllerBlockEntity c = controller();
        if (c == null) {
            contentArea.child(text(Component.translatable("gui.arenas_ld.arena.not_loaded"), DANGER));
            return;
        }
        UUID me = self();
        Lobby own = c.getLobbyByMember(me);

        if (own == null) {
            contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.lobbies")));
            boolean any = false;
            for (Lobby lobby : c.getLobbies()) {
                if (lobby.status() == LobbyStatus.IN_RUN || lobby.status() == LobbyStatus.DISBANDED) continue;
                if (lobby.visibility() == LobbyVisibility.PRIVATE) continue;
                contentArea.child(lobbyRow(c, lobby, me));
                any = true;
            }
            if (!any) contentArea.child(text(Component.translatable("gui.arenas_ld.arena.no_lobbies"), INK_DIM));
            contentArea.child(spacer(6));
            ButtonComponent create = button(Component.translatable("gui.arenas_ld.arena.create_lobby"),
                b -> send(new ArenaLobbyActionPayload(pos, "create")));
            create.horizontalSizing(Sizing.fixed(140));
            contentArea.child(create);

            // Incoming invites to me.
            boolean firstInvite = true;
            for (PendingInvite inv : c.getPendingInvites()) {
                if (!inv.invitedUuid().equals(me)) continue;
                if (firstInvite) { contentArea.child(spacer(6)); contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.invites"))); firstInvite = false; }
                contentArea.child(inviteRow(inv));
            }
        } else {
            buildOwnLobby(c, own, me);
        }
    }

    private void buildOwnLobby(ArenaControllerBlockEntity c, Lobby own, UUID me) {
        boolean owner = own.isOwner(me);
        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.my_lobby")));

        int queueIdx = c.getQueuedLobbyIds().indexOf(own.lobbyId());
        String status = queueIdx >= 0
            ? Component.translatable("gui.arenas_ld.arena.queued_pos", queueIdx + 1).getString()
            : own.status().name();
        contentArea.child(text(Component.literal("Status: " + status + "   Size: " + own.members().size()
            + "/" + c.getMaxPartySize() + "   Vis: " + own.visibility().name()
            + (own.hardcoreEnabled() ? "   [HARDCORE]" : "")), INK_MID));

        for (UUID member : own.members()) {
            String name = own.memberNames().getOrDefault(member, member.toString().substring(0, 8));
            boolean ready = own.readyMembers().contains(member);
            FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
            row.surface(Surface.flat(ROW_BG));
            row.padding(Insets.of(2, 2, 6, 6));
            row.gap(6);
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.child(text(Component.literal((own.isOwner(member) ? "★ " : "") + name), ready ? GOOD : INK));
            row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
            row.child(text(Component.literal(ready ? "READY" : "…"), ready ? GOOD : INK_DIM));
            if (owner && !own.isOwner(member)) {
                ButtonComponent kick = button(Component.literal("✕"), b -> send(new ArenaLobbyTargetPayload(pos, "kick", member)));
                kick.sizing(Sizing.fixed(18), Sizing.fixed(16));
                row.child(kick);
            }
            contentArea.child(row);
        }

        contentArea.child(spacer(6));
        FlowLayout actions = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        actions.gap(6);
        actions.child(button(Component.translatable("gui.arenas_ld.arena.ready"), b -> send(new ArenaLobbyActionPayload(pos, "ready"))));
        actions.child(button(Component.translatable("gui.arenas_ld.arena.leave"), b -> send(new ArenaLobbyActionPayload(pos, "leave"))));
        if (owner) {
            actions.child(button(Component.translatable("gui.arenas_ld.arena.start"), b -> send(new ArenaLobbyActionPayload(pos, "start"))));
        }
        contentArea.child(actions);

        if (owner) {
            FlowLayout ownerRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
            ownerRow.gap(6);
            LobbyVisibility next = switch (own.visibility()) {
                case PUBLIC -> LobbyVisibility.FRIENDS;
                case FRIENDS -> LobbyVisibility.PRIVATE;
                case PRIVATE -> LobbyVisibility.PUBLIC;
            };
            ownerRow.child(button(Component.translatable("gui.arenas_ld.arena.visibility", own.visibility().name()),
                b -> send(new ArenaSetVisibilityPayload(pos, next.name()))));
            boolean hc = own.hardcoreEnabled();
            ownerRow.child(button(Component.translatable("gui.arenas_ld.arena.hardcore", hc ? "ON" : "OFF"),
                b -> send(new ArenaSetHardcorePayload(pos, !hc))));
            contentArea.child(ownerRow);

            // Join requests to my lobby.
            boolean firstReq = true;
            for (PendingJoinRequest req : c.getPendingJoinRequests()) {
                if (!req.lobbyId().equals(own.lobbyId())) continue;
                if (firstReq) { contentArea.child(spacer(4)); contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.join_requests"))); firstReq = false; }
                contentArea.child(requestRow(req));
            }

            // Invite online players not already in a lobby.
            contentArea.child(spacer(4));
            contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.arena.invite_players")));
            if (minecraft != null && minecraft.getConnection() != null) {
                for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                    UUID id = info.getProfile().getId();
                    if (id.equals(me) || own.isMember(id)) continue;
                    FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
                    row.verticalAlignment(VerticalAlignment.CENTER);
                    row.gap(6);
                    row.child(text(Component.literal(info.getProfile().getName()), INK));
                    row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
                    row.child(button(Component.translatable("gui.arenas_ld.arena.invite"),
                        b -> send(new ArenaLobbyTargetPayload(pos, "invite", id))));
                    contentArea.child(row);
                }
            }
        }
    }

    private FlowLayout lobbyRow(ArenaControllerBlockEntity c, Lobby lobby, UUID me) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        row.surface(Surface.flat(ROW_BG));
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.child(text(Component.literal(lobby.ownerName() + "  (" + lobby.members().size() + "/" + c.getMaxPartySize() + ")"), INK));
        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        if (lobby.visibility() == LobbyVisibility.PUBLIC) {
            row.child(button(Component.translatable("gui.arenas_ld.arena.join"),
                b -> send(new ArenaLobbyTargetPayload(pos, "join", lobby.lobbyId()))));
        } else {
            row.child(button(Component.translatable("gui.arenas_ld.arena.request"),
                b -> send(new ArenaLobbyTargetPayload(pos, "request", lobby.lobbyId()))));
        }
        return row;
    }

    private FlowLayout inviteRow(PendingInvite inv) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.surface(Surface.flat(ROW_BG));
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.child(text(Component.literal(inv.ownerName()), INK));
        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        row.child(button(Component.translatable("gui.arenas_ld.arena.accept"),
            b -> send(new ArenaLobbyTargetPayload(pos, "accept_invite", inv.lobbyId()))));
        row.child(button(Component.translatable("gui.arenas_ld.arena.decline"),
            b -> send(new ArenaLobbyTargetPayload(pos, "decline_invite", inv.lobbyId()))));
        return row;
    }

    private FlowLayout requestRow(PendingJoinRequest req) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        row.surface(Surface.flat(ROW_BG));
        row.padding(Insets.of(2, 2, 6, 6));
        row.gap(6);
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.child(text(Component.literal(req.requesterName()), INK));
        row.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        row.child(button(Component.translatable("gui.arenas_ld.arena.accept"),
            b -> send(new ArenaLobbyTargetPayload(pos, "accept_request", req.requesterUuid()))));
        row.child(button(Component.translatable("gui.arenas_ld.arena.decline"),
            b -> send(new ArenaLobbyTargetPayload(pos, "decline_request", req.requesterUuid()))));
        return row;
    }

    private FlowLayout header() {
        FlowLayout h = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(40));
        h.surface(Surface.flat(PANEL_2));
        h.padding(Insets.of(8, 8, 10, 10));
        h.gap(10);
        h.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(18), Sizing.fixed(18));
        mark.surface(Surface.flat(ACCENT).and(Surface.outline(ACCENT_DARK)));
        h.child(mark);
        h.child(text(Component.translatable("container.arenas_ld.arena_controller"), INK));
        h.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));
        ButtonComponent close = button(Component.literal("×"), b -> onClose());
        close.sizing(Sizing.fixed(22), Sizing.fixed(18));
        h.child(close);
        return h;
    }

    private void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    private FlowLayout sectionHeader(Component title) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.child(text(title, INK_DIM));
        return row;
    }

    private FlowLayout spacer(int px) {
        FlowLayout s = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        s.surface(Surface.BLANK);
        return s;
    }

    private LabelComponent text(Component c, int color) {
        LabelComponent l = Components.label(c);
        l.color(Color.ofArgb(color));
        return l;
    }

    private ButtonComponent button(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent b = Components.button(text, action);
        b.sizing(Sizing.content(), Sizing.fixed(18));
        b.renderer((ctx, rendered, delta) -> {
            int fill = rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK;
            ctx.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            ctx.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), HAIRLINE);
        });
        return b;
    }
}
