package net.ledok.arenas_ld.screen;

import io.wispforest.owo.ui.base.BaseOwoHandledScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
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
import net.ledok.arenas_ld.networking.ModPackets;
import net.ledok.arenas_ld.util.AttributeData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * owo-lib editor for a mob's attribute list (id + value rows). Opened from the
 * boss / mob spawner screens. Mirrors the dark spawner-screen styling and, like
 * the parent screens, ignores the inventory ("E") key so it can't be closed
 * mid-edit.
 */
public class MobAttributesScreen extends BaseOwoHandledScreen<FlowLayout, MobAttributesScreenHandler> {
    private static final int BG          = 0xFF070E14;
    private static final int PANEL       = 0xFF121922;
    private static final int PANEL_2     = 0xFF0C1218;
    private static final int HAIRLINE    = 0xFF283442;
    private static final int HAIRLINE_HI = 0xFF3A4A5C;
    private static final int ROW_BG      = 0xFF19222D;
    private static final int INK         = 0xFFE8EEF5;
    private static final int INK_MID     = 0xFF9AA8B8;
    private static final int INK_DIM     = 0xFF5F6E80;
    private static final int DANGER      = 0xFFE8624A;
    private static final int ACCENT      = 0xFFA98BE8;
    private static final int ACCENT_DARK = 0xFF6C4FB5;

    private static final int DROPDOWN_MAX_CANDIDATES = 50;
    private static final int DROPDOWN_MAX_HEIGHT = 140;

    // Working model — raw strings so mid-edit values survive add/remove rebuilds.
    private final List<String> ids = new ArrayList<>();
    private final List<String> values = new ArrayList<>();
    private final List<Double> maxValues = new ArrayList<>();

    private FlowLayout contentArea;
    private LabelComponent footerLabel;
    private String footerError;

    // Attribute-ID suggestion dropdown (one open at a time, anchored under its row).
    private int idDropdownIndex = -1;
    private TextBoxComponent idDropdownField;
    private FlowLayout idDropdownPanel;

