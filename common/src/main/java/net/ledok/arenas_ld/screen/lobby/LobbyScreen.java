package net.ledok.arenas_ld.screen.lobby;

import net.ledok.arenas_ld.ArenasLdMod;
import net.ledok.arenas_ld.api.client.ArenasClientApi;
import net.ledok.arenas_ld.screen.ArenasUi;
import net.ledok.arenas_ld.screen.FitCanvasHandledScreen;
import net.ledok.arenas_ld.screen.RentalPick;
import net.ledok.vectorlib.client.canvas.CanvasGroup;
import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

/**
 * The shell every controller lobby screen shares (dungeon, raid, arena): a window-filling canvas at 1:1 (no fit
 * scaling, UI rework D1) drawn by one {@link LobbyView}. Subclasses map their menu data and payloads onto
 * {@link LobbyHost} and call {@link #snapshot} + {@link #activeView()}.{@link LobbyView#dataChanged} when a snapshot
 * arrives.
 *
 * <p><b>Hosted</b> (4.35.0, LeDok: "only the hub's screens when Eden is there"): when a hub registered
 * {@link ArenasClientApi#setLobbyHost}, the screen hands a hosted view to it right after opening, and the hub shows it
 * instead. The menu stays open (actions and updates go through it, as always); this screen object keeps receiving the
 * updates through {@link #current()}. Without a host, or when it declines, the screen shows itself as before.
 */
