package net.ledok.arenas_ld.client.hud;

import net.ledok.arenas_ld.client.ArenasClientConfig;
import net.ledok.arenas_ld.packet.LobbyBadgePayload;
import net.ledok.arenas_ld.screen.ArenasParchment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * The "you're in a lobby" badge: "Lobby: Forgotten Caves (2/4, 1 ready)", top-left (or top-right under the
 * effect icons, or hidden: {@code lobby_badge} in the client config). Clicking it with the chat open opens the
 * lobby's screen ({@code /arenasld lobby open}).
 */
public final class LobbyBadgeHud {
    private static final int PAD = 3;
    private static LobbyBadgePayload badge = LobbyBadgePayload.NONE;
    /** Where it was drawn last frame (GUI pixels), for the chat click; width 0 when not drawn. */
    private static int x, y, w, h;

    private LobbyBadgeHud() {
    }

    public static void onPayload(LobbyBadgePayload payload) {
        badge = payload;
    }

    public static void clear() {
        badge = LobbyBadgePayload.NONE;
        w = 0;
    }

    public static void render(GuiGraphics graphics, int screenWidth) {
        Minecraft mc = Minecraft.getInstance();
        String mode = ArenasClientConfig.get().lobby_badge;
        if (!badge.inLobby() || "hidden".equals(mode) || mc.options.hideGui || mc.player == null) {
            w = 0;
            return;
        }
        Font font = mc.font;
        Component text = Component.translatable("hud.arenas_ld.lobby_badge", badge.activity(), badge.members(),
            badge.maxMembers(), badge.ready());
        w = font.width(text) + PAD * 2;
        h = font.lineHeight + PAD * 2 - 1;
        if ("top_right".equals(mode)) {
            x = screenWidth - w - 2;
            y = 2 + effectRows(mc) * 26;
        } else {
            x = 2;
            y = 2;
        }
        graphics.fill(x, y, x + w, y + h, 0xE6D8C69E);
        graphics.renderOutline(x, y, w, h, ArenasParchment.ACCENT);
        graphics.drawString(font, text, x + PAD, y + PAD, ArenasParchment.INK, false);
    }

    /** Whether the GUI point is on the badge (it was drawn last frame). */
    public static boolean contains(double mouseX, double mouseY) {
        return w > 0 && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /** The rows of effect icons vanilla draws top-right (beneficial, then harmful). */
    private static int effectRows(Minecraft mc) {
        boolean good = false, bad = false;
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            if (!effect.showIcon()) {
                continue;
            }
            if (effect.getEffect().value().isBeneficial()) {
                good = true;
            } else {
                bad = true;
            }
        }
        return (good ? 1 : 0) + (bad ? 1 : 0);
    }
}
