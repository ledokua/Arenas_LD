package net.ledok.arenas_ld.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Registry-backed suggestion dropdown for a ResourceLocation-ID text box, shared by the
 * spawner screens (entity types) and the attribute editor (attributes).
 *
 * <p>Construct with the text box it completes, mount {@link #panel()} directly below the
 * field's row, and place {@link #chevron()} inside the row. Clicking the field or the
 * chevron opens the dropdown, typing filters it live, and picking a row writes the id
 * back via {@code field.text(...)} so the caller's own onChanged subscriber sees it.
 *
 * <p>Screens with several completed fields at once (the attribute editor's rows) share a
 * {@link Group} so opening one dropdown closes any other.
 */
public final class IdSuggestionDropdown {
    // Shared dark-UI palette (matches the spawner/attribute screens).
    private static final int PANEL_2  = 0xFF0C1218;
    private static final int HAIRLINE = 0xFF283442;
    private static final int ROW_BG   = 0xFF19222D;
    private static final int INK      = 0xFFE8EEF5;
    private static final int INK_MID  = 0xFF9AA8B8;
    private static final int ACCENT   = 0xFFA98BE8;

    private static final int MAX_CANDIDATES = 50;
    private static final int MAX_HEIGHT = 140;

    private final Font font;
    private final Supplier<? extends Collection<String>> candidateSource;
    private final TextBoxComponent field;
    private final FlowLayout panel;
    @Nullable private final Group group;
    private boolean open;

    public IdSuggestionDropdown(Font font, Registry<?> registry, TextBoxComponent field) {
        this(font, registry, field, null);
    }

    public IdSuggestionDropdown(Font font, Registry<?> registry, TextBoxComponent field, @Nullable Group group) {
        this(font, () -> registry.keySet().stream().map(ResourceLocation::toString).toList(), field, group);
    }

    /** Non-registry candidates (e.g. server-provided loot table ids); the supplier is re-read on every refresh. */
    public IdSuggestionDropdown(Font font, Supplier<? extends Collection<String>> candidateSource, TextBoxComponent field) {
        this(font, candidateSource, field, null);
    }

    public IdSuggestionDropdown(Font font, Supplier<? extends Collection<String>> candidateSource,
                                TextBoxComponent field, @Nullable Group group) {
        this.font = font;
        this.candidateSource = candidateSource;
        this.field = field;
        this.group = group;
        this.panel = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.panel.surface(Surface.BLANK);
        field.onChanged().subscribe(v -> { if (open) refresh(); });
        field.mouseDown().subscribe((mouseX, mouseY, button) -> { open(); return false; });
        attachFullValueTooltip(field);
    }

    /**
     * Creates a text box whose max length is raised BEFORE the initial value goes in.
     * {@code Components.textBox(sizing, text)} inserts the text while the vanilla 32-char
     * default still applies, silently clipping long ids/names every time a screen rebuilds —
     * a later {@code setMaxLength} can't restore what was already cut.
     */
    public static TextBoxComponent textBox(Sizing horizontalSizing, @Nullable String initialValue, int maxLength) {
        TextBoxComponent field = Components.textBox(horizontalSizing);
        field.setMaxLength(maxLength);
        field.text(initialValue == null ? "" : initialValue);
        return field;
    }

    /**
     * Mirrors the field's full value into a hover tooltip. ID fields are usually narrower than
     * a long ResourceLocation and an unfocused text box hard-clips its text at the pixel width,
     * so without this the stored value looks truncated even though it is intact.
     */
    public static void attachFullValueTooltip(TextBoxComponent field) {
        applyFullValueTooltip(field, field.getValue());
        field.onChanged().subscribe(v -> applyFullValueTooltip(field, v));
    }

    private static void applyFullValueTooltip(TextBoxComponent field, String value) {
        if (value == null || value.isBlank()) {
            field.tooltip((List<ClientTooltipComponent>) null);
        } else {
            field.tooltip(Component.literal(value));
        }
    }

    /** Empty flow to mount directly below the field's row; fills when the dropdown opens. */
    public FlowLayout panel() {
        return panel;
    }

    /** ▲/▼ toggle button sized to sit at the end of a 22px field row. */
    public ButtonComponent chevron() {
        ButtonComponent chevron = Components.button(Component.empty(), b -> toggle());
        chevron.sizing(Sizing.fixed(18), Sizing.fixed(20));
        chevron.renderer((context, rendered, delta) -> {
            String glyph = open ? "▲" : "▼";
            int tx = rendered.getX() + (rendered.getWidth() - font.width(glyph)) / 2;
            int ty = rendered.getY() + (rendered.getHeight() - font.lineHeight) / 2 + 1;
            context.drawString(font, glyph, tx, ty, INK_MID, false);
        });
        return chevron;
    }

    public void open() {
        if (open) return;
        if (group != null) group.opened(this);
        open = true;
        refresh();
    }

    public void close() {
        if (!open) return;
        open = false;
        panel.clearChildren();
        panel.surface(Surface.BLANK);
        if (group != null) group.closed(this);
    }

    private void toggle() {
        if (open) close(); else open();
    }

    private void refresh() {
        List<String> candidates = candidates(field.getValue());
        panel.clearChildren();
        if (!open || candidates.isEmpty()) {
            panel.surface(Surface.BLANK);
            return;
        }
        panel.surface(Surface.flat(PANEL_2).and(Surface.outline(HAIRLINE)));

        FlowLayout list = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        boolean first = true;
        for (String id : candidates) {
            if (!first) list.child(hairline());
            list.child(candidateRow(id));
            first = false;
        }

        int rowsHeight = candidates.size() * 20 + Math.max(0, candidates.size() - 1);
        int visibleHeight = Math.min(rowsHeight, MAX_HEIGHT);
        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(visibleHeight), list);
        scroll.scrollbar(ScrollContainer.Scrollbar.vanillaFlat());
        panel.child(scroll);
    }

    private List<String> candidates(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        return candidateSource.get().stream()
            .filter(id -> needle.isEmpty() || id.toLowerCase(Locale.ROOT).contains(needle))
            .sorted()
            .limit(MAX_CANDIDATES)
            .collect(Collectors.toList());
    }

    private ButtonComponent candidateRow(String id) {
        ButtonComponent row = Components.button(Component.empty(), b -> {
            field.text(id);
            close();
        });
        row.sizing(Sizing.fill(100), Sizing.fixed(20));
        row.renderer((context, rendered, delta) -> {
            int x1 = rendered.getX(); int y1 = rendered.getY();
            boolean hover = rendered.isHoveredOrFocused();
            context.fill(x1, y1, x1 + rendered.getWidth(), y1 + rendered.getHeight(), hover ? ROW_BG : PANEL_2);
            if (hover) context.fill(x1, y1, x1 + 2, y1 + rendered.getHeight(), ACCENT);
            context.drawString(font, id, x1 + 8, y1 + (rendered.getHeight() - font.lineHeight) / 2 + 1, INK, false);
        });
        return row;
    }

    private FlowLayout hairline() {
        FlowLayout line = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(1));
        line.surface(Surface.flat(HAIRLINE));
        return line;
    }

    /** Shared by dropdowns that should be mutually exclusive: opening one closes the rest. */
    public static final class Group {
        @Nullable private IdSuggestionDropdown openDropdown;

        private void opened(IdSuggestionDropdown dropdown) {
            if (openDropdown != null && openDropdown != dropdown) openDropdown.close();
            openDropdown = dropdown;
        }

        private void closed(IdSuggestionDropdown dropdown) {
            if (openDropdown == dropdown) openDropdown = null;
        }

        public void closeAll() {
            if (openDropdown != null) openDropdown.close();
        }
    }
}
