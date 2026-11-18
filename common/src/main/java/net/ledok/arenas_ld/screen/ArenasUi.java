package net.ledok.arenas_ld.screen;

import net.ledok.vectorlib.client.canvas.CanvasGroup;
import net.ledok.vectorlib.client.canvas.Rect;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.canvas.CanvasInput;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.Theme;
import net.ledok.vectorlib.client.canvas.layout.Flex;
import net.ledok.vectorlib.client.canvas.layout.Sizing;
import net.ledok.vectorlib.client.canvas.widget.Button;
import net.ledok.vectorlib.client.canvas.widget.Label;
import net.ledok.vectorlib.client.canvas.widget.TextField;
import net.ledok.vectorlib.client.canvas.widget.WidgetStyle;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The mod's shared dark UI palette and VectorLib theme, plus the small node factories every
 * screen uses. Mirrors the constants the owo-ui screens used to redeclare per file, so the
 * ported screens keep their exact colors.
 */
public final class ArenasUi {
    private ArenasUi() {}

    /**
     * Center placement that shrinks the canvas to fit the GUI-scaled window, never enlarging it:
     * 1:1 and pixel-crisp whenever the design fits (any GUI scale where it does), a fractional
     * fit factor only as the fallback when the window is smaller than the design.
     */
    public static Placement.Screen fitCenter(float canvasW, float canvasH, float guiW, float guiH) {
        float scale = Math.min(1f, Math.min(guiW / Math.max(1f, canvasW), guiH / Math.max(1f, canvasH)));
        return Placement.Screen.center().scale(scale);
    }

    /** Keep an open dialog on-screen after resize without rebuilding its contents. */
    public static CanvasGroup fitDialog(Flex panel) {
        CanvasGroup frame = new CanvasGroup() {
            @Override
            public Rect bounds() {
                Rect bounds = panel.bounds();
                VectorCanvas canvas = canvas();
                if (canvas == null || bounds.width() <= 0 || bounds.height() <= 0) return bounds;
                float scale = Math.min(1, Math.min(Math.max(1, canvas.width() - 12) / bounds.width(),
                    Math.max(1, canvas.height() - 12) / bounds.height()));
                panel.transform.scale(scale);
                return new Rect(bounds.x() * scale, bounds.y() * scale, bounds.width() * scale, bounds.height() * scale);
            }
        };
        frame.add(panel);
        return frame;
    }

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
    /**
     * Whether a screen's {@code keyPressed} should swallow this key. The inventory key never
     * closes an admin screen (close via × or Esc only), even mid-edit: a focused field still gets
     * the key first (a rebound inventory key must not eat a shortcut), but it is never passed on to
     * the container screen, which would close on it. The letter itself arrives through charTyped.
     */
    public static boolean swallowsInventoryKey(CanvasInput input, int keyCode, int scanCode, int modifiers) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.options.keyInventory.matches(keyCode, scanCode)) return false;
        input.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    /** Scroll nested viewports so a control reached by Tab can be seen before it is activated. */
    public static void revealFocus(CanvasInput input) {
        var node = input.focusedNode();
        if (node == null || node.bounds() == null) return;
        var bounds = node.bounds();
        for (var ancestor = node.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (!(ancestor instanceof net.ledok.vectorlib.client.canvas.widget.ScrollPanel scroll)) continue;
            float[] top = node.localToRoot(bounds.x(), bounds.y());
            float[] bottom = node.localToRoot(bounds.x(), bounds.bottom());
            float y = scroll.rootToLocal(top[0], top[1])[1];
            float end = scroll.rootToLocal(bottom[0], bottom[1])[1];
            float overflow = end - scroll.bounds().bottom();
            if (y < 0) {
                scroll.scrollTo(scroll.scrollY() + y);
            } else if (overflow > 0) {
                scroll.scrollTo(scroll.scrollY() + overflow);
            }
        }
    }

    public static TextNode text(Component text, int color) {
        return TextNode.of(text).color(color).shadow(false);
    }

    /** Single-line, width-bounded label that clips with an ellipsis. */
    public static Label label(float width, Component text, int color) {
        return Label.of(width, text).color(color).tooltipWhenCut(true);
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

    public static Button closeButton(Runnable action) {
        Button close = button(Component.literal("×"), 22, 18, action);
        close.tooltip(Component.translatable("gui.arenas_ld.close_screen"));
        return close;
    }

    /**
     * A spawner's "Scale minions: On/Off" switch (see MinionScalingHost). Starts from the spawner's
     * client copy, which the server keeps in step through block updates.
     */
    public static Button minionScalingButton(net.minecraft.core.BlockPos spawnerPos) {
        var level = Minecraft.getInstance().level;
        boolean[] on = {!(level != null && level.getBlockEntity(spawnerPos)
            instanceof net.ledok.arenas_ld.util.MinionScalingHost host) || host.scalesMinions()};
        Button[] button = new Button[1];
        button[0] = new Button(150, 18, minionScalingLabel(on[0]), () -> {
            on[0] = !on[0];
            net.ledok.arenas_ld.platform.ArenasNetwork.sendToServer(
                new net.ledok.arenas_ld.packet.SetMinionScalingPayload(spawnerPos, on[0]));
            button[0].label(minionScalingLabel(on[0]));
        });
        return button[0];
    }

    private static Component minionScalingLabel(boolean on) {
        return Component.translatable(on ? "gui.arenas_ld.spawner.scale_minions.on" : "gui.arenas_ld.spawner.scale_minions.off");
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
