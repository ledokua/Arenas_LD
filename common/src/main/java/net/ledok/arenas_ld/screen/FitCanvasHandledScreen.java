package net.ledok.arenas_ld.screen;

import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.presentation.CanvasHandledScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * A {@link CanvasHandledScreen} that behaves like a vanilla container screen at any GUI scale:
 * the canvas renders 1:1, pixel-crisp and centered whenever it fits the GUI-scaled window, and
 * shrinks to fit (slightly soft text, but fully usable and clickable) when the window is smaller
 * than the design — e.g. GUI scale 3-4 on a laptop. Replaces the old ForcedGuiScale approach of
 * changing the whole game's GUI scale while a screen was open. Recomputed on every window resize
 * (vanilla re-runs init then). fillWindow() screens are unaffected: their canvas already matches
 * the window, so the fit factor stays 1.
 */
public class FitCanvasHandledScreen<T extends AbstractContainerMenu> extends CanvasHandledScreen<T> {

    private float minLogicalWidth = 0;

    public FitCanvasHandledScreen(T handler, Inventory inventory, Component title,
                                  VectorCanvas canvas, Placement.Screen placement) {
        super(handler, inventory, title, canvas, placement);
    }

    /**
     * For fillWindow() screens whose rows need a minimum logical width (fixed badge columns):
     * when the GUI-scaled window is narrower than this, the canvas pre-scales down so the layout
     * still gets its full logical width and the whole panel just renders smaller — instead of
     * the fixed cells sliding over the flexible ones. Windows at least this wide render 1:1.
     */
    public FitCanvasHandledScreen<T> minLogicalWidth(float width) {
        this.minLogicalWidth = width;
        return this;
    }

    @Override
    protected void init() {
        if (minLogicalWidth > 0) {
            placement(Placement.Screen.center().scale(Math.min(1f, width / minLogicalWidth)));
        }
        super.init();
        // For fillWindow screens the canvas was just sized to window/scale, so this resolves to
        // the same scale and is a no-op; for fixed-size canvases it shrinks them to fit.
        placement(ArenasUi.fitCenter(canvas.width(), canvas.height(), width, height));
    }
}