public abstract class LobbyScreen<T extends AbstractContainerMenu> extends FitCanvasHandledScreen<T>
    implements LobbyHost, RentalPick.Host {

    private static volatile @Nullable ResourceLocation nextActivity;
    private static @Nullable LobbyScreen<?> hostedScreen;

    protected final LobbyView view;
    private long snapshotTick;
    private long snapshotEpochMs = System.currentTimeMillis();
    private int rentalVersion = RentalPick.version();
    private final @Nullable ResourceLocation activity;

    // hosted
    private boolean handingOff;
    private @Nullable CanvasGroup hostedRoot;
    private @Nullable LobbyView hostedView;
    private boolean hostedClosed;

    protected LobbyScreen(T handler, Inventory inventory, Component title) {
        super(handler, inventory, title, VectorCanvas.create(560, 360), Placement.Screen.center());
        fillWindow();
        this.view = new LobbyView(this, canvas);
        this.activity = nextActivity;
        nextActivity = null;
    }

    // ── hosting ──────────────────────────────────────────────────────────────

    /** {@code LobbyOpeningPayload}: the activity of the lobby menu that opens next. */
    public static void nextActivity(ResourceLocation activity) {
        nextActivity = activity;
    }

    /** The lobby screen updates go to: the one on screen, or the one a hub is showing. */
    public static @Nullable LobbyScreen<?> current() {
        if (Minecraft.getInstance().screen instanceof LobbyScreen<?> shown) {
            return shown;
        }
        LobbyScreen<?> hosted = hostedScreen;
        return hosted != null && !hosted.hostedGone() ? hosted : null;
    }

    /** Every client tick: the hosted view's countdowns, theme and rental picks (its screen isn't ticking). */
    public static void hostedTick() {
        LobbyScreen<?> hosted = hostedScreen;
        if (hosted == null) {
            return;
        }
        if (hosted.hostedGone()) {
            hostedScreen = null;
            return;
        }
        hosted.tickView();
    }

    public static void clearHosted() {
        hostedScreen = null;
        nextActivity = null;
    }

    /** The view updates go to (hosted or this screen's own). */
    protected LobbyView activeView() {
        return hostedView != null ? hostedView : view;
    }

    private boolean hostedGone() {
        Minecraft mc = Minecraft.getInstance();
        return hostedClosed || mc.player == null || mc.player.containerMenu != menu;
    }

    @Override
    protected void init() {
        if (!handingOff && hostedView == null && ArenasClientApi.lobbyHost() != null) {
            handingOff = true;
            // After the screen change that is opening us finishes (the host may switch screens right away).
            Minecraft.getInstance().execute(this::handOff);
        }
        super.init();
    }

    private void handOff() {
        Minecraft mc = Minecraft.getInstance();
        ArenasClientApi.LobbyHost host = ArenasClientApi.lobbyHost();
        boolean accepted = false;
        if (host != null && mc.player != null && mc.player.containerMenu == menu) {
            hostedRoot = CanvasGroup.create();
            hostedView = new LobbyView(this, hostedRoot);
            hostedView.open();
            try {
                accepted = host.open(activity != null ? activity : ResourceLocation.fromNamespaceAndPath(ArenasLdMod.MOD_ID, ""),
                    getTitle(), new Hosted());
            } catch (RuntimeException e) {
                ArenasLdMod.LOGGER.warn("[Arenas] The lobby host failed; showing Arenas' own lobby screen", e);
            }
        }
        handingOff = false;
        if (accepted) {
            LobbyScreen<?> previous = hostedScreen;
            if (previous != null && previous != this) {
                previous.hostedClosed = true;
            }
            hostedScreen = this;
            return;
        }
        hostedView = null;
        hostedRoot = null;
        if (mc.screen == this) {
            view.open();
            view.build();
        }
    }

    /** The view handed to the hub. */
    private final class Hosted implements ArenasClientApi.HostedLobby {
        @Override
        public CanvasNode node() {
            return hostedRoot;
        }

        @Override
        public void resize(float width, float height) {
            if (hostedView != null && !hostedGone()) {
                hostedView.hostSize(width, height);
                hostedView.build();
            }
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            return false; // the hub's canvas routes keys to the focused field; nothing else is bound
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            return false;
        }

        @Override
        public void mouseClicked(float canvasX, float canvasY) {
            if (hostedView != null && !hostedGone()) {
                hostedView.clickedAt(canvasX, canvasY);
            }
        }

        @Override
        public void close() {
            closeHosted();
        }

        @Override
        public boolean closed() {
            return hostedGone();
        }
    }

    /** Closes the hosted view's menu (only its own), without touching the screen the hub is on. */
    private void closeHosted() {
        if (hostedClosed) {
            return;
        }
        hostedClosed = true;
        if (hostedScreen == this) {
            hostedScreen = null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu == menu && mc.getConnection() != null) {
            // Not LocalPlayer.closeContainer: that would also close the hub's screen.
            mc.getConnection().send(new ServerboundContainerClosePacket(menu.containerId));
            menu.removed(mc.player);
            mc.player.containerMenu = mc.player.inventoryMenu;
        }
    }

    private void tickView() {
        if (rentalVersion != RentalPick.version()) {
            rentalVersion = RentalPick.version();
            activeView().build(); // the rental picks changed
        }
        activeView().tick();
    }

    // ── own screen ───────────────────────────────────────────────────────────

    /** The server tick the latest snapshot was taken at (invite expiries and cooldowns count from it). */
    protected void snapshot(long serverTick) {
        this.snapshotTick = serverTick;
        this.snapshotEpochMs = System.currentTimeMillis();
    }

    @Override
    protected void onCanvasResized(float width, float height) {
        if (handingOff || hostedView != null) {
            return; // the hub shows the hosted view; this screen draws nothing
        }
        view.open();
        view.build();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (hostedView == null) {
            tickView();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        float[] c = toCanvas(mouseX, mouseY);
        view.clickedAt(c[0], c[1]);
        return handled;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Close with ✕ or Esc only: the inventory key would close the lobby while typing an invite.
        if (ArenasUi.swallowsInventoryKey(input, keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (hostedView != null) {
            closeHosted(); // a server "close the lobby screen" while hosted: the hub sees closed() and goes back
            return;
        }
        super.onClose();
    }

    @Override
    public VectorCanvas rentalCanvas() {
        if (hostedRoot != null && hostedRoot.canvas() != null) {
            return hostedRoot.canvas();
        }
        return canvas;
    }

    @Override
    public Component title() {
        return getTitle();
    }

    @Override
    public long serverTick() {
        return snapshotTick + Math.max(0L, System.currentTimeMillis() - snapshotEpochMs) / 50L;
    }

    @Override
    public long snapshotTick() {
        return snapshotTick;
    }

    @Override
    public void close() {
        onClose();
    }
}
