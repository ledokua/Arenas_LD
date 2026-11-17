package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.packet.RentalLobbyPayload;
import net.ledok.arenas_ld.packet.RentalPreviewPayload;
import net.ledok.arenas_ld.packet.RequestRentalPreviewPayload;
import net.ledok.arenas_ld.packet.SetRentalChoicePayload;
import net.ledok.arenas_ld.platform.ArenasNetwork;
import net.ledok.vectorlib.client.canvas.ItemStackNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Modal;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.canvas.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static net.ledok.arenas_ld.screen.ArenasParchment.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasParchment.DANGER;
import static net.ledok.arenas_ld.screen.ArenasParchment.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasParchment.INK_MID;
import static net.ledok.arenas_ld.screen.ArenasParchment.ROW_BG;

/**
 * The dungeon and raid lobby screens' "Rental build": a per-player run modifier (LeDok) under the lobby's run
 * modifiers. Each member switches it only for themselves: switching it on opens a preview of every rental kit at their
 * level (every preset in the highest level folder at or below theirs; items with tooltips), and picking a preset
 * there confirms it. Everyone's pick shows on their member row.
 * Fed by {@link RentalLobbyPayload} (once a second when it changes); the lobby screens rebuild when {@link #version}
 * moves. Empty when the server offers no rental here.
 */
public final class RentalPick {
    /** A lobby screen the preview can open on (as a modal: a new screen would close the lobby's menu). */
    public interface Host {
        VectorCanvas rentalCanvas();
    }

    private static final float PREVIEW_W = 440;
    private static final int SLOTS_PER_ROW = 12;

    private static RentalLobbyPayload state = RentalLobbyPayload.NONE;
    private static int version;

    private RentalPick() {
    }

    /** The controller whose "rental kits only" preview opened by itself (once per lobby visit); null for none. */
    private static @Nullable BlockPos autoOpened;

    public static void onPayload(RentalLobbyPayload payload) {
        state = payload;
        version++;
        if (!payload.active() || !payload.required() || !payload.controller().equals(autoOpened)) {
            autoOpened = null;
        }
    }

    public static void clear() {
        onPayload(RentalLobbyPayload.NONE);
    }

    /** Moves whenever a new state arrives; lobby screens compare it in their tick. */
    public static int version() {
        return version;
    }

    /** A member's rental class (by name) in the viewer's lobby at {@code controller}, or null. */
    public static @Nullable String pickOf(BlockPos controller, String memberName) {
        RentalLobbyPayload s = state;
        return s.active() && s.controller().equals(controller) ? s.picks().get(memberName) : null;
    }

    /** The modifier row ("Rental build — just for you"), or an empty column; old parchment look. */
    public static Flex build(BlockPos controller) {
        return build(controller, net.ledok.arenas_ld.screen.RunTheme.PARCHMENT_PALETTE);
    }

