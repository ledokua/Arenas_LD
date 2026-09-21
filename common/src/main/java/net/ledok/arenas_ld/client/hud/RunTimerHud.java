package net.ledok.arenas_ld.client.hud;

import net.ledok.arenas_ld.packet.RunHudPayload;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.vectorlib.client.canvas.Colors;
import net.ledok.vectorlib.client.canvas.ShapeNode;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.presentation.CanvasOverlay;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The run-timer HUD bar: a dark rounded panel with a large countdown, an accent progress
 * strip, a "Hardcore" tag, an objective line, and an optional boss-HP row. Replaces the
 * vanilla boss-bar timers. Driven by {@link RunHudPayload} updates (~1/s) and counts down
 * locally between packets; hides itself when the server goes quiet.
 */
public final class RunTimerHud {
    /** Server sends every 20 ticks; past this silence we assume the run is gone. */
    private static final long STALE_TICKS = 80;
    private static final float SLIDE_TICKS = 8.0f;

    private static final float CANVAS_W = 240;
    private static final float CANVAS_H = 50;
    private static final float PANEL_W = 76;
    private static final float PANEL_H = 24;
    private static final float PANEL_X = (CANVAS_W - PANEL_W) / 2;
    private static final float BAR_W = 140;
    private static final float BAR_X = (CANVAS_W - BAR_W) / 2;
    private static final float BOSS_Y = PANEL_H + 16;

    private static @Nullable RunHudPayload data;
    private static long receivedAtTick;
    private static long shownAtTick;
    private static @Nullable CanvasOverlay.Handle handle;

    private static VectorCanvas canvas;
    private static ShapeNode panel;
    private static TextNode timeText;
    private static TextNode hardcoreTag;
    private static TextNode labelText;
    private static ShapeNode bossTrack;
    private static ShapeNode bossFill;
    private static TextNode bossText;

    private RunTimerHud() {
    }

    public static void onPayload(RunHudPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || payload.kind() == RunHudPayload.Kind.HIDDEN) {
            hide();
            return;
        }
        boolean wasHidden = handle == null;
        data = payload;
        receivedAtTick = mc.level.getGameTime();
        if (wasHidden) {
            shownAtTick = receivedAtTick;
            ensureCanvas();
            handle = CanvasOverlay.show(canvas, Placement.Screen.top(6));
        }
        applyStatic(payload);
    }

    public static void clear() {
        hide();
    }

    /** True while the run timer bar is on screen — vanilla boss bars are suppressed then. */
    public static boolean isActive() {
        return handle != null;
    }

    /** Per-frame driver, called from the shared HUD render hook. */
    public static void tick(float partialTick) {
        if (handle == null || data == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            hide();
            return;
        }
        long now = mc.level.getGameTime();
        long sinceUpdate = now - receivedAtTick;
        if (sinceUpdate > STALE_TICKS || sinceUpdate < 0) {
            hide();
            return;
        }

        float remainingF = Math.max(0, data.remainingTicks() - (sinceUpdate + partialTick));
        int secondsLeft = Math.max(0, (int) Math.ceil(remainingF / 20.0f));
        timeText.text(String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60));

        if (data.bossHpPercent() >= 0) {
            float hpFrac = data.bossHpPercent() / 100.0f;
            bossFill.path(net.ledok.vectorlib.client.canvas.Path2D.create().roundRect(
                BAR_X, BOSS_Y, Math.max(2, BAR_W * hpFrac), 4, 2));
        }

        // Slide + settle on first show.
        float t = Mth.clamp((now - shownAtTick + partialTick) / SLIDE_TICKS, 0, 1);
        float ease = 1 - (1 - t) * (1 - t);
        canvas.transform.pivot(CANVAS_W / 2, 0).translate(0, -(1 - ease) * (CANVAS_H * 0.6f));
    }

    private static void hide() {
        if (handle != null) {
            handle.hide();
            handle = null;
        }
        data = null;
    }

    private static void ensureCanvas() {
        if (canvas != null) {
            return;
        }
        canvas = VectorCanvas.create(CANVAS_W, CANVAS_H);
        canvas.theme(ArenasUi.THEME);

        panel = canvas.add(Shapes.roundRect(PANEL_X, 0, PANEL_W, PANEL_H, 4)
            .fill(Colors.withAlpha(ArenasUi.PANEL, 0xE6)));
        panel.stroke(ArenasUi.HAIRLINE_HI, 1);

        timeText = canvas.add(TextNode.of("00:00").scale(2).color(ArenasUi.INK).shadow(true)
            .align(TextNode.Align.CENTER));
        timeText.at(CANVAS_W / 2, 4);
        hardcoreTag = canvas.add(TextNode.of(Component.translatable("hud.arenas_ld.hardcore"))
            .color(ArenasUi.DANGER).shadow(true));
        hardcoreTag.at(PANEL_X + PANEL_W + 6, (PANEL_H - 9) / 2 + 1);

        labelText = canvas.add(TextNode.of(Component.empty()).color(ArenasUi.INK_MID).shadow(true)
            .align(TextNode.Align.CENTER));
        labelText.at(CANVAS_W / 2, PANEL_H + 4);

        bossTrack = canvas.add(Shapes.roundRect(BAR_X, BOSS_Y, BAR_W, 4, 2)
            .fill(Colors.withAlpha(ArenasUi.ROW_BG, 0xC8)));
        bossFill = canvas.add(Shapes.roundRect(BAR_X, BOSS_Y, 2, 4, 2).fill(ArenasUi.DANGER));
        bossText = canvas.add(TextNode.of("").color(ArenasUi.DANGER).shadow(true));
    }

    /** Applies the parts that only change when a payload arrives (label, colors, rows). */
    private static void applyStatic(RunHudPayload payload) {
        boolean close = payload.kind() == RunHudPayload.Kind.CLOSE;
        panel.stroke(close ? ArenasUi.DANGER : ArenasUi.HAIRLINE_HI, 1);

        hardcoreTag.visible(payload.hardcore());

        boolean hasLabel = !payload.label().getString().isEmpty();
        labelText.visible(hasLabel);
        if (hasLabel) {
            labelText.text(payload.label());
        }

        boolean hasBoss = payload.bossHpPercent() >= 0;
        bossTrack.visible(hasBoss);
        bossFill.visible(hasBoss);
        bossText.visible(hasBoss);
        if (hasBoss) {
            bossText.text(payload.bossHpPercent() + "%");
            bossText.at(BAR_X + BAR_W + 5, BOSS_Y - 2);
        }
    }
}
