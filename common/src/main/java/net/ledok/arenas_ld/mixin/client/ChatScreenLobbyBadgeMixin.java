package net.ledok.arenas_ld.mixin.client;

import net.ledok.arenas_ld.client.hud.LobbyBadgeHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With the chat open, a click on the lobby badge opens the player's lobby screen. */
@Mixin(ChatScreen.class)
public class ChatScreenLobbyBadgeMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void arenasLd$openLobby(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (button == 0 && LobbyBadgeHud.contains(mouseX, mouseY) && mc.player != null) {
            mc.player.connection.sendCommand("arenasld lobby open");
            cir.setReturnValue(true);
        }
    }
}