    /**
     * The rental row in the lobby's theme (UI rework): the title with Change and On/Off on one line, then the hint, the
     * reward cut and the picked build on lines of their own. Each is one line that ends in "…" with the full text in the
     * tooltip, so nothing runs out of its column.
     */
    public static Flex build(BlockPos controller, net.ledok.arenas_ld.screen.RunTheme.Palette p) {
        Flex col = Flex.column().gap(3);
        col.sizing(Sizing.fill(), Sizing.content());
        RentalLobbyPayload s = state;
        if (!s.active() || !s.controller().equals(controller) || s.presets().isEmpty()) {
            return col;
        }
        boolean on = !s.own().isEmpty();
        if (s.required()) {
            return required(col, controller, p, s, on);
        }
        Flex box = Flex.column().gap(2).padding(Insets.of(5, 8, 6, 8));
        box.sizing(Sizing.fill(), Sizing.content());
        box.backgroundFill(on ? p.rowAlt() : p.row(), on ? p.accent() : p.hairline(), 1);

        Flex head = Flex.row().gap(6).alignItems(Align.CENTER);
        head.sizing(Sizing.fill(), Sizing.content());
        head.item(net.ledok.arenas_ld.screen.run.RunWidgets.text(Component.translatable("gui.arenas_ld.rental.title"),
            on ? p.ink() : p.inkMid()));
        head.spacer();
        if (on) {
            head.item(net.ledok.arenas_ld.screen.run.RunWidgets.button(Component.translatable("gui.arenas_ld.rental.change"),
                () -> ArenasNetwork.sendToServer(new RequestRentalPreviewPayload(controller))));
        }
        head.item(net.ledok.arenas_ld.screen.run.RunWidgets.button(Component.translatable(on
                ? "gui.arenas_ld.dungeon_controller.ui.hardcore.on" : "gui.arenas_ld.dungeon_controller.ui.hardcore.off"),
            () -> ArenasNetwork.sendToServer(on
                ? new SetRentalChoicePayload(controller, "")
                : new RequestRentalPreviewPayload(controller))));
        box.item(head);

        box.item(line(Component.translatable("gui.arenas_ld.rental.hint"), p.inkMid()));
        box.item(line(Component.translatable("gui.arenas_ld.rental.rewards"), p.danger()));
        if (on) {
            box.item(line(Component.translatable("gui.arenas_ld.rental.yours", s.own(), s.ownLevel()), p.accent()));
        }
        col.item(box);
        col.item(net.ledok.arenas_ld.screen.run.RunWidgets.spacer(4));
        return col;
    }

    /**
     * "Rental kits only" (the controller requires it): no On/Off, a Choose/Change button, and a red line until the
     * viewer picked. The preview opens by itself the first time the lobby screen shows this without a pick.
     */
    private static Flex required(Flex col, BlockPos controller, net.ledok.arenas_ld.screen.RunTheme.Palette p,
                                 RentalLobbyPayload s, boolean on) {
        if (!on && !controller.equals(autoOpened)) {
            autoOpened = controller;
            ArenasNetwork.sendToServer(new RequestRentalPreviewPayload(controller));
        }
        Flex box = Flex.column().gap(2).padding(Insets.of(5, 8, 6, 8));
        box.sizing(Sizing.fill(), Sizing.content());
        box.backgroundFill(p.rowAlt(), on ? p.accent() : p.danger(), 1);

        Flex head = Flex.row().gap(6).alignItems(Align.CENTER);
        head.sizing(Sizing.fill(), Sizing.content());
        head.item(net.ledok.arenas_ld.screen.run.RunWidgets.text(Component.translatable("gui.arenas_ld.rental.required_title"), p.ink()));
        head.spacer();
        head.item(net.ledok.arenas_ld.screen.run.RunWidgets.button(Component.translatable(on
                ? "gui.arenas_ld.rental.change" : "gui.arenas_ld.rental.choose"),
            () -> ArenasNetwork.sendToServer(new RequestRentalPreviewPayload(controller))));
        box.item(head);

        box.item(line(Component.translatable("gui.arenas_ld.rental.required_hint"), p.inkMid()));
        box.item(line(Component.translatable("gui.arenas_ld.rental.required_rewards"), p.accent()));
        box.item(on
            ? line(Component.translatable("gui.arenas_ld.rental.yours", s.own(), s.ownLevel()), p.accent())
            : line(Component.translatable("gui.arenas_ld.rental.required_pick"), p.danger()));
        col.item(box);
        col.item(net.ledok.arenas_ld.screen.run.RunWidgets.spacer(4));
        return col;
    }

