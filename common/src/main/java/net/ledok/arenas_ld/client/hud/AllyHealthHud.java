package net.ledok.arenas_ld.client.hud;

import net.ledok.arenas_ld.client.ArenasClientConfig;
import net.ledok.arenas_ld.packet.AllyHealthPayload;
import net.ledok.arenas_ld.screen.RunTheme;
import net.ledok.arenas_ld.screen.run.RunWidgets;
import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.ledok.vectorlib.client.canvas.Shapes;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.presentation.CanvasOverlay;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The ally HP sidebar (UI rework step 4, TODO §7): teammates in the run (or Eden party) with head, name, health
 * numbers, an HP bar with absorption, and downed / out / offline states; the lowest health is outlined. Dark and
 * translucent so it reads over any terrain, with the hub theme's accent. Placed by {@code ally_sidebar}
 * ("left", "right", "hidden") and {@code ally_sidebar_offset_y} in arenas_ld-client.json: an anchor plus an offset,
 * so a drag-to-move setting can be added later. Hidden when the server sends nobody.
 */
public final class AllyHealthHud {
    private static final float W = 120;
    private static final float ROW = 21;
    private static final float PAD = 4;
    private static final int BG = 0xC40E1218;
    private static final int INK = 0xFFE8EEF5;
    private static final int DIM = 0xFF9AA8B8;
    private static final int HP = 0xFFD9473A;
    private static final int HP_TRACK = 0xFF3A1A17;
    private static final int ABSORB = 0xFFE8C24A;
    private static final int LOW = 0xFFFF6A5A;

    private static List<AllyHealthPayload.Ally> allies = List.of();
    private static @Nullable VectorCanvas canvas;
    private static @Nullable CanvasOverlay.Handle handle;

    private AllyHealthHud() {
    }

    public static void onPayload(AllyHealthPayload payload) {
        if (!allies.equals(payload.allies())) {
            allies = payload.allies();
            hide();
        }
        tick();
    }

    public static void clear() {
        allies = List.of();
        hide();
    }

    private static void hide() {
        if (handle != null) {
            handle.hide();
            handle = null;
        }
        canvas = null;
    }

    /** Re-anchor after resize or a config change, keeping the hotbar clear. */
    public static void tick() {
        if (allies.isEmpty() || "hidden".equals(ArenasClientConfig.get().ally_sidebar)) {
            hide();
            return;
        }
        if (canvas == null) {
            rebuild();
        }
        handle.placement(placement(canvas.height()));
    }

    private static Placement.Screen placement(float height) {
        var window = Minecraft.getInstance().getWindow();
        int guiW = window.getGuiScaledWidth();
        int guiH = window.getGuiScaledHeight();
        int top = RunTimerHud.isActive() ? RunTimerHud.bottom() + 4 : 4;
        int bottom = guiH - 30;
        float scale = Math.min(1f, Math.min(Math.max(1, guiW - 8) / W, Math.max(1, bottom - top) / height));
        float visibleHeight = height * scale;
        ArenasClientConfig config = ArenasClientConfig.get();
        boolean right = "right".equals(config.ally_sidebar);
        float x = right ? guiW - W * scale - 4 : 4;
        float y = Math.max(top, Math.min(bottom - visibleHeight,
            guiH / 2f - visibleHeight / 2f + config.ally_sidebar_offset_y));
        return Placement.Screen.at(x, y).scale(scale);
    }

    private static void rebuild() {
        float height = PAD * 2 + allies.size() * ROW - 3;
        canvas = VectorCanvas.create(W, height);
        RunTheme.Palette p = RunTheme.current().palette();
        canvas.add(Shapes.roundRect(0, 0, W, height, 3).fill(BG).stroke(p.accent(), 1));

        int lowest = -1;
        float lowestFrac = 0.5f; // only outline someone under half
        for (int i = 0; i < allies.size(); i++) {
            AllyHealthPayload.Ally ally = allies.get(i);
            if (ally.state() == AllyHealthPayload.State.ALIVE && ally.maxHealth() > 0) {
                float frac = ally.health() / ally.maxHealth();
                if (frac < lowestFrac) {
                    lowestFrac = frac;
                    lowest = i;
                }
            }
        }
        for (int i = 0; i < allies.size(); i++) {
            row(allies.get(i), PAD + i * ROW, i == lowest);
        }
        handle = CanvasOverlay.show(canvas, placement(height));
    }

    private static void row(AllyHealthPayload.Ally ally, float y, boolean lowest) {
        boolean alive = ally.state() == AllyHealthPayload.State.ALIVE;
        if (lowest) {
            canvas.add(Shapes.roundRect(2, y - 2, W - 4, ROW - 1, 2).stroke(LOW, 1));
        }
        CanvasNode head = RunWidgets.head(ally.uuid(), 9);
        head.at(PAD, y);
        if (!alive) {
            head.opacity(0.5f);
        }
        canvas.add(head);
        String right = switch (ally.state()) {
            case ALIVE -> fmt(ally.health()) + "/" + fmt(ally.maxHealth());
            case DOWNED -> ally.seconds() >= 0 ? Component.translatable("hud.arenas_ld.ally.downed_in", ally.seconds()).getString()
                : Component.translatable("hud.arenas_ld.ally.downed").getString();
            case OUT -> Component.translatable("hud.arenas_ld.ally.out").getString();
            case OFFLINE -> Component.translatable("hud.arenas_ld.ally.offline").getString();
        };
        TextNode value = TextNode.of(right).color(alive ? (lowest ? LOW : DIM) : DIM).shadow(true)
            .align(TextNode.Align.RIGHT).maxWidth((int) W - 50).ellipsis(true);
        value.at(W - PAD, y);
        float valueW = Math.min(W - 50, Minecraft.getInstance().font.width(right));
        TextNode name = TextNode.of(ally.name()).color(alive ? INK : DIM).shadow(true)
            .maxWidth((int) Math.max(10, W - PAD * 2 - 12 - valueW - 3)).ellipsis(true);
        name.at(PAD + 12, y);
        canvas.add(name);
        canvas.add(value);

        float barY = y + 11;
        float barW = W - PAD * 2;
        canvas.add(Shapes.roundRect(PAD, barY, barW, 5, 1).fill(HP_TRACK));
        if (alive && ally.maxHealth() > 0) {
            float total = Math.max(ally.maxHealth(), ally.health() + ally.absorption());
            float hpW = barW * Math.min(1, ally.health() / total);
            if (hpW > 0.5f) {
                canvas.add(Shapes.roundRect(PAD, barY, hpW, 5, 1).fill(lowest ? LOW : HP));
            }
            float absW = barW * Math.min(1, ally.absorption() / total);
            if (absW > 0.5f) {
                canvas.add(Shapes.roundRect(PAD + hpW, barY, Math.min(absW, barW - hpW), 5, 1).fill(ABSORB));
            }
        }
    }

    private static String fmt(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
