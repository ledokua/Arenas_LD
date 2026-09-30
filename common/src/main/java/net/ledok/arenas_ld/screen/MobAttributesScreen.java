package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.AttributeData;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.ScrollPanel;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT;
import static net.ledok.arenas_ld.screen.ArenasUi.ACCENT_DARK;
import static net.ledok.arenas_ld.screen.ArenasUi.DANGER;
import static net.ledok.arenas_ld.screen.ArenasUi.GOOD;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE;
import static net.ledok.arenas_ld.screen.ArenasUi.HAIRLINE_HI;
import static net.ledok.arenas_ld.screen.ArenasUi.INK;
import static net.ledok.arenas_ld.screen.ArenasUi.INK_DIM;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL;
import static net.ledok.arenas_ld.screen.ArenasUi.PANEL_2;
import net.ledok.arenas_ld.platform.ArenasNetwork;

/**
 * VectorLib editor for a mob's attribute list (id + value rows). Opened from the
 * boss / mob spawner screens. Mirrors the dark spawner-screen styling and, like
 * the parent screens, ignores the inventory ("E") key so it can't be closed
 * mid-edit.
 */
public class MobAttributesScreen extends FitCanvasHandledScreen<MobAttributesScreenHandler> {
    // Working model — raw strings so mid-edit values survive add/remove rebuilds.
    private final List<String> ids = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    private final List<Double> maxValues = new ArrayList<>();

    private Flex contentArea;
    private TextNode footerLabel;
    private String footerError;

    /** The screen that opened this editor; × and Esc return to it instead of closing everything. */
    @Nullable private Screen returnTo;

    // One attribute-ID dropdown open at a time across the rows.
    private final IdSuggestionDropdown.Group idDropdowns = new IdSuggestionDropdown.Group();