    public MobAttributesScreen(MobAttributesScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title);
        this.titleLabelY = 9999;
        this.inventoryLabelY = 9999;
    }

    /**
     * Swallow the inventory key so tapping E mid-edit doesn't close the editor.
     * Character input still reaches focused fields via charTyped; closing happens
     * via × or Escape.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        loadFromHandler();

        rootComponent.surface(Surface.flat(BG));
        rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        int shellWidth  = Math.max(440, Math.min(560, this.width - 24));
        int shellHeight = Math.max(260, this.height - 24);
        FlowLayout shell = Containers.verticalFlow(Sizing.fixed(shellWidth), Sizing.fixed(shellHeight));
        shell.surface(Surface.flat(PANEL).and(Surface.outline(HAIRLINE_HI)));

        shell.child(buildHeader());

        contentArea = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        contentArea.surface(Surface.flat(PANEL));
        contentArea.padding(Insets.of(10));
        contentArea.gap(4);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.expand(), contentArea);
        scroll.surface(Surface.flat(PANEL));
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        scroll.scrollbarThiccness(8);
        scroll.fixedScrollbarLength(28);
        scroll.scrollStep(18);
        shell.child(scroll);

        shell.child(buildFooter());
        rootComponent.child(shell);

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

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(46));
        header.surface(Surface.flat(PANEL_2));
        header.padding(Insets.of(8, 8, 10, 10));
        header.gap(10);
        header.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout mark = Containers.verticalFlow(Sizing.fixed(20), Sizing.fixed(20));
        mark.surface(Surface.flat(ACCENT).and(Surface.outline(ACCENT_DARK)));
        header.child(mark);

        LabelComponent titleLabel = Components.label(this.title);
        titleLabel.color(Color.ofArgb(INK));
        header.child(titleLabel);

        header.child(Containers.horizontalFlow(Sizing.expand(), Sizing.content()));

        ButtonComponent close = smallButton(Component.literal("×"), b -> onClose());
        close.sizing(Sizing.fixed(22), Sizing.fixed(18));
        header.child(close);
        return header;
    }

    private FlowLayout buildFooter() {
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(34));
        footer.surface(Surface.flat(PANEL_2));
        footer.padding(Insets.of(6));
        footer.gap(6);
        footer.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        footerLabel = Components.label(Component.empty());
        footerLabel.color(Color.ofArgb(DANGER));
        footerLabel.horizontalSizing(Sizing.expand());
        footer.child(footerLabel);

        ButtonComponent add = smallButton(Component.translatable("gui.arenas_ld.add"), b -> {
            ids.add("minecraft:generic.max_health");
            values.add("20.0");
            maxValues.add(Double.MAX_VALUE);
            rebuildUi();
        });
        add.horizontalSizing(Sizing.fixed(90));
        footer.child(add);

        ButtonComponent save = smallButton(Component.translatable("gui.arenas_ld.save"), b -> onSave());
        save.horizontalSizing(Sizing.fixed(90));
        footer.child(save);
        return footer;
    }

    private void rebuildUi() {
        if (contentArea == null) return;
        closeIdDropdown();
        contentArea.clearChildren();

        contentArea.child(sectionHeader(Component.translatable("gui.arenas_ld.attributes")));
        contentArea.child(spacer(2));

        if (ids.isEmpty()) {
            contentArea.child(text(Component.translatable("gui.arenas_ld.no_attributes"), INK_DIM));
        }
        for (int i = 0; i < ids.size(); i++) {
            contentArea.child(attributeRow(i));
        }

        footerLabel.text(footerError != null ? Component.literal(footerError) : Component.empty());
    }

    private FlowLayout attributeRow(int index) {
        FlowLayout container = Containers.verticalFlow(Sizing.fill(100), Sizing.content());

        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(22));
        row.gap(4);
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);

        FlowLayout dropdownPanel = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        dropdownPanel.surface(Surface.BLANK);

        row.child(attributeIdField(index, dropdownPanel));
        row.child(boxedField(Sizing.fixed(70), values.get(index), v -> values.set(index, v)));

        ButtonComponent remove = smallButton(Component.literal("×"), b -> {
            ids.remove(index);
            values.remove(index);
            maxValues.remove(index);
            rebuildUi();
        });
        remove.sizing(Sizing.fixed(20), Sizing.fixed(20));
        row.child(remove);

        container.child(row);
        container.child(dropdownPanel);
        return container;
    }

    /** ID field with a suggestion dropdown over the attribute registry, mirroring the
     *  mob-ID autocomplete on the spawner screens. */
    private FlowLayout attributeIdField(int index, FlowLayout dropdownPanel) {
        FlowLayout wrap = Containers.horizontalFlow(Sizing.expand(), Sizing.fixed(22));
        wrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        wrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        wrap.child(accent);

        TextBoxComponent field = Components.textBox(Sizing.expand(), ids.get(index));
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(v -> {
            ids.set(index, v);
            if (idDropdownIndex == index) refreshIdDropdown();
        });
        field.mouseDown().subscribe((mx, my, btn) -> {
            openIdDropdown(index, field, dropdownPanel);
            return false;
        });
        wrap.child(field);

        ButtonComponent chevron = Components.button(Component.empty(), b -> {
            if (idDropdownIndex == index) closeIdDropdown();
            else openIdDropdown(index, field, dropdownPanel);
        });
        chevron.sizing(Sizing.fixed(18), Sizing.fixed(20));
        chevron.renderer((context, rendered, delta) -> {
            String glyph = idDropdownIndex == index ? "▲" : "▼";
            int tx = rendered.getX() + (rendered.getWidth() - this.font.width(glyph)) / 2;
            int ty = rendered.getY() + (rendered.getHeight() - this.font.lineHeight) / 2 + 1;
            context.drawString(this.font, glyph, tx, ty, INK_MID, false);
        });
        wrap.child(chevron);
        return wrap;
    }

    private void openIdDropdown(int index, TextBoxComponent field, FlowLayout panel) {
        if (idDropdownIndex == index) return;
        closeIdDropdown();
        idDropdownIndex = index;
        idDropdownField = field;
        idDropdownPanel = panel;
        refreshIdDropdown();
    }

    private void closeIdDropdown() {
        idDropdownIndex = -1;
        idDropdownField = null;
        if (idDropdownPanel != null) {
            idDropdownPanel.clearChildren();
            idDropdownPanel.surface(Surface.BLANK);
            idDropdownPanel = null;
        }
    }

    private void refreshIdDropdown() {
        if (idDropdownPanel == null) return;
        List<String> candidates = attributeIdCandidates(idDropdownField != null ? idDropdownField.getValue() : "");
        idDropdownPanel.clearChildren();
        if (candidates.isEmpty()) {
            idDropdownPanel.surface(Surface.BLANK);
            return;
        }
        idDropdownPanel.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));

        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        boolean first = true;
        for (String id : candidates) {
            if (!first) list.child(hairline());
            list.child(suggestionRow(id));
            first = false;
        }

        int rowsHeight = candidates.size() * 20 + Math.max(0, candidates.size() - 1);
        int visibleHeight = Math.min(rowsHeight, DROPDOWN_MAX_HEIGHT);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(visibleHeight), list);
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        idDropdownPanel.child(scroll);
    }

    private List<String> attributeIdCandidates(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(java.util.Locale.ROOT);
        return net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.keySet().stream()
            .map(net.minecraft.resources.ResourceLocation::toString)
            .filter(id -> needle.isEmpty() || id.toLowerCase(java.util.Locale.ROOT).contains(needle))
            .sorted()
            .limit(DROPDOWN_MAX_CANDIDATES)
            .collect(java.util.stream.Collectors.toList());
    }

    private ButtonComponent suggestionRow(String id) {
        ButtonComponent row = Components.button(Component.empty(), b -> {
            if (idDropdownField != null) idDropdownField.text(id);
            closeIdDropdown();
        });
        row.sizing(Sizing.fill(100), Sizing.fixed(20));
        row.renderer((context, rendered, delta) -> {
            int x1 = rendered.getX(); int y1 = rendered.getY();
            boolean hover = rendered.isHoveredOrFocused();
            context.fill(x1, y1, x1 + rendered.getWidth(), y1 + rendered.getHeight(), hover ? ROW_BG : PANEL_2);
            if (hover) context.fill(x1, y1, x1 + 2, y1 + rendered.getHeight(), ACCENT);
            context.drawString(this.font, id, x1 + 8, y1 + (rendered.getHeight() - this.font.lineHeight) / 2 + 1, INK, false);
        });
        return row;
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

        ClientPlayNetworking.send(new ModPackets.UpdateAttributesPayload(
            menu.blockEntity.getBlockPos(), updated));
        this.onClose();
    }

    // ── UI helpers (shared styling with the spawner screens) ──────────────────

    private FlowLayout boxedField(Sizing width, String initial, Consumer<String> onChange) {
        FlowLayout wrap = Containers.horizontalFlow(width, Sizing.fixed(22));
        wrap.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));
        wrap.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        FlowLayout accent = Containers.verticalFlow(Sizing.fixed(2), Sizing.fill(100));
        accent.surface(Surface.flat(ACCENT));
        wrap.child(accent);

        TextBoxComponent field = Components.textBox(Sizing.expand(), initial == null ? "" : initial);
        field.verticalSizing(Sizing.fixed(18));
        field.onChanged().subscribe(onChange::accept);
        wrap.child(field);
        return wrap;
    }

    private FlowLayout sectionHeader(Component title) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.alignment(HorizontalAlignment.LEFT, VerticalAlignment.CENTER);
        LabelComponent titleLabel = Components.label(title);
        titleLabel.color(Color.ofArgb(INK_DIM));
        row.child(titleLabel);
        return row;
    }

    private FlowLayout hairline() {
        FlowLayout line = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(1));
        line.surface(Surface.flat(HAIRLINE));
        return line;
    }

    private FlowLayout spacer(int px) {
        FlowLayout spacer = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(px));
        spacer.surface(Surface.BLANK);
        return spacer;
    }

    private LabelComponent text(Component component, int color) {
        LabelComponent label = Components.label(component);
        label.color(Color.ofArgb(color));
        return label;
    }

    private ButtonComponent smallButton(Component text, Consumer<ButtonComponent> action) {
        ButtonComponent button = Components.button(text, action);
        button.sizing(Sizing.content(), Sizing.fixed(18));
        button.renderer((context, rendered, delta) -> {
            int fill = rendered.active() ? (rendered.isHoveredOrFocused() ? ACCENT : ACCENT_DARK) : PANEL;
            int border = rendered.active() ? ACCENT : HAIRLINE;
            context.fill(rendered.getX(), rendered.getY(), rendered.getX() + rendered.getWidth(), rendered.getY() + rendered.getHeight(), fill);
            context.drawRectOutline(rendered.getX(), rendered.getY(), rendered.getWidth(), rendered.getHeight(), border);
        });
        return button;
    }

    private static String trimDouble(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