    /** The lobby header's lines for the controller's run settings: "Rental kits only", "Base loot rolls: 9". */
    public static java.util.List<Component> settingsMeta(net.ledok.arenas_ld.util.RunSettings settings) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        if (settings.rentalRequired()) {
            out.add(Component.translatable("gui.arenas_ld.rental.required_label"));
        }
        if (settings.hasOwnLootRolls()) {
            out.add(Component.translatable("gui.arenas_ld.lobby.base_loot_rolls",
                net.ledok.arenas_ld.rewards.RewardMath.number(settings.baseLootRolls())));
        }
        return out;
    }

    /** One full-width line, cut with "…" and the full text in its tooltip. */
    private static net.ledok.vectorlib.client.canvas.widget.Label line(Component text, int color) {
        var label = net.ledok.vectorlib.client.canvas.widget.Label.of(100, text).color(color).tooltipWhenCut(true);
        label.sizing(Sizing.fill(), Sizing.content());
        return label;
    }

    // ── the preview ─────────────────────────────────────────────────────────

    /** The server's kit list: shown as a modal on the open lobby screen. */
    public static void onPreview(RentalPreviewPayload payload) {
        Host host = Minecraft.getInstance().screen instanceof Host h ? h
            : net.ledok.arenas_ld.screen.lobby.LobbyScreen.current() instanceof Host hosted ? hosted : null;
        if (host == null) {
            return;
        }
        Modal[] modal = new Modal[1];
        Flex panel = Flex.column().gap(4).padding(Insets.of(12));
        panel.sizing(Sizing.fixed(PREVIEW_W), Sizing.content());
        var look = net.ledok.arenas_ld.screen.RunTheme.current();
        var p = look.palette();
        panel.background(look.panel());
        panel.item(net.ledok.arenas_ld.screen.run.RunWidgets.text(Component.translatable("gui.arenas_ld.rental.preview_title", payload.level()), p.ink()));
        var hint = net.ledok.vectorlib.client.canvas.widget.Label.of(PREVIEW_W - 24, Component.translatable("gui.arenas_ld.rental.preview_hint")).color(p.inkMid()).tooltipWhenCut(true);
        hint.sizing(Sizing.fill(), Sizing.content());
        panel.item(hint);

        Flex list = Flex.column().gap(6);
        list.sizing(Sizing.fill(), Sizing.content());
        String own = state.own();
        for (RentalPreviewPayload.Kit kit : payload.kits()) {
            list.item(kitBlock(p, payload.controller(), kit, kit.kitId().endsWith("/" + own) && !own.isEmpty(),
                () -> modal[0].close()));
        }
        panel.item(new ScrollPanel(PREVIEW_W - 24, 220, list));

        Flex footer = Flex.row().gap(6).alignItems(Align.CENTER);
        footer.sizing(Sizing.fill(), Sizing.content());
        footer.item(net.ledok.arenas_ld.screen.run.RunWidgets.button(Component.translatable("gui.arenas_ld.rental.close"), () -> modal[0].close()));
        panel.item(footer);

        modal[0] = Modal.open(host.rentalCanvas(), panel);
        modal[0].closeOnClickOutside(true);
    }

    /** One class: its name, a Pick button, and its items as slots (vanilla tooltips on hover). */
    private static Flex kitBlock(net.ledok.arenas_ld.screen.RunTheme.Palette p, BlockPos controller, RentalPreviewPayload.Kit kit, boolean current, Runnable close) {
        Flex block = Flex.column().gap(3).padding(Insets.of(5, 6, 5, 6));
        block.sizing(Sizing.fill(), Sizing.content());
        block.backgroundFill(current ? p.rowAlt() : p.row(), current ? p.accent() : p.hairline(), 1);

        Flex head = Flex.row().gap(8).alignItems(Align.CENTER);
        head.sizing(Sizing.fill(), Sizing.content());
        var title = Component.literal(kit.name())
            .append(Component.translatable("gui.arenas_ld.rental.kit_level", kit.level()));
        if (kit.recommended()) {
            title.append(Component.literal("  ")).append(Component.translatable("gui.arenas_ld.rental.recommended")
                .withStyle(s -> s.withColor(p.good() & 0xFFFFFF)));
        }
        var name = net.ledok.arenas_ld.screen.run.RunWidgets.text(title, current ? p.accent() : p.ink());
        head.item(name);
        Flex spacer = Flex.row();
        spacer.sizing(Sizing.expand(), Sizing.content());
        head.item(spacer);
        // The spell book's spells (rental round 2): toggles, up to the book's size, starting from the server's prefill.
        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>(kit.chosen());
        Button[] pick = new Button[1];
        Runnable refreshPick = () -> {
            boolean changed = !new java.util.ArrayList<>(selected).equals(kit.chosen());
            pick[0].label(Component.translatable(current && !changed ? "gui.arenas_ld.rental.picked"
                : current ? "gui.arenas_ld.rental.save_spells" : "gui.arenas_ld.rental.pick"));
            pick[0].enabled((!current || changed) && (kit.spells().isEmpty() || !selected.isEmpty()));
        };
        pick[0] = net.ledok.arenas_ld.screen.run.RunWidgets.button(Component.translatable("gui.arenas_ld.rental.pick"), 90, () -> {
            ArenasNetwork.sendToServer(new SetRentalChoicePayload(controller, kit.kitId(), new java.util.ArrayList<>(selected)));
            close.run();
        });
        refreshPick.run();
        head.item(pick[0]);
        block.item(head);

        if (!kit.spells().isEmpty()) {
            block.item(net.ledok.arenas_ld.screen.run.RunWidgets.text(Component.translatable("gui.arenas_ld.rental.spells",
                kit.maxSpells()), p.inkMid()));
            Flex row = null;
            float chipW = (PREVIEW_W - 24 - 12 - 8) / 3f;
            for (int i = 0; i < kit.spells().size(); i++) {
                if (i % 3 == 0) {
                    row = Flex.row().gap(4);
                    row.sizing(Sizing.fill(), Sizing.content());
                    block.item(row);
                }
                String spell = kit.spells().get(i);
                Button[] chip = new Button[1];
                chip[0] = net.ledok.arenas_ld.screen.run.RunWidgets.button(spellLabel(spell, selected.contains(spell)), chipW, () -> {
                    if (selected.contains(spell)) {
                        selected.remove(spell);
                    } else if (selected.size() < kit.maxSpells()) {
                        selected.add(spell);
                    } else {
                        return; // full: switch one off first
                    }
                    chip[0].label(spellLabel(spell, selected.contains(spell)));
                    refreshPick.run();
                });
                chip[0].tooltipWhenCut(true);
                row.item(chip[0]);
            }
        }

        Flex slots = null;
        for (int i = 0; i < kit.items().size(); i++) {
            if (i % SLOTS_PER_ROW == 0) {
                slots = Flex.row().gap(2);
                slots.sizing(Sizing.fill(), Sizing.content());
                block.item(slots);
            }
            slots.item(new Slot(kit.items().get(i)));
        }
        if (kit.items().isEmpty()) {
            block.item(net.ledok.arenas_ld.screen.run.RunWidgets.text(Component.translatable("gui.arenas_ld.rental.no_preview"), p.inkDim()));
        }
        return block;
    }

    /** "● Earthquake" (in the book) / "○ Earthquake": Spell Engine's name key, the id's path when it has none. */
    private static Component spellLabel(String spellId, boolean on) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(spellId);
        String key = id == null ? spellId : "spell." + id.getNamespace() + "." + id.getPath() + ".name";
        String fallback = id == null ? spellId : id.getPath().replace('_', ' ');
        return Component.literal(on ? "● " : "○ ").append(Component.translatableWithFallback(key, fallback));
    }

    /** An item in a slot: count and durability, the vanilla tooltip on hover. */
    private static final class Slot extends Widget {
        private final ItemStack stack;

        Slot(ItemStack stack) {
            super(20, 20);
            this.stack = stack;
        }

        @Override
        protected void rebuild() {
            add(net.ledok.vectorlib.client.canvas.Shapes.rect(0, 0, 20, 20).fill((net.ledok.arenas_ld.screen.RunTheme.current().palette().hairline() & 0x00FFFFFF) | 0x55000000));
            add(ItemStackNode.of(stack, 2, 2, 16));
        }
    }
}
