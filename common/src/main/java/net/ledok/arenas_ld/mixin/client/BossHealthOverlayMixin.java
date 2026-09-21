package net.ledok.arenas_ld.mixin.client;

import net.ledok.arenas_ld.client.hud.RunTimerHud;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides vanilla boss bars while the Arenas run HUD owns the top of the screen. Bosses that
 * carry their own {@code ServerBossEvent} (wither, dragon, modded bosses) would otherwise
 * stack their bar on top of the run timer's HP row. Outside a run, vanilla bars render
 * exactly as normal.
 */
@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void arenas_ld$hideDuringRun(GuiGraphics guiGraphics, CallbackInfo ci) {
        if (RunTimerHud.isActive()) {
            ci.cancel();
        }
    }
}