    public MobAttributesScreen(MobAttributesScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(600, 340), Placement.Screen.center());
        canvas.theme(ArenasUi.THEME);
        fillWindow();
    }

    /** Fluent: closing this editor (× or Esc) goes back to the given screen. */
    public MobAttributesScreen returnTo(Screen screen) {
        this.returnTo = screen;
        return this;
    }

    @Override
    public void onClose() {
        if (returnTo != null && minecraft != null) {
            // The parent shares this menu's container id, so the server-side menu stays open.
            minecraft.setScreen(returnTo);
            return;
        }
        super.onClose();
    }

    /**
     * Swallow the inventory key so tapping E mid-edit doesn't close the editor.
     * Typing in focused fields still goes through; closing happens via × or Escape.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ArenasUi.swallowsInventoryKey(input, keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        buildAll();
    }

    private void buildAll() {
        loadFromHandler();
        canvas.clear();

        Flex root = canvas.add(Flex.column());
        root.sizing(Sizing.fill(), Sizing.fill());
        root.justify(Justify.CENTER).alignItems(Align.CENTER);

        float shellWidth  = Math.max(440, Math.min(560, canvas.width() - 24));
        float shellHeight = Math.max(260, canvas.height() - 24);
        Flex shell = root.item(Flex.column());
        shell.sizing(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.backgroundFill(PANEL, HAIRLINE_HI, 1);

        shell.item(buildHeader());

        contentArea = Flex.column().gap(4).padding(Insets.of(10));
        contentArea.sizing(Sizing.fill(), Sizing.content());
        contentArea.backgroundFill(PANEL);
        ScrollPanel scroll = new ScrollPanel(100, 100, contentArea);
        scroll.sizing(Sizing.fill(), Sizing.expand());
        scroll.barWidth(8);
        scroll.wheelStep(18);
        shell.item(scroll);

        shell.item(buildFooter());

        root.layoutIn(canvas.width(), canvas.height());
        rebuildUi();
    }

    private void loadFromHandler() {
        ids.clear();
        values.clear();
        maxValues.clear();
        for (AttributeData attribute : menu.attributes) {
            ids.add(attribute.id());
            values.add(trimDouble(attribute.value()));
            maxValues.add(attribute.maxValue());
        }
    }

    private Flex buildHeader() {
        Flex header = Flex.row().gap(10).padding(Insets.of(8, 10, 8, 10)).alignItems(Align.CENTER);
        header.sizing(Sizing.fill(), Sizing.fixed(46));
        header.backgroundFill(PANEL_2);

        Flex mark = header.item(Flex.column());
        mark.sizing(Sizing.fixed(20), Sizing.fixed(20));
        mark.backgroundFill(ACCENT, ACCENT_DARK, 1);

        header.item(ArenasUi.text(this.title, INK));
        header.spacer();
        header.item(ArenasUi.button(Component.literal("×"), 22, 18, this::onClose));
        return header;
    }

    private Flex buildFooter() {
        Flex footer = Flex.row().gap(6).padding(Insets.of(6)).alignItems(Align.CENTER);
        footer.sizing(Sizing.fill(), Sizing.fixed(34));
        footer.backgroundFill(PANEL_2);

        footerLabel = ArenasUi.text(Component.empty(), DANGER);
        footerLabel.sizing(Sizing.expand(), Sizing.content());
        footer.item(footerLabel);

        footer.item(ArenasUi.button(Component.translatable("gui.arenas_ld.add"), 90, 18, () -> {
            ids.add("minecraft:generic.max_health");
            values.add("20.0");
            maxValues.add(Double.MAX_VALUE);
            rebuildUi();
        }));
        footer.item(ArenasUi.button(Component.translatable("gui.arenas_ld.save"), 90, 18, this::onSave));
        return footer;
    }

    private void rebuildUi() {
        if (contentArea == null) return;
        idDropdowns.closeAll();
        contentArea.clear();

        contentArea.item(ArenasUi.sectionHeader(Component.translatable("gui.arenas_ld.attributes")));
        contentArea.item(ArenasUi.spacer(2));

        if (ids.isEmpty()) {
            contentArea.item(ArenasUi.text(Component.translatable("gui.arenas_ld.no_attributes"), INK_DIM));
        }
        for (int i = 0; i < ids.size(); i++) {
            contentArea.item(attributeRow(i));
        }

        footerLabel.color(DANGER);
        footerLabel.text(footerError != null ? Component.literal(footerError) : Component.empty());
    }

    private Flex attributeRow(int index) {
        Flex container = Flex.column();
        container.sizing(Sizing.fill(), Sizing.content());

        Flex row = Flex.row().gap(4).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.fixed(22));

        // ID field with an attribute-registry suggestion dropdown, mirroring the
        // mob-ID autocomplete on the spawner screens.
        Flex idWrap = Flex.row().alignItems(Align.CENTER);
        idWrap.sizing(Sizing.expand(), Sizing.fixed(22));
        idWrap.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = idWrap.item(Flex.column());
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);

        IdSuggestionDropdown.Field idField = IdSuggestionDropdown.textBox(100, ids.get(index), 256);
        idField.size(100, 18);
        idField.sizing(Sizing.expand(), Sizing.fixed(18));
        idField.changeListeners.add(v -> ids.set(index, v));
        idWrap.item(idField);

        IdSuggestionDropdown dropdown = new IdSuggestionDropdown(
            BuiltInRegistries.ATTRIBUTE, idField, idDropdowns);
        idWrap.item(dropdown.chevron());

        row.item(idWrap);
        row.item(boxedField(70, values.get(index), v -> values.set(index, v)));

        row.item(ArenasUi.button(Component.literal("×"), 20, 20, () -> {
            ids.remove(index);
            values.remove(index);
            maxValues.remove(index);
            rebuildUi();
        }));

        container.item(row);
        container.item(dropdown.panel());
        return container;
    }

    private void onSave() {
        List<AttributeData> updated = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i).trim();
            if (id.isEmpty()) continue;
            double value;
            try {
                value = Double.parseDouble(values.get(i).trim());
            } catch (NumberFormatException e) {
                footerError = Component.translatable("gui.arenas_ld.invalid_value", ids.get(i)).getString();
                rebuildUi();
                return;
            }
            updated.add(new AttributeData(id, value, maxValues.get(i)));
        }

        ArenasNetwork.sendToServer(new ModPackets.UpdateAttributesPayload(
            menu.blockEntity.getBlockPos(), updated));
        // Stays open for further edits; × or Esc goes back. The notice clears on the next rebuild.
        footerError = null;
        rebuildUi();
        footerLabel.color(GOOD);
        footerLabel.text(Component.translatable("gui.arenas_ld.saved"));
    }

    // ── UI helpers (shared styling with the spawner screens) ──────────────────

    private Flex boxedField(float width, String initial, Consumer<String> onChange) {
        Flex wrap = Flex.row().alignItems(Align.CENTER);
        wrap.sizing(Sizing.fixed(width), Sizing.fixed(22));
        wrap.backgroundFill(PANEL_2, HAIRLINE, 1);
        Flex accent = wrap.item(Flex.column());
        accent.sizing(Sizing.fixed(2), Sizing.fill());
        accent.backgroundFill(ACCENT);

        IdSuggestionDropdown.Field field = IdSuggestionDropdown.textBox(width - 2, initial, 256);
        field.size(width - 2, 18);
        field.sizing(Sizing.expand(), Sizing.fixed(18));
        field.changeListeners.add(onChange);
        wrap.item(field);
        return wrap;
    }

    private static String trimDouble(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
