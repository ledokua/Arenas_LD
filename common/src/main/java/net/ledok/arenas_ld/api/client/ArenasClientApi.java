package net.ledok.arenas_ld.api.client;

import net.ledok.vectorlib.client.canvas.CanvasNode;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Client-side API (4.35.0+): lets a hub (Eden_LD) show Arenas' lobby view inside its own screen instead of Arenas'
 * lobby screen. Client only; never reach it from a server.
 *
 * <p>The server still opens Arenas' lobby menu as always (controller block, {@code /arenasld lobby open}, invites,
 * the lobby badge, {@code ArenasApi.openLobbies / joinOpenLobby / createLobby}); all lobby actions and updates go
 * through that menu. When the menu's screen would open, Arenas builds the view and asks the host first.
 */
public final class ArenasClientApi {
    private static volatile @Nullable LobbyHost host;

    private ArenasClientApi() {
    }

    /** Where lobby views go instead of Arenas' own screen; null: Arenas' screen (the fallback). The last call wins. */
    public static void setLobbyHost(@Nullable LobbyHost lobbyHost) {
        host = lobbyHost;
    }

    public static @Nullable LobbyHost lobbyHost() {
        return host;
    }

    // ── client settings (4.36.1) ────────────────────────────────────────────

    /** One choice of a {@link Setting}: the stored value and its label. */
    public record Choice(String value, Component label) {
    }

    /**
     * One of Arenas' client settings ({@code config/arenas_ld-client.json}), for a hub's settings page: show
     * {@code name}, the {@code choices} with {@code value()} selected, and call {@code set} on a pick (it saves the
     * file; the HUDs use the new value from their next update).
     */
    public record Setting(String id, Component name, Component description, java.util.List<Choice> choices,
                          java.util.function.Supplier<String> value, java.util.function.Consumer<String> set) {
    }

    /**
     * Arenas' player-facing client settings, in display order: {@code run_hud_style} (new / classic),
     * {@code loot_reveal_style} (new / classic), {@code ally_sidebar} (left / right / hidden) and {@code lobby_badge}
     * (top_left / top_right / hidden). Ids and values stay stable.
     */
    public static java.util.List<Setting> settings() {
        return net.ledok.arenas_ld.client.ClientSettings.all();
    }

    /** A screen that can show lobby views (Eden's hub). */
    public interface LobbyHost {
        /**
         * Show this lobby view. True: you show it (Arenas opens nothing); false: Arenas shows its own screen.
         * Called on the render thread from a {@code Minecraft.execute} task (not inside a screen change), so it may
         * switch screens right away.
         *
         * @param activity the activity id (empty path when the controller has none yet)
         * @param name     the controller's display name, for your title row
         */
        boolean open(ResourceLocation activity, Component name, HostedLobby view);
    }

    /**
     * One lobby view handed to a host. It has no title bar: the host shows the name and its own ← Back. The tabs
     * (Lobbies / My lobby / Leaderboard / Invites) are at its top and Leave / Ready / Start at its bottom.
     */
    public interface HostedLobby {
        /** The view: add it to your canvas once; it rebuilds its own children. */
        CanvasNode node();

        /** The space it has, in canvas units (any size from 416×200 up; below 560 wide it is one column). */
        void resize(float width, float height);

        /** A key the hub's canvas didn't take; true when the view used it. */
        boolean keyPressed(int key, int scanCode, int modifiers);

        /** A typed character the hub's canvas didn't take. */
        boolean charTyped(char c, int modifiers);

        /** A click anywhere in the hub (canvas units), so the view can close its invite list when you click outside it. */
        void mouseClicked(float canvasX, float canvasY);

        /**
         * Done with it (← Back, tab switch, the hub closing): closes this view's server menu, which stops its updates;
         * the lobby itself keeps running. Idempotent; never closes a newer lobby's menu.
         */
        void close();

        /** True once it can't be used any more: closed, a newer lobby opened, the server closed its menu. */
        boolean closed();
    }
}
