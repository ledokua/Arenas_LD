package net.ledok.arenas_ld.screen.lobby;

import net.ledok.arenas_ld.api.ArenasApi;
import net.ledok.arenas_ld.packet.InviteCandidatesPayload;
import net.ledok.arenas_ld.packet.RequestInviteCandidatesPayload;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.arenas_ld.screen.RunTheme;
import net.ledok.arenas_ld.screen.run.RunWidgets;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.Modal;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The lobby's "+ Invite a player" picker (4.40.3, LeDok: like the Eden hub's Friends tab): a search field and the
 * online players with their heads, friends first, then party and guild, then everyone else, each with an Invite
 * button, or what stops the invite (in the party, invited, busy, doesn't accept invites). The list comes from the
 * server ({@link InviteCandidatesPayload}); the busy states are the lobby snapshot's.
 */
public final class InvitePicker {
    private static final float WIDTH = 320;
    private static final float LIST_H = 230;
    private static final float ROW_H = 26;

    private static @Nullable InvitePicker open;

    private final LobbyHost host;
    private final RunTheme.Palette p;
    private final Flex list = Flex.column().gap(2);
    private final Set<UUID> sent = new HashSet<>();
    private @Nullable List<InviteCandidatesPayload.Entry> entries;
    private String query = "";
    private @Nullable Modal modal;

    private InvitePicker(LobbyHost host, RunTheme.Palette p) {
        this.host = host;
        this.p = p;
    }

    /** Opens the picker over {@code canvas} and asks the server for the list. */
    public static void show(VectorCanvas canvas, LobbyHost host) {
        if (open != null && open.modal != null) {
            open.modal.close();
        }
        InvitePicker picker = new InvitePicker(host, RunTheme.current().palette());
        open = picker;
        picker.modal = Modal.open(canvas, picker.panel());
        picker.modal.closeOnClickOutside(true);
        picker.modal.onClose(() -> {
            if (open == picker) {
                open = null;
            }
        });
        ArenasNetwork.sendToServer(new RequestInviteCandidatesPayload(host.controllerPos()));
    }

    /** The server's list: fills the open picker for that controller. */
    public static void onPayload(InviteCandidatesPayload payload) {
        InvitePicker picker = open;
        if (picker == null || !picker.host.controllerPos().equals(payload.controller())) {
            return;
        }
        picker.entries = payload.entries();
        picker.fill();
    }

    private Flex panel() {
        var look = RunTheme.current();
        Flex panel = Flex.column().gap(4).padding(Insets.of(10));
        panel.sizing(Sizing.fixed(WIDTH), Sizing.content());
        panel.background(look.panel());

        Flex head = Flex.row().gap(6).alignItems(Align.CENTER);
        head.sizing(Sizing.fill(), Sizing.content());
        head.item(RunWidgets.grow(tr("title"), p.ink()));
        Button close = new Button(18, 16, Component.literal("✕"), () -> {
            if (modal != null) {
                modal.close();
            }
        });
        close.style(RunWidgets.flatStyle(p));
        head.item(close);
        panel.item(head);

        TextField search = new TextField(WIDTH - 20, query, text -> {
            query = text;
            fill();
        });
        search.sizing(Sizing.fill(), Sizing.fixed(16));
        search.hint(tr("search").getString());
        panel.item(search);

        list.sizing(Sizing.fill(), Sizing.content());
        ScrollPanel scroll = new ScrollPanel(WIDTH - 20, LIST_H, list);
        scroll.sizing(Sizing.fill(), Sizing.fixed(LIST_H));
        panel.item(scroll);
        fill();
        return panel;
    }

    private void fill() {
        list.clear();
        if (entries == null) {
            list.item(RunWidgets.text(tr("loading"), p.inkDim()));
            return;
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        if (needle.startsWith("@")) {
            needle = needle.substring(1);
        }
        List<Row> rows = new ArrayList<>();
        for (InviteCandidatesPayload.Entry entry : entries) {
            if (needle.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(needle)) {
                rows.add(new Row(entry, state(entry)));
            }
        }
        if (rows.isEmpty()) {
            list.item(RunWidgets.text(tr(entries.isEmpty() ? "nobody" : "no_matches"), p.inkDim()));
            return;
        }
        rows.sort(Comparator.comparingInt((Row r) -> group(r.entry()))
            .thenComparing(r -> r.state() != State.OK)
            .thenComparing(r -> r.entry().name().toLowerCase(Locale.ROOT)));
        int group = -1;
        int i = 0;
        for (Row row : rows) {
            int g = group(row.entry());
            if (g != group) {
                group = g;
                long count = rows.stream().filter(r -> group(r.entry()) == g).count();
                list.item(RunWidgets.section(p, tr("group." + g), Component.literal("· " + count), null));
            }
            list.item(row(row, i++ % 2 == 1));
        }
    }

    /** 0 friends, 1 party and guild, 2 everyone else. */
    private static int group(InviteCandidatesPayload.Entry entry) {
        ArenasApi.PlayerRelation[] all = ArenasApi.PlayerRelation.values();
        ArenasApi.PlayerRelation relation = all[Math.floorMod(entry.relation(), all.length)];
        return switch (relation) {
            case FRIEND -> 0;
            case PARTY, GUILD -> 1;
            case NONE -> 2;
        };
    }

    private enum State { OK, MEMBER, INVITED, BLOCKED, IN_LOBBY, IN_RUN, BUSY }

    private record Row(InviteCandidatesPayload.Entry entry, State state) {
    }

    private State state(InviteCandidatesPayload.Entry entry) {
        if (entry.member() || host.ownLobby().map(l -> l.members().contains(entry.uuid())).orElse(false)) {
            return State.MEMBER;
        }
        if (entry.invited() || sent.contains(entry.uuid())) {
            return State.INVITED;
        }
        if (entry.blocked().isPresent()) {
            return State.BLOCKED;
        }
        return switch (host.inviteStatus(entry.uuid())) {
            case ONLINE -> State.OK;
            case IN_LOBBY -> State.IN_LOBBY;
            case IN_RUN -> State.IN_RUN;
            case BUSY -> State.BUSY;
        };
    }

    private Flex row(Row row, boolean alt) {
        InviteCandidatesPayload.Entry entry = row.entry();
        boolean ok = row.state() == State.OK;
        Flex card = Flex.row().gap(5).padding(Insets.of(0, 5, 0, 5)).alignItems(Align.CENTER);
        card.sizing(Sizing.fill(), Sizing.fixed(ROW_H));
        card.backgroundFill(alt ? p.rowAlt() : p.row(), (p.hairline() & 0x00FFFFFF) | 0x66000000, 1);
        var head = RunWidgets.head(entry.uuid(), 16);
        if (!ok) {
            head.opacity(0.55f);
        }
        card.item(head);

        Flex text = Flex.column().gap(1);
        text.sizing(Sizing.expand(), Sizing.content());
        text.item(RunWidgets.grow(Component.literal(entry.name()), ok ? p.ink() : p.inkMid()));
        Component relation = tr("relation." + ArenasApi.PlayerRelation.values()[
            Math.floorMod(entry.relation(), ArenasApi.PlayerRelation.values().length)].name().toLowerCase(Locale.ROOT));
        Component second = ok ? relation : relation.copy().append(" · ").append(tr("state." + row.state().name().toLowerCase(Locale.ROOT)));
        int secondColor = switch (row.state()) {
            case OK -> p.good();
            case MEMBER, INVITED -> p.accent();
            case BLOCKED -> p.danger();
            default -> p.warn();
        };
        Label line = RunWidgets.grow(second, secondColor);
        entry.blocked().ifPresent(line::tooltip);
        text.item(line);
        card.item(text);

        Button invite = RunWidgets.button(tr(row.state() == State.INVITED ? "invited" : "invite"), 70, () -> {
            sent.add(entry.uuid());
            host.invite(entry.uuid());
            fill();
            // The server answers in order: the fresh list already has the invite.
            ArenasNetwork.sendToServer(new RequestInviteCandidatesPayload(host.controllerPos()));
        });
        if (ok) {
            invite.style(RunWidgets.primaryStyle(p));
        }
        invite.enabled(ok);
        card.item(invite);
        return card;
    }

    private static Component tr(String key) {
        return Component.translatable("gui.arenas_ld.invite_picker." + key);
    }

    /** Closes a picker left open (the lobby went away, a disconnect). */
    public static void close() {
        if (open != null && open.modal != null) {
            open.modal.close();
        }
        open = null;
    }
}
