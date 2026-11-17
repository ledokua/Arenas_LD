package net.ledok.arenas_ld.client;

import net.ledok.arenas_ld.api.client.ArenasClientApi;
import net.ledok.arenas_ld.screen.RunTheme;
import net.ledok.vectorlib.client.canvas.CanvasGroup;
import net.ledok.vectorlib.client.canvas.TextNode;
import net.ledok.vectorlib.client.canvas.VectorCanvas;
import net.ledok.vectorlib.client.presentation.CanvasScreen;
import net.ledok.vectorlib.client.presentation.Placement;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Development only (off unless {@code -Darenas_ld.devLobbyHost=true}): a stand-in for Eden's hub that hosts the lobby
 * view ({@link ArenasClientApi}), to try the hosted path without Eden. A plain canvas screen with a title line and
 * the view under it; Esc closes the view's menu.
 */
public final class DevLobbyHost {
    private DevLobbyHost() {
    }

    public static void registerIfEnabled() {
        if (!Boolean.getBoolean("arenas_ld.devLobbyHost")) {
            return;
        }
        ArenasClientApi.setLobbyHost((activity, name, view) -> {
            Minecraft.getInstance().setScreen(new HostScreen(activity.toString(), name, view));
            return true;
        });
    }

    private static final class HostScreen extends CanvasScreen {
        private final ArenasClientApi.HostedLobby view;
        private final String activity;
        private final Component name;

        HostScreen(String activity, Component name, ArenasClientApi.HostedLobby view) {
            super(Component.literal("Dev hub"), VectorCanvas.create(480, 300), Placement.Screen.center());
            this.view = view;
            this.activity = activity;
            this.name = name;
            fillWindow();
        }

        @Override
        protected void onCanvasResized(float width, float height) {
            canvas.clear();
            canvas.theme(RunTheme.current().theme());
            canvas.add(TextNode.of(Component.literal("Dev hub · ").append(name).append(" · " + activity + " · ← Esc"))
                .color(0xFFFFFFFF)).at(8, 6);
            CanvasGroup holder = canvas.add(CanvasGroup.create());
            holder.at(8, 22);
            holder.add(view.node());
            view.resize(width - 16, height - 30);
        }

        @Override
        protected void onCanvasFrame(float partialTick) {
            if (view.closed()) {
                onClose();
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            boolean handled = super.mouseClicked(mouseX, mouseY, button);
            float[] c = toCanvas(mouseX, mouseY);
            view.mouseClicked(c[0], c[1]);
            return handled;
        }

        @Override
        public void removed() {
            super.removed();
            view.close();
        }
    }
}
