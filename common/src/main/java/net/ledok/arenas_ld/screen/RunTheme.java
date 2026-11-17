package net.ledok.arenas_ld.screen;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.platform.ArenasPlatform;
import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.ledok.vectorlib.client.canvas.Rect;
import net.ledok.vectorlib.client.canvas.Theme;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.function.Function;

/**
 * The look of the player-facing run screens (UI rework, LeDok D3): the player's Eden hub theme when Eden_LD is there
 * ({@code net.ledok.eden_ld.api.client.EdenHubThemeApi}, read by reflection so Arenas never depends on Eden), else
 * Arenas' own parchment ({@link ArenasParchment}). Client only.
 *
 * <p>Read a {@link Look} once per build ({@link #current}); a screen compares {@link #key()} each tick and rebuilds
 * when the player picks another hub theme.
 */
public final class RunTheme {
    /** The colors a run screen draws with. {@code info} has no hub token and stays parchment blue. */
    public record Palette(int panel, int panel2, int hairline, int hairlineHi, int ink, int inkMid, int inkDim,
                          int accent, int good, int warn, int danger, int info, int row, int rowAlt,
                          int barTrack, int barFill) {
    }

    /** One theme as built: its VectorLib theme (widgets), palette and shell background. */
    public record Look(@Nullable ResourceLocation id, Theme theme, Palette palette, Function<Rect, CanvasNode> panel) {
    }

    public static final Palette PARCHMENT_PALETTE = new Palette(
        ArenasParchment.PANEL, ArenasParchment.PANEL_2, ArenasParchment.HAIRLINE, ArenasParchment.HAIRLINE_HI,
        ArenasParchment.INK, ArenasParchment.INK_MID, ArenasParchment.INK_DIM, ArenasParchment.ACCENT,
        ArenasParchment.GOOD, ArenasParchment.WARN, ArenasParchment.DANGER, ArenasParchment.INFO,
        ArenasParchment.ROW_BG, ArenasParchment.ROW_BG_ALT, 0xFFBBA47A, 0xFF6E9A4A);

    private static final Look PARCHMENT = new Look(null, ArenasParchment.THEME, PARCHMENT_PALETTE, ArenasParchment.panel());

    private static boolean looked;
    @Nullable private static Method themeMethod;
    @Nullable private static Method panelMethod;
    @Nullable private static Method idMethod;
    @Nullable private static Method hairlineHiMethod;
    @Nullable private static Method barTrackMethod;
    @Nullable private static Method barFillMethod;

    @Nullable private static Look cached;

    private RunTheme() {
    }

    private static void look() {
        if (looked) {
            return;
        }
        looked = true;
        if (!ArenasPlatform.INSTANCE.isModLoaded("eden_ld")) {
            return;
        }
        try {
            Class<?> api = Class.forName("net.ledok.eden_ld.api.client.EdenHubThemeApi");
            themeMethod = api.getMethod("theme");
            panelMethod = api.getMethod("panel");
            idMethod = api.getMethod("id");
            // Eden 1.3.101+: the few colors VectorLib's Theme doesn't carry.
            hairlineHiMethod = optional(api, "hairlineHi");
            barTrackMethod = optional(api, "barTrack");
            barFillMethod = optional(api, "barFill");
        } catch (ReflectiveOperationException | LinkageError e) {
            themeMethod = panelMethod = idMethod = null;
        }
    }

    private static @Nullable Method optional(Class<?> api, String name) {
        try {
            return api.getMethod(name);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /** What identifies the theme now: the hub theme's id, or null for parchment. Cheap; for change checks. */
    public static @Nullable Object key() {
        look();
        if (idMethod == null) {
            return null;
        }
        try {
            return idMethod.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The look to build with now: the hub theme when Eden has one, parchment otherwise. */
    @SuppressWarnings("unchecked")
    public static Look current() {
        look();
        if (themeMethod == null || panelMethod == null) {
            return PARCHMENT;
        }
        Object key = key();
        Look last = cached;
        if (last != null && Objects.equals(last.id(), key)) {
            return last;
        }
        try {
            Theme theme = (Theme) themeMethod.invoke(null);
            Function<Rect, CanvasNode> panel = (Function<Rect, CanvasNode>) panelMethod.invoke(null);
            if (theme == null || panel == null) {
                return PARCHMENT;
            }
            Palette palette = new Palette(theme.surface(), theme.surfaceAlt(), theme.hairline(),
                color(hairlineHiMethod, ArenasParchment.HAIRLINE_HI), theme.ink(), theme.inkMid(), theme.inkDim(),
                theme.accent(), theme.success(), theme.warning(), theme.danger(), ArenasParchment.INFO,
                theme.rowA(), theme.rowB(), color(barTrackMethod, PARCHMENT_PALETTE.barTrack()),
                color(barFillMethod, PARCHMENT_PALETTE.barFill()));
            Look look = new Look(key instanceof ResourceLocation id ? id : null, theme, palette, panel);
            cached = look;
            return look;
        } catch (ReflectiveOperationException | RuntimeException e) {
            ArenasLdMod.LOGGER.warn("[Arenas] Couldn't read Eden's hub theme; using parchment.", e);
            themeMethod = null;
            return PARCHMENT;
        }
    }

    private static int color(@Nullable Method method, int fallback) {
        if (method == null) {
            return fallback;
        }
        try {
            Object value = method.invoke(null);
            return value instanceof Integer i ? i : fallback;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return fallback;
        }
    }
}
