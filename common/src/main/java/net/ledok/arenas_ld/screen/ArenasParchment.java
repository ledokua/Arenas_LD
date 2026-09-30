package net.ledok.arenas_ld.screen;

import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.ledok.vectorlib.client.canvas.NineSlicePanel;
import net.ledok.vectorlib.client.canvas.Rect;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.Theme;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * The parchment look for the player-facing lobby screens — the same theme the NPCs_LD dialogs
 * and the Economy_LD shop/auction/inbox screens use (textures copied into
 * {@code assets/arenas_ld/textures/gui/parchment/} so there is no asset dependency).
 * Mirrors {@link ArenasUi}'s constant names and helpers one-for-one, so a lobby screen switches
 * palettes by importing from this class instead; the admin/editor screens stay on the dark
 * {@link ArenasUi} palette.
 */
public final class ArenasParchment {

    /**
     * Vanilla-like shell sizing for the parchment lobby screens: the shell is at most its design
     * size and shrinks with the window (the content scroll absorbs lost height) instead of the
     * old approach of forcing the whole GUI scale while the screen was open. The floor keeps the
     * layout from imploding on absurd scale/window combinations — below it the shell simply
     * overflows and clips, exactly like a vanilla chest screen does there.
     */
    public static void sizeShell(Flex shell, float canvasW, float canvasH, int designW, int designH) {
        int w = (int) Math.max(320, Math.min(designW, canvasW - 8));
        int h = (int) Math.max(240, Math.min(designH, canvasH - 8));
        shell.sizing(Sizing.fixed(w), Sizing.fixed(h));
    }
    private ArenasParchment() {}

    // Parchment palette: paper surfaces, brown inks. Same token roles as ArenasUi.
    public static final int BG          = 0xFF2A2018;
    public static final int PANEL       = 0xFFE5D8B9;
    public static final int PANEL_2     = 0xFFDACAA5;
    public static final int HAIRLINE    = 0xFF5A4632;
    public static final int HAIRLINE_HI = 0xFF3B2612;
    public static final int ROW_BG      = 0xFFD8C69E;
    public static final int ROW_BG_ALT  = 0xFFD2BF94;
    public static final int INK         = 0xFF2B1B0E;
    public static final int INK_MID     = 0xFF4A2E16;
    public static final int INK_DIM     = 0xFF8A7A60;
    public static final int ACCENT      = 0xFF7A4A1E;
    public static final int ACCENT_DARK = 0xFF5A3414;
    public static final int GOOD        = 0xFF3E6B2F;
    public static final int WARN        = 0xFF9A6A10;
    public static final int INFO        = 0xFF2F5A7A;
    public static final int DANGER      = 0xFF8B2F23;

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("arenas_ld", "textures/gui/parchment/" + name + ".png");
    }

    private static WidgetStyle.Skin.NineSlice button(String name) {
        return new WidgetStyle.Skin.NineSlice(tex(name), 32, 16, 4);
    }

    /** Parchment nine-slice buttons; sizes match {@link ArenasUi#WIDGETS} so layouts don't shift. */
    public static final WidgetStyle WIDGETS = new WidgetStyle(
            button("button_normal"), button("button_hover"), button("button_hover"), button("button_disabled"),
            new WidgetStyle.Skin.Flat(0x2E2B1B0E, HAIRLINE, 1, 2),
            new WidgetStyle.Skin.Flat(0x2E2B1B0E, ACCENT, 1, 2),
            new WidgetStyle.Skin.NineSlice(tex("panel"), 48, 48, 8),
            HAIRLINE_HI, 0xFF000000, 0xFF7A7060, INK_DIM, ACCENT, 0x557A4A1E,
            18, 16, 4, false);

    public static final Theme THEME = new Theme(
            PANEL, PANEL_2, HAIRLINE,
            INK, INK_MID, INK_DIM,
            ACCENT, GOOD, WARN, DANGER,
            ROW_BG, ROW_BG_ALT, 0x88000000,
            WIDGETS);

    /** Shell background: the tiled parchment nine-slice panel (give the shell ~8px padding for the border art). */
    public static Function<Rect, CanvasNode> panel() {
        return r -> NineSlicePanel.of(tex("panel"), 48, 48)
                .insets(8).centerMode(NineSlicePanel.Mode.TILE)
                .tint(0xFFE4E4E4) // darkened to sit with the deeper flat surfaces
                .bounds(0, 0, r.width(), r.height());
    }

    // ---- ArenasUi helper mirror (color-agnostic ones delegate; colored ones use this palette) ----

    /** Plain colored text node (no shadow). */
    public static TextNode text(Component text, int color) {
        return ArenasUi.text(text, color);
    }

    /** Single-line, width-bounded label that clips with an ellipsis. */
    public static Label label(float width, Component text, int color) {
        return ArenasUi.label(width, text, color);
    }

    /** Content-width button, 18 px tall. */
    public static Button button(Component label, Runnable onClick) {
        return ArenasUi.button(label, onClick);
    }

    /** Small fixed-size button (the ▲ ▼ ✕ × row controls). */
    public static Button button(Component label, float w, float h, Runnable onClick) {
        return ArenasUi.button(label, w, h, onClick);
    }

    /** Text field with the max length raised BEFORE the initial value goes in. */
    public static TextField textField(float width, String initial, int maxLength) {
        return ArenasUi.textField(width, initial, maxLength);
    }

    /** Vertical spacer of a fixed height. */
    public static Flex spacer(float px) {
        return ArenasUi.spacer(px);
    }

    /** 1 px horizontal hairline row in the parchment brown. */
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
    public static Function<Rect, CanvasNode> flat(int fill) {
        return ArenasUi.flat(fill);
    }

    /** Flat fill plus 1 px outline. */
    public static Function<Rect, CanvasNode> flatOutline(int fill, int outline) {
        return ArenasUi.flatOutline(fill, outline);
    }
}
