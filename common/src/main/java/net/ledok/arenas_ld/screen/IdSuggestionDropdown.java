package net.ledok.arenas_ld.screen;

import net.ledok.vectorlib.client.canvas.CanvasGroup;
import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.VirtualList;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Registry-backed suggestion dropdown for a ResourceLocation-ID text box, shared by the
 * spawner screens (entity types) and the attribute editor (attributes).
 *
 * <p>Construct with the text box it completes (created via {@link #textBox}), mount
 * {@link #panel()} directly below the field's row, and place {@link #chevron()} inside the
 * row. Clicking the field or the chevron opens the dropdown, typing filters it live, and
 * picking a row writes the id back via {@code field.text(...)} so the caller's own
 * change listener sees it. Registry-backed dropdowns also show each element's in-game name
 * beside its id and match the filter against either.
 *
 * <p>Screens with several completed fields at once (the attribute editor's rows) share a
 * {@link Group} so opening one dropdown closes any other.
 */
public final class IdSuggestionDropdown {
    private static final int MAX_HEIGHT = 140;
    private static final float ROW_H = 20;

    /** Suggestion-row look: flat panel rows that highlight on hover. */
    private static final WidgetStyle ROW_STYLE = new WidgetStyle(
            new WidgetStyle.Skin.Flat(ArenasUi.PANEL_2, 0, 0, 0),
            new WidgetStyle.Skin.Flat(ArenasUi.ROW_BG, ArenasUi.ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(ArenasUi.ROW_BG, ArenasUi.ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(ArenasUi.PANEL_2, 0, 0, 0),
            new WidgetStyle.Skin.Flat(ArenasUi.PANEL_2, ArenasUi.HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ArenasUi.PANEL_2, ArenasUi.ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(ArenasUi.PANEL_2, ArenasUi.HAIRLINE, 1, 0),
            ArenasUi.INK, ArenasUi.INK, ArenasUi.INK_DIM, ArenasUi.INK_DIM, ArenasUi.ACCENT, 0x80A98BE8,
            ROW_H, 16, 4, false);

    private final Supplier<? extends Collection<String>> candidateSource;
    @Nullable private final Registry<?> registry;
    private final Field field;
    private final Flex panel;
    private final VirtualList<String> list;
    @Nullable private final Group group;
    @Nullable private Map<String, DisplayName> names;
    private boolean open;

    public IdSuggestionDropdown(Registry<?> registry, Field field) {
        this(registry, field, null);
    }

    public IdSuggestionDropdown(Registry<?> registry, Field field, @Nullable Group group) {
        this(() -> registry.keySet().stream().map(ResourceLocation::toString).toList(), registry, field, group);
    }

    /** Non-registry candidates (e.g. server-provided loot table ids); the supplier is re-read on every refresh. */
    public IdSuggestionDropdown(Supplier<? extends Collection<String>> candidateSource, Field field) {
        this(candidateSource, field, null);
    }

    public IdSuggestionDropdown(Supplier<? extends Collection<String>> candidateSource,
                                Field field, @Nullable Group group) {
        this(candidateSource, null, field, group);
    }

    private IdSuggestionDropdown(Supplier<? extends Collection<String>> candidateSource, @Nullable Registry<?> registry,
                                 Field field, @Nullable Group group) {
        this.candidateSource = candidateSource;
        this.registry = registry;
        this.field = field;
        this.group = group;
        this.panel = Flex.column();
        this.panel.sizing(Sizing.fill(), Sizing.content());
        this.list = new VirtualList<>(100, 0, ROW_H + 1, this::candidateRow);
        field.changeListeners.add(v -> { if (open) refresh(); });
        field.mouseDownHooks.add(this::open);
        attachFullValueTooltip(field);
    }

    /**
     * Creates a text box for use with this dropdown. The max length is raised BEFORE the
     * initial value goes in, so long ids/names are never silently clipped on screen rebuilds.
     * VectorLib's change/mouse hooks are single-consumer, so the returned {@link Field}
     * multiplexes them: register via {@code field.changeListeners.add(...)}.
     */
    public static Field textBox(float width, @Nullable String initialValue, int maxLength) {
        Field field = new Field(width);
        field.maxLength(maxLength);
        field.text(initialValue == null ? "" : initialValue);
        return field;
    }

    /**
     * Mirrors the field's full value into a hover tooltip. ID fields are usually narrower than
     * a long ResourceLocation and an unfocused text box hard-clips its text at the pixel width,
     * so without this the stored value looks truncated even though it is intact.
     */
    public static void attachFullValueTooltip(Field field) {
        applyFullValueTooltip(field, field.text());
        field.changeListeners.add(v -> applyFullValueTooltip(field, v));
    }

    private static void applyFullValueTooltip(TextField field, String value) {
        if (value == null || value.isBlank()) {
            field.tooltip((Supplier<List<Component>>) null);
        } else {
            field.tooltip(Component.literal(value));
        }
    }

    /** Empty flow to mount directly below the field's row; fills when the dropdown opens. */
    public Flex panel() {
        return panel;
    }

    /** ▲/▼ toggle button sized to sit at the end of a 22px field row. */
    public Button chevron() {
        Button chevron = new Button(18, 20, Component.literal("▼"), this::toggle);
        chevron.style(ROW_STYLE);
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
        panel.clear();
        panel.background(null);
        if (group != null) group.closed(this);
    }

    private void toggle() {
        if (open) close(); else open();
    }

    private void refresh() {
        List<String> candidates = candidates(field.text());
        if (!open || candidates.isEmpty()) {
            panel.clear();
            panel.background(null);
            return;
        }
        panel.background(ArenasUi.flatOutline(ArenasUi.PANEL_2, ArenasUi.HAIRLINE));
        list.items(candidates).scrollTo(0);
        list.sizing(Sizing.fill(), Sizing.fixed(Math.min(candidates.size() * list.rowHeight(), MAX_HEIGHT)));
        if (list.parent() != panel) panel.item(list);
    }

    private List<String> candidates(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        Map<String, DisplayName> displayNames = names();
        return candidateSource.get().stream()
            .filter(id -> {
                if (needle.isEmpty() || id.toLowerCase(Locale.ROOT).contains(needle)) return true;
                DisplayName name = displayNames.get(id);
                return name != null && name.searchKey().contains(needle);
            })
            .sorted()
            .toList();
    }

    private CanvasNode candidateRow(String id, int index, float rowWidth) {
        Button button = new Button(rowWidth, ROW_H, Component.literal(id), () -> {
            field.text(id);
            field.notifyChanged();
            close();
        });
        button.style(ROW_STYLE);
        button.align(TextNode.Align.LEFT);
        DisplayName name = names().get(id);
        if (name != null) {
            // The id is what's being picked, so the name yields; the filter also matches on the name,
            // so a cut name still has to be readable on hover.
            button.secondary(name.text());
            button.cutSecondaryFirst(true);
            button.tooltipWhenCut(true);
        }

        CanvasGroup row = new CanvasGroup();
        row.add(button);
        if (index < list.size() - 1) row.add(Shapes.rect(0, ROW_H, rowWidth, 1).fill(ArenasUi.HAIRLINE));
        return row;
    }

    private Map<String, DisplayName> names() {
        if (names == null) names = registry == null ? Map.of() : resolveNames(registry);
        return names;
    }

    private static <T> Map<String, DisplayName> resolveNames(Registry<T> registry) {
        Map<String, DisplayName> resolved = new HashMap<>();
        for (Map.Entry<ResourceKey<T>, T> entry : registry.entrySet()) {
            Component name = displayName(entry.getValue());
            if (name == null) continue;
            resolved.put(entry.getKey().location().toString(),
                new DisplayName(name, name.getString().toLowerCase(Locale.ROOT)));
        }
        return resolved;
    }

    @Nullable
    private static Component displayName(Object element) {
        if (element instanceof EntityType<?> type) return type.getDescription();
        if (element instanceof Attribute attribute) return Component.translatable(attribute.getDescriptionId());
        if (element instanceof MobEffect effect) return effect.getDisplayName();
        return null;
    }

    private record DisplayName(Component text, String searchKey) {}

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

    /**
     * TextField with multiplexed change/mouse-down hooks. VectorLib's {@code onChange} is a
     * single consumer (owo had subscribable event streams); the dropdown and the owning screen
     * both need to observe the field, so they register in {@link #changeListeners} instead.
     */
    public static final class Field extends TextField {
        public final List<Consumer<String>> changeListeners = new ArrayList<>();
        public final List<Runnable> mouseDownHooks = new ArrayList<>();

        public Field(float width) {
            super(width, "", null);
            onChange(v -> changeListeners.forEach(l -> l.accept(v)));
        }

        /** Fires the change listeners for a programmatic {@code text(...)} write. */
        public void notifyChanged() {
            changeListeners.forEach(l -> l.accept(text()));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            mouseDownHooks.forEach(Runnable::run);
            return super.onMouseDown(x, y, button);
        }
    }
}
