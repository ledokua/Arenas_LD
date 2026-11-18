package net.ledok.arenas_ld.screen.run;

import net.ledok.arenas_ld.screen.RunTheme;
import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.ledok.vectorlib.client.canvas.ImageNode;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.layout.Align;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Insets;
import net.ledok.vectorlib.client.canvas.layout.Justify;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.UiSounds;
import net.ledok.vectorlib.client.canvas.widget.Widget;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Shared parts of the reworked run screens (UI rework step 1): text at 1:1, colors from the current
 * {@link RunTheme.Palette} (the player's hub theme or parchment). Client only.
 */
public final class RunWidgets {
    private RunWidgets() {
    }

    // ── text ─────────────────────────────────────────────────────────────────

    public static TextNode text(Component text, int color) {
        return TextNode.of(text).color(color).shadow(false);
    }

    /** A one-line label that clips with "…" and shows the whole text on hover when cut. */
    public static Label label(float width, Component text, int color) {
        Label label = Label.of(width, text).color(color);
        label.tooltipWhenCut(true);
        return label;
    }

    /** A label that takes the leftover width of its row. */
    public static Label grow(Component text, int color) {
        Label label = label(40, text, color);
        label.sizing(Sizing.expand(), Sizing.content());
        return label;
    }

    public static int width(Component text) {
        return Minecraft.getInstance().font.width(text);
    }

    // ── small blocks ─────────────────────────────────────────────────────────

    /** A rounded tag: text in {@code color} on a light wash of it. */
    public static Flex pill(Component text, int color) {
        Flex tag = Flex.row().padding(Insets.of(3, 5, 2, 5)).justify(Justify.CENTER).alignItems(Align.CENTER);
        tag.sizing(Sizing.content(), Sizing.content());
        tag.backgroundFill((color & 0x00FFFFFF) | 0x26000000, (color & 0x00FFFFFF) | 0x66000000, 1);
        tag.item(text(text, color));
        return tag;
    }

    /** A fixed-width pill (table cells). */
    public static Flex pill(Component text, int color, float width) {
        Flex tag = pill(text, color);
        tag.sizing(Sizing.fixed(width), Sizing.content());
        return tag;
    }

    /** A section caption ("PARTY · 3 / 4"), with an optional node on the right. */
    public static Flex section(RunTheme.Palette p, Component title, @Nullable Component count, @Nullable CanvasNode right) {
        Flex row = Flex.row().gap(6).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(text(title, p.inkDim()));
        if (count != null) {
            row.item(text(count, p.accent()));
        }
        row.spacer();
        if (right != null) {
            row.item(right);
        }
        return row;
    }

    public static Flex spacer(float px) {
        Flex gap = Flex.column();
        gap.sizing(Sizing.fill(), Sizing.fixed(px));
        return gap;
    }

    public static Flex hairline(RunTheme.Palette p) {
        Flex line = Flex.row();
        line.sizing(Sizing.fill(), Sizing.fixed(1));
        line.backgroundFill(p.hairline());
        return line;
    }

    /** A player's face (skin face + hat layer) from the client's player list; the default skin when unknown. */
    public static CanvasNode head(UUID player, float size) {
        ResourceLocation skin;
        var connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(player) : null;
        skin = info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(player).texture();
        // A group, not a row: the hat layer goes over the face (a row put it beside it, over the name: 4.36.1).
        net.ledok.vectorlib.client.canvas.CanvasGroup box = net.ledok.vectorlib.client.canvas.CanvasGroup.create();
        box.sizing(Sizing.fixed(size), Sizing.fixed(size));
        box.add(ImageNode.of(skin, 0, 0, size, size, 64, 64).region(8, 8, 8, 8));
        box.add(ImageNode.of(skin, 0, 0, size, size, 64, 64).region(40, 8, 8, 8));
        return box;
    }

    // ── buttons ──────────────────────────────────────────────────────────────

    /** A flat button look in the palette (quiet panel, row color on hover). */
    public static WidgetStyle flatStyle(RunTheme.Palette p) {
        return new WidgetStyle(
            new WidgetStyle.Skin.Flat(p.panel(), p.hairlineHi(), 1, 0),
            new WidgetStyle.Skin.Flat(p.row(), p.hairlineHi(), 1, 0),
            new WidgetStyle.Skin.Flat(p.row(), p.hairlineHi(), 1, 0),
            new WidgetStyle.Skin.Flat(p.panel(), p.hairline(), 1, 0),
            new WidgetStyle.Skin.Flat(p.panel2(), p.hairline(), 1, 0),
            new WidgetStyle.Skin.Flat(p.panel2(), p.accent(), 1, 0),
            new WidgetStyle.Skin.Flat(p.panel2(), p.hairline(), 1, 0),
            p.ink(), p.ink(), p.inkMid(), p.inkDim(), p.hairlineHi(), (p.accent() & 0x00FFFFFF) | 0x55000000,
            18, 16, 4, false);
    }

    /** A red outlined button (kick). */
    public static WidgetStyle dangerStyle(RunTheme.Palette p) {
        int d = p.danger();
        return new WidgetStyle(
            new WidgetStyle.Skin.Flat((d & 0x00FFFFFF) | 0x1F000000, d, 1, 0),
            new WidgetStyle.Skin.Flat((d & 0x00FFFFFF) | 0x44000000, d, 1, 0),
            new WidgetStyle.Skin.Flat((d & 0x00FFFFFF) | 0x44000000, d, 1, 0),
            new WidgetStyle.Skin.Flat((d & 0x00FFFFFF) | 0x1F000000, d, 1, 0),
            new WidgetStyle.Skin.Flat(p.panel2(), p.hairline(), 1, 0),
            new WidgetStyle.Skin.Flat(p.panel2(), p.accent(), 1, 0),
            new WidgetStyle.Skin.Flat(p.panel2(), p.hairline(), 1, 0),
            d, d, p.inkDim(), p.inkDim(), d, (p.accent() & 0x00FFFFFF) | 0x55000000,
            18, 16, 4, false);
    }

    /** The primary action (Start): a filled accent button, a little taller. */
    public static WidgetStyle primaryStyle(RunTheme.Palette p) {
        int a = p.accent();
        int light = 0xFFF6EAD0;
        return new WidgetStyle(
            new WidgetStyle.Skin.Flat(a, p.hairlineHi(), 1, 2),
            new WidgetStyle.Skin.Flat(lighten(a), p.hairlineHi(), 1, 2),
            new WidgetStyle.Skin.Flat(a, p.hairlineHi(), 1, 2),
            new WidgetStyle.Skin.Flat((a & 0x00FFFFFF) | 0x66000000, p.hairline(), 1, 2),
            new WidgetStyle.Skin.Flat(p.panel2(), p.hairline(), 1, 0),
            new WidgetStyle.Skin.Flat(p.panel2(), p.accent(), 1, 0),
            new WidgetStyle.Skin.Flat(p.panel2(), p.hairline(), 1, 0),
            light, 0xFFFFFFFF, light, 0xCCF6EAD0, p.hairlineHi(), (a & 0x00FFFFFF) | 0x55000000,
            20, 16, 4, false);
    }

    private static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 24);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 24);
        int b = Math.min(255, (argb & 0xFF) + 24);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** A content-width button with the theme's skin, 18 tall. */
    public static Button button(Component label, Runnable onClick) {
        return new Button(width(label) + 14, 18, label, onClick);
    }

    public static Button button(Component label, float width, Runnable onClick) {
        return new Button(width, 18, label, onClick);
    }

    // ── tab and segmented control ────────────────────────────────────────────

    /** A tab: the selected one sits on the panel color with an accent underline. */
    public static final class Tab extends Widget {
        private Component label;
        private final RunTheme.Palette p;
        private final BooleanSupplier selected;
        private final Runnable action;

        public Tab(RunTheme.Palette p, Component label, BooleanSupplier selected, Runnable action) {
            super(RunWidgets.width(label) + 16, 18);
            this.p = p;
            this.label = label;
            this.selected = selected;
            this.action = action;
            bind(() -> this.selected.getAsBoolean());
        }

        public Tab label(Component label) {
            this.label = label;
            size(RunWidgets.width(label) + 16, height);
            return this;
        }

        @Override
        protected void rebuild() {
            boolean sel = selected.getAsBoolean();
            int fill = sel ? p.panel() : (hovered || focused ? p.row() : p.panel2());
            add(Shapes.rect(0, 0, width, height).fill(fill));
            if (sel) {
                add(Shapes.rect(0, height - 2, width, 2).fill(p.accent()));
                if (focused) add(Shapes.rect(0, 0, width, 1).fill(p.accent()));
            }
            add(TextNode.of(label).at(width / 2, textY(height)).align(TextNode.Align.CENTER)
                .color(sel ? p.ink() : p.inkMid()).shadow(false));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!enabled || button != 0) return false;
            if (selected.getAsBoolean()) return true;
            UiSounds.click();
            action.run();
            return true;
        }

        @Override
        public boolean focusable() { return enabled; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (!enabled) return false;
            if (key == 257 || key == 335 || key == 32) {
                if (!selected.getAsBoolean()) {
                    UiSounds.click();
                    action.run();
                }
                return true;
            }
            return false;
        }
    }

    /** One segment of a segmented control: a wash of its color with an outline when selected. */
    public static final class Segment extends Widget {
        private final Component label;
        private final int color;
        private final RunTheme.Palette p;
        private final BooleanSupplier selected;
        private final Runnable action;

        public Segment(RunTheme.Palette p, Component label, int color, BooleanSupplier selected, Runnable action) {
            super(Math.max(60, RunWidgets.width(label) + 12), 20);
            this.p = p;
            this.label = label;
            this.color = color;
            this.selected = selected;
            this.action = action;
            bind(() -> this.selected.getAsBoolean());
        }

        @Override
        protected void rebuild() {
            boolean sel = selected.getAsBoolean();
            boolean hover = enabled && (hovered || focused);
            int fill = sel ? ((color & 0x00FFFFFF) | 0x33000000) : (hover ? p.row() : p.panel2());
            add(Shapes.rect(0, 0, width, height).fill(fill).stroke(sel ? color : p.hairline(), 1));
            int ink = sel ? color : (enabled ? p.ink() : p.inkDim());
            add(TextNode.of(label).at(width / 2, textY(height)).maxWidth(Math.max(1, (int) width - 6)).ellipsis(true)
                .align(TextNode.Align.CENTER).color(ink).shadow(false));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!enabled || button != 0) return false;
            if (selected.getAsBoolean()) return true;
            UiSounds.click();
            action.run();
            return true;
        }

        @Override
        public boolean focusable() { return enabled; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (!enabled) return false;
            if (key == 257 || key == 335 || key == 32) {
                if (!selected.getAsBoolean()) {
                    UiSounds.click();
                    action.run();
                }
                return true;
            }
            return false;
        }
    }

    /**
     * An on/off filter chip (4.41.0, the Timeline's filters): rounded, a check mark and the color's wash while on, plain
     * and dim while off. Every click runs {@code action}; the owner flips its own state.
     */
    public static final class Toggle extends Widget {
        public static final float HEIGHT = 16;
        private final Component label;
        private final int color;
        private final RunTheme.Palette p;
        private final BooleanSupplier on;
        private final Runnable action;

        public Toggle(RunTheme.Palette p, Component label, int color, BooleanSupplier on, Runnable action) {
            super(RunWidgets.width(label) + 22, HEIGHT);
            this.p = p;
            this.label = label;
            this.color = color;
            this.on = on;
            this.action = action;
            bind(() -> this.on.getAsBoolean());
        }

        @Override
        protected void rebuild() {
            boolean sel = on.getAsBoolean();
            boolean hover = enabled && (hovered || focused);
            int fill = sel ? ((color & 0x00FFFFFF) | 0x33000000) : (hover ? p.row() : p.panel2());
            add(Shapes.roundRect(0, 0, width, height, height / 2).fill(fill).stroke(sel ? color : p.hairline(), 1));
            int ink = sel ? color : (hover ? p.inkMid() : p.inkDim());
            add(TextNode.of(Component.literal(sel ? "✓" : "·")).at(9, textY(height)).align(TextNode.Align.CENTER)
                .color(ink).shadow(false));
            add(TextNode.of(label).at(16, textY(height)).maxWidth(Math.max(1, (int) width - 20)).ellipsis(true)
                .color(sel ? p.ink() : ink).shadow(false));
        }

        @Override
        public boolean onMouseDown(float x, float y, int button) {
            if (!enabled || button != 0) return false;
            UiSounds.click();
            action.run();
            return true;
        }

        @Override
        public boolean focusable() { return enabled; }

        @Override
        public boolean onKeyPressed(int key, int scanCode, int modifiers) {
            if (!enabled || key != 257 && key != 335 && key != 32) return false;
            UiSounds.click();
            action.run();
            return true;
        }
    }

    /** Segments side by side over the full width. */
    public static Flex segmented(Segment... segments) {
        Flex row = Flex.row().gap(3).alignItems(Align.CENTER);
        row.sizing(Sizing.fill(), Sizing.content());
        for (Segment segment : segments) {
            segment.sizing(Sizing.expand(), Sizing.fixed(20));
            row.item(segment);
        }
        return row;
    }
}
