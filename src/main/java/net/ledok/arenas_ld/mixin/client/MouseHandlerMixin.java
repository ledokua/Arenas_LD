package net.ledok.arenas_ld.mixin.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.ledok.arenas_ld.item.LinkerItem;
import net.ledok.arenas_ld.networking.ModPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && player.isShiftKeyDown() && player.getMainHandItem().getItem() instanceof LinkerItem) {
            ClientPlayNetworking.send(new ModPackets.CycleLinkerModePayload(vertical > 0));
            ci.cancel();
        }
    }
}
