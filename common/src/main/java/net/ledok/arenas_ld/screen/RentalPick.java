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

import java.util.List;
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
    /** The preview modal on screen, if any. */
    private static @Nullable Modal openPreview;

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
     * The rental row in the lobby's theme: the title and available actions on one line, then the hint, the
     * reward cut and the picked build on lines of their own. Each is one line that ends in "…" with the full text in the
     * tooltip, so nothing runs out of its column.
     */
    public static Flex build(BlockPos controller, net.ledok.arenas_ld.screen.RunTheme.Palette p) {
        Flex col = Flex.column().gap(3);
        col.sizing(Sizing.fill(), Sizing.content());
        RentalLobbyPayload s = state;
        if (!s.active() || !s.controller().equals(controller)) {
            return col;
        }
        if (s.presets().isEmpty()) {
            if (s.required()) col.item(line(Component.translatable("gui.arenas_ld.rental.empty"), p.danger()));
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
        head.item(net.ledok.arenas_ld.screen.run.RunWidgets.grow(Component.translatable("gui.arenas_ld.rental.title"),
            on ? p.ink() : p.inkMid()));
        if (on) {
            head.item(net.ledok.arenas_ld.screen.run.RunWidgets.button(Component.translatable("gui.arenas_ld.rental.change"),
                () -> ArenasNetwork.sendToServer(new RequestRentalPreviewPayload(controller))));
        }
        head.item(net.ledok.arenas_ld.screen.run.RunWidgets.button(Component.translatable(on
                ? "gui.arenas_ld.rental.use_own_gear" : "gui.arenas_ld.rental.choose"),
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
        head.item(net.ledok.arenas_ld.screen.run.RunWidgets.grow(Component.translatable("gui.arenas_ld.rental.required_title"), p.ink()));
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
        VectorCanvas canvas = host.rentalCanvas();
        float previewWidth = Math.max(1, Math.min(PREVIEW_W, canvas.width() - 16));
        float listHeight = Math.max(24, Math.min(220, canvas.height() - 88));
        Modal[] modal = new Modal[1];
        Flex panel = Flex.column().gap(4).padding(Insets.of(12));
        panel.sizing(Sizing.fixed(previewWidth), Sizing.content());
        var look = net.ledok.arenas_ld.screen.RunTheme.current();
        var p = look.palette();
        panel.background(look.panel());
        panel.item(line(Component.translatable("gui.arenas_ld.rental.preview_title", payload.level()), p.ink()));
        var hint = net.ledok.vectorlib.client.canvas.widget.Label.of(previewWidth - 24, Component.translatable("gui.arenas_ld.rental.preview_hint")).color(p.inkMid()).tooltipWhenCut(true);
        hint.sizing(Sizing.fill(), Sizing.content());
        panel.item(hint);

        Flex list = Flex.column().gap(6);
        list.sizing(Sizing.fill(), Sizing.content());
        if (payload.kits().isEmpty()) {
            list.item(line(Component.translatable("gui.arenas_ld.rental.empty"), p.inkMid()));
        }
        String own = state.own();
        for (RentalPreviewPayload.Kit kit : payload.kits()) {
            list.item(kitBlock(p, payload.controller(), kit, kit.kitId().endsWith("/" + own) && !own.isEmpty(), previewWidth - 32,
                () -> modal[0].close()));
        }
        ScrollPanel kits = new ScrollPanel(previewWidth - 24, listHeight, list);
        kits.sizing(Sizing.fill(), Sizing.fixed(listHeight));
        panel.item(kits);

        Flex footer = Flex.row().gap(6).alignItems(Align.CENTER);
        footer.sizing(Sizing.fill(), Sizing.content());
        Button close = net.ledok.arenas_ld.screen.run.RunWidgets.button(
            Component.translatable("gui.arenas_ld.rental.close"), () -> modal[0].close());
        footer.item(close);
        panel.item(footer);

        if (openPreview != null) {
            openPreview.close();
        }
        var input = canvas.input();
        var opener = input == null ? null : input.focusedNode();
        Modal opened = Modal.open(canvas, ArenasUi.fitDialog(panel));
        modal[0] = opened;
        openPreview = opened;
        opened.closeOnClickOutside(true);
        opened.onClose(() -> {
            if (openPreview == opened) {
                openPreview = null;
                canvas.focus(opener != null && opener.canvas() == canvas ? opener : null);
            }
        });
        canvas.focus(close);
    }

    /** One class: its name, a Pick button, and its items as slots (vanilla tooltips on hover). */
    private static Flex kitBlock(net.ledok.arenas_ld.screen.RunTheme.Palette p, BlockPos controller, RentalPreviewPayload.Kit kit, boolean current, float width, Runnable close) {
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
        var name = net.ledok.arenas_ld.screen.run.RunWidgets.grow(title, current ? p.accent() : p.ink());
        head.item(name);
        // The spell book's spells (rental round 2): toggles, up to the book's size, starting from the server's prefill.
        java.util.LinkedHashSet<String> selected = new java.util.LinkedHashSet<>(kit.chosen());
        Button[] pick = new Button[1];
        Runnable refreshPick = () -> {
            boolean changed = !new java.util.ArrayList<>(selected).equals(kit.chosen());
            pick[0].label(Component.translatable(current && !changed ? "gui.arenas_ld.rental.picked"
                : current ? "gui.arenas_ld.rental.save_spells" : "gui.arenas_ld.rental.pick"));
            pick[0].enabled((!current || changed) && (kit.spells().isEmpty() || !selected.isEmpty()));
        };
        float pickWidth = net.ledok.arenas_ld.screen.run.RunWidgets.width(
            Component.translatable("gui.arenas_ld.rental.save_spells")) + 12;
        pick[0] = net.ledok.arenas_ld.screen.run.RunWidgets.button(Component.translatable("gui.arenas_ld.rental.pick"), pickWidth, () -> {
            ArenasNetwork.sendToServer(new SetRentalChoicePayload(controller, kit.kitId(), new java.util.ArrayList<>(selected)));
            close.run();
        });
        refreshPick.run();
        head.item(pick[0]);
        block.item(head);

        int slotsPerRow = Math.max(1, Math.min(SLOTS_PER_ROW, (int) (width - 10) / 22));
        // The kit's book: its spell power goes into the spell tooltips' values, and its item slot shows the pick.
        ItemStack book = kit.spells().isEmpty() ? ItemStack.EMPTY : kit.items().stream()
            .filter(net.ledok.arenas_ld.util.RentalSpells::hasContainer).findFirst().orElse(ItemStack.EMPTY);
        // 4.40.6 (LeDok): the book in the item row holds the current pick, so its tooltip lists the picked spells.
        java.util.function.Supplier<ItemStack> shownBook = () -> net.ledok.arenas_ld.util.RentalSpells.withSpells(book,
            kit.spells().stream().filter(selected::contains).toList());
        Slot[] bookSlot = new Slot[1];
        if (!kit.spells().isEmpty()) {
            // 4.40.2 (LeDok): icon slots like the spell binding table, Spell Engine's tooltip on hover.
            // 4.40.4: one spell per tier ("3 pairs"), a row per tier, picked like radio buttons.
            List<SpellSlot> all = new java.util.ArrayList<>();
            Runnable changed = () -> {
                for (SpellSlot slot : all) {
                    slot.refresh(); // only on a click: nothing is polled per frame
                }
                if (bookSlot[0] != null) {
                    bookSlot[0].stack(shownBook.get());
                }
                refreshPick.run();
            };
            if (kit.perTier()) {
                block.item(net.ledok.arenas_ld.screen.run.RunWidgets.text(Component.translatable("gui.arenas_ld.rental.spells_per_tier"), p.inkMid()));
                List<Integer> tiers = kit.spellTiers().stream().distinct().toList();
                for (int r = 0; r < tiers.size(); r++) {
                    int tier = tiers.get(r);
                    List<String> mates = new java.util.ArrayList<>();
                    for (int i = 0; i < kit.spells().size(); i++) {
                        if (kit.spellTiers().get(i) == tier) {
                            mates.add(kit.spells().get(i));
                        }
                    }
                    Flex row = Flex.row().gap(2).alignItems(Align.CENTER);
                    row.sizing(Sizing.fill(), Sizing.content());
                    var label = net.ledok.vectorlib.client.canvas.widget.Label.of(56,
                        Component.translatable("gui.arenas_ld.rental.spell_tier", r + 1)).color(p.inkDim());
                    label.sizing(Sizing.fixed(56), Sizing.content());
                    row.item(label);
                    block.item(row);
                    int spellsPerRow = Math.max(1, Math.min(SLOTS_PER_ROW, (int) (width - 68) / 22));
                    for (int i = 0; i < mates.size(); i++) {
                        if (i > 0 && i % spellsPerRow == 0) {
                            row = block.item(Flex.row().gap(2).padding(Insets.of(0, 0, 0, 58)));
                            row.sizing(Sizing.fill(), Sizing.content());
                        }
                        String spell = mates.get(i);
                        SpellSlot slot = new SpellSlot(p, spell, book, selected, kit.maxSpells(), mates, changed);
                        all.add(slot);
                        row.item(slot);
                    }
                }
            } else {
                var counter = net.ledok.vectorlib.client.canvas.widget.Label.of(width - 12,
                    spellCounter(selected.size(), kit.maxSpells())).color(p.inkMid());
                counter.sizing(Sizing.fill(), Sizing.content());
                block.item(counter);
                Runnable inner = changed;
                changed = () -> {
                    counter.text(spellCounter(selected.size(), kit.maxSpells()));
                    inner.run();
                };
                Flex row = null;
                for (int i = 0; i < kit.spells().size(); i++) {
                    if (i % slotsPerRow == 0) {
                        row = Flex.row().gap(2);
                        row.sizing(Sizing.fill(), Sizing.content());
                        block.item(row);
                    }
                    SpellSlot slot = new SpellSlot(p, kit.spells().get(i), book, selected, kit.maxSpells(), null, changed);
                    all.add(slot);
                    row.item(slot);
                }
            }
        }

        Flex slots = null;
        for (int i = 0; i < kit.items().size(); i++) {
            if (i % slotsPerRow == 0) {
                slots = Flex.row().gap(2);
                slots.sizing(Sizing.fill(), Sizing.content());
                block.item(slots);
            }
            ItemStack item = kit.items().get(i);
            Slot slot = new Slot(item == book && !book.isEmpty() ? shownBook.get() : item);
            if (item == book && !book.isEmpty()) {
                bookSlot[0] = slot;
            }
            slots.item(slot);
        }
        if (kit.items().isEmpty()) {
            block.item(net.ledok.arenas_ld.screen.run.RunWidgets.text(Component.translatable("gui.arenas_ld.rental.no_preview"), p.inkDim()));
        }
        return block;
    }

    /** "Spells in the book: 2 / 3". */
    private static Component spellCounter(int picked, int max) {
        return Component.translatable("gui.arenas_ld.rental.spells", picked, max);
    }

    /** Spell Engine's name key, the id's path when it has none. */
    private static Component spellName(String spellId) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(spellId);
        String key = id == null ? spellId : "spell." + id.getNamespace() + "." + id.getPath() + ".name";
        String fallback = id == null ? spellId : id.getPath().replace('_', ' ');
        return Component.translatableWithFallback(key, fallback);
    }

    private static @Nullable java.lang.reflect.Method spellEntry;
    private static boolean spellEntryLooked;

    /**
     * Spell Engine's own tooltip of a spell, as on the spell binding table (name, description with values, cast,
     * range, cooldown). Read by reflection (Arenas runs without Spell Engine); name + description when it can't.
     */
    @SuppressWarnings("unchecked")
    static List<Component> spellTooltip(String spellId, ItemStack book) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(spellId);
        var player = Minecraft.getInstance().player;
        if (id != null && player != null) {
            if (!spellEntryLooked) {
                spellEntryLooked = true;
                try {
                    spellEntry = Class.forName("net.spell_engine.client.gui.SpellTooltip").getMethod("spellEntry",
                        net.minecraft.resources.ResourceLocation.class, net.minecraft.world.entity.player.Player.class,
                        ItemStack.class, boolean.class, int.class);
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    spellEntry = null;
                }
            }
            if (spellEntry != null) {
                try {
                    List<Component> lines = (List<Component>) spellEntry.invoke(null, id, player, book, true, 0);
                    if (lines != null && !lines.isEmpty()) {
                        return new java.util.ArrayList<>(lines);
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                }
            }
        }
        List<Component> out = new java.util.ArrayList<>();
        out.add(spellName(spellId).copy().withStyle(net.minecraft.ChatFormatting.BOLD));
        if (id != null) {
            String key = "spell." + id.getNamespace() + "." + id.getPath() + ".description";
            if (net.minecraft.client.resources.language.I18n.exists(key)) {
                out.add(Component.translatable(key).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        return out;
    }

    /** Whether a spell icon texture exists, checked once per texture (a missing one draws no icon). */
    private static final Map<net.minecraft.resources.ResourceLocation, Boolean> ICONS = new java.util.HashMap<>();

    private static @Nullable net.minecraft.resources.ResourceLocation icon(String spell) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(spell);
        if (id == null) {
            return null;
        }
        var texture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(id.getNamespace(),
            "textures/spell/" + id.getPath() + ".png");
        boolean exists = ICONS.computeIfAbsent(texture,
            t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
        return exists ? texture : null;
    }

    /**
     * One spell of the book's pool: its Spell Engine icon in a slot. A spell in the book is framed in the accent color,
     * the others are dimmed. One per tier ({@code rowMates}): a click picks it instead of its row mate. Free choice
     * (no row mates): a click puts it in or takes it out, ignored while the book is full. Built once; it changes only
     * on a click ({@link #refresh} from the kit block), and its tooltip lines are built on the first hover.
     */
    private static final class SpellSlot extends Widget {
        private final net.ledok.arenas_ld.screen.RunTheme.Palette p;
        private final String spell;
        private final java.util.Set<String> selected;
        private final int max;
        private final @Nullable List<String> rowMates;
        private final Runnable changed;
        private final @Nullable net.minecraft.resources.ResourceLocation icon;
        private @Nullable List<Component> spellLines;

        SpellSlot(net.ledok.arenas_ld.screen.RunTheme.Palette p, String spell, ItemStack book, java.util.Set<String> selected,
                  int max, @Nullable List<String> rowMates, Runnable changed) {
            super(20, 20);
            this.p = p;
            this.spell = spell;
            this.selected = selected;
            this.max = max;
            this.rowMates = rowMates;
            this.changed = changed;
            this.icon = icon(spell);
            tooltip(() -> {
                if (spellLines == null) {
                    spellLines = spellTooltip(spell, book); // once, on the first hover
                }
                boolean on = selected.contains(spell);
                String hint = rowMates != null ? (on ? "gui.arenas_ld.rental.spell_in_book" : "gui.arenas_ld.rental.spell_swap")
                    : on ? "gui.arenas_ld.rental.spell_click_off"
                    : selected.size() >= max ? "gui.arenas_ld.rental.spell_full" : "gui.arenas_ld.rental.spell_click_on";
                List<Component> lines = new java.util.ArrayList<>(spellLines.size() + 1);
                lines.addAll(spellLines);
                lines.add(Component.translatable(hint).withStyle(rowMates == null && !on && selected.size() >= max
                    ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.DARK_GRAY));
                return lines;
            });
        }

        @Override
        protected void rebuild() {
            boolean on = selected.contains(spell);
            boolean hover = hovered || focused;
            int bg = on ? p.rowAlt() : hover ? p.row() : (p.hairline() & 0x00FFFFFF) | 0x55000000;
            int frame = focused ? p.ink() : on ? p.accent() : hover ? p.inkMid() : (p.hairline() & 0x00FFFFFF) | 0x88000000;
            add(net.ledok.vectorlib.client.canvas.Shapes.rect(0, 0, 20, 20).fill(bg).stroke(frame, on ? 2 : 1));
            if (icon != null) {
                add(net.ledok.vectorlib.client.canvas.ImageNode.of(icon, 2, 2, 16, 16, 16, 16)
                    .tint(on ? 0xFFFFFFFF : hover ? 0xFFB0B0B0 : 0xFF6E6E6E));
            }
        }

        @Override
        public boolean focusable() {
            return true;
        }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            return switch (key) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER,
                     org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE -> onMouseDown(0, 0, 0);
                default -> false;
            };
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (button != 0) {
                return false;
            }
            if (rowMates != null) {
                if (selected.contains(spell)) {
                    return true; // a row always keeps one
                }
                selected.removeAll(rowMates);
                selected.add(spell);
            } else if (selected.contains(spell)) {
                selected.remove(spell);
            } else if (selected.size() < max) {
                selected.add(spell);
            } else {
                return true; // full: switch one off first (the tooltip says so)
            }
            net.ledok.vectorlib.client.canvas.widget.UiSounds.click();
            changed.run();
            return true;
        }
    }

    /** An item in a slot: count and durability, the vanilla tooltip on hover. */
    private static final class Slot extends Widget {
        private ItemStack stack;

        Slot(ItemStack stack) {
            super(20, 20);
            this.stack = stack;
        }

        void stack(ItemStack stack) {
            this.stack = stack;
            refresh();
        }

        @Override
        protected void rebuild() {
            add(net.ledok.vectorlib.client.canvas.Shapes.rect(0, 0, 20, 20).fill((net.ledok.arenas_ld.screen.RunTheme.current().palette().hairline() & 0x00FFFFFF) | 0x55000000));
            add(ItemStackNode.of(stack, 2, 2, 16));
        }
    }
}
