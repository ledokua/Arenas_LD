package net.ledok.arenas_ld.screen;

import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.Theme;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The mod's shared dark UI palette and VectorLib theme, plus the small node factories every
 * screen uses. Mirrors the constants the owo-ui screens used to redeclare per file, so the
 * ported screens keep their exact colors.
 */
public final class ArenasUi {
    private ArenasUi() {}

    // Shared dark palette (identical to the pre-VectorLib owo screens).
    public static final int BG          = 0xFF070E14;
    public static final int PANEL       = 0xFF121922;
    public static final int PANEL_2     = 0xFF0C1218;
    public static final int HAIRLINE    = 0xFF283442;
    public static final int HAIRLINE_HI = 0xFF3A4A5C;
    public static final int ROW_BG      = 0xFF19222D;
    public static final int ROW_BG_ALT  = 0xFF16202A;
    public static final int INK         = 0xFFE8EEF5;
    public static final int INK_MID     = 0xFF9AA8B8;
    public static final int INK_DIM     = 0xFF5F6E80;
    public static final int ACCENT      = 0xFFA98BE8;
    public static final int ACCENT_DARK = 0xFF6C4FB5;
    public static final int GOOD        = 0xFF86D36C;
    public static final int WARN        = 0xFFF5B042;
    public static final int INFO        = 0xFF6DA3E8;
    public static final int DANGER      = 0xFFE8624A;

    /** Flat widget skins matching the old custom owo button/text-box renderers. */
    public static final WidgetStyle WIDGETS = new WidgetStyle(
            new WidgetStyle.Skin.Flat(ACCENT_DARK, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ACCENT, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(ACCENT_DARK, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(0xFF232B36, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, ACCENT, 1, 0),
            new WidgetStyle.Skin.Flat(PANEL_2, HAIRLINE, 1, 0),
            INK, 0xFFFFFFFF, INK_DIM, INK_DIM, ACCENT, 0x80A98BE8,
            18, 16, 4, true);

    public static final Theme THEME = new Theme(
            PANEL, PANEL_2, HAIRLINE,
            INK, INK_MID, INK_DIM,
            ACCENT, GOOD, WARN, DANGER,
            ROW_BG, ROW_BG_ALT, 0x88000000,
            WIDGETS);

    /** Plain colored text node (no shadow), the ported {@code Components.label(...).color(...)}. */
    public static TextNode text(Component text, int color) {
        return TextNode.of(text).color(color).shadow(false);
    }

    /** Single-line, width-bounded label that clips with an ellipsis. */
    public static Label label(float width, Component text, int color) {
        return Label.of(width, text).color(color);
    }

    /** Content-width button, 18 px tall — the ported {@code Sizing.content() x fixed(18)} accent button. */
    public static Button button(Component label, Runnable onClick) {
        int w = Minecraft.getInstance().font.width(label) + 12;
        return new Button(w, 18, label, onClick);
    }

    /** Small fixed-size button (the ▲ ▼ ✕ × row controls). */
    public static Button button(Component label, float w, float h, Runnable onClick) {
        return new Button(w, h, label, onClick);
    }

    /** Text field with the max length raised BEFORE the initial value goes in (no silent clipping). */
    public static TextField textField(float width, String initial, int maxLength) {
        TextField field = new TextField(width, "", null);
        field.maxLength(maxLength);
        field.text(initial == null ? "" : initial);
        return field;
    }

    /** Vertical spacer, the ported empty {@code verticalFlow} of a fixed height. */
    public static Flex spacer(float px) {
        Flex s = Flex.column();
        s.sizing(Sizing.fill(), Sizing.fixed(px));
        return s;
    }

    /** 1 px horizontal hairline row. */
    public static Flex hairline() {
        Flex line = Flex.column();
        line.sizing(Sizing.fill(), Sizing.fixed(1));
        line.backgroundFill(HAIRLINE);
        return line;
    }

    /** Dimmed single-line section caption. */
    public static Flex sectionHeader(Component title) {
        Flex row = Flex.row();
        row.sizing(Sizing.fill(), Sizing.content());
        row.item(text(title, INK_DIM));
        return row;
    }

    /** Full-size flat backdrop shape for {@code Flex.background(...)} factories. */
    public static java.util.function.Function<net.ledok.vectorlib.client.canvas.Rect, net.ledok.vectorlib.client.canvas.CanvasNode> flat(int fill) {
        return r -> Shapes.rect(0, 0, r.width(), r.height()).fill(fill);
    }

    /** Flat fill plus 1 px outline, the ported {@code Surface.flat(a).and(Surface.outline(b))}. */
    public static java.util.function.Function<net.ledok.vectorlib.client.canvas.Rect, net.ledok.vectorlib.client.canvas.CanvasNode> flatOutline(int fill, int outline) {
        return r -> Shapes.rect(0, 0, r.width(), r.height()).fill(fill).stroke(outline, 1);
    }
}
