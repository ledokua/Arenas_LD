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
 * The run-timer HUD bar (UI rework step 4): one wide dark translucent panel with the countdown in big digits, the
 * objective beside it, a "Hardcore" tag, and a long boss-HP bar with the percent in bold (a split boss's whole tree).
 * The border takes the hub theme's accent. Replaces the
 * vanilla boss-bar timers. Driven by {@link RunHudPayload} updates (~1/s) and counts down
 * locally between packets; hides itself when the server goes quiet.
 */
public final class RunTimerHud {
    /** Server sends every 20 ticks; past this silence we assume the run is gone. */
    private static final long STALE_TICKS = 80;
    private static final float SLIDE_TICKS = 8.0f;

    // UI rework step 4: one wide dark panel, timer big on the left, the objective beside it, a long boss bar below.
    /** The panel's narrowest width; it grows (up to {@link #MAX_W}) so the objective fits (4.36.1). */
    private static final float CANVAS_W = 300;
    private static final float MAX_W = 480;
    private static final float LABEL_X = 78;
    private static final float CANVAS_H = 38;
    private static final float TOP_H = 24;
    private static final float BAR_X = 8;
    private static final float BOSS_Y = TOP_H + 3;
    private static final float BAR_H = 6;
    private static final int BG = 0xC40E1218;
    private static final int INK = 0xFFE8EEF5;
    private static final int DIM = 0xFFB4C0CE;
    private static final int HP = 0xFFD9473A;
    private static final int HP_TRACK = 0xFF3A1A17;

    private static float width = CANVAS_W;
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
        // The client setting picks the look; switching hides the other one.
        if (net.ledok.arenas_ld.client.ArenasClientConfig.get().classicRunHud()) {
            hide();
            ClassicRunTimerHud.onPayload(payload);
            return;
        }
        ClassicRunTimerHud.clear();
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
            handle = CanvasOverlay.show(canvas, Placement.Screen.top(4));
        }
        applyStatic(payload);
    }

    public static void clear() {
        hide();
        ClassicRunTimerHud.clear();
    }

    /** True while the run timer bar is on screen — vanilla boss bars are suppressed then. */
    public static boolean isActive() {
        return handle != null || ClassicRunTimerHud.isActive();
    }

    /** How far down the timer reaches (GUI px from the top), for what sits under it (the loot reveal). */
    public static int bottom() {
        return net.ledok.arenas_ld.client.ArenasClientConfig.get().classicRunHud() ? 56 : 42;
    }

    /** Per-frame driver, called from the shared HUD render hook. */
    public static void tick(float partialTick) {
        ClassicRunTimerHud.tick(partialTick);
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
                BAR_X, BOSS_Y, Math.max(2, barW() * hpFrac), BAR_H, 2));
        }

        // Slide + settle on first show.
        float t = Mth.clamp((now - shownAtTick + partialTick) / SLIDE_TICKS, 0, 1);
        float ease = 1 - (1 - t) * (1 - t);
        canvas.transform.pivot(width / 2, 0).translate(0, -(1 - ease) * (CANVAS_H * 0.6f));
    }

    private static float barW() {
        return width - 8 - 40;
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

        panel = canvas.add(Shapes.roundRect(0, 0, CANVAS_W, TOP_H, 3).fill(BG));
        panel.stroke(net.ledok.arenas_ld.screen.RunTheme.current().palette().accent(), 1);

        timeText = canvas.add(TextNode.of("00:00").scale(2).color(INK).shadow(true));
        timeText.at(8, 4);
        hardcoreTag = canvas.add(TextNode.of(Component.translatable("hud.arenas_ld.hardcore"))
            .color(0xFFFF7A6A).shadow(true).align(TextNode.Align.RIGHT));
        hardcoreTag.at(CANVAS_W - 8, 8);

        labelText = canvas.add(TextNode.of(Component.empty()).color(DIM).shadow(true).ellipsis(true));
        labelText.at(LABEL_X, 8);

        bossTrack = canvas.add(Shapes.roundRect(BAR_X, BOSS_Y, barW(), BAR_H, 2).fill(HP_TRACK));
        bossFill = canvas.add(Shapes.roundRect(BAR_X, BOSS_Y, 2, BAR_H, 2).fill(HP));
        bossText = canvas.add(TextNode.of("").color(INK).shadow(true).align(TextNode.Align.RIGHT));
    }

    /** Applies the parts that only change when a payload arrives (label, colors, rows). */
    private static void applyStatic(RunHudPayload payload) {
        boolean close = payload.kind() == RunHudPayload.Kind.CLOSE;
        boolean hasBoss = payload.bossHpPercent() >= 0;
        float height = hasBoss ? CANVAS_H : TOP_H;
        int accent = net.ledok.arenas_ld.screen.RunTheme.current().palette().accent();
        int guiW = Minecraft.getInstance().getWindow().getGuiScaledWidth();

        hardcoreTag.visible(payload.hardcore());
        float hardcoreW = payload.hardcore()
            ? Minecraft.getInstance().font.width(Component.translatable("hud.arenas_ld.hardcore")) + 8 : 0;
        boolean hasLabel = !payload.label().getString().isEmpty();
        float labelW = hasLabel ? Minecraft.getInstance().font.width(payload.label()) : 0;
        // Wide enough for the whole objective ("Returning home — /exit to leave now"), within the window.
        width = Math.max(CANVAS_W, Math.min(Math.min(MAX_W, guiW - 16), LABEL_X + labelW + 10 + hardcoreW + 8));
        canvas.size(width, CANVAS_H);
        panel.path(net.ledok.vectorlib.client.canvas.Path2D.create().roundRect(0, 0, width, height, 3));
        panel.stroke(close ? 0xFFFF7A6A : accent, 1);
        hardcoreTag.at(width - 8, 8);
        bossTrack.path(net.ledok.vectorlib.client.canvas.Path2D.create().roundRect(BAR_X, BOSS_Y, barW(), BAR_H, 2));

        labelText.visible(hasLabel);
        if (hasLabel) {
            labelText.text(payload.label());
            labelText.maxWidth((int) (width - LABEL_X - 8 - hardcoreW));
        }

        bossTrack.visible(hasBoss);
        bossFill.visible(hasBoss);
        bossText.visible(hasBoss);
        if (hasBoss) {
            bossText.text(Component.literal(payload.bossHpPercent() + "%").withStyle(net.minecraft.ChatFormatting.BOLD));
            bossText.at(width - 8, BOSS_Y - 1);
        }
        // A narrow window (GUI scale 3 on 1366×768 is 455 wide) gets the panel a little smaller rather than clipped.
        float scale = Math.min(1f, (guiW - 16) / width);
        if (handle != null) {
            handle.placement(Placement.Screen.top(4).scale(scale));
        }
    }

}
